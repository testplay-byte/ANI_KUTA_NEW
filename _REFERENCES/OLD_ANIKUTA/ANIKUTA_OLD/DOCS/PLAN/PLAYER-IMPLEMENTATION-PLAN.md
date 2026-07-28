# Video Player — Comprehensive Implementation Plan

> **Status:** Planning phase — awaiting user go-ahead before implementation.
> **Last updated:** Session 27
> **Approach:** Incremental builds with ntfy notification after EACH step (not just the whole phase).

---

## Reference Analysis Summary

I analyzed aniyomi's player code (55 files, ~13,000 lines). Key findings:

### What aniyomi has that we'll selectively adopt:
- **Track API**: `MPVLib.getPropertyInt("track-list/count")` + `track-list/N/{type,id,title,lang}` — clean way to enumerate audio/subtitle tracks
- **Track selection**: `MPVLib.setPropertyInt("sid", trackId)` for subtitles, `aid` for audio
- **Subtitle properties**: 15+ MPV properties (sub-font, sub-color, sub-scale, sub-pos, sub-delay, etc.) — all settable in real-time
- **GestureHandler**: `detectTapGestures` + `detectHorizontalDragGestures` + `detectVerticalDragGestures` — split into 3 pointerInput modifiers
- **Controls layout**: Split into TopLeft/TopRight/BottomLeft/BottomRight/Middle zones — we'll adapt this to our fullscreen layout
- **Sheets**: QualitySheet, SubtitleTracksSheet, AudioTracksSheet, SpeedSheet, MoreSheet — bottom sheet pattern for each selection type
- **Panels**: SubtitleSettingsPanel with ColorsCard + TypographyCard + MiscellaneousCard — we'll adopt this for subtitle customization
- **Preferences**: Separate preference files (PlayerPreferences, SubtitlePreferences, GesturePreferences, AudioPreferences, DecoderPreferences, AdvancedPlayerPreferences)

### What aniyomi has that we'll NOT adopt (too complex for now):
- Chapter sync (requires chapter metadata from source)
- AniSkip API integration (deferred to Phase 5)
- External intent casting (Chromecast etc.)
- Media session with full notification controls (Phase 5)
- Screenshot functionality (Phase 5)
- Video filters panel (Phase 5+)

### Our current starting point (8 files, ~1,395 lines):
- `PlayerActivity.kt` (505 lines) — fullscreen-only host
- `PlayerControls.kt` (264 lines) — minimal overlay
- `AnikutaMPVView.kt` (154 lines) — MPV wrapper (no track API yet)
- `PlayerViewModel.kt` (115 lines) — basic state (no tracks, no mode)
- `PlayerPreferences.kt` (149 lines) — basic prefs (no subtitle prefs)
- `PlayerObserver.kt` (80 lines) — MPV events
- `WatchProgressStore.kt` (102 lines) — resume
- `PlayerEnums.kt` (26 lines) — loading state

---

## Implementation Plan — 6 Phases

Each phase is broken into **steps**. After each step: build, test, ntfy notification.

---

### Phase 1: Foundation — Player Mode + Minimized View

**Goal:** Replace the current fullscreen-only player with a combined screen that has minimized mode working.

#### Step 1.1: Extend PlayerEnums + PlayerPreferences
- Add `PlayerMode` enum: `MINIMIZED`, `FULLSCREEN`
- Add `defaultPlayerView()` preference: `"minimized"` / `"fullscreen"` / `"ask"`
- Add `skipButtonDuration()` preference (default 85, in seconds)
- Add `playerGesturesEnabled()` preference (default true)
- Add `autoHideControls()` preference (default true)
- **Build + ntfy**

#### Step 1.2: Extend PlayerViewModel
- Add `playerMode` StateFlow (MINIMIZED/FULLSCREEN)
- Add `episodeList` StateFlow (from matched source)
- Add `currentEpisodeIndex` StateFlow
- Add `isSwitchingEpisode` StateFlow (loading state for episode switch)
- Add `togglePlayerMode()` function
- Add `switchToEpisode(index)` function (pauses current, loads new)
- **Build + ntfy**

