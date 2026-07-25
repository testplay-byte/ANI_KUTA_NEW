package app.confused.anikuta.feature.backup.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.backup.AutoBackupFrequency
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * A 2x2 grid frequency selector for auto-backup.
 *
 * Layout:
 * ```
 * ┌──────────────┬──────────────┐
 * │ Every 6 hrs  │ Every 12 hrs │
 * ├──────────────┼──────────────┤
 * │ Every 24 hrs │   Weekly     │
 * └──────────────┴──────────────┘
 * ```
 *
 * Active cell is filled with primary color; inactive are surfaceVariant.
 * Matches the design language (principle #8 — multi-way toggle).
 *
 * @param selected the currently selected frequency.
 * @param onSelect called when a frequency is tapped.
 */
@Composable
fun FrequencySelector(
    selected: AutoBackupFrequency,
    onSelect: (AutoBackupFrequency) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frequencies = AutoBackupFrequency.entries
    // Row 1: 6h + 12h, Row 2: 24h + Weekly
    val row1 = frequencies.take(2)
    val row2 = frequencies.drop(2)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FrequencyRow(row1, selected, onSelect)
        FrequencyRow(row2, selected, onSelect)
    }
}

@Composable
private fun FrequencyRow(
    frequencies: List<AutoBackupFrequency>,
    selected: AutoBackupFrequency,
    onSelect: (AutoBackupFrequency) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        frequencies.forEach { freq ->
            val isSelected = freq == selected
            Surface(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(freq) },
            ) {
                Text(
                    text = freq.displayName,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                )
            }
        }
    }
}
