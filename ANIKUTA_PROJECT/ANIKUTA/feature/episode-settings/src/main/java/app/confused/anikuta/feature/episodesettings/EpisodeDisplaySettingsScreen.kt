package app.confused.anikuta.feature.episodesettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.confused.anikuta.feature.animedetails.EpisodeDisplayPreferences
import org.koin.compose.koinInject

/**
 * The Episode DISPLAY settings screen — show/hide toggles for every episode-row
 * element, title max-lines, and element-background visibility.
 *
 * Full page (NOT a sheet). Layout:
 * - **Sticky** [EpisodeRowPreview] at the top (non-scrolling — stays fixed while
 *   the options below scroll).
 * - "SHOW / HIDE" group: 6 Material3 [SwitchSettingsRow]s (episode number, titles,
 *   summaries, thumbnails, dates, audio pills).
 * - "BACKGROUNDS" group: 3 Switches (title background, meta background, synopsis
 *   background) — per user request: "give the user the option to show or hide
 *   these fun options, like the background color for the text".
 * - "TITLE" group: title max-lines segmented row (1 line / 2 lines).
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
    val showTitleBg by prefs.showTitleBackground().changes().collectAsState(initial = prefs.showTitleBackground().get())
    val showMetaBg by prefs.showMetaBackground().changes().collectAsState(initial = prefs.showMetaBackground().get())
    val showSynopsisBg by prefs.showSynopsisBackground().changes().collectAsState(initial = prefs.showSynopsisBackground().get())

    SettingsSubpageScaffold(title = "Episode display", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Sticky live preview (non-scrolling, stays at top) ──
            EpisodeRowPreview(prefs = displayPrefs)
            Spacer(modifier = Modifier.height(16.dp))
            // ── Scrollable options below ──
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
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
                // ── Backgrounds (per user request: toggle element backgrounds) ──
                item {
                    SettingsGroupCard(title = "Backgrounds") {
                        SwitchSettingsRow(
                            icon = Icons.Filled.Title,
                            title = "Title background",
                            subtitle = "Give the title a dedicated background container",
                            checked = showTitleBg,
                            onCheckedChange = { prefs.showTitleBackground().set(it) },
                        )
                        InGroupDivider()
                        SwitchSettingsRow(
                            icon = Icons.Filled.FormatPaint,
                            title = "Date & audio background",
                            subtitle = "Give the date and audio pills a shared background container",
                            checked = showMetaBg,
                            onCheckedChange = { prefs.showMetaBackground().set(it) },
                        )
                        InGroupDivider()
                        SwitchSettingsRow(
                            icon = Icons.Filled.TextFields,
                            title = "Synopsis background",
                            subtitle = "Give the synopsis a dedicated background container",
                            checked = showSynopsisBg,
                            onCheckedChange = { prefs.showSynopsisBackground().set(it) },
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
}
