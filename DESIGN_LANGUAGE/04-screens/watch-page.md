# 04 — Watch Page (YouTube-style)

> The watch page sits **between** the anime details page and the fullscreen
> player. It hosts an embedded mini-player at the top (16:9), the current
> episode's description below it, and the episode list below that. The user
> can maximize the player to fullscreen.
>
> **ADR ref:** ADR-012 — Watch page: YouTube-style (minimized player +
> episodes below, maximizable).
>
> **Status:** STRUCTURE and BEHAVIOR are fixed (this doc). Visual
> refinements (player chrome, control overlay timing, transition animation
> curve, episode-row treatment) are pending — the owner will give more
> design preferences for this screen later. Document what we know now.

---

## 1. Owner's vision (verbatim)

> "It will be like how it is on YouTube, like how the users can see a
> minimized view and at the bottom they see all the other videos. He can
> maximize it."

In short: a minimized player at the top, the episode list + description
below, and a maximize action to fullscreen. This is a **distinct screen**
between the anime details page and the fullscreen player — Aniyomi does
NOT have this screen.

---

## 2. Position in the navigation flow

```
   Cover tap (Home / Library / Browse / MY / Search)
            │
            ▼
   Anime Details page  ──(tap episode row)──►  WATCH PAGE (this screen)
                                                     │
                                                     │ (maximize)
                                                     ▼
                                                Fullscreen Player
                                                     │
                                                     │ (minimize / back)
                                                     ▼
                                                (returns to WATCH PAGE)
```

- The watch page is a **new screen**, NOT a sheet on the details page and
  NOT a fullscreen player.
- The fullscreen player is reached **only** from the watch page (and from
  notification deep-links, which skip straight to fullscreen — TBD).
- Backing out of the watch page returns to the details page; backing out
  of fullscreen returns to the watch page.

---

## 3. Layout (minimized mode)

```
   ┌──────────────────────────────────────────────────────────┐
   │  ←  <anime title>                          ⋯ (more)      │  ← top bar
   ├──────────────────────────────────────────────────────────┤
   │  ┌────────────────────────────────────────────────────┐  │
   │  │             MINI-PLAYER (16:9, MPV)                │  │  ← always playing
   │  │             [tap = show controls]                  │  │     (or paused at
   │  │             [double-tap L/R = seek ±10s]           │  │     last position)
   │  │             [maximize btn = fullscreen]            │  │
   │  └────────────────────────────────────────────────────┘  │
   │  Episode 5 · "The Fall" · 24:18 / 24:00    [⏸] [expand]  │  ← player strip
   ├──────────────────────────────────────────────────────────┤
   │  Episode 5 — "The Fall"                                  │  ← description
   │  <episode synopsis, 3-line preview by default,           │     (collapsible)
   │   tap to expand to full>                                 │
   ├──────────────────────────────────────────────────────────┤
   │  Episodes                            [sort] [filter]     │  ← episode list
   │  ▶ Ep 5 · The Fall            24:00   [✓ watched]        │  ← current
   │    Ep 6 · Aftermath           24:00   [↓ downloaded]     │
   │    Ep 7 · Tide                24:00                      │
   │    …                                                     │
   └──────────────────────────────────────────────────────────┘
```

### 3.1 Vertical structure

From top to bottom, the watch page stacks:

1. **Top bar** — back button, anime title (truncated), overflow menu
   (per-anime actions: mark all watched, download settings, tracker status).
2. **Mini-player** — 16:9 MPV surface, top-aligned. Always present — shows
   the artwork as a poster with a play button until playback starts.
3. **Player strip** — directly under the player. Shows current episode
   number + title, position / duration, play/pause toggle, maximize
   button. Always visible (does NOT auto-hide).
4. **Description** — the current episode's synopsis. Collapsible: 3-line
   preview by default, tap to expand.
5. **Episode list** — full episode list with sort/filter header. The
   currently-playing episode is highlighted (accent color, "▶"
   indicator). Tapping another episode switches the mini-player (§5).

### 3.2 Scrolling behavior

- The whole page scrolls vertically **except** the mini-player and player
  strip — those two are **sticky** at the top.
