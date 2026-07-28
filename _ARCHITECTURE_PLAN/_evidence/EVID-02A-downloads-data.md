# EVID-02A — Downloads: Data Models, Composite Keys, Persistence, Storage Layer

**Agent:** Explore (research-only — no code modified)
**Task ID:** EVID-02A-DOWNLOADS-DATA
**Scope:** The download system's DATA MODELS, COMPOSITE KEYS, PERSISTENCE, and ON-DISK STORAGE LAYER only. A second agent (EVID-02B) covers the pipeline/UI/offline-playback. The boundary is the data shape + the I/O classes; the UI/pipeline glue lives in `:app` (`DownloadOrchestrator`, `AppController.downloadEpisode`) and is referenced here only where it touches data shape.
**Codebase:** `/home/z/my-project/anikuta/ANIKUTA_PROJECT/ANIKUTA/`
**Module of interest:** `:core:download` (everything under `core/download/src/main/java/app/confused/anikuta/core/download/`) + the cross-cutting consumers in `:app` and `:feature:download`.

---

## 0. File map (every file in `:core:download`)

All paths under `core/download/src/main/java/app/confused/anikuta/core/download/`:

| File | Role |
|---|---|
| `DownloadModels.kt` | `DownloadAnimeInfo`, `DownloadEpisodeInfo`, `DownloadedEpisode`, `DownloadTrack`, `TrackKind` |
| `DownloadRequest.kt` | `DownloadRequest` (the already-resolved input to the engine) |
| `DownloadTask.kt` | `DownloadTask` (the persisted queue item + the `key` composite key) |
| `DownloadStatus.kt` | `DownloadStatus` enum (the lifecycle state machine) |
| `DownloadManager.kt` | The interface (every method takes `anilistId` or `taskId`) |
| `DefaultDownloadManager.kt` | The only impl; wires Queue + Storage + Notifier |
| `DownloadQueue.kt` | State machine + `Semaphore`-based concurrency + dedup-by-key |
| `DownloadStore.kt` | JSON-list persistence via `PreferenceStore` (pref key `pref_download_tasks_v1`) |
| `DownloadStorageProvider.kt` | SAF folder I/O; owns the `<root>/ANIKUTA/downloads/anime/<Title [id]>/Episode NNN/` layout + `EpisodeMetadataCache` model |
| `DownloadPreferences.kt` | All download prefs (folder URI, method, wifi-only, concurrency, auto-pick lists, advanced settings) + `DownloadMethod` enum + `FallbackStrategy` enum |
| `TempDownloadCache.kt` | Internal-cache `<cacheDir>/anikuta_downloads/<taskId>/` for in-progress downloads |
| `HttpDownloader.kt` | The "DEFAULT"/Normal method pipeline (download → validate → publish) |
| `advanced/AdvancedHttpDownloader.kt` | Multi-threaded Range-request downloader with resume |
| `advanced/DownloadResumeManager.kt` | Per-task `resume.json` + `chunk_N.part` file manager |
| `HlsDownloader.kt` | `.m3u8` parser + segment downloader + PNG-header stripper |
| `VideoTypeDetector.kt` | URL/Content-Type → `DIRECT_VIDEO | HLS_STREAM | DASH_STREAM | HTML_PAGE | UNKNOWN` |
| `DynamicProgressTracker.kt` | 0–90% progress cap + unknown-total estimator |
| `ServerDiscoveryStore.kt` | Per-source discovered-server-names cache (keyed by `sourceId.toString()`) |
| `DownloadLogger.kt` | Tagged logger |
| `DownloadNotificationManager.kt` | Android foreground + completion notifications |
| `di/DownloadModule.kt` | Koin module; binds `DownloadManager → DefaultDownloadManager` |

Files NOT in `:core:download` but directly data-touching:
- `app/src/main/java/app/confused/anikuta/download/DownloadOrchestrator.kt` — builds `DownloadRequest` from `SEpisode` + `ResolverVideo`; lives in `:app` because `:core:download` cannot import `:feature:video-resolver` (Rule §14).
- `app/src/main/java/app/confused/anikuta/navigation/AppController.kt` — `downloadEpisode()` (the `anilistId == 0` gate at L509) + the offline-playback short-circuit at L370.
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/EpisodeDownloadState.kt` — UI-side sealed type derived from `DownloadTask` (deliberately NOT in `:core:download`).
- `feature/download/src/main/java/app/confused/anikuta/feature/download/ExtensionSourceInfo.kt` — UI DTO carrying `sourceId: Long`.

---

## 1. Data models — FULL FIELD DUMP

### 1.1 `DownloadAnimeInfo` — `DownloadModels.kt:26-31`

```kotlin
@Serializable
data class DownloadAnimeInfo(
    val anilistId: Int,         // NON-NULLABLE. Primary key for the folder structure.
    val title: String,          // Non-null. English/romaji title (from AniList).
    val coverUrl: String? = null,
    val coverColor: Int? = null,
)
```
**KDoc (L5-24):** "ANIKUTA is AniList-first (ADR-010), so downloads are keyed by `anilistId`. The `title` + `coverUrl` are carried for the Downloads screen UI (cover + name) and for the AniList-first folder name (`Anime Title [anilistId]`)." KDoc explicitly calls out `anilistId` as "The AniList ID — the primary key for the folder structure."

**`sourceId` is NOT a field here.** Source identity is on `DownloadRequest` only.

### 1.2 `DownloadEpisodeInfo` — `DownloadModels.kt:68-74`

```kotlin
@Serializable
data class DownloadEpisodeInfo(
    val episodeUrl: String,         // Non-null. The source episode URL — the stable offline-playback key.
    val episodeNumber: Float,       // Non-null. Drives the `Episode NNN` folder name (zero-padded 3-digit, floored).
    val name: String,               // Non-null. Display name (for the Downloads screen + metadata.json).
    val scanlator: String? = null,  // Audio-version hint, optional.
)
```
**KDoc (L55-67):** "Carries the `SEpisode`-equivalent fields the engine needs (the source-api `SEpisode` is NOT serializable in our store format, so we mirror the relevant fields). The `episodeUrl` is the stable key for offline-playback lookup."

**`episodeNumber: Float` IS persisted** (the answer to §10 below). It is non-null and is the source of the on-disk folder name `Episode %03d`.

### 1.3 `DownloadTrack` + `TrackKind` — `DownloadModels.kt:45-53`

```kotlin
@Serializable
data class DownloadTrack(
    val url: String,                       // Non-null. Remote URL of the track file.
    val lang: String = "",                 // Human-readable language label.
    val kind: TrackKind = TrackKind.SUBTITLE,
)

