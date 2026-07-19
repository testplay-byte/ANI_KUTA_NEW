# 05-key-flows / Download a chapter/episode

> Trace a download-icon tap from the chapter/episode list through the
> `*DownloadManager` → `*DownloadJob` (WorkManager) → `*Downloader` →
> page-fetch loop (manga) or FFmpeg-remux/torrent (anime) → notification →
> queue status.

The download subsystem is **dual**: a manga pipeline and an anime pipeline
that share the same overall shape but diverge on the per-item worker. See
[`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md)
for the full subsystem deep dive; this doc focuses on the end-to-end flow.

## Overview

```
USER: tap download icon on chapter row (or "Download next N" action)
   │
   ▼
MangaScreenModel.downloadChapters(chapters)
   └─ downloadManager.downloadChapters(manga, chapters, autoStart = true)
        └─ MangaDownloader.queueChapters(manga, chapters, autoStart)
             ├─ filter out already-downloaded (downloadCache.isChapterDownloaded)
             ├─ build MangaDownload(manga, chapter, source) per chapter
             ├─ _queueState.update { it + newDownloads }       ← StateFlow<List<MangaDownload>>
             ├─ downloadStore.addAll(newDownloads)             ← persist queue to SharedPreferences
             └─ if (autoStart) startDownloads()
                  ├─ if (MangaDownloadJob.isRunning(context)) downloader.start()
                  └─ else MangaDownloadJob.start(context)      ← WorkManager one-shot work
   │
   ▼
MangaDownloadJob.start(context)  ← CoroutineWorker, foreground service
   ├─ getForegroundInfo() → progress notification
   └─ doWork():
        downloader.start()
          ├─ activeDownloads = queue.groupBy(source).take(5).map(_.first)   ← 5 sources concurrent
          ├─ for each active download: launchDownloadJob(download)
          │     └─ downloadChapter(download)
          │          ├─ getMangaDir(manga.title, source)
          │          ├─ availSpace check (MIN_DISK_SPACE = 200 MB)
          │          ├─ tmpDir = mangaDir.createDirectory("<chapter>_tmp")
          │          ├─ if pages null: source.getPageList(chapter) → re-index
          │          ├─ download.status = DOWNLOADING
          │          ├─ pages.asFlow().flatMapMerge(concurrency = 2) { page →
          │          │     if page.imageUrl null: source.getImageUrl(page)
          │          │     getOrDownloadImage(page, download, tmpDir, dataSaver)
          │          │       ├─ skip if file exists (resume)
          │          │       ├─ copy from ChapterCache if present
          │          │       ├─ else source.getImage(page, dataSaver) → OkHttp → Throttler → <NNN>.tmp
          │          │       ├─ rename to <NNN>.<ext>  (ext from MIME / magic bytes)
          │          │       ├─ (optional) splitTallImages → <NNN>__001.jpg, <NNN>__002.jpg
          │          │       └─ retryWhen 3× with 2s/4s/8s backoff
          │          │   }.collect { notifier.onProgressChange(download) }
          │          ├─ createComicInfoFile(tmpDir, manga, chapter, source)   ← ComicInfo.xml
          │          ├─ if saveChaptersAsCBZ: archiveChapter → .cbz (ZipWriter, STORE)
          │          │   else: tmpDir.renameTo(chapterDirname)
          │          ├─ cache.addChapter(chapterDirname, mangaDir, manga)
          │          ├─ DiskUtil.createNoMediaFile(tmpDir)   ← .nomedia
          │          └─ download.status = DOWNLOADED
          ├─ removeFromQueue(download) on success
          └─ notifier.onProgressChange / onComplete / onError
   │
   ▼
MangaDownloadNotifier posts to CHANNEL_DOWNLOADER_PROGRESS (or _ERROR)
   ├─ onProgressChange: "<title> - <chapter>", "X / Y", Pause + Show Manga actions
   └─ onComplete: dismiss progress notification
   │
   ▼
SQLDelight / DownloadCache emit → chapter list recomposes (download icon → check mark)

(Anime side: identical up to the per-item worker — see step 5 below.)
```

## Step-by-step

### 1. The download-icon tap → `downloadChapters`

Both `MangaScreenModel` and `AnimeScreenModel` expose a `downloadChapters`
helper:

```kotlin
private fun downloadChapters(chapters: List<Chapter>) {
    val manga = successState?.manga ?: return
    downloadManager.downloadChapters(manga, chapters)
    toggleAllSelection(false)
}
```

This is called from:

- The per-chapter download icon in the chapter list.
- The "Download next 1 / 5 / 10 unread" action in the overflow menu
  (`runDownloadAction(DownloadAction.NEXT_N_ITEMS)`).
- The selection-mode bottom action menu (`runDownloadActionSelection`).
- The auto-download-ahead path from the reader
  (`ReaderViewModel.downloadNextChapters` — see [`read-manga.md`](read-manga.md)).
- The library-update job's auto-download-new-chapters preference
  (see [`../03-subsystems/updates.md`](../03-subsystems/updates.md)).

### 2. `*DownloadManager.downloadChapters` → `*Downloader.queueChapters`

`MangaDownloadManager.downloadChapters(manga, chapters, autoStart = true)`
(`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadManager.kt`)
delegates to `MangaDownloader.queueChapters(manga, chapters, autoStart)`,
which:

1. Filters out chapters already marked downloaded in the
   `MangaDownloadCache` (so re-tapping download on an existing chapter is
   a no-op).
2. Builds `MangaDownload(manga, chapter, source)` for each remaining
   chapter. The `MangaDownload` carries `source`, `manga`, `chapter`, and
   a mutable `pages: List<Page>?` (filled lazily by the downloader).
3. `_queueState.update { it + newDownloads }` — the in-memory queue is a
   `MutableStateFlow<List<MangaDownload>>`; this is what the Downloads UI
   and the per-chapter download-status badges collect.
4. `downloadStore.addAll(newDownloads)` — persists the queue to
   SharedPreferences so it survives process death.
5. If `autoStart`, calls `startDownloads()`.

### 3. `startDownloads` → `*DownloadJob.start` (WorkManager)

```kotlin
fun startDownloads() {
    if (downloader.isRunning) return
    if (MangaDownloadJob.isRunning(context)) {
        downloader.start()
    } else {
        MangaDownloadJob.start(context)
    }
}
```

`MangaDownloadJob`
(`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadJob.kt`)
is a `CoroutineWorker` registered with WorkManager as a one-time unique
work named `"MangaDownloader"` (anime side: `"AnimeDownloader"`). The
work is constrained by `NetworkType.CONNECTED` (or `UNMETERED` if the
user has the "only on Wi-Fi" pref on) and runs as a **foreground
service** so it survives background-death. `getForegroundInfo()` returns
the progress notification.

`doWork()` calls `downloader.start()`, which:

1. Computes the **active set**:

   ```kotlin
   val activeDownloads = queue.asSequence()
       .filter { it.status.value <= State.DOWNLOADING.value }
       .groupBy { it.source }
       .toList().take(5)               // 5 sources concurrent (manga) / 3 (anime)
       .map { (_, downloads) -> downloads.first() }
   ```

   So at most one chapter per source runs at a time, but up to 5 sources
   run in parallel. (Anime takes 3.)

2. Launches a child coroutine per active download.
3. Suspends on a `combine(...) { states -> states.contains(ERROR) }`
   flow until *any* active download errors, at which point it recomputes
   the active set. Cancelled downloads free their slot.

### 4. `MangaDownloader.downloadChapter` — the 13-step algorithm

```
1. getMangaDir(download.manga.title, download.source)         ← MangaDownloadProvider
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
7. pages.asFlow().flatMapMerge(concurrency = 2) { page →
       if (page.imageUrl == null) page.imageUrl = source.getImageUrl(page)
       getOrDownloadImage(page, download, tmpDir, dataSaver)
   }.collect { notifier.onProgressChange(download) }
8. if (!isDownloadSuccessful(download, tmpDir)) → ERROR
9. createComicInfoFile(tmpDir, manga, chapter, source)        ← ComicInfo.xml
10. if (downloadPreferences.saveChaptersAsCBZ().get())
       archiveChapter(mangaDir, chapterDirname, tmpDir)       ← ZipWriter → .cbz
    else
       tmpDir.renameTo(chapterDirname)
11. cache.addChapter(chapterDirname, mangaDir, manga)         ← MangaDownloadCache
12. DiskUtil.createNoMediaFile(tmpDir, context)               ← .nomedia
13. download.status = DOWNLOADED
```

Per-page (`getOrDownloadImage`):

- Filename is zero-padded `NNN` (≥ 3 digits; more if the chapter has
  >999 pages).
- **Skip** if the file already exists (resume from where a previous run
  left off).
- **Copy** from `ChapterCache` if the image is already there (the reader
  may have warmed the cache).
- **Else** call `source.getImage(page, dataSaver)` — `HttpSource`
  returns an OkHttp `Response`. The body is streamed through an Okio
  `Throttler` (rate-limited by `downloadPreferences.downloadSpeedLimit()`
  KB/s) into `<filename>.tmp`.
- File extension comes from the response's MIME type or by sniffing the
  magic bytes; defaults to `jpg`. `.tmp` renamed to `<filename>.<ext>`.
- If `splitTallImages()` pref is on, the image is sliced into multiple
  `<NNN>__001.jpg`, `<NNN>__002.jpg`, ... files for smoother paging
  (typical for webtoons).
- `flow { … }.retryWhen { _, attempt -> if (attempt < 3) { delay((2L
  shl attempt.toInt()) * 1000); true } else false }` — 3 retries with
  2 s / 4 s / 8 s exponential backoff. A page that exhausts its retries
  is marked `Page.State.ERROR`; the chapter download continues for the
  remaining pages (a partial chapter is still saved).

### 5. CBZ archive + ComicInfo.xml

If `saveChaptersAsCBZ()` is enabled (default: yes), `archiveChapter`
walks every file in `tmpDir` and writes them into a fresh `.cbz` (a ZIP
with `STORE` compression — already-compressed image data) using
`mihon.core.archive.ZipWriter`. The `.cbz` is renamed from
`<chapter>.cbz_tmp` to `<chapter>.cbz` once closed, and `tmpDir` is
deleted.

Before archiving, `createComicInfoFile` writes a `ComicInfo.xml` into
`tmpDir` based on `getComicInfo(manga, chapter, urls, categories,
sourceName)` (see [`../02-modules/core-metadata.md`](../02-modules/core-metadata.md)).
This follows the [ComicInfo v2.0 schema](https://anansi-project.org/docs/comicinfo/schemas/v2.0)
and lets external comic readers (KOreader, Komga, Kavita) display
metadata correctly.

### 6. Anime downloads — `AnimeDownloader.downloadEpisode`

The anime side (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloader.kt`)
follows the same outer shape (manager → job → downloader → per-item
worker) but the per-item worker is different:

