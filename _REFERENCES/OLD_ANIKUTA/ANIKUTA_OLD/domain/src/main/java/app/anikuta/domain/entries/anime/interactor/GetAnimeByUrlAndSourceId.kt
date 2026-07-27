package app.anikuta.domain.entries.anime.interactor

import app.anikuta.domain.entries.anime.model.Anime
import app.anikuta.domain.entries.anime.repository.AnimeRepository

class GetAnimeByUrlAndSourceId(
    private val animeRepository: AnimeRepository,
) {
    suspend fun await(url: String, sourceId: Long): Anime? {
        return animeRepository.getAnimeByUrlAndSourceId(url, sourceId)
    }
}
