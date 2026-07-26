# Player UX Analysis & Improvement Plan

> **Status:** Analysis phase — awaiting user direction on UI/UX design before implementation.
> **Priority:** #1 (user's top priority for next phase)
> **Design language:** Material 3 Expressive

---

## Current State

### What exists (8 files, ~1,395 lines)

| File | Lines | Purpose |
|------|-------|---------|
| `PlayerActivity.kt` | 505 | Hosts MPV, manages lifecycle, handles taps/gestures |
| `PlayerControls.kt` | 264 | M3 Expressive overlay: top bar (back + title), center (play/pause + seek ±10s), bottom (slider + time) |
| `AnikutaMPVView.kt` | 154 | MPV wrapper (initialize, loadfile, observe) |
| `PlayerPreferences.kt` | 149 | Speed, HW decoding, GPU-next, brightness, seek step, audio languages |
| `PlayerViewModel.kt` | 115 | State: position, duration, playing, buffering, loading, controls visibility |
| `WatchProgressStore.kt` | 102 | Resume position persistence |
| `PlayerObserver.kt` | 80 | MPV event observer → ViewModel state updates |
| `PlayerEnums.kt` | 26 | LoadingState enum |

### What works
- ✅ Video plays from URL (MPV backend)
- ✅ Play/pause toggle
- ✅ Seek ±10 seconds
- ✅ Seek bar with time display
- ✅ Resume from saved position
- ✅ "Start over?" overlay on resume
- ✅ Buffering/loading indicator
- ✅ Controls auto-hide

### What's missing (the gaps)

#### 1. Quality selection (in-player)
- **Current:** Quality is chosen on the detail page via `VideoPickerSheet` before the player opens.
- **Gap:** No way to change quality *while in the player* without exiting and re-selecting.
- **aniyomi reference:** `QualitySheet.kt` — a bottom sheet accessible from the player controls.

#### 2. Subtitle track selection
- **Current:** No subtitle UI at all. MPV may load embedded subtitles by default, but the user can't select/deselect them.
- **Gap:** No track list, no toggle, no "off" option.
- **aniyomi reference:** `SubtitleTracksSheet.kt` — lists available subtitle tracks (embedded + external), lets user select one or "off".

#### 3. Audio track selection
- **Current:** No audio track UI. Can't switch between Japanese/English audio.
- **Gap:** No track list.
- **aniyomi reference:** `AudioTracksSheet.kt` — lists available audio tracks.

#### 4. Subtitle customization
- **Current:** None.
- **Gap:** No control over subtitle appearance (font size, color, background, position).
- **aniyomi reference:** `SubtitlePreferences.kt` + `SubtitleSettingsColorsCard.kt` + `SubtitleDelayPanel.kt` — comprehensive subtitle styling.

#### 5. Gesture controls
- **Current:** Tap to toggle controls. No swipe gestures.
- **Gap:** No seek-by-swipe, no brightness swipe (left), no volume swipe (right).
- **aniyomi reference:** `GesturePreferences.kt` + `VerticalSliders.kt` + `DoubleTapSeekTriangles.kt`.

#### 6. Playback speed
- **Current:** `playerSpeed()` pref exists but no UI to change it.
- **Gap:** No speed control in the player.
- **aniyomi reference:** `PlaybackSpeedSheet.kt`.

#### 7. Other missing features
- PiP (Picture-in-Picture)
- AniSkip (auto-skip openings/endings)
- Sleep timer
- Screenshot
- Chapter markers
- Media session integration
- Orientation lock

---

## Analysis: How to Implement

### Architecture approach

Following the project's **modular principle** (Core Rules §3), the player UX should be split into focused, small files:

```
app/anikuta/player/
├── PlayerActivity.kt          (host — already exists)
├── PlayerViewModel.kt          (state — extend with track info)
├── PlayerPreferences.kt        (prefs — extend with subtitle prefs)
├── AnikutaMPVView.kt           (MPV wrapper — already exists)
├── PlayerObserver.kt           (MPV events — already exists)
├── PlayerEnums.kt              (enums — already exists)
├── WatchProgressStore.kt       (resume — already exists)
├── controls/
│   ├── PlayerControls.kt       (main overlay — extend)
│   ├── PlayerTopBar.kt         (NEW: back + title + settings menu)
│   ├── PlayerCenterControls.kt (NEW: play/pause + seek)
│   ├── PlayerBottomBar.kt      (NEW: slider + time + speed)
│   ├── PlayerGestureHandler.kt (NEW: swipe gestures)
│   └── sheets/
│       ├── QualitySheet.kt     (NEW: quality selection)
│       ├── SubtitleTracksSheet.kt (NEW: subtitle track list)
│       ├── AudioTracksSheet.kt    (NEW: audio track list)
│       ├── SpeedSheet.kt       (NEW: playback speed)
│       └── SubtitleSettingsSheet.kt (NEW: subtitle customization)
```

