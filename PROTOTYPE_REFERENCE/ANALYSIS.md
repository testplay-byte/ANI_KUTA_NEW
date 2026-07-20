# Prototype Analysis — Design Language Reference

> Detailed analysis of the prototype project at `Anime_App/`. The owner explicitly
> flagged these screens as design references for ANIKUTA. This doc feeds directly
> into `DESIGN_LANGUAGE/`.

---

## 1. Bottom Navigation Bar (`ui/components/BottomNavBar.kt`)

**The owner's verdict:** "The perfect way to implement a bottom navigation bar."

### Key design decisions (VERIFIED from source)

| Aspect | Implementation |
|---|---|
| **Container** | Floating pill — overlaid as a `Box` layer, NOT in `Scaffold.bottomBar` |
| **Why floating?** | Content scrolls BEHIND the nav (the owner explicitly flagged this: "the background becomes behind the bottom navigation bar and it stays there and makes the things worse" with other approaches) |
| **Shape** | `RoundedCornerShape(28.dp)` |
| **Background** | `surfaceVariant` |
| **Shadow** | `shadowElevation = 8.dp` |
| **Bar height** | 58dp (outer) / 42dp (pill) |
| **Padding** | 16dp horizontal + vertical from screen edges; 8dp horizontal inside the bar |
| **Active item** | Content-sized (NO `weight`), expands to show icon + label. `primaryContainer` bg, `onPrimaryContainer` text. |
| **Inactive items** | `weight(1f)`, icon-only. Label hidden via `AnimatedVisibility`. Transparent bg, `onSurfaceVariant` tint. |
| **Icons** | Material vector icons (`Icons.Filled.*`), NEVER emojis. 22dp size. |
| **Label** | 12sp, `FontWeight.SemiBold`, `maxLines = 1`. Only visible when active. |
| **Active pill padding** | 14dp horizontal (active) / 10dp (inactive) |
| **Animation** | `animateColorAsState` (300ms, `FastOutSlowInEasing`) for bg/text; `expandHorizontally + fadeIn` (300ms) for label enter; `fadeOut + shrinkHorizontally` (200ms/100ms) for exit. |
| **Content padding** | 110dp bottom padding on scrollable content to account for the floating nav. |

### How it's wired (`navigation/AnimeNavHost.kt`)

```
Box(fillMaxSize) {
    NavHost(...)          // content fills full screen
    if (showBottomNav) {
        BottomNavBar(...)  // overlaid on top, aligned BottomCenter
    }
}
```

- NO `Scaffold` — the nav is a floating overlay.
- Hidden on detail screens (`currentRoute?.startsWith("detail") != true`).
- Navigation uses `popUpTo(startDestination) { saveState = true }` + `launchSingleTop` + `restoreState`.

### What to port to ANIKUTA

- ✅ The floating-overlay pattern (Box, not Scaffold.bottomBar).
- ✅ The active-expands / inactive-shrinks behavior.
- ✅ The 28dp rounded pill, 42/58dp heights, 16dp edge padding.
- ✅ The `AnimatedVisibility` label expand/shrink.
- ✅ Material vector icons (we already have `material-icons-extended`).
- ⚠️ We'll use Voyager (not Navigation Compose) — adapt the route logic.
- ⚠️ ADR-017 says 3–7 tabs, rearrangeable, fixed "More" — extend the prototype's fixed 6-item list to be configurable.

---

## 2. Collapsing Header (`ui/components/CollapsingHeader.kt`)

A reusable title that shrinks on scroll. Used on Schedule, Search, Settings, etc.

| Aspect | Implementation |
|---|---|
| **Expanded** | 32sp (`displayLarge`), bold, `letterSpacing = -0.02sp` |
| **Collapsed** | 22sp (`headlineMedium`), bold — when `scrollState.value > 20` |
| **Animation** | `animateFloatAsState`, tween 300ms, `FastOutSlowInEasing` |
| **Padding** | Expanded: top 8dp / bottom 4dp. Collapsed: top 4dp / bottom 2dp. |
| **Pinned** | Always visible (sits OUTSIDE the scroll Column). Never scrolls away. |
| **Actions slot** | `actions: @Composable RowScope.() -> Unit` — for trailing buttons/toggles. |
| **Status bar** | Uses `.statusBarsPadding()` — ⚠️ this conflicts with our design language principle #1 (edge-to-edge, no status bar padding). **Decision needed.** |

### What to port

