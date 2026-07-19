package app.confused.anikuta.data.history

import app.confused.anikuta.core.common.model.History
import app.confused.anikuta.core.common.repository.HistoryRepository
import app.confused.anikuta.core.database.AnikutaDatabase
import app.cash.sqldelight.coroutines.extensions.asFlow
import app.cash.sqldelight.coroutines.extensions.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class HistoryRepositoryImpl(
    private val database: AnikutaDatabase,
) : HistoryRepository {

    override fun observeAll(): Flow<List<History>> =
        database.animehistoryQueries.selectAll(HistoryMapper::map)
            .asFlow()
            .mapToList(Dispatchers.IO)

    override fun observeByAnimeId(animeId: Long): Flow<List<History>> =
        database.animehistoryQueries.selectByAnimeId(animeId, HistoryMapper::map)
            .asFlow()
            .mapToList(Dispatchers.IO)

    override suspend fun upsert(animeId: Long, episodeId: Long, seenAt: Long, lastSecondSeen: Long) {
        database.animehistoryQueries.upsert(animeId, episodeId, seenAt, lastSecondSeen)
    }

    override suspend fun delete(id: Long) {
        database.animehistoryQueries.delete(id)
    }

    override suspend fun deleteByAnimeId(animeId: Long) {
        database.animehistoryQueries.deleteByAnimeId(animeId)
    }
}
