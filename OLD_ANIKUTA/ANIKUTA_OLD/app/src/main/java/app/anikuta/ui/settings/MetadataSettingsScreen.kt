package app.anikuta.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
 * Details → Metadata fetching subpage.
 *
 * Layout: sticky live preview at top + master toggle + conditional per-field toggles.
 * When the master toggle is off, the 3 per-field toggles are hidden.
 */
@Composable
fun MetadataSettingsScreen(onBack: () -> Unit) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val enableMetadataFetch by prefs.enableInAppMetadataFetch().stateIn(scope).collectAsState()
    val fetchThumbnails by prefs.fetchMetadataThumbnails().stateIn(scope).collectAsState()
    val fetchTitles by prefs.fetchMetadataTitles().stateIn(scope).collectAsState()
    val fetchSummaries by prefs.fetchMetadataSummaries().stateIn(scope).collectAsState()
    // Read only LAYOUT prefs for the live preview (display prefs are forced ON
    // so the preview always shows all elements, regardless of episode display settings)
    val synopsisPos by prefs.synopsisPosition().stateIn(scope).collectAsState()
    val datePos by prefs.datePosition().stateIn(scope).collectAsState()
    val thumbSize by prefs.thumbnailSize().stateIn(scope).collectAsState()
    val titlePos by prefs.titlePosition().stateIn(scope).collectAsState()
    val epNumPos by prefs.episodeNumberPosition().stateIn(scope).collectAsState()
    val thumbPos by prefs.thumbnailPosition().stateIn(scope).collectAsState()
    val dlPlacement by prefs.downloadButtonPlacement().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "Metadata fetching", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---- Sticky live preview at top ----
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
                    // Display toggles forced ON — the preview always shows all elements
                    // regardless of episode display settings. Only layout settings apply.
                    EpisodeRowPreview(
                        showThumbnails = true,
                        showSummaries = true,
                        showTitles = true,
                        showDates = true,
                        showEpisodeNumber = true,
                        showAudioPills = true,
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

            // ---- Scrollable settings ----
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Master toggle
                item {
                    SettingsGroupCard(title = "Fetching") {
                        SwitchSettingsRow(
                            icon = Icons.Default.AutoAwesome,
                            title = "Fetch episode metadata",
                            subtitle = "Automatically fetch missing episode info from external sources",
                            checked = enableMetadataFetch,
                            onCheckedChange = { prefs.enableInAppMetadataFetch().set(it) },
                        )
                    }
                }

                // Per-field toggles — only visible when master toggle is on
                item {
                    AnimatedVisibility(
                        visible = enableMetadataFetch,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        SettingsGroupCard(title = "What to fetch") {
                            SwitchSettingsRow(
                                icon = Icons.Default.Image,
                                title = "Thumbnails",
                                subtitle = "Fetch episode preview images",
                                checked = fetchThumbnails,
                                onCheckedChange = { prefs.fetchMetadataThumbnails().set(it) },
                            )
                            HorizontalDivider()
                            SwitchSettingsRow(
                                icon = Icons.Default.Title,
                                title = "Titles",
                                subtitle = "Fetch episode titles",
                                checked = fetchTitles,
                                onCheckedChange = { prefs.fetchMetadataTitles().set(it) },
                            )
                            HorizontalDivider()
                            SwitchSettingsRow(
                                icon = Icons.Default.Subtitles,
                                title = "Summaries",
                                subtitle = "Fetch episode descriptions",
                                checked = fetchSummaries,
                                onCheckedChange = { prefs.fetchMetadataSummaries().set(it) },
                            )
                        }
                    }
                }

                // Minimal note
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            text = "Metadata is fetched when you open an anime's detail page. Only fields missing from the extension are enriched.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}
