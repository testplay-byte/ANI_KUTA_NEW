package app.anikuta.domain.history.anime.interactor

import app.anikuta.domain.history.anime.model.AnimeHistoryUpdate
import app.anikuta.domain.history.anime.repository.AnimeHistoryRepository

class UpsertAnimeHistory(
    private val historyRepository: AnimeHistoryRepository,
) {

    suspend fun await(historyUpdate: AnimeHistoryUpdate) {
        historyRepository.upsertAnimeHistory(historyUpdate)
    }
}
