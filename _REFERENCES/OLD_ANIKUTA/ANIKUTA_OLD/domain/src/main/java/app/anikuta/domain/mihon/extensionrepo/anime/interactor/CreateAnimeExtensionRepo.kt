package app.anikuta.domain.mihon.extensionrepo.anime.interactor

import logcat.LogPriority
import app.anikuta.domain.mihon.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import app.anikuta.domain.mihon.extensionrepo.exception.SaveExtensionRepoException
import app.anikuta.domain.mihon.extensionrepo.model.ExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.service.ExtensionRepoService
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import app.anikuta.core.util.system.logcat

class CreateAnimeExtensionRepo(
    private val repository: AnimeExtensionRepoRepository,
    private val service: ExtensionRepoService,
) {
    private val repoRegex = """^https://.*/index\.min\.json$""".toRegex()

    suspend fun await(indexUrl: String): Result {
        // Normalize: accept URLs with or without /index.min.json suffix.
        // If the user pasted just the base URL, append /index.min.json.
        val normalizedUrl = indexUrl.trim().let { url ->
            if (url.endsWith("/index.min.json")) url
            else if (url.endsWith("/")) url + "index.min.json"
            else "$url/index.min.json"
        }

        val formattedIndexUrl = normalizedUrl.toHttpUrlOrNull()
            ?.toString()
            ?.takeIf { it.matches(repoRegex) }
            ?: return Result.InvalidUrl

        val baseUrl = formattedIndexUrl.removeSuffix("/index.min.json")

        // Try to fetch /repo.json for the repo metadata (name, signing fingerprint).
        // If it doesn't exist (404) or fails, still insert the repo with a
        // default name derived from the hostname. ANI-KUTA's trust model is
        // per-package, so we don't strictly need the signing fingerprint.
        val repo = service.fetchRepoDetails(baseUrl)
        return if (repo != null) {
            insert(repo)
        } else {
            logcat(LogPriority.WARN) { "repo.json not found at $baseUrl — inserting with default name" }
            val defaultName = baseUrl.toHttpUrlOrNull()?.host?.substringAfter(".")?.substringBefore(".")?.replaceFirstChar { it.uppercase() }
                ?: "Extension Repo"
            insert(
                ExtensionRepo(
                    baseUrl = baseUrl,
                    name = defaultName,
                    shortName = null,
                    website = baseUrl,
                    signingKeyFingerprint = "NOFINGERPRINT",
                ),
            )
        }
    }

    private suspend fun insert(repo: ExtensionRepo): Result {
        return try {
            repository.insertRepo(
                repo.baseUrl,
                repo.name,
                repo.shortName,
                repo.website,
                repo.signingKeyFingerprint,
            )
            Result.Success
        } catch (e: SaveExtensionRepoException) {
            logcat(LogPriority.WARN, e) { "SQL Conflict attempting to add new anime repository ${repo.baseUrl}" }
            return handleInsertionError(repo)
        }
    }

    /**
     * Error Handler for insert when there are trying to create new repositories
     *
     * SaveExtensionRepoException doesn't provide constraint info in exceptions.
     * First check if the conflict was on primary key. if so return RepoAlreadyExists
     * Then check if the conflict was on fingerprint. if so Return DuplicateFingerprint
     * If neither are found, there was some other Error, and return Result.Error
     *
     * @param repo Extension Repo holder for passing to DB/Error Dialog
     */
    private suspend fun handleInsertionError(repo: ExtensionRepo): Result {
        val repoExists = repository.getRepo(repo.baseUrl)
        if (repoExists != null) {
            return Result.RepoAlreadyExists
        }
        val matchingFingerprintRepo = repository.getRepoBySigningKeyFingerprint(repo.signingKeyFingerprint)
        if (matchingFingerprintRepo != null) {
            return Result.DuplicateFingerprint(matchingFingerprintRepo, repo)
        }
        return Result.Error
    }

    sealed interface Result {
        data class DuplicateFingerprint(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : Result
        data object InvalidUrl : Result
        data object RepoAlreadyExists : Result
        data object Success : Result
        data object Error : Result
    }
}
