package app.confused.anikuta.core.common.repository

import app.confused.anikuta.core.common.model.Track
import kotlinx.coroutines.flow.Flow

/** Repository interface for tracker-binding data access. */
interface TrackRepository {

    fun observeByAnimeId(animeId: Long): Flow<List<Track>>

    suspend fun getByAnimeId(animeId: Long): List<Track>

    suspend fun upsert(track: Track): Long

    suspend fun delete(id: Long)
}
