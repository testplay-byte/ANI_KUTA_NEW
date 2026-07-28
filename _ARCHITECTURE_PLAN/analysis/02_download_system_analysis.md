# 02 — Download System: Deep Analysis

> **Phase 1 / Current State.** Complete end-to-end analysis of the download pipeline: data models, the composite key system, persistence, storage, the manager interface, orchestration, offline playback, and the source-switching break. Every claim is backed by `file:line` references. Raw evidence: `_evidence/EVID-02A-downloads-data.md` + `_evidence/EVID-02B-downloads-pipeline.md`.

---

## 1. Executive summary

The download system is **the most tightly AniList-coupled subsystem in ANIKUTA**, and it is coupled by *type*, not just by convention. `DownloadAnimeInfo.anilistId: Int` is **non-nullable** (`core/download/.../DownloadModels.kt:27`), which means the type system itself makes it impossible to represent a download without an AniList ID. Every folder name, every composite key, every `DownloadManager` query, and every queue dedup goes through `anilistId`.

The system is architecturally clean in other respects — a well-designed modular engine with three downloader strategies (Normal, Advanced/multi-threaded, HLS), proper SAF integration, a state-machine queue with semaphore concurrency, and atomic publish-to-SAF. The coupling is concentrated in the **identity layer**, not the engine layer.

**The three critical defects:**

1. **The hard gate** (`AppController.kt:509-512`) — unlinked anime cannot be downloaded at all.
2. **The source-switching break** (`DefaultDownloadManager.kt:167`) — when a user switches extension source, the `episodeUrl` changes, so `isEpisodeDownloaded(anilistId, newEpisodeUrl)` returns false, and the download becomes invisible to offline playback *even though the file is still on disk*.
3. **The mandatory `ANIKUTA/` subfolder** (`DownloadStorageProvider.kt:111`) — the user picks a folder, but the app always creates an `ANIKUTA/` subfolder inside it. The user cannot use their chosen folder directly.

**The encouraging news:** `episodeNumber: Float` IS persisted on `DownloadEpisodeInfo` and the on-disk folder is already episode-number-keyed (`Episode %03d`). The on-disk structure already supports episode-number-based matching; only the in-memory lookup logic doesn't. The source-switching break is fixable with a relatively small change to the lookup logic.

---

## 2. Data models — full field dump

### 2.1 `DownloadAnimeInfo` — `core/download/.../DownloadModels.kt:26-31`

```kotlin
@Serializable
data class DownloadAnimeInfo(
    val anilistId: Int,         // NON-NULLABLE. Primary key for the folder structure.
    val title: String,          // Non-null. English/romaji title (from AniList).
    val coverUrl: String? = null,
    val coverColor: Int? = null,
)
```

**KDoc:** *"ANIKUTA is AniList-first (ADR-010), so downloads are keyed by `anilistId`. The `title` + `coverUrl` are carried for the Downloads screen UI (cover + name) and for the AniList-first folder name (`Anime Title [anilistId]`)."*

> 🔴 **`sourceId` is NOT a field here.** Source identity is on `DownloadRequest` only. This means once a download is enqueued, the source that produced it is forgotten — which is half of why source-switching breaks visibility.

### 2.2 `DownloadEpisodeInfo` — `core/download/.../DownloadModels.kt:68-74`

```kotlin
@Serializable
data class DownloadEpisodeInfo(
    val episodeUrl: String,         // Non-null. The source episode URL — the stable offline-playback key.
    val episodeNumber: Float,       // Non-null. Drives the `Episode NNN` folder name (zero-padded 3-digit, floored).
    val name: String,               // Non-null. Display name (for the Downloads screen + metadata.json).
    val scanlator: String? = null,  // Audio-version hint, optional.
)
```

> ✅ **`episodeNumber: Float` IS persisted.** This is the field that enables the proposed source-switching fix — the on-disk folder is keyed by episode number, so a fallback lookup by `(anilistId, episodeNumber)` is achievable.

### 2.3 `DownloadRequest` — `core/download/.../DownloadRequest.kt`

