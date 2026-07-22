package app.confused.anikuta.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.LibraryDisplayMode
import app.confused.anikuta.core.common.model.LibrarySortType
import app.confused.anikuta.core.designsystem.component.AddCategoryDialog
import app.confused.anikuta.core.designsystem.component.CategoryPickerDialog
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.component.SearchField
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.library.components.CategoryTabs
import app.confused.anikuta.feature.library.components.ContinueWatchingSection
import app.confused.anikuta.feature.library.components.CustomizeSheet
import app.confused.anikuta.feature.library.components.LibraryEmptyState
import app.confused.anikuta.feature.library.components.LibraryGridCard
import app.confused.anikuta.feature.library.components.LibraryListRow
import app.confused.anikuta.feature.library.components.SelectionActionBar
import app.confused.anikuta.feature.library.components.SortSheet
import org.koin.androidx.compose.koinViewModel

/**
 * The Library screen — the user's personal anime collection.
 *
 * Layout (top to bottom):
 *  1. CollapsingHeader (pinned) — title "Library" + gear (customize) button.
 *  2. Category tabs (pinned) — All + each category. Replaced by Select All/Clear
 *     bar in selection mode.
 *  3. Search field (collapsible — tap search icon to show).
 *  4. Toolbar row — display mode toggle, sort button.
 *  5. LazyVerticalGrid or LazyColumn — the library items. Continue-watching
 *     section is a full-span item at the top (when enabled + non-empty).
 *  6. SelectionActionBar (overlay, bottom) — when in selection mode.
 *
 * Per user decisions:
 *  - Q2: sort is GLOBAL.
 *  - Q3: display mode is GLOBAL.
 *  - Q5: continue-watching is a section at the top (may be removed later).
 *  - Q6: NO status filter.
 *  - Q9: state-based navigation (not Voyager) — the screen takes callbacks.
 */
