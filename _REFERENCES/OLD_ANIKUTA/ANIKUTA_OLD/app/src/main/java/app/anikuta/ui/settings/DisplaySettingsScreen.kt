package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.VisibilityOff
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
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Details → Episode display subpage.
 *
 * Layout: sticky live preview at top (non-scrolling) + scrollable toggles below.
 */
@Composable
fun DisplaySettingsScreen(onBack: () -> Unit) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
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
    val watchedAppearance by prefs.watchedEpisodeAppearance().stateIn(scope).collectAsState()
    val watchedBlurRadius by prefs.watchedEpisodeBlurRadius().stateIn(scope).collectAsState()
    val watchedAlpha by prefs.watchedEpisodeAlpha().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "Episode display", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---- Sticky live preview (non-scrolling) ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
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
                    )
                }
            }

            // ---- Scrollable toggles ----
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        }
                    }
                }

                // ---- Watched episode appearance ----
                item {
                    SettingsGroupCard(title = "Watched episode appearance") {
                        SelectableOptionCard(
                            title = "Visual treatment",
                            subtitle = "How episodes you've already watched appear in the list",
                            options = listOf(
                                Triple("none", "None", "Watched episodes look the same as unwatched"),
                                Triple("grayscale", "Grayscale", "Black & white — desaturate the entire card"),
                                Triple("blur", "Blur", "Slightly blur the entire card"),
                                Triple("both", "Grayscale + Blur", "Maximum visual distinction"),
                            ),
                            selectedValue = watchedAppearance,
                            onSelect = { prefs.watchedEpisodeAppearance().set(it) },
                        )
                        // Blur radius slider — only shown when blur is enabled
                        if (watchedAppearance == "blur" || watchedAppearance == "both") {
                            HorizontalDivider()
                            SliderSettingsRow(
                                icon = Icons.Default.BlurOn,
                                title = "Blur strength",
                                subtitle = "Higher = more blurry (may be harder to read)",
                                value = watchedBlurRadius,
                                valueRange = 0.5f..8f,
                                valueFormatter = { "${it}x" },
                                onValueChange = { prefs.watchedEpisodeBlurRadius().set(it) },
                            )
                        }
                        // Alpha slider — only shown when grayscale is enabled
                        if (watchedAppearance == "grayscale" || watchedAppearance == "both") {
                            HorizontalDivider()
                            SliderSettingsRow(
                                icon = Icons.Default.VisibilityOff,
                                title = "Dim amount",
                                subtitle = "Lower = more transparent/faded",
                                value = watchedAlpha,
                                valueRange = 0.2f..1f,
                                valueFormatter = { "${(it * 100).toInt()}%" },
                                onValueChange = { prefs.watchedEpisodeAlpha().set(it) },
                            )
                        }
                    }
                }
            }
        }
    }
}
