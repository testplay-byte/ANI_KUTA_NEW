# 04 — Episode Layout Settings Screen

> The "Episode layout" subpage of Details settings. Hosts the **beautiful
> toggles** the owner praised: 2-way and 3-way `StyledSegmentedRow`
> segmented controls for positioning each element of the episode row,
> plus a 4-way `SelectableOptionCard` for watched-episode appearance.
> Also documents the **Metadata fetching** subpage's per-field toggles.
>
> The owner: *"beautiful toggles: 3-way toggles and 2-way toggles."*
>
> **ADR ref:** ADR-015 (custom M3-inspired design language), ADR-018
> (feature parity + simple mode hides advanced groups).
>
> **Principle refs:** #5 (watched = grayscale + blur, configurable
> here), #7 (live preview, sticky), #8 (multi-way toggles via §2 / §3),
> #10 (settings divided into sections + simple mode).
>
> **Component refs:** §2 (3-way toggle), §3 (2-way toggle), §8 (live
> preview panel), §9 (subdued settings-variant section header). The
> `StyledSegmentedRow` defined here is the canonical §2 / §3
> implementation; the `SelectableOptionCard` is the canonical ≥4-option
> toggle.
>
> **Status:** STRUCTURE and toggle VISUAL RULES are fixed — the owner
> called the toggles "beautiful." Known improvement: the watched-episode
> appearance does NOT yet apply to the live preview (see
> `details-settings.md` §6 — that doc owns the fix spec).

---

## 1. Owner's brief (distilled)

- **Beautiful toggles**: 2-way and 3-way segmented controls with labels
  on each segment. Active segment filled with accent color; inactive
  are transparent.
- The toggle styles are a **design-language reference** — document them
  precisely so other settings screens can reuse the same primitive.
- Episode layout uses 2-way toggles for binary positions (Right / Below,
  Left / Right, Overlay / Badge, etc.) and 3-way toggles for ternary
  positions (Above / Below / Full, Small / Medium / Large).
- Watched-episode appearance uses a 4-way `SelectableOptionCard`
  (None / Grayscale / Blur / Grayscale + Blur).
- Metadata fetching uses a master toggle + per-field sub-toggles
  revealed via `AnimatedVisibility`.

Reference: `OLD_ANIKUTA/ANALYSIS/history-extensions-settings-screens.md`
section 4.

---

## 2. Position in the app

- Reached from: **More → Settings → Details → Episode layout** (a
  Customize subpage of the Details settings hub).
- Sibling subpages: **Episode display** (which elements show/hide) and
  **Metadata fetching** (which fields to fetch). All three share the
  sticky-preview + scrollable-toggles skeleton
  (`details-settings.md` §7).
- Backing out returns to the Details settings hub.

---

## 3. Layout (ASCII) — Episode layout subpage

```
┌─────────────────────────────────────────────────────────────┐
│  ← Episode layout                                           │ ← top bar
│  LIVE PREVIEW            ← label: primary, labelMedium Bold │ ← sticky
│  ┌─────────────────────────────────────────────────────────┐│
│  │  EpisodeRowPreview(… all layout prefs applied …)        ││
│  └─────────────────────────────────────────────────────────┘│
│  ─── scrollable settings below ──────────────────────────── │
│  ┌── "Text content" ──────────────────────────────────────┐ │
│  │  Title     [ Right | Below ]   ← 2-way §3              │ │
│  │  Synopsis  [ Right | Below ]                           │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌── "Badges & pills" ───────────────────────────────────┐  │
│  │  Date & audio pills  [ Above | Below | Full ]  3-way §2│  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌── "Episode number" ───────────────────────────────────┐  │
│  │  Position  [ Overlay | Badge ]   ← 2-way §3           │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌── "Thumbnail" ────────────────────────────────────────┐  │
│  │  Side  [ Left | Right ]              ← 2-way §3       │  │
│  │  Size  [ Small | Medium | Large ]    ← 3-way §2       │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌── "Page layout" ──────────────────────────────────────┐  │
│  │  Anime info  [ Above eps | Below eps ]   ← 2-way §3   │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌── "Download button" ──────────────────────────────────┐  │
│  │  Placement  [ Episode row | Synopsis ]   ← 2-way §3   │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

Each card holds a `LabeledSection` (title + description + control) per
toggle — see §5.

---

## 4. The `StyledSegmentedRow` — canonical §2 / §3 implementation

This is the toggle the owner called **"beautiful."** It implements both
§2 (3-way) and §3 (2-way) — the only difference is the number of
options. Both share the same container, pill, color, and typography
rules.

### 4.1 ASCII — 2-way toggle

```
┌─────────────────────────────────────────────┐
│  ┌─────────────┬──────────────┐             │  ← container
│  │    Right    │    Below     │             │
│  └─────────────┴──────────────┘             │
└─────────────────────────────────────────────┘
       ↑                ↑
   selected         unselected
   primary bg       transparent
   onPrimary text   onSurfaceVariant text
   Bold             Medium
