package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anikuta.player.PlayerEpisodePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Player → Episode list display settings subpage.
 *
 * Same structure as the detail page's DisplaySettingsScreen + LayoutSettingsScreen:
 *  - Sticky live preview at top
 *  - Show/hide toggles below
 *  - Layout positions section
 *
 * Uses PlayerEpisodePreferences (separate from detail page preferences).
 */
@Composable
fun PlayerEpisodeDisplayScreen(onBack: () -> Unit) {
    val prefs: PlayerEpisodePreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val showTitles by prefs.showEpisodeTitles().stateIn(scope).collectAsState()
    val showSummaries by prefs.showEpisodeSummaries().stateIn(scope).collectAsState()
    val showThumbnails by prefs.showEpisodeThumbnails().stateIn(scope).collectAsState()
    val showDates by prefs.showEpisodeDates().stateIn(scope).collectAsState()
    val showEpisodeNumber by prefs.showEpisodeNumber().stateIn(scope).collectAsState()
    val showAudioPills by prefs.showAudioPills().stateIn(scope).collectAsState()
    val synopsisPos by prefs.synopsisPosition().stateIn(scope).collectAsState()
    val datePos by prefs.datePosition().stateIn(scope).collectAsState()
    val thumbSize by prefs.thumbnailSize().stateIn(scope).collectAsState()
    val titlePos by prefs.titlePosition().stateIn(scope).collectAsState()
    val epNumPos by prefs.episodeNumberPosition().stateIn(scope).collectAsState()
    val thumbPos by prefs.thumbnailPosition().stateIn(scope).collectAsState()
    val dlPlacement by prefs.downloadButtonPlacement().stateIn(scope).collectAsState()
    val showDownloadButton by prefs.showDownloadButton().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "Episode list", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---- Sticky live preview ----
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            ) {
                Text(
                    "LIVE PREVIEW",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    EpisodeRowPreview(
                        showThumbnails = showThumbnails,
                        showSummaries = showSummaries,
                        showTitles = showTitles,
                        showDates = showDates,
                        showEpisodeNumber = showEpisodeNumber,
                        showAudioPills = showAudioPills,
                        synopsisPosition = synopsisPos,
                        datePosition = datePos,
                        thumbnailSize = thumbSize,
                        titlePosition = titlePos,
                        episodeNumberPosition = epNumPos,
                        thumbnailPosition = thumbPos,
                        downloadButtonPlacement = dlPlacement,
                        showDownloadButton = showDownloadButton,
                    )
                }
            }

            // ---- Scrollable settings ----
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsGroupCard(title = "Show or hide elements") {
                            SwitchSettingsRow(
                                icon = Icons.Default.Numbers,
                                title = "Episode number",
                                subtitle = "Show the episode number on each card",
                                checked = showEpisodeNumber,
                                onCheckedChange = { prefs.showEpisodeNumber().set(it) },
                            )
                            HorizontalDivider()
                            SwitchSettingsRow(
                                icon = Icons.Default.Title,
                                title = "Episode titles",
                                subtitle = "Show the parsed episode title",
                                checked = showTitles,
                                onCheckedChange = { prefs.showEpisodeTitles().set(it) },
                            )
                            HorizontalDivider()
                            SwitchSettingsRow(
                                icon = Icons.Default.Subtitles,
                                title = "Episode summaries",
                                subtitle = "Show the episode description",
                                checked = showSummaries,
                                onCheckedChange = { prefs.showEpisodeSummaries().set(it) },
                            )
                            HorizontalDivider()
                            SwitchSettingsRow(
                                icon = Icons.Default.Image,
                                title = "Episode thumbnails",
                                subtitle = "Show the preview image for each episode",
                                checked = showThumbnails,
                                onCheckedChange = { prefs.showEpisodeThumbnails().set(it) },
                            )
                            HorizontalDivider()
                            SwitchSettingsRow(
                                icon = Icons.Default.CalendarMonth,
                                title = "Episode dates",
                                subtitle = "Show the air date",
                                checked = showDates,
                                onCheckedChange = { prefs.showEpisodeDates().set(it) },
                            )
                            HorizontalDivider()
                            SwitchSettingsRow(
                                icon = Icons.Default.RecordVoiceOver,
                                title = "Audio pills",
                                subtitle = "Show SUB / DUB / HSUB tags",
                                checked = showAudioPills,
                                onCheckedChange = { prefs.showAudioPills().set(it) },
                            )
                            HorizontalDivider()
                            SwitchSettingsRow(
                                icon = Icons.Default.Download,
                                title = "Download button",
                                subtitle = "Show the download button on each episode card",
                                checked = showDownloadButton,
                                onCheckedChange = { prefs.showDownloadButton().set(it) },
                            )
                        }
                    }
                }

                // Layout positions — uses StyledSegmentedRow (matches details-page design language)
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsGroupCard(title = "Text content") {
                            LabeledSection("Title", "Where the episode title appears") {
                                StyledSegmentedRow(
                                    options = listOf("Right" to (titlePos == "right"), "Below" to (titlePos == "below")),
                                    onSelect = { prefs.titlePosition().set(if (it == 0) "right" else "below") },
                                )
                            }
                            HorizontalDivider()
                            LabeledSection("Synopsis", "Where the episode description appears") {
                                StyledSegmentedRow(
                                    options = listOf("Right" to (synopsisPos == "right"), "Below" to (synopsisPos == "below")),
                                    onSelect = { prefs.synopsisPosition().set(if (it == 0) "right" else "below") },
                                )
                            }
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsGroupCard(title = "Badges & pills") {
                            LabeledSection("Date & audio pills", "Where the date and SUB/DUB tags appear") {
                                StyledSegmentedRow(
                                    options = listOf(
                                        "Above" to (datePos == "right_above_synopsis"),
                                        "Below" to (datePos == "right_below_synopsis"),
                                        "Full" to (datePos == "below"),
                                    ),
                                    onSelect = { idx ->
                                        prefs.datePosition().set(when (idx) {
                                            0 -> "right_above_synopsis"
                                            1 -> "right_below_synopsis"
                                            else -> "below"
                                        })
                                    },
                                )
                            }
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsGroupCard(title = "Episode number") {
                            LabeledSection("Position", "Overlay on thumbnail or badge next to title") {
                                StyledSegmentedRow(
                                    options = listOf("Overlay" to (epNumPos == "overlay"), "Badge" to (epNumPos == "badge")),
                                    onSelect = { prefs.episodeNumberPosition().set(if (it == 0) "overlay" else "badge") },
                                )
                            }
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsGroupCard(title = "Thumbnail") {
                            LabeledSection("Side", "Left or right side of the episode card") {
                                StyledSegmentedRow(
                                    options = listOf("Left" to (thumbPos == "left"), "Right" to (thumbPos == "right")),
                                    onSelect = { prefs.thumbnailPosition().set(if (it == 0) "left" else "right") },
                                )
                            }
                            HorizontalDivider()
                            LabeledSection("Size", "Small, medium, or large") {
                                StyledSegmentedRow(
                                    options = listOf(
                                        "Small" to (thumbSize == "small"),
                                        "Medium" to (thumbSize == "medium"),
                                        "Large" to (thumbSize == "large"),
                                    ),
                                    onSelect = { idx ->
                                        prefs.thumbnailSize().set(when (idx) {
                                            0 -> "small"
                                            1 -> "medium"
                                            else -> "large"
                                        })
                                    },
                                )
                            }
                        }
                    }
                }

                // Download button placement — only shown when the download
                // button itself is enabled (mirrors LayoutSettingsScreen's
                // "Download button" section on the detail page).
                if (showDownloadButton) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            SettingsGroupCard(title = "Download button") {
                                LabeledSection("Placement", "Where the download button appears on episode rows") {
                                    StyledSegmentedRow(
                                        options = listOf(
                                            "Episode row" to (dlPlacement == "episode_row"),
                                            "Synopsis" to (dlPlacement == "synopsis"),
                                        ),
                                        onSelect = {
                                            prefs.downloadButtonPlacement().set(if (it == 0) "episode_row" else "synopsis")
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A labeled section with title, description, and content below.
 * Matches the details-page LayoutSettingsScreen design language.
 */
@Composable
private fun LabeledSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        content()
    }
}
