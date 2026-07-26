# 06-ui / Reusable Compose components

> A contributor's "where is the X widget defined?" lookup. The notable
> reusable Compose components used across Aniyomi's screens, grouped by what
> they do, with the file each one lives in.

## Where components live

Aniyomi's Compose components are split across two locations:

1. **`:presentation-core`** — the shared design-system library. Hosts the
   Material-3 component wrappers (`Scaffold`, `NavigationBar`,
   `FloatingActionButton`, `Button`, `Slider`, `Tabs`, `Surface`,
   `AlertDialog`, `IconToggleButton`, `NavigationRail`, `PullRefresh`), the
   reusable primitives (`Pill`, `Badges`, `SectionCard`, `WheelPicker`,
   `VerticalFastScroller`, `AdaptiveSheet`, `TwoPanelBox`,
   `LazyColumnWithAction`, `LazyList`, `LazyGrid`, `ListGroupHeader`,
   `CollapsibleBox`, `LabeledCheckbox`, `LinkIcon`, `ActionButton`,
   `CircularProgressIndicator`), the standard state screens
   (`EmptyScreen`, `LoadingScreen`, `InfoScreen`), the Material tokens
   (`Constants.kt` — `Padding`, `DISABLED_ALPHA`, `SECONDARY_ALPHA`,
   `MaterialTheme.padding`), the custom icons (`Github`, `Discord`, `Magnet`,
   `CustomIcons`), the i18n bridge (`stringResource`), and the
   `Preference.collectAsState()` bridge. Path:
   `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/`.
   See [`02-modules/presentation-core.md`](../02-modules/presentation-core.md)
   for the full inventory.

2. **`:app` → `eu.kanade.presentation.*`** — feature-flavored composables
   that build on top of `:presentation-core`. Sub-packages:
   - `eu.kanade.presentation.components` — the project-wide `AppBar` /
     `SearchToolbar`, `TabbedDialog`, `TabbedScreen`, `AdaptiveSheet`
     (Voyager-aware wrapper), `Banners` (incognito / downloaded-only /
     indexing), `FloatingActionAddButton`, `DropdownMenu` (+ `NestedMenuItem`,
     `RadioMenuItem`), `DateText`, `ItemDownloadIndicator`, the
     `EntryDownloadDropdownMenu`, `AppStateBanners`.
   - `eu.kanade.presentation.entries[.manga|.anime].components` — the entry
     detail bits: `ItemCover`, `CommonEntryItem` (library list/grid shapes),
     `MangaChapterListItem` / `AnimeEpisodeListItem`, the download
     indicators, the info headers, the cover/images dialogs.
   - `eu.kanade.presentation.library[.manga|.anime]` and
     `eu.kanade.presentation.library.components` — the library pager, list,
     comfortable-grid, compact-grid, settings dialog, toolbar, badges,
     `LazyLibraryGrid`, `LibraryTabs`.
   - `eu.kanade.presentation.browse[.manga|.anime].components` — the source
     list/grid items (`BaseMangaSourceItem`, `BrowseMangaSourceList` /
     `CompactGrid` / `ComfortableGrid`, `GlobalMangaSearchToolbar`,
     `GlobalMangaSearchCardRow`, `BrowseMangaIcons`).
   - `eu.kanade.presentation.more.*` — `MoreScreen`, `LogoHeader`,
     `NewUpdateScreen`, the onboarding steps, the entire settings system
     (`Preference`, `PreferenceScreen`, `PreferenceScaffold`,
     `PreferenceItem`, the `widget/` family: `BasePreferenceWidget`,
     `SwitchPreferenceWidget`, `TextPreferenceWidget`,
     `EditTextPreferenceWidget`, `ListPreferenceWidget`,
     `MultiSelectListPreferenceWidget`, `TriStateListDialog`,
     `AppThemePreferenceWidget`, `AppThemeModePreferenceWidget`,
     `TrackingPreferenceWidget`, `InfoWidget`, `PreferenceGroupHeader`),
     `AboutScreen`, the stats & storage content composables.
   - `eu.kanade.presentation.reader[.appbars|.settings|.components]` — the
     Compose overlay used by `ReaderActivity`: `ReaderAppBars`,
     `BottomReaderBar`, `ChapterNavigator`, `PageIndicatorText`,
     `ReaderContentOverlay`, `DisplayRefreshHost`, `ReadingModeSelectDialog`,
     `OrientationSelectDialog`, `ModeSelectionDialog`, `ChapterTransition`,
     `ReaderPageActionsDialog`, the `settings/` pages (`GeneralSettingsPage`,
     `ReadingModePage`, `ColorFilterPage`, `ReaderSettingsDialog`).
   - `eu.kanade.presentation.player.components` — the Compose overlay used by
     `PlayerActivity`: `PlayerSheet`, `ExpandableCard`,
     `ExposedTextDropDownMenu`, `SliderItem`, `TintedSliderItem`,
     `OutlinedNumericChooser`, `OvalBox`, `RepeatingIconButton`,
     `SwitchPreference`.
   - `eu.kanade.presentation.track[.manga|.anime|.components]` — the tracker
     info dialog (`AnimeTrackInfoDialogHome`, `MangaTrackInfoDialogHome`),
     the tracker search (`AnimeTrackerSearch`, `MangaTrackerSearch`), the
     `TrackLogoIcon` + preview providers, `TrackInfoDialogSelector`.
   - `eu.kanade.presentation.history[.manga|.anime].components` — history row
     items + state providers.
   - `eu.kanade.presentation.updates[.manga|.anime]` — the updates row items
     + `UpdatesDialog`.
   - `eu.kanade.presentation.category.components` — `CategoryListItem`,
     `CategoryFloatingActionButton`, `CategoryDialogs`.
   - `eu.kanade.presentation.webview.WebViewScreenContent` — the in-app
     webview.
   - `eu.kanade.presentation.crash.CrashScreen` — the crash landing page.
   - `eu.kanade.presentation.util.*` — `Navigator` helpers
     (`DefaultNavigatorScreenTransition`, `ScreenTransition`,
     `LocalBackPress`, `AssistContentScreen`, `Screen` base class, the `Tab`
     interface), `isTabletUi()`, `WindowSize`, `Permissions`, `ExceptionFormatter`,
     `FastScrollAnimateItem`, `ItemNumberFormatter`, `TimeUtils`, `Resources`.