#### Step 1.3: Extend AnikutaMPVView with track API
- Add `sid` / `aid` property delegates (like aniyomi's TrackDelegate)
- Add `getTrackCount()`, `getTrackType(n)`, `getTrackId(n)`, `getTrackTitle(n)`, `getTrackLang(n)`
- Add `loadTracks()` that returns audio + subtitle track lists
- Register `track-list` property observer
- **Build + ntfy**

#### Step 1.4: Create MinimizedControls composable
- Video player area at top (aspect-ratio adaptive)
- Controls overlay on video: play/pause, seek bar, timestamps
- Minimize/maximize button at BOTTOM RIGHT of video
- Quality + subtitle quick-access buttons on video overlay
- Tap to show/hide controls, auto-hide
- **Build + ntfy**

#### Step 1.5: Create episode info + server/version dropdowns
- Episode info bar below video (number, title, description)
- Server dropdown (clickable → shows available servers as a dropdown/popup)
- Version dropdown (SUB/DUB/HSUB)
- These are proper dropdowns, NOT chips
- **Build + ntfy**

#### Step 1.6: Episodes list below
- Reuse the detail page's EpisodeRow design (with all its customization)
- Current episode highlighted
- Tap to switch (triggers switchToEpisode)
- **Build + ntfy**

#### Step 1.7: First-time prompt + Settings
- First-time prompt dialog when user first plays an episode
- "Player" section in Settings with default view option
- Wire up the prompt to save preference
- **Build + ntfy**

#### Step 1.8: Replace old PlayerActivity
- Remove old fullscreen-only controls
- New PlayerActivity hosts the combined player
- Detail page → tap episode → opens in default view mode
- **Build + ntfy + user test**

---

### Phase 2: Fullscreen Mode + Transition

**Goal:** Fullscreen overlay controls + smooth animated transition between modes.

#### Step 2.1: Create FullscreenControls composable
- Landscape-oriented overlay
- Top bar: lock button (left) + title block + settings icons (right)
- Center: seek ±10s + play/pause
- Bottom bar: seekbar + timestamp (left) + skip/minimize/PiP (right)
- Auto-hide controls + tap to toggle
- **Build + ntfy**

#### Step 2.2: Lock button
- Lock button in top left
- When locked: ALL controls disabled, only lock button shown
- Lock button auto-hides on tap/inactivity
- Tap screen → show lock button → tap lock → unlock
- **Build + ntfy**

#### Step 2.3: Mode transition animation
- Smooth animation: video expands/collapses between modes
- Video keeps playing during transition
- Swipe up (center) → minimized to fullscreen
- Swipe down (center) → fullscreen to minimized
- **Build + ntfy**

#### Step 2.4: Maximize/minimize buttons
- Maximize button in minimized view (bottom right of video)
- Minimize button in fullscreen (bottom right)
- Both trigger the same smooth animation
- **Build + ntfy + user test**

---

### Phase 3: Selection Sheets

**Goal:** All selection bottom sheets for quality, tracks, server, speed.

#### Step 3.1: QualitySheet
- Bottom sheet listing available video qualities
- Shows current selection
- Tap to switch (reloads video at new quality)
- **Build + ntfy**

#### Step 3.2: SubtitleTracksSheet
- Bottom sheet listing subtitle tracks from MPV
- "Off" option + all available tracks
- Shows track language + title
- Tap to select → `MPVLib.setPropertyInt("sid", trackId)`
- **Build + ntfy**

#### Step 3.3: AudioTracksSheet
- Bottom sheet listing audio tracks from MPV
- Shows track language + title
- Tap to select → `MPVLib.setPropertyInt("aid", trackId)`
- **Build + ntfy**

#### Step 3.4: ServerSheet
- Bottom sheet listing available servers (from video resolution)
- Shows server name + available qualities
- Tap to switch server
- **Build + ntfy**

#### Step 3.5: SpeedSheet
- Bottom sheet with speed slider (0.25x – 4x)
- Preset buttons: 0.5x, 1x, 1.5x, 2x
- Real-time speed change via `MPVLib.setPropertyDouble("speed", value)`
- **Build + ntfy**

#### Step 3.6: MoreOptionsSheet (⋯)
- Advanced settings: subtitle delay, audio delay, screenshot, sleep timer
- Opens from the ⋯ button in top right
- **Build + ntfy + user test**

---

### Phase 4: Gestures + Pinch Zoom

**Goal:** Full gesture support with proper zones.

#### Step 4.1: Horizontal seek gesture
- Swipe left/right anywhere on video → seek
- Shows seek preview timestamp
- Pauses during seek, resumes after
- **Build + ntfy**

#### Step 4.2: Vertical brightness/volume gestures
- Left half swipe up/down → brightness
- Right half swipe up/down → volume
- Overlay indicators (brightness/volume bars)
- **Build + ntfy**

#### Step 4.3: Double-tap seek (FULL left/right halves)
- Double-tap ANYWHERE on left half → seek -10s
- Double-tap ANYWHERE on right half → seek +10s
- Ripple animation feedback
- Configurable duration in settings
- **Build + ntfy**

#### Step 4.4: Pinch zoom with magnetic resistance
- Continuous zoom (not snapping/jumping)
- Magnetic resistance at 20% intervals (40%, 60%, 80%, 100%)
- User must zoom "harder" to break past a sticky point
- After breaking past: smooth continuous zoom (99, 98, 97...)
- Also snaps when video edges align with screen (contain/cover)
- **Build + ntfy**

#### Step 4.5: Skip button + rotation
- Skip 85s button (customizable duration)
- Screen rotation toggle button
- **Build + ntfy + user test**

---

### Phase 5: Subtitle Customization

**Goal:** Full subtitle styling with live preview.

#### Step 5.1: SubtitlePreferences
- Add all subtitle preferences (font, size, color, border, position, delay, etc.)
- 15+ preferences matching aniyomi's SubtitlePreferences
- **Build + ntfy**

#### Step 5.2: Apply subtitle prefs to MPV
- In AnikutaMPVView: set all sub-* properties from preferences
- Real-time updates when preferences change
- **Build + ntfy**

#### Step 5.3: SubtitleSettingsPanel
- Colors card: text color, border color, background color, border style
- Typography card: font family, font size, bold, italic, justification
- Miscellaneous card: position, delay, shadow, override ASS
- **Build + ntfy**

#### Step 5.4: Live preview
- Sample subtitle text rendered with current settings
- Updates in real-time as user changes settings
- Presets: Small / Medium / Large / Custom
- **Build + ntfy + user test**

---

### Phase 6: Polish + MPV Config

**Goal:** PiP, media session, MPV config, performance.

#### Step 6.1: MPV config files
- Create `mpv.conf` + `input.conf` in app files dir
- Advanced settings: read/write config files
- Expose key MPV options in Settings (hwdec, gpu-next, sync, etc.)
- **Build + ntfy**

#### Step 6.2: Picture-in-Picture
- PiP mode when user switches apps
- PiP button in fullscreen
- **Build + ntfy**

#### Step 6.3: Media session
- Notification controls (play/pause/seek)
- Headphone button support
- **Build + ntfy**

#### Step 6.4: Release build performance testing
- Set up release build variant
- Test performance without debug overhead
- Optimize any remaining jank
- **Build + ntfy + user test**

---

## Modular File Structure (final)

```
app/anikuta/player/
├── PlayerActivity.kt              # host (refactored)
├── PlayerViewModel.kt              # extended (mode, tracks, episodes)
├── PlayerPreferences.kt            # extended (all player + subtitle prefs)
├── AnikutaMPVView.kt               # extended (track API, subtitle props)
├── PlayerObserver.kt               # extended (track-list observer)
├── WatchProgressStore.kt           # existing
├── PlayerEnums.kt                  # extended (PlayerMode, VideoTrack)
├── PinchZoomHandler.kt             # NEW: magnetic zoom logic
├── PlayerTransition.kt             # NEW: animated mode transition
├── SubtitleSettings.kt             # NEW: subtitle preference model
└── controls/
    ├── PlayerControls.kt           # refactored: mode-aware dispatcher
    ├── MinimizedControls.kt        # NEW: minimized view
    ├── FullscreenControls.kt       # NEW: fullscreen overlay
    ├── PlayerGestureHandler.kt     # NEW: all gestures
    ├── PlayerLockOverlay.kt        # NEW: lock button + locked state
    ├── EpisodeListView.kt          # NEW: reusable episode list (shares detail page design)
    └── sheets/
        ├── QualitySheet.kt         # NEW
        ├── SubtitleTracksSheet.kt  # NEW
        ├── AudioTracksSheet.kt     # NEW
        ├── ServerSheet.kt          # NEW
        ├── SpeedSheet.kt           # NEW
        └── MoreOptionsSheet.kt     # NEW
```

---

## Episode List Customization

The episodes list in the player will share the same design as the detail page BUT with its own separate settings:

- `PlayerEpisodePreferences` (separate from detail page preferences)
- Same options: show/hide number, titles, summaries, thumbnails, dates, audio pills
- Same layout options: positions, sizes
- Separate live preview in player settings
- This keeps customization routes free — user can have different layouts on detail page vs player

---

## Notification Schedule

After EACH step (not just each phase):
1. Build the APK
2. Send ntfy notification with:
   - Step number + description
   - Build status (success/failure)
   - Commit hash
   - APK download link
3. If build fails: fix immediately, rebuild, re-notify

This means ~25-30 notifications across all 6 phases. Each is a testable increment.

---

## Questions Still Pending

1. **Episode list settings location** — should the player's episode list settings be in:
   - (a) Settings → Player → Episode list display
   - (b) A separate "Player Display" section
   - (c) Reuse the detail page settings (not recommended — user wants separate)

2. **Server dropdown design** — when the user taps the server dropdown, should it show:
   - (a) A popup menu (like a dropdown)
   - (b) A bottom sheet
   - (c) An expandable inline list

3. **Skip 85s button position** — you said bottom right. Should it be:
   - (a) Next to the minimize/PiP buttons
   - (b) Separate, more prominent position

---

_Last updated: Session 27. Awaiting go-ahead to start Phase 1, Step 1.1._
