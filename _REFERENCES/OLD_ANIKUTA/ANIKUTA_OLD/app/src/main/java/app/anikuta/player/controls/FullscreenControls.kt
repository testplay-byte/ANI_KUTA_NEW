package app.anikuta.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Cloud
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anikuta.player.PlayerLoadingState
import app.anikuta.player.PlayerViewModel

/**
 * Phase 2.1 — Fullscreen player controls overlay (landscape).
 *
 * Layout zones:
 *  - Top left: lock button + anime name + episode info
 *  - Top right: server, subtitle, audio, quality, more options icons
 *  - Center: rewind 10s / play-pause / forward 10s
 *  - Bottom: seekbar + timestamp (left) + skip/minimize/PiP (right)
 *
 * Controls auto-hide (handled by parent). Tap to show/hide.
 */
@Composable
fun FullscreenControls(
    viewModel: PlayerViewModel,
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
) {
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.position.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val buffering by viewModel.buffering.collectAsState()
    val loadingState by viewModel.loadingState.collectAsState()
    val bufferAheadTime by viewModel.bufferAheadTime.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        // Gradient scrims for readability
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

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // ---- Top bar ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    // Top left: lock + title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp),
                    ) {
                        FSSmallButton(
                            icon = Icons.Default.Lock,
                            contentDescription = "Lock",
                            onClick = onLockToggle,
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Column {
                            Text(
                                text = viewModel.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(0.5f),
                            )
                        }
                    }
                    // Top right: settings icons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(8.dp),
                    ) {
                        FSSmallButton(icon = Icons.Default.Cloud, contentDescription = "Server", onClick = onServerClick)
                        FSSmallButton(icon = Icons.Default.Subtitles, contentDescription = "Subtitles", onClick = onSubtitleClick)
                        FSSmallButton(icon = Icons.Default.MusicNote, contentDescription = "Audio", onClick = onAudioClick)
                        FSSmallButton(icon = Icons.Default.HighQuality, contentDescription = "Quality", onClick = onQualityClick)
                        FSSmallButton(icon = Icons.Default.MoreVert, contentDescription = "More", onClick = onMoreClick)
                    }
                }

                // ---- Center controls ----
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FSCenterButton(icon = Icons.Default.Replay10, contentDescription = "Rewind 10s", onClick = { onSeekRelative(-10) })
                    Box(contentAlignment = Alignment.Center) {
                        if (buffering || loadingState == PlayerLoadingState.LOADING) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(56.dp),
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                modifier = Modifier.size(64.dp),
                                onClick = onTogglePlay,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.Black,
                                        modifier = Modifier.size(36.dp),
                                    )
                                }
                            }
                        }
                    }
                    FSCenterButton(icon = Icons.Default.Forward10, contentDescription = "Forward 10s", onClick = { onSeekRelative(10) })
                }

                // ---- Bottom bar ----
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .systemBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    // Seekbar
                    FullscreenSeekbar(
                        position = position,
                        duration = duration,
                        bufferAheadTime = bufferAheadTime,
                        onSeekTo = onSeekTo,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Bottom left: timestamp + speed + rotation
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "${formatTime(position)} / ${formatTime(duration)}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            FSSmallButton(icon = Icons.Default.Speed, contentDescription = "Speed", onClick = onSpeedClick)
                            FSSmallButton(icon = Icons.Default.RotateRight, contentDescription = "Rotate", onClick = onRotateClick)
                        }
                        // Bottom right: skip + minimize + PiP
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FSSkipButton(onClick = onSkipForward)
                            FSSmallButton(icon = Icons.Default.FullscreenExit, contentDescription = "Minimize", onClick = onMinimize)
                            FSSmallButton(icon = Icons.Default.PictureInPicture, contentDescription = "PiP", onClick = onPiPClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FSSmallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.12f),
        modifier = Modifier.size(36.dp),
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.2f),
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
        modifier = Modifier.size(40.dp),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Skip 85s",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun FullscreenSeekbar(
    position: Int,
    duration: Int,
    bufferAheadTime: Int = 0,
    onSeekTo: (Int) -> Unit,
) {
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    val displayPosition = scrubPosition ?: position.toFloat().coerceAtLeast(0f)
    val maxRange = duration.toFloat().coerceAtLeast(1f)

    // Single slider — the M3 Slider's inactiveTrackColor shows the buffer
    // ahead. No separate visual track above (that caused double seekbar).
    androidx.compose.material3.Slider(
        value = displayPosition.coerceIn(0f, maxRange),
        onValueChange = { newValue ->
            scrubPosition = newValue
        },
        onValueChangeFinished = {
            scrubPosition?.let { onSeekTo(it.toInt()) }
            scrubPosition = null
        },
        valueRange = 0f..maxRange,
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
        ),
    )
}

private fun formatTime(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
