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
 * uses those videos directly instead of calling `getVideoList(hoster)`.
 *
 * Uses [ResolverStrategyPicker] to auto-select between:
 * - [StructuredResolverStrategy] — 3-tier hierarchy using [VideoTitleParser]
 *   with hoster names as server names.
 * - [RawResolverStrategy] — flat list for unstructured extensions.
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

                val videoEntries = resolveVideoEntries(source, episode)

                val validEntries = videoEntries.filter { it.video.videoUrl.isNotBlank() }
                if (validEntries.isEmpty()) {
                    Log.i(TAG, "No valid videos from '${source.name}'")
                    return@withContext ResolverResult.NoSources
                }

                // Build the hoster name map (video index → hoster name)
                val hosterNames = mutableMapOf<Int, String>()
                validEntries.forEachIndexed { index, entry ->
                    if (entry.hosterName != null) {
                        hosterNames[index] = entry.hosterName
                    }
                }

                // Pick the best strategy based on video title quality + hoster name availability
                val strategy = ResolverStrategyPicker.pick(validEntries.map { it.video }, hasHosterNames = hosterNames.isNotEmpty())
                Log.i(TAG, "Using ${strategy::class.simpleName} for ${validEntries.size} videos, ${hosterNames.size} with hoster names")

                val servers = strategy.resolve(validEntries.map { it.video }, hosterNames)
                if (servers.isEmpty()) {
                    // Structured strategy returned empty (all unparseable) → fall back to raw
                    Log.i(TAG, "Structured strategy returned empty — falling back to RawResolverStrategy")
                    val rawServers = RawResolverStrategy.resolve(validEntries.map { it.video }, hosterNames)
                    if (rawServers.isEmpty()) {
                        ResolverResult.NoSources
                    } else {
                        Log.i(TAG, "Resolved ${rawServers.size} server(s) (raw fallback), ${validEntries.size} video(s)")
                        ResolverResult.Success(rawServers)
                    }
                } else {
                    Log.i(TAG, "Resolved ${servers.size} server(s), ${validEntries.size} video(s)")
                    ResolverResult.Success(servers)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Resolution failed from '${source.name}'", e)
                ResolverResult.Error(e.message ?: "Unknown error")
            }
        }

    /**
     * Resolves videos and their associated hoster names.
     * Returns a list of [VideoEntry] — each has a [Video] and optional hoster name.
     */
    private suspend fun resolveVideoEntries(source: AnimeSource, episode: SEpisode): List<VideoEntry> {
        // Try getHosterList first (ext-lib 16+)
        val hosters = try {
            withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                source.getHosterList(episode)
            } ?: emptyList()
        } catch (e: IllegalStateException) {
            Log.d(TAG, "Source '${source.name}' doesn't support getHosterList, falling back")
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "getHosterList failed for '${source.name}': ${e.message}")
            emptyList()
        }

        if (hosters.isNotEmpty()) {
            Log.i(TAG, "Got ${hosters.size} hosters from '${source.name}'")
            val entries = mutableListOf<VideoEntry>()
            for (hoster in hosters) {
                // Check hoster.videoList FIRST (non-lazy hosters like AnikotoS)
                val hosterVideos = hoster.videoList
                if (hosterVideos != null && hosterVideos.isNotEmpty()) {
                    Log.d(TAG, "Hoster '${hoster.hosterName}' has ${hosterVideos.size} pre-loaded videos")
                    for (video in hosterVideos) {
                        entries.add(VideoEntry(video, hoster.hosterName))
                    }
                } else {
                    // Lazy hoster — resolve via getVideoList(hoster)
                    Log.d(TAG, "Hoster '${hoster.hosterName}' is lazy — calling getVideoList(hoster)")
                    try {
                        val resolved = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                            source.getVideoList(hoster)
                        } ?: emptyList()
                        for (video in resolved) {
                            entries.add(VideoEntry(video, hoster.hosterName))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "getVideoList(hoster) failed for '${hoster.hosterName}': ${e.message}")
                    }
                }
            }
            return entries
        }

        // Fallback: old direct API (ext-lib < 16)
        Log.d(TAG, "Falling back to getVideoList(episode) for '${source.name}'")
        return try {
            val videos = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                source.getVideoList(episode)
            } ?: emptyList()
            videos.map { VideoEntry(it, null) }
        } catch (e: Exception) {
            Log.w(TAG, "getVideoList(episode) failed for '${source.name}': ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "AnikutaResolver"
        private const val SOURCE_TIMEOUT_MS = 30_000L
    }
}

/** A video with its associated hoster name (if from the hoster-based API). */
private data class VideoEntry(
    val video: Video,
    val hosterName: String?,
)

/** The result of [ResolverService.resolve]. */
sealed interface ResolverResult {
    data class Success(val servers: List<ResolverServer>) : ResolverResult
    data object NoSources : ResolverResult
    data class Error(val message: String) : ResolverResult
}
