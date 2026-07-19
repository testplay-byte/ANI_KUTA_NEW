# 04 — History Screen

> The user's chronological "what I just watched" feed: a **Continue watching**
> carousel at the top followed by time-bucketed **Today / Yesterday / This
> Week / Earlier** sections of recent entries. Re-entry point for resume.
>
> **ADR ref:** ADR-015 (custom M3-inspired design language), ADR-017
> (floating bottom nav, present on this screen).
>
> **Principle refs:** #1 (edge-to-edge top bar), #5 (watched = grayscale +
> blur, on episode thumbnails where applicable), #6 (accent-color
> left-aligned section headers), #9 (floating bottom nav).
>
> **Component refs:** §6 (episode row), §9 (section header). The screen
> reuses the floating top-bar pattern (see `03-themes/`).
>
> **Status:** STRUCTURE and the section-header pattern are fixed (the owner
> called the headers "proper"). The continue-watching card overlay text and
> card placement are flagged for rework — see §4 and §5.

---

## 1. Owner's brief (verbatim, distilled)

- "Continue watching" at the very top of the screen.
- Below that, time-grouped sections: **Today**, **Yesterday**, **This
  Week**, **Earlier**.
- The section headers — accent-colored and **left-aligned** — are
  **"proper"**. Keep them.
- The text on top of the continue-watching covers is **"not proper"**.
  Improve it.
- The card placement of the continue-watching row **"could be made
  better"**. Improve it.

Reference: `OLD_ANIKUTA/ANALYSIS/history-extensions-settings-screens.md`
section 1 (`HistoryScreen.kt` + `HistoryViewModel.kt`).

---

## 2. Position in the app

- One of the configurable bottom-nav tabs (ADR-017). Tab id: `history`;
  default label "History"; icon `history`.
- Hidden on: fullscreen player, fullscreen reader, onboarding.
- Reachable from: bottom nav (when configured), the "Continue watching"
  cards on the Home screen deep-link here on long-press (TBD).

---

## 3. Layout (ASCII)

```
┌─────────────────────────────────────────────────────────────┐
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │ ← status bar
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Floating top bar (RoundedCornerShape 20dp,           │  │
│  │   surfaceContainerHigh, tonalElev 3, shadow 6)        │  │
│  │   History                                  ⋮ overflow │  │
│  │   titleLarge Bold                  → Clear all history │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  ▌ Continue watching            ← §9, accent bar + label    │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌──────────┐  │
│  │  16:9      │ │  16:9      │ │  16:9      │ │  16:9    │  │
│  │  cover     │ │  cover     │ │  cover     │ │  cover   │  │
│  │            │ │            │ │            │ │          │  │
│  │ ─footer─── │ │ ─footer─── │ │ ─footer─── │ │ ─footer─ │  │
│  │ Title      │ │ Title      │ │ Title      │ │ Title    │  │
│  │ EP 5 · 8m  │ │ EP 12· 4m  │ │ EP 2 · 22m │ │ EP 1·2m  │  │
│  │ ▰▰▰▱▱▱▱▱  │ │ ▰▰▱▱▱▱▱▱  │ │ ▰▰▰▰▰▱▱▱  │ │ ▰▱▱▱▱▱▱▱│  │
│  └────────────┘ └────────────┘ └────────────┘ └──────────┘  │
│   ← LazyRow, full-bleed, snap-to-card, peek next card       │
│                                                             │
│  ▌ Today                  ← §9, 4×20dp primary bar + title  │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ [72×40 thumb] Episode title              45%            ││
│  │                2 min ago · EP 5                          ││
│  └─────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────┐│
│  │ [72×40 thumb] Another title              10%            ││
│  │                45 min ago · EP 12                        ││
│  └─────────────────────────────────────────────────────────┘│
│                                                             │
│  ▌ Yesterday                                                │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ [72×40 thumb] …                          78%            ││
│  └─────────────────────────────────────────────────────────┘│
│                                                             │
│  ▌ This Week                                                │
│  ...                                                        │
│  ▌ Earlier                                                  │
│  ...                                                        │
│                                                             │
│  (16dp bottom padding before floating bottom nav)           │
│                                                             │
│        ┌─────────────────────────────────────────┐          │
│        │  Floating bottom nav (§5)               │          │
│        └─────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────┘
```

### 3.1 Vertical structure (top → bottom)

1. **Floating top bar** — `History` title + overflow (`Clear all
   history`). Edge-to-edge per principle #1: the bar's *background*
   extends under the status bar; the *content* gets `statusBarsPadding`.
2. **Continue watching** (only if non-empty) — §9 header + `LazyRow` of
   `ContinueWatchingCard`s. See §4.
