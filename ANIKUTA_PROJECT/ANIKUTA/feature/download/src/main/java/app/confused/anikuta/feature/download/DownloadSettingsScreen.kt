package app.confused.anikuta.feature.download

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.download.DownloadPreferences
import app.confused.anikuta.core.download.FallbackStrategy
import org.koin.compose.koinInject

/**
 * The full-page Download settings screen (replaces the bottom sheet per the
 * owner's request: "the settings page should open up on a completely new page,
 * not a bottom-up menu").
 *
 * **Sections:**
 * 1. **General** — folder, Wi-Fi-only, concurrent downloads, show-download-button.
 * 2. **Auto-download** — toggle: when ON, the app auto-picks the best
 *    server/audio/quality based on the preference lists. When OFF, tapping
 *    download shows the video picker sheet.
 * 3. **Quality preferences** — drag-and-drop reorderable list. The top
 *    quality is tried first; if unavailable, the next, etc. + fallback strategy.
 * 4. **Audio preferences** — drag-and-drop reorderable list + fallback.
 * 5. **Server preferences** — per-extension: shows each trusted extension's
 *    sources + their discovered servers. Drag-and-drop to reorder per source.
 *
 * **Design:** CollapsingHeader, surfaceVariant 0.4f cards, RobotoFamily,
 * #B1F256 accents. Follows the design language (principle #10: settings divided
 * into sections).
 */
