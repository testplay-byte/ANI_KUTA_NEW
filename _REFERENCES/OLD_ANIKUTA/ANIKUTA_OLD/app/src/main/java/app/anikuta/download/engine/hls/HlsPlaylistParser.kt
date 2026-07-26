package app.anikuta.download.engine.hls

import okhttp3.Headers

/**
 * Pure m3u8 text parser — no network IO.
 *
 * Parses HLS playlist text into [HlsPlaylist] (Master or Media).
 *
 * Supported tags:
 * - #EXTM3U (required header)
 * - #EXT-X-VERSION
 * - #EXT-X-TARGETDURATION
 * - #EXT-X-MEDIA-SEQUENCE
 * - #EXT-X-PLAYLIST-TYPE (VOD / EVENT)
 * - #EXT-X-ENDLIST (indicates VOD)
 * - #EXT-X-STREAM-INF (master playlist variant)
 * - #EXTINF (segment duration)
 * - #EXT-X-KEY (AES-128 encryption)
 * - #EXT-X-DISCONTINUITY (ignored)
 *
 * Unsupported (throws to trigger FFmpeg fallback):
 * - #EXT-X-MAP (fMP4/CMAF init segment)
 * - METHOD=SAMPLE-AES (FairPlay DRM)
 */
class HlsPlaylistParser {

    /**
     * Parse m3u8 text.
     * @param text the raw m3u8 content
     * @param baseUrl the URL the playlist was fetched from (for resolving relative URLs)
     * @param headers HTTP headers to attach to the result (for downstream segment fetches)
     * @return parsed playlist (Master or Media)
     * @throws IllegalArgumentException if the text is not a valid m3u8 or uses unsupported features
     */
    fun parse(text: String, baseUrl: String, headers: Headers): HlsPlaylist {
        require(text.startsWith("#EXTM3U")) { "Not an m3u8 file (missing #EXTM3U)" }

        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") || it.startsWith("#EXT") }

        val hasStreamInf = lines.any { it.startsWith("#EXT-X-STREAM-INF") }
        return if (hasStreamInf) parseMaster(lines, baseUrl, headers)
        else parseMedia(lines, baseUrl, headers)
    }

    // ---- Master playlist ----