```kotlin
@Serializable
data class DownloadRequest(
    val videoUrl: String,           // The already-resolved video URL
    val headers: Map<String, String> = emptyMap(),
    val anime: DownloadAnimeInfo,   // carries anilistId (non-null)
    val episode: DownloadEpisodeInfo,
    val sourceId: Long = 0L,        // ✅ present but functionally inert (KDoc: "for logging + future re-download")
    val sourceName: String? = null,
    val videoFormat: VideoFormat = VideoFormat.DIRECT_VIDEO,
    val subtitleUrls: List<SubtitleTrack> = emptyList(),
)
```

> ⚠️ **`sourceId` IS on `DownloadRequest` but is never read for lookup, folder-naming, or keying.** It's stored on `EpisodeMetadataCache.sourceId` too (`DownloadStorageProvider.kt:461`), but no code path consumes it. This is a latent capability waiting to be activated.

### 2.4 `DownloadTask` — `core/download/.../DownloadTask.kt`

```kotlin
@Serializable
data class DownloadTask(
    val id: Long,                   // Monotonic, persisted, internal PK (pause/resume/cancel/notifications)
    val request: DownloadRequest,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,     // -1 = unknown
    val speed: Long = 0L,           // bytes/sec
    val createdAt: Long = System.currentTimeMillis(),
    val error: String? = null,
    val retryCount: Int = 0,
) {
    val key: String get() = "${request.anime.anilistId}:${request.episode.episodeUrl}"  // ◄── composite key
}
```

> 🔴 **The composite key is defined here** (line 41) as `"${anilistId}:${episodeUrl}"`. This is the single source of truth for queue dedup and offline-playback lookup.

### 2.5 `DownloadStatus` — `core/download/.../DownloadStatus.kt`

```kotlin
enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, CANCELLED, ERROR
}
```

### 2.6 `DownloadedEpisode` / `EpisodeMetadataCache` (on-disk representation)

**File:** `core/download/.../DownloadStorageProvider.kt`

```kotlin
@Serializable
data class EpisodeMetadataCache(
    val animeTitle: String,
    val episodeNumber: Float,
    val episodeName: String,
    val episodeUrl: String,
    val sourceId: Long,             // ✅ persisted to metadata.json
    val sourceName: String?,
    val videoFormat: String,
    val subtitleTracks: List<SubtitleTrack>,
    val coverColor: Int?,
    val coverUrl: String?,
    val downloadedAt: Long,
)
```

This is serialized to `data/metadata.json` inside each episode folder. It carries `sourceId` + `episodeUrl` + `episodeNumber` — all three identity signals — but the lookup logic ignores `sourceId` and `episodeNumber`.

---

## 3. The composite key system — every occurrence

The string `"${anilistId}:${episodeUrl}"` (or variants) appears in **9+ files** with **no central helper function**. This duplication is exactly what a typed `WatchableId` value class would eliminate.

| # | File:line | Context |
|---|---|---|
| 1 | `core/download/.../DownloadTask.kt:41` | `val key: String get() = "${request.anime.anilistId}:${request.episode.episodeUrl}"` — the definition |
| 2 | `core/download/.../DownloadQueue.kt:309-310` | `fun keyFor(request): String = "${request.anime.anilistId}:${request.episode.episodeUrl}"` — duplicated for dedup |
| 3 | `core/download/.../DefaultDownloadManager.kt:203` | Inline composite key in `findTask(anilistId, episodeUrl)` |
| 4 | `core/download/.../DefaultDownloadManager.kt:167` | Inline composite key in `isEpisodeDownloaded(anilistId, episodeUrl)` — **the source-switching break point** |
| 5 | `navigation/AppController.kt:584` | Inline composite key for download-state lookup |
| 6 | `navigation/AppController.kt:589` | Inline composite key (duplicate) |
| 7 | `navigation/AppController.kt:594` | Inline composite key (duplicate) |
| 8 | `navigation/AppController.kt:599` | Inline composite key (duplicate) |
| 9 | `core/player/.../WatchProgressStore.kt` | Same composite key shape for watch progress |
| 10 | `core/player/.../PlaybackStateStore.kt` | Same composite key shape for playback state |

**The anilistId gate map** (every `anilistId == 0` / `<= 0` / `== null` check):

| File:line | Check | Behavior |
|---|---|---|
| `navigation/AppController.kt:509-512` | `if (anilistId == 0)` | Hard block: Toast "Cannot download — anime not linked", return |
| `navigation/AppController.kt:370` | `if (anilistId != 0 && downloadManager.isEpisodeDownloaded(...))` | Offline-playback short-circuit skipped for anilistId = 0 |
| `core/tracker/.../TrackSyncManager.kt:62` | `if (anilistId <= 0) continue` | Tracker sync skipped |
| `feature/anime-details/.../AnimeDetailViewModel.kt:629-632` | `anime.anilistId ?: return` | Episode metadata enrichment skipped |

