# 06-ui / Theme & design system

> Material 3, 19 selectable themes (16 XML-backed + Cottoncandy + Mocha +
> dynamic Monet), an explicit AMOLED override, and one shared `ColorScheme`
> constructor. This doc explains how a user's theme choice becomes the
> `MaterialTheme.colorScheme` that every Composable reads.

The design system is split across two modules:

- **`:presentation-core`** ships the per-theme color XML resources, the two
  theme-token extensions (`ColorScheme.active`, `Typography.header`), the Moko
  Resources `stringResource` bridge, the `Preference.collectAsState()` bridge,
  the Material-3 component wrappers (`Scaffold`, `NavigationBar`,
  `FloatingActionButton`, …), and the reusable primitives (`Pill`, `Badge`,
  `SectionCard`, `WheelPicker`, …). See
  [`02-modules/presentation-core.md`](../02-modules/presentation-core.md) for
  the full component inventory.
- **`:app`** owns the *active* `ColorScheme` construction. The
  `eu.kanade.presentation.theme` package (inside `:app`) holds the
  `TachiyomiTheme` composable, the abstract `BaseColorScheme`, the 18 named
  `*ColorScheme` objects, the `MonetColorScheme` for dynamic color, and the
  `AppTheme` enum + `UiPreferences` storage.

This split keeps `:presentation-core` a leaf design-system library (no theme
selection logic) while letting `:app` own the runtime theme decision.

## Material 3 as the base

Aniyomi uses **`androidx.compose.material3`** end-to-end:

- `MaterialTheme` is the composition root for every screen.
- `ColorScheme` is the only color source — no hardcoded `Color(0xFF…)` in
  feature screens; everything reads `MaterialTheme.colorScheme.*`.
