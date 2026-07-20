@file:OptIn(ExperimentalLayoutApi::class)

package com.testplaybyte.animeapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.testplaybyte.animeapp.data.AniListClient
import com.testplaybyte.animeapp.data.HistoryRepository
import com.testplaybyte.animeapp.data.LibraryRepository
import com.testplaybyte.animeapp.model.Anime
import com.testplaybyte.animeapp.theme.WarnDark
import kotlinx.coroutines.launch

/**
 * DetailScreen - full anime detail page.
 *
 * Layout (matches the web prototype `detail-screen.tsx` + `detail-screen.module.css`):
 *   - Banner (200dp, full-width, ContentScale.Crop) at the top with a
 *     vertical gradient (dark at top for the back button, fading to the
 *     background color at the bottom).
 *   - Back button overlay (40dp circle, semi-transparent black, top-left,
 *     status-bar padded so it clears the system status bar).
 *   - Cover poster (120x170dp) overlapping the banner by 70dp via
 *     `Modifier.offset(y = (-70).dp)` (NOT negative padding - negative
 *     padding crashes Compose). The cover + all content below it sit in a
 *     single inner Column that carries the offset, so the title appears
 *     flush against the cover's visual bottom with no gap.
 *   - Title (22sp bold), score ("★ X.X" in warn amber), meta
 *     (format · episodes · season year, 13sp muted).
 *   - Genres as a FlowRow of pill chips (surfaceVariant background, 12sp,
 *     rounded pill, outlineVariant border).
 *   - Expandable synopsis (3 lines + "Read more" / "Read less").
 *   - Episode list (rows with a 32dp number box + "Episode N" + format meta),
 *     capped at 24 entries.
 *   - Sticky "Add to Library" button (full-width, 52dp, primary background,
 *     onPrimary text, pill shape) aligned to BottomCenter of the screen.
 *
 * Both the physical back button (`BackHandler`) and the on-screen back
 * button call `onBack()`. Fetches detail by id, then records to history.
 * All network calls wrapped in `runCatching`.
 */
@Composable
fun DetailScreen(
    animeId: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { AniListClient() }
    val libraryRepo = remember { LibraryRepository(context) }
    val historyRepo = remember { HistoryRepository(context) }
    val scrollState = rememberScrollState()

    val libraryItems by libraryRepo.items.collectAsStateWithLifecycle(initialValue = emptyList())
    val inLibrary = libraryItems.any { it.id == animeId }

    var anime by remember(animeId) { mutableStateOf<Anime?>(null) }
    var loading by remember(animeId) { mutableStateOf(true) }
    var synopsisExpanded by remember(animeId) { mutableStateOf(false) }

    // Fetch detail by id, then record to history. Both wrapped in runCatching.
    LaunchedEffect(animeId) {
        loading = true
        val detail = runCatching { client.fetchDetail(animeId) }.getOrNull()
        anime = detail
        loading = false
        if (detail != null) {
            runCatching { historyRepo.add(detail) }
        }
    }

    // Physical back button -> onBack(). The on-screen back button also calls onBack().
    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            anime == null -> {
                // Error state - network failure or not found.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Could not load anime",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Check your connection and try again.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onBack) {
                        Text("Go back")
                    }
                }
            }
            else -> {
                val a = anime!!
                val cleanedSynopsis = a.description?.replace(Regex("<[^>]*>"), "").orEmpty()
                val epCount = a.episodes ?: 0
                val showCount = minOf(epCount, 24)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(bottom = 110.dp), // reserved for the sticky Add to Library button
                ) {
                    // -- Banner ------------------------------------------------
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    ) {
                        AsyncImage(
                            model = a.bannerImage ?: a.coverUrl,
                            contentDescription = a.displayTitle,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        // Gradient: dark at top (for back-button legibility) fading
                        // to the background color at the bottom so the banner melts
                        // into the page.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.45f),
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                            MaterialTheme.colorScheme.background,
                                        ),
                                    ),
                                ),
                        )
                    }

                    // -- Cover + content (offset up 70dp to overlap banner) ----
                    // Wrapping the cover AND all content below it in a single
                    // offset Column means the title sits flush against the
                    // cover's visual bottom (no 70dp gap). The offset only
                    // shifts the visual position; the layout box stays put,
                    // so there is a 70dp empty region at the bottom of the
                    // scroll content which the 110dp bottom padding covers.
                    Column(
                        modifier = Modifier.offset(y = (-70).dp),
                    ) {
                        // Cover poster (120x170dp, left-aligned).
                        Box(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .size(width = 120.dp, height = 170.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            AsyncImage(
                                model = a.coverUrl,
                                contentDescription = a.displayTitle,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }

                        // Title
                        Text(
                            text = a.displayTitle,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        )

                        // Score + meta row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (a.averageScore != null) {
                                Text(
                                    text = "\u2605 ${a.scoreFormatted}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarnDark,
                                )
                            }
                            val metaParts = buildList {
                                if (a.format != null) add(a.format)
                                if (a.episodes != null) add("${a.episodes} ep")
                                if (a.seasonYear != null) add(a.seasonYear.toString())
                            }
                            if (metaParts.isNotEmpty()) {
                                Text(
                                    text = metaParts.joinToString(" \u00B7 "),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Genres (FlowRow of pill chips)
                        if (a.genres.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                a.genres.forEach { g -> GenreChip(g) }
                            }
                        }

                        // Synopsis (expandable)
                        if (cleanedSynopsis.isNotEmpty()) {
                            Text(
                                text = "Synopsis",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 14.dp,
                                    bottom = 4.dp,
                                ),
                            )
                            Text(
                                text = cleanedSynopsis,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                            )
                            Text(
                                text = if (synopsisExpanded) "Read less" else "Read more",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 16.dp, top = 2.dp)
                                    .clickable { synopsisExpanded = !synopsisExpanded },
                            )
                        }

                        // Episode list
                        if (epCount > 0) {
                            Text(
                                text = "Episodes",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = 6.dp,
                                ),
                            )
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                (1..showCount).forEach { i ->
                                    EpisodeRow(index = i, format = a.format ?: "TV")
                                }
                            }
                            if (epCount > showCount) {
                                Text(
                                    text = "+ ${epCount - showCount} more episodes",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                                )
                            }
                        }
                    }
                }

                // -- Add to Library button (sticky bottom) -----------------
                Button(
                    onClick = {
                        scope.launch {
                            if (inLibrary) libraryRepo.remove(setOf(animeId))
                            else libraryRepo.add(a)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = if (inLibrary) "In Library \u2713" else "Add to Library",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // -- Back button overlay (always visible, even during loading) ------
        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .size(40.dp)
                .clickable { onBack() },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * GenreChip - pill-shaped chip with surfaceVariant background, outlineVariant
 * border, 12sp SemiBold text. Matches the prototype's `.genreChip` CSS.
 */
@Composable
private fun GenreChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * EpisodeRow - row with a 32dp number box (surface background, 8dp radius)
 * and a two-line label ("Episode N" 13sp SemiBold + format 11sp muted).
 * Matches the prototype's `.episodeRow` / `.episodeNum` / `.episodeInfo` CSS.
 */
@Composable
private fun EpisodeRow(index: Int, format: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Episode $index",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = format,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
