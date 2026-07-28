package app.confused.anikuta.data.history

import app.confused.anikuta.core.common.model.History
import app.confused.anikuta.core.common.repository.HistoryRepository
import app.confused.anikuta.core.common.di.DispatcherProvider
import app.confused.anikuta.core.database.AnikutaDatabase
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow

class HistoryRepositoryImpl(
    private val database: AnikutaDatabase,
    private val dispatchers: DispatcherProvider,
) : HistoryRepository {

    override fun observeAll(): Flow<List<History>> =
        database.animehistoryQueries.selectAll(HistoryMapper::map)
            .asFlow()
            .mapToList(dispatchers.io)

    override fun observeByAnimeId(animeId: Long): Flow<List<History>> =
        database.animehistoryQueries.selectByAnimeId(animeId, HistoryMapper::map)
            .asFlow()
            .mapToList(dispatchers.io)

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
