package app.confused.anikuta.core.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.player.PlayerLoadingState
import app.confused.anikuta.core.player.PlayerPreferences
import app.confused.anikuta.core.player.PlayerStateHolder
import kotlinx.coroutines.launch

/**
 * Minimized video player controls overlay — clean, minimal UI.
 *
 * Ported from OLD `MinimizedControls.kt` (Task B-controls). Adapted to use
 * [PlayerStateHolder] + [PlayerPreferences] instead of the OLD `PlayerViewModel`
 * + `Injekt.get<PlayerPreferences>()`.
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
 *
 * @param stateHolder Source of player state (replaces OLD `PlayerViewModel`).
 * @param playerPreferences Player preferences (replaces OLD `Injekt.get<PlayerPreferences>()`).
 * @param onTogglePlay Called when the user wants to toggle play/pause.
 * @param onSeekRelative Called with a delta in seconds (e.g. -10 or +10).
 * @param onSeekTo Called with an absolute position in seconds (from the seekbar).
 * @param onMaximize Called when the fullscreen button is tapped.
 * @param onQualityClick Called when the quality icon is tapped.
 * @param onSubtitleClick Called when the subtitle icon is tapped.
 */
@Suppress("UNUSED_PARAMETER") // playerPreferences reserved for future subtitle/quality gesture toggles
@Composable
fun MinimizedControls(
    stateHolder: PlayerStateHolder,
    playerPreferences: PlayerPreferences,
    onTogglePlay: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    onSeekTo: (Int) -> Unit,
    onMaximize: () -> Unit,
    onQualityClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controlsVisible by stateHolder.controlsVisible.collectAsState()
    val isPlaying by stateHolder.isPlaying.collectAsState()
    val position by stateHolder.position.collectAsState()
    val duration by stateHolder.duration.collectAsState()
    val buffering by stateHolder.buffering.collectAsState()
    val loadingState by stateHolder.loadingState.collectAsState()
    val bufferAheadTime by stateHolder.bufferAheadTime.collectAsState()
    val isSwitchingEpisode by stateHolder.isSwitchingEpisode.collectAsState()

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
                        stateHolder.toggleControls()
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
                        CircularProgressIndicator(
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

// ---- Transparent icon button ----

/**
 * A minimal icon button with no background — just the icon.
 * Slightly larger touch target than the icon itself for accessibility.
 */
@Composable
private fun TransparentIconButton(
    icon: ImageVector,
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
