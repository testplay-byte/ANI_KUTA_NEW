package app.confused.anikuta.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.Category
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * A floating Dialog (NOT a bottom-up sheet) for picking categories.
 *
 * Per user decision Q7: the detail-page save button long-press opens a
 * "floating kind of menu, not a bottom-up menu" — this Dialog is that menu.
 *
 * Shows all visible categories with checkboxes. The user can select multiple.
 * An "Add new category" row at the bottom opens [AddCategoryDialog].
 *
 * @param categories All visible categories.
 * @param selectedCategoryIds The IDs currently selected (pre-checked).
 * @param onConfirm Called with the new set of selected category IDs.
 * @param onDismiss Called when the dialog is cancelled.
 * @param onAddNewCategory Called when the user taps "Add new category".
 */
@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    selectedCategoryIds: Set<Long>,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
    onAddNewCategory: () -> Unit,
) {
    val currentSelection = remember { mutableStateOf(selectedCategoryIds.toMutableSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set Categories",
                fontFamily = RobotoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(categories, key = { it.id }) { category ->
                        val isChecked = category.id in currentSelection.value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newSet = currentSelection.value.toMutableSet()
                                    if (category.id in newSet) newSet.remove(category.id)
                                    else newSet.add(category.id)
                                    currentSelection.value = newSet
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp).height(24.dp)
                                    .clickable {
                                        val newSet = currentSelection.value.toMutableSet()
                                        if (category.id in newSet) newSet.remove(category.id)
                                        else newSet.add(category.id)
                                        currentSelection.value = newSet
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isChecked) {
                                    androidx.compose.material3.Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                        modifier = Modifier.width(20.dp).height(20.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(2.dp),
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp).height(20.dp)
                                            .clickable {
                                                val newSet = currentSelection.value.toMutableSet()
                                                if (category.id in newSet) newSet.remove(category.id)
                                                else newSet.add(category.id)
                                                currentSelection.value = newSet
                                            },
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = category.name,
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddNewCategory)
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Add new category",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentSelection.value.toSet()) }) {
                Text(
                    "Done",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
