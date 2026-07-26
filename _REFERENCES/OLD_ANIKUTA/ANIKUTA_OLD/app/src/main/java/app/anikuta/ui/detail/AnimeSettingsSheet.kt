package app.anikuta.ui.detail

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.cache.ReleaseTrackingStore
import app.anikuta.data.cache.SubDubStore
import app.anikuta.notification.NotificationPreferences
import app.anikuta.ui.library.LibraryStore
import kotlinx.coroutines.flow.collectLatest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Per-anime notification + auto-download settings.
 *
 * [mode] determines which section is shown:
 *  - [SettingsMode.NOTIFICATIONS] → only the notification toggles
 *  - [SettingsMode.DOWNLOADS] → only the auto-download toggles
 *
 * UI:
 *  - No drag handle (clean top edge)
 *  - No anime name at the top
 *  - Auto-creates a tracking entry if the anime isn't tracked yet
 *  - Blocks auto-download for fully-released anime + shows a toast
 */
enum class SettingsMode { NOTIFICATIONS, DOWNLOADS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeSettingsSheet(
    anilistId: Int,
    mode: SettingsMode,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val trackingStore: ReleaseTrackingStore? = remember { try { Injekt.get() } catch (e: Exception) { null } }
    val prefs: NotificationPreferences? = remember { try { Injekt.get() } catch (e: Exception) { null } }
    val libraryStore: LibraryStore? = remember { try { Injekt.get() } catch (e: Exception) { null } }
    val subDubStore: SubDubStore? = remember { try { Injekt.get() } catch (e: Exception) { null } }

    val title = when (mode) {
        SettingsMode.NOTIFICATIONS -> "Notification Settings"
        SettingsMode.DOWNLOADS -> "Auto Download"
    }

    if (trackingStore == null || prefs == null) {
        ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = {}) {
            Text("Settings unavailable", modifier = Modifier.padding(16.dp))
        }
        return
    }

    val anime: AniListAnime? = remember(anilistId) { libraryStore?.get(anilistId) }
    val subDubInfo = remember(anilistId) { subDubStore?.get(anilistId) }

    var tracked by remember(anilistId) { mutableStateOf(trackingStore.get(anilistId)) }
    LaunchedEffect(anilistId) {
        trackingStore.changes.collectLatest { map -> tracked = map[anilistId.toString()] }
    }

    // Auto-create a tracking entry if not tracked yet (fixes toggle bug for old library anime)
    LaunchedEffect(anilistId, tracked) {
        if (tracked == null) {
            val libAnime = libraryStore?.get(anilistId)
            if (libAnime != null) {
                trackingStore.put(
                    ReleaseTrackingStore.TrackedAnime(
                        anilistId = anilistId,
                        title = libAnime.title.preferred(),
                        coverUrl = libAnime.coverImage.best(),
                    )
                )
            }
        }
    }

    val globalNotifySub by prefs.globalNotifySub().stateInAsState()
    val globalNotifyDub by prefs.globalNotifyDub().stateInAsState()
    val globalAutoDlEnabled by prefs.globalAutoDownloadEnabled().stateInAsState()
    val globalAutoDlSub by prefs.globalAutoDownloadSub().stateInAsState()
    val globalAutoDlDub by prefs.globalAutoDownloadDub().stateInAsState()

    val notifyOnNew = tracked?.notifyOnNew ?: true
    val notifySub = tracked?.notifySub ?: globalNotifySub
    val notifyDub = tracked?.notifyDub ?: globalNotifyDub
    val autoDlNew = tracked?.autoDownloadNew ?: globalAutoDlEnabled
    val autoDlSub = tracked?.autoDownloadSub ?: globalAutoDlSub
    val autoDlDub = tracked?.autoDownloadDub ?: globalAutoDlDub

    val isFullyReleased = remember(anime, subDubInfo, tracked) {
        isAnimeFullyReleased(anime, subDubInfo)
    }

