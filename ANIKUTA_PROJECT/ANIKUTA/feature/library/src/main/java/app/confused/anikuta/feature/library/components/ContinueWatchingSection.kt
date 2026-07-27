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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.library.ContinueWatchingItem
import coil3.compose.AsyncImage

/**
 * "Continue Watching" rail — wrapped in a dedicated background surface so it
 * visually stands out from the rest of the library grid.
 *
 * Per user feedback (round 3): covers should be LANDSCAPE (16:9), not portrait.
 * The title + episode info is overlaid on the cover with a bottom gradient.
 * A thin progress bar sits at the very bottom of the cover.
 *
 * If the cover URL is null/blank, a surfaceVariant placeholder with the anime
 * title is shown instead of a blank box.
 */
@Composable
fun ContinueWatchingSection(
    items: List<ContinueWatchingItem>,
    onClick: (ContinueWatchingItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
        ) {
            Text(
                text = "Continue Watching",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RobotoFamily,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.anilistId }) { item ->
                    ContinueWatchingCard(item = item, onClick = { onClick(item) })
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
) {
    // Landscape 16:9 ratio per user request.
    Box(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
    ) {
        Column {
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
                } else {
                    // Placeholder: show the anime title centered on surfaceVariant.
                    Text(
                        text = item.animeTitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = RobotoFamily,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 8.dp),
                    )
                }

                // Bottom gradient overlay for text readability.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.5f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.85f),
                            ),
                        ),
                )

                // Title + episode overlaid on the cover (bottom).
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = item.animeTitle,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = RobotoFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.episodeNumber > 0) {
                        Text(
                            text = "EP ${item.episodeNumber}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = RobotoFamily,
                            maxLines = 1,
                        )
                    }
                }

                // Progress bar at the very bottom of the cover.
                LinearProgressIndicator(
                    progress = { item.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                )
            }
        }
    }
}
