# 03-subsystems / download-manager — Downloading chapters & episodes

> Background download of manga chapters (as page images, optionally
> archived as CBZ) and anime episodes (as `.mkv` video files, optionally
> via Torrserver for torrents). Two near-identical pipelines exist, one
> per side of the manga/anime dual.

## Purpose

Aniyomi downloads content for offline use:

- **Manga** — each chapter becomes either a directory of `NNN.jpg` page
  images or a single `.cbz` archive (with an embedded `ComicInfo.xml`).
- **Anime** — each episode becomes a single `.mkv` file (multiplexed
  video + audio + subtitle tracks via FFmpeg), or a magnet/torrent stream
  downloaded through Torrserver.

Both sides share the same overall shape:

```
   UI tap "Download"
        │
        ▼
   *DownloadManager.downloadChapters/Episodes(manga, chapters)
        │  builds *Download(source, manga, chapter) objects
        ▼
   *Downloader.queueChapters(...)              ← filters out already-downloaded
        │  appends to _queueState (StateFlow<List<*Download>>)
        ▼
   *DownloadJob.start(context)                 ← WorkManager one-time work
        │  getForegroundInfo() → progress notification
        ▼
   *Downloader.start()
        │  for each active download:
        │    launchDownloadJob(download)
        │      downloadChapter/Episode(download)
        │        *DownloadProvider.findMangaDir/getMangaDir(...)
        │        *DownloadCache.addChapter/Episode(...)
        │        *DownloadNotifier.onProgressChange(download)
        │      removeFromQueue(download)  on success
        ▼
   WorkManager.cancelUniqueWork(TAG)  on pause / stop / queue empty
```

## The dual pipeline

| Concern                  | Manga                                       | Anime                                       |
|--------------------------|---------------------------------------------|---------------------------------------------|
| Manager class            | `MangaDownloadManager`                      | `AnimeDownloadManager`                      |
| Worker class             | `MangaDownloader`                           | `AnimeDownloader`                           |
| Download model           | `MangaDownload` (carries `List<Page>`)      | `AnimeDownload` (carries `Video?`)          |
| WorkManager job          | `MangaDownloadJob` (tag `"MangaDownloader"`) | `AnimeDownloadJob` (tag `"AnimeDownloader"`) |
| Provider                 | `MangaDownloadProvider`                     | `AnimeDownloadProvider`                     |
| Cache                    | `MangaDownloadCache`                        | `AnimeDownloadCache`                        |
| Store                    | `MangaDownloadStore` (SharedPreferences)    | `AnimeDownloadStore` (SharedPreferences)    |
| Notifier                 | `MangaDownloadNotifier`                     | `AnimeDownloadNotifier`                     |
| Pending deleter          | `MangaDownloadPendingDeleter`               | `AnimeDownloadPendingDeleter`               |
| Concurrency per source   | 5 sources                                   | 3 sources                                   |
| File produced            | `NNN.jpg` (or `.cbz`)                       | `<filename>.mkv`                            |
| Auxiliary libraries      | `ZipWriter`, `ImageUtil`                    | `FFmpegKit`, `FFprobeKit`, `TorrentServerService` |

The two pipelines are independent and run in parallel — a user can be
downloading a manga chapter and an anime episode simultaneously.

## The download queue and states

Source: `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/{manga,anime}/model/*Download.kt`.

Both `MangaDownload` and `AnimeDownload` are `data class`es with the same
state machine:

```
enum class State(val value: Int) {
    NOT_DOWNLOADED(0),
    QUEUE(1),
    DOWNLOADING(2),
    DOWNLOADED(3),
    ERROR(4),
}
```

State is exposed both as a `var status: State` and as a
`StateFlow<State> statusFlow` (backed by a `MutableStateFlow` — kept
`@Transient` so the download can be serialised by the store). The
`MangaDownload` also exposes `progressFlow`, which combines all page
`progressFlow`s into a single 0..100 integer. The `AnimeDownload`
exposes a `progressFlow` that's a separate `MutableStateFlow<Int>`,
updated by an FFmpeg statistics callback (`StatisticsCallback`) that
maps `s.time / duration` to a percentage.