    fun update(block: (ReleaseTrackingStore.TrackedAnime) -> ReleaseTrackingStore.TrackedAnime) {
        val current = tracked ?: return
        trackingStore.put(block(current))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = {}) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
            }

            when (mode) {
                SettingsMode.NOTIFICATIONS -> {
                    item {
                        PerAnimeSwitchRow(
                            icon = Icons.Default.Notifications,
                            title = "Notify on new episodes",
                            subtitle = "Get a notification when a new episode is available" +
                                if (tracked?.notifyOnNew == null) " (global default: ON)" else "",
                            checked = notifyOnNew,
                            onCheckedChange = { v -> update { it.copy(notifyOnNew = v) } },
                        )
                    }
                    item {
                        PerAnimeSwitchRow(
                            icon = Icons.Default.Subtitles,
                            title = "Notify on new SUB episodes",
                            subtitle = "Get notified when a new subbed episode is available" +
                                if (tracked?.notifySub == null) " (global default: ${if (globalNotifySub) "ON" else "OFF"})" else "",
                            checked = notifySub,
                            enabled = notifyOnNew,
                            onCheckedChange = { v -> update { it.copy(notifySub = v) } },
                        )
                    }
                    item {
                        PerAnimeSwitchRow(
                            icon = Icons.Default.GraphicEq,
                            title = "Notify on new DUB episodes",
                            subtitle = "Get notified when a new dubbed episode is available. Dub episodes are often released later than sub." +
                                if (tracked?.notifyDub == null) " (global default: ${if (globalNotifyDub) "ON" else "OFF"})" else "",
                            checked = notifyDub,
                            enabled = notifyOnNew,
                            onCheckedChange = { v -> update { it.copy(notifyDub = v) } },
                        )
                    }
                }
                SettingsMode.DOWNLOADS -> {
                    item {
                        PerAnimeSwitchRow(
                            icon = Icons.Default.CloudDownload,
                            title = "Auto-download new episodes",
                            subtitle = if (isFullyReleased) {
                                "⚠ All episodes have already been released. Auto-download is not available."
                            } else {
                                "When a new episode is released, automatically download it in the background" +
                                if (tracked?.autoDownloadNew == null) " (global default: ${if (globalAutoDlEnabled) "ON" else "OFF"})" else ""
                            },
                            checked = autoDlNew && !isFullyReleased,
                            enabled = !isFullyReleased,
                            onCheckedChange = { v ->
                                if (isFullyReleased) {
                                    Toast.makeText(context, "Cannot turn this on — all episodes have already been released.", Toast.LENGTH_LONG).show()
                                } else {
                                    update { it.copy(autoDownloadNew = v) }
                                }
                            },
                        )
                    }
                    if (!isFullyReleased && autoDlNew) {
                        item {
                            PerAnimeSwitchRow(
                                icon = Icons.Default.Subtitles,
                                title = "Auto-download SUB",
                                subtitle = "Download the subbed version when a new episode releases. If both SUB and DUB are on, both versions will be downloaded." +
                                    if (tracked?.autoDownloadSub == null) " (global default: ${if (globalAutoDlSub) "ON" else "OFF"})" else "",
                                checked = autoDlSub,
                                enabled = autoDlNew,
                                onCheckedChange = { v -> update { it.copy(autoDownloadSub = v) } },
                            )
                        }
                        item {
                            PerAnimeSwitchRow(
                                icon = Icons.Default.GraphicEq,
                                title = "Auto-download DUB",
                                subtitle = "Download the dubbed version when a new dub releases. Dub episodes are often released later than sub." +
                                    if (tracked?.autoDownloadDub == null) " (global default: ${if (globalAutoDlDub) "ON" else "OFF"})" else "",
                                checked = autoDlDub,
                                enabled = autoDlNew,
                                onCheckedChange = { v -> update { it.copy(autoDownloadDub = v) } },
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        update { it.copy(
                            notifyOnNew = null, notifySub = null, notifyDub = null,
                            autoDownloadNew = null, autoDownloadSub = null, autoDownloadDub = null,
                            autoDownloadQuality = null, autoDownloadAudio = null,
                        ) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Reset to global defaults") }
            }
        }
    }
}

private fun isAnimeFullyReleased(anime: AniListAnime?, subDubInfo: SubDubStore.SubDubInfo?): Boolean {
    if (anime?.status?.uppercase() != "FINISHED") return false
    val subInfo = subDubInfo ?: return false
    if (subInfo.totalEpisodes <= 0) return false
    return subInfo.subCount >= subInfo.totalEpisodes && subInfo.dubCount >= subInfo.totalEpisodes
}

@Composable
private fun PerAnimeSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun <T> app.anikuta.core.preference.Preference<T>.stateInAsState(): State<T> {
    return produceState(initialValue = this.get(), this) {
        this@stateInAsState.changes().collectLatest { value = it }
    }
}
