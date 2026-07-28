# Player Development Rules

> **Read this before working on the video player.** This file survives sandbox resets (backed up to GitHub).
> Last updated: Session 30 (subtitles fixed, folder selection, settings reorganized)

---

## Architecture: One Screen, Two Modes

The player is a **single screen** (`PlayerActivity`) with two modes:
- **MINIMIZED**: Portrait. Video (16:9) at top, server/version dropdowns, episodes list below.
- **FULLSCREEN**: Landscape. Video fills screen, overlay controls on top.

### Critical: MPV View Lifecycle
- MPV can only be initialized **ONCE per process**. `BaseMPVView.initialize()` a second time = SIGABRT.
- The `AnikutaMPVView` (via `AndroidView`) is **always present** in the layout — it is NEVER disposed/recreated during mode transitions.
- In MINIMIZED mode, the MPV view fills the entire screen. The bottom portion (below 16:9 video area) is covered by an opaque `Surface` showing dropdowns + episodes list.
- `onDestroy()` calls `MPVLib.destroy()` for full cleanup, allowing fresh `initialize()` on next open.

### Mode Switching
- `PlayerViewModel.setPlayerMode(mode)` changes the state → Compose recomposes with new layout.
- Video keeps playing during transition (MPV view is not touched).
- **Orientation MUST change** when switching modes:
  - MINIMIZED → `SCREEN_ORIENTATION_SENSOR_PORTRAIT`
  - FULLSCREEN → `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`
- The `PlayerActivity` must call `requestedOrientation` when mode changes (not just in `onCreate`).

---

## Track API (MPV)

MPV exposes tracks via the `track-list` property:
- `track-list/count` — number of tracks
- `track-list/N/type` — "audio", "sub", or "video"
- `track-list/N/id` — track ID (int)
- `track-list/N/title` — track title (may be empty)
- `track-list/N/lang` — language code (e.g., "jpn", "eng"; may be empty)

Select tracks:
- `MPVLib.setPropertyInt("sid", trackId)` — set subtitle track (-1 = off)
- `MPVLib.setPropertyInt("aid", trackId)` — set audio track (-1 = off)

The `track-list` property is observed as `MPV_FORMAT_NONE` — changes fire `eventProperty(property: String)` (no value). The Activity calls `view.loadTracks()` and pushes results to `PlayerViewModel`.

---

## Files (Phase 1)

```
app/anikuta/player/
├── PlayerActivity.kt           # Host + MPV lifecycle + PlayerScreen composable
├── PlayerViewModel.kt          # State: mode, episodes, tracks, servers, playback
├── PlayerPreferences.kt        # All player prefs (mode, skip, gestures, etc.)
├── AnikutaMPVView.kt           # MPV wrapper + track API
├── PlayerObserver.kt           # MPV event → Activity callbacks
├── WatchProgressStore.kt       # Resume position persistence
├── PlayerEnums.kt              # PlayerMode, DefaultPlayerView, VideoTrack, etc.
└── controls/
    ├── PlayerControls.kt       # Fullscreen overlay (existing, will be refactored in Phase 2)
    ├── MinimizedControls.kt    # Minimized video overlay (play/pause, seek, maximize)
    ├── ServerVersionDropdowns.kt # Server + audio version dropdown selectors
    ├── EpisodeListView.kt      # Scrollable episodes list
    └── FirstTimePlayerPrompt.kt # First-time view selection dialog
```

---

## Known Issues (fixed in Phase 1 fixes + Phase 2)

1. ✅ **First-time prompt wired** — shows when defaultView=ask + !promptShown
2. ✅ **Mode switching fixed** — handleModeChange() updates BOTH viewModel AND orientation
3. ⚠️ **Server dropdown** — needs actual video data wiring (Phase 3 sheets created, full wiring TBD)
4. ⚠️ **Audio version switching** — track API wired (Phase 3), server-level audio version TBD
5. ✅ **Orientation fixed** — handleModeChange() sets requestedOrientation per mode

---

## Phases

