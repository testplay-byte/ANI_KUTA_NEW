package app.confused.anikuta.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.EpisodeBadgeMode
import app.confused.anikuta.core.common.model.LibraryDisplayMode
import app.confused.anikuta.core.common.model.LibrarySort
import app.confused.anikuta.core.common.model.LibrarySortType
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.common.repository.CategoryRepository
import app.confused.anikuta.core.player.WatchProgressStore
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Library page.
 *
 * Combines:
 *  - [AnimeRepository.observeFavorites] — the library anime (SQLDelight).
 *  - [CategoryRepository.observeVisible] — non-hidden categories (SQLDelight).
 *  - [CategoryRepository.observeAllLinks] — anime↔category junction (for tab filtering).
 *  - [WatchProgressStore.changes] — watch progress (JSON-in-prefs) for
 *    continue-watching + progress badges.
 *  - [LibraryPreferences] — display mode, sort, columns, badge toggles.
 *
 * Per user decisions:
 *  - Q2: sort is GLOBAL.
 *  - Q3: display mode is GLOBAL.
 *  - Q5: continue-watching is a section at the top (may be removed later).
 *  - Q6: NO status filter — the 5 keywords are category-name suggestions only.
 *
 * Selection lives here (not on items) — keyed by [Anime.id] (animes._id).
 *
 * Per `RULES/ai-agent-rules.md` §3: this ViewModel calls Repositories only.
 */
