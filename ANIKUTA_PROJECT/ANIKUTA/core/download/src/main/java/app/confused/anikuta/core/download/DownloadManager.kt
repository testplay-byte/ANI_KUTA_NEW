package app.confused.anikuta.core.download

import kotlinx.coroutines.flow.Flow

/**
 * The pluggable download manager contract.
 *
 * **Modular + future-proof (ADR-020).** There is one implementation now —
 * [DefaultDownloadManager] (standard OkHttp HTTP download). A future
 * `OneDmDownloadManager` (multi-threaded, resume-capable, 1DM-style) will
 * implement this same interface; the user selects the method in
 * [DownloadPreferences]. Swapping implementations is a Koin binding change.
 *
 * **Thread-safety.** All `suspend` methods are safe to call from any
 * dispatcher; the manager internally uses `Dispatchers.IO` for file/network
 * work (Rule §9 — all network on IO). The [Flow]s are hot (StateFlow-backed)
 * and safe to collect on the main thread.
 *
 * **Persistence.** The queue is persisted across app restarts via
 * [DownloadStore]; a QUEUED/DOWNLOADING/PAUSED task resumes in QUEUED state on
 * next launch (a partial file is discarded + re-downloaded for the MVP;
 * resume-from-offset is a 1DM-method enhancement).
 *
 * **Module boundary.** This interface lives in `:core:download` and takes an
 * already-resolved [DownloadRequest] (NOT a source + episode). Resolution is
 * orchestrated by `:app`'s `DownloadOrchestrator`, which depends on
 * `:feature:video-resolver` + this interface. This keeps `:core:download` free
 * of any `:feature:*` import (Rule §14 — feature isolation).
 */
interface DownloadManager {

    // ── Reactive state (hot Flows; collect on main) ──

    /** Tasks that are queued / downloading / paused / errored (the live queue). */
    val activeDownloads: Flow<List<DownloadTask>>

    /** Tasks that finished successfully (the on-disk library). */
    val completedDownloads: Flow<List<DownloadTask>>

    /** A single combined stream of ALL tasks (active + completed). Convenience for the UI. */
    val allDownloads: Flow<List<DownloadTask>>

    // ── Queue operations ──

    /**
     * Enqueue a download. Resolves to the existing task if the same episode is
     * already queued/completed (dedup by `contentId|episodeNumber`).
     *
     * @return the task ID (existing or new). Returns -1 if the request is
     *   invalid (blank videoUrl) — logged at ERROR.
     */
    suspend fun enqueueDownload(request: DownloadRequest): Long

    /** Pause a downloading/queued task. Stays in the queue; resume to restart. */
    suspend fun pauseDownload(taskId: Long)

    /** Resume a paused/errored task back into the queue. */
    suspend fun resumeDownload(taskId: Long)

    /** Cancel a task: stops the download, removes it from the queue, deletes the partial file. */
    suspend fun cancelDownload(taskId: Long)

    /**
     * Delete a completed download: removes the task + its on-disk folder
     * (video + subtitles + metadata). Does NOT touch other episodes of the same anime.
     */
    suspend fun deleteDownload(taskId: Long)

    /** Delete ALL completed downloads for an anime (the whole anime folder). */
    suspend fun deleteAnimeDownloads(contentId: String)

    /** Retry an errored task (moves it back to QUEUED). */
    suspend fun retryDownload(taskId: Long)

    /**
     * Removes a COMPLETED task from the queue (the file stays on disk).
     * Used by the auto-clear-after-10s feature: completed entries disappear
     * from the download queue but the downloaded file remains in the user's folder.
     */
    suspend fun removeFromQueue(taskId: Long)

    // ── Folder configuration ──

    /**
     * Set the download folder from a SAF tree URI string (from
     * `ActivityResultContracts.OpenDocumentTree`). Takes the persistable
     * permission + stores the URI in preferences. Throws on permission failure.
     */
    fun setDownloadFolder(treeUriString: String)

    /** True if a writable download folder is configured. */
    fun isFolderReady(): Boolean

    // ── Offline-playback queries ──
    //
    // Phase 6 (ADR-050): these take contentId + episodeNumber (NOT anilistId +
    // episodeUrl). This is the source-switching fix — the same anime from a
    // different extension has the same contentId + episodeNumber, so offline
    // playback still finds the download.

    /**
     * True if a completed, on-disk copy exists for this episode.
     *
     * Looks up by `contentId + episodeNumber` (source-independent). Falls back
     * to a filesystem scan if no in-memory task matches (covers files from a
     * prior install or after a source switch that changed the episodeUrl).
     */
    suspend fun isEpisodeDownloaded(contentId: String, episodeNumber: Float): Boolean

    /**
     * The content:// URI of the downloaded video for offline playback, or null.
     * The URI is playable by MPV via `resolveUrlForMpv` (fd:// / real-path).
     */
    suspend fun getDownloadedVideoUri(contentId: String, episodeNumber: Float): String?

    /**
     * The content:// URIs of downloaded subtitle files for this episode
     * (loaded into MPV as external sub tracks). Empty if none.
     */
    suspend fun getDownloadedSubtitleUris(contentId: String, episodeNumber: Float): List<String>

    /** All completed episodes for an anime (for the Downloads screen grouping). */
    suspend fun getDownloadedEpisodes(contentId: String): List<DownloadedEpisode>

    /**
     * A reactive map of download state for ALL tasks, keyed by
     * `"$contentId|$episodeNumber"`. Used by the episode-row UI to show
     * download/progress/downloaded state per episode without per-row queries.
     *
     * Collect this once per screen (not per row) and build a local lookup map.
     */
    val episodeDownloadStates: Flow<Map<String, DownloadTask>>
}
