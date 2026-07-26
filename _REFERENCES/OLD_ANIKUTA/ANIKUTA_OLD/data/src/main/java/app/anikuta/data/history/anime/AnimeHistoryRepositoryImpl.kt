package app.anikuta.data.history.anime

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import app.anikuta.core.util.system.logcat
import app.anikuta.data.handlers.anime.AnimeDatabaseHandler
import app.anikuta.domain.history.anime.model.AnimeHistory
import app.anikuta.domain.history.anime.model.AnimeHistoryUpdate
import app.anikuta.domain.history.anime.model.AnimeHistoryWithRelations
import app.anikuta.domain.history.anime.repository.AnimeHistoryRepository

class AnimeHistoryRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : AnimeHistoryRepository {

    override fun getAnimeHistory(query: String): Flow<List<AnimeHistoryWithRelations>> {
        return handler.subscribeToList {
            animehistoryViewQueries.animehistory(query, AnimeHistoryMapper::mapAnimeHistoryWithRelations)
        }
    }

    override suspend fun getLastAnimeHistory(): AnimeHistoryWithRelations? {
        return handler.awaitOneOrNull {
            animehistoryViewQueries.getLatestAnimeHistory(AnimeHistoryMapper::mapAnimeHistoryWithRelations)
        }
    }

    override suspend fun getHistoryByAnimeId(animeId: Long): List<AnimeHistory> {
        return handler.awaitList {
            animehistoryQueries.getHistoryByAnimeId(
                animeId,
                AnimeHistoryMapper::mapAnimeHistory,
            )
        }
    }

    override suspend fun resetAnimeHistory(historyId: Long) {
        try {
            handler.await { animehistoryQueries.resetAnimeHistoryById(historyId) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun resetHistoryByAnimeId(animeId: Long) {
        try {
            handler.await { animehistoryQueries.resetHistoryByAnimeId(animeId) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllAnimeHistory(): Boolean {
        return try {
            handler.await { animehistoryQueries.removeAllHistory() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }

    override suspend fun upsertAnimeHistory(historyUpdate: AnimeHistoryUpdate) {
        try {
            handler.await {
                animehistoryQueries.upsert(
                    historyUpdate.episodeId,
                    historyUpdate.seenAt,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }
}
