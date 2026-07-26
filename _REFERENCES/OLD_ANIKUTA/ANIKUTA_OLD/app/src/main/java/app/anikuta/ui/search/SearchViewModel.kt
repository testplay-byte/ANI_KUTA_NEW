package app.anikuta.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.cache.CacheManager
import app.anikuta.source.bridge.SourceSearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 5 tasks 5.12 + 5.13 — Search view-model.
 *
 * Flow:
 * 1. UI calls [setQuery] on every keystroke → updates [_query].
 * 2. [_query] is debounced 400ms + distinctUntilChanged → triggers [doSearch].
 * 3. [doSearch] routes through [CacheManager] (5 min TTL, key = `search_<query>`)
 *    so rapid backspaces/edits don't re-hit AniList. Mirrors how HomeViewModel
 *    caches trending/popular/fresh.
 * 4. Result surfaces as [SearchState] (Idle / Loading / Success / Empty / Error).
 *
 * Recent searches: persisted via [PreferenceStore] (ordered list, max 10) so
 * users can re-run a previous query with one tap. Saved when the user opens an
 * anime from the results (see [onAnimeClick]).
 *
 * AniList-only for Phase 5 (Q5 decision — extension search in Phase 7).
 */
class SearchViewModel : ViewModel() {

    companion object {
        private const val TAG = "SearchViewModel"
        private const val DEBOUNCE_MS = 400L
        private const val MAX_RECENT = 10
        private const val RECENT_KEY = "search_recent_terms"
        private const val CACHE_TTL = CacheManager.TTL_HOME_SHORT // 5 min
    }

    private val anilistRepo: AniListRepository?
    private val cacheManager: CacheManager?
    private val preferenceStore: PreferenceStore?
    private val sourceBridge: app.anikuta.source.bridge.AniyomiSourceBridge?
    private val json = Json { ignoreUnknownKeys = true }

