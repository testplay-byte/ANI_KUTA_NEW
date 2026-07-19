# 02-modules / `:presentation-core` — Shared Compose theme & primitives

> `:presentation-core` is a small Android library module that holds the
> Compose design-system primitives shared across the app: Material 3 wrappers,
> reusable composables (sheets, lists, grids, badges, pickers), the Moko
> Resources `stringResource` bridge, the preference → Compose `State` bridge,
> and the per-theme color XML resources. It is consumed by `:app` and by
> `:presentation-widget`.

| | |
|---|---|
| **Path** | `../ANIYOMI/presentation-core/` |
| **Namespace** | `tachiyomi.presentation.core` |
| **Package roots** | `tachiyomi.presentation.core.*`, `mihon.presentation.core.*` |
| **Build script** | `../ANIYOMI/presentation-core/build.gradle.kts` |
| **Manifest** | `../ANIYOMI/presentation-core/src/main/AndroidManifest.xml` (empty — library only) |
| **Approx. file count** | ~30 Kotlin + ~40 color XML |

> **Note on package naming.** The module map in
> [`../00-overview/03-module-map.md`](../00-overview/03-module-map.md) lists the
> package root as `mihon.core.designsystem`. In the actual snapshot the module's
> namespace is `tachiyomi.presentation.core` (legacy Tachiyomi naming) and only
> a single helper file lives under `mihon.*` here (`mihon.presentation.core.util`
> — see below). The bulk of the design system that `mihon.core.designsystem`
> would host in newer Mihon revisions is, in this snapshot, split between
> `tachiyomi.presentation.core.*` (this module) and `eu.kanade.presentation.*`
> (inside `:app`). Treat the module-map entry as aspirational.

## Purpose & role

`presentation-core` exists so that Compose code in `:app` and in
`:presentation-widget` can share:

1. **Material 3 component wrappers** with Tachiyomi/Aniyomi-specific tweaks
   (Scaffold that handles edge-to-edge + consumed insets, Buttons with custom
   tokens, NavigationBar / NavigationRail, Tabs, Slider, FloatingActionButton,
   IconToggleButton, PullRefresh, Surface, AlertDialog).
2. **Reusable composables** that aren't Material-SDK (Pill, Badges, AdaptiveSheet,
   SectionCard, TwoPanelBox, CollapsibleBox, LabeledCheckbox, ActionButton,
   LinkIcon, WheelPicker, VerticalFastScroller, LazyList / LazyGrid helpers,
   ListGroupHeader, CircularProgressIndicator, LazyColumnWithAction,
   SettingsItems).
3. **Standard screens** reused for empty / loading / info states
   (`EmptyScreen`, `LoadingScreen`, `InfoScreen`).
4. **i18n bridge** for Moko Resources (`stringResource` / `pluralStringResource`
   composables that delegate to `tachiyomi.core.common.i18n` from `:core:common`).
5. **Preference bridge** — `Preference<T>.collectAsState()` so a
   `tachiyomi.core.common.preference.Preference` flows into Compose.
6. **Modifier / util helpers** — `secondaryItemAlpha`, `Scrollbar`,
   `LazyListState` extensions, `PaddingValues` helpers, `Elevation`, `Modifier`
   helpers, `ThemePreviews`.
7. **Custom vector icons** (`CustomIcons`, `Github`, `Discord`, `Magnet`) — SVGs
   imported from simpleicons.org via svg-to-compose.
8. **The full color palette** for every app theme — `values/colors_<theme>.xml`
   plus `values-night/colors_<theme>.xml` for dark variants.

## Build config

`../ANIYOMI/presentation-core/build.gradle.kts`

```kotlin
plugins {
    id("mihon.library")            // Android library base
    id("mihon.library.compose")    // Adds Compose
    kotlin("android")
}

android {
    namespace = "tachiyomi.presentation.core"
}
```

- Applies the `mihon.library` and `mihon.library.compose` convention plugins
  from `buildSrc/` (see [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md)).
