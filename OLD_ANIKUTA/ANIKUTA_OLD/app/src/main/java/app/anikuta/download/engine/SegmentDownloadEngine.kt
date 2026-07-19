package app.anikuta.download.engine

import android.content.Context
import android.util.Log
import app.anikuta.download.Download
import app.anikuta.download.DownloadProvider
import app.anikuta.download.DownloadVideoResolver
import app.anikuta.download.progress.ProgressTracker
import app.anikuta.download.progress.formatBytes
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.StatisticsCallback
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File

/**
 * Segment-based download engine with full resume capability.
 *
 * Architecture:
 * 1. RESOLVING: Resolve video URL + get duration via FFprobe
 * 2. DOWNLOADING: Download in 10-second segments to CACHE DIR (real file paths)
 * 3. MUXING: Concatenate segments + mux subtitles into final .mkv in cache, then copy to SAF
 *
 * Segments/subtitles/concat/mux all use cache dir (real file paths) to avoid
 * the FFmpeg concat demuxer SIGSEGV with saf: URIs. The manifest lives in SAF
 * (survives app kills). After muxing, the final .mkv is copied from cache to SAF.
 *
 * Logging policy: Only key milestones at Log.d. Errors at Log.e. Warnings at Log.w.
 * Per-segment logs are suppressed except every 10th segment (to show progress without spam).
 */