## The notable components

### Covers

| Component | File | What it does |
|---|---|---|
| `ItemCover` (enum with `Square`/`Book`/`Thumb` ratios + `@Composable operator fun invoke`) | `presentation/entries/components/ItemCover.kt` | The single shared cover composable. Uses Coil 3 `AsyncImage` with a `ColorPainter` placeholder (`CoverPlaceholderColor = 0x1F888888`) and a `cover_error` drawable fallback. Supports optional `onClick`. `ItemCover.Book` (2:3) is the standard manga/anime cover shape; `ItemCover.Square` (1:1) is used for thumbnails; `ItemCover.Thumb` (16:9) for the player's background art. |
| `EntryCompactGridItem`, `EntryComfortableGridItem`, `EntryListItem` | `presentation/library/components/CommonEntryItem.kt` | The three library card shapes. *Compact* overlays the title on the cover (gradient scrim); *Comfortable* puts the title below; *List* is a single row with cover + title + badges. All three accept `coverBadgeStart` / `coverBadgeEnd` slot composables (typically `BadgeGroup` with `Badge` for unread/downloaded/total counts) and an optional `onClickContinueViewing` FilledIconButton (the play-arrow "resume" pill). |
| `GridItemSelectable`, `selectedOutline`, `selectedBackground` | `presentation/library/components/CommonEntryItem.kt`, `presentation/core/util/Modifier.kt` | The selection-state wrapper for grid/list items (tints the row with `colorScheme.secondary` when selected). |
| `MangaCoverDialog`, `AnimeImagesDialog` | `presentation/entries/{manga,anime}/components/MangaCoverDialog.kt`, `…/AnimeImagesDialog.kt` | The full-screen cover viewer / share / edit / save dialog. Backed by `MangaCoverScreenModel` / `AnimeImageScreenModel`. |

### Library list & grid infrastructure

