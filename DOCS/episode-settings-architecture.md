# Episode Settings Architecture

**Module:** `:feature:episode-settings`
**Status:** Implemented (commit `c627c2a` on `feature/episode-metadata`)
**Last updated:** 2026-07-22

---

## 1. Overview

The episode-settings subsystem controls how the anime-details episode list looks
and how its metadata is fetched. It is a 4-screen full-page flow (NOT bottom
sheets) reached from **More → Settings → "Episode settings"**.

```
More tab
  └─ Settings screen
       └─ "Episode settings" row  (ONE entry — was 2 rows that opened sheets)
            └─ EpisodeSettingsHubScreen       (live preview + 3 links)
                 ├─ EpisodeDisplaySettingsScreen   (show/hide toggles + title lines)
                 ├─ EpisodeLayoutSettingsScreen    (position knobs)
                 └─ EpisodeMetadataSettingsScreen  (fetch toggles)
```

Each screen is a **full page** rendered via `SettingsSubpageScaffold`
(back arrow + title + content). The Display + Layout + Hub screens have a
**sticky live preview** at the top (`EpisodeRowPreview`) that reflects the
current settings using dummy data; the preview is non-scrolling so the user
sees the effect of every toggle immediately.

---

## 2. Module layout

```
feature/episode-settings/
├── build.gradle.kts                      (anikuta.library.compose + deps)
└── src/main/
    ├── AndroidManifest.xml               (empty, namespace in Gradle)
    └── java/app/confused/anikuta/feature/episodesettings/
        ├── EpisodeSettingsPage.kt         (sealed interface: Hub/Display/Layout/Metadata)
        ├── EpisodeSettingsHubScreen.kt    (hub: preview + 3 links)
        ├── EpisodeDisplaySettingsScreen.kt (show/hide Switch rows + title-lines segmented)
        ├── EpisodeLayoutSettingsScreen.kt  (position knobs — segmented rows)
        ├── EpisodeMetadataSettingsScreen.kt (master Switch + per-field Switches)
        ├── EpisodeRowPreview.kt            (shared live-preview composable, dummy data)
        ├── RememberEpisodeDisplayPrefs.kt  (reactive EpisodeDisplayPreferences → EpisodeDisplayPrefs snapshot)
        ├── SettingsScaffold.kt             (SettingsSubpageScaffold: back arrow + title)
        └── SettingsComponents.kt           (SwitchSettingsRow, ClickableSettingsRow,
                                             LabeledSegmentedRow, SettingsGroupCard, InGroupDivider)
```

### Dependencies
- `core/designsystem` — `RobotoFamily`, (CustomToggle not used here — we use Material3 Switch)
- `core/preferences` — `Preference.changes()` for reactive reads
- `core/episode-metadata` — `EpisodeMetadataPreferences` (for the Metadata screen)
- `feature/anime-details` — `EpisodeDisplayPreferences` + `EpisodeDisplayPrefs` (shared data class)
- Koin (`koin.androidx.compose` for `koinInject()`)

---

## 3. Navigation

The app uses a **hand-rolled state-machine** in `MainActivity.kt` (NOT Voyager,
NOT Compose Nav). A single state var drives the whole flow:

```kotlin
var episodeSettingsPage by remember {
    mutableStateOf<EpisodeSettingsPage?>(null)  // null = not in the flow
}
```

The `when` dispatch block (in `AnikutaApp`) renders the appropriate screen:

```kotlin
episodeSettingsPage != null -> {
    when (val page = episodeSettingsPage!!) {
        EpisodeSettingsPage.Hub     -> EpisodeSettingsHubScreen(onBack = { episodeSettingsPage = null }, onOpenDisplay = { episodeSettingsPage = Display }, ...)
        EpisodeSettingsPage.Display -> EpisodeDisplaySettingsScreen(onBack = { episodeSettingsPage = Hub })
        EpisodeSettingsPage.Layout  -> EpisodeLayoutSettingsScreen(onBack = { episodeSettingsPage = Hub })
        EpisodeSettingsPage.Metadata -> EpisodeMetadataSettingsScreen(onBack = { episodeSettingsPage = Hub })
    }
}
```

`BackHandler` pops sub-page → Hub; Hub → `null` (exits the flow).

---

## 4. The episode row (in `:feature:anime-details`)

The actual episode row lives in `feature/anime-details/.../EpisodesSection.kt`
(`EpisodeRow` composable). It uses a **fixed two-section layout** (per user spec):

