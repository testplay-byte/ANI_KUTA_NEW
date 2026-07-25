package app.confused.anikuta.feature.download

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.download.DownloadPreferences
import app.confused.anikuta.core.download.FallbackStrategy
import org.koin.compose.koinInject

/**
 * The full-page Download settings screen.
 *
 * **Sections (each in a dedicated container with a consistent background):**
 * 1. **General** — folder, Wi-Fi-only, concurrent, show-download-button.
 * 2. **Auto-download** — toggle. When ON, the preference sections (3-5) are
 *    visible + active. When OFF, they're hidden (the user picks manually each
 *    time, so preferences don't apply).
 * 3. **Preferred quality** — collapsible. Header "Preferred quality — drag to
 *    re-order". Expanded: drag-and-drop list + 3-way fallback toggle.
 * 4. **Preferred audio** — collapsible. Same structure as quality.
 * 5. **Preferred server** — collapsible. Per-extension, each also collapsible.
 *
 * **Accordion behavior:** only one of (quality/audio/server) is expanded at a
 * time. Within server, only one extension is expanded at a time.
 *
 * **Design:** CollapsingHeader, dedicated section containers (surfaceVariant
 * 0.4f), RobotoFamily, #B1F256 accents. 3-way toggles for fallback strategies
 * (matching the episode-settings segmented-row style).
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
    val serverFallback by preferences.serverFallback().changes()
        .collectAsState(initial = preferences.serverFallback().get())

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
                // Non-fatal
            }
        }
    }

    // Accordion state — which preference section is expanded (0=none, 1=quality, 2=audio, 3=server)
    var expandedSection by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(
            title = "Download settings",
            collapsed = collapsed,
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── General ──
            item {
                SectionContainer("General") {
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
            }

            // ── Auto-download ──
            item {
                SectionContainer("Auto-download") {
                    ToggleRow(
                        title = "Automatic video selection",
                        subtitle = "When ON, the app auto-picks the best server, audio, and quality based on your preferences below. When OFF, you choose manually each time.",
                        checked = autoDownload,
                        onCheckedChange = { preferences.autoDownload().set(it) },
                    )
                }
            }

            // ── Preference sections (only visible when auto-download is ON) ──
            if (autoDownload) {
                // ── Preferred quality ──
                item {
                    CollapsibleSection(
                        title = "Preferred quality",
                        subtitle = "drag to re-order",
                        isExpanded = expandedSection == 1,
                        onToggle = { expandedSection = if (expandedSection == 1) 0 else 1 },
                    ) {
                        app.confused.anikuta.feature.download.components.DragReorderableList(
                            items = qualityPrefs,
                            onReorder = { newOrder -> preferences.qualityPreferences().set(newOrder) },
                        )
                        Spacer(Modifier.size(12.dp))
                        FallbackToggle(
                            label = "If unavailable",
                            strategy = qualityFallback,
                            onSelect = { preferences.qualityFallback().set(it) },
                        )
                    }
                }

                // ── Preferred audio ──
                item {
                    CollapsibleSection(
                        title = "Preferred audio",
                        subtitle = "drag to re-order",
                        isExpanded = expandedSection == 2,
                        onToggle = { expandedSection = if (expandedSection == 2) 0 else 2 },
                    ) {
                        app.confused.anikuta.feature.download.components.DragReorderableList(
                            items = audioPrefs,
                            onReorder = { newOrder -> preferences.audioPreferences().set(newOrder) },
                        )
                        Spacer(Modifier.size(12.dp))
                        FallbackToggle(
                            label = "If unavailable",
                            strategy = audioFallback,
                            onSelect = { preferences.audioFallback().set(it) },
                        )
                    }
                }

                // ── Preferred server ──
                item {
                    CollapsibleSection(
                        title = "Preferred server",
                        subtitle = "per extension",
                        isExpanded = expandedSection == 3,
                        onToggle = { expandedSection = if (expandedSection == 3) 0 else 3 },
                    ) {
                        if (extensionSources.isEmpty()) {
                            Text(
                                text = "No trusted extensions installed. Install an extension from Browse → Extensions to get started.",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp),
                            )
                        } else {
                            // Each extension is also collapsible; only one open at a time.
                            var expandedExtension by rememberSaveable { mutableIntStateOf(-1) }
                            extensionSources.forEachIndexed { idx, extSource ->
                                CollapsibleExtensionSection(
                                    extSource = extSource,
                                    serverDiscovery = serverDiscovery,
                                    preferences = preferences,
                                    isExpanded = expandedExtension == idx,
                                    onToggle = { expandedExtension = if (expandedExtension == idx) -1 else idx },
                                )
                            }
                        }
                        Spacer(Modifier.size(12.dp))
                        FallbackToggle(
                            label = "If unavailable",
                            strategy = serverFallback,
                            onSelect = { preferences.serverFallback().set(it) },
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Section containers
// ════════════════════════════════════════════════════════════════════════════

/**
 * A dedicated section container with a consistent background — groups related
 * settings visually (per the owner's request: "each should have a proper
 * dedicated background for themselves").
 */
