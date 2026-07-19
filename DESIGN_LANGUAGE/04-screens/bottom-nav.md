# 04 — Bottom Navigation (Floating Bar)

> The ANIKUTA bottom navigation: a **floating** bar (not edge-to-edge) with a
> user-configurable tab set (3–7 tabs, rearrangeable, "More" pinned).
>
> **ADR ref:** ADR-017 — Bottom nav: configurable (3–7 tabs, rearrange, fixed
> "More"). Floating-bar redesign.
>
> **Status:** STRUCTURE and BEHAVIOR are fixed (this doc). Exact visual
> properties (corner radius, elevation, pill vs. rounded-rect, selected-tab
> affordance, icon scale animation) are refined in a dedicated design
> session. Do NOT hardcode final visual tokens yet — see §10.

---

## 1. Why a redesign

The reference (Aniyomi) ships a stock Material 3 `NavigationBar` — an
edge-to-edge bar pinned to the bottom of the screen with tonal elevation.
The owner explicitly rejected this design as "ugly" and "bad". ANIKUTA's
bottom nav is a **complete redo**:

- Do NOT take Aniyomi's `NavigationBar` as a design base.
- The bar is a **floating** element: a discrete pill/rounded-rect surface
  suspended above the screen content with horizontal margins, NOT a full-width
  strip attached to the bottom edge.
- Content scrolls **under** the floating bar (the area behind the bar is
  transparent; only the bar surface is opaque).

> The old ANIKUTA project already implemented a floating-pill bar
> (`AnikutaNavGraph.kt` §bottomBar). It's a useful structural reference (it
> works), but its visual details are placeholders. This doc supersedes it and
> adds the **configurable tab set** + **rearrangement** + **fixed "More"**
> behavior the old project did NOT have.

---

## 2. Tab set

### 2.1 Available tabs

Seven tabs exist in the pool. The user picks which to show:

| Tab id | Label (default) | Icon | Purpose |
|---|---|---|---|
| `home` | Home | home | Discovery / trending / continue-watching |
| `library` | Library | library_books | User's saved anime, organized by category |
| `updates` | Updates | new_releases | Newly released episodes across library |
| `history` | History | history | Recently watched, resumable |
| `browse` | Browse | explore | Browse sources / extensions |
| `my` | MY | dashboard / person | Personalized dashboard (ADR-021; label customizable) |
| `more` | More | more_horiz | Settings, statistics, about, etc. — **fixed** |

- The `my` tab's label is user-customizable per ADR-021 (default "MY"). All
  other tab labels are localized strings.
- Tabs map 1:1 to top-level Voyager screens.

### 2.2 Tab count limits

- **Minimum: 3 tabs** shown (including "More"). The user cannot remove tabs
  below this floor.
- **Maximum: 7 tabs** shown (i.e. all available tabs at once).
- These limits are enforced in the configuration UI (the "remove" control is
  disabled when the user is at the floor; the "add" control is disabled at
  the ceiling).

### 2.3 The "More" tab is fixed

- "More" is **always present** — it cannot be hidden, removed, or disabled.
- "More" is **always the rightmost tab** — it cannot be repositioned.
- In the rearrangement UI, "More" is a locked row with a "locked" indicator
  and no drag handle.
- Rationale: "More" is the escape hatch for everything not on the bar
  (settings, stats, storage, about, extensions settings). Always knowing
  where it is (rightmost, always there) is a stable user model.

---

## 3. Configuration UI

The user configures the bottom nav in **Settings → Appearance → Bottom
navigation** (a subpage, not inline). Layout:

```
┌──────────────────────────────────────────────┐
│  ← Bottom navigation                         │
├──────────────────────────────────────────────┤
│  Visible tabs (drag to reorder)              │
│  ⋮⋮  Home          [remove]                  │
│  ⋮⋮  Library       [remove]                  │
│  ⋮⋮  Updates       [remove]                  │
│  🔒 More                                     │ ← locked, no drag handle
├──────────────────────────────────────────────┤
│  Hidden tabs (tap to add)                    │
│  + History                                   │
│  + Browse                                    │
│  + MY                                        │
├──────────────────────────────────────────────┤
│  Reset to defaults                           │
└──────────────────────────────────────────────┘
```

