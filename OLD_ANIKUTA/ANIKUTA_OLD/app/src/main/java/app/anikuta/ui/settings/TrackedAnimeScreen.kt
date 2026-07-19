package app.anikuta.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import coil3.compose.AsyncImage
import app.anikuta.data.cache.ReleaseTrackingStore
import app.anikuta.notification.NotificationPreferences
import app.anikuta.ui.detail.AnimeSettingsSheet
import app.anikuta.ui.detail.SettingsMode
import app.anikuta.ui.library.LibraryStore
import app.anikuta.data.anilist.model.AniListAnime
import kotlinx.coroutines.flow.collectLatest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Tracked Anime screen — shows all library anime with cover + name + status.
 * Tapping a row opens a per-anime settings sheet.
 *
 * [mode] determines which per-anime sheet opens (NOTIFICATIONS or DOWNLOADS).
 * This screen is reused from both Notifications settings and Downloads settings.
 */
@Composable
fun TrackedAnimeScreen(mode: SettingsMode = SettingsMode.NOTIFICATIONS, onBack: () -> Unit) {
    val libraryStore: LibraryStore? = remember { try { Injekt.get() } catch (e: Exception) { null } }
    val trackingStore: ReleaseTrackingStore? = remember { try { Injekt.get() } catch (e: Exception) { null } }
    val prefs: NotificationPreferences? = remember { try { Injekt.get() } catch (e: Exception) { null } }

    var selectedAnimeId by remember { mutableStateOf<Int?>(null) }

    val libraryAnime = remember { libraryStore?.getAll() ?: emptyList() }
    val trackedMap by (trackingStore?.changes ?: kotlinx.coroutines.flow.flowOf(emptyMap()))
        .collectAsState(initial = trackingStore?.getAll() ?: emptyMap())

    if (selectedAnimeId != null) {
        AnimeSettingsSheet(
            anilistId = selectedAnimeId!!,
            mode = mode,
            onDismiss = { selectedAnimeId = null },
        )
    }

    val screenTitle = when (mode) {
        SettingsMode.NOTIFICATIONS -> "Tracked Anime"
        SettingsMode.DOWNLOADS -> "Auto Download"
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(screenTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // Filter out fully-released anime (FINISHED + no next airing episode).
        // These don't need tracking — no new episodes to notify about or download.
        val visibleAnime = remember(libraryAnime) {
            libraryAnime.filter { anime ->
                // Show if: status is unknown, OR not finished, OR has a next airing episode
                val isFinished = anime.status?.uppercase() == "FINISHED"
                val hasNoNextAiring = anime.nextAiringEpisode == null
                !(isFinished && hasNoNextAiring)
            }
        }

        if (visibleAnime.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No tracked anime yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text("Add anime to your library to start tracking.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleAnime, key = { it.id }) { anime ->
                    val tracked = trackedMap[anime.id.toString()]
                    TrackedAnimeCard(
                        anime = anime,
                        tracked = tracked,
                        globalNotifyDefault = prefs?.globalNotifyEnabled()?.get() ?: true,
                        globalAutoDlDefault = prefs?.globalAutoDownloadEnabled()?.get() ?: false,
                        isFullyReleased = false, // Already filtered out above
                        onClick = { selectedAnimeId = anime.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackedAnimeCard(
    anime: AniListAnime,
    tracked: ReleaseTrackingStore.TrackedAnime?,
    globalNotifyDefault: Boolean,
    globalAutoDlDefault: Boolean,
    isFullyReleased: Boolean,
    onClick: () -> Unit,
) {
    val notifyOn = tracked?.notifyOnNew ?: globalNotifyDefault
    val autoDlOn = tracked?.autoDownloadNew ?: globalAutoDlDefault

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val coverUrl = anime.coverImage.best()
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = anime.title.preferred(),
                    modifier = Modifier.width(52.dp).height(76.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.width(52.dp).height(76.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(anime.title.preferred(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (notifyOn) {
                        AssistChip(onClick = onClick, label = { Text("Notify", style = MaterialTheme.typography.labelSmall) }, leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(14.dp)) })
                    }
                    if (autoDlOn && !isFullyReleased) {
                        AssistChip(onClick = onClick, label = { Text("Auto-DL", style = MaterialTheme.typography.labelSmall) }, leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp)) })
                    }
                    if (isFullyReleased) {
                        AssistChip(onClick = onClick, label = { Text("Complete", style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
