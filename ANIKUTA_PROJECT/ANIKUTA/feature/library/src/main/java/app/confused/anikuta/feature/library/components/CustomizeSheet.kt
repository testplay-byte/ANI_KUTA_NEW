package app.confused.anikuta.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.EpisodeBadgeMode
import app.confused.anikuta.core.common.model.LibraryDisplayMode
import app.confused.anikuta.core.common.model.LibrarySortType
import app.confused.anikuta.core.designsystem.component.AnikutaBottomSheet
import app.confused.anikuta.core.designsystem.component.CustomToggle
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Unified library settings sheet — a SINGLE bottom-up sheet with 3 tabs at the
 * top: Sort, Display, Badges. All library settings are accessible from this one
 * sheet (no separate screens).
 *
 * Per user request: "I want one single bottom-up menu which has all the
 * available options. If the user clicks the settings button, it shows three
 * headings at the top: Filter, Sort, Display."
 *
 * The "Filter" tab is omitted for now (the library doesn't have a status filter
 * system — categories serve as the filter). Instead, the 3 tabs are:
 * - Sort: sort type (Title, Date Added, Last Watched, Progress, Total Episodes)
 *   + ascending/descending toggle
 * - Display: display mode (Compact/Comfortable/Cover Only/List) + columns (2-5)
 *   + title lines (1/2/3)
 * - Badges: episode badge mode (Released/Total/Off) + score badge toggle +
 *   continue watching toggle + total entries toggle
 *
 * Per design language: dragHandle = null (principle #2), max 70% screen height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeSheet(
    displayMode: LibraryDisplayMode,
    columns: Int,
    episodeBadgeMode: EpisodeBadgeMode,
    showScoreBadge: Boolean,
    showContinueWatching: Boolean,
    showTotalEntries: Boolean,
    titleLines: Int,
    sortType: LibrarySortType,
    sortAscending: Boolean,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onEpisodeBadgeModeChange: (EpisodeBadgeMode) -> Unit,
    onShowScoreBadgeChange: (Boolean) -> Unit,
    onShowContinueWatchingChange: (Boolean) -> Unit,
    onShowTotalEntriesChange: (Boolean) -> Unit,
    onTitleLinesChange: (Int) -> Unit,
    onSortChange: (LibrarySortType, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.7f

    var activeTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Sort", "Display", "Badges")

    AnikutaBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxSheetHeight),
        ) {
            // ── Header ──
            Text(
                text = "Library Settings",
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // ── Tab strip — 3 tabs at the top ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEachIndexed { index, label ->
                    val isActive = index == activeTab
                    Surface(
                        color = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { activeTab = index },
                    ) {
                        Text(
                            text = label,
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Tab content ──
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when (activeTab) {
                    0 -> sortTab(
                        sortType = sortType,
                        sortAscending = sortAscending,
                        onSortChange = onSortChange,
                    )
                    1 -> displayTab(
                        displayMode = displayMode,
                        columns = columns,
                        titleLines = titleLines,
                        onDisplayModeChange = onDisplayModeChange,
                        onColumnsChange = onColumnsChange,
                        onTitleLinesChange = onTitleLinesChange,
                    )
                    2 -> badgesTab(
                        episodeBadgeMode = episodeBadgeMode,
                        showScoreBadge = showScoreBadge,
                        showContinueWatching = showContinueWatching,
                        showTotalEntries = showTotalEntries,
                        onEpisodeBadgeModeChange = onEpisodeBadgeModeChange,
                        onShowScoreBadgeChange = onShowScoreBadgeChange,
                        onShowContinueWatchingChange = onShowContinueWatchingChange,
                        onShowTotalEntriesChange = onShowTotalEntriesChange,
                    )
                }
            }
        }
    }
}

// ── Sort tab ──

