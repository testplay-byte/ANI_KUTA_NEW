package app.anikuta.download.engine.hls

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches and parses m3u8 playlists via OkHttp.
 *
 * Handles both master (multi-variant) and media (segment) playlists.
 *
 * Strategy:
 * 1. Try fetching through the proxy (the original Video.videoUrl)
 *    - The proxy rewrites segment URLs to go through itself (handles CDN auth)
 *    - The response may not start with #EXTM3U — check for it anywhere in the body
 * 2. If the proxy doesn't return m3u8, fall back to fetching the real URL from CDN
 *    - Segment URLs will be CDN URLs (need video.headers for authentication)
 *
 * @param client OkHttp client (from NetworkHelper)
 * @param parser the m3u8 text parser
 */
class HlsPlaylistFetcher(
    private val client: OkHttpClient,
    private val parser: HlsPlaylistParser = HlsPlaylistParser(),
) {
    companion object {
        private const val TAG = "HlsPlaylistFetcher"
    }

    /**
     * Fetch [m3u8Url] and return a media playlist.
     *
     * @param m3u8Url the URL from Video.videoUrl (may be a localhost proxy URL)
     * @param headers HTTP headers from Video.headers (Referer, User-Agent, etc.)
     * @param qualityPreference user's preferred quality order
     * @return the resolved media playlist, or null on failure
     */
    suspend fun fetchMediaPlaylist(
        m3u8Url: String,
        headers: Headers,
        qualityPreference: List<String> = emptyList(),
    ): HlsPlaylist.Media? {
        // Strategy 1: Try fetching through the proxy.
        // The proxy rewrites segment URLs to go through itself (handles CDN auth).
        // The response may contain #EXTM3U but not at the start.
        Log.d(TAG, "fetchMediaPlaylist: trying proxy: $m3u8Url")
        val proxyPlaylist = fetchAndParseFlexible(m3u8Url, headers)
        if (proxyPlaylist != null) {
            Log.d(TAG, "fetchMediaPlaylist: ✓ got playlist through proxy")
            return resolveToMedia(proxyPlaylist, headers, qualityPreference)
        }

        // Strategy 2: Extract real URL from proxy and fetch directly from CDN.
        // Segment URLs will be CDN URLs — the engine will pass video.headers for auth.
        val realUrl = extractRealUrlFromProxy(m3u8Url)
        if (realUrl != null) {
            Log.d(TAG, "fetchMediaPlaylist: proxy didn't work → fetching real m3u8 from CDN: $realUrl")
            val cdnPlaylist = fetchAndParse(realUrl, headers)
            if (cdnPlaylist != null) {
                return resolveToMedia(cdnPlaylist, headers, qualityPreference)
            }
        }

        Log.w(TAG, "fetchMediaPlaylist: all strategies failed")
        return null
    }

    /**
     * Resolve a playlist (master or media) to a media playlist.
     */
    private suspend fun resolveToMedia(
        playlist: HlsPlaylist,
        headers: Headers,
        qualityPreference: List<String>,
    ): HlsPlaylist.Media? {
        return when (playlist) {
            is HlsPlaylist.Media -> playlist
            is HlsPlaylist.Master -> {
                val variant = pickVariant(playlist.variants, qualityPreference)
                    ?: playlist.variants.maxByOrNull { it.bandwidth }
                    ?: return null
                val variantUrl = HlsUrlResolver.resolve(playlist.baseUrl, variant.uri)
                Log.d(TAG, "master → variant ${variant.resolution ?: "?"} " +
                    "(${variant.bandwidth / 1000}kbps): $variantUrl")
                fetchAndParse(variantUrl, headers) as? HlsPlaylist.Media
            }
        }
    }

    /**
     * Fetch a URL and parse the response as an m3u8 playlist.
     * The response must start with #EXTM3U.
     */
    private suspend fun fetchAndParse(
        url: String,
        headers: Headers,
    ): HlsPlaylist? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).headers(headers).get().build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "fetch $url → HTTP ${resp.code}")
                    return@withContext null
                }
                val body = resp.body?.string() ?: return@withContext null
                if (!body.contains("#EXTM3U")) {
                    Log.d(TAG, "fetch $url → not an m3u8 (no #EXTM3U found)")
                    return@withContext null
                }
                // Extract from #EXTM3U onwards (in case there's a prefix)
                val m3u8Content = body.substring(body.indexOf("#EXTM3U"))
                parser.parse(m3u8Content, url, headers)
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "parse failed for $url: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "fetch $url failed: ${e.message}")
            null
        }
    }

    /**
     * Flexible fetch: tries the proxy URL, follows redirects, and looks for #EXTM3U
     * anywhere in the response body. Handles proxy responses that may have a prefix
     * or redirect URL before the actual m3u8 content.
     */
    private suspend fun fetchAndParseFlexible(
        url: String,
        headers: Headers,
    ): HlsPlaylist? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).headers(headers).get().build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.d(TAG, "flexible fetch $url → HTTP ${resp.code}")
                    return@withContext null
                }
                val body = resp.body?.string() ?: return@withContext null

                // Check if the response contains #EXTM3U anywhere
                val extm3uIndex = body.indexOf("#EXTM3U")
                if (extm3uIndex >= 0) {
                    // Extract from #EXTM3U onwards
                    val m3u8Content = body.substring(extm3uIndex)
                    Log.d(TAG, "flexible fetch: found #EXTM3U at offset $extm3uIndex, parsing")
                    // Use the proxy URL as the base URL (segment URLs are relative to it)
                    return@withContext parser.parse(m3u8Content, url, headers)
                }

                // The response doesn't contain #EXTM3U — log what we got for debugging
                Log.d(TAG, "flexible fetch $url → no #EXTM3U (response starts with: ${body.take(100)})")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.d(TAG, "flexible fetch $url failed: ${e.message}")
            null
        }
    }

    /**
     * Extract the real m3u8 URL from a proxy URL like:
     *   http://localhost:PORT/m3u8?url=https%3A%2F%2Fcdn.example.com%2Fvideo.m3u8
     */
    private fun extractRealUrlFromProxy(url: String): String? {
        if (!url.contains("localhost") && !url.contains("127.0.0.1")) return null
        val urlParam = android.net.Uri.parse(url).getQueryParameter("url") ?: return null
        Log.d(TAG, "extracted real URL from proxy: $urlParam")
        return urlParam
    }

    /**
     * Pick the variant whose resolution best matches the user's preference.
     */
    private fun pickVariant(
        variants: List<HlsPlaylist.Master.Variant>,
        preference: List<String>,
    ): HlsPlaylist.Master.Variant? {
        if (preference.isEmpty()) return null
        for (pref in preference) {
            val targetHeight = pref.removeSuffix("p").toIntOrNull() ?: continue
            val match = variants.firstOrNull { v ->
                v.resolution?.substringAfter("x")?.toIntOrNull() == targetHeight
            }
            if (match != null) return match
        }
        return null
    }
}
