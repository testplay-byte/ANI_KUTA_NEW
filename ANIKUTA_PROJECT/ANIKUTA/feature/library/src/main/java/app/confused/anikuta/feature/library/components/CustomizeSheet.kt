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
import app.confused.anikuta.core.common.model.LibraryDisplayMode
import app.confused.anikuta.core.designsystem.component.AnikutaBottomSheet
import app.confused.anikuta.core.designsystem.component.CustomToggle
import app.confused.anikuta.core.designsystem.component.SectionHeader
import app.confused.anikuta.core.designsystem.component.TwoWayToggle
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Customize sheet — bottom-up, no drag handle (design principle #2).
 *
 * Per user decision Q3: display mode is GLOBAL.
 *
 * Sections:
 *  - Layout: Grid / List (2-way toggle)
 *  - Columns: 2 / 3 / 4 / 5 (only for grid)
 *  - Badges: Episode count, Score (toggles)
 *  - Continue Watching: show/hide (toggle)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeSheet(
    displayMode: LibraryDisplayMode,
    columns: Int,
    showEpisodeBadge: Boolean,
    showScoreBadge: Boolean,
    showContinueWatching: Boolean,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onShowEpisodeBadgeChange: (Boolean) -> Unit,
    onShowScoreBadgeChange: (Boolean) -> Unit,
    onShowContinueWatchingChange: (Boolean) -> Unit,
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

            // Layout
            SectionHeader(text = "Layout")
            val layoutSelected = if (displayMode == LibraryDisplayMode.LIST) 1 else 0
            TwoWayToggle(
                options = listOf("Grid", "List"),
                selected = layoutSelected,
                onSelect = { idx ->
                    onDisplayModeChange(
                        if (idx == 1) LibraryDisplayMode.LIST else LibraryDisplayMode.COMPACT_GRID
                    )
                },
            )

            Spacer(Modifier.height(16.dp))

            // Columns (only for grid)
            if (displayMode != LibraryDisplayMode.LIST) {
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
                Spacer(Modifier.height(16.dp))
            }

            // Badges
            SectionHeader(text = "Badges")
            ToggleRow(
                label = "Episode count",
                checked = showEpisodeBadge,
                onChange = onShowEpisodeBadgeChange,
            )
            ToggleRow(
                label = "Score",
                checked = showScoreBadge,
                onChange = onShowScoreBadgeChange,
            )
            Spacer(Modifier.height(16.dp))

            // Continue Watching
            SectionHeader(text = "Sections")
            ToggleRow(
                label = "Continue Watching",
                checked = showContinueWatching,
                onChange = onShowContinueWatchingChange,
            )
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
