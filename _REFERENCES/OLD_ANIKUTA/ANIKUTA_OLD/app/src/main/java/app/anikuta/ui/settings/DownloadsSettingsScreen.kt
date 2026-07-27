package app.anikuta.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import app.anikuta.download.AudioFallback
import app.anikuta.download.PriorityMode
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 7 — Downloads settings screen with drag-and-drop priority lists.
 *
 * 3 reorderable lists (quality, audio, server) + quality-vs-audio priority
 * toggle + audio fallback radio + WiFi/delete toggles.
 */
@Composable
fun DownloadsSettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    val viewModel: DownloadsViewModel = viewModel()
    val qualityOrder by viewModel.qualityOrder.collectAsState()
    val audioOrder by viewModel.audioOrder.collectAsState()
    val serverOrder by viewModel.serverOrder.collectAsState()
    val priorityMode by viewModel.priorityMode.collectAsState()
    val audioFallback by viewModel.audioFallback.collectAsState()

    SettingsSubpageScaffold(title = "Downloads", onBack = onBack) {
        // Use a scrollable Column (NOT LazyColumn) so the inner ReorderableLazyColumn
        // doesn't get infinite-height constraints. Nested LazyColumns crash; a
        // scrollable Column wrapping a LazyColumn is fine because the Column
        // provides bounded height.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Quality priority (drag-and-drop) ----
            SettingsGroupCard(title = "Preferred quality (drag to reorder)") {
                if (qualityOrder.isEmpty()) {
                    Text(
                        "No qualities set. Default: 1080p → 720p → 360p",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    ReorderableStringList(
                        items = qualityOrder,
                        onReorder = { from, to -> viewModel.reorderQuality(from, to) },
                    )
                }
            }

            // ---- Audio priority (drag-and-drop) ----
            SettingsGroupCard(title = "Preferred audio (drag to reorder)") {
                if (audioOrder.isEmpty()) {
                    Text(
                        "No audio versions set. Default: Sub → Dub",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    ReorderableStringList(
                        items = audioOrder,
                        onReorder = { from, to -> viewModel.reorderAudio(from, to) },
                    )
                }
            }

            // ---- Quality vs Audio priority ----
            SettingsGroupCard(title = "Priority mode") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    PriorityRadioButton(
                        label = "Quality first",
                        subtitle = "Match quality, then audio version",
                        selected = priorityMode == PriorityMode.QUALITY_FIRST,
                        onClick = { viewModel.setPriorityMode(PriorityMode.QUALITY_FIRST) },
                    )
                    PriorityRadioButton(
                        label = "Audio first",
                        subtitle = "Match audio version, then quality",
                        selected = priorityMode == PriorityMode.AUDIO_FIRST,
                        onClick = { viewModel.setPriorityMode(PriorityMode.AUDIO_FIRST) },
                    )
                }
            }

            // ---- Audio fallback mode ----
            SettingsGroupCard(title = "Audio not available") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    PriorityRadioButton(
                        label = "Download next available audio",
                        subtitle = "Fall back to the next audio version in your priority list",
                        selected = audioFallback == AudioFallback.NEXT,
                        onClick = { viewModel.setAudioFallback(AudioFallback.NEXT) },
                    )
                    PriorityRadioButton(
                        label = "Show error (don't download)",
                        subtitle = "Skip this episode if the preferred audio isn't available",
                        selected = audioFallback == AudioFallback.FAIL,
                        onClick = { viewModel.setAudioFallback(AudioFallback.FAIL) },
                    )
                }
            }

            // ---- Server priority (drag-and-drop) ----
            SettingsGroupCard(title = "Preferred servers (drag to reorder)") {
                if (serverOrder.isEmpty()) {
                    Text(
                        "No server priority set. Auto-picks the first available server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    ReorderableStringList(
                        items = serverOrder,
                        onReorder = { from, to -> viewModel.reorderServer(from, to) },
                    )
                }
            }

            // ---- Download method ----
            SettingsGroupCard(title = "Download method") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val method by viewModel.downloadMethod.collectAsState()
                    Text(
                        text = "Choose how episodes are downloaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    StyledSegmentedRow(
                        options = listOf(
                            "Single-pass" to (method == "single_pass"),
                            "HLS direct" to (method == "hls_direct"),
                        ),
                        onSelect = {
                            viewModel.setDownloadMethod(if (it == 0) "single_pass" else "hls_direct")
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (method) {
                            "hls_direct" -> "Downloads .ts segments individually via HTTP. Supports resume + precise progress. May not work with all sources."
                            else -> "Aniyomi's approach. Correct size & duration. Progress is estimated. No resume after failure."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- Toggles ----
            SettingsGroupCard(title = "General") {
                Column {
                    val wifiOnly by viewModel.downloadOverWifiOnly.collectAsState()
                    ToggleRow(
                        label = "Download over WiFi only",
                        checked = wifiOnly,
                        onCheckedChange = { viewModel.setDownloadOverWifiOnly(it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val deleteAfter by viewModel.deleteAfterWatching.collectAsState()
                    ToggleRow(
                        label = "Delete after watching",
                        checked = deleteAfter,
                        onCheckedChange = { viewModel.setDeleteAfterWatching(it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val showSize by viewModel.showDownloadSize.collectAsState()
                    ToggleRow(
                        label = "Show download size",
                        checked = showSize,
                        onCheckedChange = { viewModel.setShowDownloadSize(it) },
                    )
                }
            }

            // ---- Auto-download (new releases) ----
            AutoDownloadSection()

            // ---- Tracked anime (per-anime auto-download config) ----
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigate("settings/downloads/tracked") },
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
                        Text("Per-anime auto-download settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Auto-download section — new releases + watch-flow.
 * Lives in Downloads settings (not Notifications) because it's a download feature.
 *
 * Uses produceState (not collectAsState) for reactive state — proven to work
 * with SharedPreferences-backed Preference flows.
 */
@Composable
private fun AutoDownloadSection() {
    val prefs: app.anikuta.notification.NotificationPreferences = remember { uy.kohesive.injekt.Injekt.get<app.anikuta.notification.NotificationPreferences>() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Use produceState (proven pattern) — collectAsState with cold Flow was buggy
    val autoDlEnabled by produceState(initialValue = prefs.globalAutoDownloadEnabled().get(), prefs) {
        prefs.globalAutoDownloadEnabled().changes().collect { value = it }
    }
    val autoDlAudio by produceState(initialValue = prefs.globalAutoDownloadAudio().get(), prefs) {
        prefs.globalAutoDownloadAudio().changes().collect { value = it }
    }
    val autoDlQuality by produceState(initialValue = prefs.globalAutoDownloadQuality().get(), prefs) {
        prefs.globalAutoDownloadQuality().changes().collect { value = it }
    }
    val watchFlowEnabled by produceState(initialValue = prefs.watchFlowAutoDownloadEnabled().get(), prefs) {
        prefs.watchFlowAutoDownloadEnabled().changes().collect { value = it }
    }

    var showAutoDlDialog by remember { mutableStateOf(false) }

    SettingsGroupCard(title = "Auto-download new releases") {
        ToggleRow(
            label = "Auto-download new episodes",
            checked = autoDlEnabled,
            onCheckedChange = { newValue ->
                if (newValue) showAutoDlDialog = true
                else prefs.globalAutoDownloadEnabled().set(false)
            },
        )
        if (autoDlEnabled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Which version to download (Sub / Dub / Any) — single radio selector
            Column(Modifier.padding(16.dp)) {
                Text("Download which version?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("Choose which audio version to auto-download when a new episode releases",
                     style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                listOf(
                    "SUB" to "Sub only" to "Download only subbed episodes",
                    "DUB" to "Dub only" to "Download only dubbed episodes",
                    "ANY" to "Any available" to "Download whichever version is available",
                ).forEach { (pair, desc) ->
                    val (value, label) = pair
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { prefs.globalAutoDownloadAudio().set(value) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = autoDlAudio == value, onClick = { prefs.globalAutoDownloadAudio().set(value) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Quality selector
            Column(Modifier.padding(16.dp)) {
                Text("Preferred quality", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                listOf("best" to "Best available", "1080" to "1080p", "720" to "720p", "360" to "360p").forEach { (q, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { prefs.globalAutoDownloadQuality().set(q) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = autoDlQuality == q, onClick = { prefs.globalAutoDownloadQuality().set(q) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    SettingsGroupCard(title = "Watch-flow auto-download") {
        ToggleRow(
            label = "Auto-download next episode while watching",
            checked = watchFlowEnabled,
            onCheckedChange = { prefs.watchFlowAutoDownloadEnabled().set(it) },
        )
    }

    if (showAutoDlDialog) {
        AlertDialog(
            onDismissRequest = { showAutoDlDialog = false },
            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
            title = { Text("Enable auto-download?") },
            text = {
                Text("When enabled, all anime in your library that have new episodes released " +
                     "will be automatically downloaded in the background. " +
                     "You can configure which version (sub, dub, or any) and quality below.")
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.globalAutoDownloadEnabled().set(true)
                    showAutoDlDialog = false
                    Toast.makeText(context, "Auto-download enabled", Toast.LENGTH_SHORT).show()
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showAutoDlDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * A drag-and-drop reorderable list of strings. Uses [sh.calvin.reorderable].
 *
 * Uses a LazyColumn with a calculated height (items × rowHeight) because a
 * LazyColumn inside a verticalScroll Column needs bounded height constraints.
 * The items are few (3-5), so the height is small.
 */
@Composable
private fun ReorderableStringList(
    items: List<String>,
    onReorder: (from: Int, to: Int) -> Unit,
) {
    val rowHeight = 48.dp
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onReorder(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight * items.size),
    ) {
        items(items, key = { it }) { item ->
            ReorderableItem(reorderableState, key = item) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggableHandle()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityRadioButton(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.size(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

