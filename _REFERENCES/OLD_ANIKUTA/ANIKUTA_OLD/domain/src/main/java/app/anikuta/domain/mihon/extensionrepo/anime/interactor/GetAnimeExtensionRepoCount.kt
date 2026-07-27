package app.anikuta.domain.mihon.extensionrepo.anime.interactor

import app.anikuta.domain.mihon.extensionrepo.anime.repository.AnimeExtensionRepoRepository

class GetAnimeExtensionRepoCount(
    private val repository: AnimeExtensionRepoRepository,
) {
    fun subscribe() = repository.getCount()
}
