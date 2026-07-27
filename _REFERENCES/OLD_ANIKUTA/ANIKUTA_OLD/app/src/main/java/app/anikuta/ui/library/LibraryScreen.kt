package app.anikuta.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.ui.theme.AnikutaSprings

/**
 * Phase 5 tasks 5.7 — Library screen.
 *
 * Shows the user's saved (bookmarked) anime in a 2-column M3 Expressive grid
 * with a floating top bar, a sort dropdown (Title / Last watched / Unread
 * episodes — Q4 decision), pull-to-refresh, and an empty state.
 *
 * Tap a card → [onAnimeClick] navigates to the detail page (wired by the
 * NavGraph in the main agent's pass).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onAnimeClick: (Int) -> Unit,
) {
    val viewModel: LibraryViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val displaySettings by viewModel.displaySettings.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val unwatchedCounts by viewModel.unwatchedCounts.collectAsState()
    val subDubInfo by viewModel.subDubInfo.collectAsState()
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Phase H: track scroll for the gradient blur effect at the top
    val gridState = rememberLazyGridState()
    // Smooth gradient alpha: 0 when at top, ramps up as user scrolls
    val gradientAlpha by remember {
        derivedStateOf {
            val offset = gridState.firstVisibleItemScrollOffset.toFloat()
            val itemIndex = gridState.firstVisibleItemIndex
            if (itemIndex > 0) 1f else (offset / 200f).coerceIn(0f, 1f)
        }
    }
    val scope = rememberCoroutineScope()

    // Phase H: when settings sheet opens, scroll the grid up to hide the top bar
    // + categories + count header. Reduce by 20dp so it doesn't scroll too far.
    // When sheet closes, scroll back to the top.
    androidx.compose.runtime.LaunchedEffect(showSettingsSheet) {
        if (showSettingsSheet) {
            // Scroll past: topbar (index 0) + category_pills (index 1) + count_header (index 2)
            // → land on index 3 (first anime card) with a small offset so it doesn't go too far
            try {
                gridState.animateScrollToItem(3, -22) // reduced by 10% from -20
            } catch (_: Exception) {
                try { gridState.animateScrollToItem(2, 0) } catch (_: Exception) {}
            }
        } else {
            // Sheet closed → scroll back to top
            try { gridState.animateScrollToItem(0, 0) } catch (_: Exception) {}
        }
    }

    // Create-category dialog
    if (showCreateCategoryDialog) {
        var newCategoryName by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCreateCategoryDialog = false },
            title = { Text("New category") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Category name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        viewModel.createCategory(newCategoryName)
                    }
                    showCreateCategoryDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateCategoryDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Phase H — Tabbed settings bottom sheet (Filter / Sort / Display)
    if (showSettingsSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            dragHandle = null, // removes the empty area + white line at the top
        ) {
            LibrarySettingsSheet(
                sortMode = sortMode,
                sortAscending = sortAscending,
                onSortSelected = { viewModel.setSort(it) },
                settings = displaySettings,
                onSetDisplayMode = viewModel::setDisplayMode,
                onSetGridColumns = viewModel::setGridColumns,
                onSetTitlePosition = viewModel::setTitlePosition,
                onSetTitleMaxLines = viewModel::setTitleMaxLines,
                onSetShowRating = viewModel::setShowRating,
                onSetShowYear = viewModel::setShowYear,
                onSetShowEpisodes = viewModel::setShowEpisodes,
                onSetShowSubDub = viewModel::setShowSubDub,
                onSetShowUnwatchedBadge = viewModel::setShowUnwatchedBadge,
                onSetCardBorder = viewModel::setCardBorder,
            )
        }
    }

    // Determine grid columns based on display mode + settings
    val gridColumns = when (displaySettings.displayMode) {
        LibraryDisplayPrefs.DisplayMode.GRID -> displaySettings.gridColumns
        LibraryDisplayPrefs.DisplayMode.LIST -> 1
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 0.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Floating top bar — spans full width
            item(key = "topbar", span = { GridItemSpan(maxLineSpan) }) {
                LibraryTopBar(
                    onSettingsClick = { showSettingsSheet = true },
                )
            }

            // Phase H: Category pills (below the top bar, above the grid) — always shown
            item(key = "category_pills", span = { GridItemSpan(maxLineSpan) }) {
                LibraryCategoryPills(
                    categories = categories,
                    selectedId = selectedCategoryId,
                    onSelect = { viewModel.selectCategory(it) },
                    onAddClick = { showCreateCategoryDialog = true },
                )
            }

            when (val s = state) {
                is LibraryState.Loading -> {
                    item(key = "skeleton_header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Loading your library…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                    items(
                        count = 4,
                        key = { "skeleton_$it" },
                        contentType = { "skeleton_card" },
                    ) {
                        SkeletonCard()
                    }
                }
                is LibraryState.Empty -> {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState()
                    }
                }
                is LibraryState.Error -> {
                    item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                        ErrorState(message = s.message, onRetry = { viewModel.refresh() })
                    }
                }
                is LibraryState.Success -> {
                    item(key = "count_header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "${s.anime.size} saved",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    itemsIndexed(
                        items = s.anime,
                        key = { _, it -> it.id },
                        contentType = { _, _ -> "anime_card_${displaySettings.displayMode.name}" },
                    ) { index, anime ->
                        LibraryCard(
                            anime = anime,
                            unwatchedCount = unwatchedCounts[anime.id] ?: 0,
                            subDubInfo = subDubInfo[anime.id],
                            settings = displaySettings,
                            index = index,
                            onClick = { onAnimeClick(anime.id) },
                        )
                    }
                }
            }
        }
        // Phase H: gradient blur + darkening at the top when scrolling
        // Smooth: alpha ramps from 0→1 as user scrolls. Stronger: 0.98 alpha at top.
        if (gradientAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.background.copy(alpha = 0.98f * gradientAlpha),
                            0.4f to MaterialTheme.colorScheme.background.copy(alpha = 0.9f * gradientAlpha),
                            0.7f to MaterialTheme.colorScheme.background.copy(alpha = 0.5f * gradientAlpha),
                            1f to MaterialTheme.colorScheme.background.copy(alpha = 0f),
                        ),
                    ),
            )
        }
        } // end Box
    }
}

/**
 * Floating top bar — matches HomeScreen's FloatingTopBar pattern.
 * statusBarsPadding here is the ONLY place that handles the status bar gap.
 * The sort button lives in the trailing slot (where HomeScreen puts search).
 */
