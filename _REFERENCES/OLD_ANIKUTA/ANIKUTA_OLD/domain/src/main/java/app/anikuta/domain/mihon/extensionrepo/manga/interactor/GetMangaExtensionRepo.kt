package app.anikuta.domain.mihon.extensionrepo.manga.interactor

import kotlinx.coroutines.flow.Flow
import app.anikuta.domain.mihon.extensionrepo.manga.repository.MangaExtensionRepoRepository
import app.anikuta.domain.mihon.extensionrepo.model.ExtensionRepo

class GetMangaExtensionRepo(
    private val repository: MangaExtensionRepoRepository,
) {
    fun subscribeAll(): Flow<List<ExtensionRepo>> = repository.subscribeAll()

    suspend fun getAll(): List<ExtensionRepo> = repository.getAll()
}
