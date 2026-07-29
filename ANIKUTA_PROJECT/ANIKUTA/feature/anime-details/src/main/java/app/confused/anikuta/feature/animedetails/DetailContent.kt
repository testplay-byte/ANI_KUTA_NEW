package app.confused.anikuta.feature.animedetails

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.confused.anikuta.core.common.model.details.DataSource
import app.confused.anikuta.core.common.model.details.UnifiedAnime
import app.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import app.confused.anikuta.core.preferences.EpisodeDisplayPreferences
import app.confused.anikuta.core.preferences.ThemePreferences
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode

/**
 * The main scrollable content of the detail screen.
 *
 * Renders (top → bottom):
 * 1. [DetailBanner] — blurred cover + gradient + title + action buttons.
 * 2. [GenresRow] — horizontal scroll of genre chips.
 * 3. [SynopsisSection] — collapsible synopsis with "Show more/less".
 * 4. [EpisodesSection] header — title + source chip (or "Source unavailable"
 *    chip when the extension source is no longer installed).
 * 5. **Flattened episode rows** — each episode is a separate `LazyColumn`
 *    `items()` entry (NOT a single `item { EpisodeList(...) }` block). This is
 *    the perf fix from the scroll-blur branch: for anime with 100+ episodes the
 *    old `forEachIndexed` inside one `item` composed ALL rows at once → severe
 *    jank. Now only visible rows (+ buffer) are composed.
 * 6. [InfoSection] — key/value information table (format, status, etc.).
 *
 * The whole thing is wrapped in a Material3 [PullToRefreshBox] so the user
 * can pull down to refresh all three stages (AniList + source match + episodes).
 *
 * A [ScrollBlurOverlay] sits at the top-center of the list — fades in when
 * content scrolls under the (pinned) banner. Toggleable via the
 * `ThemePreferences.headerBlurEffect` preference (default true).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailContent(
    anime: UnifiedAnime,
    episodeState: EpisodeState,
    currentMatch: SourceMatcher.SourceMatch?,
    allMatches: List<SourceMatcher.SourceMatch>,
    watchedEpisodes: Set<String>,
    episodeMetadata: Map<Int, app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata>,
    isRefreshing: Boolean,
    isSearching: Boolean,
    manualSearchResults: List<SourceMatcher.ManualSearchResult>,
    manualSearchErrors: List<Pair<String, String>>,
    autoMatchErrors: List<Pair<String, String>>?,
    hasSearched: Boolean,
    availableSources: List<SourceMatcher.SourceInfo>,
    saved: Boolean,
    onToggleSave: () -> Unit,
    onLongPressSave: () -> Unit,
    onBack: () -> Unit,
    currentDataSource: DataSource,
    entryMode: DataSource,
    onSwitchDataSource: (DataSource) -> Unit,
    onLinkToAniList: () -> Unit,
    onSwitchAnime: () -> Unit,
    onUnlinkFromAniList: () -> Unit = {},
    onRefresh: () -> Unit,
    onOpenEpisode: (SEpisode, AnimeSource, List<SEpisode>, WatchEpisodeContext) -> Unit,
    onToggleWatched: (String) -> Unit,
    onSwitchSource: (SourceMatcher.SourceMatch) -> Unit,
    onManualSearch: suspend (Long, String) -> Unit,
    onLinkManual: (AnimeCatalogueSource, SAnime) -> Unit,
    onClearManualSearch: () -> Unit,
    /** Whether the episode-metadata fetch has completed (success / skipped / error).
     *  Drives the small spinner next to the "Episodes" heading. */
    metadataFetchComplete: Boolean = false,
    /** Agent 2 — Downloads: enqueues a download for an episode. */
    onDownloadEpisode: (SEpisode, AnimeSource, WatchEpisodeContext) -> Unit = { _, _, _ -> },
    /** Per-episode download states keyed by episode URL (for the row UI). */
    downloadStates: Map<String, EpisodeDownloadState> = emptyMap(),
    onDownloadCancel: (String) -> Unit = {},
    onDownloadResume: (String) -> Unit = {},
    onDownloadRetry: (String) -> Unit = {},
    onDownloadDelete: (String) -> Unit = {},
) {
    // Parse cover color for dynamic theming (hex → Compose Color).
    // UnifiedAnime.coverColorHex comes from AniList's coverImage.color (AniList mode)
    // or Palette extraction (extension mode — Phase 9). Null → dark fallback.
    val coverColor = remember(anime) {
        anime.coverColorHex?.let { hex ->
            runCatching {
                val rgb = if (hex.startsWith("#")) hex.substring(1) else hex
                Color(AndroidColor.parseColor("#$rgb"))
            }.getOrNull()
        } ?: Color(0xFF1A1A2E)
    }

    // ── Hoisted values used by BOTH the EpisodesSection header + the flattened
    //    episode items() below ──
    // The WatchEpisodeContext (anime title + cover + metadata map) is needed by
    // every episode row's onClick handler (passed to AppController.resolveEpisode
    // → builds WatchRequest). Hoisting it out of the item {} lambda avoids
    // reconstructing it on every recomposition.
    val watchCtx = remember(anime, episodeMetadata) {
        WatchEpisodeContext(
            animeTitle = anime.title,
            coverUrl = anime.coverUrl,
            coverColorArgb = runCatching {
                val hex = anime.coverColorHex
                if (hex != null) AndroidColor.parseColor(hex) else 0
            }.getOrDefault(0),
            episodeMetadata = episodeMetadata,
        )
    }
    // The episode-display snapshot (read reactively from EpisodeDisplayPreferences
    // via Preference.changes()) is also needed by every episode row. Hoisting it
    // out of the lambda + passing it to EpisodesSection + the items() below means
    // it's read ONCE per recomposition, not per-row.
    val displaySnapshot = rememberEpisodeDisplaySnapshot(
        remember { org.koin.core.context.GlobalContext.get().get<EpisodeDisplayPreferences>() },
    )

    // ── Header-blur preference (toggleable from Appearance → General → Effects) ──
    val prefs = remember { org.koin.core.context.GlobalContext.get().get<ThemePreferences>() }
    val headerBlurEnabled by prefs.headerBlurEffect.changes()
        .collectAsState(initial = prefs.headerBlurEffect.get())

    // ── Pull-to-refresh wrapper ──
    // PullToRefreshBox shows the Material3 pull indicator at the top while
    // [isRefreshing] is true. The user drags down from the top of the list
    // to trigger [onRefresh], which re-runs the full three-stage load.
    val listState = rememberLazyListState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                // ── Banner ──
                item {
                    DetailBanner(
                        anime = anime,
                        coverColor = coverColor,
                        saved = saved,
                        currentDataSource = currentDataSource,
                        entryMode = entryMode,
                        onBack = onBack,
                        onToggleSave = onToggleSave,
                        onLongPressSave = onLongPressSave,
                        onSwitchDataSource = onSwitchDataSource,
                        onLinkToAniList = onLinkToAniList,
                        onSwitchAnime = onSwitchAnime,
                        onUnlinkFromAniList = onUnlinkFromAniList,
                        onRefresh = onRefresh,
                    )
                }

                // ── Genres ──
                item { GenresRow(anime) }

                // ── Synopsis ──
                if (!anime.description.isNullOrBlank()) {
                    item { SynopsisSection(anime.description!!) }
                }

                // ── Episodes header + non-loaded states ──
                // EpisodesSection renders the header (title + source chip / "Source
                // unavailable" chip) + the loading / searching / error / no-match
                // states. When episodes are Loaded, the rows themselves are added as
                // individual LazyColumn items() below for lazy rendering.
                item(key = "ep_header") {
                    EpisodesSection(
                        episodeState = episodeState,
                        currentMatch = currentMatch,
                        allMatches = allMatches,
                        watchedEpisodes = watchedEpisodes,
                        episodeMetadata = episodeMetadata,
                        isSearching = isSearching,
                        manualSearchResults = manualSearchResults,
                        manualSearchErrors = manualSearchErrors,
                        autoMatchErrors = autoMatchErrors,
                        hasSearched = hasSearched,
                        availableSources = availableSources,
                        initialSearchQuery = anime.title,
                        onOpenEpisode = { episode, source, episodes ->
                            onOpenEpisode(episode, source, episodes, watchCtx)
                        },
                        onToggleWatched = onToggleWatched,
                        onSwitchSource = onSwitchSource,
                        onManualSearch = onManualSearch,
                        onLinkManual = onLinkManual,
                        onClearManualSearch = onClearManualSearch,
                        metadataFetchComplete = metadataFetchComplete,
                        sourceId = anime.sourceId,
                        onDownloadEpisode = { episode, source ->
                            onDownloadEpisode(episode, source, watchCtx)
                        },
                        downloadStates = downloadStates,
                        onDownloadCancel = onDownloadCancel,
                        onDownloadResume = onDownloadResume,
                        onDownloadRetry = onDownloadRetry,
                        onDownloadDelete = onDownloadDelete,
                    )
                }

                // ── Episode rows (lazy — only visible ones are composed) ──
                // Previously ALL episodes were composed at once inside a single
                // item {} via forEachIndexed. For anime with 100+ episodes this
                // caused severe jank. Now each episode is a separate LazyColumn
                // item — Compose only composes the visible ones (+ a small buffer).
                if (episodeState is EpisodeState.Loaded) {
                    val episodes = episodeState.episodes
                    items(count = episodes.size, key = { index -> "ep_$index" }) { index ->
                        val episode = episodes[index]
                        val epNum = episode.episode_number.toInt().coerceAtLeast(1)
                        val metadata = episodeMetadata[epNum]
                        EpisodeRow(
                            episode = episode,
                            index = index,
                            isWatched = watchedEpisodes.contains(episode.url),
                            metadata = metadata,
                            displayPrefs = displaySnapshot,
                            onClick = {
                                currentMatch?.source?.let { source ->
                                    onOpenEpisode(episode, source, episodes, watchCtx)
                                }
                            },
                            onToggleWatched = { onToggleWatched(episode.url) },
                            onDownload = {
                                currentMatch?.source?.let { source ->
                                    onDownloadEpisode(episode, source, watchCtx)
                                }
                            },
                            downloadState = downloadStates[episode.url] ?: EpisodeDownloadState.NotDownloaded,
                            onDownloadCancel = { onDownloadCancel(episode.url) },
                            onDownloadResume = { onDownloadResume(episode.url) },
                            onDownloadRetry = { onDownloadRetry(episode.url) },
                            onDownloadDelete = { onDownloadDelete(episode.url) },
                        )
                    }
                    item(key = "ep_spacer") { Spacer(modifier = Modifier.height(16.dp)) }
                }

                // ── Information ──
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoSection(anime)
                }
            }

            // Scroll blur overlay — fades in when content scrolls under the (pinned)
            // banner. The flicker-fix: when firstVisibleItemIndex > 0, return
            // MAX_VALUE so the overlay stays at full opacity (firstVisibleItemScrollOffset
            // resets to 0 on every item boundary crossing — without this guard the
            // overlay would flicker disappear → reappear).
            ScrollBlurOverlay(
                scrollOffset = {
                    if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                    else listState.firstVisibleItemScrollOffset.toFloat()
                },
                backgroundColor = MaterialTheme.colorScheme.background,
                enabled = headerBlurEnabled,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
