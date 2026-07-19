# UI Architecture & Theming

The Compose-based UI layer for aniyomi: a single `MainActivity` hosts a Voyager navigator full of `Screen` objects, each backed by a `ScreenModel` (the VM), while `TachiyomiTheme` applies one of ~18 Material 3 color schemes with light/dark/AMOLED variants.

---

## Where it lives

All paths relative to `REFERENCE/`.

| Concern | Path |
|---------|------|
| Theme entry point + color-scheme registry | `app/src/main/java/eu/kanade/presentation/theme/TachiyomiTheme.kt` |
| Color-scheme implementations (one file per theme) | `app/src/main/java/eu/kanade/presentation/theme/colorscheme/*.kt` |
| Design-system tokens (window-size breakpoints only) | `app/src/main/java/mihon/core/designsystem/utils/WindowSize.kt` |
| Shared Compose components, Material wrappers, screens | `presentation-core/src/main/java/tachiyomi/presentation/core/` |
| Tiny mihon presentation helper | `presentation-core/src/main/java/mihon/presentation/core/util/PagingDataUtil.kt` |
| App-level Compose components (AppBar, EmptyScreen, Banners, etc.) | `app/src/main/java/eu/kanade/presentation/components/` |
| App-level feature screens (library, entries, player, reader, settings...) | `app/src/main/java/eu/kanade/presentation/<feature>/` |
| Base activity + activity delegates (theming, secure-screen) | `app/src/main/java/eu/kanade/tachiyomi/ui/base/` |
| Screen hosts (Voyager `Screen` impls + `ScreenModel`s) | `app/src/main/java/eu/kanade/tachiyomi/ui/<feature>/` |
| AppCompat XML theme styles (one per `AppTheme`) | `app/src/main/res/values/themes.xml` (referenced by `R.style.Theme_Tachiyomi_*`) |
| Per-theme XML color overrides (day + night) | `presentation-core/src/main/res/values/colors_*.xml` and `values-night/colors_*.xml` |
| App entry that wires Coil `ImageLoader` + Injekt modules | `app/src/main/java/eu/kanade/tachiyomi/App.kt` |

Note: the task spec referenced `mihon/core/designsystem/` as a token source. In this snapshot that package contains **only** `WindowSize.kt` (two breakpoints); there is no separate design-token module. The real color/typography tokens live inside `eu/kanade/presentation/theme/` (color) and `tachiyomi/presentation/core/theme/` (typography helper, active-color extension).

---

## What it does

The UI layer is organized as a **two-package split per feature**:

- `eu/kanade/tachiyomi/ui/<feature>/` — the **controller layer**: Voyager `Screen` subclasses (navigation entry + `@Composable Content()` wiring) and `ScreenModel` subclasses (the ViewModel-equivalent: holds `StateFlow`, calls interactors, exposes state).
- `eu/kanade/presentation/<feature>/` — the **pure-Compose view layer**: stateless `@Composable` screen content, list items, dialogs, toolbars. No Injekt, no lifecycle — only the data passed in.

This `ui/` + `presentation/` pairing is the project's UI/logic separation rule applied at package granularity: `presentation/` composables are dumb, `ui/` screen models are smart.

Theming is centralized in `TachiyomiTheme` (a `@Composable` wrapper around Material 3 `MaterialTheme`) plus an `AppCompat` XML theme applied at the Activity level via `ThemingDelegate`. The same theme is used for both anime and manga screens — there is no anime-specific or manga-specific theme.

---

## Key files & classes

### Theme (`eu/kanade.presentation.theme`)

