package app.anikuta.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Phase 7 — Custom 3-stage pull-to-refresh.
 *
 * Tracks the user's pull-down distance via [NestedScrollConnection] and maps
 * it to three stages:
 *  - Stage 1 (100dp): "Release to refresh episodes"
 *  - Stage 2 (200dp): "Release to refresh details"
 *  - Stage 3 (300dp): "Release to refresh everything"
 *
 * On release, fires [onRefresh] with the current stage. The indicator overlay
 * shows BELOW the header area (offset from top) so it doesn't overlap the
 * notification bar or the back/save buttons.
 *
 * This is a NOVEL component — aniyomi uses single-stage Material3 PullRefresh.
 */
@Composable
fun ThreeStagePullRefresh(
    isRefreshing: Boolean,
    onRefresh: (RefreshStage) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val stage1Px = with(density) { 100.dp.toPx() }
    val stage2Px = with(density) { 200.dp.toPx() }
    val stage3Px = with(density) { 300.dp.toPx() }
    val maxPullPx = with(density) { 360.dp.toPx() }

    var pullDistance by remember { mutableFloatStateOf(0f) }

    val currentStage = when {
        pullDistance >= stage3Px -> RefreshStage.Everything
        pullDistance >= stage2Px -> RefreshStage.Details
        pullDistance >= stage1Px -> RefreshStage.Episodes
        else -> RefreshStage.Idle
    }

    // Track whether we've already fired the refresh for this pull gesture
    // to avoid double-firing in onPreFling + onPostFling.
    var refreshFired by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // When the indicator is visible and the user scrolls up (available.y < 0),
                // collapse the indicator first before letting the list scroll.
                if (pullDistance > 0f && available.y < 0f) {
                    val consumed = available.y.coerceAtLeast(-pullDistance)
                    pullDistance += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // When the list is at the top and the user drags down (available.y > 0),
                // accumulate pull distance with damping (0.5x) for a natural feel.
                if (available.y > 0f && pullDistance < maxPullPx) {
                    val damped = available.y * 0.5f
                    pullDistance = (pullDistance + damped).coerceAtMost(maxPullPx)
                    return Offset(0f, damped)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // On release: if past stage 1 and we haven't already fired,
                // fire the refresh action.
                if (pullDistance >= stage1Px && refreshFired == 0f) {
                    refreshFired = 1f  // mark as fired
                    val stage = when {
                        pullDistance >= stage3Px -> RefreshStage.Everything
                        pullDistance >= stage2Px -> RefreshStage.Details
                        else -> RefreshStage.Episodes
                    }
                    onRefresh(stage)
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Reset the pull distance + fired flag after the fling completes.
                pullDistance = 0f
                refreshFired = 0f
                return Velocity.Zero
            }
        }
    }

    Box(modifier = modifier.nestedScroll(nestedScrollConnection)) {
        content()

        // Indicator overlay — shows BELOW the header area (offset from top)
        // so it doesn't overlap the notification bar or back/save buttons.
        AnimatedVisibility(
            visible = pullDistance > 0f || isRefreshing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp),  // below the header (back + save buttons)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (isRefreshing) "Refreshing…" else currentStage.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
