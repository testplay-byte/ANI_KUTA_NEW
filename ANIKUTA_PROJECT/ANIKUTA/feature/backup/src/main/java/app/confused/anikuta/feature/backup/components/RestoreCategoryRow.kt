package app.confused.anikuta.feature.backup.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.backup.RestoreCategoryResult
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * A single category row showing the category name + item count.
 *
 * Used inside the "WHAT WILL BE RESTORED" and "BREAKDOWN" sections of the
 * restore summary + complete dialogs. Displayed as a list (not grid) per
 * the owner's request for the restore complete dialog.
 *
 * @param result the per-category restore result.
 * @param showImported if true, shows importedCount (for restore complete);
 *   if false, shows skippedCount (for restore summary — "items to restore").
 */
@Composable
fun RestoreCategoryRow(
    result: RestoreCategoryResult,
    showImported: Boolean = false,
) {
    val count = if (showImported) result.importedCount else result.skippedCount
    val hasData = count > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (hasData) Icons.Filled.CheckCircle else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (hasData) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = result.category.displayName,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            color = if (hasData) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (hasData) count.toString() else "—",
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (hasData) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
        )
    }
}
