package app.anikuta.download.engine

import android.content.Context
import android.util.Log
import app.anikuta.download.Download
import app.anikuta.download.DownloadPreferences
import app.anikuta.download.DownloadProvider
import app.anikuta.download.DownloadVideoResolver
import app.anikuta.download.engine.hls.HlsPlaylist
import app.anikuta.download.engine.hls.HlsPlaylistFetcher
import app.anikuta.download.engine.hls.HlsSegmentDownloader
import app.anikuta.download.progress.ProgressTracker
import app.anikuta.download.progress.formatBytes
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okhttp3.Headers
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * HLS direct-download engine — downloads .ts segments via HTTP instead of FFmpeg -ss.
 *
 * How it works:
 * 1. Fetch the m3u8 via OkHttp → parse → get real .ts segment URLs
 * 2. HTTP-GET each .ts segment directly (no FFmpeg seeking)
 * 3. Track each segment in the manifest (resume works per-segment)
 * 4. Mux with FFmpeg concat demuxer (no -ss, no -t — real timestamps)
 *
 * This fixes:
 * - Wrong file size (no duplicate content from phantom seeks)
 * - Wrong duration (original timestamps from .ts files)
 * - Resume after failure (skip completed segments)
 *
 * Fallback: if the URL isn't an m3u8, or uses EXT-X-MAP (fMP4) or SAMPLE-AES,
 * delegates to [fallbackEngine] (SegmentDownloadEngine with FFmpeg -ss).
 *
 * @param fallbackEngine the legacy FFmpeg-based engine, used when HLS direct download isn't possible
 */
