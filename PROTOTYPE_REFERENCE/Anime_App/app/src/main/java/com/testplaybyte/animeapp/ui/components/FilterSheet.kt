@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.testplaybyte.animeapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ExperimentalMaterial3Api

// ─────────────────────────────────────────────────────────────────────────────
// Filter data — mirrors src/prototypes/anime-app/lib/filters.ts.
// All AniList enum values + display labels live here so the UI and any
// GraphQL client can share them.
// ─────────────────────────────────────────────────────────────────────────────

/** 16 genre chips shown in the Genres section (multi-select). */
val GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mecha",
    "Music", "Mystery", "Psychological", "Romance", "Sci-Fi",
    "Slice of Life", "Sports", "Supernatural", "Thriller",
)

/** (displayLabel, anilistEnum) pairs for Season — single-select. */
val SEASONS = listOf(
    "Winter" to "WINTER",
    "Spring" to "SPRING",
    "Summer" to "SUMMER",
    "Fall" to "FALL",
)

/** (displayLabel, anilistEnum) pairs for Format — single-select. */
val FORMATS = listOf(
    "TV Series" to "TV",
    "Movie" to "MOVIE",
    "OVA" to "OVA",
    "ONA" to "ONA",
    "Special" to "SPECIAL",
    "TV Short" to "TV_SHORT",
)

/** (displayLabel, anilistEnum) pairs for Status — single-select. */
val STATUSES = listOf(
    "Currently Airing" to "RELEASING",
    "Finished" to "FINISHED",
    "Upcoming" to "NOT_YET_RELEASED",
    "Cancelled" to "CANCELLED",
)

/** Descending year list — same range as the web prototype's getYearOptions(). */
val YEARS: List<Int> = (2025 downTo 1990).toList()

/** (anilistSortEnum, displayLabel) pairs for Sort — single-select. */
val SORT_OPTIONS = listOf(
    "POPULARITY_DESC" to "Popularity",
    "SCORE_DESC" to "Score",
    "START_DATE_DESC" to "Newest",
    "TITLE_ROMAJI" to "Title A-Z",
    "TRENDING_DESC" to "Trending",
    "FAVOURITES_DESC" to "Favourites",
)

// ─────────────────────────────────────────────────────────────────────────────
// View-mode + section identifiers
// ─────────────────────────────────────────────────────────────────────────────

private enum class FilterViewMode { ACCORDION, FLAT }

private enum class FlatCategory(val label: String) {
    GENRE("Genre"),
    RELEASE("Release"),
    TYPE("Type"),
    SCORE("Score"),
    SORT("Sort"),
}

private enum class AccordionSection {
    GENRES, RELEASE, TYPE, SCORE, SORT
}

