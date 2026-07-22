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
import app.confused.anikuta.core.designsystem.component.AnikutaBottomSheet
import app.confused.anikuta.core.designsystem.component.CustomToggle
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * The customize categories — each maps to a section of settings shown below
 * the horizontally-scrollable category tab strip.
 */
private enum class CustomizeCategory(val label: String) {
    DISPLAY("Display"),
    BADGES("Badges"),
    SECTIONS("Sections"),
}

/**
 * Customize sheet — bottom-up, no drag handle (design principle #2).
 *
 * Redesigned (round 3) per user feedback:
 *  - Category tab strip at the top (horizontally scrollable) — user picks a
 *    category, and the relevant options appear below.
 *  - Capped at 70% of screen height (per user: "does not take up more than
 *    70% of the device's height").
 *  - Full scrolling functionality for the options below.
 *  - Follows the design language aesthetic (lime accent, RobotoFamily
 *    ExtraBold headings, no drag handle).
 *
 * Categories:
 *  - Display: 4 display modes (Compact/Comfortable/Cover Only/List) + columns
 *  - Badges: episode badge mode (Released/Total/Off) + score badge toggle
 *  - Sections: continue watching toggle + total entries in header toggle
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
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onEpisodeBadgeModeChange: (EpisodeBadgeMode) -> Unit,
    onShowScoreBadgeChange: (Boolean) -> Unit,
    onShowContinueWatchingChange: (Boolean) -> Unit,
    onShowTotalEntriesChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // Cap the sheet at 70% of screen height.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.7f

    var activeCategory by remember { mutableIntStateOf(0) }
    val categories = CustomizeCategory.entries

    AnikutaBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxSheetHeight),
        ) {
            // ── Header ──
            Text(
                text = "Customize Library",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            // ── Category tab strip (horizontally scrollable) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEachIndexed { index, category ->
                    val isActive = index == activeCategory
                    Surface(
                        color = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { activeCategory = index },
                    ) {
                        Text(
                            text = category.label,
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ── Options below — scrollable ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (categories[activeCategory]) {
                    CustomizeCategory.DISPLAY -> DisplayOptions(
                        displayMode = displayMode,
                        columns = columns,
                        onDisplayModeChange = onDisplayModeChange,
                        onColumnsChange = onColumnsChange,
                    )
                    CustomizeCategory.BADGES -> BadgeOptions(
                        episodeBadgeMode = episodeBadgeMode,
                        showScoreBadge = showScoreBadge,
                        onEpisodeBadgeModeChange = onEpisodeBadgeModeChange,
                        onShowScoreBadgeChange = onShowScoreBadgeChange,
                    )
                    CustomizeCategory.SECTIONS -> SectionOptions(
                        showContinueWatching = showContinueWatching,
                        showTotalEntries = showTotalEntries,
                        onShowContinueWatchingChange = onShowContinueWatchingChange,
                        onShowTotalEntriesChange = onShowTotalEntriesChange,
                    )
                }
            }
        }
    }
}

// ── Display options ──

@Composable
private fun DisplayOptions(
    displayMode: LibraryDisplayMode,
    columns: Int,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onColumnsChange: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            OptionLabel("Display Mode")
        }
        item {
            SelectableRow(
                label = "Compact Grid",
                isSelected = displayMode == LibraryDisplayMode.COMPACT_GRID,
                onClick = { onDisplayModeChange(LibraryDisplayMode.COMPACT_GRID) },
            )
        }
        item {
            SelectableRow(
                label = "Comfortable Grid",
                isSelected = displayMode == LibraryDisplayMode.COMFORTABLE_GRID,
                onClick = { onDisplayModeChange(LibraryDisplayMode.COMFORTABLE_GRID) },
            )
        }
        item {
            SelectableRow(
                label = "Cover Only",
                isSelected = displayMode == LibraryDisplayMode.COVER_ONLY,
                onClick = { onDisplayModeChange(LibraryDisplayMode.COVER_ONLY) },
            )
        }
        item {
            SelectableRow(
                label = "List",
                isSelected = displayMode == LibraryDisplayMode.LIST,
                onClick = { onDisplayModeChange(LibraryDisplayMode.LIST) },
            )
        }
        // Columns (only for grid modes)
        if (displayMode != LibraryDisplayMode.LIST) {
            item {
                Spacer(Modifier.height(12.dp))
                OptionLabel("Columns")
            }
            item {
                val colOptions = listOf("2", "3", "4", "5")
                val colSelected = (columns - 2).coerceIn(0, 3)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    colOptions.forEachIndexed { idx, label ->
                        val isSelected = idx == colSelected
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onColumnsChange(idx + 2) },
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
    }
}

// ── Badge options ──

@Composable
private fun BadgeOptions(
    episodeBadgeMode: EpisodeBadgeMode,
    showScoreBadge: Boolean,
    onEpisodeBadgeModeChange: (EpisodeBadgeMode) -> Unit,
    onShowScoreBadgeChange: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            OptionLabel("Episode Count")
        }
        item {
            SelectableRow(
                label = "Released episodes",
                isSelected = episodeBadgeMode == EpisodeBadgeMode.RELEASED,
                onClick = { onEpisodeBadgeModeChange(EpisodeBadgeMode.RELEASED) },
            )
        }
        item {
            SelectableRow(
                label = "Total episodes",
                isSelected = episodeBadgeMode == EpisodeBadgeMode.TOTAL,
                onClick = { onEpisodeBadgeModeChange(EpisodeBadgeMode.TOTAL) },
            )
        }
        item {
            SelectableRow(
                label = "Off",
                isSelected = episodeBadgeMode == EpisodeBadgeMode.OFF,
                onClick = { onEpisodeBadgeModeChange(EpisodeBadgeMode.OFF) },
            )
        }
        item {
            Spacer(Modifier.height(12.dp))
            OptionLabel("Score")
        }
        item {
            ToggleRow(
                label = "Show score badge",
                checked = showScoreBadge,
                onChange = onShowScoreBadgeChange,
            )
        }
    }
}

// ── Section options ──

@Composable
private fun SectionOptions(
    showContinueWatching: Boolean,
    showTotalEntries: Boolean,
    onShowContinueWatchingChange: (Boolean) -> Unit,
    onShowTotalEntriesChange: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            OptionLabel("Library Sections")
        }
        item {
            ToggleRow(
                label = "Continue Watching",
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
}

// ── Shared components ──

@Composable
private fun OptionLabel(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
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
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
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

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        CustomToggle(checked = checked, onChange = onChange)
    }
}