> 📌 **Notably, `:core:download` itself has NO anilistId gates.** The engine would happily build `[0]` folders if asked. The gates are all upstream, in `:app` (`AppController`). This means the engine is reusable as-is for a decoupled design — only the orchestration layer needs to change.

---

## 4. `DownloadManager` interface — every method

**File:** `core/download/.../DownloadManager.kt`

| Method | Signature | anilistId usage |
|---|---|---|
| `enqueue(request: DownloadRequest): Long` | Takes the full request | anilistId inside `request.anime` |
| `pause(taskId: Long)` | By task ID | None |
| `resume(taskId: Long)` | By task ID | None |
| `cancel(taskId: Long)` | By task ID | None |
| `pauseAll()` | — | None |
| `resumeAll()` | — | None |
| `cancelAll()` | — | None |
| `isEpisodeDownloaded(anilistId: Int, episodeUrl: String): Boolean` | **Takes anilistId + episodeUrl as primitives** | 🔴 Hardcoded |
| `findTask(anilistId: Int, episodeUrl: String): DownloadTask?` | **Takes anilistId + episodeUrl as primitives** | 🔴 Hardcoded |
| `getDownloadedEpisodes(anilistId: Int): List<DownloadedEpisode>` | **Takes anilistId** | 🔴 Hardcoded |
| `getTask(taskId: Long): DownloadTask?` | By task ID | None |
| `tasksFlow: StateFlow<Map<Long, DownloadTask>>` | Observable | None |
| `downloadedEpisodesFlow: StateFlow<Map<String, List<DownloadedEpisode>>>` | Observable, keyed by anilistId.toString() | 🔴 Hardcoded |
| `episodeDownloadStates: StateFlow<Map<String, DownloadTask>>` | Observable, keyed by composite | 🔴 Hardcoded |

**5 of 14 methods/properties take or imply `anilistId` as a primitive parameter.** None take `sourceId + url` or a `WatchableId`. The interface itself encodes the AniList dependency.

---

## 5. Persistence — `DownloadStore`

**File:** `core/download/.../DownloadStore.kt`

- **Mechanism:** Single JSON `List<DownloadTask>` serialized to SharedPreferences.
- **Pref key:** `pref_download_tasks_v1` (line 73).
- **Write throttling:** Max 1 write/sec.
- **Startup behavior:** CANCELLED tasks purged; DOWNLOADING → QUEUED (partial files discarded); `resume.json` wiped by `TempDownloadCache.cleanupStale()` (`DownloadModule.kt:45`).

> ⚠️ **Crash-resume gap:** `resume.json` (used by `AdvancedHttpDownloader` for Range-resume) is wiped on startup. Resume only works *within a session* — not across crashes. This is a pre-existing bug, separate from the AniList coupling.

The queue is identified by two keys:
1. `task.id: Long` — monotonic, persisted, internal PK (for pause/resume/cancel/notifications).
2. `task.key: String = "$anilistId:$episodeUrl"` — composite, for dedup + offline lookup + UI flow.

`DownloadQueue.enqueue` dedups by: `firstOrNull { it.key == keyFor(request) }` — i.e., if a task with the same composite key exists, the new request is silently dropped.

---

## 6. On-disk storage — `DownloadStorageProvider`

**File:** `core/download/.../DownloadStorageProvider.kt`

### 6.1 Folder structure (verified)

```
<USER_PICKED_SAF_FOLDER>/
└── ANIKUTA/                                          ◄── ALWAYS created (L111: ensureDir(root, "ANIKUTA"))
    └── downloads/
        └── anime/
            └── <Anime Title> [<anilistId>]/          ◄── folder name embeds anilistId
                ├── Episode 001/
                │   ├── video.mp4                     ◄── (or .mkv/.ts/etc.)
                │   └── data/
                │       ├── subtitles/                ◄── all subtitle tracks
                │       │   ├── en.ass
                │       │   └── ja.srt
                │       └── metadata.json             ◄── EpisodeMetadataCache
                ├── Episode 002/
                │   └── ...
                └── ...
```

