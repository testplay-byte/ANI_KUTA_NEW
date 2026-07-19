package app.anikuta.domain.mihon.extensionrepo.manga.interactor

import app.anikuta.domain.mihon.extensionrepo.manga.repository.MangaExtensionRepoRepository

class DeleteMangaExtensionRepo(
    private val repository: MangaExtensionRepoRepository,
) {
    suspend fun await(baseUrl: String) {
        repository.deleteRepo(baseUrl)
    }
}