| Component | File | What it does |
|---|---|---|
| `MangaLibraryPager` / `AnimeLibraryPager` | `presentation/library/{manga,anime}/{Manga,Anime}LibraryPager.kt` | The category `HorizontalPager` — one page per category, swipeable. |
| `MangaLibraryContent` / `AnimeLibraryContent` | `presentation/library/{manga,anime}/{Manga,Anime}LibraryContent.kt` | Per-category body: chooses list vs comfortable-grid vs compact-grid based on `LibraryDisplayMode`; wires selection, FAB, scroll-to-top on tab reselect. |
| `MangaLibraryList` / `AnimeLibraryList`, `MangaLibraryComfortableGrid` / `AnimeLibraryComfortableGrid`, `MangaLibraryCompactGrid` / `AnimeLibraryCompactGrid` | `presentation/library/{manga,anime}/{Manga,Anime}Library{List,ComfortableGrid,CompactGrid}.kt` | The three `LazyList` / `LazyGrid` variants that render `EntryListItem` / `EntryComfortableGridItem` / `EntryCompactGridItem`. |
| `LazyLibraryGrid` | `presentation/library/components/LazyLibraryGrid.kt` | Shared `LazyVerticalGrid` scaffolding (column count, padding, scroll behaviour) reused by the comfortable + compact grid variants. |
| `LibraryToolbar` | `presentation/library/components/LibraryToolbar.kt` | The library `AppBar` with search-as-you-type, filter button (opens `MangaLibrarySettingsDialog` / `AnimeLibrarySettingsDialog`), and selection-mode action menu. |
| `LibraryBadges` | `presentation/library/components/LibraryBadges.kt` | The unread / downloaded / total `BadgeGroup`s that overlay each cover. Uses `tachiyomi.presentation.core.components.Badge` / `BadgeGroup`. |
| `LibraryTabs` | `presentation/library/components/LibraryTabs.kt` | The category tab row above the pager. |
| `GlobalSearchItem` | `presentation/library/components/GlobalSearchItem.kt` | The "search globally" row shown at the top of the library when a search query doesn't match any local item. |
| `DeleteLibraryEntryDialog` | `presentation/library/DeleteLibraryEntryDialog.kt` | The "remove from library / delete downloaded chapters" confirm dialog. |

### Chapter / episode rows

| Component | File | What it does |
|---|---|---|
| `MangaChapterListItem` | `presentation/entries/manga/components/MangaChapterListItem.kt` | The chapter row. Wraps a `me.saket.swipe.SwipeableActionsBox` with configurable start/end swipe actions (`LibraryPreferences.ChapterSwipeAction` — `ToggleRead` / `ToggleBookmark` / `Download` / `Disabled`), shows the unread dot + bookmark icon + title + date + read-progress + scanlator (with `DotSeparatorText` separators), and the `ChapterDownloadIndicator` on the right. Long-press → selection mode. |
| `AnimeEpisodeListItem` | `presentation/entries/anime/components/AnimeEpisodeListItem.kt` | The episode row. Mirror of `MangaChapterListItem` with `EpisodeDownloadIndicator` and episode-number formatting. |
| `AnimeSeasonListItem` | `presentation/entries/anime/components/AnimeSeasonListItem.kt` | The collapsible "season" group header used when an anime has multiple seasons. |
| `ChapterDownloadIndicator` / `EpisodeDownloadIndicator` | `presentation/entries/{manga,anime}/components/{Chapter,Episode}DownloadIndicator.kt` | The circular download-state icon (NOT_DOWNLOADED / QUEUE / DOWNLOADING (with progress %) / DOWNLOADED / ERROR). Tappable to enqueue / cancel. |
| `BaseMangaListItem` / `BaseAnimeListItem` | `presentation/entries/{manga,anime}/components/Base{Manga,Anime}ListItem.kt` | The shared base row layout (cover + title + trailing slot) reused by chapter/episode rows and the track-search results. |
| `DotSeparatorText`, `MissingItemCountListItem` | `presentation/entries/components/DotSeparatorText.kt`, `…/MissingItemCountListItem.kt` | Small helpers: an interpunct separator with proper spacing, and the "N missing chapters" hint row. |
| `EntryBottomActionMenu` / `LibraryBottomActionMenu` | `presentation/entries/components/EntryBottomActionMenu.kt`, `presentation/library/components/LibraryBottomActionMenu.kt` | The bottom action bar shown in selection mode (mark read/unread, bookmark, download, delete, open in browser, set categories, migrate, …). |
| `EntryToolbar` | `presentation/entries/components/EntryToolbar.kt` | The entry-detail AppBar with title + overflow menu (share, open in webview, migrate, edit category, …). |
| `ItemHeader` | `presentation/entries/components/ItemHeader.kt` | The "Continue reading / All chapters" header above the chapter list with sort + filter buttons. |
| `ItemsDialogs` (`DeleteItemsDialog`, `SetIntervalDialog`, `DownloadChaptersDialog`, `ScanlatorFilterDialog`) | `presentation/entries/components/ItemsDialogs.kt`, `presentation/entries/manga/components/ScanlatorFilterDialog.kt` | The bottom-sheet / alert dialogs used from the chapter list. |

