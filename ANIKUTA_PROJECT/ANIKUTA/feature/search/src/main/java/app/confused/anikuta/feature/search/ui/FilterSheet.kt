@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.confused.anikuta.feature.search.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.search.viewmodel.SearchFilters

// ── Filter data (AniList enum values + display labels) ──────────────────────

val GENRES: List<String> = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mecha",
    "Music", "Mystery", "Psychological", "Romance", "Sci-Fi",
    "Slice of Life", "Sports", "Supernatural", "Thriller",
)

val SEASONS: List<Pair<String, String>> = listOf(
    "Winter" to "WINTER",
    "Spring" to "SPRING",
    "Summer" to "SUMMER",
    "Fall" to "FALL",
)

val FORMATS: List<Pair<String, String>> = listOf(
    "TV Series" to "TV",
    "Movie" to "MOVIE",
    "OVA" to "OVA",
    "ONA" to "ONA",
    "Special" to "SPECIAL",
    "TV Short" to "TV_SHORT",
)

val STATUSES: List<Pair<String, String>> = listOf(
    "Currently Airing" to "RELEASING",
    "Finished" to "FINISHED",
    "Upcoming" to "NOT_YET_RELEASED",
    "Cancelled" to "CANCELLED",
)

val YEARS: List<Int> = (2025 downTo 1990).toList()

val FILTER_SORT_OPTIONS: List<Pair<String, String>> = listOf(
    "Popularity" to "POPULARITY_DESC",
    "Score" to "SCORE_DESC",
    "Newest" to "START_DATE_DESC",
    "Title A-Z" to "TITLE_ROMAJI",
    "Trending" to "TRENDING_DESC",
    "Favourites" to "FAVOURITES_DESC",
)

// ── View-mode + section identifiers ─────────────────────────────────────────

private enum class FilterViewMode { ACCORDION, FLAT }

private enum class FlatCategory(val label: String) {
    GENRE("Genre"), RELEASE("Release"), TYPE("Type"), SCORE("Score"), SORT("Sort"),
}

private enum class AccordionSection { GENRES, RELEASE, TYPE, SCORE, SORT }

/**
 * The filter sheet — a `ModalBottomSheet` with two view modes (Accordion + Flat).
 *
 * Ported from the prototype's `FilterSheet.kt`. Design language rules enforced:
 * - **`dragHandle = null`** (principle #2 — no drag handle on bottom-up menus).
 * - Partial height (principle #3) — content scrolls, sheet never full-screen.
 * - All accent colors from `MaterialTheme.colorScheme` (#B1F256 lime green).
 *
 * **Buffered filters (per owner request):** the sheet edits a PENDING copy of
 * the filters ([pendingFilters] via [onPendingFiltersChange]). The ViewModel
 * does NOT re-fetch on every toggle — only when the user taps "Apply filters"
 * ([onApply] → the VM syncs pending → applied + re-searches). This fixes the
 * owner's report: "it was processing the results even before I clicked the
 * apply button."
 *
 * Sort is applied live (single-select, instant) — the owner didn't ask for
 * sort to be buffered, and the sort chips are clearly the final choice.
 *
 * The Flat view's content panel uses `Modifier.animateContentSize()` so the
 * sheet height transitions smoothly when switching tabs (per owner request:
 * "its height should smoothly move up and down... it suddenly jumps").
 *
 * @param show whether the sheet is visible.
 * @param pendingFilters the in-progress filters (edited live, applied on Apply).
 * @param appliedSort the current sort (applied live).
 * @param onPendingFiltersChange called on every filter toggle (updates pending only).
 * @param onSortChange called when the sort changes (applied live).
 * @param onClearAll called when "Clear all" is tapped (clears pending + applied + re-fetches).
 * @param onApply called when "Apply filters" is tapped (syncs pending → applied + closes).
 * @param onDismiss called when the sheet is dismissed (scrim tap / back).
 */
