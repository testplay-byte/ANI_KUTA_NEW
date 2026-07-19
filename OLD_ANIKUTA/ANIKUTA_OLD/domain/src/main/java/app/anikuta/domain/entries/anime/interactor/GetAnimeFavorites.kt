package app.anikuta.domain.entries.anime.interactor

import kotlinx.coroutines.flow.Flow
import app.anikuta.domain.entries.anime.model.Anime
import app.anikuta.domain.entries.anime.repository.AnimeRepository

class GetAnimeFavorites(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(): List<Anime> {
        return animeRepository.getAnimeFavorites()
    }

    fun subscribe(sourceId: Long): Flow<List<Anime>> {
        return animeRepository.getAnimeFavoritesBySourceId(sourceId)
    }
}
