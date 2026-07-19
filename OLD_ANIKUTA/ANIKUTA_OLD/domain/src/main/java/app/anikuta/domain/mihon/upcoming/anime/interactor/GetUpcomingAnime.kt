package app.anikuta.domain.mihon.upcoming.anime.interactor

import app.anikuta.source.api.model.SAnime
import kotlinx.coroutines.flow.Flow
import app.anikuta.domain.entries.anime.model.Anime
import app.anikuta.domain.entries.anime.repository.AnimeRepository

class GetUpcomingAnime(
    private val animeRepository: AnimeRepository,
) {

    private val includedStatuses = setOf(
        SAnime.ONGOING.toLong(),
        SAnime.PUBLISHING_FINISHED.toLong(),
    )

    suspend fun subscribe(): Flow<List<Anime>> {
        return animeRepository.getUpcomingAnime(includedStatuses)
    }
}
