package app.confused.anikuta.core.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * A minimal, custom seekbar with a thin track and small thumb.
 *
 * Extracted from the OLD `MinimizedControls.kt` (Phase 2 of the ANIKUTA player
 * port — Task B-controls). Used only by [MinimizedControls].
 *
 * Features:
 *  - 5dp track (slightly thicker for better visibility)
 *  - 14dp thumb that appears during drag
 *  - Drag-to-seek with live position update
 *  - Floating time indicator above the thumb while dragging
 *  - Touch target is 28dp for comfortable interaction
 *  - Buffer-ahead segment shown as a lighter strip between progress and end
 *
 * Design language:
 *  - Active track and thumb use [MaterialTheme.colorScheme.primary]
 *    (the ANI-KUTA accent `#B1F256`).
 */
@Composable
fun MinimalSeekbar(
    position: Int,
    duration: Int,
    bufferAheadTime: Int = 0,
    onSeekTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    var trackWidthPx by remember { mutableStateOf(0f) }
    val displayPosition = scrubPosition ?: position.toFloat().coerceAtLeast(0f)
    val maxRange = duration.toFloat().coerceAtLeast(1f)
    val progress = (displayPosition / maxRange).coerceIn(0f, 1f)
    val isDragging = scrubPosition != null
    // Buffer-ahead ratio for the seekbar indicator
    val bufferProgress = if (duration > 0 && bufferAheadTime > 0) {
        (bufferAheadTime.toFloat() / maxRange).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp) // comfortable touch target
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(maxRange) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (trackWidthPx > 0) {
                            val ratio = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                            scrubPosition = ratio * maxRange
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        if (trackWidthPx > 0) {
                            val ratio = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                            scrubPosition = ratio * maxRange
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        scrubPosition?.let { onSeekTo(it.roundToInt()) }
                        scrubPosition = null
                    },
                    onDragCancel = {
                        scrubPosition = null
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Inactive track (background) — 5dp line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.3f)),
        )
        // Buffer-ahead segment — lighter color, between progress and end
        if (bufferProgress > progress) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferProgress)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.5f)),
            )
        }
        // Active track (progress) — 5dp line in primary color
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        // Thumb + floating time indicator — only visible while dragging
        if (isDragging && trackWidthPx > 0) {
            val thumbOffsetPx = trackWidthPx * progress
            val thumbSize = 14.dp
            val density = LocalDensity.current
            val thumbSizePx = with(density) { thumbSize.toPx() }
            // Thumb circle
            Box(
                modifier = Modifier
                    .offset { IntOffset((thumbOffsetPx - thumbSizePx / 2).roundToInt(), 0) }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            // Floating time indicator above the thumb
            val indicatorOffsetX = with(density) { 30.dp.toPx() }
            val indicatorOffsetY = with(density) { (-32).dp.toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (thumbOffsetPx - indicatorOffsetX).roundToInt().coerceAtLeast(0),
                            indicatorOffsetY.roundToInt(),
                        )
                    }
                    .width(60.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                ) {
                    Text(
                        text = formatTime(displayPosition.toInt()),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** Format a duration in seconds as `h:mm:ss` or `m:ss`. */
internal fun formatTime(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