### Source browse items

| Component | File | What it does |
|---|---|---|
| `BaseMangaSourceItem` / `BaseAnimeSourceItem` | `presentation/browse/{manga,anime}/components/Base{Manga,Anime}SourceItem.kt` | The source-list row (icon + name + language + trailing action slot). Wraps `BaseBrowseItem`. |
| `BaseBrowseItem` | `presentation/browse/BaseBrowseItem.kt` | The actual `Row` with `combinedClickable` (click + long-click). Used by both source-list and extension-list items. |
| `BrowseMangaSourceList` / `BrowseAnimeSourceList`, `BrowseMangaSourceCompactGrid` / `BrowseAnimeSourceCompactGrid`, `BrowseMangaSourceComfortableGrid` / `BrowseAnimeSourceComfortableGrid` | `presentation/browse/{manga,anime}/components/Browse{Manga,Anime}Source{List,CompactGrid,ComfortableGrid}.kt` | The three display-mode variants for the source browse screen. |
| `BrowseMangaSourceToolbar` / `BrowseAnimeSourceToolbar` | `presentation/browse/{manga,anime}/components/Browse{Manga,Anime}SourceToolbar.kt` | The source-browse `SearchToolbar` with display-mode toggle, webview button, source-settings button. |
| `GlobalMangaSearchToolbar` / `GlobalAnimeSearchToolbar` | `presentation/browse/{manga,anime}/components/Global{Manga,Anime}SearchToolbar.kt` | The `SearchToolbar` variant used on the global-search screen. |
| `GlobalMangaSearchCardRow` / `GlobalAnimeSearchCardRow` | `presentation/browse/{manga,anime}/components/Global{Manga,Anime}SearchCardRow.kt` | A horizontal `LazyRow` of cover cards, one row per source, on the global-search screen. |
| `GlobalSearchResultItems` / `GlobalSerachCard` (sic) | `presentation/browse/GlobalSearchResultItems.kt`, `…/GlobalSerachCard.kt` | Per-source result card (source name + a horizontal row of covers or a "no results" / "loading" / "error" state). |
| `BrowseMangaIcons` / `BrowseAnimeIcons` | `presentation/browse/{manga,anime}/components/Browse{Manga,Anime}Icons.kt` | The source/extension icon loader (Coil `AsyncImage` with a fallback). |
| `BrowseBadges` | `presentation/browse/BrowseBadges.kt` | The "LOCAL" / "INSTALLED" badges shown over source icons. |
| `BrowseSourceLoadingItem`, `BrowseSourceDialogs` | `presentation/browse/BrowseSourceLoadingItem.kt`, `…/BrowseSourceDialogs.kt` | The shimmer-style loading placeholder + the long-press dialogs (open in webview, mark as pinned, etc.). |

### Search bar & app bar

