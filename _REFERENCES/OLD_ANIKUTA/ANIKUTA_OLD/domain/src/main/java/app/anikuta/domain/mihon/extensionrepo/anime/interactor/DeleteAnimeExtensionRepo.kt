package app.anikuta.domain.mihon.extensionrepo.anime.interactor

import app.anikuta.domain.mihon.extensionrepo.anime.repository.AnimeExtensionRepoRepository

class DeleteAnimeExtensionRepo(
    private val repository: AnimeExtensionRepoRepository,
) {
    suspend fun await(baseUrl: String) {
        repository.deleteRepo(baseUrl)
    }
}
