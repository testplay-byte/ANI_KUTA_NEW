package app.anikuta.player.controls

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import app.anikuta.player.PlayerViewModel
import kotlin.math.abs

/**
 * Phase 4 — Gesture handler for fullscreen player.
 *
 * Handles ALL gestures in fullscreen mode:
 *  - Horizontal swipe: seek forward/backward
 *  - Left half vertical swipe: brightness
 *  - Right half vertical swipe: volume
 *  - Double-tap left half: seek -10s
 *  - Double-tap right half: seek +10s
 *  - Pinch zoom: with magnetic resistance at 20% intervals
 *  - Single tap: show/hide controls
 *
 * Gestures are disabled when controls are locked.
 */
@Composable
fun PlayerGestureHandler(
    viewModel: PlayerViewModel,
    onSeekRelative: (Int) -> Unit,
    onSeekTo: (Int) -> Unit,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val controlsLocked by viewModel.controlsLocked.collectAsState()
    val position by viewModel.position.collectAsState()
    val duration by viewModel.duration.collectAsState()

    // Gesture tracking state
    var seekStartPos by remember { mutableFloatStateOf(0f) }
    var seekStartX by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var lastZoomSticky by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Tap gestures: single tap = toggle controls, double tap = seek
            .pointerInput(controlsLocked) {
                if (controlsLocked) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        onToggleControls()
                    },
                    onDoubleTap = { offset ->
                        if (controlsLocked) return@detectTapGestures
                        // Full left half = -10s, full right half = +10s
                        if (offset.x < size.width / 2) {
                            onSeekRelative(-10)
                        } else {
                            onSeekRelative(10)
                        }
                    },
                )
            }
            // Drag gestures: horizontal = seek, vertical = brightness/volume
            .pointerInput(controlsLocked) {
                if (controlsLocked) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        seekStartX = offset.x
                        seekStartPos = position.toFloat()
                        isSeeking = true
                    },
                    onDragEnd = {
                        isSeeking = false
                    },
                    onDragCancel = {
                        isSeeking = false
                    },
                ) { change, dragAmount ->
                    val dragX = abs(change.position.x - seekStartX)
                    val dragY = abs(dragAmount.y)

                    // Determine if this is a horizontal or vertical drag
                    if (dragX > dragY) {
                        // Horizontal seek — map drag distance to seconds
                        // Full screen width = ~120 seconds of seek
                        val seekRange = 120f
                        val ratio = (change.position.x - seekStartX) / size.width
                        val targetPos = (seekStartPos + ratio * seekRange)
                            .coerceIn(0f, duration.toFloat())
                        onSeekTo(targetPos.toInt())
                    } else {
                        // Vertical: left half = brightness, right half = volume
                        val isLeftHalf = change.position.x < size.width / 2
                        if (isLeftHalf) {
                            // Brightness: drag up = brighter
                            val brightnessDelta = -dragAmount.y * 0.001f
                            activity?.window?.let { window ->
                                val attrs = window.attributes
                                val current = attrs.screenBrightness
                                val newBrightness = (current + brightnessDelta).coerceIn(-1f, 1f)
                                attrs.screenBrightness = newBrightness
                                window.attributes = attrs
                            }
                        } else {
                            // Volume: drag up = louder
                            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE)
                                as? android.media.AudioManager
                            audioManager?.let { am ->
                                val currentVol = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                val maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                val volDelta = (-dragAmount.y * 0.02f).toInt()
                                val newVol = (currentVol + volDelta).coerceIn(0, maxVol)
                                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
                            }
                        }
                    }
                }
            }
            // Pinch zoom with magnetic resistance
            .pointerInput(controlsLocked) {
                if (controlsLocked) return@pointerInput
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (zoomScale * zoom).coerceIn(0.4f, 3.0f)

                    // Magnetic resistance at 20% intervals (0.4, 0.6, 0.8, 1.0, 1.2, ... 3.0)
                    val nearestSticky = (newScale * 5).toInt() / 5f
                    val distanceToSticky = abs(newScale - nearestSticky)

                    if (distanceToSticky < 0.03f) {
                        // Near a sticky point — snap to it
                        zoomScale = nearestSticky
                        lastZoomSticky = nearestSticky
                    } else {
                        // Check if we're breaking away from a sticky point
                        if (abs(newScale - lastZoomSticky) < 0.08f) {
                            // Still in the resistance zone — stay at sticky
                            zoomScale = lastZoomSticky
                        } else {
                            // Broken past resistance — free movement
                            zoomScale = newScale
                        }
                    }

                    // Apply zoom to MPV via video-zoom property
                    try {
                        `is`.xyz.mpv.MPVLib.setPropertyDouble("video-zoom", (zoomScale - 1.0).toDouble())
                    } catch (e: Exception) {
                        // MPV may not be ready
                    }
                }
            },
    )
}
