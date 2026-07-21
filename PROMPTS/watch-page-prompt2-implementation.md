# Prompt 2 — Watch Page & Player: Full Implementation

You are the ANIKUTA watch page + player implementation agent. You have completed Prompt 1 (setup + analysis). Now implement the full watch page and video player.

**This is a large task.** Follow the to-do list at the end strictly. Build via GitHub Actions after each major milestone. Do NOT skip verification steps.

---

## 0. GitHub Access

```
Repo: https://github.com/testplay-byte/ANI_KUTA_NEW
PAT:  <provided-separately-by-user>
```

Configure git:
```bash
git remote set-url origin "https://x-access-token:<PAT>@github.com/testplay-byte/ANI_KUTA_NEW.git"
git config user.email "agent@anikuta.dev"
git config user.name "ANIKUTA Watch Page Agent"
```

**CI workflow**: `.github/workflows/ci.yml` triggers on push to `main`. It builds the debug APK and uploads it as an artifact. Monitor builds via:
```bash
curl -s -H "Authorization: token <PAT>" "https://api.github.com/repos/testplay-byte/ANI_KUTA_NEW/actions/runs?per_page=1"
```

**CRITICAL — CI concurrency**: The workflow has `cancel-in-progress: true`. This means if you push twice quickly, the first build is cancelled. **Always wait for a build to finish before pushing again.** Do NOT push documentation-only commits while a code build is running.

**ntfy notifications**: Send to `https://ntfy.sh/TASKISDONE` after each major milestone and after the final APK builds.

---

## 1. Architecture Overview

You are building three modules + wiring them into the app:

```
┌──────────────────────────────────────────────────────────────────┐
│ :feature:watch (NEW — the watch page)                            │
│  • WatchScreen.kt — YouTube-style: sticky MPV on top,           │
│    episode description + episode list below (scrolls as one)    │
│  • WatchViewModel.kt — state holder (episode list, current ep,  │
│    servers, audio versions, qualities, watch progress)           │
│  • components/ — EpisodeDescriptionSection, WatchEpisodeList,   │
│    WatchTopBar (floating pill, hides on scroll)                  │
├──────────────────────────────────────────────────────────────────┤
│ :feature:player (NEW — fullscreen player)                        │
│  • FullscreenPlayerScreen.kt — landscape fullscreen overlay     │
│  • FullscreenControls.kt — lock, title, server/sub/audio/quality│
│    /more icons, replay10/play-pause/forward10, seekbar, speed,  │
│    rotate, skip, minimize, PiP                                  │
│  • FullscreenGestureHandler.kt — tap, double-tap seek,          │
│    horizontal seek, vertical brightness/volume, pinch zoom      │
├──────────────────────────────────────────────────────────────────┤
│ :core:player (NEW — MPV wrapper, shared by both)                 │
│  • AnikutaMPVView.kt — extends is.xyz.mpv.BaseMPVView           │
│  • PlayerObserver.kt — MPV event bridge                         │
│  • MpvConfigManager.kt — mpv.conf + input.conf                  │
│  • PlayerUtils.kt — URL resolution (content:// → fd://)         │
│  • PlayerPreferences.kt — all player settings                   │
│  • PlayerEpisodePreferences.kt — episode list display prefs     │
│  • WatchProgressStore.kt — watch progress (PreferenceStore JSON)│
│  • PlaybackStateStore.kt — resume state (video URL + tracks)    │
│  • controls/ — MinimizedControls, EpisodeSwitchingOverlay,      │
│    ServerVersionDropdowns, sheets (Subtitle, Audio, Quality,    │
│    Speed, Server, More)                                          │
│  • subtitles/ — SubtitleTrackFormatter (raw names → clean chips)│
│    SubtitleSettingsPanel, NumericKeypad, ColorPickerDialog      │
│  • res/layout/mpv_view.xml — REQUIRED for BaseMPVView inflation │
└──────────────────────────────────────────────────────────────────┘
```

### Modular design rules (CRITICAL):

1. **`:core:player`** owns the MPV view, all player state, all controls. It does NOT depend on `:feature:watch` or `:feature:player`. Both feature modules depend on `:core:player`.

2. **`:feature:watch`** owns the YouTube-style watch page (portrait). It uses `:core:player`'s `AnikutaMPVView` + `MinimizedControls`. It does NOT own fullscreen — it delegates to `:feature:player` when the user taps maximize.

3. **`:feature:player`** owns the fullscreen landscape experience. It uses `:core:player`'s `AnikutaMPVView` (same instance — never recreate) + `FullscreenControls`. It's triggered by a callback from `:feature:watch`.

4. **The MPV view is NEVER recreated.** When switching minimized ↔ fullscreen, the same `AnikutaMPVView` instance is re-parented. Use `AndroidView.factory` with a local `mpvView` cache (check existing first, reuse if non-null).

5. **Episode description is a separate module/section.** `EpisodeDescriptionSection` composable fetches metadata from `EpisodeMetadataRepository` (if wired) and falls back to `SEpisode.summary` / `SEpisode.name`. It's a standalone component that can be placed in the watch page or reused elsewhere.

