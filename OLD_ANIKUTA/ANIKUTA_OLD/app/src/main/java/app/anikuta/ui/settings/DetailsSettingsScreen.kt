package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
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
 * Phase 7.5+ — Details settings hub page.
 *
 * Redesigned from a single cluttered page into a modular hub:
 *  - Live preview at the top (bare episode card, reflects all settings)
 *  - Three subpage links: Display, Layout, Metadata
 *
 * This keeps the page clean and organized. Each subpage handles one
 * category of settings, making it easy to find and adjust options.
 */
@Composable
fun DetailsSettingsScreen(
    onBack: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenLayout: () -> Unit,
    onOpenMetadata: () -> Unit,
) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()

    // Collect all prefs for the live preview
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
    // Read download button placement so the live preview accounts for it
    val dlPlacement by prefs.downloadButtonPlacement().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "Details", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Live preview ----
            // Bare episode card in 16dp horizontal padding — exactly like the
            // detail page's episode list context. NOT wrapped in SettingsGroupCard
            // so the padding and card structure match the real detail page.
            item {
                Column {
                    // Section label
                    Text(
                        "LIVE PREVIEW",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    // The preview card — 16dp horizontal padding to match the detail page
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
            }

            // ---- Subpage links ----
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "CUSTOMIZE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    SettingsGroupCard(title = "Settings") {
                        ClickableSettingsRow(
                            icon = Icons.Default.ViewAgenda,
                            title = "Episode display",
                            subtitle = "Show or hide episode numbers, titles, summaries, thumbnails, dates, and audio pills",
                            onClick = onOpenDisplay,
                        )
                        androidx.compose.material3.HorizontalDivider()
                        ClickableSettingsRow(
                            icon = Icons.Default.Tune,
                            title = "Episode layout",
                            subtitle = "Positions for title, synopsis, date, episode number, thumbnail, and anime info",
                            onClick = onOpenLayout,
                        )
                        androidx.compose.material3.HorizontalDivider()
                        ClickableSettingsRow(
                            icon = Icons.Default.AutoAwesome,
                            title = "Metadata fetching",
                            subtitle = "Fetch episode thumbnails, titles, and descriptions from external sources",
                            onClick = onOpenMetadata,
                        )
                    }
                }
            }

            // ---- Dynamic theming toggle ----
            item {
                val dynamicTheming by prefs.dynamicDetailTheming().stateIn(scope).collectAsState()
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsGroupCard(title = "Appearance") {
                        SwitchSettingsRow(
                            icon = Icons.Default.Palette,
                            title = "Dynamic theming",
                            subtitle = "Color the detail page based on the anime's cover image",
                            checked = dynamicTheming,
                            onCheckedChange = { prefs.dynamicDetailTheming().set(it) },
                        )
                    }
                }
            }
        }
    }
}
