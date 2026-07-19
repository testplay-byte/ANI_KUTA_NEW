# 04 — Episode List (Component)

> The episode list component. Renders a list of episodes, each row showing
> episode number, title, thumbnail, filler badge, watch status, and
> download status. The single most distinctive design decision: **watched
> episodes are grayscale + blurred** (principle #5). Used on **two
> surfaces**: the anime details screen (below the header) and the watch
> page (below the mini-player + description).
>
> **Principles applied:** #5 (watched = grayscale + blur), #6 (accent
> section headers — the "Episodes" header above the list), #11 (custom
> M3 — adapted).
>
> **Components used:** §6 (episode row — this IS the spec for §6), §9
> (section header for the "Episodes" header above the list).
>
> **Status:** STRUCTURE, BEHAVIOR, and the grayscale+blur effect are fixed
> (this doc). Sub-token refinements (exact blur radius, swipe thresholds
> on tablets) tuned in `03-themes/`.

---

## 1. Where the episode list appears

```
┌─ Anime Details screen ─────────────┐    ┌─ Watch page (ADR-012) ──────┐
│  [blurred cover header]            │    │  [mini-player 16:9]         │
│  Synopsis / Information            │    │  Episode 5 — "The Fall"     │
│  Episodes  ← §9 header             │    │  <description>              │
│   ┌─────────────────────────────┐  │    │  Episodes  ← §9 header      │
│   │ EpisodeRow #1   ← §6 row    │  │    │   ┌──────────────────────┐  │
│   │ EpisodeRow #2   ← §6 row    │  │    │   │ EpisodeRow #1        │  │
│   │ EpisodeRow #3   ← §6 row    │  │    │   │ EpisodeRow #2 ← now  │  │
│   │ …                          │  │    │   │ EpisodeRow #3        │  │
│   └─────────────────────────────┘  │    │   └──────────────────────┘  │
└────────────────────────────────────┘    └─────────────────────────────┘
```

**Both surfaces use the SAME component** (component §6) with the same
configurable display settings. The watch page highlights the currently
playing episode (§6.4). The player-side `EpisodeListView` in the old
project is a separate implementation — the new project unifies them.

---

## 2. Owner's vision

> "Watched episodes are black & white with a blur. It's a proper example
> of design preferences."

This is the owner's #1 flagged preference for the episode list. Subtle
but unmistakable; works without bright colors, strikethroughs, or large
checkmarks. The eye is drawn to the next-to-watch episode naturally.

---

## 3. The watched effect (principle #5, component §6)

### 3.1 Four configurable modes

The effect is **configurable** (in episode-layout settings, TBD). Four
modes — default is **GRAYSCALE**:

| Mode | Effect | Use case |
|---|---|---|
| `NONE` | No treatment — watched episodes look identical to unwatched. | Users who want a flat list. |
| `GRAYSCALE` (default) | Desaturate the entire row (text, icons, thumbnail, card bg) to B&W. | Owner's preferred. |
| `BLUR` | Apply a subtle blur to the entire row. | Subtle alternative. |
| `BOTH` | Grayscale AND blur. Maximum visual distinction. | Power users who want a strong signal. |

### 3.2 Default parameters (KEEP from old project)

| Parameter | Default | Why |
|---|---|---|
| `grayscaleAlpha` | `0.55f` | Slightly more than half-opacity — readable but clearly "done". |
| `blurRadiusDp` | `2f` (subtle) | Doesn't destroy legibility; just signals "watched". |
| Platform floor | API 31+ | `RenderEffect` for grayscale + `Modifier.blur` for blur. Below 31, alpha dimming only — show a settings notice. |

### 3.3 Implementation — `RenderEffect`, not `ColorFilter`

**Hard rule** (from analysis §2.4): the effect MUST use
`RenderEffect.createColorFilterEffect` via `Modifier.graphicsLayer`, NOT
`drawWithContent` + `ColorFilter.colorMatrix` on a `Paint`. The latter
only affects rasterised draw operations (images) and does NOT affect
Compose's text rendering pipeline — themed text colors stay unchanged,
giving a "half-grayscale" appearance. `RenderEffect` intercepts the
entire rendered output of the layer (text, icons, shapes, images) and
desaturates it uniformly.

### 3.4 Where the effect is applied

