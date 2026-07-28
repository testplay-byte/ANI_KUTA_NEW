package app.anikuta.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Reusable shimmer skeleton placeholder (Phase 6 task 6.30).
 * Shows a pulsing box that mimics content loading. Used to replace
 * bare CircularProgressIndicator spinners with content-shaped skeletons.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 8,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .alpha(alpha),
    )
}

/** Skeleton card matching the anime card shape (cover + title). */
@Composable
fun SkeletonAnimeCard(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier = modifier.padding(4.dp)) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            cornerRadius = 12,
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp),
            cornerRadius = 4,
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(4.dp))
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(12.dp),
            cornerRadius = 4,
        )
    }
}
