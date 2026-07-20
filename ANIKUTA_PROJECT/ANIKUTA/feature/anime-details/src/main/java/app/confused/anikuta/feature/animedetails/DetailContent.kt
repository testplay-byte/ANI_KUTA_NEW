package app.confused.anikuta.feature.animedetails

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.confused.anikuta.core.anilist.model.AniListAnime
import app.confused.anikuta.core.anilist.model.coverColorHex
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import eu.kanade.tachiyomi.animesource.AnimeSource
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
 */
@Composable
fun DetailContent(
    anime: AniListAnime,
    episodeState: EpisodeState,
    currentMatch: SourceMatcher.SourceMatch?,
    allMatches: List<SourceMatcher.SourceMatch>,
    watchedEpisodes: Set<String>,
    onBack: () -> Unit,
    onOpenEpisode: (SEpisode, AnimeSource) -> Unit,
    onToggleWatched: (String) -> Unit,
    onSwitchSource: (SourceMatcher.SourceMatch) -> Unit,
) {
    var saved by remember { mutableStateOf(false) }

    // Parse cover color for dynamic theming (hex → Compose Color)
    val coverColor = remember(anime) {
        anime.coverColorHex?.let { hex ->
            runCatching {
                val rgb = if (hex.startsWith("#")) hex.substring(1) else hex
                Color(AndroidColor.parseColor("#$rgb"))
            }.getOrNull()
        } ?: Color(0xFF1A1A2E)
    }

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
                onBack = onBack,
                onToggleSave = { saved = !saved },
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
            EpisodesSection(
                episodeState = episodeState,
                currentMatch = currentMatch,
                allMatches = allMatches,
                watchedEpisodes = watchedEpisodes,
                onOpenEpisode = onOpenEpisode,
                onToggleWatched = onToggleWatched,
                onSwitchSource = onSwitchSource,
            )
        }

        // ── Information ──
        item {
            Spacer(modifier = Modifier.height(16.dp))
            InfoSection(anime)
        }
    }
}
