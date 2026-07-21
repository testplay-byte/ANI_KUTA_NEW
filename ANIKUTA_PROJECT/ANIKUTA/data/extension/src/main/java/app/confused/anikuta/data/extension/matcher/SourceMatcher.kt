package app.confused.anikuta.data.extension.matcher

import android.util.Log
import app.confused.anikuta.data.extension.AnimeExtensionManager
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Searches trusted extension sources for an anime by title and returns the
 * best-matching source + [SAnime] pair.
 *
 * **Threading (CRITICAL):**
 * All `source.getSearchAnime()` / `source.getEpisodeList()` calls are wrapped
 * in `withContext(Dispatchers.IO)`. These suspend functions internally delegate
 * to RxJava's `awaitSingle()` → `Observable.subscribe()` → `call.execute()`,
 * which runs **synchronously on the calling thread**. If the calling thread is
 * the main thread (which it is — `viewModelScope.launch` runs on Main), the
 * network call throws `NetworkOnMainThreadException`. The `withContext(IO)`
 * shifts execution to an IO dispatcher so the network call is legal.
 *
 * **Priority-based search** (per Step 5 requirements):
 * - [match] searches sources in the order they appear in
 *   [AnimeExtensionManager.installedExtensionsFlow] — the "trusted sources list".
 *   The first source that returns a title-similar result wins.
 * - [matchAll] searches every source concurrently and returns every match —
 *   used by the source-switcher UI to show the user all available sources.
 *
 * **Title matching** (ported from the old ANIKUTA's `TitleMatcher.kt`):
 * - Normalizes both the query and each result title (lowercase, strip
 *   parentheticals, strip non-alphanumerics, collapse whitespace).
 * - Similarity: exact = 1.0, substring = 0.95, else Levenshtein-based.
 * - Threshold: 0.80 (matches the old project).
 *
 * **Error handling**: each source call is wrapped in try-catch. A failing
 * source is logged and skipped — one broken extension doesn't kill the entire
 * search.
 *
 * @param extensionManager provides the live list of installed + trusted sources.
 */
class SourceMatcher(
    private val extensionManager: AnimeExtensionManager,
) {

    /**
     * Per-source errors from the most recent [matchAll] call.
     * `null` if [matchAll] hasn't been called yet. Empty if all sources succeeded.
     * The UI reads this to show the user WHY auto-match failed (not just "no match").
     */
    @Volatile
    var lastMatchAllErrors: List<Pair<String, String>>? = null
        private set

    /**
     * A single source + SAnime match with a similarity score.
     *
     * @param source the catalogue source that returned the match.
     * @param sAnime the matched anime (its `url` is needed for `getEpisodeList`).
     * @param score title similarity (0.0–1.0); higher is better.
     */
    data class SourceMatch(
        val source: AnimeCatalogueSource,
        val sAnime: SAnime,
        val score: Double,
    )

    /**
     * A raw search result from a source (for manual search).
     * No similarity scoring — just the raw result.
     */
    data class ManualSearchResult(
        val source: AnimeCatalogueSource,
        val sAnime: SAnime,
        val sourceName: String,
        val title: String,
        val thumbnailUrl: String?,
    )

    /**
     * A lightweight source reference for the UI's source selector.
     * Holds just the source ID + name — not the full [AnimeCatalogueSource]
     * (which is a heavy object with a lazy OkHttp client).
     *
     * The UI uses this to render a source-picker dropdown. When the user
     * selects a source, the ID is passed back to [searchOneSource] which
     * resolves it to the full source object.
     */
    data class SourceInfo(
        val id: Long,
        val name: String,
    )

    /**
     * The outcome of searching a single source.
     * Either [Success] (with results) or [Failed] (with an error message).
     * The UI uses this to show per-source failure reasons so the user knows
     * WHY a source didn't return results — not just that it didn't.
     *
     * Generic in [T] so it can hold either [SourceMatch] (auto-match) or
     * [ManualSearchResult] (manual search).
     */
    sealed class SourceSearchOutcome<out T> {
        data class Success<T>(val results: List<T>) : SourceSearchOutcome<T>()
        data class Failed(val sourceName: String, val error: String) : SourceSearchOutcome<Nothing>()
    }

    /**
     * The result of [match]. Either a single best match, no match, or an error.
     */
    sealed class Result {
        /** A matching source + anime was found. */
        data class Match(val match: SourceMatch) : Result()
        /** No source returned a title-similar result. */
        data object NoMatch : Result()
        /** The search itself failed (e.g. no sources installed, or all threw). */
        data class Error(val message: String) : Result()
    }

    // ── Source listing (for the manual-search source selector) ──

    /**
     * Returns the list of available (installed + trusted) catalogue sources
     * as [SourceInfo] — lightweight (id + name) for the UI's source selector.
     *
     * The manual search sheet calls this to populate its source picker.
     * The user selects ONE source, then [searchOneSource] is called with
     * that source's ID.
     */
    fun getAvailableSources(): List<SourceInfo> {
        return getCatalogueSources().map { SourceInfo(it.id, it.name) }
    }

    /**
     * Searches ONE specific source (by ID) for a custom query.
     * Used by the manual search sheet when the user picks a source from the
     * selector — only that source is searched, and only its results are shown.
     *
     * @param sourceId the ID of the source to search (from [SourceInfo.id]).
     * @param query the search query.
     * @return [SourceSearchOutcome.Success] with raw results (no similarity
     *   scoring — the user picks manually), or [SourceSearchOutcome.Failed]
     *   if the source threw an exception.
     */
    suspend fun searchOneSource(sourceId: Long, query: String): SourceSearchOutcome<ManualSearchResult> {
        val source = getCatalogueSources().firstOrNull { it.id == sourceId }
            ?: return SourceSearchOutcome.Failed(
                sourceName = "(unknown)",
                error = "Source not found. It may have been untrusted or uninstalled.",
            )

        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "searchOneSource: searching '${source.name}' for '$query'")
                val page = source.getSearchAnime(1, query, AnimeFilterList(emptyList()))
                Log.d(TAG, "searchOneSource: '${source.name}' returned ${page.animes.size} results")
                val results = page.animes.map { sAnime ->
                    ManualSearchResult(
                        source = source,
                        sAnime = sAnime,
                        sourceName = source.name,
                        title = sAnime.title,
                        thumbnailUrl = sAnime.thumbnail_url,
                    )
                }
                SourceSearchOutcome.Success(results)
            } catch (e: Throwable) {
                val errorMsg = e.cause?.message ?: e.message ?: e::class.java.simpleName
                Log.e(TAG, "searchOneSource: '${source.name}' failed for '$query'", e)
                SourceSearchOutcome.Failed(source.name, errorMsg)
            }
        }
    }

    // ── Auto-match (searches ALL sources, returns best match) ──

    /**
     * Searches trusted sources in priority order and returns the first match.
     *
     * @param title the anime title (typically from AniList's `displayTitle`).
     */
    suspend fun match(title: String): Result {
        val sources = getCatalogueSources()
        if (sources.isEmpty()) {
            Log.w(TAG, "No catalogue sources installed — cannot match '$title'")
            return Result.Error("No sources installed")
        }
        Log.i(TAG, "Searching ${sources.size} sources for '$title'")

        for (source in sources) {
            val outcome = searchSourceDetailed(source, title)
            if (outcome is SourceSearchOutcome.Success<*> && outcome.results.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                val match = outcome.results.first() as SourceMatch
                Log.i(TAG, "Matched '${match.sAnime.title}' (score=${match.score}) from '${source.name}'")
                return Result.Match(match)
            }
        }
        Log.i(TAG, "No source has '$title'")
        return Result.NoMatch
    }

    /**
     * Searches trusted sources **sequentially in priority order** (top of the
     * trusted list = highest priority) and returns matches.
     *
     * **Sequential priority-based search (per user request):**
     * - Sources are searched ONE AT A TIME, in the order they appear in the
     *   installed-extensions list (the "trusted sources list").
     * - If a source returns an **exact match** (score = 1.0), that source is
     *   used immediately — the remaining sources are NOT searched. This is
     *   faster and avoids unnecessary network calls.
     * - If no exact match, but there are fuzzy matches (score >= [THRESHOLD]),
     *   those are collected and the search continues to the next source.
     * - If a source fails (throws an exception), the error is recorded and the
     *   search continues with the next source.
     * - After all sources are searched (or an exact match short-circuits),
     *   matches are sorted by score descending.
     *
     * This is different from the old concurrent approach (which searched all
     * sources at once via `async + awaitAll`). The sequential approach is:
     * - **Faster** when the first source has an exact match (only 1 network call)
     * - **More predictable** — the first source's exact match always wins
     * - **Less load** on extension servers (doesn't hit all of them simultaneously)
     *
     * Also populates [lastMatchAllErrors] with per-source errors so the UI
     * can show the user WHY auto-match failed (not just "no match").
     */
    suspend fun matchAll(title: String): List<SourceMatch> {
        val sources = getCatalogueSources()
        if (sources.isEmpty()) {
            Log.w(TAG, "matchAll: no catalogue sources available")
            lastMatchAllErrors = listOf(
                "(no sources)" to "No trusted extensions are installed. Install an anime extension from Settings → Extensions first.",
            )
            return emptyList()
        }

        Log.i(TAG, "matchAll: sequentially searching ${sources.size} sources for '$title': ${sources.map { it.name }}")
        val allMatches = mutableListOf<SourceMatch>()
        val errors = mutableListOf<Pair<String, String>>()

        for ((index, source) in sources.withIndex()) {
            Log.i(TAG, "matchAll: searching source ${index + 1}/${sources.size}: '${source.name}'")
            val outcome = searchSourceDetailed(source, title)

            when (outcome) {
                is SourceSearchOutcome.Success -> {
                    val matches = outcome.results
                    if (matches.isNotEmpty()) {
                        Log.i(TAG, "matchAll: '${source.name}' returned ${matches.size} matches")
                        allMatches.addAll(matches)

                        // Check for exact match (score = 1.0) — if found, stop searching.
                        val exactMatch = matches.firstOrNull { it.score >= 1.0 }
                        if (exactMatch != null) {
                            Log.i(TAG, "matchAll: EXACT match found from '${source.name}' — stopping search (skipping ${sources.size - index - 1} remaining sources)")
                            break
                        }
                    } else {
                        Log.d(TAG, "matchAll: '${source.name}' returned 0 matches above threshold")
                    }
                }
                is SourceSearchOutcome.Failed -> {
                    Log.w(TAG, "matchAll: '${source.name}' failed: ${outcome.error}")
                    errors.add(outcome.sourceName to outcome.error)
                }
            }
        }

        lastMatchAllErrors = errors.ifEmpty { null }

        // Sort by score descending so the best match is first.
        val sorted = allMatches.sortedByDescending { it.score }
        Log.i(TAG, "matchAll: found ${sorted.size} total matches for '$title'")
        sorted.forEach { m ->
            Log.i(TAG, "matchAll: match '${m.sAnime.title}' (score=${m.score}) from '${m.source.name}'")
        }
        return sorted
    }

    /**
     * Searches one source and returns the outcome.
     *
     * **Threading:** wraps `source.getSearchAnime()` in `withContext(Dispatchers.IO)`
     * because the suspend function internally delegates to RxJava's `awaitSingle()`
     * → `Observable.subscribe()` → `call.execute()`, which runs synchronously on
     * the calling thread. Without this, calling from the Main thread throws
     * `NetworkOnMainThreadException`.
     */
    private suspend fun searchSourceDetailed(
        source: AnimeCatalogueSource,
        query: String,
    ): SourceSearchOutcome<SourceMatch> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "searchSource: calling getSearchAnime on '${source.name}' with query='$query'")
            val page = source.getSearchAnime(1, query, AnimeFilterList(emptyList()))
            Log.d(TAG, "searchSource: '${source.name}' returned ${page.animes.size} results")
            val normalizedQuery = normalizeTitle(query)
            val matches = page.animes
                .map { sAnime ->
                    val score = similarity(normalizedQuery, normalizeTitle(sAnime.title))
                    SourceMatch(source, sAnime, score)
                }
                .filter { it.score >= THRESHOLD }
            SourceSearchOutcome.Success(matches)
        } catch (e: Throwable) {
            // Catch Throwable (not Exception) so that binary-incompat errors like
            // IncompatibleClassChangeError / NoClassDefFoundError don't crash the
            // app — a broken extension is logged + skipped, and the search
            // continues with the remaining sources.
            val msg = e.cause?.message ?: e.message ?: e::class.java.simpleName
            Log.e(TAG, "searchSource: Source '${source.name}' search failed for '$query'", e)
            SourceSearchOutcome.Failed(source.name, msg)
        }
    }

    /**
     * Returns the catalogue sources from all installed + trusted extensions.
     *
     * **CRITICAL:** reads from [AnimeExtensionManager.getInstalledExtensions]
     * (synchronous read of `installedMap.value`) rather than
     * `installedExtensionsFlow.value` (which is lazily-collected and returns
     * the empty initial list until the first subscriber arrives).
     */
    private fun getCatalogueSources(): List<AnimeCatalogueSource> {
        return extensionManager.getInstalledExtensions()
            .flatMap { it.sources }
            .filterIsInstance<AnimeCatalogueSource>()
    }

    // ── Title matching helpers ──

    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("""\b\d+(st|nd|rd|th)?\s+season\b"""), "") // "2nd season"
            .replace(Regex("""\bseason\s+\d+\b"""), "")               // "season 2"
            .replace(Regex("""\([^)]*\)"""), "")                      // parentheticals
            .replace(Regex("""\[[^]]*]"""), "")                      // bracketed
            .replace(Regex("""[^\w\s]"""), "")                        // non-alphanumerics
            .trim()
            .replace(Regex("""\s+"""), " ")                           // collapse whitespace
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a.contains(b) || b.contains(a)) return 0.95
        val dist = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length)
        return 1.0 - dist.toDouble() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev.indices.forEach { prev[it] = curr[it] }
        }
        return prev[b.length]
    }

    companion object {
        private const val TAG = "AnikutaSourceMatcher"
        private const val THRESHOLD = 0.80
    }
}
