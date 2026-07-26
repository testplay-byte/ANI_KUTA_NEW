package app.confused.anikuta.core.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import app.confused.anikuta.core.download.advanced.AdvancedHttpDownloader
import app.confused.anikuta.core.download.advanced.DownloadResumeManager

/**
 * The actual HTTP file downloader (the "DEFAULT" method — ADR-020).
 *
 * **Download pipeline (v2 — internal-cache-first + validation):**
 *
 * 1. **Download to internal cache** ([TempDownloadCache]) — fast, private, no
 *    SAF per-byte overhead. The user's folder is NOT touched yet.
 * 2. **Validate** the response via [VideoTypeDetector]:
 *    - Reject HLS (.m3u8) / DASH (.mpd) — the default downloader has no ffmpeg.
 *    - Reject HTML pages (resolver bugs that return a watch-page URL).
 *    - Reject tiny files (< [MIN_VALID_VIDEO_BYTES]) — a real video is at
 *      least hundreds of KB; a corrupt/playlist download is usually < 100KB.
 * 3. **Download subtitles** to the temp cache (best-effort, skipped on failure).
 * 4. **Write metadata.json** to the temp cache.
 * 5. **Publish to SAF** via [DownloadStorageProvider.publishToUserFolder] —
 *    copies the validated temp files to the user's folder. Only on success is
 *    the task marked COMPLETED.
 * 6. **Clean up** the temp dir regardless (success or failure).
 *
 * This pipeline ensures the user's folder NEVER contains partial/corrupt files.
 * A "completed" task always has a real, validated video on disk.
 *
 * **Cancellation / pause.** This is a `suspend` function; the [DownloadQueue]
 * runs it in a child Job. Pausing/cancelling = cancelling that Job →
 * `CancellationException` here (caught by the queue → PAUSED/CANCELLED). The
 * copy loop checks [ensureActive] cooperatively so a large file cancels promptly.
 *
 * **All I/O on Dispatchers.IO** (Rule §9).
 *
 * **Future 1DM method.** A future `OneDmDownloader` will implement multi-
 * threaded ranged downloads with resume + HLS/ffmpeg support. It will replace
 * this class for the ONEDM method. The [DownloadManager] interface stays the same.
 *
 * @param client Shared OkHttp client (connection-pooled, injected by DI).
 * @param storage The SAF storage provider (publishes to the user's folder).
 * @param tempCache The internal-cache temp-download manager.
 */