```
downloadEpisode(download):
1. getAnimeDir(download.anime.title, download.source)
2. availSpace check (same MIN_DISK_SPACE = 200 MB)
3. tmpDir = animeDir.createDirectory("<episode>_tmp")
4. if (download.video == null):
     hosters = EpisodeLoader.getHosters(episode, anime, source)
     bestVideo = HosterLoader.getBestVideo(source, hosters)   ← picks first usable
     download.video = bestVideo
5. getOrDownloadVideoFile(download, tmpDir)
6. if (!isDownloadSuccessful(download, tmpDir)) → ERROR
7. tmpDir.renameTo(episodeDirname)
8. cache.addEpisode(episodeDirname, animeDir, anime)
9. DiskUtil.createNoMediaFile(tmpDir, context)
10. download.status = DOWNLOADED
```

`getOrDownloadVideoFile` switches on
`preferences.useExternalDownloader().get() == download.changeDownloader`:

- **Internal** (default) → `downloadVideo`:
  - If `torrentPreferences.torrServerEnable().get()` AND the URL looks
    like a torrent → `torrentDownload`:
    1. `TorrentServerService.start()` (foreground service, starts
       Torrserver on the configured port).
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
  Aniyomi doesn't download the bytes — it just launches the external
  app with the URL, headers, and a target filename, and immediately
  marks the download as completed.

