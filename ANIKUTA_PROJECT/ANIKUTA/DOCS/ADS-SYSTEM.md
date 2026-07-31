# ANIKUTA Advertising System — Architecture Documentation

> **Module:** `:core:ads`
> **Status:** Production-ready (v0.2.4)
> **Last updated:** 2026-07-31

## Table of Contents

1. [Overview](#1-overview)
2. [Module Structure](#2-module-structure)
3. [Core Components](#3-core-components)
4. [Ad Interaction State Machine](#4-ad-interaction-state-machine)
5. [Integration with AppController](#5-integration-with-appcontroller)
6. [UI Components](#6-ui-components)
7. [Settings UI](#7-settings-ui)
8. [Lifecycle and Threading](#8-lifecycle-and-threading)
9. [Privacy and On-Device Tracking](#9-privacy-and-on-device-tracking)
10. [Configuration Parameters](#10-configuration-parameters)
11. [Future Extensions](#11-future-extensions)
12. [Testing Guide](#12-testing-guide)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. Overview

The ANIKUTA Advertising System is a **highly customizable, modular, on-device** ad
interstitial system. It shows link-based ads before anime detail navigation,
tracks ad views locally (no server communication), and lets the user configure
every aspect of the ad experience.

### Key Features

- **Link-based ads**: The user clicks "OK" → a browser opens to a configurable URL → the user returns → the ad is counted.
- **Daily quota**: Configurable 1–1000 ads per day (default: 1000 for testing).
- **Cooldown**: After watching an ad, no new ad is shown for a configurable period (default: 30 minutes).
- **Minimum stay**: The user must stay on the ad URL for a configurable minimum time (default: 2 seconds). If they return sooner, the ad is NOT counted and a "Please take some time" message is shown.
- **On-device tracking**: All tracking (ads shown today, last ad timestamp, total lifetime ads) is stored locally via `PreferenceStore`. No data is sent to any server.
- **Per-navigation interception**: Ads are shown before anime detail pages — regardless of entry point (browse, search, library, downloads, history, linking flow).
- **Beautiful UI**: A themed dialog with a pill emoji, "Your daily dose of pills is here." message, and OK/Cancel buttons.

### Design Philosophy

The system is designed to be:
1. **Modular** — All ad logic lives in `:core:ads`, isolated from the rest of the app.
2. **Customizable** — Every parameter (quota, cooldown, min-stay, URL) is user-configurable in Settings → General → Advertising.
3. **Private** — No server communication. All tracking is on-device.
4. **Non-intrusive** — The user can turn it off entirely, and can always skip an ad (Cancel stays on the current page).
5. **Testable** — Default quota is 1000/day for thorough testing. The cooldown + min-stay can be adjusted to 0 for rapid testing.

---

## 2. Module Structure

```
core/ads/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml          (empty — pure library)
    └── java/app/confused/anikuta/core/ads/
        ├── AdsPreferences.kt         (user-configurable settings)
        ├── AdTracker.kt              (on-device view tracking)
        ├── AdManager.kt              (state machine + business logic)
        └── di/
            └── AdsModule.kt          (Koin DI registration)
```

### Dependencies

```kotlin
// core/ads/build.gradle.kts
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:preferences"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
```

The module depends only on `:core:common` and `:core:preferences` — no feature or app modules. This keeps it fully decoupled.

---

## 3. Core Components

### 3.1 AdsPreferences

**File:** `AdsPreferences.kt`

User-configurable settings, backed by `PreferenceStore`.

| Setting | Key | Default | Range | Description |
|---|---|---|---|---|
| `adsEnabled` | `pref_ads_enabled` | `true` | bool | Master on/off toggle |
| `dailyAdQuota` | `pref_ads_daily_quota` | `1000` | 1–1000 | Max ads per day (resets at midnight) |
| `cooldownMinutes` | `pref_ads_cooldown_minutes` | `30` | 0–1440 | Minutes to wait after an ad before showing the next |
| `minStaySeconds` | `pref_ads_min_stay_seconds` | `2` | 1–60 | Minimum time the user must stay on the ad URL |
| `adUrl` | `pref_ads_url` | `https://1118000.xyz/` | URL | The URL the user is redirected to |

Each setting has a getter, setter, and reactive `Flow` observer.

### 3.2 AdTracker

**File:** `AdTracker.kt`

On-device tracking of ad views. All data stays on the device.

| Tracked field | Key | Description |
|---|---|---|
| `adsShownToday` | `pref_ads_shown_today` | How many ads the user has seen today (resets at midnight) |
| `lastAdTimestamp` | `pref_ads_last_timestamp` | When the last ad was watched (for cooldown) |
| `totalAdsShown` | `pref_ads_total` | Lifetime count of ads watched |
| `lastResetDate` | `pref_ads_last_reset_date` | Date string of the last daily reset (for detecting day rollover) |

**Daily reset logic:** `resetDailyIfNeeded()` compares the current date (formatted as `yyyy-MM-dd`) with `lastResetDate`. If different, `adsShownToday` is reset to 0. This is called automatically before every quota check.

### 3.3 AdManager

**File:** `AdManager.kt`

The central orchestrator. Manages the ad interaction state machine + enforces quota/cooldown/min-stay.

**Key methods:**
- `shouldShowAd(): Boolean` — Checks if an ad should be shown right now (enabled, not over quota, not in cooldown, not already in progress).
- `startAdDialog()` — Transitions to `DialogShowing` state.
- `acceptAd()` — User clicked OK → transitions to `AdInProgress`.
- `cancelAd(): Boolean` — User clicked Cancel → transitions to `Idle` (ad NOT counted).
- `onAdReturn(): Boolean` — User returned from browser → checks min-stay → records ad view if stayed long enough.
- `dismissTooEarly()` — User clicked "Try Again" from the too-early message.
- `cancelFromTooEarly(): Boolean` — User clicked "Skip" from the too-early message.
- `getRemainingCooldownMs(): Long` — Remaining cooldown in ms (for UI display).

---

## 4. Ad Interaction State Machine

```
                    ┌──────────────────────────────────────────────┐
                    │                                              │
                    ▼                                              │
                 ┌──────┐                                          │
                 │ Idle │◄─────────────────────────────────────────┤
                 └──────┘                                          │
                    │                                              │
                    │ shouldShowAd() = true                        │
                    │ → startAdDialog()                            │
                    ▼                                              │
            ┌────────────────┐                                    │
            │ DialogShowing  │                                    │
            │ (OK / Cancel)  │                                    │
            └────────────────┘                                    │
                    │                                              │
           ┌────────┴────────┐                                     │
           │                 │                                     │
     acceptAd()        cancelAd()                                  │
           │                 │                                     │
           ▼                 ▼                                     │
   ┌──────────────┐   ┌──────────┐                                 │
   │ AdInProgress │   │ Cancelled │────────────────────────────────►│
   │ (browser     │   └──────────┘                                 │
   │  open)       │                                                │
   └──────────────┘                                                │
           │                                                       │
     onAdReturn()                                                  │
           │                                                       │
     ┌─────┴─────┐                                                 │
     │           │                                                 │
 stayed >=   stayed <                              ┌──────────────┐
 min-stay    min-stay                               │  Completed   │
     │           │                                  └──────────────┘
     │           ▼                                         │
     │   ┌──────────────────┐                             │
     │   │ ReturnedTooEarly │                             │
     │   │ (Try Again/Skip) │                             │
     │   └──────────────────┘                             │
     │           │                                        │
     │     ┌─────┴──────┐                                 │
     │     │            │                                 │
     │  retry()    cancel()                               │
     │     │            │                                 │
     │     ▼            ▼                                 │
     │  back to     ┌──────┐                              │
     │  Dialog      │ Idle │◄────────────────────────────┘
     │  Showing     └──────┘
     │
     ▼
 recordAdView()
 → cooldown starts
 → deferred navigation
   executes
```

### State descriptions

| State | Description | User action | Next state |
|---|---|---|---|
| `Idle` | No ad in progress | `shouldShowAd()` returns true | `DialogShowing` |
| `DialogShowing` | Ad dialog visible | Click OK | `AdInProgress` |
| `DialogShowing` | Ad dialog visible | Click Cancel | `Cancelled` → `Idle` |
| `AdInProgress` | Browser open, waiting for return | `onAdReturn()` (stayed ≥ min) | `Completed` → `Idle` |
| `AdInProgress` | Browser open, waiting for return | `onAdReturn()` (stayed < min) | `ReturnedTooEarly` |
| `ReturnedTooEarly` | "Please take some time" message | Click "Try Again" | `DialogShowing` |
| `ReturnedTooEarly` | "Please take some time" message | Click "Skip" | `Idle` (ad NOT counted) |
| `Completed` | Ad counted (transient) | — | `Idle` |
| `Cancelled` | Ad NOT counted (transient) | — | `Idle` |

---

## 5. Integration with AppController

The ad system is integrated into `AppController` via the `withAdGate` method.

### withAdGate

```kotlin
private fun withAdGate(action: () -> Unit) {
    if (adManager.shouldShowAd()) {
        pendingAdNavigation = action  // defer the navigation
        adManager.startAdDialog()     // show the ad dialog
    } else {
        action()  // no ad needed — execute immediately
    }
}
```

### Intercepted navigation methods

All anime-detail navigation methods are wrapped with `withAdGate`:

| Method | Entry point | Wrapped? |
|---|---|---|
| `pushDetail(anilistId)` | Browse, Search, Updates, History, Profile | ✅ |
| `pushExtensionDetail(source, sAnime, anilistId)` | Search (extension result), Library (unlinked) | ✅ |
| `onLinked(anilistId, ...)` | ExtensionLinkingSheet (after link) | ✅ |
| `onGoWithoutLinking(source, sAnime)` | ExtensionLinkingSheet ("go without") | ✅ |
| `openLibraryAnime(anime)` | Library tab | ✅ (delegates to pushDetail/pushExtensionDetail) |
| `openDownloadedAnimeByContentId(contentId)` | Downloaded Files screen | ✅ (delegates to pushDetail/pushExtensionDetail) |

### Ad callback methods (called by the UI)

| Method | Called when | Effect |
|---|---|---|
| `onAdAccepted()` | User clicks OK on the ad dialog | Opens browser to ad URL, sets `adAwaitingReturn = true` |
| `onAdCancelled()` | User clicks Cancel | Discards deferred navigation, stays on current page |
| `onAdReturn()` | Activity ON_RESUME while `adAwaitingReturn` | Checks min-stay, records ad if stayed long enough, executes deferred navigation |
| `onAdTooEarlyRetry()` | User clicks "Try Again" on too-early message | Goes back to ad dialog |
| `onAdTooEarlyCancel()` | User clicks "Skip" on too-early message | Discards deferred navigation, stays on current page |

### Lifecycle observer

In `AnikutaRoot.kt`, a `DisposableEffect` observes the Activity lifecycle:

```kotlin
val lifecycleOwner = LocalLifecycleOwner.current
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME && appController.adAwaitingReturn) {
            appController.onAdReturn()
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

This detects when the user returns from the browser (ON_RESUME) and triggers the min-stay check.

---

## 6. UI Components

### AdDialog

**File:** `app/src/main/java/app/confused/anikuta/navigation/AdDialog.kt`

A full-screen overlay with a semi-transparent scrim + a centered card.

**States rendered:**

1. **DialogShowing** — The main ad card:
   - Pill emoji (💊) in a circular badge
   - "Your daily dose of pills is here." (bold, 20sp)
   - "Support the app by visiting our sponsor.\nX ads remaining today."
   - Cancel (outlined) + OK (filled) buttons

2. **AdInProgress** — Waiting card:
   - CircularProgressIndicator
   - "Waiting for you to return…"
   - "Please stay on the page for at least a few seconds, then return to the app."

3. **ReturnedTooEarly** — Warning card:
   - ⚠️ emoji
   - "Please take some time."
   - "Please at least stay there for X seconds.\nYou returned after only Y seconds."
   - Skip (outlined) + Try Again (filled) buttons

### Rendering in AnikutaRoot

```kotlin
// In AppOverlays()
if (appController.pendingAdNavigation != null) {
    AdDialog(appController)
}
```

The dialog is shown when `pendingAdNavigation` is non-null (set by `withAdGate`).

---

## 7. Settings UI

### AdSettingsSection

**File:** `feature/settings/src/main/java/app/confused/anikuta/feature/settings/AdSettingsSection.kt`

Embedded in `GeneralSettingsScreen` under the "Advertising" section label.

**UI elements:**
1. **Master toggle** — "Show ads" (on/off)
2. **Live stats card** — Shows "Today: X" and "Total: Y" (ads shown)
3. **Daily ad quota** — Selector card → number input dialog (1–1000)
4. **Cooldown after ad** — Selector card → number input dialog (0–1440 min)
5. **Minimum stay time** — Selector card → number input dialog (1–60 sec)
6. **Ad URL** — Selector card → text input dialog

All settings are reactive — changes are immediately reflected in the UI via `Flow` observers.

---

## 8. Lifecycle and Threading

### Threading model

- **AdManager** — All state mutations happen on the main thread (the `StateFlow` is consumed by Compose).
- **AdTracker** — All reads/writes are synchronous via `PreferenceStore` (SharedPreferences with `apply()`). Thread-safe.
- **AdsPreferences** — Same as AdTracker — synchronous, thread-safe.
- **AdDialog** — Composable, runs on the main thread.

### Lifecycle integration

1. **App open** — No ad check on startup (ads are only triggered by navigation).
2. **Navigation to anime detail** — `withAdGate` checks `shouldShowAd()`. If true, the ad dialog is shown + navigation is deferred.
3. **User clicks OK** — Browser opens. `adAwaitingReturn = true`.
4. **User returns (ON_RESUME)** — `onAdReturn()` checks min-stay. If stayed long enough → ad counted, navigation proceeds. If not → "too early" message.
5. **User clicks Cancel** — Navigation discarded, stays on current page.
6. **App backgrounded during ad** — The `pendingAdNavigation` lambda is held in memory. When the user returns to the app, the ad dialog is still showing (Compose state survives configuration changes).

---

## 9. Privacy and On-Device Tracking

### What is tracked (on-device only)

- Number of ads shown today
- Timestamp of the last ad
- Total lifetime ads shown
- Date of the last daily reset

### What is NOT tracked

- No user identity
- No device fingerprint
- No browsing history
- No network requests
- No server-side analytics

All tracking data is stored in `SharedPreferences` (via `PreferenceStore`) and never leaves the device. The data is cleared if the user clears app data or uninstalls the app.

### Future: backend integration

The user has indicated that in the future, the ad system will be moved to a backend. The current architecture is designed to make this transition easy:

1. **AdManager** can be extended to fetch ad config from a server (instead of `AdsPreferences`).
2. **AdTracker** can be extended to report views to a server (with user consent).
3. The `AdSource` interface (similar to `UpdateSource`) would abstract the ad provider.

---

## 10. Configuration Parameters

### Default values (testing mode)

| Parameter | Default | Reason |
|---|---|---|
| `dailyAdQuota` | 1000 | Maximum flexibility for testing |
| `cooldownMinutes` | 30 | Reasonable default; can be set to 0 for rapid testing |
| `minStaySeconds` | 2 | Per user spec |
| `adUrl` | `https://1118000.xyz/` | Placeholder URL |

### Production values (recommended)

When moving to production, change the defaults in `AdsPreferences.kt`:

| Parameter | Production default |
|---|---|
| `dailyAdQuota` | 1–10 |
| `cooldownMinutes` | 30 |
| `minStaySeconds` | 5–10 |
| `adUrl` | (the real ad URL) |

---

## 11. Future Extensions

### Architecturally ready

1. **Per-context ad frequency** — Different ad frequency for library vs search vs browse.
2. **Ad scheduling** — Only show ads during certain hours.
3. **Ad frequency capping** — Max N ads per hour (in addition to daily quota).
4. **Remote config** — Fetch ad settings from a server (override local).
5. **A/B testing** — Different ad configs for different user cohorts.
6. **Per-anime override** — Skip ads for specific anime (e.g., premium users).
7. **Backend ad serving** — Fetch the ad URL from a server instead of a fixed URL.
8. **Ad rotation** — Multiple ad URLs, rotated per view.
9. **Impression tracking** — Track impressions (dialog shown) separately from views (user clicked OK).
10. **Click tracking** — Track whether the user actually engaged with the ad content.

### Implementation path for backend integration

1. Create an `AdSource` interface (similar to `UpdateSource`):
   ```kotlin
   interface AdSource {
       suspend fun fetchAdUrl(): String?
       suspend fun reportView(adId: String): Boolean
   }
   ```
2. Implement `RemoteAdSource` that fetches from a server.
3. Modify `AdManager` to use `AdSource` instead of `AdsPreferences.getAdUrl()`.
4. The UI + state machine remain unchanged.

---

## 12. Testing Guide

### Manual testing flow

1. **Verify ad appears on navigation:**
   - Open the app → tap any anime in Browse/Search/Library
   - The ad dialog should appear (if quota not reached + not in cooldown)

2. **Verify Cancel stays on current page:**
   - When the ad dialog appears, click Cancel
   - You should stay on the current page (no navigation)

3. **Verify OK opens browser + min-stay:**
   - When the ad dialog appears, click OK
   - Browser opens to the ad URL
   - Return immediately (< 2 seconds) → "Please take some time" message
   - Click "Try Again" → ad dialog reappears
   - Click OK again → return after 2+ seconds → ad counted, navigation proceeds

4. **Verify cooldown:**
   - After watching an ad, immediately tap another anime
   - No ad should appear (cooldown active)
   - Wait 30 minutes (or set cooldown to 0 in settings for testing)
   - Tap another anime → ad should appear

5. **Verify daily quota:**
   - Set quota to 1 in Settings → General → Advertising
   - Watch 1 ad
   - Tap another anime → no ad should appear (quota reached)
   - The quota resets at midnight

6. **Verify settings UI:**
   - Go to Settings → General → Advertising
   - Toggle ads off → no ads should appear
   - Change quota/cooldown/min-stay → verify behavior changes
   - Check live stats (Today/Total) update after each ad

### Logcat tags

| Tag | Purpose |
|---|---|
| `AnikutaAds` | Ad gate triggers, accept/cancel, browser open |
| `AnikutaAdManager` | State machine transitions, shouldShowAd decisions |

---

## 13. Troubleshooting

### "Ads not showing"

1. Check Settings → General → Advertising → "Show ads" is ON
2. Check the daily quota isn't reached (view "Today" stat)
3. Check the cooldown hasn't elapsed (wait or set cooldown to 0)
4. Check `AdManager.state` is `Idle` (not stuck in another state)
5. Check logcat for `AnikutaAdManager` logs

### "Ad counted even though I clicked Cancel"

This should not happen. If it does:
- Check that `onAdCancelled()` is being called (not `onAdAccepted()`)
- Check that `pendingAdNavigation` is set to null in `onAdCancelled()`

### "Min-stay check not working"

1. Verify the Activity ON_RESUME lifecycle observer is registered (in `AnikutaRoot`)
2. Check `adAwaitingReturn` is true when returning
3. Check `AdManager.onAdReturn()` is called
4. Verify `minStaySeconds` is > 0 in settings

### "App crashes during ad"

The ad system itself is crash-safe (all errors are caught). If the app crashes:
- Check if the browser intent failed (no browser installed) — `onAdAccepted()` has a try/catch that falls back to cancel
- Check for Compose state issues (the `AdDialog` uses standard Compose patterns)

---

## Appendix: File Reference

| File | Purpose |
|---|---|
| `core/ads/.../AdsPreferences.kt` | User-configurable ad settings |
| `core/ads/.../AdTracker.kt` | On-device ad view tracking |
| `core/ads/.../AdManager.kt` | State machine + business logic |
| `core/ads/.../di/AdsModule.kt` | Koin DI registration |
| `app/.../navigation/AppController.kt` | `withAdGate` + ad callback methods |
| `app/.../navigation/AnikutaRoot.kt` | Lifecycle observer + AdDialog rendering |
| `app/.../navigation/AdDialog.kt` | The ad interstitial dialog UI |
| `feature/settings/.../AdSettingsSection.kt` | Settings UI for ad configuration |
