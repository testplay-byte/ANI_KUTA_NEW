package app.confused.anikuta.feature.library.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.BadgePosition
import app.confused.anikuta.core.common.model.EpisodeBadgeMode
import app.confused.anikuta.core.common.model.LibraryDisplayMode
import app.confused.anikuta.core.common.model.LibrarySortType
import app.confused.anikuta.core.designsystem.component.AnikutaBottomSheet
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Unified library settings sheet — a SINGLE bottom-up sheet with 2 tabs at the
 * top: Sort, and Display & Badges (combined per user request).
 *
 * Per user: "combine the display and badges together and show all of their
 * options in one single menu below."
 *
 * The 2 tabs each get a DEDICATED shared background container, are CENTERED,
 * and have a separator below them so the content below looks separate.
 * Per user: "add a dedicated background to both of those so that they look
 * combined together... center them... add some separator below them."
 *
 * Sort options use a full-fledged selection card (filled background + check
 * icon), with proper separation between each option. Per user: "I want a proper
 * full-fledged selection and I want you to add proper separation between each
 * one of the sort options."
 *
 * Display modes are shown as a 4-grid of visual cards. Per user: "show some
 * proper beautiful options in a grid, like a 4-grid."
 *
 * Badge position is configurable (top/bottom L/R). Compact grid restricts to
 * top-only. Per user: "if the user has selected compact grid then he will not
 * be given options to select the episode's badges on the bottom right or bottom
 * left corners."
 *
 * Toggles use proper Material3 Switch (not CustomToggle). Per user: "properly
 * add the toggles."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeSheet(
    displayMode: LibraryDisplayMode,
    columns: Int,
    episodeBadgeMode: EpisodeBadgeMode,
    episodeBadgePosition: BadgePosition,
    showScoreBadge: Boolean,
    scoreBadgePosition: BadgePosition,
    showContinueWatching: Boolean,
    showTotalEntries: Boolean,
    titleLines: Int,
    sortType: LibrarySortType,
    sortAscending: Boolean,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onEpisodeBadgeModeChange: (EpisodeBadgeMode) -> Unit,
    onEpisodeBadgePositionChange: (BadgePosition) -> Unit,
    onShowScoreBadgeChange: (Boolean) -> Unit,
    onScoreBadgePositionChange: (BadgePosition) -> Unit,
    onShowContinueWatchingChange: (Boolean) -> Unit,
    onShowTotalEntriesChange: (Boolean) -> Unit,
    onTitleLinesChange: (Int) -> Unit,
    onSortChange: (LibrarySortType, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.75f

    var activeTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Sort", "Display & Badges")

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

            // ── Tab strip — 2 tabs in a shared centered background ──
            // Per user: "add a dedicated background to both of those so that they
            // look combined together... center them."
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    tabs.forEachIndexed { index, label ->
                        val isActive = index == activeTab
                        Surface(
                            color = if (isActive) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { activeTab = index },
                        ) {
                            Text(
                                text = label,
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // ── Separator below the tabs ──
            // Per user: "add some separator below them so that the actual content
            // which is below them is actually being shown separately."
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 0.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.height(8.dp))

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
                    1 -> displayBadgesTab(
                        displayMode = displayMode,
                        columns = columns,
                        titleLines = titleLines,
                        episodeBadgeMode = episodeBadgeMode,
                        episodeBadgePosition = episodeBadgePosition,
                        showScoreBadge = showScoreBadge,
                        scoreBadgePosition = scoreBadgePosition,
                        showContinueWatching = showContinueWatching,
                        showTotalEntries = showTotalEntries,
                        onDisplayModeChange = onDisplayModeChange,
                        onColumnsChange = onColumnsChange,
                        onTitleLinesChange = onTitleLinesChange,
                        onEpisodeBadgeModeChange = onEpisodeBadgeModeChange,
                        onEpisodeBadgePositionChange = onEpisodeBadgePositionChange,
                        onShowScoreBadgeChange = onShowScoreBadgeChange,
                        onScoreBadgePositionChange = onScoreBadgePositionChange,
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
            SortOptionCard(
                label = type.displayName,
                isSelected = sortType == type,
                onClick = { onSortChange(type, sortAscending) },
            )
        }
        item { Spacer(Modifier.height(6.dp)) }
    }
    item {
        Spacer(Modifier.height(8.dp))
        OptionLabel("Direction")
    }
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Ascending" to true, "Descending" to false).forEach { (label, asc) ->
                val isSelected = sortAscending == asc
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).clickable { onSortChange(sortType, asc) },
                ) {
                    Text(
                        text = label,
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

// ── Display & Badges tab (combined) ──

private fun androidx.compose.foundation.lazy.LazyListScope.displayBadgesTab(
    displayMode: LibraryDisplayMode,
    columns: Int,
    titleLines: Int,
    episodeBadgeMode: EpisodeBadgeMode,
    episodeBadgePosition: BadgePosition,
    showScoreBadge: Boolean,
    scoreBadgePosition: BadgePosition,
    showContinueWatching: Boolean,
    showTotalEntries: Boolean,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onTitleLinesChange: (Int) -> Unit,
    onEpisodeBadgeModeChange: (EpisodeBadgeMode) -> Unit,
    onEpisodeBadgePositionChange: (BadgePosition) -> Unit,
    onShowScoreBadgeChange: (Boolean) -> Unit,
    onScoreBadgePositionChange: (BadgePosition) -> Unit,
    onShowContinueWatchingChange: (Boolean) -> Unit,
    onShowTotalEntriesChange: (Boolean) -> Unit,
) {
    // ── Display mode (4-grid of visual cards) ──
    item { OptionLabel("Display Mode") }
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DisplayModeCard(
                icon = Icons.Filled.GridView,
                label = "Compact",
                isSelected = displayMode == LibraryDisplayMode.COMPACT_GRID,
                onClick = { onDisplayModeChange(LibraryDisplayMode.COMPACT_GRID) },
                modifier = Modifier.weight(1f),
            )
            DisplayModeCard(
                icon = Icons.Filled.ViewAgenda,
                label = "Comfortable",
                isSelected = displayMode == LibraryDisplayMode.COMFORTABLE_GRID,
                onClick = { onDisplayModeChange(LibraryDisplayMode.COMFORTABLE_GRID) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    item { Spacer(Modifier.height(8.dp)) }
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DisplayModeCard(
                icon = Icons.Filled.GridView,
                label = "Cover Only",
                isSelected = displayMode == LibraryDisplayMode.COVER_ONLY,
                onClick = { onDisplayModeChange(LibraryDisplayMode.COVER_ONLY) },
                modifier = Modifier.weight(1f),
            )
            DisplayModeCard(
                icon = Icons.Filled.List,
                label = "List",
                isSelected = displayMode == LibraryDisplayMode.LIST,
                onClick = { onDisplayModeChange(LibraryDisplayMode.LIST) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    // ── Columns (grid modes only) ──
    if (displayMode != LibraryDisplayMode.LIST) {
        item {
            Spacer(Modifier.height(16.dp))
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

    // ── Title lines ──
    item {
        Spacer(Modifier.height(16.dp))
        OptionLabel("Title lines")
    }
    item {
        SegmentedButtons(
            options = listOf("1" to 1, "2" to 2, "3" to 3),
            selected = titleLines,
            onSelect = onTitleLinesChange,
        )
    }

    // ── Episode badge ──
    item {
        Spacer(Modifier.height(16.dp))
        OptionLabel("Episode Badge")
    }
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Released" to EpisodeBadgeMode.RELEASED, "Total" to EpisodeBadgeMode.TOTAL, "Off" to EpisodeBadgeMode.OFF).forEach { (label, mode) ->
                val isSelected = episodeBadgeMode == mode
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).clickable { onEpisodeBadgeModeChange(mode) },
                ) {
                    Text(
                        text = label,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }

    // ── Episode badge position (compact grid = top only) ──
    if (episodeBadgeMode != EpisodeBadgeMode.OFF) {
        item {
            Spacer(Modifier.height(12.dp))
            OptionLabel("Episode Badge Position")
        }
        item {
            BadgePositionSelector(
                selected = episodeBadgePosition,
                compactMode = displayMode == LibraryDisplayMode.COMPACT_GRID,
                onSelect = onEpisodeBadgePositionChange,
            )
        }
    }

    // ── Score badge ──
    item {
        Spacer(Modifier.height(16.dp))
        OptionLabel("Score Badge")
    }
    item {
        SwitchRow(
            label = "Show score badge",
            checked = showScoreBadge,
            onChange = onShowScoreBadgeChange,
        )
    }

    // ── Score badge position (compact grid = top only) ──
    if (showScoreBadge) {
        item {
            Spacer(Modifier.height(8.dp))
            OptionLabel("Score Badge Position")
        }
        item {
            BadgePositionSelector(
                selected = scoreBadgePosition,
                compactMode = displayMode == LibraryDisplayMode.COMPACT_GRID,
                onSelect = onScoreBadgePositionChange,
            )
        }
    }

    // ── Toggles ──
    item {
        Spacer(Modifier.height(16.dp))
        OptionLabel("Toggles")
    }
    item {
        SwitchRow(
            label = "Show continue watching",
            checked = showContinueWatching,
            onChange = onShowContinueWatchingChange,
        )
    }
    item {
        SwitchRow(
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

/**
 * A full-fledged sort-option card — filled background when selected + a check
 * icon on the right. Per user: "I want a proper full-fledged selection."
 * Separation between options is handled by the parent (6dp spacers between each).
 */
@Composable
private fun SortOptionCard(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 0.5.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            if (isSelected) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(20.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A display-mode visual card — icon on top, label below. Selected = primary
 * border + tinted background. Per user: "show some proper beautiful options in
 * a grid, like a 4-grid."
 */
@Composable
private fun DisplayModeCard(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 0.5.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Badge position selector — 4 quadrants (top-left, top-right, bottom-left,
 * bottom-right). In compact grid mode, only the top quadrants are available
 * (bottom is occupied by the title overlay). Per user: "if the user has
 * selected compact grid then he will not be given options to select the
 * episode's badges on the bottom right or bottom left corners."
 */
@Composable
private fun BadgePositionSelector(
    selected: BadgePosition,
    compactMode: Boolean,
    onSelect: (BadgePosition) -> Unit,
) {
    val positions = if (compactMode) {
        listOf(BadgePosition.TOP_START to "Top Left", BadgePosition.TOP_END to "Top Right")
    } else {
        listOf(
            BadgePosition.TOP_START to "Top Left",
            BadgePosition.TOP_END to "Top Right",
            BadgePosition.BOTTOM_START to "Bottom Left",
            BadgePosition.BOTTOM_END to "Bottom Right",
        )
    }
    // If the current selection isn't in the available set (e.g. switched to
    // compact while a bottom position was selected), auto-fall-back to TOP_END.
    val effectiveSelected = if (positions.any { it.first == selected }) selected else BadgePosition.TOP_END

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        positions.chunked(2).forEach { row ->
            // Each row of 2 buttons
        }
        // Render as a wrapping row (2 per row)
    }
    // Simpler: render 2 rows of 2 (or 1 row of 2 for compact)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        positions.chunked(2).forEach { chunk ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                chunk.forEach { (pos, label) ->
                    val isSelected = effectiveSelected == pos
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(pos) },
                    ) {
                        Text(
                            text = label,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
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
}

@Composable
private fun SegmentedButtons(
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (label, value) ->
            val isSelected = selected == value
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).clickable { onSelect(value) },
            ) {
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * A toggle row with a proper Material3 Switch (not CustomToggle).
 * Per user: "properly add the toggles."
 */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