```
                     enqueue               start()
   NOT_DOWNLOADED ─────────► QUEUE ──────────────► DOWNLOADING
        ▲                       │                      │
        │                       │ pause()/stop()       │ success
        │                       ▼                      ▼
        └─────────────────── QUEUE ◄────────────── DOWNLOADED
                                                (removed from queue)
                                                    │
                                                    │ failure / cancel
                                                    ▼
                                                  ERROR
```

The queue itself is a `MutableStateFlow<List<*Download>>` named
`_queueState`. All mutations go through `_queueState.update { ... }` so
collectors see consistent snapshots. The store is updated in lock-step
with the in-memory queue so the queue survives app restarts.

### Concurrency

```
val activeDownloads = queue.asSequence()
    .filter { it.status.value <= State.DOWNLOADING.value }   // ignore completed
    .groupBy { it.source }                                    // per-source groups
    .toList().take(N)                                         // top N sources
    .map { (_, downloads) -> downloads.first() }             // first download per source
```

`N` is **5** for manga and **3** for anime (the anime-side code comment
still says "5" but the `take(3)` is what's actually executed). The
downloader then launches a child coroutine per active download and
suspends on a `combine(...) { states -> states.contains(ERROR) }` flow
until *any* active download errors, at which point it recomputes the
active set. Cancelled downloads free their slot.

## Manga downloads

Source: `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloader.kt`.

The single entry point of interest is `downloadChapter(download)`:

```
1. getMangaDir(download.manga.title, download.source)         ← *DownloadProvider
2. availSpace = DiskUtil.getAvailableStorageSpace(mangaDir)
   if (availSpace < MIN_DISK_SPACE) → ERROR, notifier.onError
3. tmpDir = mangaDir.createDirectory("<chapter>_tmp")
4. If download.pages == null:
     pages = download.source.getPageList(chapter.toSChapter())
     download.pages = pages.mapIndexed { i, p -> Page(i, p.url, p.imageUrl, p.uri) }
       (don't trust the source's own indexing — re-index)
5. dataSaver = if (sourcePreferences.dataSaverDownloader().get())
                  DataSaver(source, prefs) else DataSaver.NoOp
6. download.status = DOWNLOADING
7. pages.asFlow().flatMapMerge(concurrency = 2) { page →        ← 2 pages in flight
       if (page.imageUrl == null) page.imageUrl = source.getImageUrl(page)
       getOrDownloadImage(page, download, tmpDir, dataSaver)
   }.collect { notifier.onProgressChange(download) }
8. if (!isDownloadSuccessful(download, tmpDir)) → ERROR
9. createComicInfoFile(tmpDir, manga, chapter, source)         ← ComicInfo.xml
10. if (downloadPreferences.saveChaptersAsCBZ().get())
       archiveChapter(mangaDir, chapterDirname, tmpDir)        ← ZipWriter → .cbz
    else
       tmpDir.renameTo(chapterDirname)
11. cache.addChapter(chapterDirname, mangaDir, manga)
12. DiskUtil.createNoMediaFile(tmpDir, context)                 ← .nomedia
13. download.status = DOWNLOADED
```

### Per-page download (`getOrDownloadImage` / `downloadImage`)

For each page:

1. Compute the filename as zero-padded `NNN` with at least 3 digits
   (or more, if the chapter has >999 pages).
2. Skip if the image file already exists on disk (resume from where a
   previous run left off).
3. If the image is in the `ChapterCache` (page-image disk cache used by
   the reader), copy it from cache to the tmp dir instead of re-downloading.
4. Otherwise call `source.getImage(page, dataSaver)` — the `HttpSource`
   implementation returns an OkHttp `Response`. The response body is
   streamed through an Okio `Throttler` (rate-limited by
   `downloadPreferences.downloadSpeedLimit().get()` KB/s) into
   `<filename>.tmp`.
5. The file extension is determined from the response's MIME type, or by
   sniffing the file's magic bytes, defaulting to `jpg`. The `.tmp` file
   is renamed to `<filename>.<ext>`.
6. If `downloadPreferences.splitTallImages().get()`, the image is run
   through `ImageUtil.splitTallImage` which slices very tall images
   (typical of webtoons) into multiple `<NNN>__001.jpg`,
   `<NNN>__002.jpg`, ... files for smoother paging.
7. The page's `status` is set to `Page.State.READY` and its `uri` is
   set to the file URI — the reader uses these later to render.

### Retry logic

`downloadImage` wraps the OkHttp call in a `flow { ... }.retryWhen { _, attempt ->
if (attempt < 3) { delay((2L shl attempt.toInt()) * 1000); true } else false }`.
So a page that fails to download is retried up to 3 times with
exponentially growing delays: 2 s, 4 s, 8 s. A page that exhausts its
retries is marked `Page.State.ERROR`; the chapter download continues for
the remaining pages (a partial chapter is still saved, and the user can
re-download the missing pages from the chapter screen).

### CBZ archive + ComicInfo.xml

If `saveChaptersAsCBZ()` is enabled (default: yes), `archiveChapter`
walks every file in `tmpDir` and writes them into a fresh `.cbz` (a ZIP
with `STORE` compression — already-compressed image data) using
`mihon.core.archive.ZipWriter`. The `.cbz` is renamed from
`<chapter>.cbz_tmp` to `<chapter>.cbz` once closed, and `tmpDir` is
deleted.

Before archiving, `createComicInfoFile` writes a `ComicInfo.xml` into
`tmpDir` based on `getComicInfo(manga, chapter, urls, categories, sourceName)`
(see [`../02-modules/core-metadata.md`](../02-modules/core-metadata.md)).
This file follows the [ComicInfo v2.0 schema](https://anansi-project.org/docs/comicinfo/schemas/v2.0)
and lets external comic readers (KOReader, Komga, Kavita) display
metadata correctly. The `<Pages>` element is not populated (Aniyomi
writes the schema but lets the reader compute page dimensions).

### `MangaDownloadNotifier`

Posts four kinds of notification:

| Method              | Channel                            | Trigger                                                    |
|---------------------|------------------------------------|------------------------------------------------------------|
| `onProgressChange`  | `CHANNEL_DOWNLOADER_PROGRESS`      | Every page download completion. Shows `<title> - <chapter>` and `downloadedImages / pages` progress. Has Pause + Show Manga actions. |
| `onPaused`          | `CHANNEL_DOWNLOADER_PROGRESS`      | All downloads paused. Shows Resume + Clear actions.         |
| `onComplete`        | (dismisses progress notification)  | Queue drained successfully.                                |
| `onWarning`         | `CHANNEL_DOWNLOADER_ERROR`         | Network dropped, only-Wifi policy violated, or queue-size warning. Auto-dismisses after `WARNING_NOTIF_TIMEOUT_MS`. |
| `onError`           | `CHANNEL_DOWNLOADER_ERROR`         | A page failed all retries, or the chapter download threw.  |

Respects `SecurityPreferences.hideNotificationContent()` — when set, the
progress notification shows only the `X / Y` count and no title/text.

## Anime downloads

Source: `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloader.kt`.

The anime side is more complex because of the three download strategies
(direct / FFmpeg / torrent) and the external-downloader hand-off.

```
downloadEpisode(download):
1. getAnimeDir(download.anime.title, download.source)
2. availSpace check (same MIN_DISK_SPACE = 200 MB)
3. tmpDir = animeDir.createDirectory("<episode>_tmp")
4. if (download.video == null):
     hosters = EpisodeLoader.getHosters(episode, anime, source)  ← ext-lib-16
     bestVideo = HosterLoader.getBestVideo(source, hosters)       ← picks first usable
     download.video = bestVideo
5. getOrDownloadVideoFile(download, tmpDir)
6. if (!isDownloadSuccessful(download, tmpDir)) → ERROR
7. tmpDir.renameTo(episodeDirname)
8. cache.addEpisode(episodeDirname, animeDir, anime)
9. DiskUtil.createNoMediaFile(tmpDir, context)
10. download.status = DOWNLOADED
```

### Video fetch strategy (`getOrDownloadVideoFile` / `downloadVideo`)

There are two top-level strategies, switched by
`preferences.useExternalDownloader().get() == download.changeDownloader`:

- **Internal** (default) → `downloadVideo`:
  - If `torrentPreferences.torrServerEnable().get()` AND the video URL
    looks like a torrent (starts with `magnet`, ends with `.torrent`, or
    is already a Torrserver URL) → `torrentDownload`:
    1. `TorrentServerService.start()` (foreground service, starts
       Torrserver on the configured port — see
       [`torrent-streaming.md`](torrent-streaming.md)).
    2. If the URL was a Torrserver stream URL, decode `link=` / `index=`
       back into a magnet URI.
    3. `torrentServerApi.addTorrent(magnet, title, ...)` → returns a
       `Torrent` with `hash` + `fileStats`.
    4. `torrentServerUtils.getTorrentPlayLink(torrent, index)` → an
       `http://127.0.0.1:<port>/stream/...?link=<hash>&index=<n>&play`
       URL that Torrserver serves.
    5. Replace `video.videoUrl` with that URL and fall through to
       `ffmpegDownload` (Torrserver presents an HTTP stream, so FFmpeg
       can just demux it).
  - Otherwise → `ffmpegDownload` directly.
- **External** → `downloadVideoExternal`: hands the URL off to a
  user-selected external downloader (1DM or ADM) via a per-app intent.
  Aniyomi doesn't actually download the bytes — it just launches the
  external app with the URL, headers, and a target filename, and
  immediately marks the download as completed. The user is responsible
  for moving the file into Aniyomi's download directory afterwards.

### `ffmpegDownload` — the actual transcode/mux

Source: lines 551–615 of `AnimeDownloader.kt`.

```
val headerOptions = (video.headers ?: source.headers)
    .joinToString("", "-headers '", "'") { "${it.first}: ${it.second}\r\n" }

duration = getDuration(ffprobeCommand(video.videoUrl, headerOptions))?.toLong() ?: 0L
                                                                       ← via FFprobeKit

session = FFmpegKit.executeWithArgumentsAsync(
    getFFmpegOptions(video, headerOptions, ffmpegFilename),  ← builds the CLI array
    completeCallback,                                         ← renames .tmp → .mkv on success
    logCallback,                                              ← WARNING+ → logcat ERROR
    statCallback,                                             ← updates download.progress
)
```

The `ffmpegOptions` builder constructs a command that:

- Adds the source's headers (so cookies / referer work for HTTP inputs).
- Adds `-i <videoUrl>` plus an `-i <subtitleUrl>` for every subtitle
  track and an `-i <audioUrl>` for every audio track (the `Video` model
  can carry multiple alternative tracks — see `source-api.md`).
- Maps video, audio, and subtitle streams (`-map 0:v`, `-map 0:a?`,
  `-map 0:s?`, `-map 0:t?`, plus extra maps for each added input).
- Sets `-f matroska -c:a copy -c:v copy -c:s copy` — this is a
  **remux**, not a re-encode. The video and audio bytes are copied
  untouched; only the container changes (to `.mkv`). This is fast and
  lossless.
- Adds per-track metadata `-metadata:s:<type>:<i> title=<lang>`.
- Appends the source's own FFmpeg args (`video.ffmpegStreamArgs`,
  `video.ffmpegVideoArgs`) — sources can request e.g. `-rtsp_transport
  tcp` or `-user_agent ...` here.

Progress is reported by the `StatisticsCallback`: FFmpeg calls it with
`s.time` (microseconds of output written so far); the callback divides
by `duration` (seconds) to compute `download.progress = (100 * outTime / duration).toInt()`.

### `stopHttpServer`

If the source set up a local `NanoHTTPD` (the ext-lib-17
`AnimeHttpSource.server` field — see `source-api.md`), it is stopped
after the download completes (or fails) so the local port is freed.

## Storage layout

Source: `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/{manga,anime}/*DownloadProvider.kt`.

```
<root downloads dir>/                  ← StorageManager.getDownloadsDirectory() (SAF tree-uri)
├── <source name>/                     ← DiskUtil.buildValidFilename(source.toString())
│   ├── <manga title>/                 ← DiskUtil.buildValidFilename(title)
│   │   ├── <chapter>/                 ← getChapterDirName(name, scanlator)
│   │   │   ├── 001.jpg                ← pages (.jpg/.png/.gif/.webp by sniffed MIME)
│   │   │   ├── 002.jpg
│   │   │   ├── 010__001.jpg           ← split-tall-image slices (webtoons)
│   │   │   ├── 010__002.jpg
│   │   │   ├── ComicInfo.xml          ← if CBZ
│   │   │   └── .nomedia               ← DiskUtil.createNoMediaFile
│   │   ├── <chapter>.cbz              ← if saveChaptersAsCBZ (a single archive)
│   │   └── <chapter>_tmp/             ← in-progress download (001.tmp, ...)
│   └── <anime title>/
│       └── <episode>/                 ← getEpisodeDirName(name, scanlator)
│           └── <filename>.mkv         ← FFmpeg-remuxed matroska
```

The chapter directory name is `"${scanlator}_$chapterName"` if a
scanlator is set, else just `chapterName`; the whole thing is sanitised
through `DiskUtil.buildValidFilename`. `getValidChapterDirNames` returns
both the directory-of-images name and the `.cbz` name so callers can
find either form transparently.

When a source folder name changes (extension renamed),
`MangaDownloadManager.renameSource(old, new)` and the anime twin rename
the existing download folder so nothing is orphaned; the cache then
invalidates and rescans. See [`storage-and-cache.md`](storage-and-cache.md)
for the SAF / folder picker / cache eviction story.

## *DownloadProvider, *DownloadStore, *DownloadCache

### *DownloadProvider

A pure path-resolver over `StorageManager.getDownloadsDirectory()`. Has
no state. Creates the source / manga / chapter directories on demand,
finds them on disk, sanitises names, and computes the valid set of
filenames to look for when a chapter has been renamed (e.g. between
`Chapter Name` and `Scanlator_Chapter Name.cbz`).

### *DownloadStore

Persists the active queue across app restarts. Backed by a private
`SharedPreferences` file named `active_downloads` (one per side, but the
same name — they live in different preference stores).

Each download is serialised as a tiny JSON object
`{mangaId, chapterId, order}` (manga) or `{animeId, episodeId, order}`
(anime) under the key `chapterId.toString()` / `episodeId.toString()`.
`order` is an in-process counter used to keep the queue order on
restore. The store is updated in lock-step with the in-memory queue:
`addAll`, `remove`, `removeAll`, `clear`.

On `restore()`, the store reads every JSON entry, sorts by `order`, then
resolves each `(mangaId, chapterId)` back to a `Manga` + `Chapter` +
`HttpSource` via the domain interactors and the source manager (with
`runBlocking` — restore happens once at startup). The store is then
cleared; the downloader immediately re-adds the restored downloads via
`addAllToQueue`, which re-persists them. Skips entries whose manga,
chapter, or source can't be resolved (e.g. extension was uninstalled).

### *DownloadCache

A filesystem mirror of the downloads directory used to answer
"is chapter X already downloaded?" without hitting disk every time
(`isChapterDownloaded` is called from the library and chapter list —
very hot path). Three-level structure:

```
RootDirectory  →  Map<Long, SourceDirectory>  →  Map<String, MangaDirectory>  →  Set<String> chapterDirs
   (root dir)        (keyed by source id)            (keyed by manga dir name)        (chapter dir names)
```

- **Renewed periodically** — every `1.hours` (`renewInterval`). Renewal
  walks the filesystem in parallel per source directory. Concurrent
  callers wait on a `Mutex`.
- **Invalidated** whenever `StorageManager.changes` emits (i.e. the user
  picks a new downloads directory).
- **Persisted to disk** as a Protocol Buffer file at
  `context.cacheDir/dl_index_cache_v3` so the cache survives app
  restarts without a full rescan. Updates are debounced (1 s) so a flurry
  of chapter downloads doesn't write the file 50 times.
- **Initialisation** waits up to 30 s for both
  `ExtensionManager.isInitialized` and `*SourceManager.isInitialized`
  to flip true — it can't resolve directory names to source IDs until
  sources are loaded.

### *DownloadPendingDeleter

A separate queue of chapters/episodes scheduled for deletion "later"
(typically: when `removeAfterRead` is on, but the user wants the chapter
around long enough to mark it as read in trackers first). Backed by its
own SharedPreferences file. `deletePendingChapters()` /
`deletePendingEpisodes()` runs the actual delete.

## WorkManager integration

Source: `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/{manga,anime}/*DownloadJob.kt`.

Both `MangaDownloadJob` and `AnimeDownloadJob` are `CoroutineWorker`s
registered with `WorkManager` as **unique one-time work** under the tags
`"MangaDownloader"` and `"AnimeDownloader"` respectively.

```
fun start(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        TAG, ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<MangaDownloadJob>().addTag(TAG).build(),
    )
}
fun stop(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(TAG)
```

`doWork()`:

1. Checks `activeNetworkState()` against
   `downloadPreferences.downloadOnlyOverWifi()`. If offline or on metered
   with the Wifi-only preference on, stops the downloader with a warning
   notification.
2. Calls `downloadManager.downloaderStart()` — returns false if there's
   nothing to download, in which case the worker exits with
   `Result.failure()`.
3. Promotes itself to a **foreground service** via `setForegroundSafely()`
   with `FOREGROUND_SERVICE_TYPE_DATA_SYNC` (Android Q+). This is what
   keeps the download running when the app is backgrounded.
4. Subscribes to a
   `combineTransform(networkStateFlow, downloadOnlyOverWifi.changes())`
   so a network change mid-download (Wifi → mobile) can pause the
   downloader with a reason.
5. Loops `while (!isStopped && downloadManager.isRunning && networkCheck)`.
6. Exits with `Result.success()` when the queue drains or the user stops.

`isRunningFlow(context)` exposes a `Flow<Boolean>` that the UI uses to
show the download-status indicator in the bottom bar. `cancelUniqueWork`
cascades into `downloaderJob` being cancelled (via `isStopped` becoming
true), which cancels each child download coroutine.

## Key files

The manga and anime sides are structurally identical; only the manga path
is listed. Substitute `Manga`→`Anime`, `Chapter`→`Episode`,
`manga/`→`anime/` for the anime twin.

| File (relative to `../ANIYOMI/`) | Role |
|---|---|
| `app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadManager.kt` | Public façade. Queue, status, progress flows; delete, rename, build-page-list APIs. |
| `app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloader.kt` | The actual download engine. `downloadChapter`, `getOrDownloadImage`, `downloadImage` (with retry), `archiveChapter`, `createComicInfoFile`. |
| `app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadProvider.kt` | Pure path resolver. Builds source/manga/chapter dir names; finds existing dirs. |
| `app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadStore.kt` | SharedPreferences-backed queue persistence. |
| `app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadCache.kt` | Filesystem mirror (1-hour TTL, ProtoBuf-persisted) for `isChapterDownloaded` and download-count queries. |
| `app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadNotifier.kt` | Progress / paused / error / warning notifications. |
| `app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadJob.kt` | WorkManager `CoroutineWorker`. Foreground service, network gating. |
| `app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadPendingDeleter.kt` | Delayed-delete queue. |
| `app/src/main/java/eu/kanade/tachiyomi/data/download/manga/model/MangaDownload.kt` | The `MangaDownload` model + `State` enum + `progressFlow`. |
| `app/src/main/java/eu/kanade/tachiyomi/data/download/anime/model/AnimeDownloadPart.kt` | (Anime only) — represents a single byte-range of a video download, for resumable multi-part downloads. |

## See also

- [`../05-key-flows/download-chapter.md`](../05-key-flows/download-chapter.md)
- [`torrent-streaming.md`](torrent-streaming.md)
- [`source-system.md`](source-system.md)
- [`storage-and-cache.md`](storage-and-cache.md)
- [`notifications.md`](notifications.md)
- [`../02-modules/source-api.md`](../02-modules/source-api.md) — `Page`
  and `Video` models.
- [`../02-modules/core-archive.md`](../02-modules/core-archive.md) —
  `ZipWriter` used by `archiveChapter`.
- [`../02-modules/core-metadata.md`](../02-modules/core-metadata.md) —
  `ComicInfo.xml` schema written into CBZ archives.
