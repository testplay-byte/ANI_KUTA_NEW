package app.anikuta.domain.mihon.extensionrepo.manga.interactor

import app.anikuta.domain.mihon.extensionrepo.manga.repository.MangaExtensionRepoRepository

class GetMangaExtensionRepoCount(
    private val repository: MangaExtensionRepoRepository,
) {
    fun subscribe() = repository.getCount()
}