- ✅ The shrink-on-scroll animation (32sp → 22sp).
- ✅ The pinned-outside-scroll pattern.
- ✅ The `actions` slot.
- ⚠️ **Reconcile status bar padding** with our principle #1 (edge-to-edge). The prototype uses `statusBarsPadding()`; our design language says "no status bar padding." I recommend: keep `statusBarsPadding()` for the collapsing header (it looks good), but make the CONTENT behind it edge-to-edge. This is a common pattern — the header respects the status bar, the content doesn't.

---

## 3. Schedule Screen (`ui/screens/ScheduleScreen.kt`)

**The owner's verdict:** "Quite good. Simple, minimal, does the work."

### Layout (top to bottom)

1. **CollapsingHeader("Schedule")** — pinned, shrinks on scroll.
2. **Day selector** — horizontal `LazyRow` of 7 day pills (62×60dp each).
   - Labels: "Today", "Tomorrow", then short weekday (EEE).
   - Each pill shows: weekday label (11sp bold) + airing count (16sp bold) or "—".
   - Active: `primaryContainer` bg + `onPrimaryContainer` text.
   - Inactive: `surfaceVariant` bg + `onSurfaceVariant` text.
   - Stays visible (between header and scroll content).
3. **Airing list** — vertical scroll, each row:
   - 48×64dp cover thumbnail with EP badge overlay (bottom, translucent black, 8sp white).
   - Title (14sp semibold, 2 lines max) + meta row (format, ★ score, episode count — all 11sp).
   - Time column (56dp wide): HH:MM (14sp bold) + relative time (9sp, "in 3h" / "2h ago").
   - Past entries: `alpha = 0.5f`.
   - Next-up entry: `primaryContainer` tint (0.4 alpha) + 1dp `primary` border.
4. **States** — loading spinner, error message, empty state (calendar icon + "Nothing airing").
5. **Bottom padding** — 110dp for the floating nav.

### Key details

- Uses AniList's `airingAt` (unix seconds) for scheduling.
- `relativeTime()` helper: "in 3h" / "2h ago" / "in 12m" / "2d ago".
- `★` is Unicode `\u2605` (BLACK STAR), NOT an emoji.
- Score color uses `WarnDark` (orange-ish) — a functional color for scores/warnings.

### What to port

- ✅ The day-selector pattern (horizontal pills with count).
- ✅ The airing-row layout (cover + EP badge + title/meta + time).
- ✅ The past-dimmed / next-up-highlighted treatment.
- ✅ The loading/error/empty states.
- ✅ The 110dp bottom padding convention.

---

## 4. Search Screen (`ui/screens/SearchScreen.kt`)

**The owner's verdict:** "The best page. UI is proper, logic is proper, functionality is proper."

### Layout (top to bottom)

1. **Collapsing topbar** — Row: [Title "Search"] [SourceToggle (right)] [SearchBar].
   - When scrolled: title shrinks, source toggle fades+shrinks to 0, search bar moves BESIDE the title.
2. **Search bar** — custom `BasicTextField` (NOT Material3 `TextField`).
   - Rounded pill shape, `surfaceVariant` bg.
   - Leading search icon, trailing clear icon.
   - Debounced search (delay before firing query).
3. **Filter chips** — horizontal row of filter chips (genre, format, etc.).
   - Opens a `FilterSheet` (bottom-up modal) for advanced filters.
4. **Results** — grid or list of `AnimeCard` components.
   - Cover image + title + score badge.
5. **States** — loading, error, empty, no-results.
6. **Bottom padding** — 110dp for floating nav.

### Key details

