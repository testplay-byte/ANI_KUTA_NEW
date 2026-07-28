# Video Player Design Plan

> **Status:** Awaiting user approval. No code written yet.
> **Last updated:** Session 26

---

## Overview

The video player is a **single screen with two modes** (YouTube-style):
- **Minimized mode**: Video at top + episode info + server/version controls + episodes list
- **Fullscreen mode**: Video fills screen with overlay controls

The video keeps playing during mode transitions. User can toggle via swipe gestures or buttons.

---

## Minimized Mode Layout

```
┌─────────────────────────────────┐
│                                 │
│         VIDEO PLAYER            │  ← Adapts to video aspect ratio
│         (16:9 or wider)         │     Width = screen width
│                                 │     Sides touch left/right edges
│  [▶] ━━━━━━●━━━━━━ 4:32  [⛶]  │  ← Controls (tap to show/hide)
├─────────────────────────────────┤
│ Episode 5 — The Dragon's Labyrinth │  ← Episode info
│ A young adventurer discovers...    │     (number, title, description)
├─────────────────────────────────┤
│ [⚙ Anikoto] [🎧 SUB·DUB] [📺 1080p] │  ← Server + version + quality chips
├─────────────────────────────────┤
│ ┌─────────────────────────────┐ │
│ │ [5] The Dragon's Labyrinth  │ │  ← Current episode (highlighted)
│ │ [6] The Lost Temple         │ │
│ │ [7] Shadow Realm            │ │  ← Scrollable episodes list
│ │ [8] The Final Battle        │ │
│ │ ...                         │ │
│ └─────────────────────────────┘ │
└─────────────────────────────────┘
```

### Video controls (tap to show/hide, auto-hide)
- Play / pause
- Seek bar with timestamp
- Minimize/maximize button (→ fullscreen)
- Double-tap left/right = seek ∓10s
- Swipe up/down left = brightness, right = volume

### Below video
1. **Episode info bar**: episode number, title, description (compact)
2. **Server & version controls**: dropdown chips (server, sub/dub/hardsub, quality)
3. **Episodes list**: scrollable, current episode highlighted, tap to switch

---

## Fullscreen Mode Layout

```
┌──────────────────────────────────────┐
│ [🔒]                    [⚙][💬][🎵]   │  ← Top left: lock + title
│ Anime Name             [📺][⋯]       │     Top right: quality, subs, audio,
│ EP 5 — Dragon's Labyrinth            │     server, more options
│                                      │
│                                      │
│         [⏪]  [⏸]  [⏩]               │  ← Center: rewind 10 / play-pause / forward 10
│                                      │
│                                      │
│                                      │
│ ━━━━━━━●━━━━━━━━━━━━━━━━━━━━━━━━━━  │  ← Seek bar
│ [4:32] [⚡] [↻]    [⏭85] [🔲] [↙]   │  ← Bottom left: time, speed, rotation
│                                      │     Bottom right: skip 85s, minimize, PiP
└──────────────────────────────────────┘
```

### Top Left
- Lock button (locks all controls)
- Anime name
- Episode number + title

### Top Right
- Server selection
- Subtitle selection
- Audio track selection
- Quality selection
- More options (⋯) → advanced settings sheet

### Bottom Left
- Timestamp (current / total)
- Playback speed control
- Screen rotation toggle

### Bottom Right
- Skip 85 seconds forward
- Minimize button (→ minimized mode)
- Picture-in-Picture button

### Center
- Rewind 10s / Play-Pause / Forward 10s

---

## Gesture Zones (Fullscreen)

```
┌──────────────────────────────────────┐
│ [double-tap]      [double-tap]       │
│    -10s               +10s           │
│                                      │
│    ┌──────────┐   ┌──────────┐      │
│    │          │   │          │      │
│    │ BRIGHT-  │   │  VOLUME  │      │
│    │  NESS    │   │          │      │
│    │  ↕ swipe │   │  ↕ swipe │      │
│    │          │   │          │      │
│    └──────────┘   └──────────┘      │
│                                      │
│    ← → SEEK (horizontal swipe)       │
│                                      │
└──────────────────────────────────────┘
```

- **Horizontal swipe**: seek forward/backward (shows preview timestamp)
- **Left half vertical swipe**: brightness up/down
- **Right half vertical swipe**: volume up/down
- **Double-tap left**: skip -10s (with ripple animation)
- **Double-tap right**: skip +10s (with ripple animation)
- **Pinch zoom**: see magnetic snapping below

---

## Pinch Zoom — Magnetic Snapping

Sticky zones at every 20% interval: 40%, 60%, 80%, 100%.

```
40% -------- 60% -------- 80% -------- 100%
 |            |            |            |
 ◄─ sticky ──►◄─ sticky ──►◄─ sticky ──►
```

**How it works:**
1. User is at 100% zoom
2. Pinches to zoom out
3. Zoom stays at 100% until user zooms out "hard enough" (past threshold)
4. Once past threshold, zoom drops to 80% and "sticks" there
5. Same pattern at every 20% mark
6. Also snaps when video edges align with screen edges (contain/cover fit)

This prevents accidental zoom changes and makes it easy to land on common zoom levels.

---

## Mode Transition

