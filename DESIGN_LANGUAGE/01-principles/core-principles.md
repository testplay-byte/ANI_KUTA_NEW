# 01 — Core Design Principles

> The cross-cutting design rules that apply to **every** ANIKUTA screen. These
> principles are the owner's stated preferences, distilled from the vision
> briefing (Phase 0b) and codified in `DOCS/04-design-decisions.md` (ADR-015,
> ADR-017, ADR-018). Per `RULES/ai-agent-rules.md` §6, every screen and
> component must follow these rules — no improvising UI.
>
> Each principle below has: **What** (the rule), **Why** (owner's reasoning),
> **Where** (which screens/components it applies to), and **Source** (the
> owner's quote or the ADR that backs it).

---

## Principle index

| # | Principle | ADR / Source |
|---|---|---|
| 1 | Edge-to-edge top bar (no inset under status bar) | Owner briefing |
| 2 | No drag handle on bottom-up menus | Owner briefing |
| 3 | Bottom-up menus are partial-height (not full-screen) | Owner briefing |
| 4 | Blurred cover + gradient darkening on detail headers | Owner briefing |
| 5 | Watched = grayscale + blur | Owner briefing |
| 6 | Accent-colored, left-aligned section headers | Owner briefing |
| 7 | Live preview in appearance-affecting settings | Owner briefing |
| 8 | Multi-way toggles (3-way and 2-way) | Owner briefing |
| 9 | Floating bottom nav (not edge-to-edge) | ADR-017 |
| 10 | Settings divided into sections; simple mode hides advanced | ADR-018 |
| 11 | Custom M3-inspired design language (not stock M3 Expressive) | ADR-015 |
| 12 | Custom numeric keyboard for numeric input | Owner briefing |

---

## 1. Edge-to-edge top bar

**What.** The app draws **edge-to-edge**. The status/notification bar area is
part of the app canvas — the top navigation bar extends to the very top of the
screen, drawing underneath the system status bar. We do **not** reserve an
inset strip and push the top bar below it.

**Why.** The owner wants a clean, modern, immersive look. The traditional
"status bar strip + top bar below it" pattern reads as dated and wastes
vertical space on tall modern phones.

**Where.** All screens that have a top navigation bar / app bar. This is a
**global** rule — there are no exceptions for settings, library, browse, etc.
The top bar background extends to the top edge; the system status bar icons
overlay it.

**Implementation note.** Use `enableEdgeToEdge()` in the Activity. Apply
`windowInsetsPadding(WindowInsets.statusBars)` only to the bar's **content**
(title, icons), never to the bar's **background**.

> **Source (owner quote):** "at the very top it will show the top navigation
> bar and it will not show under the notification bar. This is our design
> document preference for the user interface design document so this should be
> handled in all of the screens."

---

## 2. No drag handle on bottom-up menus

**What.** Bottom sheets / bottom-up menus do **not** have the top pull-down
drag-handle line (the small white/grey rounded bar that Material 3 places at
the top of a modal bottom sheet). The drag handle is set to `false` / hidden.

**Why.** The drag handle is visual noise. ANIKUTA's bottom menus are
single-purpose (pick a quality, pick a subtitle, adjust subtitle settings) —
they don't need a discoverable "grab and pull" affordance.

**Where.** All bottom-up menus: quality selection, subtitle / audio track
selection, subtitle settings, episode layout settings, and any future bottom
sheet that uses the standard bottom-up menu component (components §1).

**Implementation note.** When using `ModalBottomSheet` (or equivalent), pass
`dragHandle = null`. The sheet is still dismissible by swipe-down on the
scrim / content — we just don't render the visual handle.

> **Source (owner quote):** flagged explicitly for the quality menu and the
> subtitle menu — the drag handle bar should not appear.

---

## 3. Bottom-up menus are partial-height (not full-screen)

**What.** Bottom sheets occupy **only the height they need** to display their
content. They do **not** expand to cover the whole screen. The area above the
sheet is dimmed (scrim) and remains visible.

**Why.** A full-screen bottom sheet is indistinguishable from a pushed screen —
it breaks the user's mental model of "this is a quick menu over my current
context." Partial-height sheets reinforce that the underlying screen is still
there.

**Where.** All bottom-up menus (same list as principle #2). The subtitle
settings sheet is the canonical example.

**Implementation note.** Do not set the sheet to `expand` (full) by default.
Use the content's natural height with a `maxHeight` cap of ~0.7 of the
viewport so a long sheet still leaves the underlying context visible. The
sheet should never reach the status bar.

> **Source (owner quote):** "it does not cover the whole screen. This is a
> design language of our app too." (flagged for subtitle settings, generalized
> to all bottom-up menus here.)

---

## 4. Blurred cover + gradient darkening on detail headers

**What.** On detail screens, the top of the screen is a **blurred, scaled-up
version of the cover image** as the background, with a **vertical gradient
overlay** that goes from transparent at the very top to solid background color
at the bottom of the header region. The foreground content (cover thumbnail,
title, action buttons) sits on top.

```
┌─────────────────────────────┐
│ [blurred cover, darkened ↓] │  ← top: transparent overlay
│   ┌──┐                      │
│   │co│  Anime Title         │
│   │ve│  Studio · Year       │
│   └──┘                      │
│  [Watch] [+ Library] …      │  ← bottom: solid background color
├─────────────────────────────┤
│ (rest of the screen)        │
```

**Why.** Produces a cinematic, contextual header that ties the page to the
anime's identity, while keeping text legible via the gradient.

**Where.** Anime details screen (primary). Manga details screen (when
implemented, per ADR-009). Any "details" screen with a hero cover image. **Not**
used on list screens, settings, or the player.

**Implementation note.** Render the cover with a `BlurEffect` (radius TBD in
`03-themes/`) and a vertical scale of ~1.2× (so the blur doesn't show hard
edges). The gradient is a `Brush.verticalGradient` from `Color.Transparent`
(top) to `MaterialTheme.colorScheme.background` (bottom). Add a ~30% black
scrim on top of the blur for text contrast.

> **Source (owner quote):** flagged for the anime details screen.

---

## 5. Watched = grayscale + blur

**What.** In episode lists, episodes the user has already watched are rendered
in **grayscale** (desaturated) **with a blur effect** applied to the
thumbnail. Unwatched episodes are full color, no blur.

**Why.** The owner wants the episode list to visually communicate progress at a
glance — watched episodes "fade back" so the next-to-watch episode naturally
draws the eye. More elegant than a checkmark badge alone.

**Where.** Episode lists on: the anime details screen, the watch screen
(episode list below the mini-player), the history screen (where applicable).
**Not** on manga chapter lists (manga uses a different progress treatment,
TBD).

**Implementation note.** Apply a `ColorMatrix` grayscale (standard
0.299/0.587/0.114 luminance matrix) plus a `BlurEffect` to the watched
episode's thumbnail. Watched status is `episode.seen`. Keep the episode number
and title text in normal color so the row is still readable — only the
thumbnail is treated.

> **Source (owner quote):** flagged for the episode list on the anime details
> screen.

---

## 6. Accent-colored, left-aligned section headers

**What.** Section headers inside scrollable content (e.g., "Today",
"Yesterday" on the history screen) are rendered in the **accent color** and
**left-aligned**. They are not centered, not in the default text color, and
not styled as plain bold text.

**Why.** The owner wants section dividers to feel like intentional anchors in
the list, not incidental labels. Accent color ties them to the app's identity;
left alignment matches the natural reading flow of the list items below.

**Where.** History screen ("Today", "Yesterday", …). Any list-based screen
that groups items into named sections (library category headers, browse
section titles). **Not** for top-of-screen titles (top app bar) or settings
section titles (their own subdued treatment, see principle #10).

**Implementation note.** Use `MaterialTheme.colorScheme.primary` (or a
dedicated accent role from `03-themes/`) for text. `TextStyle(fontWeight =
SemiBold, fontSize = 14–16 sp)`, `TextAlign.Start`. Add small bottom padding
(8–12 dp) but no top divider line — the accent color itself is the divider.

> **Source (owner quote):** flagged for the history screen.

---

## 7. Live preview in appearance-affecting settings

**What.** Any settings screen whose options **affect on-screen appearance**
shows a **live preview** at the top of the screen. As the user changes a
setting, the preview updates immediately.

**Why.** The owner was emphatic that this is the right pattern — settings
should never ask the user to "apply and see." The preview IS the apply: the
user sees the result while they decide.

**Where.** Settings screens that change appearance:
- **Details settings** (anime/manga details layout) — owner: *"That is
  perfect. It is beautiful."*
- **Subtitle settings** (font, size, color, outline) — preview shows sample
  subtitle text on a still frame.
- **Theme settings** (palette, dark mode) — preview shows a miniature screen.
- **Episode layout settings** (grid vs list, thumbnail size) — preview shows a
  sample episode row.

**Not** where settings don't affect appearance (notifications, download
location, tracker login).

**Implementation note.** The preview uses the **same composables** as the real
screen, just bounded and scaled, fed by a `StateFlow` of the in-progress
(not-yet-applied) settings. It is the first thing below the top app bar;
options follow below. It must re-render on every setting change.

> **Source (owner quote):** "That is perfect. It is beautiful." (re: the
> details-settings live preview.)

---

## 8. Multi-way toggles (3-way and 2-way)

**What.** Where a setting has more than two states, use a **multi-way toggle**
— a segmented control with the option labels written on each segment. For
binary settings, use a styled **2-way toggle** (distinct from a plain M3
`Switch`). Plain switches are reserved for true on/off settings with no
intermediate or "auto" state.

**Why.** The owner prefers explicit, labeled option groups over dropdowns or
plain switches. A 3-way toggle (e.g., **Off / On / Auto**) communicates all
options at once without a tap; a labeled 2-way toggle (e.g., **List / Grid**)
is clearer than a bare switch with a separate label.

**Where.** Episode layout settings (owner's example — **List / Grid /
Compact**), subtitle alignment, theme mode (**Light / Dark / System**),
metadata source preference (**AniList / Extension** per ADR-011), and similar
multi-state settings.

**Implementation note.** 3-way toggle: single-row pill with three equal
segments; active segment filled with accent color, inactive are outline-only.
2-way toggle: same pill with two segments — **not** a `Switch` composable.
Both reuse the same `SegmentedToggle` primitive (components §2/§3). Min tap
target 48 dp; segment min width 64 dp.

> **Source (owner quote):** flagged for the episode layout settings (3-way
> toggle for layout choice).

---

## 9. Floating bottom nav (not edge-to-edge)

**What.** The bottom navigation bar is a **floating** element: it has
horizontal margins (does not touch the screen's left/right edges), rounded
corners, and a visible gap between it and the bottom of the screen. It is
**not** edge-to-edge like the reference app's bottom nav.

**Why.** The owner explicitly called the reference (Aniyomi) bottom nav "ugly"
and "bad." A floating bar reads as modern and intentional; an edge-to-edge bar
reads as a stock Material template.

**Where.** Globally — the bottom nav appears on all top-level screens (Home,
Library, Updates, History, Browse, MY, More — the user's selection per
ADR-017). It is hidden on the fullscreen player and reader.

**Implementation note.** The bar floats with ~16 dp horizontal margin and ~12
dp bottom margin (final values in `03-themes/`), rounded corners (~24 dp
radius). It sits **above** the system navigation bar inset; the inset area
below the bar is filled with the screen background color so it doesn't look
like a floating island with empty space beneath. Per ADR-017: 3–7 tabs,
user-rearrangeable, with one fixed **"More"** tab.

> **Source:** ADR-017 — "The bar is a floating design (not edge-to-edge), per
> the owner's preference." Owner: reference bottom nav is "ugly" and "bad."

---

## 10. Settings divided into sections; simple mode hides advanced

**What.** Settings are organized into **named sections** (each with a section
header), not a flat list. A **simple mode** toggle (ADR-018) hides advanced
settings so casual users see only essentials.

**Why.** Feature parity with Aniyomi means we have a *lot* of settings
(ADR-018). A flat list is unusable. Sections group related settings (Player,
Subtitles, Downloads, Notifications, Appearance, …). Simple mode lets the
owner ship an out-of-box experience that's not overwhelming.

**Where.** All settings screens: the root settings screen, player settings,
subtitle settings, download settings, appearance settings, etc.

**Implementation note.** Each setting carries a `simpleModeVisible: Boolean`
flag (ADR-018). When simple mode is on, settings with `simpleModeVisible =
false` are filtered out. Sections themselves can also be hidden in simple mode
if all their children are hidden. Section headers in settings use a slightly
more subdued treatment than principle #6 (e.g., a lighter accent or a
secondary color) — exact style in `03-themes/`.

> **Source:** ADR-018 — "A simple mode toggle hides most advanced settings,
> showing only essentials." Owner briefing on settings structure.

---

## 11. Custom M3-inspired design language (not stock M3 Expressive)

**What.** ANIKUTA's design is **inspired by** Material 3 but is **not** stock
Material 3 Expressive. The owner has specific preferences (principles #1–#10,
#12) that override M3 defaults wherever they conflict. Theme palettes are
custom (see `03-themes/`). Component shapes, motion, and spacing are tuned to
the owner's taste, not the M3 spec.

**Why.** The owner "doesn't like [M3 Expressive] that much." Stock M3
Expressive is the starting point, not the destination. The old ANIKUTA
project's flagged screens are the **primary design reference** (see
`DESIGN_LANGUAGE/README.md`).

**Where.** Everywhere — this is the meta-principle. It's the reason this folder
exists.

**Implementation note.** When a stock M3 component (`ModalBottomSheet`,
`NavigationBar`, `Switch`, `SegmentedButton`, etc.) is used, it must be
**adapted** to match the principles here (e.g., `ModalBottomSheet` → no drag
handle + partial height; `NavigationBar` → floating variant; `Switch` → 2-way
toggle; `SegmentedButton` → styled segmented toggle). Do not import a stock M3
component and use it as-is if any principle above modifies its behavior.

> **Source:** ADR-015 — "Create a custom design language inspired by M3 but
> with the owner's specific preferences." Owner: "doesn't like [M3 Expressive]
> that much."

---

## 12. Custom numeric keyboard for numeric input

**What.** When the user needs to enter a **number** (font size, subtitle
delay, skip-duration, etc.), a **custom in-app numeric keyboard** appears —
**not** the system IME. The keyboard is a bottom-up panel that follows
principle #2 (no drag handle) and principle #3 (partial height).

**Why.** The system keyboard is overkill for a single number, brings
unpredictable layouts (autocomplete bar, suggestions), and doesn't match
ANIKUTA's visual language. A custom keyboard is consistent, minimal, and can
include domain-specific affordances (e.g., a "reset to default" button).

**Where.** Anywhere a numeric value is entered: subtitle settings (font size,
outline width, delay (ms), position), player settings (skip duration,
playback speed if numeric), download settings (concurrent downloads), etc.

**Implementation note.** Layout is a numeric keypad: digits 0–9, a decimal
point (where fractional values are allowed), a backspace key, a "done" /
confirm key, and — where relevant — a "reset to default" key. It has **no**
drag handle (principle #2). It does **not** cover the whole screen (principle
#3) — it occupies the bottom ~40% of the viewport, leaving the input field
and the live preview (principle #7) visible above. Full layout spec in
[`../02-components/components.md`](../02-components/components.md) §4.

> **Source (owner quote):** flagged for the subtitle settings screen (font
> size entry) — a custom keyboard, not the system one.

---

## Sources

- Owner vision briefing (Phase 0b), transcribed quotes — see individual
  principles above.
- `DOCS/04-design-decisions.md`:
  - **ADR-015** — Custom M3-inspired design language (not stock M3 Expressive).
  - **ADR-017** — Bottom nav: configurable (3–7 tabs, rearrange, fixed
    "More"), floating design.
  - **ADR-018** — Feature parity with customizable defaults + simple mode.
- `DESIGN_LANGUAGE/README.md` — folder orientation and the "primary design
  reference = old ANIKUTA flagged screens" rule.

---

## See also

- [`../02-components/components.md`](../02-components/components.md) — the
  reusable components that implement these principles.
- [`../03-themes/`](../03-themes/) — color palettes, typography, motion specs
  (to be written).
- [`../04-screens/`](../04-screens/) — per-screen specs that apply these
  principles (to be written).
- `DOCS/04-design-decisions.md` — ADR-015, ADR-017, ADR-018.