@Composable
fun DownloadSettingsScreen(
    onBack: () -> Unit,
    extensionSources: List<ExtensionSourceInfo> = emptyList(),
    preferences: DownloadPreferences = koinInject(),
    serverDiscovery: app.confused.anikuta.core.download.ServerDiscoveryStore = koinInject(),
) {
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemIndex > 0 ||
        lazyListState.firstVisibleItemScrollOffset > 20

    // Reactive pref reads
    val folderUri by preferences.downloadFolderUri().changes()
        .collectAsState(initial = preferences.downloadFolderUri().get())
    val wifiOnly by preferences.wifiOnly().changes()
        .collectAsState(initial = preferences.wifiOnly().get())
    val concurrent by preferences.concurrentDownloads().changes()
        .collectAsState(initial = preferences.concurrentDownloads().get())
    val showButton by preferences.showDownloadButton().changes()
        .collectAsState(initial = preferences.showDownloadButton().get())
    val autoDownload by preferences.autoDownload().changes()
        .collectAsState(initial = preferences.autoDownload().get())
    val qualityPrefs by preferences.qualityPreferences().changes()
        .collectAsState(initial = preferences.qualityPreferences().get())
    val audioPrefs by preferences.audioPreferences().changes()
        .collectAsState(initial = preferences.audioPreferences().get())
    val qualityFallback by preferences.qualityFallback().changes()
        .collectAsState(initial = preferences.qualityFallback().get())
    val audioFallback by preferences.audioFallback().changes()
        .collectAsState(initial = preferences.audioFallback().get())

    val context = androidx.compose.ui.platform.LocalContext.current
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                preferences.downloadFolderUri().set(uri.toString())
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                // Non-fatal — the pref won't update.
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(
            title = "Download settings",
            collapsed = collapsed,
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
        ) {
            // ── General ──
            item {
                SectionLabel("General")
                val folderName = if (folderUri.isNotBlank()) {
                    app.confused.anikuta.core.download.DownloadStorageProvider
                        .folderDisplayName(folderUri)
                } else null
                SettingsRow(
                    title = "Download folder",
                    subtitle = if (folderName != null) "Folder: $folderName" else "Not set — tap to choose",
                    onClick = { folderLauncher.launch(null) },
                )
                ToggleRow(
                    title = "Wi-Fi only",
                    subtitle = "Pause downloads on mobile data",
                    checked = wifiOnly,
                    onCheckedChange = { preferences.wifiOnly().set(it) },
                )
                SettingsRow(
                    title = "Concurrent downloads",
                    subtitle = "${concurrent.coerceIn(1, 5)} at a time",
                    onClick = { preferences.concurrentDownloads().set(((concurrent % 5) + 1).coerceIn(1, 5)) },
                )
                ToggleRow(
                    title = "Show download button",
                    subtitle = "Display the download icon on episode rows",
                    checked = showButton,
                    onCheckedChange = { preferences.showDownloadButton().set(it) },
                )
            }

            // ── Auto-download ──
            item {
                SectionLabel("Auto-download")
                ToggleRow(
                    title = "Automatic video selection",
                    subtitle = "When ON, the app auto-picks the best server, audio, and quality based on your preferences below. When OFF, you choose manually each time.",
                    checked = autoDownload,
                    onCheckedChange = { preferences.autoDownload().set(it) },
                )
            }

            // ── Quality preferences ──
            item {
                SectionLabel("Quality preferences")
                Text(
                    text = "Top = highest priority. Drag the ≡ handle to reorder. The app tries these in order when auto-download is ON.",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
                app.confused.anikuta.feature.download.components.DragReorderableList(
                    items = qualityPrefs,
                    onReorder = { newOrder -> preferences.qualityPreferences().set(newOrder) },
                )
                FallbackRow(
                    title = "If preferred quality unavailable",
                    strategy = qualityFallback,
                    onSelect = { preferences.qualityFallback().set(it) },
                )
            }

            // ── Audio preferences ──
            item {
                SectionLabel("Audio preferences")
                Text(
                    text = "Top = preferred audio version (e.g. SUB before DUB). Drag the ≡ handle to reorder.",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
                app.confused.anikuta.feature.download.components.DragReorderableList(
                    items = audioPrefs,
                    onReorder = { newOrder -> preferences.audioPreferences().set(newOrder) },
                )
                FallbackRow(
                    title = "If preferred audio unavailable",
                    strategy = audioFallback,
                    onSelect = { preferences.audioFallback().set(it) },
                )
            }

            // ── Server preferences (per-extension) ──
            item {
                SectionLabel("Server preferences")
                Text(
                    text = "Servers are auto-discovered as you browse anime. Drag the ≡ handle to set your preferred server order per source.",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
                if (extensionSources.isEmpty()) {
                    Text(
                        text = "No trusted extensions installed. Install an extension from Browse → Extensions to get started.",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                    )
                } else {
                    extensionSources.forEach { extSource ->
                        ServerPreferenceSection(
                            extSource = extSource,
                            serverDiscovery = serverDiscovery,
                            preferences = preferences,
                        )
                    }
                }
                FallbackRow(
                    title = "If preferred server unavailable",
                    strategy = preferences.serverFallback().get(),
                    onSelect = { preferences.serverFallback().set(it) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.06.sp,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontFamily = RobotoFamily, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontFamily = RobotoFamily, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontFamily = RobotoFamily, fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontFamily = RobotoFamily, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/**
 * Per-extension server preference section. Shows the extension/source name +
 * its discovered servers (drag-and-drop reorderable). The user's saved order
 * is merged with discovered servers (saved order first, new servers appended).
 */
@Composable
private fun ServerPreferenceSection(
    extSource: ExtensionSourceInfo,
    serverDiscovery: app.confused.anikuta.core.download.ServerDiscoveryStore,
    preferences: DownloadPreferences,
) {
    val serverMap by serverDiscovery.serverMap.collectAsState(initial = emptyMap())
    val serverPrefs by preferences.serverPreferences().changes()
        .collectAsState(initial = preferences.serverPreferences().get())

    val discoveredServers = serverMap[extSource.sourceId.toString()] ?: emptyList()
    val userOrder = serverPrefs[extSource.sourceId.toString()] ?: emptyList()

    // Merge: user's saved order first (filtered to only known servers), then
    // any discovered servers not in the user's list.
    val mergedServers = (userOrder.filter { it in discoveredServers } +
        discoveredServers.filter { it !in userOrder }).distinct()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = extSource.extensionName,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = extSource.sourceName,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (mergedServers.isEmpty()) {
                Text(
                    text = "No servers discovered yet. Browse anime from this source to discover servers.",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                app.confused.anikuta.feature.download.components.DragReorderableList(
                    items = mergedServers,
                    onReorder = { newOrder ->
                        val updated = serverPrefs.toMutableMap()
                        updated[extSource.sourceId.toString()] = newOrder
                        preferences.serverPreferences().set(updated)
                    },
                )
            }
        }
    }
}

@Composable
private fun FallbackRow(
    title: String,
    strategy: FallbackStrategy,
    onSelect: (FallbackStrategy) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontFamily = RobotoFamily, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.size(8.dp))
            FallbackStrategy.entries.forEach { fs ->
                val label = when (fs) {
                    FallbackStrategy.TRY_NEXT -> "Try the next option (best-effort)"
                    FallbackStrategy.ASK -> "Ask me to pick manually"
                    FallbackStrategy.DO_NOT_DOWNLOAD -> "Don't download"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(fs) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = strategy == fs,
                        onClick = { onSelect(fs) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, fontFamily = RobotoFamily, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
