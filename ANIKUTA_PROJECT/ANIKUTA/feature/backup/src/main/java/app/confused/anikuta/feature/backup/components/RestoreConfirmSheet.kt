package app.confused.anikuta.feature.backup.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.backup.BackupCategory
import app.confused.anikuta.core.backup.BackupFormatType
import app.confused.anikuta.core.backup.RestoreSummary
import app.confused.anikuta.core.designsystem.component.AnikutaBottomSheet
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Restore confirmation bottom sheet (dragHandle = null per design principle #2).
 *
 * Shows:
 * - The detected backup format (ANIKUTA / Aniyomi).
 * - When the backup was created.
 * - A per-category summary of what will be restored.
 * - A "Restore" button + a "Cancel" text button.
 *
 * @param summary the restore summary to display.
 * @param fileUri the URI of the backup file (passed back on confirm).
 * @param onConfirm called when the user taps "Restore".
 * @param onCancel called when the user dismisses the sheet.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RestoreConfirmSheet(
    summary: RestoreSummary,
    fileUri: Uri,
    onConfirm: (Uri) -> Unit,
    onCancel: () -> Unit,
) {
    AnikutaBottomSheet(onDismiss = onCancel) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Title
            Text(
                text = "Restore backup",
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Format + date info
            val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.getDefault())
            val dateStr = summary.createdAt?.let { dateFormat.format(Date(it)) } ?: "Unknown date"

            RestoreInfoRow(label = "Format", value = summary.formatType.displayName)
            RestoreInfoRow(label = "Created", value = dateStr)
            Spacer(modifier = Modifier.height(12.dp))

            // Category summary
            Text(
                text = "WHAT WILL BE RESTORED",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.06.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Category summary — scrollable if long
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                summary.categoryResults.forEach { result ->
                    RestoreCategoryRow(result)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onConfirm(fileUri) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "Restore",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun RestoreInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RestoreCategoryRow(result: app.confused.anikuta.core.backup.RestoreCategoryResult) {
    val category = result.category
    val hasData = result.skippedCount > 0
    val icon = if (hasData) Icons.Filled.CheckCircle else Icons.Filled.Warning
    val iconTint = if (hasData) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.displayName,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val countText = when {
                result.skippedCount > 0 -> "${result.skippedCount} items"
                !result.note.isNullOrBlank() -> result.note!!
                else -> "No data"
            }
            Text(
                text = countText,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