6. **Episode list is a separate module/section.** `WatchEpisodeList` composable renders the list with `PlayerEpisodePreferences` (separate from the detail page's episode prefs). It's standalone and highly customizable.

7. **Subtitle track formatting is a separate module.** `SubtitleTrackFormatter` takes raw MPV track names (which can be hash-like filenames, e.g. `cuhcdrfytgvhjue6t576buy57g4e.vtt`) and formats them into clean, human-readable names with language codes. This is its own file with its own tests-mentally-verify logic.

---

## 2. MPV Integration (CRITICAL — get this right or nothing works)

### 2.1 Add MPV dependencies to `:core:player`

File: `ANIKUTA_PROJECT/ANIKUTA/core/player/build.gradle.kts`

```kotlin
plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.core.player"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.database)
    implementation(projects.core.preferences)
    implementation(projects.core.sourceApi)

    // MPV — the video player (ADR-025)
    implementation("com.github.aniyomiorg:aniyomi-mpv-lib:1.18.n")
    // FFmpeg — libmpv.so is dynamically linked against it
    implementation("com.github.jmir1:ffmpeg-kit:1.18")
    implementation("com.arthenica:smart-exception-java:0.2.1")
    // Seeker — Compose seekbar library (for MinimalSeekbar)
    implementation("io.github.2307vivek:seeker:1.2.2")
    // NanoHTTPD — localhost proxy for proxied video URLs
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    // Media session — for background media controls
    implementation("androidx.media:media:1.7.0")
    // TrueType parser — for subtitle font parsing
    implementation("io.github.yubyf:truetypeparser-light:2.1.4")

    // Standard
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}
```

### 2.2 Enable ABI splits in `app/build.gradle.kts`

File: `ANIKUTA_PROJECT/ANIKUTA/app/build.gradle.kts`

Find the `splits.abi` block (currently `isEnable = false`) and change to:
```kotlin
splits {
    abi {
        isEnable = true
        reset()
        include("arm64-v8a")  // ADR-032: arm64-v8a only for now
        isUniversalApk = false
    }
}
```

### 2.3 Create `mpv_view.xml`

File: `ANIKUTA_PROJECT/ANIKUTA/core/player/src/main/res/layout/mpv_view.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<app.confused.anikuta.core.player.AnikutaMPVView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

**CRITICAL**: `BaseMPVView` requires a real `XmlBlock$Parser`-backed `AttributeSet`. Compose's `AndroidView` factory gives no XML. Faking one with `Xml.newPullParser()` crashes with `ClassCastException`. You MUST inflate from this XML layout.

### 2.4 Port `AnikutaMPVView.kt`

Port from `OLD_ANIKUTA/.../player/AnikutaMPVView.kt` (375 lines). Key points:
- Extends `is.xyz.mpv.BaseMPVView`
- `initOptions(vo: String)` — sets hwdec, profile=fast, demuxer-max-bytes=256MB, keep-open, tls-verify, subtitle styling
- `observeProperties()` — observes time-pos, duration, volume, track-list, pause, paused-for-cache, seeking, eof-reached, hwdec-current, sid, aid
- `loadTracks(): Pair<List<VideoTrack>, List<VideoTrack>>` — returns (subs, audio) with "Off" (id=-1) prepended. Uses the ugly-filename detection logic (see §2.5)
- `sid` / `aid` properties — use `getPropertyString` (NOT `getPropertyInt` — returns "unsupported format")
- `applySubtitlePreferences()` / `applySubtitlePreferencesInit()` — applies subtitle styling from `PlayerPreferences`
- Change package from `app.anikuta.player` to `app.confused.anikuta.core.player`

### 2.5 Subtitle track name formatting (SEPARATE MODULE)

File: `ANIKUTA_PROJECT/ANIKUTA/core/player/src/main/java/app/confused/anikuta/core/player/subtitles/SubtitleTrackFormatter.kt`

Port the ugly-filename detection from the old `AnikutaMPVView.loadTracks()`:
```kotlin
object SubtitleTrackFormatter {
    fun formatTrackName(id: Int, title: String, lang: String): String {
        val isUglyFilename = title.isNotBlank() && (
            title.endsWith(".vtt", ignoreCase = true) ||
            title.endsWith(".srt", ignoreCase = true) ||
            title.endsWith(".ass", ignoreCase = true) ||
            title.endsWith(".ssa", ignoreCase = true) ||
            (title.length > 20 && title.none { it == ' ' })  // hash-like
        )
        val displayTitle = if (isUglyFilename) "" else title
        return when {
            displayTitle.isNotBlank() && lang.isNotBlank() -> "$displayTitle ($lang)"
            displayTitle.isNotBlank() -> displayTitle
            lang.isNotBlank() -> lang
            else -> "Track $id"
        }
    }
}
```

### 2.6 Port `PlayerObserver.kt`, `MpvConfigManager.kt`, `PlayerUtils.kt`

Port from old project, change packages. These are straightforward ports.

### 2.7 MPV asset copying (CRITICAL)

`MpvConfigManager` or `PlayerUtils` must copy `cacert.pem` + `subfont.ttf` from APK assets to `ctx.filesDir/mpv/` **ROOT** (NOT `fonts/` subdirectory — this was a subtitle rendering bug that took 15 builds to fix).

Create:
- `ANIKUTA_PROJECT/ANIKUTA/core/player/src/main/assets/cacert.pem` (Mozilla CA bundle — copy from old project)
- `ANIKUTA_PROJECT/ANIKUTA/core/player/src/main/assets/subfont.ttf` (subtitle font — copy from old project)

---

## 3. Watch Page (`:feature:watch`)

### 3.1 Build.gradle

File: `ANIKUTA_PROJECT/ANIKUTA/feature/watch/build.gradle.kts`

```kotlin
plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.watch"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.database)
    implementation(projects.core.preferences)
    implementation(projects.core.sourceApi)
    implementation(projects.core.player)
    implementation(projects.core.anilist)
    implementation(projects.core.episodeMetadata)
    implementation(projects.data.anime)
    implementation(projects.data.history)
    implementation(projects.feature.videoResolver)

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
```

### 3.2 WatchScreen.kt — YouTube-style layout

File: `ANIKUTA_PROJECT/ANIKUTA/feature/watch/src/main/java/app/confused/anikuta/feature/watch/WatchScreen.kt`

Layout (portrait, MINIMIZED mode):
```
┌─────────────────────────────────────┐
│  [Floating top bar — pill-shaped]   │ ← WatchTopBar (back + title + settings)
│          statusBarsPadding()         │   HIDES on scroll (video moves to top)
├─────────────────────────────────────┤
│                                     │
│         MPV AndroidView             │ ← 16:9 Box, rounded 14dp, black bg
│      (MinimizedControls overlay)    │   Sticky — stays at top on scroll
│                                     │
├─────────────────────────────────────┤
│  [Episode details section]          │ ← EpisodeDescriptionSection (MODULAR)
│  Episode N · Title · Date · Summary │   Scrolls with the list below
│  [Server ▼] [Audio ▼]               │ ← ServerVersionDropdowns
│  ─────────────────                  │
│  Episodes                           │ ← WatchEpisodeList (MODULAR)
│  [Episode row 1]                    │
│  [Episode row 2]                    │
│  [Episode row N]                    │
└─────────────────────────────────────┘
```

**Sticky player behavior (CRITICAL):**
- The MPV player + minimized controls are in a `stickyHeader()` in the `LazyColumn` (or use a `Box` with the player on top + a scrolling list below).
- When the user scrolls down, the top bar (WatchTopBar) **hides** and the video player **moves to the very top** (under the status bar, edge-to-edge per design principle #1). The player NEVER hides — it always stays at the top.
- When the user scrolls back up, the top bar reappears.
- Use `rememberLazyListState()` + `nestedScroll` to detect scroll direction + amount.

**Implementation approach:**
```kotlin
@Composable
fun WatchScreen(watchRequest: WatchRequest, onBack: () -> Unit, onFullscreen: () -> Unit) {
    val listState = rememberLazyListState()
    // Top bar hides when the user has scrolled down more than 50dp
    val topBarVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 200 }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            // Sticky player
            item(key = "player") {
                PlayerSection(
                    watchRequest = watchRequest,
                    onFullscreen = onFullscreen,
                    modifier = Modifier.statusBarsPadding().padding(top = if (topBarVisible) 56.dp else 0.dp),
                )
            }
            // Episode description (MODULAR — separate composable)
            item(key = "description") { EpisodeDescriptionSection(...) }
            // Server/Audio dropdowns
            item(key = "dropdowns") { ServerVersionDropdowns(...) }
            // Divider
            item(key = "divider") { HorizontalDivider() }
            // Episode list (MODULAR — separate composable)
            item(key = "episodes_header") { Text("Episodes", ...) }
            items(episodeList) { ep -> WatchEpisodeRow(...) }
        }

        // Floating top bar (overlays the LazyColumn, animates in/out)
        AnimatedVisibility(visible = topBarVisible, enter = fadeIn(), exit = fadeOut()) {
            WatchTopBar(title = animeTitle, onBack = onBack)
        }
    }
}
```

### 3.3 EpisodeDescriptionSection.kt (MODULAR)

File: `.../feature/watch/components/EpisodeDescriptionSection.kt`

A standalone composable that shows:
- Episode number badge
- Episode title (large, ExtraBold)
- Air date (if available)
- Synopsis/description (3-line, expandable)

Data sources (in priority order):
1. `EpisodeMetadataRepository.fetch(request)` — if wired, fetches rich metadata
2. `SEpisode.summary` — fallback from the source
3. `SEpisode.name` — final fallback

Must be reusable — can be placed in the watch page or a future dedicated episode details page.

### 3.4 WatchEpisodeList.kt (MODULAR)

File: `.../feature/watch/components/WatchEpisodeList.kt`

A standalone composable rendering the episode list. Uses `PlayerEpisodePreferences` (NOT the detail page's prefs). Features:
- Alternating card colors
- Current episode highlighted (primary border + elevated surface)
- Thumbnail (configurable size: small/medium/large, position: left/right)
- Title + number + date + audio pills (SUB/DUB/HSUB from scanlator)
- Switching animation (pulsing background on current episode while loading)
- Tap to switch episode (triggers `onEpisodeSwitch(index)`)

Must be reusable — can be placed in the watch page or used standalone.

### 3.5 WatchTopBar.kt

File: `.../feature/watch/components/WatchTopBar.kt`

Floating pill-shaped `Surface`:
- `RoundedCornerShape(20.dp)`, `surfaceContainerHigh`, tonal elevation 3dp, shadow elevation 6dp
- Back button (circular, `secondaryContainer`) + "ANIKUTA" title (primary, ExtraBold) + settings button
- `statusBarsPadding()` + horizontal padding 12dp + vertical 4dp
- **Hides on scroll** (see WatchScreen §3.2)

### 3.6 WatchViewModel.kt

File: `.../feature/watch/WatchViewModel.kt`

State holder (pure StateFlows, no MPV calls — mirrors old `PlayerViewModel`):
- `playerMode: StateFlow<PlayerMode>` (MINIMIZED / FULLSCREEN)
- `episodeList: StateFlow<List<SEpisode>>`
- `currentEpisodeIndex: StateFlow<Int>`
- `isSwitchingEpisode: StateFlow<Boolean>`
- `controlsVisible: StateFlow<Boolean>` (auto-hide after 5s)
- `subtitleTracks / audioTracks / currentSubtitleId / currentAudioId`
- `availableServers / currentServer / availableAudioVersions / currentAudioVersion`
- `currentVideoQuality / currentVideoUrl`
- `position / duration / isPlaying / buffering / bufferAheadTime`
- `loadingState / errorMessage`
- `watchProgress: StateFlow<WatchProgress?>` — for resume position

Mutators: `setPlayerMode`, `setEpisodeList`, `setSwitchingEpisode`, `setSubtitleTracks`, etc. (NO MPV calls — the Activity/Screen owns MPV).

---

## 4. Fullscreen Player (`:feature:player`)

### 4.1 Build.gradle

File: `ANIKUTA_PROJECT/ANIKUTA/feature/player/build.gradle.kts`

```kotlin
plugins { id("anikuta.library.compose") }
android { namespace = "app.confused.anikuta.feature.player" }
dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.player)
    implementation(projects.core.sourceApi)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
}
```

### 4.2 FullscreenPlayerScreen.kt

Landscape, edge-to-edge immersive (NO `statusBarsPadding()` — hard rule from `player.md`).

Layout:
```
┌─────────────────────────────────────────────────────┐
│ [Lock] Episode Title                    [Server][Sub]│ ← Top, auto-hides 4s
│                                       [Audio][Qual][More]│
│                                                     │
│            [Replay10]  [▶/⏸]  [Forward10]           │ ← Center, auto-hides
│                                                     │
│ [━━━━━━━━●━━━━━━━━━━━━] 12:34 / 24:00    [1x][↻]   │ ← Bottom seekbar
│ [Skip +85s]                          [Minimize][PiP] │
└─────────────────────────────────────────────────────┘
```

### 4.3 FullscreenControls.kt

Port from `OLD_ANIKUTA/.../controls/FullscreenControls.kt` (358 lines). Components:
- `FSSmallButton` (36dp, rounded) — for server, sub, audio, quality, more, speed, rotate
- `FSCenterButton` (44dp, circle) — play/pause
- `FSSkipButton` (40dp) — skip +85s (anime opening length, configurable via `PlayerPreferences.skipButtonDuration()`)
- `FullscreenSeekbar` — M3 `Slider` with buffer coloring via `inactiveTrackColor`
- Lock button (top-left) — when locked, only the unlock button is tappable
- Auto-hide: 4s (controls), 3s (lock button)

### 4.4 FullscreenGestureHandler.kt

Port from `OLD_ANIKUTA/.../controls/PlayerGestureHandler.kt` (169 lines). Gestures:

| Gesture | Action |
|---|---|
| Single tap | Toggle controls |
| Double-tap left half | Seek -10s + "-10s" pill animation |
| Double-tap right half | Seek +10s + "+10s" pill |
| Horizontal drag | Seek (full width = ~120s) |
| Vertical drag left half | Brightness (window.attributes.screenBrightness) |
| Vertical drag right half | Volume (AudioManager.STREAM_MUSIC) |
| Pinch zoom | MPV video-zoom with magnetic snap at 0.2 intervals |

**CRITICAL**: All gestures early-return when `controlsLocked == true` (locked = only the unlock button works).

---

## 5. Player Controls (`:core:player/controls/`)

### 5.1 MinimizedControls.kt

Port from `OLD_ANIKUTA/.../controls/MinimizedControls.kt` (524 lines). Layout:
- Top-left: `position / duration` text
- Top-right: subtitle + quality icons
- Center: transparent play/pause icon (double-tap center third toggles play/pause)
- Bottom: `MinimalSeekbar` (custom) + maximize (fullscreen) button

Gestures (inline in MinimizedControls):
- Single tap → toggle controls
- Double-tap left third → seek -10s + side pill
- Double-tap center third → toggle play/pause + center icon
- Double-tap right third → seek +10s + side pill

### 5.2 MinimalSeekbar.kt

Custom seekbar (port from old project, or use `seeker` library):
- 5dp track, 14dp thumb (visible only while dragging)
- Floating time indicator pill above the thumb while dragging
- Buffer-ahead segment (`bufferAheadTime`)

### 5.3 Sheets (bottom-up menus — ALL with `dragHandle = null`)

Per design principle #2: **NO drag handle on ANY bottom-up menu.** Every `ModalBottomSheet` must set `dragHandle = null`.

Port these from `OLD_ANIKUTA/.../controls/sheets/PlayerSheets.kt`:

1. **SubtitleTracksSheet** — chip-based track selection (FilterChip in FlowRow). "Off" = AssistChip (outline). Language tracks = FilterChip (filled when selected). "Subtitle Settings" row at top → opens SubtitleSettingsSheet.

2. **SubtitleSettingsSheet** — partial height (max 450dp so video stays visible). Sections: Typography (font, size, scale, border, bold, italic), Colors (text, border, bg — each → ColorPickerSheet), Position & Misc (position, shadow, override ASS, delay stepper). Every change calls `onSettingsChanged()` → `mpvView?.applySubtitlePreferences()`. Uses `setPropertyInt`/`setPropertyDouble` for numerics (NOT `setPropertyString` — doesn't reliably update numeric MPV properties at runtime).

3. **AudioTracksSheet** — `SheetOption` rows with title = track.name, subtitle = track.language, checkmark if selected.

4. **QualitySheet** — two modes ("current" / "all" per `PlayerPreferences.qualitySheetDisplayMode()`). `SheetOption` rows with textual ✓. Selection match by `videoTitle` NOT `videoUrl` (proxied URLs change — hard rule).

5. **SpeedSheet** — speed options (0.25x to 4x).

6. **ServerSheet** — list of available servers.

7. **MoreSheet** — sleep timer, screenshot, re-resolve video, etc.

### 5.4 NumericKeypad.kt

Port from `OLD_ANIKUTA/.../controls/NumericKeypad.kt`. Custom numeric keypad for precise input (subtitle font size, delay, etc.). Design language: `NumericEntrySheet` with a display + keypad + apply/cancel. Heights must be properly managed.

### 5.5 ColorPickerDialog.kt

Port from `OLD_ANIKUTA/.../controls/ColorPickerDialog.kt`. Preset colors + custom RGBA sliders + live preview.

### 5.6 EpisodeSwitchingOverlay.kt

Loading overlay shown during episode/quality switches. Full thumbnail + spinner for episode switches. Semi-transparent scrim + spinner for quality switches (freezes last frame).

### 5.7 ServerVersionDropdowns.kt

Port from old project. Two side-by-side dropdowns (Server, Audio version), each expands downward via `AnimatedVisibility(expandVertically)`.

---

## 6. Watch Progress + Resume

### 6.1 WatchProgressStore.kt

Port from `OLD_ANIKUTA/.../player/WatchProgressStore.kt` (160 lines).
- Backed by `PreferenceStore.getObject<Map<String, Progress>>` (JSON-serialized)
- Key: `"$anilistId:$episodeUrl"` (stable across sessions)
- `Progress(positionSeconds, durationSeconds, title, updatedAt, coverUrl?, animeTitle?, episodeNumber, thumbnailUrl?)`
- Methods: `save(...)`, `get(anilistId, episodeUrl)`, `clear(anilistId, episodeUrl)`, `clearAnime(anilistId)`, `deleteAll()`
- `changes: Flow<Map<String, Progress>>` — reactive stream

### 6.2 PlaybackStateStore.kt

Port from `OLD_ANIKUTA/.../player/PlaybackStateStore.kt` (108 lines).
- Stores: `videoUrl, videoServer, videoAudio, videoQuality, videoHeaders, audioTrackId, subtitleTrackId, sourceId, updatedAt`
- For resume-from-history: restores the exact playback state

### 6.3 Progress saving logic

Save progress in these contexts:
1. **Periodic** — every 10s during playback
2. **On pause** — `onPause()` calls `saveProgress()`
3. **On destroy** — `onDestroy()` calls `saveProgress()`
4. **On END_FILE** — genuine end (not switch): save final + mark seen

Logic:
- Read `watch_threshold` pref (default 0.85 = 85%)
- If `dur > 0 && pos < dur - 2` → save + maybe mark seen (if `pos >= dur * threshold`)
- If `pos >= dur - 2` (finished) → save final + mark seen + sync to AniList (TBD)
- Also write to `EpisodeRepository.updateSeen(id, seen, lastSecondSeen, totalSeconds)` + `HistoryRepository.upsert(animeId, episodeId, seenAt, lastSecondSeen)` (the new project's SQLDelight-backed stores)

### 6.4 Resume on load

On first `FILE_LOADED` for an episode:
1. `val pos = watchProgressStore.get(anilistId, episodeUrl)` 
2. If `pos.positionSeconds > 5` → seek there + show "Start over?" pill (auto-dismiss 10s)
3. Restore saved audio + subtitle tracks from `PlaybackStateStore`

---

## 7. Subtitle Track Handling

### 7.1 Track loading

After `MPV_EVENT_FILE_LOADED`:
1. `view.loadTracks()` → returns `(subTracks, audioTracks)` with "Off" prepended
2. Set VM state: `setSubtitleTracks(subTracks)`, `setAudioTracks(audioTracks)`
3. Auto-select subtitle track per `PlayerPreferences.defaultSubtitleMode()`:
   - "off" → never auto-select
   - "auto" → only if a track matches `preferredSubtitleLanguage()` (default "en,eng")
   - "on" → pick best track (language match first, sorted by quality)
4. **User choice wins**: if `userDisabledSubtitles == true`, never override
5. Re-assert `sid` 500ms later (MPV sometimes resets to "no" when track-list changes)

### 7.2 External tracks

Send `MPVLib.command(arrayOf("sub-add", url, "auto", "", lang))` for external subtitle URLs. **ONLY after `FILE_LOADED`** (sending before causes silent drops). Run on `Dispatchers.IO` (each sub-add triggers HTTPS download). Dedupe by URL with `addedTrackUrls: MutableSet<String>`.

### 7.3 SubtitleTrackFormatter (MODULAR — see §2.5)

Raw MPV track names can be:
- Hash-like filenames: `cuhcdrfytgvhjue6t576buy57g4e.vtt` → display only language code
- Real titles: `"English Subtitles"` → display `"English Subtitles (en)"`
- No title + no lang → display `"Track 3"`

The formatter is a standalone object that can be unit-tested independently.

---

## 8. Navigation Wiring

### 8.1 WatchRequest data class

File: `ANIKUTA_PROJECT/ANIKUTA/feature/watch/src/main/java/app/confused/anikuta/feature/watch/WatchRequest.kt`

```kotlin
data class WatchRequest(
    val videoUrl: String,
    val videoHeaders: String?,      // comma-separated "Key: Value" for extension proxy
    val videoTitle: String,          // for the overlay
    val anilistId: Int,              // keys watch progress
    val animeTitle: String,          // for display
    val coverUrl: String?,           // for history + dynamic theming
    val coverColor: Int?,            // ARGB for dynamic theme (cover-color theming)
    val episodeUrl: String,          // secondary progress key
    val episodeNumber: Float,        // for AniList progress sync
    val sourceId: Long,              // for episode switching (re-resolve)
    val videoServer: String,         // current server name
    val videoAudio: String,          // current audio version
    val videoQuality: Int,           // current quality
    val episodeList: List<SEpisode>, // for the episode list
)
```

### 8.2 Modify MainActivity.kt

File: `ANIKUTA_PROJECT/ANIKUTA/app/src/main/java/app/confused/anikuta/MainActivity.kt`

1. Add state: `var watchTarget by remember { mutableStateOf<WatchRequest?>(null) }`
2. In `onVideoSelected`, construct a `WatchRequest` from `detailAnimeId` + `resolveTarget` + `video` + anime metadata, then `watchTarget = watchRequest`
3. Add a `when` branch for `watchTarget != null -> WatchScreen(...)` — positioned ABOVE `detailAnimeId != null` (so back from watch → detail)
4. Extend `BackHandler`:
   - Add `watchTarget != null` to `enabled`
   - Add `watchTarget != null -> { saveProgress(); watchTarget = null }` branch (ABOVE `detailAnimeId`)
5. The fullscreen ↔ minimized transition is a NESTED `BackHandler` inside the watch screen (enabled only when fullscreen) — so back from fullscreen → minimized watch page.

### 8.3 AnimeDetailScreen → pass anime metadata

The `onOpenEpisode` callback currently passes `(SEpisode, AnimeSource)`. You need to also pass anime metadata (title, cover URL, cover color, anilistId) so the watch page can construct a `WatchRequest`. Either:
- Expand the callback signature, OR
- Capture `detailAnimeId` at the `MainActivity` level and pass anime metadata through a new parameter

---

## 9. PlayerPreferences + Settings

### 9.1 Port PlayerPreferences.kt

Port from `OLD_ANIKUTA/.../player/PlayerPreferences.kt` (326 lines). All keys prefixed `pref_`. Categories:

**Playback:** playerSpeed (1.0f), tryHWDecoding (true), gpuNext (false), volumeBoostCap (0), preferredAudioLanguages ("jpn,eng"), seekStepSeconds (10), brightness (-1 = system), autoHideControls (true)

**Player view:** defaultPlayerView ("minimized"/"fullscreen"/"ask"), playerPromptShown (false), skipButtonDuration (85s), playerGesturesEnabled (true), pipOnExit (false), showPlayerTopBar (true), qualitySheetDisplayMode ("current"/"all")

**Subtitle styling:** subtitleFont ("Sans Serif"), subtitleFontSize (55), subtitleFontScale (1.0f), subtitleBorderSize (3), boldSubtitles, italicSubtitles, textColorSubtitles (0xFFFFFFFF), borderColorSubtitles (0xFF000000), backgroundColorSubtitles (0x00000000), subtitlePosition (100), subtitleShadowOffset (0), overrideSubsASS (false)

**Subtitle behavior:** defaultSubtitleMode ("on"/"off"/"auto"), preferredSubtitleLanguage ("en,eng"), subtitlesDelay (0ms)

**Watched appearance:** watchedEpisodeAppearance ("none"/"grayscale"/"blur"/"both"), watchedEpisodeBlurRadius (2f), watchedEpisodeAlpha (0.55f)

### 9.2 Port PlayerEpisodePreferences.kt

Port from old project. All keys prefixed `player_ep_`. Separate from detail-page prefs so the user can have different layouts in each context.

### 9.3 Settings UI

Create a `PlayerSettingsScreen` (in `:feature:settings` or `:feature:player`) that exposes all `PlayerPreferences`. Follow design language: settings divided into sections, simple mode hides advanced (ADR-018). Use `SettingsGroupCard` from `:core:designsystem`.

---

## 10. Dynamic Cover-Color Theming

Per `watch-page.md §7` + `themes-and-colors.md §6`:

1. Create `generateDynamicScheme(Color(coverColor))` in `:core:designsystem` — generates a `ColorScheme` from the anime's cover color.
2. Wrap the watch page subtree in `AnikutaTheme(colorScheme = dynamicScheme)` so the watch page is tinted with the cover color.
3. The fullscreen player uses the same dynamic scheme.

---

## 11. Crash Safety

The app already has `AnikutaCrashHandler` + `ErrorActivity` (installed in `App.kt`). Any uncaught exception will show the crash screen with Copy/Restart/Close. Make sure your player code:
- Catches `Throwable` around all MPV calls (MPV native errors throw `Error` subclasses)
- Never lets a native crash escape to the crash handler (it would still show the screen, but the process is already dead from SIGABRT — so catch what you can)
- Logs all errors with `Log.e(TAG, "...", e)`

---

## 12. Logging

Use `android.util.Log` with descriptive tags:
- `AnikutaWatchVM` — WatchViewModel
- `AnikutaWatchScreen` — WatchScreen
- `AnikutaPlayer` — player lifecycle
- `AnikutaMPV` — MPV events
- `AnikutaProgress` — watch progress saving
- `AnikutaSubtitles` — subtitle track handling
- `AnikutaResolver` — video resolution (existing)

Log at these points:
- Episode switch (start, server resolved, video loaded, done)
- Quality switch
- Audio/subtitle track change
- Progress save (periodic, on pause, on destroy)
- Resume position seek
- MPV events (FILE_LOADED, END_FILE, error)
- Gesture events (seek, brightness, volume — debug level only)

---

## 13. Verification Checklist (after each milestone)

After each major step, verify:
1. **Code compiles** — no Kotlin errors (check CI build log for `e: file://...` lines)
2. **No nested LazyColumn** — episode list inside the watch page's LazyColumn must be a plain `Column` with `forEach` (NOT another `LazyColumn`) — this caused a crash before
3. **All source calls on Dispatchers.IO** — `source.getSearchAnime()`, `source.getEpisodeList()`, `source.getVideoList()` must be wrapped in `withContext(Dispatchers.IO)` or they'll throw `NetworkOnMainThreadException`
4. **All bottom-up menus have `dragHandle = null`** — design principle #2
5. **Edge-to-edge** — no `statusBarsPadding()` on the fullscreen player (hard rule from `player.md`)
6. **MPV view never recreated** — `AndroidView.factory` checks existing first
7. **`subfont.ttf` in config-dir ROOT** — NOT `fonts/` subdirectory
8. **Send ntfy notification** after each milestone

