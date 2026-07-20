package app.confused.anikuta.data.extension.repo

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistence + CRUD for [ExtensionRepo] rows.
 *
 * Backed by a single [SharedPreferences] file holding a JSON array of repos
 * (per the implementation prompt: "Store repos in SharedPreferences — simple,
 * no DB needed for repos"). The reference uses a SQLDelight table; we choose
 * SharedPreferences to keep `:data:extension` free of the `:core:database`
 * dependency for Phase 4B.
 *
 * Exposes a [repos] [StateFlow] so the UI / API can observe changes reactively.
 * All mutations are synchronous and emit a new list to the flow.
 */
class ExtensionRepoRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val _repos = MutableStateFlow(loadAll())
    /** The current list of repos, observable. */
    val repos: StateFlow<List<ExtensionRepo>> = _repos.asStateFlow()

    /** Returns every repo (snapshot). */
    fun getAll(): List<ExtensionRepo> = _repos.value

    /** Returns the repo with the given [baseUrl], or null. */
    fun getRepo(baseUrl: String): ExtensionRepo? =
        _repos.value.firstOrNull { it.baseUrl == baseUrl }

    /** Returns the count of repos (used by the Browse screen's "add repo" CTA). */
    fun getCount(): Int = _repos.value.size

    /**
     * Adds [repo]. Returns `false` if a repo with the same [ExtensionRepo.baseUrl]
     * already exists.
     */
    fun insert(repo: ExtensionRepo): Boolean {
        if (_repos.value.any { it.baseUrl == repo.baseUrl }) {
            Log.w(TAG, "Repo already exists: ${repo.baseUrl}")
            return false
        }
        val updated = _repos.value + repo
        persist(updated)
        return true
    }

    /** Adds or replaces the repo with the same [ExtensionRepo.baseUrl]. */
    fun upsert(repo: ExtensionRepo) {
        val updated = _repos.value.filterNot { it.baseUrl == repo.baseUrl } + repo
        persist(updated)
    }

    /**
     * Replaces [oldBaseUrl]'s repo with [newRepo]. Used by the duplicate-fingerprint
     * flow (the reference's `ReplaceAnimeExtensionRepo`). Returns `false` if the
     * old repo doesn't exist.
     */
    fun replace(oldBaseUrl: String, newRepo: ExtensionRepo): Boolean {
        val idx = _repos.value.indexOfFirst { it.baseUrl == oldBaseUrl }
        if (idx < 0) return false
        val updated = _repos.value.toMutableList().apply { this[idx] = newRepo }
        persist(updated)
        return true
    }

    /** Updates an existing repo's metadata (name/shortName/website/iconUrl). */
    fun update(repo: ExtensionRepo) {
        val idx = _repos.value.indexOfFirst { it.baseUrl == repo.baseUrl }
        if (idx < 0) {
            Log.w(TAG, "Cannot update unknown repo: ${repo.baseUrl}")
            return
        }
        val updated = _repos.value.toMutableList().apply { this[idx] = repo }
        persist(updated)
    }

    /** Deletes the repo with the given [baseUrl]. Returns `false` if not found. */
    fun delete(baseUrl: String): Boolean {
        val before = _repos.value.size
        val updated = _repos.value.filterNot { it.baseUrl == baseUrl }
        if (updated.size == before) return false
        persist(updated)
        return true
    }

    /** Resets to just the default repo (used on first run / "restore defaults"). */
    fun resetToDefault() {
        persist(listOf(ExtensionRepo.DEFAULT))
    }

    private fun persist(repos: List<ExtensionRepo>) {
        val encoded = json.encodeToString(repos)
        prefs.edit().putString(KEY_REPOS, encoded).apply()
        _repos.value = repos
        Log.i(TAG, "Repo list updated (${repos.size} repos)")
    }

    private fun loadAll(): List<ExtensionRepo> {
        val raw = prefs.getString(KEY_REPOS, null) ?: return listOf(ExtensionRepo.DEFAULT)
        return try {
            json.decodeFromString<List<ExtensionRepo>>(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode repos, falling back to default", e)
            listOf(ExtensionRepo.DEFAULT)
        }
    }

    companion object {
        private const val TAG = "AnikutaExtRepo"
        private const val PREFS_NAME = "anikuta_extension_repos"
        private const val KEY_REPOS = "repos_json"
    }
}