```

### 4.2 ASCII — 3-way toggle

```
┌─────────────────────────────────────────────┐
│  ┌─────────┬─────────┬─────────┐            │  ← container
│  │  Above  │  Below  │  Full   │            │
│  └─────────┴─────────┴─────────┘            │
└─────────────────────────────────────────────┘
       ↑         ↑         ↑
   selected  unselected  unselected
```

### 4.3 Visual rules (HARD — do not deviate)

| Property | Value |
|---|---|
| Container fill | `surfaceVariant.copy(alpha = 0.5f)` |
| Container shape | `RoundedCornerShape(12.dp)`, inner padding `4.dp` |
| Pill arrangement | `Row(spacedBy = 4.dp)`, each pill `weight(1f)` |
| Pill shape | `RoundedCornerShape(8.dp)`, vertical padding `8.dp` |
| Selected pill fill | `colorScheme.primary` (NOT `primaryContainer` — rejected as "too dark/blue") |
| Selected pill text | `colorScheme.onPrimary`, `FontWeight.Bold` |
| Unselected pill fill | `Color.Transparent` (container shows through) |
| Unselected pill text | `colorScheme.onSurfaceVariant`, `FontWeight.Medium` |
| Pill text style | `labelMedium`, `TextAlign.Center` |

### 4.4 Usage contracts

Both 2-way and 3-way variants take `options: List<Pair<String, Boolean>>`
(label, isSelected) and an `onSelect: (Int) -> Unit`. The caller owns
the label ↔ pref-value mapping (e.g. `"right_above_synopsis"` for the
date-position 3-way). The toggle itself is category-agnostic.

---

## 5. The `LabeledSection` — title + description + control

Each toggle inside a `SettingsGroupCard` is wrapped in a `LabeledSection`:

```
   Title                                ← titleMedium SemiBold
   Where the episode title appears      ← bodySmall onSurfaceVariant
   ┌─────────────┬──────────────┐       ← the control (§2/§3/etc.)
   │    Right    │    Below     │
   └─────────────┴──────────────┘
```

- Padding: `horizontal = 16.dp, vertical = 12.dp`.
- Title: `titleMedium`, `FontWeight.SemiBold`, `onSurface`.
- Description: `bodySmall`, `onSurfaceVariant`, one line.
- 10dp spacer between description and control. Control fills max width.

> The old project has TWO private `LabeledSection` definitions
> (`LayoutSettingsScreen` and `PlayerEpisodeDisplayScreen` each declare
> their own). The new project ships ONE shared `LabeledSection` in
> `SettingsComponents.kt`.

---

## 6. The 4-way `SelectableOptionCard` — for ≥4-option choices

When a setting has 4+ options (e.g. Watched-episode appearance: None /
Grayscale / Blur / Grayscale + Blur), the `StyledSegmentedRow` becomes
too cramped. Use `SelectableOptionCard` instead: each option is its own
tappable bordered card with a checkmark when selected.

### 6.1 ASCII — 4-way `SelectableOptionCard`

```
   Visual treatment
   How episodes you've already watched appear in the list

   ┌─────────────────────────────────────────────────────┐
   │  ○  None                                            │  ← unselected
   │     Watched episodes look the same as unwatched     │     1dp outlineVariant
   └─────────────────────────────────────────────────────┘
   ┌─────────────────────────────────────────────────────┐
   │  ●  Grayscale                       ✓               │  ← selected
   │     Black & white — desaturate the entire card      │     2dp primary border
   └─────────────────────────────────────────────────────┘     primary-tinted text
   ┌─────────────────────────────────────────────────────┐
   │  ○  Blur                                            │
   │     Slightly blur the entire card                   │
   └─────────────────────────────────────────────────────┘
   ┌─────────────────────────────────────────────────────┐
   │  ○  Grayscale + Blur                                │
   │     Maximum visual distinction                      │
   └─────────────────────────────────────────────────────┘
