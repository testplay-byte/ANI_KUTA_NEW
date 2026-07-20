@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.testplaybyte.animeapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.testplaybyte.animeapp.data.AniListClient
import com.testplaybyte.animeapp.model.Anime
import com.testplaybyte.animeapp.ui.components.AnimeCard
import com.testplaybyte.animeapp.ui.components.FilterSheet
import com.testplaybyte.animeapp.theme.RobotoFamily
import kotlinx.coroutines.delay

/**
 * SearchScreen — matches the web prototype exactly.
 *
 * Layout (top to bottom):
 *   1. Collapsing topbar:
 *      - Row: [Title "Search"] [SourceToggle (right)] [SearchBar]
 *      - When scrolled: title shrinks, source toggle fades+shrinks to 0,
 *        search bar moves BESIDE the title (same row, fills remaining space).
 *   2. Quick row: [Filters (left)] [Sort dropdown (right)] — slides out when scrolled.
 *   3. Scrollable content:
 *      - Recent searches (in a surface-1 card, collapsible, with individual delete)
 *      - Results grid (in a surface-1 card, 3-column chunked) with label + count
 */
@Composable
fun SearchScreen(
    onOpenAnime: (Int) -> Unit,
) {
    val client = remember { AniListClient() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("anilist") }
    var sort by remember { mutableStateOf("POPULARITY_DESC") }
    var results by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortDropdown by remember { mutableStateOf(false) }
    var recents by remember { mutableStateOf<List<String>>(emptyList()) }

    // Filter state
    var selectedGenres by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var selectedSeason by remember { mutableStateOf<String?>(null) }
    var selectedFormat by remember { mutableStateOf<String?>(null) }
    var selectedStatus by remember { mutableStateOf<String?>(null) }

    val collapsed = scrollState.value > 20

    // Fetch default content when source changes or on first load
    LaunchedEffect(source) {
        if (query.isBlank()) {
            loading = true
            error = null
            val result = runCatching {
                if (source == "anilist") client.fetchPopular()
                else client.fetchTrendingFull()
            }
            results = result.getOrDefault(emptyList())
            error = result.exceptionOrNull()?.message
            loading = false
            hasSearched = false
        }
    }

    // Debounced search
    LaunchedEffect(query) {
        if (query.isBlank()) {
            loading = true
            error = null
            val result = runCatching {
                if (source == "anilist") client.fetchPopular()
                else client.fetchTrendingFull()
            }
            results = result.getOrDefault(emptyList())
            error = result.exceptionOrNull()?.message
            loading = false
            hasSearched = false
            return@LaunchedEffect
        }
        loading = true
        delay(500)
        recents = (listOf(query.trim()) + recents.filter { it != query.trim() }).take(12)
        val result = runCatching { client.search(query.trim()) }
        results = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.message
        loading = false
        hasSearched = true
    }

    val activeFilterCount = selectedGenres.size +
        (if (selectedYear != null) 1 else 0) +
        (if (selectedSeason != null) 1 else 0) +
        (if (selectedFormat != null) 1 else 0) +
        (if (selectedStatus != null) 1 else 0)

    val sectionLabel = when {
        query.isNotBlank() -> "Results for \"$query\""
        source == "extension" -> "Trending now · Extension"
        else -> "Popular anime"
    }
    val showCount = !loading && error == null && results.isNotEmpty()

    val sortOptions = listOf(
        "POPULARITY_DESC" to "Popularity",
        "SCORE_DESC" to "Score",
        "START_DATE_DESC" to "Newest",
        "TITLE_ROMAJI" to "Title A-Z",
        "TRENDING_DESC" to "Trending",
    )
    val currentSortLabel = sortOptions.find { it.first == sort }?.second ?: "Popularity"

    val showRecent = query.isBlank() && activeFilterCount == 0 && recents.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Collapsing topbar ──
        SearchTopBar(
            collapsed = collapsed,
            query = query,
            onQueryChange = { query = it },
            onClearQuery = { query = "" },
            source = source,
            onSourceChange = { newSource ->
                source = newSource
                sort = if (newSource == "extension") "TRENDING_DESC" else "POPULARITY_DESC"
            },
            onSubmit = { keyboard?.hide() },
        )

        // ── Quick row: Filters (left) + Sort (right) — slides out when collapsed ──
        AnimatedVisibility(
            visible = !collapsed,
            enter = fadeIn(),
            exit = fadeOut() + shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Filters button (LEFT)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { showFilterSheet = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.FilterList,
                        contentDescription = "Filters",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "Filters",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (activeFilterCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ) {
                            Text(
                                text = activeFilterCount.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                // Sort dropdown (RIGHT)
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .clickable { showSortDropdown = !showSortDropdown }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = currentSortLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = if (showSortDropdown) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Sort",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = showSortDropdown,
                        onDismissRequest = { showSortDropdown = false },
                    ) {
                        sortOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label, fontSize = 14.sp, fontWeight = if (value == sort) FontWeight.ExtraBold else FontWeight.Normal) },
                                onClick = {
                                    sort = value
                                    showSortDropdown = false
                                },
                                leadingIcon = if (value == sort) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                            )
                        }
                    }
                }
            }
        }

        // ── Scrollable content ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 110.dp),
        ) {
            // Recent searches (in a card)
            if (showRecent) {
                RecentSearchesCard(
                    recents = recents,
                    onPick = { query = it },
                    onRemove = { item -> recents = recents.filter { it != item } },
                    onClear = { recents = emptyList() },
                )
            }

            // Results tray (in a card) — minimal edge padding so it's closer to device borders
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                ) {
                    // Section header — label + count
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = sectionLabel,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        if (showCount) {
                            Text(
                                text = "${results.size} found",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Results grid
                    if (loading) {
                        Text(
                            text = "Loading…",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else if (error != null) {
                        Text(
                            text = error ?: "An error occurred",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else if (results.isEmpty() && hasSearched) {
                        Text(
                            text = "No results found for \"$query\"",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        results.chunked(3).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowItems.forEach { anime ->
                                    AnimeCard(
                                        anime = anime,
                                        onClick = onOpenAnime,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(3 - rowItems.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }

    // Filter sheet
    FilterSheet(
        show = showFilterSheet,
        onDismiss = { showFilterSheet = false },
        onApply = { showFilterSheet = false },
        onClearAll = {
            selectedGenres = emptySet()
            selectedYear = null
            selectedSeason = null
            selectedFormat = null
            selectedStatus = null
        },
        selectedGenres = selectedGenres,
        onGenreToggle = { genre ->
            selectedGenres = if (genre in selectedGenres) selectedGenres - genre else selectedGenres + genre
        },
        selectedYear = selectedYear,
        onYearSelect = { selectedYear = it },
        selectedSeason = selectedSeason,
        onSeasonSelect = { selectedSeason = it },
        selectedFormat = selectedFormat,
        onFormatSelect = { selectedFormat = it },
        selectedStatus = selectedStatus,
        onStatusSelect = { selectedStatus = it },
    )
}

// ── SearchTopBar — collapsing title + source toggle + search bar ──────────

@Composable
private fun SearchTopBar(
    collapsed: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    source: String,
    onSourceChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val titleFontSize by animateFloatAsState(
        targetValue = if (collapsed) 26f else 36f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "titleSize",
    )
    val sourceAlpha by animateFloatAsState(
        targetValue = if (collapsed) 0f else 1f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "sourceAlpha",
    )
    val sourceWidth by animateDpAsState(
        targetValue = if (collapsed) 0.dp else 180.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "sourceWidth",
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .statusBarsPadding(),
        ) {
            // Row: Title + (SourceToggle OR SearchBar) — animated transition
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Title (always visible)
                Text(
                    text = "Search",
                    fontFamily = RobotoFamily,
                    fontSize = titleFontSize.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.02).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )

                if (collapsed) {
                    // When collapsed: search bar moves BESIDE the title
                    Spacer(Modifier.width(12.dp))
                    SearchBar(
                        value = query,
                        onChange = onQueryChange,
                        onClear = onClearQuery,
                        onSubmit = onSubmit,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    // When expanded: source toggle on the right
                    if (sourceWidth > 0.dp) {
                        Row(
                            modifier = Modifier
                                .width(sourceWidth)
                                .alpha(sourceAlpha)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(3.dp),
                        ) {
                            SourceToggleBtn(
                                label = "AniList",
                                icon = Icons.Filled.Search,
                                active = source == "anilist",
                                onClick = { onSourceChange("anilist") },
                            )
                            SourceToggleBtn(
                                label = "Extension",
                                icon = Icons.Filled.Extension,
                                active = source == "extension",
                                onClick = { onSourceChange("extension") },
                            )
                        }
                    }
                }
            }

            // Search bar below title — animated appearance when un-collapsing
            // Uses AnimatedVisibility so it slides/fades in smoothly (not a hard jump)
            AnimatedVisibility(
                visible = !collapsed,
                enter = fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                    expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                    shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)),
            ) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    SearchBar(
                        value = query,
                        onChange = onQueryChange,
                        onClear = onClearQuery,
                        onSubmit = onSubmit,
                        compact = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

// ── Source toggle button ──────────────────────────────────────────────────

@Composable
private fun RowScope.SourceToggleBtn(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Search bar ────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    value: String,
    onChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val height = if (compact) 44.dp else 52.dp
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp),
            )
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontSize = if (compact) 14.sp else 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = "Search anime...",
                            fontSize = if (compact) 14.sp else 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )
            if (value.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onClear() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ── Recent searches card (collapsible, with individual delete) ────────────

@Composable
private fun RecentSearchesCard(
    recents: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    var collapsed by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val visibleCount = if (expanded) recents.size else minOf(3, recents.size)
    val visible = recents.take(visibleCount)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Header: title + collapse toggle / show button / clear all
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: title + collapse chevron
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "RECENT SEARCHES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 0.08.sp,
                    )
                    if (!collapsed) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { collapsed = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Collapse",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                // Right: Show button (when collapsed) or Clear all
                if (collapsed) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable { collapsed = false }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Show",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                } else {
                    Text(
                        text = "Clear all",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onClear() },
                    )
                }
            }

            // List (hidden when collapsed)
            AnimatedVisibility(
                visible = !collapsed,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    visible.forEach { recent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(recent) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Clock icon
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = recent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            // Individual delete button
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { onRemove(recent) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }

                    // Show more / less
                    if (recents.size > 3) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .clickable { expanded = !expanded }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (expanded) "Show less" else "Show ${recents.size - 3} more",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
