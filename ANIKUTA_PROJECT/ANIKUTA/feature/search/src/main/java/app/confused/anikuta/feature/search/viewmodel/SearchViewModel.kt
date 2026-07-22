package app.confused.anikuta.feature.search.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.model.AniListAnime
import app.confused.anikuta.data.extension.AnimeExtensionManager
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import app.confused.anikuta.feature.search.data.RecentSearchesStore
import app.confused.anikuta.feature.search.data.SearchUiPreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The search source the user has selected on the Search page.
 *
 * - [ANILIST] — searches AniList via [AniListApi]. Results are [AniListAnime].
 * - [EXTENSION] — searches a single extension source via [SourceMatcher].
 *   Results are [SAnime] (extension-native; not AniList anime). Tapping one
 *   triggers the extension→AniList linking flow (see ExtensionLinkingSheet).
 */
enum class SearchSource { ANILIST, EXTENSION }

/**
 * A single result item — either an AniList anime or an extension anime.
 *
 * The UI renders both in the same ResultsCard grid; this sealed type lets the
 * card stay generic while preserving the underlying object for the tap handler
 * (AniList → open detail by ID; Extension → start linking flow).
 */
sealed class SearchResult {
    data class AniList(val anime: AniListAnime) : SearchResult() {
        val id: Int get() = anime.id
    }

    data class Extension(
        val source: AnimeCatalogueSource,
        val sAnime: SAnime,
        val sourceName: String,
    ) : SearchResult()
}

/**
 * One row of the extension default view (Popular or Latest from a source).
 * Shown when source=EXTENSION and query is blank.
 */
data class ExtensionRow(
    val source: AnimeCatalogueSource,
    val sourceName: String,
    val kind: ExtensionRowKind,
    val animes: List<SAnime>,
    val error: String? = null,
)

enum class ExtensionRowKind(val label: String) {
    POPULAR("Popular"),
    LATEST("Latest"),
}

/**
 * The filters applied to an AniList search (mirrors the prototype's FilterSheet
 * state). All AniList filter options the owner asked for are here: genres,
 * year, season, format, status, minScore, sort.
 *
 * Two copies exist in the ViewModel:
 * - `appliedFilters` (in [SearchUiState.filters]) — drives the actual search.
 * - `pendingFilters` — what the FilterSheet edits locally; only synced to
 *   `appliedFilters` when the user taps "Apply filters" (per owner request:
 *   "it was processing the results even before I clicked the apply button").
 */
data class SearchFilters(
    val genres: Set<String> = emptySet(),
    val year: Int? = null,
    val season: String? = null,
    val format: String? = null,
    val status: String? = null,
    val minScore: Int = 0,
) {
    /** Number of active filters — drives the Filters button badge. */
    val activeCount: Int
        get() = genres.size +
            (if (year != null) 1 else 0) +
            (if (season != null) 1 else 0) +
            (if (format != null) 1 else 0) +
            (if (status != null) 1 else 0) +
            (if (minScore > 0) 1 else 0)

    /** True if no filters are set (the "default" search). */
    val isEmpty: Boolean get() = activeCount == 0
}

/**
 * The full UI state for the Search screen.
 */
data class SearchUiState(
    val query: String = "",
    val source: SearchSource = SearchSource.ANILIST,
    val sort: String = "POPULARITY_DESC",
    /** The APPLIED filters (drive the search). The FilterSheet edits a separate pending copy. */
    val filters: SearchFilters = SearchFilters(),
    val results: List<SearchResult> = emptyList(),
    val extensionRows: List<ExtensionRow> = emptyList(),
    val loading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
    /** Recents for the CURRENTLY-selected source (AniList and Extension have separate lists). */
    val recents: List<String> = emptyList(),
    val recentsCollapsed: Boolean = false,
    val selectedExtensionSourceId: Long? = null,
    val availableExtensionSources: List<SourceMatcher.SourceInfo> = emptyList(),
    val isRefreshing: Boolean = false,
    /** AniList pagination — page 1 is the initial load; increments on scroll-to-bottom. */
    val currentPage: Int = 1,
    val canLoadMore: Boolean = true,
)

private const val TAG = "AnikutaSearchVM"
private const val DEBOUNCE_MS = 500L
private const val PAGE_SIZE = 30
private const val MIN_SHEET_DELAY_MS = 400L