```

### 6.2 Visual rules

| Property | Value |
|---|---|
| Card shape | `RoundedCornerShape(12.dp)` |
| Card background | `surface` (NOT `primaryContainer` — rejected as "too dark/blue") |
| Selected border | `2.dp` `primary` |
| Selected text | `primary` `Bold` + trailing Check icon |
| Unselected border | `1.dp` `outlineVariant` |
| Unselected text | `onSurface` `Medium` |
| Vertical gap between cards | `8.dp` |

### 6.3 Conditional slider sub-settings

When the user picks `grayscale` or `both`, an `Alpha` slider appears
(`watchedAlpha`, 0.0–1.0). When the user picks `blur` or `both`, a
`Blur radius` slider appears (`watchedBlurRadius`, 1–10 dp). Both use
`SliderSettingsRow`.

---

## 7. Metadata fetching subpage (sibling — toggle recap)

The third Customize subpage. Uses `SwitchSettingsRow`s (true on/off
toggles per principle #8 — NOT §2/§3 segmented controls).

1. Sticky live preview (display toggles forced ON).
2. Master toggle: "Fetch episode metadata" (`enableInAppMetadataFetch`),
   icon `AutoAwesome`.
3. When master is ON, `AnimatedVisibility(expandVertically)` reveals a
   `SettingsGroupCard` "What to fetch" with three `SwitchSettingsRow`s:

| Field | Icon | Pref key |
|---|---|---|
| Thumbnails | `Image` | `fetchMetadataThumbnails` |
| Titles | `Title` | `fetchMetadataTitles` |
| Summaries | `Subtitles` | `fetchMetadataSummaries` |

4. A `surfaceContainerLow` info card: "Metadata is fetched when you open
   an anime's detail page. Only fields missing from the extension are
   enriched."

---

## 8. Owner likes (keep) vs improvements

| Aspect | Verdict | Notes |
|---|---|---|
| 2-way `StyledSegmentedRow` toggles | ✅ keep ("beautiful") | Right/Below, Left/Right, Overlay/Badge, Above-eps/Below-eps, Episode-row/Synopsis |
| 3-way `StyledSegmentedRow` toggles | ✅ keep ("beautiful") | Above/Below/Full (date+pills); Small/Medium/Large (thumbnail) |
| 4-way `SelectableOptionCard` for ≥4 options | ✅ keep | Used for watched-episode appearance |
| Container: `surfaceVariant` 50% alpha, 12dp radius, 4dp padding | ✅ keep | Soft neutral container |
| Selected pill: `primary` + `onPrimary` Bold; Unselected: transparent + `onSurfaceVariant` Medium | ✅ keep | |
| `primary` NOT `primaryContainer` for selected states | ✅ keep | "Too dark/blue" rejection is canonical |
| Sticky live preview at top of every subpage | ✅ keep | "Perfect, beautiful" per owner |
| `LabeledSection` (title + description + control below) | ✅ keep | |
| `SettingsGroupCard` grouping (3–5 controls per card) | ✅ keep | |
| Metadata fetching: master toggle + per-field sub-toggles + `AnimatedVisibility` | ✅ keep | Clean reveal pattern |
| Slider sub-settings appear conditionally | ✅ keep | Blur only when blur is on; alpha only when grayscale is on |
| `SwitchSettingsRow` for on/off element toggles | ✅ keep | |
| Per-anime info position (`animeInfoPosition`: above/below episodes) | ✅ keep | |
| Two private `LabeledSection` definitions | ⚠️ improve | Unify into one shared `LabeledSection` in `SettingsComponents.kt` |
| Duplicate Display/Layout settings vs `PlayerEpisodeDisplayScreen` | ⚠️ improve | Decide whether the player needs its own pref set or can share |
| `downloadButtonPlacement` `.get()` bug (now fixed) | ⚠️ note | Always use `stateIn(scope).collectAsState()` for live-preview prefs |
| Watched-episode appearance doesn't update live preview | ⚠️ improve | See `details-settings.md` §6 for the fix |

---

## 9. Simple mode + open design-session items

- This entire subpage is **hidden in simple mode** (positions are
  power-user territory). Episode display stays visible (but its 4-way
  watched-appearance card and sliders are hidden); Metadata fetching
  also stays visible.
- Open items: 4th option for thumbnail size (e.g. "Adaptive"); a
  "Reset to defaults" action at the bottom; the selected-pill fill
  transition motion curve (TBD in `03-themes/`); whether
  `SelectableOptionCard` should support a 5th+ option (beyond 4,
  switch to a radio-list dialog).

---

## See also

- [`../01-principles/core-principles.md`](../01-principles/core-principles.md)
  — principles #5, #7, #8, #10.
- [`../02-components/components.md`](../02-components/components.md) — §2
  (3-way toggle), §3 (2-way toggle), §8 (live preview panel), §9
  (subdued settings-variant section header).
- [`details-settings.md`](details-settings.md) — parent hub screen; §6
  there owns the `EpisodeRowPreview` watched-appearance fix spec.
- [`bottom-nav.md`](bottom-nav.md) — the floating nav that surfaces the
  "More" tab leading here.
- `OLD_ANIKUTA/ANALYSIS/history-extensions-settings-screens.md` §4 —
  source analysis of the old project's settings subpages.
- `DOCS/04-design-decisions.md` — ADR-015, ADR-018.