- **Drag-to-reorder** the visible tabs (Material `DragAndDropList` or
  Compose reorderable library — TBD). The "More" row is not draggable.
- Tapping a hidden tab **moves it to the bottom of the visible list** (just
  above the locked "More"). If the visible list is at the ceiling (7), the
  add control is disabled.
- Tapping `remove` on a visible tab moves it to the hidden list. If the
  visible list is at the floor (3), the remove control is disabled.
- **Reset to defaults** restores `Home · Library · Updates · More` (4 tabs)
  — also the first-run default.
- Changes are **live**: closing the subpage is not required. The bottom nav
  on the parent screen updates as the user reorders.

### 3.1 Persistence

- The user's tab configuration is persisted as an ordered list of tab ids in
  `UiPreferences` (e.g. `["home", "library", "updates"]`).
- "More" is **always appended at render time**, never stored in the user's
  list. This guarantees "More" can never be misplaced by a corrupted
  preference.
- The configuration is included in the app backup payload.

---

## 4. Floating bar layout (ASCII mockup)

```
   ┌──────────────────────────────────────────────────────────┐
   │                                                          │
   │             (screen content scrolls under the bar;        │
   │              the area behind the bar is transparent)      │
   │                                                          │
   │  ┌──────────────────────────────────────────────────┐    │
   │  │  [Home] [Library] [Updates]            [More]    │    │ ← floating pill
   │  └──────────────────────────────────────────────────┘    │
   │                                                          │
   └──────────────────────────────────────────────────────────┘
                         ▲ navigationBarsPadding (gesture inset)
```

- The bar has **horizontal margins** (24dp in the old project; final value
  TBD) and a **bottom margin** above the system gesture inset.
- The bar sits **above** the content (z-order) — content scrolls under it
  through the transparent area surrounding the bar surface.
- The bar surface is **opaque** (or near-opaque scrim) — content does not
  bleed through the bar itself, only around it.

### 4.1 With a selected tab

When a tab is selected, the bar shows a **selected affordance** on that tab.
The exact treatment is TBD (options: pill background, scaled-up icon, label
reveal, color shift). The old project's `secondaryContainer` pill + icon
scale animation is a starting reference, not the final decision.

```
   ┌──────────────────────────────────────────────────┐
   │  ╭────────╮                                     │
   │  │ ⌂ Home │   📚        🔔           ⋯          │
   │  ╰────────╯  (selected)                         │
   └──────────────────────────────────────────────────┘
```

### 4.2 Visibility rules

The floating bar is shown only on **top-level destinations** (the routes
matching the configured tabs). On any deeper screen (anime details, watch
page, settings subpages) the bar is **hidden** — the full screen is
available for content. This mirrors the old project's `showBottomBar`
behavior.

When the bar is hidden, the system gesture inset is still respected — the
screen's own bottom padding handles it.

---

## 5. Tab behavior

### 5.1 Tapping a tab

- Tapping a visible tab navigates to that screen using the standard
  `popUpTo(start) { saveState = true }` + `launchSingleTop = true` +
  `restoreState = true` pattern (same as the old project) so each top-level
  tab preserves its own scroll/state.
- Tapping the already-selected tab scrolls its content to the top (standard
  M3 behavior) — TBD whether this is enabled per tab.

### 5.2 Tapping "More"

- "More" opens the `MoreScreen` (a top-level destination, not a sheet).
- `MoreScreen` contains: Settings, Statistics, Storage, About, Extensions
  settings, Backup/Restore, and (per ADR-017) the **alt-tab** row for any
  hidden tab the user might want quick access to without adding it to the
  bar (whether alt-tabs are shown in More is TBD).

### 5.3 Badges & long-press

- Tabs can show badges (e.g. unread count on `updates`, download queue count
  on `library`). Badge styling and per-tab enablement are TBD.