@Serializable
enum class TrackKind { SUBTITLE, AUDIO }
```
**KDoc (L33-44):** "Mirrors `eu.kanade.tachiyomi.animesource.model.Track(url, lang)`. We use our own serializable type so `:core:download` does not leak the source-api `Track` into its persisted store."

### 1.4 `DownloadRequest` — `DownloadRequest.kt:32-46`

```kotlin
@Serializable
data class DownloadRequest(
    val anime: DownloadAnimeInfo,                       // Non-null. Carries anilistId.
    val episode: DownloadEpisodeInfo,                   // Non-null. Carries episodeUrl + episodeNumber.
    val videoUrl: String,                               // Non-null. Direct video file URL (from ResolverVideo.url).
    val videoHeaders: String? = null,                   // Newline-separated "Key: Value" HTTP headers.
    val subtitleTracks: List<DownloadTrack> = emptyList(),
    val audioTracks: List<DownloadTrack> = emptyList(),
    val sourceId: Long = 0L,                            // The source ID (for logging + future re-download). DEFAULT 0L.
    val videoServer: String = "",                       // UI display only.
    val videoQuality: String = "",                      // UI display only (e.g. "1080p").
    val videoAudio: String = "",                        // UI display only (e.g. "SUB").
)
```
**KDoc (L5-30):** "The input to `DownloadManager.enqueueDownload` — an ALREADY-RESOLVED video. Per ARCHITECTURE.md §3 module boundaries, `:core:download` MUST NOT import `:feature:video-resolver` (feature isolation — Rule §14). Video URL resolution is orchestrated by `:app`'s `DownloadOrchestrator`, which calls `ResolverService.resolve()`, picks the best `ResolverVideo`, and hands the resolved URL + headers + subtitle tracks to this module via `DownloadRequest`."

**`sourceId` IS persisted here** — but only on the request sub-object (the answer to §9 below). The KDoc describes it as "for logging + future re-download" — i.e. NOT used by any current lookup.

### 1.5 `DownloadTask` — `DownloadTask.kt:26-49` (the queue item; persisted)

```kotlin
@Serializable
data class DownloadTask(
    val id: Long,                                       // Monotonic task ID, assigned by the queue at enqueue.
    val request: DownloadRequest,                       // The original resolved request (anime + episode + video URL + sourceId).
    val status: DownloadStatus,                         // Lifecycle state.
    val progress: Int = 0,                              // 0..100 (video download percentage).
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,                         // -1 = unknown / chunked.
    val errorMessage: String? = null,                   // Set when status == ERROR.
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val videoUri: String? = null,                       // content:// URI of the finished video (set on COMPLETED).
    val subtitleUris: List<String> = emptyList(),       // content:// URIs of finished subtitle files (COMPLETED).
) {
    /** Composite key for dedup + offline-playback lookup. */
    val key: String get() = "${request.anime.anilistId}:${request.episode.episodeUrl}"

    val isInQueue: Boolean
        get() = status == DownloadStatus.QUEUED ||
                status == DownloadStatus.DOWNLOADING ||
                status == DownloadStatus.PAUSED ||
                status == DownloadStatus.ERROR
}
```
**KDoc (L5-25):** "The live state of a single download — persisted in `DownloadStore` (so the queue survives app restarts) and emitted via `DownloadManager.activeDownloads` / `DownloadManager.completedDownloads` Flows for the UI. Identity: `id` is a monotonic Long assigned at enqueue time. The composite key `(anime.anilistId, episode.episodeUrl)` is unique — enqueuing the same episode twice is a no-op (the manager checks first)."

The composite key is a **derived property** on the task — not a stored field, but recomputed on every read. Every dedup + offline-lookup goes through `task.key` (or the inline `"$anilistId:$episodeUrl"` at `DefaultDownloadManager.kt:203`).

### 1.6 `DownloadedEpisode` — `DownloadModels.kt:87-93` (on-disk read shape)

```kotlin
data class DownloadedEpisode(                           // NOT @Serializable — it's a read model, not persisted.
    val episode: DownloadEpisodeInfo,                   // The episode identity (episodeUrl + episodeNumber + name).
    val videoUri: String,                               // content:// URI of the downloaded video.
    val subtitleUris: List<String>,                     // content:// URIs of downloaded subtitle files.
    val sizeBytes: Long,                                // Total size of the episode folder (video + subtitles).
    val completedAt: Long,                              // Epoch millis when the download finished.
)
```
**KDoc (L76-86):** "A completed, on-disk downloaded episode — returned by `DownloadManager.getDownloadedEpisodes` for the Downloads screen." Returned only — never serialized (the persisted form is `DownloadTask`).

### 1.7 `EpisodeMetadataCache` — `DownloadStorageProvider.kt:453-462`

```kotlin
@Serializable
data class EpisodeMetadataCache(
    val anilistId: Int,
    val animeTitle: String,
    val episodeNumber: Float,
    val episodeName: String,
    val videoUrl: String,
    val downloadedAt: Long,
    val sourceId: Long,
)
```
**KDoc (L449-452):** "The cached episode metadata written to `data/metadata.json` alongside the video. Human-readable so a user browsing the folder can identify the episode."

This is the **second on-disk persisted shape** — JSON, written into `<epDir>/data/metadata.json`. Note it includes BOTH `anilistId: Int` AND `sourceId: Long`. It is written by `HttpDownloader.writeMetadataToCache` (`HttpDownloader.kt:481-500`) using values from `task.request.*`, then copied verbatim to the user's SAF folder by `DownloadStorageProvider.publishToUserFolder` (`DownloadStorageProvider.kt:185-197`).

### 1.8 `DownloadStatus` enum — `DownloadStatus.kt:18-42`

```kotlin
enum class DownloadStatus {
    QUEUED,         // In the queue, waiting for a download slot.
    DOWNLOADING,    // Actively downloading — progress is updating.
    PAUSED,         // User-paused; stays in the queue, can be resumed.
    COMPLETED,      // Finished — file + all subtitles on disk. Terminal.
    ERROR,          // Failed (network/IO). Recoverable via retry.
    CANCELLED;      // User-cancelled + file deleted. Terminal.

    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED
    val isActive: Boolean get() = this == DOWNLOADING
}
```
**State transitions (KDoc L5-16):**
```
Queued ──start──▶ Downloading ──100%──▶ Completed
  │                  │
  │                  ├──pause──▶ Paused ──resume──▶ Queued
  │                  ├──error──▶ Error ──retry──▶ Queued
  │                  └──cancel──▶ Cancelled (terminal)
  └──cancel──▶ Cancelled (terminal)
```
`Cancelled` and `Completed` are terminal. `Error` is recoverable (retry → Queued). On startup `DownloadStore.purgeCancelled()` (`DownloadStore.kt:62-70`) silently drops all CANCELLED tasks.

### 1.9 `DownloadMethod` enum — `DownloadPreferences.kt:182-188`

```kotlin
enum class DownloadMethod {
    NORMAL,    // Single-threaded OkHttp download. No resume. Works for HLS + direct video.
    ADVANCED,  // Multi-threaded Range-request download with resume + auto-retry. Direct video only.
}
```

### 1.10 `FallbackStrategy` enum — `DownloadPreferences.kt:200-204`

```kotlin
enum class FallbackStrategy {
    TRY_NEXT,         // Auto-try the next option in the preference list. Default.
    ASK,              // Surface the picker sheet (auto-download effectively bypassed).
    DO_NOT_DOWNLOAD,  // Don't download; show an error.
}
```

### 1.11 `EpisodeDownloadState` sealed interface — `EpisodeDownloadState.kt:20-45` (UI-side; in `:feature:anime-details`)

```kotlin
sealed interface EpisodeDownloadState {
    data object NotDownloaded : EpisodeDownloadState
    data object Resolving : EpisodeDownloadState     // Pre-enqueue (video-resolution phase)
    data object Queued : EpisodeDownloadState
    data class Downloading(val progress: Int) : EpisodeDownloadState
    data object Paused : EpisodeDownloadState
    data class Error(val message: String?) : EpisodeDownloadState
    data object Downloaded : EpisodeDownloadState
}
```
Deliberately NOT in `:core:download` (KDoc L6-10): "Defined in `:feature:anime-details` (NOT `:core:download`) so the feature module stays decoupled from the download engine. The host (MainActivity) collects `DownloadManager.episodeDownloadStates` and maps each `DownloadTask` to this sealed type, then passes a lookup lambda into `EpisodesSection`."

### 1.12 Advanced-method persistence: `DownloadResumeManager.ResumeMetadata` + `ChunkProgress` — `DownloadResumeManager.kt:38-54`

```kotlin
@Serializable
data class ResumeMetadata(
    val taskId: Long,
    val videoUrl: String,
    val totalBytes: Long,
    val chunkCount: Int,
    val chunks: List<ChunkProgress>,
)