### 7. `ffmpegDownload` — a remux, not a re-encode

```
val headerOptions = (video.headers ?: source.headers)
    .joinToString("", "-headers '", "'") { "${it.first}: ${it.second}\r\n" }

duration = getDuration(ffprobeCommand(video.videoUrl, headerOptions))?.toLong() ?: 0L

session = FFmpegKit.executeWithArgumentsAsync(
    getFFmpegOptions(video, headerOptions, ffmpegFilename),   ← builds the CLI array
    completeCallback,                                          ← renames .tmp → .mkv on success
    logCallback,                                               ← WARNING+ → logcat ERROR
    statCallback,                                              ← updates download.progress
)
```

The `ffmpegOptions` builder constructs a command that:

- Adds the source's headers (so cookies / referer work for HTTP inputs).
- Adds `-i <videoUrl>` plus an `-i <subtitleUrl>` for every subtitle
  track and an `-i <audioUrl>` for every audio track (the `Video` model
  can carry multiple alternative tracks — see
  [`../02-modules/source-api.md`](../02-modules/source-api.md)).
- Maps video, audio, and subtitle streams (`-map 0:v`, `-map 0:a?`,
  `-map 0:s?`, `-map 0:t?`, plus extra maps for each added input).
- Sets `-f matroska -c:a copy -c:v copy -c:s copy` — this is a **remux**,
  not a re-encode. The video and audio bytes are copied untouched; only
  the container changes (to `.mkv`). This is fast and lossless.
