package app.anikuta.domain.source.anime.interactor

import kotlinx.coroutines.flow.Flow
import app.anikuta.domain.entries.anime.model.Anime
import app.anikuta.domain.entries.anime.repository.AnimeRepository
import app.anikuta.domain.source.anime.model.DeletableAnime

class GetAnimeSourcesWithNonLibraryAnime(
    private val repository: AnimeRepository,
) {

    fun subscribe(): Flow<List<DeletableAnime>> {
        return repository.getDeletableParentAnime()
    }

    suspend fun getDeletableChildren(parentId: Long): List<Anime> {
        return repository.getChildrenByParentId(parentId)
    }
}
