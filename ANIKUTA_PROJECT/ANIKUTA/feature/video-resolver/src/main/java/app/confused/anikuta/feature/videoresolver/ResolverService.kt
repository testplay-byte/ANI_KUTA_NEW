package app.confused.anikuta.feature.videoresolver

import android.util.Log
import eu.kanade.tachiyomi.animesource.AnimeSource
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
 * The new API is preferred; if it throws `IllegalStateException("Not used")`
 * (the default impl), falls back to the old API.
 *
 * Each source call is wrapped in a [withTimeoutOrNull] (10s) so a hanging
 * extension doesn't block the resolver indefinitely.
 *
 * Videos with blank `videoUrl` are filtered out (they can't be played).
 *
 * The final flat video list is grouped into the 3-tier hierarchy
 * (Server → Audio → Quality) by [VideoTitleParser.groupVideosByServer].
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

                val servers = VideoTitleParser.groupVideosByServer(validVideos)
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
            // Fetch videos from each hoster
            return hosters.flatMap { hoster ->
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

        // Fallback: old direct API
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
        private const val SOURCE_TIMEOUT_MS = 10_000L
    }
}

/** The result of [ResolverService.resolve]. */
sealed interface ResolverResult {
    /** Videos resolved successfully — [servers] is the 3-tier hierarchy. */
    data class Success(val servers: List<ResolverServer>) : ResolverResult
    /** The source returned no playable videos. */
    data object NoSources : ResolverResult
    /** The resolution failed (network error, timeout, etc.). */
    data class Error(val message: String) : ResolverResult
}
