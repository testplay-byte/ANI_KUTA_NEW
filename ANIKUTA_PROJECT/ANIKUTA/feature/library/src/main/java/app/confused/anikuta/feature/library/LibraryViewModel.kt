package app.confused.anikuta.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.LibraryDisplayMode
import app.confused.anikuta.core.common.model.LibrarySort
import app.confused.anikuta.core.common.model.LibrarySortType
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.common.repository.CategoryRepository
import app.confused.anikuta.core.player.WatchProgressStore
import app.confused.anikuta.feature.library.LibraryDialog
import app.confused.anikuta.feature.library.CategoryFilter
import app.confused.anikuta.feature.library.ContinueWatchingItem
import app.confused.anikuta.feature.library.LibraryState
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
        // ── Core data flow: library + categories + watch progress ──
        viewModelScope.launch {
            combine(
                animeRepository.observeFavorites(),
                categoryRepository.observeVisible(),
                watchProgressStore.changes,
            ) { animeList, categories, progressMap ->
                Triple(animeList, categories, progressMap)
            }.combine(preferencesFlow()) { (animeList, categories, progressMap), prefs ->
                LibraryData(animeList, categories, progressMap, prefs)
            }.collect { data ->
                val continueWatching = deriveContinueWatching(data.progressMap)
                _state.update { it.copy(
                    isLoading = false,
                    libraryAnime = data.animeList,
                    categories = data.categories,
                    continueWatching = continueWatching,
                    displayMode = prefs.displayMode,
                    columns = prefs.columns,
                    sort = LibrarySort(prefs.sortType, prefs.sortAscending),
                    showContinueWatching = prefs.showContinueWatching,
                    showEpisodeBadge = prefs.showEpisodeBadge,
                    showScoreBadge = prefs.showScoreBadge,
                ) }
            }
        }
    }

    // ── Preferences snapshot ──

    private data class PrefSnapshot(
        val displayMode: LibraryDisplayMode,
        val columns: Int,
        val sortType: LibrarySortType,
        val sortAscending: Boolean,
        val showContinueWatching: Boolean,
        val showEpisodeBadge: Boolean,
        val showScoreBadge: Boolean,
    )

    private data class LibraryData(
        val animeList: List<Anime>,
        val categories: List<app.confused.anikuta.core.common.model.Category>,
        val progressMap: Map<String, WatchProgressStore.Progress>,
        val prefs: PrefSnapshot,
    )

    private fun preferencesFlow(): kotlinx.coroutines.flow.Flow<PrefSnapshot> {
        // Nested combines (kotlinx.coroutines.flow.combine supports max 5 args).
        val displaySort = combine(
            preferences.displayMode().changes(),
            preferences.columnsPortrait().changes(),
            preferences.sortType().changes(),
            preferences.sortAscending().changes(),
        ) { mode, columns, sortType, sortAsc ->
            PrefSnapshot(mode, columns, sortType, sortAsc, true, true, false)
        }
        val badges = combine(
            preferences.showContinueWatching().changes(),
            preferences.showEpisodeBadge().changes(),
            preferences.showScoreBadge().changes(),
        ) { cw, ep, score ->
            Triple(cw, ep, score)
        }
        return combine(displaySort, badges) { snap, (cw, ep, score) ->
            snap.copy(
                showContinueWatching = cw,
                showEpisodeBadge = ep,
                showScoreBadge = score,
            )
        }
    }

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
        _state.update { it.copy(activeFilter = filter) }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        preferences.displayMode().set(mode)
    }

    fun setColumns(columns: Int) {
        preferences.columnsPortrait().set(columns)
    }

    fun setSort(type: LibrarySortType, ascending: Boolean) {
        preferences.sortType().set(type)
        preferences.sortAscending().set(ascending)
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
        val dialog = state.dialog
        viewModelScope.launch {
            try {
                animeIds.forEach { animeId ->
                    categoryRepository.setAnimeCategories(animeId, categoryIds)
                }
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
                    // Set favorite=false (keep the row for history).
                    val anime = animeRepository.getById(id)
                    if (anime != null) {
                        animeRepository.updateFavorite(id, favorite = false, dateAdded = anime.dateAdded)
                        // Also clear category assignments.
                        categoryRepository.setAnimeCategories(id, emptyList())
                    }
                }
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

    /**
     * Update the last-watched timestamp for an anime (called when the user
     * resumes watching from the continue-watching section).
     */
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
