# 04 — Fullscreen Player

> The fullscreen video player — reached by **maximizing the watch page's
> mini-player** (ADR-012). Edge-to-edge immersive. Hosts the MPV surface
> plus the controls overlay and the bottom-up menus (subtitle-tracks,
> subtitle-settings, quality).
>
> **ADR refs:** ADR-012 (watch page → maximize → fullscreen), ADR-015
> (custom M3-inspired design language).
>
> **Principles applied:** #1 (edge-to-edge — actually applied here, no
> `statusBarsPadding`), #2 (no drag handle on bottom-up menus), #3
> (partial-height bottom-up menus), #4 (gradient + blur below the
> mini-player on the watch page), #7 (live-apply in subtitle settings),
> #11 (custom M3 — adapted), #12 (custom numeric keyboard).
>
> **Components used:** §1 (bottom-up menus), §4 (custom numeric keypad),
> §8 (live preview — partial reuse in subtitle-settings).
>
> **Status:** STRUCTURE and BEHAVIOR fixed (this doc). Control overlay
> timing and the blur implementation tuned in `03-themes/`.

---

## 1. Position in the navigation flow

```
   Anime Details ──(episode tap)──► Video Resolver ──► Watch Page
                                                           │
                                                           │ (maximize)
                                                           ▼
                                                  Fullscreen Player ◄── THIS DOC
                                                           │
                                                           │ (minimize / back)
                                                           ▼
                                                  (returns to Watch Page)
```

- Reached **only** by maximizing the watch page's mini-player. No direct
  path from details to fullscreen (ADR-012 — watch page sits between).
- Backing out returns to the watch page; playback continues
  uninterrupted across maximize/minimize (MPV surface is reused).
- Notification deep-links can skip straight to fullscreen — TBD.

---

## 2. Owner's vision

> "Edge-to-edge top bar. Player controls: timestamp TOP-LEFT, seek bar
> BOTTOM, fullscreen button RIGHT. Top-right: ONLY subtitles + quality.
> Below player: episode number, title, release date, description
> (gradient + blur). Subtitle bottom-up menu — no drag handle. Subtitle
> settings bottom-up menu — partial height, custom keyboard. Quality
> bottom-up menu — no drag handle."

The owner enumerated the exact control zones (§4, §5) and the exact
bottom-up menus (§6, §7, §8). This doc ports those verbatim and adds
the improvements flagged in analysis §1.4 and §9 (remove
`statusBarsPadding`, add real blur under the watch-page player).

---

## 3. Edge-to-edge policy (principle #1 — actually applied here)

**Hard rule** (improvement over old project — analysis §1.4): the top
bar in fullscreen MUST be **truly edge-to-edge**. The old project's
`.statusBarsPadding()` on the floating pill top bar defeats
`enableEdgeToEdge()` — REMOVE it on:

| Surface | Old project | New project |
|---|---|---|
| Floating pill top bar (minimized) | `.statusBarsPadding()` | **No padding** — bar sits under the status bar. |
| Video area when `showTopBar == false` (minimized) | `.statusBarsPadding()` | **No padding** — video goes edge-to-edge. |
| Fullscreen controls top row | `.statusBarsPadding()` (no-op since system bars hidden) | **No padding** — consistent. |

In fullscreen, the system status bar AND navigation bar are hidden
(immersive sticky — `PlayerActivity.kt:2202-2213` in old project).
The player overlay draws over the full screen.

---

## 4. Control overlay — minimized (watch page mini-player)

The overlay on the watch page's mini-player (16:9). Also the baseline
for fullscreen (§5 adds more controls). Ported verbatim from old
project's `MinimizedControls.kt` — KEEP.

```
┌─────────────────────────────────────────────────────────┐
│ 00:00 / 24:00                       [💬 Subtitles] [HD] │  ← top row
│                  ▶ / ⏸  (transparent, 72dp target)      │  ← center
│ ━━━━━━━━━━━━━━━━━━━━━ ⊙                       ⛶       │  ← bottom row
│   MinimalSeekbar (weight 1f)                   Fullscreen
└─────────────────────────────────────────────────────────┘
```

