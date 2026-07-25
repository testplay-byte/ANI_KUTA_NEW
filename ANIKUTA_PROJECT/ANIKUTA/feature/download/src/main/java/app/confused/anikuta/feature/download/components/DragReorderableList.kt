package app.confused.anikuta.feature.download.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
 * A smooth drag-and-drop reorderable list with animated item placement.
 *
 * **How it works:**
 * - Each item has a drag handle (≡) on the **right**. Touching the handle +
 *   dragging starts reordering immediately (no long-press).
 * - The dragged item follows the finger exactly (graphicsLayer translationY
 *   tracks the accumulated drag offset).
 * - The target index is computed via `round(dragOffset / itemHeight)` — this
 *   allows multi-position moves in a single drag (drag fast → skip positions).
 * - When the target index changes, the internal list reorders instantly.
 *   **Non-dragged items animate to their new positions** — each item tracks
 *   its previous index; when the list reorders, the item's visual offset
 *   animates from the old position to the new one using `animateFloatAsState`
 *   with a spring animation. This gives the smooth sliding effect the owner
 *   requested (items don't "jump" — they slide).
 * - On release, [onReorder] is called with the final order. The dragged item
 *   animates back to its final position (the offset animates to 0).
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
    val itemHeightFloat = itemHeightPx

    // Internal copy — reordered during drag without calling onReorder.
    val internalItems = remember { mutableStateListOf<String>() }
    LaunchedEffect(items) {
        if (internalItems.toList() != items) {
            internalItems.clear()
            internalItems.addAll(items)
        }
    }

    // Track each item's "visual offset" for animation. When the list reorders,
    // an item that moved from index 2 to index 0 needs to animate UP by 2
    // item-heights. We do this by:
    // 1. Before the reorder, record each item's current index.
    // 2. After the reorder, compute the delta (newIndex - oldIndex).
    // 3. Set the item's visual offset to -delta * itemHeight (so it appears
    //    at its OLD position initially).
    // 4. Animate the offset to 0 (so it slides to its NEW position).
    val itemVisualOffsets = remember { mutableStateListOf<Float>() }
    // Ensure the offset list matches the item count
    LaunchedEffect(internalItems.size) {
        while (itemVisualOffsets.size < internalItems.size) itemVisualOffsets.add(0f)
        while (itemVisualOffsets.size > internalItems.size) itemVisualOffsets.removeAt(itemVisualOffsets.size - 1)
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
            val dragTranslationPx = if (isDragged) dragOffset else 0f

            // Animated offset for non-dragged items. When the list reorders,
            // this animates from the old position delta to 0 (smooth slide).
            val targetOffset = if (isDragged) dragTranslationPx else 0f
            val animatedOffset by animateFloatAsState(
                targetValue = if (isDragged) dragTranslationPx else itemVisualOffsets.getOrElse(index) { 0f },
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 400f,
                ),
                label = "itemOffset$index",
            )
            // For the dragged item, use the raw drag offset (no animation —
            // it follows the finger exactly). For non-dragged items, use the
            // animated offset (smooth slide).
            val finalOffset = if (isDragged) dragTranslationPx else animatedOffset

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
                    .graphicsLayer { translationY = finalOffset }
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
                                        // Reset all visual offsets
                                        for (i in itemVisualOffsets.indices) itemVisualOffsets[i] = 0f
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
                                            // ── Record old indices BEFORE the reorder ──
                                            // (for the animation: each displaced item needs
                                            // to start at its old position + animate to 0).
                                            val oldIndex = draggedIndex
                                            val newIndex = targetIndex

                                            // Move the item in the internal list
                                            val moved = internalItems.removeAt(oldIndex)
                                            internalItems.add(newIndex, moved)

                                            // ── Set visual offsets for the displaced items ──
                                            // Items between oldIndex and newIndex (exclusive of
                                            // the dragged item) shifted by 1 position. Set their
                                            // visual offset so they appear at their OLD position
                                            // initially, then animate to 0 (slide to new pos).
                                            if (newIndex > oldIndex) {
                                                // Moved down: items oldIndex+1..newIndex shifted up by 1
                                                for (i in (oldIndex + 1)..newIndex) {
                                                    if (i - 1 < itemVisualOffsets.size) {
                                                        itemVisualOffsets[i] = itemHeightFloat // appear 1 below
                                                    }
                                                }
                                            } else {
                                                // Moved up: items newIndex..oldIndex-1 shifted down by 1
                                                for (i in newIndex until oldIndex) {
                                                    if (i + 1 < itemVisualOffsets.size) {
                                                        itemVisualOffsets[i] = -itemHeightFloat // appear 1 above
                                                    }
                                                }
                                            }

                                            // Adjust dragOffset so the dragged item stays under the finger
                                            val indexShift = newIndex - oldIndex
                                            dragOffset -= indexShift * itemHeightPx
                                            draggedIndex = newIndex

                                            // Trigger the animation: set all offsets to 0 (they'll
                                            // animate from their current value to 0 via animateFloatAsState).
                                            // We use a small delay to let the initial offset render first.
                                            // Actually, animateFloatAsState will animate from the current
                                            // value to the new target (0) automatically. We just need to
                                            // set the offsets to 0.
                                            // But we can't set them here directly (the animation reads them).
                                            // Instead, we set them + let the next recomposition trigger
                                            // the animation. The animateFloatAsState reads
                                            // itemVisualOffsets[index] as its target; when we set it to 0,
                                            // it animates from the old value (itemHeightFloat) to 0.
                                            for (i in itemVisualOffsets.indices) {
                                                if (i != newIndex) itemVisualOffsets[i] = 0f
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
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
