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
 * A single episode row — fully customizable display with thumbnail, title,
 * description, date, audio pills, and episode number.
 *
 * Design (rebuilt to match the OLD ANIKUTA project's `EpisodeRowContent` —
 * minimal, high-contrast, theme-aware):
 * - Episode number badge: black 70%-alpha pill, 6dp corners, "EP N" in
 *   `labelSmall` Bold White (overlay variant, default). Badge variant uses
 *   `primaryContainer`/`onPrimaryContainer`. Circle fallback (no thumbnail)
 *   is a 40dp `surfaceVariant` disc with the bare number.
 * - Title: `titleSmall` Bold, `maxLines = titleMaxLines` (default 1 per user
 *   request — "force it to be on one single line"), `Ellipsis`. NO heavy
 *   surface background — plain text on the card (like the old project).
 * - Date: `outlineVariant` pill, `labelSmall` Medium, `onSurfaceVariant`,
 *   format "MMM d, yyyy". Shown when `showDates` + data available.
 * - Audio pills: single `outlineVariant` surface holding SUB/DUB/HSUB. When
 *   2+ versions are present, uses short letters ("S", "D") separated by
 *   3dp dots; when only one, uses the full label. Derived from
 *   `episode.scanlator` AND `episode.name` (many extensions put the audio
 *   token in the episode name).
 * - Thumbnail: `metadata.thumbnailUrl ?: episode.preview_url` (fallback so
 *   thumbnails render even when metadata is missing).
 * - Synopsis: plain text on card (no surface background), `bodySmall`,
 *   `maxLines = synopsisMaxLines`, `Ellipsis`.
 * - Watched effect: grayscale + alpha 0.55f via [watchedEpisodeEffect].
 *
 * @param displayPrefs The current display-prefs snapshot. NOW WIRED — previously
 *   this was always null and the row fell back to hardcoded defaults, which is
 *   why settings changes never affected the actual rendered list.
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

    // Apply preferences or use defaults (defaults aligned with EpisodeDisplayPreferences)
    val showThumbnail = displayPrefs?.showThumbnails ?: true
    val showTitle = displayPrefs?.showTitles ?: true
    val showSummary = displayPrefs?.showSummaries ?: true
    val showDate = displayPrefs?.showDates ?: true
    val showNumber = displayPrefs?.showEpisodeNumber ?: true
    val showAudioPills = displayPrefs?.showAudioPills ?: true
    val thumbPos = displayPrefs?.thumbnailPosition ?: "left"
    val titlePos = displayPrefs?.titlePosition ?: "right"
    val synopsisPos = displayPrefs?.synopsisPosition ?: "below"
    val datePos = displayPrefs?.datePosition ?: "right_below_synopsis"
    val epNumPos = displayPrefs?.episodeNumberPosition ?: "overlay"
    val thumbSize = displayPrefs?.thumbnailSize ?: "medium"
    val titleMaxLines = displayPrefs?.titleMaxLines ?: 1
    val synopsisMaxLines = displayPrefs?.synopsisMaxLines ?: 3

    // Use metadata title if available, otherwise parse the extension title
    val displayTitle = metadata?.title
        ?: app.confused.anikuta.core.episodemetadata.util.EpisodeTitleParser.parseTitle(
            episode.name, episode.episode_number,
        )
        ?: episode.name.ifBlank { "Episode ${formatEpisodeNumber(episode.episode_number)}" }

    val description = metadata?.description ?: episode.summary
    // Thumbnail fallback: prefer metadata, fall back to the extension's preview_url.
    // Previously this used metadata ONLY, so thumbnails never rendered when
    // metadata was missing — even though extensions often provide preview_url.
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
    // Many anime extensions put "SUB"/"DUB" in the episode name because the
    // scanlator field is rarely populated. This is the pragmatic episode-list-
    // time approach; true per-episode audio-track data is only available after
    // getVideoList() at watch time.
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
                EpisodeThumbnail(
                    url = thumbnailUrl,
                    width = thumbWidth,
                    height = thumbHeight,
                    contentDescription = displayTitle,
                    showNumber = showNumber,
                    epNumPos = epNumPos,
                    epNumText = epNumText,
                    alignEnd = false,
                )
                Spacer(modifier = Modifier.size(10.dp))
            } else if (showNumber && epNumPos == "badge") {
                // Inline badge (when no thumbnail, or explicit badge mode)
                InlineEpisodeNumberBadge(epNumText)
                Spacer(modifier = Modifier.size(10.dp))
            } else if (thumbnailUrl == null && showNumber && epNumPos != "badge") {
                // Circle fallback (no thumbnail at all)
                CircleEpisodeNumber(bareEpNum)
                Spacer(modifier = Modifier.size(10.dp))
            }

            // Title column (when title is "right" of thumbnail)
            if (showTitle && titlePos == "right") {
                Column(modifier = Modifier.weight(1f)) {
                    EpisodeTitle(displayTitle, titleMaxLines)
                    // Date + audio pills beside title (when date position is "right_*")
                    if (showDate && datePos == "right_above_synopsis" && (dateText != null || hasAnyAudioPills)) {
                        Spacer(Modifier.size(4.dp))
                        DateAndAudioRow(dateText, showDate, hasAnyAudioPills, hasSub, hasDub, hasHsub, showAudioPills)
                    }
                    if (showSummary && !description.isNullOrBlank() && synopsisPos == "right") {
                        Spacer(Modifier.size(4.dp))
                        EpisodeSynopsis(description, synopsisMaxLines)
                    }
                    if (showDate && datePos == "right_below_synopsis" && (dateText != null || hasAnyAudioPills)) {
                        Spacer(Modifier.size(4.dp))
                        DateAndAudioRow(dateText, showDate, hasAnyAudioPills, hasSub, hasDub, hasHsub, showAudioPills)
                    }
                }
            } else if (showTitle && titlePos == "below" && (thumbnailUrl != null || epNumPos == "badge" || (thumbnailUrl == null && showNumber && epNumPos != "badge"))) {
                // Title is below — fill remaining horizontal space in the top row so the
                // thumbnail/badge stays at its natural size instead of stretching to fill width.
                Spacer(modifier = Modifier.weight(1f))
            } else if (!showTitle && (thumbnailUrl != null || epNumPos == "badge")) {
                // No title — fill remaining horizontal space so the thumbnail/badge doesn't stretch
                Spacer(modifier = Modifier.weight(1f))
            }

            // Thumbnail (right position)
            if (thumbnailUrl != null && thumbPos == "right") {
                Spacer(modifier = Modifier.size(10.dp))
                EpisodeThumbnail(
                    url = thumbnailUrl,
                    width = thumbWidth,
                    height = thumbHeight,
                    contentDescription = displayTitle,
                    showNumber = showNumber,
                    epNumPos = epNumPos,
                    epNumText = epNumText,
                    alignEnd = true,
                )
            }
        }

        // Title "below" thumbnail (full width)
        if (showTitle && titlePos == "below") {
            Spacer(Modifier.size(8.dp))
            EpisodeTitle(displayTitle, titleMaxLines)
        }

        // Synopsis "below" (full width)
        if (showSummary && !description.isNullOrBlank() && synopsisPos == "below") {
            Spacer(Modifier.size(6.dp))
            EpisodeSynopsis(description, synopsisMaxLines)
        }

        // Date + audio pills "below" (full-width row)
        if (showDate && datePos == "below" && (dateText != null || hasAnyAudioPills)) {
            Spacer(Modifier.size(6.dp))
            DateAndAudioRow(dateText, showDate, hasAnyAudioPills, hasSub, hasDub, hasHsub, showAudioPills)
        }
    }
}

