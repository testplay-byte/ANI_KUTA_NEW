package app.confused.anikuta.feature.backup.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.backup.RestoreSummary
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Restore confirmation dialog with highlighted sections.
 *
 * Shown after the user selects a backup file. Shows:
 * - Title with restore icon
 * - Format + date info in a dedicated highlighted card
 * - Highlighted total items to restore
 * - "What will be restored" section with a dedicated background wrapping all entries
 * - Restore + Cancel buttons
 *
 * Design: #B1F256 primary, RobotoFamily, surface2 for contrast.
 */
@Composable
fun RestoreSummaryDialog(
    summary: RestoreSummary,
    fileUri: Uri,
    onConfirm: (Uri) -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            Button(onClick = { onConfirm(fileUri) }) {
                Text("Restore", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Restore backup",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Format + date in a dedicated highlighted card ──
                val dateStr = summary.createdAt?.let {
                    SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.getDefault()).format(Date(it))
                } ?: "Unknown date"
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        InfoRow(label = "Format", value = summary.formatType.displayName)
                        InfoRow(label = "Created", value = dateStr)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Highlighted total ──
                val totalItems = summary.categoryResults.sumOf { it.skippedCount }
                StatCard(
                    label = "Total items to restore",
                    value = totalItems.toString(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── "What will be restored" — whole section in a dedicated background ──
                Surface(
                    color = MaterialTheme.colorScheme.surface2,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Text(
                            text = "WHAT WILL BE RESTORED",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.06.sp,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Category entries inside the section background
                        summary.categoryResults.forEach { result ->
                            RestoreCategoryRow(result)
                        }
                    }
                }
            }
        },
    )
}

/** A simple label-value row for info display. */
@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