```
┌───────────────────────────────────────────────────┐
│  TOP SECTION (height driven by thumbnail)          │
│  ┌──────────┐  ┌─ Title (bg) ──────────────────┐  │
│  │          │  │ EP 3  The Dragon's Labyrinth   │  │
│  │ Thumbnail│  └────────────────────────────────┘  │
│  │  EP 3   │  ┌─ Date + Audio (bg) ────────────┐  │
│  │          │  │ Mar 15, 2024  SUB•DUB          │  │
│  └──────────┘  └────────────────────────────────┘  │
├───────────────────────────────────────────────────┤
│  BOTTOM SECTION                                    │
│  ┌─ Synopsis (bg) ──────────────────────────────┐  │
│  │ A young adventurer discovers a hidden...     │  │
│  └──────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────┘
```

- The top section's right side is divided into TWO equal sub-sections:
  title (top) + date/audio (bottom). Height is driven by the thumbnail.
- Each element (title, date+audio, synopsis) gets a **dedicated background
  container** (toggleable): title = `surfaceContainerHigh`, meta = `surfaceContainer`,
  synopsis = `surfaceContainerLow`.
- **No alternating zebra-stripe** — all rows use `surfaceVariant@0.2` (single
  lighter shade).
- Layout position prefs are **dormant** — the row uses this single fixed view.
  Only thumbnail SIZE is active.

### 4.1 Episode number badge (overlay)
- `Surface(RoundedCornerShape(6.dp), Color.Black.copy(0.7f))` at TopStart of
  the thumbnail, 4dp outer pad.
- Text `"EP N"` in RobotoFamily 11sp Bold **White**, tight 6dp/1dp inner pad,
  `lineHeight = 13.sp`.
- Circle fallback (no thumbnail): 40dp `surfaceVariant` disc, bare number.

### 4.2 Title
- Inside a `surfaceContainerHigh` background container (toggleable via
  `showTitleBackground`). When toggled off, renders as plain text.
- `maxLines = titleMaxLines` (default **1** — "force single line"), configurable 1/2.
- `FontWeight.Bold`, `onSurface`, `RobotoFamily`, 14sp, 8dp/4dp inner pad.

### 4.3 Date + Audio (shared meta background)
- Inside a `surfaceContainer` background container (toggleable via
  `showMetaBackground`). Holds the date pill + audio pills in a Row.
- **Date pill**: `outlineVariant` surface, 10sp Medium, `onSurfaceVariant`,
  tight 6dp/1dp pad, `lineHeight = 12.sp`. Format: `"MMM d, yyyy"`.
- Source: `metadata.airDate` (epoch seconds → ×1000) OR `episode.date_upload`.

### 4.4 Audio pills (SUB/DUB/HSUB)
- **Single** `outlineVariant` surface holding all detected versions.
- **ALWAYS uses full names**: "SUB", "DUB", "HSUB" separated by 3dp dots →
  "SUB•DUB" (per user request — not short letters).
- Tight 6dp/1dp pad, 10sp SemiBold, `lineHeight = 12.sp`.
- Derived from `parseAudioAvailability(episode.scanlator, episode.name)` — checks BOTH
  (many extensions put the token in the episode name since `scanlator` is rarely set).
- HSUB is checked before SUB (since "HSUB" contains "SUB" as a substring).

### 4.5 Thumbnail
- `metadata.thumbnailUrl ?: episode.preview_url` (fallback so thumbnails render
  even when metadata is missing or disabled).
- Sizes: small (100×56), medium (120×68), large (160×90).
- `clip(RoundedCornerShape(10.dp))`, `ContentScale.Crop`.

### 4.6 Synopsis
- Inside a `surfaceContainerLow` background container (toggleable via
  `showSynopsisBackground`). When toggled off, renders as plain text.
- `maxLines = synopsisMaxLines` (default 3), `Ellipsis`, 12sp Normal.

### 4.7 Watched effect
- `Modifier.watchedEpisodeEffect(isWatched)` → grayscale (RenderEffect, API 31+) + alpha 0.55f.

---

## 5. The critical wiring fix

**Before this work**, `EpisodeDisplayPreferences` was DISCONNECTED from `EpisodeRow`:
- `EpisodesSection` (public composable) had no `displayPrefs` parameter.
- Inner `EpisodeList` accepted `displayPrefs: EpisodeDisplayPrefs? = null` but its
  caller never passed anything.
- `EpisodeRow` always fell back to the `EpisodeDisplayPrefs` data-class defaults.
- Result: **settings changes only affected the settings preview, NOT the actual
  rendered list.** This is why the user's date/title settings "didn't work."