### 6.2 The mandatory `ANIKUTA/` subfolder

**File:** `core/download/.../DownloadStorageProvider.kt:106-119`

```kotlin
fun getAnimeRoot(anime: DownloadAnimeInfo): DocumentFile {
    val root = getDownloadRoot()              // = the user-picked SAF folder
    val anikutaDir = ensureDir(root, "ANIKUTA")        // ◄── L111: ALWAYS created
    val downloadsDir = ensureDir(anikutaDir, "downloads")
    val animeDir = ensureDir(downloadsDir, "anime")
    val folderName = "${anime.title} [${anime.anilistId}]"   // ◄── anilistId embedded in folder name
    return ensureDir(animeDir, folderName)
}
```

> 🔴 **The `ANIKUTA/` subfolder is ALWAYS created inside the user's chosen SAF folder.** The user cannot avoid it. This contradicts the desired behavior: *"a folder selection system where the user picks a folder and the app uses it directly (no additional app-named subfolder created inside it)."*

### 6.3 Episode folder naming

```kotlin
val episodeFolderName = "Episode %03d".format(episode.episodeNumber.toInt())  // zero-padded 3-digit, floored
```

> ✅ **The on-disk structure IS episode-number-keyed.** This is the key insight for the source-switching fix: the filesystem already supports `Episode 001`, `Episode 002`, etc. — independent of source. Only the in-memory lookup logic (`isEpisodeDownloaded`) fails to leverage this.

### 6.4 Subtitles + metadata

- Subtitles: `data/subtitles/<lang>.<ext>` — all subtitle tracks from the resolved video are downloaded.
- Metadata: `data/metadata.json` — the `EpisodeMetadataCache` (§2.6), carrying `sourceId`, `episodeUrl`, `episodeNumber`, subtitle tracks, cover color, etc.

### 6.5 SAF integration