- When the user scrolls the description/episode list up under the player
  strip, the player remains visible and continues playing (core
  YouTube-on-mobile behavior). The old project achieves this by keeping
  the MPV `AndroidView` at the top of a `LazyColumn` with a sticky header
  for the player strip.

---

## 4. Mini-player behavior

### 4.1 State

| State | What it shows | Audio |
|---|---|---|
| **Poster** | Anime cover art + big play button | silent |
| **Playing** | Video, controls overlay auto-hides after 5s | playing |
| **Paused** | Video frozen at current frame, controls visible | silent |

- The watch page **opens in Poster state** if there is no resume position,
  or **Playing** state if there is one (auto-resume).
- The mini-player is **never disposed** while the watch page is on screen.
  Switching episodes, scrolling, or expanding/collapsing the description
  does NOT recreate the player surface.

### 4.2 Controls overlay (mirrors old `MinimizedControls`)

- Top-left: current time / total duration. Top-right: subtitle + quality.
- Center: play/pause. Bottom: seekbar (fills width) + maximize button.
- Single tap (overlay hidden): show overlay.
- Single tap (overlay visible, on center): toggle play/pause.
- Single tap (overlay visible, elsewhere): hide overlay.
- Double-tap left/right thirds: seek ±10s. Auto-hide after 5s.
- The mini-player is intentionally minimal: NO volume/brightness gesture
  bars (fullscreen-only) and NO subtitle delay / audio delay / screenshot
  / sleep timer (those live in the "more" sheet OR in fullscreen).

---

## 5. Switching episodes from the list

When the user taps a different episode in the list below:

1. The mini-player surface is **kept** (not recreated) — MPV loads the new
   video URL into the same surface.
