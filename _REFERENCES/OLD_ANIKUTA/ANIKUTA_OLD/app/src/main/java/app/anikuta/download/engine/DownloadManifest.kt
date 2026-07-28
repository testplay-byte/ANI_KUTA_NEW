package app.anikuta.download.engine

import android.content.Context
import android.util.Log
import app.anikuta.download.DownloadProvider
import com.hippo.unifile.UniFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Manages the download manifest — a JSON file that tracks segment-level
 * download state for resume capability.
 *
 * The manifest is stored at:
 *   downloads/<source>/<anime>/<episode>/manifest.json
 *
 * It records:
 * - The resolved video URL (for reference; re-resolved on resume)
 * - Total video duration + segment count
 * - Per-segment status (done / partial / pending)
 * - Subtitle track download status
 * - Total + downloaded byte counts
 *
 * On resume, the engine reads the manifest, skips "done" segments,
 * deletes "partial" segments, and downloads only "pending" ones.
 *
 * Atomic writes: manifest is written to manifest.json.tmp first,
 * then renamed to manifest.json. This prevents corruption if the app
 * crashes during a write.
 */
class DownloadManifest(
    private val context: Context,
    private val provider: DownloadProvider,
) {
    companion object {
        private const val TAG = "DownloadManifest"
        const val MANIFEST_FILE = "manifest.json"
        private const val MANIFEST_TMP = "manifest.json.tmp"
        const val SEGMENT_DURATION_SEC = 60
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Serializable manifest data model.
     */
    @Serializable
    data class Manifest(
        val downloadId: String,
        val anilistId: Int,
        val sourceId: Long,
        val sourceName: String,
        val animeTitle: String,
        val episodeUrl: String,
        val episodeName: String,
        val episodeNumber: Float,
        /** Resolved video URL at resolution time (for reference; re-resolved on resume). */
        val videoUrl: String = "",
        /** Total video duration in milliseconds. */
        val totalDurationMs: Long = 0L,
        /** Duration of each segment in seconds. */
        val segmentDurationSec: Int = SEGMENT_DURATION_SEC,
        /** Total number of segments. */
        val totalSegments: Int = 0,
        /** Per-segment state. */
        val segments: List<SegmentState> = emptyList(),
        /** Subtitle tracks. */
        val subtitles: List<SubtitleState> = emptyList(),
        /** Estimated total file size in bytes (-1 if unknown). */
        val totalSizeBytes: Long = -1L,
        /** Downloaded bytes so far. */
        val downloadedBytes: Long = 0L,
        /** Which engine created this manifest: "hls" (direct HTTP) or "ffmpeg-ss" (legacy). Empty = old. */
        val playlistType: String = "",
        /** Timestamps. */
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
    )

    @Serializable
    data class SegmentState(
        val index: Int,
        val status: SegmentStatus,
        val fileName: String,
        val sizeBytes: Long = 0L,
        /** Start time in milliseconds. */
        val startTimeMs: Long = 0L,
        /** HLS: absolute segment URL (for direct HTTP download). Empty for legacy engine. */
        val url: String = "",
        /** HLS: per-segment duration in milliseconds (from #EXTINF). */
        val durationMs: Long = 0L,
    )

    @Serializable
    enum class SegmentStatus(val value: String) {
        PENDING("pending"),
        DOWNLOADING("downloading"),
        DONE("done"),
        PARTIAL("partial"),
        ERROR("error");

        companion object {
            fun fromValue(v: String): SegmentStatus = entries.find { it.value == v } ?: PENDING
        }
    }

    @Serializable
    data class SubtitleState(
        val url: String,
        val lang: String,
        val downloaded: Boolean,
        val fileName: String,
    )

    // ---- Read / Write ----

    /**
     * Read the manifest for a download. Returns null if no manifest exists.
     */
    suspend fun read(download: app.anikuta.download.Download): Manifest? = withContext(Dispatchers.IO) {
        val manifestFile = getManifestFile(download) ?: return@withContext null
        try {
            val content = manifestFile.openInputStream().bufferedReader().use { it.readText() }
            val manifest = json.decodeFromString(Manifest.serializer(), content)
            Log.d(TAG, "read: ✓ ${download.episodeName} — ${manifest.totalSegments} segments, " +
                "${manifest.segments.count { it.status == SegmentStatus.DONE }} done")
            manifest
        } catch (e: Exception) {
            Log.e(TAG, "read: ❌ ${download.episodeName}: ${e.message}")
            null
        }
    }

    /**
     * Write the manifest atomically (write to .tmp, then rename).
     * Logging is suppressed (called for every segment — too noisy).
     */
    suspend fun write(download: app.anikuta.download.Download, manifest: Manifest) = withContext(Dispatchers.IO) {
        val episodeDir = provider.getEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            ?: return@withContext

        val updatedAt = manifest.copy(updatedAt = System.currentTimeMillis())
        val content = json.encodeToString(Manifest.serializer(), updatedAt)

        try {
            val tmpFile = episodeDir.createFile(MANIFEST_TMP) ?: return@withContext
            tmpFile.openOutputStream(false).bufferedWriter().use { it.write(content) }

            val existingManifest = episodeDir.findFile(MANIFEST_FILE)
            existingManifest?.delete()
            tmpFile.renameTo(MANIFEST_FILE)
            // No log here — called for every segment, would be too noisy
        } catch (e: Exception) {
            Log.e(TAG, "write: ❌ ${download.episodeName}: ${e.message}")
        }
    }

    /**
     * Delete the manifest file. Called after successful mux.
     */
    suspend fun delete(download: app.anikuta.download.Download) = withContext(Dispatchers.IO) {
        val manifestFile = getManifestFile(download)
        manifestFile?.delete()
        Log.d(TAG, "delete: ✓ ${download.episodeName}")
    }

    /**
     * Check if a manifest exists for this download.
     */
    fun exists(download: app.anikuta.download.Download): Boolean {
        return getManifestFile(download) != null
    }

    /**
     * Create a new manifest with all segments set to PENDING.
     */
    fun createFresh(
        download: app.anikuta.download.Download,
        videoUrl: String,
        totalDurationMs: Long,
        subtitles: List<SubtitleState> = emptyList(),
        totalSizeBytes: Long = -1L,
    ): Manifest {
        val totalSegments = if (totalDurationMs > 0) {
            kotlin.math.ceil(totalDurationMs.toDouble() / (SEGMENT_DURATION_SEC * 1000)).toInt()
        } else {
            Log.w(TAG, "createFresh: totalDurationMs=$totalDurationMs, defaulting to 0 segments")
            0
        }

        val segments = (0 until totalSegments).map { index ->
            SegmentState(
                index = index,
                status = SegmentStatus.PENDING,
                fileName = "seg_%04d.ts".format(index),
                sizeBytes = 0L,
                startTimeMs = index.toLong() * SEGMENT_DURATION_SEC * 1000,
            )
        }

        val manifest = Manifest(
            downloadId = download.id,
            anilistId = download.anilistId,
            sourceId = download.sourceId,
            sourceName = download.sourceName,
            animeTitle = download.animeTitle,
            episodeUrl = download.episodeUrl,
            episodeName = download.episodeName,
            episodeNumber = download.episodeNumber,
            videoUrl = videoUrl,
            totalDurationMs = totalDurationMs,
            segmentDurationSec = SEGMENT_DURATION_SEC,
            totalSegments = totalSegments,
            segments = segments,
            subtitles = subtitles,
            totalSizeBytes = totalSizeBytes,
            downloadedBytes = 0L,
        )

        Log.d(TAG, "createFresh: ✓ created manifest for ${download.episodeName} — " +
            "$totalSegments segments, duration=${totalDurationMs}ms, " +
            "${subtitles.size} subtitle tracks, estimated size=${totalSizeBytes} bytes")
        return manifest
    }

    /**
     * Create a new manifest for an HLS direct download (per-segment URLs from m3u8).
     *
     * Unlike [createFresh] (which uses FFmpeg -ss with fixed segment durations),
     * this uses the REAL segment URLs and durations from the m3u8 playlist.
     * Each segment has its own URL and duration (they can vary).
     */
    fun createFreshHls(
        download: app.anikuta.download.Download,
        videoUrl: String,
        totalDurationMs: Long,
        segmentUrls: List<String>,
        segmentDurationsMs: List<Long>,
        subtitles: List<SubtitleState> = emptyList(),
        totalSizeBytes: Long = -1L,
    ): Manifest {
        val totalSegments = segmentUrls.size

        val segments = (0 until totalSegments).map { index ->
            SegmentState(
                index = index,
                status = SegmentStatus.PENDING,
                fileName = "seg_%04d.ts".format(index),
                sizeBytes = 0L,
                startTimeMs = segmentDurationsMs.take(index).sum(),
                url = segmentUrls[index],
                durationMs = segmentDurationsMs.getOrNull(index) ?: 0L,
            )
        }

        val manifest = Manifest(
            downloadId = download.id,
            anilistId = download.anilistId,
            sourceId = download.sourceId,
            sourceName = download.sourceName,
            animeTitle = download.animeTitle,
            episodeUrl = download.episodeUrl,
            episodeName = download.episodeName,
            episodeNumber = download.episodeNumber,
            videoUrl = videoUrl,
            totalDurationMs = totalDurationMs,
            segmentDurationSec = 0, // 0 = variable (HLS segments have different durations)
            totalSegments = totalSegments,
            segments = segments,
            subtitles = subtitles,
            totalSizeBytes = totalSizeBytes,
            downloadedBytes = 0L,
            playlistType = "hls",
        )

        Log.d(TAG, "createFreshHls: ✓ created HLS manifest for ${download.episodeName} — " +
            "$totalSegments segments, duration=${totalDurationMs}ms, " +
            "${subtitles.size} subtitle tracks")
        return manifest
    }

    /**
     * Mark a segment as done and update byte counts.
     */
    fun markSegmentDone(manifest: Manifest, index: Int, sizeBytes: Long): Manifest {
        val segments = manifest.segments.map { seg ->
            if (seg.index == index) {
                seg.copy(status = SegmentStatus.DONE, sizeBytes = sizeBytes)
            } else seg
        }
        val downloadedBytes = segments.sumOf { if (it.status == SegmentStatus.DONE) it.sizeBytes else 0L }
        return manifest.copy(segments = segments, downloadedBytes = downloadedBytes, updatedAt = System.currentTimeMillis())
    }

    /**
     * Mark a segment as partial (interrupted mid-download).
     */
    fun markSegmentPartial(manifest: Manifest, index: Int): Manifest {
        val segments = manifest.segments.map { seg ->
            if (seg.index == index) seg.copy(status = SegmentStatus.PARTIAL) else seg
        }
        return manifest.copy(segments = segments, updatedAt = System.currentTimeMillis())
    }

    /**
     * Mark a segment as downloading (in-progress).
     */
    fun markSegmentDownloading(manifest: Manifest, index: Int): Manifest {
        val segments = manifest.segments.map { seg ->
            if (seg.index == index) seg.copy(status = SegmentStatus.DOWNLOADING) else seg
        }
        return manifest.copy(segments = segments, updatedAt = System.currentTimeMillis())
    }

    /**
     * Get the next segment to download. Returns null if all are done.
     *
     * Picks up segments with status PENDING, PARTIAL, ERROR, or DOWNLOADING.
     * DOWNLOADING is included because: if the app was killed mid-segment, the
     * segment stays in DOWNLOADING state. On resume, we must re-download it
     * (the partial file was in cache which may have been cleared).
     *
     * FIX (F1): Previously, DOWNLOADING segments were NOT picked up, causing
     * getNextPendingSegment to return null (loop exits, "all segments done"
     * logged) while allSegmentsDone returned false (DOWNLOADING != DONE) →
     * "Some segments failed to download" false positive.
     */
    fun getNextPendingSegment(manifest: Manifest): SegmentState? {
        return manifest.segments.firstOrNull {
            it.status == SegmentStatus.PENDING ||
            it.status == SegmentStatus.PARTIAL ||
            it.status == SegmentStatus.ERROR ||
            it.status == SegmentStatus.DOWNLOADING
        }
    }

    /**
     * Get the count of completed segments.
     */
    fun getCompletedSegmentCount(manifest: Manifest): Int {
        return manifest.segments.count { it.status == SegmentStatus.DONE }
    }

    /**
     * Check if all segments are done.
     */
    fun allSegmentsDone(manifest: Manifest): Boolean {
        return manifest.segments.isNotEmpty() && manifest.segments.all { it.status == SegmentStatus.DONE }
    }

    // ---- Private helpers ----

    private fun getManifestFile(download: app.anikuta.download.Download): UniFile? {
        val episodeDir = provider.findEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            ?: return null
        return episodeDir.findFile(MANIFEST_FILE)
    }
}
