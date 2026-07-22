package app.confused.anikuta.feature.episodesettings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.confused.anikuta.feature.animedetails.EpisodeDisplayPreferences
import org.koin.compose.koinInject

/**
 * The Episode DISPLAY settings screen — show/hide toggles for every episode-row
 * element + the title max-lines choice.
 *
 * Full page (NOT a sheet). Layout:
 * - Sticky [EpisodeRowPreview] at the top.
 * - "SHOW / HIDE" group: 6 Material3 [SwitchSettingsRow]s (episode number, titles,
 *   summaries, thumbnails, dates, audio pills).
 * - "TITLE" group: title max-lines segmented row (1 line / 2 lines).
 *
 * Per user feedback:
 * - Uses proper Material3 [Switch] toggles, NOT the ugly custom on/off pill buttons.
 * - Title defaults to 1 line (user: "force it to be on one single line") with an
 *   option for 2 lines.
 *
 * @param onBack Called when back is pressed.
 */
@Composable
fun EpisodeDisplaySettingsScreen(onBack: () -> Unit) {
    val prefs: EpisodeDisplayPreferences = koinInject()
    val displayPrefs = rememberEpisodeDisplayPrefs()

    // Individual reactive states for each toggle
    val showNumber by prefs.showEpisodeNumber().changes().collectAsState(initial = prefs.showEpisodeNumber().get())
    val showTitles by prefs.showEpisodeTitles().changes().collectAsState(initial = prefs.showEpisodeTitles().get())
    val showSummaries by prefs.showEpisodeSummaries().changes().collectAsState(initial = prefs.showEpisodeSummaries().get())
    val showThumbnails by prefs.showEpisodeThumbnails().changes().collectAsState(initial = prefs.showEpisodeThumbnails().get())
    val showDates by prefs.showEpisodeDates().changes().collectAsState(initial = prefs.showEpisodeDates().get())
    val showAudioPills by prefs.showAudioPills().changes().collectAsState(initial = prefs.showAudioPills().get())
    val titleLines by prefs.titleMaxLines().changes().collectAsState(initial = prefs.titleMaxLines().get())

    SettingsSubpageScaffold(title = "Episode display", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            // ── Sticky live preview ──
            item {
                EpisodeRowPreview(prefs = displayPrefs)
                Spacer(modifier = Modifier.height(16.dp))
            }
            // ── Show / hide toggles ──
            item {
                SettingsGroupCard(title = "Show / hide") {
                    SwitchSettingsRow(
                        icon = Icons.Filled.Numbers,
                        title = "Episode number",
                        subtitle = "Show the episode number on each card",
                        checked = showNumber,
                        onCheckedChange = { prefs.showEpisodeNumber().set(it) },
                    )
                    InGroupDivider()
                    SwitchSettingsRow(
                        icon = Icons.Filled.Title,
                        title = "Episode titles",
                        subtitle = "Show the parsed episode title",
                        checked = showTitles,
                        onCheckedChange = { prefs.showEpisodeTitles().set(it) },
                    )
                    InGroupDivider()
                    SwitchSettingsRow(
                        icon = Icons.Filled.Subtitles,
                        title = "Episode summaries",
                        subtitle = "Show the episode description",
                        checked = showSummaries,
                        onCheckedChange = { prefs.showEpisodeSummaries().set(it) },
                    )
                    InGroupDivider()
                    SwitchSettingsRow(
                        icon = Icons.Filled.Image,
                        title = "Episode thumbnails",
                        subtitle = "Show the preview image for each episode",
                        checked = showThumbnails,
                        onCheckedChange = { prefs.showEpisodeThumbnails().set(it) },
                    )
                    InGroupDivider()
                    SwitchSettingsRow(
                        icon = Icons.Filled.CalendarMonth,
                        title = "Episode dates",
                        subtitle = "Show the air date",
                        checked = showDates,
                        onCheckedChange = { prefs.showEpisodeDates().set(it) },
                    )
                    InGroupDivider()
                    SwitchSettingsRow(
                        icon = Icons.Filled.RecordVoiceOver,
                        title = "Audio pills",
                        subtitle = "Show SUB / DUB / HSUB tags",
                        checked = showAudioPills,
                        onCheckedChange = { prefs.showAudioPills().set(it) },
                    )
                }
            }
            // ── Title ──
            item {
                SettingsGroupCard(title = "Title") {
                    LabeledSegmentedRow(
                        label = "Title lines",
                        description = "Force the title to a single line, or allow it to wrap to two",
                        options = listOf(
                            "1 line" to (titleLines <= 1),
                            "2 lines" to (titleLines >= 2),
                        ),
                        onSelect = { idx ->
                            prefs.titleMaxLines().set(if (idx == 0) 1 else 2)
                        },
                    )
                }
            }
        }
    }
}