2. The description block updates to the new episode's synopsis.
3. The episode list re-highlights the new current episode; the previous
   one is marked "watched" if the user passed the progress threshold
   (default 80% — same as Aniyomi's history threshold). The list scroll
   position is preserved (no force-scroll).
4. A brief loading state shows on the player area while the new video
   buffers (`EpisodeSwitchingOverlay`); playback auto-starts once buffered.

### 5.1 "Up next" auto-advance (TBD)

- When the current episode finishes in the mini-player, the next episode
  auto-advances (same behavior as the fullscreen player). Whether to show
  an "Up next in 5s…" countdown card on the mini-player is TBD.

### 5.2 Episode list ordering & filtering

- The episode list header has sort (number asc/desc, date) and filter
  (sub/dub, downloaded, watched) controls — mirrors Aniyomi's episode
  list controls, no watch-page-specific behavior.

---

## 6. Maximize transition (mini → fullscreen)

### 6.1 Triggers

The user can maximize the mini-player to fullscreen via:

- Tapping the **maximize button** in the player strip.
- **Pinching out** on the player (gesture — TBD whether enabled).
- **Rotating** the device to landscape (auto-maximize on rotation — TBD
  whether enabled; the old project has this as a setting).

### 6.2 Transition

- The MPV `AndroidView` is **NOT recreated**. It is the same surface —
  only the overlay layout changes and the surface's layout params expand
  to fill the screen. The old project uses the same approach
  (`PlayerScreen.kt` — `MPVLib` stays alive; the Compose overlay switches
  between `MinimizedControls` and `FullscreenControls`).
- The transition is animated: the player rectangle expands from its
  minimized 16:9 position to fill the screen. Description and episode
  list slide down off-screen. Top bar fades out. Animation spec:
  `AnikutaSprings.spatial` (see `01-principles/motion.md` TBD). Playback
  continues uninterrupted (audio keeps playing; video frame may freeze
  for one frame then resume).

### 6.3 In fullscreen

- The fullscreen player shows the existing `FullscreenControls` overlay
  (top/bottom bars, gestures for seek/volume/brightness, subtitle/audio/
  quality sheets, "more" sheet with sleep timer etc.) — feature-parity
  with the old project's fullscreen player
  (`OLD_ANIKUTA/…/player/controls/FullscreenControls.kt`).
- The watch page's description + episode list are NOT visible in
  fullscreen (immersive mode).

### 6.4 Minimize (fullscreen → mini)

- Tapping minimize (or back gesture, or rotating back to portrait if
  auto-rotate is on) returns to the watch page. The player rectangle
  shrinks back to 16:9 at the top; description and episode list slide
  back up. Playback continues uninterrupted.

---

## 7. Cover-color theming on the watch page

- The watch page applies **cover-color dynamic theming** (per
  `03-themes/themes-and-colors.md` §6): the entire watch page subtree is
  wrapped in a `MaterialTheme` override derived from the anime's cover
  art — affects the top bar, player strip, description card, and episode
  list rows. The mini-player surface itself is unaffected (video is video).
  Backing out of the watch page restores the user's selected palette.
- Inherited from the old project (`PlayerScreen.kt` calls
  `generateDynamicScheme(Color(coverColor)).toM3ColorScheme()`).

---

## 8. PiP (picture-in-picture) interaction

- When the user navigates away from the watch page (device Home, or back
  to details) **while playback is active**, the player enters PiP mode
  (small floating window over the new screen). Tapping the PiP window
  returns to the watch page (not fullscreen).
- PiP is a system-level feature; this spec only defines entry/exit
  points. Full PiP behavior is TBD in the player architecture spec.

---

## 9. Relationship to the old ANIKUTA project

The old project at `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/
player/` already implements the watch page concept under a single
`PlayerScreen` that switches between `MINIMIZED` and `FULLSCREEN` modes.
Direct-reference files:

- `PlayerScreen.kt` — single screen hosting both modes. In ANIKUTA we
  split into `WatchScreen` + `FullscreenPlayer` overlay; MPV lifecycle
  stays shared.
- `controls/MinimizedControls.kt` — mini-player controls overlay.
- `controls/FullscreenControls.kt` — fullscreen controls overlay.
- `controls/EpisodeListView.kt` — episode list rendering.
- `controls/EpisodeSwitchingOverlay.kt` — loading overlay during switch.
- `PlayerActivity.kt` — activity hosting `PlayerScreen`. ANIKUTA uses a
  Voyager screen (no separate activity) — TBD in the player spec.

The old `PlayerScreen.kt` comment confirms the contract we keep: the MPV
AndroidView is ALWAYS present (fills the screen); it is never
disposed/recreated during mode transitions — only the overlay layout
changes.

---

## 10. What we know now vs. what the owner will decide later

### Decided (this doc)

- The watch page exists, between details and fullscreen.
- Sticky 16:9 mini-player at top, description below, episode list below.
- Maximize transitions to fullscreen; MPV surface is reused.
- Cover-color theming applies. Switching episodes keeps the surface,
  updates description + list highlight.

### Pending (owner will decide in a future design session)

- Whether description is above OR below the episode list.
- "Up next" auto-advance countdown on the mini-player.
- Gesture set on the mini-player (pinch-to-maximize, swipe-to-seek).
- Auto-maximize on device rotation. Whether the top bar hides on scroll.
- Visual treatment of the current-episode row (accent pill? "Now playing"
  badge? play icon?).
- PiP entry/exit visuals. Whether the mini-player shows a scrubber
  thumbnail preview (YouTube does; complex).
- Control overlay timing (current default: 5s auto-hide minimized, 4s
  fullscreen — inherited from old project).
- Episode list display options (the old project has extensive
  per-episode-row display preferences in `DisplaySettingsScreen` —
  inherited but may be reworked).

Until the owner decides, the implementation ships the old project's
behavior verbatim, adapted to the new screen split (`WatchScreen` +
`FullscreenPlayer` overlay, shared MPV lifecycle).

---

## See also

- `DESIGN_LANGUAGE/README.md` — folder map + primary reference policy.
- `DESIGN_LANGUAGE/03-themes/themes-and-colors.md` §6 — cover-color theming.
- `DESIGN_LANGUAGE/04-screens/bottom-nav.md` — the watch page is a
  non-top-level destination, so the floating bar is hidden here.
- `DOCS/04-design-decisions.md` ADR-012 (watch page), ADR-018 (simple mode).
- `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/player/` — prior
  working implementation (read-only structural reference).
- `PLANNING/02-screen-specs/player.md` (TBD) — fullscreen player + MPV
  embedding architecture.
