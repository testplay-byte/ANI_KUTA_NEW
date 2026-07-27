package app.anikuta.download.engine

import android.content.Context
import android.util.Log
import app.anikuta.download.Download
import app.anikuta.download.DownloadPreferences
import app.anikuta.download.DownloadProvider
import app.anikuta.download.DownloadVideoResolver
import app.anikuta.download.progress.ProgressTracker
import app.anikuta.download.progress.formatBytes
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Single-pass FFmpeg download engine — mirrors aniyomi's approach exactly.
 *
 * Uses ONE FFmpeg call with the proxy URL (no -ss, no -t, no segments):
 *   ffmpeg -headers '...' -i "http://localhost:PORT/m3u8?url=..." \
 *          -map 0:v -map 0:a? -map 0:s? -c copy -f matroska output.mkv
 *
 * FFmpeg's HLS demuxer:
 * 1. Fetches m3u8 through the proxy (native HTTP, not OkHttp)
 * 2. Downloads .ts segments through the proxy (CDN auth handled by proxy)
 * 3. Muxes into one .mkv with correct duration and size
 * 4. No -ss seeking = no duplicate content
 *
 * This is the proven approach used by aniyomi. The only downside is no resume
 * after failure — but aniyomi has the same limitation and it works fine.
 *
 * Progress is tracked via FFmpeg's statistics callback (statistics.time vs
 * FFprobe duration), same as aniyomi.
 */
