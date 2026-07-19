# 02 — Reusable Components

> The reusable UI components that implement the core principles in
> [`../01-principles/core-principles.md`](../01-principles/core-principles.md).
> Every ANIKUTA screen composes these components — they are the vocabulary of
> the design language. Per `RULES/ai-agent-rules.md` §6, do **not** improvise
> a new component when one of these fits; extend or adapt an existing one
> instead.
>
> Each component below has: **Description** (what it is and looks like),
> **Visual style**, **Used on** (which screens), **Key properties**, and the
> **Principles** it implements.

---

## Component index

| § | Component | Implements principles |
|---|---|---|
| 1 | Bottom-up menu (no drag handle, partial height) | #2, #3 |
| 2 | 3-way toggle | #8 |
| 3 | 2-way toggle | #8 |
| 4 | Custom numeric keyboard | #2, #3, #12 |
| 5 | Floating bottom nav | #9 |
| 6 | Episode row (watched = grayscale + blur) | #5 |
| 7 | Blurred cover header | #4 |
| 8 | Live preview panel | #7 |
| 9 | Section header (accent, left-aligned) | #6 |

Screen specs in `04-screens/` reference these §N anchors (e.g., "uses §1 and
§7"). When a stock Material 3 component is the **base**, it must be adapted
per principle #11 — never used unmodified.

## §1 — Bottom-up menu (no drag handle, partial height)

**Description.** The standard bottom sheet for ANIKUTA. Slides up from the
bottom over a dimmed scrim. Used for any modal, single-purpose menu that does
not warrant a full pushed screen.

```
┌──────────────────────────────────┐
│ (underlying screen, dimmed scrim)│
├──────────────────────────────────┤  ← NO drag handle
│ 1080p                            │
│ 720p   ✓                         │
│ 480p                             │
└──────────────────────────────────┘
```

**Visual style.**
- **Base:** Material 3 `ModalBottomSheet`, **adapted**.
- **Drag handle:** `null` (hidden). *Hard rule — principle #2.*
- **Height:** natural content, capped at ~70% of viewport. Never full-screen.
  *Hard rule — principle #3.*
- **Top corners:** rounded ~24 dp; bottom flush with screen edge.
- **Background:** `surface` (or `surfaceContainerHigh` in dark themes).
- **Scrim:** ~60% black, dismiss-on-tap.

**Used on.** Quality selection, audio/subtitle track selection, subtitle
settings (also uses §4, §8), episode layout settings (also uses §2/§3), and
any future bottom sheet.

**Key properties.**
| Property | Value | Notes |
|---|---|---|
| `dragHandle` | `null` | Principle #2. Never override. |
| `maxHeightFraction` | `0.7f` | Principle #3. |
| `shape` | `RoundedCornerShape(topStart=24.dp, topEnd=24.dp)` | |
| `scrimColor` | `Color.Black.copy(alpha=0.6f)` | Tap to dismiss. |
| `onDismissRequest` | required | Always wired — no non-dismissible sheet. |

**Principles:** #2, #3.

---

## §2 — 3-way toggle

**Description.** A segmented control with **three** mutually-exclusive options,
each labeled with the option name. The active segment is filled with the
accent color; inactive segments are outline-only.

**Visual style.**
- **Base:** Material 3 `SegmentedButton` (single-select), **adapted**.
- **Layout:** single horizontal pill, three equal-width segments.
- **Active segment:** filled with `colorScheme.primary`, text in `onPrimary`.
- **Inactive segments:** `surface` fill, `onSurfaceVariant` text, thin 1 dp
  divider between segments.
- **Shape:** outer pill ~16 dp radius. **Sizing:** height ~48 dp; segment min
  width 64 dp.
- **Motion:** animated fill transition on selection (~150 ms, emphasized).

**Used on.** Episode layout (**List / Grid / Compact** — owner's example),
theme mode (**Light / Dark / System**), metadata source (**AniList /
Extension / Auto** per ADR-011), subtitle alignment (**Left / Center /
Right**), anywhere a setting has exactly three discrete states.

**Key properties.**
| Property | Value | Notes |
|---|---|---|
| `options` | `List<String>` (length 3) | Labels on segments. |
| `selected` | `Int` (0–2) | Active segment index. |
| `onSelect` | `(Int) -> Unit` | Required. |
| `minHeight` | `48.dp` | Tap target. |
| `enabled` | `Boolean` | Dim when disabled. |

**Principles:** #8.

---

## §3 — 2-way toggle

**Description.** A binary segmented control with **two** labeled options
(e.g., **List / Grid**). Distinct from a plain `Switch`: a `Switch` is a bare
on/off affordance; the 2-way toggle **labels both states** so the user sees
what each side means without a separate label.

**Visual style.** Same treatment as §2, but with two segments. Active segment
is filled with accent color. **Not** used for true on/off settings — those use
a `Switch`. The 2-way toggle is for **labeled binary choices**.

**Used on.** Library view (**List / Grid**), episode sort (**By number / By
date** when only two options), any labeled binary preference where both
option names are meaningful. **Not** for "Enable X" (use `Switch`).

**Key properties.** Same as §2, with `options` of length 2.

**Principles:** #8.

---

## §4 — Custom numeric keyboard

**Description.** A custom in-app numeric keypad that replaces the system IME
for numeric entry. Presented as a bottom-up panel (reuses §1's presentation
rules). **Never** the system keyboard.

```
┌──────────────────────────────────┐
│ (input field + live preview)     │  ← visible above the keyboard
├──────────────────────────────────┤  ← NO drag handle
│  1   2   3                       │
│  4   5   6                       │
│  7   8   9                       │
│  .   0   ⌫                       │
│              [ Reset ]  [ Done ]  │
└──────────────────────────────────┘
```

**Visual style.**
- **Presentation:** bottom-up panel, no drag handle (principle #2), partial
  height ~40% of viewport (principle #3).
- **Layout:** 4-row × 3-column digit grid (1–9, then `.`/`0`/`⌫`), plus a
  bottom action row with **Reset** (to default) and **Done** (confirm).
- **Keys:** large (~56 dp tall), rounded ~12 dp, `surfaceVariant` fill,
  `onSurface` text. The **Done** key is filled with accent color.
- **Backspace (⌫):** long-press clears the field. **Decimal (`.`):** hidden
  when input is integer-only. **Reset:** hidden when the input has no default.

**Used on.** Subtitle settings (font size, outline width, delay (ms),
position), player settings (skip duration in seconds), download settings
(concurrent downloads), any numeric input field.

**Key properties.**
| Property | Value | Notes |
|---|---|---|
| `value` | `String` | Current input. |
| `onValueChange` | `(String) -> Unit` | Per keystroke. |
| `allowDecimal` | `Boolean` | Show/hide `.` key. |
| `defaultValue` | `String?` | If non-null, show **Reset**. |
| `onDone` | `() -> Unit` | Confirm and dismiss. |
| `dragHandle` | `null` | Principle #2. Never override. |
| `maxHeightFraction` | `0.4f` | Principle #3. |
| **No** system IME | — | The text field's IME is suppressed; this keyboard is the only input. |

**Implementation note.** The numeric input field is **not** a normal
`TextField` with the system IME — it's a read-only display that opens §4 on
focus. This guarantees the system keyboard never appears for numeric fields.

**Principles:** #2, #3, #12.

---

## §5 — Floating bottom nav

**Description.** The redesigned bottom navigation bar. Floating (with margins
and rounded corners), not edge-to-edge. Holds 3–7 tabs, user-rearrangeable,
with one fixed **"More"** tab. Per ADR-017.

**Visual style.**
- **Base:** Material 3 `NavigationBar`, **heavily adapted**.
- **Layout:** floating pill, ~16 dp horizontal margin from screen edges, ~12
  dp bottom margin (above the system nav bar inset). Shape: fully rounded
  pill (~28 dp outer radius).
- **Fill:** `surfaceContainer` with subtle elevation shadow.
- **Tabs:** 3–7. Each tab is an icon + label; active tab uses accent color for
  icon and label, inactive uses `onSurfaceVariant`.
- **"More" tab:** always present, always last (rightmost). Icon: ⋯ or
  hamburger-like glyph (TBD). Cannot be removed or reordered by the user.
- **Rearranging:** long-press a tab to enter drag mode; drop to reorder.
  Applies to all tabs **except** "More".

**Used on.** All top-level screens (Home, Library, Updates, History, Browse,
MY — the user's selection). **Hidden** on: fullscreen player, fullscreen
reader, onboarding.

**Key properties.**
| Property | Value | Notes |
|---|---|---|
| `tabs` | `List<NavTab>` | 3–7 entries, user-configurable. |
| `moreTab` | fixed | Always last. Cannot be removed/reordered. |
| `selected` | `Int` | Active tab index. |
| `onSelect` | `(Int) -> Unit` | |
| `onRearrange` | `(List<NavTab>) -> Unit` | Long-press drag. |
| `horizontalMargin` | `16.dp` | Principle #9. |
| `bottomMargin` | `12.dp` | Above system nav bar inset. |
| `shape` | `RoundedCornerShape(28.dp)` | Floating pill. |

**Principles:** #9 (ADR-017).

---

## §6 — Episode row (watched = grayscale + blur)

**Description.** The episode list item. Renders an episode's thumbnail,
number, title, and metadata (filler badge, watch status). The thumbnail's
treatment encodes watched status: watched episodes are grayscale + blurred;
unwatched are full color.

**Visual style.**
- **Layout:** horizontal row, ~72 dp tall. Thumbnail (16:9, ~128×72 dp) at
  start; text stack (number + title + meta) fills the rest; trailing status
  icon at the end. Tap target = entire row.
- **Thumbnail (unwatched):** full color, no filter.
- **Thumbnail (watched):** `ColorMatrix` grayscale + `BlurEffect` (radius
  ~4–8 dp). *Hard rule — principle #5.*
- **Episode number:** `onSurface`, bold, ~14 sp. Prefix "EP".
- **Title:** `onSurface`, ~14 sp, single line (ellipsize).
- **Meta row:** `onSurfaceVariant`, ~12 sp — filler badge (accent color,
  "Filler"), duration, air date (if available).
- **Watch status icon:** end side. Unwatched = nothing (or play glyph);
  watched = a checkmark in accent color.

**Used on.** Anime details (episode list), watch screen (episode list below
the mini-player), history screen (recent-episode rows where applicable).
Manga chapter list uses a **different** component (TBD) — do not reuse this
for manga.

**Key properties.**
| Property | Value | Notes |
|---|---|---|
| `episode` | `Episode` | Number, title, thumbnail, filler, duration, `seen`. |
| `onClick` | `() -> Unit` | Plays the episode. |
| `onLongClick` | `() -> Unit` | Opens episode action menu (download, mark watched, etc.). |
| `grayscaleIfWatched` | `true` | Principle #5 — never override. |

**Principles:** #5.

---

## §7 — Blurred cover header

**Description.** The hero header for detail screens. A blurred, scaled-up
version of the cover image fills the header background, with a vertical
gradient overlay darkening from transparent (top) to solid background
(bottom). The foreground content (cover thumbnail, title, action buttons)
sits on top.

```
┌─────────────────────────────┐
│ [blurred cover, no overlay] │  ← transparent at top
│ [blurred cover, ↓ darker  ] │
│   ┌──┐  Anime Title         │
│   │co│  Studio · Year       │
│   └──┘                      │
│  [Watch] [+ Library] …      │  ← solid background at bottom
├─────────────────────────────┤
│ (rest of the screen)        │
```

**Visual style.**
- **Background layer:** cover image, scaled ~1.2× and blurred (radius
  ~24–32 dp), filling the header region edge-to-edge.
- **Gradient overlay:** `Brush.verticalGradient` from `Color.Transparent`
  (top) to `colorScheme.background` (bottom). A secondary ~30% black scrim
  sits on top of the blur for text contrast.
- **Foreground content:** sharp cover thumbnail (~96×144 dp or 16:9, rounded
  ~12 dp); title (`onSurface`, ~22 sp, bold); subtitle row (studio, year,
  score — `onSurfaceVariant`, ~13 sp); action row (**Watch** filled accent,
  **+ Library** outlined, share, etc.).
- **Height:** ~280–320 dp, depending on content.

**Used on.** Anime details (primary), manga details (when implemented, per
ADR-009), any "details" screen with a hero cover image. **Not** on list /
settings / player screens.

**Key properties.**
| Property | Value | Notes |
|---|---|---|
| `coverImageUrl` | `String?` | The full-res cover. |
| `blurRadius` | `~28.dp` | Principle #4. |
| `scale` | `1.2f` | Avoid hard edges on the blur. |
| `gradient` | `transparent → background` | Principle #4. |
| `scrimAlpha` | `0.3f` | Black scrim over the blur for text contrast. |
| `content` | `@Composable` | The foreground (thumbnail, title, actions). |

**Principles:** #4.

---

## §8 — Live preview panel

**Description.** A miniature, non-interactive (or limited-interactive)
preview of the affected screen, shown at the top of an appearance-affecting
settings screen. Updates live as the user changes settings below it.

**Visual style.**
- **Layout:** the first element below the top app bar — a rounded rectangle
  (~16 dp) containing a scaled-down render of the affected screen. Background
  `surfaceVariant` (so it stands out from the settings list). Aspect ratio
  matches the device screen so it looks like a real screen, not a banner.
- **Content:** the **same composables** as the real screen, fed by a
  `StateFlow` of the in-progress (not-yet-applied) settings. Optionally
  non-interactive — taps inside the preview are ignored.
- **Height:** ~180–220 dp, capped so it doesn't dominate the screen.
- **Update:** immediate — every setting change re-renders the preview in the
  same frame.

**Used on.** Details settings (anime/manga details layout — owner's flagship
example), subtitle settings (preview shows sample subtitle text on a still
frame), theme settings (mini screen with the new palette), episode layout
settings (sample episode row — pairs with §6). **Not** on settings that don't
affect appearance (notifications, downloads, tracker login).

**Key properties.**
| Property | Value | Notes |
|---|---|---|
| `previewContent` | `@Composable (SettingsState) -> Unit` | The screen being previewed. |
| `settingsState` | `StateFlow<SettingsState>` | In-progress settings, updated live. |
| `interactive` | `false` (default) | Taps ignored; can be enabled for "tap to preview fullscreen". |
| `height` | `~200.dp` | Tunable per screen. |

**Principles:** #7.

---

## §9 — Section header (accent, left-aligned)

**Description.** The in-list section divider. Renders the section name in
the accent color, left-aligned, with no divider line above or below (the
accent color itself is the divider).

**Visual style.**
- **Text color:** `MaterialTheme.colorScheme.primary` (accent). *Hard rule —
  principle #6.*
- **Alignment:** `TextAlign.Start` (left). *Hard rule — principle #6.*
- **Style:** `SemiBold`, ~14–16 sp.
- **Spacing:** ~12 dp bottom padding (to its section's first item), ~20 dp top
  padding (from the previous section's last item). No top border, no bottom
  border.
- **Settings variant:** settings section headers use a slightly more subdued
  treatment (e.g., `onSurfaceVariant` or a lighter accent) to differentiate
  them from in-list section headers. Exact spec in `03-themes/`. (See
  principle #10.)

**Used on.** History screen (**Today**, **Yesterday**, **This Week**, etc.),
library (category headers), browse (**Recently Added**, **Trending**, etc.),
settings screens (section titles, subdued variant), any list-based screen
that groups items into named sections.

**Key properties.**
| Property | Value | Notes |
|---|---|---|
| `text` | `String` | Section name. |
| `color` | `MaterialTheme.colorScheme.primary` | Principle #6. Settings variant: `onSurfaceVariant`. |
| `align` | `TextAlign.Start` | Principle #6. Never center. |
| `style` | `SemiBold, 14–16.sp` | |

**Principles:** #6.

---

## See also

- [`../01-principles/core-principles.md`](../01-principles/core-principles.md) —
  the principles these components implement.
- [`../03-themes/`](../03-themes/) — color palettes, typography, motion specs
  (to be written).
- [`../04-screens/`](../04-screens/) — per-screen specs (to be written).
- `DOCS/04-design-decisions.md` — ADR-015, ADR-017, ADR-018.

> The **Component index** table at the top of this file is the canonical
> component → principle map (read the rightmost column).
