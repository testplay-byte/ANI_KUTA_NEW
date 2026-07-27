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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Player → Subtitles subpage: default mode + preferred language + appearance.
 *
 * This is a full-screen settings page for ALL subtitle-related settings:
 *  - Default subtitle mode (Off/On/Auto) — uses SelectableOptionCard
 *  - Preferred subtitle language
 *  - Subtitle appearance (font, size, colors, position, delay) — the
 *    SubtitleSettingsPanel (same as the in-player sheet)
 *
 * NOTE: SubtitleSettingsPanel already has its own internal verticalScroll.
 * We must NOT wrap it in another scrollable container — a plain Column with
 * fillMaxSize is sufficient; the panel handles its own scrolling.
 */
@Composable
fun SubtitleSettingsScreen(
    onBack: () -> Unit,
) {
    val prefs = remember { Injekt.get<PlayerPreferences>() }
    val scope = rememberCoroutineScope()
    val subtitleMode by prefs.defaultSubtitleMode().stateIn(scope).collectAsState()
    var subtitleLang by remember { mutableStateOf(prefs.preferredSubtitleLanguage().get()) }

    SettingsSubpageScaffold(title = "Subtitles", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Default subtitle mode — custom card selector
            item {
                SettingsGroupCard(title = "Default subtitle mode") {
                    SelectableOptionCard(
                        title = "What to do when an episode starts playing",
                        options = listOf(
                            Triple("off", "Off", "Never show subtitles"),
                            Triple("on", "On", "Always show the first available track"),
                            Triple("auto", "Auto", "Only show if a track matches the preferred language"),
                        ),
                        selectedValue = subtitleMode,
                        onSelect = { prefs.defaultSubtitleMode().set(it) },
                    )
                }
            }
            // Preferred subtitle language
            item {
                SettingsGroupCard(title = "Preferred subtitle language") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Language code(s)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Used by Auto mode, and as a tiebreaker for On", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = subtitleLang,
                            onValueChange = { subtitleLang = it; prefs.preferredSubtitleLanguage().set(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("en,eng") },
                        )
                    }
                }
            }
            // Subtitle appearance — the panel (has its own scroll)
            item {
                SettingsGroupCard(title = "Appearance") {
                    Column(Modifier.padding(16.dp)) {
                        app.anikuta.player.controls.SubtitleSettingsPanel(
                            onSettingsChanged = {},
                        )
                    }
                }
            }
        }
    }
}
