# EVID-02B — Download Pipeline, Orchestration, Offline Playback, Source-Switching Break, UI Layer

**Agent:** Explore (research-only)
**Task ID:** EVID-02B-DOWNLOADS-PIPELINE
**Scope:** User flow (tap download → file on disk), `DownloadOrchestrator`, offline playback, source-switching break, UI layer. **Excludes** data models / persistence / storage (covered by the parallel data-layer agent).
**Status:** Research complete. No code modified. Every claim cited `file:line`.

---

## 0. File map (this agent's scope)

| Concern | File (relative to `ANIKUTA_PROJECT/ANIKUTA/`) | Lines |
|---|---|---|
| Episode-row download button | `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/EpisodeDownloadControl.kt` | 177 |
| Episode-row state machine | `feature/anime-details/.../EpisodeDownloadState.kt` | 46 |
| Episode section wiring | `feature/anime-details/.../EpisodesSection.kt` | 952 |
| Video resolver (watch flow) | `feature/video-resolver/.../ResolverService.kt` | 158 |
| Resolver sheet UI | `feature/video-resolver/.../VideoResolverSheet.kt` | 127 |
| Orchestrator (resolve → select → enqueue) | `app/src/main/java/app/confused/anikuta/download/DownloadOrchestrator.kt` | 396 |
| Orchestrator entry from UI | `app/src/main/java/app/confused/anikuta/navigation/AppController.kt` | 745 |
| Navigator destinations | `app/src/main/java/app/confused/anikuta/navigation/Destinations.kt` | — |
| Root composable + overlays | `app/src/main/java/app/confused/anikuta/navigation/AnikutaRoot.kt` | 213 |
| `MainActivity` (theme/OAuth only) | `app/src/main/java/app/confused/anikuta/MainActivity.kt` | 96 |
| DownloadManager interface | `core/download/.../DownloadManager.kt` | 122 |
| DefaultDownloadManager | `core/download/.../DefaultDownloadManager.kt` | 220 |
| DownloadQueue (state machine + Semaphore) | `core/download/.../DownloadQueue.kt` | 315 |
| DownloadTask (composite key) | `core/download/.../DownloadTask.kt` | 49 |
| HttpDownloader (HTTP I/O + validation) | `core/download/.../HttpDownloader.kt` | 536 |
| DownloadStorageProvider (SAF folder) | `core/download/.../DownloadStorageProvider.kt` | 462 |
| DownloadStore (JSON persistence) | `core/download/.../DownloadStore.kt` | 75 |
| DownloadPreferences | `core/download/.../DownloadPreferences.kt` | 204 |
| DownloadNotificationManager | `core/download/.../DownloadNotificationManager.kt` | 191 |
| Download models (DTOs) | `core/download/.../DownloadModels.kt` | 93 |
| `resolveUrlForMpv` (fd:// / real-path) | `core/player/.../PlayerUtils.kt` | 76 |
| MPV loadfile (offline path) | `core/player/.../PlayerInitializer.kt:117-141` | — |
| WatchRequest | `feature/watch/.../WatchRequest.kt` | 38 |
| DownloadsScreen (queue UI) | `feature/download/.../DownloadsScreen.kt` | 569 |
| DownloadedFilesScreen (library UI) | `feature/download/.../DownloadedFilesScreen.kt` | 206 |
| DownloadSettingsScreen | `feature/download/.../DownloadSettingsScreen.kt` | 527 |
| DownloadVideoPickerSheet | `feature/download/.../DownloadVideoPickerSheet.kt` | 232 |
| DownloadViewModel + UiState | `feature/download/.../DownloadViewModel.kt`, `DownloadUiState.kt` | 105 + 41 |
| AnimeDetailViewModel (switch source) | `feature/anime-details/.../AnimeDetailViewModel.kt:323-400` | — |

---

## 1. The user flow: tap download → file on disk

### 1.1 Step-by-step pipeline (ASCII)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  USER TAPS DOWNLOAD ON EPISODE ROW                                            │
│  EpisodeDownloadControl.kt:65  (NotDownloaded → IconButton → onDownload())   │
└────────────────────────────────────────┬─────────────────────────────────────┘
                                         │
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  EpisodeRow's onDownload lambda                                               │
│  EpisodesSection.kt:313-315                                                   │
│    onDownload = {                                                             │
│      currentSource?.let { source -> onDownloadEpisode(episode, source) }      │
│    }                                                                          │
│  ↑ `currentSource` is the AnimeSource from the matched SourceMatch — the      │
│    extension source currently selected.                                       │
└────────────────────────────────────────┬─────────────────────────────────────┘
                                         │
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  Propagated through EpisodesSection → DetailContent → AnimeDetailScreen       │
│  as `onDownloadEpisode: (SEpisode, AnimeSource, WatchEpisodeContext) -> Unit` │
│  AnimeDetailScreen.kt:68, 240 ; DetailContent.kt:73, 168-169                 │
└────────────────────────────────────────┬─────────────────────────────────────┘
                                         │
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  Voyager Screen wiring (Destinations.kt)                                      │
│  AnimeDetailDestination.Content (Destinations.kt:127-146):                    │
│    onDownloadEpisode = { episode, source, watchCtx ->                         │
│        appController.downloadEpisode(episode, source, watchCtx, animeId)      │
│    }       ↑ ★ anilistId ENTRY POINT ★ (animeId from Voyager route arg)      │
│  ExtensionAnimeDetailDestination.Content (Destinations.kt:189-191):           │
│    onDownloadEpisode = { ep, src, ctx ->                                      │
│        appController.downloadEpisode(ep, src, ctx, downloadKey)               │
│    }       ↑ downloadKey = anilistId ?: 0 (Destinations.kt:173)               │
└────────────────────────────────────────┬─────────────────────────────────────┘
                                         │
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  AppController.downloadEpisode                                                │
│  AppController.kt:503-542                                                     │
│                                                                               │
│  ★ HARD GATE: if (anilistId == 0) → Toast "Cannot download — anime not       │
│    linked" + return. (AppController.kt:509-512) — see §2.                    │
│                                                                               │
│  1. Builds DownloadAnimeInfo(anilistId, title, coverUrl) — L513-517           │
│     ★ anilistId is captured here from the function arg.                       │
│  2. Sets `resolvingEpisodes[episode.url] = true` (instant spinner, L519)     │
│  3. scope.launch { downloadOrchestrator.enqueueDownload(animeInfo, episode,  │
│     source) }  (L521-523)                                                     │
│  4. when(result):                                                             │
│     - Success → Toast "Download started"                                      │
│     - ShowPicker → downloadPickerTarget = result (overlay sheet opens)        │
│     - NoSources / Error → Toast                                               │
│  5. finally { resolvingEpisodes.remove(episode.url) }                         │
└────────────────────────────────────────┬─────────────────────────────────────┘
                                         │
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  DownloadOrchestrator.enqueueDownload                                         │
│  DownloadOrchestrator.kt:61-140                                               │
│                                                                               │
│  1. Folder check: manager.isFolderReady() (L66-69) — returns Error if no     │
│     SAF folder set.                                                           │
│  2. resolver.resolve(source, episode) (L73) → ResolverResult                 │
│     ★ The resolver hits the extension (getHosterList/getVideoList) — the     │
│       episode's URL is the EXTENSION's episode URL (per-source).              │
│  3. serverDiscovery.recordServers(source.id, serverNames) (L80)              │
│  4. If !autoDownload → return ShowPicker (L83-90)                             │
│  5. selection = selectBestVideo(source.id, result.servers) (L93)              │
│     - qualityPrefs / audioPrefs / serverPrefs ordering (L208-212)             │
│     - Step 1: top-audio availability check (L220-242)                         │
│     - Step 2: top-quality availability check (L246-271)                       │
│     - Step 3: iterate preferred combinations (L274-287)                       │
│     - Step 4: TRY_NEXT best-effort (L291-303)                                 │
│  6. Selection.Selected → request = buildRequest(anime, episode, source,       │
│     selection)  (L96, L332-356)                                               │
│  7. taskId = manager.enqueueDownload(request)  (L97)                         │
│  8. taskId >= 0 → EnqueueResult.Success(taskId)  (L98-104)                    │
│                                                                               │
│  ★ buildRequest (L332-356):                                                   │
│    epInfo = DownloadEpisodeInfo(                                              │
│      episodeUrl = episode.url,         ← EXTENSION episode URL (per-source)   │
│      episodeNumber = episode.episode_number,                                  │
│      name = episode.name,                                                     │
│      scanlator = episode.scanlator,                                           │
│    )                                                                          │
│    DownloadRequest(                                                           │
│      anime = anime,                    ← carries anilistId                    │
│      episode = epInfo,                                                        │
│      videoUrl = selection.video.url,                                          │
│      videoHeaders = selection.video.videoHeaders,                             │
│      subtitleTracks = ..., audioTracks = ...,                                 │
│      sourceId = source.id,             ← the extension's source ID            │
│      videoServer, videoQuality, videoAudio,                                   │
│    )                                                                          │
└────────────────────────────────────────┬─────────────────────────────────────┘
                                         │
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  DefaultDownloadManager.enqueueDownload                                       │
│  DefaultDownloadManager.kt:111-121                                            │
│                                                                               │
│  1. Reject blank videoUrl → -1L (L112-115)                                    │
│  2. Reject !storage.isFolderReady() → -1L (L116-119)                          │
│  3. return queue.enqueue(request)  (L120)                                     │
└────────────────────────────────────────┬─────────────────────────────────────┘
                                         │
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  DownloadQueue.enqueue  (DownloadQueue.kt:86-108)                             │
│                                                                               │
│  ★ DEDUP: existing = _tasks.firstOrNull { it.key == keyFor(request) }        │
│    keyFor(request) = "${request.anime.anilistId}:${request.episode.episodeUrl}"│
│    (DownloadQueue.kt:309-310 + DownloadTask.kt:41)                            │
│  - If existing: keep status (no re-download); if ERROR → resumeInternal       │
│  - Else: task = DownloadTask(id = idCounter.getAndIncrement(),                │
│      request, status = QUEUED, createdAt = now)  (L97-102)                    │
│  - updateTasks(_tasks.value + task) (L103)                                    │
│  - persistNow() → DownloadStore.setAll (L104)                                 │
│  - tryStartNext() (L106)                                                      │
└────────────────────────────────────────┬─────────────────────────────────────┘
                                         │
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  DownloadQueue.tryStartNext → launchDownload  (DownloadQueue.kt:180-271)      │
│                                                                               │
│  - connectivityCheck() — Wi-Fi-only pref  (L181-184)                          │
│  - finds first QUEUED task (L185)                                             │
│  - launchDownload:                                                            │
│    permits.withPermit { ... }  (L193) ← Semaphore(N=concurrentDownloads)      │
│    Re-confirm status = QUEUED (L195-196)                                      │
│    mutateTask → status = DOWNLOADING (L197-199)                               │
│    completed = downloader.download(task) { downloaded, total -> ... }         │
│      ↑ callback fires per byte-tick → DynamicProgressTracker → mutateTask     │
│    mutateTask(task.id) { completed }  → status = COMPLETED                    │
│    onTaskCompleted?.invoke(completed)  → notifier.notifyCompleted             │
│  catch DownloadException → status = ERROR, onTaskError?.invoke                │
│  catch CancellationException → pause/cancel                                   │
│  finally { jobs.remove(task.id); tryStartNext() }                             │
└────────────────────────────────────────┬─────────────────────────────────────┘
                                         │
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  HttpDownloader.download  (HttpDownloader.kt:71-166)                          │
│                                                                               │
│  Pipeline v2 (internal-cache-first + validation):                             │
│  1. downloadVideoToCache (L93-99, L173-212)                                   │
│     - URL-based HLS routing (L181-187)                                        │
│     - ADVANCED method → AdvancedHttpDownloader multi-threaded (L196-208)      │
│     - Normal method → downloadNormal single-threaded OkHttp (L218-287)        │
│     - Content-Type HLS detection → HlsDownloader (L250-256)                   │
│  1b. HLS playlist content detection (small file + #EXTM3U) (L107-114)        │
│  2. validateDownloadedFile — rejects empty + <500KB (L116, L342-359)          │
│  2b. verifyVideoMagicBytes — rejects HTML/PNG/JPEG masquerading (L124,       │
│      L375-451)                                                                │
│  3. downloadSubtitlesToCache (best-effort) (L128, L454-479)                   │
│  4. writeMetadataToCache — EpisodeMetadataCache JSON (L131, L481-500)         │
│  5. storage.publishToUserFolder(...) (L135-142) → SAF atomic move             │
│     - copies video → Episode NNN/video.<ext>                                  │
│     - copies subs → Episode NNN/data/subtitles/<lang>_<i>.<ext>               │
│     - copies metadata.json → Episode NNN/data/metadata.json                   │
│     - returns PublishResult.Success(videoUri, subtitleUris, sizeBytes)        │
│  6. task.copy(status=COMPLETED, progress=100, videoUri, subtitleUris, ...)    │
│  finally { tempCache.cleanupTask(task.id) }  (L161-165)                       │
└────────────────────────────────────────┬─────────────────────────────────────┘
                                         │
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  ON-DISK RESULT                                                               │
│  <USER_SAF_ROOT>/ANIKUTA/downloads/anime/<Title [anilistId]>/Episode NNN/     │
│    ├── video.<ext>            (content:// URI stored in DownloadTask.videoUri)│
│    └── data/                                                                  │
│        ├── subtitles/<lang>_<i>.<ext>  (content:// URIs in subtitleUris)      │
│        └── metadata.json       (EpisodeMetadataCache JSON)                   │
│  Folder name helpers:                                                         │
│    animeFolderName   = "<sanitized title> [<anilistId>]"                      │
│      (DownloadStorageProvider.kt:86-89)  ★ anilistId in folder name           │
│    episodeFolderName = "Episode %03d" (floored)                               │
│      (DownloadStorageProvider.kt:92-95)                                       │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 The two `anilistId` entry points

1. **`AnimeDetailDestination`** (linked / AniList-mode page) — Voyager route arg `animeId: Int` is passed verbatim as `anilistId` (`Destinations.kt:110, 134`). Non-zero (linked anime only).
2. **`ExtensionAnimeDetailDestination`** (extension-mode page) — `downloadKey = anilistId ?: 0` (`Destinations.kt:173`). If the anime is unlinked (`anilistId == null`), `downloadKey = 0` → the `== 0` gate in `AppController.downloadEpisode` blocks the download.

The `anilistId` is threaded verbatim through:
- `AppController.downloadEpisode(episode, source, watchCtx, anilistId)` (`AppController.kt:503-507`)
- → `DownloadAnimeInfo(anilistId = anilistId, ...)` (`AppController.kt:513-517`)
- → `DownloadRequest(anime = animeInfo, ...)` (`DownloadOrchestrator.kt:344`)
- → `DownloadTask.request.anime.anilistId` (persisted in `DownloadStore`)
- → composite key `"$anilistId:$episodeUrl"` (`DownloadTask.kt:41`)
- → on-disk folder name suffix `" [<anilistId>]"` (`DownloadStorageProvider.kt:88`)

---

## 2. The `anilistId == 0` hard gate

### 2.1 Location + behavior

**File:** `app/src/main/java/app/confused/anikuta/navigation/AppController.kt:509-512`

```kotlin
fun downloadEpisode(
    episode: SEpisode,
    source: AnimeSource,
    watchCtx: WatchEpisodeContext,
    anilistId: Int,
) {
    if (anilistId == 0) {
        Toast.makeText(context, "Cannot download — anime not linked", Toast.LENGTH_SHORT).show()
        return
    }
    // ... build DownloadAnimeInfo + launch orchestrator
}
```

### 2.2 User-facing behavior

- **Toast** (LENGTH_SHORT): `"Cannot download — anime not linked"`.
- **No fallback.** No file is created; the orchestrator is never called.
- **Trigger condition:** an unlinked extension anime is opened via `ExtensionAnimeDetailDestination` with `anilistId = null` → `downloadKey = anilistId ?: 0` (`Destinations.kt:173`). Every episode row's download button on an unlinked extension anime bounces off this gate.

### 2.3 Why it exists

The `DownloadAnimeInfo.anilistId: Int` field is **non-nullable** (`DownloadModels.kt:26-31`). The on-disk folder name embeds `[anilistId]` (`DownloadStorageProvider.kt:88`). The composite dedup key is `"$anilistId:$episodeUrl"` (`DownloadTask.kt:41`). All of these would corrupt (folder name `[0]`, key `"0:..."`, dedup collisions across unlinked anime) if `anilistId = 0` were allowed through — so the gate is at the orchestrator entry, BEFORE `DownloadAnimeInfo` is constructed.

There is **no `anilistId == 0` gate inside `DownloadOrchestrator` itself** — the orchestrator assumes a non-zero anilistId (its KDoc at L20-26 doesn't even mention it; the gate is upstream in `AppController`).

---

## 3. Offline playback

### 3.1 The offline short-circuit

**File:** `app/src/main/java/app/confused/anikuta/navigation/AppController.kt:358-433` (`resolveEpisode`)

```kotlin
fun resolveEpisode(
    episode: SEpisode,
    source: AnimeSource,
    episodeList: List<SEpisode>,
    watchCtx: WatchEpisodeContext,
    anilistId: Int,
) {
    val epNum = episode.episode_number.toInt().let { if (it > 0) it else 0 }
    scope.launch {
        // ── Offline-playback short-circuit ──
        try {
            if (anilistId != 0 && downloadManager.isEpisodeDownloaded(anilistId, episode.url)) {
                val videoUri = downloadManager.getDownloadedVideoUri(anilistId, episode.url)
                val subUris = downloadManager.getDownloadedSubtitleUris(anilistId, episode.url)
                if (videoUri != null) {
                    pushWatch(
                        WatchRequest(
                            videoUrl = videoUri,           // ← content:// URI
                            videoHeaders = null,
                            videoTitle = episode.name,
                            anilistId = anilistId,
                            ...
                            episodeUrl = episode.url,       // ← EXTENSION episode URL
                            episodeNumber = episode.episode_number,
                            sourceId = source.id,
                            source = source,
                            ...
                            subtitleTracks = subUris.map { SubtitleTrack(it, "Downloaded") },
                            ...
                        )
                    )
                    return@launch
                }
            }
        } catch (e: Exception) { ... fall through to stream ... }
        // ── Streaming path (resolver sheet) ──
        ...
    }
}
```

### 3.2 How a downloaded file is matched to a play request

**Matching key:** `"$anilistId:$episodeUrl"` — the **same composite key** used for dedup at enqueue time.

**`DefaultDownloadManager.isEpisodeDownloaded`** (`DefaultDownloadManager.kt:163-169`):

```kotlin
override suspend fun isEpisodeDownloaded(anilistId: Int, episodeUrl: String): Boolean {
    val task = findTask(anilistId, episodeUrl)
    if (task?.status == DownloadStatus.COMPLETED) return true
    // Fallback: check the filesystem (covers files from a prior install).
    if (task == null) return false       // ← ★ THIS IS THE BREAK POINT
    return storage.isEpisodeDownloaded(task.request.anime, task.request.episode)
}
```

**`findTask`** (`DefaultDownloadManager.kt:202-204`):

```kotlin
private fun findTask(anilistId: Int, episodeUrl: String): DownloadTask? {
    return queue.tasks.value.firstOrNull { it.key == "$anilistId:$episodeUrl" }
}
```

So **offline lookup is by composite key** = `(anilistId, episodeUrl)`. NOT by episode number.

**`getDownloadedVideoUri`** (`DefaultDownloadManager.kt:171-175`) — same key lookup; returns `task.videoUri` (the SAF content:// URI captured at publish time) or `storage.getVideoUri(...)` as fallback.

**`getDownloadedSubtitleUris`** (`DefaultDownloadManager.kt:177-185`) — same.

### 3.3 Building the WatchRequest with the local content:// URI

The WatchRequest built in `AppController.resolveEpisode` (L375-398) for offline playback uses:
- `videoUrl = videoUri` — the content:// URI from `getDownloadedVideoUri` (`AppController.kt:377`)
- `videoHeaders = null` (no HTTP headers for local files)
- `subtitleTracks = subUris.map { SubtitleTrack(it, "Downloaded") }` (`AppController.kt:393-395`) — each subtitle URI is a content:// URI
- `videoServer = ""`, `videoAudio = ""`, `videoQuality = 0` (no resolution for offline)
- `source = source` (the AnimeSource is kept for the watch page's UI, even though it isn't re-resolved for offline)
- `episodeUrl = episode.url` (kept for WatchProgressStore saving later — `WatchScreen.kt:645, 683`)

### 3.4 `resolveUrlForMpv` — fd:// / real-path handling

**File:** `core/player/src/main/java/app/confused/anikuta/core/player/PlayerUtils.kt:70-76`

```kotlin
fun resolveUrlForMpv(url: String, context: Context): String {
    return if (url.startsWith("content://")) {
        Uri.parse(url).resolveUri(context) ?: url
    } else {
        url
    }
}
```

`Uri.resolveUri(context)` (L51-64) dispatches by scheme:
- `"file"` → `path` (raw filesystem path)
- `"content"` → `openContentFd(context)` (L22-49) — opens a ParcelFileDescriptor, detaches the FD, then either:
  - `Utils.findRealPath(fd)` returns a real path → close the FD, return the path. (SAF files on primary external storage resolve to `/storage/emulated/0/...`.)
  - else → `"fd://$fd"` (an MPV-recognized file-descriptor URL).
- `"data"` → `"data://$schemeSpecificPart"`
- in `Utils.PROTOCOLS` (http/https/rtmp/etc.) → toString()
- else → toString()

### 3.5 MPV loadfile (the final hand-off)

**File:** `core/player/src/main/java/app/confused/anikuta/core/player/PlayerInitializer.kt:117-141`

```kotlin
fun loadVideo(view: AnikutaMPVView, url: String, context: Context) {
    val resolvedUrl = resolveUrlForMpv(url, context)
    Log.i(TAG, "Loading video: $resolvedUrl")
    if (resolvedUrl.startsWith("fd://") || resolvedUrl.startsWith("content://")) {
        view.postDelayed({
            try { MPVLib.command(arrayOf("loadfile", resolvedUrl, "replace")) }
            catch (e: Exception) { Log.e(TAG, "Failed to load offline video", e) }
        }, 500)   // ← 500ms delay for SurfaceView's surfaceCreated
    } else {
        try { MPVLib.command(arrayOf("loadfile", resolvedUrl, "replace")) }
        catch (e: Exception) { Log.e(TAG, "Failed to load video", e) }
    }
}
```

The 500ms delay for offline URLs prevents the `assertion WinID != 0` crash (per the KDoc L118-121) — `SurfaceView.surfaceCreated` must fire before MPV attaches.

---

## 4. Source switching + downloads — THE CRITICAL BREAK

### 4.1 What happens when the user switches the extension source

**File:** `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/AnimeDetailViewModel.kt:323-351` (`switchExtension`)

```kotlin
fun switchExtension(source: AnimeCatalogueSource, sAnime: SAnime) {
    val anilistId = currentAnilistId()
    if (anilistId != null) {
        sourceLinkStore.saveLink(anilistId, source.id, sAnime.url, sAnime.title)
        sourcePrefs.edit().putLong(sourcePrefKey(anilistId), source.id).apply()
    }
    activeRequest = DetailsRequest.ByExtension(
        sourceId = source.id,
        animeUrl = sAnime.url,           // ← NEW extension's anime URL
        animeTitle = sAnime.title,
        anilistId = anilistId,           // ← anilistId UNCHANGED
    )
    _currentMatch.value = SourceMatcher.SourceMatch(source, sAnime, 1.0)
    if (_currentDataSource.value == DataSource.ANILIST) {
        reloadEpisodesOnly()             // ← fetches NEW episodes from NEW source
    } else {
        load()
    }
}
```

**Key observation:** `anilistId` is preserved; `sourceId + animeUrl` change; the episode list is reloaded from the new extension. The new extension returns **NEW episode URLs** (every extension uses its own URL scheme — e.g. `https://gogoanime.gg/jjk-ep-1` vs `https://9anime.to/watch/jjk/ep-1`).

### 4.2 The downloaded-files query

#### `DownloadedFilesScreen` query mechanism

**File:** `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadedFilesScreen.kt:67-72`

```kotlin
val state by viewModel.state.collectAsStateWithLifecycle()
...
val downloaded = state.downloaded
```

**`DownloadViewModel.groupByAnime`** (`DownloadViewModel.kt:93-104`):

```kotlin
private fun groupByAnime(tasks: List<DownloadTask>): Map<DownloadedAnimeKey, List<DownloadTask>> {
    return tasks
        .groupBy {
            DownloadedAnimeKey(
                anilistId = it.request.anime.anilistId,    // ★ groups by anilistId
                title = it.request.anime.title,
                coverUrl = it.request.anime.coverUrl,
                coverColor = it.request.anime.coverColor,
            )
        }
        .toSortedMap(compareBy { it.title.lowercase() })
}
```

**`DownloadUiState.downloaded`** is fed from `DownloadManager.completedDownloads` Flow (`DownloadViewModel.kt:36-49`), which is `queue.tasks.map { list -> list.filter { it.status == DownloadStatus.COMPLETED } }` (`DefaultDownloadManager.kt:102-103`).

So `DownloadedFilesScreen` queries **all** completed tasks, grouped by `anilistId` (+ title/cover for display). It does NOT filter by `episodeUrl`. The `DownloadedAnimeKey.anilistId` is the grouping key (`DownloadUiState.kt:29-34`).

Inside each anime card, episodes are listed from `task.request.episode` (with `episodeUrl`, `episodeNumber`, `name`) and sorted by `episodeNumber` (`DownloadedFilesScreen.kt:172`).

#### `DownloadedFilesScreen` per-episode tap → playback

**File:** `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadedFilesScreen.kt:104-110`

```kotlin
onPlay = { episodeUrl ->
    onPlayEpisode?.invoke(animeKey.anilistId, episodeUrl)
},
```

**`DownloadedFilesDestination`** wiring (`Destinations.kt:369-381`):

```kotlin
object DownloadedFilesDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        DownloadedFilesScreen(
            onBack = { navigator.pop() },
            onPlayEpisode = { anilistId, episodeUrl ->
                appController.pushDetail(anilistId)    // ← just pushes the detail page
            },
        )
    }
}
```

**Crucial:** `onPlayEpisode` does NOT directly play offline. It pushes the AniList detail page. The user then taps the episode row, which goes through `AppController.resolveEpisode` and its offline short-circuit (§3.1). So the actual playback path always re-checks `isEpisodeDownloaded(anilistId, episodeUrl)` against the CURRENT episode list's URLs.

### 4.3 Why downloads DISAPPEAR when sources change — the precise break

**Setup:** User downloaded EP 1 from source A (gogoanime). The download task is keyed `"12345:https://gogoanime.gg/jjk-ep-1"` and the file lives at `<folder>/Anime Title [12345]/Episode 001/video.mp4`.

**User switches to source B (9anime).** `switchExtension` (`AnimeDetailViewModel.kt:323-351`):
- `sourceLinkStore.saveLink(12345, 9anime-sourceId, 9anime-animeUrl, ...)` — replaces the saved link.
- `reloadEpisodesOnly()` fetches 9anime's episode list → episodes have NEW URLs like `https://9anime.to/watch/jjk/ep-1`.

**Now the user taps EP 1 on the detail page** → `AppController.resolveEpisode(episode, source, ..., anilistId=12345)` where `episode.url = "https://9anime.to/watch/jjk/ep-1"`.

**Offline check** at `AppController.kt:370`:

```kotlin
if (anilistId != 0 && downloadManager.isEpisodeDownloaded(anilistId, episode.url)) {
```

→ `DefaultDownloadManager.isEpisodeDownloaded(12345, "https://9anime.to/watch/jjk/ep-1")` (`DefaultDownloadManager.kt:163-169`):

```kotlin
override suspend fun isEpisodeDownloaded(anilistId: Int, episodeUrl: String): Boolean {
    val task = findTask(anilistId, episodeUrl)
    if (task?.status == DownloadStatus.COMPLETED) return true
    if (task == null) return false       // ← ★★★ THE BREAK — returns false here ★★★
    return storage.isEpisodeDownloaded(task.request.anime, task.request.episode)
}
```

`findTask(12345, "https://9anime.to/watch/jjk/ep-1")` (`DefaultDownloadManager.kt:202-204`):

```kotlin
private fun findTask(anilistId: Int, episodeUrl: String): DownloadTask? {
    return queue.tasks.value.firstOrNull { it.key == "$anilistId:$episodeUrl" }
}
```

The queue contains a task with key `"12345:https://gogoanime.gg/jjk-ep-1"`, but the lookup is for `"12345:https://9anime.to/watch/jjk/ep-1"`. **Mismatch → `findTask` returns null → `isEpisodeDownloaded` returns false** → offline short-circuit fails → falls through to streaming resolution.

**Meanwhile, `DownloadedFilesScreen` STILL SHOWS the old download** (because `groupByAnime` groups by `anilistId`, not by `episodeUrl` — see §4.2). The user sees their downloaded EP 1 listed in the Downloaded Files screen, but if they tap it, they're pushed to the detail page where the episode list is from source B, and tapping EP 1 there streams instead of playing offline.

**There is no episode-number-based matching anywhere in the offline-lookup path.** The composite key `"$anilistId:$episodeUrl"` is the ONLY lookup mechanism in `DownloadManager`:

| Method | Lookup key | File:line |
|---|---|---|
| `isEpisodeDownloaded(anilistId, episodeUrl)` | `"$anilistId:$episodeUrl"` | `DefaultDownloadManager.kt:163-169, 202-204` |
| `getDownloadedVideoUri(anilistId, episodeUrl)` | `"$anilistId:$episodeUrl"` | `DefaultDownloadManager.kt:171-175` |
| `getDownloadedSubtitleUris(anilistId, episodeUrl)` | `"$anilistId:$episodeUrl"` | `DefaultDownloadManager.kt:177-185` |
| `getDownloadedEpisodes(anilistId)` | filters by `it.request.anime.anilistId == anilistId` | `DefaultDownloadManager.kt:187-200` |
| `cancelDownload(taskId)` / `resumeDownload(taskId)` / `deleteDownload(taskId)` | `task.id: Long` | `DownloadQueue.kt:110-151` |
| `deleteAnimeDownloads(anilistId)` | filters by `it.request.anime.anilistId == anilistId` | `DefaultDownloadManager.kt:150-159` |

There is no `getDownloadedByEpisodeNumber(anilistId, episodeNumber)` method anywhere in `DownloadManager` (grep-verified — `episodeNumber` is used only for folder names + UI display, never for lookup).

### 4.4 The fix episode-number-based matching would require

The on-disk folder structure IS episode-number-keyed: `<folder>/Anime Title [12345]/Episode 001/video.mp4`. So a hypothetical fix would:

1. Add `DownloadManager.getDownloadedByEpisodeNumber(anilistId: Int, episodeNumber: Float): DownloadedEpisode?`
2. In `DefaultDownloadManager`, fall back to this lookup when `findTask(anilistId, episodeUrl)` returns null but `episodeUrl` doesn't match any task in the queue.
3. The implementation would scan completed tasks for the same `anilistId` whose `episode.episodeNumber` matches, OR fall all the way down to a filesystem scan via `storage.findEpisodeDir(anime, episode)` (which already keys on `episodeFolderName(episode)` = `"Episode %03d"`).

But today, this fallback does NOT exist. The `if (task == null) return false` at `DefaultDownloadManager.kt:167` is the **single line that causes downloads to "disappear" when sources change.**

### 4.5 Aggravating factor: source switching doesn't migrate downloads

`switchExtension` (`AnimeDetailViewModel.kt:323-351`) does NOT call any download-migration logic. There is no "re-key existing downloads from old-episodeUrl to new-episodeUrl" step. The download tasks retain their original (old-source) `episodeUrl` forever. Source switching is one-way: downloads stay bound to the source that produced them.

### 4.6 Cross-source matching mechanism: NONE

Searched `core/download/` and `app/src/main/.../download/` for any mechanism that tries to match a download by episode number across sources — **none exists**. The folder-name helper `episodeFolderName(episode) = "Episode %03d"` (`DownloadStorageProvider.kt:92-95`) is episode-number-keyed and COULD support cross-source matching (the folder survives a source switch), but no code path queries the filesystem by episode number alone.

---

## 5. `DownloadOrchestrator` (`:app`)

### 5.1 Responsibilities

**File:** `app/src/main/java/app/confused/anikuta/download/DownloadOrchestrator.kt` (396 lines)

Lives in `:app` because `:core:download` cannot import `:feature:video-resolver` (Rule §14 — feature isolation). Bridges the two: **resolve → select → enqueue** (the KDoc at L20-26 explains this).

**Two modes** (L28-41):

1. **Auto-download ON (default):** `enqueueDownload()` resolves videos, picks the best (server, audio, quality) based on `DownloadPreferences`, enqueues. Single tap. If fallback=ASK → returns `EnqueueResult.ShowPicker`.
2. **Auto-download OFF:** `enqueueDownload()` always returns `ShowPicker` with the resolved servers; the host shows `DownloadVideoPickerSheet`; the user picks; the host calls `enqueueSpecific()`.

**Two public entry points:**

- `enqueueDownload(anime: DownloadAnimeInfo, episode: SEpisode, source: AnimeSource): EnqueueResult` (L61-140) — resolve + auto-pick.
- `enqueueSpecific(video: ResolverVideo, serverName: String, audioLabel: String, context: PickerContext): EnqueueResult` (L147-171) — enqueue a specific user-picked video. Skips resolve (the `PickerContext` carries the resolve result).

### 5.2 Auto-pick best quality — the algorithm

**`selectBestVideo(sourceId: Long, servers: List<ResolverServer>): Selection`** (`DownloadOrchestrator.kt:207-307`):

Reads (L208-212):
- `qualityPrefs = preferences.qualityPreferences().get()` — ordered list, top = highest priority (default `["1080p", "720p", "480p", "360p"]`).
- `audioPrefs = preferences.audioPreferences().get()` — ordered (default `["SUB", "DUB"]`).
- `serverPrefs = preferences.serverPreferences().get()[sourceId.toString()]` — per-source server priority.
- `audioFallback = preferences.audioFallback().get()` — `TRY_NEXT` / `ASK` / `DO_NOT_DOWNLOAD` (default `TRY_NEXT`).
- `qualityFallback = preferences.qualityFallback().get()` — same enum (default `TRY_NEXT`).

Algorithm (4 steps, full detail in KDoc L173-205):

1. **Top-audio availability check** (L220-242): if `audioPrefs.firstOrNull()` isn't available on ANY server, apply `audioFallback`. `ASK`/`DO_NOT_DOWNLOAD` → return `NoMatch`. `TRY_NEXT` → proceed.
2. **Top-quality availability check** (L246-271): if `qualityPrefs.firstOrNull()` isn't available within any preferred audio, apply `qualityFallback` similarly.
3. **Try all preferred (audio × quality) combinations** in priority order (L274-287): iterate servers (by preference) → audios (by preference, filtered) → videos (by quality preference, filtered). First match → `Selected`.
4. **Best-effort fallback** (L291-303): if BOTH fallbacks are `TRY_NEXT`, pick the first available (any audio, any quality). Otherwise `NoMatch`.

**`Selection`** is a private sealed interface (L362-369): `Selected(video, serverName, audioLabel)` or `NoMatch`.

**Helpers:**
- `orderByName(items, prefs, nameOf)` (L309-313) — sorts items by preference index (un-preferred items get `Int.MAX_VALUE`, sorted last).
- `orderByQuality(videos, qualityPrefs)` (L315-319) — same, for video quality strings.
- `matchesQuality(video, qualityPrefs)` (L321-324) — true if video quality is in the preference list.
- `matchesAudio(audioLabel, audioPrefs)` (L327-330) — true if audio label is in the preference list (or prefs is empty = accept any).

### 5.3 Does `DownloadOrchestrator` require `anilistId`?

**Indirectly YES, but it doesn't enforce it.** `DownloadOrchestrator` itself does NOT have an `anilistId == 0` check. The check is upstream in `AppController.downloadEpisode` (`AppController.kt:509-512`).

However, `enqueueDownload` accepts `anime: DownloadAnimeInfo`, whose `anilistId: Int` field is non-nullable (`DownloadModels.kt:27`). And `buildRequest` (L332-356) propagates that anilistId into the `DownloadRequest.anime.anilistId`, which becomes the composite key (`DownloadTask.kt:41`) and the folder name (`DownloadStorageProvider.kt:88`). Passing `anilistId = 0` through would corrupt the dedup keyspace.

### 5.4 Minimum input to enqueue a download

The orchestrator needs:

1. `anime: DownloadAnimeInfo` — requires `anilistId: Int` (non-null), `title: String`, `coverUrl: String?`, `coverColor: Int?`.
2. `episode: SEpisode` — the source-api episode model (must have `url`, `episode_number`, `name`, optionally `scanlator`).
3. `source: AnimeSource` — used to call the resolver (must be a non-null extension source instance).

Plus, before enqueue:
- `manager.isFolderReady()` must be true (SAF folder set) — checked at `DownloadOrchestrator.kt:66-69` AND `DefaultDownloadManager.kt:116-119`.

So the practical minimum is: (anilistId ≠ 0, episode, source, SAF folder configured). The resolver will then call the source's `getHosterList`/`getVideoList` and either auto-pick or show the picker.

### 5.5 Result type

**`EnqueueResult`** (L384-395): sealed interface
- `Success(taskId: Long)`
- `ShowPicker(servers, anime, episode, source)` — auto-off or fallback=ASK
- `NoSources`
- `Error(message: String)`

The host (`AppController.downloadEpisode`, L524-534) maps these to toasts or to `downloadPickerTarget = result` (which triggers the `DownloadVideoPickerSheet` overlay via `AnikutaRoot.kt:201-212`).

### 5.6 PickerContext (L377-381)

```kotlin
data class PickerContext(
    val anime: DownloadAnimeInfo,
    val episode: SEpisode,
    val source: AnimeSource,
)
```

Carries the resolve result so `enqueueSpecific` doesn't re-resolve. Built by `AppController.enqueuePickedVideo` (`AppController.kt:545-572`) from the `downloadPickerTarget` (an `EnqueueResult.ShowPicker`).

---

## 6. `AppController` download-related methods

**File:** `app/src/main/java/app/confused/anikuta/navigation/AppController.kt`

`AppController` is the Koin-singleton state holder + business-logic coordinator for the app shell (KDoc L64-90). It's the bridge between UI events and the download engine. All download UI events funnel through it.

### 6.1 Construction (Koin-injected dependencies)

`AppController` constructor (L91-108) takes:
- `resolverService: ResolverService` (shared with watch flow)
- `downloadManager: DownloadManager`
- `downloadOrchestrator: DownloadOrchestrator`
- `serverDiscoveryStore: ServerDiscoveryStore`
- (plus tracker/sourceMatcher/etc., not download-related)

### 6.2 Download-tasks flow bridging (L146-160)

```kotlin
private val _downloadTasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
val downloadTasksFlow: StateFlow<Map<String, DownloadTask>> = _downloadTasks.asStateFlow()

init {
    scope.launch {
        downloadManager.episodeDownloadStates.collect { tasks ->
            _downloadTasks.value = tasks
        }
    }
}
```

`downloadManager.episodeDownloadStates` (`DownloadManager.kt:121`, `DefaultDownloadManager.kt:108-109`) is a `Flow<Map<String, DownloadTask>>` keyed by `"$anilistId:$episodeUrl"`. AppController bridges it into a `StateFlow` so both `.value` access (for download action functions) and Flow collection (for composables) work.

### 6.3 Resolving state (immediate spinner)

```kotlin
val resolvingEpisodes = mutableStateMapOf<String, Boolean>()   // L132
```

A Compose `SnapshotStateMap` keyed by episode URL. Set to `true` at `AppController.kt:519` (just before `downloadOrchestrator.enqueueDownload` is launched) and cleared in the `finally` block (L539). Merged into `getDownloadStates` as `EpisodeDownloadState.Resolving` so the row shows an immediate spinner before the queue task exists.

### 6.4 Key methods (cite list)

| Method | File:line | Purpose |
|---|---|---|
| `resolveEpisode(episode, source, episodeList, watchCtx, anilistId)` | L358-433 | Offline short-circuit → streaming resolver sheet |
| `onVideoSelected(video: ResolverVideo)` | L440-487 | Build WatchRequest from picked video (streaming path) |
| `retryResolve()` | L490-493 | Re-call `resolveEpisode` with stored target |
| `downloadEpisode(episode, source, watchCtx, anilistId)` | L503-542 | **★ anilistId==0 gate (L509-512)** + orchestrator call |
| `enqueuePickedVideo(video, serverName, audioLabel)` | L545-572 | Picker → orchestrator.enqueueSpecific |
| `cancelDownload(anilistId, episodeUrl)` | L576-586 | Lookup task by `"$anilistId:$episodeUrl"` → `manager.cancelDownload(taskId)` |
| `resumeDownload(anilistId, episodeUrl)` | L588-591 | Same lookup → `manager.resumeDownload(taskId)` |
| `retryDownload(anilistId, episodeUrl)` | L593-596 | Same lookup → `manager.retryDownload(taskId)` |
| `deleteDownload(anilistId, episodeUrl)` | L598-604 | Same lookup → `manager.deleteDownload(taskId)` + Toast "Download deleted" |
| `getDownloadStates(anilistId, tasksMap)` | L617-642 | Build `Map<String, EpisodeDownloadState>` (keyed by episode URL) for the detail page |
| `checkForDownloadErrors(tasksMap)` | L648-661 | Observe ERROR transitions, show Toast for new errors |

### 6.5 `getDownloadStates` (L617-642) — the row-state builder

```kotlin
fun getDownloadStates(
    anilistId: Int,
    tasksMap: Map<String, DownloadTask>,
): Map<String, EpisodeDownloadState> {
    val states = tasksMap
        .filterKeys { it.startsWith("$anilistId:") }    // ← ★ anilistId prefix filter
        .mapKeys { it.key.substringAfter(':') }          // ← strip prefix, key by episodeUrl
        .mapValues { (_, task) ->
            when (task.status) {
                DownloadStatus.QUEUED -> EpisodeDownloadState.Queued
                DownloadStatus.DOWNLOADING -> EpisodeDownloadState.Downloading(task.progress)
                DownloadStatus.PAUSED -> EpisodeDownloadState.Paused
                DownloadStatus.ERROR -> EpisodeDownloadState.Error(task.errorMessage)
                DownloadStatus.COMPLETED -> EpisodeDownloadState.Downloaded
                DownloadStatus.CANCELLED -> EpisodeDownloadState.NotDownloaded
            }
        }
        .toMutableMap()
    resolvingEpisodes.forEach { (episodeUrl, isResolving) ->
        if (isResolving) states[episodeUrl] = EpisodeDownloadState.Resolving
    }
    return states
}
```

**Critical for the source-switching break:** `filterKeys { it.startsWith("$anilistId:") }` (L622) means ONLY tasks whose composite key starts with `"<anilistId>:"` are shown. After a source switch, the new episodes have new URLs that DON'T match any task's `episodeUrl` — so the corresponding row state is `NotDownloaded` (the default, from `EpisodesSection.kt:316`).

### 6.6 Download-error toast (L648-661)

`checkForDownloadErrors` is called from `AnikutaRoot.kt:79-81` on every `downloadTasksMap` emission. It tracks previously-seen ERROR task IDs in `previousErrorIds: Set<Long>` (L163) and shows a Toast `"Download failed: $msg"` (LENGTH_LONG) for each NEW error. Old non-error IDs are pruned to avoid unbounded set growth.

### 6.7 Navigator push for offline playback from DownloadedFilesScreen

`DownloadedFilesDestination` (`Destinations.kt:369-381`) wires `onPlayEpisode = { anilistId, episodeUrl -> appController.pushDetail(anilistId) }` — pushes the AniList detail page. The user must then tap the episode row, which goes through `resolveEpisode` (which checks `isEpisodeDownloaded`). There is NO direct offline-playback path from `DownloadedFilesScreen` (the `episodeUrl` arg is captured but unused — the host only uses `anilistId`).

---

## 7. UI layer (`:feature:download`)

### 7.1 Main files

| File | Lines | Purpose |
|---|---|---|
| `DownloadsScreen.kt` | 569 | The download QUEUE screen (active + completed-but-not-yet-auto-cleared). Anime-sectioned cards. |
| `DownloadedFilesScreen.kt` | 206 | The downloaded LIBRARY screen (completed only, grouped by anime). |
| `DownloadSettingsScreen.kt` | 527 | Settings — SAF folder, method, concurrency, auto-download, preference lists, fallbacks, advanced. |
| `DownloadVideoPickerSheet.kt` | 232 | Modal bottom sheet for manual video pick (auto-download OFF or fallback=ASK). |
| `DownloadViewModel.kt` | 105 | VM: combines active + completed + folderReady; auto-clears after 10s. |
| `DownloadUiState.kt` | 41 | State DTO + `DownloadedAnimeKey` grouping key. |
| `ExtensionSourceInfo.kt` | — | DTO for the per-extension server-preference section in settings. |
| `DownloadsMoreEntries.kt` | — | "More" tab entries that lead to downloads. |
| `components/DragReorderableList.kt` | — | Drag-reorder list for preference lists. |
| `components/DownloadedAnimeCard.kt` | — | (Card composable — `DownloadedFilesScreen` has its own copy.) |
| `components/QueueRow.kt` | — | (Legacy queue row — `DownloadsScreen` has its own inline.) |
| `components/DownloadsEmptyState.kt` | — | (Empty-state composable.) |
| `di/DownloadModule.kt` | — | Koin module. |

### 7.2 `DownloadsScreen` — the queue UI

`DownloadsScreen.kt:81-221` — main composable.

**State source:** `viewModel.state.collectAsStateWithLifecycle()` (L87) → `DownloadUiState` with `queue: List<DownloadTask>` (active tasks) and `downloaded: Map<DownloadedAnimeKey, List<DownloadTask>>` (completed tasks grouped by anime).

**Layout:**
- `CollapsingHeader("Downloads")` with a settings gear + a "Downloaded" icon (only shows if `state.downloaded.isNotEmpty()` — L131-139).
- `DownloadActionBar` (bulk pause/resume/retry/cancel all) when `queue.isNotEmpty()`.
- Summary chips: downloading / queued / paused / failed counts.
- `LazyColumn` of `AnimeSectionCard`s, grouped by `it.request.anime.title` (L118-120 — note: by **title**, not anilistId; this is the QUEUE grouping, distinct from `DownloadedFilesScreen`'s anilistId grouping).
- Each card contains an `EpisodeRow` per task with info pills (server/audio/quality/size/percentage/status) + a 3-dot menu → `EpisodeMenuSheet` for pause/resume/cancel/retry.
- Empty state: `DownloadsEmptyStateContent()`.
- Auto-requests POST_NOTIFICATIONS permission on Android 13+ (L99-107).

**`AnimeSectionCard`** groups queue tasks by anime title (NOT anilistId) — `DownloadsScreen.kt:118-120`. (Cosmetic grouping — for the queue view, two unrelated anime with the same title would collide, but this is unlikely in practice.)

### 7.3 `DownloadedFilesScreen` — the library UI

`DownloadedFilesScreen.kt:62-116` — main composable.

**State source:** same `viewModel.state` — uses `state.downloaded: Map<DownloadedAnimeKey, List<DownloadTask>>` (L72).

**Layout:**
- `CollapsingHeader("Downloaded")`.
- Empty state if `downloaded.isEmpty()`.
- `LazyColumn` over `downloaded.forEach { (animeKey, episodes) -> DownloadedAnimeCard(...) }`.
- Items keyed by `"downloaded_${animeKey.anilistId}"` (L101) — so anilistId is the LazyColumn identity.
- Tap an episode → `onPlay(animeKey.anilistId, episodeUrl)` (`DownloadedFilesScreen.kt:104-110`).
- Delete per episode → `viewModel.deleteEpisode(taskId)` (L108).
- Delete all per anime → `viewModel.deleteAnime(animeKey.anilistId)` (L109).

**`DownloadedAnimeCard`** (`DownloadedFilesScreen.kt:118-206`):
- Header: cover + title + episode count + delete-all + expand chevron.
- Episodes sorted by `episodeNumber` (`task.request.episode.episodeNumber`, L172) — ascending.
- Each row: `EP <number>`, episode name, quality pill, delete button.
- Tap row → `onPlay(task.request.episode.episodeUrl)` (L175) → bubbles up to `onPlayEpisode(anilistId, episodeUrl)`.

### 7.4 `DownloadViewModel` + `DownloadUiState` — UI state + grouping key

**`DownloadViewModel`** (`DownloadViewModel.kt:26-105`):

- Combines `manager.activeDownloads` + `manager.completedDownloads` + `preferences.downloadFolderUri().changes()` into `DownloadUiState` (L36-49).
- **Auto-clear-after-10s** (L51-68): each completed task in the active-queue flow is collected; a per-task coroutine waits 10s then calls `manager.removeFromQueue(task.id)`. The file stays on disk; the task disappears from the active-queue section. (Per the owner's spec — see L52-55 KDoc.)
- Forwards actions to the manager: `pause`, `resume`, `cancel`, `retry`, `deleteEpisode`, `deleteAnime`, `setDownloadFolder`.

**`DownloadUiState`** (`DownloadUiState.kt:16-23`):

```kotlin
data class DownloadUiState(
    val queue: List<DownloadTask> = emptyList(),
    val downloaded: Map<DownloadedAnimeKey, List<DownloadTask>> = emptyMap(),
    val folderReady: Boolean = false,
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = queue.isEmpty() && downloaded.isEmpty()
}
```

**`DownloadedAnimeKey`** (`DownloadUiState.kt:29-34`):

```kotlin
data class DownloadedAnimeKey(
    val anilistId: Int,        // ★ the grouping key
    val title: String,
    val coverUrl: String?,
    val coverColor: Int?,
)
```

**`groupByAnime`** (`DownloadViewModel.kt:93-104`):

```kotlin
return tasks
    .groupBy {
        DownloadedAnimeKey(
            anilistId = it.request.anime.anilistId,    // ★ by anilistId
            title = it.request.anime.title,
            coverUrl = it.request.anime.coverUrl,
            coverColor = it.request.anime.coverColor,
        )
    }
    .toSortedMap(compareBy { it.title.lowercase() })
```

**Summary:** the UI groups completed downloads by **`anilistId`** (not `episodeUrl`, not `sourceId`). This means downloads DO survive a source switch AT THE UI level (the grouping key is anilistId). The break is in the DETAIL-PAGE row-state matching (§4), not in the library grouping.

### 7.5 `DownloadSettingsScreen` — what's configurable

**File:** `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadSettingsScreen.kt`

Sections (per the KDoc L54-66 + verified by reading L79-307):

1. **Download method** (L135-180)
   - Segmented toggle: Normal vs Advanced (`preferences.method()`).
   - When Advanced: 3 sliders — Parallel threads (1..8), Max retries per chunk (0..10), Min size for multi-threading (1..20 MB).

2. **General** (L187-219)
   - **Download folder** — SAF picker (`ActivityResultContracts.OpenDocumentTree`). Takes persistable permission, stores URI in `preferences.downloadFolderUri()`. Shows current folder name (or "Not set — tap to choose").
   - **Show download button** — toggle (`preferences.showDownloadButton()`).
   - **Wi-Fi only** — toggle (`preferences.wifiOnly()`).
   - **Concurrent downloads** — slider 1..5 (`preferences.concurrentDownloads()`).

3. **Auto-download** (L224-231)
   - **Auto-download** — toggle (`preferences.autoDownload()`). When ON, the orchestrator auto-picks; when OFF, the picker sheet is shown.

4. **Preference lists** (only when `autoDownload == true`, L236-310)
   - **Preferred quality** — drag-reorderable list (`preferences.qualityPreferences()`). Default `["1080p", "720p", "480p", "360p"]`.
     - Fallback toggle: If unavailable → TRY_NEXT / ASK / DO_NOT_DOWNLOAD (`preferences.qualityFallback()`).
   - **Preferred audio** — drag-reorderable list (`preferences.audioPreferences()`). Default `["SUB", "DUB"]`.
     - Fallback toggle (`preferences.audioFallback()`).
   - **Preferred server** — per-extension collapsible sections (`preferences.serverPreferences()` — `Map<sourceId, List<serverName>>`). Uses `ServerDiscoveryStore` to merge discovered servers with the user's order.
     - Fallback toggle (`preferences.serverFallback()`).

5. **(No "auto-download new episodes" toggle exists.)** Auto-download in this codebase means "auto-pick the best video when the user taps download" — NOT background auto-download of new episodes.

### 7.6 `DownloadVideoPickerSheet`

**File:** `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadVideoPickerSheet.kt` (232 lines)

A `ModalBottomSheet` (`dragHandle = null`, full-expanded skipPartiallyExpanded) shown by `AnikutaRoot.kt:201-212` when `appController.downloadPickerTarget != null`. Renders the same 3-tier hierarchy (Server → Audio → Quality) as the resolver sheet, but for downloads. The user taps a quality button → `onVideoSelected(video, serverName, audioLabel)` → `AppController.enqueuePickedVideo`.

---

## 8. `DownloadPreferences` + `DownloadNotificationManager`

### 8.1 `DownloadPreferences` catalog

**File:** `core/download/src/main/java/app/confused/anikuta/core/download/DownloadPreferences.kt:31-180`

| Preference | Key (constant) | Default | Type |
|---|---|---|---|
| `downloadFolderUri()` | `pref_dl_folder_uri` | `""` | `String` (SAF tree URI) |
| `method()` | `pref_dl_method` | `DownloadMethod.NORMAL` | enum `NORMAL` / `ADVANCED` |
| `wifiOnly()` | `pref_dl_wifi_only` | `true` | `Boolean` |
| `concurrentDownloads()` | `pref_dl_concurrent` | `3` | `Int` (UI clamps 1..5) |
| `showDownloadButton()` | `pref_dl_show_button` | `true` | `Boolean` |
| `autoDownload()` | `pref_dl_auto_pick` | `true` | `Boolean` |
| `qualityPreferences()` | `pref_dl_quality_prefs` | `["1080p","720p","480p","360p"]` | `List<String>` (JSON) |
| `audioPreferences()` | `pref_dl_audio_prefs` | `["SUB","DUB"]` | `List<String>` (JSON) |
| `serverPreferences()` | `pref_dl_server_prefs` | `emptyMap()` | `Map<String, List<String>>` (JSON, keyed by sourceId-as-string) |
| `qualityFallback()` | `pref_dl_quality_fallback` | `TRY_NEXT` | enum `TRY_NEXT` / `ASK` / `DO_NOT_DOWNLOAD` |
| `audioFallback()` | `pref_dl_audio_fallback` | `TRY_NEXT` | enum (same) |
| `serverFallback()` | `pref_dl_server_fallback` | `TRY_NEXT` | enum (same) |
| `advancedThreadCount()` | `pref_dl_adv_threads` | `4` | `Int` (UI clamps 1..8) |
| `advancedMaxRetries()` | `pref_dl_adv_retries` | `3` | `Int` (UI clamps 0..10) |
| `advancedMinSizeMb()` | `pref_dl_adv_min_size_mb` | `5` | `Int` (UI clamps 1..20) |

**Defaults live in** `DownloadPreferences.Companion` (L158-178): `DEFAULT_QUALITY_PREFS`, `DEFAULT_AUDIO_PREFS`. Other defaults are inline in each `getX()` call.

**Two enums** (L183-204):
- `DownloadMethod { NORMAL, ADVANCED }`
- `FallbackStrategy { TRY_NEXT, ASK, DO_NOT_DOWNLOAD }`

### 8.2 `DownloadNotificationManager` — summary

**File:** `core/download/src/main/java/app/confused/anikuta/core/download/DownloadNotificationManager.kt` (191 lines)

**One channel:** `CHANNEL_ID = "anikuta_downloads"` (L183), name "Downloads", `IMPORTANCE_LOW` (L161). Created at construction (L34-36 → `ensureChannel()` L156-173).

**Three notification types:**

1. **Active-download summary** (`updateProgress(active: List<DownloadTask>)`, L51-102):
   - Single ongoing notification, `SUMMARY_ID = 9001` (L184).
   - Title: `"${anime.title} — EP ${epNum}"` (single task) or `"Downloading N episodes"` (multi).
   - Body: `"$progress% • $downloaded / $total"` (or just downloaded if total unknown).
   - Progress bar: 0..100, indeterminate when `progress <= 0`.
   - `setOngoing(true)`, `setOnlyAlertOnce(true)`, `setSilent(true)`.
   - **Throttled** to once per `PROGRESS_THROTTLE_MS = 800ms` (L187, L58-59).
   - Tap intent: app launch intent (no deep link — see KDoc L24-26).
   - Cancelled when `active.isEmpty()` (L53-56).
   - **Resilience:** the entire body is wrapped in try/catch (L97-101) so a notification failure NEVER crashes the download engine. `firstOrNull` (not `first`) is used to avoid `NoSuchElementException` (KDoc L42-49 — this was the path of a prior enqueue-time crash). `notify()` is wrapped for `SecurityException` (POST_NOTIFICATIONS denied) and generic `Exception`.

2. **One-shot completion** (`notifyCompleted(task)`, L105-120):
   - `task.id.toInt() + COMPLETION_OFFSET` (= 10_000) as the notification ID.
   - `stat_sys_download_done` icon. `"Download complete"` + `"${anime.title} — EP ${epNum}"`. Auto-cancel. PRIORITY_LOW.

3. **One-shot error** (`notifyError(task)`, L123-138):
   - `task.id.toInt() + ERROR_OFFSET` (= 20_000) as the notification ID.
   - `stat_notify_error` icon. `"Download failed"` + `"${anime.title} — ${errorMessage}"`. Auto-cancel. PRIORITY_DEFAULT.

The summary notification is driven by a hot StateFlow collector in `DefaultDownloadManager` (`DefaultDownloadManager.kt:87-97`). One-shot completion/error notifications are posted by the queue's `onTaskCompleted` / `onTaskError` callbacks (`DefaultDownloadManager.kt:72-73`, `DownloadQueue.kt:235-236, 248-249, 260-261`).

---

## 9. `DownloadQueue` — identification mechanism

### 9.1 Identification model

**File:** `core/download/src/main/java/app/confused/anikuta/core/download/DownloadQueue.kt` (315 lines)

There are **two** identification mechanisms in play:

1. **`task.id: Long`** — a monotonic ID assigned at enqueue time by `idCounter.getAndIncrement()` (`DownloadQueue.kt:98, 61`). Survives restarts (initialized from `loadMaxId() + 1` at L61, which reads `store.getAll().maxOfOrNull { it.id }` at L305). Used by:
   - `pause(taskId)` (L110)
   - `resume(taskId)` (L121)
   - `cancel(taskId)` (L123)
   - `retry(taskId)` (L137)
   - `removeCompleted(taskId)` (L148)
   - `jobs[task.id] = job` (the per-task coroutine Job, L270)
   - `mutateTask(taskId, transform)` (L273-277)
   - The notification offsets (`task.id.toInt() + COMPLETION_OFFSET`, etc.)
   - `DownloadViewModel` action methods (`pause(taskId)`, `cancel(taskId)`, etc.)
   - `AppController.checkForDownloadErrors` (tracks `previousErrorIds: Set<Long>` of task IDs)

2. **`task.key: String`** = `"${request.anime.anilistId}:${request.episode.episodeUrl}"` (`DownloadTask.kt:41`). This is the **composite dedup + offline-lookup key**. Used by:
   - `enqueue` dedup check: `_tasks.firstOrNull { it.key == keyFor(request) }` (`DownloadQueue.kt:87`, `keyFor` at L309-310)
   - `DefaultDownloadManager.findTask(anilistId, episodeUrl)` (`DefaultDownloadManager.kt:202-204`) — used by every offline-playback query (`isEpisodeDownloaded`, `getDownloadedVideoUri`, `getDownloadedSubtitleUris`).
   - `DefaultDownloadManager.episodeDownloadStates` Flow (`DefaultDownloadManager.kt:108-109`) — `queue.tasks.associateBy { it.key }` — the per-episode-row UI map.
   - `AppController.getDownloadStates` (`AppController.kt:622`) — `filterKeys { it.startsWith("$anilistId:") }` then `mapKeys { it.key.substringAfter(':') }`.
   - `AppController.cancelDownload` / `resumeDownload` / `retryDownload` / `deleteDownload` (`AppController.kt:584, 589, 594, 599`) — they look up the task by `"$anilistId:$episodeUrl"` from `downloadTasksFlow.value` to get the `task.id`, then call the manager.

**So:** the queue identifies items internally by `task.id: Long` (assigned at enqueue, persisted), but the **external API** (UI, offline lookup, dedup) uses the composite key `"$anilistId:$episodeUrl"`. The composite key is the *de facto* identifier for cross-system lookups; `task.id` is the internal DB-style PK.

### 9.2 State machine

`DownloadStatus` enum (referenced from `DownloadTask.kt:17-25` + `DownloadQueue.kt:111-145`):

| Status | Meaning | Transitions |
|---|---|---|
| `QUEUED` | In queue, waiting for a permit | enqueue → QUEUED; resume → QUEUED; retry (from ERROR) → QUEUED |
| `DOWNLOADING` | Actively downloading | QUEUED → DOWNLOADING (when permit acquired) |
| `PAUSED` | User-paused | QUEUED/DOWNLOADING → PAUSED (via `pause()`) |
| `ERROR` | Failed | DOWNLOADING → ERROR (on exception) |
| `COMPLETED` | On disk, ready for offline playback | DOWNLOADING → COMPLETED |
| `CANCELLED` | Cancelled (transient — purged on restart) | any → CANCELLED (via `cancel()`); filtered out by `purgeCancelled()` at startup |

`DownloadTask.isInQueue` (`DownloadTask.kt:44-48`): true for QUEUED/DOWNLOADING/PAUSED/ERROR (i.e. NOT terminal). Used to split `activeDownloads` (in-queue) from `completedDownloads` (COMPLETED) in `DefaultDownloadManager.kt:99-103`.

### 9.3 Concurrency model

`Semaphore` with `concurrentDownloads().get().coerceIn(1, 5)` permits (`DownloadQueue.kt:74, 296-297`).

- `tryStartNext()` (L180-188): finds first QUEUED task, calls `launchDownload` (cheap if nothing to start).
- `launchDownload` (L190-271): launches a coroutine; inside `permits.withPermit { ... }` re-confirms status = QUEUED, sets DOWNLOADING, calls `downloader.download(task) { ... }`. The semaphore blocks coroutine suspension until a permit is free.
- On completion/pause/cancel/error, the `finally` block (L264-268) removes the job from `jobs` map and calls `tryStartNext()` to start the next queued task.
- `refreshConcurrency()` (L154-162): rebuilds the `Semaphore` when the pref changes (called from somewhere — actually, looking at the code, it's defined but I didn't find the caller; presumably `DownloadAppModule` or the preferences screen wires it via a `changes()` collector). Note: rebuilding the Semaphore doesn't carry over permits, which could over-grant briefly — but the comment at L154 says "Re-evaluate the concurrency limit (call when the pref changes)".

### 9.4 Persistence

The entire task list is persisted as a single JSON-serialized `List<DownloadTask>` in `DownloadStore` (`DownloadStore.kt:28-75`). Key: `pref_download_tasks_v1`.

- `persistNow()` (`DownloadQueue.kt:291-294`) — called on every state change (enqueue/pause/resume/cancel/retry/completed/error).
- `persistThrottled()` (L283-289) — called per byte-tick; throttled to once per `PERSIST_INTERVAL_MS = 1000ms` (L313).
- `purgeCancelled()` (`DownloadStore.kt:62-70`) — called at queue construction (`DownloadQueue.kt:58`); removes CANCELLED tasks from the persisted list on startup.

---

## 10. Summary of evidence

### 10.1 The anilistId entry point — marked at every step

| Step | File:line | anilistId source |
|---|---|---|
| Voyager route | `Destinations.kt:110` (`AnimeDetailDestination(animeId: Int)`) | route arg |
| Voyager route | `Destinations.kt:158-161` (`ExtensionAnimeDetailDestination(..., anilistId: Int? = null)`) | nullable; `?: 0` fallback at L173 |
| UI callback wiring | `Destinations.kt:133-134` / `:189-190` | passed as `animeId` / `downloadKey` to `AppController.downloadEpisode` |
| Hard gate | `AppController.kt:509-512` | `if (anilistId == 0) { Toast; return }` |
| Build DTO | `AppController.kt:513-517` | `DownloadAnimeInfo(anilistId = anilistId, ...)` |
| Orchestrator | `DownloadOrchestrator.kt:61-65, 344` | `anime.anilistId` flows into `DownloadRequest.anime` |
| Composite key | `DownloadTask.kt:41` | `"${request.anime.anilistId}:${request.episode.episodeUrl}"` |
| Dedup at enqueue | `DownloadQueue.kt:87, 309-310` | `keyFor(request) = "${request.anime.anilistId}:${request.episode.episodeUrl}"` |
| Folder name | `DownloadStorageProvider.kt:88` | `"$safeTitle [${anime.anilistId}]"` |
| Episode folder | `DownloadStorageProvider.kt:92-95` | episode-number-keyed (`"Episode %03d"`) — NOT anilistId |
| Metadata cache | `HttpDownloader.kt:487-495` | `EpisodeMetadataCache(anilistId = task.request.anime.anilistId, ...)` |
| Offline lookup | `DefaultDownloadManager.kt:163-169, 202-204` | `findTask(anilistId, episodeUrl)` by composite key |
| WatchRequest (offline) | `AppController.kt:380` | `anilistId = anilistId` |
| WatchRequest (streaming) | `AppController.kt:468` | `anilistId = target.anilistId` |
| DownloadedFilesScreen grouping | `DownloadViewModel.kt:97` | `groupBy { DownloadedAnimeKey(anilistId = it.request.anime.anilistId, ...) }` |
| Detail-page row state filter | `AppController.kt:622` | `filterKeys { it.startsWith("$anilistId:") }` |

### 10.2 The source-switching break — single-line pinpoint

**File:** `core/download/src/main/java/app/confused/anikuta/core/download/DefaultDownloadManager.kt:167`

```kotlin
override suspend fun isEpisodeDownloaded(anilistId: Int, episodeUrl: String): Boolean {
    val task = findTask(anilistId, episodeUrl)
    if (task?.status == DownloadStatus.COMPLETED) return true
    if (task == null) return false       // ← ★ THIS LINE
    return storage.isEpisodeDownloaded(task.request.anime, task.request.episode)
}
```

`findTask` (`DefaultDownloadManager.kt:202-204`) matches by `"$anilistId:$episodeUrl"`. When the user switches sources, the new episode list has different `episodeUrl`s, so `findTask` returns null, so `isEpisodeDownloaded` returns false, so the offline short-circuit at `AppController.kt:370` fails, so the resolver sheet opens (or streams directly), so the user re-streams the episode they already downloaded.

**The on-disk file is NOT lost** — it's still in `<folder>/Anime Title [anilistId]/Episode 001/video.mp4`. The break is in the LOOKUP, not the storage.

**The `DownloadedFilesScreen` STILL SHOWS the download** (`DownloadViewModel.kt:93-104` groups by anilistId, not episodeUrl) — so the user sees their download listed but can't actually play it offline after switching sources.

**Fix:** add a fallback in `findTask` (or `isEpisodeDownloaded`) that, when no task matches `(anilistId, episodeUrl)`, scans completed tasks for the same `anilistId` whose `episode.episodeNumber` matches the requested episode's number. The on-disk folder is already episode-number-keyed (`DownloadStorageProvider.kt:92-95`), so a filesystem fallback (`storage.findEpisodeDir(anime, episode)`) would also work — but it's currently gated behind `if (task == null) return false` and never reached.

### 10.3 No cross-source matching mechanism exists

Verified by grep across `core/download/` and `app/src/main/.../download/`: `episodeNumber` is used ONLY for folder names (`DownloadStorageProvider.kt:93`), UI display (`DownloadedFilesScreen.kt:172, 179`, `DownloadNotificationManager.kt:68, 110`), and logging. There is no `getDownloadedByEpisodeNumber(anilistId, episodeNumber)` method anywhere in `DownloadManager` or its implementations.

### 10.4 `DownloadOrchestrator` responsibilities + minimum input

- **Responsibilities:** resolve → select-best (or show picker) → enqueue. Auto-pick algorithm respects user preference lists + fallback strategies. Lives in `:app` to bridge `:feature:video-resolver` and `:core:download` (Rule §14).
- **Minimum input:** `DownloadAnimeInfo` (with non-zero `anilistId`), `SEpisode` (with `url`, `episode_number`, `name`), `AnimeSource` (non-null extension source). Plus a configured SAF folder (`manager.isFolderReady()`). The orchestrator itself does NOT check `anilistId == 0` — that gate is upstream in `AppController.downloadEpisode`.

### 10.5 The UI query mechanism — what key does `DownloadedFilesScreen` use?

**`DownloadedFilesScreen`** uses **`anilistId`** as the grouping/display key (via `DownloadedAnimeKey.anilistId` — `DownloadUiState.kt:31`, `DownloadViewModel.kt:97`). It does NOT filter by `episodeUrl`. So downloads DO survive a source switch at the library level — they're listed under the anime's anilistId regardless of which source produced them.

**`AppController.getDownloadStates`** (the detail-page row-state builder) uses **`"$anilistId:$episodeUrl"`** as the lookup key (`AppController.kt:622-623`). So at the detail-page level, downloads DO NOT survive a source switch — the row state for the new-source episode shows `NotDownloaded` even though a download exists for the same `anilistId` + same `episodeNumber` under the old-source `episodeUrl`.

This is the **asymmetry that causes the bug**: the library view (anilistId-keyed) shows the download; the detail-page row (anilistId+episodeUrl-keyed) doesn't.

### 10.6 `DownloadPreferences` catalog — see §8.1 table.

### 10.7 `DownloadQueue` identification mechanism — see §9.1.

---

## 11. Notes / contradictions resolved

- **Task description mentioned "MainActivity.resolveEpisode":** `MainActivity` does NOT contain `resolveEpisode`. `MainActivity.kt` is only 96 lines and handles theme + OAuth callback (`MainActivity.kt:34-96`). `resolveEpisode` lives in `AppController.kt:358-433`. The previous architecture (per `AppController` KDoc L67-78) had all this logic inside `MainActivity`'s `AnikutaApp()` composable (~1174 lines); it was extracted into `AppController` (a Koin singleton) so Voyager screens could stay thin. The task description's "MainActivity.resolveEpisode" is the pre-refactor name.

- **Task description mentioned `ResolverService` alongside `VideoResolverSheet`:** the resolver has TWO entry points. `VideoResolverSheet` is the WATCH-flow sheet (user picks a video to stream). `DownloadVideoPickerSheet` is the DOWNLOAD-flow sheet (user picks a video to download — used when auto-download is OFF or fallback=ASK). Both consume the same `ResolverResult.Success.servers: List<ResolverServer>` produced by `ResolverService.resolve(source, episode)`. The watch path is `AppController.resolveEpisode` → resolver sheet → `onVideoSelected` → `WatchRequest`. The download path is `AppController.downloadEpisode` → orchestrator → (if ShowPicker) `DownloadVideoPickerSheet` → `enqueuePickedVideo` → orchestrator.enqueueSpecific.

- **Task description asked "Does `DownloadOrchestrator` require anilistId?":** It doesn't enforce it (no `== 0` check inside the orchestrator), but its input type `DownloadAnimeInfo.anilistId: Int` is non-nullable, and downstream every consumer (composite key, folder name, offline lookup) treats it as the primary key. The gate is upstream in `AppController.downloadEpisode:509-512`.

- **Task description asked "What's the user-facing behavior [of the anilistId==0 gate]?":** A Toast `"Cannot download — anime not linked"` (LENGTH_SHORT). No file is created; no orchestrator call; the resolving-spinner state is never set. Silent except for the toast.

- **Task description asked "Is there ANY mechanism that tries to match by episode number across sources?":** No. Verified by grep across `core/download/` — `episodeNumber` is used only for folder names, UI display, and logging. No lookup-by-episode-number exists in `DownloadManager` or its implementations. This is the root cause of the source-switching break (§4).

- **Task description asked "How does [resolveEpisode] match a downloaded file to the episode being played? By anilistId + episodeUrl? By episode number?":** By **`anilistId + episodeUrl`** — the composite key. NOT by episode number. See §3.2.

- **Task description asked "What's the key in the UI state [for DownloadedFilesScreen]?":** The UI state's grouping key is `DownloadedAnimeKey(anilistId, title, coverUrl, coverColor)` (`DownloadUiState.kt:29-34`). The `anilistId` is the functional key; the other fields are display fields. The LazyColumn item key is `"downloaded_${animeKey.anilistId}"` (`DownloadedFilesScreen.kt:101`).

- **The `DownloadedFilesScreen` `onPlayEpisode` is misleading:** the callback signature is `(Int, String) -> Unit` (anilistId, episodeUrl) but the host (`Destinations.kt:376-378`) only uses the `anilistId` — it pushes the detail page. The `episodeUrl` is captured but discarded. So tapping a downloaded episode in the library view does NOT directly play it offline; it just opens the detail page (where the user must tap the episode row, which then re-checks `isEpisodeDownloaded`). If the user has switched sources, the re-check fails (§4).

---

**End of evidence file. No code modified.**
