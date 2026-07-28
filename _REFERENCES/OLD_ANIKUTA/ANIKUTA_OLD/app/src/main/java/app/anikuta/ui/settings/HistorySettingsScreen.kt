package app.anikuta.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.core.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 6 — History settings subpage.
 *
 * Settings for the History page:
 *   - Watch threshold (the percentage at which an episode is marked "watched")
 *   - Retention (keep forever / clear after N days)
 *   - Clear all history
 *
 * The watch threshold is stored in PreferenceStore key `watch_threshold`
 * (default 0.85 = 85%). The player reads this to decide when to mark an
 * episode as seen.
 *
 * Related files:
 *   - HistoryViewModel.kt — the history state
 *   - PlayerActivity.kt saveProgress() — writes progress
 *   - WatchProgressStore.kt — the data store
 */
@Composable
fun HistorySettingsScreen(onBack: () -> Unit) {
    val preferenceStore: PreferenceStore? = try { Injekt.get() } catch (e: Exception) { null }
    val thresholdPref = preferenceStore?.getFloat("watch_threshold", 0.85f)
    var threshold by remember { mutableFloatStateOf(thresholdPref?.get() ?: 0.85f) }
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all history?") },
            text = { Text("This removes all watch progress. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val store = Injekt.get<app.anikuta.player.WatchProgressStore>()
                        store.deleteAll()
                    } catch (_: Exception) {}
                    showClearDialog = false
                }) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }

    SettingsSubpageScaffold(title = "History", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsGroupCard(title = "Watch threshold") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "Mark episode as watched at ${(threshold * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "An episode is marked 'watched' when playback reaches " +
                                "this percentage. Default is 85%.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Slider(
                            value = threshold,
                            onValueChange = {
                                threshold = it
                                thresholdPref?.set(it)
                            },
                            valueRange = 0.5f..1f,
                            steps = 9, // 50%, 55%, ..., 100%
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            item {
                SettingsGroupCard(title = "Retention") {
                    ClickableSettingsRow(
                        icon = Icons.Default.History,
                        title = "Keep history forever",
                        subtitle = "Watch progress is never automatically deleted",
                        onClick = { /* documented in-line */ },
                    )
                }
            }
            item {
                SettingsGroupCard(title = "Data") {
                    ClickableSettingsRow(
                        icon = Icons.Default.Delete,
                        title = "Clear all history",
                        subtitle = "Remove all watch progress",
                        onClick = { showClearDialog = true },
                    )
                }
            }
        }
    }
}
