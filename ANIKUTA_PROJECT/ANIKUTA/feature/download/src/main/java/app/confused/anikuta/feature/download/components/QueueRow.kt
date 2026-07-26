package app.confused.anikuta.feature.download.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.download.DownloadStatus
import app.confused.anikuta.core.download.DownloadTask
import coil3.compose.AsyncImage

/**
 * A single row in the live download queue.
 *
 * Layout: cover (48×68) + title/episode/progress column + action buttons.
 * Actions depend on status:
 *  - DOWNLOADING → Pause + Cancel
 *  - QUEUED → Cancel
 *  - PAUSED → Resume + Cancel
 *  - ERROR → Retry + Cancel
 *
 * Design: `surfaceVariant` 0.4f card (matches More screen), RoundedCornerShape(12dp),
 * RobotoFamily, #B1F256 accents. No drag handle (not a bottom sheet).
 */
@Composable
fun QueueRow(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover thumbnail
            val cover = task.request.anime.coverUrl
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = task.request.anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 48.dp, height = 68.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.size(width = 48.dp, height = 68.dp),
                ) {}
            }

            Spacer(Modifier.size(12.dp))

            // Title + episode + progress
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.request.anime.title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "EP ${task.request.episode.episodeNumber.toInt()} — ${statusLabel(task.status)}",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Server / quality / audio line (if available)
                val videoInfo = listOfNotNull(
                    task.request.videoServer.takeIf { it.isNotBlank() },
                    task.request.videoQuality.takeIf { it.isNotBlank() },
                    task.request.videoAudio.takeIf { it.isNotBlank() },
                )
                if (videoInfo.isNotEmpty()) {
                    Text(
                        text = videoInfo.joinToString(" • "),
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        LinearProgressIndicator(
                            progress = { (task.progress / 100f).coerceIn(0f, 1f) },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                        Text(
                            text = progressText(task),
                            fontFamily = RobotoFamily,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    DownloadStatus.QUEUED -> {
                        LinearProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                    }
                    DownloadStatus.ERROR -> {
                        Text(
                            text = task.errorMessage ?: "Download failed",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    else -> {}
                }
            }

            Spacer(Modifier.size(8.dp))

            // Action buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        QueueActionButton(Icons.Filled.Pause, "Pause", onPause)
                    }
                    DownloadStatus.PAUSED -> {
                        QueueActionButton(Icons.Filled.PlayArrow, "Resume", onResume)
                    }
                    DownloadStatus.ERROR -> {
                        QueueActionButton(Icons.Filled.Refresh, "Retry", onRetry)
                    }
                    DownloadStatus.QUEUED -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    else -> {}
                }
                QueueActionButton(Icons.Filled.Close, "Cancel", onCancel)
            }
        }
    }
}

@Composable
private fun QueueActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.DOWNLOADING -> "Downloading"
    DownloadStatus.PAUSED -> "Paused"
    DownloadStatus.ERROR -> "Error"
    DownloadStatus.COMPLETED -> "Completed"
    DownloadStatus.CANCELLED -> "Cancelled"
}

private fun progressText(task: DownloadTask): String {
    val pct = "${task.progress}%"
    return if (task.totalBytes > 0) {
        "$pct • ${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}"
    } else {
        "$pct • ${formatBytes(task.downloadedBytes)}"
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}