- No build types or flavors of its own — it inherits the app's build type when
  consumed.
- Kotlin compiler opts into the usual Compose experimental APIs
  (`ExperimentalMaterial3Api`, `ExperimentalFoundationApi`,
  `ExperimentalLayoutApi`, `ExperimentalComposeUiApi`, `FlowPreview`).

### Dependencies

```kotlin
api(projects.core.common)    // exposes Preference, i18n helpers, util
api(projects.i18n)           // exposes Moko Resources MR strings

implementation(compose.activity, compose.foundation, compose.material3.core,
               compose.material.icons, compose.animation, compose.animation.graphics,
               compose.ui.tooling.preview)
debugImplementation(compose.ui.tooling)

implementation(androidx.paging.runtime, androidx.paging.compose)
implementation(kotlinx.immutables)   // ImmutableList used by many components
```

Two `api` deps are notable: `:core:common` (so consumers get `Preference` /
`stringResource` transitively) and `:i18n` (so the `MR.strings.*` accessor is
visible). `:presentation-widget` then `api`s `:i18n` and `:i18n-aniyomi` to
re-expose them to widget code.

The module does **not** depend on `:app`, `:data`, `:domain`, or `:source-api`
— it is a leaf design-system library. (The widget module pulls `:domain` in for
its data fetches; see [`presentation-widget.md`](presentation-widget.md).)

## Package layout

```
presentation-core/src/main/
├── AndroidManifest.xml                            (empty)
├── java/
│   ├── tachiyomi/presentation/core/
│   │   ├── theme/          Color.kt, Typography.kt
│   │   ├── components/     Reusable composables (see table below)
│   │   │   └── material/   Material 3 wrappers
│   │   ├── icons/          CustomIcons, Github, Discord, Magnet
│   │   ├── i18n/           Localize.kt — stringResource bridge
│   │   ├── screens/        EmptyScreen, LoadingScreen, InfoScreen
│   │   └── util/           Preference, Scrollbar, ThemePreviews, PaddingValues,
│   │                       Modifier, Elevation, LazyListState
│   └── mihon/presentation/core/util/
│       └── PagingDataUtil.kt   collectAsLazyPagingItems on StateFlow<Flow<PagingData>>
└── res/values/                                  color XML (one per theme + base)
    └── res/values-night/                        dark-mode variants
```

### `theme/` — design tokens

| File | Provides |
|---|---|
| `theme/Color.kt` | `ColorScheme.active` extension — an "active" highlight color (yellow/amber) that swaps with `isSystemInDarkTheme()`. |
| `theme/Typography.kt` | `Typography.header` extension — a `bodyMedium`-based header style with `onSurfaceVariant` color and `SemiBold` weight. |

The full Material 3 `ColorScheme`s and `Typography` are constructed in `:app`
(`eu.kanade.presentation.theme`) using the color resources from `res/values/`.
This module only ships the tokens and a couple of extensions.

### `components/` — reusable composables

| File | Provides |
|---|---|
| `AdaptiveSheet.kt` | Bottom sheet that adapts its height to content + window size. |
| `ActionButton.kt` | Standard action button used in toolbars / empty states. |
| `Badges.kt` | Small badge composables (count, status, etc.). |
| `CircularProgressIndicator.kt` | M3 progress indicator wrapper. |
| `CollapsibleBox.kt` | Box with a collapse / expand animation. |
| `LabeledCheckbox.kt` | Checkbox with a label. |
| `LazyColumnWithAction.kt` | `LazyColumn` with a trailing FAB / action slot. |
| `LazyGrid.kt`, `LazyList.kt` | Helpers + headers for paged grids/lists. |
| `ListGroupHeader.kt` | Sticky-style group header. |
| `LinkIcon.kt` | Icon button that opens an external link. |
| `Pill.kt` | The little rounded "pill" used for badges and tags. |
| `SectionCard.kt` | Card with a header + content slot — used throughout settings. |
| `SettingsItems.kt` | Reusable settings row composables (switch, slider, list prefs). |
| `TwoPanelBox.kt` | Two-pane layout for tablets / foldables. |
| `VerticalFastScroller.kt` | Fast-scroll handle for `LazyColumn`. |
| `WheelPicker.kt` | Wheel-style picker (used in date / number pickers). |

