package app.anikuta.ui.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.ui.theme.AnikutaSprings

/**
 * Phase 5 task 5.12 — Search screen (M3 Expressive).
 *
 * Layout:
 *  - Top: statusBarsPadding-wrapped OutlinedTextField with leading Search
 *    icon + trailing Clear icon (when non-empty). Autofocus on first show.
 *  - When query is blank: Recent searches column with one tap to re-run +
 *    "Clear recent searches" button.
 *  - When query is non-empty: 2-column LazyVerticalGrid of cover-art cards.
 *    States — Loading (centered spinner), Empty ("No results for '$query'"),
 *    Error ("Couldn't search: $message").
 *
 * Cards use AnikutaSprings.press + AnikutaSprings.effects for the same
 * scale/corner-morph press feedback as HomeScreen's ExpressiveAnimeCard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onAnimeClick: (Int) -> Unit,
    onSourceResultClick: (String) -> Unit = {},
) {
    val viewModel: SearchViewModel = viewModel()
    val query by viewModel.query.collectAsState()
    val state by viewModel.state.collectAsState()
    val recent by viewModel.recentSearches.collectAsState()
    val availableGenres by viewModel.availableGenres.collectAsState()
    val selectedGenres by viewModel.selectedGenres.collectAsState()
    val selectedYears by viewModel.selectedYears.collectAsState()
    val selectedFormats by viewModel.selectedFormats.collectAsState()
    val selectedSeasons by viewModel.selectedSeasons.collectAsState()
    val selectedStatuses by viewModel.selectedStatuses.collectAsState()
    val selectedSort by viewModel.selectedSort.collectAsState()
    val showAdult by viewModel.showAdult.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()
    val sourceResults by viewModel.sourceResults.collectAsState()
    val popularResults by viewModel.popularResults.collectAsState()
    val latestResults by viewModel.latestResults.collectAsState()
    val isLoadingBrowse by viewModel.isLoadingBrowse.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var showFilterSheet by remember { mutableStateOf(false) }

    // Active filter count (for the badge on the filter button)
    val activeFilterCount = selectedGenres.size + selectedYears.size + selectedFormats.size + selectedSeasons.size + selectedStatuses.size +
        (if (selectedSort != null) 1 else 0)

    // Phase I — Tabbed filter bottom sheet (redesigned)
    if (showFilterSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            dragHandle = null,
        ) {
            FilterSheetTabbed(
                searchMode = searchMode,
                availableGenres = availableGenres,
                availableYears = viewModel.availableYears,
                availableFormats = viewModel.availableFormats,
                availableSeasons = viewModel.availableSeasons,
                availableStatuses = viewModel.availableStatuses,
                availableSorts = viewModel.availableSorts,
                selectedGenres = selectedGenres,
                selectedYears = selectedYears,
                selectedFormats = selectedFormats,
                selectedSeasons = selectedSeasons,
                selectedStatuses = selectedStatuses,
                selectedSort = selectedSort,
                showAdult = showAdult,
                onGenreToggle = { viewModel.toggleGenre(it) },
                onYearToggle = { viewModel.toggleYear(it) },
                onFormatToggle = { viewModel.toggleFormat(it) },
                onSeasonToggle = { viewModel.toggleSeason(it) },
                onStatusToggle = { viewModel.toggleStatus(it) },
                onSortSelected = { viewModel.setSortFilter(it) },
                onShowAdultChanged = { viewModel.setShowAdult(it) },
                onClearAll = { viewModel.clearFilters() },
                onApply = {
                    showFilterSheet = false
                    viewModel.onSubmit()
                },
            )
        }
    }

    // Autofocus on first show — search is the user's primary intent here.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // --- Source toggle — AniList (left), Recent (center, icon), Extensions (right) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // AniList (left)
            FilterChip(
                selected = searchMode == SearchMode.ANILIST,
                onClick = { viewModel.setSearchMode(SearchMode.ANILIST) },
                label = { Text("AniList") },
            )
            // Recent (center — icon only, no text)
            Surface(
                modifier = Modifier.clickable { viewModel.setSearchMode(SearchMode.RECENT) },
                shape = RoundedCornerShape(16.dp),
                color = if (searchMode == SearchMode.RECENT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = "Recent searches",
                    modifier = Modifier.padding(10.dp).size(20.dp),
                    tint = if (searchMode == SearchMode.RECENT) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Extensions (right)
            FilterChip(
                selected = searchMode == SearchMode.SOURCES,
                onClick = { viewModel.setSearchMode(SearchMode.SOURCES) },
                label = { Text("Extensions") },
            )
        }

        // --- Search bar + filter button ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchBarField(
                query = query,
                onQueryChange = viewModel::setQuery,
                onClear = { viewModel.setQuery("") },
                onSubmit = viewModel::onSubmit,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f),
            )
            // Filter button with active-count badge
            Box {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable { showFilterSheet = true },
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = "Filters",
                            tint = if (activeFilterCount > 0)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (activeFilterCount > 0) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(activeFilterCount.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // --- Body ----------------------------------------------------------
        when {
            // RECENT mode → show recent searches
            searchMode == SearchMode.RECENT -> {
                RecentSearchesSection(
                    recent = recent,
                    onSelect = { term ->
                        viewModel.selectRecent(term)
                        keyboard?.show()
                    },
                    onClear = viewModel::clearRecent,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // SOURCES mode + no query → show Popular + Latest
            query.isBlank() && searchMode == SearchMode.SOURCES -> {
                ExtensionBrowseSection(
                    popular = popularResults,
                    latest = latestResults,
                    isLoading = isLoadingBrowse,
                    onResultClick = { result ->
                        val encodedUrl = java.net.URLEncoder.encode(result.url, "UTF-8")
                        val encodedTitle = java.net.URLEncoder.encode(result.title, "UTF-8")
                        val encodedThumb = java.net.URLEncoder.encode(result.thumbnailUrl ?: "", "UTF-8")
                        onSourceResultClick("source-link/${result.sourceId}/$encodedUrl/$encodedTitle/$encodedThumb")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (val s = state) {
                        is SearchState.Idle -> Unit
                        is SearchState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        is SearchState.Success -> {
                            if (searchMode == SearchMode.SOURCES) {
                                SourceResultsGrid(
                                    results = sourceResults,
                                    onResultClick = { result ->
                                        val encodedUrl = java.net.URLEncoder.encode(result.url, "UTF-8")
                                        val encodedTitle = java.net.URLEncoder.encode(result.title, "UTF-8")
                                        val encodedThumb = java.net.URLEncoder.encode(result.thumbnailUrl ?: "", "UTF-8")
                                        onSourceResultClick("source-link/${result.sourceId}/$encodedUrl/$encodedTitle/$encodedThumb")
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else if (s.anime.isEmpty()) {
                                EmptyState(query)
                            } else {
                                ResultsGrid(
                                    anime = s.anime,
                                    hasMore = s.hasMore,
                                    isLoadingMore = s.isLoadingMore,
                                    onLoadMore = { viewModel.loadMore() },
                                    onAnimeClick = { id ->
                                        viewModel.onAnimeClick(id)
                                        onAnimeClick(id)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        is SearchState.Empty -> EmptyState(query)
                        is SearchState.Error -> ErrorState(s.message, onRetry = viewModel::retry)
                    }
                }
            }
        }
    }
}

/**
 * M3 Expressive search bar — rounded OutlinedTextField with leading Search
 * icon and trailing Clear icon (visible only when there's text to clear).
 * Trailing is wrapped in an IconButton so the touch target meets M3
 * accessibility minimums.
 */
