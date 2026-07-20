@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.confused.anikuta.feature.animedetails

import android.os.Build
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.model.AniListAnime
import app.confused.anikuta.core.anilist.model.coverColorHex
import app.confused.anikuta.core.anilist.model.coverUrl
import app.confused.anikuta.core.anilist.model.displayTitle
import app.confused.anikuta.core.anilist.model.nextAiringDisplay
import app.confused.anikuta.core.anilist.model.seasonDisplay
import app.confused.anikuta.core.anilist.model.startDateDisplay
import app.confused.anikuta.core.anilist.model.studioName
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Anime detail screen — redesigned per OLD_ANIKUTA's DetailScreen.kt + owner feedback.
 *
 * Improvements over the previous version:
 * - Back gesture handled (BackHandler) — goes back, doesn't exit the app.
 * - No share button (removed per owner feedback).
 * - Blurred background is TINTED with the cover color (from AniList's coverImage.color).
 * - Title is on the bottom half of the thumbnail (2 lines max, overlapping the gradient).
 * - Cover color captured for dynamic theming.
 * - Genres = single scrollable row (LazyRow, not FlowRow).
 * - Synopsis: "Show more" AND "Show less" toggle.
 * - More AniList fields: season, studio, start date, next airing, etc.
 * - In-memory caching (5-min TTL) in AniListApi.
 */
@Composable
fun AnimeDetailScreen(
    animeId: Int,
    api: AniListApi,
    onBack: () -> Unit,
    onOpenEpisode: (Int) -> Unit = {},
) {
    // Handle the system back gesture
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()

    var anime by remember { mutableStateOf<AniListAnime?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(animeId) {
        scope.launch {
            loading = true
            val result = runCatching { api.fetchById(animeId) }
            anime = result.getOrNull()
            error = result.exceptionOrNull()?.message
            loading = false
        }
    }

    // Parse the cover color (hex string like "#FF5722" → Compose Color)
    val coverColor = remember(anime) {
        anime?.coverColorHex?.let { hex ->
            runCatching {
                val rgb = if (hex.startsWith("#")) hex.substring(1) else hex
                Color(android.graphics.Color.parseColor("#$rgb"))
            }.getOrNull()
        } ?: Color(0xFF1A1A2E) // fallback dark color
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            loading -> LoadingState()
            error != null -> ErrorState(message = error!!)
            anime != null -> DetailContent(
                anime = anime!!,
                coverColor = coverColor,
                saved = saved,
                onBack = onBack,
                onToggleSave = { saved = !saved },
                onOpenEpisode = onOpenEpisode,
            )
        }
    }
}

@Composable
private fun DetailContent(
    anime: AniListAnime,
    coverColor: Color,
    saved: Boolean,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onOpenEpisode: (Int) -> Unit,
) {
    var descriptionExpanded by remember { mutableStateOf(false) }
    var watchedEpisodes by remember { mutableStateOf(setOf<Int>()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // ── Banner ──
        item {
            DetailBanner(
                anime = anime,
                coverColor = coverColor,
                saved = saved,
                onBack = onBack,
                onToggleSave = onToggleSave,
            )
        }

        // ── Genre chips (single scrollable row) ──
        val genres = anime.genres
        if (!genres.isNullOrEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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

        // ── Synopsis ──
        if (!anime.description.isNullOrBlank()) {
            item {
                val cleanDesc = anime.description!!.replace(Regex("<[^>]*>"), "")
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Synopsis",
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = cleanDesc,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Show more / Show less toggle
                    if (cleanDesc.length > 200) {
                        Text(
                            text = if (descriptionExpanded) "Show less" else "Show more",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { descriptionExpanded = !descriptionExpanded },
                        )
                    }
                }
            }
        }

        // ── Episodes section ──
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Episodes",
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                // Show episode count from AniList metadata (informational only)
                val epCount = anime.episodes
                if (epCount != null) {
                    Text(
                        text = "$epCount episodes",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Episode list — NO DEMO EPISODES.
        // Episodes come from extensions (when loaded). Until then, show a proper
        // empty state explaining that extensions are needed.
        item {
            NoExtensionsEpisodesState()
        }

        // ── Information section ──
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Information",
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow("Format", anime.format ?: "Unknown")
                InfoRow("Status", anime.status?.replace("_", " ")?.lowercase() ?: "Unknown")
                anime.seasonDisplay?.let { InfoRow("Season", it) }
                InfoRow("Episodes", (anime.episodes ?: 0).toString())
                if (anime.averageScore != null) {
                    InfoRow("Score", "${anime.averageScore} / 100")
                }
                anime.studioName?.let { InfoRow("Studio", it) }
                anime.startDateDisplay?.let { InfoRow("Aired", it) }
                anime.source?.let { InfoRow("Source", it) }
            }
        }
    }
}

/**
 * The banner: 360dp tall, blurred cover (8dp) + cover-color tint (20%) + gradient overlay,
 * action buttons, and cover thumbnail + title at the bottom.
 *
 * Per OLD_ANIKUTA + owner feedback:
 * - Blurred background is TINTED with the cover color (from AniList's coverImage.color).
 * - Title is on the bottom half of the thumbnail (2 lines max).
 * - No share button (removed per owner feedback).
 */
@Composable
private fun DetailBanner(
    anime: AniListAnime,
    coverColor: Color,
    saved: Boolean,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // Blurred cover background (360dp tall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
        ) {
            // 1) Blurred cover image (8dp blur)
            if (anime.coverUrl != null) {
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(8.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }

            // 2) Cover-color tint (20% alpha) — the dynamic theming effect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(coverColor.copy(alpha = 0.2f)),
            )

            // 3) Gradient overlay: black 20% at top → transparent → background at bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.2f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
        }

        // Action buttons row (top, over the banner)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back button
            ActionButton(
                icon = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Row {
                // Save button (no share button — removed per owner feedback)
                ActionButton(
                    icon = if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (saved) "Remove from library" else "Add to library",
                    onClick = onToggleSave,
                )
                // More button
                ActionButton(
                    icon = Icons.Filled.MoreHoriz,
                    contentDescription = "More",
                    onClick = {},
                )
            }
        }

        // Cover thumbnail + title (bottom-aligned, overlapping the gradient)
        // Title is on the bottom half of the thumbnail (2 lines max)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 100×150dp cover thumbnail
            if (anime.coverUrl != null) {
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = anime.displayTitle,
                    modifier = Modifier
                        .size(width = 100.dp, height = 150.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                // Title (2 lines max)
                Text(
                    text = anime.displayTitle,
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Meta: score · status · episodes
                val metaParts = buildList {
                    anime.averageScore?.let { add("★ $it%") }
                    anime.status?.let { add(it.replace("_", " ").lowercase()) }
                    anime.episodes?.let { add("$it eps") }
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
                // Next airing episode pill
                anime.nextAiringDisplay?.let { display ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = display,
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = CircleShape,
        modifier = Modifier
            .padding(4.dp)
            .size(40.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Episode row — alternating background + watched effect (grayscale + alpha).
 */
@Composable
private fun EpisodeRow(
    episodeNumber: Int,
    isWatched: Boolean,
    onClick: () -> Unit,
    onToggleWatched: () -> Unit,
) {
    val isEven = episodeNumber % 2 == 0
    val cardColor = if (isEven) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .watchedEpisodeEffect(isWatched)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            text = "Episode $episodeNumber",
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Play",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onClick),
        )
    }
}

private fun Modifier.watchedEpisodeEffect(isWatched: Boolean): Modifier {
    if (!isWatched) return this
    return this.graphicsLayer {
        alpha = 0.55f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            renderEffect = RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(matrix),
            ).asComposeRenderEffect()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Empty state for the episodes section when no extensions are loaded.
 *
 * Per owner feedback: do NOT show demo/fake episodes. Show a proper empty state
 * explaining that extensions are needed to load episode lists.
 */
@Composable
private fun NoExtensionsEpisodesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No episodes loaded",
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Install an anime extension from Settings → Extensions to load episode lists from streaming sources.",
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
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