- **Phase 1** ✅ (complete): Foundation — enums, prefs, VM, track API, minimized controls, dropdowns, episodes list, prompt, settings, combined activity
- **Phase 2** ✅ (complete): Fullscreen mode + transition + lock + auto-hide
- **Phase 3** ✅ (complete): Selection sheets (quality, subtitle tracks, audio tracks, server, speed, more)
- **Phase 4** ✅ (complete): Gestures + pinch zoom (seek, brightness, volume, double-tap, magnetic zoom)
- **Phase 5** (in progress): Subtitle customization
- **Phase 6** (pending): Polish + MPV config + PiP

---

## Notification Rule
- Send ntfy after EACH build completion (GitHub Actions workflow does this automatically).
- Topic: `ntfy.sh/TASKISDONE` (changed from `THEANIMEAPPTASKISDONE` in session 30).
- The workflow's `Notify ntfy.sh` step fires on both success and failure.

---

## Subtitle Rules (Session 30 — CRITICAL)

### Font file
- `app/src/main/assets/subfont.ttf` MUST be a valid TrueType font (valid signature: `\x00\x01\x00\x00`). It was once a GitHub HTML 404 page — that broke all subtitle rendering for 6+ builds.
- It's copied to `<configDir>/subfont.ttf` (config root, NOT `fonts/` subdir) by `PlayerActivity.copyAssets()`.
- The mpv-lib native `ass_set_fonts()` looks for `<configDir>/subfont.ttf` as the default fallback font.

### Property API
- **Init-time options** (before `MPVLib.init()`): use `MPVLib.setOptionString()` inside `AnikutaMPVView.initOptions()`.
- **Runtime properties**: use `MPVLib.setPropertyString()` for string values, `MPVLib.setPropertyInt()` for integers, `MPVLib.setPropertyDouble()` for floats.
- **CRITICAL:** `setPropertyString("sub-font-size", "55")` does NOT reliably work for numeric properties. Use `setPropertyInt("sub-font-size", 55)` instead. This was the root cause of "font size change not reflected on video."
- `sid`/`aid` are node/string properties — read with `getPropertyString()`, not `getPropertyInt()`.

### Subtitle default mode
- `PlayerPreferences.defaultSubtitleMode()`: "off" / "on" / "auto".
- "on" = always auto-select the best track (language-matched first, else first).
- "auto" = only select if a track matches `preferredSubtitleLanguage`.
- `userDisabledSubtitles` flag: set when user explicitly taps "Off" in the sheet. Respected by `autoSelectSubtitleTrack` in ALL modes. Reset on new episode load.

### Settings UI design language
- Use `SelectableOptionCard` (card-style selector with primary border) for 2-4 options with descriptions.
- Use `StyledSegmentedRow` (pill-style segmented row) for short-label 2-3 options.
- Both are in `app/src/main/java/app/anikuta/ui/settings/SelectableOptionCard.kt`.
- Do NOT use `primaryContainer` for selected state (it's too dark/blue). Use `primary` border + `primary` text.
- Player settings is a HUB (links only) → subpages (General, Playback, Subtitles, Display & Behavior, Episode list).

### Custom keypad
- `NumericEntrySheet` (bottom sheet, not popup) — always uses `CustomKeypadSheet` (not experimental).
- 4×3 grid: [1][2][3][DEL] / [4][5][6][0] / [7][8][9][OK]. DEL and OK are 112dp tall (2 rows).
- Value display on top with +/- stepper buttons.
- No "Done" button (OK in the grid handles confirmation).

---

## Design Decisions
- **No Palette API** — dynamic theming uses AniList `coverImage.color` + HSL variant generation
- **Blur stays** — the banner blur on the detail page is a user-requested feature, do NOT remove it
- **Modular** — each component in its own file, easy to find and edit
- **Episode list has separate settings** from the detail page (PlayerEpisodePreferences)
- **Server dropdown** drops down from where tapped (not a bottom sheet)
- **Storage** is NOT in player settings — it's in the top-level "Data & Storage" category
