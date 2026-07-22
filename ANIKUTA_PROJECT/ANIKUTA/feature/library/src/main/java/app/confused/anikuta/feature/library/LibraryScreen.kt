package app.confused.anikuta.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.LibraryDisplayMode
import app.confused.anikuta.core.common.model.LibrarySortType
import app.confused.anikuta.core.designsystem.component.AddCategoryDialog
import app.confused.anikuta.core.designsystem.component.AnikutaBottomSheet
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
 *     showTotalEntries is on) + pill-shaped search button + options (Tune) button.
 *  2. Category tabs (pinned) — All + each category.
 *  3. Search bar (animated — appears when the search pill is tapped).
 *  4. LazyVerticalGrid or LazyColumn — the library items.
 *  5. SelectionActionBar (overlay, bottom) — when in selection mode.
 *
 * UI improvements (round 3):
 *  - Search button is a wider pill with "Search" text + icon. Tapping it
 *    transitions into a full-width search bar with keyboard.
 *  - Options button opens a bottom-up sheet (not a dropdown) with Sort,
 *    Display modes, and Customize entry.
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

    val headerTitle = when {
        state.selectionMode -> "${state.selectedIds.size} selected"
        state.showTotalEntries -> "${state.totalEntryCount} in Library"
        else -> "Library"
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Pinned header ──
            // Search bar is SEPARATE from the header (not inside CollapsingHeader
            // actions). It sits in its own row below the title, aligned to the
            // right side, with the settings button to its right.
            CollapsingHeader(
                title = headerTitle,
                collapsed = collapsed,
                actions = {
                    if (!state.selectionMode) {
                        // Options button (settings) — on the far right
                        HeaderActionButton(
                            icon = Icons.Filled.Tune,
                            contentDescription = "Library options",
                            onClick = { viewModel.showOptionsSheet() },
                        )
                    }
                },
            )

            // ── Search bar row — separate from header, right-aligned ──
            // The search icon button toggles an expandable text field.
            // When active, the text field fills the width (left of the settings button).
            if (!state.selectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.hasActiveSearch) {
                        // Active search: text field fills width + close button
                        SearchField(
                            query = state.searchQuery.trim(),
                            onQueryChange = { viewModel.setSearchQuery(it) },
                            placeholder = "Search library",
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        HeaderActionButton(
                            icon = Icons.Filled.Close,
                            contentDescription = "Close search",
                            onClick = { viewModel.setSearchQuery("") },
                        )
                    } else {
                        // Inactive: search icon button on the right
                        Spacer(Modifier.weight(1f))
                        HeaderActionButton(
                            icon = Icons.Filled.Search,
                            contentDescription = "Search library",
                            onClick = { viewModel.setSearchQuery(" ") }, // activate search mode
                        )
                    }
                }
            }

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
            is LibraryDialog.OptionsSheet -> OptionsSheet(
                displayMode = state.displayMode,
                onSort = { viewModel.showSortSheet() },
                onDisplayMode = {
                    viewModel.setDisplayMode(it)
                },
                onCustomize = { viewModel.showCustomizeSheet() },
                onDismiss = { viewModel.dismissDialog() },
            )
            is LibraryDialog.CustomizeSheet -> CustomizeSheet(
                displayMode = state.displayMode,
                columns = state.columns,
                episodeBadgeMode = state.episodeBadgeMode,
                showScoreBadge = state.showScoreBadge,
                showContinueWatching = state.showContinueWatching,
                showTotalEntries = state.showTotalEntries,
                titleLines = state.titleLines,
                onDisplayModeChange = { viewModel.setDisplayMode(it) },
                onColumnsChange = { viewModel.setColumns(it) },
                onEpisodeBadgeModeChange = { viewModel.setEpisodeBadgeMode(it) },
                onShowScoreBadgeChange = { viewModel.setShowScoreBadge(it) },
                onShowContinueWatchingChange = { viewModel.setShowContinueWatching(it) },
                onShowTotalEntriesChange = { viewModel.setShowTotalEntries(it) },
                onTitleLinesChange = { viewModel.setTitleLines(it) },
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
                titleLines = state.titleLines,
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
                titleLines = state.titleLines,
                onClick = { onItemClick(anime) },
                onLongClick = { onItemLongClick(anime) },
            )
        }
    }
}

// ── Pill-shaped search button ──

@Composable
private fun SearchPillButton(
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isActive) Icons.Filled.Close else Icons.Filled.Search,
                contentDescription = if (isActive) "Close search" else "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Search",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Options bottom-up sheet (replaces dropdown) ──

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun OptionsSheet(
    displayMode: LibraryDisplayMode,
    onSort: () -> Unit,
    onDisplayMode: (LibraryDisplayMode) -> Unit,
    onCustomize: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnikutaBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Library Options",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))

            // Sort entry
            OptionSheetRow(
                label = "Sort",
                onClick = {
                    onDismiss()
                    onSort()
                },
            )
            Spacer(Modifier.height(8.dp))

            // Display modes section label
            Text(
                text = "DISPLAY MODE",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )

            OptionSheetRow(
                label = "Compact Grid",
                isSelected = displayMode == LibraryDisplayMode.COMPACT_GRID,
                onClick = {
                    onDisplayMode(LibraryDisplayMode.COMPACT_GRID)
                    onDismiss()
                },
            )
            OptionSheetRow(
                label = "Comfortable Grid",
                isSelected = displayMode == LibraryDisplayMode.COMFORTABLE_GRID,
                onClick = {
                    onDisplayMode(LibraryDisplayMode.COMFORTABLE_GRID)
                    onDismiss()
                },
            )
            OptionSheetRow(
                label = "Cover Only",
                isSelected = displayMode == LibraryDisplayMode.COVER_ONLY,
                onClick = {
                    onDisplayMode(LibraryDisplayMode.COVER_ONLY)
                    onDismiss()
                },
            )
            OptionSheetRow(
                label = "List",
                isSelected = displayMode == LibraryDisplayMode.LIST,
                onClick = {
                    onDisplayMode(LibraryDisplayMode.LIST)
                    onDismiss()
                },
            )

            Spacer(Modifier.height(12.dp))
            OptionSheetRow(
                label = "Customize",
                onClick = {
                    onDismiss()
                    onCustomize()
                },
            )
        }
    }
}

@Composable
private fun OptionSheetRow(
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
            )
            if (isSelected) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = RobotoFamily,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
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

// ── Selection top bar ──

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

// ── Filtering + sorting ──

private fun filteredSortedItems(state: LibraryState): List<Anime> {
    var result = state.libraryAnime

    // Category filter
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
