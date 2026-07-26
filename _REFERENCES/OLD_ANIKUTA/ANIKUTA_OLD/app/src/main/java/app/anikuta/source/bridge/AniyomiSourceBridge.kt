package app.anikuta.source.bridge

import android.util.Log
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.model.AnimeFilterList
import app.anikuta.source.api.model.SAnime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Bridges AniList (discovery) ↔ extension sources (streaming).
 *
 * Given an AniList anime, searches every installed catalogue source for a
 * matching SAnime using fuzzy title matching (≥80% similarity). Returns the
 * best match (or None / NotAired).
 *
 * Matching behavior (Phase 5 Q2 decision):
 *  - **Not aired** (AniList status = "NOT_YET_RELEASED"): returns [SourceMatchResult.NotAired].
 *  - **0 matches** across all sources: returns [SourceMatchResult.NoMatch].
 *  - **1 match** ≥ threshold: returns [SourceMatchResult.SingleMatch].
 *  - **Multiple matches**: returns [SourceMatchResult.MultipleMatches] with the
 *    highest-scoring one as `best` (auto-picked). The user can override via a
 *    "Wrong anime?" link in the detail page.
 *
 * The search runs across all sources in parallel for speed. Results from each
 * source are scored against the AniList title (preferred = English → romaji →
 * native), and the top candidate per source is kept.
 */
