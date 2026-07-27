package app.confused.anikuta.core.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.player.PlayerLoadingState
import app.confused.anikuta.core.player.PlayerPreferences
import app.confused.anikuta.core.player.PlayerStateHolder

/**
 * Fullscreen player controls overlay (landscape).
 *
 * **Overhauled** per Phase 9 Round 2 spec:
 * - Top-left: lock button + anime title + episode pill + quality pill (horizontal Row)
 * - Top-right: frosted glass row (Server, Subtitles, Audio, Quality, More)
 * - Center: rewind 10s / play-pause / forward 10s (fade only — no slide)
 * - Bottom: custom progress bar (thicker, bigger thumb, tooltip, buffer) +
 *   current time (left, safe-area padded) / exit + total time (right) +
 *   bottom-left controls row (speed, rotate, next, PiP) in frosted container
 *
 * Animations:
 * - Top elements: slide UP when hiding, slide DOWN when showing
 * - Bottom elements: slide DOWN when hiding, slide UP when showing
 * - Center: fade only (no movement)
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun FullscreenControls(
    stateHolder: PlayerStateHolder,
    playerPreferences: PlayerPreferences,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    onSeekTo: (Int) -> Unit,
    onMinimize: () -> Unit,
    onLockToggle: () -> Unit,
    onQualityClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit,
    onServerClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onMoreClick: () -> Unit,
    onSkipForward: () -> Unit,
    onPiPClick: () -> Unit,
    onRotateClick: () -> Unit,
    modifier: Modifier = Modifier,
    animeTitle: String = "",
    episodeInfo: String = "",
    qualityInfo: String = "",
    currentSpeed: Float = 1.0f,
) {
    val controlsVisible by stateHolder.controlsVisible.collectAsState()
    val controlsLocked by stateHolder.controlsLocked.collectAsState()
    val isPlaying by stateHolder.isPlaying.collectAsState()
    val position by stateHolder.position.collectAsState()
    val duration by stateHolder.duration.collectAsState()
    val buffering by stateHolder.buffering.collectAsState()
    val loadingState by stateHolder.loadingState.collectAsState()
    val bufferAheadTime by stateHolder.bufferAheadTime.collectAsState()

    // Track whether the user is actively seeking — controls must NOT auto-hide
    // while the seek bar is being dragged.
    var isSeeking by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (controlsLocked) {
            // ── LOCKED STATE ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.18f to Color.Transparent,
                        ),
                    ),
            )
            FSSmallButton(
                icon = Icons.Default.Lock,
                contentDescription = "Unlock",
                onClick = onLockToggle,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
        } else {
            // ── NORMAL (UNLOCKED) STATE ──
            // Gradient scrims — always visible
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.12f to Color.Transparent,
                            0.85f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.65f),
                        ),
                    ),
            )

            // Tap-to-toggle overlay — disabled while seeking
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isSeeking) {
                        if (!isSeeking) {
                            detectTapGestures(
                                onTap = { stateHolder.toggleControls() },
                                onDoubleTap = { offset ->
                                    if (offset.x < size.width / 2) {
                                        onSeekRelative(-10)
                                    } else {
                                        onSeekRelative(10)
                                    }
                                },
                            )
                        }
                    },
            )

            // ── Top elements: slide UP when hiding, DOWN when showing ──
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // ── Top-left: lock + anime info (horizontal Row) ──
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 12.dp, top = 12.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Lock button
                        FSSmallButton(
                            icon = Icons.Default.Lock,
                            contentDescription = "Lock",
                            onClick = onLockToggle,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        // Anime info column
                        Column {
                            if (animeTitle.isNotEmpty()) {
                                Text(
                                    text = animeTitle,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(0.5f),
                                )
                            }
                            if (episodeInfo.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                FSInfoPill(text = episodeInfo)
                            }
                            if (qualityInfo.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                FSInfoPill(text = qualityInfo)
                            }
                        }
                    }

                    // ── Top-right: frosted glass row ──
                    Surface(
                        color = Color.Black.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 16.dp, top = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            FSSmallButton(icon = Icons.Default.Cloud, contentDescription = "Server", onClick = onServerClick)
                            FSSmallButton(icon = Icons.Default.Subtitles, contentDescription = "Subtitles", onClick = onSubtitleClick)
                            FSSmallButton(icon = Icons.Default.MusicNote, contentDescription = "Audio", onClick = onAudioClick)
                            FSSmallButton(icon = Icons.Default.HighQuality, contentDescription = "Quality", onClick = onQualityClick)
                            FSSmallButton(icon = Icons.Default.MoreVert, contentDescription = "More", onClick = onMoreClick)
                        }
                    }
                }
            }

            // ── Center controls: FADE ONLY (no slide) ──
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FSCenterButton(icon = Icons.Default.Replay10, contentDescription = "Rewind 10s", onClick = { onSeekRelative(-10) })
                        Box(contentAlignment = Alignment.Center) {
                            if (buffering || loadingState == PlayerLoadingState.LOADING) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(56.dp),
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier.size(64.dp),
                                    onClick = onTogglePlay,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp),
                                        )
                                    }
                                }
                            }
                        }
                        FSCenterButton(icon = Icons.Default.Forward10, contentDescription = "Forward 10s", onClick = { onSeekRelative(10) })
                    }
                }
            }

            // ── Bottom elements: slide DOWN when hiding, UP when showing ──
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        // Custom progress bar (thicker, bigger thumb, tooltip, buffer)
                        FullscreenSeekbarCustom(
                            position = position,
                            duration = duration,
                            bufferAheadTime = bufferAheadTime,
                            onSeekTo = onSeekTo,
                            onSeekStart = { isSeeking = true },
                            onSeekEnd = { isSeeking = false },
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Bottom row: left controls | right controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Bottom-left: current time (safe-area padded) + frosted controls group
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // Current time in frosted container
                                FSTimeContainer(text = formatTime(position))

                                // Frosted controls group: Speed, Rotate, Next, PiP
                                Surface(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        FSSpeedButton(speed = currentSpeed, onClick = onSpeedClick)
                                        FSSmallButton(icon = Icons.Default.RotateRight, contentDescription = "Rotate", onClick = onRotateClick)
                                        FSSkipButton(onClick = onSkipForward)
                                        FSSmallButton(icon = Icons.Default.PictureInPicture, contentDescription = "PiP", onClick = onPiPClick)
                                    }
                                }
                            }
                            // Bottom-right: exit + total time
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                FSExitButton(onClick = onMinimize)
                                FSTimeContainer(text = formatTime(duration))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Custom progress bar — thicker track, bigger square thumb, buffer, tooltip
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun FullscreenSeekbarCustom(
    position: Int,
    duration: Int,
    bufferAheadTime: Int = 0,
    onSeekTo: (Int) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
) {
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    val displayPosition = scrubPosition ?: position.toFloat().coerceAtLeast(0f)
    val maxRange = duration.toFloat().coerceAtLeast(1f)
    val progress = (displayPosition / maxRange).coerceIn(0f, 1f)
    val bufferProgress = if (bufferAheadTime > 0) {
        ((bufferAheadTime.toFloat()) / maxRange).coerceIn(0f, 1f)
    } else 0f

    val trackColor = Color.White.copy(alpha = 0.25f)
    val bufferColor = Color.White.copy(alpha = 0.15f)
    val progressColor = MaterialTheme.colorScheme.primary

    // Thicker bar: 6dp (was 4dp), bigger thumb: 18dp (was 14dp)
    val barHeightDp = 6.dp
    val thumbSizeDp = 18.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp) // Touch target height
            .pointerInput(maxRange) {
                detectTapGestures(
                    onTap = { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekTo((fraction * maxRange).toInt())
                    },
                )
            }
            .pointerInput(maxRange) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onSeekStart()
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        scrubPosition = fraction * maxRange
                    },
                    onDragEnd = {
                        scrubPosition?.let { onSeekTo(it.toInt()) }
                        scrubPosition = null
                        onSeekEnd()
                    },
                    onDragCancel = {
                        scrubPosition = null
                        onSeekEnd()
                    },
                    onDrag = { change, _ ->
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        scrubPosition = fraction * maxRange
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxWidth().height(32.dp),
        ) {
            val barHeight = barHeightDp.toPx()
            val barY = (size.height - barHeight) / 2f
            val barWidth = size.width
            val cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())

            // Background track (thicker)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, barY),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius,
            )

            // Buffer indicator (behind progress)
            if (bufferProgress > 0f) {
                drawRoundRect(
                    color = bufferColor,
                    topLeft = Offset(0f, barY),
                    size = Size(barWidth * bufferProgress, barHeight),
                    cornerRadius = cornerRadius,
                )
            }

            // Progress (played portion)
            drawRoundRect(
                color = progressColor,
                topLeft = Offset(0f, barY),
                size = Size(barWidth * progress, barHeight),
                cornerRadius = cornerRadius,
            )

            // Bigger square thumb with rounded corners
            val thumbSize = thumbSizeDp.toPx()
            val thumbX = (barWidth * progress - thumbSize / 2f).coerceAtLeast(0f)
            val thumbY = (size.height - thumbSize) / 2f
            drawRoundRect(
                color = progressColor,
                topLeft = Offset(thumbX, thumbY),
                size = Size(thumbSize, thumbSize),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
        }

        // ── Seek tooltip: shows current scrub time while dragging ──
        if (scrubPosition != null) {
            val tooltipText = formatTime(scrubPosition!!.toInt())
            val tooltipX = (progress * 100).coerceIn(5f, 90f)
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .offset(x = (tooltipX * 3.2).dp, y = (-20).dp)
                    .padding(0.dp),
            ) {
                Text(
                    text = tooltipText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  UI helper composables
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun FSSmallButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.12f),
        modifier = modifier.size(36.dp),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun FSCenterButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.15f),
        modifier = Modifier.size(44.dp),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun FSSkipButton(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.12f),
        modifier = Modifier.size(36.dp),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Next episode",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun FSSpeedButton(
    speed: Float,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.12f),
        modifier = Modifier.size(36.dp),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "${speed}x",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FSTimeContainer(text: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun FSExitButton(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.3f),
        modifier = Modifier.size(32.dp),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.FullscreenExit,
                contentDescription = "Exit fullscreen",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun FSInfoPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Format a duration in seconds as `h:mm:ss` or `m:ss`. Uses the shared formatTime. */