| File | Role |
|------|------|
| `TachiyomiTheme.kt` | Public `@Composable TachiyomiTheme(appTheme?, amoled?, content)`. Reads `UiPreferences` via `Injekt.get<UiPreferences>()` for `appTheme()` and `themeDarkAmoled()`, picks a `BaseColorScheme`, calls `MaterialTheme(colorScheme = ...)`. Also exposes `TachiyomiPreviewTheme` (defaults for `@Preview`) and `playerRippleConfiguration` (low-alpha ripple for player overlays). |
| `colorscheme/BaseColorScheme.kt` | Abstract base. Holds `darkScheme` + `lightScheme` (`ColorScheme`). `getColorScheme(isDark, isAmoled)` returns: light scheme if not dark; dark scheme if dark+not-amoled; **AMOLED = dark scheme with `background`/`surface` forced to `Color.Black`** and surface containers to near-black (`0xFF0C0C0C`/`0x131313`/`0x1B1B1B`) so content scrolling behind the nav bar is still visible. |
| `colorscheme/TachiyomiColorScheme.kt` | The default blue theme. Inline comments document semantic roles (e.g. `secondary` = unread badge, `tertiary` = downloaded badge, `secondaryContainer` = nav-bar selector pill). |
| `colorscheme/MonetColorScheme.kt` | Dynamic-color theme. On Android 12+ uses `dynamicDark/LightColorScheme(context)`; on 8.1–11 extracts a seed from the system wallpaper and generates a full M3 `ColorScheme` via `SchemeContent`/`MaterialDynamicColors`; below 8.1 falls back to `TachiyomiColorScheme`. |
| `colorscheme/{Cloudflare,Cottoncandy,Doom,GreenApple,Lavender,Matrix,MidnightDusk,Mocha,Monochrome,Nord,Sapphire,Strawberry,Tako,TealTurqoise,TidalWave,YinYang,Yotsuba}ColorScheme.kt` | One file per extra theme; each is an `internal object : BaseColorScheme()` with hardcoded `darkColorScheme(...)` + `lightColorScheme(...)`. 17 extras + default + Monet = 19 total in the `colorSchemes` map. |
| `UiPreferences` (`app/.../domain/ui/UiPreferences.kt`) | Preference façade. Keys: `pref_theme_mode_key` (enum `ThemeMode` — `LIGHT`/`DARK`/`SYSTEM`), `pref_theme_dark_amoled_key` (bool), `pref_theme_key` (enum `AppTheme`). |
| `AppTheme` (`app/.../domain/ui/model/AppTheme.kt`) | Enum of all 19 themes (+ 3 deprecated values `DARK_BLUE`/`HOT_PINK`/`BLUE` kept for migration). |

### Design tokens (`mihon.core.designsystem`)

| File | Role |
|------|------|
| `mihon/core/designsystem/utils/WindowSize.kt` | `isMediumWidthWindow()` (>600dp) and `isExpandedWidthWindow()` (>840dp) composables + `MediumWidthWindowSize`/`ExpandedWidthWindowSize` dp constants. Used to switch between phone and tablet/TwoPane layouts. |

### Shared Compose components (`tachiyomi.presentation.core`)

Located in `presentation-core/src/main/java/tachiyomi/presentation/core/`. This is the **reusable component library** shared with the widget module and any future feature module.

- `theme/Typography.kt` — `val Typography.header` extension (a `bodyMedium`-derived `TextStyle` for section headers).
- `theme/Color.kt` — `val ColorScheme.active` extension (yellow/amber "active" highlight).
- `components/material/Scaffold.kt` — a forked Material 3 `Scaffold` with Tachiyomi-specific patches: top-bar scroll behavior passed by default, no height constraint on expanded app bar, FAB height factored into inner padding, an added `startBar` slot for `NavigationRail`, and consumed-insets handling. This is the scaffold used app-wide.
- `components/material/{Button,IconButtonTokens,NavigationBar,NavigationRail,Tabs,FloatingActionButton,Slider,IconToggleButton,Surface,AlertDialog,PullRefresh,Constants}.kt` — thin Material 3 wrappers / token overrides.
- `components/{ActionButton,ListGroupHeader,SectionCard,CircularProgressIndicator,Badges,LazyColumnWithAction,Pill,VerticalFastScroller,LinkIcon,WheelPicker,CollapsibleBox,LabeledCheckbox,AdaptiveSheet,TwoPanelBox,LazyList,LazyGrid,SettingsItems}.kt` — generic building blocks.
- `screens/{LoadingScreen,EmptyScreen,InfoScreen}.kt` — three standard full-screen states.
- `icons/{CustomIcons,Discord,Github}.kt` — vector icons not in `material-icons-extended`.
- `i18n/Localize.kt` — `stringResource` bridge to moko-resources (`MR.strings.*`).
- `util/{Preference,Elevation,Scrollbar,PaddingValues,Modifier,LazyListState,PagingDataUtil(under mihon.*),ThemePreviews}.kt` — helpers. `ThemePreviews` is an annotation that emits both a Light and Dark `@Preview` (used pervasively for component previews).
- `util/WindowSize.kt` — TODO check: there's also `eu/kanade/presentation/util/WindowSize.kt` for `isTabletUi()`; the design-system one is the lower-level breakpoint.

