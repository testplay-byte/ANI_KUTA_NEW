# 05-key-flows / Open & read a manga chapter

> Trace a chapter tap from any list (Library, History, Updates, manga
> details, deep link, notification) through the legacy `ReaderActivity` →
> `ReaderViewModel` → `ChapterLoader` → `PageLoader` → `Viewer`, and back
> out via the history + tracker write paths.

The reader is one of only two legacy View-based Activities in the codebase
(the other is the anime player — see [`watch-anime.md`](watch-anime.md)).
See [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md)
for the full subsystem deep dive; this doc focuses on the end-to-end flow.

## Overview

```
USER: tap chapter row (anywhere)
   │
   ▼
context.startActivity(ReaderActivity.newIntent(context, mangaId, chapterId))
   │
   ▼
ReaderActivity.onCreate()
   ├─ inflate binding (reader_activity.xml)
   ├─ viewModel.init(mangaId, chapterId)  ← launchNonCancellable
   └─ collect viewModel.state  →  viewer.setChapters(state.viewerChapters)
   │
   ▼
ReaderViewModel.init(mangaId, chapterId)
   ├─ getManga.await(mangaId)
   ├─ sourceManager.getOrStub(manga.source)   ← MangaSource (extension or stub)
   ├─ new ChapterLoader(context, downloadManager, downloadProvider, manga, source)
   ├─ chapterList = getChaptersByMangaId.await(...)   ← runBlocking once
   │     sortedWith(getChapterSort(manga))
   │     filtered by skipRead / skipFiltered / skipDupe / downloadedOnly
   │     mapped to ReaderChapter
   └─ loadChapter(loader, chapterList.first { it.chapter.id == chapterId })
        │
        ▼
   ChapterLoader.loadChapter(chapter)
        ├─ getPageLoader(chapter)  ── dispatch:
        │     ├─ Downloaded?            → DownloadPageLoader (→ ArchivePageLoader if .cbz)
        │     ├─ LocalMangaSource?      → Directory/Archive/Epub PageLoader
        │     ├─ HttpSource?            → HttpPageLoader
        │     └─ StubSource?            → throw
        ├─ loader.getPages()            ── List<ReaderPage>
        ├─ chapter.requestedPage = chapter.last_page_read   (resume)
        └─ chapter.state = Loaded(pages)
   │
   ▼
ViewerChapters(curr, prev, next) emitted into StateFlow<State>
   │
   ▼
ReaderActivity observes state  →  viewer = ReadingMode.toViewer(...)
   └─ viewer.setChapters(viewerChapters)
        └─ PagerViewerAdapter / WebtoonAdapter builds items (pages + transitions + adjacent)
             └─ PagerPageHolder / WebtoonPageHolder per visible page
                  ├─ loader.loadPage(page)     ← HttpPageLoader enqueues PriorityBlockingQueue
                  ├─ page.statusFlow.collectLatest  ← QUEUE→LOAD_PAGE→DOWNLOAD_IMAGE→READY
                  └─ when READY: stream() → BufferedSource
                       ├─ isAnimated? → PhotoView (Coil Drawable)
                       └─ else        → SubsamplingScaleImageView (ImageSource.inputStream)
   │
USER swipes/pages
   │
   ▼
Viewer.onPageSelected(page)  →  ReaderViewModel.onPageSelected(page)
   ├─ updateChapterProgress(selectedChapter, page)   ← write last_page_read / mark read
   ├─ if (selectedChapter != currentChapter) loadNewChapter(selectedChapter)
   ├─ if (page > 25% of chapter) downloadNextChapters()  ← auto-download-ahead
   └─ eventChannel.send(PageChanged)
   │
On chapter complete (last page):
   ├─ readerChapter.chapter.read = true
   ├─ updateTrackChapterRead(readerChapter)  →  TrackChapter.await(...)  (see track-progress.md)
   ├─ deleteChapterIfNeeded(readerChapter)   ← remove-after-read N-back
   └─ mark duplicate-numbered chapters read (if pref on)
   │
On Activity onPause:
   └─ viewModel.flushReadTimer()  →  updateHistory(currChapter)  →  UpsertMangaHistory
```

