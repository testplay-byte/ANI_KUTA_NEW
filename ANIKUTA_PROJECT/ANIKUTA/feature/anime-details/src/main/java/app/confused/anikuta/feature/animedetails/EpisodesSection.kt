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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
    episodeMetadata: Map<Int, app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata>,
    isSearching: Boolean,
    manualSearchResults: List<SourceMatcher.ManualSearchResult>,
    manualSearchErrors: List<Pair<String, String>>,
    autoMatchErrors: List<Pair<String, String>>?,
    hasSearched: Boolean,
    availableSources: List<SourceMatcher.SourceInfo>,
    initialSearchQuery: String,
    onOpenEpisode: (SEpisode, AnimeSource, List<SEpisode>) -> Unit,
    onToggleWatched: (String) -> Unit,
    onSwitchSource: (SourceMatcher.SourceMatch) -> Unit,
    onManualSearch: suspend (Long, String) -> Unit,
    onLinkManual: (AnimeCatalogueSource, SAnime) -> Unit,
    onClearManualSearch: () -> Unit,
) {
    var showManualSearch by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Section header: "Episodes" + metadata loading indicator + source name ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Episodes",
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                // Metadata loading indicator — shows a small spinner next to
                // "Episodes" while metadata is being fetched in the background.
                // When episodes are loaded but metadata map is still empty,
                // the fetch is in progress.
                if (episodeState is EpisodeState.Loaded && episodeMetadata.isEmpty()) {
                    Spacer(modifier = Modifier.size(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Right side: the source name (or "Search manually" CTA when no match).
            // Either way, clicking it opens the ManualSearchSheet.
            when {
                // Source matched → show source name as a tappable chip.
                // Clicking it opens the manual search sheet (which lets the user
                // switch sources or re-link).
                currentMatch != null -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { showManualSearch = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
                                contentDescription = "Search or switch source",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
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
        }

        // ── Episode list / state ──
        when (episodeState) {
            is EpisodeState.Idle -> {}
            is EpisodeState.Searching -> SearchingState()
            is EpisodeState.Loading -> EpisodesLoadingState(episodeState.sourceName)
            is EpisodeState.Loaded -> EpisodeList(
                episodes = episodeState.episodes,
                watchedEpisodes = watchedEpisodes,
                episodeMetadata = episodeMetadata,
                onOpenEpisode = onOpenEpisode,
                currentSource = currentMatch?.source,
                onToggleWatched = onToggleWatched,
            )
            is EpisodeState.NoMatch -> NoSourcesState(
                onSearchManually = { showManualSearch = true },
                autoMatchErrors = autoMatchErrors,
            )
            is EpisodeState.Error -> EpisodesErrorState(episodeState.message)
        }
    }

    // ── Manual search sheet ──
    // Opens when the user taps the source name chip or the "Search manually" CTA.
    // The sheet has the source selector + search bar + results list + link flow.
    if (showManualSearch) {
        ManualSearchSheet(
            initialQuery = initialSearchQuery,
            availableSources = availableSources,
            isSearching = isSearching,
            results = manualSearchResults,
            errors = manualSearchErrors,
            hasSearched = hasSearched,
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
 * The episode list with alternating backgrounds + watched effect.
 *
 * **CRITICAL — Compose layout:**
 * This is a plain [Column] (NOT a [LazyColumn]) because it's rendered inside
 * the outer `DetailContent`'s `LazyColumn` (via `item { EpisodesSection(...) }`).
 * Nesting a `LazyColumn` inside another `LazyColumn` gives the inner one
 * infinite height constraints, which Compose disallows:
 * ```
 * IllegalStateException: Vertically scrollable component was measured with
 * an infinity maximum height constraints, which is disallowed.
 * ```
 *
 * Anime episode lists are typically 4–25 items, so a non-lazy `Column` with
 * `forEach` is fine performance-wise. If episode counts ever grow to hundreds
 * (e.g. long-running shonen), the correct fix is to flatten the episode rows
 * into the parent `LazyColumn` using `items()` — NOT to nest another
 * `LazyColumn`.
 */
@Composable
private fun EpisodeList(
    episodes: List<SEpisode>,
    watchedEpisodes: Set<String>,
    episodeMetadata: Map<Int, app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata>,
    displayPrefs: EpisodeDisplayPrefs? = null,
    onOpenEpisode: (SEpisode, AnimeSource, List<SEpisode>) -> Unit,
    currentSource: AnimeSource?,
    onToggleWatched: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        episodes.forEachIndexed { index, episode ->
            val epNum = episode.episode_number.toInt().coerceAtLeast(1)
            val metadata = episodeMetadata[epNum]
            EpisodeRow(
                episode = episode,
                index = index,
                isWatched = watchedEpisodes.contains(episode.url),
                metadata = metadata,
                displayPrefs = displayPrefs,
                onClick = {
                    currentSource?.let { source ->
                        onOpenEpisode(episode, source, episodes)
                    }
                },
                onToggleWatched = { onToggleWatched(episode.url) },
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * A single episode row — fully customizable display with thumbnail, title,
 * description, date, audio pills, and episode number.
 *
 * Layout (default):
 * ```
 * ┌─────────────────────────────────────────────┐
 * │ ┌──────────┐  ┌─ Title (surfaceContainer) ─┐│
 * │ │ Thumbnail│  │ EP 3  The Dragon's Labyrinth ││
 * │ │  EP 3   │  └──────────────────────────────┘│
 * │ │         │  [Mar 15] [SUB] [DUB]            ││
 * │ └──────────┘  ┌─ Synopsis (surfaceContainer)─┐│
 * │               │ A young adventurer discovers ││
 * │               │ a hidden labyrinth beneath... ││
 * │               └──────────────────────────────┘│
 * └─────────────────────────────────────────────┘
 * ```
 *
 * Improvements per user feedback:
 * - Episode number badge: "EP N" format, compact (no excess height)
 * - No play icon (removed per user request)
 * - Synopsis spans full width below thumbnail + title
 * - Surface container colors with higher contrast (not blending into bg)
 * - Alternating backgrounds (zebra stripe) per design language §6
 */
@Composable
private fun EpisodeRow(
    episode: SEpisode,
    index: Int,
    isWatched: Boolean,
    metadata: app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata? = null,
    displayPrefs: EpisodeDisplayPrefs? = null,
    onClick: () -> Unit,
    onToggleWatched: () -> Unit,
) {
    val isEven = index % 2 == 0
    val cardColor = if (isEven) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
    }

    // Apply preferences or use defaults
    val showThumbnail = displayPrefs?.showThumbnails ?: true
    val showTitle = displayPrefs?.showTitles ?: true
    val showSummary = displayPrefs?.showSummaries ?: true
    val showDate = displayPrefs?.showDates ?: false
    val showNumber = displayPrefs?.showEpisodeNumber ?: true
    val showAudioPills = displayPrefs?.showAudioPills ?: false
    val thumbPos = displayPrefs?.thumbnailPosition ?: "left"
    val epNumPos = displayPrefs?.episodeNumberPosition ?: "overlay"
    val thumbSize = displayPrefs?.thumbnailSize ?: "medium"
    val titleMaxLines = displayPrefs?.titleMaxLines ?: 2
    val synopsisMaxLines = displayPrefs?.synopsisMaxLines ?: 3

    // Use metadata title if available, otherwise parse the extension title
    val displayTitle = metadata?.title
        ?: app.confused.anikuta.core.episodemetadata.util.EpisodeTitleParser.parseTitle(
            episode.name, episode.episode_number
        )
        ?: episode.name.ifBlank { "Episode ${formatEpisodeNumber(episode.episode_number)}" }

    val description = metadata?.description ?: episode.summary
    val thumbnailUrl = if (showThumbnail) metadata?.thumbnailUrl else null
    val epNumText = "EP ${formatEpisodeNumber(episode.episode_number)}"

    // Thumbnail sizes
    val (thumbWidth, thumbHeight) = when (thumbSize) {
        "small" -> 100.dp to 56.dp
        "large" -> 160.dp to 90.dp
        else -> 120.dp to 68.dp  // medium (default)
    }

    // Audio pills from scanlator
    val scanlatorUpper = episode.scanlator?.uppercase() ?: ""
    val hasSub = scanlatorUpper.contains("SUB")
    val hasDub = scanlatorUpper.contains("DUB")
    val hasHsub = scanlatorUpper.contains("HSUB")
    val hasAnyAudioPills = showAudioPills && (hasSub || hasDub || hasHsub)

    // Date
    val dateText = if (showDate && (metadata?.airDate ?: 0L) > 0) {
        formatDate(metadata!!.airDate!!)
    } else if (showDate && episode.date_upload > 0) {
        formatDate(episode.date_upload * 1000)
    } else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .watchedEpisodeEffect(isWatched)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        // ── Top row: thumbnail + title (side by side) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // Thumbnail (left position)
            if (thumbnailUrl != null && thumbPos == "left") {
                Box {
                    coil3.compose.AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = displayTitle,
                        modifier = Modifier
                            .size(width = thumbWidth, height = thumbHeight)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                    // Episode number overlay — compact "EP N" badge
                    if (showNumber && epNumPos == "overlay") {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.align(Alignment.TopStart).padding(3.dp),
                        ) {
                            Text(
                                text = epNumText,
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.size(10.dp))
            } else if (showNumber && epNumPos == "badge") {
                // Compact number badge (no thumbnail)
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = epNumText,
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                Spacer(modifier = Modifier.size(10.dp))
            }

            // Title (with prominent background)
            if (showTitle) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = displayTitle,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }

            // Thumbnail (right position)
            if (thumbnailUrl != null && thumbPos == "right") {
                Spacer(modifier = Modifier.size(10.dp))
                Box {
                    coil3.compose.AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = displayTitle,
                        modifier = Modifier
                            .size(width = thumbWidth, height = thumbHeight)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                    if (showNumber && epNumPos == "overlay") {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
                        ) {
                            Text(
                                text = epNumText,
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }

        // ── Date + audio pills row (full width, below thumbnail + title) ──
        if (dateText != null || hasAnyAudioPills) {
            Spacer(modifier = Modifier.size(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dateText != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = dateText,
                            fontFamily = RobotoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (hasSub) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("SUB", fontFamily = RobotoFamily, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (hasDub) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("DUB", fontFamily = RobotoFamily, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (hasHsub) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text("HSUB", fontFamily = RobotoFamily, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }

        // ── Synopsis (full width, below thumbnail + title + pills) ──
        if (showSummary && !description.isNullOrBlank()) {
            Spacer(modifier = Modifier.size(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = description,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = synopsisMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Formats epoch millis to "MMM dd, yyyy". */
private fun formatDate(epochMillis: Long): String {
    return try {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
        sdf.format(java.util.Date(epochMillis))
    } catch (e: Exception) {
        ""
    }
}

/** Display preferences passed to EpisodeRow (simplified from EpisodeDisplayPreferences). */
data class EpisodeDisplayPrefs(
    val showThumbnails: Boolean = true,
    val showTitles: Boolean = true,
    val showSummaries: Boolean = true,
    val showDates: Boolean = false,
    val showEpisodeNumber: Boolean = true,
    val showAudioPills: Boolean = false,
    val thumbnailPosition: String = "left",
    val titlePosition: String = "right",
    val synopsisPosition: String = "below",
    val datePosition: String = "right_below_synopsis",
    val episodeNumberPosition: String = "overlay",
    val thumbnailSize: String = "medium",
    val titleMaxLines: Int = 2,
    val synopsisMaxLines: Int = 3,
)

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
