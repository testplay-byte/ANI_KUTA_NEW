package app.confused.anikuta.feature.my.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.player.WatchProgressStore
import coil3.compose.AsyncImage
import java.util.concurrent.TimeUnit

/**
 * Section: Recently Watched — mini list of last 3 watched episodes.
 *
 * Compact: cover + title + episode + time. Shows only the 3 most recent.
 *
 * Design: matches More page entry cards (surfaceVariant alpha 0.4f,
 * RoundedCornerShape 12dp).
 */
@Composable
fun RecentlyWatchedSection(
    recentlyWatched: List<WatchProgressStore.Progress>,
    onOpenAnime: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recentlyWatched.isEmpty()) return

    // Show only the 3 most recent
    val display = recentlyWatched.take(3)

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("Recently Watched")

        display.forEach { progress ->
            RecentlyWatchedRow(progress, onOpenAnime)
        }
    }
}

@Composable
private fun RecentlyWatchedRow(
    progress: WatchProgressStore.Progress,
    onOpenAnime: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover image (if available)
            if (progress.coverUrl != null) {
                AsyncImage(
                    model = progress.coverUrl,
                    contentDescription = progress.animeTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp, 56.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp, 56.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = progress.animeTitle ?: progress.title,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (progress.episodeNumber >= 0) {
                    Text(
                        text = "Episode ${progress.episodeNumber.toInt()}",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Time ago
            Text(
                text = formatTimeAgo(progress.updatedAt),
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Format a timestamp as a relative time string ("2h ago", "3d ago"). */
fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "just now"
    }
}
