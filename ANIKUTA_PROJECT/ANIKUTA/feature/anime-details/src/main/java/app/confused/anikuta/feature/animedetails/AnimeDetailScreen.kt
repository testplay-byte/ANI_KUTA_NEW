@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.confused.anikuta.feature.animedetails

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.model.AniListAnime
import app.confused.anikuta.core.anilist.model.coverUrl
import app.confused.anikuta.core.anilist.model.displayTitle
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Anime detail screen — shows full anime info with a blurred cover header.
 *
 * Per `DESIGN_LANGUAGE/04-screens/anime-details.md`:
 * - Blurred cover image at the top with a gradient darkening overlay (principle #4).
 * - Cover image overlapping the blurred header.
 * - Title (ExtraBold), score, format, episodes, status, genres.
 * - Description (expandable).
 * - Episode list below.
 * - Back button (top-left).
 *
 * Uses AniList data (ADR-010).
 */
@Composable
fun AnimeDetailScreen(
    animeId: Int,
    api: AniListApi,
    onBack: () -> Unit,
    onOpenEpisode: (Int) -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    var anime by remember { mutableStateOf<AniListAnime?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(animeId) {
        scope.launch {
            loading = true
            val result = runCatching { api.fetchById(animeId) }
            anime = result.getOrNull()
            error = result.exceptionOrNull()?.message
            loading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            loading -> LoadingState()
            error != null -> ErrorState(message = error!!)
            anime != null -> AnimeDetailContent(
                anime = anime!!,
                onBack = onBack,
                onOpenEpisode = onOpenEpisode,
            )
        }
    }
}

@Composable
private fun AnimeDetailContent(
    anime: AniListAnime,
    onBack: () -> Unit,
    onOpenEpisode: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    var descriptionExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
    ) {
        // Blurred cover header + gradient
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
            ) {
                // Blurred cover background
                if (anime.coverUrl != null) {
                    AsyncImage(
                        model = anime.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(20.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
                // Gradient overlay (transparent → background)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.background,
                                ),
                            ),
                        ),
                )
                // Back button
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(12.dp)
                        .size(40.dp)
                        .clickable(onClick = onBack),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        // Cover + title section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Cover image (overlapping the header)
                if (anime.coverUrl != null) {
                    AsyncImage(
                        model = anime.coverUrl,
                        contentDescription = anime.displayTitle,
                        modifier = Modifier
                            .size(width = 100.dp, height = 140.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = anime.displayTitle,
                        fontFamily = RobotoFamily,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Meta row: format, episodes, status
                    val metaParts = buildList {
                        anime.format?.let { add(it) }
                        anime.episodes?.let { add("$it eps") }
                        anime.status?.let { add(it.replace("_", " ").lowercase()) }
                    }
                    if (metaParts.isNotEmpty()) {
                        Text(
                            text = metaParts.joinToString(" · "),
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Score
                    if (anime.averageScore != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "★ ${anime.averageScore}%",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // Genres
        val genres = anime.genres
        if (!genres.isNullOrEmpty()) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    genres.forEach { genre ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = genre,
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        // Description
        if (!anime.description.isNullOrBlank()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Synopsis",
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Strip HTML tags from AniList description
                    val cleanDesc = anime.description!!.replace(Regex("<[^>]*>"), "")
                    Text(
                        text = cleanDesc,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { descriptionExpanded = !descriptionExpanded },
                    )
                }
            }
        }

        // Episodes section
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Episodes",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Episode list (placeholder: generate N episodes based on the anime's episode count)
        val episodeCount = anime.episodes ?: 12
        items(episodeCount) { index ->
            EpisodeRow(
                episodeNumber = index + 1,
                title = "Episode ${index + 1}",
                onClick = { onOpenEpisode(index + 1) },
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episodeNumber: Int,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Episode number circle
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$episodeNumber",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = title,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Play",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Couldn't load anime",
            fontFamily = RobotoFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