class AniyomiSourceBridge(
    private val sourceManager: AnimeSourceManager,
) {

    companion object {
        private const val TAG = "SourceBridge"
    }

    /**
     * Search all installed catalogue sources for [anime] and return the best match.
     */
    suspend fun findMatch(anime: AniListAnime): SourceMatchResult =
        withContext(Dispatchers.IO) {
            // Not-aired check first — no point searching if the anime hasn't
            // been released yet.
            if (anime.status == "NOT_YET_RELEASED") {
                Log.d(TAG, "'${anime.title.preferred()}' not yet released — skipping search")
                return@withContext SourceMatchResult.NotAired(
                    anime.title.preferred(),
                    anime.seasonYear,
                    anime.season,
                )
            }

            val query = anime.title.preferred()
            val sources = sourceManager.getCatalogueSources()
            if (sources.isEmpty()) {
                Log.w(TAG, "No catalogue sources installed — can't match")
                return@withContext SourceMatchResult.NoMatch(query)
            }

            // Load source priority order (if set). Sources earlier in the
            // priority list are preferred when scores are tied.
            val priorityOrder: List<String> = try {
                val prefs = Injekt.get<app.anikuta.domain.source.service.SourcePreferences>()
                val json = prefs.sourcePriorityOrder().get()
                kotlinx.serialization.json.Json.decodeFromString<List<String>>(json)
            } catch (e: Exception) { emptyList() }

            // Build a source→priorityIndex map for sorting
            val sourcePriorityMap = mutableMapOf<Long, Int>()
            sources.forEach { source ->
                val extPkg = try {
                    uy.kohesive.injekt.Injekt.get<app.anikuta.extension.anime.AnimeExtensionManager>()
                        .getExtensionPackage(source.id)
                } catch (e: Exception) { null }
                if (extPkg != null) {
                    val idx = priorityOrder.indexOf(extPkg)
                    sourcePriorityMap[source.id] = if (idx == -1) Int.MAX_VALUE else idx
                }
            }
            Log.d(TAG, "Searching ${sources.size} source(s) for \"$query\"… (priority: ${sourcePriorityMap.size} mapped)")

            // Search every source in parallel, collect scored candidates.
            val candidates = coroutineScope {
                sources.map { source ->
                    async { searchSource(source, query) }
                }.awaitAll()
            }.flatten()

            if (candidates.isEmpty()) {
                Log.d(TAG, "No candidates found across ${sources.size} source(s)")
                return@withContext SourceMatchResult.NoMatch(query)
            }

            // Sort by score descending, then by source priority (lower index = higher priority).
            val scored = candidates
                .filter { it.score >= TitleMatcher.THRESHOLD }
                .sortedWith(
                    compareByDescending<ScoredCandidate> { it.score }
                        .thenBy { sourcePriorityMap[it.source.id] ?: Int.MAX_VALUE },
                )

            if (scored.isEmpty()) {
                Log.d(TAG, "Found ${candidates.size} candidate(s) but none ≥ ${TitleMatcher.THRESHOLD}")
                return@withContext SourceMatchResult.NoMatch(query)
            }

            val best = scored.first()
            Log.d(TAG, "Best match: '${best.sAnime.title}' from '${best.sourceName}' (score=${"%.2f".format(best.score)})")

            if (scored.size == 1) {
                SourceMatchResult.SingleMatch(best.sAnime, best.sourceName, best.score)
            } else {
                SourceMatchResult.MultipleMatches(
                    best = best.sAnime,
                    bestSourceName = best.sourceName,
                    bestScore = best.score,
                    allMatches = scored.map { MatchedCandidate(it.sAnime, it.sourceName, it.score) },
                )
            }
        }

    /**
     * Search a single source and return scored candidates.
     * Catches per-source errors so one broken source doesn't kill the whole search.
     */
    private suspend fun searchSource(
        source: AnimeCatalogueSource,
        query: String,
    ): List<ScoredCandidate> = try {
        val page = source.getSearchAnime(1, query, AnimeFilterList())
        val queryNorm = TitleMatcher.normalize(query)
        page.animes.map { sAnime ->
            val score = TitleMatcher.similarity(query, sAnime.title)
            ScoredCandidate(sAnime, source.name, source, score)
        }.also {
            Log.v(TAG, "[${source.name}] returned ${page.animes.size} result(s)")
        }
    } catch (e: Exception) {
        Log.e(TAG, "[${source.name}] search failed: ${e.message}", e)
        emptyList()
    }

    private data class ScoredCandidate(
        val sAnime: SAnime,
        val sourceName: String,
        val source: AnimeCatalogueSource,
        val score: Double,
    )

    /**
     * Search all installed catalogue sources for [query] (Phase 5 part 4).
     *
     * Unlike [findMatch] (which finds the best match for a specific anime),
     * this returns ALL results from ALL sources — for the Search page's
     * "Extensions" mode. Results are grouped by source.
     *
     * Returns a flat list of [SourceSearchResult] (one per source anime found).
     * Errors per source are caught and logged (one broken source doesn't kill
     * the whole search).
     */
    suspend fun searchAllSources(query: String): List<SourceSearchResult> = withContext(Dispatchers.IO) {
        val sources = sourceManager.getCatalogueSources()
        if (sources.isEmpty()) return@withContext emptyList()

        coroutineScope {
            sources.map { source ->
                async { searchSourcePublic(source, query) }
            }.awaitAll().flatten()
        }
    }

    /** Public version of searchSource that returns SourceSearchResult (not ScoredCandidate). */
    private suspend fun searchSourcePublic(
        source: AnimeCatalogueSource,
        query: String,
    ): List<SourceSearchResult> = try {
        val page = source.getSearchAnime(1, query, AnimeFilterList())
        page.animes.map { sAnime ->
            SourceSearchResult(
                title = sAnime.title,
                thumbnailUrl = sAnime.thumbnail_url,
                url = sAnime.url,
                sourceName = source.name,
                sourceId = source.id,
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "[${source.name}] search failed: ${e.message}", e)
        emptyList()
    }

    /**
     * Fetch popular anime from all installed sources (Phase I — extension browse mode).
     * Returns up to 20 results per source, flattened.
     */
    suspend fun fetchPopularFromAllSources(): List<SourceSearchResult> = withContext(Dispatchers.IO) {
        val sources = sourceManager.getCatalogueSources()
        if (sources.isEmpty()) return@withContext emptyList()
        coroutineScope {
            sources.map { source ->
                async { fetchFromSource(source, isPopular = true) }
            }.awaitAll().flatten()
        }
    }

    /**
     * Fetch latest anime from all installed sources (Phase I — extension browse mode).
     * Returns up to 20 results per source, flattened.
     */
    suspend fun fetchLatestFromAllSources(): List<SourceSearchResult> = withContext(Dispatchers.IO) {
        val sources = sourceManager.getCatalogueSources()
        if (sources.isEmpty()) return@withContext emptyList()
        coroutineScope {
            sources.map { source ->
                async { fetchFromSource(source, isPopular = false) }
            }.awaitAll().flatten()
        }
    }

    private suspend fun fetchFromSource(
        source: AnimeCatalogueSource,
        isPopular: Boolean,
    ): List<SourceSearchResult> = try {
        val page = if (isPopular) source.getPopularAnime(1) else source.getLatestUpdates(1)
        page.animes.take(20).map { sAnime ->
            SourceSearchResult(
                title = sAnime.title,
                thumbnailUrl = sAnime.thumbnail_url,
                url = sAnime.url,
                sourceName = source.name,
                sourceId = source.id,
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "[${source.name}] fetch ${if (isPopular) "popular" else "latest"} failed: ${e.message}", e)
        emptyList()
    }
}

/**
 * A single search result from an extension source (Phase 5 part 4).
 *
 * Unlike [AniListAnime] (which has AniList ID + rich metadata), this is a
 * lightweight wrapper around the extension's [SAnime] — just the fields
 * needed to display a search result card and navigate to the detail page.
 */
data class SourceSearchResult(
    val title: String,
    val thumbnailUrl: String?,
    val url: String,
    val sourceName: String,
    val sourceId: Long,
)

/* ------------------------------------------------------------------ */
/* Result types                                                        */
/* ------------------------------------------------------------------ */

sealed class SourceMatchResult {
    /** No source had the anime. Show "Not available" + release info if known. */
    data class NoMatch(val searchedTitle: String) : SourceMatchResult()

    /** The anime hasn't aired yet. Show "will be available after it airs". */
    data class NotAired(
        val title: String,
        val seasonYear: Int?,
        val season: String?,
    ) : SourceMatchResult()

    /** Exactly one source matched. Auto-select + show a badge. */
    data class SingleMatch(
        val sAnime: SAnime,
        val sourceName: String,
        val score: Double,
    ) : SourceMatchResult()

    /** Multiple sources matched. The highest-scoring is auto-picked ([best]).
     *  User can override via "Wrong anime?" → picks from [allMatches]. */
    data class MultipleMatches(
        val best: SAnime,
        val bestSourceName: String,
        val bestScore: Double,
        val allMatches: List<MatchedCandidate>,
    ) : SourceMatchResult()
}

data class MatchedCandidate(
    val sAnime: SAnime,
    val sourceName: String,
    val score: Double,
)
