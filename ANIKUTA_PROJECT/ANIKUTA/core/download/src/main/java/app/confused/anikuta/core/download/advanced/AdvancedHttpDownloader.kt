package app.confused.anikuta.core.download.advanced

import app.confused.anikuta.core.download.DownloadException
import app.confused.anikuta.core.download.DownloadLogger
import app.confused.anikuta.core.download.DownloadPreferences
import app.confused.anikuta.core.download.TempDownloadCache
import app.confused.anikuta.core.download.VideoTypeDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext

/**
 * The Advanced download method — multi-threaded Range-request download with
 * resume + auto-retry.
 *
 * **Pipeline:**
 * 1. Send a HEAD request to get the total content length + check if the server
 *    supports Range requests (`Accept-Ranges: bytes`).
 * 2. If Range is NOT supported (or the file is too small) → fall back to a
 *    single-threaded download (degraded but functional).
 * 3. If Range IS supported:
 *    a. Split the file into N chunks (configurable, default 4).
 *    b. Check for resume metadata — if it exists + chunk files are valid,
 *       resume each chunk from its last downloaded byte.
 *    c. Download each chunk in parallel (coroutines) using Range requests.
 *    d. Write each chunk to its own `.part` file (RandomAccessFile for
 *       concurrent writes without corruption).
 *    e. Track per-chunk progress; periodically save resume metadata.
 *    f. If a chunk fails (network error), retry it (up to maxRetries, default 3).
 *    g. On success, concatenate the chunks into the final temp video file.
 * 4. Clean up chunk files + resume metadata.
 *
 * **Resume capability:**
 * - If the download is interrupted (app crash, network loss, user pause), the
 *   resume metadata + chunk files persist in the temp cache.
 * - On restart, [DownloadResumeManager.loadResume] reads the metadata, validates
 *   the chunk files, and each chunk resumes from its last downloaded byte.
 * - The user doesn't need to do anything — resume is automatic.
 *
 * **When to use Advanced vs Normal:**
 * - Advanced: direct video files (mp4/mkv/etc.) where the server supports Range.
 * - Normal: HLS streams (segmented, can't be multi-threaded) + small files.
 * - The [HttpDownloader] decides which to use based on the method pref + video type.
 *
 * **Thread-safety:** each chunk writes to its own `.part` file (no shared state).
 * The progress callback uses a Mutex to aggregate bytes safely.
 *
 * All I/O on `Dispatchers.IO`.
 *
 * @param client Shared OkHttp client.
 * @param tempCache Temp cache for chunk files + resume metadata.
 * @param resumeManager Resume metadata manager.
 * @param preferences Download preferences (thread count, retries, min size).
 */
