package app.anikuta.domain.items.episode.interactor

import logcat.LogPriority
import app.anikuta.core.util.system.logcat
import app.anikuta.domain.items.episode.model.EpisodeUpdate
import app.anikuta.domain.items.episode.repository.EpisodeRepository

class UpdateEpisode(
    private val episodeRepository: EpisodeRepository,
) {

    suspend fun await(episodeUpdate: EpisodeUpdate) {
        try {
            episodeRepository.updateEpisode(episodeUpdate)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun awaitAll(episodeUpdates: List<EpisodeUpdate>) {
        try {
            episodeRepository.updateAllEpisodes(episodeUpdates)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
