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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import org.koin.compose.koinInject

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

    // ── Inject + read the episode-display preferences reactively ──
    // This is the fix for the critical wiring bug: previously EpisodesSection
    // never received displayPrefs, so EpisodeRow always fell back to the
    // EpisodeDisplayPrefs data-class defaults — settings changes only affected
    // the settings preview, NOT the actual rendered list.
    val displayPrefs: EpisodeDisplayPreferences = koinInject()
    val snapshot = rememberEpisodeDisplaySnapshot(displayPrefs)

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
                displayPrefs = snapshot,
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
 * A single episode row — the PRIMARY two-section view (per user spec).
 *
 * Layout:
 * ```
 * ┌───────────────────────────────────────────────────┐
 * │  TOP SECTION (height driven by thumbnail)          │
 * │  ┌──────────┐  ┌─ Title (bg) ──────────────────┐  │
 * │  │          │  │ EP 3  The Dragon's Labyrinth   │  │
 * │  │ Thumbnail│  └────────────────────────────────┘  │
 * │  │  EP 3   │  ┌─ Date + Audio (bg) ────────────┐  │
 * │  │          │  │ Mar 15, 2024  SUB•DUB          │  │
 * │  └──────────┘  └────────────────────────────────┘  │
 * ├───────────────────────────────────────────────────┤
 * │  BOTTOM SECTION                                    │
 * │  ┌─ Synopsis (bg) ──────────────────────────────┐  │
 * │  │ A young adventurer discovers a hidden...     │  │
 * │  └──────────────────────────────────────────────┘  │
 * └───────────────────────────────────────────────────┘
 * ```
 *
 * - The top section's right side is divided into TWO equal sub-sections:
 *   title (top) + date/audio (bottom). Height is driven by the thumbnail.
 * - Each element (title, date+audio, synopsis) gets a dedicated background
 *   container (toggleable via [EpisodeDisplayPrefs.showTitleBackground] etc.).
 * - NO alternating zebra-stripe colors — all rows use the same lighter shade.
 * - Episode number badge: black 70%-alpha pill overlay on the thumbnail.
 * - Audio pills: ALWAYS show full names ("SUB", "DUB") with dot separators →
 *   "SUB•DUB" (per user request — not short letters).
 * - Pill heights are minimal: `labelSmall` typography + tight 6dp/1dp padding
 *   (per user: "the background height is way too much").
 * - Thumbnail: `metadata.thumbnailUrl ?: episode.preview_url` (fallback).
 * - Watched effect: grayscale + alpha 0.55f via [watchedEpisodeEffect].
 *
 * NOTE: Layout position prefs (thumbnailPosition, titlePosition, etc.) are
 * DORMANT for now — the row uses this single fixed view. Per user: "we are
 * only going to focus on one single view for the current time being."
 * The thumbnail SIZE pref is still active (small/medium/large).
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
    // Single lighter shade for ALL rows — no alternating zebra-stripe (per user request).
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

    // Apply preferences or use defaults
    val showThumbnail = displayPrefs?.showThumbnails ?: true
    val showTitle = displayPrefs?.showTitles ?: true
    val showSummary = displayPrefs?.showSummaries ?: true
    val showDate = displayPrefs?.showDates ?: true
    val showNumber = displayPrefs?.showEpisodeNumber ?: true
    val showAudioPills = displayPrefs?.showAudioPills ?: true
    val thumbSize = displayPrefs?.thumbnailSize ?: "medium"
    val titleMaxLines = displayPrefs?.titleMaxLines ?: 1
    val synopsisMaxLines = displayPrefs?.synopsisMaxLines ?: 3
    val showTitleBg = displayPrefs?.showTitleBackground ?: true
    val showMetaBg = displayPrefs?.showMetaBackground ?: true
    val showSynopsisBg = displayPrefs?.showSynopsisBackground ?: true

    // Use metadata title if available, otherwise parse the extension title
    val displayTitle = metadata?.title
        ?: app.confused.anikuta.core.episodemetadata.util.EpisodeTitleParser.parseTitle(
            episode.name, episode.episode_number,
        )
        ?: episode.name.ifBlank { "Episode ${formatEpisodeNumber(episode.episode_number)}" }

    val description = metadata?.description ?: episode.summary
    // Thumbnail fallback: prefer metadata, fall back to the extension's preview_url.
    val thumbnailUrl = if (showThumbnail) {
        metadata?.thumbnailUrl ?: episode.preview_url
    } else {
        null
    }
    val epNumText = "EP ${formatEpisodeNumber(episode.episode_number)}"
    val bareEpNum = formatEpisodeNumber(episode.episode_number)

    // Thumbnail sizes
    val (thumbWidth, thumbHeight) = when (thumbSize) {
        "small" -> 100.dp to 56.dp
        "large" -> 160.dp to 90.dp
        else -> 120.dp to 68.dp  // medium (default)
    }

    // Audio availability — parse BOTH scanlator AND episode name.
    val audio = parseAudioAvailability(episode.scanlator, episode.name)
    val hasSub = audio.hasSub
    val hasDub = audio.hasDub
    val hasHsub = audio.hasHsub
    val hasAnyAudioPills = showAudioPills && (hasSub || hasDub || hasHsub)

    // Date — prefer metadata airDate (epoch seconds), fall back to episode.date_upload (epoch millis)
    val dateText = if (showDate) {
        val airDate = metadata?.airDate
        when {
            airDate != null && airDate > 0 -> formatDate(airDate * 1000L)
            episode.date_upload > 0 -> formatDate(episode.date_upload)
            else -> null
        }
    } else null

    val hasMetaRow = dateText != null || hasAnyAudioPills

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
        // ══ TOP SECTION: thumbnail (left) + title/date-audio (right, 2 equal sub-sections) ══
        // The thumbnail drives the top-section height. The right column fills
        // the thumbnail height and is split equally between title and meta.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // ── Thumbnail (left) with episode-number overlay ──
            if (thumbnailUrl != null) {
                EpisodeThumbnail(
                    url = thumbnailUrl,
                    width = thumbWidth,
                    height = thumbHeight,
                    contentDescription = displayTitle,
                    showNumber = showNumber,
                    epNumText = epNumText,
                )
                Spacer(modifier = Modifier.size(10.dp))
            } else if (showNumber) {
                // No thumbnail — show the circle episode-number as the left element
                CircleEpisodeNumber(bareEpNum)
                Spacer(modifier = Modifier.size(10.dp))
            }

            // ── Right column: two equal sub-sections (title on top, meta on bottom) ──
            if (showTitle || hasMetaRow) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(IntrinsicSize.Min),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Top sub-section: Title (with optional background)
                    if (showTitle) {
                        if (showTitleBg) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = displayTitle,
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = titleMaxLines,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        } else {
                            Text(
                                text = displayTitle,
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = titleMaxLines,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Bottom sub-section: Date + Audio (with optional shared background)
                    if (hasMetaRow) {
                        if (showMetaBg) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                DateAndAudioRow(
                                    dateText = dateText,
                                    showDate = showDate,
                                    hasSub = hasSub,
                                    hasDub = hasDub,
                                    hasHsub = hasHsub,
                                    showAudioPills = showAudioPills,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        } else {
                            DateAndAudioRow(
                                dateText = dateText,
                                showDate = showDate,
                                hasSub = hasSub,
                                hasDub = hasDub,
                                hasHsub = hasHsub,
                                showAudioPills = showAudioPills,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } else if (thumbnailUrl != null || showNumber) {
                // No title and no meta — fill remaining space so thumbnail keeps natural size
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // ══ BOTTOM SECTION: Synopsis (with optional background) ══
        if (showSummary && !description.isNullOrBlank()) {
            Spacer(Modifier.size(8.dp))
            if (showSynopsisBg) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(8.dp),
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
            } else {
                Text(
                    text = description,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = synopsisMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The episode thumbnail with the overlay episode-number badge.
 *
 * The overlay badge: semi-transparent BLACK pill (70% alpha) at the top-left
 * corner. 6dp corners, `labelSmall` Bold White text, tight 6dp/1dp padding.
 */
@Composable
private fun EpisodeThumbnail(
    url: String,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    contentDescription: String,
    showNumber: Boolean,
    epNumText: String,
) {
    Box {
        coil3.compose.AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(width = width, height = height)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        if (showNumber) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            ) {
                Text(
                    text = epNumText,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** The circle fallback — 40dp `surfaceVariant` disc with the bare number (no "EP"). */
@Composable
private fun CircleEpisodeNumber(bareEpNum: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = bareEpNum,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The date pill + audio pills row.
 *
 * Per user request: pills have MINIMAL height. Uses `labelSmall`-equivalent
 * sizing with tight 6dp/1dp padding (was 8dp/3dp — too tall per user feedback).
 *
 * Audio pills ALWAYS show full names: "SUB", "DUB", "HSUB" with dot separators
 * → "SUB•DUB" (per user: "make sure that it shows the full name, like SUB•DUB").
 */
@Composable
private fun DateAndAudioRow(
    dateText: String?,
    showDate: Boolean,
    hasSub: Boolean,
    hasDub: Boolean,
    hasHsub: Boolean,
    showAudioPills: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasAudio = showAudioPills && (hasSub || hasDub || hasHsub)
    if (!showDate && !hasAudio) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDate && dateText != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            ) {
                Text(
                    text = dateText,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (hasAudio) {
            AudioPills(hasSub = hasSub, hasDub = hasDub, hasHsub = hasHsub)
        }
    }
}

/**
 * The audio-pills composable — one `outlineVariant` surface holding all detected
 * audio versions. ALWAYS uses full names ("SUB", "DUB", "HSUB") separated by
 * 3dp dots → "SUB•DUB" (per user request — not short letters).
 */
@Composable
private fun AudioPills(hasSub: Boolean, hasDub: Boolean, hasHsub: Boolean) {
    val parts = buildList {
        if (hasSub) add("SUB")
        if (hasDub) add("DUB")
        if (hasHsub) add("HSUB")
    }
    if (parts.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            parts.forEachIndexed { idx, label ->
                if (idx > 0) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * Parse SUB/DUB/HSUB availability from BOTH the scanlator AND the episode name.
 *
 * Many anime extensions leave `scanlator` null but put the audio token in the
 * episode name (e.g. "Episode 5 - SUB"). Checking both maximises the chance
 * of detecting audio availability at episode-list time.
 *
 * NOTE: HSUB is checked BEFORE SUB (since "HSUB" contains "SUB" as a substring).
 */
private data class AudioAvailability(
    val hasSub: Boolean,
    val hasDub: Boolean,
    val hasHsub: Boolean,
)

private fun parseAudioAvailability(scanlator: String?, episodeName: String): AudioAvailability {
    val haystack = ((scanlator ?: "") + " " + episodeName).uppercase()
    val hasHsub = haystack.contains("HSUB") || haystack.contains("HARDSUB")
    val hasSub = haystack.contains("SUB") && !hasHsub
    val hasDub = haystack.contains("DUB") && !hasHsub
    return AudioAvailability(hasSub = hasSub, hasDub = hasDub, hasHsub = hasHsub)
}

/**
 * Reads [EpisodeDisplayPreferences] into a reactive [EpisodeDisplayPrefs] snapshot.
 * Collected via `Preference.changes()` so the episode list updates instantly
 * when a setting changes in the new `:feature:episode-settings` screens.
 */
@Composable
private fun rememberEpisodeDisplaySnapshot(prefs: EpisodeDisplayPreferences): EpisodeDisplayPrefs {
    val showNumber by prefs.showEpisodeNumber().changes().collectAsState(initial = prefs.showEpisodeNumber().get())
    val showTitles by prefs.showEpisodeTitles().changes().collectAsState(initial = prefs.showEpisodeTitles().get())
    val showSummaries by prefs.showEpisodeSummaries().changes().collectAsState(initial = prefs.showEpisodeSummaries().get())
    val showThumbnails by prefs.showEpisodeThumbnails().changes().collectAsState(initial = prefs.showEpisodeThumbnails().get())
    val showDates by prefs.showEpisodeDates().changes().collectAsState(initial = prefs.showEpisodeDates().get())
    val showAudioPills by prefs.showAudioPills().changes().collectAsState(initial = prefs.showAudioPills().get())
    val thumbPos by prefs.thumbnailPosition().changes().collectAsState(initial = prefs.thumbnailPosition().get())
    val titlePos by prefs.titlePosition().changes().collectAsState(initial = prefs.titlePosition().get())
    val synopsisPos by prefs.synopsisPosition().changes().collectAsState(initial = prefs.synopsisPosition().get())
    val datePos by prefs.datePosition().changes().collectAsState(initial = prefs.datePosition().get())
    val epNumPos by prefs.episodeNumberPosition().changes().collectAsState(initial = prefs.episodeNumberPosition().get())
    val thumbSize by prefs.thumbnailSize().changes().collectAsState(initial = prefs.thumbnailSize().get())
    val titleLines by prefs.titleMaxLines().changes().collectAsState(initial = prefs.titleMaxLines().get())
    val synopsisLines by prefs.synopsisMaxLines().changes().collectAsState(initial = prefs.synopsisMaxLines().get())
    val showTitleBg by prefs.showTitleBackground().changes().collectAsState(initial = prefs.showTitleBackground().get())
    val showMetaBg by prefs.showMetaBackground().changes().collectAsState(initial = prefs.showMetaBackground().get())
    val showSynopsisBg by prefs.showSynopsisBackground().changes().collectAsState(initial = prefs.showSynopsisBackground().get())

    return remember(
        showNumber, showTitles, showSummaries, showThumbnails, showDates, showAudioPills,
        thumbPos, titlePos, synopsisPos, datePos, epNumPos, thumbSize, titleLines, synopsisLines,
        showTitleBg, showMetaBg, showSynopsisBg,
    ) {
        EpisodeDisplayPrefs(
            showThumbnails = showThumbnails,
            showTitles = showTitles,
            showSummaries = showSummaries,
            showDates = showDates,
            showEpisodeNumber = showNumber,
            showAudioPills = showAudioPills,
            thumbnailPosition = thumbPos,
            titlePosition = titlePos,
            synopsisPosition = synopsisPos,
            datePosition = datePos,
            episodeNumberPosition = epNumPos,
            thumbnailSize = thumbSize,
            titleMaxLines = titleLines,
            synopsisMaxLines = synopsisLines,
            showTitleBackground = showTitleBg,
            showMetaBackground = showMetaBg,
            showSynopsisBackground = showSynopsisBg,
        )
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
    val showDates: Boolean = true,
    val showEpisodeNumber: Boolean = true,
    val showAudioPills: Boolean = true,
    val thumbnailPosition: String = "left",
    val titlePosition: String = "right",
    val synopsisPosition: String = "below",
    val datePosition: String = "right_below_synopsis",
    val episodeNumberPosition: String = "overlay",
    val thumbnailSize: String = "medium",
    val titleMaxLines: Int = 1,
    val synopsisMaxLines: Int = 3,
    // ── Background toggles (per user request: show/hide element backgrounds) ──
    val showTitleBackground: Boolean = true,
    val showMetaBackground: Boolean = true,
    val showSynopsisBackground: Boolean = true,
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