@Composable
fun LibraryScreen(
    onOpenAnime: (Int) -> Unit,
    onOpenContinueWatching: (ContinueWatchingItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val collapsed = if (state.displayMode == LibraryDisplayMode.LIST) {
        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20
    } else {
        gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 20
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Pinned header ──
            CollapsingHeader(
                title = if (state.selectionMode) "${state.selectedIds.size} selected"
                        else "Library",
                collapsed = collapsed,
                actions = {
                    if (!state.selectionMode) {
                        HeaderActionButton(
                            icon = Icons.Filled.Settings,
                            contentDescription = "Customize",
                            onClick = { viewModel.showCustomizeSheet() },
                        )
                    }
                },
            )

            // ── Category tabs or Select All/Clear bar ──
            if (state.selectionMode) {
                SelectionTopBar(
                    onSelectAll = {
                        val visibleIds = filteredSortedItems(state).map { it.id }
                        viewModel.selectAllVisible(visibleIds)
                    },
                    onClear = { viewModel.clearSelection() },
                )
            } else {
                CategoryTabs(
                    categories = state.categories,
                    activeFilter = state.activeFilter,
                    onSelect = { viewModel.setActiveFilter(it) },
                )
            }

            // ── Search field (collapsible) ──
            if (state.hasActiveSearch) {
                SearchField(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    placeholder = "Search library",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // ── Toolbar row (display mode + sort + search toggle) ──
            if (!state.selectionMode) {
                ToolbarRow(
                    displayMode = state.displayMode,
                    onToggleDisplayMode = {
                        val newMode = if (state.displayMode == LibraryDisplayMode.LIST)
                            LibraryDisplayMode.COMPACT_GRID
                        else LibraryDisplayMode.LIST
                        viewModel.setDisplayMode(newMode)
                    },
                    onSort = { viewModel.showSortSheet() },
                    onSearch = { viewModel.setSearchQuery(if (state.hasActiveSearch) "" else " ") },
                    isSearchActive = state.hasActiveSearch,
                )
            }

            // ── Content ──
            val items = filteredSortedItems(state)
            val showContinueWatching = state.showContinueWatching &&
                state.continueWatching.isNotEmpty() && !state.selectionMode

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Loading…",
                        fontFamily = RobotoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (state.isLibraryEmpty) {
                LibraryEmptyState(isEmpty = true)
            } else if (items.isEmpty()) {
                LibraryEmptyState(isEmpty = false)
            } else {
                when (state.displayMode) {
                    LibraryDisplayMode.LIST -> ListContent(
                        items = items,
                        state = state,
                        listState = listState,
                        showContinueWatching = showContinueWatching,
                        continueWatching = state.continueWatching,
                        onOpenAnime = onOpenAnime,
                        onOpenContinueWatching = {
                            viewModel.updateLastWatched(it.anilistId)
                            onOpenContinueWatching(it)
                        },
                        onItemClick = { anime ->
                            if (state.selectionMode) viewModel.toggleSelection(anime.id)
                            else onOpenAnime(anime.anilistId ?: return@ListContent)
                        },
                        onItemLongClick = { anime ->
                            viewModel.toggleSelection(anime.id)
                        },
                    )
                    LibraryDisplayMode.COMPACT_GRID,
                    LibraryDisplayMode.COMFORTABLE_GRID -> GridContent(
                        items = items,
                        state = state,
                        gridState = gridState,
                        showContinueWatching = showContinueWatching,
                        continueWatching = state.continueWatching,
                        onOpenAnime = onOpenAnime,
                        onOpenContinueWatching = {
                            viewModel.updateLastWatched(it.anilistId)
                            onOpenContinueWatching(it)
                        },
                        onItemClick = { anime ->
                            if (state.selectionMode) viewModel.toggleSelection(anime.id)
                            else onOpenAnime(anime.anilistId ?: return@GridContent)
                        },
                        onItemLongClick = { anime ->
                            viewModel.toggleSelection(anime.id)
                        },
                    )
                }
            }
        }

        // ── Floating selection action bar (overlay) ──
        if (state.selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 90.dp),
            ) {
                SelectionActionBar(
                    selectedCount = state.selectedIds.size,
                    onCancel = { viewModel.clearSelection() },
                    onCategory = { viewModel.showMoveToCategorySheet() },
                    onDelete = { viewModel.showDeleteConfirmation() },
                )
            }
        }

        // ── Sheets + dialogs ──
        when (val dialog = state.dialog) {
            is LibraryDialog.CustomizeSheet -> CustomizeSheet(
                displayMode = state.displayMode,
                columns = state.columns,
                showEpisodeBadge = state.showEpisodeBadge,
                showScoreBadge = state.showScoreBadge,
                showContinueWatching = state.showContinueWatching,
                onDisplayModeChange = { viewModel.setDisplayMode(it) },
                onColumnsChange = { viewModel.setColumns(it) },
                onShowEpisodeBadgeChange = { viewModel.setShowEpisodeBadge(it) },
                onShowScoreBadgeChange = { viewModel.setShowScoreBadge(it) },
                onShowContinueWatchingChange = { viewModel.setShowContinueWatching(it) },
                onDismiss = { viewModel.dismissDialog() },
            )
            is LibraryDialog.SortSheet -> SortSheet(
                sortType = state.sort.type,
                ascending = state.sort.ascending,
                onSortChange = { type, asc -> viewModel.setSort(type, asc) },
                onDismiss = { viewModel.dismissDialog() },
            )
            is LibraryDialog.MoveToCategorySheet -> {
                var showAddCategory by remember { mutableStateOf(false) }
                if (!showAddCategory) {
                    CategoryPickerDialog(
                        categories = state.categories,
                        selectedCategoryIds = emptySet(),
                        onConfirm = { ids -> viewModel.moveSelectedToCategories(ids.toList()) },
                        onDismiss = { viewModel.dismissDialog() },
                        onAddNewCategory = { showAddCategory = true },
                    )
                } else {
                    AddCategoryDialog(
                        onConfirm = { name ->
                            viewModel.createCategory(name)
                            showAddCategory = false
                        },
                        onDismiss = { showAddCategory = false },
                    )
                }
            }
            is LibraryDialog.DeleteConfirmation -> {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { viewModel.dismissDialog() },
                    title = { Text("Remove from Library", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
                    text = { Text("Remove ${dialog.animeIds.size} anime from your library?") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { viewModel.removeSelectedFromLibrary() },
                        ) {
                            Text("Remove", color = MaterialTheme.colorScheme.error, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { viewModel.dismissDialog() }) {
                            Text("Cancel", fontFamily = RobotoFamily, fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
            }
            null -> {}
        }
    }
}

// ── Grid content ──

@Composable
private fun GridContent(
    items: List<Anime>,
    state: LibraryState,
    gridState: LazyGridState,
    showContinueWatching: Boolean,
    continueWatching: List<ContinueWatchingItem>,
    onOpenAnime: (Int) -> Unit,
    onOpenContinueWatching: (ContinueWatchingItem) -> Unit,
    onItemClick: (Anime) -> Unit,
    onItemLongClick: (Anime) -> Unit,
) {
    val columns = if (state.columns == 0) 3 else state.columns.coerceIn(2, 5)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (showContinueWatching) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueWatchingSection(
                    items = continueWatching,
                    onClick = onOpenContinueWatching,
                )
            }
        }
        items(items, key = { it.id }) { anime ->
            LibraryGridCard(
                item = anime,
                selected = anime.id in state.selectedIds,
                selectionMode = state.selectionMode,
                showEpisodeBadge = state.showEpisodeBadge,
                showScoreBadge = state.showScoreBadge,
                onClick = { onItemClick(anime) },
                onLongClick = { onItemLongClick(anime) },
            )
        }
    }
}

// ── List content ──

@Composable
private fun ListContent(
    items: List<Anime>,
    state: LibraryState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    showContinueWatching: Boolean,
    continueWatching: List<ContinueWatchingItem>,
    onOpenAnime: (Int) -> Unit,
    onOpenContinueWatching: (ContinueWatchingItem) -> Unit,
    onItemClick: (Anime) -> Unit,
    onItemLongClick: (Anime) -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (showContinueWatching) {
            item {
                ContinueWatchingSection(
                    items = continueWatching,
                    onClick = onOpenContinueWatching,
                )
            }
        }
        items(items, key = { it.id }) { anime ->
            LibraryListRow(
                item = anime,
                selected = anime.id in state.selectedIds,
                selectionMode = state.selectionMode,
                showEpisodeBadge = state.showEpisodeBadge,
                showScoreBadge = state.showScoreBadge,
                onClick = { onItemClick(anime) },
                onLongClick = { onItemLongClick(anime) },
            )
        }
    }
}

// ── Toolbar row ──

@Composable
private fun ToolbarRow(
    displayMode: LibraryDisplayMode,
    onToggleDisplayMode: () -> Unit,
    onSort: () -> Unit,
    onSearch: () -> Unit,
    isSearchActive: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolbarIconButton(
                icon = if (displayMode == LibraryDisplayMode.LIST) Icons.Filled.Apps
                       else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = "Toggle layout",
                onClick = onToggleDisplayMode,
            )
            Spacer(Modifier.width(8.dp))
            ToolbarIconButton(
                icon = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort",
                onClick = onSort,
            )
        }
        ToolbarIconButton(
            icon = if (isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
            contentDescription = "Search",
            onClick = onSearch,
        )
    }
}