- Long-pressing a tab opens a quick-action menu (TBD): likely "Hide from
  navigation" (jumps to the configuration UI), "Mark all read" (for
  `updates`), etc.

---

## 6. Rearrangement behavior

### 6.1 Drag-and-drop

- The user reorders tabs in the configuration subpage (see §3), NOT by
  long-pressing a tab on the live bar — drag-on-the-bar would conflict with
  tab-tap and gesture navigation.
- The reorder is committed on drop (no separate "apply" button).

### 6.2 Animation

- When the user reorders, the live bottom nav on the parent screen animates
  the tab swap (`AnimatedContent` with the `AnikutaSprings` spatial spec —
  see `01-principles/motion.md` TBD).
- Adding/removing a tab animates the bar's width change and the tab's
  enter/exit.

### 6.3 "More" invariance

- "More" is rendered rightmost regardless of the user's stored order. The
  stored list is rendered left-to-right, then "More" is appended — enforced
  at the composable level, not at the data level.

---

## 7. Relationship to Aniyomi's `NavStyle`

Aniyomi has a `NavStyle` preference that controls which of Updates/History/
Browse appear on the bottom nav vs. as alt-tabs inside More. ANIKUTA's model
is **simpler and more general**: the user directly picks the visible tab set
and order. There is no separate `NavStyle` enum. The set of tabs the user
has hidden is the equivalent of Aniyomi's "alt-tab" set — those tabs (if
any) can be surfaced inside `MoreScreen` per §5.2.

This is a deliberate departure — do NOT port Aniyomi's `NavStyle` machinery.

---

## 8. Edge cases

- **First run:** default tab set is `Home · Library · Updates · More`.
- **Restore from backup:** the restored tab list is applied verbatim; unknown
  ids (e.g. a tab removed in a future release) are dropped silently. "More"
  is always re-appended.
- **Simple mode (ADR-018):** the configuration subpage is hidden — the user
  gets the default tab set and cannot change it.
- **Tablet/foldable/landscape:** the floating bar stays bottom-attached (no
  side rail at this time). Tablet/landscape layout is TBD in a separate spec.

---

## 9. Accessibility

- Each tab is a clickable surface with a content description (the tab label).
- The selected state is announced via semantics (`Role.Tab`, `selected =
  true`).
- Touch targets meet M3 minimum (48dp tap area, even if the visual pill is
  shorter — the touch target is padded).
- The bar respects `navigationBarsPadding()` so it never sits under the
  system gesture inset.

---

## 10. Open for the design session (DO NOT implement yet)

- Final corner radius of the floating bar surface (old project: 28dp).
- Final elevation / shadow (old project: `shadowElevation = 8dp`,
  `tonalElevation = 0dp`).
- Pill vs. rounded-rect silhouette.
- Selected-tab affordance: pill background / icon scale / label reveal /
  color shift / combination.
- Horizontal and vertical margins (old project: 24dp / 12dp).
- Whether the bar uses `surfaceContainer` (old project) or a custom
  scrim+blur.
- Animation specs for tab swap / enter / exit.
- Whether tapping the selected tab scrolls to top.
- Long-press quick-action menu contents per tab.
- Badge styling.

Until these are decided, the implementation ships the old project's visual
tokens (28dp radius, 8dp elevation, `surfaceContainer` color) as
placeholders, with the configurable tab set + rearrangement + fixed "More"
behavior from this doc fully implemented.

---

## See also

- `DESIGN_LANGUAGE/README.md` — folder map + primary reference policy.
- `DESIGN_LANGUAGE/03-themes/themes-and-colors.md` — palette provides the
  `surfaceContainer` and `secondaryContainer` roles used by the bar.
- `DOCS/04-design-decisions.md` ADR-017 (configurable bottom nav), ADR-018
  (simple mode), ADR-021 (MY screen).
- `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/navigation/AnikutaNavGraph.kt`
  — the prior floating-pill implementation (read-only structural reference).
