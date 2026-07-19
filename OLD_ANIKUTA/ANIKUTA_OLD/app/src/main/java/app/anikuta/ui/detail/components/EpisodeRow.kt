package app.anikuta.ui.detail.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.anikuta.source.api.model.SEpisode
import app.anikuta.ui.detail.DynamicColorScheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Swipe distance (in dp) required to trigger the "toggle watched" action
 * (right swipe). This is intentionally SHORT so the user can quickly
 * mark episodes as watched with a small flick.
 */
private val SWIPE_WATCHED_THRESHOLD = 80.dp

/**
 * Swipe distance (in dp) required to trigger the "download" action
 * (left swipe). This is intentionally LONGER than the watched threshold
 * so that downloads are not triggered accidentally — the user must
 * deliberately swipe far to the left.
 */
private val SWIPE_DOWNLOAD_THRESHOLD = 160.dp

/**
 * Maximum visual overshoot beyond the threshold (as a multiplier).
 * Prevents the row from being dragged unreasonably far.
 */
private const val MAX_OVERSHOOT = 1.3f

/**
 * A single episode row with:
 *
 * 1. **Swipe-to-reveal actions** — swipe right to toggle watched,
 *    swipe left to download. Each action has an INDEPENDENT threshold
 *    (watched = 80dp, download = 160dp) so download is less sensitive.
 *
 *    **Action triggers on RELEASE, not mid-drag.** The action fires only
 *    when the user lifts their finger AND the swipe offset has crossed
 *    the threshold. This prevents accidental triggers if the user swipes
 *    past the threshold and then drags back. A haptic pulse fires at the
 *    moment of crossing (mid-drag) to give feedback that "you've gone far
 *    enough", but the actual action waits for the release.
 *
 * 2. **Configurable watched appearance** — watched episodes can be
 *    grayscale, blurred, both, or none, per the user's Settings preference.
 *
 * 3. **Long-press menu** — long-pressing the row triggers [onLongClick],
 *    which typically shows the [EpisodeOptionsSheet].
 *
 * 4. **Vertical scroll coexistence** — uses [detectHorizontalDragGestures]
 *    which only consumes horizontal drag events. Vertical drags pass
 *    through to the parent `LazyColumn` for scrolling.
 *
 * ## Gesture handling
 *
 * The swipe is implemented with a custom [pointerInput] + [detectHorizontalDragGestures].
 * Click and long-press are handled by [combinedClickable] on the same element.
 * [detectHorizontalDragGestures] only activates for horizontal drags, so taps
 * and long-presses propagate to [combinedClickable] normally.
 *
 * @param episode         The episode data.
 * @param isSeen          Whether this episode is marked as watched.
 * @param onClick         Called on tap (starts playback).
 * @param onLongClick     Called on long-press (shows options sheet).
 * @param onSwipeRight    Called when swiped right past the watched threshold AND released.
 * @param onSwipeLeft     Called when swiped left past the download threshold AND released.
 * @param index           Row index (for alternating background colors).
 * @param dynamicColors   Optional dynamic color scheme for theming.
 * @param appearance      Visual treatment for watched episodes (grayscale/blur/both/none).
 * @param grayscaleAlpha  Alpha multiplier when grayscale is applied (default 0.55).
 * @param blurRadiusDp    Blur radius in dp when blur is applied (default 2dp).
 * @param content         The episode content composable (rich or simple layout).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeRow(
    episode: SEpisode,
    isSeen: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    index: Int = 0,
    dynamicColors: DynamicColorScheme? = null,
    appearance: WatchedEpisodeAppearance = WatchedEpisodeAppearance.GRAYSCALE,
    grayscaleAlpha: Float = 0.55f,
    blurRadiusDp: Float = 2f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // --- Thresholds in pixels ---
    val watchedThresholdPx = with(density) { SWIPE_WATCHED_THRESHOLD.toPx() }
    val downloadThresholdPx = with(density) { SWIPE_DOWNLOAD_THRESHOLD.toPx() }

    // --- Swipe state ---
    val swipeOffset = remember { Animatable(0f) }

    // --- Threshold-crossing feedback (mid-drag haptic) ---
    // Tracks whether we've already fired the "crossing" haptic for the
    // current drag gesture. This lets us buzz once when the user crosses
    // the threshold, but NOT fire the action until release.
    var crossedWatchedThreshold by remember { mutableStateOf(false) }
    var crossedDownloadThreshold by remember { mutableStateOf(false) }

    // --- Card background color (alternating) ---
    val cardColor = if (dynamicColors != null) {
        if (index % 2 == 0) dynamicColors.surfaceLow else dynamicColors.surfaceHigh
    } else {
        if (index % 2 == 0) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        // --- Background (revealed during swipe) ---
        SwipeBackground(
            offset = swipeOffset.value,
            watchedThreshold = watchedThresholdPx,
            downloadThreshold = downloadThresholdPx,
            isSeen = isSeen,
            modifier = Modifier.matchParentSize(),
        )

        // --- Foreground (draggable + clickable content) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .watchedEpisodeEffect(
                    appearance = if (isSeen) appearance else WatchedEpisodeAppearance.NONE,
                    alpha = grayscaleAlpha,
                    blurRadiusDp = blurRadiusDp,
                )
                .offset {
                    IntOffset(swipeOffset.value.roundToInt(), 0)
                }
                .background(cardColor, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(episode.url) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            // Reset crossing flags at the start of each gesture
                            crossedWatchedThreshold = false
                            crossedDownloadThreshold = false
                        },
                        onDragEnd = {
                            // === ISSUE 4 FIX ===
                            // The action fires HERE — on finger release (drag end),
                            // NOT mid-drag. We check the final offset to decide which
                            // action (if any) to trigger.
                            val finalOffset = swipeOffset.value
                            scope.launch {
                                when {
                                    finalOffset > watchedThresholdPx -> {
                                        onSwipeRight()
                                    }
                                    finalOffset < -downloadThresholdPx -> {
                                        onSwipeLeft()
                                    }
                                }
                                // Animate back to rest position
                                swipeOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                swipeOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                            }
                        },
                    ) { _, dragAmount ->
                        scope.launch {
                            val newOffset = (swipeOffset.value + dragAmount)
                                .coerceIn(
                                    -downloadThresholdPx * MAX_OVERSHOOT,
                                    watchedThresholdPx * MAX_OVERSHOOT,
                                )
                            swipeOffset.snapTo(newOffset)
                        }

                        // === Mid-drag haptic feedback (does NOT trigger the action) ===
                        // Fire a single haptic pulse when the user crosses the threshold
                        // for the first time in this gesture. This gives feedback that
                        // "you've swiped far enough" without firing the action prematurely.
                        if (!crossedWatchedThreshold && swipeOffset.value > watchedThresholdPx) {
                            crossedWatchedThreshold = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        if (!crossedDownloadThreshold && swipeOffset.value < -downloadThresholdPx) {
                            crossedDownloadThreshold = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                ),
        ) {
            content()
        }
    }
}

/**
 * The background revealed when the user swipes the episode row.
 *
 * - **Right swipe** (positive offset): Shows a "watched" action with an
 *   eye icon on a primary-colored background.
 * - **Left swipe** (negative offset): Shows a "download" action with a
 *   download icon on a secondary-colored background.
 *
 * The background fills the entire row area and is revealed as the
 * foreground content is dragged away. The icon scales up and becomes
 * fully opaque as the swipe approaches the threshold, providing visual
 * feedback that the action will trigger.
 */
@Composable
private fun SwipeBackground(
    offset: Float,
    watchedThreshold: Float,
    downloadThreshold: Float,
    isSeen: Boolean,
    modifier: Modifier = Modifier,
) {
    if (offset == 0f) return

    val bgColor: Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    val iconTint: Color
    val alignment: Alignment
    val progress: Float  // 0f → 1f as swipe approaches threshold

    when {
        offset > 0f -> {
            // Right swipe → toggle watched
            bgColor = MaterialTheme.colorScheme.primaryContainer
            icon = if (isSeen) Icons.Default.VisibilityOff else Icons.Default.Visibility
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer
            alignment = Alignment.CenterStart
            progress = (offset / watchedThreshold).coerceIn(0f, 1f)
        }

        else -> {
            // Left swipe → download (offset < 0)
            bgColor = MaterialTheme.colorScheme.secondaryContainer
            icon = Icons.Default.CloudDownload
            iconTint = MaterialTheme.colorScheme.onSecondaryContainer
            alignment = Alignment.CenterEnd
            progress = (abs(offset) / downloadThreshold).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor),
        contentAlignment = alignment,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint.copy(alpha = 0.4f + 0.6f * progress),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    val scale = 0.8f + 0.2f * progress
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}