@Composable
private fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun HeaderActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── Selection top bar (replaces CategoryTabs in selection mode) ──

@Composable
private fun SelectionTopBar(
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SelectionPill(text = "Select All", icon = Icons.Filled.SelectAll, onClick = onSelectAll)
        SelectionPill(text = "Clear", icon = Icons.Filled.Close, onClick = onClear)
    }
}

@Composable
private fun SelectionPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, fontFamily = RobotoFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Filtering + sorting (pure functions) ──

/**
 * Apply the current category filter + search query + sort to the library anime.
 * This is computed on every recomposition — for large libraries, consider
 * memoizing or moving to the ViewModel.
 */
private fun filteredSortedItems(state: LibraryState): List<Anime> {
    var result = state.libraryAnime

    // Category filter — NOTE: proper category filtering requires the
    // anime_category junction, which is not yet loaded into LibraryState.
    // For now, all category tabs show the same items. Phase C will wire
    // the junction query (observeAnimeIdsForCategory) into the ViewModel
    // so each tab filters correctly. This is a known gap.
    // (CategoryFilter.All and CategoryFilter.One currently behave the same.)

    // Search filter
    if (state.searchQuery.isNotBlank()) {
        val q = state.searchQuery.trim()
        result = result.filter { it.title.contains(q, ignoreCase = true) }
    }

    // Sort
    val comparator = when (state.sort.type) {
        LibrarySortType.TITLE -> compareBy<Anime> { it.title.lowercase() }
        LibrarySortType.DATE_ADDED -> compareByDescending<Anime> { it.dateAdded }
        LibrarySortType.LAST_WATCHED -> compareByDescending<Anime> { it.lastWatched }
        LibrarySortType.PROGRESS -> compareByDescending<Anime> { it.lastWatched } // proxy; real progress is in WatchProgressStore
        LibrarySortType.TOTAL_EPISODES -> compareByDescending<Anime> { it.totalEpisodes ?: 0 }
    }
    result = if (state.sort.ascending) result.sortedWith(comparator)
             else result.sortedWith(comparator.reversed())

    return result
}