3. **Time-bucketed sections** — one §9 header per non-empty bucket,
   each followed by a `Column` of `HistoryEntryRow`s. See §6.
4. **Floating bottom nav** (§5).

### 3.2 Grouping logic (ViewModel)

Calendar-day-based, not 24-hour deltas — "Today" always means the current
calendar day regardless of when the user opens the screen.

| Bucket | Definition |
|---|---|
| Today | Same calendar day as `now` |
| Yesterday | The day before Today |
| This Week | 2–7 calendar days ago |
| Earlier | Older than 7 days |

`continueWatching` = entries whose `progressFraction < 0.9f`. Once an
episode crosses 90% watched, it leaves the carousel and only appears in
the time buckets.

---

## 4. Continue-watching card — KEEP carousel, REWORK overlay text

### 4.1 What the owner flagged

The old implementation overlays **title + remaining-minutes + progress
bar** directly on the cover with a harsh 75%-black bottom gradient, in
`labelMedium` (too small). The owner called this **"not proper"**.

### 4.2 What to keep

- The carousel position (right under the top bar).
- The horizontal `LazyRow` form.
- The 16:9 aspect ratio per card.
- The progress-bar affordance (it's the right info to surface).

### 4.3 What to improve (design language)

| Problem (old) | Fix (new) |
|---|---|
| `labelMedium` title — too small to read | `titleSmall` Bold, white, max 2 lines ellipsized |
| 75% black gradient — too harsh, competes with art | Soft **50% black** gradient over the **bottom 40%** of the cover only — upper 60% stays clean |
| Title, "X min left", **and** progress bar all stacked in the bottom overlay → cluttered | Split: title + `EP n · X min left` in the bottom overlay; progress bar becomes a **thin 2dp strip along the very bottom edge** of the card (full-width, accent fill) |
| `Color.White` progress bar on white-ish gradient — low contrast | Progress fill = `colorScheme.primary`; track = `Color.White.copy(alpha = 0.25f)` |
| Cards sized 200×112 dp with 10dp spacing → small + dense | Card width = **`0.62 × screenWidth`** (next card peeks ~24dp, signaling "swipe me"); spacing 12dp; first/last item 16dp horizontal padding |
| Gradient covers full height | Gradient only over bottom 40%, anchored `BottomStart`–`BottomEnd` |
| No snap — fling lands anywhere | `rememberSnapFlingBehavior` so the row always settles with one card leading-edge aligned |

### 4.4 New card visual (ASCII)

```
┌──────────────────────────────────┐
│                                  │
│        full-color cover          │  ← upper 60%: clean, no overlay
│                                  │
│                                  │
├──────────────────────────────────┤  ← gradient begins here (40%)
│ Title                            │      0% → 50% black
│ EP 5 · 8 min left                │
└──────────────────────────────────┘
████████████░░░░░░░░░░░░░░░░░░░░░░   ← 2dp accent-color progress strip
                                      along the very bottom edge
```

The progress strip is part of the card surface, not inside the gradient
overlay — it visually anchors the card's "resumability" without fighting
the title for the same overlay space.

---

## 5. Continue-watching card placement — REWORK

The owner said the placement "could be made better." Three improvements:

1. **Hero-peek width.** Cards are no longer a fixed 200dp. They're
   `0.62 × screenWidth` wide, with 16:9 height. On a 411dp-wide phone
   that's ~255×143 dp — large enough to read the title without leaning in,
   small enough that the next card peeks ~24dp into view, signaling
   "swipe me".
2. **Snap-to-card fling.** Use `LazyRow` + `rememberSnapFlingBehavior`
   so a swipe always settles with one card leading-edge aligned. No more
   landing halfway between cards.
3. **Sticky-peek first/last.** The first card has 16dp leading inset; the
   last card has 16dp trailing inset — so the row never visually clips
   into the screen edge.

Considered and rejected: a single full-width hero banner (would lose the
"see what's next" affordance); a 2-row staggered grid (breaks the
linear-resume mental model). The hero-peek carousel keeps the linear
swipe model and just makes each card readable.

---

## 6. Time-bucketed sections — KEEP the headers, KEEP the row pattern

### 6.1 The section header — KEEP ("proper")

The owner explicitly called the accent-color header bar on the left of
each section header **"proper"** — keep it. This is §9.

```
▌ Today          ← 4×20dp primary pill, 8dp gap, titleLarge Bold, left-aligned
```

Spec (matches the old project's `HistorySectionHeader`):

- 4dp × 20dp `primary`-colored `Surface`, `RoundedCornerShape(2.dp)`.
- 8dp spacer.
- Title text: `MaterialTheme.typography.titleLarge`,
  `FontWeight.Bold`, default text color (NOT accent — only the bar is
  accent; the title stays in `onSurface` so the bar pops against it).
- Padding: `horizontal = 16.dp, vertical = 4.dp` (top of section).
- No divider line above or below — the accent bar IS the divider.

> The app-wide §9 component ships with a `style: SectionHeaderStyle`
> parameter — `List` (3×16dp pill + uppercase `labelMedium`) for browse /
> library / settings, and `Prominent` (4×20dp + `titleLarge`) reserved for
> History. Both are valid §9 instantiations.

### 6.2 The history entry row — KEEP

Each row in a time bucket is a `HistoryEntryRow`:

- `Surface(RoundedCornerShape(14.dp), surfaceContainerLow, tonalElev 1.dp)`.
- `Row`, height ~64dp:
  1. 72×40 dp 16:9 thumbnail (hue-derived placeholder fallback when no
     URL).
  2. 12dp spacer.
  3. `Column(weight 1f)`:
     - Title — `bodyMedium` Medium, 2 lines, ellipsized.
     - Meta — `bodySmall` `onSurfaceVariant` — "2 min ago · EP 5".
  4. 8dp spacer.
  5. Trailing percentage readout — `labelSmall` Bold `primary`,
     e.g. "45%".
- `combinedClickable`:
  - `onClick` → resume playback (jumps to the Watch page, ADR-012).
  - `onLongClick` → haptic + single-entry delete dialog.

### 6.3 Empty state, long-press, and Clear all

- **Empty state.** If `continueWatching` is empty AND all four buckets are
  empty: centered illustration + "No watch history yet" + "Episodes you
  watch will show up here." Floating top bar and bottom nav remain.
- **Long-press a row.** Confirmation dialog to remove that single entry.
  Does NOT delete the underlying watch progress — only the history entry.
- **Overflow → Clear all history.** Larger confirmation dialog listing
  how many entries will be cleared. Same preservation semantics: clears
  the history feed, NOT watch progress.

---

## 7. Owner likes (keep) vs improvements

| Aspect | Owner verdict | Notes |
|---|---|---|
| "Continue watching" section at the top | ✅ keep | Carousel position is right |
| Section headers with accent color on left (Today / Yesterday / This Week / Earlier) | ✅ keep | "Proper" — see §6.1, component §9 |
| Per-row 16:9 thumbnail + title + relative time + percent | ✅ keep | See §6.2 |
| Long-press to remove single entry; overflow → Clear all | ✅ keep | Standard destructive-action pattern |
| Floating top bar (RoundedCornerShape 20dp, tonalElev 3, shadow 6) | ✅ keep | Same family as HomeScreen + floating bottom nav (§5) |
| Text overlay on covers (title, min-left, progress bar) | ⚠️ improve | "Not proper" — see §4.3 |
| Card placement in continue-watching row | ⚠️ improve | "Could be made better" — see §5 |
| Calendar-day-based bucketing (Today = current day, not 24h delta) | ✅ keep | Matches user expectation |
| 90% completion threshold for leaving Continue watching | ✅ keep | Reasonable default; expose in Settings (simple-mode-hidden) if user wants to tune |

---

## 8. Data flow (ViewModel)

- Reactive: collects `WatchProgressStore.changes`. History updates in
  real time as the user watches — no manual refresh.
- `continueWatching` = entries with `progressFraction < 0.9f`, ordered
  by `lastWatchedAt` descending.
- `groups` = ordered buckets via `groupByTime(now)`:
  `Today` / `Yesterday` / `This Week` (2–7 days) / `Earlier`. Empty
  buckets are not rendered.
- States: `Loading` / `Empty` / `Error(message)` /
  `Success(continueWatching, groups)`.

---

## 9. What's open for the design session

- Exact `0.62 × screenWidth` ratio — may need a max cap on tablets
  (e.g. 360dp).
- Whether "X min left" should localize / show as a percentage instead.
- Whether time-bucket headers should sticky-pin during fast-scroll.
- Animation on entry removal (slide-out + reflow).
- Whether the 4×20dp `History`-variant header should be the default §9
  style app-wide, or remain a `Prominent` variant reserved for History.

---

## See also

- [`../01-principles/core-principles.md`](../01-principles/core-principles.md)
  — principles #1, #5, #6, #9.
- [`../02-components/components.md`](../02-components/components.md) — §6
  (episode row), §9 (section header), §5 (floating bottom nav).
- [`bottom-nav.md`](bottom-nav.md) — the floating nav that appears on
  this screen.
- [`watch-page.md`](watch-page.md) — the destination of a row tap
  (resume).
- `OLD_ANIKUTA/ANALYSIS/history-extensions-settings-screens.md` §1 —
  source analysis of the old project's `HistoryScreen.kt` +
  `HistoryViewModel.kt`.
- `DOCS/04-design-decisions.md` — ADR-015, ADR-017.
