package app.confused.anikuta.core.download

import kotlinx.serialization.Serializable

/**
 * The live state of a single download — persisted in [DownloadStore] (so the
 * queue survives app restarts) and emitted via [DownloadManager.activeDownloads]
 * / [DownloadManager.completedDownloads] Flows for the UI.
 *
 * Identity: [id] is a monotonic Long assigned at enqueue time. The composite
 * key `(anime.anilistId, episode.episodeUrl)` is unique — enqueuing the same
 * episode twice is a no-op (the manager checks first).
 *
 * @param id Unique task ID (assigned by the manager).
 * @param request The original resolved request (anime + episode + video URL).
 * @param status Current lifecycle state (see [DownloadStatus]).
 * @param progress 0..100 (video download percentage). 0 until bytes flow.
 * @param downloadedBytes Bytes written so far (for the UI + notification).
 * @param totalBytes Total expected bytes (-1 = unknown / chunked).
 * @param errorMessage Human-readable error (when status == ERROR); null otherwise.
 * @param createdAt Epoch millis when enqueued.
 * @param updatedAt Epoch millis of the last state change (for sorting + UI).
 * @param videoUri The content:// URI of the finished video (set on COMPLETED).
 * @param subtitleUris The content:// URIs of finished subtitle files (COMPLETED).
 */
@Serializable
data class DownloadTask(
    val id: Long,
    val request: DownloadRequest,
    val status: DownloadStatus,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val videoUri: String? = null,
    val subtitleUris: List<String> = emptyList(),
) {
    /** Composite key for dedup + offline-playback lookup. */
    val key: String get() = "${request.anime.anilistId}:${request.episode.episodeUrl}"

    /** True when the task is in the queue (not terminal, not yet completed). */
    val isInQueue: Boolean
        get() = status == DownloadStatus.QUEUED ||
            status == DownloadStatus.DOWNLOADING ||
            status == DownloadStatus.PAUSED ||
            status == DownloadStatus.ERROR
}
