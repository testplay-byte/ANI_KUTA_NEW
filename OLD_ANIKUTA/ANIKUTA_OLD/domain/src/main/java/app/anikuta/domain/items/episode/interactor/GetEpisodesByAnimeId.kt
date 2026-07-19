package app.anikuta.domain.items.episode.interactor

import logcat.LogPriority
import app.anikuta.core.util.system.logcat
import app.anikuta.domain.items.episode.model.Episode
import app.anikuta.domain.items.episode.repository.EpisodeRepository

class GetEpisodesByAnimeId(
    private val episodeRepository: EpisodeRepository,
) {

    suspend fun await(animeId: Long): List<Episode> {
        return try {
            episodeRepository.getEpisodeByAnimeId(animeId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
}