class HttpDownloader(
    private val client: OkHttpClient,
    private val storage: DownloadStorageProvider,
    private val tempCache: TempDownloadCache,
    private val preferences: DownloadPreferences,
    private val advancedDownloader: AdvancedHttpDownloader,
    private val hlsDownloader: HlsDownloader = HlsDownloader(client),
) {

    /**
     * Downloads the video + subtitles + metadata, validates, and publishes to SAF.
     *
     * @param task The task to download.
     * @param onProgress Called on every progress tick with (downloadedBytes, totalBytes).
     * @return The updated task with [DownloadTask.videoUri] + [DownloadTask.subtitleUris] set
     *   + status COMPLETED.
     * @throws DownloadException on any failure (network, validation, publish). The queue
     *   catches it → ERROR with [DownloadException.message] shown to the user.
     * @throws kotlinx.coroutines.CancellationException on pause/cancel.
     */
    suspend fun download(
        task: DownloadTask,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): DownloadTask = withContext(Dispatchers.IO) {
        val anime = task.request.anime
        val episode = task.request.episode
        val videoUrl = task.request.videoUrl

        if (videoUrl.isBlank()) {
            throw DownloadException("Video URL is blank — cannot download")
        }

        DownloadLogger.i("Starting download: ${anime.title} EP ${episode.episodeNumber}")
        DownloadLogger.i("  URL: $videoUrl")
        DownloadLogger.i("  Method: ${preferences.method().get()}")
        DownloadLogger.i("  Headers: ${task.request.videoHeaders?.take(100) ?: "none"}")
        DownloadLogger.i("  Server: ${task.request.videoServer}, Audio: ${task.request.videoAudio}, Quality: ${task.request.videoQuality}")

        try {
            // ── 1. Download + validate the video to internal cache ──
            val videoExt = inferVideoExtension(videoUrl)
            var tempVideo = tempCache.videoFile(task.id, videoExt)
            var downloadedBytes = downloadVideoToCache(
                url = videoUrl,
                headers = task.request.videoHeaders,
                tempFile = tempVideo,
                taskId = task.id,
                onProgress = onProgress,
            )

            // ── 1b. HLS content detection ──
            // Many extension video URLs are proxy URLs (e.g. localhost:PORT/m3u8?url=...)
            // that return HLS playlists but don't have ".m3u8" in the URL. The
            // VideoTypeDetector may have treated them as DIRECT_VIDEO (UNKNOWN →
            // DIRECT_VIDEO fallback). If the downloaded file is small AND starts
            // with "#EXTM3U", it's an HLS playlist — re-download via HlsDownloader.
            if (tempVideo.length() < 500 * 1024 && isHlsPlaylist(tempVideo)) {
                DownloadLogger.i("Downloaded file is an HLS playlist (${tempVideo.length()} bytes) — switching to HlsDownloader")
                tempVideo.delete()
                tempVideo = tempCache.videoFile(task.id, "ts")
                downloadedBytes = hlsDownloader.download(videoUrl, task.request.videoHeaders, tempVideo) { d, t ->
                    onProgress(d, t)
                }
            }

            // ── 2. Validate the downloaded file ──
            validateDownloadedFile(videoUrl, tempVideo, downloadedBytes)

            // ── 2b. Playability verification ──
            // Check the file's magic bytes to verify it's a real video (not an
            // HTML error page or a redirect). This prevents "downloaded but
            // won't play" situations. MPV can't play an HTML file masquerading
            // as a video.
            verifyVideoMagicBytes(tempVideo)

            // ── 3. Download subtitles to temp cache (best-effort) ──
            val tempSubsDir = tempCache.subtitlesDir(task.id)
            downloadSubtitlesToCache(task.request.subtitleTracks, tempSubsDir)

            // ── 4. Write metadata.json to temp cache ──
            val tempMeta = tempCache.metadataFile(task.id)
            writeMetadataToCache(tempMeta, task)

            // ── 5. Publish to the user's SAF folder ──
            val publishResult = storage.publishToUserFolder(
                anime = anime,
                episode = episode,
                tempVideoFile = tempVideo,
                tempSubtitlesDir = tempSubsDir,
                tempMetadataFile = tempMeta,
                videoExtension = videoExt,
            )
            when (publishResult) {
                is DownloadStorageProvider.PublishResult.Success -> {
                    DownloadLogger.i("Download complete: ${anime.title} EP ${episode.episodeNumber} " +
                        "(${publishResult.sizeBytes} bytes, ${publishResult.subtitleUris.size} subs)")
                    task.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 100,
                        videoUri = publishResult.videoUri,
                        subtitleUris = publishResult.subtitleUris,
                        downloadedBytes = publishResult.sizeBytes,
                        totalBytes = publishResult.sizeBytes,
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                is DownloadStorageProvider.PublishResult.Error -> {
                    throw DownloadException(publishResult.message)
                }
            }
        } finally {
            // Always clean up the temp dir (success or failure). The user's
            // folder is the source of truth; the temp cache is disposable.
            tempCache.cleanupTask(task.id)
        }
    }

    /**
     * Downloads the video to the internal cache file, reporting progress.
     * Routes to [HlsDownloader] for HLS streams; direct-stream for video files.
     * Returns the total bytes downloaded.
     */
    private suspend fun downloadVideoToCache(
        url: String,
        headers: String?,
        tempFile: File,
        taskId: Long,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        // Pre-flight: if the URL clearly indicates HLS, route directly to HlsDownloader.
        val preType = VideoTypeDetector.detectFromUrl(url)
        if (preType == VideoTypeDetector.VideoType.HLS_STREAM) {
            DownloadLogger.i("HLS detected (URL) — delegating to HlsDownloader")
            return hlsDownloader.download(url, headers, tempFile) { downloaded, total ->
                onProgress(downloaded, total)
            }
        }

        // ── Advanced method: route to AdvancedHttpDownloader ──
        // The advanced downloader does its own HEAD probe + multi-threaded
        // Range requests. If the server doesn't support Range, it falls back
        // to single-threaded internally. HLS is handled above (URL-based check).
        // For Content-Type-based HLS detection, the advanced downloader will
        // fail on the HEAD probe if it's HLS (no Content-Length for playlists),
        // and we catch that + retry with HlsDownloader.
        if (preferences.method().get() == DownloadMethod.ADVANCED) {
            DownloadLogger.i("Advanced method — delegating to AdvancedHttpDownloader")
            return try {
                advancedDownloader.download(taskId, url, headers, tempFile) { downloaded, total ->
                    onProgress(downloaded, total)
                }
            } catch (e: DownloadException) {
                // If the advanced downloader failed (e.g. server returned HLS,
                // no Content-Length, etc.), fall back to the normal path.
                DownloadLogger.w("Advanced download failed, falling back to normal: ${e.message}")
                downloadNormal(url, headers, tempFile, taskId, onProgress)
            }
        }

        // ── Normal method ──
        return downloadNormal(url, headers, tempFile, taskId, onProgress)
    }

    /**
     * The Normal method: single-threaded streaming with Content-Type detection.
     * Also used as a fallback when the Advanced method fails.
     */
    private suspend fun downloadNormal(
        url: String,
        headers: String?,
        tempFile: File,
        taskId: Long,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        val request = Request.Builder().url(url).apply {
            if (!headers.isNullOrBlank()) {
                headers.split('\n').forEach { line ->
                    val sep = line.indexOf(':')
                    if (sep > 0) {
                        addHeader(line.substring(0, sep).trim(), line.substring(sep + 1).trim())
                    }
                }
            }
        }.build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DownloadException("HTTP ${response.code} for video URL")
                }

                val videoType = VideoTypeDetector.detect(url, response)
                if (!VideoTypeDetector.isDownloadable(videoType)) {
                    val reason = VideoTypeDetector.unsupportedReason(videoType)
                        ?: "Unsupported video type: $videoType"
                    DownloadLogger.e("Rejected video (type=$videoType): $reason")
                    throw DownloadException(reason)
                }

                // ── HLS detected via Content-Type ──
                if (videoType == VideoTypeDetector.VideoType.HLS_STREAM) {
                    DownloadLogger.i("HLS detected (Content-Type) — delegating to HlsDownloader")
                    return@use hlsDownloader.download(url, headers, tempFile) { downloaded, total ->
                        onProgress(downloaded, total)
                    }
                }

                // ── Direct video stream ──
                val total = response.body?.contentLength() ?: -1L
                DownloadLogger.i("Downloading (type=$videoType, contentLength=$total) → ${tempFile.name}")

                FileOutputStream(tempFile).use { os ->
                    response.body?.byteStream()?.use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            os.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                        os.flush()
                    }
                }
                tempFile.length()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            DownloadLogger.d("Video download cancelled/paused (task $taskId)")
            throw e
        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            throw DownloadException("Video download failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /**
     * Checks if the file is an HLS playlist by reading the first few bytes.
     * HLS playlists start with `#EXTM3U` (case-insensitive).
     */
    private fun isHlsPlaylist(file: File): Boolean {
        return try {
            val first20 = ByteArray(20)
            java.io.FileInputStream(file).use { it.read(first20) }
            val text = String(first20).trimStart()
            text.startsWith("#EXTM3U", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates the downloaded temp file before publishing. Rejects:
     *  - Empty/missing files
     *  - Files smaller than [MIN_VALID_VIDEO_BYTES] (corrupt/playlist/error page)
     *
     * **Note:** The video-type check (HLS/DASH/HTML rejection) already happened
     * at the HTTP-response level in [downloadVideoToCache]. We do NOT re-check
     * the URL-based type here because many video URLs have no clean extension
     * (e.g. `https://cdn.example.com/video/abc123?token=xyz`) — the URL-based
     * check would return UNKNOWN and reject valid downloads. The file-size
     * check is sufficient as a second line of defense.
     */
    private fun validateDownloadedFile(url: String, tempFile: File, downloadedBytes: Long) {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            throw DownloadException("Downloaded file is empty — the source returned no data.")
        }
        if (tempFile.length() < MIN_VALID_VIDEO_BYTES) {
            // Log the first 200 bytes as text for debugging (shows error pages, redirects, etc.)
            val preview = try {
                val bytes = ByteArray(minOf(200, tempFile.length().toInt()))
                java.io.FileInputStream(tempFile).use { it.read(bytes) }
                "\n--- File content preview ---\n${String(bytes).take(200)}\n--- End preview ---"
            } catch (e: Exception) { "" }
            DownloadLogger.e("Downloaded file too small: ${tempFile.length()} bytes (min=$MIN_VALID_VIDEO_BYTES) URL: $url$preview")
            throw DownloadException(
                "Downloaded file is only ${tempFile.length()} bytes — the server returned an error page or redirect instead of the video. " +
                "Try a different server or quality. (URL: ${url.take(80)}...)"
            )
        }
    }

    /**
     * Verifies the downloaded file's magic bytes to ensure it's a real video
     * (not an HTML error page or a redirect that passed the size check).
     *
     * Checks the first few bytes for known video container signatures:
     *  - MP4/M4V/MOV: `ftyp` at offset 4
     *  - MKV/WebM: `1A 45 DF A3` (EBML)
     *  - MPEG-TS: `47` (sync byte) at offset 0, 188, 376, ...
     *  - FLV: `46 4C 56` ("FLV")
     *  - AVI: `52 49 46 46` ("RIFF")
     *
     * If the file starts with `3C 21` (HTML `<!`) or `3C 68` (HTML `<h`), it's
     * an HTML page → rejected.
     */
    private fun verifyVideoMagicBytes(tempFile: File) {
        try {
            val header = ByteArray(16)
            java.io.FileInputStream(tempFile).use { it.read(header) }
            val hex = header.joinToString(" ") { "%02X".format(it) }

            // Check for HTML (error page masquerading as video)
            if (header[0] == 0x3C.toByte() && (header[1] == 0x21.toByte() || header[1] == 0x68.toByte())) {
                DownloadLogger.e("Downloaded file is HTML, not a video (magic: $hex)")
                throw DownloadException(
                    "The downloaded file is an HTML page, not a video. " +
                    "The server may have returned an error page or a captcha. " +
                    "Try a different server or quality."
                )
            }

            // Check for known video magic bytes
            val isMp4 = header.size > 7 &&
                header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
                header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()
            val isMkv = header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()
            val isTs = header[0] == 0x47.toByte() // MPEG-TS sync byte
            val isFlv = header[0] == 'F'.code.toByte() && header[1] == 'L'.code.toByte() &&
                header[2] == 'V'.code.toByte()
            val isAvi = header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()

            if (!isMp4 && !isMkv && !isTs && !isFlv && !isAvi) {
                DownloadLogger.w("Downloaded file has unknown magic bytes: $hex — proceeding (may still be playable)")
                // Don't reject — some video formats have non-standard headers.
                // The size check + HTML check are the primary guards.
            } else {
                DownloadLogger.d("Downloaded file verified as valid video (magic: $hex)")
            }
        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            DownloadLogger.w("Magic byte check failed (non-fatal): ${e.message}")
            // Non-fatal — don't block the download if we can't read the header.
        }
    }

    /** Downloads every subtitle track to the temp cache dir. Best-effort (failures skipped). */
    private suspend fun downloadSubtitlesToCache(
        tracks: List<DownloadTrack>,
        tempSubsDir: File,
    ) {
        tracks.forEachIndexed { index, track ->
            coroutineContext.ensureActive()
            try {
                val ext = subtitleExtension(track.url)
                val safeLang = track.lang.ifBlank { "track" }
                    .replace(Regex("[^A-Za-z0-9 ]"), " ").trim().ifBlank { "track" }
                val tempSub = File(tempSubsDir, "${safeLang}_$index.$ext")
                FileOutputStream(tempSub).use { os ->
                    client.newCall(Request.Builder().url(track.url).build()).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            DownloadLogger.w("Subtitle $index (${track.lang}) HTTP ${resp.code} — skipped")
                            return@use
                        }
                        resp.body?.byteStream()?.use { it.copyTo(os) }
                    }
                }
                DownloadLogger.d("Subtitle $index (${track.lang}) downloaded (${tempSub.length()} bytes)")
            } catch (e: Exception) {
                DownloadLogger.w("Subtitle $index (${track.lang}) failed — skipped", e)
            }
        }
    }

    private fun writeMetadataToCache(metaFile: File, task: DownloadTask) {
        try {
            val json = kotlinx.serialization.json.Json {
                encodeDefaults = true
                prettyPrint = true
            }
            val cache = EpisodeMetadataCache(
                anilistId = task.request.anime.anilistId,
                animeTitle = task.request.anime.title,
                episodeNumber = task.request.episode.episodeNumber,
                episodeName = task.request.episode.name,
                videoUrl = task.request.videoUrl,
                downloadedAt = System.currentTimeMillis(),
                sourceId = task.request.sourceId,
            )
            metaFile.writeText(json.encodeToString(EpisodeMetadataCache.serializer(), cache))
        } catch (e: Exception) {
            DownloadLogger.w("Failed to write metadata.json (non-fatal)", e)
        }
    }

    private fun inferVideoExtension(url: String): String {
        val noQuery = url.substringBefore('?')
        val path = noQuery.substringAfterLast('/')
        val dot = path.lastIndexOf('.')
        if (dot < 0 || dot == path.length - 1) return "mp4"
        val ext = path.substring(dot + 1).lowercase()
        // HLS streams are concatenated into a .ts file (MPV plays .ts natively).
        if (ext == "m3u8" || ext == "m3u") return "ts"
        return when (ext) {
            "mp4", "mkv", "webm", "avi", "mov", "m4v", "ts" -> ext
            else -> "mp4"
        }
    }

    private fun subtitleExtension(url: String): String {
        val noQuery = url.substringBefore('?')
        val dot = noQuery.lastIndexOf('.')
        if (dot < 0) return "srt"
        return when (noQuery.substring(dot + 1).lowercase()) {
            "ass", "srt", "vtt", "ssa", "sub" -> noQuery.substring(dot + 1).lowercase()
            else -> "srt"
        }
    }

    companion object {
        private const val BUFFER_SIZE = 8 * 1024 // 8 KB
        // A real video episode is at least hundreds of KB. Anything smaller is
        // almost certainly an error page, a playlist, or a corrupt download.
        // 500 KB is a conservative minimum (a 5-minute low-res episode is ~10+ MB).
        private const val MIN_VALID_VIDEO_BYTES = 500L * 1024L
    }
}

/** Thrown when a download fails (network/IO/validation/HTTP error). Carries a user-facing message. */
class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
