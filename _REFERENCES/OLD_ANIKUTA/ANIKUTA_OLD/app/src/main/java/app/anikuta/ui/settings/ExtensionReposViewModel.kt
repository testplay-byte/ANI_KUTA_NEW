package app.anikuta.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.data.mihon.AnimeExtensionRepoRepositoryImpl
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.DeleteAnimeExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import app.anikuta.domain.mihon.extensionrepo.model.ExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.service.ExtensionRepoService
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import app.anikuta.data.handlers.anime.AnimeDatabaseHandler

/**
 * Phase 7 — ViewModel for the Extension Repositories settings screen.
 *
 * Robust DI: tries Injekt.get() first, and if that fails, manually constructs
 * the interactor from its dependencies (which are more likely to be registered).
 * This catches the case where a specific interactor fails to resolve due to
 * a transitive dependency issue.
 */
class ExtensionReposViewModel : ViewModel() {

    companion object {
        private const val TAG = "ExtReposViewModel"
    }

    private val getExtensionRepo: GetAnimeExtensionRepo? by lazy { resolveGetRepo() }
    private val createRepo: CreateAnimeExtensionRepo? by lazy { resolveCreateRepo() }
    private val deleteRepo: DeleteAnimeExtensionRepo? by lazy { resolveDeleteRepo() }
    private val updateRepo: UpdateAnimeExtensionRepo? by lazy { resolveUpdateRepo() }

    private val _repos = MutableStateFlow<List<ExtensionRepo>>(emptyList())
    val repos: StateFlow<List<ExtensionRepo>> = _repos.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _createResult = MutableStateFlow<CreateResult>(CreateResult.Idle)
    val createResult: StateFlow<CreateResult> = _createResult.asStateFlow()

    // ---- Manual construction fallbacks ----

    private fun resolveRepository(): AnimeExtensionRepoRepository? {
        return try {
            Injekt.get<AnimeExtensionRepoRepository>()
        } catch (e: Exception) {
            Log.e(TAG, "Injekt.get<AnimeExtensionRepoRepository> failed, trying manual construction", e)
            try {
                val handler = Injekt.get<AnimeDatabaseHandler>()
                AnimeExtensionRepoRepositoryImpl(handler)
            } catch (e2: Exception) {
                Log.e(TAG, "Manual AnimeExtensionRepoRepositoryImpl construction also failed", e2)
                null
            }
        }
    }

    private fun resolveService(): ExtensionRepoService? {
        return try {
            Injekt.get<ExtensionRepoService>()
        } catch (e: Exception) {
            Log.e(TAG, "Injekt.get<ExtensionRepoService> failed, trying manual construction", e)
            try {
                val networkHelper = Injekt.get<NetworkHelper>()
                val json = Injekt.get<Json>()
                ExtensionRepoService(networkHelper, json)
            } catch (e2: Exception) {
                Log.e(TAG, "Manual ExtensionRepoService construction also failed", e2)
                null
            }
        }
    }

    private fun resolveGetRepo(): GetAnimeExtensionRepo? {
        return try {
            Injekt.get<GetAnimeExtensionRepo>()
        } catch (e: Exception) {
            Log.e(TAG, "Injekt.get<GetAnimeExtensionRepo> failed, trying manual", e)
            resolveRepository()?.let { GetAnimeExtensionRepo(it) }
        }
    }

    private fun resolveCreateRepo(): CreateAnimeExtensionRepo? {
        return try {
            Injekt.get<CreateAnimeExtensionRepo>()
        } catch (e: Exception) {
            Log.e(TAG, "Injekt.get<CreateAnimeExtensionRepo> failed, trying manual", e)
            val repo = resolveRepository() ?: return null
            val service = resolveService() ?: return null
            CreateAnimeExtensionRepo(repo, service)
        }
    }

    private fun resolveDeleteRepo(): DeleteAnimeExtensionRepo? {
        return try {
            Injekt.get<DeleteAnimeExtensionRepo>()
        } catch (e: Exception) {
            Log.e(TAG, "Injekt.get<DeleteAnimeExtensionRepo> failed, trying manual", e)
            resolveRepository()?.let { DeleteAnimeExtensionRepo(it) }
        }
    }

    private fun resolveUpdateRepo(): UpdateAnimeExtensionRepo? {
        return try {
            Injekt.get<UpdateAnimeExtensionRepo>()
        } catch (e: Exception) {
            Log.e(TAG, "Injekt.get<UpdateAnimeExtensionRepo> failed, trying manual", e)
            val repo = resolveRepository() ?: return null
            val service = resolveService() ?: return null
            UpdateAnimeExtensionRepo(repo, service)
        }
    }

    init {
        viewModelScope.launch {
            getExtensionRepo?.subscribeAll()?.collectLatest { repoList ->
                _repos.value = repoList
                Log.d(TAG, "Repos updated: ${repoList.size}")
            }
        }
    }

    fun createRepo(url: String) {
        val interactor = createRepo ?: run {
            Log.e(TAG, "createRepo: CreateAnimeExtensionRepo is null (DI + manual both failed)")
            _createResult.value = CreateResult.Error("Internal error: could not initialize repo service. Check logcat for ExtReposViewModel.")
            return
        }
        viewModelScope.launch {
            _createResult.value = CreateResult.Loading
            try {
                Log.i(TAG, "createRepo: attempting to add '$url'")
                val result = interactor.await(url)
                _createResult.value = when (result) {
                    is CreateAnimeExtensionRepo.Result.Success -> {
                        Log.i(TAG, "createRepo: SUCCESS — repo added")
                        CreateResult.Success
                    }
                    is CreateAnimeExtensionRepo.Result.InvalidUrl -> {
                        Log.w(TAG, "createRepo: InvalidUrl")
                        CreateResult.Error("Invalid URL. Must be an HTTPS URL ending with /index.min.json (or just the base URL).")
                    }
                    is CreateAnimeExtensionRepo.Result.RepoAlreadyExists -> {
                        Log.w(TAG, "createRepo: RepoAlreadyExists")
                        CreateResult.Error("Repository already exists")
                    }
                    is CreateAnimeExtensionRepo.Result.DuplicateFingerprint -> CreateResult.DuplicateFingerprint(result.oldRepo, result.newRepo)
                    is CreateAnimeExtensionRepo.Result.Error -> {
                        Log.e(TAG, "createRepo: Error")
                        CreateResult.Error("Failed to add repository")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "createRepo failed", e)
                _createResult.value = CreateResult.Error(e.message ?: "Unknown error: ${e.javaClass.simpleName}")
            }
        }
    }

    fun deleteRepo(baseUrl: String) {
        val interactor = deleteRepo ?: return
        viewModelScope.launch {
            try {
                interactor.await(baseUrl)
                Log.i(TAG, "Deleted repo: $baseUrl")
            } catch (e: Exception) {
                Log.e(TAG, "deleteRepo failed", e)
            }
        }
    }

    fun refreshRepos() {
        val interactor = updateRepo ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                interactor.awaitAll()
                Log.i(TAG, "Repos refreshed")
            } catch (e: Exception) {
                Log.e(TAG, "refreshRepos failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun dismissCreateResult() {
        _createResult.value = CreateResult.Idle
    }

    sealed class CreateResult {
        data object Idle : CreateResult()
        data object Loading : CreateResult()
        data object Success : CreateResult()
        data class Error(val message: String) : CreateResult()
        data class DuplicateFingerprint(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : CreateResult()
    }
}
