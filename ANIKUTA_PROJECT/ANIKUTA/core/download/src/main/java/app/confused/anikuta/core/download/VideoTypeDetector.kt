package app.confused.anikuta.core.download

import okhttp3.Response

/**
 * Detects what kind of content a video URL points to, so the downloader can
 * decide how to handle it.
 *
 * **Why this exists.** Many anime extensions return URLs that are NOT direct
 * video files:
 *  - **HLS** (`.m3u8`) — a playlist of segment URLs. Downloading the `.m3u8`
 *    file itself gives you a tiny text file, not the video. Aniyomi handles
 *    these via ffmpeg; the default ANIKUTA downloader cannot (no ffmpeg bundled).
 *    We detect + reject these with a clear error so the user isn't confused by
 *    a "completed" 5KB corrupt file.
 *  - **DASH** (`.mpd`) — same problem as HLS (XML manifest, not video).
 *  - **HTML pages** — some extensions return a watch-page URL rather than a
 *    stream URL (a resolver bug). Downloading it gives HTML. We detect via the
 *    `Content-Type` header.
 *  - **Direct video** (`.mp4`, `.mkv`, `.webm`, etc.) — the only type the
 *    default downloader can handle. Stream to disk.
 *
 * **Future.** The 1DM method (ADR-020) will support HLS via ffmpeg. The
 * detector's [VideoType] enum is designed so the future downloader can route
 * HLS to an ffmpeg pipeline without changing this class.
 */
object VideoTypeDetector {

    /** The kind of content a video URL/response represents. */
    enum class VideoType {
        /** A direct video file (mp4/mkv/webm/m4v/ts/mov/avi). Stream to disk. */
        DIRECT_VIDEO,

        /** An HLS playlist (.m3u8). Needs ffmpeg to download — not supported by the default downloader. */
        HLS_STREAM,

        /** A DASH manifest (.mpd). Needs ffmpeg — not supported by the default downloader. */
        DASH_STREAM,

        /** An HTML page (the URL is a watch page, not a stream — a resolver bug). */
        HTML_PAGE,

        /** Unknown — inspect further or reject. */
        UNKNOWN,
    }

    /**
     * Inspects the URL + the OkHttp [response] headers to determine the content
     * type. The [response] should be the executed call (body not yet consumed).
     *
     * Priority: Content-Type header > URL extension > unknown.
     */
    fun detect(url: String, response: Response): VideoType {
        val contentType = response.header("Content-Type")?.lowercase()?.trim() ?: ""

        // ── Content-Type header ──
        if (contentType.contains("html")) return VideoType.HTML_PAGE
        if (contentType.contains("mpegurl") || contentType.contains("m3u8")) return VideoType.HLS_STREAM
        if (contentType.contains("dash+xml") || contentType.contains("mpd")) return VideoType.DASH_STREAM
        // Direct video content types
        if (contentType.startsWith("video/")) return VideoType.DIRECT_VIDEO

        // ── URL extension (fallback when Content-Type is missing/generic) ──
        val urlType = detectFromUrl(url)
        // If the URL clearly indicates HLS/DASH/HTML, honor that (catches
        // servers that send a generic Content-Type for HLS playlists).
        if (urlType == VideoType.HLS_STREAM || urlType == VideoType.DASH_STREAM ||
            urlType == VideoType.HTML_PAGE) {
            return urlType
        }
        // If the URL clearly indicates a direct video (mp4/mkv/etc.), honor it.
        if (urlType == VideoType.DIRECT_VIDEO) return VideoType.DIRECT_VIDEO

        // UNKNOWN — the Content-Type is generic (octet-stream, missing, etc.)
        // and the URL has no clean extension. This is VERY common for video
        // CDNs (e.g. `https://cdn.example.com/video/abc123?token=xyz`).
        // We treat UNKNOWN as DIRECT_VIDEO (downloadable) — the file-size
        // validation in HttpDownloader catches corrupt/error downloads.
        // Rejecting UNKNOWN here was the cause of the "Unknown video format"
        // error on valid video URLs.
        return VideoType.DIRECT_VIDEO
    }

    /** Inspects only the URL (no response yet) — used for pre-flight checks. */
    fun detectFromUrl(url: String): VideoType {
        val noQuery = url.substringBefore('?').lowercase()
        val path = noQuery.substringAfterLast('/')
        val dot = path.lastIndexOf('.')
        if (dot < 0) return VideoType.UNKNOWN
        val ext = path.substring(dot + 1)
        return when (ext) {
            "mp4", "mkv", "webm", "m4v", "mov", "avi", "ts" -> VideoType.DIRECT_VIDEO
            "m3u8", "m3u" -> VideoType.HLS_STREAM
            "mpd" -> VideoType.DASH_STREAM
            "html", "htm", "php", "asp", "aspx", "jsp" -> VideoType.HTML_PAGE
            else -> VideoType.UNKNOWN
        }
    }

    /**
     * True if this video type can be downloaded by the default downloader.
     * DIRECT_VIDEO → [HttpDownloader]; HLS_STREAM → [HlsDownloader].
     * DASH + HTML remain unsupported (DASH needs ffmpeg; HTML is a resolver bug).
     */
    fun isDownloadable(type: VideoType): Boolean =
        type == VideoType.DIRECT_VIDEO || type == VideoType.HLS_STREAM

    /** A human-readable reason for unsupported types (for the error message). */
    fun unsupportedReason(type: VideoType): String? = when (type) {
        VideoType.DASH_STREAM -> "DASH stream (.mpd) — requires the 1DM download method (future)."
        VideoType.HTML_PAGE -> "The URL returned an HTML page, not a video. This is likely a resolver issue with this extension."
        VideoType.UNKNOWN -> "Unknown video format — cannot verify this is a playable video file."
        VideoType.DIRECT_VIDEO -> null
        VideoType.HLS_STREAM -> null // now supported via HlsDownloader
    }
}
