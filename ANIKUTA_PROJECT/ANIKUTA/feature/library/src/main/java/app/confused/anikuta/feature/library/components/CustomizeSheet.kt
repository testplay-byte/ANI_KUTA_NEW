package app.confused.anikuta.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.EpisodeBadgeMode
import app.confused.anikuta.core.common.model.LibraryDisplayMode
import app.confused.anikuta.core.designsystem.component.AnikutaBottomSheet
import app.confused.anikuta.core.designsystem.component.CustomToggle
import app.confused.anikuta.core.designsystem.component.SectionHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Customize sheet — bottom-up, no drag handle (design principle #2).
 *
 * Sections:
 *  - Display: Compact Grid / Comfortable Grid / Cover Only / List (4 options)
 *  - Columns: 2 / 3 / 4 / 5 (only for grid modes)
 *  - Episode badge: Released / Total / Off (3 options)
 *  - Badges: Score toggle
 *  - Sections: Continue Watching toggle, Total entries in header toggle
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
    AnikutaBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Customize Library",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))

            // ── Display Mode (4 options) ──
            SectionHeader(text = "Display")
            DisplayModeRow(
                label = "Compact Grid",
                isSelected = displayMode == LibraryDisplayMode.COMPACT_GRID,
                onClick = { onDisplayModeChange(LibraryDisplayMode.COMPACT_GRID) },
            )
            DisplayModeRow(
                label = "Comfortable Grid",
                isSelected = displayMode == LibraryDisplayMode.COMFORTABLE_GRID,
                onClick = { onDisplayModeChange(LibraryDisplayMode.COMFORTABLE_GRID) },
            )
            DisplayModeRow(
                label = "Cover Only",
                isSelected = displayMode == LibraryDisplayMode.COVER_ONLY,
                onClick = { onDisplayModeChange(LibraryDisplayMode.COVER_ONLY) },
            )
            DisplayModeRow(
                label = "List",
                isSelected = displayMode == LibraryDisplayMode.LIST,
                onClick = { onDisplayModeChange(LibraryDisplayMode.LIST) },
            )

            // ── Columns (only for grid modes) ──
            if (displayMode != LibraryDisplayMode.LIST) {
                Spacer(Modifier.height(16.dp))
                SectionHeader(text = "Columns")
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

            // ── Episode Badge Mode ──
            Spacer(Modifier.height(16.dp))
            SectionHeader(text = "Episode Count")
            DisplayModeRow(
                label = "Released episodes",
                isSelected = episodeBadgeMode == EpisodeBadgeMode.RELEASED,
                onClick = { onEpisodeBadgeModeChange(EpisodeBadgeMode.RELEASED) },
            )
            DisplayModeRow(
                label = "Total episodes",
                isSelected = episodeBadgeMode == EpisodeBadgeMode.TOTAL,
                onClick = { onEpisodeBadgeModeChange(EpisodeBadgeMode.TOTAL) },
            )
            DisplayModeRow(
                label = "Off",
                isSelected = episodeBadgeMode == EpisodeBadgeMode.OFF,
                onClick = { onEpisodeBadgeModeChange(EpisodeBadgeMode.OFF) },
            )

            // ── Badges ──
            Spacer(Modifier.height(16.dp))
            SectionHeader(text = "Badges")
            ToggleRow(
                label = "Show score",
                checked = showScoreBadge,
                onChange = onShowScoreBadgeChange,
            )

            // ── Sections ──
            Spacer(Modifier.height(16.dp))
            SectionHeader(text = "Sections")
            ToggleRow(
                label = "Continue Watching",
                checked = showContinueWatching,
                onChange = onShowContinueWatchingChange,
            )
            ToggleRow(
                label = "Show total entries in header",
                checked = showTotalEntries,
                onChange = onShowTotalEntriesChange,
            )
        }
    }
}

@Composable
private fun DisplayModeRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
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
