package app.confused.anikuta.core.tracker

/**
 * Domain model for a tracker binding (a row in the `animetrack` SQLDelight table).
 *
 * Links a local anime to a remote tracker entry. Used by [TrackRepository]
 * and [TrackSyncManager].
 *
 * @property id local DB row id (_id)
 * @property animeId local anime id (references animes._id)
 * @property trackerId which tracker (1=MAL, 2=AniList)
 * @property remoteId the tracker's media id (AniList mediaId / MAL anime id)
 * @property remoteUrl browser URL to the media page
 * @property lastSeen last episode seen (as a Double for fractional episodes)
 * @property score user's score (0-100 for AniList, 0-10 for MAL)
 * @property status tracker-specific status constant
 * @property totalEpisodes total episodes from the remote source
 * @property displayScore formatted score string for display
 */
data class AnimeTrack(
    val id: Long,
    val animeId: Long,
    val trackerId: Long,
    val remoteId: Long,
    val remoteUrl: String?,
    val lastSeen: Long,
    val score: Double,
    val status: Long,
    val totalEpisodes: Long,
    val displayScore: String?,
)
