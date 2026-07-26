package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Player settings HUB page — contains ONLY links to dedicated subpages.
 * No inline settings here; each category has its own screen.
 *
 * Subpages:
 *   - General          → default view, quality sheet display
 *   - Playback         → speed, hardware decoding, audio language
 *   - Subtitles        → default mode, preferred language, appearance
 *   - Display & Behavior → top bar, auto-hide, gestures, PiP, skip
 *   - Episode list     → episode display settings
 *
 * Storage is NOT here — it's in the app-level Data & Storage settings.
 */
@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit,
    onOpenEpisodeDisplay: () -> Unit = {},
    onOpenSubtitleSettings: () -> Unit = {},
    onOpenGeneral: () -> Unit = {},
    onOpenPlayback: () -> Unit = {},
    onOpenDisplayBehavior: () -> Unit = {},
) {
    SettingsSubpageScaffold(title = "Player", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsGroupCard(title = "Categories") {
                    ClickableSettingsRow(
                        icon = Icons.Default.AspectRatio,
                        title = "General",
                        subtitle = "Default view, quality sheet display",
                        onClick = onOpenGeneral,
                    )
                    androidx.compose.material3.HorizontalDivider()
                    ClickableSettingsRow(
                        icon = Icons.Default.PlayCircle,
                        title = "Playback",
                        subtitle = "Speed, hardware decoding, audio language",
                        onClick = onOpenPlayback,
                    )
                    androidx.compose.material3.HorizontalDivider()
                    ClickableSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = "Subtitles",
                        subtitle = "Default mode, language, appearance",
                        onClick = onOpenSubtitleSettings,
                    )
                    androidx.compose.material3.HorizontalDivider()
                    ClickableSettingsRow(
                        icon = Icons.Default.DisplaySettings,
                        title = "Display & Behavior",
                        subtitle = "Top bar, auto-hide, gestures, PiP, skip",
                        onClick = onOpenDisplayBehavior,
                    )
                    androidx.compose.material3.HorizontalDivider()
                    ClickableSettingsRow(
                        icon = Icons.Default.ViewAgenda,
                        title = "Episode list",
                        subtitle = "Customize how episodes appear in the player",
                        onClick = onOpenEpisodeDisplay,
                    )
                }
            }
        }
    }
}