@Composable
private fun SectionContainer(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = label.uppercase(),
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.06.sp,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                content()
            }
        }
    }
}

/**
 * A collapsible section with a header (title + subtitle on the right + chevron).
 * The header is tappable to expand/collapse. When expanded, [content] appears
 * with a smooth animation.
 */
@Composable
private fun CollapsibleSection(
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header row — tap to expand/collapse
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggle)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = subtitle,
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(if (isExpanded) 90f else 0f),
                    )
                }
                // Expanded content
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * A collapsible per-extension server-preference section. Shows the extension
 * name + source name; when expanded, shows the discovered servers with
 * drag-and-drop reordering.
 */
@Composable
private fun CollapsibleExtensionSection(
    extSource: ExtensionSourceInfo,
    serverDiscovery: app.confused.anikuta.core.download.ServerDiscoveryStore,
    preferences: DownloadPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val serverMap by serverDiscovery.serverMap.collectAsState(initial = emptyMap())
    val serverPrefs by preferences.serverPreferences().changes()
        .collectAsState(initial = preferences.serverPreferences().get())

    val discoveredServers = serverMap[extSource.sourceId.toString()] ?: emptyList()
    val userOrder = serverPrefs[extSource.sourceId.toString()] ?: emptyList()
    val mergedServers = (userOrder.filter { it in discoveredServers } +
        discoveredServers.filter { it !in userOrder }).distinct()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Extension header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (isExpanded) 90f else 0f),
                )
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    if (mergedServers.isEmpty()) {
                        Text(
                            text = "No servers discovered yet. Browse or watch anime from this source to discover servers.",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
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
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Settings rows
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
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

// ════════════════════════════════════════════════════════════════════════════
//  3-way fallback toggle (matching the episode-settings segmented-row style)
// ════════════════════════════════════════════════════════════════════════════

/**
 * A 3-way segmented toggle for the fallback strategy (Try next / Ask / Don't
 * download). Matches the `SegmentedRow` style from `:feature:episode-settings`
 * — selected segment = primary background + onPrimary ExtraBold text;
 * unselected = transparent + onSurfaceVariant Medium text.
 */
@Composable
private fun FallbackToggle(
    label: String,
    strategy: FallbackStrategy,
    onSelect: (FallbackStrategy) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        val options = listOf(
            "Try next" to (strategy == FallbackStrategy.TRY_NEXT),
            "Ask" to (strategy == FallbackStrategy.ASK),
            "Don't" to (strategy == FallbackStrategy.DO_NOT_DOWNLOAD),
        )
        SegmentedRowLocal(options = options, onSelect = { idx ->
            onSelect(when (idx) {
                0 -> FallbackStrategy.TRY_NEXT
                1 -> FallbackStrategy.ASK
                else -> FallbackStrategy.DO_NOT_DOWNLOAD
            })
        })
    }
}

/**
 * A local copy of the `SegmentedRow` from `:feature:episode-settings` (which
 * `:feature:download` can't import). Same visual style — a pill row where the
 * selected segment is filled with `primary`.
 */
@Composable
private fun SegmentedRowLocal(
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { idx, (label, selected) ->
                val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                val fg = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(idx) },
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = fg,
                        )
                    }
                }
            }
        }
    }
}
