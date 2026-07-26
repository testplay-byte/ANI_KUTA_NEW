# 06-ui / Views → Compose migration status

> Aniyomi is mid-migration. Almost every screen is Compose + Voyager; the
> manga **Reader** and the anime **Player** are the two View-based holdouts
> because they host non-Compose `View`s (the Subsampling Scale ImageView
> pagers and the MPV `AniyomiMPVView`). Both embed Compose *on top* of those
> views for their menus, dialogs, and on-screen controls. This doc maps the
> migration state and lists what's left.

## What's already Compose + Voyager

Everything reachable from `HomeScreen`'s `TabNavigator` is Compose + Voyager.
That includes:

- **Library** — `MangaLibraryTab`, `AnimeLibraryTab` + their `ScreenModel`s,
  the per-category `HorizontalPager`, the list/comfortable-grid/compact-grid
  variants, the settings sheet.
- **Updates** — `UpdatesTab` + the `animeUpdatesTab` / `mangaUpdatesTab`
  sub-tabs.
- **History** — `HistoriesTab` + the anime/manga history sub-tabs.
- **Browse** — `BrowseTab` and its six sub-tabs (anime sources, manga
  sources, anime extensions, manga extensions, migrate-anime-source,
  migrate-manga-source); every pushed screen (`BrowseAnimeSourceScreen`,
  `BrowseMangaSourceScreen`, `GlobalAnimeSearchScreen`,
  `GlobalMangaSearchScreen`, `AnimeExtensionDetailsScreen`,
  `MangaExtensionDetailsScreen`, `AnimeSourcePreferencesScreen`,
  `MangaSourcePreferencesScreen`, `MigrateAnimeScreen`, `MigrateMangaScreen`,
  `MigrateAnimeSearchScreen`, `MigrateMangaSearchScreen`,
  `AnimeSourceSearchScreen`, `MangaSourceSearchScreen`,
  `MigrateSeasonSelectScreen`, `AnimeSourcesFilterScreen`,
  `MangaSourcesFilterScreen`, `AnimeExtensionFilterScreen`,
  `MangaExtensionFilterScreen`).
- **More** — `MoreTab`; pushed screens `OnboardingScreen`,
  `NewUpdateScreen`, `DownloadsTab`, `CategoriesTab`, `StatsTab`,
  `StorageTab`, `SettingsScreen` (and all of its sub-screens — Appearance,
  Library, Reader, Downloads, Tracking, Browse, Data & storage, Security,
  Advanced, Search, plus the data / browse / debug / about / OSS-license
  leaves), `PlayerSettingsScreen` (and all of its sub-screens — Main,
  Player, Audio, Subtitle, Decoder, Gestures, Advanced, Torrent, Custom
  button, Editor, Code editor).
- **Entry detail** — `MangaScreen`, `AnimeScreen` and their `ScreenModel`s;
  the track-info sheets (`MangaTrackInfoDialogHomeScreen`,
  `AnimeTrackInfoDialogHomeScreen`); the migrate dialogs.
- **Deep link UI** — `DeepLinkAnimeScreen`, `DeepLinkMangaScreen`.
- **WebView (in-app)** — `WebViewScreen` (the Voyager `Screen` variant —
  embeds a `WebViewScreenContent` Compose wrapper around an Android
  `WebView`).

State for all of the above lives in Voyager `ScreenModel`s (typically
`StateScreenModel<T>`). See [`01-architecture/03-state-and-async.md`](../01-architecture/03-state-and-async.md)
and the screen catalog in [`screens.md`](screens.md).

## The two View-based holdouts

### `ReaderActivity` — manga reader

`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`
is a `BaseActivity` (AppCompat, **not** Voyager). It uses ViewBinding:

```kotlin
lateinit var binding: ReaderActivityBinding
…
setContentView(binding.root)
```

The `binding.viewerContainer` is a `FrameLayout` that hosts the active
`Viewer`'s Android `View`:

