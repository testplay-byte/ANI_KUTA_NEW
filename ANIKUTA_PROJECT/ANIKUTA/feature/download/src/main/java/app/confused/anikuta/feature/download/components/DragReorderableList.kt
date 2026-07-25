package app.confused.anikuta.feature.download.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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

/**
 * A drag-and-drop reorderable list. Each item has a drag handle (≡) on the
 * left; long-press the handle to start dragging. When the dragged item
 * crosses another item's vertical midpoint, they swap. On release, [onReorder]
 * is called with the new order.
 *
 * **Usage:** for short lists (< 20 items) like quality/audio/server
 * preference lists. Uses a plain `Column` (not LazyColumn) because these
 * lists are short and drag-and-drop with LazyColumn requires complex
 * item-key tracking.
 *
 * **Design:** surfaceVariant 0.4f cards, RoundedCornerShape(12dp), the
 * dragged item gets an elevation shadow + slight scale. RobotoFamily.
 * The drag handle is tinted `onSurfaceVariant` (subtle); it becomes `primary`
 * (lime green) while dragging for visual feedback.
 *
 * @param items The list of strings to display + reorder.
 * @param onReorder Called with the new list order when the user finishes
 *   dragging an item.
 */
@Composable
fun DragReorderableList(
    items: List<String>,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val itemHeightDp = 44.dp
    val itemHeightPx = with(density) { itemHeightDp.toPx() }

    // Drag state
    var draggedIndex by remember { mutableIntStateOf(-1) } // which item is being dragged
    var dragOffset by remember { mutableFloatStateOf(0f) } // pixel offset from the dragged item's top

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, item ->
            val isDragged = index == draggedIndex
            // Calculate the Y offset for the dragged item.
            val graphicsLayerOffset = if (isDragged) dragOffset else 0f

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
                    .graphicsLayer { translationY = graphicsLayerOffset }
                    .then(
                        if (isDragged) Modifier.shadow(8.dp, RoundedCornerShape(12.dp))
                        else Modifier
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Drag handle — long-press to start dragging.
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .pointerInput(items) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedIndex = index
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        if (draggedIndex >= 0) {
                                            draggedIndex = -1
                                            dragOffset = 0f
                                        }
                                    },
                                    onDragCancel = {
                                        draggedIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y

                                        // Check if we should swap with the next/previous item.
                                        val currentIndex = draggedIndex
                                        if (currentIndex >= 0) {
                                            // If dragged down past the next item's midpoint → swap down
                                            if (dragOffset > itemHeightPx && currentIndex < items.size - 1) {
                                                val mutable = items.toMutableList()
                                                val moved = mutable.removeAt(currentIndex)
                                                mutable.add(currentIndex + 1, moved)
                                                onReorder(mutable)
                                                draggedIndex = currentIndex + 1
                                                dragOffset -= itemHeightPx
                                            }
                                            // If dragged up past the previous item's midpoint → swap up
                                            else if (dragOffset < -itemHeightPx && currentIndex > 0) {
                                                val mutable = items.toMutableList()
                                                val moved = mutable.removeAt(currentIndex)
                                                mutable.add(currentIndex - 1, moved)
                                                onReorder(mutable)
                                                draggedIndex = currentIndex - 1
                                                dragOffset += itemHeightPx
                                            }
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
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.width(4.dp))

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
                }
            }
        }
    }
}