The user picks a folder via the Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`). The folder URI is persisted in `DownloadPreferences.downloadFolderUri`. All file operations use `DocumentFile` (SAF), not raw `java.io.File`. This is correct for modern Android, but SAF has known performance overhead for large libraries.

---

## 7. The downloaders — three strategies

| Downloader | File | Capabilities |
|---|---|---|
| `HttpDownloader` (Normal/DEFAULT) | `core/download/.../HttpDownloader.kt` | Single-threaded; magic-byte validation (rejects PNG/HTML disguised as video); atomic publish to SAF |
| `AdvancedHttpDownloader` | `core/download/.../advanced/AdvancedHttpDownloader.kt` | Multi-threaded Range requests (4-thread default); resume via `resume.json` + `chunk_N.part` files; retry logic |
| `HlsDownloader` | `core/download/.../HlsDownloader.kt` | `.m3u8` parser; segment download + concat; PNG-header stripping; subtitle extraction |

All three write to internal cache first (`<cacheDir>/anikuta_downloads/<taskId>/`), then atomically publish to SAF via `DownloadStorageProvider`. This is a clean design — a download failure never leaves a partial file in the user-visible SAF folder.

**Method selection:** `DownloadPreferences.method` (enum `NORMAL` / `ADVANCED`), default `ADVANCED`. HLS is auto-detected by `VideoTypeDetector` (URL/Content-Type → `DIRECT_VIDEO | HLS_STREAM | DASH_STREAM | HTML_PAGE | UNKNOWN`) regardless of the method pref.

**Limitations:**
- Encrypted HLS + DASH: NOT supported (needs FFmpegKit — open branch `feature/downloads-ffmpegkit`).
- Resume across crashes: broken (`resume.json` wiped on startup).
- PNG/corrupt rejection: handled by magic-byte check in `HttpDownloader`.

---

## 8. `DownloadOrchestrator` (`:app`)

**File:** `app/.../download/DownloadOrchestrator.kt`

Lives in `:app` (not `:core:download` or `:feature:download`) because `:core:download` cannot import `:feature:video-resolver` (Rule §14 — feature modules can't be imported by core). It bridges the resolver + the download engine.

**Two modes:**
1. **Auto-download ON** (default) — `selectBestVideo:207-307` runs a 4-step priority search over the resolved video list, picking the best quality/audio based on `DownloadPreferences.qualityPreferences` (default `["1080p","720p","480p","360p"]`) and `audioPreferences` (default `["SUB","DUB"]`). If no match, falls back per `FallbackStrategy` (TRY_NEXT / ASK / DO_NOT_DOWNLOAD).
2. **Auto-download OFF** — always shows `DownloadVideoPickerSheet` (the user picks manually).

**Minimum input to enqueue:**
- Non-zero `anilistId` (enforced by the upstream gate at `AppController.kt:509`)
- `SEpisode` (the source episode)
- `AnimeSource` (to resolve videos)
- A configured SAF folder (`DownloadPreferences.downloadFolderUri`)

> 🔴 **The orchestrator itself has no anilistId gate** — it accepts whatever `DownloadAnimeInfo` it's given. The gate is purely upstream in `AppController`. This means the orchestrator + engine are reusable as-is for a decoupled design.

---

## 9. The user flow — end-to-end pipeline

```
┌─────────────────────────────────────────────────────────────────────┐
│ 1. USER TAPS DOWNLOAD on an episode row (AnimeDetailScreen)         │
│    EpisodeDownloadControl.kt → AppController.downloadEpisode(...)   │
└──────────────────────────────┬──────────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 2. THE HARD GATE                                                    │
│    AppController.kt:509-512:                                        │
│      if (anilistId == 0) {                                          │
│          Toast "Cannot download — anime not linked"                 │
│          return   ◄── BLOCKED for unlinked anime                    │
│      }                                                              │
└──────────────────────────────┬──────────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 3. BUILD DownloadAnimeInfo                                          │
│    DownloadAnimeInfo(anilistId, title, coverUrl, coverColor)        │
│    (anilistId is non-null by type — the gate ensured it)            │
└──────────────────────────────┬──────────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 4. DownloadOrchestrator.enqueueDownload(animeInfo, episode, source) │
│    - Resolves videos via AnimeSource.getHosterList / getVideoList   │
│    - Auto-picks best quality (or shows DownloadVideoPickerSheet)    │
│    - Builds DownloadRequest(videoUrl, headers, anime, episode,      │
│                              sourceId, sourceName, ...)             │
└──────────────────────────────┬──────────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 5. DownloadQueue.enqueue(request)                                   │
│    - key = "${anilistId}:${episodeUrl}"  (composite)                │
│    - DEDUP: firstOrNull { it.key == keyFor(request) } → drop if     │
│      a task with the same key exists                                │
│    - Assigns task.id (monotonic)                                    │
│    - Semaphore(concurrentDownloads=3) controls parallelism          │
└──────────────────────────────┬──────────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 6. DOWNLOADER (HttpDownloader / AdvancedHttpDownloader / HlsDownloader) │
│    - Writes to internal cache: <cacheDir>/anikuta_downloads/<taskId>/ │
│    - Validates magic bytes (rejects PNG/HTML)                       │
│    - Downloads all subtitles                                        │
│    - Writes metadata.json (EpisodeMetadataCache)                    │
│    - ATOMIC PUBLISH to SAF via DownloadStorageProvider              │
└──────────────────────────────┬──────────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 7. ON DISK                                                          │
│    <USER_FOLDER>/ANIKUTA/downloads/anime/<Title [anilistId]>/       │
│      Episode NNN/                                                   │
│        video.<ext>                                                  │
│        data/subtitles/<lang>.<ext>                                  │
│        data/metadata.json                                           │
└──────────────────────────────┬──────────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 8. OFFLINE PLAYBACK                                                 │
│    AppController.resolveEpisode(anilistId, episode):                │
│      if (anilistId != 0 &&                                          │
│          downloadManager.isEpisodeDownloaded(anilistId,             │
│                                               episode.url)) {      │
│          val localUri = downloadManager.getLocalUri(anilistId, ...) │
│          WatchRequest(videoUrl = localUri.toString(),               │
│                       anilistId = anilistId, ...)                   │
│          resolveUrlForMpv(localUri) → "fd://<fd>" or real path      │
│      }   ◄── SKIPPED for anilistId = 0                              │
│                                                                        │
│    isEpisodeDownloaded(anilistId, episodeUrl):                     │
│      DefaultDownloadManager.kt:167:                                 │
│        val task = findTask(anilistId, episodeUrl)                  │
│        if (task == null) return false   ◄── THE BREAK POINT        │
│        return task.status == COMPLETED                             │
└─────────────────────────────────────────────────────────────────────┘
```

**The anilistId entry point:** Step 2 (the gate). From there, it propagates into `DownloadAnimeInfo` (step 3), the composite key (step 5), the folder name (step 7), and the offline-playback lookup (step 8). Every subsequent step depends on it.

---

## 10. THE SOURCE-SWITCHING BREAK — pinpointed

This is the most important defect in the download system, and it has a precise location.

### 10.1 The scenario

1. User watches anime "Frieren" via Extension A (sourceId = 100). Episode 1 URL = `https://extA.com/frieren/ep1`. User downloads it.
2. The download is stored at `<folder>/ANIKUTA/downloads/anime/Frieren [12345]/Episode 001/video.mp4`.
3. The `DownloadTask` is keyed `"12345:https://extA.com/frieren/ep1"`, status COMPLETED.
4. User switches to Extension B (sourceId = 200) for the same anime. Now Episode 1 URL = `https://extB.com/frieren/01`.
5. User taps Episode 1 to watch offline.