### 4.1 Top-left: timestamp

`Text("${formatTime(position)} / ${formatTime(duration)}")`. `Color.White`,
12 sp, `FontWeight.Medium`. `TopStart`, `padding(start = 12.dp, top = 8.dp)`.
Format `m:ss` or `h:mm:ss`.

### 4.2 Top-right: ONLY subtitle + quality (HARD RULE)

`Row(align = TopEnd, padding(end = 8.dp, top = 4.dp), spacedBy = 4.dp)`.
Two icons ONLY (left → right): **Subtitles**, then **Quality**.
`TransparentIconButton` — 36 dp Box, no background, 22 dp icon, white @ 85%.
Minimized mode does NOT surface audio / server / speed / more (those
live in fullscreen only, §5). Matches owner spec verbatim.

### 4.3 Center: play/pause + double-tap zones

Single tap anywhere: toggle controls visibility. Double-tap left/right
thirds: ±10 s (text pill "-10s" / "+10s"). Double-tap center third:
toggle play/pause. When controls visible, a 72 dp transparent Box in
the center toggles play/pause.

### 4.4 Bottom: seek bar + fullscreen button (HARD RULE — fullscreen RIGHT)

`Row(align = BottomCenter, padding(start = 8.dp, end = 8.dp, bottom = 6.dp))`.
`MinimalSeekbar` takes `weight(1f)`. 8 dp gap. **Fullscreen button on
the RIGHT** (hard rule — owner spec).

### 4.5 MinimalSeekbar

28 dp touch target, 5 dp thin track (`RoundedCornerShape(3.dp)`). Three
layers: inactive (white 30%) → buffer-ahead (white 50%) → active progress
(`primary`). 14 dp thumb (`primary`), only visible **while dragging**.
Floating time indicator above the thumb while dragging: 60 dp pill
(`Color.Black @ 0.7f`, 6 dp corners, 11 sp white text).
`detectHorizontalDragGestures` — live `scrubPosition`, `onSeekTo` on
`onDragEnd`.

### 4.6 Auto-hide

Fullscreen: 4 s. Minimized: 5 s (user-requested longer). Lock: 3 s.

---

## 5. Control overlay — fullscreen

Adds more controls in the top-right (server / audio / more) and adds
volume / brightness gestures. The **top-left timestamp, bottom seekbar,
fullscreen-button-on-right** zones are identical to minimized (§4) —
only the top-right expands.

```
┌──────────────────────────────────────────────────────────────────┐
│ 00:00 / 24:00              [🔒 Lock]                              │ ← top-left: timestamp
│                              [🌐 Server][💬 Sub][🎵 Audio][HD Quality][⋯ More] │ ← top-right (fullscreen only)
│                       ▶ / ⏸  (center, 72dp)                      │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ⊙                  ⛶ (minimize) │ ← bottom: seekbar + minimize
└──────────────────────────────────────────────────────────────────┘
   ↑ edge-to-edge (system bars hidden — immersive sticky)
   ↑ gestures: horizontal seek, left-vertical brightness, right-vertical volume
```

### 5.1 Top-right icons (fullscreen only)

Five icons (left → right), all `TransparentIconButton`:

1. **Server** (🌐) — opens the resolver's compact dropdown variant
   (reuses [`video-resolver.md`](video-resolver.md) §11 data model).
2. **Subtitles** (💬) — opens the subtitle-tracks sheet (§6).
3. **Audio** (🎵) — opens the audio-tracks sheet (TBD — same pattern).
4. **Quality** (HD) — opens the quality sheet (§8).
5. **More** (⋯) — sleep timer, screenshot, subtitle/audio delay,
   playback speed, open in browser.

**Open question** (analysis §8 item 6): owner only flagged minimized
top-right (2 icons). Whether fullscreen should also be reduced to 2 is
TBD. Until decided, ship the 5-icon version (matches old project's
`FullscreenControls.kt`).

### 5.2 Gestures (fullscreen only)

