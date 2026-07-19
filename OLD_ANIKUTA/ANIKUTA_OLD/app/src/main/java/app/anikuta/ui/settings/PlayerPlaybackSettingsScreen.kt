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
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
 * Player → Playback subpage: speed, hardware decoding, audio language.
 */
@Composable
fun PlayerPlaybackSettingsScreen(onBack: () -> Unit) {
    val prefs = remember { Injekt.get<PlayerPreferences>() }
    val scope = rememberCoroutineScope()
    val speed by prefs.playerSpeed().stateIn(scope).collectAsState()
    val hwdec by prefs.tryHWDecoding().stateIn(scope).collectAsState()
    var audioLang by remember { mutableStateOf(prefs.preferredAudioLanguages().get()) }

    SettingsSubpageScaffold(title = "Playback", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsGroupCard(title = "Playback speed") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Speed: %.2fx".format(speed), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = speed,
                            onValueChange = { prefs.playerSpeed().set(it) },
                            valueRange = 0.25f..2.0f,
                            steps = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                SettingsGroupCard(title = "Decoding") {
                    SwitchSettingsRow(
                        icon = Icons.Default.Memory,
                        title = "Hardware decoding",
                        subtitle = "Use GPU for video decoding (recommended)",
                        checked = hwdec,
                        onCheckedChange = { prefs.tryHWDecoding().set(it) },
                    )
                }
            }
            item {
                SettingsGroupCard(title = "Audio") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Preferred audio languages", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Comma-separated, e.g. jpn,eng", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = audioLang,
                            onValueChange = { audioLang = it; prefs.preferredAudioLanguages().set(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("jpn,eng") },
                        )
                    }
                }
            }
        }
    }
}