class SinglePassDownloadEngine(
    private val context: Context,
    private val provider: DownloadProvider,
    private val resolver: DownloadVideoResolver,
    private val progressTracker: ProgressTracker,
    private val downloadPrefs: DownloadPreferences,
) : DownloadEngine {

    companion object {
        private const val TAG = "SinglePassEngine"
        private const val MIN_DISK_SPACE_MB = 200L
        private const val FINAL_VIDEO_NAME = "video.mkv"
        private const val CACHE_BASE_DIR = "anikuta_dl"
    }

    private data class CancelState(@Volatile var paused: Boolean, @Volatile var cancelled: Boolean)
    private val cancelStates = ConcurrentHashMap<String, CancelState>()

    override suspend fun resolve(download: Download): Boolean {
        Log.d(TAG, "resolve: → ${download.episodeName}")
        download.status = Download.State.RESOLVING
        return try {
            val video = resolver.resolve(download)
            if (video == null) {
                download.error = "Could not resolve video URL"
                return false
            }
            download.video = video
            val parsed = app.anikuta.ui.detail.VideoTitleParser.parse(video)
            download.serverName = parsed.server
            download.audioVersion = parsed.audio.name
            download.qualityLabel = "${parsed.quality ?: 0}p"
            Log.d(TAG, "resolve: ✓ ${video.videoTitle} (${download.serverName}/${download.audioVersion}/${download.qualityLabel})")
            true
        } catch (e: Exception) {
            download.error = "Resolve failed: ${e.message}"
            false
        }
    }

    override suspend fun download(download: Download): Boolean {
        Log.d(TAG, "download: → ${download.episodeName}")
        download.status = Download.State.DOWNLOADING
        progressTracker.resetSpeed()

        // NOTE: Do NOT call disableRedirection() here — it also disables statistics
        // callbacks, which we need for progress tracking. FFmpeg logs will appear in
        // logcat but that's acceptable for a single FFmpeg call (not 144 like the
        // segment engine).

        if (!hasMinDiskSpace()) {
            download.error = "Insufficient disk space"
            return false
        }

        val video = download.video ?: run {
            if (!resolve(download)) return false
            download.video ?: return false
        }

        val cacheDir = getCacheDir(download)
        val outputFile = File(cacheDir, FINAL_VIDEO_NAME)

        // Build FFmpeg command — mirrors aniyomi's getFFmpegOptions
        val cmd = buildFFmpegCommand(video, outputFile.absolutePath)
        Log.d(TAG, "download: FFmpeg command length=${cmd.length}")

        // Get duration for progress estimation
        val durationMs = getVideoDurationMs(video)
        Log.d(TAG, "download: estimated duration=${durationMs}ms (${durationMs / 1000 / 60}m${(durationMs / 1000) % 60}s)")

        // Size estimate: quality-based bitrate × duration.
        // This is the proven method that worked well. FFprobe bitrate was unreliable
        // (returned 1MB for some sources) so we stick with the quality-based approach.
        val bitrateBytesPerSec = when {
            download.qualityLabel.contains("1080") -> 400 * 1024
            download.qualityLabel.contains("720") -> 150 * 1024
            else -> 50 * 1024 // 360p or unknown
        }
        var estimatedTotalBytes = (durationMs / 1000 * bitrateBytesPerSec).toLong().coerceAtLeast(1_000_000)
        progressTracker.setTotalSize(download, estimatedTotalBytes)
        Log.d(TAG, "download: estimated total size=${estimatedTotalBytes / 1024 / 1024}MB (bitrate=${bitrateBytesPerSec / 1024}KB/s)")

        // Start FFmpeg async
        download.status = Download.State.DOWNLOADING
        val args = FFmpegKitConfig.parseArguments(cmd)
        Log.d(TAG, "download: executing FFmpeg async with ${args.size} args")

        var ffmpegFinished = false
        var ffmpegSession: com.arthenica.ffmpegkit.FFmpegSession? = null

        val ffmpegJob = kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            ffmpegSession = com.arthenica.ffmpegkit.FFmpegKit.executeWithArgumentsAsync(
                args,
                { session -> ffmpegFinished = true },
                null,
                null, // no statistics callback — we poll file size instead
            )
        }

        // Poll output file size for progress.
        //
        // Estimation strategy (robust):
        // 1. Initial estimate: quality-based bitrate × FFprobe duration
        // 2. NEVER shrink the total estimate during the first 30% of the download
        //    (early rate is unreliable — FFmpeg is buffering, not downloading at full speed)
        // 3. After 30%: only refine DOWNWARD if the rate-based estimate is consistently
        //    smaller (by at least 30%) for 5 consecutive polls
        // 4. If file size stabilizes (8 consecutive polls with no change) and FFmpeg
        //    hasn't finished, the real total = current file size
        // 5. Never let the total go below the current downloaded bytes
        var lastFileSize = 0L
        var stableSizeCount = 0
        var calibratedTotal = estimatedTotalBytes
        var shrinkVoteCount = 0 // consecutive polls where rate-based estimate was smaller
        val pollIntervalMs = 500L
        val startTime = System.currentTimeMillis()
        val minPollsBeforeShrink = 20 // ~10 seconds (20 × 500ms) before allowing shrink
        var pollCount = 0

        while (!ffmpegFinished) {
            pollCount++
            val cs = cancelStates[download.id]
            if (cs?.cancelled == true || download.status == Download.State.NOT_DOWNLOADED) {
                ffmpegSession?.cancel()
                break
            }
            if (cs?.paused == true || download.status == Download.State.PAUSED) {
                ffmpegSession?.cancel()
                break
            }

            val currentSize = if (outputFile.exists()) outputFile.length() else 0L

            if (currentSize > 0) {
                download.downloadedBytes = currentSize

                // Never let total go below current size
                if (calibratedTotal < currentSize) {
                    calibratedTotal = currentSize
                    progressTracker.setTotalSize(download, calibratedTotal)
                }

                if (currentSize == lastFileSize) {
                    // File not growing — maybe FFmpeg finished writing
                    stableSizeCount++
                    if (stableSizeCount >= 8 && currentSize < calibratedTotal) {
                        // File stopped growing for 4 seconds — likely done
                        calibratedTotal = currentSize
                        progressTracker.setTotalSize(download, calibratedTotal)
                        Log.d(TAG, "download: file stabilized → total=${calibratedTotal / 1024 / 1024}MB")
                    }
                } else {
                    stableSizeCount = 0
                    lastFileSize = currentSize

                    // Only consider shrinking after enough polls and if current progress > 30%
                    val currentProgress = if (calibratedTotal > 0) currentSize.toDouble() / calibratedTotal else 0.0
                    if (pollCount > minPollsBeforeShrink && currentProgress > 0.3) {
                        val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                        if (elapsedSec > 5) {
                            val bytesPerSec = currentSize.toDouble() / elapsedSec
                            val rateBasedTotal = (bytesPerSec * (durationMs / 1000.0)).toLong()
                            // Only shrink if rate-based estimate is at least 30% smaller
                            // AND larger than current size (sanity check)
                            if (rateBasedTotal < calibratedTotal * 0.7 && rateBasedTotal > currentSize) {
                                shrinkVoteCount++
                                // Require 5 consecutive votes before shrinking
                                if (shrinkVoteCount >= 5) {
                                    calibratedTotal = rateBasedTotal
                                    progressTracker.setTotalSize(download, calibratedTotal)
                                    Log.d(TAG, "download: refined total → ${calibratedTotal / 1024 / 1024}MB (rate-based after $pollCount polls)")
                                    shrinkVoteCount = 0
                                }
                            } else {
                                shrinkVoteCount = 0
                            }
                        }
                    }
                }

                // Calculate progress
                val progress = if (calibratedTotal > 0) {
                    ((currentSize.toDouble() / calibratedTotal) * 100).toInt().coerceIn(0, 99)
                } else 0

                if (progress != download.progress && progress > 0) {
                    download.progress = progress
                    // Log every 5% or every 10 polls
                    if (progress % 5 == 0 || pollCount % 10 == 0) {
                        Log.d(TAG, "download: progress=$progress% (${currentSize / 1024 / 1024}MB / ~${calibratedTotal / 1024 / 1024}MB)")
                    }
                }
            }

            delay(pollIntervalMs)
        }

        // Wait for the FFmpeg job to fully complete
        ffmpegJob.join()

        val session = ffmpegSession

        val cs = cancelStates[download.id]
        if (cs?.cancelled == true || download.status == Download.State.NOT_DOWNLOADED) {
            Log.d(TAG, "download: ✕ cancelled")
            cleanupCache(download)
            return false
        }

        if (session == null || !ReturnCode.isSuccess(session.returnCode)) {
            download.error = "FFmpeg failed (rc=${session?.returnCode?.value ?: -1})"
            Log.e(TAG, "download: ❌ ${download.error}")
            cleanupCache(download)
            return false
        }

        if (!outputFile.exists() || outputFile.length() == 0L) {
            download.error = "Output file is empty"
            Log.e(TAG, "download: ❌ ${download.error}")
            cleanupCache(download)
            return false
        }

        val actualSize = outputFile.length()
        Log.d(TAG, "download: ✓ FFmpeg success, size=${formatBytes(actualSize)}")

        // FFprobe the final file for actual duration + resolution
        val mediaInfo = getMediaInfo(outputFile)
        download.actualDurationMs = mediaInfo.durationMs
        download.actualResolution = mediaInfo.resolution
        progressTracker.setTotalSize(download, actualSize)
        download.downloadedBytes = actualSize
        Log.d(TAG, "download: final mkv duration=${mediaInfo.durationMs}ms, resolution=${mediaInfo.resolution}")

        // Copy to SAF
        if (!copyMkvToSaf(outputFile, download)) {
            download.error = "Failed to copy video to storage"
            return false
        }

        // Write .episode_url file
        provider.getEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            ?.let { provider.writeEpisodeUrlFile(it, download.episodeUrl) }

        // Cleanup
        cleanupCache(download)
        progressTracker.markComplete(download)
        download.status = Download.State.DOWNLOADED
        Log.d(TAG, "download: ✓ COMPLETE — ${download.episodeName} " +
            "(${formatBytes(actualSize)}, ${mediaInfo.resolution}, ${download.serverName}/${download.audioVersion}/${download.qualityLabel})")
        return true
    }

    /**
     * Build the FFmpeg command — mirrors aniyomi's getFFmpegOptions exactly.
     * No -ss, no -t — single pass with -c copy.
     */
    private fun buildFFmpegCommand(video: Video, outputPath: String): String {
        val headerOptions = buildHeaderOptions(video)

        val cmd = StringBuilder()

        // Video input with headers
        if (video.videoUrl.startsWith("http")) {
            cmd.append("-headers '")
            cmd.append(headerOptions.replace("'", "\\'"))
            cmd.append("' ")
        }
        cmd.append("-i \"").append(video.videoUrl).append("\"")

        // Subtitle inputs
        video.subtitleTracks.forEach { track ->
            cmd.append(" -headers '")
            cmd.append(headerOptions.replace("'", "\\'"))
            cmd.append("' ")
            cmd.append("-i \"").append(track.url).append("\"")
        }

        // Audio inputs (external audio tracks if any)
        video.audioTracks.forEach { track ->
            cmd.append(" -headers '")
            cmd.append(headerOptions.replace("'", "\\'"))
            cmd.append("' ")
            cmd.append("-i \"").append(track.url).append("\"")
        }

        // Mapping: video from input 0, audio from input 0, subtitles from extra inputs
        cmd.append(" -map 0:v -map 0:a?")
        video.subtitleTracks.forEachIndexed { i, _ ->
            cmd.append(" -map ").append(i + 1).append(":s?")
        }

        // Codec: copy all streams (no re-encoding)
        cmd.append(" -c copy -f matroska")

        // Subtitle metadata
        video.subtitleTracks.forEachIndexed { i, track ->
            cmd.append(" -metadata:s:s:").append(i).append(" \"title=").append(track.lang).append("\"")
        }

        // Output file
        cmd.append(" \"").append(outputPath).append("\" -y")

        return cmd.toString()
    }

    private fun buildHeaderOptions(video: Video): String {
        return video.headers?.let { headers ->
            headers.toMultimap().map { (key, values) ->
                "$key: ${values.firstOrNull() ?: ""}"
            }.joinToString("\r\n")
        } ?: "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
    }

    private suspend fun getVideoDurationMs(video: Video): Long = withContext(Dispatchers.IO) {
        try { FFmpegKitConfig.enableRedirection() } catch (_: Exception) {}
        val headerOptions = buildHeaderOptions(video)
        val cmd = if (video.videoUrl.startsWith("http")) {
            "-headers '$headerOptions' -i \"${video.videoUrl}\" -show_entries format=duration -v quiet -of csv=\"p=0\""
        } else {
            "-i \"${video.videoUrl}\" -show_entries format=duration -v quiet -of csv=\"p=0\""
        }
        val session = FFprobeKit.execute(cmd)
        try { FFmpegKitConfig.disableRedirection() } catch (_: Exception) {}
        val output = session.output?.trim()
        if (ReturnCode.isSuccess(session.returnCode) && !output.isNullOrEmpty()) {
            val durationSec = output.toDoubleOrNull() ?: 0.0
            (durationSec * 1000).toLong()
        } else {
            24 * 60 * 1000L // fallback
        }
    }

    /**
     * Get the video's bitrate (bits per second) via FFprobe.
     * Returns 0 if the bitrate can't be determined.
     * Used for accurate file size estimation.
     */
    private suspend fun getVideoBitrate(video: Video): Long = withContext(Dispatchers.IO) {
        try { FFmpegKitConfig.enableRedirection() } catch (_: Exception) {}
        val headerOptions = buildHeaderOptions(video)
        // Query both format-level and stream-level bitrate; use whichever is available
        val cmd = if (video.videoUrl.startsWith("http")) {
            "-headers '$headerOptions' -i \"${video.videoUrl}\" -show_entries format=bit_rate -v quiet -of csv=\"p=0\""
        } else {
            "-i \"${video.videoUrl}\" -show_entries format=bit_rate -v quiet -of csv=\"p=0\""
        }
        val session = FFprobeKit.execute(cmd)
        try { FFmpegKitConfig.disableRedirection() } catch (_: Exception) {}
        val output = session.output?.trim()
        if (ReturnCode.isSuccess(session.returnCode) && !output.isNullOrEmpty()) {
            val bitrate = output.toLongOrNull() ?: 0L
            if (bitrate > 0) {
                Log.d(TAG, "getVideoBitrate: ✓ ${bitrate / 1000} kbps")
                bitrate
            } else 0L
        } else {
            Log.d(TAG, "getVideoBitrate: not available, using fallback")
            0L
        }
    }

    private data class MediaInfo(val durationMs: Long, val resolution: String)

    private suspend fun getMediaInfo(file: File): MediaInfo = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext MediaInfo(0L, "")
        try { FFmpegKitConfig.enableRedirection() } catch (_: Exception) {}

        val durCmd = "-i \"${file.absolutePath}\" -show_entries format=duration -v quiet -of csv=\"p=0\""
        val durSession = FFprobeKit.execute(durCmd)
        val durStr = durSession.output?.trim()
        val durationMs = if (ReturnCode.isSuccess(durSession.returnCode) && !durStr.isNullOrEmpty()) {
            (durStr.toDoubleOrNull() ?: 0.0) * 1000
        } else 0L

        val resCmd = "-i \"${file.absolutePath}\" -show_entries stream=width,height -select_streams v:0 -v quiet -of csv=\"p=0\""
        val resSession = FFprobeKit.execute(resCmd)
        val resStr = resSession.output?.trim()
        val resolution = if (ReturnCode.isSuccess(resSession.returnCode) && !resStr.isNullOrEmpty()) {
            resStr.replace(",", "x")
        } else ""

        try { FFmpegKitConfig.disableRedirection() } catch (_: Exception) {}
        MediaInfo(durationMs.toLong(), resolution)
    }

    fun resetFlags(downloadId: String) {
        cancelStates[downloadId] = CancelState(false, false)
    }

    override suspend fun pause(download: Download) {
        cancelStates[download.id]?.paused = true
        // Cancel the running FFmpeg session — it will stop
        try { FFmpegKit.cancel() } catch (_: Exception) {}
        delay(500)
        download.status = Download.State.PAUSED
    }

    override suspend fun cancel(download: Download) {
        cancelStates[download.id]?.cancelled = true
        try { FFmpegKit.cancel() } catch (_: Exception) {}
        delay(300)
        cleanupCache(download)
        download.status = Download.State.NOT_DOWNLOADED
    }

    override fun isCompleted(download: Download): Boolean {
        return provider.getDownloadedVideoFile(download.episodeName, download.animeTitle, download.sourceName) != null
    }

    private suspend fun copyMkvToSaf(cacheMkv: File, download: Download): Boolean = withContext(Dispatchers.IO) {
        if (!cacheMkv.exists() || cacheMkv.length() == 0L) return@withContext false
        val episodeDir = provider.getEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            ?: return@withContext false
        val safFile = episodeDir.createFile(FINAL_VIDEO_NAME) ?: return@withContext false
        try {
            cacheMkv.inputStream().use { input ->
                safFile.openOutputStream(false).use { output -> input.copyTo(output) }
            }
            Log.d(TAG, "copyMkvToSaf: ✓ ${formatBytes(cacheMkv.length())} → SAF")
            true
        } catch (e: Exception) {
            Log.e(TAG, "copyMkvToSaf: ❌ ${e.message}")
            false
        }
    }

    private fun getCacheDir(download: Download): File {
        return File(context.cacheDir, "$CACHE_BASE_DIR/${download.id}").apply { mkdirs() }
    }

    private fun hasMinDiskSpace(): Boolean {
        return try {
            val stat = android.os.StatFs(context.filesDir.path)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            freeBytes > MIN_DISK_SPACE_MB * 1024 * 1024
        } catch (e: Exception) { true }
    }

    private fun cleanupCache(download: Download) {
        try {
            val cacheDir = File(context.cacheDir, "$CACHE_BASE_DIR/${download.id}")
            if (cacheDir.exists()) cacheDir.deleteRecursively()
        } catch (_: Exception) {}
    }
}
