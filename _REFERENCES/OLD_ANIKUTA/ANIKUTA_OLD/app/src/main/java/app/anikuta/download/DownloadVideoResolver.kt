package app.anikuta.download

import android.util.Log
import app.anikuta.download.DownloadPreferences
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import app.anikuta.source.AndroidAnimeSourceManager
import app.anikuta.ui.detail.VideoTitleParser
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Resolves a [Video] from the source for a download. Handles expired links by
 * re-calling the source's getHosterList/getVideoList and applying the user's
 * download priority preferences (quality, audio, server) to pick the best video.
 *
 * This is the download equivalent of DetailViewModel.resolveVideos() +
 * PlayerActivity.resolveVideoList() — but instead of showing a picker, it
 * automatically selects the best video based on DownloadPreferences.
 */
class DownloadVideoResolver(
    private val sourceManager: AnimeSourceManager,
    private val downloadPrefs: DownloadPreferences,
) {

    /**
     * Resolve a video for the given download. Re-fetches from the source
     * (handles expired links) and applies priority preferences.
     *
     * @param download the download to resolve a video for
     * @return the best matching Video, or null if resolution failed
     */
    suspend fun resolve(download: Download): Video? {
        val source = sourceManager.get(download.sourceId) as? AnimeCatalogueSource
        if (source == null) {
            Log.e(TAG, "Source not found: ${download.sourceId}")
            return null
        }

        // Build a minimal SEpisode for the source call
        val episode = eu.kanade.tachiyomi.animesource.model.SEpisodeImpl().apply {
            url = download.episodeUrl
            name = download.episodeName
            episode_number = download.episodeNumber
        }

        // Fetch all videos from the source (same pattern as DetailViewModel)
        val allVideos = withContext(Dispatchers.IO) {
            try {
                val hosters = source.getHosterList(episode)
                hosters.mapNotNull { hoster ->
                    hoster.videoList?.filter { it.videoUrl.isNotBlank() }
                }.flatten()
            } catch (e: Exception) {
                Log.d(TAG, "getHosterList failed, falling back to getVideoList: ${e.message}")
                try {
                    source.getVideoList(episode).filter { it.videoUrl.isNotBlank() }
                } catch (e2: Exception) {
                    Log.e(TAG, "getVideoList also failed: ${e2.message}")
                    emptyList()
                }
            }
        }

        if (allVideos.isEmpty()) {
            Log.e(TAG, "No videos resolved for ${download.episodeName}")
            return null
        }

        // Parse all videos and apply priority preferences
        val parsed = allVideos.map { VideoTitleParser.parse(it) }
        val bestVideo = pickBestVideo(parsed, allVideos)
        Log.d(TAG, "Resolved video: ${bestVideo.videoTitle} → ${bestVideo.videoUrl.take(60)}...")
        return bestVideo
    }

    /**
     * Pick the best video based on the user's download priority preferences.
     * Priority: quality → audio → server (or audio → quality → server,
     * depending on qualityVsAudioPriority setting).
     */
    private fun pickBestVideo(
        parsed: List<app.anikuta.ui.detail.ParsedVideo>,
        originals: List<Video>,
    ): Video {
        val qualityOrder = downloadPrefs.preferredQualityOrder().get()
        val audioOrder = downloadPrefs.preferredAudioOrder().get()
        val serverOrder = downloadPrefs.preferredServerOrder().get()
        val qualityFirst = downloadPrefs.qualityVsAudioPriority().get() ==
            PriorityMode.QUALITY_FIRST.value

        // Score each video: lower score = higher priority
        val scored = parsed.mapIndexed { index, pv ->
            val qualityScore = qualityOrder.indexOf("${pv.quality ?: 0}p").let {
                if (it < 0) qualityOrder.size else it
            }
            val audioScore = audioOrder.indexOf(pv.audio.name.lowercase()).let {
                if (it < 0) audioOrder.size else it
            }
            val serverScore = serverOrder.indexOf(pv.server).let {
                if (it < 0) serverOrder.size else it
            }
            val totalScore = if (qualityFirst) {
                qualityScore * 100 + audioScore * 10 + serverScore
            } else {
                audioScore * 100 + qualityScore * 10 + serverScore
            }
            Triple(totalScore, index, pv)
        }.sortedBy { it.first }

        val bestIndex = scored.first().second
        return originals[bestIndex]
    }

    companion object {
        private const val TAG = "DownloadVideoResolver"
    }
}