### 10.2 The break

**File:** `core/download/.../DefaultDownloadManager.kt:167`

```kotlin
override fun isEpisodeDownloaded(anilistId: Int, episodeUrl: String): Boolean {
    val task = findTask(anilistId, episodeUrl)    // ◄── L167: the break point
    if (task == null) return false                // ◄── returns false → re-streams
    return task.status == DownloadStatus.COMPLETED
}
```

`findTask(anilistId, episodeUrl)` looks up by the composite key `"${anilistId}:${episodeUrl}"`. With the new source, `episodeUrl` = `https://extB.com/frieren/01`, but the stored task's key uses `https://extA.com/frieren/ep1`. The lookup returns `null` → `isEpisodeDownloaded` returns `false` → the offline short-circuit at `AppController.resolveEpisode:367-402` fails → the episode re-streams.

**The file is still on disk.** The download is still listed in `DownloadedFilesScreen` (which groups by `anilistId`, so it survives at the library level). But the detail-page row state shows `NotDownloaded` (`AppController.getDownloadStates:622` filters by `"$anilistId:"` prefix + episodeUrl match), and offline playback doesn't trigger.

### 10.3 Why it's fixable

- `episodeNumber: Float` IS persisted on `DownloadEpisodeInfo` (§2.2).
- The on-disk folder IS episode-number-keyed (`Episode %03d`, §6.3).
- `metadata.json` carries `episodeNumber` + `sourceId` + `episodeUrl` (§2.6).

**No cross-source / episode-number matching exists today** (grep-verified). `episodeNumber` is used ONLY for folder names, UI display, and logging. But the on-disk structure already supports it — a filesystem fallback (`storage.findEpisodeDir(anime, episode)` keyed on `Episode %03d`) would work. The fix is to add a fallback in `findTask` (or `isEpisodeDownloaded`) that, when no task matches `(anilistId, episodeUrl)`, scans completed tasks for the same `anilistId` whose `episode.episodeNumber` matches.

### 10.4 The asymmetry

| Surface | Keyed by | Source-switch behavior |
|---|---|---|
| `DownloadedFilesScreen` (library-level list) | `anilistId` (groups by anime) | ✅ Downloads SURVIVE — visible at library level |
| `DownloadViewModel.groupByAnime:97` | `anilistId` | ✅ Survives |
| Detail-page episode row state (`AppController.getDownloadStates:622`) | `"$anilistId:"` prefix + episodeUrl match | ❌ Shows `NotDownloaded` post-switch |
| Offline playback (`isEpisodeDownloaded:167`) | `"$anilistId:$episodeUrl"` exact match | ❌ Re-streams post-switch |

**User-visible symptom:** *"My download is listed in the Downloads screen, but when I open the anime and tap the episode, it streams instead of playing offline."*

---

## 11. UI layer — `:feature:download`

**Main files:**
- `DownloadsScreen` — top-level: queue + downloaded library + settings tabs
- `DownloadedFilesScreen` — lists downloaded anime, grouped by `anilistId`
- `DownloadSettingsScreen` — SAF folder, auto-download, concurrency, wifi-only, method, quality prefs
- `DownloadVideoPickerSheet` — manual video picker (when auto-download is off or no match)
- `DownloadViewModel` + `DownloadUiState`
- `DownloadPreferencesSheet` — per-anime download prefs (dragHandle = null)