- `Typography` is the M3 default (`androidx.compose.material3.Typography()`)
  plus one project extension (see [Typography](#typography) below).
- `Shapes` is the M3 default; per-component corner radii are baked into the
  Material wrappers in
  `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/`.
- `RippleConfiguration` / `RippleAlpha` for the player uses a custom
  `playerRippleConfiguration` (black/white ripple at 10 % alpha — see
  `../ANIYOMI/app/src/main/java/eu/kanade/presentation/theme/TachiyomiTheme.kt`)
  so ripples stay visible over video.

The Compose M3 wrappers shipped from `:presentation-core` (e.g. the custom
edge-to-edge `Scaffold` adapted from AOSP, the `NavigationBar` /
`NavigationRail`, the `FloatingActionButton`, `Slider`, `Surface`,
`AlertDialog`, `Tabs`, `IconToggleButton`) exist because the upstream M3
release at the time of the snapshot had bugs or missing edge-to-edge handling.
See [`02-modules/presentation-core.md`](../02-modules/presentation-core.md)
§"components/material/" for the full list.

## The 19 selectable themes

The user-visible list is the `AppTheme` enum
(`../ANIYOMI/app/src/main/java/eu/kanade/domain/ui/model/AppTheme.kt`).
Each entry maps 1-to-1 to a `*ColorScheme` object registered in the
`colorSchemes` map inside `TachiyomiTheme.kt`. Three of the enum entries
(`DARK_BLUE`, `HOT_PINK`, `BLUE`) are deprecated (titleRes = `null`) and
hidden from the picker; they map to nothing in the modern code.

| `AppTheme` | `*ColorScheme` object | XML color resource? | Notes |
|---|---|---|---|
| `DEFAULT` | `TachiyomiColorScheme` | `colors_tachiyomi.xml` | Default; M3 builder-generated from `#2979FF` primary, `#47A84A` tertiary. |
| `MONET` | `MonetColorScheme` | — (dynamic) | Material You. Hidden pre-Android 12 (see [Dynamic color](#dynamic-color-android-12)). |
| `CLOUDFLARE` | `CloudflareColorScheme` | `color_cloudflare.xml` | |
| `COTTONCANDY` | `CottoncandyColorScheme` | — (pure Kotlin) | Colors hard-coded in the `*ColorScheme.kt`. |
| `DOOM` | `DoomColorScheme` | `color_doom.xml` | |
| `GREEN_APPLE` | `GreenAppleColorScheme` | `colors_greenapple.xml` | |
| `LAVENDER` | `LavenderColorScheme` | `color_lavender.xml` | |
| `MATRIX` | `MatrixColorScheme` | `color_matrix.xml` | |
| `MIDNIGHT_DUSK` | `MidnightDuskColorScheme` | `colors_midnightdusk.xml` | |
| `MOCHA` | `MochaColorScheme` | — (pure Kotlin) | Colors hard-coded in the `*ColorScheme.kt`. |
| `SAPPHIRE` | `SapphireColorScheme` | `color_sapphire.xml` | |
| `NORD` | `NordColorScheme` | `colors_nord.xml` | Uses the Nord palette (`#5E81AC` primary, etc.). |
| `STRAWBERRY_DAIQUIRI` | `StrawberryColorScheme` | `colors_strawberry.xml` | |
| `TAKO` | `TakoColorScheme` | `colors_tako.xml` | |
| `TEALTURQUOISE` | `TealTurqoiseColorScheme` | `colors_tealturqoise.xml` | (sic — the file/object use the misspelling `tealturqoise`.) |
| `TIDAL_WAVE` | `TidalWaveColorScheme` | `colors_tidalwave.xml` | |
| `YINYANG` | `YinYangColorScheme` | `colors_yinyang.xml` | Yin & Yang. |
| `YOTSUBA` | `YotsubaColorScheme` | `colors_yotsuba.xml` | |
| `MONOCHROME` | `MonochromeColorScheme` | `colors_monochrome.xml` | |

> **Counting note.** The briefing's prose says "17 themes" loosely. The
> *actual* snapshot ships **19 selectable themes**: 16 with paired
> `values/colors_<theme>.xml` + `values-night/colors_<theme>.xml` resources in
> `:presentation-core`, two pure-Kotlin (`Cottoncandy`, `Mocha`), and one
> dynamic (`Monet`). The 16 XML-backed themes are exactly the ones listed in
> [`02-modules/presentation-core.md`](../02-modules/presentation-core.md)
> §"Theme color files"; `Cottoncandy`, `Mocha`, and `Monet` have **no** XML
> file — their `ColorScheme` is constructed directly from `Color(0xFF…)`
> literals inside the `:app` colorscheme objects.

The `*ColorScheme` objects all live under
`../ANIYOMI/app/src/main/java/eu/kanade/presentation/theme/colorscheme/`.
The 16 XML color files live under
`../ANIYOMI/presentation-core/src/main/res/values/` (light) and
`../ANIYOMI/presentation-core/src/main/res/values-night/` (dark) — see the
"Theme color files" table in
[`02-modules/presentation-core.md`](../02-modules/presentation-core.md) for
the full file list.

## The color system (light / dark / AMOLED)

Every `*ColorScheme` object subclasses `BaseColorScheme`
(`../ANIYOMI/app/src/main/java/eu/kanade/presentation/theme/colorscheme/BaseColorScheme.kt`)
and overrides two `ColorScheme`s:

```kotlin
internal abstract class BaseColorScheme {
    abstract val darkScheme: ColorScheme
    abstract val lightScheme: ColorScheme
    fun getColorScheme(isDark: Boolean, isAmoled: Boolean): ColorScheme { … }
}
```

### How the active `ColorScheme` is constructed

`TachiyomiTheme` is the project's `MaterialTheme` wrapper. Every entry-point
that hosts Compose — `MainActivity`, `CrashActivity`, `WebViewActivity`,
the reader/player's ComposeView overlays, and `@Preview` composables — goes
through it (often indirectly via the `setComposeContent` helper, see
[`compose-migration.md`](compose-migration.md)).

```kotlin
// ../ANIYOMI/app/src/main/java/eu/kanade/presentation/theme/TachiyomiTheme.kt
@Composable
fun TachiyomiTheme(
    appTheme: AppTheme? = null,        // default: read from UiPreferences
    amoled: Boolean? = null,           // default: read from UiPreferences
    content: @Composable () -> Unit,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    BaseTachiyomiTheme(
        appTheme  = appTheme  ?: uiPreferences.appTheme().get(),
        isAmoled  = amoled    ?: uiPreferences.themeDarkAmoled().get(),
        content   = content,
    )
}
```

`BaseTachiyomiTheme` calls `getThemeColorScheme(appTheme, isAmoled)`:

```kotlin
val colorScheme = if (appTheme == AppTheme.MONET) {
    MonetColorScheme(LocalContext.current)        // dynamic
} else {
    colorSchemes.getOrDefault(appTheme, TachiyomiColorScheme)
}
return colorScheme.getColorScheme(
    isSystemInDarkTheme(),
    isAmoled,
)
```

`BaseColorScheme.getColorScheme(isDark, isAmoled)` is the single decision
point:

| State | Returned `ColorScheme` |
|---|---|
| `isDark = false` | `lightScheme` |
| `isDark = true`, `isAmoled = false` | `darkScheme` |
| `isDark = true`, `isAmoled = true` | `darkScheme.copy(background = Black, onBackground = White, surface = Black, onSurface = White, surfaceVariant = surfaceContainer, surfaceContainerLowest/Low/Container = surfaceContainer, surfaceContainerHigh = 0xFF131313, surfaceContainerHighest = 0xFF1B1B1B)` |

So:

- **Light mode** = the theme's `lightScheme`.
- **Dark mode** = the theme's `darkScheme`.
- **AMOLED mode** = the dark scheme with `background` / `surface` forced to
  pure `Color.Black`, `onBackground` / `onSurface` forced to `Color.White`,
  and the `surfaceContainer*` tiers swapped to a near-black 3-step ramp
  (`0xFF0C0C0C` → `0xFF131313` → `0xFF1B1B1B`). The M3 navigation-bar
  guideline says the bar background can't be pure black when content scrolls
  behind it, hence the dedicated `surfaceContainer` ramp.

### Where the color values come from

For the 16 XML-backed themes, the `*ColorScheme` object's `lightScheme` /
`darkScheme` are built from the literal ARGB hex values from the matching
`colors_<theme>.xml` / `values-night/colors_<theme>.xml`. For example,
`TachiyomiColorScheme`
(`../ANIYOMI/app/src/main/java/eu/kanade/presentation/theme/colorscheme/TachiyomiColorScheme.kt`)
opens with a doc-comment listing the key colors:

```
Primary   #2979FF
Secondary #2979FF
Tertiary  #47A84A
Neutral   #919094
```

and then constructs `darkColorScheme(...)` / `lightColorScheme(...)` with the
full M3 token set (`primary`, `onPrimary`, `primaryContainer`,
`onPrimaryContainer`, `inversePrimary`, `secondary`, `onSecondary`,
`secondaryContainer`, `tertiary`, `tertiaryContainer`, `background`,
`surface`, `surfaceVariant`, `onSurfaceVariant`, `surfaceTint`,
`inverseSurface`, `inverseOnSurface`, `error` / `onError` / `errorContainer`,
`outline`, `outlineVariant`, and the six `surfaceContainer*` tiers).

Inline comments document which tokens are *semantically* used by the app —
e.g. `secondary` = unread badge color, `tertiary` = downloaded badge color,
`secondaryContainer` = the navigation-bar selector pill / progress indicator
remaining color, `surfaceContainer` = the navigation bar background. This is
why a theme author can't freely rebind these slots — the library cards,
badges, and the bottom nav rely on specific roles.

For `CottoncandyColorScheme` and `MochaColorScheme` the colors are inlined in
the `.kt` file (no XML). For `MonetColorScheme` they come from the system
(see [Dynamic color](#dynamic-color-android-12)).

### Base colors & AMOLED overrides

`../ANIYOMI/presentation-core/src/main/res/values/colors.xml` holds:

- the splash-screen color (`splash_background`),
- the cover-placeholder color (`cover_placeholder`),
- the divider color,
- the **AMOLED overrides**: `amoled_background`, `amoled_onBackground`,
  `amoled_surface`, `amoled_surfaceContainer`, `amoled_surfaceContainerHigh`,
  `amoled_surfaceContainerHighest` (these back the legacy
  `ThemePrefWidget` preview and any XML-based views that still need the
  AMOLED colors),
- two Material constants: `md_black_1000_12`, `md_white_1000_12`.

These are *not* what the Compose `ColorScheme` reads at runtime (the
`*ColorScheme` objects hard-code the AMOLED ramp), but they keep the few
remaining XML layouts (e.g. the theme preview widget, the reader's
`ReaderActivityBinding`) consistent.

## Dynamic color (Android 12+)

`AppTheme.MONET` enables Material You. The implementation
(`../ANIYOMI/app/src/main/java/eu/kanade/presentation/theme/colorscheme/MonetColorScheme.kt`)
branches on SDK:

| SDK | Behaviour |
|---|---|
| `≥ S (31)` | `MonetSystemColorScheme` — delegates to `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` from `androidx.compose.material3`. |
| `≥ O_MR1 (27)` | `MonetCompatColorScheme` — extracts the wallpaper's primary color via `WallpaperManager.getWallpaperColors(FLAG_SYSTEM).primaryColor.toArgb()`, then runs it through Google's `SchemeContent` + `MaterialDynamicColors` (the same HCT engine M3 uses internally) to build a full `ColorScheme`. Also reads `UiModeManager.contrast` on `≥ UPSIDE_DOWN_CAKE (34)` for contrast adjustment. |
| `< O_MR1` | Falls back to `TachiyomiColorScheme`. |

`MonetColorScheme.extractSeedColorFromImage(bitmap)` is a public helper that
runs `QuantizerCelebi` + `Score.score` to pick a seed color from an arbitrary
bitmap (used by the cover→theme experiments).

### Availability & default

`DeviceUtil.isDynamicColorAvailable` (in `:app`) gates the option's
visibility. `UiPreferences.appTheme()` defaults to `AppTheme.MONET` when
dynamic color is available, otherwise `AppTheme.DEFAULT`:

```kotlin
fun appTheme() = preferenceStore.getEnum(
    "pref_app_theme",
    if (DeviceUtil.isDynamicColorAvailable) AppTheme.MONET else AppTheme.DEFAULT,
)
```

The picker (`AppThemesList` inside `AppThemePreferenceWidget.kt`) hides
`MONET` when `!DeviceUtil.isDynamicColorAvailable`.

## Typography

The project uses the **default M3 `Typography`** (no custom font family, no
custom text styles) plus one extension shipped from `:presentation-core`:

```kotlin
// ../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/theme/Typography.kt
val Typography.header: TextStyle
    @Composable get() = bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
```

`Typography.header` is the canonical section-header style across screens and
`SectionCard`s. Beyond it, screens use `MaterialTheme.typography.*` directly
(`titleSmall` / `titleMedium`, `bodyMedium` / `bodySmall`, `labelLarge`,
`labelMedium`, …). Notably, the global `setComposeContent` helper
additionally injects `LocalTextStyle provides MaterialTheme.typography.bodySmall`
so any `Text` without an explicit style defaults to `bodySmall`.

## Shapes

No custom `Shapes` object is defined. The project uses the M3 default
`androidx.compose.material3.Shapes`. Per-component corner radii are baked
into the Material wrappers in `:presentation-core/components/material/`
(`Button`, `Surface`, `Scaffold`, `FloatingActionButton`, `Tabs`, …). The
reusable primitives pick radii from `MaterialTheme.shapes.small` /
`.medium` / `.large` / `.extraLarge` / `.extraSmall`. The
`:presentation-widget` module ships its own `appwidget_*_radius` dimens
(see [`02-modules/presentation-widget.md`](../02-modules/presentation-widget.md)).

The two theme-token extensions (`ColorScheme.active`, `Typography.header`)
are the only "design tokens" the project adds on top of M3 — there is no
spacing token system; `MaterialTheme.padding` (a `Padding` object exposed
from `Constants.kt` in `:presentation-core`) is the closest thing.

## Theme preference storage

Theme preferences live in `UiPreferences`
(`../ANIYOMI/app/src/main/java/eu/kanade/domain/ui/UiPreferences.kt`),
which wraps `tachiyomi.core.common.preference.PreferenceStore` (SharedPreferences
under the hood — see [`01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md)):

| Preference key | Getter | Default | Values |
|---|---|---|---|
| `pref_theme_mode_key` | `UiPreferences.themeMode()` | `ThemeMode.SYSTEM` | `LIGHT` / `DARK` / `SYSTEM` |
| `pref_app_theme` | `UiPreferences.appTheme()` | `MONET` (if dynamic color available) else `DEFAULT` | `AppTheme` enum |
| `pref_theme_dark_amoled_key` | `UiPreferences.themeDarkAmoled()` | `false` | `Boolean` |

The light/dark decision is applied at two layers:

1. **AppCompat / View layer**: `ThemeMode.setAppCompatDelegateThemeMode(...)`
   is called whenever the user changes `themeMode` (see
   `SettingsAppearanceScreen.getThemeGroup`). This drives the legacy
   `AppCompat` night-mode qualifier (which the few XML layouts and the
   `colors.xml` AMOLED overrides still react to).
2. **Compose layer**: `TachiyomiTheme` calls `isSystemInDarkTheme()` and
   uses it (along with `themeDarkAmoled`) to pick `lightScheme` / `darkScheme`
   / the AMOLED override.

These two must stay in sync — when the user picks `ThemeMode.SYSTEM`, both
the AppCompat delegate and `isSystemInDarkTheme()` follow the system. When
they pick `LIGHT` or `DARK`, the AppCompat delegate is forced and
`isSystemInDarkTheme()` is *not* overridden — but the `themeMode` value is
read at recomposition time, so toggling it triggers an `ActivityCompat.recreate`
(see the `onValueChanged` in `SettingsAppearanceScreen`).

### The theme picker UI

`SettingsAppearanceScreen`
(`../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAppearanceScreen.kt`)
is the Compose settings screen that owns the theme group. It renders:

- `AppThemeModePreferenceWidget` — the Light / Dark / System segmented
  control. Writes `themeMode` and immediately calls
  `setAppCompatDelegateThemeMode`.
- `AppThemePreferenceWidget`
  (`../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/widget/AppThemePreferenceWidget.kt`)
  — a `LazyRow` of `AppThemePreviewItem` cards. Each preview is itself
  wrapped in `TachiyomiTheme(appTheme = appTheme, amoled = amoled)` so the
  user sees the *actual* `ColorScheme` they'll get. Selecting a theme
  `set`s the preference and calls `ActivityCompat.recreate(activity)` so the
  new theme is applied app-wide. The picker filters out deprecated entries
  (`titleRes == null`) and hides `MONET` when dynamic color isn't available.
- A switch for `pref_dark_theme_pure_black` (AMOLED), disabled when
  `themeMode == LIGHT`.

The "Display" group on the same screen also covers `tabletUiMode`,
`startScreen`, `navStyle`, `dateFormat`, and `relativeTime` — these are
UI-shape preferences rather than color, but they live in the same
`UiPreferences` bag.

## Key files table

| File | Why it matters |
|---|---|
| `../ANIYOMI/app/src/main/java/eu/kanade/presentation/theme/TachiyomiTheme.kt` | The `MaterialTheme` wrapper; the `colorSchemes` map; `playerRippleConfiguration`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/presentation/theme/colorscheme/BaseColorScheme.kt` | Abstract base; `getColorScheme(isDark, isAmoled)` — the single AMOLED decision point. |
| `../ANIYOMI/app/src/main/java/eu/kanade/presentation/theme/colorscheme/TachiyomiColorScheme.kt` | Reference implementation; documents which M3 tokens mean what (badges, nav bar, …). |
| `../ANIYOMI/app/src/main/java/eu/kanade/presentation/theme/colorscheme/MonetColorScheme.kt` | Dynamic-color implementation (system ≥ S, wallpaper-seeded compat ≥ O_MR1, fallback). |
| `../ANIYOMI/app/src/main/java/eu/kanade/domain/ui/model/AppTheme.kt` | The `AppTheme` enum (19 active + 3 deprecated entries). |
| `../ANIYOMI/app/src/main/java/eu/kanade/domain/ui/UiPreferences.kt` | `themeMode()` / `appTheme()` / `themeDarkAmoled()` preference getters. |
| `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAppearanceScreen.kt` | The Compose settings screen that owns the theme & display preference groups. |
| `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/widget/AppThemePreferenceWidget.kt` | The `LazyRow` of theme-preview cards (each preview wraps itself in `TachiyomiTheme`). |
| `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/widget/AppThemeModePreferenceWidget.kt` | The Light / Dark / System segmented control. |
| `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/theme/Color.kt` | `ColorScheme.active` extension (the "active" amber/yellow accent). |
| `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/theme/Typography.kt` | `Typography.header` extension. |
| `../ANIYOMI/presentation-core/src/main/res/values/colors.xml` | Base colors + AMOLED overrides + Material constants. |
| `../ANIYOMI/presentation-core/src/main/res/values/colors_tachiyomi.xml` | Default theme M3 palette (light). |
| `../ANIYOMI/presentation-core/src/main/res/values-night/colors_tachiyomi.xml` | Default theme M3 palette (dark). |

## See also

- [`02-modules/presentation-core.md`](../02-modules/presentation-core.md) — the design-system library that ships the color XMLs and the Material wrappers.
- [`02-modules/app.md`](../02-modules/app.md) — the `:app` module that hosts `TachiyomiTheme` and the `*ColorScheme` objects.
- [`01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md) — the `PreferenceStore` that backs `UiPreferences`.
- [`components.md`](components.md) — the reusable Compose components that consume `MaterialTheme.colorScheme`.
- [`screens.md`](screens.md) — every screen, all of which are themed by `TachiyomiTheme` via `setComposeContent`.
- [`compose-migration.md`](compose-migration.md) — how the legacy Activities theme their Compose overlays.
