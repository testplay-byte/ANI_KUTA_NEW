package app.anikuta.ui.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.player.WatchProgressStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 5 task 5.8 — ViewModel for the Library screen.
 *
 * Loads saved anime from [LibraryStore], exposes them as a [LibraryState]
 * StateFlow, and supports re-sorting via [SortMode] (Q4 decision: Title /
 * Last watched / Unread episodes — no filters).
 *
 * Crash-resistant: if DI fails or loading throws, exposes an Error state
 * instead of crashing (same pattern as [app.anikuta.ui.home.HomeViewModel]
 * and [app.anikuta.ui.detail.DetailViewModel]).
 */
class LibraryViewModel : ViewModel() {

    companion object {
        private const val TAG = "LibraryViewModel"
    }

    private val libraryStore: LibraryStore? = try {
        Injekt.get<LibraryStore>().also { Log.d(TAG, "✅ LibraryStore obtained") }
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to get LibraryStore from DI", e); null
    }

    // Used for the LAST_WATCHED sort — we look up the most recent
    // updatedAt timestamp across all episodes of each saved anime.
    private val watchProgressStore: WatchProgressStore? = try {
        Injekt.get<WatchProgressStore>()
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to get WatchProgressStore from DI", e); null
    }

    // Phase 4 — categories
    private val categoryStore: CategoryStore? = try {
        Injekt.get<CategoryStore>()
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to get CategoryStore from DI", e); null
    }

    // Phase B — sub/dub cache
    private val subDubStore: app.anikuta.data.cache.SubDubStore? = try {
        Injekt.get()
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to get SubDubStore from DI", e); null
    }