**UI query mechanism:** `DownloadViewModel.groupByAnime:97` groups `downloadedEpisodesFlow` (keyed by `anilistId.toString()`) into `Map<anilistId, List<DownloadedEpisode>>`. The UI displays anime folders by anilistId. This is why downloads survive at the library level after a source switch (the grouping key doesn't include episodeUrl).

---

## 12. `DownloadPreferences` — full catalog

**File:** `core/download/.../DownloadPreferences.kt` — 15 preferences:

| Preference | Key | Default | Notes |
|---|---|---|---|
| `downloadFolderUri` | `pref_download_folder_uri` | `""` (none) | Only required setting |
| `method` | `pref_download_method` | `ADVANCED` | `NORMAL` / `ADVANCED` |
| `wifiOnly` | `pref_download_wifi_only` | `true` | |
| `concurrentDownloads` | `pref_download_concurrent` | `3` | Clamped 1..5 |
| `autoDownload` | `pref_download_auto` | `true` | Auto-pick best quality |
| `qualityPreferences` | `pref_download_quality_prefs` | `["1080p","720p","480p","360p"]` | JSON list |
| `audioPreferences` | `pref_download_audio_prefs` | `["SUB","DUB"]` | JSON list |
| `fallbackStrategy` | `pref_download_fallback` | `TRY_NEXT` | `TRY_NEXT` / `ASK` / `DO_NOT_DOWNLOAD` |
| `advancedChunkSize` | `pref_download_chunk_size` | `2MB` | Advanced method |
| `advancedMaxRetries` | `pref_download_max_retries` | `3` | Advanced method |
| `advancedTimeout` | `pref_download_timeout` | `30s` | Advanced method |
| `notificationEnabled` | `pref_download_notif` | `true` | |
| `deleteOnWatch` | `pref_download_delete_on_watch` | `false` | Auto-delete after watching |
| `autoDownloadNewEpisodes` | `pref_download_auto_new` | `false` | Future (ADR-020) |
| `pref_download_tasks_v1` | (internal) | `[]` | The persisted queue (DownloadStore) |

---

## 13. `DownloadNotificationManager`

- One channel `anikuta_downloads` (IMPORTANCE_LOW).
- Three notification types: active summary (ID=9001, throttled to 800ms), completion (task.id+10000), error (task.id+20000).
- Entire `updateProgress` body wrapped in try/catch — a notification failure NEVER crashes the engine.

---

## 14. Conclusion — what this analysis establishes

1. **The download engine is clean; the identity layer is not.** `:core:download` has zero anilistId gates internally. The coupling is concentrated in: (a) `DownloadAnimeInfo.anilistId: Int` (non-nullable by type), (b) the composite key `"$anilistId:$episodeUrl"` (duplicated 9+ times), (c) the `DownloadManager` interface (5 methods take anilistId), (d) the folder name `<Title [anilistId]>`, (e) the upstream gate in `AppController`.

2. **The source-switching break has a single fixable location:** `DefaultDownloadManager.kt:167`. The on-disk structure already supports episode-number-keyed lookup; only the in-memory lookup logic needs a fallback.

3. **The mandatory `ANIKUTA/` subfolder contradicts the desired UX.** Fixing this requires changing `DownloadStorageProvider.kt:111` + a migration to move existing downloads up one level (or keep them and only change new downloads).

4. **`sourceId` is persisted but inert.** It's on `DownloadRequest` and `EpisodeMetadataCache` but never read for lookup. Activating it is a key part of the redesign.

5. **`episodeNumber` is persisted and is the key to the source-switching fix.** The on-disk folder is `Episode %03d` — source-independent. A fallback lookup by `(anilistId, episodeNumber)` would make downloads survive source switches.

6. **The engine supports three downloaders** (Normal, Advanced/multi-threaded, HLS) with atomic publish, magic-byte validation, and subtitle extraction. This is a solid foundation; the redesign doesn't need to touch the engine.

These findings directly motivate `proposals/03_download_system_redesign.md` — which proposes replacing the anilistId-keyed composite with a `WatchableId` + episode-number-based matching, removing the hard gate, and making the SAF folder user-direct.

---

*Evidence sources: `_evidence/EVID-02A-downloads-data.md` (805 lines) + `_evidence/EVID-02B-downloads-pipeline.md` (770 lines).*