## Step-by-step

### 1. The chapter tap → `ReaderActivity`

Every UI surface that lets the user open the reader builds the same intent:

```kotlin
context.startActivity(ReaderActivity.newIntent(context, mangaId, chapterId))
```

Call sites include:

- `MangaLibraryTab.Content` — the "continue reading" button
  (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/library/manga/MangaLibraryTab.kt`).
- The manga details screen's chapter list
  (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/entries/manga/MangaScreen.kt`).
- The History tab (`MangaHistoryTab.kt`).
- The Updates tab (`MangaUpdatesTab.kt`).
- Notification actions (`NotificationHandler.kt`).
- Deep links (`DeepLinkMangaActivity.kt`).

`ReaderActivity` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`)
is **not** a Voyager screen — it is a plain `BaseActivity` launched via
`startActivity`. The reason it has not been migrated to Compose is that it
is built on top of two non-Compose Views:

- `com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView` — the
  pan/zoom/tile decoder for non-animated images.
- `com.github.chrisbanes.photoview.PhotoView` — pinch-to-zoom for animated
  images (GIF/APNG/WebP), decoded by Coil 3.

The menus and overlays on top of the page surface are Compose, rendered
into `binding.dialogRoot` and `binding.pageNumber` via `setComposeContent`.

### 2. `ReaderActivity.onCreate()` → `viewModel.init`

`onCreate` inflates `reader_activity.xml`, obtains the ViewModel via
`viewModels<ReaderViewModel>()`, and in a `launchNonCancellable` block calls
`viewModel.init(mangaId, chapterId)`. It then `collect`s `viewModel.state`
and `viewModel.eventFlow` so it can react to page changes, set-cover
results, saved-image results, etc.

Lifecycle responsibilities:

| Lifecycle | What it does |
|---|---|
| `onCreate` | Inflate binding; `viewModel.init`; collect state; set up Compose menu content. |
| `onResume` | Restart the read timer; re-apply menu visibility. |
| `onPause` | `viewModel.flushReadTimer()` — write session read duration to history. |
| `onDestroy` | `viewer.destroy()`; null out `config`. |
| `dispatchKeyEvent` / `dispatchGenericMotionEvent` | Forward to active `Viewer` (volume keys, gamepad, scroll wheel). |
| `onKeyUp` (N/P) | Load next / previous chapter. |

### 3. `ReaderViewModel.init` → chapter list

`ReaderViewModel.init(mangaId, initialChapterId)`
(`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt`)
is a `suspend fun` called from `onCreate`'s `launchNonCancellable`. It:

1. `getManga.await(mangaId)` — single-row SQLDelight query.
2. `sourceManager.isInitialized.first { it }` — wait for the
   `MangaExtensionManager` to finish its initial scan.
3. `source = sourceManager.getOrStub(manga.source)` — returns the live
   `HttpSource`/`LocalMangaSource`, or a `StubMangaSource` if the extension
   is uninstalled (so the reader shows a friendly "reinstall extension"
   error instead of an NPE).
4. Constructs `ChapterLoader(context, downloadManager, downloadProvider,
   manga, source)`.
5. Builds `chapterList` — `getChaptersByMangaId.await(...)` (a
   `runBlocking` one-shot; subsequent accesses use the cached list), then
   `sortedWith(getChapterSort(manga))`, then filtered by `skipRead` /
   `skipFiltered` / `skipDupe` / `downloadedOnly` reader preferences, then
   mapped to `ReaderChapter` wrappers.
6. `loadChapter(loader, chapterList.first { it.chapter.id == chapterId })`.

### 4. `ChapterLoader.loadChapter` → `PageLoader` dispatch

`ChapterLoader.loadChapter(chapter)` (in
`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/ChapterLoader.kt`)
sets `chapter.state = Loading`, then calls `getPageLoader(chapter)` which
dispatches on download state and source type:

```
                ┌─────────────────────────────────────────┐
                │ ChapterLoader.getPageLoader(chapter)    │
                └───────────────────┬─────────────────────┘
                                    │
   isChapterDownloaded? ────────────┼───────────────┐
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

