package app.anikuta.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import app.anikuta.player.PlayerLoadingState
import app.anikuta.player.PlayerViewModel
import app.anikuta.ui.theme.AnikutaSprings

/**
 * ANI-KUTA PlayerControls — minimal M3 Expressive overlay.
 *
 * Selective copy-paste from aniyomi's split controls (D1): aniyomi splits
 * controls into TopLeft/TopRight/BottomLeft/BottomRight/Middle + sheets +
 * panels (≈20 files). We collapse the essentials into one overlay:
 *  - top bar (back + title)
 *  - center (play/pause + seek-by-10s + buffering)
 *  - bottom (slider + time)
 *
 * Source: REFERENCE/app/.../ui/player/controls/PlayerControls.kt (collapsed).
 */
@Composable
fun PlayerControls(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onSeekTo: (Int) -> Unit,
    onTogglePause: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.position.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val buffering by viewModel.buffering.collectAsState()
    val loadingState by viewModel.loadingState.collectAsState()
    val title = viewModel.title

    AnimatedVisibility(
        visible = controlsVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.15f to Color.Transparent,
                        0.85f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.65f),
                    ),
                ),
        ) {
            // ---- Top bar ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            // ---- Center controls ----
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CenterIconButton(
                    icon = Icons.Default.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    onClick = { onSeekRelative(-10) },
                )
                Box(contentAlignment = Alignment.Center) {
                    if (buffering || loadingState == PlayerLoadingState.LOADING) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(64.dp),
                        )
                    } else {
                        val scale by animateFloatAsState(
                            targetValue = if (isPlaying) 1f else 0.94f,
                            animationSpec = AnikutaSprings.press,
                            label = "playButtonScale",
                        )
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier
                                .size(72.dp)
                                .scale(scale),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                IconButton(onClick = onTogglePause) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.Black,
                                        modifier = Modifier.size(40.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                CenterIconButton(
                    icon = Icons.Default.Forward10,
                    contentDescription = "Forward 10 seconds",
                    onClick = { onSeekRelative(10) },
                )
            }

            // ---- Bottom seek bar ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Slider(
                    value = position.toFloat().coerceAtLeast(0f),
                    onValueChange = { onSeekTo(it.toInt()) },
                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(position),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = formatTime(duration),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = AnikutaSprings.press,
        label = "centerIconScale",
    )
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressed = true
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }
    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(56.dp)
            .scale(scale),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(32.dp),
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