private fun androidx.compose.foundation.lazy.LazyListScope.sortTab(
    sortType: LibrarySortType,
    sortAscending: Boolean,
    onSortChange: (LibrarySortType, Boolean) -> Unit,
) {
    item { OptionLabel("Sort by") }
    LibrarySortType.entries.forEach { type ->
        item {
            SelectableRow(
                label = type.displayName,
                isSelected = sortType == type,
                onClick = { onSortChange(type, sortAscending) },
            )
        }
    }
    item {
        Spacer(Modifier.height(12.dp))
        OptionLabel("Direction")
    }
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("Ascending" to true, "Descending" to false).forEach { (label, asc) ->
                val isSelected = sortAscending == asc
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).clickable { onSortChange(sortType, asc) },
                ) {
                    Text(
                        text = label,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// ── Display tab ──

private fun androidx.compose.foundation.lazy.LazyListScope.displayTab(
    displayMode: LibraryDisplayMode,
    columns: Int,
    titleLines: Int,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onTitleLinesChange: (Int) -> Unit,
) {
    item { OptionLabel("Display Mode") }
    listOf(
        "Compact Grid" to LibraryDisplayMode.COMPACT_GRID,
        "Comfortable Grid" to LibraryDisplayMode.COMFORTABLE_GRID,
        "Cover Only" to LibraryDisplayMode.COVER_ONLY,
        "List" to LibraryDisplayMode.LIST,
    ).forEach { (label, mode) ->
        item {
            SelectableRow(
                label = label,
                isSelected = displayMode == mode,
                onClick = { onDisplayModeChange(mode) },
            )
        }
    }

    if (displayMode != LibraryDisplayMode.LIST) {
        item {
            Spacer(Modifier.height(12.dp))
            OptionLabel("Columns per row")
        }
        item {
            SegmentedButtons(
                options = listOf("2" to 2, "3" to 3, "4" to 4, "5" to 5),
                selected = columns,
                onSelect = onColumnsChange,
            )
        }
    }

    item {
        Spacer(Modifier.height(12.dp))
        OptionLabel("Title lines")
    }
    item {
        SegmentedButtons(
            options = listOf("1" to 1, "2" to 2, "3" to 3),
            selected = titleLines,
            onSelect = onTitleLinesChange,
        )
    }
}

// ── Badges tab ──

private fun androidx.compose.foundation.lazy.LazyListScope.badgesTab(
    episodeBadgeMode: EpisodeBadgeMode,
    showScoreBadge: Boolean,
    showContinueWatching: Boolean,
    showTotalEntries: Boolean,
    onEpisodeBadgeModeChange: (EpisodeBadgeMode) -> Unit,
    onShowScoreBadgeChange: (Boolean) -> Unit,
    onShowContinueWatchingChange: (Boolean) -> Unit,
    onShowTotalEntriesChange: (Boolean) -> Unit,
) {
    item { OptionLabel("Episode Badge") }
    listOf(
        "Released" to EpisodeBadgeMode.RELEASED,
        "Total" to EpisodeBadgeMode.TOTAL,
        "Off" to EpisodeBadgeMode.OFF,
    ).forEach { (label, mode) ->
        item {
            SelectableRow(
                label = label,
                isSelected = episodeBadgeMode == mode,
                onClick = { onEpisodeBadgeModeChange(mode) },
            )
        }
    }

    item {
        Spacer(Modifier.height(12.dp))
        OptionLabel("Toggles")
    }
    item {
        ToggleRow(
            label = "Show score badge",
            checked = showScoreBadge,
            onChange = onShowScoreBadgeChange,
        )
    }
    item {
        ToggleRow(
            label = "Show continue watching",
            checked = showContinueWatching,
            onChange = onShowContinueWatchingChange,
        )
    }
    item {
        ToggleRow(
            label = "Show total entries in header",
            checked = showTotalEntries,
            onChange = onShowTotalEntriesChange,
        )
    }
}

// ── Shared components ──

@Composable
private fun OptionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.06.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun SelectableRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(20.dp),
            ) {}
        }
    }
}

@Composable
private fun SegmentedButtons(
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (label, value) ->
            val isSelected = selected == value
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).clickable { onSelect(value) },
            ) {
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        CustomToggle(
            checked = checked,
            onChange = onChange,
        )
    }
}
