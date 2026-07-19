package app.anikuta.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anikuta.player.PlayerLoadingState
import app.anikuta.player.PlayerViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Minimized video player controls overlay — clean, minimal UI.
 *
 * Layout (when controls are visible):
 *  - Top-left: current time / total duration
 *  - Top-right: subtitle button + quality button (subtitle to the LEFT of quality)
 *  - Center: transparent play/pause icon (single-click toggles play/pause)
 *  - Bottom: minimal seekbar (left, fills width) + maximize button (right)
 *
 * Gestures:
 *  - Single tap (controls hidden): show controls
 *  - Single tap (controls visible, on center icon): toggle play/pause
 *  - Single tap (controls visible, elsewhere): hide controls
 *  - Double-tap left third: skip -10s (animation on LEFT side)
 *  - Double-tap right third: skip +10s (animation on RIGHT side)
 *  - Double-tap center third: toggle play/pause (animation in CENTER, smaller)
 *
 * Double-tap animations do NOT show the controls — just a brief icon overlay.
 * Skip animations appear on the side that was tapped (left/right).
 * Play/pause animation appears in the center (smaller than skip animations).
 */
@Composable
fun MinimizedControls(
    viewModel: PlayerViewModel,
    onTogglePlay: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    onSeekTo: (Int) -> Unit,
    onMaximize: () -> Unit,
    onQualityClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.position.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val buffering by viewModel.buffering.collectAsState()
    val loadingState by viewModel.loadingState.collectAsState()
    val bufferAheadTime by viewModel.bufferAheadTime.collectAsState()
    val isSwitchingEpisode by viewModel.isSwitchingEpisode.collectAsState()

    // Double-tap animation state
    var doubleTapAnim by remember { mutableStateOf<DoubleTapFeedback?>(null) }
    val animAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // Single tap: toggle controls
                        viewModel.toggleControls()
                    },
                    onDoubleTap = { offset ->
                        // Double tap: determine zone (left/center/right thirds)
                        val w = size.width.toFloat()
                        val zone = when {
                            offset.x < w / 3 -> DoubleTapZone.LEFT
                            offset.x > w * 2f / 3f -> DoubleTapZone.RIGHT
                            else -> DoubleTapZone.CENTER
                        }
                        when (zone) {
                            DoubleTapZone.LEFT -> {
                                onSeekRelative(-10)
                                scope.launch {
                                    doubleTapAnim = DoubleTapFeedback.Rewind
                                    animAlpha.snapTo(0f)
                                    animAlpha.animateTo(1f, tween(150))
                                    animAlpha.animateTo(0f, tween(500))
                                    doubleTapAnim = null
                                }
                            }
                            DoubleTapZone.RIGHT -> {
                                onSeekRelative(10)
                                scope.launch {
                                    doubleTapAnim = DoubleTapFeedback.Forward
                                    animAlpha.snapTo(0f)
                                    animAlpha.animateTo(1f, tween(150))
                                    animAlpha.animateTo(0f, tween(500))
                                    doubleTapAnim = null
                                }
                            }
                            DoubleTapZone.CENTER -> {
                                onTogglePlay()
                                scope.launch {
                                    doubleTapAnim = if (isPlaying) DoubleTapFeedback.Pause else DoubleTapFeedback.Play
                                    animAlpha.snapTo(0f)
                                    animAlpha.animateTo(1f, tween(150))
                                    animAlpha.animateTo(0f, tween(500))
                                    doubleTapAnim = null
                                }
                            }
                        }
                    },
                )
            },
    ) {
        // Gradient overlay for control readability — only when controls are visible
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.25f to Color.Transparent,
                            0.65f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f),
                        ),
                    ),
            )
        }

        // Loading / switching episode indicator
        if (buffering || loadingState == PlayerLoadingState.LOADING || isSwitchingEpisode) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }

        // Double-tap feedback animation overlay
        // Skip animations (Rewind/Forward): just "+10s"/"-10s" text in a dark pill,
        // no icon. Appears on the SIDE that was tapped.
        // Play/Pause animation: icon only in a dark circle, appears in CENTER.
        doubleTapAnim?.let { feedback ->
            val isCenterAnim = feedback == DoubleTapFeedback.Pause || feedback == DoubleTapFeedback.Play
            val alignment = if (isCenterAnim) Alignment.Center else {
                when (feedback) {
                    DoubleTapFeedback.Rewind -> Alignment.CenterStart
                    DoubleTapFeedback.Forward -> Alignment.CenterEnd
                    else -> Alignment.Center
                }
            }
            val sidePadding = if (isCenterAnim) 0.dp else 40.dp

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = alignment,
            ) {
                if (isCenterAnim) {
                    // Center: play/pause icon in a dark circle (no text)
                    val icon = if (feedback == DoubleTapFeedback.Pause) Icons.Default.Pause else Icons.Default.PlayArrow
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = sidePadding),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.45f * animAlpha.value),
                            modifier = Modifier.size(48.dp),
                        ) {}
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = animAlpha.value),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                } else {
                    // Side: just text in a dark pill (no icon)
                    val label = if (feedback == DoubleTapFeedback.Rewind) "-10s" else "+10s"
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.6f * animAlpha.value),
                        modifier = Modifier.padding(horizontal = sidePadding),
                    ) {
                        Text(
                            text = label,
                            color = Color.White.copy(alpha = animAlpha.value),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // Controls (show/hide on single tap)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // ---- Top-left: current time / total duration ----
                Text(
                    text = "${formatTime(position)} / ${formatTime(duration)}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 8.dp),
                )

                // ---- Top-right: subtitle (left) + quality (right) ----
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 8.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransparentIconButton(
                        icon = Icons.Default.Subtitles,
                        contentDescription = "Subtitles",
                        onClick = onSubtitleClick,
                    )
                    TransparentIconButton(
                        icon = Icons.Default.HighQuality,
                        contentDescription = "Quality",
                        onClick = onQualityClick,
                    )
                }

                // ---- Center: transparent play/pause (single-click toggles play/pause) ----
                // When controls are visible, a single tap on this icon toggles play/pause.
                // The pointerInput consumes the tap so the outer Box doesn't toggle controls.
                // Double-tap center (when controls are hidden) is handled by the outer Box.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp) // larger touch target
                        .pointerInput(Unit) {
                            detectTapGestures { onTogglePlay() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(56.dp),
                    )
                }

                // ---- Bottom: seekbar (left, fills width) + maximize (right) ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Minimal seekbar — takes all available space
                    MinimalSeekbar(
                        position = position,
                        duration = duration,
                        bufferAheadTime = bufferAheadTime,
                        onSeekTo = onSeekTo,
                        modifier = Modifier.weight(1f),
                    )
                    // Spacing between seekbar and maximize
                    Box(modifier = Modifier.width(8.dp))
                    // Maximize button
                    TransparentIconButton(
                        icon = Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        onClick = onMaximize,
                    )
                }
            }
        }
    }
}

// ---- Double-tap feedback types ----

private enum class DoubleTapZone { LEFT, CENTER, RIGHT }

private enum class DoubleTapFeedback { Pause, Play, Rewind, Forward }

// ---- Minimal seekbar ----

/**
 * A minimal, custom seekbar with a thin track and small thumb.
 *
 * Features:
 *  - 5dp track (slightly thicker for better visibility)
 *  - 14dp thumb that appears during drag
 *  - Drag-to-seek with live position update
 *  - Floating time indicator above the thumb while dragging
 *  - Touch target is 28dp for comfortable interaction
 *  - Designed to support buffering indicator in the future
 */
@Composable
private fun MinimalSeekbar(
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
    // P2b: Buffer-ahead ratio for the seekbar indicator
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
        // P2b: Buffer-ahead segment — lighter color, between progress and end
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
            val density = androidx.compose.ui.platform.LocalDensity.current
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

// ---- Transparent icon button ----

/**
 * A minimal icon button with no background — just the icon.
 * Slightly larger touch target than the icon itself for accessibility.
 */
@Composable
private fun TransparentIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun formatTime(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
