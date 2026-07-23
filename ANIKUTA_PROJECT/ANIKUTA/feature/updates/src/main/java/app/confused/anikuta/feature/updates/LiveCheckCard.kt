package app.confused.anikuta.feature.updates

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage

/**
 * The "Currently checking" live card — shown at the top of the Updates tab
 * while an update check is in flight (and briefly when it completes).
 *
 * Renders a prominent card with:
 *  - The anime currently being searched (poster + title) — [AnimatedContent]
 *    crossfades smoothly between anime so the user sees the progression.
 *  - A pulsing "Searching…" label + a search icon.
 *  - A deterministic progress bar (`currentIndex / totalCount`).
 *  - A "Found N so far" counter.
 *
 * When the check completes ([CheckProgressUi.Completed]), the card morphs to a
 * success state ("Found N new episodes" with a check icon) for ~1.5s before
 * the VM resets progress to Idle (which hides this card via [AnimatedVisibility]).
 *
 * Per the user's request: "while it is refreshing … it should properly show a
 * live preview of which anime it is currently searching for. It should show
 * that anime's poster image and that anime's name … with proper animations and
 * smooth flow."
 */
@Composable
fun LiveCheckCard(
    progress: CheckProgressUi,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = progress !is CheckProgressUi.Idle,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(250)),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            when (progress) {
                is CheckProgressUi.Checking -> CheckingContent(progress)
                is CheckProgressUi.Completed -> CompletedContent(progress)
                CheckProgressUi.Idle -> { /* covered by AnimatedVisibility */ }
            }
        }
    }
}

@Composable
private fun CheckingContent(progress: CheckProgressUi.Checking) {
    Column(modifier = Modifier.padding(14.dp)) {
        // Header row: pulsing search icon + "Searching your library…"
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "pulseAlpha",
            )
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(18.dp)
                    .alpha(pulse),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Searching your library…",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${progress.currentIndex} / ${progress.totalCount}",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(10.dp))

        // Current anime — AnimatedContent crossfades the poster + title when
        // the checker moves to the next anime.
        AnimatedContent(
            targetState = progress.currentAnime.id,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
            },
            label = "currentAnimeCrossfade",
        ) { _ ->
            CurrentAnimeRow(anime = progress.currentAnime)
        }

        Spacer(Modifier.height(10.dp))

        // Deterministic progress bar (currentIndex / totalCount).
        val fraction = if (progress.totalCount > 0) {
            progress.currentIndex.toFloat() / progress.totalCount
        } else 0f
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (progress.foundSoFar > 0) {
                "Found ${progress.foundSoFar} new so far"
            } else {
                "No new episodes yet"
            },
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CurrentAnimeRow(anime: Anime) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Poster thumbnail (shimmer-pulse placeholder + crossfade-in image).
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 62.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val cover = anime.coverUrl
            if (!cover.isNullOrEmpty()) {
                AsyncImage(
                    model = cover,
                    contentDescription = anime.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = anime.title,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Checking sources for new episodes…",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompletedContent(progress: CheckProgressUi.Completed) {
    Row(
        modifier = Modifier.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = if (progress.foundCount > 0) {
                "Found ${progress.foundCount} new update${if (progress.foundCount == 1) "" else "s"}"
            } else {
                "Up to date — no new episodes"
            },
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A subtle "new" dot rendered at the start of a row for freshly-found updates. */
@Composable
fun NewBadgeDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.primary),
    )
}
