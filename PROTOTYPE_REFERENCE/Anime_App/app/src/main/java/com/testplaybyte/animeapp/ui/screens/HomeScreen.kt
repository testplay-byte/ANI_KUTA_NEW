package com.testplaybyte.animeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.testplaybyte.animeapp.data.AniListClient
import com.testplaybyte.animeapp.data.HistoryRepository
import com.testplaybyte.animeapp.model.Anime
import com.testplaybyte.animeapp.ui.components.AnimeCard
import com.testplaybyte.animeapp.ui.components.CollapsingHeader
import com.testplaybyte.animeapp.ui.components.ContinueWatching
import com.testplaybyte.animeapp.ui.components.HeroCarousel
import java.util.Calendar

/**
 * HomeScreen - landing screen.
 *
 * Layout (matches the web prototype `home-screen.tsx` + `home-screen.module.css`):
 *   - Pinned `CollapsingHeader("Anime")` sitting OUTSIDE the scroll Column so
 *     it always stays visible (shrinks 32sp -> 22sp on scroll via the header's
 *     internal `derivedStateOf` + `animateFloatAsState`).
 *   - verticalScroll Column with 110dp bottom padding for the floating nav.
 *   - Sections: HeroCarousel (trending) -> ContinueWatching (from history) ->
 *     "Popular This Season" 3-col grid -> "Top Rated" 3-col grid.
 *
 * The grids are non-lazy Column-of-Rows (chunked by 3) so they can nest
 * inside a verticalScroll Column (LazyVerticalGrid would crash with nested
 * scrolling). Each row is a Row with 3 AnimeCards using `weight(1f)`.
 *
 * All three AniList queries are launched in parallel on first composition
 * via `coroutineScope { async { runCatching { ... }.getOrDefault(...) } }`.
 */
@Composable
fun HomeScreen(
    onOpenAnime: (Int) -> Unit,
) {
    val context = LocalContext.current
    val client = remember { AniListClient() }
    val historyRepo = remember { HistoryRepository(context) }
    val scrollState = rememberScrollState()

    var trending by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var seasonal by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var topRated by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    val historyItems by historyRepo.items.collectAsStateWithLifecycle(initialValue = emptyList())

    // Compute current season/year from the device clock (only once).
    val (season, year) = remember {
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) // 0..11
        val s = when (m) {
            in 0..2 -> "WINTER"
            in 3..5 -> "SPRING"
            in 6..8 -> "SUMMER"
            in 9..11 -> "FALL"
            else -> "WINTER"
        }
        s to y
    }

    // Parallel fetch - coroutineScope { async { ... } ... } runs concurrently.
    // All wrapped in runCatching so a network failure doesn't crash the screen.
    LaunchedEffect(Unit) {
        loading = true
        coroutineScope {
            val t = async { runCatching { client.fetchTrending() }.getOrDefault(emptyList()) }
            val s = async { runCatching { client.fetchSeasonal(season, year) }.getOrDefault(emptyList()) }
            val r = async { runCatching { client.fetchTopRated() }.getOrDefault(emptyList()) }
            trending = t.await()
            seasonal = s.await()
            topRated = r.await()
        }
        loading = false
    }

    // Derived visibility flags - avoid re-evaluating list.isEmpty() on every recomposition.
    val showHero by remember { derivedStateOf { trending.isNotEmpty() } }
    val showContinue by remember { derivedStateOf { historyItems.any { it.progress > 0 } } }
    val showSeasonal by remember { derivedStateOf { seasonal.isNotEmpty() } }
    val showTopRated by remember { derivedStateOf { topRated.isNotEmpty() } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Pinned collapsing header - sits OUTSIDE the scroll Column so it stays visible.
        CollapsingHeader(title = "Anime", scrollState = scrollState)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 110.dp), // reserved for the floating nav bar
        ) {
            // -- Hero / Trending -------------------------------------------
            if (showHero) {
                HeroCarousel(items = trending, onClick = onOpenAnime)
            } else if (loading) {
                HeroSkeleton()
            }
            Spacer(modifier = Modifier.height(24.dp))

            // -- Continue Watching (only if there's in-progress history) ---
            if (showContinue) {
                SectionHeader("Continue Watching")
                ContinueWatching(items = historyItems, onClick = onOpenAnime)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // -- Popular This Season ---------------------------------------
            if (showSeasonal) {
                SectionHeader("Popular This Season")
                AnimeGrid(items = seasonal, onOpenAnime = onOpenAnime)
                Spacer(modifier = Modifier.height(24.dp))
            } else if (loading) {
                SectionHeader("Popular This Season")
                GridSkeleton(count = 6)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // -- Top Rated -------------------------------------------------
            if (showTopRated) {
                SectionHeader("Top Rated")
                AnimeGrid(items = topRated, onOpenAnime = onOpenAnime)
            } else if (loading) {
                SectionHeader("Top Rated")
                GridSkeleton(count = 9)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    )
}

/**
 * AnimeGrid - non-lazy 3-column grid laid out as a Column of Rows (chunked
 * by 3). Safe to embed inside a verticalScroll Column (LazyVerticalGrid
 * would crash with nested scrolling).
 *
 * Spacing matches the prototype CSS:
 *   - 16dp effective horizontal padding (Column 12dp + AnimeCard internal 4dp).
 *   - 8dp column gap (AnimeCard left 4dp + right 4dp, Row spacedBy 0dp).
 *   - 12dp row gap (Column spacedBy 12dp).
 */
@Composable
private fun AnimeGrid(
    items: List<Anime>,
    onOpenAnime: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                rowItems.forEach { anime ->
                    Box(modifier = Modifier.weight(1f)) {
                        AnimeCard(anime = anime, onClick = onOpenAnime)
                    }
                }
                // Fill remaining slots so columns stay aligned.
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Placeholder box for the hero while trending is loading. */
@Composable
private fun HeroSkeleton() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Placeholder grid of cover-shaped boxes while data is loading. */
@Composable
private fun GridSkeleton(count: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        (0 until count).chunked(3).forEach { rowIndices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                rowIndices.forEach {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                repeat(3 - rowIndices.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