---

## 14. To-Do List (100+ tasks)

### Phase A: MPV Core (`:core:player`) — 20 tasks

- [ ] **A1**: Read `OLD_ANIKUTA/.../player/AnikutaMPVView.kt` end-to-end. Understand every property observer, every MPV command, the track-list parsing, the subtitle styling.
- [ ] **A2**: Add MPV dependencies to `core/player/build.gradle.kts` (see §2.1)
- [ ] **A3**: Enable ABI splits in `app/build.gradle.kts` (see §2.2)
- [ ] **A4**: Create `core/player/src/main/res/layout/mpv_view.xml` (see §2.3)
- [ ] **A5**: Copy `cacert.pem` + `subfont.ttf` from old project's assets to `core/player/src/main/assets/`
- [ ] **A6**: Port `AnikutaMPVView.kt` → `core/player/.../AnikutaMPVView.kt` (change package to `app.confused.anikuta.core.player`)
- [ ] **A7**: Port `PlayerObserver.kt` → `core/player/.../PlayerObserver.kt`
- [ ] **A8**: Port `MpvConfigManager.kt` → `core/player/.../MpvConfigManager.kt`
- [ ] **A9**: Port `PlayerUtils.kt` → `core/player/.../PlayerUtils.kt` (URL resolution)
- [ ] **A10**: Create `SubtitleTrackFormatter.kt` in `core/player/.../subtitles/` (see §2.5) — standalone, testable
- [ ] **A11**: Port `PlayerPreferences.kt` → `core/player/.../PlayerPreferences.kt` (see §9.1)
- [ ] **A12**: Port `PlayerEpisodePreferences.kt` → `core/player/.../PlayerEpisodePreferences.kt`
- [ ] **A13**: Port `WatchProgressStore.kt` → `core/player/.../WatchProgressStore.kt` (see §6.1)
- [ ] **A14**: Port `PlaybackStateStore.kt` → `core/player/.../PlaybackStateStore.kt`
- [ ] **A15**: Port `PlayerEnums.kt` → `core/player/.../PlayerEnums.kt` (PlayerMode, VideoTrack, loading states)
- [ ] **A16**: Create `core/player/.../mpv_view.xml` if not done in A4
- [ ] **A17**: Verify all packages changed from `app.anikuta.player` to `app.confused.anikuta.core.player`
- [ ] **A18**: Verify `AnikutaMPVView` extends `is.xyz.mpv.BaseMPVView` and the XML layout references the correct FQCN
- [ ] **A19**: **BUILD CHECK** — commit + push + verify CI builds successfully (MPV native libs should package)
- [ ] **A20**: Send ntfy notification: "Phase A (MPV core) complete"