### `components/material/` — Material 3 wrappers

Each file wraps the corresponding M3 component with project-specific defaults
or fixes for issues in the upstream Compose M3 release.

| File | Wraps |
|---|---|
| `AlertDialog.kt` | `androidx.compose.material3.AlertDialog` |
| `Button.kt` | `Button`, `OutlinedButton`, `TextButton` (with custom tokens) |
| `Constants.kt` | `Padding` class, `MaterialTheme.padding` extension, `DISABLED_ALPHA` (.38f), `SECONDARY_ALPHA` (.78f), `topSmallPaddingValues` |
| `FloatingActionButton.kt` | `FloatingActionButton` + `ExtendedFloatingActionButton` |
| `IconButtonTokens.kt` | Sizing tokens for icon buttons |
| `IconToggleButton.kt` | `IconToggleButton` |
| `NavigationBar.kt` | Bottom `NavigationBar` |
| `NavigationRail.kt` | Tablet/foldable `NavigationRail` |
| `PullRefresh.kt` | Pull-to-refresh wrapper |
| `Scaffold.kt` | A custom `Scaffold` that handles edge-to-edge insets and consumed insets (Apache-2.0 licensed, adapted from AOSP) |
| `Slider.kt` | `Slider` wrapper |
| `Surface.kt` | `Surface` wrapper |
| `Tabs.kt` | `TabRow` / `Tab` wrappers |

### `screens/` — standard state screens

| File | Use |
|---|---|
| `EmptyScreen.kt` | The "no items" placeholder with an icon and optional action. |
| `InfoScreen.kt` | A screen with icon + heading + subtitle + accept/reject buttons + content slot (used by onboarding-style flows). |
| `LoadingScreen.kt` | Centered `CircularProgressIndicator`. |

### `i18n/` — Moko Resources bridge

`i18n/Localize.kt` exposes four `@Composable @ReadOnlyComposable` functions:

```kotlin
fun stringResource(resource: StringResource): String
fun stringResource(resource: StringResource, vararg args: Any): String
fun pluralStringResource(resource: PluralsResource, count: Int): String
fun pluralStringResource(resource: PluralsResource, count: Int, vararg args: Any): String
```

Each delegates to the `tachiyomi.core.common.i18n` helpers from `:core:common`,
which resolve `MR.strings.*` (`:i18n`) and `AR.strings.*` (`:i18n-aniyomi`)
via the Moko Resources Android implementation. This is the canonical way to
localise strings in Compose code across the project.

### `util/` — Compose utilities

| File | Provides |
|---|---|
| `Preference.kt` | `Preference<T>.collectAsState()` — wraps `Preference.changes()` in a `State<T>` with `get()` as the initial value. |
| `Scrollbar.kt` | A scrollbar overlay Composable for `LazyList`. |
| `ThemePreviews.kt` | `@PreviewLightDark` helper annotations for theme previews. |
| `PaddingValues.kt` | `PaddingValues` arithmetic helpers. |
| `Modifier.kt` | `secondaryItemAlpha()`, etc. |
| `Elevation.kt` | Tonal-elevation helpers. |
| `LazyListState.kt` | Extensions on `LazyListState` (e.g. scroll-to-top binding). |

### `mihon/presentation/core/util/`

| File | Provides |
|---|---|
| `PagingDataUtil.kt` | `StateFlow<Flow<PagingData<T>>>.collectAsLazyPagingItems()` — bridges the wrapped-paging-data pattern used by some `ScreenModel`s into Compose `LazyPagingItems`. |

### `icons/`