/**
 * The episode thumbnail with an optional overlay episode-number badge.
 *
 * The overlay badge is the OLD ANIKUTA design: a semi-transparent BLACK pill
 * (70% alpha — NOT a theme color) at the top-start/top-end corner, so it stays
 * high-contrast on any thumbnail. 6dp corners, `labelSmall` Bold White text,
 * 6dp/2dp inner padding.
 */
@Composable
private fun EpisodeThumbnail(
    url: String,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    contentDescription: String,
    showNumber: Boolean,
    epNumPos: String,
    epNumText: String,
    alignEnd: Boolean,
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
        if (showNumber && epNumPos == "overlay") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(if (alignEnd) Alignment.TopEnd else Alignment.TopStart)
                    .padding(4.dp),
            ) {
                Text(
                    text = epNumText,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** The inline badge variant — `primaryContainer` pill beside the title. */
@Composable
private fun InlineEpisodeNumberBadge(epNumText: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = epNumText,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
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

/** Plain-text title on the card (no surface background — matches old project). */
@Composable
private fun EpisodeTitle(title: String, maxLines: Int) {
    Text(
        text = title,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Plain-text synopsis on the card (no surface background). */
@Composable
private fun EpisodeSynopsis(text: String, maxLines: Int) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The date pill + audio pills row.
 *
 * Date pill: `outlineVariant` surface, `labelSmall` Medium, `onSurfaceVariant`,
 * 8dp/3dp padding, single line.
 *
 * Audio pills: a SINGLE `outlineVariant` surface holding all detected versions.
 * When 2+ versions → short letters ("S", "D") separated by 3dp dots; when only
 * one → full label ("SUB"). Mirrors the old project's `AudioPills`.
 */
@Composable
private fun DateAndAudioRow(
    dateText: String?,
    showDate: Boolean,
    hasAnyAudioPills: Boolean,
    hasSub: Boolean,
    hasDub: Boolean,
    hasHsub: Boolean,
    showAudioPills: Boolean,
) {
    if (!showDate && !hasAnyAudioPills) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (showAudioPills && hasAnyAudioPills) {
            AudioPills(hasSub = hasSub, hasDub = hasDub, hasHsub = hasHsub)
        }
    }
}

/**
 * The audio-pills composable — one `outlineVariant` surface holding all detected
 * audio versions. 2+ versions → short letters + 3dp dots; 1 version → full label.
 */
@Composable
private fun AudioPills(hasSub: Boolean, hasDub: Boolean, hasHsub: Boolean) {
    data class Audio(val full: String, val short: String)
    val parts = buildList {
        if (hasSub) add(Audio("SUB", "S"))
        if (hasDub) add(Audio("DUB", "D"))
        if (hasHsub) add(Audio("HSUB", "H"))
    }
    if (parts.isEmpty()) return
    val useShort = parts.size >= 2
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            parts.forEachIndexed { idx, audio ->
                if (idx > 0) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                Text(
                    text = if (useShort) audio.short else audio.full,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
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

    return remember(
        showNumber, showTitles, showSummaries, showThumbnails, showDates, showAudioPills,
        thumbPos, titlePos, synopsisPos, datePos, epNumPos, thumbSize, titleLines, synopsisLines,
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