### Base activity (`eu/kanade.tachiyomi.ui.base`)

| File | Role |
|------|------|
| `activity/BaseActivity.kt` | `open class BaseActivity : AppCompatActivity`. Delegates to `SecureActivityDelegate` + `ThemingDelegate` (Kotlin interface delegation). In `attachBaseContext` calls `prepareTabletUiContext()` to inject a tablet UI context on wide screens. In `onCreate` calls `applyAppTheme(this)` *before* `super.onCreate`. **No base fragment** exists — the project is fully Activity + Compose; the old fragment path was removed. |
| `delegate/ThemingDelegate.kt` | `interface ThemingDelegate` + `ThemingDelegateImpl`. `applyAppTheme(activity)` reads `UiPreferences` and calls `activity.setTheme(...)` for the picked `AppTheme`'s `R.style.Theme_Tachiyomi_*` res id; if AMOLED is on, additionally overlays `R.style.ThemeOverlay_Tachiyomi_Amoled`. This applies the **AppCompat XML** theme (status bar, window background, splash) — the Compose `MaterialTheme` is applied separately inside `setComposeContent`. |
| `delegate/SecureActivityDelegate.kt` | App-lock + secure-screen (FLAG_SECURE) logic driven by `SecurityPreferences` + `BasePreferences.incognitoMode()`. Not theming per se but lives in the same base package and mixes into every activity. |

### App-level presentation components (`eu/kanade.presentation.components`)

App-specific (not in `presentation-core`) but reused across feature screens: `AppBar.kt`, `Banners.kt` (incognito/downloaded-only/indexing banners), `EmptyScreen.kt`, `AdaptiveSheet.kt`, `TabbedDialog.kt`, `TabbedScreen.kt`, `FloatingActionAddButton.kt`, `DropdownMenu.kt`, `DateText.kt`, `ItemDownloadIndicator.kt`, `EntryDownloadDropdownMenu.kt`.

### App entry (`App.kt`)

`class App : Application(), DefaultLifecycleObserver, SingletonImageLoader.Factory`. On `onCreate` calls `patchInjekt()` then imports the four Injekt modules. Implements `newImageLoader(context)` to build the Coil `ImageLoader` (see "How it works → Image loading").

---

## How it works

### The Activity + ScreenModel + Compose pattern

A typical screen (e.g. anime detail) is built from three pieces:

