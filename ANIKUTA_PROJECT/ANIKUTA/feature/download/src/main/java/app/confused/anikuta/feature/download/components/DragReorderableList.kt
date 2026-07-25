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
 * A performant drag-and-drop reorderable list.
 *
 * **Performance design (fixes the scroll-jank issue):**
 * - NO per-item `animateFloatAsState` (that was causing the jank — each item
 *   had its own animation, and during scroll they all recomposed).
 * - Uses `pointerInput(Unit)` — stable key, gesture never cancelled.
 * - Internal `mutableStateListOf` — reorders during drag without calling
 *   `onReorder` (no parent recomposition → no jank).
 * - The dragged item follows the finger via `graphicsLayer.translationY`
 *   (cheap — no recomposition, just a draw-phase transform).
 * - Non-dragged items snap to their new positions (no animation). This is
 *   intentional — the animation was the source of the performance problem.
 *   The snap is fast enough that it looks natural during an active drag
 *   (the user's finger is covering the movement).
 *
 * **How it works:**
 * - Touch the ≡ handle on the right + drag.
 * - The dragged item follows the finger exactly.
 * - When the dragged item crosses another item's midpoint, they swap.
 * - On release, [onReorder] is called with the final order.
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
            val translationPx = if (isDragged) dragOffset else 0f

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
                    // graphicsLayer is draw-phase only — no recomposition, performant.
                    .graphicsLayer { translationY = translationPx }
                    .then(
                        if (isDragged) Modifier.shadow(8.dp, RoundedCornerShape(12.dp))
                        else Modifier
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}.",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        text = item,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    // Drag handle — on the RIGHT. 48dp × 48dp touch target.
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
                                        if (internalItems.toList() != items) {
                                            onReorder(internalItems.toList())
                                        }
                                        draggedIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        internalItems.clear()
                                        internalItems.addAll(items)
                                        draggedIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y

                                        val shift = (dragOffset / itemHeightPx).roundToInt()
                                        val targetIndex = (draggedIndex + shift)
                                            .coerceIn(0, internalItems.size - 1)

                                        if (targetIndex != draggedIndex && draggedIndex >= 0) {
                                            val moved = internalItems.removeAt(draggedIndex)
                                            internalItems.add(targetIndex, moved)
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