class HlsDownloadEngine(
    private val context: Context,
    private val provider: DownloadProvider,
    private val resolver: DownloadVideoResolver,
    private val manifestManager: DownloadManifest,
    private val progressTracker: ProgressTracker,
    private val fetcher: HlsPlaylistFetcher,
    private val segmentDownloader: HlsSegmentDownloader,
    private val networkHelper: NetworkHelper,
    private val fallbackEngine: SegmentDownloadEngine,
    private val downloadPrefs: DownloadPreferences,
) : DownloadEngine {

    companion object {
        private const val TAG = "HlsDownloadEngine"
        private const val MIN_DISK_SPACE_MB = 200L
        private const val FINAL_VIDEO_NAME = "video.mkv"
        private const val CACHE_BASE_DIR = "anikuta_dl"
        private const val SEGMENTS_DIR = "segments"
        private const val SUBTITLES_DIR = "subtitles"
        private const val CONCAT_FILE = "concat.txt"
    }

    /** Per-download cancellation state (fixes concurrency bug with @Volatile flags). */
    private data class CancelState(@Volatile var paused: Boolean, @Volatile var cancelled: Boolean)
    private val cancelStates = ConcurrentHashMap<String, CancelState>()

    /** Result of the HLS download attempt. */
    private enum class HlsResult { SUCCESS, FAILURE, UNSUPPORTED }

    override suspend fun resolve(download: Download): Boolean {
        Log.d(TAG, "resolve: → ${download.episodeName}")
        download.status = Download.State.RESOLVING
        return try {
            val video = resolver.resolve(download)
            if (video == null) {
                Log.e(TAG, "resolve: ❌ could not resolve video")
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
            Log.e(TAG, "resolve: ❌ ${e.message}")
            download.error = "Resolve failed: ${e.message}"
            false
        }
    }

    override suspend fun download(download: Download): Boolean {
        Log.d(TAG, "download: → ${download.episodeName}")
        download.status = Download.State.DOWNLOADING
        progressTracker.resetSpeed()

        // Suppress FFmpeg logs (for the muxing phase later)
        try { FFmpegKitConfig.disableRedirection() } catch (_: Exception) {}

        if (!hasMinDiskSpace()) {
            download.error = "Insufficient disk space (need ${MIN_DISK_SPACE_MB} MB)"
            Log.e(TAG, "download: ❌ ${download.error}")
            return false
        }

        val video = download.video ?: run {
            if (!resolve(download)) return false
            download.video ?: return false
        }

        // Try HLS direct download first
        val result = tryHlsDownload(download, video)

        // Fall back to FFmpeg engine if HLS isn't supported
        if (result == HlsResult.UNSUPPORTED) {
            Log.w(TAG, "download: HLS not supported → falling back to FFmpeg engine")
            return fallbackEngine.download(download)
        }

        return result == HlsResult.SUCCESS
    }

    /**
     * Attempt the HLS direct download. Returns UNSUPPORTED if the URL isn't an m3u8
     * or uses features we don't handle (EXT-X-MAP, SAMPLE-AES).
     */
    private suspend fun tryHlsDownload(download: Download, video: Video): HlsResult {
        val url = video.videoUrl
        if (!url.startsWith("http")) return HlsResult.UNSUPPORTED

        val headers = video.headers ?: Headers.headersOf(
            "User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
        )

        // 1. Fetch + parse m3u8 (fetcher extracts real URL from proxy if needed)
        val qualityPref = downloadPrefs.preferredQualityOrder().get()
        val media = try {
            fetcher.fetchMediaPlaylist(url, headers, qualityPref)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "tryHlsDownload: ${e.message} → falling back")
            return HlsResult.UNSUPPORTED
        }
        if (media == null) {
            Log.w(TAG, "tryHlsDownload: not an m3u8 → falling back")
            return HlsResult.UNSUPPORTED
        }

        // The fetcher tries the proxy first (which rewrites segment URLs to go through
        // the proxy for CDN authentication). If the proxy doesn't return m3u8, it falls
        // back to fetching directly from the CDN (segments are CDN URLs — video.headers
        // are used for authentication).
        // Either way, the segment URLs in the playlist are used AS-IS — no rewriting needed.
        val totalDurationMs = (media.segments.sumOf { it.durationSec } * 1000).toLong()
        Log.d(TAG, "tryHlsDownload: ${media.segments.size} segments, " +
            "duration=${totalDurationMs / 1000 / 60}m${(totalDurationMs / 1000) % 60}s, " +
            "vod=${media.isVod}, firstSegUrl=${media.segments.firstOrNull()?.url?.take(60)}")

        if (media.segments.isEmpty()) {
            download.error = "No segments in playlist"
            return HlsResult.FAILURE
        }

        // 2. Set up cache
        val cacheDir = getCacheDir(download)
        val segmentsDir = File(cacheDir, SEGMENTS_DIR).apply { mkdirs() }
        val subtitlesDir = File(cacheDir, SUBTITLES_DIR).apply { mkdirs() }

        // 3. Create or load manifest with REAL segment URLs
        var manifest = manifestManager.read(download) ?: run {
            val subtitleStates = video.subtitleTracks.mapIndexed { i, track ->
                DownloadManifest.SubtitleState(
                    url = track.url,
                    lang = track.lang,
                    downloaded = false,
                    fileName = "sub_${i}_${track.lang.ifBlank { "und" }}.vtt",
                )
            }
            val fresh = manifestManager.createFreshHls(
                download = download,
                videoUrl = url,
                totalDurationMs = totalDurationMs,
                segmentUrls = media.segments.map { it.url },
                segmentDurationsMs = media.segments.map { (it.durationSec * 1000).toLong() },
                subtitles = subtitleStates,
            )
            manifestManager.write(download, fresh)

            // Write .episode_url file for stable episode identification
            provider.getEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
                ?.let { provider.writeEpisodeUrlFile(it, download.episodeUrl) }
            fresh
        }

        // On resume: refresh segment URLs from the fresh m3u8 (localhost proxy port changes)
        if (manifest.playlistType == "hls" && manifest.segments.size == media.segments.size) {
            manifest = manifest.copy(
                segments = manifest.segments.mapIndexed { i, seg ->
                    seg.copy(url = media.segments[i].url)
                }
            )
            manifestManager.write(download, manifest)
        }

        // Verify cache files for DONE segments
        manifest = verifySegmentFiles(download, manifest, segmentsDir)

        // Size estimation
        val doneSegs = manifest.segments.filter { it.status == DownloadManifest.SegmentStatus.DONE }
        if (doneSegs.isNotEmpty()) {
            val avg = doneSegs.sumOf { it.sizeBytes } / doneSegs.size
            progressTracker.setTotalSize(download, avg * manifest.totalSegments)
        } else {
            progressTracker.setTotalSize(download, totalDurationMs * 250 / 1000)
        }

        progressTracker.updateFromManifest(download, manifest)
        Log.d(TAG, "tryHlsDownload: ${manifestManager.getCompletedSegmentCount(manifest)}/${manifest.totalSegments} done, " +
            "resuming from segment ${manifest.segments.indexOfFirst { it.status != DownloadManifest.SegmentStatus.DONE }.let { if (it < 0) -1 else it }}")

        // 4. Download subtitles
        manifest = downloadSubtitles(download, video, manifest, subtitlesDir)

        // 5. Download each missing segment
        var segIndex = 0
        val doneCount = manifestManager.getCompletedSegmentCount(manifest)
        while (true) {
            val cs = cancelStates[download.id]
            if (cs?.paused == true || download.status == Download.State.PAUSED) {
                Log.d(TAG, "download: ⏸ paused — saving manifest")
                manifestManager.write(download, manifest)
                download.status = Download.State.PAUSED
                return HlsResult.FAILURE
            }
            if (cs?.cancelled == true || download.status == Download.State.NOT_DOWNLOADED) {
                Log.d(TAG, "download: ✕ cancelled — cleaning up")
                cleanupCache(download)
                return HlsResult.FAILURE
            }

            val segState = manifestManager.getNextPendingSegment(manifest) ?: break
            val hlsSeg = media.segments.getOrNull(segState.index) ?: break

            manifest = manifestManager.markSegmentDownloading(manifest, segState.index)
            manifestManager.write(download, manifest)

            val segFile = File(segmentsDir, segState.fileName)
            val result = segmentDownloader.download(hlsSeg, headers, segFile)

            // Check pause/cancel after segment
            if (cs?.paused == true || download.status == Download.State.PAUSED) {
                manifestManager.write(download, manifest)
                download.status = Download.State.PAUSED
                return HlsResult.FAILURE
            }

            if (result.success) {
                manifest = manifestManager.markSegmentDone(manifest, segState.index, result.sizeBytes)
                manifestManager.write(download, manifest)
                progressTracker.updateFromManifest(download, manifest)
                reEstimateSize(download, manifest)
                segIndex++
                if (segIndex % 10 == 0 || doneCount + segIndex == manifest.totalSegments) {
                    Log.d(TAG, "download: progress ${doneCount + segIndex}/${manifest.totalSegments} " +
                        "(${(doneCount + segIndex) * 100 / manifest.totalSegments}%)")
                }
            } else {
                manifest = manifestManager.markSegmentPartial(manifest, segState.index)
                manifestManager.write(download, manifest)
                Log.w(TAG, "download: ⚠ segment ${segState.index} failed: ${result.error}")
            }
            yield()
        }

        if (!manifestManager.allSegmentsDone(manifest)) {
            download.error = "Some segments failed to download"
            Log.e(TAG, "download: ❌ ${download.error}")
            return HlsResult.FAILURE
        }

        // 6. Mux via FFmpeg concat demuxer
        download.status = Download.State.MUXING
        Log.d(TAG, "download: → muxing ${manifest.totalSegments} segments")
        if (!muxSegments(download, video, manifest, cacheDir, segmentsDir, subtitlesDir)) {
            download.error = "Muxing failed"
            Log.e(TAG, "download: ❌ ${download.error}")
            // Reset segments for retry
            segmentsDir.deleteRecursively(); segmentsDir.mkdirs()
            val reset = manifest.copy(segments = manifest.segments.map {
                it.copy(status = DownloadManifest.SegmentStatus.PENDING, sizeBytes = 0L)
            })
            manifestManager.write(download, reset)
            return HlsResult.FAILURE
        }

        // 7. Copy to SAF + FFprobe + trim
        val cacheMkv = File(cacheDir, FINAL_VIDEO_NAME)
        if (!copyMkvToSaf(cacheMkv, download)) {
            download.error = "Failed to copy video to storage"
            return HlsResult.FAILURE
        }
        val actualSize = cacheMkv.length()
        if (actualSize > 0) {
            progressTracker.setTotalSize(download, actualSize)
            download.downloadedBytes = actualSize
        }

        val mediaInfo = getMediaInfo(cacheMkv)
        download.actualDurationMs = mediaInfo.durationMs
        download.actualResolution = mediaInfo.resolution
        Log.d(TAG, "download: final mkv duration=${mediaInfo.durationMs}ms, " +
            "resolution=${mediaInfo.resolution}, size=${formatBytes(actualSize)}")

        // Trim if actual duration is significantly less than estimated
        if (mediaInfo.durationMs > 0 && mediaInfo.durationMs < totalDurationMs * 0.8) {
            Log.d(TAG, "download: trimming — actual=${mediaInfo.durationMs}ms < estimated=${totalDurationMs}ms")
            trimMkv(cacheMkv, mediaInfo.durationMs / 1000.0)
        }

        // 8. Cleanup
        cleanupCache(download)
        manifestManager.delete(download)
        progressTracker.markComplete(download)
        download.status = Download.State.DOWNLOADED
        Log.d(TAG, "download: ✓ COMPLETE — ${download.episodeName} " +
            "(${formatBytes(actualSize)}, ${mediaInfo.resolution}, ${download.serverName}/${download.audioVersion}/${download.qualityLabel})")
        return HlsResult.SUCCESS
    }

    // ---- Per-download cancellation ----

    fun resetFlags(downloadId: String) {
        cancelStates[downloadId] = CancelState(false, false)
    }

    override suspend fun pause(download: Download) {
        cancelStates[download.id]?.paused = true
        delay(500)
        download.status = Download.State.PAUSED
    }

    override suspend fun cancel(download: Download) {
        cancelStates[download.id]?.cancelled = true
        delay(300)
        cleanupCache(download)
        download.status = Download.State.NOT_DOWNLOADED
    }

    override fun isCompleted(download: Download): Boolean {
        return provider.getDownloadedVideoFile(download.episodeName, download.animeTitle, download.sourceName) != null
    }

    // ---- Shared helpers (same logic as SegmentDownloadEngine) ----

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
                if (i < updatedSubs.size) updatedSubs[i] = updatedSubs[i].copy(downloaded = true)
                updatedManifest = updatedManifest.copy(subtitles = updatedSubs)
            }
        }
        manifestManager.write(download, updatedManifest)
        updatedManifest
    }

    private suspend fun muxSegments(
        download: Download,
        video: Video,
        manifest: DownloadManifest.Manifest,
        cacheDir: File,
        segmentsDir: File,
        subtitlesDir: File,
    ): Boolean = withContext(Dispatchers.IO) {
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
                    if (subFile.exists()) append(" -i \"").append(subFile.absolutePath).append("\"")
                }
            }
            append(" -map 0:v -map 0:a?")
            video.subtitleTracks.forEachIndexed { i, _ ->
                val subState = manifest.subtitles.getOrNull(i)
                if (subState?.downloaded == true) append(" -map ").append(i + 1).append(":s?")
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

    private suspend fun copyMkvToSaf(cacheMkv: File, download: Download): Boolean = withContext(Dispatchers.IO) {
        if (!cacheMkv.exists() || cacheMkv.length() == 0L) return@withContext false
        val episodeDir = provider.getEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            ?: return@withContext false
        val safFile = episodeDir.createFile(FINAL_VIDEO_NAME) ?: return@withContext false
        try {
            cacheMkv.inputStream().use { input ->
                safFile.openOutputStream(false).use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "copyMkvToSaf: ❌ ${e.message}")
            false
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

    private suspend fun trimMkv(file: File, actualDurationSec: Double): Boolean = withContext(Dispatchers.IO) {
        val trimmedFile = File(file.parentFile, "trimmed_${file.name}")
        val cmd = "-i \"${file.absolutePath}\" -t $actualDurationSec -c copy -avoid_negative_ts make_zero \"${trimmedFile.absolutePath}\" -y"
        val session = FFmpegKit.execute(cmd)
        val success = ReturnCode.isSuccess(session.returnCode) && trimmedFile.exists() && trimmedFile.length() > 0
        if (success) {
            file.delete()
            trimmedFile.renameTo(file)
        } else {
            trimmedFile.delete()
        }
        success
    }

    private suspend fun verifySegmentFiles(
        download: Download,
        manifest: DownloadManifest.Manifest,
        segmentsDir: File,
    ): DownloadManifest.Manifest {
        if (!segmentsDir.exists()) {
            val reset = manifest.copy(segments = manifest.segments.map {
                if (it.status == DownloadManifest.SegmentStatus.DONE)
                    it.copy(status = DownloadManifest.SegmentStatus.PENDING, sizeBytes = 0L)
                else it
            })
            manifestManager.write(download, reset)
            return reset
        }
        var changed = false
        val segments = manifest.segments.map { seg ->
            if (seg.status == DownloadManifest.SegmentStatus.DONE) {
                val file = File(segmentsDir, seg.fileName)
                if (!file.exists() || file.length() == 0L) {
                    changed = true
                    seg.copy(status = DownloadManifest.SegmentStatus.PENDING, sizeBytes = 0L)
                } else seg
            } else if (seg.status == DownloadManifest.SegmentStatus.DOWNLOADING) {
                changed = true
                seg.copy(status = DownloadManifest.SegmentStatus.PENDING, sizeBytes = 0L)
            } else seg
        }
        return if (changed) {
            val updated = manifest.copy(segments = segments)
            manifestManager.write(download, updated)
            updated
        } else manifest
    }

    private fun reEstimateSize(download: Download, manifest: DownloadManifest.Manifest) {
        val doneSegs = manifest.segments.filter { it.status == DownloadManifest.SegmentStatus.DONE }
        if (doneSegs.isNotEmpty() && manifest.totalSegments > 0) {
            val avg = doneSegs.sumOf { it.sizeBytes } / doneSegs.size
            val est = avg * manifest.totalSegments
            if (est > 0 && Math.abs(est - download.totalSize) > download.totalSize * 0.05) {
                progressTracker.setTotalSize(download, est)
            }
        }
    }

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
        } catch (e: Exception) { true }
    }

    private fun cleanupCache(download: Download) {
        try {
            val cacheDir = File(context.cacheDir, "$CACHE_BASE_DIR/${download.id}")
            if (cacheDir.exists()) cacheDir.deleteRecursively()
        } catch (_: Exception) {}
    }
}