1. **Activity host.** There is essentially one Compose host activity — `MainActivity : BaseActivity()`. It calls `setComposeContent { ... }` (an extension in `app/.../util/view/ViewExtensions.kt`) which wraps the content in `TachiyomiTheme { ... }` and provides default `LocalTextStyle`/`LocalContentColor`. Inside, it creates a Voyager `Navigator(screen = HomeScreen)`. The player (`PlayerActivity`) and reader (`ReaderActivity`) are *separate* `BaseActivity` subclasses that still use View data-binding for their legacy surfaces but embed Compose via `ComposeView.setComposeContent(...)` for sub-views (e.g. reader page-number overlay, dialog root). Every activity inherits `BaseActivity` and therefore gets `applyAppTheme` + secure-screen wiring for free.
2. **Voyager `Screen`.** e.g. `class AnimeScreen(animeId: Long, fromSource: Boolean = false) : Screen(), AssistContentScreen`. Its `@Composable override fun Content()` is the entry point. It calls `rememberScreenModel { AnimeScreenModel(context, lifecycle, animeId, fromSource) }` to obtain the screen model, then `screenModel.state.collectAsStateWithLifecycle()` and delegates rendering to `eu.kanade.presentation.entries.anime.AnimeScreen(state, ...)`.
3. **Voyager `ScreenModel`.** e.g. `class AnimeScreenModel(...) : StateScreenModel<AnimeScreenModel.State>(State.Loading)`. Constructor takes a handful of context args + ~25 dependencies with `= Injekt.get()` defaults — this is the constructor-injection pattern. The model exposes a `StateFlow<State>`; `screenModelScope` (a `CoroutineScope`) runs interactor calls; the `State` is an `Immutable` sealed hierarchy (`Loading | Success`. Pure composables in `eu/kanade/presentation/...` consume only the `State` + callbacks — they have no `Injekt` import.

So the rule is: `ui/<feature>/` = `Screen` + `ScreenModel` (Injekt-aware, stateful); `presentation/<feature>/` = stateless composables (no Injekt, no lifecycle). The `ScreenModel` is the VM-equivalent; Voyager owns its lifecycle tied to the `Navigator` step.

### Theme application — two layers

1. **AppCompat XML layer** (`ThemingDelegate.applyAppTheme` in `BaseActivity.onCreate`). Picks `R.style.Theme_Tachiyomi_<Name>` (one per `AppTheme`, defined in `app/src/main/res/values/themes.xml`) and optionally overlays `ThemeOverlay.Tachiyomi.Amoled`. This layer controls window background, status bar, splash, AppCompat widget defaults — anything still in the View world.
2. **Compose Material 3 layer** (`TachiyomiTheme` composable, applied by `setComposeContent` at the root of every Compose tree). Resolves the `AppTheme` to a `BaseColorScheme`, then `BaseColorScheme.getColorScheme(isSystemInDarkTheme(), isAmoled)` returns the final M3 `ColorScheme` and hands it to `MaterialTheme(colorScheme = ..., content = ...)`.

Both layers read the same `UiPreferences.appTheme()` + `UiPreferences.themeDarkAmoled()`, so they stay in sync.

### Light / Dark / AMOLED variants

- **Light vs dark** is driven by `UiPreferences.themeMode()` (enum `ThemeMode` of `LIGHT`/`DARK`/`SYSTEM`). `SYSTEM` defers to `isSystemInDarkTheme()` inside the composable. `App.kt` also calls `setAppCompatDelegateThemeMode(themeMode)` so AppCompat's day/night follows the same pref. Each `BaseColorScheme` subclass ships both `lightScheme` and `darkScheme`.
- **AMOLED** is a boolean pref (`pref_theme_dark_amoled_key`). It only applies in dark mode. When on, `BaseColorScheme.getColorScheme` returns a *copy* of the dark scheme with `background`/`surface` set to `Color.Black` and `surfaceContainer*` set to three near-black grays (`0x0C0C0C`, `0x131313`, `0x1B1B1B`) so that scrollable content behind the navigation bar isn't pure black (M3 guideline note in source).
- **Monet (dynamic color)** is a special `AppTheme.MONET` value: `MonetColorScheme(context)` is constructed with the current `Context` (not pre-registered in the static map) and uses Android 12+ `dynamicDarkColorScheme`/`dynamicLightColorScheme`, falling back to wallpaper-seed extraction on 8.1–11 and to `TachiyomiColorScheme` below 8.1.

### Color / token system

There is no separate "design token" file. Tokens are:

- **Compose color tokens** = the M3 `ColorScheme` slots (`primary`, `secondary`, `tertiary`, `surface`, `surfaceContainer*`, etc.) filled per-theme in each `*ColorScheme.kt`. Inline comments in `TachiyomiColorScheme.kt` document the semantic mapping (e.g. `secondary` = unread badge, `tertiary` = downloaded badge, `surfaceVariant`/`surfaceContainer` = nav-bar background).
- **AppCompat XML color tokens** = per-theme `colors_*.xml` files in `presentation-core/src/main/res/values/` (+ `values-night/` overrides) referenced by the `themes.xml` styles.
- **Typography** = mostly stock M3 `Typography`; the only custom extension is `Typography.header` (`presentation-core/.../theme/Typography.kt`).
- **Shape / elevation** = stock M3; small tweaks in `presentation-core/.../util/Elevation.kt` and `components/material/Constants.kt`.
- **Window-size breakpoints** = `MediumWidthWindowSize = 600.dp`, `ExpandedWidthWindowSize = 840.dp` (`mihon.core.designsystem.utils.WindowSize`). `isTabletUi()` (in `eu/kanade/presentation/util/WindowSize.kt`) builds on these to switch to two-pane layouts (e.g. library pager, settings master-detail).

### Image loading (Coil)

- `App` implements `coil3.SingletonImageLoader.Factory`. `newImageLoader(context)` builds the global `ImageLoader`:
  - `OkHttpNetworkFetcherFactory(callFactoryLazy)` — reuses `Injekt.get<NetworkHelper>().client` (OkHttp) for network fetches, so all image requests go through the same interceptor stack (User-Agent, Cloudflare, rate-limit) as source HTTP.
  - `TachiyomiImageDecoder.Factory()` — custom decoder (libimagedecoder) for GIF/animated WebP/etc.
  - `BufferedSourceFetcher.Factory()` — generic source fetcher.
  - `MangaCoverFetcher.MangaFactory` / `MangaCoverFactory` + `AnimeImageFetcher.AnimeFactory` / `AnimeCoverFactory` — custom fetchers for manga covers and anime covers/thumbnails (resolve covers from cache, local source, or HTTP).
  - `AnimeKeyer`/`MangaKeyer`/`AnimeCoverKeyer`/`MangaCoverKeyer` — memory-cache keyers.
  - `crossfade` scaled by `animatorDurationScale`; `allowRgb565` on low-RAM devices; `fetcherCoroutineContext = Dispatchers.IO.limitedParallelism(8)` and `decoderCoroutineContext = ...limitedParallelism(3)` to throttle.
- Coil BOM is `io.coil-kt.coil3:coil-bom:3.1.0` (see `gradle/libs.versions.toml`); bundle = `coil-core`, `coil-gif`, `coil-compose`, `coil-network-okhttp`.

---

## Dependencies

### This subsystem depends on

- **`:presentation-core`** (Gradle project) — provides shared Compose components, `Scaffold`, `ThemePreviews`, moko-resources `stringResource` bridge, window-size helpers. `app/build.gradle.kts` line: `implementation(projects.presentationCore)`.
- **AndroidX Compose BOM** `2025.03.00` (`gradle/compose.versions.toml`) + `compose.activity`, `compose.foundation`, `compose.material3.core`, `compose.material.icons`, `compose.animation`, `compose.animation.graphics`, `compose.ui.tooling(preview)`, `compose.ui.util`.
- **Coil 3** (`libs.coil.bom` + `libs.bundles.coil`) for image loading.
- **Voyager** `1.0.1` (`libs.bundles.voyager` = navigator + screenmodel + tab-navigator + transitions) for navigation + `ScreenModel` (the VM layer).
- **moko-resources** (`:i18n` + `:i18n-aniyomi`) for localized strings (`MR.strings.*`, `AYMR.strings.*`).
- **Material Components for Android** (`libs.material`) for the AppCompat XML themes overlaid by `ThemingDelegate`.
- **Injekt** (`libs.injekt` = `com.github.mihonapp:injekt:91edab2317`) — `TachiyomiTheme` and `ThemableDelegate` read `UiPreferences` via `Injekt.get<UiPreferences>()`.

### What depends on this subsystem

- Every `:app` screen (`ui/<feature>/` + `presentation/<feature>/`) consumes `TachiyomiTheme` (via `setComposeContent`) and the shared `presentation-core` components.
- `:presentation-widget` consumes `presentation-core` components for the Glance widget UIs.
- `MainActivity`, `PlayerActivity`, `ReaderActivity`, `CrashActivity`, `UnlockActivity` all extend `BaseActivity` and therefore inherit `ThemingDelegate` + `SecureActivityDelegate`.

---

## Anime vs manga

**Theme is 100% shared.** `TachiyomiTheme`, all `BaseColorScheme` subclasses, the XML themes, `BaseActivity`, `ThemingDelegate`, and the entire `presentation-core` component library are anime/manga-agnostic. There is no `AnimeTheme` or `MangaTheme`.

**Screens are split.** For every feature that has both kinds, there are parallel packages and parallel composables:

| Feature | Anime | Manga |
|---------|-------|-------|
| Library | `presentation/library/anime/` + `ui/library/anime/` | `presentation/library/manga/` + `ui/library/manga/` |
| Entry detail | `presentation/entries/anime/` + `ui/entries/anime/` | `presentation/entries/manga/` + `ui/entries/manga/` |
| Browse / sources | `presentation/browse/anime/` + `ui/browse/anime/` | `presentation/browse/manga/` + `ui/browse/manga/` |
| History | `presentation/history/anime/` + `ui/history/anime/` | `presentation/history/manga/` + `ui/history/manga/` |
| Updates | `presentation/updates/anime/` + `ui/updates/anime/` | `presentation/updates/manga/` + `ui/updates/manga/` |
| Track | `presentation/track/anime/` + `ui/.../track/anime/` | `presentation/track/manga/` + `ui/.../track/manga/` |
| Categories | `presentation/category/AnimeCategoryScreen.kt` + `ui/category/` | `presentation/category/MangaCategoryScreen.kt` + `ui/category/` |
| Player / Reader | `ui/player/` (PlayerActivity, mpv) | `ui/reader/` (ReaderActivity, SubsamplingScaleImageView) |
| Settings | `presentation/more/settings/screen/player/...` | `presentation/more/settings/screen/SettingsReaderScreen.kt` |

**Coupling concerns for anime-first:**

- The shared `presentation-core` library and the theme are zero-coupling — they're the cleanest layer to inherit wholesale.
- `MainActivity` hosts *both* anime and manga tabs side-by-side in `HomeScreen`. An anime-first fork will either need to prune the manga tabs/screens or hide them behind a feature flag; the `ui/` + `presentation/` split makes this a per-package deletion rather than a deep refactor.
- The dual `MangaDownloadCache`/`AnimeDownloadCache`, `MangaCoverCache`/`AnimeCoverCache`, etc. fields in `MainActivity` show the parallel-repo coupling at the host-activity level. Anime-first can drop the manga fields.
- `TachiyomiTheme` reads `UiPreferences` from Injekt at composition time — this is fine as long as `PreferenceModule` is imported; an anime-first fork that trims manga-only preferences doesn't break the theme.

---

## Relationships

- **PLAYER** — `ui/player/PlayerActivity.kt` extends `BaseActivity`, applies `TachiyomiTheme` via embedded `ComposeView.setComposeContent(...)`. Player-specific composables live in `eu/kanade/presentation/player/components/` (e.g. `PlayerSheet.kt`, `TintedSliderItem.kt`, `RepeatingIconButton.kt`, `OvalBox.kt`) and reuse `presentation-core` Material wrappers. `playerRippleConfiguration` (low-alpha ripple) is exported from `TachiyomiTheme.kt` for the player overlays. See SUBSYSTEMS/PLAYER.md (TODO if not yet written).
- **READER** — `ui/reader/ReaderActivity.kt` extends `BaseActivity`; uses View data-binding for the reader surface but embeds Compose for the page-number indicator and reader settings dialogs (`presentation/reader/`). See SUBSYSTEMS/READER.md (TODO if not yet written).
- **APP-STRUCTURE.md** — `REFERENCE-DOCS/APP-STRUCTURE.md` enumerates all 20 `ui/<feature>/` packages, the `data/` package tree, and entry points; this doc zooms into the `ui/base/` + `presentation/` + `presentation-core/` slice of that map.
- **ARCHITECTURE.md** — `REFERENCE-DOCS/ARCHITECTURE.md` covers the cross-cutting layering (domain ← data ← app), the dual anime/manga model, and the "mixed View + Compose UI" observation this doc expands on.
- **MODULES.md** — `REFERENCE-DOCS/MODULES.md` documents the `:presentation-core` and `:presentation-widget` modules and their consumers.
- **DI** — see `SUBSYSTEMS/DI.md`. `TachiyomiTheme` and `ThemableDelegate` are direct Injekt consumers (`Injekt.get<UiPreferences>()`).
- **DATA-LAYER** (TODO) — repo bindings injected into screen models feed the `State` that `presentation/` composables render.
- Every other subsystem doc in `SUBSYSTEMS/` — they're all rendered through this theme + component library.

---

## Notes for our build (anime-first)

1. **Reuse the theme wholesale.** `TachiyomiTheme` + the 19 color schemes + `BaseColorScheme` AMOLED logic + `presentation-core` components are zero anime/manga coupling. Inherit them as-is. Our project's UI/logic separation rule is already satisfied by the `ui/` (ScreenModel) + `presentation/` (stateless composable) split — keep that convention for any new anime-only screens.
2. **Prune manga packages.** After forking, the manga `presentation/<feature>/manga/` and `ui/<feature>/manga/` packages can be deleted package-by-package without touching the theme. `MainActivity` and `HomeScreen` will need their manga tabs/fields removed. The `Screen`/`ScreenModel`/composable trinity per feature makes the cuts surgical.
3. **One activity, one Compose host** is the dominant pattern (`MainActivity`). The player and reader are the *only* other activities and they still wrap Compose. We should keep this — avoid introducing new activities for new screens; use Voyager `Screen`s pushed onto the `Navigator` instead.
4. **Avoid extending the XML theme layer** unless a screen genuinely needs AppCompat widgets. New screens should be pure Compose and rely on `TachiyomiTheme`'s M3 `ColorScheme` only; the `ThemingDelegate` XML layer is legacy scaffolding for the window/splash/status-bar.
5. **Coil config is reusable** — `App.newImageLoader` already wires anime cover + thumbnail fetchers (`AnimeImageFetcher`, `AnimeCoverKeyer`). The manga fetchers can be dropped from the builder for an anime-only build.
6. **Tablet/two-pane** — `isMediumWidthWindow()`/`isExpandedWidthWindow()` and `isTabletUi()` are the breakpoints to reuse for our larger-screen layouts.

---

## TODOs / open questions

- TODO: confirm the exact contents of `app/src/main/res/values/themes.xml` and `values-night/` overlays — referenced by `R.style.Theme_Tachiyomi_*` but not read line-by-line for this doc.
- TODO: enumerate which `colors_*.xml` files in `presentation-core/src/main/res/values/` map to which `AppTheme` (the filenames — `colors_tachiyomi.xml`, `colors_nord.xml`, `colors_tako.xml`, `color_lavender.xml`, `color_sapphire.xml`, `color_matrix.xml`, `color_doom.xml`, `color_cloudflare.xml`, `colors_strawberry.xml`, `colors_greenapple.xml`, `colors_midnightdusk.xml`, `colors_tealturqoise.xml`, `colors_yinyang.xml`, `colors_yotsuba.xml`, `colors_monochrome.xml`, `colors_tidalwave.xml`, `colors_cottoncandy.xml`(?) — need a 1:1 mapping verified against the `AppTheme` enum).
- TODO: read `eu/kanade/presentation/util/WindowSize.kt` vs `mihon/core/designsystem/utils/WindowSize.kt` to document which call sites use which (one is the low-level breakpoint, the other is the `isTabletUi()` business-rule wrapper).
- TODO: confirm whether `MochaColorScheme` exists in the `colorSchemes` map — it is imported by `TachiyomiTheme.kt` (line 22) but the map on line 104 does **not** include `AppTheme.MOCHA`. Either the import is dead or the map is missing an entry; verify.
- TODO: document the `presentation-core` consumers in `:presentation-widget` (Glance widget composables reuse `EmptyScreen`/`LoadingScreen`?).
- TODO: decide (for our build) whether to keep Voyager or migrate to Jetpack Navigation Compose — recorded as an open decision, not made here.
- TODO: decide whether to keep the dual XML+Compose theme layer or collapse to Compose-only — recorded as an open decision; the XML layer is needed only as long as we keep AppCompat widgets/splash.