| `Viewer` impl | Backing View | File |
|---|---|---|
| `PagerViewer` (left-to-right, right-to-left, vertical) | `androidx.viewpager.widget.ViewPager` + custom `PagerViewerAdapter`; per-page `PagerPageHolder` wraps a `SubsamplingScaleImageView` (`ReaderPageImageView`) | `reader/viewer/pager/*.kt` |
| `WebtoonViewer` (continuous vertical scroll) | `androidx.recyclerview.widget.RecyclerView` (`WebtoonRecyclerView`) + `WebtoonLayoutManager` + per-page `WebtoonPageHolder` wrapping `WebtoonSubsamplingImageView` | `reader/viewer/webtoon/*.kt` |

The Viewer hierarchy also includes `PagerTransitionHolder` /
`WebtoonTransitionHolder` (the "chapter X done → chapter Y next" cards),
`ReaderTransitionView`, `ReaderPageImageView`, `ReaderProgressIndicator`,
`ReaderButton`, `GestureDetectorWithLongTap`, the `ViewerNavigation`
strategy family (`navigation/{Edge,L,RightAndLeft,Kindlish,Disabled}Navigation.kt`),
`ViewerConfig`, `PagerConfig`, `WebtoonConfig`, and the `Pager` /
`PagerViewers` enums. **All View-based.**

Why not Compose? The Subsampling Scale ImageView (`subsampling-scale-image-view`
library) is the long-standing workhorse for tiling huge images with
sub-sampling, memory pressure management, and fast pan/zoom. Compose's
`AsyncImage` (Coil 3) does not yet match its tiling behaviour for very large
images. Replacing it would mean re-implementing sub-sampling tiling in
Compose — out of scope for the reference snapshot. See
[`03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md).

### `PlayerActivity` — anime player

`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt`
is a `BaseActivity` (AppCompat, **not** Voyager). It uses ViewBinding:

```kotlin
val player by lazy { binding.player }
…
setContentView(binding.root)
```

`binding.player` is an `AniyomiMPVView`
(`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt`)
— a `FrameLayout` that wraps the native `libmpv` render surface and exposes
the mpv command API (`loadfile`, `set_property`, observers via
`PlayerObserver.kt`, etc.). The MPV library ships as `aniyomi-mpv-lib`
(see `00-overview/02-tech-stack.md`).

Why not Compose? libmpv's Android surface is a `SurfaceView` / `TextureView`
that has to be managed by a `View` hierarchy with specific lifecycle hooks
(`onAttachedToWindow`, `onDetachedFromWindow`, surface-available callbacks).
There is no Compose-native MPV binding. See
[`03-subsystems/anime-player.md`](../03-subsystems/anime-player.md).

## Compose interop in the reader / player

Both Activities overlay Compose *on top* of their View-based viewer. The
bridge is the `setComposeContent` helper.

### The `setComposeContent` helper

`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/util/view/ViewExtensions.kt`
ships two helpers:

```kotlin
// Activity-level: wraps ComponentActivity.setContent with the project theme
inline fun ComponentActivity.setComposeContent(
    parent: CompositionContext? = null,
    crossinline content: @Composable () -> Unit,
) {
    setContent(parent) {
        TachiyomiTheme {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodySmall,
                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            ) { content() }
        }
    }
}

// View-level: for ComposeView inside a ViewBinding layout
fun ComposeView.setComposeContent(content: @Composable () -> Unit) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        TachiyomiTheme {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodySmall,
                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            ) { content() }
        }
    }
}
```

Both wrap the supplied content in `TachiyomiTheme` (so the overlay matches
the user's chosen theme + AMOLED) and inject a default `bodySmall` text
style + `onBackground` content color. The Compose-view variant uses
`DisposeOnViewTreeLifecycleDestroyed` so the Compose tree follows the host
Activity's lifecycle.

The activity-level helper is used by every Compose-hosting Activity that
isn't (yet) Voyager-based — `MainActivity`, `CrashActivity`,
`WebViewActivity` — and by the reader's `binding.dialogRoot` /
`binding.pageNumber` `ComposeView`s. The player's `binding.controls` is a
`ComposeView` that calls `.setContent { TachiyomiTheme { PlayerControls(...) } }`
directly (functionally equivalent to the helper, just inlined).

### Reader Compose overlay

In `ReaderActivity.initializeMenu()`:

```kotlin
binding.pageNumber.setComposeContent {
    val state by viewModel.state.collectAsState()
    val showPageNumber by viewModel.readerPreferences.showPageNumber().collectAsState()
    if (!state.menuVisible && showPageNumber) {
        PageIndicatorText(currentPage = state.currentPage, totalPages = state.totalPages)
    }
}

