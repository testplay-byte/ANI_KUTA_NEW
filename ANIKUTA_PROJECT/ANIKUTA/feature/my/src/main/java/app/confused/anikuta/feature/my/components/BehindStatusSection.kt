package app.confused.anikuta.feature.my.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import app.confused.anikuta.core.tracker.BehindAnime
import coil3.compose.AsyncImage

/**
 * Section: Behind Status — dedicated section showing anime the user is behind on.
 *
 * Each row: cover + title + "Watched X / Y" + progress bar.
 * Tap → opens anime detail page.
 *
 * Design: matches More page entry cards (surfaceVariant alpha 0.4f,
 * RoundedCornerShape 12dp).
 */
@Composable
fun BehindStatusSection(
    behindAnime: List<BehindAnime>,
    onOpenAnime: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("Behind Status")

        if (behindAnime.isEmpty()) {
            // "You're all caught up!" message
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "You're all caught up! 🎉",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            behindAnime.take(10).forEach { item ->
                BehindAnimeRow(item, onOpenAnime)
            }
        }
    }
}

@Composable
private fun BehindAnimeRow(
    item: BehindAnime,
    onOpenAnime: (Int) -> Unit,
) {
    val anilistId = item.anime.anilistId ?: return
    val progress = if (item.totalReleasedEpisodes > 0) {
        item.watchedEpisodes.toFloat() / item.totalReleasedEpisodes.toFloat()
    } else {
        0f
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable { onOpenAnime(anilistId) },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover image
            if (item.anime.coverUrl != null) {
                AsyncImage(
                    model = item.anime.coverUrl,
                    contentDescription = item.anime.title,
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
                    text = item.anime.title,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "Watched ${item.watchedEpisodes} / ${item.totalReleasedEpisodes}",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
        }
    }
}