/**
 * Phase H — Simplified top bar with ONE settings button.
 * The button opens the tabbed settings sheet (Filter / Sort / Display).
 */
@Composable
private fun LibraryTopBar(
    onSettingsClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Library",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            // Single settings button — opens tabbed bottom sheet
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { onSettingsClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = "Library settings",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * M3 Expressive library card — redesigned (Phase A).
 *
 * Adapts to the current [DisplayMode]:
 *  - GRID_2: rich card — 2:3 cover + title + score/year/format metadata
 *  - GRID_3: compact card — cover-only with gradient + title overlay
 *  - LIST: horizontal row — small cover (left) + title/metadata (right)
 *
 * All modes share the spring press feedback (scale 1→0.96 + corner morph
 * 16→20dp) via [AnikutaSprings].
 *
 * Badges:
 *  - Unwatched count (top-start, primary color) — when unwatchedCount > 0
 *  - Score (top-end, surfaceVariant with star) — when averageScore != null
 */
@Composable
private fun LibraryCard(
    anime: AniListAnime,
    unwatchedCount: Int,
    settings: LibraryDisplayPrefs.Settings,
    subDubInfo: app.anikuta.data.cache.SubDubStore.SubDubInfo? = null,
    index: Int = 0,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = AnikutaSprings.press,
        label = "lib_card_scale",
    )
    val cornerRadius by animateFloatAsState(
        targetValue = if (isPressed) 20f else 16f,
        animationSpec = AnikutaSprings.effects,
        label = "lib_card_corner",
    )

    when (settings.displayMode) {
        LibraryDisplayPrefs.DisplayMode.LIST -> LibraryListCard(
            anime = anime,
            unwatchedCount = unwatchedCount,
            subDubInfo = subDubInfo,
            settings = settings,
            index = index,
            scale = scale,
            cornerRadius = cornerRadius,
            interactionSource = interactionSource,
            onClick = onClick,
        )
        LibraryDisplayPrefs.DisplayMode.GRID -> LibraryRichCard(
            anime = anime,
            unwatchedCount = unwatchedCount,
            subDubInfo = subDubInfo,
            settings = settings,
            index = index,
            scale = scale,
            cornerRadius = cornerRadius,
            interactionSource = interactionSource,
            onClick = onClick,
        )
    }
}

/**
 * GRID — Rich card matching the detail page episode list style.
 *
 * Patterns from EpisodeRow:
 *  - Alternating bg: surfaceContainerLow (even) / surfaceContainerHigh (odd)
 *  - 12dp rounded Surface (was Card)
 *  - Title in a surfaceContainer Surface (8dp rounded) — like episode titles
 *  - SUB/DUB in AudioPills style (outlineVariant, 6dp rounded, dot separators)
 *  - Score badge on the cover (black 70% alpha overlay — like episode number)
 */
@Composable
private fun LibraryRichCard(
    anime: AniListAnime,
    unwatchedCount: Int,
    subDubInfo: app.anikuta.data.cache.SubDubStore.SubDubInfo?,
    settings: LibraryDisplayPrefs.Settings,
    index: Int,
    scale: Float,
    cornerRadius: Float,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    // Alternating bg — matches EpisodeRow pattern
    val cardColor = if (index % 2 == 0) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderWidth = when (settings.cardBorder) {
        LibraryDisplayPrefs.CardBorder.NONE -> 0.dp
        LibraryDisplayPrefs.CardBorder.THIN -> 1.dp
        LibraryDisplayPrefs.CardBorder.THICK -> 2.dp
    }
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        tonalElevation = 1.dp,
        border = if (borderWidth > 0.dp) androidx.compose.foundation.BorderStroke(borderWidth, borderColor) else null,
        interactionSource = interactionSource,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Cover with badges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            ) {
                AsyncImage(
                    model = anime.coverImage.best(),
                    contentDescription = anime.title.preferred(),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // Unwatched badge (top-start) — if enabled
                if (settings.showUnwatchedBadge && unwatchedCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                    ) {
                        Text(
                            text = unwatchedCount.toString(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }
                // Score badge (top-end) — if enabled — black overlay like episode number
                if (settings.showRating && anime.averageScore != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("★", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                anime.averageScore.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color.White,
                            )
                        }
                    }
                }
                // OVERLAY title — if titlePosition is OVERLAY
                if (settings.titlePosition == LibraryDisplayPrefs.TitlePosition.OVERLAY) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                ),
                            ),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(8.dp),
                    ) {
                        Text(
                            text = anime.title.preferred(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = settings.titleMaxLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val metaItems = mutableListOf<String>()
                        if (settings.showYear && anime.seasonYear != null) metaItems.add(anime.seasonYear.toString())
                        if (anime.format != null) metaItems.add(formatLabel(anime.format))
                        if (settings.showEpisodes && anime.episodes != null) metaItems.add("${anime.episodes} eps")
                        if (metaItems.isNotEmpty()) {
                            Text(
                                metaItems.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
            // BELOW title + metadata — if titlePosition is BELOW
            if (settings.titlePosition == LibraryDisplayPrefs.TitlePosition.BELOW) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    // Title in a surfaceContainer pill-shaped container — matches EpisodeRow
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = anime.title.preferred(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1, // single line — user requested
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // Metadata pills row — matches EpisodeRow's DateAudioPillsRow
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Rating pill
                        if (settings.showRating && anime.averageScore != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("★", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        anime.averageScore.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                        // Year pill
                        if (settings.showYear && anime.seasonYear != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            ) {
                                Text(
                                    anime.seasonYear.toString(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                        // Format pill
                        if (anime.format != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            ) {
                                Text(
                                    formatLabel(anime.format),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                        // Episode count pill
                        if (settings.showEpisodes && anime.episodes != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            ) {
                                Text(
                                    "${anime.episodes} eps",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                        // Audio pills (SUB/DUB) — matches AudioPills pattern
                        if (settings.showSubDub && subDubInfo != null && (subDubInfo.hasSub || subDubInfo.hasDub)) {
                            LibraryAudioPills(hasSub = subDubInfo.hasSub, hasDub = subDubInfo.hasDub)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Audio pills matching the detail page's AudioPills composable.
 * outlineVariant bg, 6dp rounded, dot separators, short labels when 2+.
 */
@Composable
private fun LibraryAudioPills(hasSub: Boolean, hasDub: Boolean) {
    data class Audio(val full: String, val short: String)
    val parts = buildList {
        if (hasSub) add(Audio("SUB", "S"))
        if (hasDub) add(Audio("DUB", "D"))
    }
    val useShort = parts.size >= 2
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            parts.forEachIndexed { idx, audio ->
                if (idx > 0) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                androidx.compose.foundation.shape.CircleShape,
                            ),
                    )
                }
                Text(
                    text = if (useShort) audio.short else audio.full,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * LIST — Horizontal row: small cover (left) + title/metadata (right).
 *
 * Cover is 56×80dp (2:3 portrait). The right side shows the title,
 * score, year/format, and unwatched count.
 */
@Composable
private fun LibraryListCard(
    anime: AniListAnime,
    unwatchedCount: Int,
    subDubInfo: app.anikuta.data.cache.SubDubStore.SubDubInfo?,
    settings: LibraryDisplayPrefs.Settings,
    index: Int,
    scale: Float,
    cornerRadius: Float,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    // Alternating bg — matches EpisodeRow pattern
    val cardColor = if (index % 2 == 0) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        tonalElevation = 1.dp,
        interactionSource = interactionSource,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover thumbnail (56×80dp, 2:3)
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                AsyncImage(
                    model = anime.coverImage.best(),
                    contentDescription = anime.title.preferred(),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // Unwatched badge (top-start) — black overlay like grid cards
                if (settings.showUnwatchedBadge && unwatchedCount > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart),
                        shape = RoundedCornerShape(4.dp),
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                    ) {
                        Text(
                            text = unwatchedCount.toString(),
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }
            }
            // Metadata (right) — title as plain text + metadata pills
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                // Title — plain text (no background), single line
                Text(
                    text = anime.title.preferred(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Metadata pills row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (settings.showRating && anime.averageScore != null) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.outlineVariant) {
                            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("★", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(anime.averageScore.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    if (settings.showYear && anime.seasonYear != null) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.outlineVariant) {
                            Text(anime.seasonYear.toString(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                        }
                    }
                    if (anime.format != null) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.outlineVariant) {
                            Text(formatLabel(anime.format), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                        }
                    }
                    if (settings.showSubDub && subDubInfo != null && (subDubInfo.hasSub || subDubInfo.hasDub)) {
                        LibraryAudioPills(hasSub = subDubInfo.hasSub, hasDub = subDubInfo.hasDub)
                    }
                }
            }
        }
    }
}

/** Maps AniList format strings to readable labels. */
private fun formatLabel(format: String): String = when (format) {
    "TV" -> "TV"
    "MOVIE" -> "Movie"
    "OVA" -> "OVA"
    "ONA" -> "ONA"
    "SPECIAL" -> "Special"
    "MUSIC" -> "Music"
    else -> format
}

@Composable
private fun SkeletonCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ) {}
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.8f).height(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                ) {}
                Surface(
                    modifier = Modifier.fillMaxWidth(0.5f).height(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                ) {}
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp, bottom = 80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Text(
                "Your library is empty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Save anime from the detail page — tap the bookmark icon while viewing an anime and it'll show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Couldn't load: $message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

/**
 * Phase H — Tabbed library settings bottom sheet.
 *
 * Mirrors aniyomi's AnimeLibrarySettingsDialog pattern:
 *   - Pill-shaped category selection at the TOP (no empty space)
 *   - 3 tabs: Filter, Sort, Display
 *   - Filter: (future — downloaded, unseen, tracked filters)
 *   - Sort: Title, Last watched, Unread (with ascending/descending)
 *   - Display: Grid/List, columns, title position, max lines, show/hide fields, borders
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySettingsSheet(
    sortMode: SortMode,
    sortAscending: Boolean,
    onSortSelected: (SortMode) -> Unit,
    settings: LibraryDisplayPrefs.Settings,
    onSetDisplayMode: (LibraryDisplayPrefs.DisplayMode) -> Unit,
    onSetGridColumns: (Int) -> Unit,
    onSetTitlePosition: (LibraryDisplayPrefs.TitlePosition) -> Unit,
    onSetTitleMaxLines: (Int) -> Unit,
    onSetShowRating: (Boolean) -> Unit,
    onSetShowYear: (Boolean) -> Unit,
    onSetShowEpisodes: (Boolean) -> Unit,
    onSetShowSubDub: (Boolean) -> Unit,
    onSetShowUnwatchedBadge: (Boolean) -> Unit,
    onSetCardBorder: (LibraryDisplayPrefs.CardBorder) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Filter", "Sort", "Display")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)  // Phase H: limit sheet height
            .navigationBarsPadding(),
    ) {
        // Tab row — NO categories here (they're on the library page itself)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                )
            }
        }

        // Tab content
        when (selectedTab) {
            0 -> FilterTab()
            1 -> SortTab(sortMode, sortAscending, onSortSelected)
            2 -> DisplayTab(settings, onSetDisplayMode, onSetGridColumns, onSetTitlePosition, onSetTitleMaxLines, onSetShowRating, onSetShowYear, onSetShowEpisodes, onSetShowSubDub, onSetShowUnwatchedBadge, onSetCardBorder)
        }
    }
}

@Composable
private fun FilterTab() {
    // Future: downloaded, unseen, tracked, started, completed filters
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            "No filters available yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Filters (downloaded, unseen, tracked) will be added in a future update.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SortTab(sortMode: SortMode, sortAscending: Boolean, onSortSelected: (SortMode) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        SortMode.entries.forEach { mode ->
            val isSelected = mode == sortMode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onSortSelected(mode) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    mode.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (isSelected) {
                    // Show asc/desc indicator — click to toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (sortAscending) "A→Z" else "Z→A",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            if (sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = if (sortAscending) "Ascending" else "Descending",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayTab(
    settings: LibraryDisplayPrefs.Settings,
    onSetDisplayMode: (LibraryDisplayPrefs.DisplayMode) -> Unit,
    onSetGridColumns: (Int) -> Unit,
    onSetTitlePosition: (LibraryDisplayPrefs.TitlePosition) -> Unit,
    onSetTitleMaxLines: (Int) -> Unit,
    onSetShowRating: (Boolean) -> Unit,
    onSetShowYear: (Boolean) -> Unit,
    onSetShowEpisodes: (Boolean) -> Unit,
    onSetShowSubDub: (Boolean) -> Unit,
    onSetShowUnwatchedBadge: (Boolean) -> Unit,
    onSetCardBorder: (LibraryDisplayPrefs.CardBorder) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Display mode
        SettingsSection("Layout") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = settings.displayMode == LibraryDisplayPrefs.DisplayMode.GRID, onClick = { onSetDisplayMode(LibraryDisplayPrefs.DisplayMode.GRID) }, label = { Text("Grid") })
                FilterChip(selected = settings.displayMode == LibraryDisplayPrefs.DisplayMode.LIST, onClick = { onSetDisplayMode(LibraryDisplayPrefs.DisplayMode.LIST) }, label = { Text("List") })
            }
        }
        // Grid-only settings
        if (settings.displayMode == LibraryDisplayPrefs.DisplayMode.GRID) {
            SettingsSection("Columns") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4, 5).forEach { cols ->
                        FilterChip(selected = settings.gridColumns == cols, onClick = { onSetGridColumns(cols) }, label = { Text(cols.toString()) })
                    }
                }
            }
            SettingsSection("Title position") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = settings.titlePosition == LibraryDisplayPrefs.TitlePosition.BELOW, onClick = { onSetTitlePosition(LibraryDisplayPrefs.TitlePosition.BELOW) }, label = { Text("Below cover") })
                    FilterChip(selected = settings.titlePosition == LibraryDisplayPrefs.TitlePosition.OVERLAY, onClick = { onSetTitlePosition(LibraryDisplayPrefs.TitlePosition.OVERLAY) }, label = { Text("On cover") })
                }
            }
        }
        SettingsSection("Title lines") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3).forEach { lines ->
                    FilterChip(selected = settings.titleMaxLines == lines, onClick = { onSetTitleMaxLines(lines) }, label = { Text(lines.toString()) })
                }
            }
        }
        SettingsSection("Card border") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryDisplayPrefs.CardBorder.entries.forEach { border ->
                    FilterChip(selected = settings.cardBorder == border, onClick = { onSetCardBorder(border) }, label = { Text(border.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
        }
        SettingsSection("Show on cards") {
            SwitchRow("Rating", settings.showRating, onSetShowRating)
            SwitchRow("Year", settings.showYear, onSetShowYear)
            SwitchRow("Episode count", settings.showEpisodes, onSetShowEpisodes)
            SwitchRow("SUB / DUB badges", settings.showSubDub, onSetShowSubDub)
            SwitchRow("Unwatched badge", settings.showUnwatchedBadge, onSetShowUnwatchedBadge)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    content()
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Phase H — Category pills shown on the library page (below the top bar).
 *
 * Pill-shaped (RoundedCornerShape(20dp)), horizontally scrollable.
 * Selected = primary background; unselected = surfaceContainerHigh.
 * Only shown when there's more than 1 category.
 */
@Composable
private fun LibraryCategoryPills(
    categories: List<CategoryStore.Category>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    onAddClick: () -> Unit,
) {
    // Rectangular field (8dp rounded — less rounded than pills, more like a contained section)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        LazyRow(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories) { category ->
                val isSelected = category.id == selectedId
                Surface(
                    modifier = Modifier.clickable { onSelect(category.id) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = category.name,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // "New" button for creating categories
            item {
                Surface(
                    modifier = Modifier.clickable { onAddClick() },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "New category", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}
