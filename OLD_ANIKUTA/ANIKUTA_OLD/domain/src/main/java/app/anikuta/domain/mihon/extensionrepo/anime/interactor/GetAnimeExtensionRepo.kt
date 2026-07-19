package app.anikuta.domain.mihon.extensionrepo.anime.interactor

import kotlinx.coroutines.flow.Flow
import app.anikuta.domain.mihon.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import app.anikuta.domain.mihon.extensionrepo.model.ExtensionRepo

class GetAnimeExtensionRepo(
    private val repository: AnimeExtensionRepoRepository,
) {
    fun subscribeAll(): Flow<List<ExtensionRepo>> = repository.subscribeAll()

    suspend fun getAll(): List<ExtensionRepo> = repository.getAll()
}