| Component | File | What it does |
|---|---|---|
| `AppBar` (+ `AppBarTitle`, `AppBarActions`, `AppBar.Action`, `AppBar.OverflowAction`) | `presentation/components/AppBar.kt` | The project's M3 `TopAppBar` wrapper. Supports title + subtitle, up button, action-mode (counter + cancel), and a list of `AppBarAction`s (each rendered as a tooltip-wrapped `IconButton`, with overflow collected into a `DropdownMenu`). |
| `SearchToolbar` | `presentation/components/AppBar.kt` | A `SearchAppBar`-style variant of `AppBar` with an in-bar `BasicTextField`, debounced search (`SEARCH_DEBOUNCE_MILLIS = 250L`), close-search button, IME handling. Used by `BrowseMangaSourceToolbar`, `GlobalMangaSearchToolbar`, `LibraryToolbar`, the extensions tab, the history tab. |
| `DropdownMenu` (+ `NestedMenuItem`, `RadioMenuItem`) | `presentation/components/DropdownMenu.kt` | M3 `DropdownMenu` wrappers with nested-submenu and radio-button variants. |
| `FloatingActionAddButton` | `presentation/components/FloatingActionAddButton.kt` | The "Add" `ExtendedFloatingActionButton` that auto-collapses when the bound `LazyListState` is scrolled down (uses `shouldExpandFAB()`). |
| `TabbedScreen`, `TabbedDialog` | `presentation/components/TabbedScreen.kt`, `presentation/components/TabbedDialog.kt` | The reusable `Scaffold`+`PrimaryTabRow`+`HorizontalPager` shells. `TabbedScreen` is the phone top-level-tab wrapper (Library/Updates/History/Browse/…); `TabbedDialog` is the bottom-sheet variant with paged tabs (used by the reader settings, the chapter settings, etc.). |
| `DateText` | `presentation/components/DateText.kt` | Locale-aware date `Text` that respects `UiPreferences.dateFormat()` + `relativeTime()`. |
| `Banners` (`AppStateBanners`, `WarningBanner`, `DownloadedOnlyBannerBackgroundColor`, `IncognitoModeBannerBackgroundColor`, `IndexingBannerBackgroundColor`) | `presentation/components/Banners.kt` | The three coloured banners pinned under the status bar (incognito / downloaded-only / indexing) + the warning banner used for incognito mode. `SubcomposeLayout`-based so the banners stack without overlap. |
| `EntryDownloadDropdownMenu` | `presentation/components/EntryDownloadDropdownMenu.kt` | The "download next N / unread / all" dropdown used from the entry detail screen. |
| `AdaptiveSheet` / `NavigatorAdaptiveSheet` | `presentation/components/AdaptiveSheet.kt` | Voyager-aware wrapper around `tachiyomi.presentation.core.components.AdaptiveSheet`. On phones: bottom sheet with swipe-to-dismiss; on tablets: centered dialog (max width 460.dp). Used to host track-info dialogs, episode-settings, etc. as `Navigator`-based sheets. |

### Pills, badges, tags

| Component | File | What it does |
|---|---|---|
| `Pill` | `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/components/Pill.kt` | The little rounded pill (`MaterialTheme.shapes.extraLarge` `Surface` with `surfaceContainerHigh` background) used for badges and counts. The download-queue header uses it for the pending-count badge. |
| `Badge` (text + icon variants), `BadgeGroup` | `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/components/Badges.kt` | The small rectangular badges (`RectangleShape` by default) that overlay covers — unread count (`secondary`/`onSecondary`), downloaded count (`tertiary`/`onTertiary`), total count, source-local badge. `BadgeGroup` clips them into a connected row. |
| `LibraryBadges` | `presentation/library/components/LibraryBadges.kt` | The composition of `Badge`/`BadgeGroup` actually used by library cards. |

### Lists, grids, scrolling

