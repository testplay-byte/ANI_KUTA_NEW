# 04-screens / bottom-nav.md

> The ANIKUTA bottom navigation bar — a floating pill design.
>
> **Primary reference:** `PROTOTYPE_REFERENCE/Anime_App/app/src/main/java/com/testplaybyte/animeapp/ui/components/BottomNavBar.kt`
> (the owner's prototype — "the perfect way to implement a bottom navigation bar").

## Design decisions (VERIFIED from prototype source)

### Container

- **Floating overlay** — the nav is a `Box` layer overlaid on top of content,
  NOT in `Scaffold.bottomBar`. Content scrolls BEHIND the nav.
  - **Why:** the owner explicitly flagged that other approaches cause "the
    background becomes behind the bottom navigation bar and it stays there and
    makes the things worse." The floating-overlay pattern avoids this.
- **Shape:** `RoundedCornerShape(28.dp)`.
- **Background:** `surfaceVariant`.
- **Shadow:** `shadowElevation = 8.dp`.
- **Outer height:** 58dp. **Pill height:** 42dp.
- **Edge padding:** 16dp horizontal + vertical from screen edges.
- **Inner padding:** 8dp horizontal inside the bar.

### Items

- **Active item:** content-sized (NO `weight` modifier), expands to show
  icon + label. `primaryContainer` bg, `onPrimaryContainer` text.
- **Inactive items:** `weight(1f)`, icon-only. Label hidden via
  `AnimatedVisibility`. Transparent bg, `onSurfaceVariant` tint.
- **Icons:** Material vector icons (`Icons.Filled.*`), 22dp. NEVER emojis.
- **Label:** 12sp, `FontWeight.SemiBold`, `maxLines = 1`. Only visible when active.
- **Active pill padding:** 14dp horizontal (active) / 10dp (inactive).

### Animation

- **Background/text color:** `animateColorAsState`, tween 300ms,
  `FastOutSlowInEasing`.
- **Label enter:** `expandHorizontally(tween 300ms) + fadeIn(tween 200ms)`.
- **Label exit:** `fadeOut(tween 100ms) + shrinkHorizontally(tween 200ms)`.

### Content padding

- All screens with the floating nav use **110dp bottom padding** on their
  scrollable content to account for the nav's height + padding.

## Wiring (Voyager adaptation)

The prototype uses Navigation Compose. ANIKUTA uses Voyager. The adaptation:

```kotlin
// In the main screen host (NOT Scaffold)
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background),
) {
    // Content fills the full screen
    Navigator(currentScreen) { ... }

    // Floating bottom nav — overlaid on top
    if (showBottomNav) {
        AnikutaBottomNavBar(
            items = navItems,
            currentRoute = currentTab,
            onSelect = { tab -> navigator.replace(tab) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
```

- NO `Scaffold` — the nav is a floating overlay.
- Hidden on detail/player screens.
- Uses `navigator.replace(tab)` with `saveState`/`restoreState` for tab switching.

## ANIKUTA extensions (per ADR-017)

The prototype has a fixed 6-item nav. ANIKUTA extends it:

- **3–7 tabs** (min 3, max ~7). The user picks which to show.
- **Rearrangeable** — drag-to-reorder in a Settings subpage.
- **One fixed tab:** "More" — always present, always rightmost. Cannot be removed
  or repositioned.
- **Available tabs:** Home, Library, Updates, History, Browse, MY, More.
- **Default first-run set:** Home · Library · Updates · More.

## ASCII layout

```
┌──────────────────────────────────────────┐
│                                          │  ← content (scrolls behind nav)
│           (screen content)               │
│                                          │
│                                          │
│   ┌──────────────────────────────────┐   │  ← 16dp edge padding
│   │  [icon] [icon label] [icon] ...  │   │  ← floating pill (28dp radius)
│   └──────────────────────────────────┘   │
└──────────────────────────────────────────┘
         ↑ active item (expanded)    ↑ inactive items (icon-only)
```

## Component spec

```kotlin
@Composable
fun AnikutaBottomNavBar(
    items: List<NavItem>,        // 3–7 items
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
)

data class NavItem(
    val route: String,
    val label: String,           // MR.strings.xxx (Moko Resources)
    val icon: ImageVector,       // Material vector icon
)
```

## See also

- [`PROTOTYPE_REFERENCE/ANALYSIS.md`](../../PROTOTYPE_REFERENCE/ANALYSIS.md) §1 — full source analysis.
- [`../01-principles/core-principles.md`](../01-principles/core-principles.md) principle #9 (floating bottom nav).
- [`../../DOCS/04-design-decisions.md`](../../DOCS/04-design-decisions.md) ADR-017 (configurable nav).
- [`../02-components/components.md`](../02-components/components.md) §5 (floating bottom nav component).