@Composable
fun FilterSheet(
    show: Boolean,
    pendingFilters: SearchFilters,
    appliedSort: String,
    onPendingFiltersChange: (SearchFilters) -> Unit,
    onSortChange: (String) -> Unit,
    onClearAll: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var viewMode by remember { mutableStateOf(FilterViewMode.ACCORDION) }
    var openAccordionId by remember { mutableStateOf<AccordionSection?>(null) }
    var flatCategory by remember { mutableStateOf(FlatCategory.GENRE) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null, // principle #2 — NO drag handle
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            // Header
            FilterHeader(
                viewMode = viewMode,
                onViewModeChange = { mode ->
                    viewMode = mode
                    openAccordionId = null
                },
            )

            // Body — scrollable
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            ) {
                if (viewMode == FilterViewMode.ACCORDION) {
                    AccordionView(
                        openId = openAccordionId,
                        onToggle = { id ->
                            openAccordionId = if (openAccordionId == id) null else id
                        },
                        filters = pendingFilters,
                        onFiltersChange = onPendingFiltersChange,
                        sort = appliedSort,
                        onSortChange = onSortChange,
                    )
                } else {
                    FlatView(
                        flatCategory = flatCategory,
                        onCategoryChange = { flatCategory = it },
                        filters = pendingFilters,
                        onFiltersChange = onPendingFiltersChange,
                        sort = appliedSort,
                        onSortChange = onSortChange,
                    )
                }
            }

            // Bottom actions
            FilterActions(
                onClearAll = {
                    openAccordionId = null
                    onClearAll()
                },
                onApply = onApply,
            )
        }
    }
}

// ── Header ──────────────────────────────────────────────────────────────────

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
            fontFamily = RobotoFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.02).sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
            .background(if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
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

// ── Accordion view ──────────────────────────────────────────────────────────

@Composable
private fun AccordionView(
    openId: AccordionSection?,
    onToggle: (AccordionSection) -> Unit,
    filters: SearchFilters,
    onFiltersChange: (SearchFilters) -> Unit,
    sort: String,
    onSortChange: (String) -> Unit,
) {
    AccordionSection(
        label = "Genres",
        count = filters.genres.size,
        icon = Icons.Filled.GridView,
        isOpen = openId == AccordionSection.GENRES,
        onToggle = { onToggle(AccordionSection.GENRES) },
    ) {
        GenresContent(
            selected = filters.genres,
            onToggle = { g ->
                onFiltersChange(filters.copy(genres = if (g in filters.genres) filters.genres - g else filters.genres + g))
            },
        )
    }

    AccordionSection(
        label = "Release",
        count = (if (filters.year != null) 1 else 0) + (if (filters.season != null) 1 else 0),
        icon = Icons.Filled.CalendarMonth,
        isOpen = openId == AccordionSection.RELEASE,
        onToggle = { onToggle(AccordionSection.RELEASE) },
    ) {
        ReleaseContent(
            selectedYear = filters.year,
            onYearSelect = { y -> onFiltersChange(filters.copy(year = y)) },
            selectedSeason = filters.season,
            onSeasonSelect = { s -> onFiltersChange(filters.copy(season = s)) },
        )
    }

    AccordionSection(
        label = "Type",
        count = (if (filters.format != null) 1 else 0) + (if (filters.status != null) 1 else 0),
        icon = Icons.Filled.Category,
        isOpen = openId == AccordionSection.TYPE,
        onToggle = { onToggle(AccordionSection.TYPE) },
    ) {
        TypeContent(
            selectedFormat = filters.format,
            onFormatSelect = { f -> onFiltersChange(filters.copy(format = f)) },
            selectedStatus = filters.status,
            onStatusSelect = { s -> onFiltersChange(filters.copy(status = s)) },
        )
    }

    AccordionSection(
        label = "Minimum score",
        count = if (filters.minScore > 0) 1 else 0,
        icon = Icons.Filled.Star,
        isOpen = openId == AccordionSection.SCORE,
        onToggle = { onToggle(AccordionSection.SCORE) },
    ) {
        ScoreContent(
            value = filters.minScore.toFloat(),
            onChange = { v -> onFiltersChange(filters.copy(minScore = v.toInt())) },
        )
    }

    AccordionSection(
        label = "Sort by",
        count = 0,
        icon = Icons.Filled.Sort,
        isOpen = openId == AccordionSection.SORT,
        onToggle = { onToggle(AccordionSection.SORT) },
    ) {
        SortContent(selected = sort, onSelect = onSortChange)
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
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isOpen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = if (isOpen) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isOpen) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface),
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
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isOpen) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
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
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isOpen) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = if (isOpen) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).rotate(if (isOpen) 180f else 0f),
            )
        }
        AnimatedVisibility(
            visible = isOpen,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(200)),
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
                content()
            }
        }
    }
}