// ─────────────────────────────────────────────────────────────────────────────
// FilterSheet — ModalBottomSheet replicating the web prototype's filter sheet
// (`src/prototypes/anime-app/components/filter-sheet.tsx`).
//
// TWO view modes (toggle in header):
//   - Accordion (default): 5 expandable sections, only ONE open at a time.
//       1. Genres   → multi-select FlowRow of 16 chips
//       2. Release  → Year cycle pill + Season cycle pill, side by side
//       3. Type     → Format cycle pill + Status cycle pill, side by side
//       4. Min score → Slider (0-100, step 5) + "Any" / "X.X+" label
//       5. Sort by  → single-select chip row of 6 sort options
//   - Flat: tab row at top + single content panel showing the selected
//     category (same content as the matching accordion section).
//
// Bottom actions: "Clear all" (outlined, left) + "Apply filters" (filled, right).
// Sheet has NO drag handle — only the scrim tap (default ModalBottomSheet
// behavior) closes it.
//
// Hoisted state: genres/year/season/format/status live in the caller.
// Internal state: view mode, open accordion section, flat tab, sort option,
// and min score (these have no upstream wiring in this version).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FilterSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onClearAll: () -> Unit,
    // Filter state (hoisted)
    selectedGenres: Set<String>,
    onGenreToggle: (String) -> Unit,
    selectedYear: Int?,
    onYearSelect: (Int?) -> Unit,
    selectedSeason: String?,
    onSeasonSelect: (String?) -> Unit,
    selectedFormat: String?,
    onFormatSelect: (String?) -> Unit,
    selectedStatus: String?,
    onStatusSelect: (String?) -> Unit,
) {
    if (!show) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Internal UI state — reset each time the sheet is shown (early-return
    // above means this composable leaves composition when show == false).
    var viewMode by remember { mutableStateOf(FilterViewMode.ACCORDION) }
    var openAccordionId by remember { mutableStateOf<AccordionSection?>(null) }
    var flatCategory by remember { mutableStateOf(FlatCategory.GENRE) }

    // Internal filter state — Sort + Min score have no caller-side wiring.
    var selectedSort by remember { mutableStateOf("POPULARITY_DESC") }
    var minScore by remember { mutableStateOf(0f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null, // NO dismiss handle — tap scrim to close
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            // ── Header — "Filters" title + view toggle ──
            FilterHeader(
                viewMode = viewMode,
                onViewModeChange = { mode ->
                    viewMode = mode
                    openAccordionId = null // collapse all when switching modes
                },
            )

            // ── Body — scrollable ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            ) {
                if (viewMode == FilterViewMode.ACCORDION) {
                    AccordionView(
                        openId = openAccordionId,
                        onToggle = { id ->
                            openAccordionId = if (openAccordionId == id) null else id
                        },
                        selectedGenres = selectedGenres,
                        onGenreToggle = onGenreToggle,
                        selectedYear = selectedYear,
                        onYearSelect = onYearSelect,
                        selectedSeason = selectedSeason,
                        onSeasonSelect = onSeasonSelect,
                        selectedFormat = selectedFormat,
                        onFormatSelect = onFormatSelect,
                        selectedStatus = selectedStatus,
                        onStatusSelect = onStatusSelect,
                        minScore = minScore,
                        onMinScoreChange = { minScore = it },
                        selectedSort = selectedSort,
                        onSortChange = { selectedSort = it },
                    )
                } else {
                    FlatView(
                        flatCategory = flatCategory,
                        onCategoryChange = { flatCategory = it },
                        selectedGenres = selectedGenres,
                        onGenreToggle = onGenreToggle,
                        selectedYear = selectedYear,
                        onYearSelect = onYearSelect,
                        selectedSeason = selectedSeason,
                        onSeasonSelect = onSeasonSelect,
                        selectedFormat = selectedFormat,
                        onFormatSelect = onFormatSelect,
                        selectedStatus = selectedStatus,
                        onStatusSelect = onStatusSelect,
                        minScore = minScore,
                        onMinScoreChange = { minScore = it },
                        selectedSort = selectedSort,
                        onSortChange = { selectedSort = it },
                    )
                }
            }

            // ── Bottom actions — Clear all (outlined) + Apply filters (filled) ──
            FilterActions(
                onClearAll = {
                    // Reset internal state alongside the caller's hoisted state.
                    selectedSort = "POPULARITY_DESC"
                    minScore = 0f
                    openAccordionId = null
                    onClearAll()
                },
                onApply = onApply,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header — "Filters" title (left) + view-mode toggle (right)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FilterHeader(
    viewMode: FilterViewMode,
    onViewModeChange: (FilterViewMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Filters",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // View-mode toggle pill container (surface-3 background)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ViewToggleButton(
                icon = Icons.Filled.ViewStream,
                contentDescription = "Accordion view",
                isActive = viewMode == FilterViewMode.ACCORDION,
                onClick = { onViewModeChange(FilterViewMode.ACCORDION) },
            )
            ViewToggleButton(
                icon = Icons.Filled.GridView,
                contentDescription = "Flat view",
                isActive = viewMode == FilterViewMode.FLAT,
                onClick = { onViewModeChange(FilterViewMode.FLAT) },
            )
        }
    }
}

@Composable
private fun ViewToggleButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Accordion view — 5 sections, only one open at a time
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccordionView(
    openId: AccordionSection?,
    onToggle: (AccordionSection) -> Unit,
    selectedGenres: Set<String>,
    onGenreToggle: (String) -> Unit,
    selectedYear: Int?,
    onYearSelect: (Int?) -> Unit,
    selectedSeason: String?,
    onSeasonSelect: (String?) -> Unit,
    selectedFormat: String?,
    onFormatSelect: (String?) -> Unit,
    selectedStatus: String?,
    onStatusSelect: (String?) -> Unit,
    minScore: Float,
    onMinScoreChange: (Float) -> Unit,
    selectedSort: String,
    onSortChange: (String) -> Unit,
) {
    AccordionSection(
        label = "Genres",
        count = selectedGenres.size,
        icon = Icons.Filled.GridView,
        isOpen = openId == AccordionSection.GENRES,
        onToggle = { onToggle(AccordionSection.GENRES) },
    ) {
        GenresContent(
            selected = selectedGenres,
            onToggle = onGenreToggle,
        )
    }

    AccordionSection(
        label = "Release",
        count = (if (selectedYear != null) 1 else 0) + (if (selectedSeason != null) 1 else 0),
        icon = Icons.Filled.CalendarMonth,
        isOpen = openId == AccordionSection.RELEASE,
        onToggle = { onToggle(AccordionSection.RELEASE) },
    ) {
        ReleaseContent(
            selectedYear = selectedYear,
            onYearSelect = onYearSelect,
            selectedSeason = selectedSeason,
            onSeasonSelect = onSeasonSelect,
        )
    }

    AccordionSection(
        label = "Type",
        count = (if (selectedFormat != null) 1 else 0) + (if (selectedStatus != null) 1 else 0),
        icon = Icons.Filled.Category,
        isOpen = openId == AccordionSection.TYPE,
        onToggle = { onToggle(AccordionSection.TYPE) },
    ) {
        TypeContent(
            selectedFormat = selectedFormat,
            onFormatSelect = onFormatSelect,
            selectedStatus = selectedStatus,
            onStatusSelect = onStatusSelect,
        )
    }

    AccordionSection(
        label = "Minimum score",
        count = if (minScore > 0f) 1 else 0,
        icon = Icons.Filled.Star,
        isOpen = openId == AccordionSection.SCORE,
        onToggle = { onToggle(AccordionSection.SCORE) },
    ) {
        ScoreContent(
            value = minScore,
            onChange = onMinScoreChange,
        )
    }

    AccordionSection(
        label = "Sort by",
        count = 0,
        icon = Icons.Filled.Sort,
        isOpen = openId == AccordionSection.SORT,
        onToggle = { onToggle(AccordionSection.SORT) },
    ) {
        SortContent(
            selected = selectedSort,
            onSelect = onSortChange,
        )
    }
}

@Composable
private fun AccordionSection(
    label: String,
    count: Int,
    icon: ImageVector,
    isOpen: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        // Header row — tappable, surface-3 bg normally; primaryContainer when open.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isOpen) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .border(
                    width = 1.dp,
                    color = if (isOpen) Color.Transparent
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Icon — 28dp circle, surface bg normally; tinted onPrimaryContainer when open.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isOpen) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surface,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isOpen) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            // Label
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isOpen) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // Count badge (only when > 0)
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isOpen) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOpen) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // Chevron — rotates 180° when open.
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = if (isOpen) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (isOpen) 180f else 0f),
            )
        }
        // Animated expand/collapse content.
        AnimatedVisibility(
            visible = isOpen,
            enter = expandVertically(animationSpec = tween(300)) +
                fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(300)) +
                fadeOut(animationSpec = tween(200)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            ) {
                content()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Flat view — tab row at top + single content panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FlatView(
    flatCategory: FlatCategory,
    onCategoryChange: (FlatCategory) -> Unit,
    selectedGenres: Set<String>,
    onGenreToggle: (String) -> Unit,
    selectedYear: Int?,
    onYearSelect: (Int?) -> Unit,
    selectedSeason: String?,
    onSeasonSelect: (String?) -> Unit,
    selectedFormat: String?,
    onFormatSelect: (String?) -> Unit,
    selectedStatus: String?,
    onStatusSelect: (String?) -> Unit,
    minScore: Float,
    onMinScoreChange: (Float) -> Unit,
    selectedSort: String,
    onSortChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Tab row — pill tabs in a surface-3 container, horizontally scrollable.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FlatCategory.entries.forEach { cat ->
                val isActive = cat == flatCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                        )
                        .clickable { onCategoryChange(cat) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = cat.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Content panel — same composables used by the accordion sections.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
        ) {
            when (flatCategory) {
                FlatCategory.GENRE -> GenresContent(
                    selected = selectedGenres,
                    onToggle = onGenreToggle,
                )
                FlatCategory.RELEASE -> ReleaseContent(
                    selectedYear = selectedYear,
                    onYearSelect = onYearSelect,
                    selectedSeason = selectedSeason,
                    onSeasonSelect = onSeasonSelect,
                )
                FlatCategory.TYPE -> TypeContent(
                    selectedFormat = selectedFormat,
                    onFormatSelect = onFormatSelect,
                    selectedStatus = selectedStatus,
                    onStatusSelect = onStatusSelect,
                )
                FlatCategory.SCORE -> ScoreContent(
                    value = minScore,
                    onChange = onMinScoreChange,
                )
                FlatCategory.SORT -> SortContent(
                    selected = selectedSort,
                    onSelect = onSortChange,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section content composables — shared between accordion + flat views
// ─────────────────────────────────────────────────────────────────────────────

/** Genres — multi-select FlowRow of pill chips. */
@Composable
private fun GenresContent(
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GENRES.forEach { genre ->
            FilterChipPill(
                label = genre,
                selected = genre in selected,
                onClick = { onToggle(genre) },
            )
        }
    }
}

/** Release — two side-by-side cycle pills (Year + Season). */
@Composable
private fun ReleaseContent(
    selectedYear: Int?,
    onYearSelect: (Int?) -> Unit,
    selectedSeason: String?,
    onSeasonSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CyclePill(
            label = if (selectedYear == null) "Year: Any" else "Year: $selectedYear",
            onClick = {
                // Cycle: Any → 2025 → 2024 → ... → 1990 → Any
                if (selectedYear == null) {
                    onYearSelect(YEARS.first())
                } else {
                    val idx = YEARS.indexOf(selectedYear)
                    if (idx in 0 until YEARS.lastIndex) onYearSelect(YEARS[idx + 1])
                    else onYearSelect(null)
                }
            },
            modifier = Modifier.weight(1f),
        )
        val seasonLabel = SEASONS.firstOrNull { it.second == selectedSeason }?.first ?: "Any"
        CyclePill(
            label = "Season: $seasonLabel",
            onClick = {
                // Cycle: Any → WINTER → SPRING → SUMMER → FALL → Any
                val values = SEASONS.map { it.second }
                if (selectedSeason == null) {
                    onSeasonSelect(values.first())
                } else {
                    val idx = values.indexOf(selectedSeason)
                    if (idx in 0 until values.lastIndex) onSeasonSelect(values[idx + 1])
                    else onSeasonSelect(null)
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/** Type — two side-by-side cycle pills (Format + Status). */
@Composable
private fun TypeContent(
    selectedFormat: String?,
    onFormatSelect: (String?) -> Unit,
    selectedStatus: String?,
    onStatusSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val formatLabel = FORMATS.firstOrNull { it.second == selectedFormat }?.first ?: "Any"
        CyclePill(
            label = "Format: $formatLabel",
            onClick = {
                // Cycle: Any → TV → MOVIE → ... → TV_SHORT → Any
                val values = FORMATS.map { it.second }
                if (selectedFormat == null) {
                    onFormatSelect(values.first())
                } else {
                    val idx = values.indexOf(selectedFormat)
                    if (idx in 0 until values.lastIndex) onFormatSelect(values[idx + 1])
                    else onFormatSelect(null)
                }
            },
            modifier = Modifier.weight(1f),
        )
        val statusLabel = STATUSES.firstOrNull { it.second == selectedStatus }?.first ?: "Any"
        CyclePill(
            label = "Status: $statusLabel",
            onClick = {
                // Cycle: Any → RELEASING → FINISHED → NOT_YET_RELEASED → CANCELLED → Any
                val values = STATUSES.map { it.second }
                if (selectedStatus == null) {
                    onStatusSelect(values.first())
                } else {
                    val idx = values.indexOf(selectedStatus)
                    if (idx in 0 until values.lastIndex) onStatusSelect(values[idx + 1])
                    else onStatusSelect(null)
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/** Minimum score — Slider (0-100, step 5) + value label ("Any" or "X.X+"). */
@Composable
private fun ScoreContent(
    value: Float,
    onChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..100f,
            steps = 19, // (100 - 0) / 5 - 1 = 19 → 21 discrete steps (0, 5, 10, …, 100)
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (value <= 0f) "Any" else "${"%.1f".format(value / 10f)}+",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}

/** Sort by — single-select chip row of 6 sort options. */
@Composable
private fun SortContent(
    selected: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SORT_OPTIONS.forEach { (value, label) ->
            FilterChipPill(
                label = label,
                selected = value == selected,
                onClick = { onSelect(value) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable chip / pill primitives
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Filter chip — rounded pill, outline border when inactive, primaryContainer
 * bg + onPrimaryContainer text + checkmark icon when active.
 */
@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
            )
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Cycle pill — compact tappable pill styled like a dropdown. Shows the current
 * selection with a chevron; tapping advances to the next option (caller
 * controls the cycle logic via [onClick]).
 */
@Composable
private fun CyclePill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom actions — Clear all (outlined, left) + Apply filters (filled, right)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FilterActions(
    onClearAll: () -> Unit,
    onApply: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Clear all — outlined pill (transparent bg + outlineVariant border)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(50))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(50),
                )
                .clickable(onClick = onClearAll),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Clear all",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Apply filters — filled pill (primary bg + onPrimary text, bold)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onApply),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Apply filters",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