### MPV track API

MPV exposes tracks via the `track-list` property. Each track has:
- `type`: "video" | "audio" | "sub"
- `id`: track ID (int)
- `lang`: language code (e.g., "jpn", "eng")
- `title`: track name
- `default`: whether it's the default track

To select a track: `MPVLib.setPropertyInt("aid", trackId)` for audio, `MPVLib.setPropertyInt("sid", trackId)` for subtitles.

To get the list: read `MPVLib.getProperty("track-list/count")`, then iterate `track-list/N/type`, `track-list/N/id`, etc.

### Subtitle customization via MPV properties

MPV supports extensive subtitle styling via properties:
- `sub-scale`: font size multiplier (1.0 = default)
- `sub-color`: subtitle text color (ass-color format)
- `sub-border-color`: outline color
- `sub-back-color`: background color
- `sub-pos`: vertical position (0-100, 100 = bottom)
- `sub-border-size`: outline thickness
- `sub-shadow-offset`: shadow offset
- `sub-font`: font family
- `sub-bold`, `sub-italic`: font weight/style
- `sub-delay`: subtitle timing offset (seconds)

These can be set via `MPVLib.setProperty()` in real-time — no restart needed.

---

## Improvement Plan (prioritized)

### Phase 1: Core player UX (essential)
1. **In-player quality selection** — bottom sheet with available qualities
2. **Subtitle track selection** — bottom sheet listing tracks + "off"
3. **Audio track selection** — bottom sheet listing tracks
4. **Playback speed** — bottom sheet with speed slider (0.25x – 4x)
5. **Settings menu** — gear icon in top bar → opens a sheet with all options

### Phase 2: Gesture controls
6. **Horizontal swipe** — seek forward/backward
7. **Left vertical swipe** — brightness
8. **Right vertical swipe** — volume
9. **Double-tap** — seek ±10s (with visual feedback triangles)
10. **Pinch** — zoom (optional)

### Phase 3: Subtitle customization
11. **Subtitle settings sheet** — font size, color, background, position, delay
12. **Subtitle delay** — sync adjustment (±ms)
13. **Subtitle presets** — "Small", "Medium", "Large", "Custom"

### Phase 4: Polish
14. **PiP (Picture-in-Picture)** — continue playback when switching apps
15. **Media session** — notification controls, headphone button support
16. **AniSkip** — auto-skip openings/endings (requires API integration)
17. **Sleep timer** — stop after N minutes
18. **Screenshot** — capture current frame
19. **Orientation lock** — force landscape/portrait

---

## Material 3 Expressive Design Notes

The player overlay should follow M3 Expressive principles:

### Visual style
- **Scrim gradient**: top and bottom dark gradients for text readability (already implemented)
- **Circular buttons**: play/pause is a 72dp circle with white surface (already implemented)
- **Spring animations**: press scale uses `AnikutaSprings.press` (already implemented)
- **Bottom sheets**: M3 ModalBottomSheet with rounded top corners, drag handle
- **Icons**: outlined or filled, 24-32dp, white tint on dark scrim
- **Typography**: titleMedium for title, labelMedium for time, bodyMedium for sheet content

### Color scheme (for the player)
- Overlay background: Black with 55-65% alpha gradient
- Buttons: White surface with Black icons (high contrast on video)
- Slider: Primary color for thumb + active track, White 30% for inactive
- Sheets: surfaceContainerLow background, onSurface text
- Selected state: primaryContainer background

### Animation
- Controls fade in/out (200ms)
- Sheets slide up (300ms spring)
- Button press scale (spring: 0.88-0.94x)
- Seek bar thumb grows on touch

---

## Questions for the user

Before implementation, I need direction on:

1. **Overall look**: Do you want a clean minimal overlay (like current) or a richer overlay with more buttons (like aniyomi)?
2. **Settings access**: Gear icon in top bar → bottom sheet? Or a dedicated settings page?
3. **Gestures**: Do you want swipe-to-seek + brightness/volume swipes? Or just tap?
4. **Subtitle defaults**: What font size/color/position should be the default?
5. **Priority**: Which of the 4 phases do you want first? (I recommend Phase 1: core UX)

---

_Last updated: Session 23. This is a planning document — implementation begins after user confirms direction._
