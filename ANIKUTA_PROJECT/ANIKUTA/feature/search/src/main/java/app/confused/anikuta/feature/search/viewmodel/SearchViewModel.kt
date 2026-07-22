package app.confused.anikuta.feature.search.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.model.AniListAnime
import app.confused.anikuta.data.extension.AnimeExtensionManager
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import app.confused.anikuta.feature.search.data.RecentSearchesStore
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
 * state). All fields are hoisted here so the FilterSheet composable is stateless
 * and the ViewModel drives the re-fetch.
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
}

/**
 * The full UI state for the Search screen.
 *
 * - [query] / [source] / [sort] / [filters] are the user's inputs.
 * - [results] / [loading] / [error] / [hasSearched] are the search outcome.
 * - [extensionRows] holds the Popular + Latest rows for the extension default
 *   view (source=EXTENSION + blank query).
 * - [selectedExtensionSourceId] is the extension source currently used for
 *   search + the default view. Defaults to the first trusted source.
 */
data class SearchUiState(
    val query: String = "",
    val source: SearchSource = SearchSource.ANILIST,
    val sort: String = "POPULARITY_DESC",
    val filters: SearchFilters = SearchFilters(),
    val results: List<SearchResult> = emptyList(),
    val extensionRows: List<ExtensionRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
    val recents: List<String> = emptyList(),
    val selectedExtensionSourceId: Long? = null,
    val availableExtensionSources: List<SourceMatcher.SourceInfo> = emptyList(),
    val isRefreshing: Boolean = false,
)

private const val TAG = "AnikutaSearchVM"
private const val DEBOUNCE_MS = 500L

/**
 * The ViewModel for the Search page.
 *
 * Orchestrates:
 * - AniList search (debounced) — [AniListApi.searchAnime] for plain query,
 *   [AniListApi.searchAnimeWithFilters] when filters/sort are active.
 * - Extension search — [SourceMatcher.searchOneSource] for the selected source.
 * - Extension default view (Popular + Latest) — calls each trusted source's
 *   `getPopularAnime`/`getLatestUpdates` on `Dispatchers.IO`.
 * - Recent searches — persisted via [RecentSearchesStore].
 *
 * All network/source calls run on `Dispatchers.IO` (the source-api's suspend
 * functions internally delegate to RxJava's `awaitSingle()` which runs
 * synchronously on the calling thread — calling from Main throws
 * `NetworkOnMainThreadException`).
 *
 * @param anilistApi the AniList GraphQL client.
 * @param extensionManager provides installed + trusted sources.
 * @param sourceMatcher searches extension sources by query.
 * @param recentsStore persists recent search strings.
 */
