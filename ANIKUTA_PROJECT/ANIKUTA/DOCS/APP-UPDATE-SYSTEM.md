# ANIKUTA App Self-Update System — Architecture Documentation

> **Module:** `:core:app-update`
> **Status:** Production-ready (v0.2.4)
> **Last updated:** 2026-07-31

## Table of Contents

1. [Overview](#1-overview)
2. [Module Structure](#2-module-structure)
3. [Core Components](#3-core-components)
4. [Update Detection Flow](#4-update-detection-flow)
5. [Download Flow](#5-download-flow)
6. [Install Flow](#6-install-flow)
7. [UI Components](#7-ui-components)
8. [Settings UI](#8-settings-ui)
9. [GitHub Releases Integration](#9-github-releases-integration)
10. [Downloaded APK Lifecycle](#10-downloaded-apk-lifecycle)
11. [Testing Loop](#11-testing-loop)
12. [Future Extensions](#12-future-extensions)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. Overview

The ANIKUTA App Self-Update System allows the app to check for new versions on
GitHub, download the APK, and install it — all from within the app. It uses the
GitHub Releases API as the primary update source, with a pluggable architecture
for adding custom sources in the future.

### Key Features

- **GitHub Releases integration** — Checks the latest release via the GitHub REST API.
- **Automatic check on startup** — Every time the app opens, it checks for updates.
- **Manual check** — The user can manually check in Settings → About → Updates.
- **In-app download** — Downloads the APK with a progress bar (in-button fill animation).
- **System installer** — Opens the downloaded APK via FileProvider + ACTION_VIEW.
- **6-hour dismiss cooldown** — After dismissing an update, it won't reappear for 6 hours (can be disabled for testing).
- **Downloaded versions list** — Shows previously downloaded APKs with Install + Delete buttons.
- **Auto-cleanup** — Old downloaded APKs (versions ≤ current) are deleted on startup.
- **Background download** — The download continues even if the user closes the update sheet.

### Design Philosophy

1. **Pluggable sources** — The `UpdateSource` interface abstracts the update provider. GitHub is the default; custom JSON endpoints, Firebase Remote Config, etc. can be added.
2. **Non-blocking** — All network operations run on `Dispatchers.IO`. The UI is never frozen.
3. **Resilient** — All errors are caught and logged. The app never crashes due to an update check failure.
4. **User-controlled** — The user can disable auto-check, dismiss updates, delete downloaded APKs.
5. **Storage-conscious** — Old APKs are auto-deleted. The user can manually delete any downloaded APK.

---

## 2. Module Structure

```
core/app-update/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml          (empty — pure library)
    └── java/app/confused/anikuta/core/appupdate/
        ├── UpdateModels.kt           (AppUpdateInfo, DownloadProgress, DownloadedApk)
        ├── UpdateSource.kt           (interface for pluggable sources)
        ├── GitHubUpdateSource.kt     (GitHub Releases API implementation)
        ├── AppUpdatePreferences.kt   (settings + downloaded APK records)
        ├── UpdateDownloader.kt       (OkHttp streaming download with progress)
        ├── ApkInstaller.kt           (FileProvider + ACTION_VIEW)
        ├── AppUpdateManager.kt       (orchestrator)
        └── di/
            └── AppUpdateModule.kt    (Koin DI registration)
```

### Dependencies

```kotlin
// core/app-update/build.gradle.kts
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:preferences"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
```

---

## 3. Core Components

### 3.1 UpdateSource (interface)

**File:** `UpdateSource.kt`

```kotlin
interface UpdateSource {
    val id: String
    suspend fun fetchLatestUpdate(currentVersionCode: Long, currentVersionName: String): AppUpdateInfo?
}
```

Pluggable abstraction. Each source checks for updates and returns an `AppUpdateInfo` if a newer version is available, or `null` if not.

### 3.2 GitHubUpdateSource

**File:** `GitHubUpdateSource.kt`

Checks the GitHub Releases API: `GET https://api.github.com/repos/{owner}/{repo}/releases/latest`

**JSON parsing:** Uses `@Serializable` data classes with `@SerialName` annotations for snake_case GitHub API fields:
- `tag_name` → `tagName`
- `published_at` → `publishedAt`
- `browser_download_url` → `browserDownloadUrl`

**Version comparison:** Parses `tag_name` (e.g., `v0.3.0`) → strips `v` prefix → splits by `.` → computes version code: `major * 10000 + minor * 100 + patch` (e.g., `0.3.0` → `300`). Compares against the installed version code.

**APK asset:** Finds the first `.apk` asset in the release's `assets` array.

### 3.3 AppUpdatePreferences

**File:** `AppUpdatePreferences.kt`

| Stored field | Key | Description |
|---|---|---|
| `updateCheckEnabled` | `pref_app_update_enabled` | Auto-check on/off (default: true) |
| `lastCheckTimestamp` | `pref_app_update_last_check` | When the last check ran |
| `lastDismissedVersion` | `pref_app_update_dismissed_version` | Version the user last dismissed |
| `lastDismissedTimestamp` | `pref_app_update_dismissed_timestamp` | When the user dismissed it |
| `downloadedApks` | `pref_app_update_downloaded_apks` | List of downloaded APK records (JSON) |

**Downloaded APK methods:**
- `addDownloadedApk(apk)` — Add a downloaded APK record.
- `removeDownloadedApk(filePath)` — Remove a record (does NOT delete the file).
- `deleteDownloadedApk(filePath)` — Delete the file from disk AND remove the record.
- `isVersionDownloaded(versionName)` — Check if an APK file exists for a version.
- `getDownloadedApkPath(versionName)` — Get the file path for a downloaded version.

**Dismiss cooldown:**
- `recordDismissal(version)` — Records the version + timestamp.
- `isDismissedInCooldown(version)` — Returns true if the version was dismissed < 6 hours ago.
- `clearDismissCooldown()` — Clears the cooldown (for testing).

### 3.4 UpdateDownloader

**File:** `UpdateDownloader.kt`

Downloads the APK via OkHttp with streaming + progress reporting.

**File location:** `context.cacheDir/updates/anikuta-<versionName>.apk`

**Progress reporting:** Emits a `DownloadProgress` every 200ms (or when a chunk completes), containing:
- `bytesDownloaded`, `totalBytes`, `percent`
- `speedBytesPerSec`
- `isComplete`, `error`

**Resilience:**
- Network errors → emits `DownloadProgress.error(message)` + cleans up the partial file.
- The caller can cancel the coroutine to abort the download.

### 3.5 ApkInstaller

**File:** `ApkInstaller.kt`

Launches the system installer via `FileProvider` + `ACTION_VIEW`:

```kotlin
val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
val intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, "application/vnd.android.package-archive")
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
context.startActivity(intent)
```

**Requirements:**
- `REQUEST_INSTALL_PACKAGES` permission in the manifest.
- `FileProvider` declared in the manifest with `res/xml/file_paths.xml`.

### 3.6 AppUpdateManager

**File:** `AppUpdateManager.kt`

The central orchestrator. Exposes:

| State | Type | Description |
|---|---|---|
| `latestUpdate` | `StateFlow<AppUpdateInfo?>` | The most recent update found |
| `downloadProgress` | `StateFlow<DownloadProgress?>` | Live download progress |
| `isChecking` | `StateFlow<Boolean>` | True while a check is running |
| `lastCheckError` | `StateFlow<String?>` | Error message if the last check failed |

**Key methods:**
- `checkForUpdate(): AppUpdateInfo?` (suspend) — Queries all sources, returns the first non-null result.
- `shouldShowDialog(): Boolean` — True if `latestUpdate` is non-null AND not in dismiss cooldown.
- `startDownload()` — Starts the APK download (background coroutine).
- `installDownloadedApk(apkPath?)` — Launches the system installer.
- `dismissUpdate()` — Records the 6-hour dismiss cooldown.
- `isLatestUpdateDownloaded(): Boolean` — Checks if the APK file exists for the current update.
- `getDownloadedApkPath(): String?` — Gets the file path for the downloaded APK.
- `deleteDownloadedApk(filePath): Boolean` — Deletes the file + record.
- `cleanupOldDownloads()` — Deletes APKs for versions ≤ current (called on startup).
- `clearUpdateState()` — Clears `downloadProgress` + `latestUpdate` (called on startup).

---

## 4. Update Detection Flow

```
┌─────────────────┐
│ App opens       │
│ (AnikutaRoot    │
│  LaunchedEffect)│
└────────┬────────┘
         │
         ▼
┌─────────────────────┐
│ cleanupOldDownloads │ (deletes APKs for versions ≤ current)
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│ clearUpdateState    │ (clears downloadProgress + latestUpdate)
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│ checkForUpdate()    │
│ (suspend, IO)       │
└────────┬────────────┘
         │
    ┌────┴────┐
    │         │
  update    null
  found     
    │         │
    ▼         ▼
┌────────┐ ┌──────────────┐
│ clear  │ │ No dialog    │
│ dismiss│ │ (no update)  │
│ cooldown│ └──────────────┘
└───┬────┘
    │
    ▼
┌──────────────┐
│ showUpdate   │
│ Sheet()      │
└──────────────┘
    │
    ▼
┌──────────────────────┐
│ UpdateBottomSheet    │
│ (bottom-up sheet)    │
│ - Bold heading       │
│ - Version + date     │
│ - Changelog card     │
│ - Download + X row   │
└──────────────────────┘
```

### Version comparison logic

```
GitHub tag: "v0.3.0"
  → removePrefix("v") → "0.3.0"
  → split(".") → ["0", "3", "0"]
  → major=0, minor=3, patch=0
  → versionCode = 0*10000 + 3*100 + 0 = 300

Installed versionCode: 6 (from PackageManager)

300 > 6 → update available!
```

---

## 5. Download Flow

```
User clicks "Download"
         │
         ▼
┌──────────────────────────┐
│ AppUpdateManager         │
│ .startDownload()         │
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│ UpdateDownloader         │
│ .download(info)          │
│ (Flow<DownloadProgress>) │
└────────┬─────────────────┘
         │
         │ Every 200ms:
         ▼
┌──────────────────────────┐
│ Emit DownloadProgress    │
│ - bytesDownloaded        │
│ - totalBytes             │
│ - percent                │
│ - speed                  │
└────────┬─────────────────┘
         │
         │ UI updates:
         ▼
┌──────────────────────────┐
│ DownloadProgressButton   │
│ - Fill animation L→R     │
│ - "Downloading X%" text  │
│ - Text color adapts      │
└────────┬─────────────────┘
         │
         │ On completion:
         ▼
┌──────────────────────────┐
│ DownloadProgress.complete│
│ + record in preferences  │
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│ Button becomes           │
│ "Install Update"         │
└──────────────────────────┘
```

### In-button progress bar text color

The text color adapts to the fill level:

```kotlin
val primaryLuminance = 0.299f * primaryColor.red +
                       0.587f * primaryColor.green +
                       0.114f * primaryColor.blue

val textColor = if (percent >= 50) {
    // Center is over the fill — contrast with primary
    if (primaryLuminance < 0.5f) onPrimaryColor  // dark primary → white text
    else onSurfaceColor                            // light primary → dark text
} else {
    // Center is over the light background — dark text
    onSurfaceColor
}
```

---

## 6. Install Flow

```
User clicks "Install Update"
         │
         ▼
┌──────────────────────────────────┐
│ AppUpdateManager                 │
│ .installDownloadedApk(path)      │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│ ApkInstaller                     │
│ .installApk(apkPath)             │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│ FileProvider.getUriForFile()     │
│ + ACTION_VIEW intent             │
│ + FLAG_GRANT_READ_URI_PERMISSION │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│ System installer dialog opens    │
│ "Install ANIKUTA?"               │
│ [Cancel] [Install]               │
└────────┬─────────────────────────┘
         │
         │ User clicks Install:
         ▼
┌──────────────────────────────────┐
│ App is updated (or replaced)     │
│ App may restart                  │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│ On next startup:                 │
│ cleanupOldDownloads() deletes    │
│ the just-installed APK file      │
│ clearUpdateState() resets UI     │
└──────────────────────────────────┘
```

### Post-install cleanup

After the user installs an update, the app restarts with the new version. On the next startup:
1. `cleanupOldDownloads()` runs — deletes any APK whose version name matches the installed version (the "just installed" case).
2. `clearUpdateState()` runs — clears `downloadProgress` + `latestUpdate` so the Install button doesn't persist.
3. `checkForUpdate()` runs — if a newer version is still available (e.g., the testing loop), the update sheet appears again with the Download button (not Install).

---

## 7. UI Components

### UpdateBottomSheet

**File:** `app/src/main/java/app/confused/anikuta/navigation/UpdateBottomSheet.kt`

A bottom-up `ModalBottomSheet` with `dragHandle = null` (per design language).

**Layout (top to bottom):**

1. **Heading** — "New Update Available" (26sp, ExtraBold, primary color)
2. **Version + date** — "v0.3.0 · Jul 31, 2026" (15sp ExtraBold + 13sp onSurfaceVariant)
3. **"What's New" label** — (14sp ExtraBold, primary color)
4. **Changelog card** — Scrollable Surface with the release notes (13sp, onSurface, 19sp line height)
5. **Bottom row** — Download button (left, weight 1f) + X button (right, 52dp square)

**Download button states:**

| State | Appearance | Trigger |
|---|---|---|
| Not downloaded | "Download (58.8 MB)" with download icon | `progress == null && !isAlreadyDownloaded` |
| Downloading | In-button progress bar: fill L→R + "Downloading X%" | `progress` is non-null, not complete, no error |
| Downloaded | "Install Update" with install icon | `isAlreadyDownloaded` OR `progress.isComplete` |
| Error | "Retry" (red button) | `progress.error != null` |

**X button:** Closes the sheet (calls `dismissUpdateSheet()`). The download continues in the background if it was started.

### Rendering in AnikutaRoot

```kotlin
// In AppOverlays()
if (appController.showUpdateDialog) {
    UpdateBottomSheet(appController)
}
```

---

## 8. Settings UI

### AboutScreen

**File:** `feature/settings/src/main/java/app/confused/anikuta/feature/settings/AboutScreen.kt`

Reached from Settings → About & Updates.

**Sections:**

1. **App version** — "ANIKUTA" + "Version 0.2.4 (6)"
2. **Updates**:
   - Auto-check toggle (on/off)
   - "Check for updates" button (manual check → triggers `onUpdateFound` callback → shows bottom sheet)
   - Error card (if last check failed)
3. **Downloaded versions** (only shows if there are valid APK files on disk):
   - Each row: cloud icon + version + size + date + Install button + Delete button (trash icon)

**Manual check behavior:**
- Click "Check for updates" → `checkForUpdate()` runs.
- If update found → `onUpdateFound()` callback → `AppController.showUpdateSheet()` → bottom sheet appears.
- If no update → nothing happens (the button just stops spinning).
- If error → red error card appears.

**Downloaded versions list:**
- Only shows APKs where `File(filePath).exists()` is true (stale entries are filtered).
- Install button → `installDownloadedApk(filePath)` → system installer.
- Delete button → `deleteDownloadedApk(filePath)` → deletes file + record.

---

## 9. GitHub Releases Integration

### How the app checks for updates

```
GET https://api.github.com/repos/testplay-byte/ANI_KUTA_NEW/releases/latest

Headers:
  Accept: application/vnd.github+json
  User-Agent: ANIKUTA-App-Update-Checker
```

### Required release structure

The GitHub release must have:
- A `tag_name` starting with `v` followed by a semantic version (e.g., `v0.3.0`).
- At least one `.apk` asset attached.
- `draft = false` and `prerelease = false` (otherwise `/releases/latest` won't return it).

### Creating a release (for maintainers)

```bash
# 1. Build the APK via CI
# 2. Download the APK artifact
# 3. Create the release:
curl -X POST \
  -H "Authorization: token <PAT>" \
  -H "Content-Type: application/json" \
  -d '{
    "tag_name": "v0.3.0",
    "target_commitish": "main",
    "name": "v0.3.0 — Release Name",
    "body": "Release notes here",
    "draft": false,
    "prerelease": false
  }' \
  "https://api.github.com/repos/testplay-byte/ANI_KUTA_NEW/releases"

# 4. Upload the APK:
curl -X POST \
  -H "Authorization: token <PAT>" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @app-arm64-v8a-debug.apk \
  "https://uploads.github.com/repos/testplay-byte/ANI_KUTA_NEW/releases/<RELEASE_ID>/assets?name=anikuta-v0.3.0-arm64-v8a.apk"
```

### Version code derivation

The `GitHubUpdateSource` derives the version code from the tag name:

```
"v0.3.0" → strip "v" → "0.3.0" → split "." → [0, 3, 0]
→ versionCode = 0 * 10000 + 3 * 100 + 0 = 300
```

This is compared against the installed app's `versionCode` (from `PackageManager`).

**Important:** The GitHub release tag version code (300) is independent of the APK's actual `versionCode` (e.g., 6). This means a release tagged `v0.3.0` will always be detected as an update for an APK with `versionCode = 6`, even if the APK is the same build. This is used for the testing loop (see §11).

---

## 10. Downloaded APK Lifecycle

### States

```
┌─────────────┐  download   ┌──────────────┐  complete  ┌─────────────┐
│ Not         │ ──────────► │ Downloading  │ ─────────► │ Downloaded  │
│ downloaded  │             │ (progress)   │            │ (Install)   │
└─────────────┘             └──────────────┘            └──────┬──────┘
                                 │                              │
                            error│                              │ install
                                 ▼                              ▼
                           ┌──────────┐                  ┌──────────────┐
                           │ Error    │                  │ System       │
                           │ (Retry)  │                  │ installer    │
                           └──────────┘                  └──────┬───────┘
                                                                │
                                                         next startup
                                                                │
                                                                ▼
                                                  ┌─────────────────────────┐
                                                  │ cleanupOldDownloads()   │
                                                  │ deletes the APK file    │
                                                  │ + clearUpdateState()    │
                                                  │ resets the UI           │
                                                  └─────────────────────────┘
```

### File location

Downloaded APKs are stored in:
```
context.cacheDir/updates/anikuta-<versionName>.apk
```

Example: `/data/user/0/app.confused.anikuta.dev/cache/updates/anikuta-0.3.0.apk`

### FileProvider configuration

The `FileProvider` is declared in `app/src/main/AndroidManifest.xml`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

And `app/src/main/res/xml/file_paths.xml`:

```xml
<paths>
    <cache-path name="updates" path="updates/" />
    <cache-path name="cache" path="." />
</paths>
```

### Auto-cleanup logic

`cleanupOldDownloads()` runs on every app startup. It deletes any downloaded APK where:
1. The APK's parsed version code ≤ the installed version code, OR
2. The APK's version name matches the installed version name (the "just installed" case)

This prevents storage bloat from accumulated old APK files.

---

## 11. Testing Loop

The update system supports a repeatable testing loop:

### Setup

1. Build version X (e.g., v0.2.4, versionCode 6).
2. Publish a GitHub release tagged `v0.3.0` (versionCode 300) with the same APK.
3. The app's `checkForUpdate()` will detect `v0.3.0` (300 > 6) every time it runs.

### Loop

1. **Install v0.2.4 manually** (one-time).
2. **Open the app** → update bottom sheet appears immediately (no 6h cooldown during testing).
3. **Click Download** → in-button progress bar → "Install Update" button.
4. **Click Install** → system installer opens → install.
5. **Open the app** → the old APK is cleaned up → `clearUpdateState()` resets the UI → `checkForUpdate()` finds v0.3.0 again → update sheet appears with Download button.
6. **Repeat from step 3.**

### How the cooldown is disabled for testing

In `AnikutaRoot.kt`:

```kotlin
if (update != null) {
    // Clear the dismiss cooldown so the dialog ALWAYS shows on startup
    GlobalContext.get().get<AppUpdatePreferences>().clearDismissCooldown()
    appController.showUpdateSheet()
}
```

This ensures the update dialog appears every time the app opens, regardless of whether the user previously dismissed it.

### Stopping the loop

To stop the testing loop:
1. Remove the `clearDismissCooldown()` call from `AnikutaRoot.kt`.
2. Re-enable the 6-hour dismiss cooldown.
3. Delete the `v0.3.0` release (or mark it as a prerelease).

---

## 12. Future Extensions

### Adding a new update source

1. Implement the `UpdateSource` interface:
   ```kotlin
   class CustomJsonUpdateSource(
       private val client: OkHttpClient,
       private val jsonUrl: String,
   ) : UpdateSource {
       override val id = "custom"
       override suspend fun fetchLatestUpdate(...): AppUpdateInfo? {
           // Fetch JSON from jsonUrl, parse, return AppUpdateInfo
       }
   }
   ```

2. Register in `AppUpdateModule.kt`:
   ```kotlin
   single<UpdateSource>(named("custom")) { CustomJsonUpdateSource(...) }
   single<List<UpdateSource>> { listOf(get(named("github")), get(named("custom"))) }
   ```

3. The `AppUpdateManager` will automatically query both sources (priority order).

### Other future extensions

1. **WorkManager periodic check** — Background periodic update checks (not just on app open).
2. **Delta updates** — Download only the changed parts (requires server support).
3. **Rollout phasing** — Gradual rollout to a percentage of users.
4. **Update notifications** — Show a notification when an update is available (even if the user dismissed the dialog).
5. **Automatic install** — Auto-install the update without user confirmation (requires `REQUEST_INSTALL_PACKAGES` + system-level permission).
6. **Download over Wi-Fi only** — Check network type before downloading.
7. **Resume interrupted downloads** — Support HTTP Range requests for resuming.
8. **Multiple APK flavors** — Different APKs for different ABIs (arm64, x86, etc.).

---

## 13. Troubleshooting

### "Update not detected"

1. Verify the GitHub release exists: `curl https://api.github.com/repos/testplay-byte/ANI_KUTA_NEW/releases/latest`
2. Verify the release has `tag_name` starting with `v` (e.g., `v0.3.0`).
3. Verify the release has an `.apk` asset.
4. Verify the release is NOT a draft and NOT a prerelease.
5. Check logcat for `AnikutaGitHubUpdate` and `AnikutaAppUpdateManager` logs.
6. Verify the `@SerialName` annotations are present in `GitHubUpdateSource.kt` (this was a bug in v0.2.0 that was fixed in v0.2.1+).

### "Download fails"

1. Check the APK download URL is accessible (HTTP 200).
2. Check network connectivity.
3. Check logcat for `AnikutaUpdateDownloader` errors.
4. Verify the OkHttp client timeouts (30s connect, 60s read).

### "Install fails"

1. Verify `REQUEST_INSTALL_PACKAGES` permission is in the manifest.
2. Verify the `FileProvider` is declared + `file_paths.xml` includes the `updates/` cache path.
3. Check if the user needs to grant "Install unknown apps" permission in system settings.
4. Check logcat for `AnikutaApkInstaller` errors.

### "Install button persists after update"

This was fixed in v0.2.4. If it still happens:
1. Verify `cleanupOldDownloads()` is called on startup (in `AnikutaRoot.kt`).
2. Verify `clearUpdateState()` is called on startup.
3. Check if the downloaded APK's version name matches the installed version name (it should be cleaned up).

### "Old APKs accumulate"

1. Verify `cleanupOldDownloads()` runs on startup.
2. Check the version code comparison logic — the APK's parsed version code must be ≤ the installed version code.
3. The user can manually delete APKs in Settings → About → Downloaded versions.

---

## Appendix: File Reference

| File | Purpose |
|---|---|
| `core/app-update/.../UpdateModels.kt` | AppUpdateInfo, DownloadProgress, DownloadedApk data classes |
| `core/app-update/.../UpdateSource.kt` | Pluggable source interface |
| `core/app-update/.../GitHubUpdateSource.kt` | GitHub Releases API implementation |
| `core/app-update/.../AppUpdatePreferences.kt` | Settings + downloaded APK records |
| `core/app-update/.../UpdateDownloader.kt` | OkHttp streaming download with progress |
| `core/app-update/.../ApkInstaller.kt` | FileProvider + ACTION_VIEW system installer |
| `core/app-update/.../AppUpdateManager.kt` | Central orchestrator |
| `core/app-update/.../di/AppUpdateModule.kt` | Koin DI registration |
| `app/.../navigation/UpdateBottomSheet.kt` | The update bottom sheet UI |
| `app/.../navigation/AnikutaRoot.kt` | Startup check + sheet rendering |
| `app/.../navigation/AppController.kt` | `showUpdateSheet()`, `dismissUpdateSheet()` |
| `app/src/main/res/xml/file_paths.xml` | FileProvider paths config |
| `feature/settings/.../AboutScreen.kt` | About & Updates settings screen |
