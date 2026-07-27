package app.anikuta.domain.items.episode.interactor

import app.anikuta.domain.items.episode.model.Episode
import app.anikuta.domain.items.episode.repository.EpisodeRepository

class GetEpisodeByUrlAndAnimeId(
    private val episodeRepository: EpisodeRepository,
) {

    suspend fun await(url: String, sourceId: Long): Episode? {
        return try {
            episodeRepository.getEpisodeByUrlAndAnimeId(url, sourceId)
        } catch (e: Exception) {
            null
        }
    }
}