@Composable
private fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.focusRequester(focusRequester),
        placeholder = {
            Text("Search your anime")
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear search",
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSubmit() },
        ),
    )
}

/**
 * Recent-searches section — shown when the query is blank.
 *
 * Each row: History icon + term, tap to fill the bar and trigger search.
 * "Clear recent searches" appears as a tonal text button at the bottom
 * (only when there's something to clear).
 */
@Composable
private fun RecentSearchesSection(
    recent: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Section header — same tonal accent bar pattern as HomeScreen's HomeSection.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Recent searches",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        if (recent.isEmpty()) {
            // First-visit empty state — nudge the user toward typing.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No recent searches yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Type an anime title above to start searching.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            recent.forEach { term ->
                RecentRow(
                    term = term,
                    onClick = { onSelect(term) },
                )
            }
            // Clear-all action
            TextButton(
                onClick = onClear,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear recent searches")
            }
        }
    }
}

@Composable
private fun RecentRow(
    term: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            term,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 2-column grid of anime cards. Cards use the same AnikutaSprings.press +
 * AnikutaSprings.effects press feedback as HomeScreen's ExpressiveAnimeCard —
 * scale 1.0 → 0.96 + corner radius 16dp → 20dp.
 */
@Composable
private fun ResultsGrid(
    anime: List<AniListAnime>,
    onAnimeClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    // Phase 5 part 2: detect scroll near the bottom → trigger loadMore.
    androidx.compose.runtime.LaunchedEffect(gridState, hasMore, isLoadingMore) {
        androidx.compose.runtime.snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= anime.size - 4 // trigger when 4 items from the end
        }.collect { nearEnd ->
            if (nearEnd && hasMore && !isLoadingMore) {
                onLoadMore()
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        items(
            items = anime,
            key = { it.id },
            contentType = { "search_anime_card" },
        ) { item ->
            SearchAnimeCard(item, onClick = { onAnimeClick(item.id) })
        }
        // Loading-more footer (full-width span)
        if (isLoadingMore) {
            item(key = "loading_more", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * M3 Expressive anime card — spring-based press feedback (scale + corner
 * morph) matching HomeScreen's ExpressiveAnimeCard. Fixed aspect ratio so
 * the grid stays uniform across varying cover-art ratios.
 */
@Composable
private fun SearchAnimeCard(
    anime: AniListAnime,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = AnikutaSprings.press,
        label = "search_card_scale",
    )
    val cornerRadius by animateFloatAsState(
        targetValue = if (isPressed) 20f else 16f,
        animationSpec = AnikutaSprings.effects,
        label = "search_card_corner",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(cornerRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp,
        ),
        interactionSource = interactionSource,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = anime.coverImage.best(),
                contentDescription = anime.title.preferred(),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(topStart = cornerRadius.dp, topEnd = cornerRadius.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = anime.title.preferred(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Subtitle: format · score · year
                val bits = buildList {
                    anime.format?.let { add(formatLabel(it)) }
                    anime.averageScore?.let { add("★ $it") }
                    anime.seasonYear?.let { add(it.toString()) }
                }
                if (bits.isNotEmpty()) {
                    Text(
                        bits.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Maps AniList's upper-case format enum to a readable label. */
private fun formatLabel(format: String): String = when (format) {
    "TV" -> "TV"
    "TV_SHORT" -> "TV Short"
    "MOVIE" -> "Movie"
    "SPECIAL" -> "Special"
    "OVA" -> "OVA"
    "ONA" -> "ONA"
    "MUSIC" -> "Music"
    else -> format
}

@Composable
private fun EmptyState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "No results for '$query'",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Try a different spelling or title.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Couldn't search: $message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Retry")
        }
    }
}

/**
 * Phase 5 part 3 — Filter bottom sheet content.
 *
 * Shows three filter sections: Genre (from AniList's GenreCollection),
 * Year (from the current results), and Format (TV/MOVIE/OVA/ONA/SPECIAL/MUSIC).
 * Each section uses FlowRow of FilterChips. A "Clear all" button at the bottom.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterSheetContent(
    searchMode: SearchMode,
    availableGenres: List<String>,
    availableYears: List<Int>,
    availableFormats: List<String>,
    availableSeasons: List<String>,
    availableStatuses: List<String>,
    availableSorts: List<String>,
    selectedGenre: String?,
    selectedYear: Int?,
    selectedFormat: String?,
    selectedSeason: String?,
    selectedStatus: String?,
    selectedSort: String?,
    showAdult: Boolean,
    onGenreSelected: (String?) -> Unit,
    onYearSelected: (Int?) -> Unit,
    onFormatSelected: (String?) -> Unit,
    onSeasonSelected: (String?) -> Unit,
    onStatusSelected: (String?) -> Unit,
    onSortSelected: (String?) -> Unit,
    onShowAdultChanged: (Boolean) -> Unit,
    onClearAll: () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
    ) {
        item {
            Text(
                if (searchMode == SearchMode.ANILIST) "AniList Filters" else "Extension Filters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        // Extension mode: show additional info about source-dependent filters
        if (searchMode == SearchMode.SOURCES) {
            item {
                Text(
                    "Note: Genre, season, language, and rating filters depend on the installed extensions. " +
                        "Sort, year, type, and status filters are applied client-side.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        // Genre
        item {
            Text(
                "Genre",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        item {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                availableGenres.take(20).forEach { genre ->
                    FilterChip(
                        selected = selectedGenre == genre,
                        onClick = { onGenreSelected(if (selectedGenre == genre) null else genre) },
                        label = { Text(genre) },
                    )
                }
            }
        }
        // Format
        item {
            Text(
                "Format",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
        }
        item {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                availableFormats.forEach { format ->
                    FilterChip(
                        selected = selectedFormat == format,
                        onClick = { onFormatSelected(if (selectedFormat == format) null else format) },
                        label = { Text(format) },
                    )
                }
            }
        }
        // Year
        if (availableYears.isNotEmpty()) {
            item {
                Text(
                    "Year",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            item {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    availableYears.forEach { year ->
                        FilterChip(
                            selected = selectedYear == year,
                            onClick = { onYearSelected(if (selectedYear == year) null else year) },
                            label = { Text(year.toString()) },
                        )
                    }
                }
            }
        }
        // AniList mode: additional filters (Season, Status, Sort, Adult)
        if (searchMode == SearchMode.ANILIST) {
            // Season
            item {
                Text("Season", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            }
            item {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    availableSeasons.forEach { season ->
                        FilterChip(selected = selectedSeason == season, onClick = { onSeasonSelected(if (selectedSeason == season) null else season) }, label = { Text(season.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
            // Status
            item {
                Text("Status", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            }
            item {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    availableStatuses.forEach { status ->
                        FilterChip(selected = selectedStatus == status, onClick = { onStatusSelected(if (selectedStatus == status) null else status) }, label = { Text(status.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
            // Sort
            item {
                Text("Sort by", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            }
            item {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    availableSorts.forEach { sort ->
                        FilterChip(selected = selectedSort == sort, onClick = { onSortSelected(if (selectedSort == sort) null else sort) }, label = { Text(sort.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
            // Adult content toggle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Show adult results", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = showAdult, onCheckedChange = onShowAdultChanged)
                }
            }
        }
        // Extension mode: additional filter sections
        if (searchMode == SearchMode.SOURCES) {
            // Sort
            item {
                Text("Sort", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            }
            item {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf("Relevance", "Popularity", "Latest", "A-Z").forEach { sort ->
                        FilterChip(selected = false, onClick = { /* TODO: wire to source sort */ }, label = { Text(sort) })
                    }
                }
            }
            // Status
            item {
                Text("Status", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            }
            item {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf("Airing", "Completed", "Upcoming", "Cancelled").forEach { status ->
                        FilterChip(selected = false, onClick = { /* TODO: wire to source status filter */ }, label = { Text(status) })
                    }
                }
            }
        }
        // Clear all
        item {
            TextButton(
                onClick = onClearAll,
                modifier = Modifier.padding(vertical = 16.dp),
            ) {
                Text("Clear all filters")
            }
        }
    }
}

