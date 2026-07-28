package app.anikuta.domain.mihon.extensionrepo.anime.interactor

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import app.anikuta.domain.mihon.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import app.anikuta.domain.mihon.extensionrepo.model.ExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.service.ExtensionRepoService

class UpdateAnimeExtensionRepo(
    private val repository: AnimeExtensionRepoRepository,
    private val service: ExtensionRepoService,
) {

    suspend fun awaitAll() = coroutineScope {
        repository.getAll()
            .map { async { await(it) } }
            .awaitAll()
    }

    suspend fun await(repo: ExtensionRepo) {
        val newRepo = service.fetchRepoDetails(repo.baseUrl) ?: return
        if (
            repo.signingKeyFingerprint.startsWith("NOFINGERPRINT") ||
            repo.signingKeyFingerprint == newRepo.signingKeyFingerprint
        ) {
            repository.upsertRepo(newRepo)
        }
    }
}