class SearchViewModel(
    private val anilistApi: AniListApi,
    private val extensionManager: AnimeExtensionManager,
    private val sourceMatcher: SourceMatcher,
    private val recentsStore: RecentSearchesStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** The current debounce job — cancelled when the user types again. */
    private var searchJob: Job? = null

    /** The current extension-default-view load job — cancelled on source change. */
    private var extensionDefaultJob: Job? = null

    init {
        // Load recents + available extension sources on init.
        _uiState.update {
            it.copy(
                recents = recentsStore.get(),
                availableExtensionSources = sourceMatcher.getAvailableSources(),
                selectedExtensionSourceId = sourceMatcher.getAvailableSources().firstOrNull()?.id,
            )
        }
        // Kick off the initial AniList default load (popular anime).
        loadAniListDefault()
    }

    // ── Public state setters (called by the UI) ──────────────────────────────

    fun onQueryChange(q: String) {
        _uiState.update { it.copy(query = q) }
        scheduleSearch()
    }

    fun onSourceChange(newSource: SearchSource) {
        if (newSource == _uiState.value.source) {
            // Tapping the already-selected source — the UI interprets this as
            // "open the extension source picker" (handled in SearchScreen).
            // The VM does nothing here; the UI shows the picker.
            return
        }
        // Switching source — reset results + sort default + kick off default load.
        val newSort = if (newSource == SearchSource.EXTENSION) "TRENDING_DESC" else "POPULARITY_DESC"
        _uiState.update {
            it.copy(
                source = newSource,
                sort = newSort,
                results = emptyList(),
                error = null,
                hasSearched = false,
            )
        }
        scheduleSearch()
    }

    /** The user re-tapped the Extension toggle → UI opens the source picker. */
    fun onPickExtensionSource(sourceId: Long) {
        if (sourceId == _uiState.value.selectedExtensionSourceId) return
        _uiState.update {
            it.copy(selectedExtensionSourceId = sourceId, results = emptyList())
        }
        scheduleSearch()
    }

    fun onSortChange(sort: String) {
        _uiState.update { it.copy(sort = sort) }
        scheduleSearch()
    }

    fun onFiltersChange(filters: SearchFilters) {
        _uiState.update { it.copy(filters = filters) }
        scheduleSearch()
    }

    fun onClearFilters() {
        _uiState.update { it.copy(filters = SearchFilters()) }
        scheduleSearch()
    }

    fun onPickRecent(query: String) {
        _uiState.update { it.copy(query = query) }
        scheduleSearch()
    }

    fun onRemoveRecent(query: String) {
        recentsStore.remove(query)
        _uiState.update { it.copy(recents = recentsStore.get()) }
    }

    fun onClearRecents() {
        recentsStore.clear()
        _uiState.update { it.copy(recents = emptyList()) }
    }

    // ── Search orchestration ─────────────────────────────────────────────────

    /**
     * Debounced search: cancels the previous job, waits [DEBOUNCE_MS], then
     * dispatches to the appropriate source (AniList or the selected extension).
     */
    private fun scheduleSearch() {
        searchJob?.cancel()
        val state = _uiState.value
        if (state.source == SearchSource.ANILIST) {
            searchJob = viewModelScope.launch { runAniListSearch(state) }
        } else {
            // Extension: no debounce on source switch, but debounce on typing.
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

    /** AniList: popular (blank query) or search (non-blank) or filtered search. */
    private suspend fun runAniListSearch(state: SearchUiState) {
        _uiState.update { it.copy(loading = true, error = null) }
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val hasFilters = state.filters.activeCount > 0
                if (state.query.isBlank() && !hasFilters) {
                    // Default view: popular anime (matches the prototype).
                    anilistApi.fetchPopular(perPage = 30)
                } else if (hasFilters) {
                    // Filtered search (query may be blank → filter-only browse).
                    anilistApi.searchAnimeWithFilters(
                        query = state.query.ifBlank { null },
                        perPage = 30,
                        genres = state.filters.genres,
                        year = state.filters.year,
                        season = state.filters.season,
                        format = state.filters.format,
                        status = state.filters.status,
                        sort = state.sort,
                        minScore = state.filters.minScore,
                    )
                } else {
                    // Plain search — add to recents (debounced, non-blank).
                    anilistApi.searchAnime(state.query.trim(), perPage = 30)
                }
            }
        }
        result.onSuccess { list ->
            if (state.query.isNotBlank() && state.filters.activeCount == 0) {
                recentsStore.add(state.query)
            }
            _uiState.update {
                it.copy(
                    results = list.map { SearchResult.AniList(it) },
                    loading = false,
                    error = null,
                    hasSearched = state.query.isNotBlank() || state.filters.activeCount > 0,
                    recents = recentsStore.get(),
                )
            }
        }.onFailure { e ->
            Log.e(TAG, "AniList search failed for '${state.query}'", e)
            _uiState.update {
                it.copy(loading = false, error = e.message ?: "Search failed", hasSearched = true)
            }
        }
    }

    /** Initial AniList load (no query → popular). Called from init. */
    private fun loadAniListDefault() {
        viewModelScope.launch { runAniListSearch(_uiState.value) }
    }

    /**
     * Extension default view (source=EXTENSION, query=blank): loads Popular +
     * Latest rows from the selected source.
     */
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
        val sources = sourceMatcher.getAvailableSources()
        val selected = sources.firstOrNull { it.id == sourceId }
        val catalogueSource = resolveCatalogueSource(sourceId)
        if (catalogueSource == null) {
            _uiState.update {
                it.copy(
                    loading = false,
                    error = "Source '${selected?.name ?: sourceId}' is no longer available.",
                    extensionRows = emptyList(),
                )
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

    /** Loads Popular + Latest rows for one source (on Dispatchers.IO). */
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

    /** Extension free-text search on the selected source (one source only). */
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
                _uiState.update {
                    it.copy(
                        results = outcome.results.map { r ->
                            SearchResult.Extension(r.source, r.sAnime, r.sourceName)
                        },
                        loading = false,
                        error = null,
                        hasSearched = true,
                        extensionRows = emptyList(),
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

    /** Resolve a source ID → [AnimeCatalogueSource] (for the default-view rows). */
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
