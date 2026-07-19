# Download Manager

Aniyomi's offline media system: two fully parallel, independent download managers (one for anime episodes, one for manga chapters), each driven by a WorkManager `CoroutineWorker` foreground job, persisting state to SharedPreferences, and storing files under `<baseStorage>/downloads/<source>/<title>/<item>/`.

## Where it lives

- Root: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/download/`
  - `anime/` — anime episode download pipeline (10 files).
  - `manga/` — manga chapter download pipeline (9 files, mirrors anime minus a part model).
- UI: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/ui/download/`
  - `DownloadsTab.kt` — single Compose tab hosting a 2-page `HorizontalPager` (anime page 0, manga page 1).
  - `anime/` and `manga/` subpackages — per-side screen models, adapters, holders, items.
- DI registration: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` lines ~201-207 (six `addSingletonFactory` calls: 3 anime + 3 manga — Provider, Manager, Cache).
- Cross-cutting:
  - `REFERENCE/domain/src/main/java/tachiyomi/domain/download/service/DownloadPreferences.kt` — shared preference holder for both sides.
  - `REFERENCE/domain/src/main/java/tachiyomi/domain/storage/service/StorageManager.kt` — owns the on-disk base directory and the `downloads/` subdirectory.
- Consumers:
  - Anime player: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt`.
  - Manga reader: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/ChapterLoader.kt`.

## What it does

Downloads anime episodes (as muxed `.mkv` video files via FFmpeg) and manga chapters (as numbered image files or `.cbz` archives) for offline playback/reading. Manages a persistent queue, concurrency across sources, retries, network gating (Wi-Fi only), progress notifications, cancellation, on-disk cache indexing, and deletion (immediate or deferred-pending). Exposes query APIs (`isEpisodeDownloaded`, `isChapterDownloaded`, `getDownloadCount`, `getDownloadSize`) so the player/reader can transparently fall back to local files when an item is offline.

## Key files & classes

### Anime side (`data/download/anime/`)
- `AnimeDownloadManager.kt` — public facade. Queue lifecycle (`startDownloads`, `pauseDownloads`, `clearQueue`), enqueue API (`downloadEpisodes`, `addDownloadsToStartOfQueue`, `startDownloadNow`), deletion (`deleteEpisodes`, `deleteAnime`, `enqueueEpisodesToDelete`, `deletePendingEpisodes`), queries (`isEpisodeDownloaded`, `getDownloadCount`, `getDownloadSize`), and `buildVideo(...)` which materializes a downloaded file back into a `Video` object for the player.
- `AnimeDownloader.kt` — owns the actual queue (`MutableStateFlow<List<AnimeDownload>>`), the coroutine scope (`SupervisorJob + Dispatchers.IO`), the downloader job scheduler, and `downloadEpisode(...)` which pulls hosters via `EpisodeLoader.getHosters`, picks best video via `HosterLoader.getBestVideo`, then either FFmpeg-muxes to `.mkv` or hands off to an external downloader (1DM / ADM). Concurrency: up to 3 sources in parallel (`.take(3)` in `launchDownloaderJob`).
- `AnimeDownloadProvider.kt` — path scheme: `<downloads>/<sourceName>/<animeTitle>/<episodeName>`. Methods: `getAnimeDir`, `findEpisodeDir`, `findEpisodeDirs`, `getEpisodeDirName`, `getValidEpisodeDirNames` (handles old + new name formats), `getSourceDirName`, `getAnimeDirName`. Uses `DiskUtil.buildValidFilename` for sanitization.
- `AnimeDownloadJob.kt` — `CoroutineWorker` (WorkManager). `getForegroundInfo` returns `ForegroundInfo` with `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` on Android Q+. `doWork()` calls `downloadManager.downloaderStart()`, sets foreground, then busy-waits on `!isStopped && isRunning && networkCheck`. Companion `start/stop/isRunning/isRunningFlow` use `enqueueUniqueWork(..., ExistingWorkPolicy.REPLACE, ...)` with tag `"AnimeDownloader"`.
- `AnimeDownloadStore.kt` — persists the live queue across app restarts. SharedPreferences name: `"active_downloads"`. Keyed by `episode.id.toString()`. Stores serialized `{animeId, episodeId, order}`; `restore()` re-fetches anime/episode/source from DB.
- `AnimeDownloadCache.kt` — in-memory + on-disk (ProtoBuf) index of the downloads directory (`RootDirectory → SourceDirectory → AnimeDirectory → episodeDirs`). Renewed every 1 hour or on `StorageManager.changes`. Disk cache file: `context.cacheDir/dl_anime_index_cache_v3`. Skips `_tmp` dirs. Recognizes `.mp4`/`.mkv` files and directories as episodes.
- `AnimeDownloadNotifier.kt` — progress / paused / warning / error notifications on `CHANNEL_DOWNLOADER_PROGRESS` and `CHANNEL_DOWNLOADER_ERROR`. Adds Pause + Show Anime actions.
- `AnimeDownloadPendingDeleter.kt` — defers episode deletion. SharedPreferences `"episodes_to_delete"`. Stores serialized `{episodes, anime}` per anime id; `getPendingEpisodes()` clears and returns them.
- `model/AnimeDownload.kt` — data class `(source: AnimeHttpSource, anime: Anime, episode: Episode, changeDownloader: Boolean, video: Video?)`. Implements `ProgressListener`. State enum: `NOT_DOWNLOADED(0)`, `QUEUE(1)`, `DOWNLOADING(2)`, `DOWNLOADED(3)`, `ERROR(4)`. Exposes `statusFlow` + `progressFlow`.
- `model/AnimeDownloadPart.kt` — represents a byte-range HTTP download part (`range`, `file`, `request`, `listener`, `completed`). Appears unused by the current FFmpeg-only `downloadEpisode` flow in `AnimeDownloader.kt`; likely legacy / reserved for HTTP-range partial downloads. TODO: confirm.

### Manga side (`data/download/manga/`)
- `MangaDownloadManager.kt` — parallel facade for chapters. `downloadChapters`, `buildPageList` (returns `List<Page>` from downloaded image files), `deleteChapters`, `deleteManga`, `renameChapter`, queries, `statusFlow`/`progressFlow`.
- `MangaDownloader.kt` — parallel downloader. Concurrency: up to 5 sources in parallel (`.take(5)`), and within a chapter downloads 2 page images concurrently via `flatMapMerge(concurrency = 2)`. Saves images as zero-padded `001.<ext>`, `002.<ext>`, ... (min 3 digits, scales with page count). Optionally splits tall images (`splitTallImages` pref) and archives to `.cbz` via `ZipWriter` (from `mihon.core.archive`) when `saveChaptersAsCBZ` is on. Writes a `ComicInfo.xml` metadata file. Uses a `Throttler` for `downloadSpeedLimit`.
- `MangaDownloadProvider.kt` — parallel path scheme: `<downloads>/<sourceName>/<mangaTitle>/<chapterName>`. `getValidChapterDirNames` returns `[chapterDirName, chapterDirName.cbz]` (folder vs CBZ).
- `MangaDownloadJob.kt` — parallel `CoroutineWorker`. Tag `"MangaDownloader"`. Same structure as anime.
- `MangaDownloadStore.kt` — parallel store. Also uses SharedPreferences name `"active_downloads"` (see TODOs — potential collision with anime side).
- `MangaDownloadCache.kt` — parallel cache. Disk cache file: `dl_manga_index_cache_v3` (TODO: confirm exact name).
- `MangaDownloadNotifier.kt` — parallel notifier.
- `MangaDownloadPendingDeleter.kt` — parallel pending deleter. Pref name: `"chapters_to_delete"` (TODO: confirm).
- `model/MangaDownload.kt` — `(source: HttpSource, manga: Manga, chapter: Chapter)`. Holds `pages: List<Page>?`. Same State enum.

### UI (`ui/download/`)
- `DownloadsTab.kt` — Compose `Tab` (index 6). `PrimaryTabRow` with two tabs (Anime / Manga), `HorizontalPager` with `rememberPagerState { 2 }`. Per-page FAB toggles pause/start. App bar shows anime download count pill; actions include sort (by upload date / chapter-or-episode number, asc/desc) and "Cancel all".
- `anime/AnimeDownloadQueueScreenModel.kt` — Voyager `ScreenModel`. Subscribes to `downloadManager.queueState`, groups by source, exposes `AnimeDownloadHeaderItem` list. Wraps `startDownloads`/`pauseDownloads`/`clearQueue`/`reorder`/`cancel`. Exposes `isDownloaderRunning` (stateIn of WorkManager flow) + `statusFlow`/`progressFlow`.
- `anime/AnimeDownloadQueueScreen.kt`, `AnimeDownloadQueueTab.kt`, `AnimeDownloadAdapter.kt`, `AnimeDownloadHolder.kt`, `AnimeDownloadHeaderHolder.kt`, `AnimeDownloadItem.kt`, `AnimeDownloadHeaderItem.kt` — RecyclerView-based queue rendering (mixed View + Compose).
- `manga/*` — parallel UI for the manga tab.

## How it works

### Enqueue → queue → execute → store → notify

1. **Enqueue.** A screen (e.g. anime episode list, manga chapter list) calls `AnimeDownloadManager.downloadEpisodes(anime, episodes, autoStart=true, alt=false, video?)` or `MangaDownloadManager.downloadChapters(manga, chapters, autoStart=true)`.
   - Filters out already-downloaded items (`provider.findEpisodeDir(...) == null`).
   - Filters out already-queued items (`queueState.value.none { it.episode.id == episode.id }`).
   - Sorts by `sourceOrder` descending (newest first).
   - Wraps each in `AnimeDownload(source, anime, episode, changeDownloader, video)` / `MangaDownload(source, manga, chapter)`.
   - Calls `addAllToQueue(downloads)` which: marks each as `QUEUE`, persists via `store.addAll(downloads)`, appends to `_queueState`.
   - If `autoStart && wasEmpty`, fires `AnimeDownloadJob.start(context)` / `MangaDownloadJob.start(context)` — a one-time `WorkManager` request enqueued uniquely with `ExistingWorkPolicy.REPLACE`.
   - Fillermark (anime) and category/seen/bookmark filters (manga) are applied at this layer for deletion decisions, not enqueue decisions.

2. **Worker boot.** `AnimeDownloadJob.doWork()`:
   - Checks network state vs `downloadOnlyOverWifi` preference.
   - Calls `downloadManager.downloaderStart()` → `AnimeDownloader.start()` → `launchDownloaderJob()`.
   - If `start()` returns false (queue empty or already running) → `Result.failure()`.
   - `setForegroundSafely()` — promotes the worker to a foreground service with `FOREGROUND_SERVICE_TYPE_DATA_SYNC` (Android Q+) and a sticky download notification.
   - Spins up a coroutine collecting `networkStateFlow + wifiPref.changes` to keep `networkCheck` fresh.
   - Busy-waits: `while (active) { active = !isStopped && downloadManager.isRunning && networkCheck }`. When the queue drains, `isRunning` flips false and the worker returns `Result.success()`.

3. **Queue scheduling.** `AnimeDownloader.launchDownloaderJob()`:
   - Builds `activeDownloadsFlow` from `queueState.transformLatest { queue -> ... }`.
   - Each iteration: takes the queue, filters items at/below `DOWNLOADING` state, `groupBy { it.source }`, takes the first `N` sources (N=3 anime / N=5 manga), picks the first download from each source group, emits the active list.
   - Suspends until any active download enters `ERROR` state, then re-emits (re-evaluates queue).
   - Stops the downloader when no items remain below `DOWNLOADED`.
   - `supervisorScope` collects the flow and maintains a `mutableMapOf<Download, Job>`: cancels jobs no longer active, launches new ones via `launchDownloadJob(download)` (in `launchIO`).
   - Single-threaded queue mutation (main thread); safe concurrent reads.

4. **Per-download execution.**
   - **Anime `downloadEpisode(download)`:**
     - Resolves `animeDir = provider.getAnimeDir(anime.title, source)`.
     - Disk space check: `MIN_DISK_SPACE = 200 MB`. On failure → `ERROR` + notifier.
     - Creates `tmpDir = animeDir.createDirectory(episodeDirname + "_tmp")`.
     - If `download.video == null`, fetches hosters via `EpisodeLoader.getHosters(episode, anime, source)` and best video via `HosterLoader.getBestVideo(source, hosters)`.
     - `getOrDownloadVideoFile`:
       - If a `<filename>.mkv` already exists in tmpDir → reuse.
       - Else, if `useExternalDownloader() == download.changeDownloader` → internal FFmpeg path (`downloadVideo` → `ffmpegDownload`).
       - Else → external downloader path (`downloadVideoExternal`) which sends an `Intent` to 1DM (`idm.internet.download.manager.Downloader`) or ADM (`com.dv.adm.AEditor`), passing video URL + headers + filename; for ADM, marks the download as `DOWNLOADED` immediately and removes from queue (the external app does the actual work).
     - **FFmpeg flow** (`ffmpegDownload`): probes duration with `FFprobeKit`, then `FFmpegKit.executeWithArgumentsAsync` with a command that maps video + audio + subtitle tracks (including external ones from `video.subtitleTracks`/`video.audioTracks`), copies all codecs (`-c:a copy -c:v copy -c:s copy`), outputs to `<filename>.mkv` (matroska container). Progress reported via `StatisticsCallback` → `download.progress = (100 * outTime / duration)`.
     - On success: deletes the `_tmp.mkv` leftover, renames `tmpDir` → `episodeDirname`, registers in `cache.addEpisode(...)`, creates `.nomedia` via `DiskUtil.createNoMediaFile`, sets `download.status = DOWNLOADED`.
     - Removes from queue on `DOWNLOADED`.
   - **Manga `downloadChapter(download)`:**
     - Resolves `mangaDir`, disk-space check (same 200 MB), creates tmpDir.
     - Fetches page list via `source.getPageList(chapter.toSChapter())` (or reuses `download.pages`), re-indexes pages.
     - Applies `DataSaver` (compressor) if `dataSaverDownloader` pref is on.
     - Deletes leftover `.tmp` files in tmpDir.
     - Sets `download.status = DOWNLOADING`.
     - `pageList.asFlow().flatMapMerge(concurrency = 2)`: for each page, fetches image URL if missing (`source.getImageUrl`), then `getOrDownloadImage`:
       - Reuses existing image if present.
       - Else copies from `ChapterCache` if cached (`chapterCache.isImageInCache`).
       - Else `downloadImage` → `source.getImage(page, dataSaver)` → throttled save to `<filename>.tmp` → rename to `<filename>.<ext>` (extension derived from MIME type via `ImageUtil.getExtensionFromMimeType`).
     - Optionally `splitTallImageIfNeeded` (splits long strip images for reader paging).
     - On success: writes `ComicInfo.xml` (`createComicInfoFile`), and either renames tmpDir → chapterDir (raw images) or `archiveChapter(mangaDir, chapterDirname, tmpDir)` → `.cbz` via `ZipWriter`. Registers in cache, creates `.nomedia`, marks `DOWNLOADED`.

5. **Store on disk (layout).**
   ```
   <baseStorage>/downloads/
     <sourceName>/                       # DiskUtil.buildValidFilename(source.toString())
       <animeOrMangaTitle>/              # DiskUtil.buildValidFilename(title)
         <scanlator>_<episodeName>/      # or just <episodeName> if no scanlator
           <title> - <episodeName>.mkv   # anime: single mkv (muxed)
           .nomedia
         <chapterName>.cbz               # manga: optionally archived
         <chapterName>/                  # manga: raw images if not CBZ
           001.jpg
           002.png
           ...
           ComicInfo.xml
   ```
   - `baseStorage` is a UniFile URI from `StoragePreferences.baseStorageDirectory()`.
   - On change of base URI, `StorageManager` recreates `downloads/`, `local/`, `localanime/`, `autobackup/`, `mpv-config/` and emits `changes` which invalidates both download caches.
   - `_tmp` suffix directories are excluded from cache indexing.
   - Manga recognizes both `<chapterDir>` (folder) and `<chapterDir>.cbz` (archive) as valid downloaded chapters via `getValidChapterDirNames`.

6. **Notify.**
   - Progress: `notifier.onProgressChange(download)` posts to `Notifications.ID_DOWNLOAD_EPISODE_PROGRESS` (anime) / `ID_DOWNLOAD_CHAPTER_PROGRESS` (manga). Indeterminate bar at 0%, determinate otherwise. Title format: `<anime title> - <episode name>` (chopped to 30 chars).
   - Pause: `onPaused()` — "Resume" + "Cancel all" actions via `NotificationReceiver`.
   - Complete: `onComplete()` — dismisses progress notification.
   - Warning: `onWarning(reason, timeout?, contentIntent?)` — separate `ID_DOWNLOAD_EPISODE_ERROR` notification, auto-dismiss after timeout (used for queue-size warnings + network reasons).
   - Error: `onError(error, episode, animeTitle, animeId)` — separate error notification, "Show anime" action.
   - Pause/Resume/Clear are also broadcastable via `NotificationReceiver.pauseAnimeDownloadsPendingBroadcast` etc.

### Concurrency summary
- Anime: 3 sources concurrently, 1 episode per source, FFmpeg single-stream mux per episode.
- Manga: 5 sources concurrently, 1 chapter per source, 2 page images concurrent per chapter.
- Both run on a `CoroutineScope(SupervisorJob() + Dispatchers.IO)`; a single `downloaderJob: Job?` per side; cancellation cascades via `supervisorScope` to per-download child jobs.
- Queue size warnings fire at: anime `DOWNLOADS_QUEUED_WARNING_THRESHOLD = 20` total or `EPISODES_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 10` per source; manga parallels with `CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD`.

### Retry
- Both `downloadVideo` (anime) and `downloadImage` (manga) wrap their network/ffmpeg call in a `flow { ... }.retryWhen { _, attempt -> if (attempt < 3) { delay((2L shl attempt) * 1000); true } else false }` — 3 retries with 2s, 4s, 8s exponential backoff.
- Per-attempt failure deletes the partial `.tmp` file before retrying.
- After exhaustion, the throwable propagates → `download.status = ERROR` + `notifier.onError(...)` + `stop()` (anime) or stays in queue as ERROR (manga, which then triggers re-evaluation of `activeDownloadsFlow`).

### Cancellation
- **Pause** (`pause()`): cancels `downloaderJob`, sets all `DOWNLOADING` items back to `QUEUE`. Queue state preserved. Manga additionally tracks `isPaused` flag and emits `onPaused` notification via `stop()`. Anime emits `onPaused` directly in `stop()` if `reason == null` and queue non-empty.
- **Stop** (`stop(reason?)`): cancels `downloaderJob`, sets all `DOWNLOADING` items to `ERROR`, fires `onWarning(reason)` if reason provided else `onPaused`/`onComplete`, then calls `AnimeDownloadJob.stop(context)` → `WorkManager.cancelUniqueWork("AnimeDownloader")`.
- **Clear queue** (`clearQueue()`): cancels job, `internalClearQueue()` (resets `_queueState` to empty, `store.clear()`), dismisses progress notification, then `stop()`.
- **Remove specific items** (`removeFromQueue(episodes)` / `removeFromQueue(anime)`): `removeFromQueueIf { ... }` updates `_queueState`, calls `store.removeAll(downloads)`, resets matching items' status to `NOT_DOWNLOADED`.
- Network-gated stop: `AnimeDownloadJob.checkNetworkState` calls `downloadManager.downloaderStop(reason)` when Wi-Fi required but unavailable, or when offline.

### Storage of queue state across restarts
- `AnimeDownloadStore` / `MangaDownloadStore`: SharedPreferences `"active_downloads"`, key = `episode.id.toString()` / `chapter.id.toString()`, value = JSON `{animeId, episodeId, order}` or `{mangaId, chapterId, order}`. Counter preserves queue order.
- On `AnimeDownloader.init` / `MangaDownloader.init`: `scope.launch { val items = async { store.restore() }; addAllToQueue(items.await()) }`. `restore()` clears the prefs (re-added immediately by `addAllToQueue`).
- Note: both stores use the **same** SharedPreferences file name `"active_downloads"` — see TODOs.

### Cache (downloads directory index)
- `AnimeDownloadCache` / `MangaDownloadCache`: avoids expensive `UniFile.listFiles()` calls by keeping an in-memory tree (`RootDirectory → SourceDirectory → AnimeDirectory → episodeDirs`).
- Persisted to `context.cacheDir/dl_anime_index_cache_v3` (anime) via `ProtoBuf.encodeToByteArray`. Reloaded on init.
- Refresh policy: every 1 hour (`renewInterval = 1.hours`), or immediately on `storageManager.changes` (e.g. user changes base storage dir), or on `invalidateCache()`.
- Wait for `extensionManager.isInitialized` + `sourceManager.isInitialized` (30s timeout) before first rebuild so source-name → source-id mapping is available.
- Exposes `changes: Flow<Unit>` for UI reactivity.

### How the player/reader consumes downloaded files
- **Anime player** (`EpisodeLoader`):
  - `EpisodeLoader.isDownload(episode, anime)` → `AnimeDownloadManager.isEpisodeDownloaded(..., skipCache = true)` (filesystem probe, bypasses cache).
  - `getHosters(episode, anime, source)`: if downloaded → `getHostersOnDownloaded` → `downloadManager.buildVideo(source, anime, episode)` finds the episode dir, lists files filtered by `"video" in file.type`, takes the first, returns `Video(videoUrl = file.uri.toString(), videoTitle = "download: ...", initialized = true)` with `status = Video.State.READY`. Wrapped in a single-element `Hoster` list.
  - Player then plays the local URI directly (no network fetch).
- **Manga reader** (`ChapterLoader.getPageLoader`):
  - `downloadManager.isChapterDownloaded(..., skipCache = true)`.
  - If true → `DownloadPageLoader(chapter, manga, source, downloadManager, downloadProvider)`, which calls `downloadManager.buildPageList(source, manga, chapter)` to enumerate image files in the chapter dir and wraps them as `Page(index, uri = file.uri)` with `status = Page.State.READY`.
  - Reader then loads page images from local URIs.

## Dependencies

### What it depends on
- **SOURCE-SYSTEM** (anime source / manga source hierarchy):
  - `AnimeHttpSource` / `HttpSource` — provides `getHosterList` / `getVideoList` / `getPageList` / `getImageUrl` / `getImage`.
  - `AnimeSourceManager` / `MangaSourceManager` — source lookup for enqueue + cache rebuild.
  - `HosterLoader` + `EpisodeLoader.getHosters` (anime) — best-video resolution.
  - `LocalAnimeSource` / `LocalMangaSource` — `getDownloadCount`/`getDownloadSize` short-circuit for local-source entries (uses `LocalAnimeSourceFileSystem` / `LocalMangaSourceFileSystem`).
- **CORE-ARCHIVE** (`mihon.core.archive`):
  - `ZipWriter` — used by `MangaDownloader.archiveChapter` to produce `.cbz` files.
  - Archive readers (`ArchivePageLoader`, `archiveReader`) used on the reader-consume side for local-source `.cbz`/`.epub`, not by the downloader itself.
- **DATA-LAYER** (`domain` module):
  - `tachiyomi.domain.entries.{anime,manga}.interactor.GetAnime/GetManga`, `tachiyomi.domain.items.{episode,chapter}.interactor.GetEpisode/GetChapter` — used by `DownloadStore.restore()` and `AnimeDownload.fromEpisodeId` / `MangaDownload.fromChapterId`.
  - `tachiyomi.domain.category.{anime,manga}.interactor.GetAnimeCategories/GetMangaCategories` — used by `getEpisodesToDelete` / `getChaptersToDelete` for category-based delete exclusions.
  - `tachiyomi.domain.storage.service.StorageManager` — base + downloads directory.
  - `tachiyomi.domain.download.service.DownloadPreferences` — wifi-only, external downloader, CBZ save, speed limit, auto-download, remove-after-read, fillermark, bookmark, category exclusions, new-episode auto-download.
  - `tachiyomi.domain.track.manga.interactor.GetMangaTracks` — used by manga downloader for `ComicInfo.xml` metadata (TODO: confirm).
- **EXTENSIONS / native deps** (anime only):
  - `com.arthenica.ffmpegkit.*` — `FFmpegKit`, `FFprobeKit`, `FFmpegKitConfig` for video muxing and duration probing.
- **Networking / caching**:
  - `okhttp3.Response`, `okio.Throttler` (manga image throttling).
  - `ChapterCache` (`eu.kanade.tachiyomi.data.cache.ChapterCache`) — manga image cache, used to skip re-download when image already cached.
- **Persistence / serialization**:
  - `kotlinx.serialization` (`Json`, `ProtoBuf`, `@Serializable`) — store + cache.
  - `androidx.core.content.edit` (SharedPreferences) — store + pending deleter.
- **WorkManager**:
  - `androidx.work.CoroutineWorker`, `OneTimeWorkRequestBuilder`, `WorkManager`, `ForegroundInfo`, `ExistingWorkPolicy.REPLACE`, `WorkInfo`.
- **UI / Voyager** (UI side only):
  - `cafe.adriel.voyager.*` — `ScreenModel`, `Tab`, `Screen`.
  - Compose + RecyclerView hybrid (`DownloadListBinding`, adapters, holders).
- **DI**:
  - `uy.kohesive.injekt.Injekt` — singletons registered in `AppModule.kt`.

### What depends on it
- **Episode list / chapter list screens** — call `downloadEpisodes` / `downloadChapters`, `deleteEpisodes` / `deleteChapters`, query `isEpisodeDownloaded` / `isChapterDownloaded` for UI badges, and `startDownloadNow` for "download now" actions.
- **Anime player (`EpisodeLoader`)** — uses `isEpisodeDownloaded` + `buildVideo` for offline playback.
- **Manga reader (`ChapterLoader`)** — uses `isChapterDownloaded` + `DownloadPageLoader` for offline reading.
- **Library update job** (`AnimeLibraryUpdateNotifier`, `MangaLibraryUpdateNotifier`) — references `HELP_WARNING_URL` for queue-size warnings (loose coupling).
- **Domain interactors** (`DeleteEpisodeDownload`, `DeleteChapterDownload`, `SyncEpisodesWithSource`, `SyncChaptersWithSource`, `EpisodeFilter`, `ChapterFilter`) — invoke manager methods for cleanup during library sync and for downloaded-state filters.
- **Notification receivers** (`NotificationReceiver`) — pause / resume / clear broadcasts.
- **Extension/Source rename flows** — `renameSource(oldSource, newSource)` keeps disk folders in sync when a source's display name changes; `renameEpisode` / `renameChapter` for per-item renames.

## Anime vs manga

**Split: fully parallel, NOT unified.** There is no common `DownloadManager` base class, no shared `Downloader`, no shared `DownloadProvider`. Each side has its own complete pipeline:

| Concept | Anime | Manga |
|---|---|---|
| Manager | `AnimeDownloadManager` | `MangaDownloadManager` |
| Downloader | `AnimeDownloader` | `MangaDownloader` |
| Provider | `AnimeDownloadProvider` | `MangaDownloadProvider` |
| Cache | `AnimeDownloadCache` | `MangaDownloadCache` |
| Worker | `AnimeDownloadJob` | `MangaDownloadJob` |
| Store | `AnimeDownloadStore` | `MangaDownloadStore` |
| Notifier | `AnimeDownloadNotifier` | `MangaDownloadNotifier` |
| Pending deleter | `AnimeDownloadPendingDeleter` | `MangaDownloadPendingDeleter` |
| Model | `AnimeDownload` (+ `AnimeDownloadPart`) | `MangaDownload` |
| DI | `addSingletonFactory { AnimeDownloadManager(app) }` | `addSingletonFactory { MangaDownloadManager(app) }` |
| WorkManager tag | `"AnimeDownloader"` | `"MangaDownloader"` |
| UI tab | `animeDownloadTab` (page 0) | `mangaDownloadTab` (page 1) |

**Shared across both sides:**
- `DownloadPreferences` (single preference holder; wifi-only, speed-limit, auto-download, remove-after-read — all keys shared; anime-only or manga-only prefs are separate keys).
- `StorageManager.getDownloadsDirectory()` — both sides write into the SAME `downloads/` root, distinguished only by source-name subdirectory (which is unique per source). This is safe because source IDs differ between anime and manga source managers (TODO: confirm), but a manga source named identically to an anime source could collide on disk (TODO: confirm — `getSourceDirName` uses `source.toString()` which includes the source's display name, not its ID).

### How anime downloads differ from manga downloads

- **Output format:**
  - Anime: a single muxed `.mkv` file per episode (video + audio + subtitle streams copied). Filename: `<title> - <episode>.mkv`.
  - Manga: either a directory of numbered image files (`001.jpg`, `002.png`, ...) OR a `.cbz` archive containing those images + `ComicInfo.xml`. Filename/dirname: `<scanlator>_<chapterName>` or `<chapterName>`.

- **Acquisition mechanism:**
  - Anime: `EpisodeLoader.getHosters` → `HosterLoader.getBestVideo` → FFmpeg pulls the video URL (with HTTP headers from source/video) and muxes streams into `.mkv`. Alternatively, hands the URL off to an external downloader app (1DM, ADM) via `Intent`.
  - Manga: `source.getPageList(chapter)` returns page metadata; per page, `source.getImageUrl(page)` then `source.getImage(page, dataSaver)` returns the image bytes; written to disk as numbered files. Optional `DataSaver` wrapper compresses images on the fly.

- **Concurrency:**
  - Anime: 3 sources parallel, 1 episode per source (FFmpeg is single-threaded per episode).
  - Manga: 5 sources parallel, 1 chapter per source, 2 page images concurrent within a chapter.

- **Progress tracking:**
  - Anime: `progress` derived from FFmpeg `StatisticsCallback.time / duration`. Coarse.
  - Manga: `progress` = average of all page progresses (`pages.map(Page::progress).average()`), debounced 50ms.

- **Special features:**
  - Anime: external downloader integration (1DM/ADM), FFmpeg codec-copy muxing, multi-stream handling (audio + subtitle tracks), byte-range part model (`AnimeDownloadPart` — appears unused in current flow).
  - Manga: `.cbz` archiving via `ZipWriter`, `ComicInfo.xml` metadata generation, `ChapterCache` reuse, `DataSaver` compression, `splitTallImages` post-processing, `Throttler` speed limit.

- **Both:** same retry policy (3 attempts, 2/4/8s backoff), same 200 MB min disk space, same queue/store/cache/notifier architecture, same WorkManager foreground worker pattern.

### Are they coupled?

**No.** There is zero code-level coupling between the anime and manga download pipelines:
- No shared interface, no shared base class, no shared state.
- The only shared dependencies are `DownloadPreferences`, `StorageManager`, `Injekt` (DI), and the `data/notification/` infrastructure.
- UI is weakly coupled only at the `DownloadsTab` Compose level (a 2-page pager hosting both side-by-side); each page is fully independent.

**Yes, anime-only could be shipped cleanly.** Deleting the `data/download/manga/` and `ui/download/manga/` packages, removing the 3 manga `addSingletonFactory` lines from `AppModule.kt`, and stripping the manga tab from `DownloadsTab.kt` would leave a fully functional anime-only download manager. The only shared concern is `DownloadPreferences` (which contains both anime and manga keys — the unused manga keys would simply be inert prefs). TODO: confirm no other call sites (e.g. `MangaDownloadManager` imports in `ChapterLoader`, `SyncChaptersWithSource`, `DeleteChapterDownload`, `ChapterFilter`) would need to be removed — those are call sites in the manga reader/library code, which would also be removed in an anime-only build.

## Relationships

- **SOURCE-SYSTEM** — download manager is the primary consumer of `AnimeHttpSource`/`HttpSource` page/video fetch APIs. It also depends on `AnimeSourceManager`/`MangaSourceManager` for source resolution during enqueue, cache rebuild, and `buildVideo`/`buildPageList`. `LocalAnimeSource`/`LocalMangaSource` short-circuit count/size queries.
- **PLAYER (anime)** — `EpisodeLoader.isDownload` + `EpisodeLoader.getHostersOnDownloaded` + `AnimeDownloadManager.buildVideo` form the offline-playback path. The player plays the local file URI directly.
- **READER (manga)** — `ChapterLoader.getPageLoader` + `DownloadPageLoader` + `MangaDownloadManager.buildPageList` form the offline-reading path. The reader loads page image URIs directly.
- **CORE-ARCHIVE** — `ZipWriter` (manga downloader) produces `.cbz` archives. On the consume side, `ArchivePageLoader` reads `.cbz` for local-source manga (not directly invoked by the downloader, but the format the downloader emits).
- **DATA-LAYER** — `GetAnime`/`GetManga`/`GetEpisode`/`GetChapter` restore downloads from store; `GetAnimeCategories`/`GetMangaCategories` drive delete-exclusion logic; `DownloadPreferences` + `StorageManager` configure behavior; `StorageManager.changes` invalidates the cache.

Cross-references: see SOURCE-SYSTEM.md (source/extension plugin boundary), PLAYER.md (offline playback), READER.md (offline reading), CORE-ARCHIVE.md (`ZipWriter`, archive readers), DATA-LAYER.md (download preferences, storage, interactors). TODO: link to those docs once written.

## Notes for our build (anime-first)

For an anime-only build, the minimum viable download manager is the anime side as-is:

- **Keep:** `AnimeDownloadManager`, `AnimeDownloader`, `AnimeDownloadProvider`, `AnimeDownloadCache`, `AnimeDownloadJob`, `AnimeDownloadStore`, `AnimeDownloadNotifier`, `AnimeDownloadPendingDeleter`, `AnimeDownload` model.
- **Drop:** entire `data/download/manga/` and `ui/download/manga/` packages; the 3 manga `addSingletonFactory` calls in `AppModule.kt`; the manga tab in `DownloadsTab.kt` (collapse `HorizontalPager` to a single page, or render `AnimeDownloadQueueScreen` directly).
- **Audit call sites:** `ChapterLoader`, `SyncChaptersWithSource`, `DeleteChapterDownload`, `ChapterFilter`, `MangaDownloadManager` import in `EpisodeOptionsDialogScreen` (anime side — likely a false positive from grep; verify) — all manga-side consumers must be removed with the manga code path.
- **Preferences cleanup:** `DownloadPreferences` can stay as-is (inert manga keys are harmless) or be slimmed to anime-only keys (`downloadOnlyOverWifi`, `useExternalDownloader`, `externalDownloaderSelection`, `autoDownloadWhileWatching`, `removeAfterReadSlots`, `removeAfterMarkedAsRead`, `removeBookmarkedChapters`, `downloadFillermarkedItems`, `removeExcludeAnimeCategories`, `downloadNewEpisodes`, `downloadNewEpisodeCategories`, `downloadNewEpisodeCategoriesExclude`, `numberOfDownloads`, `downloadNewUnseenEpisodesOnly`). Drop the manga-only keys (`saveChaptersAsCBZ`, `splitTallImages`, `autoDownloadWhileReading`, `downloadSpeedLimit` — actually `downloadSpeedLimit` is manga-only via `Throttler`; anime uses FFmpeg, no throttler), `removeExcludeCategories`, `downloadNewChapters*`, `downloadNewUnreadChaptersOnly`.
- **FFmpeg dependency:** the anime side requires `com.arthenica:ffmpeg-kit-*` (full or video variant). This is a large native dependency (~30-60 MB per ABI). Confirm licensing + APK size budget.
- **External downloader integration:** decide whether to keep 1DM/ADM Intent hand-off or simplify to FFmpeg-only.
- **AnimeDownloadPart:** investigate whether this byte-range part model is wired anywhere (HTTP partial download). If unused, drop it to reduce surface area.
- **Storage:** confirm `baseStorage` URI flow + SAF (Storage Access Framework) permissions for the user-chosen downloads directory. Both anime + manga currently share `<base>/downloads/`; anime-only is unaffected.
- **WorkManager unique-work tag:** keep `"AnimeDownloader"` or rename to `"AnikutaDownloader"` for clarity.
- **Cache file naming:** `dl_anime_index_cache_v3` — consider renaming to `dl_index_cache` since versioning is no longer needed for a fresh project.
- **SharedPreferences `"active_downloads"` collision:** in aniyomi this name is shared between anime and manga stores (latent bug if episode and chapter IDs ever overlap). For anime-only, this is a non-issue — but if we ever keep both, rename to `active_anime_downloads` / `active_manga_downloads`.

## TODOs / open questions

- **`AnimeDownloadPart` usage:** confirm whether the byte-range HTTP-part model is referenced anywhere outside its own file. Grep found only the model file itself; no call sites in `AnimeDownloader.kt`. Likely dead code or planned feature. TODO: trace `AnimeDownloadPart` imports across the repo to confirm.
- **`MangaDownloadCache` disk file name:** I read `AnimeDownloadCache` uses `dl_anime_index_cache_v3`. The manga side was not fully read; it almost certainly uses `dl_manga_index_cache_v3` by symmetry, but TODO: confirm by reading `MangaDownloadCache.kt`.
- **`MangaDownloadPendingDeleter` pref name:** not read; likely `"chapters_to_delete"` by symmetry with anime's `"episodes_to_delete"`. TODO: confirm.
- **`SharedPreferences` "active_downloads" collision:** both `AnimeDownloadStore` and `MangaDownloadStore` use the same SharedPrefs file name `"active_downloads"`. Keys are `episode.id.toString()` vs `chapter.id.toString()`. If episode and chapter IDs come from separate SQLDelight tables with independent auto-increment, IDs could numerically overlap (e.g. episode id=5 and chapter id=5 both key to `"5"`), causing cross-pollution of the queue on app restart. Is this a latent bug in aniyomi, or are IDs globally unique? TODO: check the SQLDelight schema for `anime_episodes` and `manga_chapters` ID generation.
- **Source-name disk collision:** anime and manga source managers are separate; if a user has both an anime source and a manga source with identical `toString()` names, their downloads would land in the same `<sourceName>/` directory. Is source display name guaranteed unique across managers? TODO: verify.
- **`take(3)` vs comment "5 different sources":** the anime `launchDownloaderJob` has a comment "Concurrently download from 5 different sources" but the code says `.take(3)`. Likely a stale comment. TODO: note as aniyomi upstream inconsistency; pick one for our build.
- **`DownloadPreferences.downloadSpeedLimit`:** used only by manga (`Throttler`). Anime FFmpeg path has no throttling. Decide whether anime needs bandwidth limiting (FFmpeg `-limitrate` could do it, but codec-copy can't limit rate). TODO: product decision.
- **`GetMangaTracks` import in `MangaDownloader`:** confirm role (ComicInfo metadata for tracker tags). TODO: read `createComicInfoFile` for the exact fields populated.
- **`ensureSuccessfulAnimeDownload` in `AnimeDownloader`:** defined but not called from `downloadEpisode` (which does its own rename + cache add). Possibly dead. TODO: trace callers.
- **`EpisodeOptionsDialogScreen` imports `AnimeDownloadManager`:** verify whether this is anime-side or a mis-categorized import (grep hit). TODO: confirm.
- **Notification channel + IDs:** `Notifications.ID_DOWNLOAD_EPISODE_PROGRESS` vs `ID_DOWNLOAD_CHAPTER_PROGRESS` and `ID_DOWNLOAD_EPISODE_ERROR` — confirm these are distinct integers and don't collide. TODO: read `Notifications.kt`.
- **Reader/Player docs cross-link:** TODO: link to PLAYER.md, READER.md, SOURCE-SYSTEM.md, CORE-ARCHIVE.md, DATA-LAYER.md once those subsystem docs exist in this folder.
