package app.confused.anikuta.feature.episodesettings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Title
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.confused.anikuta.core.episodemetadata.EpisodeMetadataPreferences
import org.koin.compose.koinInject

/**
 * The METADATA FETCHING settings screen — controls whether episode metadata is
 * fetched from external sources (Jikan/MAL, Anikage.cc, AniList Streaming) and
 * which fields are populated.
 *
 * Full page. Layout (NO live preview — metadata is about fetching, not display):
 * - "METADATA FETCHING" group:
 *   - Master toggle: "Fetch episode metadata" (Material3 [SwitchSettingsRow]).
 * - When master is ON, an "ANIMATE-IN" group of per-field toggles:
 *   - Thumbnails, Titles, Descriptions, Air dates.
 *
 * Per user requirement: uses proper Material3 Switch toggles, NOT custom on/off
 * pill buttons. The per-field toggles are hidden (collapsed via [AnimatedVisibility])
 * when the master toggle is off — mirroring the OLD ANIKUTA project.
 *
 * @param onBack Called when back is pressed.
 */
@Composable
fun EpisodeMetadataSettingsScreen(onBack: () -> Unit) {
    val prefs: EpisodeMetadataPreferences = koinInject()

    val enabled by prefs.enabled().changes().collectAsState(initial = prefs.enabled().get())
    val fetchThumbnails by prefs.fetchThumbnails().changes().collectAsState(initial = prefs.fetchThumbnails().get())
    val fetchTitles by prefs.fetchTitles().changes().collectAsState(initial = prefs.fetchTitles().get())
    val fetchSummaries by prefs.fetchSummaries().changes().collectAsState(initial = prefs.fetchSummaries().get())
    val fetchAirDates by prefs.fetchAirDates().changes().collectAsState(initial = prefs.fetchAirDates().get())

    SettingsSubpageScaffold(title = "Metadata fetching", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            // ── Master toggle ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsGroupCard(title = "Metadata fetching") {
                    SwitchSettingsRow(
                        icon = Icons.Filled.AutoAwesome,
                        title = "Fetch episode metadata",
                        subtitle = "Automatically fetch missing episode info from external sources (Jikan, Anikage, AniList)",
                        checked = enabled,
                        onCheckedChange = { prefs.enabled().set(it) },
                    )
                }
            }
            // ── Per-field toggles (only when master is on) ──
            item {
                AnimatedVisibility(visible = enabled) {
                    SettingsGroupCard(title = "Fetch fields") {
                        SwitchSettingsRow(
                            icon = Icons.Filled.Image,
                            title = "Thumbnails",
                            subtitle = "Fetch episode preview images",
                            checked = fetchThumbnails,
                            onCheckedChange = { prefs.fetchThumbnails().set(it) },
                        )
                        InGroupDivider()
                        SwitchSettingsRow(
                            icon = Icons.Filled.Title,
                            title = "Titles",
                            subtitle = "Fetch episode titles",
                            checked = fetchTitles,
                            onCheckedChange = { prefs.fetchTitles().set(it) },
                        )
                        InGroupDivider()
                        SwitchSettingsRow(
                            icon = Icons.Filled.Subtitles,
                            title = "Descriptions",
                            subtitle = "Fetch episode descriptions / synopses",
                            checked = fetchSummaries,
                            onCheckedChange = { prefs.fetchSummaries().set(it) },
                        )
                        InGroupDivider()
                        SwitchSettingsRow(
                            icon = Icons.Filled.CalendarMonth,
                            title = "Air dates",
                            subtitle = "Fetch episode air dates",
                            checked = fetchAirDates,
                            onCheckedChange = { prefs.fetchAirDates().set(it) },
                        )
                    }
                }
            }
        }
    }
}