| Component | File | What it does |
|---|---|---|
| `Scaffold` | `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Scaffold.kt` | Custom M3 `Scaffold` with edge-to-edge + consumed-insets handling (adapted from AOSP, Apache-2.0). Adds a `startBar` slot (used by `HomeScreen` for the `NavigationRail`). |
| `NavigationBar`, `NavigationRail` | `…/components/material/{NavigationBar,NavigationRail}.kt` | M3 bottom-nav / tablet rail wrappers. |
| `ScrollbarLazyColumn`, `ScrollbarLazyGrid` (`VerticalFastScroller`-wrapped `Lazy*`) | `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/components/{LazyList,LazyGrid,VerticalFastScroller}.k` | The `LazyColumn` / `LazyGrid` variants with the project's fast-scroll handle. |
| `VerticalFastScroller` | `…/components/VerticalFastScroller.kt` | The fast-scroll handle (a draggable `Box` with a `systemGestureExclusion` strip) that overlay a `LazyColumn` / `LazyGrid`. |
| `LazyColumnWithAction` | `…/components/LazyColumnWithAction.kt` | `LazyColumn` with a trailing FAB / action slot. |
| `ListGroupHeader` | `…/components/ListGroupHeader.kt` | Sticky-style group header for `LazyColumn`s. |
| `SectionCard` | `…/components/SectionCard.kt` | `ElevatedCard` with an optional title; the canonical settings-row container. |
| `CollapsibleBox` | `…/components/CollapsibleBox.kt` | A box with expand/collapse animation; used for "show more" sections. |
| `TwoPanelBox` | `…/components/TwoPanelBox.kt` | Two-pane layout for tablets / foldables; used by `SettingsScreen` and `PlayerSettingsScreen`. |
| `AdaptiveSheet` (the underlying impl) | `…/components/AdaptiveSheet.kt` | Bottom-sheet (phone) / centered-dialog (tablet) container with `AnchoredDraggableState` swipe-to-dismiss. |
| `WheelPicker` | `…/components/WheelPicker.kt` | Wheel-style picker for date / number selection. |
| `LabeledCheckbox`, `LinkIcon`, `ActionButton` | `…/components/{LabeledCheckbox,LinkIcon,ActionButton}.kt` | Small form primitives. |
| `ReorderableLazyColumn` (via `sh.calvin.reorderable`) | `presentation/category/{Anime,Manga}CategoryScreen.kt`, `presentation/more/settings/screen/player/custombutton/components/CustomButtonScreen.kt` | Drag-to-reorder list (uses Calvin Liang's `reorderable` library). The drag handle is `Icons.Outlined.DragHandle` on `CategoryListItem` / `CustomButtonListItem`. |

### Bottom sheets & dialogs

| Component | File | What it does |
|---|---|---|
| `AdaptiveSheet` (`:app` wrapper) | `presentation/components/AdaptiveSheet.kt` | Voyager-aware wrapper; can host a `Navigator` so a sheet can have its own back stack (`NavigatorAdaptiveSheet`). |
| `TabbedDialog` | `presentation/components/TabbedDialog.kt` | Bottom sheet with `PrimaryTabRow` + `HorizontalPager`; the standard "pinned sheet with tabs" shell. Used by reader settings, episode-settings, etc. |
| `AlertDialog` (M3 wrapper) | `…/components/material/AlertDialog.kt` | Project-default M3 `AlertDialog`. |
| `TriStateListDialog` | `presentation/more/settings/widget/TriStateListDialog.kt` | The tri-state (yes / no / default) checkbox list dialog used by per-category library-update preferences. |

### Empty / loading / info state screens

| Component | File | What it does |
|---|---|---|
| `EmptyScreen` (+ `EmptyScreenAction`) | `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/screens/EmptyScreen.kt` | The "no items" placeholder: centered icon + message + optional action buttons. Picks one of a handful of random messages when given a plural `StringResource`. |
| `LoadingScreen` | `…/screens/LoadingScreen.kt` | Centered `CircularProgressIndicator`. |
| `InfoScreen` | `…/screens/InfoScreen.kt` | Icon + heading + subtitle + accept/reject buttons + content slot. Used by onboarding-style flows. |
| `EmptyScreen` (`:app` preview wrapper) | `presentation/components/EmptyScreen.kt` | Just a `@PreviewLightDark` wrapper around the `:presentation-core` `EmptyScreen`. |

### Settings widgets

| Component | File | What it does |
|---|---|---|
| `Preference`, `PreferenceScreen`, `PreferenceScaffold`, `PreferenceItem` | `presentation/more/settings/{Preference,PreferenceScreen,PreferenceItem,PreferenceScaffold}.k` | The whole declarative settings DSL. A `PreferenceScreen` is a `List<Preference>`; each `Preference` is either a `PreferenceItem` (a typed row — `SwitchPreference`, `TextPreference`, `EditTextPreference`, `ListPreference`, `MultiSelectListPreference`, `InfoWidget`, `CustomPreference`) or a `PreferenceGroup` (titled section). `PreferenceScaffold` renders the `LazyColumn` + `AppBar` + search. |
| `BasePreferenceWidget` | `presentation/more/settings/widget/BasePreferenceWidget.kt` | The shared row shell (icon + title + subtitle + trailing slot + subcomponent slot) every settings widget builds on. |
| `SwitchPreferenceWidget`, `TextPreferenceWidget`, `EditTextPreferenceWidget`, `ListPreferenceWidget`, `MultiSelectListPreferenceWidget`, `TrackingPreferenceWidget`, `InfoWidget` | `presentation/more/settings/widget/*.kt` | The typed `PreferenceItem` widgets. |
| `PreferenceGroupHeader` | `presentation/more/settings/widget/PreferenceGroupHeader.kt` | The sticky group-title row. |
| `AppThemePreferenceWidget`, `AppThemeModePreferenceWidget` | `presentation/more/settings/widget/App*.kt` | The theme picker widgets (see [`theme-design.md`](theme-design.md)). |
| `SettingsItems` (`SettingsItems.kt` in `:presentation-core`) | `…/components/SettingsItems.kt` | Reusable slider / list-pref / switch row composables used inside the reader/player settings panels. |

### Player-specific components

The Player's Compose overlay (`binding.controls.setContent { TachiyomiTheme { PlayerControls(...) } }`)
lives under `ui/player/controls/` and `presentation/player/components/`. Notable
pieces:

| Component | File | What it does |
|---|---|---|
| `PlayerControls` | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerControls.kt` | The root overlay; arranges the four corner controls + middle seek/scrub + the bottom seek bar + dialog/sheet host. |
| `TopLeftPlayerControls`, `TopRightPlayerControls`, `MiddlePlayerControls`, `BottomLeftPlayerControls`, `BottomRightPlayerControls` | `…/controls/{Top,Middle,Bottom}{Left,Right}PlayerControls.kt` | The four corner clusters (lock, PiP, screenshot, audio/subtitle picker, settings, …). |
| `SeekBar` | `…/controls/components/SeekBar.kt` | The bottom scrub bar with thumbnail-preview-on-drag (`ThumbnailPreview.kt`). |
| `ControlsButton` | `…/controls/components/ControlsButton.kt` | The standard player icon button. |
| `VerticalSliders` | `…/controls/components/VerticalSliders.kt` | The vertical brightness / volume sliders (drag on left/right screen edge). |
| `BrightnessOverlay`, `DoubleTapSeekTriangles`, `AutoPlaySwitch` | `…/controls/components/{BrightnessOverlay,DoubleTapSeekTriangles,AutoPlaySwitch}.kt` | Visual overlays for the brightness dimmer, the double-tap-to-seek animation, and the auto-play toggle. |
| Sheets (`ChaptersSheet`, `PlaybackSpeedSheet`, `QualitySheet`, `AudioTracksSheet`, `SubtitleTracksSheet`, `GenericTracksSheet`, `ScreenshotSheet`, `MoreSheet`) | `…/controls/components/sheets/*.kt` | The bottom sheets reachable from the player controls. |
| Dialogs (`EpisodeListDialog`, `IntegerPickerDialog`, `PlayerDialog`) | `…/controls/components/dialogs/*.kt` | The modal dialogs (episode list jump, seek-seconds picker, etc.). |
| Panels (`SubtitleSettingsPanel`, `SubtitleSettingsColorsCard`, `SubtitleSettingsTypographyCard`, `SubtitleSettingsMiscellaneousCard`, `SubtitleDelayPanel`, `AudioDelayPanel`, `VideoFiltersPanel`) | `…/controls/components/panels/*.kt` | The detailed subtitle/audio/filter settings panels (shown when the gear icon is tapped). |
| `PlayerSheet`, `ExpandableCard`, `ExposedTextDropDownMenu`, `SliderItem`, `TintedSliderItem`, `OutlinedNumericChooser`, `OvalBox`, `RepeatingIconButton`, `SwitchPreference` | `presentation/player/components/*.kt` | The reusable form primitives used inside the player settings screens and the on-screen panels. |

### Reader-specific components

| Component | File | What it does |
|---|---|---|
| `ReaderAppBars` (+ `BottomReaderBar`) | `presentation/reader/appbars/{ReaderAppBars,BottomReaderBar}.kt` | The top + bottom app bars overlaying the reader. Top: chapter title, menu, webview, share. Bottom: prev/next page, page slider, chapter list button. |
| `PageIndicatorText` | `presentation/reader/PageIndicatorText.kt` | The bottom-centre "1 / 24" page indicator. |
| `ReaderContentOverlay` | `presentation/reader/ReaderContentOverlay.kt` | The brightness-dim + color-filter overlay drawn over the page. |
| `DisplayRefreshHost` | `presentation/reader/DisplayRefreshHost.kt` | The "flash on page change" animation host (for e-ink / low-refresh-rate screens). |
| `ReadingModeSelectDialog`, `OrientationSelectDialog`, `ModeSelectionDialog`, `ChapterNavigator` | `presentation/reader/{ReadingModeSelectDialog,OrientationSelectDialog,ChapterTransition,components/ChapterNavigator,components/ModeSelectionDialog}.kt` | The mode / orientation pickers and the prev/next chapter navigator. |
| `ReaderPageActionsDialog` | `presentation/reader/ReaderPageActionsDialog.kt` | The per-page action sheet (share, save, set as cover). |
| `ChapterTransition` | `presentation/reader/ChapterTransition.kt` | The "chapter X done → chapter Y next" transition card between chapters. |
| `ReaderSettingsDialog` (+ `GeneralSettingsPage`, `ReadingModePage`, `ColorFilterPage`) | `presentation/reader/settings/*.kt` | The tabbed reader-settings sheet. |

### Track & history

| Component | File | What it does |
|---|---|---|
| `AnimeTrackInfoDialogHome` / `MangaTrackInfoDialogHome` | `presentation/track/{anime,manga}/{Anime,Manga}TrackInfoDialogHome.kt` | The track-info sheet for an entry (one card per registered tracker). |
| `AnimeTrackerSearch` / `MangaTrackerSearch` | `presentation/track/{anime,manga}/{Anime,Manga}TrackerSearch.kt` | The "search the tracker for this entry" sheet. |
| `TrackLogoIcon` (+ `TrackLogoIconPreviewProvider`) | `presentation/track/components/TrackLogoIcon.kt` | The tracker-logo `Image` (MAL / AniList / Shikimori / Bangumi / Simkl / Komga / MangaUpdates / Bangumi / …) with optional tinting. |
| `TrackInfoDialogSelector` | `presentation/track/TrackInfoDialogSelector.kt` | Picks between the manga / anime track dialog. |
| `MangaHistoryItem` / `AnimeHistoryItem` | `presentation/history/{manga,anime}/components/{Manga,Anime}HistoryItem.kt` | The history row (cover + title + chapter/episode + "X time ago" + resume). |
| `HistoryDialogs` | `presentation/history/HistoryDialogs.kt` | The remove / remove-all history dialogs. |

### More / stats / storage / categories

| Component | File | What it does |
|---|---|---|
| `MoreScreen`, `LogoHeader` | `presentation/more/{MoreScreen,LogoHeader}.kt` | The "More" tab list + the Aniyomi logo header. |
| `OnboardingScreen` + steps (`ThemeStep`, `StorageStep`, `PermissionStep`, `GuidesStep`, `OnboardingStep`) | `presentation/more/onboarding/*.kt` | The first-run onboarding pager. |
| `NewUpdateScreen` | `presentation/more/NewUpdateScreen.kt` | The "new version available" card. |
| `StatsItem`, `MangaStatsScreenContent`, `AnimeStatsScreenContent`, `StatsData`, `StatsScreenState` | `presentation/more/stats/*.kt` | The stats screen content (one `StatsItem` per metric). |
| `StorageItem`, `StorageScreenContent`, `CumulativeStorage`, `SelectStorageCategory`, `StorageScreenState` | `presentation/more/storage/*.kt` | The storage screen content (per-entry disk usage bar). |
| `CategoryListItem`, `CategoryFloatingActionButton`, `CategoryDialogs`, `CategoryExtensions` | `presentation/category/components/*.kt` | The category editor row (drag handle + name + edit/delete), the add-category FAB, the rename/create dialog. |

### Crash & webview

| Component | File | What it does |
|---|---|---|
| `CrashScreen` | `presentation/crash/CrashScreen.kt` | The crash-landing composable (stack trace + copy + restart). |
| `WebViewScreenContent` | `presentation/webview/WebViewScreenContent.kt` | The in-app webview (Compose toolbar + Android `WebView` + share / open-in-browser / clear-cookies actions). |

## See also

- [`02-modules/presentation-core.md`](../02-modules/presentation-core.md) — the design-system library that ships the Material wrappers and reusable primitives.
- [`theme-design.md`](theme-design.md) — the `TachiyomiTheme` and the `ColorScheme` every component reads.
- [`screens.md`](screens.md) — the screens that consume these components.
- [`compose-migration.md`](compose-migration.md) — the reader/player Compose overlays listed above are the prime example of Compose-on-Views interop.
- [`01-architecture/03-state-and-async.md`](../01-architecture/03-state-and-async.md) — the `ScreenModel` pattern these components' parents follow.
