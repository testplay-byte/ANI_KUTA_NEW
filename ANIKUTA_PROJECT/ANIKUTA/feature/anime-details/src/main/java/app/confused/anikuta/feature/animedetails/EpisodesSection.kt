package app.confused.anikuta.feature.animedetails

import android.os.Build
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode

/**
 * The episodes section — shows the source indicator, source switcher, manual
 * search button, and the episode list. Renders the appropriate state
 * (searching / loading / loaded / no-match / error) based on [episodeState].
 *
 * Per the design language spec (`DESIGN_LANGUAGE/04-screens/episode-list.md`):
 * - Episode rows have alternating backgrounds (zebra stripe).
 * - Watched episodes are grayscale + alpha 0.55f (RenderEffect, API 31+).
 * - The section header shows the source name (tappable → switcher).
 *
 * Header layout (next to "Episodes"):
 * - **Matched, multiple sources:** source-name chip (tappable → switcher) + search icon.
 * - **Matched, single source:** source name text + search icon.
 * - **No match / error:** "No source" text + prominent "Search manually" button.
 * - **Searching / loading:** search icon only (source not yet known).
 *
 * The search icon opens [ManualSearchSheet], where the user can search
 * extensions with a custom query and manually link a result.
 */
@Composable
fun EpisodesSection(
    episodeState: EpisodeState,
    currentMatch: SourceMatcher.SourceMatch?,
    allMatches: List<SourceMatcher.SourceMatch>,
    watchedEpisodes: Set<String>,
    isSearching: Boolean,
    manualSearchResults: List<SourceMatcher.ManualSearchResult>,
    initialSearchQuery: String,
    onOpenEpisode: (SEpisode, AnimeSource) -> Unit,
    onToggleWatched: (String) -> Unit,
    onSwitchSource: (SourceMatcher.SourceMatch) -> Unit,
    onManualSearch: suspend (String) -> Unit,
    onLinkManual: (AnimeCatalogueSource, SAnime) -> Unit,
    onClearManualSearch: () -> Unit,
) {
    var showSourceSwitcher by remember { mutableStateOf(false) }
    var showManualSearch by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Section header: "Episodes" + source indicator + search button ──
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

            // Right side: source indicator + search button
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    // Matched + multiple sources → tappable chip
                    currentMatch != null && allMatches.size > 1 -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clickable { showSourceSwitcher = true },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = currentMatch.source.name,
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Icon(
                                    imageVector = Icons.Filled.ExpandMore,
                                    contentDescription = "Switch source",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    // Matched, single source → plain source name
                    currentMatch != null -> {
                        Text(
                            text = currentMatch.source.name,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // No match → "Search manually" CTA button
                    episodeState is EpisodeState.NoMatch -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clickable { showManualSearch = true },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(
                                    text = "Search manually",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                    else -> { /* searching / loading — source not yet known */ }
                }

                // Search icon (always available when a source is matched, so the
                // user can re-search / switch even after a successful match).
                // Hidden only in the NoMatch state (the CTA button covers it).
                if (currentMatch != null || episodeState !is EpisodeState.NoMatch) {
                    IconButton(
                        onClick = { showManualSearch = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search sources manually",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // ── Episode list / state ──
        when (episodeState) {
            is EpisodeState.Idle -> {}
            is EpisodeState.Searching -> SearchingState()
            is EpisodeState.Loading -> EpisodesLoadingState(episodeState.sourceName)
            is EpisodeState.Loaded -> EpisodeList(
                episodes = episodeState.episodes,
                watchedEpisodes = watchedEpisodes,
                onOpenEpisode = onOpenEpisode,
                currentSource = currentMatch?.source,
                onToggleWatched = onToggleWatched,
            )
            is EpisodeState.NoMatch -> NoSourcesState(onSearchManually = { showManualSearch = true })
            is EpisodeState.Error -> EpisodesErrorState(episodeState.message)
        }
    }

    // ── Source switcher dialog ──
    if (showSourceSwitcher && allMatches.isNotEmpty()) {
        SourceSwitcherDialog(
            matches = allMatches,
            currentSourceId = currentMatch?.source?.id,
            onSelect = { match ->
                onSwitchSource(match)
                showSourceSwitcher = false
            },
            onDismiss = { showSourceSwitcher = false },
        )
    }

    // ── Manual search sheet ──
    if (showManualSearch) {
        ManualSearchSheet(
            initialQuery = initialSearchQuery,
            isSearching = isSearching,
            results = manualSearchResults,
            onManualSearch = onManualSearch,
            onLinkManual = { result ->
                onLinkManual(result.source, result.sAnime)
            },
            onDismiss = {
                showManualSearch = false
                onClearManualSearch()
            },
        )
    }
}

/**
 * The scrollable episode list with alternating backgrounds + watched effect.
 */
@Composable
private fun EpisodeList(
    episodes: List<SEpisode>,
    watchedEpisodes: Set<String>,
    onOpenEpisode: (SEpisode, AnimeSource) -> Unit,
    currentSource: AnimeSource?,
    onToggleWatched: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
    ) {
        itemsIndexed(episodes, key = { _, ep -> ep.url }) { index, episode ->
            EpisodeRow(
                episode = episode,
                index = index,
                isWatched = watchedEpisodes.contains(episode.url),
                onClick = {
                    currentSource?.let { onOpenEpisode(episode, it) }
                },
                onToggleWatched = { onToggleWatched(episode.url) },
            )
        }
    }
}

/**
 * A single episode row — number badge, name, play icon, watched grayscale.
 * Alternating backgrounds (zebra stripe) per design language §6.
 */
@Composable
private fun EpisodeRow(
    episode: SEpisode,
    index: Int,
    isWatched: Boolean,
    onClick: () -> Unit,
    onToggleWatched: () -> Unit,
) {
    val isEven = index % 2 == 0
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
        // Episode number badge
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = formatEpisodeNumber(episode.episode_number),
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        // Episode name
        Text(
            text = episode.name.ifBlank { "Episode ${formatEpisodeNumber(episode.episode_number)}" },
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Play icon
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Play episode",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onClick),
        )
    }
}

/** Formats an episode number: 5.0f → "5", 5.5f → "5.5", -1f → "?". */
private fun formatEpisodeNumber(num: Float): String {
    if (num <= 0f) return "?"
    return if (num == num.toLong().toFloat()) num.toLong().toString() else num.toString()
}

/**
 * Watched episode visual effect: grayscale (RenderEffect, API 31+) + alpha 0.55f.
 * Per design language §3.3: MUST use RenderEffect (not ColorFilter on Paint)
 * so Compose's text rendering pipeline is also desaturated.
 */
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
