package app.anikuta.notification

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import app.anikuta.ui.detail.AudioVersion
import app.anikuta.ui.detail.VideoTitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Phase N-2 (Detection) — Resolves videos for a single episode to detect
 * sub/dub availability.
 *
 * This is the same logic as DetailViewModel.resolveVideos(), but stripped down
 * to just the sub/dub detection part (no UI, no grouping, no caching to
 * SubDubStore — the caller handles that).
 *
 * Timeout: 30 seconds. If the extension is slow, we give up and return
 * AudioVersion.ANY (the tracker falls back to "notify as ANY").
 */
class SubDubResolver {

    companion object {
        private const val TAG = "SubDubResolver"
        private const val RESOLVE_TIMEOUT_MS = 30_000L
    }

    /**
     * Result of resolving videos for an episode.
     *
     * @param audioVersions The set of AudioVersions found (SUB, DUB, HSUB, ANY).
     * @param hasSub True if SUB or HSUB is in the set.
     * @param hasDub True if DUB is in the set.
     * @param videoCount Total number of videos resolved (for counting).
     * @param timedOut True if the resolution timed out (treat as ANY).
     */
    data class ResolveResult(
        val audioVersions: Set<AudioVersion>,
        val hasSub: Boolean,
        val hasDub: Boolean,
        val videoCount: Int,
        val timedOut: Boolean,
    )

    /**
     * Resolve videos for a single episode and detect sub/dub.
     *
     * @param source The extension source.
     * @param episode The episode to resolve.
     * @return The resolve result, or null if resolution failed entirely.
     */
    suspend fun resolve(source: AnimeCatalogueSource, episode: SEpisode): ResolveResult? {
        return withContext(Dispatchers.IO) {
            try {
                val videos = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                    // Try getHosterList first (same as DetailViewModel), fall back to getVideoList
                    try {
                        val hosters = source.getHosterList(episode)
                        hosters.mapNotNull { hoster ->
                            hoster.videoList?.filter { it.videoUrl.isNotBlank() }
                        }.flatten()
                    } catch (e: Exception) {
                        Log.d(TAG, "getHosterList failed (${e.message}), falling back to getVideoList")
                        source.getVideoList(episode).filter { it.videoUrl.isNotBlank() }
                    }
                } ?: run {
                    Log.w(TAG, "Video resolution timed out after ${RESOLVE_TIMEOUT_MS}ms for ${episode.name}")
                    return@withContext ResolveResult(
                        audioVersions = setOf(AudioVersion.ANY),
                        hasSub = false,
                        hasDub = false,
                        videoCount = 0,
                        timedOut = true,
                    )
                }

                // Parse audio versions from video titles
                val audioVersions = videos
                    .map { VideoTitleParser.parse(it).audio }
                    .toSet()

                val hasSub = audioVersions.any { it == AudioVersion.SUB || it == AudioVersion.HSUB }
                val hasDub = audioVersions.any { it == AudioVersion.DUB }

                Log.d(TAG, "Resolved ${videos.size} videos for '${episode.name}': sub=$hasSub dub=$hasDub versions=$audioVersions")

                ResolveResult(
                    audioVersions = audioVersions,
                    hasSub = hasSub,
                    hasDub = hasDub,
                    videoCount = videos.size,
                    timedOut = false,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve videos for '${episode.name}'", e)
                null
            }
        }
    }
}
