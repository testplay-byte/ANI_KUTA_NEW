package app.confused.anikuta.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.Category
import app.confused.anikuta.core.designsystem.theme.Motion
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * A floating Dialog for picking categories.
 *
 * Per user decision Q7: the detail-page save button long-press opens a
 * "floating kind of menu" — this Dialog is that menu.
 *
 * Redesigned (round 2) for a more compact, polished look:
 *  - Compact rows (less vertical padding).
 *  - Lime-green check icon on selected categories.
 *  - Smaller dialog height (fits more categories without overwhelming).
 *  - "Add new category" row with accent styling.
 *
 * @param categories All visible categories.
 * @param selectedCategoryIds The IDs currently selected (pre-checked). This
 *   is loaded by the caller before showing the dialog — the anime's current
 *   categories are passed here so they show tick marks.
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
    val currentSelection = remember(selectedCategoryIds) { mutableStateOf(selectedCategoryIds.toMutableSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Set Categories",
                fontFamily = RobotoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(categories, key = { it.id }) { category ->
                        val isChecked = category.id in currentSelection.value
                        CategoryRow(
                            name = category.name,
                            isChecked = isChecked,
                            onClick = {
                                val newSet = currentSelection.value.toMutableSet()
                                if (category.id in newSet) newSet.remove(category.id)
                                else newSet.add(category.id)
                                currentSelection.value = newSet
                            },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Add new category row
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddNewCategory),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Add new category",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentSelection.value.toSet()) }) {
                Text(
                    "Done",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
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
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun CategoryRow(
    name: String,
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                      else Color.Transparent,
        animationSpec = tween(Motion.DurationStandard),
        label = "categoryRowBg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Checkbox circle
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (isChecked) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(
                    width = 2.dp,
                    color = if (isChecked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isChecked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = if (isChecked) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (isChecked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
        )
    }
}
