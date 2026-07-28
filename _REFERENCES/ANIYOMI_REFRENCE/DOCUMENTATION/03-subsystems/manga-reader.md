# 03-subsystems / manga-reader

> The manga reading engine: a View-based Activity that turns a `Chapter` into a
> stream of decoded pages, drives paged or continuous viewers over them, and
> reports reading progress back to history, trackers, and the download manager.

## 1. Purpose & overview

The reader is the screen that lets the user read a single manga chapter. It is
invoked from anywhere in the app that has a chapter id — Library, History,
Updates, the chapter list of a manga, deep links, or notifications. It receives
a `mangaId` and a `chapterId`, fetches the chapter's pages from the appropriate
source (online HTTP, downloaded directory/archive, EPUB, or local-file source),
and renders them through one of six reading modes.

It is one of only two **legacy View-based Activities** left in the codebase
(the other being the anime `PlayerActivity`). The reason is that the reader is
built on top of two non-Compose Views that have no Compose equivalent:

- **`com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView`** — a
  pan/zoom image view that tiles large bitmaps to avoid OOMs and supports
  sub-sampling on demand. Used for non-animated images.
- **`com.github.chrisbanes.photoview.PhotoView`** — a pinch-to-zoom image view
  used for animated images (GIF/APNG/WebP), where Coil decodes a `Drawable`.

Because the reader has not been migrated, its state lives in an
`androidx.lifecycle.ViewModel` (`ReaderViewModel`) — not a Voyager `ScreenModel`
— and it is launched with `startActivity(Intent(...))` rather than
`navigator.push(...)`. See [`../01-architecture/04-navigation.md`](../01-architecture/04-navigation.md)
for the broader navigation picture.

**Source root:** `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/`

```
reader/
├── ReaderActivity.kt              ← View-based Activity; hosts viewer + Compose menus
├── ReaderViewModel.kt             ← androidx.lifecycle.ViewModel; orchestrates chapter loading
├── ReaderNavigationOverlayView.kt ← Custom View that draws the tap-zone overlay
├── SaveImageNotifier.kt           ← BigPicture notification for "save page"
├── loader/                        ← PageLoader hierarchy (where pages come from)
├── model/                         ← ReaderPage, ReaderChapter, ViewerChapters, …
├── setting/                       ← ReaderPreferences, ReadingMode, ReaderOrientation, …
└── viewer/                        ← Viewer interface and the 6 implementations
    ├── pager/                     ← L2R / R2L / Vertical ViewPager-based viewers
    ├── webtoon/                   ← Webtoon (continuous) + Continuous-Vertical RecyclerView-based viewers
    ├── navigation/                ← Tap-zone layouts (L, Edge, Kindlish, …)
    └── ReaderPageImageView.kt     ← shared FrameLayout wrapping SSIV / PhotoView
```

> Note: Aniyomi has no "anime reader"; the manga reader is single-sided. The
> anime counterpart is the MPV-based `PlayerActivity` covered in
> [`anime-player.md`](anime-player.md).

## 2. Activity ↔ ViewModel split

### `ReaderActivity.kt` (974 lines)

A `BaseActivity` that owns:

- `binding: ReaderActivityBinding` — the View hierarchy inflated from
  `reader_activity.xml`. The most important child is `binding.viewerContainer`,
  the `FrameLayout` into which the active `Viewer`'s root `View` is added.
- `viewModel by viewModels<ReaderViewModel>()` — the standard Android
  `ViewModelProvider` mechanism. Survives configuration changes.
- `config: ReaderConfig` — an inner class that subscribes to a handful of
  reader-level preferences (background color, custom brightness, grayscale /
  inverted-color paint, keep-screen-on, fullscreen, cutout mode, display
  profile) and applies them to the window / container.
- A `displayRefreshHost: DisplayRefreshHost` — drives an optional
  black-frame flash between page changes (epilepsy-reduction preference).

The Activity's lifecycle responsibilities:

| Lifecycle | What it does |
|---|---|
| `onCreate` | Inflate binding; call `viewModel.init(mangaId, chapterId)` in a `launchNonCancellable`; collect `viewModel.state` and `viewModel.eventFlow`; set up the menu Compose content. |
| `onResume` | Restart the read timer; re-apply menu visibility (immersive mode is lost on rotation). |
| `onPause` | `viewModel.flushReadTimer()` — write the session read duration to history. |
| `onDestroy` | `viewer.destroy()`; null out `config`. |
| `dispatchKeyEvent` / `dispatchGenericMotionEvent` | Forwarded to the active `Viewer` first (volume keys, gamepad, scroll wheel). |
| `onKeyUp` (N/P) | Load next / previous chapter — keyboard shortcut. |

The Activity does **not** touch the source or the database directly. All that
goes through the ViewModel.

The UI chrome (top/bottom app bars, settings dialog, reading-mode picker,
orientation picker, page-action sheet, page number indicator, color/brightness
overlays) is rendered with **Jetpack Compose** via `setComposeContent { ... }`
into `binding.dialogRoot` and `binding.pageNumber`. The reader is therefore a
hybrid: the page-display surface is a View, the menus on top of it are Compose.

### `ReaderViewModel.kt` (997 lines)

An `androidx.lifecycle.ViewModel`. Holds all reader state in a single
`StateFlow<State>` plus a one-shot `Channel<Event>` for results that should be
consumed once (saved image, share image, set-as-cover result, page-changed
flash, set-orientation).

`State` is a small `@Immutable data class`:

