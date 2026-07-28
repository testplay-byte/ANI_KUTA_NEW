package app.confused.anikuta.feature.download.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.download.DownloadTask
import app.confused.anikuta.feature.download.DownloadedAnimeKey
import coil3.compose.AsyncImage

/**
 * A completed-anime card in the Downloaded section.
 *
 * Header row: cover + title + "N episodes" + expand toggle + delete-all.
 * Expanded: per-episode rows (episode number + delete).
 *
 * Design: `surfaceVariant` 0.4f card, RoundedCornerShape(12dp), RobotoFamily.
 */
@Composable
fun DownloadedAnimeCard(
    key: DownloadedAnimeKey,
    episodes: List<DownloadTask>,
    onDeleteEpisode: (taskId: Long) -> Unit,
    onDeleteAll: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!key.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = key.coverUrl,
                        contentDescription = key.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 44.dp, height = 62.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.size(width = 44.dp, height = 62.dp),
                    ) {}
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = key.title,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${episodes.size} episode${if (episodes.size != 1) "s" else ""} downloaded",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDeleteAll, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete all",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Expanded episode list (plain Column inside the card — NOT a nested LazyColumn).
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 68.dp, end = 12.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    episodes.sortedBy { it.request.episode.episodeNumber }.forEach { ep ->
                        DownloadedEpisodeRow(
                            episodeNumber = ep.request.episode.episodeNumber.toInt(),
                            episodeName = ep.request.episode.name,
                            onDelete = { onDeleteEpisode(ep.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedEpisodeRow(
    episodeNumber: Int,
    episodeName: String,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "EP $episodeNumber",
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = episodeName,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete episode",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