- **Swipe up** from center → minimized to fullscreen
- **Swipe down** from center → fullscreen to minimized
- **Maximize button** (in minimized view) → fullscreen
- **Minimize button** (in fullscreen) → minimized
- Smooth animation (YouTube-style)
- Video keeps playing during transition
- Position/quality/subtitle state preserved

---

## Episode Switching

When user taps an episode in the list (minimized mode):
1. Current episode pauses
2. Loading animation appears on the video player
3. New episode loads in the background
4. Video starts playing once loaded
5. Episode info updates (title, description)
6. List updates — new episode highlighted as "playing"

---

## First-Time Prompt

When the user plays an episode for the very first time:

```
┌─────────────────────────────────┐
│            🎬                   │
│                                 │
│   Choose your default view      │
│   You can change this later     │
│                                 │
│   ┌─────────────────────────┐   │
│   │ 📱 Minimized             │   │  ← Video + episode list
│   │    Video + episode list  │   │
│   └─────────────────────────┘   │
│   ┌─────────────────────────┐   │
│   │ ⛶ Fullscreen             │   │  ← Immersive video
│   │    Immersive video       │   │
│   └─────────────────────────┘   │
│                                 │
│   ☑ Remember my selection       │
└─────────────────────────────────┘
```

Also available in Settings → Player → Default view.

---

## Implementation Phases

### Phase 1: Core Player + Minimized Mode (Foundation)
- PlayerMode enum (MINIMIZED, FULLSCREEN)
- PlayerViewModel extended with mode state + episode list
- Minimized view layout (video + info + server chips + episodes list)
- Basic video controls (play/pause, seek bar, timestamps)
- Episode switching from list (with loading state)
- First-time prompt dialog
- Default view preference in Settings

### Phase 2: Fullscreen Mode + Transition (Core)
- Fullscreen overlay controls (top/bottom bars, center)
- Smooth animated transition between modes
- Swipe up/down to toggle modes
- Maximize/minimize buttons
- Controls auto-hide + tap to show/hide
- Lock button (locks all controls)

### Phase 3: Selection Sheets (Features)
- Quality selection bottom sheet
- Subtitle track selection sheet
- Audio track selection sheet
- Server selection sheet
- Playback speed sheet
- More options (⋯) → advanced settings sheet

### Phase 4: Gestures + Pinch Zoom (Interaction)
- Horizontal swipe to seek
- Left/right vertical swipe for brightness/volume
- Double-tap to skip ±10s
- Pinch zoom with magnetic snapping (20% intervals)
- Skip 85 seconds button
- Screen rotation toggle

### Phase 5: Polish (Future)
- Picture-in-Picture mode
- Media session (notification controls)
- AniSkip integration (auto-skip openings)
- Sleep timer
- Subtitle customization (font, color, position)
- Performance optimization (release build testing)

---

## Modular File Structure

```
app/anikuta/player/
├── PlayerActivity.kt              # host (existing, extend)
├── PlayerViewModel.kt              # state (existing, extend with mode + tracks)
├── PlayerPreferences.kt            # prefs (existing, extend with default view)
├── AnikutaMPVView.kt               # MPV wrapper (existing)
├── PlayerObserver.kt               # MPV events (existing)
├── WatchProgressStore.kt           # resume (existing)
├── PlayerEnums.kt                  # extend with PlayerMode enum
├── PinchZoomHandler.kt             # NEW: magnetic zoom logic
├── PlayerTransition.kt             # NEW: animated mode transition
└── controls/
    ├── PlayerControls.kt           # refactor: mode-aware dispatcher
    ├── MinimizedControls.kt        # NEW: minimized view controls
    ├── FullscreenControls.kt       # NEW: fullscreen overlay controls
    ├── PlayerGestureHandler.kt     # NEW: all gesture detection
    ├── PlayerLockOverlay.kt        # NEW: lock button + locked state
    └── sheets/
        ├── QualitySheet.kt         # NEW: quality selection
        ├── SubtitleTracksSheet.kt  # NEW: subtitle track list
        ├── AudioTracksSheet.kt     # NEW: audio track list
        ├── ServerSheet.kt          # NEW: server selection
        ├── SpeedSheet.kt           # NEW: playback speed
        └── MoreOptionsSheet.kt     # NEW: advanced settings (⋯)
```

---

## Settings Integration

A new "Player" section in Settings:
- **Default view** — Minimized / Fullscreen / Ask every time
- **Default playback speed** — 0.25x to 4x
- **Seek step** — seconds per swipe (default 10)
- **Gestures enabled** — toggle all gestures on/off
- **Pinch zoom enabled** — toggle
- **Auto-hide controls** — toggle + timeout
- **Skip button duration** — customizable (default 85s)
- **Subtitle settings** — font size, color, background, position (Phase 5)

---

## Remaining Questions

1. **Skip 85 seconds button** — is this for skipping anime openings? Should the duration be customizable?
2. **Lock button behavior** — should ALL controls be disabled when locked, or should some still work?
3. **Server selection in minimized mode** — separate dropdown chips or one combined "settings" chip?
4. **Current full-screen player** — replace with the new combined player? (Recommendation: yes)
5. **Performance** — should we set up release builds for proper performance testing?

---

_Last updated: Session 26. Awaiting user approval before implementation._
