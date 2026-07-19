package app.anikuta.download.engine

import app.anikuta.download.Download
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for download strategies.
 *
 * The engine is responsible for the actual downloading of a single episode.
 * It does NOT manage the queue, WorkManager, or notifications — those are
 * handled by [app.anikuta.download.DownloadWorker] and
 * [app.anikuta.download.DownloadManager].
 *
 * This interface allows swapping download strategies without touching the
 * rest of the system:
 * - [SegmentDownloadEngine] — segment-based with manifest resume (default)
 * - Future: HttpRangeDownloadEngine — HTTP byte-range for direct URLs
 * - Future: HybridDownloadEngine — picks the best strategy per URL type
 *
 * Lifecycle:
 * 1. [resolve] — called first to resolve the video URL + get video metadata
 * 2. [download] — called to start/resume downloading
 * 3. [pause] — called to pause (saves state for resume)
 * 4. [cancel] — called to cancel (cleans up partial files)
 *
 * The engine updates [Download.status], [Download.progress],
 * [Download.totalSize], [Download.downloadedBytes], and [Download.speed]
 * directly on the Download object. The queue UI observes these via StateFlows.
 */
interface DownloadEngine {

    /**
     * Resolve the video URL from the source. This handles expired proxy URLs
     * by re-calling the source's getHosterList/getVideoList.
     *
     * Sets download.status = RESOLVING during this phase.
     * Sets download.video on success.
     *
     * @return true if resolution succeeded, false otherwise
     */
    suspend fun resolve(download: Download): Boolean

    /**
     * Start or resume downloading. This is the main download phase.
     *
     * For segment-based engines:
     * - Reads the manifest if it exists (resume)
     * - Downloads missing segments
     * - Muxes into final .mkv when all segments are done
     *
     * Updates download.status, download.progress, download.totalSize,
     * download.downloadedBytes, download.speed during execution.
     *
     * @return true if download completed successfully, false on error
     */
    suspend fun download(download: Download): Boolean

    /**
     * Pause the download. Saves state so [download] can resume later.
     * The engine should stop after the current segment and persist state.
     */
    suspend fun pause(download: Download)

    /**
     * Cancel the download and clean up partial files.
     * Removes tmp directory and manifest.
     */
    suspend fun cancel(download: Download)

    /**
     * Check if a download is already complete (has a valid .mkv file).
     * Used for fast lookups without starting the engine.
     */
    fun isCompleted(download: Download): Boolean
}
