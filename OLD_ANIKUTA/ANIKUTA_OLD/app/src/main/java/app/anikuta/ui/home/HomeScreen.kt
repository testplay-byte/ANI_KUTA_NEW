package app.anikuta.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.ui.theme.AnikutaSprings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAnimeClick: (Int) -> Unit = {},
) {
    val viewModel: HomeViewModel = viewModel()
    val trending by viewModel.trending.collectAsState()
    val popular by viewModel.popular.collectAsState()
    val fresh by viewModel.fresh.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(top = 0.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Floating top bar — expressive surface with containment
            item(key = "topbar") {
                FloatingTopBar()
            }

            // Hero — first trending anime with deliberate surface
            item(key = "hero") {
                HeroSection(trending)
            }

            item(key = "trending", contentType = "anime_section") {
                HomeSection("Trending Now") { AnimeSection(trending, viewModel, onAnimeClick) }
            }
            item(key = "fresh", contentType = "anime_section") {
                HomeSection("Freshly Updated") { AnimeSection(fresh, viewModel, onAnimeClick) }
            }
            item(key = "genres", contentType = "genre_section") {
                HomeSection("Browse by Genre") { GenreSection(genres) }
            }
            item(key = "popular", contentType = "anime_section") {
                HomeSection("Most Popular") { AnimeSection(popular, viewModel, onAnimeClick) }
            }
            item(key = "schedule", contentType = "text_section") {
                HomeSection("Coming Up Next") {
                    Text(
                        "Schedule coming in a later phase",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingTopBar() {
    // M3 Expressive: deliberate surface containment with high-contrast container
    // statusBarsPadding() here — the ONLY place that handles the status bar gap
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "ANI-KUTA",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            // Expressive search icon — contained in a tonal circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { /* TODO: search */ },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroSection(trending: HomeSectionState) {
    val heroAnime = (trending as? HomeSectionState.Success)?.anime?.firstOrNull()

    // M3 Expressive: deliberate surface — highest elevation container with shadow
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(200.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (heroAnime != null) {
                AsyncImage(
                    model = heroAnime.coverImage.best(),
                    contentDescription = heroAnime.title.preferred(),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.1f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.85f),
                                ),
                            ),
                        ),
                )
                // "Trending #1" badge — expressive contained chip
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        "★ Trending #1",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp),
                ) {
                    Text(
                        heroAnime.title.preferred(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (heroAnime.averageScore != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "★ ${heroAnime.averageScore}  ·  ${heroAnime.episodes ?: "?"} episodes",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "ANI-KUTA",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "Discover. Watch. Enjoy.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSection(title: String, content: @Composable () -> Unit) {
    // M3 Expressive: section header with a tonal accent bar
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            // Tonal accent bar — expressive visual marker
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        content()
    }
}

@Composable
private fun AnimeSection(state: HomeSectionState, viewModel: HomeViewModel, onAnimeClick: (Int) -> Unit) {
    when (state) {
        is HomeSectionState.Loading -> SkeletonRow()
        is HomeSectionState.Success -> {
            if (state.anime.isEmpty()) {
                Text(
                    "No data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = state.anime,
                        key = { it.id },
                        contentType = { "anime_card" },
                    ) { anime ->
                        ExpressiveAnimeCard(anime, onAnimeClick)
                    }
                }
            }
        }
        is HomeSectionState.Error -> {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Couldn't load: ${state.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { viewModel.refresh() }) { Text("Retry") }
            }
        }
        else -> {}
    }
}

/**
 * M3 Expressive anime card — spring-based press feedback + shape morphing.
 *
 * On press: card scales to 0.96 + corner radius morphs from 16dp to 20dp.
 * Uses spatial spring for scale, effects spring for shape.
 */
@Composable
private fun ExpressiveAnimeCard(anime: AniListAnime, onAnimeClick: (Int) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring-based scale animation on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = AnikutaSprings.press,
        label = "card_scale",
    )

    // Spring-based corner radius morph on press (16dp → 20dp)
    val cornerRadius by animateFloatAsState(
        targetValue = if (isPressed) 20f else 16f,
        animationSpec = AnikutaSprings.effects,
        label = "card_corner",
    )

    Card(
        modifier = Modifier
            .width(140.dp)
            .height(300.dp)
            .scale(scale),
        shape = RoundedCornerShape(cornerRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp,
        ),
        interactionSource = interactionSource,
        onClick = { onAnimeClick(anime.id) },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = anime.coverImage.best(),
                contentDescription = anime.title.preferred(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = cornerRadius.dp, topEnd = cornerRadius.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = anime.title.preferred(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (anime.averageScore != null) {
                        Text(
                            "★ ${anime.averageScore}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (anime.episodes != null) {
                        Text(
                            "· ${anime.episodes} eps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                anime.genres?.take(2)?.let { genres ->
                    Text(
                        genres.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreSection(state: HomeSectionState) {
    when (state) {
        is HomeSectionState.Loading -> {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(List(5) { it }, key = { it }, contentType = { "skeleton_chip" }) {
                    AssistChip(onClick = {}, label = { Text("Loading...") })
                }
            }
        }
        is HomeSectionState.GenresSuccess -> {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = state.genres.take(5),
                    key = { it },
                    contentType = { "genre_chip" },
                ) { genre ->
                    // Expressive chip — contained in secondaryContainer
                    AssistChip(
                        onClick = { /* TODO: browse by genre */ },
                        label = { Text(genre) },
                        shape = RoundedCornerShape(8.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }
        }
        is HomeSectionState.Error -> {
            Text(
                "Couldn't load genres",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        else -> {}
    }
}

@Composable
private fun SkeletonRow() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(List(5) { it }, key = { it }, contentType = { "skeleton_card" }) {
            Card(
                modifier = Modifier.width(140.dp).height(300.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    ) {}
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Surface(modifier = Modifier.fillMaxWidth(0.8f).height(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {}
                        Surface(modifier = Modifier.fillMaxWidth(0.5f).height(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {}
                        Surface(modifier = Modifier.fillMaxWidth(0.6f).height(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {}
                    }
                }
            }
        }
    }
}