```kotlin
data class State(
    val manga: Manga? = null,
    val viewerChapters: ViewerChapters? = null,
    val bookmarked: Boolean = false,
    val isLoadingAdjacentChapter: Boolean = false,
    val currentPage: Int = -1,
    val viewer: Viewer? = null,
    val dialog: Dialog? = null,
    val menuVisible: Boolean = false,
    val brightnessOverlayValue: Int = 0, // -100..100
)
```

Key collaborators (all injected via Injekt):

| Field | Role |
|---|---|
| `sourceManager: MangaSourceManager` | Resolve `Manga.source` to a `MangaSource` / `HttpSource` / `LocalMangaSource`. |
| `downloadManager: MangaDownloadManager` | Check if a chapter is downloaded; queue auto-delete-after-read; download-ahead. |
| `downloadProvider: MangaDownloadProvider` | Find the on-disk path of a downloaded chapter. |
| `readerPreferences: ReaderPreferences` | The reader preference catalog (see §7). |
| `trackPreferences` + `trackChapter: TrackChapter` | Push read-progress to trackers. |
| `getManga`, `getChaptersByMangaId`, `getNextChapters`, `updateChapter`, `upsertHistory`, `setMangaViewerFlags` | Domain interactors. |
| `getIncognitoState` | Whether reads should be recorded at all. |

The ViewModel also persists a tiny amount of state into `SavedStateHandle`
(`chapter_id`, `page_index`) so the reader can restore the right page after a
process kill.

## 3. The `loader/` package — where pages come from

The loader layer abstracts over the four possible origins of a chapter's pages.
It is structured as one dispatcher (`ChapterLoader`) and a sealed hierarchy of
`PageLoader` implementations.

### `PageLoader.kt` — the abstract base

```kotlin
abstract class PageLoader {
    var isRecycled = false; private set
    abstract var isLocal: Boolean
    abstract suspend fun getPages(): List<ReaderPage>
    open suspend fun loadPage(page: ReaderPage) {}
    open fun retryPage(page: ReaderPage) {}
    open fun recycle() { isRecycled = true }
}
```

`isLocal` distinguishes loaders that read from disk (no network, no evict-on-
recycle) from the online loader (which caches page images to disk and can be
re-tried). The viewer calls `getPages()` once and `loadPage(page)` per page.

### `ChapterLoader.kt` — the dispatcher

`ChapterLoader` is constructed per `Manga` + `MangaSource` and is the **only**
entry point the ViewModel uses to load a chapter:

```kotlin
suspend fun loadChapter(chapter: ReaderChapter) {
    if (chapterIsReady(chapter)) return
    chapter.state = ReaderChapter.State.Loading
    withIOContext {
        val loader = getPageLoader(chapter)
        chapter.pageLoader = loader
        val pages = loader.getPages().onEach { it.chapter = chapter }
        if (pages.isEmpty()) throw Exception(...)
        if (!chapter.chapter.read || preserveReadingPosition)
            chapter.requestedPage = chapter.chapter.last_page_read
        chapter.state = ReaderChapter.State.Loaded(pages)
    }
}
```

The interesting method is `getPageLoader(chapter)` — a `when` on the source
type and the download state:

```
                ┌─────────────────────────────────────────┐
                │ ChapterLoader.getPageLoader(chapter)    │
                └───────────────────┬─────────────────────┘
                                    │
   isChapterDownloaded? ────────────┼───────────────┐
         (MangaDownloadManager      │               │
          .isChapterDownloaded,     │               │
          skipCache=true)           │               │
                                    ▼               ▼
                          DownloadPageLoader   source is LocalMangaSource?
                                              │                │
                                              ▼                ▼
                                  Format.Directory        source is HttpSource?
                                       │                          │
                                       ▼                          ▼
                            DirectoryPageLoader          HttpPageLoader
                                       │
                                       ▼
                            Format.Archive ─→ ArchivePageLoader
                            Format.Epub    ─→ EpubPageLoader
```

The branches in order:

1. **Downloaded** (managed downloads directory): `DownloadPageLoader`. May
   itself dispatch to `ArchivePageLoader` if the download was saved as a `.cbz`
   rather than a directory of images.
2. **`LocalMangaSource`** (id `0L`): the local-source format is a `Format`
   sealed class — `Directory`, `Archive`, or `Epub` — and each maps to its own
   loader. This is how the reader supports local files without going through a
   network source. See [`../02-modules/source-local.md`](../02-modules/source-local.md).
3. **`HttpSource`** (the typical case): `HttpPageLoader`.
4. **`StubMangaSource`**: the source was uninstalled — throw a friendly error.

### `HttpPageLoader.kt` — online streaming with priority queue

The most complex loader. Responsibilities:

- **Page list**: `getPages()` first tries `ChapterCache.getPageListFromCache()`
  (so reopening a chapter skips the page-list HTTP call) and falls back to
  `source.getPageList(chapter.chapter)`. The result is re-indexed with our own
  sequential indices — sources can't be trusted to set them.
- **Per-page image fetch**: a `PriorityBlockingQueue<PriorityPage>` consumed by
  a single long-running coroutine on `Dispatchers.IO`. Each `PriorityPage`
  carries a priority: `2` = user-initiated retry, `1` = the page the viewer is
  waiting on, `0` = a preload.
- **Preloading**: when a page is loaded, the next `preloadSize = 4` pages are
  queued at priority 0 so the user rarely waits.
- **Caching**: each image is fetched via `source.getImage(page, dataSaver)`
  (the `DataSaver` wrapper lets sources serve bandwidth-reduced URLs) and
  stored in `ChapterCache` keyed by image URL. `page.stream` is then a lambda
  that opens an `InputStream` on that cached file. If the cache evicts the
  image, `loadPage` resets the page to `QUEUE` and re-fetches.
