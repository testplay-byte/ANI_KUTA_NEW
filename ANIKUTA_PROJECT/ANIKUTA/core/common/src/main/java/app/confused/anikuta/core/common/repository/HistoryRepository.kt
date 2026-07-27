package app.confused.anikuta.core.common.repository

import app.confused.anikuta.core.common.model.History
import kotlinx.coroutines.flow.Flow

/** Repository interface for watch-history data access. */
interface HistoryRepository {

    fun observeAll(): Flow<List<History>>

    fun observeByAnimeId(animeId: Long): Flow<List<History>>

    suspend fun upsert(animeId: Long, episodeId: Long, seenAt: Long, lastSecondSeen: Long)

    suspend fun delete(id: Long)

    suspend fun deleteByAnimeId(animeId: Long)
}