    private fun parseMaster(lines: List<String>, baseUrl: String, headers: Headers): HlsPlaylist.Master {
        val variants = mutableListOf<HlsPlaylist.Master.Variant>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val attrs = parseAttributes(line.substringAfter("#EXT-X-STREAM-INF:"))
                i++
                if (i < lines.size) {
                    val uri = HlsUrlResolver.resolve(baseUrl, lines[i])
                    variants.add(
                        HlsPlaylist.Master.Variant(
                            uri = uri,
                            bandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0L,
                            resolution = attrs["RESOLUTION"],
                            codecs = attrs["CODECS"],
                            frameRate = attrs["FRAME-RATE"]?.toDoubleOrNull(),
                        )
                    )
                }
            }
            i++
        }
        return HlsPlaylist.Master(baseUrl, headers, variants)
    }

    // ---- Media playlist ----

    private fun parseMedia(lines: List<String>, baseUrl: String, headers: Headers): HlsPlaylist.Media {
        var version = 3
        var targetDurationSec = 10
        var mediaSequence: Long = 0
        var isVod = false
        var currentKey: HlsPlaylist.Media.Key? = null
        val segments = mutableListOf<HlsPlaylist.Media.Segment>()
        var pendingDuration: Double? = null

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-VERSION:") -> {
                    version = line.substringAfter(":").trim().toIntOrNull() ?: 3
                }

                line.startsWith("#EXT-X-TARGETDURATION:") -> {
                    targetDurationSec = line.substringAfter(":").trim().toIntOrNull() ?: 10
                }

                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    mediaSequence = line.substringAfter(":").trim().toLongOrNull() ?: 0L
                }

                line.startsWith("#EXT-X-PLAYLIST-TYPE:") -> {
                    val type = line.substringAfter(":").trim().uppercase()
                    if (type == "VOD") isVod = true
                }

                line.startsWith("#EXT-X-ENDLIST") -> {
                    isVod = true
                }

                line.startsWith("#EXT-X-KEY:") -> {
                    currentKey = parseKey(line.substringAfter("#EXT-X-KEY:"), baseUrl)
                }

                line.startsWith("#EXT-X-MAP:") -> {
                    throw IllegalArgumentException("EXT-X-MAP (fMP4) not supported by direct downloader — use FFmpeg")
                }

                line.startsWith("#EXTINF:") -> {
                    val durStr = line.substringAfter("#EXTINF:").substringBefore(",")
                    pendingDuration = durStr.trim().toDoubleOrNull() ?: 0.0
                }

                line.startsWith("#EXT-X-DISCONTINUITY") -> {
                    // Ignore — concat demuxer handles timestamp resets
                }

                line.startsWith("#EXT-X-BYTERANGE:") -> {
                    // v1: skip byte-range segments (rare in anime sources)
                }

                !line.startsWith("#") && pendingDuration != null -> {
                    val segUrl = HlsUrlResolver.resolve(baseUrl, line)
                    val index = segments.size
                    segments.add(
                        HlsPlaylist.Media.Segment(
                            index = index,
                            sequenceNumber = mediaSequence + index,
                            durationSec = pendingDuration,
                            url = segUrl,
                            key = currentKey,
                        )
                    )
                    pendingDuration = null
                }
            }
        }

        return HlsPlaylist.Media(
            baseUrl = baseUrl,
            headers = headers,
            version = version,
            targetDurationSec = targetDurationSec,
            mediaSequence = mediaSequence,
            isVod = isVod,
            segments = segments,
        )
    }

    // ---- Attribute parsing ----

    /**
     * Parse an attribute string like:
     *   BANDWIDTH=2400000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
     * into a map. Handles quoted values containing commas.
     */
    private fun parseAttributes(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val sb = StringBuilder()
        var key = ""
        var inQuotes = false
        var afterEquals = false

        for (ch in text) {
            when {
                ch == '"' -> { inQuotes = !inQuotes; sb.append(ch) }
                ch == '=' && !inQuotes -> {
                    key = sb.toString().trim()
                    sb.clear()
                    afterEquals = true
                }
                ch == ',' && !inQuotes -> {
                    if (afterEquals) result[key] = sb.toString().trim()
                    sb.clear()
                    afterEquals = false
                }
                else -> sb.append(ch)
            }
        }
        if (afterEquals && key.isNotEmpty()) {
            result[key] = sb.toString().trim()
        }
        return result
    }

    /**
     * Parse #EXT-X-KEY attributes.
     * Example: METHOD=AES-128,URI="https://cdn/key.bin",IV=0x00000000000000000000000000000001
     */
    private fun parseKey(text: String, baseUrl: String): HlsPlaylist.Media.Key {
        val attrs = parseAttributes(text)
        val methodStr = attrs["METHOD"] ?: "NONE"
        val method = when (methodStr.uppercase()) {
            "NONE" -> HlsPlaylist.Media.Key.Method.NONE
            "AES-128" -> HlsPlaylist.Media.Key.Method.AES_128
            "SAMPLE-AES" -> throw IllegalArgumentException("SAMPLE-AES not supported")
            else -> HlsPlaylist.Media.Key.Method.UNKNOWN
        }
        val uri = attrs["URI"]?.trim('"')?.let { HlsUrlResolver.resolve(baseUrl, it) }
        val iv = attrs["IV"]?.let { parseIv(it) }
        return HlsPlaylist.Media.Key(method, uri, iv)
    }

    /**
     * Parse an IV hex string like "0x00000000000000000000000000000001" into a 16-byte array.
     */
    private fun parseIv(ivStr: String): ByteArray {
        val hex = ivStr.removePrefix("0x").removePrefix("0X")
        val padded = hex.padStart(32, '0')
        return padded.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
