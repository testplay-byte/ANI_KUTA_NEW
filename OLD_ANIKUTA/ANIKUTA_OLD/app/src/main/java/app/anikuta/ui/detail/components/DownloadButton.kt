package app.anikuta.ui.detail.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.anikuta.download.Download

/**
 * Tall download button — used in the episode row's "synopsis" layout
 * (where the download button is a separate panel beside the synopsis text).
 *
 * The button shows different states:
 *
 * - **Idle**: Download icon
 * - **Downloading / Queued / Resolving / Muxing**: Indeterminate spinner
 * - **Reconnecting**: Pulsing error-colored spinner
 * - **Error**: Error icon
 * - **Paused**: Download icon (muted)
 * - **Downloaded / On disk**: Done icon (primary color)
 *
 * The background color alternates based on the row index to contrast
 * with the episode card's alternating background.
 *
 * @param episodeUrl       URL of the episode (used to look up status).
 * @param downloadStatus   Map of episode URL → download state.
 * @param downloadProgress Map of episode URL → progress percentage.
 * @param downloadedOnDisk Set of episode URLs that exist on disk.
 * @param onDownload       Called when the button is tapped.
 * @param onLongClick      Called when the button is long-pressed.
 * @param index            Row index (for alternating background).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DownloadButtonTall(
    episodeUrl: String,
    downloadStatus: Map<String, Download.State>,
    downloadProgress: Map<String, Int>,
    downloadedOnDisk: Set<String>,
    onDownload: () -> Unit,
    onLongClick: () -> Unit = {},
    index: Int = 0,
) {
    val status = downloadStatus[episodeUrl]
    val progress = downloadProgress[episodeUrl] ?: 0
    val isOnDisk = downloadedOnDisk.contains(episodeUrl)

    val defaultBg = if (index % 2 == 0) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val backgroundColor = when {
        status == Download.State.DOWNLOADING -> MaterialTheme.colorScheme.primaryContainer
        status == Download.State.ERROR -> MaterialTheme.colorScheme.errorContainer
        status == Download.State.DOWNLOADED || isOnDisk ->
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        status == Download.State.PAUSED -> defaultBg
        status == Download.State.RECONNECTING -> MaterialTheme.colorScheme.errorContainer
        else -> defaultBg
    }

    val iconColor = when {
        status == Download.State.ERROR -> MaterialTheme.colorScheme.error
        status == Download.State.DOWNLOADED || isOnDisk -> MaterialTheme.colorScheme.primary
        status == Download.State.RECONNECTING -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight()
            .combinedClickable(
                onClick = onDownload,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        tonalElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                status == Download.State.DOWNLOADING -> {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                status == Download.State.QUEUE ||
                status == Download.State.RESOLVING ||
                status == Download.State.MUXING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                status == Download.State.ERROR -> {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "Failed",
                        tint = iconColor,
                        modifier = Modifier.size(24.dp),
                    )
                }

                status == Download.State.PAUSED -> {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Paused",
                        tint = iconColor,
                        modifier = Modifier.size(24.dp),
                    )
                }

                status == Download.State.RECONNECTING -> {
                    val transition = rememberInfiniteTransition(label = "reconnect")
                    val spinnerColor by transition.animateColor(
                        initialValue = MaterialTheme.colorScheme.error,
                        targetValue = Color(0xFFFFA000),
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "reconnect_color",
                    )
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = spinnerColor,
                    )
                }

                status == Download.State.DOWNLOADED || isOnDisk -> {
                    Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = "Downloaded",
                        tint = iconColor,
                        modifier = Modifier.size(24.dp),
                    )
                }

                else -> {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download",
                        tint = iconColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
