package app.anikuta.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.model.SAnime
import app.anikuta.source.api.model.SEpisode

/**
 * Hidden Debug / Extension-test screen (Phase 5 task 5.1).
 *
 * Developer tool to verify the extension chain end-to-end before wiring it
 * into the detail page: pick a source → search → episodes → videos → URL.
 * Every step is logged on-screen. Accessible via long-press on the version
 * number in Settings (wired in task 5.14). Easily removable: delete this
 * file + the nav route + the long-press handler.
 */
@Composable
fun DebugScreen(onBack: () -> Unit) {
    val viewModel: DebugViewModel = viewModel()
    val sources by viewModel.sources.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val log by viewModel.log.collectAsState()
    val loading by viewModel.loading.collectAsState()

    var query by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    "Debug — Extension Test",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (loading) {
                    Spacer(modifier = Modifier.weight(1f))
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Source picker
                item(key = "sources") {
                    SectionCard("1. Sources (${sources.size})") {
                        if (sources.isEmpty()) {
                            Text(
                                "No catalogue sources registered. Install an extension, then tap Refresh.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        } else {
                            sources.forEach { source ->
                                SourceRow(
                                    source = source,
                                    selected = source.id == selectedSource?.id,
                                    onClick = { viewModel.selectSource(source) },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.reloadExtensions() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Re-scan installed extensions")
                        }
                    }
                }

                // Search
                item(key = "search") {
                    SectionCard("2. Search") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Anime title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = selectedSource != null,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.search(query) },
                            enabled = selectedSource != null && query.isNotBlank() && !loading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Search")
                        }
                    }
                }

                // Search results
                if (searchResults.isNotEmpty()) {
                    item(key = "results_header") {
                        Text(
                            "3. Search results (${searchResults.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(searchResults, key = { it.url }) { anime ->
                        ResultRow(anime) { viewModel.fetchEpisodes(anime) }
                    }
                }

                // Episodes
                if (episodes.isNotEmpty()) {
                    item(key = "episodes_header") {
                        HorizontalDivider()
                        Text(
                            "4. Episodes (${episodes.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(episodes, key = { it.url }) { ep ->
                        EpisodeRow(ep) { viewModel.fetchVideos(ep) }
                    }
                }

                // Videos
                if (videos.isNotEmpty()) {
                    item(key = "videos_header") {
                        HorizontalDivider()
                        Text(
                            "5. Videos (${videos.size}) — tap to copy URL",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(videos, key = { it.videoUrl + it.videoTitle }) { vid ->
                        VideoRow(vid)
                    }
                }

                // Log
                item(key = "log_header") {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Log (${log.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        OutlinedButton(onClick = { viewModel.clearLog() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear log", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Clear")
                        }
                    }
                }
                itemsIndexed(log, key = { index, entry -> "${entry.time}_$index" }) { _, entry ->
                    Text(
                        text = entry.message,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (entry.error) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SourceRow(source: AnimeCatalogueSource, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                source.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                "id=${source.id} · ${source.lang}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultRow(anime: SAnime, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(anime.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "url=${anime.url}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("Get episodes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: SEpisode, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Ep ${episode.episode_number} — ${episode.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "url=${episode.url}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VideoRow(video: app.anikuta.source.api.model.Video) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        onClick = {
            val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("video_url", video.videoUrl),
            )
            android.widget.Toast.makeText(ctx, "Copied: ${video.videoUrl}", android.widget.Toast.LENGTH_SHORT).show()
        },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                video.videoTitle.ifBlank { "untitled" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                video.videoUrl,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