- Adds per-track metadata `-metadata:s:<type>:<i> title=<lang>`.
- Appends the source's own FFmpeg args (`video.ffmpegStreamArgs`,
  `video.ffmpegVideoArgs`) — sources can request e.g. `-rtsp_transport
  tcp` or `-user_agent ...` here.

Progress is reported by the `StatisticsCallback`: FFmpeg calls it with
`s.time` (microseconds of output written so far); the callback divides
by `duration` (seconds) to compute
`download.progress = (100 * outTime / duration).toInt()`.

### 8. Notifications + queue status

`*DownloadNotifier` posts notifications throughout:

| Method | Channel | Trigger |
|---|---|---|
| `onProgressChange` | `CHANNEL_DOWNLOADER_PROGRESS` | Every page (manga) / progress tick (anime). Shows `<title> - <chapter>` and `downloadedImages / pages` or `progress%`. Has Pause + Show Manga/Anime actions. |
| `onPaused` | `CHANNEL_DOWNLOADER_PROGRESS` | All downloads paused. Shows Resume + Clear actions. |
| `onComplete` | (dismisses progress notification) | Queue drained successfully. |
| `onWarning` | `CHANNEL_DOWNLOADER_ERROR` | Network dropped, only-Wifi policy violated, or queue-size warning. Auto-dismisses after `WARNING_NOTIF_TIMEOUT_MS`. |
| `onError` | `CHANNEL_DOWNLOADER_ERROR` | A page failed all retries, or the chapter download threw. |

Both notifiers respect `SecurityPreferences.hideNotificationContent()` —
when set, the progress notification shows only the count and no
title/text.

The Downloads tab (`DownloadsTab` /
`MangaDownloadQueueTab`/`AnimeDownloadQueueTab`) collects
`*DownloadManager.queueState` and renders the live queue, with per-item
pause/resume/cancel/move-to-top actions that just call back into the
manager. The chapter list's per-chapter download-status badge is driven
by the same `queueState` plus the `*DownloadCache` (which knows which
chapters are already on disk) — see
[`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md)
for the cache TTL and ProtoBuf persistence.

## Sequence diagram (manga side; anime worker differs per step 6/7)

```
USER: tap download icon on chapter row
   │
   ▼
MangaScreenModel.downloadChapters(chapters)
   └─ downloadManager.downloadChapters(manga, chapters, autoStart = true)
        └─ MangaDownloader.queueChapters(manga, chapters, true)
             ├─ filter out already-downloaded
             ├─ build MangaDownload per chapter
             ├─ _queueState.update { it + newDownloads }    ← Downloads UI recomposes
             ├─ downloadStore.addAll(newDownloads)          ← persist to SharedPreferences
             └─ startDownloads()
                  └─ MangaDownloadJob.start(context)        ← WorkManager one-shot
                       └─ doWork():
                            downloader.start()
                              ├─ activeDownloads = queue.groupBy(source).take(5).map(_.first)
                              └─ for each: launchDownloadJob(download)
                                   └─ downloadChapter(download)
                                        ├─ getMangaDir / availSpace / tmpDir
                                        ├─ source.getPageList(chapter) → re-index
                                        ├─ download.status = DOWNLOADING
                                        ├─ pages.flatMapMerge(2) { getOrDownloadImage }
                                        │     ├─ skip / copy-from-cache / source.getImage → OkHttp → Throttler
                                        │     ├─ rename .tmp → <NNN>.<ext>
                                        │     ├─ (optional) splitTallImages
                                        │     └─ retryWhen 3× (2s/4s/8s backoff)
                                        ├─ createComicInfoFile (ComicInfo.xml)
                                        ├─ if saveChaptersAsCBZ: archiveChapter → .cbz
                                        │   else: tmpDir.renameTo(chapterDirname)
                                        ├─ cache.addChapter
                                        ├─ .nomedia file
                                        └─ download.status = DOWNLOADED
                              ├─ removeFromQueue(download) on success
                              └─ notifier.onProgressChange / onComplete / onError
   │
   ▼
Notification posts (progress / error / complete)
   │
   ▼
queueState + downloadCache emit → chapter row recomposes (icon: download → check)
```

## See also

- [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) — the full download subsystem deep dive (state machine, concurrency, providers, caches, store, pending deleter, WorkManager integration).
- [`../03-subsystems/torrent-streaming.md`](../03-subsystems/torrent-streaming.md) — the Torrserver hand-off in the anime torrent-download branch.
- [`read-manga.md`](read-manga.md) and [`watch-anime.md`](watch-anime.md) — the auto-download-ahead path triggered from the reader/player.
- [`../03-subsystems/updates.md`](../03-subsystems/updates.md) — the "auto-download new chapters/episodes" library-update preference.
- [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md) — the SAF-based download directory layout.
