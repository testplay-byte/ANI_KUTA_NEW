package app.anikuta.domain.mihon.extensionrepo.anime.interactor

import app.anikuta.domain.mihon.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import app.anikuta.domain.mihon.extensionrepo.model.ExtensionRepo

class ReplaceAnimeExtensionRepo(
    private val repository: AnimeExtensionRepoRepository,
) {
    suspend fun await(repo: ExtensionRepo) {
        repository.replaceRepo(repo)
    }
}
