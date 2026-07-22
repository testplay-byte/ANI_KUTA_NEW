package app.confused.anikuta.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.component.SectionHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.library.ContinueWatchingItem
import coil3.compose.AsyncImage

/**
 * "Continue Watching" rail — a horizontal scroll of recently-watched anime
 * with a thin progress bar across the bottom of each cover.
 *
 * One card per anime: the cover is 16:9 (so the progress bar reads naturally),
 * with the title + episode number beneath. Tapping the card delegates to
 * [onClick].
 */
@Composable
fun ContinueWatchingSection(
    items: List<ContinueWatchingItem>,
    onClick: (ContinueWatchingItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeader(text = "Continue Watching")

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.anilistId }) { item ->
                ContinueWatchingCard(item = item, onClick = { onClick(item) })
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (!item.coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.animeTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            // Progress bar overlay across the bottom of the cover.
            LinearProgressIndicator(
                progress = { item.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.animeTitle,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = RobotoFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = "EP ${item.episodeNumber}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = RobotoFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
