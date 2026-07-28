package app.confused.anikuta.core.tracker

import android.util.Log
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.player.WatchProgressStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Auto-syncs watch progress to linked trackers.
 *
 * Listens to [WatchProgressStore.changes] and, when progress is saved for an
 * anime that has a tracker binding, calls [Tracker.updateProgress] after a
 * debounce delay (~10s) to avoid spamming the API.
 *
 * All work runs on `Dispatchers.IO`.
 */
class TrackSyncManager(
    private val watchProgressStore: WatchProgressStore,
    private val trackRepository: TrackRepository,
    private val trackerManager: TrackerManager,
    private val animeRepository: AnimeRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private var debounceJob: Job? = null

    /** Start listening to watch progress changes. Call once at app startup. */
    fun start() {
        watchProgressStore.changes
            .onEach { progressMap ->
                // Debounce: cancel any pending sync and wait 10s before syncing.
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(DEBOUNCE_MS)
                    syncPendingProgress(progressMap)
                }
            }
            .launchIn(scope)
        Log.i(TAG, "TrackSyncManager started — watching progress changes")
    }

    /** Sync the most recent progress entries to their trackers. */
    private suspend fun syncPendingProgress(progressMap: Map<String, WatchProgressStore.Progress>) {
        if (progressMap.isEmpty()) return
        syncMutex.withLock {
            // Group progress entries by anilistId to find the latest episode per anime.
            val latestByAnime = progressMap.values
                .filter { it.episodeNumber >= 0 }
                .groupBy { extractAnilistId(progressMap, it) }
                .mapValues { (_, entries) -> entries.maxByOrNull { it.updatedAt } }

            for ((anilistId, progress) in latestByAnime) {
                if (anilistId <= 0) continue
                val p = progress ?: continue
                try {
                    syncAnimeProgress(anilistId, p)
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed for anilistId=$anilistId", e)
                }
            }
        }
    }

    /** Extract the AniList ID from a progress key (format: "$anilistId:$episodeUrl"). */
    private fun extractAnilistId(
        progressMap: Map<String, WatchProgressStore.Progress>,
        progress: WatchProgressStore.Progress,
    ): Int {
        // Find the key that maps to this progress object.
        val key = progressMap.entries.find { it.value === progress }?.key ?: return -1
        val anilistIdStr = key.substringBefore(":")
        return anilistIdStr.toIntOrNull() ?: -1
    }

    /** Sync a single anime's progress to all its linked trackers. */
    private suspend fun syncAnimeProgress(anilistId: Int, progress: WatchProgressStore.Progress) {
        val anime = animeRepository.getByAnilistId(anilistId) ?: return
        val tracks = trackRepository.getTracks(anime.id)
        if (tracks.isEmpty()) return

        val episodeNumber = progress.episodeNumber.toInt().coerceAtLeast(1)
        val status = computeStatus(episodeNumber, anime.totalEpisodes)

        for (track in tracks) {
            val tracker = trackerManager.getTracker(track.trackerId.toInt()) ?: continue
            if (!tracker.isLoggedIn) continue
            Log.d(TAG, "Syncing: ${anime.title} ep $episodeNumber → ${tracker.name}")
            tracker.updateProgress(
                remoteAnimeId = track.remoteId.toInt(),
                episodeNumber = episodeNumber,
                status = status,
            )
            trackRepository.updateLastSeen(track.id, episodeNumber.toLong())
        }
    }

    /** Determine the track status based on progress vs total episodes. */
    private fun computeStatus(episodeNumber: Int, totalEpisodes: Int?): TrackStatus {
        if (totalEpisodes != null && totalEpisodes > 0 && episodeNumber >= totalEpisodes) {
            return TrackStatus.COMPLETED
        }
        return TrackStatus.WATCHING
    }

    companion object {
        private const val TAG = "AnikutaTrackSync"
        private const val DEBOUNCE_MS = 10_000L // 10 seconds
    }
}
