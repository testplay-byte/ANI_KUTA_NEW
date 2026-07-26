package app.confused.anikuta.feature.animedetails

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * The state-driven download control for an episode row.
 *
 * Renders different controls based on [state]:
 *  - [NotDownloaded] → download button (primary tint)
 *  - [Queued] → spinner + cancel button
 *  - [Downloading] → progress bar + cancel button
 *  - [Paused] → resume (play) + cancel button
 *  - [Error] → error icon + retry + cancel button
 *  - [Downloaded] → green checkmark + delete button
 *
 * Design: compact (36dp touch targets), #B1F256 primary accents, RobotoFamily.
 * Uses [AnimatedContent] for smooth state transitions.
 *
 * @param state The current download state for this episode.
 * @param enabled Whether the control is enabled (false when the download-button
 *   pref is off — the whole control is hidden by the caller in that case).
 * @param onDownload Called when the user taps the download button.
 * @param onCancel Called when the user taps cancel.
 * @param onResume Called when the user taps resume (paused state).
 * @param onRetry Called when the user taps retry (error state).
 * @param onDelete Called when the user taps delete (downloaded state).
 */
@Composable
fun EpisodeDownloadControl(
    state: EpisodeDownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            EpisodeDownloadState.NotDownloaded -> {
                IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Download episode",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            EpisodeDownloadState.Resolving -> {
                // Immediate spinner — the resolve phase (1-3s) before the task
                // is enqueued. Cancelable.
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(4.dp))
                CancelButton(onCancel)
            }

            EpisodeDownloadState.Queued -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(4.dp))
                CancelButton(onCancel)
            }

            is EpisodeDownloadState.Downloading -> {
                // Compact progress indicator (determinate if progress > 0).
                if (state.progress > 0) {
                    LinearProgressIndicator(
                        progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                } else {
                    // Indeterminate (content-length unknown / connecting)
                    LinearProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(width = 40.dp, height = 4.dp),
                    )
                }
                Spacer(Modifier.size(6.dp))
                CancelButton(onCancel)
            }

            EpisodeDownloadState.Paused -> {
                IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Resume download",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                CancelButton(onCancel)
            }

            is EpisodeDownloadState.Error -> {
                IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Retry download",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
                CancelButton(onCancel)
            }

            EpisodeDownloadState.Downloaded -> {
                // Green checkmark (non-interactive) + delete button
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(2.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CancelButton(onCancel: () -> Unit) {
    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Cancel download",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}