- **Recycle**: on `recycle()`, the page list itself is written back to
  `ChapterCache` so the next open is instant. The coroutine scope and the
  queue are cancelled/cleared.

The page's reactive `statusFlow` (`Page.State.QUEUE` → `LOAD_PAGE` →
`DOWNLOAD_IMAGE` → `READY` / `ERROR`) drives the viewer's progress indicator
and retry button.

### `DownloadedPageLoader.kt` — directory or archive on disk

Wraps `MangaDownloadProvider.findChapterDir(...)`:

- If the chapter was saved as a single `.cbz`/`.cbr`/`.zip`/`.rar` file →
  delegate to `ArchivePageLoader(file.archiveReader(context))` (libarchive via
  `:core:archive`).
- Otherwise (directory of images) → call
  `downloadManager.buildPageList(...)` and produce `ReaderPage`s whose
  `stream` lambda opens the `ContentResolver` for each page `Uri`.

`isLocal = true`, so the viewer never preloads network-style and the loader
never writes back to the chapter cache.

### `ArchivePageLoader.kt` — `.cbz` / `.cbr` / `.zip` / `.rar`

Uses `mihon.core.archive.ArchiveReader` (from `:core:archive`, libarchive-backed).
`getPages()` walks all entries, filters by `ImageUtil.isImage`, sorts by
natural-order name comparison, and assigns each a `stream` that calls
`reader.getInputStream(name)`. Pages are already `READY` because the
`BufferedSource` is available locally.

See [`../02-modules/core-archive.md`](../02-modules/core-archive.md) for how
the underlying libarchive JNI reader works (mmap over `ParcelFileDescriptor`,
sequential walk, no random access).

### `EpubPageLoader.kt` — `.epub`

Uses `mihon.core.archive.EpubReader`. `getPages()` calls
`reader.getImagesFromPages()`, which walks the OPF spine and resolves each
XHTML/HTML item to its image resource via the EPUB manifest. Each page's
`stream` opens the unzipped image directly.

### `DirectoryPageLoader.kt` — a plain directory of images

The simplest loader. Lists `UniFile.listFiles()`, filters images, sorts by
natural-order name, and assigns each page a `stream` of
`file.openInputStream()`. Used by both `LocalMangaSource` (when the local
manga is a folder of images) and as a fallback.

## 4. The `viewer/` package — the six reading modes

### `Viewer.kt` — the interface

```kotlin
interface Viewer {
    fun getView(): View
    fun destroy() {}
    fun setChapters(chapters: ViewerChapters)
    fun moveToPage(page: ReaderPage)
    fun handleKeyEvent(event: KeyEvent): Boolean
    fun handleGenericMotionEvent(event: MotionEvent): Boolean
}
```

The Activity holds exactly one `Viewer` in `State.viewer` and adds its `getView()`
into `binding.viewerContainer`. When the user picks a different reading mode,
the old viewer is `destroy()`-ed, removed, and a new one is constructed.

### `ReadingMode` enum — the six modes

Declared in `setting/ReadingMode.kt`. Each mode has a `flagValue` (stored in
`manga.reader_mode`), a `ViewerType` (Pager or Webtoon), and a `Direction`.

| `ReadingMode` | flag | Type | Direction | Viewer class |
|---|---|---|---|---|
| `DEFAULT` | `0x00` | — | — | (resolves to default pref) |
| `LEFT_TO_RIGHT` | `0x01` | Pager | Horizontal | `L2RPagerViewer` |
| `RIGHT_TO_LEFT` | `0x02` | Pager | Horizontal | `R2LPagerViewer` |
| `VERTICAL` | `0x03` | Pager | Vertical | `VerticalPagerViewer` |
| `WEBTOON` | `0x04` | Webtoon | Vertical | `WebtoonViewer(isContinuous = true)` |
| `CONTINUOUS_VERTICAL` | `0x05` | Webtoon | Vertical | `WebtoonViewer(isContinuous = false)` |

There is no separate "continuous horizontal" mode; horizontal scrolling is
always paged.

### `PagerViewer` family — `pager/`

The paged viewers wrap an AndroidX `DirectionalViewPager` (a fork of
`ViewPager` that supports vertical scrolling). The hierarchy:

```
Viewer (interface)
 └ PagerViewer (abstract)            ← shared logic; owns the ViewPager + adapter + config
    ├ L2RPagerViewer                 ← horizontal, swipe right-to-left advances
    ├ R2LPagerViewer                 ← horizontal, swipe left-to-right advances (manga-style)
    └ VerticalPagerViewer            ← vertical ViewPager
```

Key files:

- **`Pager.kt`** — a `DirectionalViewPager` subclass that adds `tapListener`
  and `longTapListener` via `GestureDetectorWithLongTap`, and swallows the
  default `executeKeyEvent` so the Activity can route keys itself.
- **`PagerViewer.kt`** — the abstract base. Owns `pager`, `config: PagerConfig`,
  `adapter: PagerViewerAdapter`, and `currentPage: Any?` (a `ReaderPage` or a
  `ChapterTransition`). Implements tap-zone dispatch (`config.navigator.getAction(pos)`)
  and chapter-preload triggering when within 5 pages of the chapter end.
- **`PagerConfig.kt`** — extends `ViewerConfig` with pager-specific settings:
  `imageScaleType`, `imageZoomType`, `imageCropBorders`, `navigateToPan`,
  `landscapeZoom`, dual-page split / invert / rotate-to-fit, and the
  `navigationModePager` → `ViewerNavigation` mapping.