class LibraryViewModel(
    private val animeRepository: AnimeRepository,
    private val categoryRepository: CategoryRepository,
    private val watchProgressStore: WatchProgressStore,
    private val preferences: LibraryPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        // ── Core data flow: library + categories + links + watch progress ──
        viewModelScope.launch {
            try {
                combine(
                    animeRepository.observeFavorites(),
                    categoryRepository.observeVisible(),
                    categoryRepository.observeAllLinks(),
                    watchProgressStore.changes,
                ) { animeList, categories, links, progressMap ->
                    LibraryData(animeList, categories, links, progressMap)
                }.combine(preferencesFlow()) { data, prefs ->
                    Pair(data, prefs)
                }.collect { (data, prefs) ->
                    val continueWatching = deriveContinueWatching(data.progressMap)
                    val animeCategoryLinks = buildAnimeCategoryMap(data.links)
                    Log.d(TAG, "State updated: ${data.animeList.size} anime, ${data.categories.size} categories, ${data.links.size} links")
                    _state.update { it.copy(
                        isLoading = false,
                        libraryAnime = data.animeList,
                        categories = data.categories,
                        animeCategoryLinks = animeCategoryLinks,
                        continueWatching = continueWatching,
                        displayMode = prefs.displayMode,
                        columns = prefs.columns,
                        sort = LibrarySort(prefs.sortType, prefs.sortAscending),
                        showContinueWatching = prefs.showContinueWatching,
                        episodeBadgeMode = prefs.episodeBadgeMode,
                        showScoreBadge = prefs.showScoreBadge,
                        showTotalEntries = prefs.showTotalEntries,
                    ) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to collect library data flow", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    // ── Internal data holders ──

    private data class PrefSnapshot(
        val displayMode: LibraryDisplayMode,
        val columns: Int,
        val sortType: LibrarySortType,
        val sortAscending: Boolean,
        val showContinueWatching: Boolean,
        val episodeBadgeMode: EpisodeBadgeMode,
        val showScoreBadge: Boolean,
        val showTotalEntries: Boolean,
    )

    private data class LibraryData(
        val animeList: List<Anime>,
        val categories: List<app.confused.anikuta.core.common.model.Category>,
        val links: List<app.confused.anikuta.core.common.model.AnimeCategoryLink>,
        val progressMap: Map<String, WatchProgressStore.Progress>,
    )

    private fun buildAnimeCategoryMap(
        links: List<app.confused.anikuta.core.common.model.AnimeCategoryLink>,
    ): Map<Long, Set<Long>> {
        val map = mutableMapOf<Long, MutableSet<Long>>()
        for (link in links) {
            map.getOrPut(link.animeId) { mutableSetOf() }.add(link.categoryId)
        }
        return map.mapValues { it.value.toSet() }
    }

    private fun preferencesFlow(): kotlinx.coroutines.flow.Flow<PrefSnapshot> {
        // Nested combines (kotlinx.coroutines.flow.combine supports max 5 args).
        val displaySort = combine(
            preferences.displayMode().changes(),
            preferences.columnsPortrait().changes(),
            preferences.sortType().changes(),
            preferences.sortAscending().changes(),
        ) { mode, columns, sortType, sortAsc ->
            PrefSnapshot(mode, columns, sortType, sortAsc, true, EpisodeBadgeMode.RELEASED, false, false)
        }
        val badges = combine(
            preferences.showContinueWatching().changes(),
            preferences.episodeBadgeMode().changes(),
            preferences.showScoreBadge().changes(),
            preferences.showTotalEntries().changes(),
        ) { cw, epMode, score, total ->
            Quad(cw, epMode, score, total)
        }
        return combine(displaySort, badges) { snap, (cw, epMode, score, total) ->
            snap.copy(
                showContinueWatching = cw,
                episodeBadgeMode = epMode,
                showScoreBadge = score,
                showTotalEntries = total,
            )
        }
    }

    private data class Quad<T1, T2, T3, T4>(
        val a: T1, val b: T2, val c: T3, val d: T4,
    )

    // ── Continue-watching derivation ──

    private fun deriveContinueWatching(progressMap: Map<String, WatchProgressStore.Progress>): List<ContinueWatchingItem> {
        // Group by anilistId, pick the most-recently-watched episode per anime.
        val byAnime = mutableMapOf<Int, MutableList<Map.Entry<String, WatchProgressStore.Progress>>>()
        for (entry in progressMap.entries) {
            val anilistId = entry.key.substringBefore(':').toIntOrNull() ?: continue
            byAnime.getOrPut(anilistId) { mutableListOf() }.add(entry)
        }
        return byAnime.map { (anilistId, entries) ->
            val latest = entries.maxByOrNull { it.value.updatedAt } ?: return@map null
            val progress = latest.value
            val ratio = if (progress.durationSeconds > 0) {
                (progress.positionSeconds.toFloat() / progress.durationSeconds.toFloat()).coerceIn(0f, 1f)
            } else 0f
            ContinueWatchingItem(
                anilistId = anilistId,
                animeTitle = progress.animeTitle ?: "Unknown",
                coverUrl = progress.coverUrl,
                episodeNumber = if (progress.episodeNumber >= 0) progress.episodeNumber.toInt() else -1,
                episodeTitle = progress.title,
                progress = ratio,
                lastWatchedAt = progress.updatedAt,
            )
        }.filterNotNull()
            .sortedByDescending { it.lastWatchedAt }
            .take(20)  // cap at 20 items
    }

    // ── Public actions ──

    fun setActiveFilter(filter: CategoryFilter) {
        Log.d(TAG, "setActiveFilter: $filter")
        _state.update { it.copy(activeFilter = filter) }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        Log.d(TAG, "setDisplayMode: $mode")
        preferences.displayMode().set(mode)
    }

    fun setColumns(columns: Int) {
        preferences.columnsPortrait().set(columns)
    }

    fun setSort(type: LibrarySortType, ascending: Boolean) {
        preferences.sortType().set(type)
        preferences.sortAscending().set(ascending)
    }

    fun setEpisodeBadgeMode(mode: EpisodeBadgeMode) {
        preferences.episodeBadgeMode().set(mode)
    }

    fun setShowScoreBadge(enabled: Boolean) {
        preferences.showScoreBadge().set(enabled)
    }

    fun setShowContinueWatching(enabled: Boolean) {
        preferences.showContinueWatching().set(enabled)
    }

    fun setShowTotalEntries(enabled: Boolean) {
        preferences.showTotalEntries().set(enabled)
    }

    fun createCategory(name: String) {
        viewModelScope.launch {
            try {
                categoryRepository.create(name)
                Log.d(TAG, "createCategory: '$name' created")
            } catch (e: Exception) {
                Log.e(TAG, "createCategory failed for '$name'", e)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun toggleSelection(animeId: Long) {
        _state.update { s ->
            val newSelection = if (animeId in s.selectedIds) {
                s.selectedIds - animeId
            } else {
                s.selectedIds + animeId
            }
            s.copy(
                selectedIds = newSelection,
                selectionMode = newSelection.isNotEmpty(),
            )
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedIds = emptySet(), selectionMode = false) }
    }

    fun selectAllVisible(visibleIds: List<Long>) {
        _state.update { it.copy(selectedIds = visibleIds.toSet(), selectionMode = true) }
    }

    fun showCustomizeSheet() {
        _state.update { it.copy(dialog = LibraryDialog.CustomizeSheet) }
    }

    fun showSortSheet() {
        _state.update { it.copy(dialog = LibraryDialog.SortSheet) }
    }

    fun showMoveToCategorySheet() {
        val ids = _state.value.selectedIds.toList()
        if (ids.isNotEmpty()) {
            _state.update { it.copy(dialog = LibraryDialog.MoveToCategorySheet(ids)) }
        }
    }

    fun showDeleteConfirmation() {
        val ids = _state.value.selectedIds.toList()
        if (ids.isNotEmpty()) {
            _state.update { it.copy(dialog = LibraryDialog.DeleteConfirmation(ids)) }
        }
    }

    fun dismissDialog() {
        _state.update { it.copy(dialog = null) }
    }

    fun moveSelectedToCategories(categoryIds: List<Long>) {
        val state = _state.value
        val animeIds = state.selectedIds
        viewModelScope.launch {
            try {
                animeIds.forEach { animeId ->
                    categoryRepository.setAnimeCategories(animeId, categoryIds)
                }
                Log.d(TAG, "moveSelectedToCategories: ${animeIds.size} anime → categories $categoryIds")
            } catch (e: Exception) {
                Log.e(TAG, "moveSelectedToCategories failed", e)
            }
            _state.update {
                it.copy(
                    dialog = null,
                    selectedIds = emptySet(),
                    selectionMode = false,
                )
            }
        }
    }

    fun removeSelectedFromLibrary() {
        val animeIds = _state.value.selectedIds
        viewModelScope.launch {
            try {
                animeIds.forEach { id ->
                    val anime = animeRepository.getById(id)
                    if (anime != null) {
                        animeRepository.updateFavorite(id, favorite = false, dateAdded = anime.dateAdded)
                        categoryRepository.setAnimeCategories(id, emptyList())
                    }
                }
                Log.d(TAG, "removeSelectedFromLibrary: ${animeIds.size} anime removed")
            } catch (e: Exception) {
                Log.e(TAG, "removeSelectedFromLibrary failed", e)
            }
            _state.update {
                it.copy(
                    dialog = null,
                    selectedIds = emptySet(),
                    selectionMode = false,
                )
            }
        }
    }

    fun updateLastWatched(anilistId: Int) {
        viewModelScope.launch {
            try {
                animeRepository.updateLastWatchedByAnilistId(anilistId, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.w(TAG, "updateLastWatched failed for anilistId=$anilistId", e)
            }
        }
    }

    companion object {
        private const val TAG = "AnikutaLibVM"
    }
}