class AdvancedHttpDownloader(
    private val client: OkHttpClient,
    private val tempCache: TempDownloadCache,
    private val resumeManager: DownloadResumeManager,
    private val preferences: DownloadPreferences,
) {

    /**
     * Downloads a video using multi-threaded Range requests + resume.
     *
     * @param taskId The download task ID (for temp file naming).
     * @param videoUrl The direct video URL.
     * @param headers HTTP headers (newline-separated "Key: Value").
     * @param tempVideoFile The final output file (chunks are concatenated here).
     * @param onProgress Called with (downloadedBytes, totalBytes).
     * @return The total bytes downloaded.
     * @throws DownloadException on any failure.
     * @throws kotlinx.coroutines.CancellationException on pause/cancel.
     */
    suspend fun download(
        taskId: Long,
        videoUrl: String,
        headers: String?,
        tempVideoFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        DownloadLogger.i("Advanced download starting: $videoUrl")

        // ── 1. HEAD request to check Range support + get content length ──
        val (totalBytes, supportsRange) = probeServer(videoUrl, headers)
        if (totalBytes <= 0) {
            throw DownloadException("Server didn't return a valid Content-Length — cannot use multi-threaded download.")
        }

        val threadCount = preferences.advancedThreadCount().get().coerceIn(1, 8)
        val maxRetries = preferences.advancedMaxRetries().get().coerceIn(0, 10)
        val minSizeBytes = preferences.advancedMinSizeMb().get() * 1024L * 1024L

        // ── 2. Decide: multi-threaded or single-threaded? ──
        if (!supportsRange || totalBytes < minSizeBytes || threadCount == 1) {
            DownloadLogger.i("Advanced: falling back to single-threaded " +
                "(range=$supportsRange, size=$totalBytes, minSize=$minSizeBytes, threads=$threadCount)")
            return@withContext downloadSingleThreaded(taskId, videoUrl, headers, tempVideoFile, totalBytes, onProgress)
        }

        // ── 3. Multi-threaded: split into chunks ──
        val chunkSize = totalBytes / threadCount
        val chunks = (0 until threadCount).map { i ->
            val start = i * chunkSize
            val end = if (i == threadCount - 1) totalBytes - 1 else (start + chunkSize - 1)
            DownloadResumeManager.ChunkProgress(i, start, end, 0L)
        }

        // ── 4. Check for resume metadata ──
        val resumeMetadata = resumeManager.loadResume(taskId)
        val resumeChunks = if (resumeMetadata != null &&
            resumeMetadata.videoUrl == videoUrl &&
            resumeMetadata.chunks.size == chunks.size) {
            DownloadLogger.i("Advanced: resuming from ${resumeMetadata.chunks.sumOf { it.downloaded }} / $totalBytes bytes")
            resumeMetadata.chunks
        } else {
            chunks
        }

        // ── 5. Download each chunk in parallel ──
        val progressMutex = Mutex()
        val chunkProgress = resumeChunks.toMutableList()
        var lastSaveTime = System.currentTimeMillis()

        suspend fun saveResumeThrottled() {
            val now = System.currentTimeMillis()
            if (now - lastSaveTime >= RESUME_SAVE_INTERVAL_MS) {
                resumeManager.saveResume(DownloadResumeManager.ResumeMetadata(
                    taskId = taskId,
                    videoUrl = videoUrl,
                    totalBytes = totalBytes,
                    chunkCount = threadCount,
                    chunks = chunkProgress.toList(),
                ))
                lastSaveTime = now
            }
        }

        try {
            coroutineScope {
                chunkProgress.map { chunk ->
                    async(Dispatchers.IO) {
                        downloadChunkWithRetry(
                            taskId = taskId,
                            chunk = chunk,
                            videoUrl = videoUrl,
                            headers = headers,
                            maxRetries = maxRetries,
                            onChunkProgress = { downloaded ->
                                progressMutex.withLock {
                                    chunkProgress[chunk.index] = chunk.copy(downloaded = downloaded)
                                    val total = chunkProgress.sumOf { it.downloaded }
                                    onProgress(total, totalBytes)
                                }
                                saveResumeThrottled()
                            },
                        )
                    }
                }.awaitAll()
            }

            // ── 6. Concatenate chunks into the final file ──
            concatenateChunks(taskId, threadCount, tempVideoFile)

            // ── 7. Clean up ──
            resumeManager.clearResume(taskId)
            for (i in 0 until threadCount) {
                resumeManager.chunkFile(taskId, i).delete()
            }

            DownloadLogger.i("Advanced download complete: ${tempVideoFile.length()} bytes ($threadCount threads)")
            tempVideoFile.length()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Save resume metadata on cancellation (pause/cancel) so we can resume.
            resumeManager.saveResume(DownloadResumeManager.ResumeMetadata(
                taskId = taskId,
                videoUrl = videoUrl,
                totalBytes = totalBytes,
                chunkCount = threadCount,
                chunks = chunkProgress.toList(),
            ))
            throw e
        }
    }

    /**
     * Probes the server with a HEAD request to get Content-Length + Accept-Ranges.
     * Returns (totalBytes, supportsRange).
     */
    private suspend fun probeServer(url: String, headers: String?): Pair<Long, Boolean> {
        val request = buildRequest(url, headers).head().build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DownloadException("HTTP ${response.code} probing server for Range support")
                }
                val total = response.header("Content-Length")?.toLongOrNull() ?: -1L
                val acceptRanges = response.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) ?: false
                DownloadLogger.i("Advanced probe: totalBytes=$total, acceptRanges=$acceptRanges")
                total to acceptRanges
            }
        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            throw DownloadException("Server probe failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /**
     * Downloads a single chunk with retry logic. Uses Range requests to resume
     * from the chunk's last downloaded byte. Writes to the chunk's `.part` file
     * using RandomAccessFile (for concurrent writes).
     */
    private suspend fun downloadChunkWithRetry(
        taskId: Long,
        chunk: DownloadResumeManager.ChunkProgress,
        videoUrl: String,
        headers: String?,
        maxRetries: Int,
        onChunkProgress: (downloaded: Long) -> Unit,
    ) {
        var attempt = 0
        var lastError: Exception? = null

        while (attempt <= maxRetries) {
            try {
                downloadChunk(taskId, chunk, videoUrl, headers, onChunkProgress)
                return // success
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // don't retry on cancel
            } catch (e: Exception) {
                attempt++
                lastError = e
                if (attempt <= maxRetries) {
                    DownloadLogger.w("Advanced: chunk ${chunk.index} attempt $attempt failed, retrying: ${e.message}")
                    delay(RETRY_DELAY_MS)
                }
            }
        }
        throw DownloadException(
            "Chunk ${chunk.index} failed after $maxRetries retries: ${lastError?.message}",
            lastError,
        )
    }

    /**
     * Downloads a single chunk. Resumes from [chunk.downloaded] bytes.
     * Uses `Range: bytes=start+downloaded-end` to request the remaining bytes.
     */
    private suspend fun downloadChunk(
        taskId: Long,
        chunk: DownloadResumeManager.ChunkProgress,
        videoUrl: String,
        headers: String?,
        onChunkProgress: (downloaded: Long) -> Unit,
    ) {
        val chunkFile = resumeManager.chunkFile(taskId, chunk.index)
        val resumeFrom = chunk.start + chunk.downloaded
        val rangeHeader = "bytes=$resumeFrom-${chunk.end}"

        val request = buildRequest(videoUrl, headers)
            .header("Range", rangeHeader)
            .build()

        DownloadLogger.d("Advanced: chunk ${chunk.index} downloading $rangeHeader (resume from ${chunk.downloaded})")

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                throw DownloadException("HTTP ${response.code} for chunk ${chunk.index}")
            }
            // Open the chunk file in append mode if resuming, or write mode if fresh.
            val raf = RandomAccessFile(chunkFile, "rw")
            raf.use { out ->
                out.seek(chunk.downloaded) // position at the resume point
                response.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(CHUNK_BUFFER_SIZE)
                    var downloaded = chunk.downloaded
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        onChunkProgress(downloaded)
                    }
                }
            }
        }
    }

    /** Concatenates the chunk files into the final video file. */
    private fun concatenateChunks(taskId: Long, chunkCount: Int, outputFile: File) {
        outputFile.outputStream().use { out ->
            for (i in 0 until chunkCount) {
                val chunkFile = resumeManager.chunkFile(taskId, i)
                if (!chunkFile.exists()) {
                    throw DownloadException("Chunk $i file missing during concatenation")
                }
                chunkFile.inputStream().use { it.copyTo(out) }
            }
            out.flush()
        }
        DownloadLogger.d("Advanced: concatenated $chunkCount chunks → ${outputFile.length()} bytes")
    }

    /**
     * Single-threaded fallback (used when the server doesn't support Range or
     * the file is too small for multi-threading to be worth it).
     */
    private suspend fun downloadSingleThreaded(
        taskId: Long,
        videoUrl: String,
        headers: String?,
        tempVideoFile: File,
        totalBytes: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Long {
        val request = buildRequest(videoUrl, headers).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DownloadException("HTTP ${response.code} for video URL")
                }
                tempVideoFile.outputStream().use { out ->
                    response.body?.byteStream()?.use { input ->
                        val buffer = ByteArray(CHUNK_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, totalBytes)
                        }
                    }
                    out.flush()
                }
                tempVideoFile.length()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            throw DownloadException("Single-threaded download failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /** Builds a Request.Builder with the headers parsed from the "Key: Value\n" format. */
    private fun buildRequest(url: String, headers: String?): Request.Builder {
        return Request.Builder().url(url).apply {
            if (!headers.isNullOrBlank()) {
                headers.split('\n').forEach { line ->
                    val sep = line.indexOf(':')
                    if (sep > 0) {
                        addHeader(line.substring(0, sep).trim(), line.substring(sep + 1).trim())
                    }
                }
            }
        }
    }

    companion object {
        private const val CHUNK_BUFFER_SIZE = 64 * 1024 // 64 KB
        private const val RESUME_SAVE_INTERVAL_MS = 2_000L // save resume metadata every 2s
        private const val RETRY_DELAY_MS = 1_000L // wait 1s between retries
    }
}