- **Horizontal drag**: seek (proportional, with time-preview pill).
- **Left-vertical drag**: brightness (system setting).
- **Right-vertical drag**: volume (system stream).
- **Pinch out**: TBD — could maximize/minimize (disabled by default).
- These do NOT exist on the mini-player (intentionally minimal — see
  [`watch-page.md`](watch-page.md) §4.2).

---

## 6. Subtitle-tracks bottom-up menu (component §1 — no drag handle)

### 6.1 Layout

```
┌─────────────────────────────────────────────────────────┐
│ Subtitles                          (titleMedium, Bold)  │ ← NO drag handle
│─────────────────────────────────────────────────────────│   (principle #2 — NEW)
│ [⚙]  Subtitle Settings                          >       │ ← navigates to §7
│─────────────────────────────────────────────────────────│
│ Subtitle track   (labelMedium, SemiBold, onSurfaceVar)  │
│  ┌──────┐ ┌──────────┐ ┌──────────┐  …                  │ ← FlowRow of chips
│  │ Off  │ │ English  │ │ Japanese │                     │
│  └──────┘ └──────────┘ └──────────┘                     │
│   (outline   (FilterChip,                               │
│    AssistChip  filled when                              │
│    when sel.)  selected)                                │
└─────────────────────────────────────────────────────────┘
```

### 6.2 Sheet chrome (component §1)

- `ModalBottomSheet` with `dragHandle = null` (principle #2 — hard rule;
  the old project's `PlayerSheet` did NOT pass this; the new project
  MUST).
- `skipPartiallyExpanded = true`. Top corners:
  `RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)`.
- Container color: `surfaceContainerLow`. 20 dp horizontal / 8 dp
  vertical padding. Title: `titleMedium` / Bold / `onSurface`, 12 dp
  bottom padding.
