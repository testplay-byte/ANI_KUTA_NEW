package app.confused.anikuta.feature.download.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlin.math.roundToInt

/**
 * A smooth drag-and-drop reorderable list.
 *
 * **How it works:**
 * - Each item has a drag handle (≡) on the **right**. Touching the handle +
 *   dragging starts reordering immediately (no long-press).
 * - The dragged item follows the finger exactly (graphicsLayer translationY
 *   tracks the accumulated drag offset).
 * - The target index is computed via `round(dragOffset / itemHeight)` — this
 *   allows multi-position moves in a single drag (drag fast → skip positions).
 * - When the target index changes, the internal list reorders instantly +
 *   the drag offset is adjusted so the item stays visually under the finger
 *   (no jump). Other items animate to their new positions.
 * - On release, [onReorder] is called with the final order.
 *
 * **Why an internal copy?** Calling [onReorder] during drag causes the parent
 * to persist the new order → recomposition → `pointerInput` key change →
 * gesture cancellation (laggy, choppy). By keeping an internal
 * `mutableStateListOf` + only syncing to the parent on drag END, the gesture
 * is never cancelled, and the list reorders smoothly in real-time.
 *
 * **Scroll coexistence:** only the handle area (48dp) captures drag gestures;
 * the rest of the row passes through to the parent scroll.
 *
 * @param items The list of strings to display + reorder.
 * @param onReorder Called with the new list order when the user FINISHES
 *   dragging (on drag end). Not called during the drag.
 */
@Composable
fun DragReorderableList(
    items: List<String>,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val itemHeightDp = 48.dp
    val itemHeightPx = with(density) { itemHeightDp.toPx() }

    // Internal copy — reordered during drag without calling onReorder.
    // Synced from [items] when the external list changes (e.g. the parent
    // adds a new item). Only [onReorder] is called on drag END.
    val internalItems = remember { mutableStateListOf<String>() }
    LaunchedEffect(items) {
        if (internalItems.toList() != items) {
            internalItems.clear()
            internalItems.addAll(items)
        }
    }

    // Drag state
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        internalItems.forEachIndexed { index, item ->
            val isDragged = index == draggedIndex
            val translationAmount = if (isDragged) dragOffset else 0f

            Surface(
                color = if (isDragged) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeightDp)
                    .graphicsLayer { this.translationY = translationAmount }
                    .then(
                        if (isDragged) Modifier.shadow(8.dp, RoundedCornerShape(12.dp))
                        else Modifier
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Priority number
                    Text(
                        text = "${index + 1}.",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp),
                    )

                    // Item label
                    Text(
                        text = item,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )

                    // Drag handle — on the RIGHT. 48dp × 48dp touch target.
                    // Uses pointerInput(Unit) — stable key so the gesture is
                    // never cancelled by recomposition during drag.
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(itemHeightDp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggedIndex = index
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        // Persist the final order to the parent.
                                        if (internalItems.toList() != items) {
                                            onReorder(internalItems.toList())
                                        }
                                        draggedIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        // Revert to the original order.
                                        internalItems.clear()
                                        internalItems.addAll(items)
                                        draggedIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y

                                        // Compute the target index based on how far
                                        // the item has been dragged. round() allows
                                        // multi-position moves (drag fast → skip).
                                        val shift = (dragOffset / itemHeightPx).roundToInt()
                                        val targetIndex = (draggedIndex + shift)
                                            .coerceIn(0, internalItems.size - 1)

                                        if (targetIndex != draggedIndex && draggedIndex >= 0) {
                                            // Reorder the internal list: move the dragged
                                            // item from its current position to targetIndex.
                                            val moved = internalItems.removeAt(draggedIndex)
                                            internalItems.add(targetIndex, moved)

                                            // Adjust dragOffset so the item stays visually
                                            // under the finger after the reorder. If the item
                                            // moved down by N positions, the Column rendered it
                                            // N*itemHeight lower, so we subtract N*itemHeight
                                            // from the offset to keep it visually in place.
                                            val indexShift = targetIndex - draggedIndex
                                            dragOffset -= indexShift * itemHeightPx
                                            draggedIndex = targetIndex
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = if (isDragged) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