- **`PagerViewerAdapter.kt`** — a `ViewPagerAdapter` whose `items` list is a
  mix of `ReaderPage`, `InsertPage`, and `ChapterTransition.Prev`/`.Next`.
  Notable behaviours:
  - Adds the **last 2 pages** of the previous chapter and the **first 2 pages**
    of the next chapter so chapter boundaries feel seamless.
  - For `R2LPagerViewer`, the whole list is `reverse()`-d.
  - Handles **dual-page split**: when a wide page is detected it gets split in
    half and an `InsertPage` is inserted next to it; if the user toggles
    `dualPageSplitPaged` off, all insert pages are removed.
- **`PagerPageHolder.kt`** — a `ReaderPageImageView` subclass that is one cell
  of the ViewPager. On `init`, it launches a `loadPageAndProcessStatus` job
  that calls `loader.loadPage(page)` and then `collectLatest`-es the page's
  `statusFlow` to swap between queued / loading / downloading / ready / error
  UI. When the page becomes `READY`, it reads the `BufferedSource` from
  `page.stream`, optionally splits/rotates wide images for dual-page mode, and
  calls `setImage(...)` on the parent `ReaderPageImageView`.
- **`PagerTransitionHolder.kt`** — a cell that shows a `ReaderTransitionView`
  (Compose) with "Previous/Next chapter" affordances.

The R2L viewer overrides `moveToNext`/`moveToPrevious` to swap left/right,
which is the only behavioural difference between the three paged viewers.

### `WebtoonViewer` family — `webtoon/`

The continuous viewers use a `RecyclerView`-based infinite scroll. There is one
`WebtoonViewer` class; the `isContinuous` constructor flag only affects page
layout (continuous mode uses `WRAP_CONTENT` for each holder, the non-continuous
mode forces each page to fill the screen with vertical paging).

```
WebtoonFrame (FrameLayout)
 └ WebtoonRecyclerView (RecyclerView)
     └ WebtoonAdapter
         ├ WebtoonPageHolder     (one per ReaderPage)
         └ WebtoonTransitionHolder (one per ChapterTransition)
```

Key files:

- **`WebtoonRecyclerView.kt`** — a `RecyclerView` that adds pinch-to-zoom
  (scales the whole recycler between `MIN_RATE` and `MAX_RATE`), double-tap
  zoom, and fling handling. `zoomOutDisabled` clamps the lower bound back to
  `DEFAULT_RATE` when the user disables zoom-out.
- **`WebtoonFrame.kt`** — the container that owns the `ScaleGestureDetector`
  and `GestureDetector` (fling). It translates motion events back into the
  recycler's coordinate space, because the recycler is scaled and its own
  touch handling would otherwise break.
- **`WebtoonLayoutManager.kt`** — a `LinearLayoutManager` subclass that
  implements a custom `scrollToPositionWithOffset` and exposes a scroll
  distance for tap-driven scrolls (default `3/4` of the screen height).
- **`WebtoonAdapter.kt`** — a `RecyclerView.Adapter` with two view types
  (`PAGE_VIEW`, `TRANSITION_VIEW`). Uses `DiffUtil` to dispatch delta updates
  when the chapter list changes, so adding/removing transitions doesn't
  rebind every visible page.
- **`WebtoonPageHolder.kt`** — wraps a `ReaderPageImageView(isWebtoon = true)`.
  Like `PagerPageHolder`, it subscribes to `page.statusFlow` and shows a
  progress indicator while the page is downloading. The holder explicitly
  sets a minimum height while loading so the recycler doesn't try to fill the
  screen with placeholder views.
- **`WebtoonTransitionHolder.kt`** — shows the same `ReaderTransitionView` as
  the pager counterpart.
