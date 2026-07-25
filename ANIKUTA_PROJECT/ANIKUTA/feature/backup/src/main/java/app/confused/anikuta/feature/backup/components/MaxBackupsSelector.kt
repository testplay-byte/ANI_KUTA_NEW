package app.confused.anikuta.feature.backup.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.backup.BackupPreferences
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * A 4-cell grid selector for the max auto-backups to keep (1-4).
 *
 * Each cell shows a number (1, 2, 3, 4). The selected cell is filled with
 * primary color; inactive are surfaceVariant.
 *
 * @param selected the currently selected max-keep value (1-4).
 * @param onSelect called when a number is tapped.
 */
@Composable
fun MaxBackupsSelector(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (n in BackupPreferences.MAX_KEEP_MIN..BackupPreferences.MAX_KEEP_MAX) {
            val isSelected = n == selected
            Surface(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(n) },
            ) {
                Text(
                    text = n.toString(),
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .padding(vertical = 14.dp)
                        .align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}
