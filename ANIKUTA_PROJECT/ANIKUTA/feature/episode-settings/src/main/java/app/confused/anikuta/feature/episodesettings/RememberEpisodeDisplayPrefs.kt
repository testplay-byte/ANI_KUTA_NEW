package app.confused.anikuta.feature.episodesettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.confused.anikuta.feature.animedetails.EpisodeDisplayPrefs
import app.confused.anikuta.feature.animedetails.EpisodeDisplayPreferences
import org.koin.compose.koinInject

/**
 * Reads the current [EpisodeDisplayPreferences] into an [EpisodeDisplayPrefs]
 * snapshot, reactively (collects on `Preference.changes` so the live preview +
 * the actual episode list both update instantly when a setting changes).
 *
 * Used by every episode-settings screen to drive its live preview.
 */
@Composable
fun rememberEpisodeDisplayPrefs(): EpisodeDisplayPrefs {
    val prefs: EpisodeDisplayPreferences = koinInject()
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
