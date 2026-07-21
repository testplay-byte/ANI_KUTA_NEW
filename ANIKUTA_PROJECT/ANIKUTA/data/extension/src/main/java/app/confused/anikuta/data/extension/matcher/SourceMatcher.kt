package app.confused.anikuta.data.extension.matcher

import android.util.Log
import app.confused.anikuta.data.extension.AnimeExtensionManager
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Searches trusted extension sources for an anime by title and returns the
 * best-matching source + [SAnime] pair.
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
 * search. (Per `RULES/ai-agent-rules.md` §12: Understand → Locate → Fix.)
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

    /**
     * Searches trusted sources in priority order and returns the first match.
     *
     * "First match" = the first source (in installed-extensions order) that
     * returns at least one [SAnime] with title similarity >= [THRESHOLD].
     * Within that source's results, the highest-scoring anime is picked.
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
     * Searches ALL sources concurrently and returns every match (for the
     * source-switcher UI). Matches are sorted by score descending so the
     * best match is first.
     *
     * Also populates [lastMatchAllErrors] with per-source errors so the UI
     * can show the user WHY auto-match failed (not just "no match").
     */
    suspend fun matchAll(title: String): List<SourceMatch> = coroutineScope {
        val sources = getCatalogueSources()
        if (sources.isEmpty()) {
            Log.w(TAG, "matchAll: no catalogue sources available")
            lastMatchAllErrors = listOf(
                "(no sources)" to "No trusted extensions are installed. Install an anime extension from Settings → Extensions first.",
            )
            return@coroutineScope emptyList()
        }

        Log.i(TAG, "matchAll: searching ${sources.size} sources for '$title': ${sources.map { it.name }}")
        val results = sources.map { source ->
            async {
                Log.d(TAG, "matchAll: starting search on '${source.name}'")
                searchSourceDetailed(source, title)
            }
        }.awaitAll()

        // Collect per-source errors for the UI.
        val errors = results.mapNotNull { outcome ->
            when (outcome) {
                is SourceSearchOutcome.Failed -> outcome.sourceName to outcome.error
                is SourceSearchOutcome.Success<*> -> null
            }
        }
        lastMatchAllErrors = errors.ifEmpty { null }

        val matches = results.filterIsInstance<SourceSearchOutcome.Success<SourceMatch>>()
            .flatMap { it.results }
            .sortedByDescending { it.score }
        Log.i(TAG, "matchAll: found ${matches.size} matches for '$title'")
        matches.forEach { m ->
            Log.i(TAG, "matchAll: match '${m.sAnime.title}' (score=${m.score}) from '${m.source.name}'")
        }
        matches
    }

    /**
     * Searches one source and returns the outcome: [SourceSearchOutcome.Success]
     * (with matches above the threshold, or empty if the source returned results
     * but none matched) or [SourceSearchOutcome.Failed] (if the source threw).
     *
     * Unlike the old `searchSource` (which returned `null` for both no-match and
     * error), this lets [matchAll] distinguish "no results" from "error" so the
     * UI can show per-source failure reasons.
     */
    private suspend fun searchSourceDetailed(
        source: AnimeCatalogueSource,
        query: String,
    ): SourceSearchOutcome<SourceMatch> {
        return try {
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
     * (which reads the `installedMap` StateFlow's current value synchronously)
     * rather than `installedExtensionsFlow.value`.
     *
     * `installedExtensionsFlow` is built with `stateIn(SharingStarted.Lazily, ...)`
     * — its `.value` returns the empty initial list until the first subscriber
     * arrives and the `map` operator runs. On a fresh app start where the user
     * navigates directly to a detail page (without first visiting the Extensions
     * screen), no subscriber has collected the flow yet, so `.value` was
     * `emptyList()` → "no sources have this anime" even though 2 extensions
     * were installed. Reading `getInstalledExtensions()` avoids this race.
     */
    private fun getCatalogueSources(): List<AnimeCatalogueSource> {
        return extensionManager.getInstalledExtensions()
            .flatMap { it.sources }
            .filterIsInstance<AnimeCatalogueSource>()
    }

    // ── Title matching helpers (ported from OLD_ANIKUTA TitleMatcher.kt) ──

    /**
     * Searches ALL sources for a custom query (manual search).
     * Returns raw results without similarity scoring — the user picks manually.
     *
     * **Note:** per-source errors are swallowed here (logged but not returned).
     * For a version that returns per-source errors so the UI can show them,
     * use [searchAllSourcesDetailed].
     */
    suspend fun searchAllSources(query: String): List<ManualSearchResult> = coroutineScope {
        searchAllSourcesDetailed(query)
            .filterIsInstance<SourceSearchOutcome.Success<ManualSearchResult>>()
            .flatMap { it.results }
    }

    /**
     * Searches ALL sources for a custom query, returning per-source outcomes
     * (success OR failure). The UI uses this to show the user WHY a source
     * didn't return results — not just that it didn't.
     *
     * @return a list of [SourceSearchOutcome] — one per source searched.
     *   [SourceSearchOutcome.Success] contains the results; [SourceSearchOutcome.Failed]
     *   contains the source name + error message.
     */
    suspend fun searchAllSourcesDetailed(query: String): List<SourceSearchOutcome<ManualSearchResult>> = coroutineScope {
        val sources = getCatalogueSources()
        if (sources.isEmpty()) {
            Log.w(TAG, "searchAllSourcesDetailed: no catalogue sources available")
            return@coroutineScope listOf(
                SourceSearchOutcome.Failed(
                    sourceName = "(no sources)",
                    error = "No trusted extensions are installed. Install an anime extension from Settings → Extensions first.",
                ),
            )
        }

        Log.i(TAG, "searchAllSourcesDetailed: searching ${sources.size} sources for '$query'")
        sources.map { source ->
            async {
                try {
                    Log.d(TAG, "searchAllSourcesDetailed: searching '${source.name}' for '$query'")
                    val page = source.getSearchAnime(1, query, AnimeFilterList(emptyList()))
                    Log.d(TAG, "searchAllSourcesDetailed: '${source.name}' returned ${page.animes.size} results")
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
                    // Catch Throwable — see searchSource for rationale.
                    val errorMsg = e.cause?.message ?: e.message ?: e::class.java.simpleName
                    Log.e(TAG, "searchAllSourcesDetailed: '${source.name}' failed for '$query'", e)
                    SourceSearchOutcome.Failed(source.name, errorMsg)
                }
            }
        }.awaitAll().also { outcomes ->
            val successCount = outcomes.count { it is SourceSearchOutcome.Success<*> }
            val failCount = outcomes.count { it is SourceSearchOutcome.Failed }
            Log.i(TAG, "searchAllSourcesDetailed: $successCount sources succeeded, $failCount failed")
        }
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
