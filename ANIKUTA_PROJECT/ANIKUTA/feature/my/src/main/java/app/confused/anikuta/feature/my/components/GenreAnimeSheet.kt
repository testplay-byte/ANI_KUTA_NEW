package app.confused.anikuta.feature.my.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage

/**
 * Bottom sheet showing anime in a specific genre.
 *
 * Shows a random selection of anime from the user's library that have the
 * selected genre. Each anime is shown with its cover and a one-line title.
 * The selection is randomized each time the sheet is opened.
 *
 * Per design language: dragHandle = null (principle #2), partial height
 * (principle #3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreAnimeSheet(
    genre: String,
    anime: List<Anime>,
    onDismiss: () -> Unit,
    onOpenAnime: (Int) -> Unit,
) {
    // Randomize the selection each time the sheet is shown.
    val randomAnime = remember(anime) { anime.shuffled() }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxHeight = screenHeight * 0.7f // principle #3: max 70% of viewport

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(top = 16.dp, bottom = 32.dp),
        ) {
            // Header
            Text(
                text = genre,
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
            Text(
                text = "${anime.size} anime in your library",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
            )

            if (randomAnime.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No anime found in this genre.",
                        fontFamily = RobotoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Horizontal scroll of anime covers
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(randomAnime.take(20)) { animeItem ->
                        GenreAnimeCard(
                            anime = animeItem,
                            onClick = {
                                animeItem.anilistId?.let { onOpenAnime(it) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreAnimeCard(
    anime: Anime,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(width = 100.dp, height = 180.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        // Cover image
        if (anime.coverUrl != null) {
            AsyncImage(
                model = anime.coverUrl,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 100.dp, height = 140.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(width = 100.dp, height = 140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        // Title (one line)
        Text(
            text = anime.title,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
    }
}
