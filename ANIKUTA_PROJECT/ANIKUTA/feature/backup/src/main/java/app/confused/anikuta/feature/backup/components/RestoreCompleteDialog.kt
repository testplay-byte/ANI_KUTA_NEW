package app.confused.anikuta.feature.backup.components

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
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

/**
 * Restore complete dialog with list-based breakdown.
 *
 * Shown after a restore operation finishes. Shows:
 * - Title with check icon
 * - Highlighted total stats (imported / skipped / errors)
 * - "BREAKDOWN" section in a dedicated background, with per-category
 *   results displayed as a vertical list (not a grid)
 * - OK button
 *
 * Design: #B1F256 primary, RobotoFamily, surface2 for contrast, list layout.
 */
@Composable
fun RestoreCompleteDialog(
    summary: RestoreSummary,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Restore complete",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Highlighted totals ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCard(
                        label = "Imported",
                        value = summary.totalImported.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    if (summary.totalSkipped > 0) {
                        StatCard(
                            label = "Skipped",
                            value = summary.totalSkipped.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (summary.totalErrors > 0) {
                        StatCard(
                            label = "Errors",
                            value = summary.totalErrors.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Breakdown as a list inside a dedicated background ──
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Text(
                            text = "BREAKDOWN",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.06.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Per-category list (not grid)
                        summary.categoryResults.forEach { result ->
                            RestoreCategoryRow(result, showImported = true)
                        }
                    }
                }
            }
        },
    )
}
