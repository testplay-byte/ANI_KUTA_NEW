package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Player → General subpage: default view + quality sheet display.
 * Uses SelectableOptionCard (custom card-style selector) for both.
 */
@Composable
fun PlayerGeneralSettingsScreen(onBack: () -> Unit) {
    val prefs = remember { Injekt.get<PlayerPreferences>() }
    val scope = rememberCoroutineScope()
    val defaultView by prefs.defaultPlayerView().stateIn(scope).collectAsState()
    val qualityDisplayMode by prefs.qualitySheetDisplayMode().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "General", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsGroupCard(title = "Default view") {
                    SelectableOptionCard(
                        title = "Which mode to open the player in",
                        options = listOf(
                            Triple("minimized", "Minimized", "Video at top, episode list below"),
                            Triple("fullscreen", "Fullscreen", "Video fills the screen"),
                            Triple("ask", "Ask", "Prompt each time"),
                        ),
                        selectedValue = defaultView,
                        onSelect = { prefs.defaultPlayerView().set(it) },
                    )
                }
            }
            item {
                SettingsGroupCard(title = "Quality sheet display") {
                    SelectableOptionCard(
                        title = "Choose what qualities to show",
                        options = listOf(
                            Triple("current", "Current only", "Only qualities for the current server + audio"),
                            Triple("all", "Show all", "All qualities from all servers, in sections"),
                        ),
                        selectedValue = qualityDisplayMode,
                        onSelect = { prefs.qualitySheetDisplayMode().set(it) },
                    )
                }
            }
        }
    }
}
