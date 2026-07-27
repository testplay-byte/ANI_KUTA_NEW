package app.anikuta.download.progress

import app.anikuta.download.Download
import app.anikuta.download.engine.DownloadManifest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Calculates and reports download progress based on segment completion.
 *
 * Progress is segment-based (not time-estimated):
 *   progress = (completedSegments / totalSegments) × 100
 *
 * This is accurate, granular, and naturally reflects resume state —
 * skipped (already-done) segments count as complete.
 *
 * Also tracks:
 * - Downloaded bytes / total bytes
 * - Download speed (bytes/sec, rolling average over 5 samples)
 *
 * The tracker updates the [Download] object directly, which propagates
 * to all observers via StateFlows.
 */
class ProgressTracker {

    companion object {
        private const val TAG = "ProgressTracker"
        private const val SPEED_SAMPLE_COUNT = 5
    }

    /** Speed calculation state — rolling average. */
    private data class SpeedSample(val timestamp: Long, val bytes: Long)

    private val speedSamples = mutableListOf<SpeedSample>()
    private var lastBytes: Long = 0L
    private var lastTimestamp: Long = 0L

    /**
     * Update progress from manifest state.
     * Called by the engine after each segment download.
     * No logging here — called for every segment, would be too noisy.
     */
    fun updateFromManifest(download: Download, manifest: DownloadManifest.Manifest) {
        val completed = manifest.segments.count { it.status == DownloadManifest.SegmentStatus.DONE }
        val total = manifest.totalSegments

        val progress = if (total > 0) {
            (completed * 100 / total).coerceIn(0, 99)
        } else {
            0
        }

        if (progress != download.progress) {
            download.progress = progress
        }

        download.downloadedBytes = manifest.downloadedBytes
        if (manifest.totalSizeBytes > 0) {
            download.totalSize = manifest.totalSizeBytes
        }
    }

    /**
     * Update byte count and calculate speed.
     * Called by the engine periodically during a segment download.
     * No logging here — called very frequently.
     */
    fun updateBytes(download: Download, downloadedBytes: Long, timestamp: Long = System.currentTimeMillis()) {
        download.downloadedBytes = downloadedBytes

        if (lastTimestamp > 0 && lastBytes > 0) {
            val timeDiff = timestamp - lastTimestamp
            val byteDiff = downloadedBytes - lastBytes

            if (timeDiff > 0 && byteDiff > 0) {
                addSpeedSample(timestamp, downloadedBytes)
                download.speed = calculateAverageSpeed()
            }
        }

        lastBytes = downloadedBytes
        lastTimestamp = timestamp
    }

    fun setTotalSize(download: Download, totalSize: Long) {
        download.totalSize = totalSize
    }

    fun markComplete(download: Download) {
        download.progress = 100
        download.speed = 0
    }

    /**
     * Reset speed tracking (called when starting a new segment or resuming).
     */
    fun resetSpeed() {
        speedSamples.clear()
        lastBytes = 0L
        lastTimestamp = 0L
    }

    // ---- Private helpers ----

    private fun addSpeedSample(timestamp: Long, bytes: Long) {
        speedSamples.add(SpeedSample(timestamp, bytes))
        if (speedSamples.size > SPEED_SAMPLE_COUNT) {
            speedSamples.removeAt(0)
        }
    }

    private fun calculateAverageSpeed(): Long {
        if (speedSamples.size < 2) return 0
        val first = speedSamples.first()
        val last = speedSamples.last()
        val timeDiff = last.timestamp - first.timestamp
        val byteDiff = last.bytes - first.bytes
        return if (timeDiff > 0) byteDiff * 1000 / timeDiff else 0
    }
}

// Top-level utility functions for formatting bytes/speed.
// Used by ProgressTracker, DownloadNotifier, SegmentDownloadEngine, and the UI.
fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}

fun formatSpeed(bytesPerSec: Long): String {
    return formatBytes(bytesPerSec) + "/s"
}