@Serializable
data class ChunkProgress(
    val index: Int,
    val start: Long,
    val end: Long,
    val downloaded: Long,
)
```
Per-task temp file `<cacheDir>/anikuta_downloads/<taskId>/resume.json` (`DownloadResumeManager.kt:57-58`). Plus per-chunk files `<cacheDir>/anikuta_downloads/<taskId>/chunk_<i>.part` (`DownloadResumeManager.kt:61-62`). Both are in **internal cache, NOT in the user's SAF folder** — cleaned up by `TempDownloadCache.cleanupTask` on completion/failure/cancel.

---

## 2. The composite-key system — every occurrence of `"${anilistId}:${episodeUrl}"`

The key format is `"<anilistId>:<episodeUrl>"` — `anilistId` (Int) + literal `:` + `episodeUrl` (String). Below: every code occurrence of the exact construction, plus every place that uses the key as a Map key.

### 2.1 Inside `:core:download` (the engine itself)

| # | File:line | Code | Purpose |
|---|---|---|---|
| 1 | `DownloadTask.kt:41` | `val key: String get() = "${request.anime.anilistId}:${request.episode.episodeUrl}"` | **The canonical definition.** Every dedup + offline-lookup goes through this. |
| 2 | `DownloadQueue.kt:309-310` | `private fun keyFor(request: DownloadRequest): String = "${request.anime.anilistId}:${request.episode.episodeUrl}"` | Used by `enqueue()` at L87 (`_tasks.value.firstOrNull { it.key == keyFor(request) }`) for dedup. **Duplicates `DownloadTask.key` logic** — the queue has its own helper so it can dedup BEFORE constructing a `DownloadTask`. |
| 3 | `DefaultDownloadManager.kt:203` | `return queue.tasks.value.firstOrNull { it.key == "$anilistId:$episodeUrl" }` | `findTask(anilistId, episodeUrl)` — used by `isEpisodeDownloaded`, `getDownloadedVideoUri`, `getDownloadedSubtitleUris` (all three offline-playback queries). |
| 4 | `DownloadManager.kt:116` (KDoc) | `"$anilistId:$episodeUrl"` | Documents the `episodeDownloadStates` Flow key. |
| 5 | `DefaultDownloadManager.kt:107` (KDoc) | `"$anilistId:$episodeUrl"` | Same. |
| 6 | `DefaultDownloadManager.kt:109` | `queue.tasks.map { list -> list.associateBy { it.key } }` | The actual keying for `episodeDownloadStates` — uses `DownloadTask.key` (the #1 canonical form). |

### 2.2 Outside `:core:download` (consumers — for context; full map is in EVID-01 §7.4)

| File:line | Code | Purpose |
|---|---|---|
| `app/.../navigation/AppController.kt:584` | `val task = downloadTasksFlow.value["$anilistId:$episodeUrl"] ?: return` | `cancelDownload(anilistId, episodeUrl)` — row-action handler. |
| `app/.../navigation/AppController.kt:589` | Same | `resumeDownload(...)`. |
| `app/.../navigation/AppController.kt:594` | Same | `retryDownload(...)`. |
| `app/.../navigation/AppController.kt:599` | Same | `deleteDownload(...)`. |
| `core/player/.../WatchProgressStore.kt:64` | `private fun key(anilistId: Int, episodeUrl: String) = "$anilistId:$episodeUrl"` | Watch-progress keys (separate store; same key shape — deliberately aligned for cross-store lookups). |
| `core/player/.../PlaybackStateStore.kt:60` | Same | Playback-state keys. |
| `core/tracker/.../TrackSyncManager.kt:73` (KDoc) | `"$anilistId:$episodeUrl"` | `extractAnilistId(progressMap, ...)` parses the anilistId prefix off these keys. |
| `core/backup/.../translation/AniyomiBackupTranslator.kt:352` | `val key = "${res.anilistId}:${hist.url}"` | Building watch-progress keys during Aniyomi→ANIKUTA backup translation. |
| `core/backup/.../model/WatchProgressBackup.kt:9,16` (KDoc) | `"$anilistId:$episodeUrl"` | Backup model: the persisted Map key. |
| `core/backup/.../provider/WatchProgressBackupProvider.kt:22` (KDoc) | Same | Backup provider doc. |

### 2.3 Is there a `downloadKey()` / `compositeKey()` helper?

**No.** There is no central helper. The key construction is duplicated in three places:
1. `DownloadTask.key` getter (`DownloadTask.kt:41`) — used by the manager's reactive map + `findTask` via `task.key`.
2. `DownloadQueue.keyFor(request)` (`DownloadQueue.kt:309-310`) — duplicates the logic so the queue can dedup without instantiating a task first.
3. Inline string `"$anilistId:$episodeUrl"` at `DefaultDownloadManager.kt:203` and four times in `AppController.kt` (L584, L589, L594, L599).

`WatchProgressStore` (`core/player/.../WatchProgressStore.kt:64`) and `PlaybackStateStore` (`core/player/.../PlaybackStateStore.kt:60`) each have their **own private `key(anilistId, episodeUrl)` helper** with the same body. There is no shared helper in `:core:common`.

This is the duplication the `WatchableId` value type proposal (EVID-01 §15, recommended action #1) is designed to eliminate.

---

## 3. The anilistId-gate map (download-relevant subset)

Full codebase map is in EVID-01 §14.3 (32 distinct occurrences in 16 files). The download-system-relevant gates:

| File:line | Gate | Behaviour |
|---|---|---|
| `app/.../navigation/AppController.kt:509-512` | `if (anilistId == 0) { Toast.makeText(..., "Cannot download — anime not linked", ...).show(); return }` | **The hard download gate.** Blocks ALL downloads for unlinked extension anime. |
| `app/.../navigation/AppController.kt:370` | `if (anilistId != 0 && downloadManager.isEpisodeDownloaded(anilistId, episode.url)) { ... }` | Offline-playback short-circuit. Skipped for `anilistId == 0` — unlinked anime never plays offline even if a file exists on disk (it can't, because the gate at L509 prevents the download in the first place). |
| `core/tracker/.../TrackSyncManager.kt:62` | `if (anilistId <= 0) continue` | Skips tracker sync for `0:*` keys. (Not in `:core:download`, but the keys it parses come from `WatchProgressStore` which mirrors `DownloadTask.key`'s shape — listed for context.) |

Inside `:core:download` itself, there are **NO `anilistId == 0` / `<= 0` checks**. The engine happily accepts `anilistId = 0` and would build folder name `Anime Title [0]` and composite key `0:<episodeUrl>` — it's the upstream UI in `:app` that prevents that from happening. The download manager is structurally AniList-keyed but does not enforce non-zero; the gate lives in `AppController`.

No `anilistId == null` / `anilistId != null` gates exist in `:core:download` either — `DownloadAnimeInfo.anilistId: Int` is non-nullable by type, so there is nothing to gate against.

---

## 4. `DownloadManager` interface — every method signature, marked

Source: `core/download/src/main/java/app/confused/anikuta/core/download/DownloadManager.kt:30-122`.

### 4.1 Reactive-state properties (Flows)

| # | Signature | `anilistId` usage |
|---|---|---|
| 1 | `val activeDownloads: Flow<List<DownloadTask>>` (L35) | — (whole-list) |
| 2 | `val completedDownloads: Flow<List<DownloadTask>>` (L38) | — (whole-list) |
| 3 | `val allDownloads: Flow<List<DownloadTask>>` (L41) | — (whole-list) |
| 4 | `val episodeDownloadStates: Flow<Map<String, DownloadTask>>` (L121) | **Keyed by `"$anilistId:$episodeUrl"`** (KDoc L116). |

### 4.2 Queue operations

| # | Signature | `anilistId` usage |
|---|---|---|
| 5 | `suspend fun enqueueDownload(request: DownloadRequest): Long` (L52) | `anilistId` is INSIDE `request.anime.anilistId` (non-null `Int`). No primitive anilistId param. |
| 6 | `suspend fun pauseDownload(taskId: Long)` (L55) | Takes `taskId: Long` (the monotonic ID, NOT anilistId). |
| 7 | `suspend fun resumeDownload(taskId: Long)` (L58) | `taskId: Long`. |
| 8 | `suspend fun cancelDownload(taskId: Long)` (L61) | `taskId: Long`. |
| 9 | `suspend fun deleteDownload(taskId: Long)` (L67) | `taskId: Long`. |
| 10 | `suspend fun deleteAnimeDownloads(anilistId: Int)` (L70) | **Takes `anilistId: Int` directly.** The ONLY method (besides the offline-playback queries) that takes a primitive anilistId. |
| 11 | `suspend fun retryDownload(taskId: Long)` (L73) | `taskId: Long`. |
| 12 | `suspend fun removeFromQueue(taskId: Long)` (L80) | `taskId: Long`. |

### 4.3 Folder configuration

| # | Signature | `anilistId` usage |
|---|---|---|
| 13 | `fun setDownloadFolder(treeUriString: String)` (L89) | — (SAF URI string) |
| 14 | `fun isFolderReady(): Boolean` (L92) | — |

### 4.4 Offline-playback queries

| # | Signature | `anilistId` usage |
|---|---|---|
| 15 | `suspend fun isEpisodeDownloaded(anilistId: Int, episodeUrl: String): Boolean` (L97) | **`anilistId: Int`** (non-null). |
| 16 | `suspend fun getDownloadedVideoUri(anilistId: Int, episodeUrl: String): String?` (L103) | **`anilistId: Int`** (non-null). |
| 17 | `suspend fun getDownloadedSubtitleUris(anilistId: Int, episodeUrl: String): List<String>` (L109) | **`anilistId: Int`** (non-null). |
| 18 | `suspend fun getDownloadedEpisodes(anilistId: Int): List<DownloadedEpisode>` (L112) | **`anilistId: Int`** (non-null). |

**Summary of anilistId usage on the interface:** 5 of the 14 methods/properties take or imply `anilistId` as a primitive (`deleteAnimeDownloads`, `isEpisodeDownloaded`, `getDownloadedVideoUri`, `getDownloadedSubtitleUris`, `getDownloadedEpisodes`, plus the `episodeDownloadStates` map key). The remaining 8 take `taskId: Long` or operate on whole-list Flows. **No method takes `sourceId + url`** — unlinked extension anime have NO way to query the download system through this interface.

---

## 5. `DefaultDownloadManager` implementation — how each interface method maps

Source: `core/download/src/main/java/app/confused/anikuta/core/download/DefaultDownloadManager.kt:45-220`.

Constructor deps (L45-58): `Context`, `OkHttpClient` (qualified `"download"`), `DownloadPreferences`, `DownloadStore`, `TempDownloadCache`, `AdvancedHttpDownloader`, `DownloadResumeManager`, optional `CoroutineScope`.

Wires together (L60-74):
- `DownloadStorageProvider(appContext, preferences)` — the SAF folder I/O.
- `HttpDownloader(okHttp, storage, tempCache, preferences, advancedDownloader)` — the actual downloader.
- `DownloadNotificationManager(appContext)` — notifications.
- `DownloadQueue(downloader, store, preferences, connectivityCheck = { isNetworkAllowed() }, scope)` — state machine.
- A background collector (L87-97) drives `notifier.updateProgress(active)` from the queue's `tasks` Flow.

| Interface method | Impl (line) | Key logic |
|---|---|---|
| `activeDownloads` | L99-100 | `queue.tasks.map { it.filter { it.isInQueue } }` |
| `completedDownloads` | L102-103 | `queue.tasks.map { it.filter { it.status == DownloadStatus.COMPLETED } }` |
| `allDownloads` | L105 | `queue.tasks` (direct pass-through) |
| `episodeDownloadStates` | L107-109 | `queue.tasks.map { list -> list.associateBy { it.key } }` — keys by the composite key |
| `enqueueDownload(request)` | L111-121 | Rejects blank `videoUrl` (-1) + missing folder (-1); else `queue.enqueue(request)` |
| `pauseDownload(taskId)` | L123 | `queue.pause(taskId)` |
| `resumeDownload(taskId)` | L124 | `queue.resume(taskId)` |
| `cancelDownload(taskId)` | L125 | `queue.cancel(taskId)` |
| `retryDownload(taskId)` | L126 | `queue.retry(taskId)` |
| `removeFromQueue(taskId)` | L128-134 | Only removes if status == COMPLETED → `queue.removeCompleted(taskId)` |
| `setDownloadFolder(treeUriString)` | L136-138 | `storage.takeFolderPermission(Uri.parse(treeUriString))` |
| `isFolderReady()` | L140 | `storage.isFolderReady()` |
| `deleteDownload(taskId)` | L142-148 | If COMPLETED, `storage.deleteEpisode(task.request.anime, task.request.episode)`; then `queue.removeCompleted(taskId)` |
| `deleteAnimeDownloads(anilistId)` | L150-159 | Filters tasks by `it.request.anime.anilistId == anilistId && status == COMPLETED`; `queue.removeCompleted(id)` each; then `storage.deleteAnime(anilistId, first.request.anime.title)` |
| `isEpisodeDownloaded(anilistId, episodeUrl)` | L163-169 | `findTask(anilistId, episodeUrl)`; if COMPLETED → true; else if task exists, `storage.isEpisodeDownloaded(...)`; else false |
| `getDownloadedVideoUri(anilistId, episodeUrl)` | L171-175 | `findTask(...)`; if not COMPLETED → null; else `storage.getVideoUri(...) ?: task.videoUri` |
| `getDownloadedSubtitleUris(anilistId, episodeUrl)` | L177-185 | `findTask(...)`; if not COMPLETED → empty; else `storage.getSubtitleUris(...).ifEmpty { task.subtitleUris }` |
| `getDownloadedEpisodes(anilistId)` | L187-200 | Filters queue by `anilistId == anilistId && status == COMPLETED`; maps each to `DownloadedEpisode` with `task.videoUri ?: storage.getVideoUri(...)` |
| `findTask(anilistId, episodeUrl)` (private) | L202-204 | `queue.tasks.value.firstOrNull { it.key == "$anilistId:$episodeUrl" }` — the **only inline construction** of the composite key outside the `DownloadTask.key` getter |
| `isNetworkAllowed()` (private) | L207-219 | Wi-Fi-only check via `ConnectivityManager` |

---

## 6. `DownloadStore` — persistence mechanism

Source: `core/download/src/main/java/app/confused/anikuta/core/download/DownloadStore.kt:28-75`.

### 6.1 Mechanism

**Single JSON-serialized `List<DownloadTask>` held in `PreferenceStore.getObject()`** — the same `PreferenceStore` that backs every other pref in the app. NOT a database. NOT a JSON file on disk. NOT a separate SharedPreferences file.

```kotlin
class DownloadStore(store: PreferenceStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val tasksPref: Preference<List<DownloadTask>> = store.getObject(
        KEY_TASKS,
        emptyList(),
        { list -> json.encodeToString(ListSerializer(DownloadTask.serializer()), list) },
        { str -> try { json.decodeFromString(ListSerializer(DownloadTask.serializer()), str) }
                 catch (e: Exception) { DownloadLogger.w(...); emptyList() } },
    )

    val changes: Flow<List<DownloadTask>> = tasksPref.changes().map { it }
    fun getAll(): List<DownloadTask> = tasksPref.get()
    fun setAll(tasks: List<DownloadTask>) { tasksPref.set(tasks) }
    fun purgeCancelled(): List<DownloadTask> { /* filters out CANCELLED tasks */ }

    companion object { private const val KEY_TASKS = "pref_download_tasks_v1" }
}
```

### 6.2 The persisted record

- **Preference key:** `"pref_download_tasks_v1"` (`DownloadStore.kt:73`).
- **Persisted shape:** `List<DownloadTask>` (the entire queue — active + completed — is one JSON array, serialized whole on every state change).
- **Serialization:** kotlinx.serialization `ListSerializer(DownloadTask.serializer())`. `DownloadTask` is `@Serializable` (`DownloadTask.kt:26`), and so are all its nested types (`DownloadRequest`, `DownloadAnimeInfo`, `DownloadEpisodeInfo`, `DownloadTrack`, `TrackKind`). `DownloadStatus` is a plain enum (serializable by default).
- **Decode-failure handling:** On any `Exception` during decode, the store logs a warning and **returns `emptyList()`** — i.e. a corrupt store means a fresh start (no partial recovery). See `DownloadStore.kt:42-47`.
- **Throttling:** The store itself does not throttle. `DownloadQueue.persistThrottled()` (`DownloadQueue.kt:283-289`) throttles to **once per 1000ms** (`PERSIST_INTERVAL_MS = 1_000L` at `DownloadQueue.kt:313`). State changes (queued/started/paused/completed/error) call `persistNow()` (`DownloadQueue.kt:291-294`) immediately.

### 6.3 What's serialized (and what's NOT)

| Serialized (via `DownloadTask` JSON) | NOT serialized |
|---|---|
| `id: Long` | The temp files in `<cacheDir>/anikuta_downloads/<taskId>/` (cleared on app startup by `TempDownloadCache.cleanupStale()`) |
| `request: DownloadRequest` (and its nested `anime`, `episode`, `videoUrl`, `videoHeaders`, `subtitleTracks`, `audioTracks`, `sourceId`, `videoServer`, `videoQuality`, `videoAudio`) | The `resume.json` + `chunk_N.part` files of the Advanced method (cleared by `TempDownloadCache.cleanupTask` on completion/failure; orphaned by a crash — restarted from scratch on next launch since the resume metadata lives in the same temp dir) |
| `status: DownloadStatus` | The on-disk video/subtitle files themselves — those live in the user's SAF folder and survive a queue wipe |
| `progress, downloadedBytes, totalBytes` | The `DownloadedEpisode` read-model (it's not `@Serializable`; reconstructed on demand from `DownloadTask` + on-disk files) |
| `errorMessage: String?` | |
| `createdAt, updatedAt: Long` | |
| `videoUri: String?`, `subtitleUris: List<String>` (content:// URIs into the SAF folder — survive across launches because the SAF persistable permission is held) | |

**Purge on startup:** `DownloadStore.purgeCancelled()` (`DownloadStore.kt:62-70`) silently removes all CANCELLED tasks on every queue init (`DownloadQueue._tasks = MutableStateFlow(store.purgeCancelled())` at `DownloadQueue.kt:58`). COMPLETED tasks are kept (they're the on-disk library); ERROR/QUEUED/DOWNLOADING/PAUSED tasks are also kept (DOWNLOADING is downgraded to QUEUED by the queue on next start — partial files are discarded, see `DownloadManager.kt:20-22` KDoc).

### 6.4 Why not SQLDelight?

KDoc at `DownloadStore.kt:17-23`: "The download state is small (tens of tasks, not thousands) and highly mutable (progress ticks). A pref-backed JSON list is simpler, has no migration cost, and matches how `WatchProgressStore` already works. The plan's status-tracking columns (ADR-024) apply to anime/episode DB rows, not to the transient download queue. A SQLDelight migration is a documented future option if the queue grows."

---

## 7. `DownloadStorageProvider` — the on-disk layer

Source: `core/download/src/main/java/app/confused/anikuta/core/download/DownloadStorageProvider.kt:42-462`.

### 7.1 Folder structure (verified)

Per the KDoc at L17-28 and the actual code at L105-119 (`ensureEpisodeDir`):

```
<USER_PICKED_FOLDER>/                  ← the SAF tree URI the user picked
└── ANIKUTA/                            ← ensureDir(root, "ANIKUTA") — L111
    └── downloads/                      ← ensureDir(anikutaDir, "downloads") — L112
        └── anime/                      ← ensureDir(downloadsDir, "anime") — L113
            └── <Anime Title [anilistId]>/   ← ensureDir(animeDir, animeFolderName(anime)) — L114
                └── Episode NNN/             ← ensureDir(showDir, episodeFolderName(episode)) — L115
                    ├── video.<ext>           ← e.g. video.mp4 / video.mkv / video.ts
                    └── data/                 ← ensureDir(epDir, "data") — L116
                        ├── subtitles/        ← ensureDir(epDir, "data/subtitles") — L117 (note: nested string path)
                        └── metadata.json