class SegmentDownloadEngine(
    private val context: Context,
    private val provider: DownloadProvider,
    private val resolver: DownloadVideoResolver,
    private val manifestManager: DownloadManifest,
    private val progressTracker: ProgressTracker,
) : DownloadEngine {

    companion object {
        private const val TAG = "SegmentDownloadEngine"
        // Segment duration: 60 seconds (1 minute). Larger segments mean fewer files,
        // better timestamp alignment, and less chance of frame ordering issues.
        // The last segment may be shorter (handled by FFmpeg's -t flag).
        private const val SEGMENT_DURATION_SEC = 60
        private const val MIN_DISK_SPACE_MB = 200L
        private const val FINAL_VIDEO_NAME = "video.mkv"
        private const val CACHE_BASE_DIR = "anikuta_dl"
        private const val SEGMENTS_DIR = "segments"
        private const val SUBTITLES_DIR = "subtitles"
        private const val CONCAT_FILE = "concat.txt"
    }

    @Volatile
    private var paused = false

    @Volatile
    private var cancelled = false

    override suspend fun resolve(download: Download): Boolean {
        Log.d(TAG, "resolve: → ${download.episodeName}")
        download.status = Download.State.RESOLVING

        return try {
            val video = resolver.resolve(download)
            if (video == null) {
                Log.e(TAG, "resolve: ❌ could not resolve video for ${download.episodeName}")
                download.error = "Could not resolve video URL"
                return false
            }
            download.video = video

            // FIX (Issue H): Parse server/audio/quality info early so it's available
            // during download (not just after completion).
            val parsed = app.anikuta.ui.detail.VideoTitleParser.parse(video)
            download.serverName = parsed.server
            download.audioVersion = parsed.audio.name
            download.qualityLabel = "${parsed.quality ?: 0}p"
            Log.d(TAG, "resolve: ✓ ${video.videoTitle ?: "untitled"} (${download.serverName}/${download.audioVersion}/${download.qualityLabel})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "resolve: ❌ ${download.episodeName}: ${e.message}")
            download.error = "Resolve failed: ${e.message}"
            false
        }
    }

    override suspend fun download(download: Download): Boolean {
        Log.d(TAG, "download: → ${download.episodeName}")
        download.status = Download.State.DOWNLOADING
        progressTracker.resetSpeed()

        // FIX (F2): Disable FFmpegKit's log redirection to logcat.
        // FFmpegKit redirects FFmpeg's stdout/stderr to logcat under the
        // 'ffmpeg-kit' tag, producing ~15 lines per segment (~435 per episode).
        // disableRedirection() stops this redirect — only our Log.d messages remain.
        try {
            FFmpegKitConfig.disableRedirection()
        } catch (e: Exception) {
            Log.w(TAG, "download: could not disable FFmpegKit redirection: ${e.message}")
        }

        if (!hasMinDiskSpace()) {
            download.error = "Insufficient disk space (need ${MIN_DISK_SPACE_MB} MB)"
            Log.e(TAG, "download: ❌ ${download.error}")
            return false
        }

        val video = download.video ?: run {
            if (!resolve(download)) return false
            download.video ?: return false
        }

        // Get video duration via FFprobe
        val durationMs = getVideoDurationMs(video)
        if (durationMs <= 0) {
            download.error = "Could not determine video duration"
            Log.e(TAG, "download: ❌ ${download.error}")
            return false
        }

        // Size estimation — ALWAYS set this, even on resume, to fix E1/E3
        // (stale totalSize from previous download causing wrong display).
        // The manifest's totalSizeBytes may be -1 (unknown), so we re-estimate.
        val contentLength = getContentLength(video)
        val estimatedSize = if (contentLength > 0) contentLength else durationMs * 250
        progressTracker.setTotalSize(download, estimatedSize)
        Log.d(TAG, "download: duration=${durationMs / 1000 / 60}m${(durationMs / 1000) % 60}s, " +
            "size=${formatBytes(estimatedSize)}, segments=${(durationMs / 10000 + 1).toInt()}")

        // Set up cache directory
        val cacheDir = getCacheDir(download)
        val segmentsDir = File(cacheDir, SEGMENTS_DIR).apply { mkdirs() }
        val subtitlesDir = File(cacheDir, SUBTITLES_DIR).apply { mkdirs() }

        // Load or create manifest
        var manifest: DownloadManifest.Manifest = manifestManager.read(download) ?: run {
            val subtitleStates = video.subtitleTracks.mapIndexed { i, track ->
                DownloadManifest.SubtitleState(
                    url = track.url,
                    lang = track.lang,
                    downloaded = false,
                    fileName = "sub_${i}_${track.lang.ifBlank { "und" }}.vtt",
                )
            }
            val fresh = manifestManager.createFresh(
                download = download,
                videoUrl = video.videoUrl,
                totalDurationMs = durationMs,
                subtitles = subtitleStates,
                totalSizeBytes = if (contentLength > 0) contentLength else -1L,
            )
            manifestManager.write(download, fresh)
            Log.d(TAG, "download: ✓ fresh manifest created")

            // Write .episode_url file for stable episode identification (fixes H4).
            // This file survives metadata enrichment (which can change episode names)
            // and allows the detail page to match episodes by their stable URL.
            val episodeDir = provider.getEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            if (episodeDir != null) {
                provider.writeEpisodeUrlFile(episodeDir, download.episodeUrl)
            }

            fresh
        }

        // Verify cache files exist for "done" segments
        manifest = verifySegmentFiles(download, manifest, segmentsDir)

        val doneCount = manifestManager.getCompletedSegmentCount(manifest)
        Log.d(TAG, "download: ${manifest.totalSegments} segments, $doneCount done, " +
            "resuming from segment ${manifest.segments.indexOfFirst { it.status != DownloadManifest.SegmentStatus.DONE }.let { if (it < 0) -1 else it }}")

        progressTracker.updateFromManifest(download, manifest)

        // Download subtitles
        manifest = downloadSubtitles(download, video, manifest, subtitlesDir)

        // Download segments
        var segmentIndex = 0
        while (true) {
            // Check pause/cancel via download.status (fixes D2)
            if (download.status == Download.State.PAUSED) {
                Log.d(TAG, "download: ⏸ paused by user — saving manifest")
                manifestManager.write(download, manifest)
                return false
            }
            if (download.status == Download.State.NOT_DOWNLOADED || cancelled) {
                Log.d(TAG, "download: ✕ cancelled by user — cleaning up")
                cleanupCache(download)
                return false
            }

            val segment = manifestManager.getNextPendingSegment(manifest)
            if (segment == null) {
                Log.d(TAG, "download: ✓ all segments done")
                break
            }

            manifest = manifestManager.markSegmentDownloading(manifest, segment.index)
            manifestManager.write(download, manifest)

            val segmentFile = File(segmentsDir, segment.fileName)
            val segmentResult = downloadSegment(download, video, segment, manifest, segmentFile)

            // Check pause/cancel again after segment finishes
            if (download.status == Download.State.PAUSED) {
                Log.d(TAG, "download: ⏸ paused (after segment ${segment.index}) — saving manifest")
                manifestManager.write(download, manifest)
                return false
            }
            if (download.status == Download.State.NOT_DOWNLOADED || cancelled) {
                Log.d(TAG, "download: ✕ cancelled (after segment ${segment.index}) — cleaning up")
                cleanupCache(download)
                return false
            }

            if (segmentResult.success) {
                manifest = manifestManager.markSegmentDone(manifest, segment.index, segmentResult.sizeBytes)
                manifestManager.write(download, manifest)
                progressTracker.updateFromManifest(download, manifest)

                // FIX (Issue D + G): Continuously re-estimate total size after each segment.
                // Uses the average segment size so far × total segments.
                // FIX (Issue G): Detect small/empty segments (which indicate the video is
                // shorter than the FFprobe-estimated duration). If we see segments that are
                // significantly smaller than the average, reduce the effective total segment
                // count for the estimate.
                if (manifest.totalSegments > 0) {
                    val doneSegments = manifest.segments.filter { it.status == DownloadManifest.SegmentStatus.DONE }
                    if (doneSegments.isNotEmpty()) {
                        val totalDownloadedBytes = doneSegments.sumOf { it.sizeBytes }
                        val avgSegSize = totalDownloadedBytes / doneSegments.size

                        // Issue G: Count "real" segments (size > 50% of average).
                        // Small/empty segments indicate the video has ended but FFprobe
                        // reported a longer duration. Only count real segments for the estimate.
                        val realSegmentCount = doneSegments.count { it.sizeBytes > avgSegSize * 0.5 }
                        val effectiveTotalSegments = if (realSegmentCount < doneSegments.size && realSegmentCount > 0) {
                            // Extrapolate: if X% of segments so far are "real", assume X% of total are real
                            val realRatio = realSegmentCount.toDouble() / doneSegments.size
                            (manifest.totalSegments * realRatio).toInt().coerceAtLeast(1)
                        } else {
                            manifest.totalSegments
                        }

                        val reEstimatedTotal = avgSegSize * effectiveTotalSegments
                        if (reEstimatedTotal > 0 && Math.abs(reEstimatedTotal - download.totalSize) > download.totalSize * 0.05) {
                            progressTracker.setTotalSize(download, reEstimatedTotal)
                        }
                    }
                }

                segmentIndex++
                if (segmentIndex % 10 == 0 || segmentIndex == manifest.totalSegments) {
                    Log.d(TAG, "download: progress ${doneCount + segmentIndex}/${manifest.totalSegments} " +
                        "(${(doneCount + segmentIndex) * 100 / manifest.totalSegments}%)")
                }
            } else {
                manifest = manifestManager.markSegmentPartial(manifest, segment.index)
                manifestManager.write(download, manifest)
                Log.w(TAG, "download: ⚠ segment ${segment.index} failed — marked partial")
            }

            yield()
        }

        if (!manifestManager.allSegmentsDone(manifest)) {
            download.error = "Some segments failed to download"
            Log.e(TAG, "download: ❌ ${download.error}")
            return false
        }

        // MUXING phase
        download.status = Download.State.MUXING
        Log.d(TAG, "download: → muxing ${manifest.totalSegments} segments")

        val muxSuccess = muxSegments(download, video, manifest, cacheDir, segmentsDir, subtitlesDir)
        if (!muxSuccess) {
            download.error = "Muxing failed"
            Log.e(TAG, "download: ❌ ${download.error}")
            // FIX (Issue E): Clean up the cache dir after muxing failure.
            // Without this, the next retry finds all segments "done" in the manifest
            // but the cache files may be corrupt/missing → muxing fails again →
            // infinite loop. By deleting the cache, the next retry will re-download
            // all segments from scratch.
            Log.d(TAG, "download: cleaning cache after muxing failure — next retry will re-download")
            try {
                segmentsDir.deleteRecursively()
                segmentsDir.mkdirs()
            } catch (e: Exception) {
                Log.w(TAG, "download: could not clean segments dir: ${e.message}")
            }
            // Also reset all segments to PENDING in the manifest so verifySegmentFiles
            // on the next retry will re-download them
            val resetManifest = manifest.copy(
                segments = manifest.segments.map { seg ->
                    seg.copy(status = DownloadManifest.SegmentStatus.PENDING, sizeBytes = 0L)
                }
            )
            manifestManager.write(download, resetManifest)
            return false
        }

        // Copy final .mkv from cache to SAF
        val cacheMkv = File(cacheDir, FINAL_VIDEO_NAME)
        val copySuccess = copyMkvToSaf(cacheMkv, download)
        if (!copySuccess) {
            download.error = "Failed to copy video to storage"
            Log.e(TAG, "download: ❌ ${download.error}")
            return false
        }

        // FIX (Issue 7): Set the final actual size (not the estimate).
        // The actual file size is the most accurate — no more estimation.
        val actualFileSize = cacheMkv.length()
        if (actualFileSize > 0) {
            progressTracker.setTotalSize(download, actualFileSize)
            download.downloadedBytes = actualFileSize
        }

        // FIX (Issue B + F): FFprobe the final .mkv to get actual duration + resolution.
        val mediaInfo = getMediaInfo(cacheMkv)
        download.actualDurationMs = mediaInfo.durationMs
        download.actualResolution = mediaInfo.resolution
        Log.d(TAG, "download: final mkv actual duration=${mediaInfo.durationMs}ms, " +
            "resolution=${mediaInfo.resolution}, size=${formatBytes(actualFileSize)}")

        // FIX (Issue F): If the actual duration is significantly less than the estimated
        // duration (e.g., 225s actual vs 1440s estimated), re-mux with -t to trim
        // the padding. This fixes the wrong duration shown in the player.
        if (mediaInfo.durationMs > 0 && mediaInfo.durationMs < durationMs * 0.8) {
            Log.d(TAG, "download: trimming padding — actual=${mediaInfo.durationMs}ms < estimated=${durationMs}ms")
            val trimSuccess = trimMkv(cacheMkv, mediaInfo.durationMs / 1000.0)
            if (trimSuccess) {
                Log.d(TAG, "download: ✓ trimmed to ${mediaInfo.durationMs}ms, new size=${formatBytes(cacheMkv.length())}")
            } else {
                Log.w(TAG, "download: ⚠ trim failed — keeping original muxed file")
            }
        }

        // Note: server/audio/quality info is already parsed in resolve() (Issue H)

        // Clean up
        cleanupCache(download)
        manifestManager.delete(download)
        progressTracker.markComplete(download)
        download.status = Download.State.DOWNLOADED
        Log.d(TAG, "download: ✓ COMPLETE — ${download.episodeName} " +
            "(${formatBytes(actualFileSize)}, ${mediaInfo.resolution}, ${download.serverName}/${download.audioVersion}/${download.qualityLabel})")
        return true
    }

    override suspend fun pause(download: Download) {
        Log.d(TAG, "pause: → ${download.episodeName}")
        paused = true
        delay(500)
        download.status = Download.State.PAUSED
    }

    override suspend fun cancel(download: Download) {
        Log.d(TAG, "cancel: → ${download.episodeName}")
        cancelled = true
        delay(300)
        cleanupCache(download)
        download.status = Download.State.NOT_DOWNLOADED
    }

    override fun isCompleted(download: Download): Boolean {
        return provider.getDownloadedVideoFile(download.episodeName, download.animeTitle, download.sourceName) != null
    }

    // ---- Segment download (to cache dir) ----

    private data class SegmentResult(val success: Boolean, val sizeBytes: Long)

    private suspend fun downloadSegment(
        download: Download,
        video: Video,
        segment: DownloadManifest.SegmentState,
        manifest: DownloadManifest.Manifest,
        segmentFile: File,
    ): SegmentResult = withContext(Dispatchers.IO) {
        val startTimeSec = segment.index * SEGMENT_DURATION_SEC
        val headerOptions = buildHeaderOptions(video)
        val outputPath = segmentFile.absolutePath

        val cmd = StringBuilder().apply {
            if (video.videoUrl.startsWith("http")) {
                append("-headers '")
                append(headerOptions.replace("'", "\\'"))
                append("' ")
            }
            append("-ss ").append(startTimeSec).append(" ")
            append("-t ").append(SEGMENT_DURATION_SEC).append(" ")
            append("-i \"").append(video.videoUrl).append("\" ")
            append("-c copy ")
            append("-avoid_negative_ts make_zero ")
            append("-f mpegts ")
            append("\"").append(outputPath).append("\" -y")
        }.toString()

        val contentLength = try { getContentLength(video) } catch (_: Exception) { 0L }

        val statsCallback = StatisticsCallback { stats ->
            val estimatedSegSize = if (contentLength > 0) {
                contentLength / manifest.totalSegments
            } else 0
            if (estimatedSegSize > 0) {
                val segProgress = stats.time / (SEGMENT_DURATION_SEC * 1000.0)
                val segBytes = (estimatedSegSize * segProgress).toLong()
                val totalDownloaded = manifest.downloadedBytes + segBytes
                progressTracker.updateBytes(download, totalDownloaded)
            }
        }

        FFmpegKitConfig.enableStatisticsCallback(statsCallback)
        val session = FFmpegKit.execute(cmd)
        FFmpegKitConfig.enableStatisticsCallback(null)

        val returnCode = session.returnCode
        val segSize = segmentFile.length()

        if (ReturnCode.isSuccess(returnCode) && segSize > 0) {
            SegmentResult(true, segSize)
        } else {
            Log.w(TAG, "downloadSegment: ⚠ seg ${segment.index} rc=${returnCode.value}")
            segmentFile.delete()
            SegmentResult(false, 0)
        }
    }

    // ---- Subtitle download (to cache dir) ----

    private suspend fun downloadSubtitles(
        download: Download,
        video: Video,
        manifest: DownloadManifest.Manifest,
        subtitlesDir: File,
    ): DownloadManifest.Manifest = withContext(Dispatchers.IO) {
        if (video.subtitleTracks.isEmpty()) return@withContext manifest

        var updatedManifest = manifest
        video.subtitleTracks.forEachIndexed { i, track ->
            val subState = manifest.subtitles.getOrNull(i)
            if (subState?.downloaded == true) {
                val subFile = File(subtitlesDir, subState.fileName)
                if (subFile.exists() && subFile.length() > 0) return@forEachIndexed
            }

            val subFileName = "sub_${i}_${track.lang.ifBlank { "und" }}.vtt"
            val subFile = File(subtitlesDir, subFileName)
            val headerOptions = buildHeaderOptions(video)
            val cmd = StringBuilder().apply {
                append("-headers '")
                append(headerOptions.replace("'", "\\'"))
                append("' ")
                append("-i \"").append(track.url).append("\" ")
                append("-c copy ")
                append("\"").append(subFile.absolutePath).append("\" -y")
            }.toString()

            Log.d(TAG, "downloadSubtitles: ${track.lang}")
            val session = FFmpegKit.execute(cmd)
            if (ReturnCode.isSuccess(session.returnCode) && subFile.length() > 0) {
                val updatedSubs = updatedManifest.subtitles.toMutableList()
                if (i < updatedSubs.size) {
                    updatedSubs[i] = updatedSubs[i].copy(downloaded = true)
                }
                updatedManifest = updatedManifest.copy(subtitles = updatedSubs)
            } else {
                Log.w(TAG, "downloadSubtitles: ⚠ ${track.lang} failed")
            }
        }

        manifestManager.write(download, updatedManifest)
        updatedManifest
    }

    // ---- Muxing (all in cache dir) ----

    private suspend fun muxSegments(
        download: Download,
        video: Video,
        manifest: DownloadManifest.Manifest,
        cacheDir: File,
        segmentsDir: File,
        subtitlesDir: File,
    ): Boolean = withContext(Dispatchers.IO) {
        // Build concat list with REAL FILE PATHS
        val concatFile = File(cacheDir, CONCAT_FILE)
        val concatBuilder = StringBuilder()
        manifest.segments.forEach { seg ->
            val segFile = File(segmentsDir, seg.fileName)
            if (!segFile.exists() || segFile.length() == 0L) {
                Log.e(TAG, "muxSegments: ❌ segment file missing: ${seg.fileName}")
                return@withContext false
            }
            concatBuilder.append("file '").append(segFile.absolutePath).append("'\n")
        }
        concatFile.writeText(concatBuilder.toString())

        val outputFile = File(cacheDir, FINAL_VIDEO_NAME)

        val cmd = StringBuilder().apply {
            append("-f concat -safe 0 -i \"").append(concatFile.absolutePath).append("\"")

            video.subtitleTracks.forEachIndexed { i, _ ->
                val subState = manifest.subtitles.getOrNull(i)
                if (subState?.downloaded == true) {
                    val subFile = File(subtitlesDir, subState.fileName)
                    if (subFile.exists()) {
                        append(" -i \"").append(subFile.absolutePath).append("\"")
                    }
                }
            }

            append(" -map 0:v -map 0:a?")
            video.subtitleTracks.forEachIndexed { i, _ ->
                val subState = manifest.subtitles.getOrNull(i)
                if (subState?.downloaded == true) {
                    append(" -map ").append(i + 1).append(":s?")
                }
            }

            append(" -c copy -f matroska")

            video.subtitleTracks.forEachIndexed { i, track ->
                val subState = manifest.subtitles.getOrNull(i)
                if (subState?.downloaded == true) {
                    append(" -metadata:s:s:").append(i).append(" \"title=").append(track.lang).append("\"")
                }
            }

            append(" \"").append(outputFile.absolutePath).append("\" -y")
        }.toString()

        Log.d(TAG, "muxSegments: executing (cmd length=${cmd.length})")
        val session = FFmpegKit.execute(cmd)
        val success = ReturnCode.isSuccess(session.returnCode)

        if (success && outputFile.length() > 0) {
            Log.d(TAG, "muxSegments: ✓ ${formatBytes(outputFile.length())}")
        } else {
            Log.e(TAG, "muxSegments: ❌ rc=${session.returnCode.value}")
            outputFile.delete()
        }

        success
    }

    /**
     * Copy the muxed .mkv from cache dir to SAF.
     */
    private suspend fun copyMkvToSaf(cacheMkv: File, download: Download): Boolean = withContext(Dispatchers.IO) {
        if (!cacheMkv.exists() || cacheMkv.length() == 0L) {
            Log.e(TAG, "copyMkvToSaf: ❌ cache mkv empty")
            return@withContext false
        }

        val episodeDir = provider.getEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            ?: return@withContext false

        val safFile = episodeDir.createFile(FINAL_VIDEO_NAME) ?: return@withContext false

        try {
            cacheMkv.inputStream().use { input ->
                safFile.openOutputStream(false).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "copyMkvToSaf: ✓ ${formatBytes(cacheMkv.length())} → SAF")
            true
        } catch (e: Exception) {
            Log.e(TAG, "copyMkvToSaf: ❌ ${e.message}")
            false
        }
    }

    /**
     * Verify that "done" segments still have their cache files.
     */
    private suspend fun verifySegmentFiles(
        download: Download,
        manifest: DownloadManifest.Manifest,
        segmentsDir: File,
    ): DownloadManifest.Manifest {
        if (!segmentsDir.exists()) {
            // Cache dir doesn't exist — all "done" segments need re-downloading
            val resetSegments = manifest.segments.map { seg ->
                if (seg.status == DownloadManifest.SegmentStatus.DONE) {
                    seg.copy(status = DownloadManifest.SegmentStatus.PENDING)
                } else seg
            }
            val resetManifest = manifest.copy(segments = resetSegments)
            manifestManager.write(download, resetManifest)
            return resetManifest
        }

        var changed = false
        val segments = manifest.segments.map { seg ->
            when (seg.status) {
                DownloadManifest.SegmentStatus.DONE -> {
                    val file = File(segmentsDir, seg.fileName)
                    if (!file.exists() || file.length() == 0L) {
                        Log.w(TAG, "verifySegmentFiles: ⚠ seg ${seg.index} cache missing — resetting")
                        changed = true
                        seg.copy(status = DownloadManifest.SegmentStatus.PENDING, sizeBytes = 0L)
                    } else seg
                }
                DownloadManifest.SegmentStatus.DOWNLOADING -> {
                    // FIX (F4): A segment stuck in DOWNLOADING state means the app was
                    // killed mid-download. Reset to PENDING so it gets re-downloaded.
                    Log.w(TAG, "verifySegmentFiles: ⚠ seg ${seg.index} stuck in DOWNLOADING — resetting to PENDING")
                    changed = true
                    seg.copy(status = DownloadManifest.SegmentStatus.PENDING, sizeBytes = 0L)
                }
                else -> seg
            }
        }

        return if (changed) {
            val updated = manifest.copy(segments = segments)
            manifestManager.write(download, updated)
            updated
        } else manifest
    }

    // ---- FFprobe / metadata ----

    private suspend fun getVideoDurationMs(video: Video): Long = withContext(Dispatchers.IO) {
        val headerOptions = buildHeaderOptions(video)
        val cmd = if (video.videoUrl.startsWith("http")) {
            "-headers '$headerOptions' -i \"${video.videoUrl}\" -show_entries format=duration -v quiet -of csv=\"p=0\""
        } else {
            "-i \"${video.videoUrl}\" -show_entries format=duration -v quiet -of csv=\"p=0\""
        }

        val session = FFprobeKit.execute(cmd)
        val output = session.output?.trim()

        if (ReturnCode.isSuccess(session.returnCode) && !output.isNullOrEmpty()) {
            val durationSec = output.toDoubleOrNull() ?: 0.0
            (durationSec * 1000).toLong()
        } else {
            24 * 60 * 1000L // fallback
        }
    }

    private fun getContentLength(video: Video): Long = 0L

    /**
     * Media info extracted from a video file via FFprobe (Issue B).
     */
    private data class MediaInfo(val durationMs: Long, val resolution: String)

    /**
     * Trim the .mkv file to the actual content duration (remove padding).
     * Uses FFmpeg with -t to cut the file at the specified duration.
     * The trimmed file replaces the original.
     *
     * @param file the .mkv file to trim (modified in place)
     * @param actualDurationSec the actual content duration in seconds
     * @return true if trimming succeeded
     */
    private suspend fun trimMkv(file: File, actualDurationSec: Double): Boolean = withContext(Dispatchers.IO) {
        val trimmedFile = File(file.parentFile, "trimmed_${file.name}")
        val cmd = "-i \"${file.absolutePath}\" -t $actualDurationSec -c copy -avoid_negative_ts make_zero \"${trimmedFile.absolutePath}\" -y"

        Log.d(TAG, "trimMkv: cmd=$cmd")
        val session = FFmpegKit.execute(cmd)
        val success = ReturnCode.isSuccess(session.returnCode) && trimmedFile.exists() && trimmedFile.length() > 0

        if (success) {
            // Replace the original with the trimmed version
            file.delete()
            trimmedFile.renameTo(file)
        } else {
            Log.w(TAG, "trimMkv: ❌ rc=${session.returnCode.value}")
            trimmedFile.delete()
        }
        success
    }

    /**
     * Run FFprobe on a local .mkv file to get the actual duration and resolution.
     * This corrects the wrong duration from FFprobe on the HLS URL.
     *
     * FIX (Issue F): Re-enable FFmpegKit redirection before calling FFprobe.
     * The download() method calls disableRedirection() to suppress FFmpeg logs,
     * but this also suppresses FFprobe's output → output is empty → duration=0.
     * We re-enable it here, run FFprobe, then disable it again.
     */
    private suspend fun getMediaInfo(file: File): MediaInfo = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext MediaInfo(0L, "")

        // Re-enable redirection so FFprobe output is captured
        try {
            FFmpegKitConfig.enableRedirection()
        } catch (e: Exception) {
            Log.w(TAG, "getMediaInfo: could not enable redirection: ${e.message}")
        }

        // Get duration
        val durationCmd = "-i \"${file.absolutePath}\" -show_entries format=duration -v quiet -of csv=\"p=0\""
        val durationSession = FFprobeKit.execute(durationCmd)
        val durationStr = durationSession.output?.trim()
        Log.d(TAG, "getMediaInfo: FFprobe duration output='$durationStr' rc=${durationSession.returnCode.value}")
        val durationMs = if (ReturnCode.isSuccess(durationSession.returnCode) && !durationStr.isNullOrEmpty()) {
            val durationSec = durationStr.toDoubleOrNull() ?: 0.0
            (durationSec * 1000).toLong()
        } else 0L

        // Get resolution (video stream width × height)
        val resCmd = "-i \"${file.absolutePath}\" -show_entries stream=width,height -select_streams v:0 -v quiet -of csv=\"p=0\""
        val resSession = FFprobeKit.execute(resCmd)
        val resStr = resSession.output?.trim()
        Log.d(TAG, "getMediaInfo: FFprobe resolution output='$resStr' rc=${resSession.returnCode.value}")
        val resolution = if (ReturnCode.isSuccess(resSession.returnCode) && !resStr.isNullOrEmpty()) {
            resStr.replace(",", "x")
        } else ""

        // Disable redirection again to suppress FFmpeg logs during subsequent operations
        try {
            FFmpegKitConfig.disableRedirection()
        } catch (e: Exception) {
            // ignore
        }

        Log.d(TAG, "getMediaInfo: duration=${durationMs}ms, resolution=$resolution (from ${file.name})")
        MediaInfo(durationMs, resolution)
    }

    // ---- Helpers ----

    private fun buildHeaderOptions(video: Video): String {
        return video.headers?.let { headers ->
            headers.toMultimap().map { (key, values) ->
                "$key: ${values.firstOrNull() ?: ""}"
            }.joinToString("\r\n")
        } ?: "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
    }

    private fun getCacheDir(download: Download): File {
        return File(context.cacheDir, "$CACHE_BASE_DIR/${download.id}").apply { mkdirs() }
    }

    private fun hasMinDiskSpace(): Boolean {
        return try {
            val stat = android.os.StatFs(context.filesDir.path)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            freeBytes > MIN_DISK_SPACE_MB * 1024 * 1024
        } catch (e: Exception) {
            true
        }
    }

    private fun cleanupCache(download: Download) {
        try {
            val cacheDir = File(context.cacheDir, "$CACHE_BASE_DIR/${download.id}")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.w(TAG, "cleanupCache: ⚠ ${e.message}")
        }
    }

    fun resetFlags() {
        paused = false
        cancelled = false
    }
}