The effect is applied at the **outer `EpisodeRow` container level** —
NOT on individual elements. This ensures the entire row (card
background, thumbnail, title, summary, audio pills, date pill —
everything) is desaturated uniformly. Only the **swipe background**
revealed underneath during a swipe gesture is NOT desaturated (it sits
outside the effect's modifier scope).

---

## 4. Layout — single episode row

### 4.1 Rich row (when thumbnail + summary are available — the default)

```
 UNWATCHED                                  WATCHED (grayscale + blur)
┌────────────────────────────────────────┐ ┌────────────────────────────────────────┐
│  [EP 5]   ┌──────────────────────────┐ │ │  [EP 5]   ┌──────────────────────────┐ │
│  overlay  │ Title (bold, titleSmall) │ │ │  overlay  │ Title (bold, titleSmall) │ │ ← B&W + blur
│  on thumb │   [EP 5 badge]           │ │ │  on thumb │   [EP 5 badge]           │ │   applied to
│  ┌──────┐ │ ──────────────────────── │ │ │  ┌──────┐ │ ──────────────────────── │ │   entire row
│  │ THUMB│ │ [Jan 15, 2025] [S•D]     │ │ │  │ GRAY │ │ [Jan 15, 2025] [S•D]     │ │
│  │ NAIL │ │ ┌──────────────────────┐ │ │ │  │ BLUR │ │ ┌──────────────────────┐ │ │
│  │      │ │ │ Synopsis (3-line)    │ │ │ │  │      │ │ │ Synopsis (3-line)    │ │ │
│  └──────┘ │ └──────────────────────┘ │ │ │  └──────┘ │ └──────────────────────┘ │ │
│           └──────────────────────────┘ │ │           └──────────────────────────┘ │
│   ← alternating bg: surfaceContainerLow│ │   ← alternating bg: surfaceContainerLow│
│   ← rounded 12dp, 6dp h-pad, 2dp v-pad │ │   ← same shape, just desaturated      │
│   ← WatchedEpisodeEffect at container  │ │                                        │
│   ← SwipeBackground revealed under swipe│ │                                        │
└────────────────────────────────────────┘ └────────────────────────────────────────┘
```

### 4.2 Simple row (no thumbnail/summary — fallback)

When neither thumbnail nor summary is available, the row collapses to a
compact text-only layout:

```
┌────────────────────────────────────────┐
│  (5)   ┌────────────────────────────┐ │
│ circle │ Episode 5      [EP 5 badge] │ │ ← surfaceContainer bg, 8dp corners
│ badge  │                              │ │
│ 40×40  └────────────────────────────┘ │
│              [Jan 15, 2025] [S•D]      │ ← DateAudioPillsRow, 6dp below
└────────────────────────────────────────┘
```

The circle badge is a `40 × 40 dp` `Surface(CircleShape, surfaceVariant)`
containing the episode number in `labelMedium` bold.

### 4.3 Configurable positions (per `EpisodeDisplaySettings`)

All positions are user-configurable in episode-layout settings (live
preview per principle #7). Defaults match the owner's preference.

| Setting | Options | Default |
|---|---|---|
| `thumbnailPosition` | `left` / `right` | `left` |
| `thumbnailSize` | `small` (100×56) / `medium` (120×68) / `large` (160×90) | `medium` |
| `titlePosition` | `right` (beside thumb) / `below` (full-width) | `right` |
| `episodeNumberPosition` | `overlay` (on thumb, black 70% bg, white text) / `badge` (in title row, primaryContainer bg) / `circle` (left badge — simple row only) | `overlay` |
| `synopsisPosition` | `right` / `below` | `right` |
| `datePosition` | `right_above_synopsis` / `right_below_synopsis` / `below` | `right_below_synopsis` |
| `downloadButtonPlacement` | `episode_row` (outside the row, compact tall button) / `synopsis` (inside the synopsis row) | `episode_row` |
| `showThumbnails` / `showSummaries` / `showTitles` / `showDates` / `showEpisodeNumber` / `showAudioPills` | boolean | all `true` |

---

## 5. Row anatomy — every element

| Element | Style | Position |
|---|---|---|
| **Thumbnail** | 16:9 `AsyncImage`, `clip(RoundedCornerShape(10.dp))`, `surfaceContainer` placeholder while loading. | Start (or end, per `thumbnailPosition`). |
| **EP overlay** | `Surface(RoundedCornerShape(6.dp), Black.copy(alpha = 0.7f))`, `labelSmall` bold, white. "EP N" where N = `episode_number` formatted. | Top-start of thumbnail. Only when `episodeNumberPosition = "overlay"`. |
| **Title** | `titleSmall`, bold, `onSurface`, single line, `TextOverflow.Ellipsis`. | Top of the text stack. |
| **Filler badge** (NEW — improvement) | `Surface(RoundedCornerShape(4.dp), primary)` with `labelSmall` bold `onPrimary` text "Filler". Hidden if `fillermark` is null/blank. | Beside the title (or below it — TBD). |
| **Audio pills** | `S•D` short form when 2+ audio versions exist; full label (`SUB`) when 1. `Surface(RoundedCornerShape(6.dp), outlineVariant)`, `labelSmall` semibold `onSurfaceVariant`. | Beside the date. |
| **Date pill** | `bodySmall`, `onSurfaceVariant`. Formatted `MMM d, yyyy` (locale-aware). Hidden if `date_upload <= 0`. | Per `datePosition`. |
| **Synopsis** | `bodyMedium`, `onSurfaceVariant`, 3-line, expandable on tap. `surfaceContainer` bg, 8dp corners. | Per `synopsisPosition`. |
| **Download button** | Compact tall button (state-aware: download / queued / downloading / paused / error / downloaded). | Outside the row (`episode_row` placement) or beside the synopsis (`synopsis` placement). |
| **Downloaded checkmark** (NEW) | Green check icon overlay on the thumbnail corner when the episode is downloaded on disk. Aniyomi-style scannability. | Top-end of thumbnail. |
| **"Now playing" indicator** (NEW) | Primary-colored accent border + small "▶" icon on the thumbnail when this is the current episode. (Watch page only — the details screen doesn't have a current episode.) | Edge of thumbnail. |

---

## 6. Alternating card backgrounds (zebra-stripe rhythm)

The card background alternates per index — a subtle visual rhythm that
helps eye-tracking on long lists:

- `index % 2 == 0` → `surfaceContainerLow` (or the dynamic theme's
  `surfaceLow`).
- `index % 2 == 1` → `surfaceContainerHigh` (or the dynamic theme's
  `surfaceHigh`).

This is the **only** place zebra striping is used — don't apply it to
other lists (history, library, browse). KEEP from old project.

---

## 7. Gestures

The row is a full gesture handler (port from old project — KEEP all):

| Gesture | Action | Implementation |
|---|---|---|
| **Tap** | Open the video resolver sheet (which leads to the watch page — see [`anime-details.md`](anime-details.md) §8). If the episode is downloaded, skip the resolver and go straight to the watch page. | `combinedClickable(onClick = onClick)` |
| **Long-press** | Open the episode options bottom sheet (§8). Haptic `LongPress` feedback. | `combinedClickable(onLongClick = { haptic; onLongClick() })` |
| **Swipe right (80 dp)** | Toggle watched / unwatched. | `detectHorizontalDragGestures` + threshold check on `onDragEnd`. |
| **Swipe left (160 dp)** | Start / cancel download. Longer threshold to prevent accidental downloads. | Same gesture handler. |
| **Mid-drag haptic** | Single `LongPress` pulse when crossing the threshold (does NOT trigger the action — only feedback). | — |
| **Action fires on release** | Not mid-drag. Prevents accidental triggers if user swipes past threshold and drags back. | — |
| **Spring-back animation** | `spring(DampingRatioMediumBouncy, StiffnessMedium)` returns the row to rest. | — |
| **Max overshoot** | `1.3×` the threshold — prevents unreasonable dragging. | — |
| **Vertical scroll coexistence** | `detectHorizontalDragGestures` only consumes horizontal drags; vertical drags pass through to the parent `LazyColumn`. | — |
| **Swipe background reveal** | `SwipeBackground` shows a `primaryContainer` (swipe right → watched) or `secondaryContainer` (swipe left → download) background with an eye/download icon that scales up + becomes opaque as the swipe approaches the threshold. | — |

---

## 8. Long-press options bottom sheet (`EpisodeOptionsSheet`)

State-aware bottom-up menu (component §1 — no drag handle, partial
height). Shows different actions based on the episode's download status
and watched state:

| Episode state | Options shown |
|---|---|
| Downloaded (queue or disk) | Play downloaded / Delete download (destructive) / Mark as unwatched |
| Downloading / Queued / Resolving / Muxing / Reconnecting | Cancel download (destructive) / Mark as watched or unwatched |
| Paused | Resume / Cancel download (destructive) / watched toggle |
| Error | Retry / Cancel download (destructive) / watched toggle |
| Not downloaded | Download / watched toggle |

### 8.1 Compose gotcha (port from old project)

The options sheet MUST be **extracted into a dedicated composable that
receives `episode` as a parameter** — NOT an inline `longPressEpisode?.let
{ ModalBottomSheet(...) }` block. Compose did not reliably recompose the
inline `?.let` block in the old project's 800-line `DetailScreen`
function. Extracting the sheet and passing `episode` as a parameter
makes Compose track the parameter change and reliably recompose whenever
`episode` transitions from `null` to non-null.

---

## 9. Watched state — persistence & reactivity

- Watched state tracked in `EpisodeSeenStore` keyed by `"$anilistId:$episodeUrl"`.
- The store is reactive via a `changes: Flow<Set<String>>`; the screen
  collects it in a `LaunchedEffect` so watched toggles from the player
  or another tab update the row instantly.
- The store also re-reads on `ON_RESUME` (returning from the player
  refreshes the set without depending on Flow timing).
- New `SEpisode` objects MUST be created when enriching metadata (Compose
  `LazyColumn` skips recomposition for same object references).

---

## 10. What the owner likes (KEEP — from analysis §2.9)

- **Watched = grayscale + blur** (configurable: none / grayscale / blur /
  both) — the owner's #1 flagged preference.
- **Effect applied at the row container level** — uniform desaturation
  across text, icons, thumbnail, card background. No "half-grayscale".
- **Default `grayscale` mode at `alpha=0.55f`** — readable but clearly
  "done".
- **`BOTH` mode** for maximum distinction.
- **GPU `RenderEffect` approach** (not `drawWithContent` + `ColorFilter`)
  — the only way to desaturate Compose's text rendering pipeline.
- **Alternating card backgrounds** — subtle zebra-stripe rhythm.
- **Rich layout** with thumbnail + title + audio pills + date + synopsis
  — matches modern streaming apps (Netflix, Crunchyroll).
- **All positions configurable** — power users can rearrange; defaults
  match the owner's preference.
- **Swipe gestures with mid-drag haptic + action-on-release** — polished;
  prevents accidental triggers.
- **Long-press bottom sheet** with state-aware options — better than a
  context menu; clear destructive-action coloring.
- **Adaptive audio pills** (`S•D` short form when 2+) — guarantees they
  fit on one row. Avoid `BoxWithConstraints` inside
  `Row(height(IntrinsicSize.Min))` (SubcomposeLayout intrinsic crash —
  analysis §2.6).

---

## 11. What to improve (from analysis §2.10 + this doc)

- **API 31+ requirement** — either bump minSdk to 31 (recommended) or
  show a settings notice explaining the limitation on older devices.
- **Proper audio-version taxonomy** — replace the fragile `scanlator`
  contains-check (matches "HSUB" as both H and S) with a `AudioVersion`
  class derived from the resolver's `VideoTitleParser.AudioVersion`
  (see [`video-resolver.md`](video-resolver.md) §6).
- **Filler badge** — display `SEpisode.fillermark`. (NEW — the task
  description explicitly asks for this; the old project never displayed
  it.)
- **"Currently watching" indicator** — primary-colored accent border or
  "now playing" icon on the current episode (watch page only).
- **Downloaded checkmark on the thumbnail** — Aniyomi-style scannability.
- **Avoid `IntrinsicSize.Min` + `Row(weight(1f))` + `Surface(weight(1f))`**
  — fragile. Use a `Flow` or fixed max-width pill cluster instead.
- **Unify the player-side `EpisodeListView`** with this component — same
  data, same component, different surface. Old project has two separate
  implementations.

---

## 12. Accessibility

- Each row is a clickable surface with `Role.Button` semantics. Content
  description: "Episode {number}, {title}, {watched|unwatched},
  {downloaded|not downloaded}".
- Swipe gestures have an alternative: long-press → options sheet →
  "Mark as watched" / "Download" actions (for users who can't swipe).
- The grayscale+blur effect must not reduce text contrast below WCAG AA
  — the `0.55f` alpha default is tuned for this.
- Filler badge has `contentDescription = "Filler episode"`.

---

## See also

- [`../01-principles/core-principles.md`](../01-principles/core-principles.md)
  — principle #5 (watched = grayscale + blur), #6, #7 (live preview in
  episode-layout settings), #11.
- [`../02-components/components.md`](../02-components/components.md) —
  component §6 (this IS the spec for §6), §9 (Episodes section header),
  §1 (long-press options bottom sheet).
- [`anime-details.md`](anime-details.md) — the primary surface that hosts
  this list (below the blurred cover header).
- [`watch-page.md`](watch-page.md) — the second surface that hosts this
  list (below the mini-player + description). Highlights the current
  episode.
- [`video-resolver.md`](video-resolver.md) — the sheet opened on row tap.
- [`player.md`](player.md) — fullscreen player (reachable from the
  watch page).
- `OLD_ANIKUTA/ANALYSIS/details-episodes-resolution-screens.md` §2 —
  source analysis (read-only structural reference).
