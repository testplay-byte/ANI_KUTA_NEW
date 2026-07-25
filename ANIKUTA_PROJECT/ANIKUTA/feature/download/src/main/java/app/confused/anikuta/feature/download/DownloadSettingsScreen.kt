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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * 3. **Quality preferences** — priority-ordered list (reorderable). The top
 *    quality is tried first; if unavailable, the next, etc. + fallback strategy.
 * 4. **Audio preferences** — priority-ordered list (reorderable) + fallback.
 *
 * **Design:** CollapsingHeader, surfaceVariant 0.4f cards, RobotoFamily,
 * #B1F256 accents. Follows the design language (principle #10: settings divided
 * into sections).
 */
@Composable
fun DownloadSettingsScreen(
    onBack: () -> Unit,
    preferences: DownloadPreferences = koinInject(),
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

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                preferences.downloadFolderUri().set(uri.toString())
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                androidx.compose.ui.platform.LocalContext.current.contentResolver
                    .takePersistableUriPermission(uri, flags)
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
                    text = "Top = highest priority. The app tries these in order when auto-download is ON.",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
                ReorderableList(
                    items = qualityPrefs,
                    onMove = { from, to ->
                        val mutable = qualityPrefs.toMutableList()
                        val moved = mutable.removeAt(from)
                        mutable.add(to, moved)
                        preferences.qualityPreferences().set(mutable)
                    },
                    onAdd = { newQuality ->
                        preferences.qualityPreferences().set(qualityPrefs + newQuality)
                    },
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
                    text = "Top = preferred audio version (e.g. SUB before DUB).",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
                ReorderableList(
                    items = audioPrefs,
                    onMove = { from, to ->
                        val mutable = audioPrefs.toMutableList()
                        val moved = mutable.removeAt(from)
                        mutable.add(to, moved)
                        preferences.audioPreferences().set(mutable)
                    },
                    onAdd = { newAudio ->
                        preferences.audioPreferences().set(audioPrefs + newAudio)
                    },
                )
                FallbackRow(
                    title = "If preferred audio unavailable",
                    strategy = audioFallback,
                    onSelect = { preferences.audioFallback().set(it) },
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
 * A reorderable preference list. Each row shows the item + up/down arrows.
 * (Up/down arrows are used instead of drag-and-drop for reliability — a
 * full drag-and-drop implementation is a tracked future enhancement.)
 */
@Composable
private fun ReorderableList(
    items: List<String>,
    onMove: (from: Int, to: Int) -> Unit,
    onAdd: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        items.forEachIndexed { index, item ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}.",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        text = item,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { if (index > 0) onMove(index, index - 1) },
                        modifier = Modifier.size(32.dp),
                        enabled = index > 0,
                    ) {
                        Icon(Icons.Filled.ArrowUpward, "Move up",
                            tint = if (index > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { if (index < items.size - 1) onMove(index, index + 1) },
                        modifier = Modifier.size(32.dp),
                        enabled = index < items.size - 1,
                    ) {
                        Icon(Icons.Filled.ArrowDownward, "Move down",
                            tint = if (index < items.size - 1) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp))
                    }
                }
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