### Phase B: Player Controls (`:core:player/controls/`) — 20 tasks

- [ ] **B1**: Port `MinimizedControls.kt` → `core/player/.../controls/MinimizedControls.kt` (see §5.1)
- [ ] **B2**: Port/create `MinimalSeekbar.kt` (see §5.2) — use `seeker` library or custom
- [ ] **B3**: Port `EpisodeSwitchingOverlay.kt` → `core/player/.../controls/EpisodeSwitchingOverlay.kt`
- [ ] **B4**: Port `ServerVersionDropdowns.kt` → `core/player/.../controls/ServerVersionDropdowns.kt`
- [ ] **B5**: Port `SubtitleTracksSheet` from `PlayerSheets.kt` → `core/player/.../controls/sheets/SubtitleTracksSheet.kt` (dragHandle = null!)
- [ ] **B6**: Port `SubtitleSettingsSheet` + `SubtitleSettingsPanel.kt` → `core/player/.../controls/sheets/` (max 450dp height, dragHandle = null)
- [ ] **B7**: Port `AudioTracksSheet` → `core/player/.../controls/sheets/AudioTracksSheet.kt`
- [ ] **B8**: Port `QualitySheet` → `core/player/.../controls/sheets/QualitySheet.kt` (match by videoTitle, not videoUrl)
- [ ] **B9**: Port `SpeedSheet` → `core/player/.../controls/sheets/SpeedSheet.kt`
- [ ] **B10**: Port `ServerSheet` → `core/player/.../controls/sheets/ServerSheet.kt`
- [ ] **B11**: Port `MoreSheet` → `core/player/.../controls/sheets/MoreSheet.kt`
- [ ] **B12**: Port `NumericKeypad.kt` → `core/player/.../controls/NumericKeypad.kt` (see §5.4)
- [ ] **B13**: Port `ColorPickerDialog.kt` → `core/player/.../controls/ColorPickerDialog.kt`
- [ ] **B14**: Port `FirstTimePlayerPrompt.kt` → `core/player/.../controls/FirstTimePlayerPrompt.kt`
- [ ] **B15**: Verify ALL sheets have `dragHandle = null` (design principle #2)
- [ ] **B16**: Verify all controls use RobotoFamily ExtraBold for headings
- [ ] **B17**: Verify all controls use the #B1F256 theme colors
- [ ] **B18**: Verify no nested LazyColumn anywhere (episode list in sheets must be Column)
- [ ] **B19**: **BUILD CHECK** — commit + push + verify CI builds
- [ ] **B20**: Send ntfy notification: "Phase B (player controls) complete"

### Phase C: Watch Page (`:feature:watch`) — 20 tasks

- [ ] **C1**: Create `feature/watch/build.gradle.kts` (see §3.1)
- [ ] **C2**: Create `WatchRequest.kt` data class (see §8.1)
- [ ] **C3**: Create `WatchViewModel.kt` (see §3.6) — pure StateFlows, no MPV calls
- [ ] **C4**: Create `WatchScreen.kt` — YouTube-style layout (see §3.2)
- [ ] **C5**: Implement sticky player behavior — MPV stays at top, top bar hides on scroll
- [ ] **C6**: Create `EpisodeDescriptionSection.kt` (see §3.3) — MODULAR, fetches from EpisodeMetadataRepository
- [ ] **C7**: Create `WatchEpisodeList.kt` (see §3.4) — MODULAR, uses PlayerEpisodePreferences
- [ ] **C8**: Create `WatchTopBar.kt` (see §3.5) — floating pill, hides on scroll
- [ ] **C9**: Wire `AndroidView(AnikutaMPVView)` — inflate from `R.layout.mpv_view`, cache instance, never recreate
- [ ] **C10**: Wire `MinimizedControls` overlay on the MPV view
- [ ] **C11**: Wire episode switching — `onEpisodeSwitch(index)` → resolve video → loadfile
- [ ] **C12**: Wire server/audio/quality dropdowns
- [ ] **C13**: Wire watch progress saving (periodic 10s, on pause, on destroy)
- [ ] **C14**: Wire resume position (on FILE_LOADED, seek to saved pos, show "start over?" pill)
- [ ] **C15**: Wire subtitle track auto-selection + manual selection
- [ ] **C16**: Wire audio track auto-selection + manual selection
- [ ] **C17**: Wire fullscreen button → `onFullscreen()` callback
- [ ] **C18**: Verify all source calls are on `Dispatchers.IO` (NetworkOnMainThreadException guard)
- [ ] **C19**: **BUILD CHECK** — commit + push + verify CI builds
- [ ] **C20**: Send ntfy notification: "Phase C (watch page) complete"

### Phase D: Fullscreen Player (`:feature:player`) — 15 tasks

- [ ] **D1**: Create `feature/player/build.gradle.kts` (see §4.1)
- [ ] **D2**: Create `FullscreenPlayerScreen.kt` (see §4.2) — landscape, edge-to-edge, NO statusBarsPadding
- [ ] **D3**: Port `FullscreenControls.kt` → `feature/player/.../FullscreenControls.kt` (see §4.3)
- [ ] **D4**: Port `FullscreenGestureHandler.kt` → `feature/player/.../FullscreenGestureHandler.kt` (see §4.4)
- [ ] **D5**: Wire the SAME `AnikutaMPVView` instance (never recreate) — re-parent on mode switch
- [ ] **D6**: Wire lock button — when locked, only unlock button is tappable
- [ ] **D7**: Wire auto-hide (4s controls, 3s lock button)
- [ ] **D8**: Wire all sheet openings (server, sub, audio, quality, speed, more) from fullscreen icons
- [ ] **D9**: Wire skip button (+85s, configurable)
- [ ] **D10**: Wire PiP entry (manual button + on-user-leave-hint if pref enabled)
- [ ] **D11**: Wire orientation change (fullscreen → sensor landscape, minimized → sensor portrait)
- [ ] **D12**: Wire nested BackHandler (back from fullscreen → minimized, NOT exit watch page)
- [ ] **D13**: Verify all gestures early-return when `controlsLocked == true`
- [ ] **D14**: **BUILD CHECK** — commit + push + verify CI builds
- [ ] **D15**: Send ntfy notification: "Phase D (fullscreen player) complete"

### Phase E: Navigation + Integration — 10 tasks

- [ ] **E1**: Modify `MainActivity.kt` — add `watchTarget` state (see §8.2)
- [ ] **E2**: Modify `onVideoSelected` — construct `WatchRequest` from detailAnimeId + resolveTarget + video
- [ ] **E3**: Add `when` branch for `watchTarget != null -> WatchScreen(...)` (above detailAnimeId)
- [ ] **E4**: Extend `BackHandler` — add watchTarget branch (above detailAnimeId)
- [ ] **E5**: Expand `AnimeDetailScreen.onOpenEpisode` to pass anime metadata (title, cover, coverColor, anilistId)
- [ ] **E6**: Wire `EpisodeMetadataRepository` in Koin (create a module or add to RepositoryModule)
- [ ] **E7**: Create `generateDynamicScheme(Color)` in `:core:designsystem` for cover-color theming
- [ ] **E8**: Wrap watch page + fullscreen player in `AnikutaTheme(colorScheme = dynamicScheme)`
- [ ] **E9**: **BUILD CHECK** — commit + push + verify CI builds
- [ ] **E10**: Send ntfy notification: "Phase E (navigation + integration) complete"

### Phase F: Polish + Settings — 10 tasks

- [ ] **F1**: Create `PlayerSettingsScreen` exposing all `PlayerPreferences` (design language: sectioned, simple/advanced modes)
- [ ] **F2**: Wire `showPlayerTopBar` pref — toggles WatchTopBar visibility reactively
- [ ] **F3**: Wire `defaultPlayerView` pref — "minimized"/"fullscreen"/"ask" (with FirstTimePlayerPrompt)
- [ ] **F4**: Wire `qualitySheetDisplayMode` pref — "current"/"all"
- [ ] **F5**: Wire `seekStepSeconds` pref — configurable double-tap seek amount
- [ ] **F6**: Wire `skipButtonDuration` pref — configurable skip button (default 85s)
- [ ] **F7**: Wire `playerGesturesEnabled` pref — disables all gestures when off
- [ ] **F8**: Wire `pipOnExit` pref — auto-PiP on nav-away while playing
- [ ] **F9**: Verify all settings persist across app restarts
- [ ] **F10**: **BUILD CHECK** — commit + push + verify CI builds

### Phase G: Final Verification — 10 tasks

- [ ] **G1**: Read the CI build log — verify NO compilation errors
- [ ] **G2**: Download the APK artifact — verify it's a valid APK
- [ ] **G3**: Verify the APK contains MPV native libs (`libmpv.so` in `lib/arm64-v8a/`)
- [ ] **G4**: Verify the APK contains `cacert.pem` + `subfont.ttf` in assets
- [ ] **G5**: Verify the APK contains `AnikutaMPVView` class
- [ ] **G6**: Verify the APK contains `WatchScreen` + `FullscreenPlayerScreen` classes
- [ ] **G7**: Verify no nested LazyColumn (grep for `LazyColumn` inside `item { }` blocks)
- [ ] **G8**: Verify all `ModalBottomSheet` have `dragHandle = null`
- [ ] **G9**: Verify `app/build.gradle.kts` has ABI splits enabled
- [ ] **G10**: Send final ntfy notification with APK download URL

---

## 15. Rules Summary (DO NOT BREAK THESE)

1. **NEVER nest a LazyColumn inside another LazyColumn** — use `Column` + `forEach` for inner lists. This caused a crash before.
2. **NEVER call source.getSearchAnime/getEpisodeList/getVideoList on the main thread** — wrap in `withContext(Dispatchers.IO)`. This caused NetworkOnMainThreadException.
3. **NEVER create a ModalBottomSheet without `dragHandle = null`** — design principle #2.
4. **NEVER recreate the AnikutaMPVView** — cache the instance, reuse on mode switches.
5. **NEVER put `subfont.ttf` in a `fonts/` subdirectory** — it goes in the MPV config-dir ROOT.
6. **NEVER use `statusBarsPadding()` on the fullscreen player** — edge-to-edge immersive (hard rule from `player.md`).
7. **NEVER match videos by `videoUrl`** — proxied URLs change. Match by `videoTitle`.
8. **NEVER send `sub-add` before `FILE_LOADED`** — external tracks silently dropped.
9. **NEVER read `sid`/`aid` as ints** — use `getPropertyString` (MPV returns "node"/"string").
10. **ALWAYS catch `Throwable` around MPV calls** — native errors throw `Error` subclasses.
11. **ALWAYS send ntfy notifications** after each milestone.
12. **ALWAYS wait for a CI build to finish before pushing again** — concurrency cancels in-progress builds.
13. **ALWAYS verify the APK after each build** — download + check for MPV native libs + key classes.
14. **ALWAYS follow the 12 design principles** (especially: NO drag handle, edge-to-edge, #B1F256 theme, RobotoFamily ExtraBold).

---

## 16. After Completion

After all phases are done and the final APK builds successfully:

1. Update `/home/z/my-project/worklog.md` (or the project's `RULES/sessions/`) with a detailed session handoff documenting:
   - What was built (list all files created/modified)
   - Architecture decisions made
   - Any gaps remaining (e.g., episode metadata sources not implemented, AniList sync TBD)
   - Lessons learned

2. Send a final ntfy notification with the APK download URL.

3. Report back to the user with:
   - Summary of what was built
   - APK download link
   - What to test
   - Recommended next phase

**Good luck. Take your time. Verify everything. Don't rush.**