binding.dialogRoot.setComposeContent {
    val state by viewModel.state.collectAsState()
    val settingsScreenModel = remember { ReaderSettingsScreenModel(…) }
    ReaderContentOverlay(brightness = state.brightnessOverlayValue, color = …, …)
    ReaderAppBars(…)              // top + bottom app bars
    if (state.showMenus) { … }
    if (state.dialog == ReaderDialog.Settings) ReaderSettingsDialog(…)
    if (state.dialog == ReaderDialog.ReadingModeSelect) ReadingModeSelectDialog(…)
    if (state.dialog == ReaderDialog.Orientation) OrientationSelectDialog(…)
    if (state.dialog == ReaderDialog.PageActions) ReaderPageActionsDialog(…)
    DisplayRefreshHost(…)
}
```

So the page-pager `View` lives in `binding.viewerContainer`, while *every*
menu, dialog, indicator, and color overlay is Compose, hosted by
`binding.dialogRoot` (a `ComposeView`) and `binding.pageNumber` (a
`ComposeView`). The Compose tree reads `viewModel.state` (a
`StateFlow<ReaderState>`) and `readerPreferences.*` (a `Preference<T>`)
directly via `collectAsState()`.

### Player Compose overlay

In `PlayerActivity.onCreate()`:

```kotlin
binding.controls.setContent {
    TachiyomiTheme {
        PlayerControls(
            viewModel = viewModel,
            onBackPress = { … finish() / enterPiP() … },
            modifier = Modifier.onGloballyPositioned { pipRect = … },
        )
    }
}
```

`binding.controls` is a `ComposeView` overlaid on the `AniyomiMPVView`.
`PlayerControls` (under `ui/player/controls/`) arranges the four corner
control clusters, the middle seek/scrub cluster, the bottom `SeekBar`, the
sheets (`ChaptersSheet`, `PlaybackSpeedSheet`, `QualitySheet`,
`AudioTracksSheet`, `SubtitleTracksSheet`, `ScreenshotSheet`, `MoreSheet`),
the dialogs (`EpisodeListDialog`, `IntegerPickerDialog`, `PlayerDialog`),
the gesture handler, the panels (`SubtitleSettingsPanel`,
`SubtitleDelayPanel`, `AudioDelayPanel`, `VideoFiltersPanel`), and the
brightness/double-tap overlays. All Compose, all reading
`viewModel.state` / `PlayerPreferences`.

### Other Compose-hosting Activities

| Activity | Compose content | Notes |
|---|---|---|
| `MainActivity` | `setComposeContent { Navigator(HomeScreen) { … } }` | The Voyager host. |
| `CrashActivity` | `setComposeContent { CrashScreen(exception, onRestartClick = …) }` | No Voyager. |
| `WebViewActivity` | `setComposeContent { WebViewScreenContent(…) }` | No Voyager. The in-app webview (`WebViewScreen`) is the Voyager equivalent. |

`UnlockActivity` is the only Compose-free Activity — it just fires a
`BiometricPrompt` and finishes.

## What remains to migrate

| Area | Status | Why not yet |
|---|---|---|
| `ReaderActivity` shell | View-based (ViewBinding + `Viewer.getView()`) | Wraps the Subsampling Scale ImageView pagers. |
| `PagerViewer` (`Pager`, `PagerPageHolder`, `PagerTransitionHolder`, `PagerViewerAdapter`, `PagerConfig`) | View-based (`ViewPager` + `SubsamplingScaleImageView`) | The paged-manga reading engine. Would need a Compose sub-sampling-tiled image viewer. |
| `WebtoonViewer` (`WebtoonRecyclerView`, `WebtoonLayoutManager`, `WebtoonPageHolder`, `WebtoonSubsamplingImageView`, `WebtoonTransitionHolder`, `WebtoonAdapter`, `WebtoonFrame`, `WebtoonBaseHolder`, `WebtoonConfig`) | View-based (`RecyclerView` + `SubsamplingScaleImageView`) | The continuous-scroll reading engine. |
| `ReaderNavigationOverlayView` | View-based | The legacy tap-zone overlay (largely superseded by the Compose `ViewerNavigation` strategy). |
| `PlayerActivity` shell | View-based (ViewBinding + `AniyomiMPVView`) | Wraps the MPV surface. |
| `AniyomiMPVView` | View-based (`FrameLayout` + libmpv JNI) | The MPV render surface; can't be a Composable until there's a Compose-native MPV binding. |
| `MangaDownloadHolder` / `AnimeDownloadHolder` / `MangaDownloadHeaderHolder` / `AnimeDownloadHeaderHolder` / `MangaDownloadAdapter` / `AnimeDownloadAdapter` | View-based (`RecyclerView` adapters + view holders) | The download queue rows are still `RecyclerView`, not `LazyColumn`. (`DownloadsTab` itself is Compose; the inner rows aren't.) |

Everything else (every Voyager screen, every `ScreenModel`, every settings
screen, the entire `More` tab subtree, every dialog/sheet/overlay except
the reader viewer views and the MPV view) is already Compose.

The Compose menu overlays on the reader/player are *not* a transitional
measure — they're the long-term plan. The reader/player will stay
Activity-based until the underlying View (Subsampling Scale ImageView /
MPV) has a Compose-native replacement, at which point only the viewer view
needs to swap.

## Implications for the ANIKUTA port

The reference snapshot shows that a Compose-first Aniyomi-class app is
viable for ~95 % of the UI — every screen, every settings surface, every
dialog, every on-screen control except the two viewers. The two remaining
View systems exist for hard technical reasons (sub-sampling image tiling;
libmpv surface lifecycle), not for preference.

For ANIKUTA, the open question (record this as a decision in
`DOCS/04-design-decisions.md` when that file exists) is whether to:

1. **Stay View-based for the reader/player**, porting the Subsampling
   Scale ImageView + `AniyomiMPVView` pattern verbatim. Lowest risk; keeps
   the proven engines. ANIKUTA's reader/player would mirror Aniyomi's
   architecture: a Voyager `Screen`-hosting `MainActivity` plus two
   `AppCompatActivity`s for reading/watching, with Compose overlays via
   `setComposeContent`.
2. **Go Compose-only**, picking or building a Compose sub-sampling image
   viewer (e.g. a future Compose-native tile renderer, or a `TextureView`
   + `AsyncImage` hybrid) and a Compose-native MPV binding (or an
   alternative player engine like ExoPlayer / Media3, which has
   first-class Compose support via `androidx.media3:media3-ui-compose`).
   Higher upfront cost; eliminates the dual-Activity split, the ViewBinding
   shims, and the `setComposeContent` interop; gives a single Compose
   codebase.
3. **Hybrid**: Compose-only for the manga reader (if a Compose
   sub-sampling viewer matures) but keep the MPV `View` for the anime
   player (libmpv is the only realistic option for the codec/feature
   matrix Aniyomi supports).

Whichever path is chosen, the rest of the app — every Voyager `Screen`,
every `ScreenModel`, every theme, every component in
[`components.md`](components.md) — ports directly. The migration state
documented here is the boundary.

## See also

- [`03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) — the reader's Viewer hierarchy.
- [`03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) — the MPV view + observer wiring.
- [`01-architecture/04-navigation.md`](../01-architecture/04-navigation.md) — why the reader/player are Activities, not Voyager `Screen`s.
- [`screens.md`](screens.md) — the full screen catalog (legacy Activities called out per group).
- [`components.md`](components.md) — the reader/player Compose overlay components.
- [`02-modules/app.md`](../02-modules/app.md) — ViewBinding layouts (`ReaderActivityBinding`, `PlayerLayoutBinding`).
