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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
 *  1. CollapsingHeader (pinned) — title "Library" (or "N in Library" when
 *     showTotalEntries is on) + search button + overflow menu button.
 *  2. Category tabs (pinned) — All + each category. Replaced by Select All/Clear
 *     bar in selection mode.
 *  3. Search field (collapsible — tap search icon to show).
 *  4. LazyVerticalGrid or LazyColumn — the library items. Continue-watching
 *     section is a full-span item at the top (when enabled + non-empty).
 *  5. SelectionActionBar (overlay, bottom) — when in selection mode.
 *
 * Per user decisions:
 *  - Q2: sort is GLOBAL.
 *  - Q3: display mode is GLOBAL.
 *  - Q5: continue-watching is a section at the top.
 *  - Q6: NO status filter.
 *  - Q9: state-based navigation (not Voyager).
 *
 * UI improvements (round 2):
 *  - Combined overflow menu (sort + view mode + customize) in header.
 *  - Search button in header, left of the menu button.
 *  - "N in Library" heading when showTotalEntries is on.
 *  - Category tab filtering now works (uses animeCategoryLinks).
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

    // Header title: "N in Library" when showTotalEntries is on, else "Library".
    val headerTitle = when {
        state.selectionMode -> "${state.selectedIds.size} selected"
        state.showTotalEntries -> "${state.totalEntryCount} in Library"
        else -> "Library"
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Pinned header with combined search + overflow menu ──
            CollapsingHeader(
                title = headerTitle,
                collapsed = collapsed,
                actions = {
                    if (!state.selectionMode) {
                        // Search button (left of the menu button)
                        HeaderActionButton(
                            icon = if (state.hasActiveSearch) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = "Search",
                            onClick = {
                                viewModel.setSearchQuery(if (state.hasActiveSearch) "" else " ")
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        // Combined overflow menu button
                        OverflowMenuButton(
                            displayMode = state.displayMode,
                            onSort = { viewModel.showSortSheet() },
                            onDisplayMode = { viewModel.setDisplayMode(it) },
                            onCustomize = { viewModel.showCustomizeSheet() },
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
                    LibraryDisplayMode.COMFORTABLE_GRID,
                    LibraryDisplayMode.COVER_ONLY -> GridContent(
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
                episodeBadgeMode = state.episodeBadgeMode,
                showScoreBadge = state.showScoreBadge,
                showContinueWatching = state.showContinueWatching,
                showTotalEntries = state.showTotalEntries,
                onDisplayModeChange = { viewModel.setDisplayMode(it) },
                onColumnsChange = { viewModel.setColumns(it) },
                onEpisodeBadgeModeChange = { viewModel.setEpisodeBadgeMode(it) },
                onShowScoreBadgeChange = { viewModel.setShowScoreBadge(it) },
                onShowContinueWatchingChange = { viewModel.setShowContinueWatching(it) },
                onShowTotalEntriesChange = { viewModel.setShowTotalEntries(it) },
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
                displayMode = state.displayMode,
                episodeBadgeMode = state.episodeBadgeMode,
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
                episodeBadgeMode = state.episodeBadgeMode,
                showScoreBadge = state.showScoreBadge,
                onClick = { onItemClick(anime) },
                onLongClick = { onItemLongClick(anime) },
            )
        }
    }
}

// ── Overflow menu (combined sort + view mode + customize) ──

@Composable
private fun OverflowMenuButton(
    displayMode: LibraryDisplayMode,
    onSort: () -> Unit,
    onDisplayMode: (LibraryDisplayMode) -> Unit,
    onCustomize: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        HeaderActionButton(
            icon = Icons.Filled.Tune,
            contentDescription = "Library options",
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // Sort
            DropdownMenuItem(
                text = { Text("Sort", fontFamily = RobotoFamily, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Outlined.Sort, contentDescription = null) },
                onClick = {
                    expanded = false
                    onSort()
                },
            )
            // Display mode section
            DropdownMenuItem(
                text = { Text("Compact Grid", fontFamily = RobotoFamily, fontWeight = if (displayMode == LibraryDisplayMode.COMPACT_GRID) FontWeight.ExtraBold else FontWeight.SemiBold) },
                leadingIcon = { if (displayMode == LibraryDisplayMode.COMPACT_GRID) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) else Icon(Icons.Outlined.GridView, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDisplayMode(LibraryDisplayMode.COMPACT_GRID)
                },
            )
            DropdownMenuItem(
                text = { Text("Comfortable Grid", fontFamily = RobotoFamily, fontWeight = if (displayMode == LibraryDisplayMode.COMFORTABLE_GRID) FontWeight.ExtraBold else FontWeight.SemiBold) },
                leadingIcon = { if (displayMode == LibraryDisplayMode.COMFORTABLE_GRID) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) else Icon(Icons.Outlined.GridView, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDisplayMode(LibraryDisplayMode.COMFORTABLE_GRID)
                },
            )
            DropdownMenuItem(
                text = { Text("Cover Only", fontFamily = RobotoFamily, fontWeight = if (displayMode == LibraryDisplayMode.COVER_ONLY) FontWeight.ExtraBold else FontWeight.SemiBold) },
                leadingIcon = { if (displayMode == LibraryDisplayMode.COVER_ONLY) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) else Icon(Icons.Outlined.GridView, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDisplayMode(LibraryDisplayMode.COVER_ONLY)
                },
            )
            DropdownMenuItem(
                text = { Text("List", fontFamily = RobotoFamily, fontWeight = if (displayMode == LibraryDisplayMode.LIST) FontWeight.ExtraBold else FontWeight.SemiBold) },
                leadingIcon = { if (displayMode == LibraryDisplayMode.LIST) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) else Icon(Icons.Outlined.GridView, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDisplayMode(LibraryDisplayMode.LIST)
                },
            )
            // Customize
            DropdownMenuItem(
                text = { Text("Customize", fontFamily = RobotoFamily, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                onClick = {
                    expanded = false
                    onCustomize()
                },
            )
        }
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

// ── Filtering + sorting (uses animeCategoryLinks for category filtering) ──

/**
 * Apply the current category filter + search query + sort to the library anime.
 *
 * Category filtering uses [LibraryState.animeCategoryLinks] — a map from
 * animeId to the set of categoryIds it belongs to. When the active filter is
 * [CategoryFilter.One], only anime whose id maps to a set containing the
 * selected category's id are shown.
 */
private fun filteredSortedItems(state: LibraryState): List<Anime> {
    var result = state.libraryAnime

    // Category filter — uses the anime_category junction data.
    result = when (state.activeFilter) {
        is CategoryFilter.All -> result
        is CategoryFilter.One -> {
            val categoryId = (state.activeFilter as CategoryFilter.One).category.id
            result.filter { anime ->
                state.animeCategoryLinks[anime.id]?.contains(categoryId) == true
            }
        }
    }

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
        LibrarySortType.PROGRESS -> compareByDescending<Anime> { it.lastWatched }
        LibrarySortType.TOTAL_EPISODES -> compareByDescending<Anime> { it.totalEpisodes ?: 0 }
    }
    result = if (state.sort.ascending) result.sortedWith(comparator)
             else result.sortedWith(comparator.reversed())

    return result
}
