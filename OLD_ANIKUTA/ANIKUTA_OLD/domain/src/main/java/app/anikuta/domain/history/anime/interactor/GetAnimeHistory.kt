package app.anikuta.domain.history.anime.interactor

import kotlinx.coroutines.flow.Flow
import app.anikuta.domain.history.anime.model.AnimeHistory
import app.anikuta.domain.history.anime.model.AnimeHistoryWithRelations
import app.anikuta.domain.history.anime.repository.AnimeHistoryRepository

class GetAnimeHistory(
    private val repository: AnimeHistoryRepository,
) {

    suspend fun await(animeId: Long): List<AnimeHistory> {
        return repository.getHistoryByAnimeId(animeId)
    }

    fun subscribe(query: String): Flow<List<AnimeHistoryWithRelations>> {
        return repository.getAnimeHistory(query)
    }
}