SVG-to-Compose vector icons:

| File | Icon |
|---|---|
| `CustomIcons.kt` | The `object CustomIcons` container (simpleicons.org imports). |
| `Github.kt` | GitHub logo. |
| `Discord.kt` | Discord logo. |
| `Magnet.kt` | Magnet icon (torrent-related UI). |

## The color system

Aniyomi ships **many** built-in color themes; each is a pair of XML files — one
in `res/values/` (light) and one in `res/values-night/` (dark). The M3
`ColorScheme` for each theme is constructed in `:app` from these color
resources at runtime.

### Theme color files

Both a `values/colors_<theme>.xml` and a `values-night/colors_<theme>.xml`
exist for every theme in this list:

| Theme | File prefix | Notes |
|---|---|---|
| Default (Tachiyomi) | `colors_tachiyomi.xml` | M3 builder-generated from key colors `#2979FF` primary, `#47A84A` tertiary. |
| Tidal Wave | `colors_tidalwave.xml` | |
| Yin & Yang | `colors_yinyang.xml` | |
| Nord | `colors_nord.xml` | Uses the Nord palette (`#5E81AC` primary, etc.). |
| Teal & Turquoise | `colors_tealturqoise.xml` | |
| Midnight Dusk | `colors_midnightdusk.xml` | |
| Strawberry Daiquiri | `colors_strawberry.xml` | |
| Yotsuba | `colors_yotsuba.xml` | |
| Tako | `colors_tako.xml` | |
| Green Apple | `colors_greenapple.xml` | |
| Monochrome | `colors_monochrome.xml` | |
| Cloudflare | `color_cloudflare.xml` | |
| Sapphire | `color_sapphire.xml` | |
| Doom | `color_doom.xml` | |
| Matrix | `color_matrix.xml` | |
| Lavender | `color_lavender.xml` | |

Plus the base `colors.xml` which holds the splash color, cover placeholder,
divider, AMOLED overrides (`amoled_background`, `amoled_onBackground`,
`amoled_surface`, `amoled_surfaceContainer*`), and Material constants
(`md_black_1000_12`, `md_white_1000_12`).

### AMOLED mode

When the user enables AMOLED mode in Settings, `:app` overrides the
`background`, `surface`, and `surfaceContainer*` slots of the active
`ColorScheme` with the `amoled_*` colors from `colors.xml` (pure black +
near-black surface tiers). The override happens in `:app`, not here — this
module just ships the raw color values.

## Typography

The project uses Material 3's default `Typography` and adds one extension in
`theme/Typography.kt`:

```kotlin
val Typography.header: TextStyle
    @Composable get() = bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
```

This `header` style is the canonical section-header text style across screens
and `SectionCard`s.

## Shapes

No custom shapes are defined in this module. The project uses the M3 default
`Shapes` from `androidx.compose.material3.Shapes`; per-component corner radii
are baked into the Material wrappers in `components/material/`. The widget
module ships its own `appwidget_*_radius` dimens (see
[`presentation-widget.md`](presentation-widget.md)).

## Dark / light theme handling

`presentation-core` is **passive** about dark mode — it ships paired
`values/colors_*.xml` + `values-night/colors_*.xml` resources so Android's
resource resolver picks the right palette based on the system / app theme
mode. The actual decision of *which* theme to apply is made in `:app`:

1. `UiPreferences.themeMode()` (`light` / `dark` / `system`) is read in
   `App.onCreate()` and pushed to the AppCompat delegate via
   `setAppCompatDelegateThemeMode(...)`.
2. `:app` constructs the M3 `ColorScheme` (light or dark variant) from the
   active theme's color resources.
3. The `ColorScheme.active` extension in `theme/Color.kt` reacts to
   `isSystemInDarkTheme()` to flip the "active" accent between amber and
   yellow.

This separation keeps `presentation-core` free of any theme-selection logic —
it only provides tokens and dark/light pairs.

## Reusable components provided

