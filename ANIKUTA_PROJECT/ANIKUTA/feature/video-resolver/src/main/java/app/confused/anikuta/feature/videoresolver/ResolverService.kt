package app.confused.anikuta.feature.videoresolver

import android.util.Log
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resolves the video list for an episode from a matched [AnimeSource].
 *
 * Handles both the old `getVideoList(episode)` API (ext-lib < 16) and the new
 * `getHosterList(episode)` + `getVideoList(hoster)` API (ext-lib 16+).
 *
 * **Key fix for structured extensions (like AnikotoS):**
 * When a hoster's [Hoster.videoList] is already populated (non-null, non-empty),
 * uses those videos directly instead of calling `getVideoList(hoster)` (which
 * would do a network request to an empty URL and fail).
 *
 * Only calls `getVideoList(hoster)` for **lazy** hosters (where [Hoster.videoList]
 * is null or [Hoster.lazy] is true).
 *
 * Uses [ResolverStrategyPicker] to automatically choose between the structured
 * 3-tier hierarchy ([StructuredResolverStrategy]) and the flat list
 * ([RawResolverStrategy]) based on the video title quality.
 */
class ResolverService {

    /**
     * Resolves videos from [source] for [episode].
     *
     * @return [ResolverResult.Success] with grouped servers, [ResolverResult.NoSources]
     *   if no playable videos were found, or [ResolverResult.Error] on failure.
     */
    suspend fun resolve(source: AnimeSource, episode: SEpisode): ResolverResult =
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Resolving videos from '${source.name}' for episode '${episode.name}'")

                val videos = resolveVideos(source, episode)

                val validVideos = videos.filter { it.videoUrl.isNotBlank() }
                if (validVideos.isEmpty()) {
                    Log.i(TAG, "No valid videos from '${source.name}'")
                    return@withContext ResolverResult.NoSources
                }

                // Pick the best strategy based on video title quality
                val strategy = ResolverStrategyPicker.pick(validVideos)
                Log.i(TAG, "Using ${strategy::class.simpleName} for ${validVideos.size} videos")

                val servers = strategy.resolve(validVideos)
                if (servers.isEmpty()) {
                    ResolverResult.NoSources
                } else {
                    Log.i(TAG, "Resolved ${servers.size} server(s), ${validVideos.size} video(s)")
                    ResolverResult.Success(servers)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Resolution failed from '${source.name}'", e)
                ResolverResult.Error(e.message ?: "Unknown error")
            }
        }

    /**
     * Tries the new hoster-based API first; falls back to the old direct API.
     *
     * For non-lazy hosters (videoList already populated), uses the videos directly.
     * For lazy hosters (videoList is null), calls `getVideoList(hoster)`.
     */
    private suspend fun resolveVideos(source: AnimeSource, episode: SEpisode): List<Video> {
        // Try getHosterList first (ext-lib 16+)
        val hosters = try {
            withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                source.getHosterList(episode)
            } ?: emptyList()
        } catch (e: IllegalStateException) {
            // "Not used" — the source doesn't support the hoster API
            Log.d(TAG, "Source '${source.name}' doesn't support getHosterList, falling back")
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "getHosterList failed for '${source.name}': ${e.message}")
            emptyList()
        }

        if (hosters.isNotEmpty()) {
            Log.i(TAG, "Got ${hosters.size} hosters from '${source.name}'")
            return hosters.flatMap { hoster ->
                // ★ Key fix: check hoster.videoList FIRST.
                // Non-lazy hosters (like AnikotoS) already have videoList populated.
                // Only call getVideoList(hoster) for lazy hosters (videoList is null).
                val hosterVideos = hoster.videoList
                if (hosterVideos != null && hosterVideos.isNotEmpty()) {
                    Log.d(TAG, "Hoster '${hoster.hosterName}' has ${hosterVideos.size} pre-loaded videos")
                    return@flatMap hosterVideos
                }

                // Lazy hoster — resolve it via getVideoList(hoster)
                Log.d(TAG, "Hoster '${hoster.hosterName}' is lazy — calling getVideoList(hoster)")
                try {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        source.getVideoList(hoster)
                    } ?: emptyList()
                } catch (e: Exception) {
                    Log.w(TAG, "getVideoList(hoster) failed for '${hoster.hosterName}': ${e.message}")
                    emptyList()
                }
            }
        }

        // Fallback: old direct API (ext-lib < 16)
        Log.d(TAG, "Falling back to getVideoList(episode) for '${source.name}'")
        return try {
            withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                source.getVideoList(episode)
            } ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "getVideoList(episode) failed for '${source.name}': ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "AnikutaResolver"
        private const val SOURCE_TIMEOUT_MS = 30_000L // 30s — extensions like AnikotoS need time for parallel resolution
    }
}

/** The result of [ResolverService.resolve]. */
sealed interface ResolverResult {
    /** Videos resolved successfully — [servers] is the resolver hierarchy. */
    data class Success(val servers: List<ResolverServer>) : ResolverResult
    /** The source returned no playable videos. */
    data object NoSources : ResolverResult
    /** The resolution failed (network error, timeout, etc.). */
    data class Error(val message: String) : ResolverResult
}