- **`WebtoonConfig.kt`** — extends `ViewerConfig` with webtoon-only settings:
  `imageCropBorders`, `sidePadding`, `zoomOutDisabled`, `doubleTapZoom`,
  `dualPageSplitWebtoon`, `dualPageInvertWebtoon`, `dualPageRotateToFitWebtoon`,
  and the theme-change listener (a theme change calls
  `ActivityCompat.recreate(activity)` for webtoon because the holder backgrounds
  can't be swapped in place).

### `ReaderPageImageView.kt` — the shared image view

A `FrameLayout` that picks one of two child views depending on whether the
image is animated:

| Image type | Child view | Source of bytes |
|---|---|---|
| Non-animated | `SubsamplingScaleImageView` (or `WebtoonSubsamplingImageView` for webtoon) | `ImageSource.inputStream(source.inputStream())` — SSIV handles tiling and subsampling. Hardware-bitmap config is enabled when the source supports it. |
| Animated (GIF/APNG/WebP) | `PhotoView` (or `AppCompatImageView` for webtoon) | Coil 3 `ImageRequest` with `memoryCachePolicy(DISABLED)` and `diskCachePolicy(DISABLED)` — the page cache is the only cache. |

The `Config` data class carries: `zoomDuration`, `minimumScaleType`,
`cropBorders`, `zoomStartPosition` (LEFT/CENTER/RIGHT), `landscapeZoom`.
`landscapeZoom` auto-zooms wide (landscape) images to fill the height on first
display, starting from the left or right edge depending on reading direction.

The view exposes `panLeft()` / `panRight()` / `canPanLeft()` / `canPanRight()`
— these power the **navigate-to-pan** preference: when on, tapping "next" while
a zoomed-in image can still be panned will pan first instead of advancing.

### Navigation overlays — `viewer/navigation/`

Each `ViewerNavigation` subclass divides the screen into named `RectF` regions
(`PREV`, `NEXT`, `LEFT`, `RIGHT`, `MENU`). The Activity's tap handler asks
`config.navigator.getAction(pos)` and dispatches accordingly. The five
implementations, each with an ASCII-art diagram in its source comment:

| Class | Layout | Use case |
|---|---|---|
| `LNavigation` | L-shape of NEXT on right + bottom, PREV on left + top | Default for vertical paged and all webtoon. |
| `RightAndLeftNavigation` | Right half = NEXT, left half = PREV | Default for horizontal paged. |
| `KindlishNavigation` | Left third = PREV, middle = MENU, right two-thirds = NEXT | Kindle-style. |
| `EdgeNavigation` | Right third = NEXT, left third = NEXT, middle bottom = PREV | Edge tapping. |
| `DisabledNavigation` | Only MENU is active | Pure swipe. |

`TappingInvertMode` (NONE / HORIZONTAL / VERTICAL / BOTH) mirrors the regions
for left-handed users. `ReaderNavigationOverlayView` paints the regions on
first launch (or when `showNavigationOverlayOnStart` is on).

## 5. The `model/` package — reader-side data

The reader has its own thin data classes; it does **not** use the domain
`Chapter` directly inside the viewer.

- **`ReaderChapter`** — wraps a `data.database.models.manga.Chapter` (the
  legacy DB model, not the domain one). Carries `state: State` (a sealed
  interface: `Wait`, `Loading`, `Error(Throwable)`, `Loaded(List<ReaderPage>)`),
  `pageLoader: PageLoader?`, `requestedPage: Int`, and a manual reference
  count (`ref()` / `unref()`). When the count hits zero, the `pageLoader` is
  recycled — this is how the reader frees the previous chapter's network/cache
  resources when only the current chapter is held by `ViewerChapters`.
- **`ReaderPage`** — extends `source.model.Page` and adds a back-reference to
  its `ReaderChapter` and an optional `stream: () -> InputStream`. Pages
  produced by archive/epub/directory loaders have `stream` set and
  `status = READY` immediately; pages from `HttpPageLoader` start in `QUEUE`
  and get their `stream` set only after the image is on disk.
- **`InsertPage`** — `class InsertPage(parent: ReaderPage) : ReaderPage(...)`.
  Represents the right half of a dual-page-spread split. Carries the same
  `index` as its parent so the adapter can keep them adjacent. Crucially, the
  ViewModel **ignores** `InsertPage` in `onPageSelected` (it doesn't update
  reading progress for it).
- **`ViewerChapters`** — a `(currChapter, prevChapter?, nextChapter?)` triple.
  Has `ref()` / `unref()` that propagate to all three; `unref`-ing the previous
  `ViewerChapters` is what triggers chapter recycling.
- **`ChapterTransition`** — `sealed class` with `Prev` and `Next` subclasses.
  Each carries `(from: ReaderChapter, to: ReaderChapter?)`. Its `equals` is
  symmetric in `from`/`to` so the adapter doesn't think a transition changed
  when the user scrolls back and forth.

## 6. The `setting/` package — preferences & enums

### `ReaderPreferences.kt`

A `preferenceStore`-backed catalog (see
[`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md))
with about 45 preferences, grouped in regions:

| Region | Notable preferences |
|---|---|
| General | `pageTransitions`, `flashOnPageChange`/`flashDurationMillis`/`flashPageInterval`/`flashColor`, `doubleTapAnimSpeed`, `showPageNumber`, `showReadingMode`, `fullscreen`, `cutoutShort`, `keepScreenOn`, `defaultReadingMode`, `defaultOrientationType`, `imageScaleType`, `zoomStart`, `readerTheme` (0=white, 1=black, 2=gray, 3=auto), `alwaysShowChapterTransition`, `preserveReadingPosition`, `cropBorders` / `cropBordersWebtoon`, `navigateToPan`, `landscapeZoom`, `webtoonSidePadding`, `readerHideThreshold`, `folderPerManga`, `skipRead`, `skipFiltered`, `skipDupe`, `webtoonDisableZoomOut`, `webtoonDoubleTapZoomEnabled`. |
| Split two-page spread | `dualPageSplitPaged`/`dualPageSplitWebtoon`, `dualPageInvertPaged`/`dualPageInvertWebtoon`, `dualPageRotateToFit` + the `-Invert` and `-Webtoon` variants. |
| Color filter | `customBrightness` + `customBrightnessValue` (-100..100), `colorFilter` + `colorFilterValue` + `colorFilterMode`, `grayscale`, `invertedColors`. |
| Controls | `readWithLongTap`, `readWithVolumeKeys` + `readWithVolumeKeysInverted`, `navigationModePager`, `navigationModeWebtoon`, `pagerNavInverted`, `webtoonNavInverted`, `showNavigationOverlayNewUser`, `showNavigationOverlayOnStart`. |

Embedded enums:

- **`TappingInvertMode`** — NONE / HORIZONTAL / VERTICAL / BOTH.
- **`ReaderHideThreshold`** — HIGHEST/HIGH/LOW/LOWEST, mapping to scroll-pixel
  thresholds (5/13/31/47) at which the menu auto-hides.
- **`FlashColor`** — BLACK / WHITE / WHITE_BLACK.
- The companion `ColorFilterMode` is a `List<Pair<StringResource, BlendMode>>`
  built conditionally for API ≥ 28 (adds Overlay, Lighten, Darken).

### `ReadingMode.kt`

The enum described in §4. The companion's `toViewer(preference, activity)` is
the factory the Activity uses to instantiate a `Viewer` from the user's choice.
`isPagerType(preference)` lets the activity decide which crop-border toggle to
apply.

### `ReaderOrientation.kt`

A 7-value enum (`DEFAULT`, `FREE`, `PORTRAIT`, `LANDSCAPE`, `LOCKED_PORTRAIT`,
`LOCKED_LANDSCAPE`, `REVERSE_PORTRAIT`) wrapping
`ActivityInfo.SCREEN_ORIENTATION_*` constants. Stored in
`manga.viewer_flags` alongside the reading mode (different bit mask:
`ReadingMode.MASK = 0x07`, `ReaderOrientation.MASK = 0x38`).

### `ReaderSettingsScreenModel.kt`

A small Voyager `ScreenModel` (not the ViewModel — the reader is hybrid). It
just exposes `viewerFlow` and `mangaFlow` derived from the ViewModel's
`StateFlow<State>` so the Compose settings dialogs can react to viewer/manga
changes (e.g. show "default reading mode" only when the manga's mode is
`DEFAULT`).

## 7. Page decoding pipeline

The pipeline from `chapterId` to pixels:

```
ReaderViewModel.init(mangaId, chapterId)
   │
   ├─ getManga.await(mangaId)
   ├─ sourceManager.getOrStub(manga.source)
   ├─ new ChapterLoader(context, downloadManager, downloadProvider, manga, source)
   └─ chapterList = getChaptersByMangaId.await(...)  ← runBlocking! (lazy; first access only)
        sortedWith(getChapterSort(manga))
        filtered by skipRead / skipFiltered / skipDupe / downloadedOnly
        mapped to ReaderChapter

ReaderViewModel.loadChapter(loader, selectedChapter)
   │
   └─ ChapterLoader.loadChapter(chapter)
        └─ getPageLoader(chapter) ── see §3 diagram
        └─ loader.getPages()  ← e.g. HttpPageLoader: chapterCache or source.getPageList
        └─ chapter.state = Loaded(pages)

ViewerChapters(curr, prev, next) emitted into StateFlow<State>

ReaderActivity observes state.viewerChapters ─▶ viewer.setChapters(viewerChapters)
   │
   └─ adapter builds item list (pages + transitions + last 2 / first 2 of adjacent)
   └─ PagerPageHolder / WebtoonPageHolder created per visible page
        │
        ├─ loader.loadPage(page)         ← HttpPageLoader enqueues into PriorityBlockingQueue
        ├─ page.statusFlow.collectLatest  ← QUEUE → LOAD_PAGE → DOWNLOAD_IMAGE → READY
        ├─ when READY: stream() ─→ BufferedSource
        │      ├─ ImageUtil.isAnimatedAndSupported(source)
        │      │     ├─ true  ─▶ PhotoView via Coil (Drawable)
        │      │     └─ false ─▶ SubsamplingScaleImageView via ImageSource.inputStream
        │      └─ optional dual-page split / rotate / crop-borders applied to the source
        └─ page is on screen
```

The **decoders**:

- **`TachiyomiImageDecoder`** (in `data/coil/`) — a Coil 3 `Decoder` that uses
  libvips/libpng for non-animated images, gated by the `customDecoder()`
  preference. Used both directly (in `ReaderPageImageView.setNonAnimatedImage`
  via `ImageRequest.customDecoder(true)`) and indirectly for non-reader image
  loads.
- **Coil 3's built-in decoders** — used for animated images and for the
  webtoon viewer when `alwaysDecodeLongStripWithSSIV` is off.
- **`SubsamplingScaleImageView`'s own decoder** — for the paged viewer; tiles
  the image on a background thread and subsamples based on zoom level.
- **Display profile** — `ReaderConfig.setDisplayProfile(path)` loads an ICC
  profile from a user-picked file and pushes it to both SSIV (globally via
  `SubsamplingScaleImageView.setDisplayProfile(data)`) and the custom Coil
  decoder (`TachiyomiImageDecoder.displayProfile = data`). Used for color-
  accurate reading on calibrated screens.

The **disk cache** for online images is `ChapterCache`
(`data/cache/ChapterCache.kt`), an LRU OkHttp `DiskLruCache` keyed by image
URL. The page-list (URLs only, no image bytes) is cached separately so a
reopen of the same chapter skips the page-list HTTP call entirely.

## 8. Gestures, zoom, pan

The reader supports several input modes simultaneously:

- **Tap zones** — see §4's `ViewerNavigation`. Tapping a region triggers
  `moveLeft` / `moveRight` / `moveToNext` / `moveToPrevious` / `toggleMenu`.
  Long-tap opens the page-action sheet (`ReaderPageActionsDialog`).
- **Pinch-to-zoom** — handled by `SubsamplingScaleImageView` (paged) or by
  `WebtoonFrame.ScaleListener` (webtoon, which scales the whole recycler).
- **Double-tap-to-zoom** — paged: SSIV's `setDoubleTapZoomStyle(...)` zooms to
  2× at the tap point and back. Webtoon: configurable on/off
  (`webtoonDoubleTapZoomEnabled`).
- **Pan** — `SubsamplingScaleImageView`'s built-in pan. With
  `navigateToPan = true`, tapping "next" while a zoomed image can still be
  panned will pan by a screen-width first instead of advancing the page.
- **Volume-key navigation** — `readWithVolumeKeys` (and `…Inverted`); the
  viewer handles the key events directly.
- **Keyboard / gamepad** — `ReaderActivity.dispatchKeyEvent` forwards to
  `viewer.handleKeyEvent`, which maps DPAD / PAGE_UP/DOWN / VOLUME / MENU keys
  to navigation. The Activity itself also handles `KEYCODE_N` / `KEYCODE_P`
  for next/previous chapter.
- **Mouse wheel** — `dispatchGenericMotionEvent` → `WebtoonViewer` or
  `PagerViewer.handleGenericMotionEvent` translates `AXIS_VSCROLL` to
  `moveUp` / `moveDown`.

## 9. Launching the reader & reporting progress

### Launch

The reader is launched via `ReaderActivity.newIntent(context, mangaId, chapterId)`:

```kotlin
companion object {
    fun newIntent(context: Context, mangaId: Long?, chapterId: Long?): Intent =
        Intent(context, ReaderActivity::class.java).apply {
            putExtra("manga", mangaId)
            putExtra("chapter", chapterId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
}
```

Callers (all in `:app`):

- **Library / Browse / manga details** — the chapter list bottom sheet's
  "read" action.
- **History tab** — `HistoryScreen` resume button.
- **Updates tab** — `UpdatesScreen` row tap.
- **Notifications** — `NotificationReceiver.handleNewChapters` notification
  PendingIntent.

The Activity reads the extras in `onCreate`, dismisses the matching
new-chapters notification (`Notifications.ID_NEW_CHAPTERS`), and calls
`viewModel.init(mangaId, chapterId)`.

### Progress reporting

`ReaderViewModel.onPageSelected(page: ReaderPage)` is called by the viewer
every time the visible page changes. It does, in order:

1. **Skip `InsertPage`** (split-page second halves don't advance progress).
2. If the page's chapter is different from the current one — call
   `loadNewChapter(...)` (which `flushReadTimer()`s the old chapter and loads
   the new one). This is how scrolling across a chapter boundary "promotes"
   the next chapter to active.
3. `updateChapterProgress(...)` (in a `launchNonCancellable`):
   - Sets `chapter.last_page_read = page.index`.
   - If this was the last page — mark `read = true`, push to tracker
     (`updateTrackChapterRead`), enqueue deletion (`deleteChapterIfNeeded`),
     optionally mark duplicate chapters read.
   - Persist via `updateChapter.await(ChapterUpdate(...))`.
   - All skipped when `incognitoMode` is on (or page is in ERROR state).
4. If the page is past 25% of the chapter and the current loader is a
   `DownloadPageLoader` — call `downloadNextChapters()` to download-ahead by
   `autoDownloadWhileReading` chapters.
5. Emit `Event.PageChanged` — the Activity flashes the display-refresh host.

The **read timer** is separate: `restartReadTimer()` is called on `onResume`
and on chapter change; `flushReadTimer()` is called on `onPause` and on
chapter change. It computes `sessionReadDuration = now - chapterReadStartTime`
and calls `upsertHistory.await(MangaHistoryUpdate(chapterId, readAt, sessionReadDuration))`.

See [`../05-key-flows/read-manga.md`](../05-key-flows/read-manga.md) for the
end-to-end flow and [`../03-subsystems/history.md`](history.md) for the
history model.

### Tracker integration

`updateTrackChapterRead(readerChapter)` runs in a `launchNonCancellable` when a
chapter is marked read. It checks:

- `incognitoMode` is off **and** `trackPreferences.autoUpdateTrack().get()` is on.
- Calls `trackChapter.await(context, manga.id, chapter.chapter_number.toDouble())`.

The `TrackChapter` interactor (in `:app`'s `domain/track/manga/`) looks up all
`Track` rows for the manga, finds the matching `Tracker` (MAL, AniList,
Shikimori, Bangumi, …), and updates the remote progress. See
[`../03-subsystems/trackers.md`](trackers.md).

## 10. Save-image, share, and set-as-cover

The page-action sheet (`ReaderPageActionsDialog`, opened via long-tap) exposes
three actions, all of which require the page to be in `Page.State.READY`:

- **Set as cover** — `viewModel.setAsCover()` calls `manga.editCover(context,
  stream())` (an extension in `util/editCover.kt`). Returns `Success`,
  `AddToLibraryFirst` (the manga must be in the library), or `Error`. The
  result is delivered as `Event.SetCoverResult` and shown as a toast by the
  Activity.
- **Share** — `viewModel.shareImage(copyToClipboard: Boolean)`. Copies the
  page to `context.cacheImageDir` (because the original may be inside a ZIP),
  then sends `Event.ShareImage(uri, page)` (or `CopyImage`). The Activity
  builds an `ACTION_SEND` intent with a formatted message
  `"${manga.title} - ${chapter.name} - ${page.number}"`.
- **Save** — `viewModel.saveImage()`. Uses the `ImageSaver` (in `data/saver/`)
  to write to the `Pictures/` directory (or `Pictures/<manga title>/` if
  `folderPerManga` is on). A `SaveImageNotifier` posts a `BigPictureStyle`
  notification with a share action; the result is also delivered to the
  Activity as `Event.SavedImage`.

`SaveImageNotifier.kt` is a small helper that builds the notification on
`Notifications.CHANNEL_COMMON`, fetches a thumbnail bitmap via Coil, and wires
up the share PendingIntent via `NotificationReceiver.shareImagePendingBroadcast`.

## 11. Key files table

| File | Lines | Role |
|---|---|---|
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt` | ~975 | View-based Activity; owns viewer container + Compose menus; lifecycle; key/motion dispatch; `ReaderConfig` inner class for window/preferences. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt` | ~997 | `ViewModel` with `State`/`Event`; orchestrates `ChapterLoader`, `ViewerChapters`, page selection, progress, history, tracker, download-ahead, save/share/set-cover. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderNavigationOverlayView.kt` | ~117 | Custom `View` that paints tap-zone regions on first launch. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/SaveImageNotifier.kt` | ~96 | `BigPictureStyle` notification for saved images. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/ChapterLoader.kt` | ~112 | Per-manga dispatcher: picks `DownloadPageLoader` / `LocalSource` loader / `HttpPageLoader` / throws for `StubMangaSource`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/PageLoader.kt` | ~46 | Abstract base: `getPages`, `loadPage`, `retryPage`, `recycle`, `isLocal`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/HttpPageLoader.kt` | ~228 | Online loader with `PriorityBlockingQueue`, 4-page preload, `ChapterCache` round-trip, page-list caching on recycle. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/DownloadedPageLoader.kt` | ~77 | Dispatches to `ArchivePageLoader` (for `.cbz`) or directory listing. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/ArchivePageLoader.kt` | ~36 | libarchive-backed loader via `:core:archive`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/EpubPageLoader.kt` | ~31 | EPUB loader via `:core:archive`'s `EpubReader`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/DirectoryPageLoader.kt` | ~29 | Plain-directory-of-images loader. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/model/ReaderChapter.kt` | ~51 | Chapter wrapper with `State` sealed interface and reference counting. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/model/ReaderPage.kt` | ~14 | `Page` subclass with `chapter` back-reference and `stream` lambda. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/model/InsertPage.kt` | ~11 | Half of a dual-page split; ignored by progress tracking. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/model/ViewerChapters.kt` | ~20 | `(curr, prev?, next?)` triple with ref/unref. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/model/ChapterTransition.kt` | ~35 | Sealed `Prev` / `Next` with symmetric equality. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt` | ~256 | ~45 preferences + `TappingInvertMode`/`ReaderHideThreshold`/`FlashColor` enums + `ColorFilterMode` list. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReadingMode.kt` | ~93 | The 6-mode enum + `toViewer` factory. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderOrientation.kt` | ~69 | 7-value orientation enum. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderSettingsScreenModel.kt` | ~31 | Voyager `ScreenModel` exposing `viewer`/`manga` flows to the settings dialogs. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/Viewer.kt` | ~45 | The viewer interface. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ViewerConfig.kt` | ~90 | Abstract base for `PagerConfig` / `WebtoonConfig`; common preferences. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ViewerNavigation.kt` | ~69 | Tap-zone base + `NavigationRegion` enum. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ReaderPageImageView.kt` | ~442 | Shared `FrameLayout` picking SSIV vs PhotoView; zoom/pan config. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt` | ~450 | Abstract paged viewer; owns ViewPager + adapter + config + tap-zone dispatch. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewers.kt` | ~53 | `L2RPagerViewer`, `R2LPagerViewer`, `VerticalPagerViewer`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/Pager.kt` | ~111 | `DirectionalViewPager` subclass with tap/long-tap detection. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerConfig.kt` | ~152 | Pager-specific settings + navigation-mode mapping. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewerAdapter.kt` | ~204 | Adapter mixing pages, insert pages, transitions; R2L reversal; dual-page split. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerPageHolder.kt` | ~303 | One cell of the ViewPager; loads + status-reacts + dual-page processing. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerTransitionHolder.kt` | — | Transition cell. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonViewer.kt` | ~365 | `RecyclerView`-based viewer with pinch-zoom and tap dispatch. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonRecyclerView.kt` | ~350 | `RecyclerView` with scale/zoom/fling support. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonFrame.kt` | ~110 | Container that owns scale/fling detectors and translates motion events. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonLayoutManager.kt` | — | Custom `LinearLayoutManager`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonAdapter.kt` | ~200 | DiffUtil-based adapter with two view types. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonPageHolder.kt` | ~312 | Page holder with progress indicator and status reactivity. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonConfig.kt` | ~122 | Webtoon-specific settings. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/navigation/*.kt` | ~30 each | The 5 navigation layouts. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ReaderTransitionView.kt` | ~74 | `AbstractComposeView` rendering a `ChapterTransition` cell. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ReaderProgressIndicator.kt` | — | Small circular progress bar. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/GestureDetectorWithLongTap.kt` | — | `GestureDetector` wrapper that reliably emits long-tap. |

## See also

- [`../05-key-flows/read-manga.md`](../05-key-flows/read-manga.md) — the
  end-to-end user journey from "tap a chapter" to "marked read".
- [`anime-player.md`](anime-player.md) — the anime counterpart (MPV-based).
- [`../02-modules/app.md`](../02-modules/app.md) — where the reader sits in
  the `:app` module.
- [`../02-modules/source-api.md`](../02-modules/source-api.md) — `HttpSource`
  and `Page` (the source-side half of the page contract).
- [`../02-modules/source-local.md`](../02-modules/source-local.md) — the
  `LocalMangaSource` and the `Format` sealed class.
- [`../02-modules/core-archive.md`](../02-modules/core-archive.md) — libarchive
  and EPUB readers used by the archive/epub loaders.
- [`../02-modules/core-common.md`](../02-modules/core-common.md) —
  `ChapterCache`, `ImageSaver`, `ImageUtil`, the preference system.
- [`history.md`](history.md) — the history upsert that the read timer feeds.
- [`trackers.md`](trackers.md) — `TrackChapter` and the tracker ecosystem.
- [`download-manager.md`](download-manager.md) — the download-ahead and
  delete-after-read hooks.
- [`../01-architecture/04-navigation.md`](../01-architecture/04-navigation.md)
  — why the reader is a legacy Activity and not a Voyager screen.
- [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md)
  — the `PreferenceStore` backing `ReaderPreferences`.
- [`../06-ui/compose-migration.md`](../06-ui/compose-migration.md) — the
  reader's hybrid View+Compose status.
