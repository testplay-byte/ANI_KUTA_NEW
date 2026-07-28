package app.anikuta.player

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.SEpisode
import app.anikuta.download.DownloadManager
import app.anikuta.notification.NotificationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase N-6 (Watch-flow auto-download) — Pre-downloads the next episode while
 * the user watches the current one.
 *
 * Trigger: when the user starts watching episode N that is already downloaded.
 * Logic:
 *   1. Is episode N downloaded? (the user is watching a download, not streaming)
 *   2. Is episode N+1 in the episode list?
 *   3. Is episode N+1 already downloaded or queued?
 *   4. Is watch-flow auto-download enabled? (global setting)
 *   5. If all yes → enqueue episode N+1 for download.
 *
 * This is a SEPARATE system from the new-release auto-download (Phase N-5).
 * It's global-only (no per-anime settings, per the user's instruction).
 * Lives in the player package because it's triggered by playback.
 *
 * Called from PlayerActivity with ONE line — minimal intrusion into the
 * god-object. The class does all the work.
 */
object WatchFlowAutoDownload {

    private const val TAG = "WatchFlowAutoDownload"

    /**
     * Check if the next episode should be pre-downloaded, and enqueue it if so.
     *
     * Call this from PlayerActivity when playback of an episode starts.
     *
     * @param anilistId The AniList anime ID.
     * @param animeTitle The anime title (for download matching).
     * @param sourceId The extension source ID.
     * @param sourceName The extension source name.
     * @param currentEpisodeIndex The index of the episode being played.
     * @param episodes The full episode list.
     */
    fun maybePreDownloadNext(
        anilistId: Int,
        animeTitle: String,
        sourceId: Long,
        sourceName: String,
        currentEpisodeIndex: Int,
        episodes: List<SEpisode>,
    ) {
        try {
            val prefs: NotificationPreferences = Injekt.get()
            val downloadManager: DownloadManager = Injekt.get()

            // 1. Is watch-flow auto-download enabled?
            if (!prefs.watchFlowAutoDownloadEnabled().get()) {
                return
            }

            // 2. Is the current episode downloaded? (user is watching a download)
            val currentEpisode = episodes.getOrNull(currentEpisodeIndex) ?: return
            val currentIsDownloaded = downloadManager.isEpisodeDownloaded(
                currentEpisode.name,
                animeTitle,
                sourceName,
            )
            if (!currentIsDownloaded) {
                Log.d(TAG, "maybePreDownloadNext: current episode not downloaded (streaming) — skipping")
                return
            }

            // 3. Find the next episode (index + 1)
            val nextEpisode = episodes.getOrNull(currentEpisodeIndex + 1) ?: run {
                Log.d(TAG, "maybePreDownloadNext: no next episode (end of list) — skipping")
                return
            }

            // 4. Is the next episode already downloaded?
            val nextIsDownloaded = downloadManager.isEpisodeDownloaded(
                nextEpisode.name,
                animeTitle,
                sourceName,
            )
            if (nextIsDownloaded) {
                Log.d(TAG, "maybePreDownloadNext: next episode already downloaded — skipping")
                return
            }

            // 5. Is the next episode already in the download queue?
            val nextInQueue = downloadManager.queue.value.any { it.episodeUrl == nextEpisode.url }
            if (nextInQueue) {
                Log.d(TAG, "maybePreDownloadNext: next episode already in queue — skipping")
                return
            }

            // 6. All conditions met — enqueue the download
            val downloadId = downloadManager.enqueueDownload(
                anilistId = anilistId,
                sourceId = sourceId,
                sourceName = sourceName,
                animeTitle = animeTitle,
                episode = nextEpisode,
            )
            Log.d(TAG, "maybePreDownloadNext: ✓ pre-downloaded next episode '${nextEpisode.name}' for '$animeTitle' (dlId=$downloadId)")
        } catch (e: Exception) {
            Log.e(TAG, "maybePreDownloadNext: failed", e)
        }
    }
}
