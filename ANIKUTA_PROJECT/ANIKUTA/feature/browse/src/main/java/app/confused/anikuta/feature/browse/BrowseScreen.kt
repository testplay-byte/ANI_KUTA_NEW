package app.confused.anikuta.feature.browse

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.model.AniListAnime
import app.confused.anikuta.core.anilist.model.coverUrl
import app.confused.anikuta.core.anilist.model.displayTitle
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Browse screen — shows trending anime from AniList in a grid.
 *
 * Phase 4: fetches trending anime from AniList (ADR-010, ADR-030).
 * Uses the CollapsingHeader from the design system — collapses when the grid scrolls.
 * Grid of AnimeCard composables (cover + title).
 * Loading/error/empty states.
 *
 * The screen content scrolls behind the floating bottom nav (per design language).
 */
@Composable
fun BrowseScreen(
    api: AniListApi,
    onOpenAnime: (Int) -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    var anime by remember { mutableStateOf<List<AniListAnime>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Derive "collapsed" from the grid's scroll state
    val collapsed by remember {
        derivedStateOf {
            gridState.firstVisibleItemScrollOffset > 20 || gridState.firstVisibleItemIndex > 0
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            loading = true
            val result = runCatching { api.fetchTrending(perPage = 30) }
            anime = result.getOrDefault(emptyList())
            error = result.exceptionOrNull()?.message
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = "Browse", collapsed = collapsed)

        when {
            loading && anime.isEmpty() -> LoadingState()
            error != null && anime.isEmpty() -> ErrorState(message = error!!)
            anime.isEmpty() -> EmptyState()
            else -> AnimeGrid(
                anime = anime,
                gridState = gridState,
                onOpenAnime = onOpenAnime,
            )
        }
    }
}

@Composable
private fun AnimeGrid(
    anime: List<AniListAnime>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onOpenAnime: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(anime) { item ->
            AnimeCard(anime = item, onClick = { onOpenAnime(item.id) })
        }
        // Bottom padding for the floating nav
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(110.dp))
        }
    }
}

@Composable
private fun AnimeCard(
    anime: AniListAnime,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        // Cover image (2:3 aspect ratio)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = anime.coverUrl,
                contentDescription = anime.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Score badge
            if (anime.averageScore != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "${anime.averageScore}",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = anime.displayTitle,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
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

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No anime found",
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
