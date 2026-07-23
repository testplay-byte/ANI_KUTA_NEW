package app.confused.anikuta.core.tracker

import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.player.WatchProgressStore

/**
 * Computed profile statistics for the My Profile page.
 *
 * When no tracker is linked, all distributions are derived from local data
 * (library anime + watch progress). When AniList is linked, richer stats come
 * from [TrackerUserStats].
 */
data class ProfileStats(
    val totalAnime: Int,
    val totalEpisodesWatched: Int,
    val totalWatchTimeMinutes: Int,
    val meanScore: Double?,
    val genreDistribution: Map<String, Int>,
    val formatDistribution: Map<String, Int>,
    val statusDistribution: Map<TrackStatus, Int>,
    val scoreDistribution: Map<Int, Int>,
    val countryDistribution: Map<String, Int>,
    val behindAnime: List<BehindAnime>,
    val recentlyWatched: List<WatchProgressStore.Progress>,
)

/** An anime the user is behind on (watched < released episodes). */
data class BehindAnime(
    val anime: Anime,
    val watchedEpisodes: Int,
    val totalReleasedEpisodes: Int,
)