- Uses `BasicTextField` for the search bar (custom styled, not Material3 default).
- Debounced search with `kotlinx.coroutines.delay`.
- Source toggle (AniList vs extension) — aligns with our ADR-011 (dual metadata source).
- `FilterSheet` is a bottom-up modal (matches our design language principle #2: no drag handle, partial height).

### What to port

- ✅ The collapsing search bar pattern (moves beside title on scroll).
- ✅ The custom `BasicTextField` search bar (pill shape, leading/trailing icons).
- ✅ The debounced search.
- ✅ The filter chips + `FilterSheet` bottom-up modal.
- ✅ The results grid with `AnimeCard`.
- ✅ The source toggle (maps to our ADR-011 metadata source preference).

---

## 5. Settings Screen (`ui/screens/SettingsScreen.kt`)

**The owner's verdict:** "Good too."

### Layout

1. **CollapsingHeader("Settings")** — pinned.
2. **SettingsGroup sections** — surface-tinted cards, each with a label header.
   - "Appearance" — theme toggle (dark/light segmented).
   - "Display" — single-line titles toggle, poster style selector.
   - "Animations" — toggle animations on/off.
   - "Data Management" — clear history, clear library (with confirm dialogs).
   - "About" — version info.
3. **Custom toggle** — NOT the default Material3 `Switch`. A custom pill toggle.
4. **Segmented theme toggle** — dark/light with icons (`DarkMode` / `LightMode`).
5. **Text-only segmented selectors** — for poster style, etc.
6. **110dp bottom padding** for floating nav.

### Key details

- `SettingsGroup(label)` composable wraps settings in a surface-tinted card.
- `SettingRow(title, description, trailing)` — standard row with title, description, and a trailing composable (toggle, selector, etc.).
- `CustomToggle` — a custom pill-shaped toggle (not Material3 Switch).
- `ThemeSegmentedToggle` — a 2-way segmented button with sun/moon icons.
- `StackedRow` — a setting row where the control is below the title (for wider controls).
- Confirm dialogs for destructive actions (clear history, clear library).
- Settings stored via `SettingsRepository` (DataStore/SharedPreferences).

### What to port

- ✅ The `SettingsGroup` card pattern (surface-tinted, labeled sections).
- ✅ The `SettingRow` pattern (title + description + trailing).
- ✅ The custom toggle (pill-shaped, not default Switch).
- ✅ The segmented theme toggle (dark/light with icons).
- ✅ The confirm-dialog pattern for destructive actions.
- ⚠️ Our ADR-018 adds "simple mode" (hide advanced settings) — extend the pattern.
- ⚠️ Our settings will use Koin-injected `SettingsRepository`, not direct instantiation.

---

## 6. Theme (`theme/Color.kt` + `theme/Theme.kt`)

### Prototype's palette

The prototype uses a **dark purple** M3 Expressive palette. BUT the owner wants
**#B1F256 (lime green)** as ANIKUTA's primary color. So we adapt the STRUCTURE
but use our own colors.

### Structure to port

| Element | Prototype | ANIKUTA |
|---|---|---|
| Surface tonal tiers | `surface1`–`surface5` (5 levels) | Same structure, our colors |
| Text tiers | `text` / `textMuted` / `textSubtle` (3 levels) | Same |
| M3 color roles | primary, primaryContainer, secondary, tertiary, error, etc. | Same roles, #B1F256-based |
| Functional colors | `warn` (orange), `success` (green) | Same |
| Default theme | Dark | Dark (per owner preference) |
| Status bar | Matches `background` color | Same |

### ANIKUTA's primary color: #B1F256

The owner specified `#B1F256` as the starter primary color. This is a bright
lime green. We'll derive the full M3 color scheme from it:
- `primary` = #B1F256
- `onPrimary` = dark (near-black, for contrast on the bright green)
- `primaryContainer` = a darker/desaturated variant of #B1F256
- `onPrimaryContainer` = light text on the container

The theme is **customizable** (ADR-015) — the user can change the palette later.
The `#B1F256` palette is the default.

---

## 7. Other Components Worth Noting

| Component | File | What it does |
|---|---|---|
| `AnimeCard` | `ui/components/AnimeCard.kt` | Cover image + title + score badge (used in grids/lists) |
| `HeroCarousel` | `ui/components/HeroCarousel.kt` | Featured anime carousel on Home |
| `ContinueWatching` | `ui/components/ContinueWatching.kt` | Continue-watching row on Home/History |
| `FilterSheet` | `ui/components/FilterSheet.kt` | Bottom-up filter modal for Search |
| `DetailScreen` | `ui/screens/DetailScreen.kt` | Anime details page |

---

## Summary: What ANIKUTA's design language inherits from this prototype

1. **Floating bottom nav** (overlay, not Scaffold) — the #1 priority.
2. **Collapsing header** — shrink-on-scroll title, pinned.
3. **Surface-tinted section cards** — for settings and grouped content.
4. **Custom toggles** — pill-shaped, not default Material3 Switch.
5. **Segmented selectors** — for 2-way and multi-way choices.
6. **Day-selector pills** — for the schedule screen.
7. **Airing-row layout** — cover + EP badge + title/meta + time.
8. **Custom search bar** — BasicTextField pill, not Material3 TextField.
9. **110dp bottom padding** — standard for all screens with the floating nav.
10. **Material vector icons** — never emojis.

## What ANIKUTA changes/adds

1. **Primary color** — #B1F256 (lime green), not the prototype's purple.
2. **Voyager navigation** — not Navigation Compose.
3. **Koin DI** — not direct instantiation.
4. **SQLDelight** — not the prototype's repository pattern.
5. **Configurable bottom nav** — 3–7 tabs, rearrangeable, fixed "More" (ADR-017).
6. **Simple mode** — hide advanced settings (ADR-018).
7. **Design language docs** — every component spec'd in `DESIGN_LANGUAGE/`.