The chosen `PageLoader` is stored on `chapter.pageLoader`, then
`loader.getPages()` is awaited to produce `List<ReaderPage>`. The
chapter's `requestedPage` is restored to `chapter.chapter.last_page_read`
(unless the chapter is already `read` or `preserveReadingPosition` is
off), so the reader reopens at the right page. Finally,
`chapter.state = Loaded(pages)`.

### 5. The four `PageLoader`s

| Loader | Source | How pages arrive |
|---|---|---|
| `HttpPageLoader` | online `HttpSource` | `getPages()` first tries `ChapterCache.getPageListFromCache()`, falls back to `source.getPageList(chapter)`. Re-indexed with our own sequential indices (sources can't be trusted). Per-page image fetch is a `PriorityBlockingQueue<PriorityPage>` consumed by a single long-running coroutine on `Dispatchers.IO`; priorities are 2=user retry, 1=viewer-waiting, 0=preload. Preloads next 4 pages. Each image is fetched via `source.getImage(page, dataSaver)` and stored in `ChapterCache` keyed by image URL. `page.stream` becomes a lambda that opens an `InputStream` on the cached file. On `recycle()`, the page list is written back to `ChapterCache` so the next open is instant. |
| `DownloadedPageLoader` | downloaded directory or `.cbz` | Wraps `MangaDownloadProvider.findChapterDir(...)`. If `.cbz`/`.cbr`/`.zip`/`.rar` → delegates to `ArchivePageLoader`. Otherwise calls `downloadManager.buildPageList(...)` and produces `ReaderPage`s whose `stream` lambda opens the `ContentResolver` for each page `Uri`. `isLocal = true`, so the viewer never preloads network-style and never writes to chapter cache. |
| `ArchivePageLoader` | `.cbz`/`.cbr`/`.zip`/`.rar` | Uses `mihon.core.archive.ArchiveReader` (libarchive). Walks all entries, filters by `ImageUtil.isImage`, sorts by natural-order name, assigns each a `stream` that calls `reader.getInputStream(name)`. Pages are immediately `READY`. |
| `EpubPageLoader` | `.epub` | Uses `mihon.core.archive.EpubReader`. Walks the OPF spine, resolves each XHTML/HTML item to its image via the EPUB manifest. |
| `DirectoryPageLoader` | plain folder of images | Lists `UniFile.listFiles()`, filters images, sorts by natural-order name. Used by `LocalMangaSource` when the local manga is a folder of images. |

### 6. `ViewerChapters` → `Viewer` → pixels

`loadChapter` builds a `ViewerChapters(curr, prev, next)` triple and emits
it into `StateFlow<State>`. The Activity observes the change and:

1. Picks the `Viewer` based on `ReadingMode.fromValue(manga.viewer_flags
   & MASK)`, falling back to `readerPreferences.defaultReadingMode()` for
   `DEFAULT`. The six modes:
   | `ReadingMode` | Viewer class |
   |---|---|
   | `LEFT_TO_RIGHT` | `L2RPagerViewer` |
   | `RIGHT_TO_LEFT` | `R2LPagerViewer` |
   | `VERTICAL` | `VerticalPagerViewer` |
   | `WEBTOON` | `WebtoonViewer(isContinuous = true)` |
   | `CONTINUOUS_VERTICAL` | `WebtoonViewer(isContinuous = false)` |
2. Calls `viewer.setChapters(viewerChapters)`.
3. The viewer's adapter builds an item list mixing pages,
   `ChapterTransition.Prev` / `.Next` (so chapter boundaries feel
   seamless), and the last 2 pages of the previous chapter and first 2 of
   the next chapter for preload. `R2LPagerViewer` reverses the list.
4. Each `PagerPageHolder` / `WebtoonPageHolder` is a `ReaderPageImageView`
   that:
   - Calls `loader.loadPage(page)` — `HttpPageLoader` enqueues the page
     into its `PriorityBlockingQueue`.
   - `collectLatest`-es `page.statusFlow` (`QUEUE` → `LOAD_PAGE` →
     `DOWNLOAD_IMAGE` → `READY` / `ERROR`) to swap between
     queued / loading / downloading / ready / error UI.
   - When `READY`, reads the `BufferedSource` from `page.stream` and:
     - If animated → `PhotoView` via Coil `ImageRequest`
       (`memoryCachePolicy(DISABLED)`, `diskCachePolicy(DISABLED)` — the
       page cache is the only cache).
     - Else → `SubsamplingScaleImageView` via
       `ImageSource.inputStream(source.inputStream())` (SSIV tiles and
       subsamples on demand).
   - Optional dual-page split / rotate / crop-borders applied to the
     source first.

### 7. `onPageSelected` — the heart of the read loop

Every time the visible page changes, the viewer calls
`ReaderViewModel.onPageSelected(page)`:

```kotlin
fun onPageSelected(page: ReaderPage) {
    if (page is InsertPage) return            // dual-page split halves don't count

    val selectedChapter = page.chapter
    val pages = selectedChapter.pages ?: return

    viewModelScope.launchNonCancellable {
        updateChapterProgress(selectedChapter, page)   // ← write last_page_read
    }

    if (selectedChapter != getCurrentChapter()) {
        loadNewChapter(selectedChapter)                // ← preload adjacent chapter
    }

    val inDownloadRange = page.number.toDouble() / pages.size > 0.25
    if (inDownloadRange) {
        downloadNextChapters()                         // ← auto-download-ahead
    }

    eventChannel.trySend(Event.PageChanged)
}
```

`updateChapterProgress(readerChapter, page)` writes
`chapters.last_page_read = page.index` via `updateChapter.await(
ChapterUpdate(id, read, lastPageRead))`. If the page is the **last page**
(`page.index == pages.lastIndex`), it calls
`updateChapterProgressOnComplete(readerChapter)`:

1. `readerChapter.chapter.read = true`.
2. `updateTrackChapterRead(readerChapter)` → `TrackChapter.await(context,
   manga.id, chapter.chapter_number)` (see [`track-progress.md`](track-progress.md)).
3. `deleteChapterIfNeeded(readerChapter)` — if the
   `removeAfterReadSlots` preference is set, enqueues the chapter N-back
   for deletion.
4. Optionally marks duplicate-numbered chapters read (if the
   `markDuplicateReadChapterAsRead` pref is on).

`loadNewChapter(chapter)` is called when the user crosses a chapter
boundary: it flushes the read timer, restarts it, and calls `loadChapter`
on the new chapter. The old chapter's `ViewerChapters.unref()` triggers
its `PageLoader.recycle()`, freeing the network/cache resources.

`downloadNextChapters()` only fires if `downloadAheadAmount > 0` AND the
current chapter is being read from a `DownloadPageLoader` (so we don't
download-ahead over a streaming read). It checks that the next chapter is
already downloaded, then enqueues the next `downloadAheadAmount` unread
chapters via `downloadManager.downloadChapters(manga, chaptersToDownload)`
— see [`download-chapter.md`](download-chapter.md).

### 8. History & tracker writes

`updateHistory(readerChapter)` is called from:

- `flushReadTimer()` on `Activity.onPause` (and on chapter change).
- The reader's `onSaveInstanceState`-non-config-change hook.

```kotlin
private suspend fun updateHistory(readerChapter: ReaderChapter) {
    if (incognitoMode) return
    val chapterId = readerChapter.chapter.id!!
    val readAt = Date()
    val sessionReadDuration = chapterReadStartTime?.let { readAt.time - it } ?: 0
    upsertHistory.await(MangaHistoryUpdate(chapterId, readAt, sessionReadDuration))
    chapterReadStartTime = null
}
```

The `UpsertMangaHistory` interactor calls `history.upsert` SQLDelight
query — note the `time_read = time_read + :time_read` accumulator
(documented in [`../03-subsystems/history.md`](../03-subsystems/history.md)).

The tracker write happens in `updateTrackChapterRead` (step 7's
`updateChapterProgressOnComplete`): `TrackChapter.await(context, manga.id,
chapterNumber)`. This pushes the new chapter count to every bound tracker
(MAL, AniList, Shikimori, Bangumi, MangaUpdates, Komga/Kavita/Suwayomi as
enhanced trackers) — see [`track-progress.md`](track-progress.md).

> **Manga/anime asymmetry:** The reader's `updateHistory` is gated on
> `!incognitoMode` only. The anime player's equivalent is gated on
> `!incognitoMode || hasTrackers`. Documented in
> [`../03-subsystems/history.md`](../03-subsystems/history.md).

## Sequence diagram

```
USER: tap chapter
   │
   ▼
ReaderActivity.onCreate()
   └─ viewModel.init(mangaId, chapterId)
        ├─ getManga.await(mangaId)  ── SQLDelight
        ├─ sourceManager.getOrStub(manga.source)  ── HttpSource (extension)
        ├─ new ChapterLoader(ctx, dlMgr, dlProv, manga, source)
        ├─ chapterList = getChaptersByMangaId.await() → sort → filter → map to ReaderChapter
        └─ loadChapter(loader, selectedChapter)
             └─ ChapterLoader.loadChapter(chapter)
                  ├─ getPageLoader(chapter)  ── HttpPageLoader (typical)
                  ├─ loader.getPages()  ── chapterCache.getPageListFromCache OR source.getPageList(chapter)
                  │       └─ OkHttp → Jsoup → List<Page> with image URLs
                  ├─ chapter.requestedPage = chapter.last_page_read   ← resume
                  └─ chapter.state = Loaded(pages)
        └─ emit ViewerChapters(curr, prev, next) into StateFlow<State>
   │
   ▼
ReaderActivity collects state → viewer = ReadingMode.toViewer(...)
   └─ viewer.setChapters(viewerChapters)
        └─ adapter → PagerPageHolder(page)
             ├─ loader.loadPage(page)  ── HttpPageLoader.PriorityBlockingQueue
             │     └─ source.getImage(page, dataSaver) → OkHttp → ChapterCache.put
             ├─ page.statusFlow: QUEUE → LOAD_PAGE → DOWNLOAD_IMAGE → READY
             └─ when READY: stream() → SubsamplingScaleImageView (or PhotoView if animated)
USER: swipe to next page
   │
   ▼
Viewer → ReaderViewModel.onPageSelected(page)
   ├─ updateChapterProgress: updateChapter.await(ChapterUpdate(id, lastPageRead))
   ├─ if last page: updateChapterProgressOnComplete
   │     ├─ chapter.read = true
   │     ├─ TrackChapter.await(ctx, manga.id, chapterNumber)   → see track-progress.md
   │     └─ deleteChapterIfNeeded(chapter)                     → see download-chapter.md
   └─ if page > 25%: downloadNextChapters()                    → see download-chapter.md
USER: back / home
   │
   ▼
ReaderActivity.onPause() → viewModel.flushReadTimer()
   └─ updateHistory(currChapter)
        └─ upsertHistory.await(MangaHistoryUpdate(chapterId, readAt, sessionReadDuration))
             └─ history.upsert SQLDelight query (time_read += sessionReadDuration)
```

## See also

- [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) — the full reader subsystem deep dive (viewer hierarchy, navigation overlays, preferences, decoders).
- [`watch-anime.md`](watch-anime.md) — the anime counterpart.
- [`../03-subsystems/history.md`](../03-subsystems/history.md) — the history write path and the resume-point logic.
- [`track-progress.md`](track-progress.md) — what `TrackChapter` does after a chapter is marked read.
- [`download-chapter.md`](download-chapter.md) — the auto-download-ahead path.
- [`../01-architecture/04-navigation.md`](../01-architecture/04-navigation.md) — why the reader is a legacy Activity, not a Voyager screen.