    init {
        anilistRepo = try {
            Injekt.get<AniListRepository>().also { Log.d(TAG, "AniListRepository obtained") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get AniListRepository", e)
            null
        }
        cacheManager = try {
            Injekt.get<CacheManager>().also { Log.d(TAG, "CacheManager obtained") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get CacheManager", e)
            null
        }
        preferenceStore = try {
            Injekt.get<PreferenceStore>().also { Log.d(TAG, "PreferenceStore obtained") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get PreferenceStore", e)
            null
        }
        sourceBridge = try {
            Injekt.get<app.anikuta.source.bridge.AniyomiSourceBridge>()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get AniyomiSourceBridge", e)
            null
        }
    }

    // --- Query -------------------------------------------------------------

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // --- Search state ------------------------------------------------------

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    // --- Recent searches ---------------------------------------------------

    /**
     * Ordered list of recent search terms, persisted via PreferenceStore.
     * Newest first; capped at [MAX_RECENT]. We use `getObject<List<String>>`
     * (JSON-serialized) so order is preserved — `getStringSet` is unordered.
     *
     * Explicit `List<String>` type annotations on the lambda parameters are
     * required because `getObject<T>` can't infer `T` from `defaultValue =
     * emptyList()` alone — the compiler bails out with "Cannot infer type for
     * this parameter" cascading through both lambdas.
     */
    private val recentPref: Preference<List<String>>? = preferenceStore?.getObject(
        key = RECENT_KEY,
        defaultValue = emptyList<String>(),
        serializer = { terms: List<String> ->
            json.encodeToString(ListSerializer(String.serializer()), terms)
        },
        deserializer = { raw: String ->
            try {
                json.decodeFromString(ListSerializer(String.serializer()), raw)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't decode recent searches — resetting", e)
                emptyList()
            }
        },
    )

    val recentSearches: StateFlow<List<String>> = (recentPref?.changes() ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Phase 5 revamp: auto-search debounce REMOVED.
        // Search now fires only on explicit submit (keyboard Enter / search button).
        // The user requested: "remove that and wire the Enter button of the keyboard
        // with the actual search."

        // Phase I: eagerly fetch AniList genres so they're available in the filter sheet
        // even before the user searches (was only fetched on first search → appeared empty).
        viewModelScope.launch {
            val repo = anilistRepo ?: return@launch
            try {
                val genres = repo.getGenres()
                if (genres.isNotEmpty()) {
                    _availableGenres.value = genres
                    Log.d(TAG, "Loaded ${genres.size} AniList genres")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load AniList genres: ${e.message}")
            }
        }
    }

    /** Current page for pagination (Phase 5 part 2). */
    private var currentPage = 1
    /** Whether more pages are available. */
    private var hasMore = false
    /** The query for the current search (used for loadMore). */
    private var currentQuery = ""

    // Phase 5 part 3 — filters
    /** Available genres (fetched from AniList on first search). */
    private val _availableGenres = MutableStateFlow<List<String>>(emptyList())
    val availableGenres: StateFlow<List<String>> = _availableGenres.asStateFlow()

    // Phase 5 part 4 — source toggle
    /** Search mode: AniList (default) or Extensions. */
    private val _searchMode = MutableStateFlow(SearchMode.RECENT)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    /** Source search results (when in SOURCES mode). Separate from AniList results. */
    private val _sourceResults = MutableStateFlow<List<SourceSearchResult>>(emptyList())
    val sourceResults: StateFlow<List<SourceSearchResult>> = _sourceResults.asStateFlow()

    /** Popular anime from extensions (shown when SOURCES mode + no query). */
    private val _popularResults = MutableStateFlow<List<SourceSearchResult>>(emptyList())
    val popularResults: StateFlow<List<SourceSearchResult>> = _popularResults.asStateFlow()

    /** Latest anime from extensions (shown when SOURCES mode + no query). */
    private val _latestResults = MutableStateFlow<List<SourceSearchResult>>(emptyList())
    val latestResults: StateFlow<List<SourceSearchResult>> = _latestResults.asStateFlow()

    /** Whether popular/latest are currently loading. */
    private val _isLoadingBrowse = MutableStateFlow(false)
    val isLoadingBrowse: StateFlow<Boolean> = _isLoadingBrowse.asStateFlow()

    fun setSearchMode(mode: SearchMode) {
        _searchMode.value = mode
        val q = _query.value.trim()
        if (mode == SearchMode.RECENT) {
            _query.value = ""
            _state.value = SearchState.Idle
            return
        }
        if (q.isNotBlank()) {
            viewModelScope.launch {
                if (mode == SearchMode.ANILIST) doSearch(q) else doSourceSearch(q)
            }
        } else if (mode == SearchMode.SOURCES) {
            loadExtensionBrowse()
        } else if (mode == SearchMode.ANILIST) {
            // AniList with no query → show trending/popular results
            loadAniListBrowse()
        }
    }

    /** Load trending anime from AniList (Phase I — AniList browse mode). */
    private fun loadAniListBrowse() {
        val repo = anilistRepo ?: return
        viewModelScope.launch {
            _state.value = SearchState.Loading
            try {
                val data = repo.getTrending(page = 1, perPage = 25)
                allResults = data
                hasMore = false
                // If filters are active, apply them; otherwise show all trending
                val hasActiveFilters = _selectedGenres.value.isNotEmpty() || _selectedYears.value.isNotEmpty() ||
                    _selectedFormats.value.isNotEmpty() || _selectedSeasons.value.isNotEmpty() ||
                    _selectedStatuses.value.isNotEmpty()
                if (hasActiveFilters) {
                    applyFilters()
                } else {
                    _state.value = if (data.isEmpty()) {
                        SearchState.Empty
                    } else {
                        SearchState.Success(anime = data, hasMore = false, isLoadingMore = false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AniList browse failed", e)
                _state.value = SearchState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /** Load popular + latest from all extension sources (Phase I). */
    private fun loadExtensionBrowse() {
        val bridge = sourceBridge ?: return
        _isLoadingBrowse.value = true
        viewModelScope.launch {
            try {
                val popular = bridge.fetchPopularFromAllSources()
                val latest = bridge.fetchLatestFromAllSources()
                _popularResults.value = popular
                _latestResults.value = latest
                _state.value = if (popular.isEmpty() && latest.isEmpty()) {
                    SearchState.Empty
                } else {
                    SearchState.Idle // browse mode uses popularResults/latestResults directly
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load extension browse", e)
                _state.value = SearchState.Error(e.message ?: "Unknown error")
            } finally {
                _isLoadingBrowse.value = false
            }
        }
    }

    /** Selected genres (multi-select). Empty = no filter. */
    private val _selectedGenres = MutableStateFlow<Set<String>>(emptySet())
    val selectedGenres: StateFlow<Set<String>> = _selectedGenres.asStateFlow()

    /** Selected years (multi-select). Empty = no filter. */
    private val _selectedYears = MutableStateFlow<Set<Int>>(emptySet())
    val selectedYears: StateFlow<Set<Int>> = _selectedYears.asStateFlow()

    /** Selected formats (multi-select). Empty = no filter. */
    private val _selectedFormats = MutableStateFlow<Set<String>>(emptySet())
    val selectedFormats: StateFlow<Set<String>> = _selectedFormats.asStateFlow()

    /** Selected seasons (multi-select). Empty = no filter. */
    private val _selectedSeasons = MutableStateFlow<Set<String>>(emptySet())
    val selectedSeasons: StateFlow<Set<String>> = _selectedSeasons.asStateFlow()

    /** Selected statuses (multi-select). Empty = no filter. */
    private val _selectedStatuses = MutableStateFlow<Set<String>>(emptySet())
    val selectedStatuses: StateFlow<Set<String>> = _selectedStatuses.asStateFlow()

    /** Selected sort method (single-select). null = no sort. */
    private val _selectedSort = MutableStateFlow<String?>(null)
    val selectedSort: StateFlow<String?> = _selectedSort.asStateFlow()

    /** Show adult results toggle (Phase I). Default false. */
    private val _showAdult = MutableStateFlow(false)
    val showAdult: StateFlow<Boolean> = _showAdult.asStateFlow()

    /** Available seasons for the filter sheet. */
    val availableSeasons = listOf("WINTER", "SPRING", "SUMMER", "FALL")

    /** Available statuses for the filter sheet. */
    val availableStatuses = listOf("RELEASING", "FINISHED", "NOT_YET_RELEASED", "CANCELLED")

    /** Available sort methods for the filter sheet. */
    val availableSorts = listOf("POPULARITY", "SCORE", "NEWEST", "TRENDING")

    /** The unfiltered results (before client-side filtering). Used for re-filtering. */
    private var allResults: List<AniListAnime> = emptyList()

    /** Available formats for the filter sheet. */
    val availableFormats = listOf("TV", "MOVIE", "OVA", "ONA", "SPECIAL", "MUSIC")

    /** Available years (computed from search results + a range). */
    val availableYears: List<Int>
        get() {
            val current = java.time.Year.now().value
            return (current downTo current - 20).toList()
        }

    fun toggleGenre(genre: String) {
        val current = _selectedGenres.value.toMutableSet()
        if (current.contains(genre)) current.remove(genre) else current.add(genre)
        _selectedGenres.value = current
        applyFilters()
    }
    fun toggleFormat(format: String) {
        val current = _selectedFormats.value.toMutableSet()
        if (current.contains(format)) current.remove(format) else current.add(format)
        _selectedFormats.value = current
        applyFilters()
    }
    fun toggleSeason(season: String) {
        val current = _selectedSeasons.value.toMutableSet()
        if (current.contains(season)) current.remove(season) else current.add(season)
        _selectedSeasons.value = current
        applyFilters()
    }
    fun toggleStatus(status: String) {
        val current = _selectedStatuses.value.toMutableSet()
        if (current.contains(status)) current.remove(status) else current.add(status)
        _selectedStatuses.value = current
        applyFilters()
    }
    fun toggleYear(year: Int) {
        val current = _selectedYears.value.toMutableSet()
        if (current.contains(year)) current.remove(year) else current.add(year)
        _selectedYears.value = current
        applyFilters()
    }
    fun setSortFilter(sort: String?) { _selectedSort.value = sort; applyFilters() }
    fun setShowAdult(show: Boolean) { _showAdult.value = show; applyFilters() }
    fun clearFilters() {
        _selectedGenres.value = emptySet()
        _selectedYears.value = emptySet()
        _selectedFormats.value = emptySet()
        _selectedSeasons.value = emptySet()
        _selectedStatuses.value = emptySet()
        _selectedSort.value = null
        _showAdult.value = false
        applyFilters()
    }

    /** Apply the current filters to [allResults] and update the state. */
    private fun applyFilters() {
        val genres = _selectedGenres.value
        val years = _selectedYears.value
        val formats = _selectedFormats.value
        val seasons = _selectedSeasons.value
        val statuses = _selectedStatuses.value
        val filtered = allResults.filter { anime ->
            (genres.isEmpty() || anime.genres?.any { it in genres } == true) &&
            (years.isEmpty() || anime.seasonYear in years) &&
            (formats.isEmpty() || anime.format in formats) &&
            (seasons.isEmpty() || anime.season in seasons) &&
            (statuses.isEmpty() || anime.status in statuses)
        }
        _state.value = if (filtered.isEmpty() && allResults.isNotEmpty()) {
            SearchState.Empty
        } else {
            SearchState.Success(anime = filtered, hasMore = hasMore, isLoadingMore = false)
        }
    }

    private suspend fun doSearch(q: String) {
        val repo = anilistRepo
        if (repo == null) {
            _state.value = SearchState.Error("App not properly initialized")
            return
        }
        // Reset pagination state for a new search.
        currentPage = 1
        currentQuery = q
        _state.value = SearchState.Loading
        try {
            // Fetch genres on first search (for the filter sheet).
            if (_availableGenres.value.isEmpty()) {
                try { _availableGenres.value = repo.getGenres() } catch (e: Exception) { /* non-fatal */ }
            }
            val data = repo.searchAnime(q, page = 1, perPage = 25)
            hasMore = data.size >= 25
            allResults = data
            if (data.isEmpty()) {
                _state.value = SearchState.Empty
            } else {
                saveRecent(q.trim())
                // Apply any active filters to the first page.
                if (_selectedGenres.value.isNotEmpty() || _selectedYears.value.isNotEmpty() || _selectedFormats.value.isNotEmpty() || _selectedSeasons.value.isNotEmpty() || _selectedStatuses.value.isNotEmpty() || _selectedSort.value != null) {
                    applyFilters()
                } else {
                    _state.value = SearchState.Success(anime = data, hasMore = hasMore, isLoadingMore = false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$q'", e)
            _state.value = SearchState.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Load the next page of results (Phase 5 part 2).
     * Called when the user scrolls to the bottom of the results grid.
     * Appends to the existing Success list. No-op if not Success or already loading.
     */
    fun loadMore() {
        val repo = anilistRepo ?: return
        val current = _state.value as? SearchState.Success ?: return
        if (!current.hasMore || current.isLoadingMore) return
        _state.value = current.copy(isLoadingMore = true)
        viewModelScope.launch {
            try {
                currentPage++
                val nextData = repo.searchAnime(currentQuery, page = currentPage, perPage = 25)
                hasMore = nextData.size >= 25
                allResults = allResults + nextData
                // Re-apply filters to the combined list.
                if (_selectedGenres.value.isNotEmpty() || _selectedYears.value.isNotEmpty() || _selectedFormats.value.isNotEmpty() || _selectedSeasons.value.isNotEmpty() || _selectedStatuses.value.isNotEmpty() || _selectedSort.value != null) {
                    applyFilters()
                    // But re-add the loading=false + hasMore on the filtered result.
                    val filteredState = _state.value as? SearchState.Success
                    if (filteredState != null) {
                        _state.value = filteredState.copy(hasMore = hasMore, isLoadingMore = false)
                    }
                } else {
                    _state.value = SearchState.Success(anime = allResults, hasMore = hasMore, isLoadingMore = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMore failed (page $currentPage)", e)
                _state.value = current.copy(isLoadingMore = false)
            }
        }
    }

    // --- Public API --------------------------------------------------------

    /** Called on every keystroke from the search bar. */
    fun setQuery(q: String) {
        _query.value = q
    }

    /** Tapped a recent-search chip — fills the bar and immediately triggers search. */
    fun selectRecent(term: String) {
        _query.value = term
        viewModelScope.launch { doSearch(term) }
    }

    /**
     * Called when the user presses the keyboard's search/submit button.
     * Triggers the actual search (Phase 5 revamp: was previously a no-op that
     * only saved to recents — now it fires the search).
     */
    fun onSubmit() {
        val term = _query.value.trim()
        val hasActiveFilters = _selectedGenres.value.isNotEmpty() || _selectedYears.value.isNotEmpty() ||
            _selectedFormats.value.isNotEmpty() || _selectedSeasons.value.isNotEmpty() ||
            _selectedStatuses.value.isNotEmpty() || _selectedSort.value != null
        // If in RECENT mode, auto-switch to AniList for the search
        if (_searchMode.value == SearchMode.RECENT) {
            _searchMode.value = SearchMode.ANILIST
        }
        if (term.isNotBlank()) {
            viewModelScope.launch {
                if (_searchMode.value == SearchMode.SOURCES) doSourceSearch(term)
                else doSearch(term)
            }
        } else if (hasActiveFilters) {
            // Empty query + active filters → load trending then apply filters client-side
            if (_searchMode.value == SearchMode.ANILIST) {
                loadAniListBrowse() // loads trending, then applyFilters runs on it
            }
        }
    }

    /** Retry the last search (used by the error-state retry button). Phase 5. */
    fun retry() {
        onSubmit()
    }

    /**
     * Search all installed extension sources (Phase 5 part 4).
     * Results are stored in [_sourceResults] (separate from AniList results).
     * No pagination (extensions return one page at a time; aniyomi's global
     * search is also first-page-only).
     */
    private suspend fun doSourceSearch(q: String) {
        val bridge = sourceBridge
        if (bridge == null) {
            _state.value = SearchState.Error("Sources not available")
            return
        }
        _state.value = SearchState.Loading
        _sourceResults.value = emptyList()
        try {
            val results = bridge.searchAllSources(q)
            _sourceResults.value = results
            _state.value = if (results.isEmpty()) {
                SearchState.Empty
            } else {
                saveRecent(q.trim())
                SearchState.Success(anime = emptyList(), hasMore = false, isLoadingMore = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Source search failed for '$q'", e)
            _state.value = SearchState.Error(e.message ?: "Unknown error")
        }
    }

    /** Wipe all recent searches. */
    fun clearRecent() {
        recentPref?.set(emptyList())
    }

    /**
     * Save a search term to recent searches (newest first, deduped, capped
     * at [MAX_RECENT]).
     */
    private fun saveRecent(term: String) {
        val pref = recentPref ?: return
        val current = pref.get()
        if (current.firstOrNull() == term) return  // already newest — no-op
        val updated = (listOf(term) + current.filter { it != term }).take(MAX_RECENT)
        pref.set(updated)
        Log.d(TAG, "Saved recent search: '$term' (total: ${updated.size})")
    }

    /**
     * Called by the UI when the user taps an anime in the results grid.
     * Saves the current query to recent searches (if not already saved by
     * doSearch).
     */
    fun onAnimeClick(anilistId: Int) {
        // doSearch already saves on success; this is a no-op fallback.
        saveRecent(_query.value.trim())
    }
}

/**
 * Search screen state machine.
 *
 * - [Idle]: query is blank — show recent searches.
 * - [Loading]: debounced query fired, awaiting AniList / cache.
 * - [Success]: cache/AniList returned one or more anime — render the grid.
 * - [Empty]: AniList returned zero matches — show "No results for '$query'".
 * - [Error]: network / GraphQL / DI failure — show message.
 */
sealed class SearchState {
    data object Idle : SearchState()
    data object Loading : SearchState()
    data class Success(
        val anime: List<AniListAnime>,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
    ) : SearchState()
    data object Empty : SearchState()
    data class Error(val message: String) : SearchState()
}

/** Search mode (Phase 5 part 4). */
enum class SearchMode(val label: String) {
    RECENT("Recent"),
    ANILIST("AniList"),
    SOURCES("Extensions"),
}
