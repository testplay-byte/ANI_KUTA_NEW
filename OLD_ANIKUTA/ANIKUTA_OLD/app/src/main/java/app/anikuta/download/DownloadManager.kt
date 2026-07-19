package app.anikuta.download

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import app.anikuta.download.engine.DownloadEngine
import app.anikuta.source.api.model.SEpisode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Public facade for the download system. Manages the download queue lifecycle:
 * enqueue, pause, resume, cancel, retry, query.
 *
 * Delegates actual downloading to [DownloadWorker] (WorkManager) which calls
 * the [DownloadEngine] (segment-based with resume).
 *
 * The queue is reactive: status/progress changes on individual [Download] objects
 * propagate to all observers via the downloads' StateFlows. The [queue] flow
 * re-emits when the list structure changes (add/remove). For per-download
 * status/progress, observers should collect [Download.statusFlow] and
 * [Download.progressFlow] directly.
 *
 * To fix bug B3 (status changes not reaching the UI), the [queueStateFlow]
 * combines the queue list with each download's statusFlow, so any status
 * change triggers a re-emit.
 *
 * @param context application context
 * @param store persistent queue state
 * @param prefs download preferences (wifi-only, concurrent, priority lists)
 */
class DownloadManager(
    private val context: Context,
    private val store: DownloadStore,
    private val prefs: DownloadPreferences,
) {
    companion object {
        private const val TAG = "DownloadManager"
        private const val UNIQUE_WORK = "AnikutaDownloader"
    }

    private val workManager: WorkManager = WorkManager.getInstance(context)

    /** Live in-memory queue (Download objects with StateFlows). */
    private val _queue = MutableStateFlow<List<Download>>(emptyList())
    val queue: StateFlow<List<Download>> = _queue.asStateFlow()

    /**
     * Reactive map of episodeUrl → download status.
     * Re-emits whenever any download's status changes (fixes bug B3).
     * Keyed by episodeUrl (stable) instead of episodeName (mutable) — fixes H4.
     * Used by DetailViewModel for the download button state.
     */
    private val _downloadStatusMap = MutableStateFlow<Map<String, Download.State>>(emptyMap())
    val downloadStatusMap: StateFlow<Map<String, Download.State>> = _downloadStatusMap.asStateFlow()

    /**
     * Reactive map of episodeUrl → download progress (0-100).
     * Re-emits whenever any download's progress changes.
     * Keyed by episodeUrl (stable) — fixes H4.
     */
    private val _downloadProgressMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Int>> = _downloadProgressMap.asStateFlow()

    /**
     * Refresh both status and progress maps from the current queue state.
     * Keyed by episodeUrl (stable identifier that survives metadata enrichment).
     */
    private fun refreshStatusMap() {
        _downloadStatusMap.value = _queue.value.associate { it.episodeUrl to it.status }
        _downloadProgressMap.value = _queue.value.associate { it.episodeUrl to it.progress }
    }

    init {
        // Restore queue from persistent store on startup
        restoreQueue()
        Log.d(TAG, "init: ✓ DownloadManager initialized, queue size=${_queue.value.size}")
    }

    /**
     * Enqueue a single episode for download.
     */
    fun enqueueDownload(
        anilistId: Int,
        sourceId: Long,
        sourceName: String,
        animeTitle: String,
        episode: SEpisode,
    ): String {
        val downloadId = "dl_${anilistId}_${episode.episode_number}_${System.currentTimeMillis()}"

        Log.d(TAG, "enqueueDownload: → ${episode.name} (id=$downloadId, source=$sourceName, anime=$animeTitle)")

        val entry = DownloadStore.DownloadStoreEntry(
            id = downloadId,
            anilistId = anilistId,
            sourceId = sourceId,
            sourceName = sourceName,
            animeTitle = animeTitle,
            episodeUrl = episode.url,
            episodeName = episode.name.ifBlank { "Episode ${episode.episode_number}" },
            episodeNumber = episode.episode_number,
        )

        // Dedup check: only add to live queue if the store actually added it
        // (fixes C2 — duplicate downloads when tapping the spinning circle)
        val wasAdded = store.add(entry)
        if (wasAdded) {
            addToLiveQueue(entry)
            startWork()
            Log.d(TAG, "enqueueDownload: ✓ enqueued ${episode.name} (id=$downloadId)")
        } else {
            Log.d(TAG, "enqueueDownload: ⏭ skipped duplicate — ${episode.name} already in queue")
        }
        return downloadId
    }

    /**
     * Enqueue multiple episodes for download.
     */
    fun enqueueDownloads(
        anilistId: Int,
        sourceId: Long,
        sourceName: String,
        animeTitle: String,
        episodes: List<SEpisode>,
    ) {
        Log.d(TAG, "enqueueDownloads: → ${episodes.size} episodes for $animeTitle")

        val entries = episodes.mapIndexed { index, ep ->
            DownloadStore.DownloadStoreEntry(
                id = "dl_${anilistId}_${ep.episode_number}_${System.currentTimeMillis()}_${index}",
                anilistId = anilistId,
                sourceId = sourceId,
                sourceName = sourceName,
                animeTitle = animeTitle,
                episodeUrl = ep.url,
                episodeName = ep.name.ifBlank { "Episode ${ep.episode_number}" },
                episodeNumber = ep.episode_number,
                order = index,
            )
        }
        store.addAll(entries)
        entries.forEach { addToLiveQueue(it) }
        startWork()

        Log.d(TAG, "enqueueDownloads: ✓ enqueued ${entries.size} downloads")
    }

    /**
     * Cancel a download + delete ALL associated files.
     *
     * Per user decision: Cancel ALWAYS deletes everything — the episode folder
     * from SAF (including the .mkv, manifest, etc.), the cache dir (segments,
     * subtitles), and removes from queue + store. This applies to ALL states
     * (downloading, paused, error, queued, even completed).
     *
     * Use [removeDownload] instead if you want to keep completed files.
     */
    fun cancelDownload(downloadId: String) {
        Log.d(TAG, "cancelDownload: → $downloadId")
        val download = _queue.value.find { it.id == downloadId }
        if (download != null) {
            // FIX (Issue 9): Cancel any running FFmpeg process immediately.
            // Without this, FFmpeg keeps running even after the download is removed
            // from the queue, and the worker's retry loop re-downloads.
            try {
                com.arthenica.ffmpegkit.FFmpegKit.cancel()
                Log.d(TAG, "cancelDownload: ✓ cancelled FFmpeg")
            } catch (e: Exception) {
                Log.w(TAG, "cancelDownload: ⚠ could not cancel FFmpeg: ${e.message}")
            }

            // 1. Delete the episode folder from SAF (includes .mkv, manifest, etc.)
            //    Uses episodeUrl (stable) first, falls back to episodeName.
            //    Also cleans up empty anime/source directories.
            try {
                val provider = Injekt.get<DownloadProvider>()
                val deleted = provider.deleteEpisodeByUrl(download.episodeUrl, download.animeTitle, download.sourceName)
                if (!deleted) {
                    provider.deleteEpisode(download.episodeName, download.animeTitle, download.sourceName)
                }
                Log.d(TAG, "cancelDownload: ✓ deleted SAF folder (+ cleanup empty dirs)")
            } catch (e: Exception) {
                Log.w(TAG, "cancelDownload: ⚠ could not delete SAF folder: ${e.message}")
            }

            // 2. Delete the cache dir (segments, subtitles, concat, tmp .mkv)
            try {
                val cacheDir = java.io.File(context.cacheDir, "anikuta_dl/$downloadId")
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                    Log.d(TAG, "cancelDownload: ✓ deleted cache dir")
                }
            } catch (e: Exception) {
                Log.w(TAG, "cancelDownload: ⚠ could not delete cache dir: ${e.message}")
            }

            // 3. Set status to NOT_DOWNLOADED so the statusFlow emits (fixes C3)
            download.status = Download.State.NOT_DOWNLOADED
        }
        // 4. Remove from queue + store
        _queue.value = _queue.value.filter { it.id != downloadId }
        store.remove(downloadId)
        refreshStatusMap()
        Log.d(TAG, "cancelDownload: ✓ $downloadId")
    }

    /** Pause a download. */
    fun pauseDownload(downloadId: String) {
        Log.d(TAG, "pauseDownload: → $downloadId")
        _queue.value.find { it.id == downloadId }?.let { download ->
            download.status = Download.State.PAUSED
            store.update(downloadId, status = Download.State.PAUSED.value)
        }
        Log.d(TAG, "pauseDownload: ✓ $downloadId")
    }

    /** Resume a paused download. */
    fun resumeDownload(downloadId: String) {
        Log.d(TAG, "resumeDownload: → $downloadId")
        _queue.value.find { it.id == downloadId }?.let { download ->
            download.status = Download.State.QUEUE
            store.update(downloadId, status = Download.State.QUEUE.value)
        }
        startWork()
        Log.d(TAG, "resumeDownload: ✓ $downloadId")
    }

    /** Pause all downloads. */
    fun pauseAll() {
        Log.d(TAG, "pauseAll: → pausing all downloads")
        _queue.value.forEach { download ->
            if (download.status == Download.State.DOWNLOADING ||
                download.status == Download.State.QUEUE ||
                download.status == Download.State.RESOLVING) {
                download.status = Download.State.PAUSED
                store.update(download.id, status = Download.State.PAUSED.value)
            }
        }
        Log.d(TAG, "pauseAll: ✓ all paused")
    }

    /** Resume all paused downloads. */
    fun resumeAll() {
        Log.d(TAG, "resumeAll: → resuming all paused downloads")
        _queue.value.forEach { download ->
            if (download.status == Download.State.PAUSED) {
                download.status = Download.State.QUEUE
                store.update(download.id, status = Download.State.QUEUE.value)
            }
        }
        startWork()
        Log.d(TAG, "resumeAll: ✓ all resumed")
    }

    /**
     * Cancel all downloads and delete ALL associated files.
     * Calls [cancelDownload] for each download in the queue.
     */
    fun cancelAll() {
        Log.d(TAG, "cancelAll: → cancelling all downloads")
        val allIds = _queue.value.map { it.id }.toList()
        allIds.forEach { cancelDownload(it) }
        Log.d(TAG, "cancelAll: ✓ all cancelled")
    }

    /**
     * Retry all failed downloads.
     * Only retries downloads with status ERROR.
     */
    fun retryAll() {
        Log.d(TAG, "retryAll: → retrying all failed downloads")
        _queue.value.filter { it.status == Download.State.ERROR }.forEach { download ->
            retryDownload(download.id)
        }
        Log.d(TAG, "retryAll: ✓ all failed downloads retried")
    }

    /** Retry a failed download. */
    fun retryDownload(downloadId: String) {
        Log.d(TAG, "retryDownload: → $downloadId")
        val download = _queue.value.find { it.id == downloadId } ?: return
        download.status = Download.State.QUEUE
        download.progress = 0
        download.error = null
        store.update(downloadId, status = Download.State.QUEUE.value, progress = 0, error = null)
        startWork()
        Log.d(TAG, "retryDownload: ✓ $downloadId")
    }

    /**
     * Remove a download from the queue. Conditional based on status:
     *
     * - If status == DOWNLOADED: only remove from queue + store (KEEP the file).
     *   The detail page will still show the green checkmark by checking the filesystem.
     *
     * - If status != DOWNLOADED (incomplete): delete EVERYTHING — the episode folder
     *   from SAF, the cache dir, and remove from queue + store. Same as [cancelDownload].
     *
     * Per user decision: completed downloads are kept on disk when removed from the
     * downloads page. Incomplete downloads are fully cleaned up.
     */
    fun removeDownload(downloadId: String) {
        val download = _queue.value.find { it.id == downloadId } ?: return
        Log.d(TAG, "removeDownload: → $downloadId (status=${download.status})")

        if (download.status == Download.State.DOWNLOADED) {
            // Completed: keep the file, just remove from queue
            Log.d(TAG, "removeDownload: completed — keeping file, removing from queue")
            _queue.value = _queue.value.filter { it.id != downloadId }
            store.remove(downloadId)
            refreshStatusMap()
        } else {
            // Incomplete: delete everything (same as cancel)
            Log.d(TAG, "removeDownload: incomplete — deleting all files")
            cancelDownload(downloadId)
        }
    }

    /**
     * Clear all completed downloads from the queue.
     * Per user decision: this does NOT delete files (completed downloads are kept).
     */
    fun clearCompleted() {
        Log.d(TAG, "clearCompleted: → clearing completed downloads (keeping files)")
        _queue.value = _queue.value.filter { it.status != Download.State.DOWNLOADED }
        store.clearCompleted()
        refreshStatusMap()
    }

    /** Check if an episode is downloaded (for offline playback). */
    fun isEpisodeDownloaded(episodeName: String, animeTitle: String, sourceName: String): Boolean {
        val provider = Injekt.get<DownloadProvider>()
        return provider.isEpisodeDownloaded(episodeName, animeTitle, sourceName)
    }

    /** Get the downloaded video file URI for offline playback. */
    fun getDownloadedVideoUri(episodeName: String, animeTitle: String, sourceName: String): String? {
        val provider = Injekt.get<DownloadProvider>()
        val file = provider.getDownloadedVideoFile(episodeName, animeTitle, sourceName) ?: return null
        return file.uri.toString()
    }

    /** Delete a downloaded episode. */
    fun deleteDownloadedEpisode(episodeName: String, animeTitle: String, sourceName: String): Boolean {
        val provider = Injekt.get<DownloadProvider>()
        return provider.deleteEpisode(episodeName, animeTitle, sourceName)
    }

    /** Start the WorkManager job (unique work — only one instance runs at a time). */
    private fun startWork() {
        Log.d(TAG, "startWork: → starting download worker")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (prefs.downloadOverWifiOnly().get()) NetworkType.UNMETERED
                else NetworkType.CONNECTED
            )
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .addTag(UNIQUE_WORK)
            .build()

        // FIX (F3): Use APPEND_OR_REPLACE instead of KEEP.
        // KEEP drops new work requests when a worker is already running — so if a
        // second download is enqueued while the first is running, the worker won't
        // pick it up after the first finishes. APPEND_OR_REPLACE queues the new work
        // to run after the current work completes.
        workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        Log.d(TAG, "startWork: ✓ worker enqueued")
    }

    /** Restore the queue from persistent store on app start. */
    private fun restoreQueue() {
        val entries = store.getAll()
        if (entries.isNotEmpty()) {
            entries.forEach { addToLiveQueue(it) }
            Log.d(TAG, "restoreQueue: ✓ restored ${entries.size} downloads from store")
        }
    }

    /** Add a store entry to the live in-memory queue. */
    private fun addToLiveQueue(entry: DownloadStore.DownloadStoreEntry) {
        val download = Download(
            id = entry.id,
            anilistId = entry.anilistId,
            sourceId = entry.sourceId,
            sourceName = entry.sourceName,
            animeTitle = entry.animeTitle,
            episodeUrl = entry.episodeUrl,
            episodeName = entry.episodeName,
            episodeNumber = entry.episodeNumber,
            order = entry.order,
        ).apply {
            status = Download.State.fromValue(entry.status)
            progress = entry.progress
            totalSize = entry.totalSize
            downloadedBytes = entry.downloadedBytes
            error = entry.error
        }
        _queue.value = _queue.value + download
        refreshStatusMap()

        // Observe this download's statusFlow AND progressFlow and update the maps when they change.
        // This is the fix for bug B3 — status changes now propagate to the UI reactively.
        // Also fixes C5 — progress changes propagate for determinate progress display.
        GlobalScope.launch {
            download.statusFlow.collect { _ -> refreshStatusMap() }
        }
        GlobalScope.launch {
            download.progressFlow.collect { _ -> refreshStatusMap() }
        }
    }

    /**
     * Update a download's state in both the live queue and persistent store.
     * Called by the worker when status/progress changes.
     *
     * When status changes to DOWNLOADED, starts the 20-second auto-remove countdown
     * (Issue 4). The download stays visible in the downloads page with a countdown
     * bar, then is automatically removed from the queue (file is kept on disk).
     */
    fun updateDownloadState(
        downloadId: String,
        status: Download.State? = null,
        progress: Int? = null,
        totalSize: Long? = null,
        downloadedBytes: Long? = null,
        error: String? = null,
    ) {
        _queue.value.find { it.id == downloadId }?.let { download ->
            if (status != null) download.status = status
            if (progress != null) download.progress = progress
            if (totalSize != null) download.totalSize = totalSize
            if (downloadedBytes != null) download.downloadedBytes = downloadedBytes
            if (error != null) download.error = error

            // Issue 4: Start auto-remove countdown when download completes
            if (status == Download.State.DOWNLOADED) {
                startAutoRemoveCountdown(download)
            }
        }
        store.update(
            downloadId,
            status = status?.value,
            progress = progress,
            error = error,
        )
    }

    /**
     * Start the 20-second auto-remove countdown for a completed download (Issue 4).
     * When the countdown reaches 0, the download is removed from the queue
     * (the file is kept on disk — this is NOT a delete, just a queue removal).
     */
    private fun startAutoRemoveCountdown(download: Download) {
        download.autoRemoveProgress = 1.0f // start at 100%
        Log.d(TAG, "startAutoRemoveCountdown: ${download.episodeName} — 20s countdown started")

        GlobalScope.launch {
            val totalDurationMs = 20_000L // 20 seconds
            val updateIntervalMs = 100L // update every 100ms for smooth bar
            val steps = (totalDurationMs / updateIntervalMs).toInt()
            for (step in steps downTo 0) {
                // Check if the download was removed from the queue (e.g., user cancelled)
                val stillInQueue = _queue.value.any { it.id == download.id }
                if (!stillInQueue) {
                    Log.d(TAG, "startAutoRemoveCountdown: ${download.episodeName} removed from queue — stopping countdown")
                    return@launch
                }
                download.autoRemoveProgress = step.toFloat() / steps
                delay(updateIntervalMs)
            }

            // Countdown finished — remove from queue (keep the file)
            Log.d(TAG, "startAutoRemoveCountdown: ${download.episodeName} — countdown finished, removing from queue")
            _queue.value = _queue.value.filter { it.id != download.id }
            store.remove(download.id)
            refreshStatusMap()
        }
    }

    /**
     * Start the 10-second reconnect timeout for a download in RECONNECTING state (Issue 6).
     *
     * When the network drops, the download is set to RECONNECTING. If the network
     * doesn't come back within 10 seconds, the download is set to ERROR.
     * If the network comes back (worker restarts, picks up RECONNECTING → QUEUE),
     * the timeout is cancelled because the download is no longer RECONNECTING.
     */
    fun startReconnectTimeout(downloadId: String) {
        Log.d(TAG, "startReconnectTimeout: $downloadId — 10s timeout started")
        GlobalScope.launch {
            delay(10_000L) // 10 seconds

            // Check if the download is still in RECONNECTING state
            val download = _queue.value.find { it.id == downloadId }
            if (download != null && download.status == Download.State.RECONNECTING) {
                Log.w(TAG, "startReconnectTimeout: $downloadId — 10s elapsed, setting to ERROR")
                download.error = "Network connection lost"
                updateDownloadState(
                    downloadId,
                    status = Download.State.ERROR,
                    error = "Network connection lost",
                )
            }
        }
    }
}
