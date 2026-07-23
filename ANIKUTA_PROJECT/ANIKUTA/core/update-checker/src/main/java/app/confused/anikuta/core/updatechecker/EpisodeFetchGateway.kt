package app.confused.anikuta.core.updatechecker

/**
 * Abstraction over "find an extension source for an anime title and fetch its
 * current episode list".
 *
 * **Why this interface exists (architectural note).**
 * [UpdateChecker] lives in `:core:update-checker`. Per `ARCHITECTURE.md` §3,
 * `:core` modules may NOT depend on `:data:*`. But fetching an episode list
 * requires `AnimeExtensionManager` + `SourceMatcher` (both in `:data:extension`)
 * plus `AnimeSource.getEpisodeList(...)` (from `:core:source-api`). To respect
 * the dependency rule, this gateway interface is declared in `:core` and
 * implemented in `:data:extension` (`EpisodeFetchGatewayImpl`), then injected
 * into `UpdateChecker` via Koin.
 *
 * This also makes [UpdateChecker] trivially testable (swap in a fake gateway)
 * and lets a future `WorkManager` worker call update checks without the worker
 * module pulling the entire extension stack.
 *
 * **Implementor contract.**
 *  - All network/source work MUST happen on `Dispatchers.IO`. The implementor
 *    is responsible for `withContext(Dispatchers.IO)`.
 *  - Implementors MUST catch `Throwable` (not `Exception`) — extension
 *    bytecode can throw `IncompatibleClassChangeError` / `NoClassDefFoundError`
 *    on binary-incompat, and one broken source must not abort the whole check.
 *  - On any failure, return `EpisodeFetchResult.NoSource` (never throw).
 */
interface EpisodeFetchGateway {

    /**
     * Resolves an extension source for [animeTitle] and fetches its current
     * episode list.
     *
     * The resolution uses `SourceMatcher.matchAll(title)` (first match wins).
     * A future optimization can consult `ExtensionLinkStore` to skip
     * re-matching for previously-linked anime — see the worklog.
     *
     * @param animeTitle The anime's display title (English > Romaji > Native).
     * @return The episodes + the source name, or `NoSource` if no extension
     *   source matched or the fetch failed.
     */
    suspend fun fetchEpisodes(animeTitle: String): EpisodeFetchResult
}

/**
 * Outcome of an episode fetch.
 */
sealed interface EpisodeFetchResult {
    /**
     * Episodes were fetched successfully.
     *
     * @property sourceName The extension source name (for display + logging).
     * @property episodes The full current episode list from the source, sorted
     *   as the source returns it (caller re-sorts as needed).
     */
    data class Success(
        val sourceName: String,
        val episodes: List<EpisodeInfo>,
    ) : EpisodeFetchResult

    /** No extension source matched, or the fetch failed. Episodes list is empty. */
    data object NoSource : EpisodeFetchResult
}
