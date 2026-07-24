package app.confused.anikuta.core.tracker

import app.confused.anikuta.core.database.AnikutaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for the `animetrack` SQLDelight table.
 *
 * CRUD operations for tracker bindings (which anime is linked to which tracker).
 * All DB access runs on `Dispatchers.IO`.
 */
class TrackRepository(
    private val database: AnikutaDatabase,
) {
    /** Get all tracker bindings for a local anime. */
    suspend fun getTracks(animeId: Long): List<AnimeTrack> = withContext(Dispatchers.IO) {
        database.animetrackQueries.selectByAnimeId(animeId, ::mapTrack).executeAsList()
    }

    /** Get a specific tracker binding for an anime. */
    suspend fun getTrack(animeId: Long, trackerId: Int): AnimeTrack? = withContext(Dispatchers.IO) {
        database.animetrackQueries.selectByAnimeIdAndTrackerId(
            animeId = animeId,
            trackerId = trackerId.toLong(),
            mapper = ::mapTrack,
        ).executeAsOneOrNull()
    }

    /** Get all tracker bindings (for backup/debug). */
    suspend fun getAllTracks(): List<AnimeTrack> = withContext(Dispatchers.IO) {
        database.animetrackQueries.selectAllTracks(::mapTrack).executeAsList()
    }

    /** Bind an anime to a tracker (insert or update). */
    suspend fun bind(
        animeId: Long,
        trackerId: Int,
        remoteId: Int,
        remoteUrl: String? = null,
        lastSeen: Long = 0,
        score: Double = 0.0,
        status: Long = 0,
        totalEpisodes: Long = 0,
        displayScore: String? = null,
    ) = withContext(Dispatchers.IO) {
        database.animetrackQueries.upsert(
            animeId = animeId,
            trackerId = trackerId.toLong(),
            remoteId = remoteId.toLong(),
            remoteUrl = remoteUrl,
            lastSeen = lastSeen,
            score = score,
            status = status,
            totalEpisodes = totalEpisodes,
            displayScore = displayScore,
        )
    }

    /** Remove a tracker binding for an anime. */
    suspend fun unbind(animeId: Long, trackerId: Int) = withContext(Dispatchers.IO) {
        database.animetrackQueries.deleteByAnimeIdAndTrackerId(
            animeId = animeId,
            trackerId = trackerId.toLong(),
        )
    }

    /** Update the last-seen episode for a binding (called by [TrackSyncManager]). */
    suspend fun updateLastSeen(id: Long, lastSeen: Long) = withContext(Dispatchers.IO) {
        database.animetrackQueries.updateLastSeen(
            lastSeen = lastSeen,
            id = id,
        )
    }

    /** Maps a SQLDelight row to the [AnimeTrack] domain model. */
    @Suppress("UNUSED_PARAMETER")
    private fun mapTrack(
        id: Long,
        anime_id: Long,
        tracker_id: Long,
        remote_id: Long,
        remote_url: String?,
        last_seen: Long,
        score: Double,
        status: Long,
        total_episodes: Long,
        display_score: String?,
    ): AnimeTrack = AnimeTrack(
        id = id,
        animeId = anime_id,
        trackerId = tracker_id,
        remoteId = remote_id,
        remoteUrl = remote_url,
        lastSeen = last_seen,
        score = score,
        status = status,
        totalEpisodes = total_episodes,
        displayScore = display_score,
    )
}
