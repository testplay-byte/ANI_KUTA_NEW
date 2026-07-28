package app.confused.anikuta.core.download

/**
 * The lifecycle state of a single [DownloadTask].
 *
 * State transitions (driven by [DownloadQueue] / [DefaultDownloadManager]):
 * ```
 * Queued ──start──▶ Downloading ──100%──▶ Completed
 *   │                  │                    
 *   │                  ├──pause──▶ Paused ──resume──▶ Queued
 *   │                  ├──error──▶ Error ──retry──▶ Queued
 *   │                  └──cancel──▶ Cancelled (terminal)
 *   └──cancel──▶ Cancelled (terminal)
 * ```
 *
 * `Cancelled` and `Completed` are terminal. `Error` is recoverable (retry → Queued).
 */
enum class DownloadStatus {
    /** In the queue, waiting for a download slot (concurrency-limited). */
    QUEUED,

    /** Actively downloading — [DownloadTask.progress] is updating. */
    DOWNLOADING,

    /** User-paused; stays in the queue, can be resumed. */
    PAUSED,

    /** Finished — the file + all subtitles are on disk. Terminal. */
    COMPLETED,

    /** Failed (network/IO). Recoverable via retry. */
    ERROR,

    /** User-cancelled + file deleted. Terminal. */
    CANCELLED;

    /** Whether this status is terminal (no further transitions). */
    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED

    /** Whether the task is currently consuming a download slot. */
    val isActive: Boolean get() = this == DOWNLOADING
}
