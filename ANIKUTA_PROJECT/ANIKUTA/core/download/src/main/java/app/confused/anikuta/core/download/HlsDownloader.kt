package app.confused.anikuta.core.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Downloads HLS (`.m3u8`) streams by parsing the playlist, downloading each
 * segment, and concatenating them into a single `.ts` file that MPV plays
 * natively — **no ffmpeg required**.
 *
 * **Why this exists.** The owner confirmed that anime apps (including Aniyomi)
 * download HLS streams. Most anime extensions return HLS URLs. Without HLS
 * support, the majority of downloads fail. This class makes HLS downloadable
 * using segment concatenation (the standard approach for unencrypted HLS).
 *
 * **Pipeline:**
 * 1. Fetch the `.m3u8` playlist text.
 * 2. If it's a **master playlist** (contains `#EXT-X-STREAM-INF`), pick the
 *    first variant (highest bandwidth by default) → fetch its media playlist.
 * 3. Parse the media playlist for segment URLs (`.ts`, `.m4s`, `.aac`, `.mp4`).
 * 4. Check for **encryption** (`#EXT-X-KEY`). Encrypted HLS needs AES
 *    decryption — not supported by the default downloader (rejected with a
 *    clear error; most anime HLS is unencrypted).
 * 5. Download each segment sequentially → append to the output `.ts` file.
 * 6. Report progress per segment.
 *
 * **Relative URLs.** Segment URLs in playlists are often relative. Resolved
 * against the playlist URL (both relative-to-directory and relative-to-base).
 *
 * **Concatenation.** For `.ts` segments, simple byte concatenation produces a
 * valid MPEG-TS stream (MPV plays it). For `.m4s` (fMP4), the init segment
 * (`#EXT-X-MAP`) is written first, then media segments — also concatenated.
 * This works for the common case; edge cases (discontinuities, ad breaks) may
 * produce a file with minor glitches but is still playable.
 *
 * **Future 1DM.** The 1DM method will use ffmpeg for HLS (proper muxing +
 * encrypted support). This class is the default-method fallback.
 *
 * All I/O on `Dispatchers.IO`.
 */
