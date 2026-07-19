package app.anikuta.domain.entries.anime.interactor

import app.anikuta.domain.entries.anime.repository.AnimeRepository

class ResetAnimeViewerFlags(
    private val animeRepository: AnimeRepository,
) {
    suspend fun await(): Boolean {
        return animeRepository.resetAnimeViewerFlags()
    }
}
