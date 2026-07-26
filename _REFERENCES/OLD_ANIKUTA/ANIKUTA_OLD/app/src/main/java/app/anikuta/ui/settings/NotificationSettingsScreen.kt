package app.anikuta.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.core.preference.Preference
import app.anikuta.notification.NotificationPreferences
import kotlinx.coroutines.flow.collectLatest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Notification Settings — ONE clean page (not a hub of sub-screens).
 *
 * Everything notification-related fits on a single scrollable page:
 *  - Enable notifications (with confirmation dialog)
 *  - Notify mode (modern card selector)
 *  - Sub/Dub notification toggles
 *  - Quiet hours
 *  - Check completed anime
 *
 * Plus one card → Tracked Anime (per-anime notification config subpage).
 *
 * Auto-download is NOT here — it lives in Downloads settings now.
 * Battery optimization is NOT here — it lives in General settings now.
 */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val prefs: NotificationPreferences = remember { Injekt.get() }
    val context = LocalContext.current

    val enabled by prefs.globalNotifyEnabled().stateInAsState()
    val mode by prefs.notifyMode().stateInAsState()
    val checkCompleted by prefs.checkCompletedAnime().stateInAsState()
    val notifySub by prefs.globalNotifySub().stateInAsState()
    val notifyDub by prefs.globalNotifyDub().stateInAsState()
    val quietEnabled by prefs.quietHoursEnabled().stateInAsState()
    val quietStart by prefs.quietHoursStart().stateInAsState()
    val quietEnd by prefs.quietHoursEnd().stateInAsState()

    var showEnableDialog by remember { mutableStateOf(false) }

    SettingsSubpageScaffold(title = "Notifications", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- Enable + notify mode ----
            item {
                SettingsGroupCard(title = "General") {
                    SwitchSettingsRow(
                        icon = Icons.Default.Notifications,
                        title = "Enable notifications",
                        subtitle = "Get notified when new episodes are available",
                        checked = enabled,
                        onCheckedChange = { newValue ->
                            if (newValue) showEnableDialog = true
                            else prefs.globalNotifyEnabled().set(false)
                        },
                    )
                    HorizontalDivider()
                    ModernNotifyModeSelector(currentMode = mode) { prefs.notifyMode().set(it) }
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Check,
                        title = "Check completed anime",
                        subtitle = "Continue checking finished anime (for dub episodes that release later)",
                        checked = checkCompleted,
                        onCheckedChange = { prefs.checkCompletedAnime().set(it) },
                    )
                }
            }

            // ---- Sub / Dub (combined — not separate pages) ----
            item {
                SettingsGroupCard(title = "Sub / Dub") {
                    SwitchSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = "Notify on new SUB episodes",
                        subtitle = "Get notified when a new subbed episode is available",
                        checked = notifySub,
                        onCheckedChange = { prefs.globalNotifySub().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.GraphicEq,
                        title = "Notify on new DUB episodes",
                        subtitle = "Get notified when a new dubbed episode is available. Dub episodes are often released later than sub.",
                        checked = notifyDub,
                        onCheckedChange = { prefs.globalNotifyDub().set(it) },
                    )
                }
            }

            // ---- Quiet hours ----
            item {
                SettingsGroupCard(title = "Quiet hours") {
                    SwitchSettingsRow(
                        icon = Icons.Default.Bedtime,
                        title = "Enable quiet hours",
                        subtitle = "Silence notifications during set hours",
                        checked = quietEnabled,
                        onCheckedChange = { prefs.quietHoursEnabled().set(it) },
                    )
                    if (quietEnabled) {
                        HorizontalDivider()
                        TimeSelectorRow("Start", quietStart) { prefs.quietHoursStart().set(it) }
                        HorizontalDivider()
                        TimeSelectorRow("End", quietEnd) { prefs.quietHoursEnd().set(it) }
                    }
                }
            }

            // ---- Tracked anime (card → subpage) ----
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onNavigate("settings/notifications/tracked") },
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tracked Anime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text("Per-anime notification settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Confirmation dialog when enabling notifications
    if (showEnableDialog) {
        AlertDialog(
            onDismissRequest = { showEnableDialog = false },
            icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
            title = { Text("Enable notifications?") },
            text = {
                Text("When enabled, you'll receive notifications when new episodes of your library anime are released. " +
                     "You can configure which anime to track and whether to notify on sub, dub, or both.")
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.globalNotifyEnabled().set(true)
                    showEnableDialog = false
                    Toast.makeText(context, "Notifications enabled", Toast.LENGTH_SHORT).show()
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showEnableDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// =========================================================================
// Modern notify mode selector (card-based)
// =========================================================================

@Composable
private fun ModernNotifyModeSelector(currentMode: String, onModeChange: (String) -> Unit) {
    val modes = listOf(
        Triple("extension", "When watchable", "Notify only when the episode is actually available on the extension"),
        Triple("anilist", "When aired", "Notify at the AniList airing time (may not be watchable yet)"),
        Triple("both", "Both", "Notify at airing time AND when watchable"),
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Notify mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text("When to send new-episode notifications", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        modes.forEach { (value, title, desc) ->
            val isSelected = currentMode == value
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onModeChange(value) },
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = { onModeChange(value) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = if (isSelected) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

// =========================================================================
// Shared helpers
// =========================================================================

@Composable
private fun TimeSelectorRow(label: String, hour: Int, onHourChange: (Int) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.weight(1f))
        Text(formatHour(hour), style = MaterialTheme.typography.bodyLarge)
    }
    if (showDialog) {
        var selectedHour by remember { mutableStateOf(hour) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Select hour") },
            text = {
                Column {
                    Text("Hour: $selectedHour (${formatHour(selectedHour)})")
                    Slider(value = selectedHour.toFloat(), onValueChange = { selectedHour = it.toInt() }, valueRange = 0f..23f, steps = 22)
                }
            },
            confirmButton = { TextButton(onClick = { onHourChange(selectedHour); showDialog = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
        )
    }
}

private fun formatHour(hour: Int): String {
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
    return "$displayHour:00 $amPm"
}

@Composable
private fun <T> Preference<T>.stateInAsState(): State<T> {
    return produceState(initialValue = this.get(), this) {
        this@stateInAsState.changes().collectLatest { value = it }
    }
}
