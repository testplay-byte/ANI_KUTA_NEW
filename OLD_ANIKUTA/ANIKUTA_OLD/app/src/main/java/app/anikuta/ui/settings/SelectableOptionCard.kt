package app.anikuta.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A custom selectable-option card selector — cleaner than segmented buttons
 * for 2-4 options. Each option is a tappable card with a title, optional
 * subtitle, and a checkmark when selected.
 *
 * Color scheme (matches the details-page layout settings design language):
 *  - Selected: `primary` border (NOT primaryContainer — that was too dark/blue)
 *    + `primary` tint text + checkmark. Background stays surface.
 *  - Unselected: `outlineVariant` border + `onSurface` text.
 *
 * @param title The group label shown above the options.
 * @param subtitle Optional description below the title.
 * @param options List of (value, label, optional description) tuples.
 * @param selectedValue The currently-selected value.
 * @param onSelect Called when an option is tapped.
 */
@Composable
internal fun SelectableOptionCard(
    title: String,
    subtitle: String? = null,
    options: List<Triple<String, String, String?>>,
    selectedValue: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        // Options with clear visual separation between each card (8dp gap).
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label, desc) ->
                val isSelected = value == selectedValue
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface, // neutral surface, not dark blue
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(value) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                            if (desc != null) {
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A styled segmented button row — matches the details-page LayoutSettingsScreen
 * design language. Rounded container with pill-style selected/unselected states.
 *
 * - Container: `surfaceVariant` at 50% alpha (soft neutral).
 * - Selected pill: `primary` background + `onPrimary` text (NOT primaryContainer).
 * - Unselected pill: transparent + `onSurfaceVariant` text.
 *
 * Use this for 2-3 short-label options where a card selector is overkill.
 */
@Composable
internal fun StyledSegmentedRow(
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { index, (label, selected) ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary
                        else androidx.compose.ui.graphics.Color.Transparent,
                    onClick = { onSelect(index) },
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}
