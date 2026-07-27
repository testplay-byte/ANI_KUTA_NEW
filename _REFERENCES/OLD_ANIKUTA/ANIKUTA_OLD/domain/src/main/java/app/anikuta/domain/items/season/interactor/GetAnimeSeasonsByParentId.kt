package app.anikuta.domain.items.season.interactor

import app.anikuta.domain.anime.SeasonAnime
import logcat.LogPriority
import app.anikuta.core.util.system.logcat
import app.anikuta.domain.entries.anime.repository.AnimeRepository

class GetAnimeSeasonsByParentId(
    private val animeRepository: AnimeRepository,
) {
    suspend fun await(animeId: Long): List<SeasonAnime> {
        return try {
            animeRepository.getAnimeSeasonsById(animeId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
}
