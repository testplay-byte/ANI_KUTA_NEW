package app.confused.anikuta.feature.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.confused.anikuta.core.player.PlayerEpisodePreferences
import org.koin.compose.koinInject

/**
 * Snapshot of all watch-page episode-display preferences.
 *
 * SEPARATE from the details page's [app.confused.anikuta.feature.animedetails.EpisodeDisplayPrefs]
 * — the two episode lists (details + watch) are independently customizable.
 * Per user: "the details page and the watch page would be customizable
 * separately. Their episode lists will be separate and they will be easily
 * customizable separately, meaning making changes in one will not automatically
 * make the changes in the other one."
 *
 * Backed by [PlayerEpisodePreferences] (`player_ep_*` keys).
 */
data class WatchEpisodeDisplayPrefs(
    val showThumbnails: Boolean = true,
    val showTitles: Boolean = true,
    val showSummaries: Boolean = true,
    val showDates: Boolean = true,
    val showEpisodeNumber: Boolean = true,
    val showAudioPills: Boolean = true,
    val thumbnailSize: String = "medium",
    val titleMaxLines: Int = 1,
    val synopsisMaxLines: Int = 2,
    val showTitleBackground: Boolean = true,
    val showDateBackground: Boolean = true,
    val showAudioBackground: Boolean = true,
    val showSynopsisBackground: Boolean = true,
)

/**
 * Reads [PlayerEpisodePreferences] into a reactive [WatchEpisodeDisplayPrefs]
 * snapshot. Used by the watch page's episode list + description section.
 */
@Composable
fun rememberWatchEpisodeDisplayPrefs(): WatchEpisodeDisplayPrefs {
    val prefs: PlayerEpisodePreferences = koinInject()
    val showThumbnails by prefs.showEpisodeThumbnails().changes().collectAsState(initial = prefs.showEpisodeThumbnails().get())
    val showTitles by prefs.showEpisodeTitles().changes().collectAsState(initial = prefs.showEpisodeTitles().get())
    val showSummaries by prefs.showEpisodeSummaries().changes().collectAsState(initial = prefs.showEpisodeSummaries().get())
    val showDates by prefs.showEpisodeDates().changes().collectAsState(initial = prefs.showEpisodeDates().get())
    val showNumber by prefs.showEpisodeNumber().changes().collectAsState(initial = prefs.showEpisodeNumber().get())
    val showAudioPills by prefs.showAudioPills().changes().collectAsState(initial = prefs.showAudioPills().get())
    val thumbSize by prefs.thumbnailSize().changes().collectAsState(initial = prefs.thumbnailSize().get())
    val titleLines by prefs.titleMaxLines().changes().collectAsState(initial = prefs.titleMaxLines().get())
    val synopsisLines by prefs.synopsisMaxLines().changes().collectAsState(initial = prefs.synopsisMaxLines().get())
    val showTitleBg by prefs.showTitleBackground().changes().collectAsState(initial = prefs.showTitleBackground().get())
    val showDateBg by prefs.showDateBackground().changes().collectAsState(initial = prefs.showDateBackground().get())
    val showAudioBg by prefs.showAudioBackground().changes().collectAsState(initial = prefs.showAudioBackground().get())
    val showSynopsisBg by prefs.showSynopsisBackground().changes().collectAsState(initial = prefs.showSynopsisBackground().get())

    return remember(
        showThumbnails, showTitles, showSummaries, showDates, showNumber, showAudioPills,
        thumbSize, titleLines, synopsisLines, showTitleBg, showDateBg, showAudioBg, showSynopsisBg,
    ) {
        WatchEpisodeDisplayPrefs(
            showThumbnails = showThumbnails,
            showTitles = showTitles,
            showSummaries = showSummaries,
            showDates = showDates,
            showEpisodeNumber = showNumber,
            showAudioPills = showAudioPills,
            thumbnailSize = thumbSize,
            titleMaxLines = titleLines,
            synopsisMaxLines = synopsisLines,
            showTitleBackground = showTitleBg,
            showDateBackground = showDateBg,
            showAudioBackground = showAudioBg,
            showSynopsisBackground = showSynopsisBg,
        )
    }
}
