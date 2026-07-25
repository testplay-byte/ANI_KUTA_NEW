package app.confused.anikuta.feature.backup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 * Grid-based restore complete dialog.
 *
 * Shown after a restore operation finishes. Shows:
 * - Title with check icon
 * - Highlighted total stats (imported / skipped / errors)
 * - 2-column grid of per-category results (imported count)
 * - OK button
 *
 * Design: #B1F256 primary, RobotoFamily, grid layout.
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

                // ── Per-category grid ──
                Text(
                    text = "BREAKDOWN",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.06.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (summary.categoryResults.size > 4) 200.dp else 140.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(summary.categoryResults) { result ->
                        CategoryCountCard(
                            name = result.category.displayName,
                            count = result.importedCount,
                        )
                    }
                }
            }
        },
    )
}
