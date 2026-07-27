package app.anikuta.download

import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents a single episode download. Mirrors aniyomi's AnimeDownload but
 * adapted for ANI-KUTA's data model (anilistId instead of animeId, episodeUrl
 * as the unique key, sourceId for re-resolution).
 *
 * Status + progress are exposed as StateFlows so the UI can observe them.
 * The queue in [DownloadManager] observes these flows to propagate state
 * changes reactively (fixes bug B3 — status changes not reaching the UI).
 */
data class Download(
    val id: String,
    val anilistId: Int,
    val sourceId: Long,
    val sourceName: String,
    val animeTitle: String,
    val episodeUrl: String,
    val episodeName: String,
    val episodeNumber: Float,
    val order: Int = 0,
    var video: Video? = null,
) {
    private val _statusFlow = MutableStateFlow(State.QUEUE)
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(value) {
            if (_statusFlow.value != value) {
                _statusFlow.value = value
            }
        }

    private val _progressFlow = MutableStateFlow(0)
    val progressFlow = _progressFlow.asStateFlow()
    var progress: Int
        get() = _progressFlow.value
        set(value) {
            val clamped = value.coerceIn(0, 100)
            if (_progressFlow.value != clamped) {
                _progressFlow.value = clamped
            }
        }

    /** Estimated total file size in bytes (from Content-Length or segment calculation), or -1 if unknown. */
    var totalSize: Long = -1L

    /** Downloaded bytes so far. */
    var downloadedBytes: Long = 0L

    /** Actual video duration in milliseconds (from FFprobe on the final .mkv). 0 if unknown. */
    var actualDurationMs: Long = 0L

    /** Actual video resolution (e.g., "640x360"). Empty if unknown. */
    var actualResolution: String = ""

    /** Server name used for the download (e.g., "VidPlay-1"). */
    var serverName: String = ""

    /** Audio version (e.g., "SUB", "DUB"). */
    var audioVersion: String = ""

    /** Quality label (e.g., "360p"). */
    var qualityLabel: String = ""

    /** Download speed in bytes/sec (updated during download). */
    private val _speedFlow = MutableStateFlow(0L)
    val speedFlow = _speedFlow.asStateFlow()
    var speed: Long
        get() = _speedFlow.value
        set(value) { _speedFlow.value = value }

    /**
     * Countdown for auto-removal from the downloads page (Issue 4).
     *
     * When a download completes, it stays in the downloads page for 20 seconds
     * so the user can see it finished. A countdown bar shows the remaining time.
     * When the countdown reaches 0, the download is removed from the queue
     * (the file is kept on disk).
     *
     * Values: 1.0 (just completed) → 0.0 (time to remove). -1 = not counting down.
     */
    private val _autoRemoveCountdown = MutableStateFlow(-1f)
    val autoRemoveCountdown = _autoRemoveCountdown.asStateFlow()
    var autoRemoveProgress: Float
        get() = _autoRemoveCountdown.value
        set(value) { _autoRemoveCountdown.value = value }

    /** Error message if status is ERROR. */
    var error: String? = null

    /**
     * Download state machine.
     *
     * Flow: QUEUE → RESOLVING → DOWNLOADING → MUXING → DOWNLOADED
     *                  ↓             ↓
     *                ERROR         ERROR
     *                                ↓
     *                              PAUSED → DOWNLOADING (resume)
     *
     * - QUEUE:      Enqueued, waiting for worker to pick it up
     * - RESOLVING:  Resolving video URL from source (handles expired links)
     * - DOWNLOADING: Actively downloading segments
     * - MUXING:     All segments done, concatenating into final .mkv
     * - DOWNLOADED: Complete, ready for offline playback
     * - ERROR:      Failed after all retries
     * - PAUSED:     User paused, can be resumed
     */
    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        RESOLVING(2),
        DOWNLOADING(3),
        MUXING(4),
        DOWNLOADED(5),
        ERROR(6),
        PAUSED(7),
        RECONNECTING(8);  // Issue 6: network lost, trying to reconnect (10s timeout → ERROR)

        companion object {
            fun fromValue(v: Int): State = entries.find { it.value == v } ?: QUEUE
        }
    }
}
