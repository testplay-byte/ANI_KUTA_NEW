package app.confused.anikuta.data.extension.api

import android.content.Context
import android.util.Log
import app.confused.anikuta.data.extension.loader.AnimeExtensionLoader
import app.confused.anikuta.data.extension.model.AnimeExtension
import app.confused.anikuta.data.extension.model.AnimeLoadResult
import app.confused.anikuta.data.extension.repo.ExtensionRepoApi
import app.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * The public façade for fetching available extensions and checking for updates.
 *
 * This is the high-level orchestrator over [ExtensionRepoApi] (single-repo fetch)
 * and [ExtensionRepoRepository] (the list of repos to fetch from). It mirrors
 * the Aniyomi reference's `AnimeExtensionApi` (`eu.kanade.tachiyomi.extension.anime.api`)
 * but is constructor-injected (Koin, ADR-023) instead of Injekt, and uses
 * `index.json` (per the implementation prompt) rather than `index.min.json`.
 *
 * Two public operations:
 * - [findAvailableExtensions] — fetches every repo's index concurrently, merges
 *   by `pkgName`, returns the full list of [AnimeExtension.Available].
 * - [checkForUpdates] — compares the installed extensions against the available
 *   list and returns those with a newer `versionCode` or `libVersion`.
 *
 * Throttling (the reference's once-a-day `last_ext_check` preference) is the
 * caller's responsibility for Phase 4B — the manager decides when to call.
 */
class AnimeExtensionApi(
    private val repoRepository: ExtensionRepoRepository,
    private val repoApi: ExtensionRepoApi,
) {

    /**
     * Fetches the index from every configured repo concurrently and merges the
     * results by `pkgName` (first repo wins on conflict, matching the reference).
     *
     * @return the merged list of available extensions (empty on total failure).
     */
    suspend fun findAvailableExtensions(): List<AnimeExtension.Available> = coroutineScope {
        val repos = repoRepository.getAll()
        if (repos.isEmpty()) {
            Log.i(TAG, "No repos configured; returning empty available list")
            return@coroutineScope emptyList()
        }
        repos.map { repo ->
            async { repoApi.fetchExtensions(repo) }
        }.awaitAll().flatten().distinctBy { it.pkgName }
    }

    /**
     * Compares the currently-installed extensions against [available] and returns
     * those with a newer `versionCode` or `libVersion`.
     *
     * @param available  the available list (caller's choice: pass the cached list
     *   or `null` to re-fetch fresh).
     * @return the installed extensions that have an update, or `null` if the
     *   available list couldn't be fetched.
     */
    suspend fun checkForUpdates(
        context: Context,
        loader: AnimeExtensionLoader,
        available: List<AnimeExtension.Available>? = null,
    ): List<AnimeExtension.Installed>? {
        val availableList = available ?: findAvailableExtensions().takeIf { it.isNotEmpty() } ?: return null

        val installed = loader.loadExtensions(context)
            .filterIsInstance<AnimeLoadResult.Success>()
            .map { it.extension }

        val withUpdates = installed.filter { installedExt ->
            val availableExt = availableList.firstOrNull { it.pkgName == installedExt.pkgName }
            availableExt != null && (
                availableExt.versionCode > installedExt.versionCode ||
                    availableExt.libVersion > installedExt.libVersion
            )
        }
        if (withUpdates.isNotEmpty()) {
            Log.i(TAG, "${withUpdates.size} extension(s) have updates: ${withUpdates.joinToString { it.name }}")
        } else {
            Log.i(TAG, "No extension updates available")
        }
        return withUpdates
    }

    /** Builds the full APK download URL for an available extension. */
    fun getApkUrl(extension: AnimeExtension.Available): String {
        val base = extension.repoUrl
        return if (base.endsWith("/")) "${base}apk/${extension.apkName}" else "$base/apk/${extension.apkName}"
    }

    companion object {
        private const val TAG = "AnikutaExtApi"
    }
}