```

The exact path template is:

```
<USER_PICKED_FOLDER>/ANIKUTA/downloads/anime/<Anime Title [anilistId]>/Episode NNN/video.<ext>
```

This **matches** the task description's template (`<USER_FOLDER>/ANIKUTA/downloads/anime/Anime Title [anilistId]/Episode NNN/video.<ext>`). Verified at `DownloadStorageProvider.kt:105-119`.

### 7.2 Folder-name generation

**Anime folder name** — `DownloadStorageProvider.kt:86-89`:
```kotlin
fun animeFolderName(anime: DownloadAnimeInfo): String {
    val safeTitle = sanitizeFileName(anime.title.ifBlank { "Unknown" })
    return "$safeTitle [${anime.anilistId}]"
}
```
The `[anilistId]` literal bracket comes from `anime.anilistId: Int` (`DownloadAnimeInfo.anilistId`, non-null). E.g. `"Jujutsu Kaisen [101522]"` (KDoc L85).

**Episode folder name** — `DownloadStorageProvider.kt:91-95`:
```kotlin
fun episodeFolderName(episode: DownloadEpisodeInfo): String {
    val n = episode.episodeNumber.toInt().coerceAtLeast(0)
    return "Episode %03d".format(n)
}
```
Zero-padded **3-digit**, **floored** (`.toInt()` truncates the float; `.5` specials → `0`). E.g. `"Episode 001"`, `"Episode 012"`, `"Episode 123"`.

**Video file name** — `DownloadStorageProvider.kt:97-101`:
```kotlin
fun videoFileName(videoUrl: String): String {
    val ext = extractExtension(videoUrl)
    return "video.$ext"
}
```
`extractExtension` (L397-408): URL path extension, lowercased, whitelisted to `mp4/mkv/webm/avi/mov/m4v/ts`, defaulting to `mp4`. (Note: `HttpDownloader` uses its OWN `inferVideoExtension` at L502-514 which additionally maps `m3u8`/`m3u` → `ts`. The provider's `videoFileName` is only used for the SAF `openVideoOutputStream` path; the actual write uses the extension inferred by the HttpDownloader via `publishToUserFolder(videoExtension = ...)`.)

**Subtitle file name** — `DownloadStorageProvider.kt:241-252` (`openSubtitleOutputStream`):
```kotlin
val ext = subtitleExtension(track.url)
val safeLang = sanitizeFileName(track.lang.ifBlank { "track" })
val name = "${safeLang}_$index.$ext"
```
E.g. `"English_0.srt"`, `"Japanese_1.ass"`. Extension whitelist: `ass/srt/vtt/ssa/sub`, default `srt` (`subtitleExtension` at L410-419).

### 7.3 Where subtitles + metadata live

- **Subtitles:** `<Episode NNN>/data/subtitles/<lang>_<i>.<ext>` (L116-117 creates the dir; L240-252 writes the files).
- **metadata.json:** `<Episode NNN>/data/metadata.json` (L116 creates the `data` dir; `writeMetadata` at L254-269 + `publishToUserFolder` at L185-197 write the file).

### 7.4 SAF (Storage Access Framework) — folder-picker behaviour

**YES, it uses SAF.** The flow:

1. The user picks a folder via `ActivityResultContracts.OpenDocumentTree` (in the DownloadSettingsScreen — covered by the second agent's evidence).
2. The resulting tree URI string is handed to `DownloadManager.setDownloadFolder(treeUriString)` (`DownloadManager.kt:89`).
3. `DefaultDownloadManager.setDownloadFolder` (`DefaultDownloadManager.kt:136-138`) calls `storage.takeFolderPermission(Uri.parse(treeUriString))`.
4. `DownloadStorageProvider.takeFolderPermission` (`DownloadStorageProvider.kt:55-66`):
   ```kotlin
   fun takeFolderPermission(treeUri: Uri) {
       val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
       context.contentResolver.takePersistableUriPermission(treeUri, flags)
       preferences.downloadFolderUri().set(treeUri.toString())
       ...
   }
   ```
5. The URI string is persisted in the preference `pref_dl_folder_uri` (`DownloadPreferences.kt:159`).
6. On every folder operation, `rootTree()` (`DownloadStorageProvider.kt:69-78`) re-parses the URI string → `DocumentFile.fromTreeUri(...)` → checks `canWrite()` (returns null + logs a warning if revoked).

All file/folder creation uses `DocumentFile.createDirectory(...)` / `createFile(...)` and `ContentResolver.openOutputStream(...)` — never raw `java.io.File` (KDoc L31-34): "All file creation uses `DocumentFile` (content:// URIs) — NEVER raw `java.io.File`, because the user's folder may be on an SD card / external storage we can't reach with File paths."

### 7.5 CRITICAL QUESTION: Does the SAF picker create an app-named subfolder ("ANIKUTA") inside the user's chosen folder?

**YES.** The user picks a folder (e.g. `Downloads/`), and the app creates `ANIKUTA/downloads/anime/...` INSIDE that folder. The code that decides this is `DownloadStorageProvider.ensureEpisodeDir` at L106-119:

```kotlin
fun ensureEpisodeDir(anime: DownloadAnimeInfo, episode: DownloadEpisodeInfo): DocumentFile? {
    val root = rootTree() ?: run { ...; return null }   // ← the user-picked folder
    val anikutaDir = ensureDir(root, "ANIKUTA")          // ← L111: creates "ANIKUTA" inside the user's folder
    val downloadsDir = ensureDir(anikutaDir, "downloads")
    val animeDir = ensureDir(downloadsDir, "anime")
    val showDir = ensureDir(animeDir, animeFolderName(anime))
    val epDir = ensureDir(showDir, episodeFolderName(episode))
    ensureDir(epDir, "data")
    ensureDir(epDir, "data/subtitles")
    return epDir
}
```

The `rootTree()` (L69-78) returns the `DocumentFile` for the user-picked tree URI **directly** — there is no `.findFile("ANIKUTA")` lookup at the root level. The `"ANIKUTA"` directory is **created** (via `ensureDir(root, "ANIKUTA")` at L111) inside whatever the user picked.

`ensureDir` (L360-365):
```kotlin
private fun ensureDir(parent: DocumentFile, name: String): DocumentFile? {
    parent.findFile(name)?.let { return it }     // reuse if exists
    return parent.createDirectory(name).also { ... }   // else create
}
```

So the layout is **strictly**:
```
<USER_PICKED_FOLDER>/                       ← user chose this (e.g. Downloads/, or SD card root)
└── ANIKUTA/                                ← ALWAYS created here by the app (L111)
    └── downloads/
        └── anime/
            └── <Title [anilistId]>/
                └── Episode NNN/
                    ├── video.<ext>
                    └── data/
                        ├── subtitles/
                        └── metadata.json
