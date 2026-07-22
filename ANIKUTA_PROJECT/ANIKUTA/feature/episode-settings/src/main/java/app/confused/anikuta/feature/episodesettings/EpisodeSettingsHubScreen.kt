package app.confused.anikuta.feature.episodesettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The Episode Settings HUB screen.
 *
 * Full page (NOT a bottom sheet, per user requirement). Layout:
 * - Sticky [EpisodeRowPreview] at the top (non-scrolling).
 * - Scrollable "CUSTOMIZE" group below with 3 clickable rows:
 *   1. Episode display  → [EpisodeDisplaySettingsScreen]
 *   2. Episode layout   → [EpisodeLayoutSettingsScreen]
 *   3. Metadata fetching → [EpisodeMetadataSettingsScreen]
 *
 * @param onBack Called when the back arrow (or system back) is pressed.
 * @param onOpenDisplay Navigate to the Display sub-page.
 * @param onOpenLayout Navigate to the Layout sub-page.
 * @param onOpenMetadata Navigate to the Metadata sub-page.
 */
@Composable
fun EpisodeSettingsHubScreen(
    onBack: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenLayout: () -> Unit,
    onOpenMetadata: () -> Unit,
) {
    val displayPrefs = rememberEpisodeDisplayPrefs()

    SettingsSubpageScaffold(
        title = "Episode settings",
        onBack = onBack,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Sticky live preview (non-scrolling, stays at top) ──
            EpisodeRowPreview(prefs = displayPrefs)
            Spacer(modifier = Modifier.height(16.dp))
            // ── Scrollable options below ──
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                item {
                    SettingsGroupCard(title = "Customize") {
                        ClickableSettingsRow(
                            icon = Icons.Filled.ViewAgenda,
                            title = "Episode display",
                            subtitle = "Show or hide episode numbers, titles, summaries, thumbnails, dates, and audio pills",
                            onClick = onOpenDisplay,
                        )
                        InGroupDivider()
                        ClickableSettingsRow(
                            icon = Icons.Filled.Tune,
                            title = "Episode layout",
                            subtitle = "Positions for title, synopsis, date, episode number, and thumbnail",
                            onClick = onOpenLayout,
                        )
                        InGroupDivider()
                        ClickableSettingsRow(
                            icon = Icons.Filled.AutoAwesome,
                            title = "Metadata fetching",
                            subtitle = "Fetch episode thumbnails, titles, descriptions, and air dates from external sources",
                            onClick = onOpenMetadata,
                        )
                    }
                }
            }
        }
    }
}
