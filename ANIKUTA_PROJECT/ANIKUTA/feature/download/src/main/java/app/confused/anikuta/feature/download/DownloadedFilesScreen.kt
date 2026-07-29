package app.confused.anikuta.feature.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel

/**
 * The Downloaded Files page — shows all completed downloads grouped by anime.
 *
 * Reached from the Downloads screen's "Downloaded" icon (only shows if the
 * user has at least one completed download).
 *
 * **Layout:**
 * - CollapsingHeader ("Downloaded")
 * - Anime-sectioned cards: each anime has a header (cover + title + episode
 *   count) + a list of downloaded episodes with delete buttons.
 * - Tap an episode → plays it offline (wired by the host).
 * - Delete button per episode → removes the file + the task.
 *
 * **Design:** #B1F256 primary, RobotoFamily, surfaceVariant cards.
 */
@Composable
fun DownloadedFilesScreen(
    onBack: () -> Unit,
    onPlayEpisode: ((String, String) -> Unit)? = null,
    viewModel: DownloadViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemIndex > 0 ||
        lazyListState.firstVisibleItemScrollOffset > 20

    val downloaded = state.downloaded

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = "Downloaded", collapsed = collapsed)

        if (downloaded.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Download, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No downloaded files", fontFamily = RobotoFamily,
                        fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text("Downloaded episodes will appear here",
                        fontFamily = RobotoFamily, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                downloaded.forEach { (animeKey, episodes) ->
                    item(key = "downloaded_${animeKey.contentId}") {
                        DownloadedAnimeCard(
                            animeKey = animeKey,
                            episodes = episodes,
                            onPlay = { episodeUrl ->
                                onPlayEpisode?.invoke(animeKey.contentId, episodeUrl)
                            },
                            onDelete = { taskId -> viewModel.deleteEpisode(taskId) },
                            onDeleteAll = { viewModel.deleteAnime(animeKey.contentId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedAnimeCard(
    animeKey: DownloadedAnimeKey,
    episodes: List<app.confused.anikuta.core.download.DownloadTask>,
    onPlay: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteAll: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column {
            // Header: cover + title + count + delete-all + expand
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!animeKey.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = animeKey.coverUrl,
                        contentDescription = animeKey.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 44.dp, height = 62.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(animeKey.title, fontFamily = RobotoFamily, fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${episodes.size} episode${if (episodes.size != 1) "s" else ""}",
                        fontFamily = RobotoFamily, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.IconButton(onClick = onDeleteAll, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete all",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                Icon(Icons.Filled.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }

            // Episode list
            if (expanded) {
                episodes.sortedBy { it.request.episode.episodeNumber }.forEach { task ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onPlay(task.request.episode.episodeUrl) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("EP ${task.request.episode.episodeNumber.toInt()}",
                            fontFamily = RobotoFamily, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(48.dp))
                        Text(task.request.episode.name.ifBlank { "Episode ${task.request.episode.episodeNumber.toInt()}" },
                            fontFamily = RobotoFamily, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        // Quality pill
                        if (task.request.videoQuality.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(task.request.videoQuality, fontFamily = RobotoFamily,
                                    fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        androidx.compose.material3.IconButton(onClick = { onDelete(task.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete episode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
