package app.confused.anikuta.core.tracker

import android.util.Log
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.AnimeStatus
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.player.WatchProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Computes [ProfileStats] from local data (WatchProgressStore + AnimeRepository)
 * and optionally enriches with AniList stats when a tracker is linked.
 *
 * The Profile page works in two modes (ADR-013):
 * 1. **Local mode (no AniList linked):** stats derived from local data only.
 * 2. **AniList mode (linked):** richer stats from [TrackerManager.anilist].
 *
 * Exposes a [Flow]<[ProfileStats]> that re-computes when library or progress
 * changes.
 */
class StatsCalculator(
    private val watchProgressStore: WatchProgressStore,
    private val animeRepository: AnimeRepository,
    private val trackerManager: TrackerManager,
) {
    /**
     * Flow of profile stats. Recomputes when:
     * - The library changes (anime added/removed/updated)
     * - Watch progress changes
     */
    fun observeStats(): Flow<ProfileStats> =
        combine(
            animeRepository.observeFavorites(),
            watchProgressStore.changes,
        ) { libraryAnime, progressMap ->
            computeLocalStats(libraryAnime, progressMap)
        }.flowOn(Dispatchers.IO)

    /** Fetch enriched AniList stats (if linked). Returns null if not linked. */
    suspend fun fetchAniListStats(): TrackerUserStats? = withContext(Dispatchers.IO) {
        if (!trackerManager.anilist.isLoggedIn) return@withContext null
        try {
            trackerManager.anilist.fetchUserStats()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch AniList stats", e)
            null
        }
    }

    /** Fetch the AniList username (if linked). Returns null if not linked. */
    fun observeAniListUsername(): Flow<String?> = trackerManager.anilist.username

    /** Fetch the AniList avatar URL (if linked). Returns null if not linked. */
    fun observeAniListAvatar(): Flow<String?> = trackerManager.anilist.avatar

    /** Whether AniList is currently linked. */
    fun isAniListLinked(): Boolean = trackerManager.anilist.isLoggedIn

    /**
     * Compute local stats from library anime + watch progress.
     */
    private fun computeLocalStats(
        libraryAnime: List<Anime>,
        progressMap: Map<String, WatchProgressStore.Progress>,
    ): ProfileStats {
        val totalAnime = libraryAnime.size

        // Total episodes watched: count unique episodes in progress map.
        val totalEpisodesWatched = progressMap.size

        // Total watch time: sum of positionSeconds across all progress entries.
        val totalWatchSeconds = progressMap.values.sumOf { it.positionSeconds }
        val totalWatchMinutes = totalWatchSeconds / 60

        // Genre distribution: count genres across library anime.
        val genreDist = mutableMapOf<String, Int>()
        for (anime in libraryAnime) {
            for (genre in anime.genre) {
                genreDist[genre] = (genreDist[genre] ?: 0) + 1
            }
        }

        // Format distribution: AniList format is not stored locally; derive from
        // what we can. Local anime don't have format info, so this is empty
        // in local mode. When AniList is linked, the enriched stats provide it.
        val formatDist = emptyMap<String, Int>()

        // Status distribution: shows the anime's release status (Finished,
        // Releasing, Not Yet Released, Cancelled, On Hiatus) — NOT tracker
        // status. Based on AnimeStatus constants from the Anime model.
        val statusDist = mutableMapOf<String, Int>()
        for (anime in libraryAnime) {
            val status = when (anime.status) {
                AnimeStatus.COMPLETED -> "Finished"
                AnimeStatus.ONGOING -> "Releasing"
                AnimeStatus.CANCELLED -> "Cancelled"
                AnimeStatus.ON_HIATUS -> "On Hiatus"
                AnimeStatus.LICENSED -> "Licensed"
                AnimeStatus.PUBLISHING_FINISHED -> "Publishing Finished"
                else -> "Not Yet Released"
            }
            statusDist[status] = (statusDist[status] ?: 0) + 1
        }

        // Score distribution: from local anime scores (AniList average score).
        val scoreDist = mutableMapOf<Int, Int>()
        for (anime in libraryAnime) {
            val score = anime.score?.toInt() ?: continue
            if (score > 0) {
                val bucket = (score / 10).coerceIn(1, 10)
                scoreDist[bucket] = (scoreDist[bucket] ?: 0) + 1
            }
        }

        // Country distribution: not stored locally; empty in local mode.
        val countryDist = emptyMap<String, Int>()

        // Mean score: average of library anime scores.
        val scored = libraryAnime.mapNotNull { it.score }.filter { it > 0 }
        val meanScore = if (scored.isNotEmpty()) scored.average() else null

        // Behind anime: library anime where watched < released episodes.
        val behindAnime = computeBehindAnime(libraryAnime, progressMap)

        // Recently watched: last 3 by updatedAt.
        val recentlyWatched = progressMap.values
            .sortedByDescending { it.updatedAt }
            .take(3)

        return ProfileStats(
            totalAnime = totalAnime,
            totalEpisodesWatched = totalEpisodesWatched,
            totalWatchTimeMinutes = totalWatchMinutes,
            meanScore = meanScore,
            genreDistribution = genreDist,
            formatDistribution = formatDist,
            statusDistribution = statusDist,
            scoreDistribution = scoreDist,
            countryDistribution = countryDist,
            behindAnime = behindAnime,
            recentlyWatched = recentlyWatched,
            libraryAnime = libraryAnime,
        )
    }

    /** Compute the list of anime the user is behind on. */
    private fun computeBehindAnime(
        libraryAnime: List<Anime>,
        progressMap: Map<String, WatchProgressStore.Progress>,
    ): List<BehindAnime> {
        // Count watched episodes per anilistId from the progress map.
        val watchedByAnilistId = mutableMapOf<Int, Int>()
        for ((key, _) in progressMap) {
            val anilistIdStr = key.substringBefore(":")
            val anilistId = anilistIdStr.toIntOrNull() ?: continue
            watchedByAnilistId[anilistId] = (watchedByAnilistId[anilistId] ?: 0) + 1
        }

        return libraryAnime.mapNotNull { anime ->
            val anilistId = anime.anilistId ?: return@mapNotNull null
            val watched = watchedByAnilistId[anilistId] ?: 0
            val released = anime.releasedEpisodes ?: return@mapNotNull null
            if (released > 0 && watched < released) {
                BehindAnime(anime = anime, watchedEpisodes = watched, totalReleasedEpisodes = released)
            } else {
                null
            }
        }.sortedByDescending { it.totalReleasedEpisodes - it.watchedEpisodes }
    }

    companion object {
        private const val TAG = "AnikutaStatsCalc"
    }
}