Quick index of what `:app` and `:presentation-widget` get when they
`implementation(projects.presentationCore)`:

- **Layout**: `Scaffold`, `TwoPanelBox`, `SectionCard`, `AdaptiveSheet`,
  `CollapsibleBox`, `LazyColumnWithAction`, `LazyGrid`, `LazyList`,
  `ListGroupHeader`, `VerticalFastScroller`.
- **Actions**: `Button`/`OutlinedButton`/`TextButton`, `FloatingActionButton`,
  `ActionButton`, `LinkIcon`, `IconToggleButton`, `NavigationBar`,
  `NavigationRail`, `Tabs`.
- **Feedback**: `CircularProgressIndicator`, `PullRefresh`, `Badges`, `Pill`,
  `AlertDialog`.
- **Inputs**: `LabeledCheckbox`, `Slider`, `WheelPicker`, `SettingsItems`.
- **State screens**: `EmptyScreen`, `LoadingScreen`, `InfoScreen`.
- **Tokens**: `MaterialTheme.padding`, `DISABLED_ALPHA`, `SECONDARY_ALPHA`,
  `ColorScheme.active`, `Typography.header`.
- **Bridges**: `stringResource`, `pluralStringResource`,
  `Preference<T>.collectAsState()`,
  `StateFlow<Flow<PagingData<T>>>.collectAsLazyPagingItems()`.
- **Icons**: `CustomIcons`, `Github`, `Discord`, `Magnet`.

## Key files table

| File | Why it matters |
|---|---|
| `../ANIYOMI/presentation-core/build.gradle.kts` | Module config; `api(projects.core.common)` + `api(projects.i18n)` exports. |
| `../ANIYOMI/presentation-core/src/main/AndroidManifest.xml` | Empty placeholder (library manifest). |
| `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/theme/Color.kt` | `ColorScheme.active` extension. |
| `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/theme/Typography.kt` | `Typography.header` extension. |
| `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/i18n/Localize.kt` | The `stringResource` / `pluralStringResource` Compose bridge to Moko Resources. |
| `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/util/Preference.kt` | `Preference<T>.collectAsState()` bridge. |
| `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Scaffold.kt` | Custom edge-to-edge Scaffold (adapted from AOSP). |
| `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Constants.kt` | `Padding`, `MaterialTheme.padding`, alpha constants. |
| `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/screens/InfoScreen.kt` | Standard info / onboarding-style screen. |
| `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/components/SectionCard.kt` | Reusable card-with-header used throughout settings. |
| `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/components/WheelPicker.kt` | Wheel picker used in date / number dialogs. |
| `../ANIYOMI/presentation-core/src/main/java/mihon/presentation/core/util/PagingDataUtil.kt` | `StateFlow<Flow<PagingData<T>>>.collectAsLazyPagingItems()` helper. |
| `../ANIYOMI/presentation-core/src/main/res/values/colors.xml` | Base colors + AMOLED overrides. |
| `../ANIYOMI/presentation-core/src/main/res/values/colors_tachiyomi.xml` | Default theme M3 palette (light). |
| `../ANIYOMI/presentation-core/src/main/res/values-night/colors_tachiyomi.xml` | Default theme M3 palette (dark). |

## See also

- [`app.md`](app.md) — the consumer; `:app` builds the active `ColorScheme`
  from these color resources.
- [`presentation-widget.md`](presentation-widget.md) — the other consumer.
- [`../00-overview/03-module-map.md`](../00-overview/03-module-map.md) — module roster.
- [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md) — `Preference` model used by `Preference.collectAsState()`.
- [`i18n.md`](i18n.md) and [`i18n-aniyomi.md`](i18n-aniyomi.md) — the Moko Resources modules that back `stringResource`.
- [`../06-ui/theme-design.md`](../06-ui/theme-design.md) — full theme discussion (cross-link target).
- [`../06-ui/components.md`](../06-ui/components.md) — component catalog (cross-link target).
