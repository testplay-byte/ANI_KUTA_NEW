package app.confused.anikuta.feature.animedetails

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.confused.anikuta.core.common.model.details.DataSource
import app.confused.anikuta.core.common.model.details.UnifiedAnime
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
 * 4. [EpisodesSection] — real episode list from the matched source.
 * 5. [InfoSection] — key/value information table (format, status, etc.).
 *
 * The whole thing is wrapped in a Material3 [PullToRefreshBox] so the user
 * can pull down to refresh all three stages (AniList + source match + episodes).
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
    onRefresh: () -> Unit,
    onOpenEpisode: (SEpisode, AnimeSource, List<SEpisode>, WatchEpisodeContext) -> Unit,
    onToggleWatched: (String) -> Unit,
    onSwitchSource: (SourceMatcher.SourceMatch) -> Unit,
    onManualSearch: suspend (Long, String) -> Unit,
    onLinkManual: (AnimeCatalogueSource, SAnime) -> Unit,
    onClearManualSearch: () -> Unit,
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

    // ── Pull-to-refresh wrapper ──
    // PullToRefreshBox shows the Material3 pull indicator at the top while
    // [isRefreshing] is true. The user drags down from the top of the list
    // to trigger [onRefresh], which re-runs the full three-stage load.
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        LazyColumn(
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
                    onRefresh = onRefresh,
                )
            }

            // ── Genres ──
            item { GenresRow(anime) }

            // ── Synopsis ──
            if (!anime.description.isNullOrBlank()) {
                item { SynopsisSection(anime.description!!) }
            }

            // ── Episodes ──
            item {
                // Construct the WatchEpisodeContext (anime title + cover + metadata
                // map) so the watch page can render rich episode rows + theme itself.
                // Per user: "the meta data of the episode which the user wants to play
                // does not get shared to the watch page."
                val watchCtx = WatchEpisodeContext(
                    animeTitle = anime.title,
                    coverUrl = anime.coverUrl,
                    coverColorArgb = runCatching {
                        val hex = anime.coverColorHex
                        if (hex != null) AndroidColor.parseColor(hex) else 0
                    }.getOrDefault(0),
                    episodeMetadata = episodeMetadata,
                )
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

            // ── Information ──
            item {
                Spacer(modifier = Modifier.height(16.dp))
                InfoSection(anime)
            }
        }
    }
}
