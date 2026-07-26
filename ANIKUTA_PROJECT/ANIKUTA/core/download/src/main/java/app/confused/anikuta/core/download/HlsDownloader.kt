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
     * @param onProgress Called with (downloadedBytes, totalBytes). totalBytes is
     *   -1 (unknown) since HLS segment sizes aren't known until downloaded.
     *   The caller's DynamicProgressTracker handles the -1 case by estimating.
     * @return The total bytes written.
     * @throws DownloadException on any failure (encrypted, no segments, network).
     */
    suspend fun download(
        m3u8Url: String,
        headers: String?,
        tempFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
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
        // Report progress as ACTUAL BYTES (file size), not segment counts.
        // totalBytes = -1 (unknown) — the DynamicProgressTracker will estimate.
        FileOutputStream(tempFile).use { out ->
            // Write the init segment first (for fMP4/.m4s streams).
            if (initSegmentUrl != null) {
                coroutineContext.ensureActive()
                downloadSegment(initSegmentUrl, headers, out)
                out.flush()
                onProgress(tempFile.length(), -1L)
            }
            // Download each media segment.
            for ((i, segUrl) in segments.withIndex()) {
                coroutineContext.ensureActive()
                downloadSegment(segUrl, headers, out)
                out.flush()
                // Report actual file size after each segment
                onProgress(tempFile.length(), -1L)
                if (i % 5 == 0) {
                    DownloadLogger.d("HLS progress: segment ${i+1}/${segments.size}, ${tempFile.length()/1024}KB")
                }
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

    /**
     * Downloads a single segment, strips any PNG header (anti-scraping
     * obfuscation used by some CDNs), and appends the cleaned bytes to [out].
     *
     * **Why PNG stripping is needed:** some CDNs (e.g. megaplay.buzz /
     * kotocdn.site) prepend a PNG image header to each HLS segment to prevent
     * direct downloading. The extension's LocalProxyServer strips this header
     * before serving to MPV. Our downloader must do the same — otherwise the
     * concatenated .ts file starts with PNG magic bytes and is rejected.
     *
     * The stripping logic mirrors the extension's `stripPngHeader`:
     * 1. Check if the segment starts with PNG magic bytes (89 50 4E 47).
     * 2. Find the "IEND" marker (end of the PNG data).
     * 3. Skip 8 bytes after IEND.
     * 4. Look for the MPEG-TS sync byte (0x47) at a position where 0x47 also
     *    appears 188 bytes later (confirming it's a real sync byte).
     * 5. Return everything from that sync byte onward.
     *
     * If the segment doesn't start with PNG, it's returned as-is.
     */
    private fun downloadSegment(url: String, headers: String?, out: FileOutputStream) {
        val request = buildRequest(url, headers)
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DownloadException("HTTP ${response.code} fetching segment: $url")
                }
                // Read the full segment into memory (segments are typically < 5MB).
                val rawBytes = response.body?.bytes()
                    ?: throw DownloadException("Empty segment response: $url")
                // Strip PNG header if present.
                val cleanBytes = stripPngHeader(rawBytes)
                out.write(cleanBytes)
            }
        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            throw DownloadException("Segment download failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /**
     * Strips a PNG header from a segment if present.
     * Mirrors the extension's LocalProxyServer.stripPngHeader logic.
     */
    private fun stripPngHeader(data: ByteArray): ByteArray {
        if (data.size < 8) return data
        // Check for PNG magic bytes (89 50 4E 47)
        if (!(data[0] == 0x89.toByte() && data[1] == 'P'.code.toByte() &&
                data[2] == 'N'.code.toByte() && data[3] == 'G'.code.toByte())) {
            return data // Not a PNG — return as-is
        }
        // Find the IEND marker (end of PNG data)
        var cut = -1
        for (i in 0 until data.size - 4) {
            if (data[i] == 'I'.code.toByte() && data[i + 1] == 'E'.code.toByte() &&
                data[i + 2] == 'N'.code.toByte() && data[i + 3] == 'D'.code.toByte()) {
                cut = i + 8 // skip 8 bytes after IEND (IEND + CRC)
                break
            }
        }
        if (cut < 0 || cut >= data.size) return data
        // Look for the MPEG-TS sync byte (0x47) where 0x47 also appears 188 bytes later
        val scanLimit = minOf(data.size - 188, cut + 400)
        for (i in cut until scanLimit) {
            if (data[i] == 0x47.toByte() && data[i + 188] == 0x47.toByte()) {
                DownloadLogger.d("Stripped PNG header: ${cut} bytes → MPEG-TS starts at $i")
                return data.copyOfRange(i, data.size)
            }
        }
        // Fallback: just cut after IEND
        DownloadLogger.d("Stripped PNG header (fallback): cut at $cut")
        return data.copyOfRange(cut, data.size)
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