    // Phase E — persisted display customization
    private val displayPrefs: LibraryDisplayPrefs? = try {
        Injekt.get<LibraryDisplayPrefs>()
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to get LibraryDisplayPrefs from DI", e); null
    }

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.TITLE)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    /** Sort direction (Phase H). True = ascending, False = descending. */
    private val _sortAscending = MutableStateFlow(true)
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Persisted display settings (Phase E).
     * Reactive — updates when the user changes any setting in the customization sheet.
     * Survives screen switches + app restarts (was in-memory before, which reset on tab switch).
     */
    val displaySettings: StateFlow<LibraryDisplayPrefs.Settings> = displayPrefs?.let { prefs ->
        kotlinx.coroutines.flow.MutableStateFlow(prefs.getSettings()).also { mutable ->
            viewModelScope.launch {
                prefs.changes.collect { mutable.value = it }
            }
        }
    } ?: kotlinx.coroutines.flow.MutableStateFlow(
        LibraryDisplayPrefs.Settings(
            displayMode = LibraryDisplayPrefs.DisplayMode.GRID,
            gridColumns = 2,
            titlePosition = LibraryDisplayPrefs.TitlePosition.BELOW,
            titleMaxLines = 2,
            showRating = true,
            showYear = true,
            showEpisodes = true,
            showSubDub = true,
            showUnwatchedBadge = true,
            cardBorder = LibraryDisplayPrefs.CardBorder.THIN,
        )
    )

    /** Categories (Default + user-created). Phase 4. */
    private val _categories = MutableStateFlow<List<CategoryStore.Category>>(emptyList())
    val categories: StateFlow<List<CategoryStore.Category>> = _categories.asStateFlow()

    /**
     * Unwatched episode counts per anime (Phase 4 part 3).
     * Key = AniList ID, value = number of episodes with progress below 85%.
     * Updated reactively from WatchProgressStore.changes.
     */
    private val _unwatchedCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val unwatchedCounts: StateFlow<Map<Int, Int>> = _unwatchedCounts.asStateFlow()

    /**
     * Sub/Dub info per anime (Phase B).
     * Key = AniList ID, value = SubDubInfo (hasSub, hasDub, subCount, dubCount).
     * Populated when the user opens the Detail page (DetailViewModel.resolveVideos).
     */
    private val _subDubInfo = MutableStateFlow<Map<Int, app.anikuta.data.cache.SubDubStore.SubDubInfo>>(emptyMap())
    val subDubInfo: StateFlow<Map<Int, app.anikuta.data.cache.SubDubStore.SubDubInfo>> = _subDubInfo.asStateFlow()

    /** Currently selected category tab (id). Default = 0 (the "Default" category). */
    private val _selectedCategoryId = MutableStateFlow(0L)
    val selectedCategoryId: StateFlow<Long> = _selectedCategoryId.asStateFlow()

    /** Anime→category assignments (AniList ID → set of category IDs). */
    private var categoryAssignments: Map<String, Set<Long>> = emptyMap()

    /** The full unfiltered anime list (before category filtering). */
    private var allAnime: List<AniListAnime> = emptyList()

    /** Set the display mode (GRID or LIST). Persisted. */
    fun setDisplayMode(mode: LibraryDisplayPrefs.DisplayMode) {
        displayPrefs?.setDisplayMode(mode)
    }

    /** Set the number of grid columns (2-5). Persisted. */
    fun setGridColumns(columns: Int) {
        displayPrefs?.setGridColumns(columns)
    }

    /** Set the title position (BELOW or OVERLAY on the cover). Persisted. */
    fun setTitlePosition(pos: LibraryDisplayPrefs.TitlePosition) {
        displayPrefs?.setTitlePosition(pos)
    }

    /** Set the max lines for the title (1-3). Persisted. */
    fun setTitleMaxLines(lines: Int) {
        displayPrefs?.setTitleMaxLines(lines)
    }

    fun setShowRating(show: Boolean) = displayPrefs?.setShowRating(show) ?: Unit
    fun setShowYear(show: Boolean) = displayPrefs?.setShowYear(show) ?: Unit
    fun setShowEpisodes(show: Boolean) = displayPrefs?.setShowEpisodes(show) ?: Unit
    fun setShowSubDub(show: Boolean) = displayPrefs?.setShowSubDub(show) ?: Unit
    fun setShowUnwatchedBadge(show: Boolean) = displayPrefs?.setShowUnwatchedBadge(show) ?: Unit
    fun setCardBorder(border: LibraryDisplayPrefs.CardBorder) = displayPrefs?.setCardBorder(border) ?: Unit

    /** Select a category tab. Re-filters the anime list. */
    fun selectCategory(id: Long) {
        _selectedCategoryId.value = id
        applyFilterAndSort()
    }

    /** Create a new category. Returns the new category's id. */
    fun createCategory(name: String): Long {
        return categoryStore?.createCategory(name) ?: -1L
    }

    /** Rename a category. */
    fun renameCategory(id: Long, newName: String) {
        categoryStore?.renameCategory(id, newName)
    }

    /** Delete a category. Can't delete Default (id=0). */
    fun deleteCategory(id: Long) {
        categoryStore?.deleteCategory(id)
        if (_selectedCategoryId.value == id) {
            _selectedCategoryId.value = 0L
        }
    }

    /** Apply the current category filter + sort to [allAnime] and push to [_state]. */
    private fun applyFilterAndSort() {
        val selected = _selectedCategoryId.value
        val filtered = if (selected == 0L) {
            // Default category = show all (anime not assigned to any custom category
            // OR assigned to Default). For simplicity, "Default" shows everything.
            allAnime
        } else {
            allAnime.filter { anime ->
                val cats = categoryAssignments[anime.id.toString()] ?: emptySet()
                cats.contains(selected)
            }
        }
        _state.value = if (filtered.isEmpty() && allAnime.isNotEmpty()) {
            LibraryState.Empty
        } else if (filtered.isEmpty()) {
            LibraryState.Empty
        } else {
            LibraryState.Success(sort(filtered))
        }
    }

    init {
        // Collect from LibraryStore.changes so the Library page updates in
        // real time when the user saves/removes an anime on the detail page.
        viewModelScope.launch {
            val store = libraryStore
            if (store == null) {
                _state.value = LibraryState.Error("App not properly initialized")
                return@launch
            }
            store.changes.collect { anime ->
                allAnime = anime
                applyFilterAndSort()
            }
        }
        // Phase 4 — collect categories + assignments reactively.
        viewModelScope.launch {
            val catStore = categoryStore ?: return@launch
            catStore.changes.collect { state ->
                _categories.value = state.categories
                categoryAssignments = state.assignments
                applyFilterAndSort()
            }
        }
        // Phase 4 part 3 — collect watch progress to compute unwatched counts.
        // An episode is "unwatched" if its progress fraction < 0.85 (the
        // watched threshold). Count per anime, expose as Map<anilistId, count>.
        viewModelScope.launch {
            val progressStore = watchProgressStore ?: return@launch
            progressStore.changes.collect { all ->
                val counts = HashMap<Int, Int>()
                for ((key, progress) in all) {
                    val anilistId = key.substringBefore(':').toIntOrNull() ?: continue
                    val fraction = if (progress.durationSeconds > 0) {
                        (progress.positionSeconds.toFloat() / progress.durationSeconds.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    // Below 85% = unwatched (in progress). Count it.
                    if (fraction < 0.85f) {
                        counts[anilistId] = (counts[anilistId] ?: 0) + 1
                    }
                }
                _unwatchedCounts.value = counts
            }
        }
        // Phase B — collect sub/dub info reactively.
        viewModelScope.launch {
            val store = subDubStore ?: return@launch
            store.changes.collect { all ->
                _subDubInfo.value = all.mapKeys { (k, _) -> k.toIntOrNull() ?: 0 }
            }
        }
    }

    /** Reload the library from the store and re-apply the current sort. */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val store = libraryStore ?: return@launch
                allAnime = store.getAll()
                applyFilterAndSort()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Switch sort mode and re-sort the currently-loaded list. */
    fun setSort(mode: SortMode) {
        if (mode == _sortMode.value) {
            // Same sort tapped again → invert direction
            _sortAscending.value = !_sortAscending.value
        } else {
            _sortMode.value = mode
            _sortAscending.value = true // default to ascending on new sort
        }
        applyFilterAndSort()
    }

    /**
     * Sort the list per [_sortMode].
     *
     * - TITLE: alphabetical by preferred title (case-insensitive).
     * - LAST_WATCHED: by most-recent watch-progress timestamp across all
     *   episodes of each anime (desc). Anime with no progress sort last,
     *   preserving their relative order.
     *
     *   Phase 1 fix: previously scanned all progress keys PER ANIME (O(n×m)).
     *   Now builds a `Map<Int, Long>` (anilistId → maxUpdatedAt) ONCE per sort,
     *   then looks up — O(n + m).
     *
     * - UNREAD: best-effort — we don't yet track seen-episode counts for
     *   saved anime, so we sort by total episode count descending as a
     *   proxy (more episodes → likely more to watch). Will be refined when
     *   seen-episode tracking lands (Phase 4).
     */
    private fun sort(anime: List<AniListAnime>): List<AniListAnime> {
        val ascending = _sortAscending.value
        return when (_sortMode.value) {
            SortMode.TITLE -> if (ascending) {
                anime.sortedBy { it.title.preferred().lowercase() }
            } else {
                anime.sortedByDescending { it.title.preferred().lowercase() }
            }
            SortMode.LAST_WATCHED -> {
                val progress = watchProgressStore?.getAll().orEmpty()
                val maxUpdatedAtByAnime = HashMap<Int, Long>(progress.size)
                for (key in progress.keys) {
                    val anilistId = key.substringBefore(':').toIntOrNull() ?: continue
                    val updatedAt = progress[key]?.updatedAt ?: 0L
                    val current = maxUpdatedAtByAnime[anilistId] ?: 0L
                    if (updatedAt > current) {
                        maxUpdatedAtByAnime[anilistId] = updatedAt
                    }
                }
                if (ascending) {
                    anime.sortedBy { maxUpdatedAtByAnime[it.id] ?: 0L }
                } else {
                    anime.sortedByDescending { maxUpdatedAtByAnime[it.id] ?: 0L }
                }
            }
            SortMode.UNREAD -> if (ascending) {
                anime.sortedBy { it.episodes ?: 0 }
            } else {
                anime.sortedByDescending { it.episodes ?: 0 }
            }
        }
    }
}

/** UI state for the Library screen. */
sealed class LibraryState {
    data object Loading : LibraryState()
    data class Success(val anime: List<AniListAnime>) : LibraryState()
    data object Empty : LibraryState()
    data class Error(val message: String) : LibraryState()
}

/** Sort modes for the Library screen (Q4 decision). */
enum class SortMode(val label: String) {
    TITLE("Title"),
    LAST_WATCHED("Last watched"),
    UNREAD("Unread episodes"),
}