class HlsDownloader(
    private val client: OkHttpClient,
) {

    /**
     * Downloads an HLS stream to [tempFile] (a `.ts` file).
     *
     * @param m3u8Url The `.m3u8` playlist URL.
     * @param headers HTTP headers (newline-separated "Key: Value" format).
     * @param tempFile The output file (will be overwritten).
     * @param onProgress Called with (segmentsDownloaded, totalSegments).
     * @return The total bytes written.
     * @throws DownloadException on any failure (encrypted, no segments, network).
     */
    suspend fun download(
        m3u8Url: String,
        headers: String?,
        tempFile: File,
        onProgress: (downloadedSegments: Long, totalSegments: Long) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        DownloadLogger.i("HLS download starting: $m3u8Url")

        // ── 1. Fetch the playlist ──
        val masterText = fetchText(m3u8Url, headers)
        if (masterText.isBlank()) {
            throw DownloadException("HLS playlist is empty — the source returned no data.")
        }

        // ── 2. Master playlist → pick the first variant ──
        val (mediaPlaylistUrl, mediaText) = if (isMasterPlaylist(masterText)) {
            val variantUrl = pickFirstVariant(masterText, m3u8Url)
                ?: throw DownloadException("HLS master playlist has no variants.")
            DownloadLogger.d("HLS master → variant: $variantUrl")
            variantUrl to fetchText(variantUrl, headers)
        } else {
            m3u8Url to masterText
        }

        if (mediaText.isBlank()) {
            throw DownloadException("HLS media playlist is empty.")
        }

        // ── 3. Check for encryption ──
        if (isEncrypted(mediaText)) {
            throw DownloadException(
                "Encrypted HLS stream — the default downloader cannot decrypt DRM/AES-128. " +
                "This will be supported by the 1DM download method (future)."
            )
        }

        // ── 4. Parse segments + init map ──
        val initSegmentUrl = parseInitSegment(mediaText, mediaPlaylistUrl)
        val segments = parseSegments(mediaText, mediaPlaylistUrl)
        if (segments.isEmpty()) {
            throw DownloadException("HLS playlist contains no segments.")
        }
        DownloadLogger.i("HLS: ${segments.size} segment(s)" +
            (if (initSegmentUrl != null) " + init segment" else ""))

        // ── 5. Download + concatenate ──
        val totalToDownload = segments.size + (if (initSegmentUrl != null) 1 else 0)
        var downloaded = 0L
        FileOutputStream(tempFile).use { out ->
            // Write the init segment first (for fMP4/.m4s streams).
            if (initSegmentUrl != null) {
                coroutineContext.ensureActive()
                downloadSegment(initSegmentUrl, headers, out)
                downloaded++
                onProgress(downloaded, totalToDownload)
            }
            // Download each media segment.
            for (segUrl in segments) {
                coroutineContext.ensureActive()
                downloadSegment(segUrl, headers, out)
                downloaded++
                onProgress(downloaded, totalToDownload)
            }
            out.flush()
        }

        val totalBytes = tempFile.length()
        DownloadLogger.i("HLS download complete: $totalBytes bytes (${segments.size} segments)")
        totalBytes
    }

    /** Fetches the text content of a playlist URL. */
    private fun fetchText(url: String, headers: String?): String {
        val request = buildRequest(url, headers)
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DownloadException("HTTP ${response.code} fetching HLS playlist")
                }
                response.body?.string() ?: ""
            }
        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            throw DownloadException("Failed to fetch HLS playlist: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /** Downloads a single segment and appends its bytes to [out]. */
    private fun downloadSegment(url: String, headers: String?, out: FileOutputStream) {
        val request = buildRequest(url, headers)
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DownloadException("HTTP ${response.code} fetching segment: $url")
                }
                response.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(SEGMENT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            throw DownloadException("Segment download failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /** Builds an OkHttp request with the headers parsed from the "Key: Value\n" format. */
    private fun buildRequest(url: String, headers: String?): Request {
        return Request.Builder().url(url).apply {
            if (!headers.isNullOrBlank()) {
                headers.split('\n').forEach { line ->
                    val sep = line.indexOf(':')
                    if (sep > 0) {
                        addHeader(line.substring(0, sep).trim(), line.substring(sep + 1).trim())
                    }
                }
            }
        }.build()
    }

    // ── HLS playlist parsing ──

    /** True if the playlist is a master playlist (contains variant streams). */
    private fun isMasterPlaylist(text: String): Boolean =
        text.contains("#EXT-X-STREAM-INF")

    /** True if the playlist is encrypted (EXT-X-KEY with a METHOD other than NONE). */
    private fun isEncrypted(text: String): Boolean {
        // Look for #EXT-X-KEY lines with a method other than NONE.
        val keyLines = text.lines().filter { it.startsWith("#EXT-X-KEY") }
        for (line in keyLines) {
            val method = Regex("METHOD=([^,\\s]+)").find(line)?.groupValues?.getOrNull(1)
            if (method != null && method.uppercase() != "NONE") {
                DownloadLogger.w("HLS encrypted: METHOD=$method")
                return true
            }
        }
        return false
    }

    /**
     * Picks the first variant URL from a master playlist.
     * Returns the resolved absolute URL (or null if no variants).
     * The first variant is typically the highest bandwidth (playlists are
     * usually ordered by quality descending).
     */
    private fun pickFirstVariant(text: String, baseUrl: String): String? {
        val lines = text.lines()
        for (i in lines.indices) {
            if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                // The next non-comment line is the variant URL.
                for (j in i + 1 until lines.size) {
                    val line = lines[j].trim()
                    if (line.isNotEmpty() && !line.startsWith("#")) {
                        return resolveUrl(line, baseUrl)
                    }
                }
            }
        }
        return null
    }

    /** Extracts the init segment URL (#EXT-X-MAP:URI="...") for fMP4 streams. */
    private fun parseInitSegment(text: String, baseUrl: String): String? {
        for (line in text.lines()) {
            if (line.startsWith("#EXT-X-MAP")) {
                val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)
                if (uri != null) return resolveUrl(uri, baseUrl)
            }
        }
        return null
    }

    /** Extracts all media segment URLs from the playlist (non-comment lines after #EXTINF). */
    private fun parseSegments(text: String, baseUrl: String): List<String> {
        val segments = mutableListOf<String>()
        val lines = text.lines()
        var expectSegment = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF") || trimmed.startsWith("#EXT-X-BYTERANGE")) {
                expectSegment = true
            } else if (expectSegment && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                segments.add(resolveUrl(trimmed, baseUrl))
                expectSegment = false
            }
        }
        return segments
    }

    /**
     * Resolves a possibly-relative URL against the base playlist URL.
     * Handles: absolute URLs, relative-to-directory, relative-to-base.
     */
    private fun resolveUrl(url: String, baseUrl: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        return try {
            val base = java.net.URI(baseUrl)
            base.resolve(url).toString()
        } catch (e: Exception) {
            // Fallback: simple directory-relative resolution.
            val baseDir = baseUrl.substringBeforeLast('/', "")
            if (baseDir.isBlank()) url else "$baseDir/$url"
        }
    }

    companion object {
        private const val SEGMENT_BUFFER_SIZE = 64 * 1024 // 64 KB per segment read
    }
}