```

This is also confirmed by:
- `findEpisodeDir` (L122-129) — `root.findFile("ANIKUTA")?.findFile("downloads")?.findFile("anime")?.findFile(animeFolderName(anime))?.findFile(episodeFolderName(episode))`.
- `deleteAnime` (L345-356) — `root.findFile("ANIKUTA")?.findFile("downloads")?.findFile("anime")?.listFiles()?.firstOrNull { it.name?.endsWith("[$anilistId]") == true }`.
- `cleanupEmptyAnimeFolder` (L323-342) — same `root.findFile("ANIKUTA")?.findFile("downloads")?.findFile("anime")` chain.
- `folderDisplayName` companion (L434-446) — pure URI parsing for the settings UI; no I/O.

**Implication:** The user cannot avoid the `ANIKUTA/` prefix. If the user picks `Downloads/`, downloads land in `Downloads/ANIKUTA/downloads/anime/...`. The app does NOT support "use the picked folder directly as the anime parent."

### 7.6 Other provider methods

- `publishToUserFolder(...)` (L147-211): The atomic-publish step. Copies the temp video + temp subtitles + temp metadata.json from `TempDownloadCache` into the SAF folder. Returns `PublishResult.Success(videoUri, subtitleUris, sizeBytes)` or `PublishResult.Error(message)`.
- `openVideoOutputStream(epDir, videoUrl)` (L226-238): Used by the (unused?) direct-write path; deletes any existing file first.
- `openSubtitleOutputStream(epDir, track, index)` (L241-252): Same.
- `writeMetadata(epDir, metadata)` (L254-269): Writes `metadata.json` directly to SAF.
- `getVideoUri(anime, episode)` (L274-279): Returns the content:// URI of the `video.*` file (matches by prefix `"video."`). Used by `DefaultDownloadManager.getDownloadedVideoUri`.
- `getSubtitleUris(anime, episode)` (L282-286): Returns all files in `data/subtitles/`.
- `isEpisodeDownloaded(anime, episode)` (L289-291): `getVideoUri(...) != null`.
- `episodeFolderSize(anime, episode)` (L294-297): Recursive folder size (video + subs + metadata).
- `deleteEpisode(anime, episode)` (L307-317): Deletes the Episode folder + auto-deletes the anime folder if empty (`cleanupEmptyAnimeFolder`).
- `deleteAnime(anilistId, animeTitle)` (L345-356): Looks up the folder by `name?.endsWith("[$anilistId]")` (NOT by title — title is unused here, only the bracketed ID is matched). Note this method is only called from `DefaultDownloadManager.deleteAnimeDownloads` which passes the title for logging only.
- `cleanupEmptyAnimeFolder(anime)` (L323-342): Post-delete cleanup; deletes the anime folder if it has no remaining episode folders.

---

## 8. `HttpDownloader` / `AdvancedHttpDownloader` / `HlsDownloader` — capabilities

### 8.1 `HttpDownloader` — the orchestration layer (the "DEFAULT" pipeline)

Source: `HttpDownloader.kt:51-536`. Pipeline (KDoc L17-31):

1. **Download to internal cache** (`TempDownloadCache` — `<cacheDir>/anikuta_downloads/<taskId>/video.<ext>`). Fast, private, no SAF per-byte overhead. User's folder NOT touched yet.
2. **Validate** via `VideoTypeDetector`:
   - Reject HLS (.m3u8) / DASH (.mpd) at the URL/Content-Type level — BUT then re-route to `HlsDownloader` if HLS detected (L101-114 for content-based HLS re-route; L182-187 for URL-based pre-flight).
   - Reject HTML pages (resolver bugs).
   - Reject tiny files (`< MIN_VALID_VIDEO_BYTES = 500 KB`) — `validateDownloadedFile` at L342-359.
3. **Verify magic bytes** (`verifyVideoMagicBytes` at L375-451): MP4 (`ftyp`), MKV/WebM (`1A 45 DF A3`), MPEG-TS (0x47 sync bytes at offsets 0, 188, 376, 564, 752), FLV, AVI. Rejects HTML (`<!` / `<h`), PNG (only if file < 10 MB AND not valid .ts), JPEG (same).
4. **Download subtitles** to temp cache (best-effort; failures skipped — `downloadSubtitlesToCache` at L454-479).
5. **Write metadata.json** to temp cache (`writeMetadataToCache` at L481-500).
6. **Publish to SAF** via `DownloadStorageProvider.publishToUserFolder` — atomic copy of validated temp files to the user's folder.
7. **Clean up** temp dir regardless of success/failure (`tempCache.cleanupTask(task.id)` in `finally` at L161-165).

**Method routing inside `downloadVideoToCache` (L173-212):**
- Pre-flight URL check via `VideoTypeDetector.detectFromUrl` — if HLS, delegate to `HlsDownloader.download` immediately (L181-187).
- If `preferences.method() == DownloadMethod.ADVANCED`, delegate to `AdvancedHttpDownloader.download` (L196-208). On `DownloadException`, fall back to the Normal path.
- Else (Normal method), call `downloadNormal` (L218-287) — single-threaded streaming with Content-Type detection + HLS-by-Content-Type re-route.

**Capabilities summary:**
- Range requests: NO (only the Advanced method does Range).
- Resume: NO (only the Advanced method).
- Multi-threaded: NO (only the Advanced method).
- HLS handling: YES — delegated to `HlsDownloader`.
- Subtitle handling: YES — all `subtitleTracks` are downloaded to `data/subtitles/<lang>_<i>.<ext>` (best-effort; failures skipped).
- PNG/corrupt rejection: YES — magic-byte verification rejects HTML/PNG/JPEG; size check rejects < 500 KB files; MPEG-TS sync-byte detection prevents false PNG rejection on valid .ts files that start with a PNG poster.

### 8.2 `AdvancedHttpDownloader` — multi-threaded Range + resume

Source: `advanced/AdvancedHttpDownloader.kt:70-401`.

**Capabilities:**
- **Multi-threaded:** YES — splits the file into N chunks (configurable: `preferences.advancedThreadCount()` default 4, clamped 1..8) downloaded in parallel via `async(Dispatchers.IO) { ... }.awaitAll()` (L160-180).
- **Range requests:** YES — uses `Range: bytes=<start>-<end>` headers per chunk (L291-294). Falls back to single-threaded if the server doesn't support Range OR file < `advancedMinSizeMb` (default 5 MB) OR `threadCount == 1` (L109-113).
- **Resume:** YES — per-chunk `.part` files in `<cacheDir>/anikuta_downloads/<taskId>/chunk_<i>.part` + `resume.json` metadata (via `DownloadResumeManager`). On cancellation, the current chunk progress is saved (L193-203). On restart, `loadResume` validates chunk files and resumes from the last downloaded byte (L124-132, `DownloadResumeManager.kt:69-91`).
- **Auto-retry:** YES — per-chunk retry up to `advancedMaxRetries` (default 3, clamped 0..10) with 1s delay between attempts (`downloadChunkWithRetry` at L246-276, `RETRY_DELAY_MS = 1_000L`).
- **Concatenation:** After all chunks finish, `concatenateChunks` (L324-336) sequentially appends `chunk_0.part`, `chunk_1.part`, ... → `video.<ext>` (RandomAccessFile seek-based writes per chunk mean each chunk file is its own contiguous byte range).
- **Server probe:** `probeServer` (L214-239) sends `Range: bytes=0-0` GET (not HEAD — many servers 405 on HEAD). 206 → Range supported; 200 → not supported. Total size from `Content-Range: bytes 0-0/TOTAL` or `Content-Length`.
- **HLS:** NOT supported (the probe fails because HLS playlists have no Content-Length → `DownloadException` → falls back to Normal → which re-routes to `HlsDownloader`).
- **Subtitle handling:** NOT in this class — `HttpDownloader` handles subtitles separately after the video download completes.
- **Resume metadata persistence:** `resume.json` + `chunk_*.part` files in **internal cache** (NOT in the user's SAF folder). Cleaned up on completion (`clearResume(taskId)` at L186 + `TempDownloadCache.cleanupTask`). On a crash, the temp dir is wiped on next startup by `TempDownloadCache.cleanupStale()` — so resume only works across pause/cancel within the same app session, NOT across crashes (despite the resume file existing). This is a known limitation.

### 8.3 `HlsDownloader` — HLS playlist parser + segment downloader

Source: `HlsDownloader.kt:47-333`.

**Capabilities:**
- **Playlist parsing:** Master playlists (`#EXT-X-STREAM-INF`) → picks the first variant (highest bandwidth, by convention). Media playlists → parses segments after `#EXTINF` / `#EXT-X-BYTERANGE`.
- **Encryption:** Detects `#EXT-X-KEY` with a METHOD other than NONE → REJECTS with "Encrypted HLS stream — the default downloader cannot decrypt DRM/AES-128. This will be supported by the 1DM download method (future)." (L92-97). Most anime HLS is unencrypted.
- **Init segment:** Parses `#EXT-X-MAP:URI="..."` for fMP4 streams; writes it first before media segments (L113-118).
- **Segment download:** Sequential (NOT parallel) — `downloadSegment` (L175-194) fetches each segment into memory and appends to the output `.ts` file.
- **PNG header stripping:** YES — `stripPngHeader` (L200-228) detects PNG magic bytes (`89 50 4E 47`), finds the `IEND` marker, skips 8 bytes, then looks for the MPEG-TS sync byte (0x47) where 0x47 also appears 188 bytes later. Mirrors the extension's `LocalProxyServer.stripPngHeader` logic for anti-scraping obfuscation used by CDNs like megaplay.buzz / kotocdn.site (KDoc L155-173).
- **Concatenation:** Byte concatenation of cleaned segments → single `.ts` file. MPV plays .ts natively, so NO ffmpeg needed.
- **Progress:** Reports actual file size after each segment; totalBytes = -1 (unknown — HLS segment sizes aren't known until downloaded). `DynamicProgressTracker` handles the -1 case via the "50MB ahead" estimator.
- **Relative URLs:** `resolveUrl` (L318-328) handles absolute URLs, relative-to-directory, and relative-to-base via `java.net.URI.resolve()`.
- **No resume:** HLS downloads restart from segment 0 on pause/cancel (the `.ts` file is overwritten). Multi-threaded HLS would require ffmpeg segment-muxing — out of scope for the default method (KDoc L42-44).

### 8.4 `TempDownloadCache` — the staging area

Source: `TempDownloadCache.kt:37-93`.

Layout in **internal cache** (NOT user's SAF folder):
```
<cacheDir>/anikuta_downloads/<taskId>/
  video.<ext>          ← the temp video file
  subtitles/
    <lang>_0.<ext>     ← temp subtitle files
  metadata.json        ← temp metadata
  resume.json          ← (Advanced method only) chunk progress
  chunk_0.part         ← (Advanced method only) chunk files
  chunk_1.part
  ...
```

- `taskDir(taskId)` (L43-45): `File(context.cacheDir, "anikuta_downloads/<taskId>")`. Created if missing.
- `cleanupTask(taskId)` (L63-73): Deletes the entire task dir on completion/failure/cancel.
- `cleanupStale()` (L79-92): On app startup (called by `DownloadModule` at `di/DownloadModule.kt:45`: `single { TempDownloadCache(get<Context>()).also { it.cleanupStale() } }`), deletes ALL stale task dirs from a previous crash.

---

## 9. Is `sourceId` stored in the download data model?

**YES — but only on `DownloadRequest.sourceId`, NOT on `DownloadAnimeInfo`.** And it is **never used as a lookup key**.

### 9.1 Where `sourceId` appears in the download data model

| File:line | Field / usage |
|---|---|
| `DownloadRequest.kt:39` | `val sourceId: Long = 0L` — the field itself. Default 0L. |
| `DownloadRequest.kt:29` (KDoc) | "@param sourceId The source ID (for logging + future re-download)." |
| `DownloadStorageProvider.kt:461` | `val sourceId: Long` — field on `EpisodeMetadataCache` (the on-disk metadata.json shape). |
| `HttpDownloader.kt:494` | `sourceId = task.request.sourceId,` — copied from the request into `EpisodeMetadataCache` when writing metadata.json. |
| `DownloadOrchestrator.kt:351` | `sourceId = source.id,` — set when building `DownloadRequest` in `buildRequest(...)`. The ONLY place sourceId is populated from a real source. |

### 9.2 Where `sourceId` is NOT used

- **`DownloadAnimeInfo`** has no `sourceId` field — only `anilistId`. This is the type that drives folder names + composite keys.
- **`DownloadTask.key`** is purely `"${anilistId}:${episodeUrl}"` — no sourceId.
- **`DownloadQueue.keyFor(request)`** — same, no sourceId.
- **`DefaultDownloadManager.findTask(anilistId, episodeUrl)`** — only matches on anilistId + episodeUrl.
- **All `DownloadManager` interface methods** that take anilistId — none take sourceId. There is no `getDownloadedVideoUri(sourceId: Long, episodeUrl: String)` overload.
- **`DownloadStorageProvider.animeFolderName`** — `"$safeTitle [${anime.anilistId}]"` — no sourceId in the folder name. (If two sources serve the same anime with different titles but the same anilistId, they share a folder — which is the intended behaviour.)
- **`DownloadStore`** serializes `sourceId` as part of `DownloadRequest`, but no read path queries by sourceId.

### 9.3 Verdict

`sourceId: Long = 0L` is **persisted** (on `DownloadRequest` inside every `DownloadTask`, and on `EpisodeMetadataCache` inside `metadata.json`). But it is **functionally inert** — the KDoc describes it as "for logging + future re-download", and there is no code path today that uses the persisted `sourceId` for any lookup, dedup, folder naming, or offline-playback query. It is a forward-looking field awaiting the "re-download from the same source" feature.

---

## 10. Is episode NUMBER persisted in the download model?

**YES.** `DownloadEpisodeInfo.episodeNumber: Float` (non-null, no default).

| File:line | Field |
|---|---|
| `DownloadModels.kt:71` | `val episodeNumber: Float,` — on `DownloadEpisodeInfo`. |
| `DownloadModels.kt:63-64` (KDoc) | "@param episodeNumber The episode number (float; .5 = special). Drives the `Episode NNN` folder name (zero-padded 3-digit, floored)." |
| `DownloadStorageProvider.kt:92-95` | `episodeFolderName` uses `episode.episodeNumber.toInt()` → `"Episode %03d".format(n)`. So the episode NUMBER drives the on-disk folder name. |
| `DownloadStorageProvider.kt:457` | `val episodeNumber: Float` — also on `EpisodeMetadataCache` (in `metadata.json`). |
| `HttpDownloader.kt:490` | `episodeNumber = task.request.episode.episodeNumber,` — copied into `EpisodeMetadataCache`. |
| `DownloadOrchestrator.kt:340` | `episodeNumber = episode.episode_number,` — set from `SEpisode.episode_number` (the source-api float field) when building `DownloadEpisodeInfo`. |

So the episode number is persisted in TWO places:
1. **Inside `DownloadTask.request.episode.episodeNumber`** (the JSON-serialized queue in `pref_download_tasks_v1`).
2. **Inside `<Episode NNN>/data/metadata.json`** as `episodeNumber: Float`.

The on-disk folder name `Episode NNN` is derived from this number (zero-padded 3-digit, floored). The episode URL is also persisted (and is the offline-playback composite-key partner), but the NUMBER is what determines the folder name.

---

## 11. Summary — key takeaways for the architecture plan

1. **The download data model is fully AniList-keyed.** `DownloadAnimeInfo.anilistId: Int` is non-null by type; every folder name, every composite key, every `DownloadManager` query method, and every queue dedup goes through `anilistId`. There is no `sourceId + url` fallback path anywhere in `:core:download`. (Consistent with EVID-01 §14.1 — "TIGHT (refuses unlinked): DownloadManager (all methods), DownloadAnimeInfo, DownloadStorageProvider (folder name).")

2. **The composite key `"$anilistId:$episodeUrl"` is duplicated 3× inside `:core:download`** (`DownloadTask.key`, `DownloadQueue.keyFor`, `DefaultDownloadManager.findTask`) + 4× inline in `AppController` + 2× more in `WatchProgressStore` / `PlaybackStateStore`. No central helper. This is the exact duplication the proposed `WatchableId` value type would eliminate.

3. **The persistence layer is a single JSON list in SharedPreferences** (`pref_download_tasks_v1`), throttled to 1 write/sec. Not SQLDelight. Survives restarts; CANCELLED tasks purged on startup; partial files discarded (DOWNLOADING → QUEUED on next launch). Resume metadata (Advanced method) lives in internal cache, wiped on crash — so cross-crash resume does NOT work despite the resume.json file existing.

4. **The on-disk folder structure is `<USER_PICKED_FOLDER>/ANIKUTA/downloads/anime/<Title [anilistId]>/Episode NNN/video.<ext>`** — the `ANIKUTA/` subfolder is ALWAYS created inside the user-picked folder (verified at `DownloadStorageProvider.kt:111`). Subtitles in `data/subtitles/`, metadata in `data/metadata.json`. All via SAF DocumentFile — never raw `java.io.File`.

5. **`sourceId` IS persisted** (on `DownloadRequest.sourceId: Long = 0L` and on `EpisodeMetadataCache.sourceId`) but is **functionally inert** — no lookup, no folder naming, no key. It's a forward-looking field.

6. **Episode NUMBER IS persisted** (`DownloadEpisodeInfo.episodeNumber: Float`) — it drives the `Episode NNN` folder name (zero-padded 3-digit, floored) and is also in `metadata.json`.

7. **Three downloaders coexist:** `HttpDownloader` (Normal: single-threaded, no resume, HLS + direct video), `AdvancedHttpDownloader` (multi-threaded Range + resume, direct video only), `HlsDownloader` (HLS segment concatenation, PNG stripping, no encryption). Routing is by `DownloadMethod` pref + URL/Content-Type detection. All three write to internal cache first, then `publishToUserFolder` atomically copies to SAF.

8. **Two anilistId gates block unlinked downloads:**
   - `AppController.kt:509-512` — `if (anilistId == 0) { Toast(..., "Cannot download — anime not linked", ...).show(); return }` — the hard gate that prevents enqueue.
   - `AppController.kt:370` — `if (anilistId != 0 && downloadManager.isEpisodeDownloaded(...))` — the offline-playback short-circuit (skipped for `anilistId == 0`, but unreachable for unlinked anime because of the L509 gate).
   - Inside `:core:download` itself there are NO anilistId gates — the engine would happily build `[0]` folders if asked. The structural gate is `DownloadAnimeInfo.anilistId: Int` (non-null by type) + the upstream UI check.

---

## 12. Files read (no code modified)

- `core/download/src/main/java/app/confused/anikuta/core/download/DownloadModels.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/DownloadRequest.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/DownloadTask.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/DownloadStatus.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/DownloadManager.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/DefaultDownloadManager.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/DownloadQueue.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/DownloadStore.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/DownloadStorageProvider.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/DownloadPreferences.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/TempDownloadCache.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/HttpDownloader.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/advanced/AdvancedHttpDownloader.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/advanced/DownloadResumeManager.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/HlsDownloader.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/VideoTypeDetector.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/DynamicProgressTracker.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/ServerDiscoveryStore.kt` (full)
- `core/download/src/main/java/app/confused/anikuta/core/download/di/DownloadModule.kt` (full)
- `app/src/main/java/app/confused/anikuta/download/DownloadOrchestrator.kt` (full)
- `app/src/main/java/app/confused/anikuta/navigation/AppController.kt` (lines 350-469, 490-619 — download-relevant sections)
- `app/src/main/java/app/confused/anikuta/di/DownloadAppModule.kt` (full)
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/EpisodeDownloadState.kt` (full)
- `feature/download/src/main/java/app/confused/anikuta/feature/download/ExtensionSourceInfo.kt` (full)
- Grep-verified: `${anilistId}:${episodeUrl}` / `$anilistId:$episodeUrl` / `anilistId == 0` / `anilistId != 0` / `sourceId` across the whole codebase.

**No code was modified.** This file is the data/persistence half of the download-evidence pair; the pipeline/UI/offline-playback half is covered by EVID-02B.