**The fix:**
- `EpisodesSection` now injects `EpisodeDisplayPreferences` via `koinInject()` and
  reads it reactively (`Preference.changes().collectAsState()`) into an
  `EpisodeDisplayPrefs` snapshot via `rememberEpisodeDisplaySnapshot()`.
- The snapshot is passed down: `EpisodesSection` → `EpisodeList` → `EpisodeRow`.
- The episode list updates **instantly** when a setting changes (no recompose delay).
- The `EpisodeDisplayPrefs` data-class defaults were aligned with the
  `EpisodeDisplayPreferences` defaults (`showDates`/`showAudioPills` were `false`
  in the data class but `true` in prefs — now both `true`; `titleMaxLines` 2 → 1).

---

## 6. Preference keys

All episode-display prefs (in `EpisodeDisplayPreferences`, package `feature.animedetails`):

| Key | Type | Default | UI |
|---|---|---|---|
| `pref_ep_show_number` | Boolean | `true` | Display screen Switch |
| `pref_ep_show_titles` | Boolean | `true` | Display screen Switch |
| `pref_ep_show_summaries` | Boolean | `true` | Display screen Switch |
| `pref_ep_show_thumbnails` | Boolean | `true` | Display screen Switch |
| `pref_ep_show_dates` | Boolean | `true` | Display screen Switch |
| `pref_ep_show_audio_pills` | Boolean | `true` | Display screen Switch |
| `pref_ep_title_lines` | Int | `1` | Display screen Segmented (1/2) |
| `pref_ep_thumb_pos` | String | `"left"` | Layout screen Segmented (Left/Right) |
| `pref_ep_title_pos` | String | `"right"` | Layout screen Segmented (Right/Below) |
| `pref_ep_synopsis_pos` | String | `"below"` | Layout screen Segmented (Right/Below) |
| `pref_ep_date_pos` | String | `"right_below_synopsis"` | Layout screen Segmented (Above/Below/Full) |
| `pref_ep_num_pos` | String | `"overlay"` | Layout screen Segmented (Overlay/Badge) |
| `pref_ep_thumb_size` | String | `"medium"` | Layout screen Segmented (S/M/L) |
| `pref_ep_synopsis_lines` | Int | `3` | (not yet in UI — reserved) |
| `pref_ep_show_title_bg` | Boolean | `true` | Display screen Switch (Backgrounds group) |
| `pref_ep_show_meta_bg` | Boolean | `true` | Display screen Switch (Backgrounds group) |
| `pref_ep_show_synopsis_bg` | Boolean | `true` | Display screen Switch (Backgrounds group) |

Episode-metadata prefs (in `EpisodeMetadataPreferences`, package `core.episodemetadata`):

| Key | Type | Default | UI |
|---|---|---|---|
| `pref_ep_metadata_enabled` | Boolean | `true` | Metadata screen master Switch |
| `pref_ep_metadata_thumbnails` | Boolean | `true` | Metadata screen Switch (hidden when master off) |
| `pref_ep_metadata_titles` | Boolean | `true` | Metadata screen Switch (hidden when master off) |
| `pref_ep_metadata_summaries` | Boolean | `true` | Metadata screen Switch (hidden when master off) |
| `pref_ep_metadata_airdates` | Boolean | `true` | Metadata screen Switch (hidden when master off) |

---

## 7. Toggle widget policy

Per user feedback ("I really hate the on/off buttons you created"), the episode
settings use the **Material3 `Switch`** (inside `SwitchSettingsRow`) for ALL
boolean toggles. The custom on/off pill toggle (`CustomToggle` in `core/designsystem`)
is NOT used in this subsystem. `CustomToggle` remains available for other screens
that prefer it, but episode settings are Switch-only.

---

## 8. Build notes

- `:feature:episode-settings` uses `id("anikuta.library.compose")` (Compose-enabled).
- `:core:episode-metadata` was reverted to `id("anikuta.library")` (pure data —
  no Compose) after the `MetadataSettingsSheet` moved out. It has NO designsystem dep.
- `:feature:anime-details` already had `koin.androidx.compose` — needed for
  `koinInject<EpisodeDisplayPreferences>()` inside `EpisodesSection`.
- No Coil dep in `:feature:episode-settings` (the preview uses a gradient Box,
  not a network image).

---

## 9. Future work (not yet implemented)

- Persisting episode metadata to the database (currently in-memory cache only)
  so stale data shows on refresh failure.
- Per-source metadata toggles (Jikan / Anikage / AniList individually).
- "Watched episode appearance" settings (grayscale / blur / both / none) —
  ported from the OLD project but not yet in the new Layout screen.
- Title 3-line option (currently 1 or 2 only).
- Sub/dub availability from persisted video-track data (after first watch).
