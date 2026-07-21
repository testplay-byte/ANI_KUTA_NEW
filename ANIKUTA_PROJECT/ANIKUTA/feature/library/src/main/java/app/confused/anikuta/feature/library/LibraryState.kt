package app.confused.anikuta.feature.library

import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.Category
import app.confused.anikuta.core.common.model.LibraryDisplayMode
import app.confused.anikuta.core.common.model.LibrarySort
import androidx.compose.runtime.Immutable

/**
 * The active category filter for the library page.
 *
 * - [All] shows every library anime regardless of category.
 * - [One] restricts to a single category.
 */
sealed interface CategoryFilter {
    data object All : CategoryFilter
    data class One(val category: Category) : CategoryFilter
}

/**
 * A continue-watching item derived from [WatchProgressStore].
 *
 * One per anime — picks the most-recently-watched episode for that anime.
 */
@Immutable
data class ContinueWatchingItem(
    val anilistId: Int,
    val animeTitle: String,
    val coverUrl: String?,
    val episodeNumber: Int,
    val episodeTitle: String,
    val progress: Float,        // 0..1
    val lastWatchedAt: Long,
)

/**
 * The UI state for the library page.
 *
 * Single source of truth — the ViewModel exposes a [StateFlow] of this.
 * The screen composable collects it and derives display items via
 * [filteredSortedItems].
 */
@Immutable
data class LibraryState(
    val isLoading: Boolean = true,
    val libraryAnime: List<Anime> = emptyList(),
    val categories: List<Category> = emptyList(),
    val activeFilter: CategoryFilter = CategoryFilter.All,
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.COMPACT_GRID,
    val columns: Int = 3,           // 0 = auto (adaptive)
    val sort: LibrarySort = LibrarySort.DEFAULT,
    val searchQuery: String = "",
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),     // animes._id
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val showContinueWatching: Boolean = true,
    val showEpisodeBadge: Boolean = true,
    val showScoreBadge: Boolean = false,
    val dialog: LibraryDialog? = null,
) {
    val isLibraryEmpty: Boolean get() = libraryAnime.isEmpty()
    val hasActiveSearch: Boolean get() = searchQuery.isNotBlank()
}

/**
 * Dialog/sheet state — a sealed type so the screen's `when` is exhaustive.
 */
sealed interface LibraryDialog {
    data object CustomizeSheet : LibraryDialog
    data object SortSheet : LibraryDialog
    /** Category picker for selection-mode "move to category". */
    data class MoveToCategorySheet(val animeIds: List<Long>) : LibraryDialog
    /** Delete confirmation. */
    data class DeleteConfirmation(val animeIds: List<Long>) : LibraryDialog
}
