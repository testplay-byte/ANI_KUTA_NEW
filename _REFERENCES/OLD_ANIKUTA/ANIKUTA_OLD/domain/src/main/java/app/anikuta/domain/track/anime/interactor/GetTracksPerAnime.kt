package app.anikuta.domain.track.anime.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.anikuta.domain.track.anime.model.AnimeTrack
import app.anikuta.domain.track.anime.repository.AnimeTrackRepository

class GetTracksPerAnime(
    private val trackRepository: AnimeTrackRepository,
) {

    fun subscribe(): Flow<Map<Long, List<AnimeTrack>>> {
        return trackRepository.getAnimeTracksAsFlow().map { tracks -> tracks.groupBy { it.animeId } }
    }
}
