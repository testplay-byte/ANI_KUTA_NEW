package app.anikuta.download.engine.hls

import java.net.URI

/**
 * Resolves relative URLs against an m3u8 base URL (RFC 3986).
 *
 * HLS segment, key, and variant URLs may be relative. This object resolves
 * them to absolute URLs using standard URI resolution.
 */
object HlsUrlResolver {

    /**
     * Resolve [ref] (relative or absolute) against [base] (the m3u8 URL).
     *
     * Handles:
     * - Absolute URLs (http://, https://) → returned as-is
     * - Protocol-relative (//host/path) → prepend scheme from base
     * - Absolute path (/path) → keep scheme+host+port from base
     * - Relative path (path, ./path, ../path) → resolve against base's directory
     */
    fun resolve(base: String, ref: String): String {
        if (ref.isBlank()) return ref
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        if (ref.startsWith("//")) {
            val scheme = runCatching { URI(base).scheme }.getOrDefault("https")
            return "$scheme:$ref"
        }
        return runCatching { URI(base).resolve(ref).toString() }.getOrElse { ref }
    }
}
