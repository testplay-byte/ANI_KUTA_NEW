package app.anikuta.domain.mihon.extensionrepo.manga.interactor

import app.anikuta.domain.mihon.extensionrepo.manga.repository.MangaExtensionRepoRepository
import app.anikuta.domain.mihon.extensionrepo.model.ExtensionRepo

class ReplaceMangaExtensionRepo(
    private val repository: MangaExtensionRepoRepository,
) {
    suspend fun await(repo: ExtensionRepo) {
        repository.replaceRepo(repo)
    }
}