// ── Flat view ───────────────────────────────────────────────────────────────

@Composable
private fun FlatView(
    flatCategory: FlatCategory,
    onCategoryChange: (FlatCategory) -> Unit,
    filters: SearchFilters,
    onFiltersChange: (SearchFilters) -> Unit,
    sort: String,
    onSortChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                        .background(if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp)
                .animateContentSize(),
        ) {
            when (flatCategory) {
                FlatCategory.GENRE -> GenresContent(
                    selected = filters.genres,
                    onToggle = { g ->
                        onFiltersChange(filters.copy(genres = if (g in filters.genres) filters.genres - g else filters.genres + g))
                    },
                )
                FlatCategory.RELEASE -> ReleaseContent(
                    selectedYear = filters.year,
                    onYearSelect = { y -> onFiltersChange(filters.copy(year = y)) },
                    selectedSeason = filters.season,
                    onSeasonSelect = { s -> onFiltersChange(filters.copy(season = s)) },
                )
                FlatCategory.TYPE -> TypeContent(
                    selectedFormat = filters.format,
                    onFormatSelect = { f -> onFiltersChange(filters.copy(format = f)) },
                    selectedStatus = filters.status,
                    onStatusSelect = { s -> onFiltersChange(filters.copy(status = s)) },
                )
                FlatCategory.SCORE -> ScoreContent(
                    value = filters.minScore.toFloat(),
                    onChange = { v -> onFiltersChange(filters.copy(minScore = v.toInt())) },
                )
                FlatCategory.SORT -> SortContent(selected = sort, onSelect = onSortChange)
            }
        }
    }
}

// ── Section contents (shared between accordion + flat) ─────────────────────

@Composable
private fun GenresContent(selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GENRES.forEach { genre ->
            FilterChipPill(label = genre, isSelected = genre in selected, onClick = { onToggle(genre) })
        }
    }
}

@Composable
private fun ReleaseContent(
    selectedYear: Int?,
    onYearSelect: (Int?) -> Unit,
    selectedSeason: String?,
    onSeasonSelect: (String?) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CyclePill(
            label = if (selectedYear == null) "Year: Any" else "Year: $selectedYear",
            onClick = {
                if (selectedYear == null) onYearSelect(YEARS.first())
                else {
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
                val values = SEASONS.map { it.second }
                if (selectedSeason == null) onSeasonSelect(values.first())
                else {
                    val idx = values.indexOf(selectedSeason)
                    if (idx in 0 until values.lastIndex) onSeasonSelect(values[idx + 1])
                    else onSeasonSelect(null)
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TypeContent(
    selectedFormat: String?,
    onFormatSelect: (String?) -> Unit,
    selectedStatus: String?,
    onStatusSelect: (String?) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val formatLabel = FORMATS.firstOrNull { it.second == selectedFormat }?.first ?: "Any"
        CyclePill(
            label = "Format: $formatLabel",
            onClick = {
                val values = FORMATS.map { it.second }
                if (selectedFormat == null) onFormatSelect(values.first())
                else {
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
                val values = STATUSES.map { it.second }
                if (selectedStatus == null) onStatusSelect(values.first())
                else {
                    val idx = values.indexOf(selectedStatus)
                    if (idx in 0 until values.lastIndex) onStatusSelect(values[idx + 1])
                    else onStatusSelect(null)
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ScoreContent(value: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..100f,
            steps = 19,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (value <= 0f) "Any" else "${"%.1f".format(value / 10f)}+",
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun SortContent(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FILTER_SORT_OPTIONS.forEach { (value, label) ->
            FilterChipPill(label = label, isSelected = value == selected, onClick = { onSelect(value) })
        }
    }
}

// ── Reusable chips ──────────────────────────────────────────────────────────

@Composable
private fun FilterChipPill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (isSelected) {
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
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CyclePill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
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

// ── Bottom actions ─────────────────────────────────────────────────────────

@Composable
private fun FilterActions(onClearAll: () -> Unit, onApply: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Clear all — outlined pill
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
        // Apply filters — filled pill
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
                fontFamily = RobotoFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