- **Partial height** (principle #3) — `heightIn(max = ~70% viewport)`.

### 6.3 "Subtitle Settings" navigation row

`Row(padding(horizontal = 16.dp, vertical = 14.dp), clickable)`. Left:
`Settings` icon (`primary`, 20 dp) + "Subtitle Settings" label
(`bodyLarge`, Medium, `onSurface`). Right: `ChevronRight` icon
(`onSurfaceVariant`, 20 dp). Tap → swaps to the subtitle-settings sheet
(§7). `HorizontalDivider(padding(horizontal = 16.dp), outlineVariant)` below.

### 6.4 Chip-based track selection

If `tracks.size <= 1`: `bodySmall` `onSurfaceVariant` empty-state message.
Else: `Text("Subtitle track", labelMedium, SemiBold, onSurfaceVariant)` +
`FlowRow(spacedBy = 8.dp h+v)`. **Off** chip: `AssistChip`, 1 dp `outline`
border (2 dp `primary` when selected), check icon when selected. **Language
track** chips: `FilterChip`, M3 fills with selected-state color when
active, otherwise tonal. Selection indicator: `Check` icon as leading
icon on the selected chip.

---

## 7. Subtitle-settings bottom-up menu (the "prime example")

The owner calls this sheet a **"prime example of design preferences."**
Hits every principle: partial height (#3), sectioned layout (#10),
tappable value chips → custom keypad (#12, component §4), live-apply
(#7), no clutter.

### 7.1 Layout

```
┌─────────────────────────────────────────────────────────┐
│ Subtitle Settings              (titleMedium, Bold)       │ ← NO drag handle
│─────────────────────────────────────────────────────────│   (principle #2 — NEW)
│  ▼ scrolls internally if it overflows the 450dp max ▼   │
│  Typography                       (titleSmall, primary)  │ ← §9 header
│   Font                                  ▾ Sans Serif      │ ← FontSelectorRow
│   Font size        [55]    ━━━━━━●━━━━━━━━━━━━━━━━━━━    │ ← TappableSliderRow
│   Scale            [1.0x]  ━━●━━━━━━━━━━━━━━━━━━━━━━━    │ ← TappableSliderRow
│   Border size      [3]     ━●━━━━━━━━━━━━━━━━━━━━━━━    │ ← TappableSliderRow
│   Bold / Italic                 [⬤─] / [─⬤]              │ ← CompactSwitchRow
│  Colors                           (titleSmall, primary)  │
│   Text / Border / Background     ■ #FFFFFFFF / …         │ ← ColorPickerRow
│  Position & Misc                  (titleSmall, primary)  │
│   Position / Shadow offset      [80%] / [2]   ━━●━━━━━   │ ← TappableSliderRow
│   Override ASS styling            [─⬤]                    │ ← CompactSwitchRow
│   Delay            (−) [120ms] (+)                        │ ← DelayStepperRow
└─────────────────────────────────────────────────────────┘
```

### 7.2 Sheet chrome

Same as §6.2 PLUS `heightIn(max = 450.dp)` (partial height — principle
#3, hard rule — port from old project, tuned at owner request).
`verticalScroll(rememberScrollState())` — content scrolls internally.

### 7.3 Section + row primitives (port verbatim)

- `SectionHeader(title)` — `Text(title, titleSmall, Bold, primary)` (§9
  component, settings variant).
- `SectionDivider` — `HorizontalDivider`, `outlineVariant` @ 50% alpha,
  6 dp vertical padding. `SectionSpacer` — 20 dp `Spacer`.

| Primitive | Use case | Layout |
|---|---|---|
| `TappableSliderRow` | Numeric range + precise input | label + value chip (right) on row 1, slider on row 2; chip opens keypad (§7.5). |
| `CompactSwitchRow` | Boolean toggle | label + M3 `Switch`. |
| `ColorPickerRow` | Color value | label + (24 dp swatch + 1 dp outline + `#AARRGGBB` hex); tap opens `ColorPickerSheet`. |
| `DelayStepperRow` | Discrete-step + precise input | label + (− / value chip / +); 100 ms step, clamped ±5000 ms; chip opens keypad. |
| `FontSelectorRow` | Enum dropdown | label + full-width `Surface(RoundedCornerShape(8.dp), surfaceContainerHigh)` + `ArrowDropDown`; tap opens `DropdownMenu`. |

### 7.4 Tappable value chip

`Surface(RoundedCornerShape(6.dp), surfaceContainerHighest)`, `clickable`.
`Text(value, bodySmall, SemiBold, primary)` — value text in `primary`.
Tap → opens the custom keypad (§7.5).

### 7.5 Custom numeric keypad (component §4)

Bottom-up sheet (component §1 — no drag handle, partial height ~40%
viewport). NOT a center popup — the video stays visible behind it so
the user sees subtitle changes in real time as they type.

```
│ Font size                            (labelMedium, ... ) │
│ ┌────┐ ┌─────────────────────┐ ┌────┐                   │
│ │ −  │ │        55           │ │ +  │                   │ ← Stepper row
│ └────┘ └─────────────────────┘ └────┘                   │
│ ┌──┬──┬──┬──────────┐                                    │
│ │ 1│ 2│ 3│          │                                    │
│ ├──┼──┼──┤  DEL     │ ← 112 dp tall, spans 2 rows        │
│ │ 4│ 5│ 6│ (backspace│                                   │
│ ├──┼──┼──┤  icon)   │                                    │
│ │ 7│ 8│ 9├──────────┤                                    │
│ ├──┴──┴──┤          │                                    │
│ │         │   OK     │ ← 112 dp, primary bg, onPrimary    │
│ │    0    │ (check   │   (spans 2 rows)                  │
│ │         │  icon)   │                                   │
│ └─────────┴──────────┘                                   │
```

4-column grid: 3 columns of numbers (1–9, single wide `0`), 1 column
for DEL (top, 112 dp, backspace icon, `surfaceContainerHigh`) + OK
(bottom, 112 dp, `primary` bg, `onPrimary` check icon). 8 dp spacing.
`RoundedCornerShape(14.dp)`. Min height 52 dp. `shadowElevation =
1 dp` numbers, `2 dp` action buttons. Number buttons: `surfaceContainerHigh`
bg, `onSurface` text, `headlineSmall` / SemiBold.

Live-apply: `LaunchedEffect(input) { onLiveChange(liveValue) }` fires on
every keystroke — the slider value AND the MPV subtitle property both
update as the user types. Input is a `String` (allows empty, leading
zeros). Max 8 digits. Empty → falls back to `initial` (no crash). `OK`
parses, clamps to `[min, max]`, fires `onConfirm`.

Full spec: component §4 in
[`../02-components/components.md`](../02-components/components.md).

### 7.6 Live-apply wiring (principle #7 — hard rule)

Every setting editable while watching is **applied to MPV live** — NOT
deferred to a "Save" button: keypad fires `onLiveChange` on every
keystroke; sliders on every change; switches on every toggle; color
pickers on every drag. The video behind the sheet shows the result
immediately. Hard principle for any in-playback settings UI.

---

## 8. Quality bottom-up menu (component §1 — no drag handle)

### 8.1 Two display modes (port from old project — KEEP)

Controlled by `qualitySheetDisplayMode` preference.

**"current" mode** (default — only qualities for current server + audio):

```
┌─────────────────────────────────────────────────────────┐
│ Quality                             (titleMedium, Bold)  │ ← NO drag handle
│─────────────────────────────────────────────────────────│   (principle #2 — NEW)
│  1080p                                                  │
│  Default • Sub                  ✓                       │ ← SheetOption (selected)
│  720p                                                   │
│  Default • Sub                                          │ ← SheetOption
│  480p                                                   │
│  Default • Sub                                          │ ← SheetOption
└─────────────────────────────────────────────────────────┘
```

**"all" mode** (every quality from every server + audio version,
grouped — same 3-tier Server → Audio → Quality hierarchy as the
resolver sheet — see [`video-resolver.md`](video-resolver.md) §5).
Server section headers (`titleSmall, primary`) → audio subheaders
(`labelMedium, SemiBold, onSurfaceVariant`) → `SheetOption` rows.

### 8.2 Sheet chrome + SheetOption row

- Same chrome as §6.2 (component §1 — no drag handle, partial height).
  Old project's `PlayerSheet` did NOT pass `dragHandle = null` — new
  project MUST (principle #2 — hard rule).
- `SheetOption`: two-line row — title (resolution, e.g. "1080p") +
  subtitle (server • audio version). Selected → `primary` + Bold,
  textual `✓` on the right. Unselected → `onSurface` + Medium. 6 dp
  vertical padding.

### 8.3 Selection match — by `videoTitle`, NOT `videoUrl` (HARD RULE)

Match the selected video by `videoTitle` (stable across re-resolutions),
NOT `videoUrl`. Proxied `localhost:PORT` URLs change every resolution —
matching by URL would de-select the current video on every background
refresh.

---

## 9. Below the player (watch page only — NOT fullscreen)

In fullscreen, the player covers the whole screen. The "below the player"
block lives on the **watch page** (see [`watch-page.md`](watch-page.md)).
For completeness, the order is **episode number → title → release date
→ description**, then a `[Server ▾] [Audio ▾]` row (unified with
[`video-resolver.md`](video-resolver.md) §11), then the Episodes list
(component §6).

### 9.1 Order (HARD RULE — port verbatim)

1. **Episode number** — `Surface(RoundedCornerShape(8.dp),
   surfaceContainerHigh)` pill with `Text("EPISODE N", labelMedium,
   Bold, primary)`. Quiet visual anchor.
2. **Title** — `titleLarge`, Bold, `onSurface`.
3. **Release date** — `bodySmall`, `onSurfaceVariant`. Formatted
   `MMM d, yyyy` (locale-aware). Hidden if `date_upload <= 0`.
4. **Description** — `bodyMedium`, `onSurfaceVariant`, `maxLines = 3`,
   `overflow = Ellipsis`. Hidden if summary blank.

### 9.2 Gradient + blur fade-out zone (principle #4 — improvement)

Old project has a 35 dp gradient-only fade-out zone between the
mini-player and the LazyColumn below (`PlayerScreen.kt:591-603`):
`Brush.verticalGradient` from `background` (top) → `background @ 85%`
(mid) → transparent (bottom). **BLUR NOT IMPLEMENTED in old project.**

New project MUST add a real **blur** (principle #4 — owner flagged it).
Options (TBD in `03-themes/`): `Modifier.blur` on a `GraphicsLayer`
snapshot (API 31+), separate blurred Bitmap, or `RenderEffect`. Below
API 31, blur is a no-op; gradient alone still applies.

---

## 10. What the owner likes (KEEP — from analysis §1.3, §3.4, §4.5, §6.4)

- **Floating pill-shaped top bar** with back / title / settings.
- **Rounded video container** (14 dp) on the themed background.
- **Dynamic theming from AniList cover color** (`03-themes/` §6).
- **LazyColumn scroll-as-one-unit** (YouTube-style) — episode details +
  dropdowns + episode list scroll together (watch page).
- **Episode details block ordering** (episode # → title → date →
  description — §9.1).
- **35 dp fade-out gradient** (upgraded to gradient + blur — §9.2).
- **Minimized-controls layout** (timestamp TL, seekbar bottom, fullscreen
  right, sub+quality TR — §4).
- **Subtitle-settings sheet** as the "prime example" (partial height,
  sectioned, tappable chips → custom keypad, live-apply — §7).
- **Quality sheet** two display modes (current / all — §8.1).
- **Textual `✓` selection indicator** in `SheetOption` (§8.2).
- **Selection match by `videoTitle`** (not `videoUrl` — §8.3).

---

## 11. What to improve (from analysis §1.4, §3.4, §4.6, §6.4 + this doc)

- **Remove `.statusBarsPadding()`** from the floating top bar (minimized),
  the video area when `showTopBar == false`, AND the fullscreen controls
  top row (§3).
- **Add a real blur effect** under the watch-page mini-player (§9.2).
- **Pass `dragHandle = null`** on the subtitle-tracks sheet AND the
  quality sheet (old project's `PlayerSheet` did NOT — principle #2 hard
  rule).
- **Subtitle-settings sheet drag handle** — old project kept it; new
  project removes it for principle #2 consistency (open question —
  analysis §8 item 1).
- **Unify resolver + player-side server/audio dropdowns** — same
  component, different surface (see [`video-resolver.md`](video-resolver.md)
  §11).
- **Reduce fullscreen top-right to 2 icons?** — open question (analysis
  §8 item 6). Until decided, ship the 5-icon version.
- **Custom keypad toggle** — old project's `useCustomKeypad` preference
  is mentioned in KDoc but not wired (keypad always used). Decide: keep
  as the only option (current) or restore the toggle.
- **Localization** — Moko Resources or stock `strings.xml`.

---

## 12. Accessibility

- All control overlay buttons have content descriptions ("Play", "Pause",
  "Subtitles", "Quality", "Fullscreen", etc.).
- Seekbar has `Role.Slider` semantics with current position + duration
  announced.
- Bottom-up menu rows are `Role.Button` with full content descriptions.
- Custom keypad number keys announce the digit; DEL → "Backspace";
  OK → "Confirm".
- Gestures (seek, brightness, volume) have button alternatives in the
  "more" sheet for users who can't swipe.
- Min tap target 48 dp on all overlay buttons (36 dp visual + 12 dp
  invisible padding).

---

## See also

- [`../01-principles/core-principles.md`](../01-principles/core-principles.md)
  — principles #1, #2, #3, #4, #7, #11, #12.
- [`../02-components/components.md`](../02-components/components.md) —
  components §1 (bottom-up menus), §4 (custom numeric keypad), §8 (live
  preview panel — partial reuse), §9 (section header).
- [`watch-page.md`](watch-page.md) — the watch page that hosts the
  mini-player; maximizing it opens this fullscreen player.
- [`video-resolver.md`](video-resolver.md) §11 — the player-side
  server/audio dropdowns reuse the resolver's data model.
- [`../03-themes/themes-and-colors.md`](../03-themes/themes-and-colors.md)
  §6 — cover-color theming (applied to the player chrome).
- `DOCS/04-design-decisions.md` — ADR-012 (watch page → maximize →
  fullscreen), ADR-015 (custom design language).
- `OLD_ANIKUTA/ANALYSIS/player-and-subtitle-screens.md` — source
  analysis (read-only structural reference).