/**
 * Phase 5 part 4 — Source results grid.
 *
 * Shows results from extension sources (not AniList). Each card shows the
 * anime title, thumbnail, and source name. Tapping a source result is a
 * no-op for now (navigating to detail from a source URL needs a different
 * route than anilistId — future work).
 */
@Composable
private fun SourceResultsGrid(
    results: List<app.anikuta.source.bridge.SourceSearchResult>,
    modifier: Modifier = Modifier,
    onResultClick: (app.anikuta.source.bridge.SourceSearchResult) -> Unit = {},
) {
    if (results.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No source results",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        items(
            items = results,
            key = { "${it.sourceName}:${it.url}" },
            contentType = { "source_result_card" },
        ) { result ->
            SourceResultCard(result, onClick = { onResultClick(result) })
        }
    }
}

@Composable
private fun SourceResultCard(
    result: app.anikuta.source.bridge.SourceSearchResult,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick,
    ) {
        Column {
            if (!result.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = result.thumbnailUrl,
                    contentDescription = result.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {}
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = result.sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Phase I — Extension browse section (shown when SOURCES mode + no query).
 *
 * Shows two horizontally-scrollable rows:
 *   - Popular: top 20 from all extension sources
 *   - Latest: top 20 from all extension sources
 * Each in a dedicated container with its own background + header.
 */
@Composable
private fun ExtensionBrowseSection(
    popular: List<app.anikuta.source.bridge.SourceSearchResult>,
    latest: List<app.anikuta.source.bridge.SourceSearchResult>,
    isLoading: Boolean,
    onResultClick: (app.anikuta.source.bridge.SourceSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLoading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (popular.isNotEmpty()) {
            item {
                BrowseSectionHeader("Popular")
            }
            item {
                SourceResultRow(
                    results = popular.take(20),
                    onResultClick = onResultClick,
                )
            }
        }
        if (latest.isNotEmpty()) {
            item {
                BrowseSectionHeader("Latest")
            }
            item {
                SourceResultRow(
                    results = latest.take(20),
                    onResultClick = onResultClick,
                )
            }
        }
        if (popular.isEmpty() && latest.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No extensions installed.\nInstall extensions to browse popular and latest anime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseSectionHeader(title: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SourceResultRow(
    results: List<app.anikuta.source.bridge.SourceSearchResult>,
    onResultClick: (app.anikuta.source.bridge.SourceSearchResult) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        results.forEach { result ->
            item(key = "${result.sourceName}:${result.url}") {
                SourceBrowseCard(result, onClick = { onResultClick(result) })
            }
        }
    }
}

@Composable
private fun SourceBrowseCard(
    result: app.anikuta.source.bridge.SourceSearchResult,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column {
            if (!result.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = result.thumbnailUrl,
                    contentDescription = result.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                ) {}
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = result.sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Phase I — Tabbed filter bottom sheet (redesigned).
 *
 * Top row: filter category tabs (Genre / Format / Year / Season / Status / Sort)
 * Below: the selected category's options (multi-select FilterChips)
 * Top-right: Apply button (closes the sheet + triggers search)
 * Bottom: Clear all + Adult toggle (AniList mode only)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheetTabbed(
    searchMode: SearchMode,
    availableGenres: List<String>,
    availableYears: List<Int>,
    availableFormats: List<String>,
    availableSeasons: List<String>,
    availableStatuses: List<String>,
    availableSorts: List<String>,
    selectedGenres: Set<String>,
    selectedYears: Set<Int>,
    selectedFormats: Set<String>,
    selectedSeasons: Set<String>,
    selectedStatuses: Set<String>,
    selectedSort: String?,
    showAdult: Boolean,
    onGenreToggle: (String) -> Unit,
    onYearToggle: (Int) -> Unit,
    onFormatToggle: (String) -> Unit,
    onSeasonToggle: (String) -> Unit,
    onStatusToggle: (String) -> Unit,
    onSortSelected: (String?) -> Unit,
    onShowAdultChanged: (Boolean) -> Unit,
    onClearAll: () -> Unit,
    onApply: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = if (searchMode == SearchMode.ANILIST) {
        listOf("Genre", "Format", "Year", "Season", "Status", "Sort")
    } else {
        listOf("Format", "Sort", "Status")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 450.dp)
            .navigationBarsPadding(),
    ) {
        // Top row: title + Clear/Apply buttons in a shared container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (searchMode == SearchMode.ANILIST) "AniList Filters" else "Extension Filters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            // Clear + Apply in a shared surfaceContainer container
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onClearAll) { Text("Clear", style = MaterialTheme.typography.labelMedium) }
                    Spacer(modifier = Modifier.width(2.dp))
                    Button(
                        onClick = onApply,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    ) { Text("Apply", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }

        // Tab row — filter categories
        ScrollableTabRow(
            selectedTabIndex = selectedTab.coerceIn(0, tabs.lastIndex),
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
            divider = {},
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                )
            }
        }

        // Tab content — scrollable
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            val currentTab = tabs.getOrNull(selectedTab) ?: ""
            when (currentTab) {
                "Genre" -> {
                    if (availableGenres.isEmpty()) {
                        Text("Loading genres...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRowFilterChips(
                            items = availableGenres,
                            selected = selectedGenres.toList(),
                            onToggle = onGenreToggle,
                        )
                    }
                }
                "Format" -> FlowRowFilterChips(
                    items = availableFormats,
                    selected = selectedFormats.toList(),
                    onToggle = onFormatToggle,
                )
                "Year" -> FlowRowFilterChips(
                    items = availableYears.map { it.toString() },
                    selected = selectedYears.map { it.toString() },
                    onToggle = { item ->
                        item.toIntOrNull()?.let { onYearToggle(it) }
                    },
                )
                "Season" -> FlowRowFilterChips(
                    items = availableSeasons,
                    selected = selectedSeasons.toList(),
                    onToggle = onSeasonToggle,
                )
                "Status" -> FlowRowFilterChips(
                    items = availableStatuses,
                    selected = selectedStatuses.toList(),
                    onToggle = onStatusToggle,
                )
                "Sort" -> FlowRowFilterChips(
                    items = availableSorts,
                    selected = listOfNotNull(selectedSort),
                    onToggle = { item ->
                        onSortSelected(if (selectedSort == item) null else item)
                    },
                )
            }

            // Adult toggle (AniList only, Sort tab)
            if (searchMode == SearchMode.ANILIST && currentTab == "Sort") {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Show adult results", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = showAdult, onCheckedChange = onShowAdultChanged)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRowFilterChips(
    items: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            FilterChip(
                selected = selected.contains(item),
                onClick = { onToggle(item) },
                label = { Text(item) },
            )
        }
    }
}
