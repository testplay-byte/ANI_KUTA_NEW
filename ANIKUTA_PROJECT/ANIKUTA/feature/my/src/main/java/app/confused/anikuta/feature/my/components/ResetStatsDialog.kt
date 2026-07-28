package app.confused.anikuta.feature.my.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/** Data categories that can be reset. */
enum class ResetCategory(val label: String) {
    WATCH_TIME("Watch Time"),
    COUNTERS("Counters"),
    DISTRIBUTION("Distribution Cache"),
    WATCH_HISTORY("Watch History"),
    ALL_STATS("All Stats"),
}

/**
 * Multi-select reset dialog with a confirmation step.
 *
 * Per the design spec: user selects what to reset, has a "Select All" option,
 * and sees a confirmation dialog: "This will permanently delete [selected data].
 * This cannot be undone. Continue?"
 */
@Composable
fun ResetStatsDialog(
    onDismiss: () -> Unit,
    onConfirm: (Set<ResetCategory>) -> Unit,
) {
    val selected = remember { mutableStateMapOf<ResetCategory, Boolean>() }
    var showConfirmation by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Reset Stats",
                fontFamily = RobotoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
            )
        },
        text = {
            Column {
                Text(
                    text = "Select what to reset:",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                ResetCategory.entries.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected[category] == true,
                            onCheckedChange = { checked ->
                                if (checked) selected[category] = true
                                else selected.remove(category)
                            },
                        )
                        Text(
                            text = category.label,
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Select All toggle
                val allSelected = ResetCategory.entries.all { selected[it] == true }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            if (checked) {
                                ResetCategory.entries.forEach { selected[it] = true }
                            } else {
                                selected.clear()
                            }
                        },
                    )
                    Text(
                        text = "Select All",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            val selectedSet = selected.filterValues { it }.keys
            Button(
                onClick = {
                    if (selectedSet.contains(ResetCategory.ALL_STATS) || selectedSet.size == ResetCategory.entries.size - 1) {
                        // Show confirmation for destructive action
                        showConfirmation = true
                    } else if (selectedSet.isNotEmpty()) {
                        showConfirmation = true
                    }
                },
                enabled = selectedSet.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Reset", fontFamily = RobotoFamily)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = RobotoFamily)
            }
        },
    )

    if (showConfirmation) {
        ConfirmationDialog(
            selectedCategories = selected.filterValues { it }.keys,
            onConfirm = {
                onConfirm(selected.filterValues { it }.keys)
                onDismiss()
            },
            onDismiss = { showConfirmation = false },
        )
    }
}

@Composable
private fun ConfirmationDialog(
    selectedCategories: Set<ResetCategory>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val categoriesText = if (selectedCategories.contains(ResetCategory.ALL_STATS)) {
        "all stats"
    } else {
        selectedCategories.joinToString(", ") { it.label.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = {
            Text(
                text = "Are you sure?",
                fontFamily = RobotoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
            )
        },
        text = {
            Text(
                text = "This will permanently delete $categoriesText. This cannot be undone. Continue?",
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Continue", fontFamily = RobotoFamily)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = RobotoFamily)
            }
        },
    )
}
