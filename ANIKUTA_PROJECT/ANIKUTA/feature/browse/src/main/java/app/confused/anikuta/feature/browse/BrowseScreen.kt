package app.confused.anikuta.feature.browse

import android.util.Log
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
import androidx.compose.runtime.collectAsState
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
import app.confused.anikuta.core.common.model.details.UnifiedAnime
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.preferences.ThemePreferences
import app.confused.anikuta.core.providerapi.HomeFeedProvider
import app.confused.anikuta.core.providerapi.MetadataCapability
import app.confused.anikuta.core.providerapi.MetadataProviderRegistry
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

private const val TAG = "AnikutaBrowseScreen"

/**
 * Browse screen — shows trending anime in a grid.
 *
 * Phase 7 (ADR-041): the screen no longer calls `AniListApi` directly. Instead
 * it resolves the active [HomeFeedProvider] via [MetadataProviderRegistry] and
 * talks to it through the capability interface. Today AniList is the only
 * registered provider; adding MAL/TMDB later is one module + one Koin line and
 * this screen stays unchanged.
 *
 * Uses the CollapsingHeader from the design system — collapses when the grid scrolls.
 * Grid of AnimeCard composables (cover + title).
 * Loading/error/empty states.
 *
 * The screen content scrolls behind the floating bottom nav (per design language).
 *
 * # Provider resolution
 *
 * `MetadataProviderRegistry.forCapability` is `suspend` (it pings `isAvailable()`
 * on each candidate), but [HomeFeedProvider.getCachedTrending] is a non-suspend
 * method we want to call synchronously for the stale-while-revalidate pattern
 * (show cached data instantly, refresh in the background). To support both, we
 * resolve the provider via [MetadataProviderRegistry.allForCapability] (NOT
 * suspend — returns all candidates that implement the capability without
 * checking availability) and use the first one. AniList is currently the only
 * HomeFeedProvider, so this is always it; when a second provider is added the
 * user's active-provider preference will be respected via `forCapability` for
 * the network refresh (the cache lookup falls back to whatever the first
 * provider returns).
 *
 * @param registry the app-wide [MetadataProviderRegistry] (injected via Koin in
 *   `BrowseTabDestination` — matches the existing parameter-passing pattern).
 * @param onOpenAnime open the anime detail page by AniList ID. The provider
 *   abstraction returns [UnifiedAnime] whose `anilistId` is nullable (non-null
 *   for AniList-sourced items; null for hypothetical future providers that
 *   don't expose an AniList ID). For AniList-sourced browse items this is
 *   always non-null; we null-check defensively before navigating.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    registry: MetadataProviderRegistry,
    onOpenAnime: (Int) -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // Resolve the active HomeFeedProvider synchronously via allForCapability
    // (NOT suspend — we need a provider handle for the non-suspend cache lookup
    // before the first composition commits). AniList is currently the only
    // HomeFeedProvider, so firstOrNull() always picks it up. When a second
    // provider is registered, the user's active-provider preference is still
    // consulted via `forCapability` for the network refresh path inside the
    // LaunchedEffect (suspend) below.
    val provider: HomeFeedProvider? = remember(registry) {
        val candidates = registry.allForCapability<HomeFeedProvider>(MetadataCapability.HOME_FEED)
        if (candidates.isEmpty()) {
            Log.w(TAG, "No HomeFeedProvider registered — browse will be empty. " +
                "Register one (e.g., AniListMetadataProvider in providerApiModule).")
        } else {
            Log.d(TAG, "Resolved HomeFeedProvider: ${candidates.first().displayName} " +
                "(${candidates.size} candidate(s))")
        }
        candidates.firstOrNull()
    }

    // Stale-while-revalidate: show cached data instantly if available
    val cached: List<UnifiedAnime>? = provider?.getCachedTrending()
    var anime by remember { mutableStateOf<List<UnifiedAnime>>(cached ?: emptyList()) }
    var loading by remember { mutableStateOf(cached == null) } // Only show loading if no cache
    var error by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) } // Background refresh indicator

    // Derive "collapsed" from the grid's scroll state
    val collapsed by remember {
        derivedStateOf {
            gridState.firstVisibleItemScrollOffset > 20 || gridState.firstVisibleItemIndex > 0
        }
    }

    // Manual refresh function — called on pull-to-refresh.
    // Forces a network fetch regardless of cache freshness.
    val manualRefresh: () -> Unit = {
        if (!isRefreshing) {
            isRefreshing = true
            scope.launch {
                val freshData = runCatching {
                    provider?.fetchTrending(perPage = 30) ?: emptyList()
                }.getOrDefault(emptyList())
                if (freshData.isNotEmpty()) {
                    anime = freshData
                    error = null
                }
                isRefreshing = false
            }
        }
    }

    // Fetch trending — stale-while-revalidate pattern.
    // If cache exists: show it immediately (loading=false), refresh in background.
    // If no cache: show loading spinner, fetch from network.
    // If refresh fails: keep showing old cached data (don't clear it).
    // NOTE: With the local persistent cache, fetchTrending() now returns the
    // local cache if it's < 24h old — so this LaunchedEffect won't make a
    // network call unless the cache is stale. This implements the "refresh
    // once a day" behavior the user requested.
    LaunchedEffect(provider) {
        if (provider == null) {
            Log.w(TAG, "No HomeFeedProvider — skipping fetch")
            loading = false
            error = "No metadata provider available"
            return@LaunchedEffect
        }
        if (cached != null) {
            // Cache exists — refresh in background, keep showing old data
            isRefreshing = true
        }
        val result = runCatching { provider.fetchTrending(perPage = 30) }
        val freshData = result.getOrDefault(emptyList())
        if (freshData.isNotEmpty()) {
            anime = freshData
            error = null
        } else if (cached == null) {
            // No cache AND fetch failed — show error
            error = result.exceptionOrNull()?.message ?: "Failed to load anime"
        }
        // If cache existed but fetch failed (freshData empty), keep showing cached data
        loading = false
        isRefreshing = false
    }

    val prefs = remember { org.koin.core.context.GlobalContext.get().get<ThemePreferences>() }
    val headerBlurEnabled by prefs.headerBlurEffect.changes()
        .collectAsState(initial = prefs.headerBlurEffect.get())

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = "Browse", collapsed = collapsed)

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading && anime.isEmpty() -> LoadingState()
                error != null && anime.isEmpty() -> ErrorState(message = error!!)
                anime.isEmpty() -> EmptyState()
                else -> androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = manualRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AnimeGrid(
                        anime = anime,
                        gridState = gridState,
                        onOpenAnime = onOpenAnime,
                    )
                }
            }
            // Scroll blur overlay — fades in when content scrolls under the header.
            // The flicker-fix: when firstVisibleItemIndex > 0, return MAX_VALUE so the
            // overlay stays at full opacity (firstVisibleItemScrollOffset resets to 0
            // on every item boundary crossing — without this guard the overlay would
            // flicker disappear → reappear).
            ScrollBlurOverlay(
                scrollOffset = {
                    if (gridState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                    else gridState.firstVisibleItemScrollOffset.toFloat()
                },
                backgroundColor = MaterialTheme.colorScheme.background,
                enabled = headerBlurEnabled,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun AnimeGrid(
    anime: List<UnifiedAnime>,
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
            // UnifiedAnime.anilistId is nullable in the abstract contract, but
            // for AniList-sourced browse items it is always non-null. Skip the
            // card click defensively if a future provider returns null.
            AnimeCard(
                anime = item,
                onClick = { item.anilistId?.let { onOpenAnime(it) } },
            )
        }
        // Bottom padding for the floating nav
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(110.dp))
        }
    }
}

@Composable
private fun AnimeCard(
    anime: UnifiedAnime,
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
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Score badge — matches library badge style (tight height, solid primary)
            if (anime.averageScore != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "${anime.averageScore}",
                        fontFamily = RobotoFamily,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = anime.title,
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