/**
 * The ViewModel for the Search page.
 *
 * Orchestrates:
 * - AniList search (debounced) — [AniListApi.searchAnime] for plain query,
 *   [AniListApi.searchAnimeWithFilters] when filters/sort are active.
 *   Supports pagination (loads page N on scroll-to-bottom, appends to results).
 * - Extension search — [SourceMatcher.searchOneSource] for the selected source.
 * - Extension default view (Popular + Latest) — calls each trusted source's
 *   `getPopularAnime`/`getLatestUpdates` on `Dispatchers.IO`.
 * - Recent searches — persisted per source via [RecentSearchesStore] (AniList
 *   and Extension recents are independent).
 * - Recents-card collapsed state — persisted via [SearchUiPreferences] so it
 *   survives screen changes + app restart (per owner request).
 * - Buffered filters — the FilterSheet edits a pending copy; only "Apply"
 *   syncs it to the applied filters + triggers a re-search.
 *
 * All network/source calls run on `Dispatchers.IO`.
 */
class SearchViewModel(
    private val anilistApi: AniListApi,
    private val extensionManager: AnimeExtensionManager,
    private val sourceMatcher: SourceMatcher,
    private val recentsStore: RecentSearchesStore,
    private val uiPreferences: SearchUiPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** The current debounce job — cancelled when the user types again. */
    private var searchJob: Job? = null

    /** The current extension-default-view load job — cancelled on source change. */
    private var extensionDefaultJob: Job? = null

    /**
     * The PENDING filters — what the FilterSheet is editing. Only synced to the
     * applied filters ([SearchUiState.filters]) when the user taps "Apply".
     */
    private var pendingFilters: SearchFilters = SearchFilters()

    init {
        val initialSource = SearchSource.ANILIST
        _uiState.update {
            it.copy(
                recents = recentsStore.get(initialSource),
                recentsCollapsed = uiPreferences.isRecentsCollapsed(),
                availableExtensionSources = sourceMatcher.getAvailableSources(),
                selectedExtensionSourceId = sourceMatcher.getAvailableSources().firstOrNull()?.id,
            )
        }
        pendingFilters = _uiState.value.filters
        loadAniListDefault()
    }

    // ── Public state setters (called by the UI) ──────────────────────────────

    fun onQueryChange(q: String) {
        _uiState.update { it.copy(query = q, currentPage = 1, canLoadMore = true) }
        scheduleSearch()
    }

    fun onSourceChange(newSource: SearchSource) {
        if (newSource == _uiState.value.source) return // re-tap handled by UI (opens picker)
        val newSort = if (newSource == SearchSource.EXTENSION) "TRENDING_DESC" else "POPULARITY_DESC"
        _uiState.update {
            it.copy(
                source = newSource,
                sort = newSort,
                results = emptyList(),
                error = null,
                hasSearched = false,
                currentPage = 1,
                canLoadMore = true,
                recents = recentsStore.get(newSource), // per-source recents
            )
        }
        scheduleSearch()
    }

    /** The user re-tapped the Extension toggle → UI opens the source picker. */
    fun onPickExtensionSource(sourceId: Long) {
        if (sourceId == _uiState.value.selectedExtensionSourceId) return
        _uiState.update {
            it.copy(selectedExtensionSourceId = sourceId, results = emptyList(), extensionRows = emptyList())
        }
        scheduleSearch()
    }

    fun onSortChange(sort: String) {
        _uiState.update { it.copy(sort = sort, currentPage = 1, canLoadMore = true) }
        scheduleSearch()
    }

    // ── Filter buffering (pending vs applied) ───────────────────────────────
    //
    // The FilterSheet edits the pending copy live (so the UI reflects the
    // user's in-progress selections), but the ViewModel does NOT re-fetch
    // until [applyFilters] is called. This fixes the owner's report: "it was
    // processing the results even before I clicked the apply button."

    /** The pending filters the FilterSheet is currently editing. */
    fun getPendingFilters(): SearchFilters = pendingFilters

    /** Update the pending filters (no re-fetch). Called live as the user toggles chips. */
    fun onPendingFiltersChange(filters: SearchFilters) {
        pendingFilters = filters
    }

    /** Sync pending → applied + trigger a re-search. Called when "Apply filters" is tapped. */
    fun applyFilters() {
        _uiState.update { it.copy(filters = pendingFilters, currentPage = 1, canLoadMore = true) }
        Log.i(TAG, "Filters applied: ${pendingFilters.activeCount} active")
        scheduleSearch()
    }

    /** Clear the pending + applied filters + re-fetch. Called by "Clear all" in the sheet. */
    fun onClearFilters() {
        pendingFilters = SearchFilters()
        _uiState.update { it.copy(filters = SearchFilters(), currentPage = 1, canLoadMore = true) }
        Log.i(TAG, "Filters cleared")
        scheduleSearch()
    }

    // ── Recents ──────────────────────────────────────────────────────────────

    fun onPickRecent(query: String) {
        _uiState.update { it.copy(query = query, currentPage = 1, canLoadMore = true) }
        scheduleSearch()
    }

    fun onRemoveRecent(query: String) {
        val src = _uiState.value.source
        recentsStore.remove(src, query)
        _uiState.update { it.copy(recents = recentsStore.get(src)) }
    }

    fun onClearRecents() {
        val src = _uiState.value.source
        recentsStore.clear(src)
        _uiState.update { it.copy(recents = emptyList()) }
    }

    /** Toggle + persist the recents-card collapsed state (survives restart). */
    fun onToggleRecentsCollapsed() {
        val newCollapsed = !_uiState.value.recentsCollapsed
        uiPreferences.setRecentsCollapsed(newCollapsed)
        _uiState.update { it.copy(recentsCollapsed = newCollapsed) }
    }

    // ── Pagination ───────────────────────────────────────────────────────────

    /**
     * Called by the UI when the user scrolls near the bottom of the results.
     * Loads the next AniList page + appends. No-op for extension search
     * (extensions return a single page from searchOneSource — pagination there
     * is a future enhancement).
     */
    fun onLoadMore() {
        val state = _uiState.value
        if (state.source != SearchSource.ANILIST) return
        if (state.loading || state.isLoadingMore) return
        if (!state.canLoadMore) return
        if (state.query.isBlank() && state.filters.isEmpty) return // default view is page-1 only for now
        val nextPage = state.currentPage + 1
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (state.filters.isEmpty) {
                        anilistApi.searchAnime(state.query.trim(), page = nextPage, perPage = PAGE_SIZE)
                    } else {
                        anilistApi.searchAnimeWithFilters(
                            query = state.query.ifBlank { null },
                            page = nextPage,
                            perPage = PAGE_SIZE,
                            genres = state.filters.genres,
                            year = state.filters.year,
                            season = state.filters.season,
                            format = state.filters.format,
                            status = state.filters.status,
                            sort = state.sort,
                            minScore = state.filters.minScore,
                        )
                    }
                }
            }
            result.onSuccess { pageResults ->
                _uiState.update {
                    it.copy(
                        results = it.results + pageResults.map { a -> SearchResult.AniList(a) },
                        currentPage = nextPage,
                        canLoadMore = pageResults.size >= PAGE_SIZE, // a full page → maybe more
                        isLoadingMore = false,
                    )
                }
                Log.i(TAG, "Loaded page $nextPage (+${pageResults.size} results, total ${_uiState.value.results.size})")
            }.onFailure { e ->
                Log.e(TAG, "loadMore page $nextPage failed", e)
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    // ── Search orchestration ─────────────────────────────────────────────────

    private fun scheduleSearch() {
        searchJob?.cancel()
        val state = _uiState.value
        if (state.source == SearchSource.ANILIST) {
            searchJob = viewModelScope.launch { runAniListSearch(state) }
        } else {
            searchJob = viewModelScope.launch {
                if (state.query.isBlank()) {
                    loadExtensionDefault()
                } else {
                    delay(DEBOUNCE_MS)
                    runExtensionSearch(state)
                }
            }
        }
    }

    /** AniList: popular (blank query, no filters) or search (non-blank) or filtered search. */
    private suspend fun runAniListSearch(state: SearchUiState) {
        _uiState.update { it.copy(loading = true, error = null) }
        val result = runCatching {
            withContext(Dispatchers.IO) {
                if (state.query.isBlank() && state.filters.isEmpty) {
                    anilistApi.fetchPopular(perPage = PAGE_SIZE)
                } else if (!state.filters.isEmpty) {
                    anilistApi.searchAnimeWithFilters(
                        query = state.query.ifBlank { null },
                        perPage = PAGE_SIZE,
                        genres = state.filters.genres,
                        year = state.filters.year,
                        season = state.filters.season,
                        format = state.filters.format,
                        status = state.filters.status,
                        sort = state.sort,
                        minScore = state.filters.minScore,
                    )
                } else {
                    anilistApi.searchAnime(state.query.trim(), perPage = PAGE_SIZE)
                }
            }
        }
        result.onSuccess { list ->
            if (state.query.isNotBlank() && state.filters.isEmpty) {
                recentsStore.add(state.source, state.query)
            }
            _uiState.update {
                it.copy(
                    results = list.map { SearchResult.AniList(it) },
                    loading = false,
                    error = null,
                    hasSearched = state.query.isNotBlank() || !state.filters.isEmpty,
                    recents = recentsStore.get(state.source),
                    currentPage = 1,
                    canLoadMore = list.size >= PAGE_SIZE,
                )
            }
        }.onFailure { e ->
            Log.e(TAG, "AniList search failed for '${state.query}'", e)
            _uiState.update {
                it.copy(loading = false, error = e.message ?: "Search failed", hasSearched = true)
            }
        }
    }

    private fun loadAniListDefault() {
        viewModelScope.launch { runAniListSearch(_uiState.value) }
    }

    private suspend fun loadExtensionDefault() {
        extensionDefaultJob?.cancel()
        val state = _uiState.value
        val sourceId = state.selectedExtensionSourceId ?: run {
            _uiState.update {
                it.copy(
                    loading = false,
                    error = "No trusted extension installed. Install one from More → Settings → Extensions.",
                    extensionRows = emptyList(),
                )
            }
            return
        }
        val catalogueSource = resolveCatalogueSource(sourceId)
        if (catalogueSource == null) {
            _uiState.update {
                it.copy(loading = false, error = "Source is no longer available.", extensionRows = emptyList())
            }
            return
        }
        _uiState.update { it.copy(loading = true, error = null, extensionRows = emptyList()) }
        extensionDefaultJob = viewModelScope.launch {
            val rows = loadExtensionRows(catalogueSource)
            _uiState.update {
                it.copy(loading = false, extensionRows = rows, hasSearched = false, results = emptyList())
            }
        }
    }

    private suspend fun loadExtensionRows(source: AnimeCatalogueSource): List<ExtensionRow> {
        val rows = mutableListOf<ExtensionRow>()
        rows += loadOneExtensionRow(source, ExtensionRowKind.POPULAR)
        if (source.supportsLatest) {
            rows += loadOneExtensionRow(source, ExtensionRowKind.LATEST)
        }
        return rows
    }

    private suspend fun loadOneExtensionRow(
        source: AnimeCatalogueSource,
        kind: ExtensionRowKind,
    ): ExtensionRow = withContext(Dispatchers.IO) {
        try {
            val page = when (kind) {
                ExtensionRowKind.POPULAR -> source.getPopularAnime(1)
                ExtensionRowKind.LATEST -> source.getLatestUpdates(1)
            }
            Log.i(TAG, "${source.name} ${kind.label}: ${page.animes.size} results")
            ExtensionRow(source, source.name, kind, page.animes)
        } catch (e: Throwable) {
            val msg = e.cause?.message ?: e.message ?: e::class.java.simpleName
            Log.e(TAG, "${source.name} ${kind.label} failed", e)
            ExtensionRow(source, source.name, kind, emptyList(), msg)
        }
    }

    private suspend fun runExtensionSearch(state: SearchUiState) {
        val sourceId = state.selectedExtensionSourceId ?: run {
            _uiState.update {
                it.copy(loading = false, error = "No extension source selected.", hasSearched = true)
            }
            return
        }
        _uiState.update { it.copy(loading = true, error = null) }
        val outcome = sourceMatcher.searchOneSource(sourceId, state.query.trim())
        when (outcome) {
            is SourceMatcher.SourceSearchOutcome.Success -> {
                // Add to the EXTENSION recents (separate from AniList's).
                recentsStore.add(SearchSource.EXTENSION, state.query)
                _uiState.update {
                    it.copy(
                        results = outcome.results.map { r ->
                            SearchResult.Extension(r.source, r.sAnime, r.sourceName)
                        },
                        loading = false,
                        error = null,
                        hasSearched = true,
                        extensionRows = emptyList(),
                        recents = recentsStore.get(SearchSource.EXTENSION),
                    )
                }
            }
            is SourceMatcher.SourceSearchOutcome.Failed -> {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = "${outcome.sourceName}: ${outcome.error}",
                        hasSearched = true,
                        results = emptyList(),
                    )
                }
            }
        }
    }

    private fun resolveCatalogueSource(sourceId: Long): AnimeCatalogueSource? {
        return extensionManager.getInstalledExtensions()
            .flatMap { it.sources }
            .filterIsInstance<AnimeCatalogueSource>()
            .firstOrNull { it.id == sourceId }
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
        extensionDefaultJob?.cancel()
    }
}
