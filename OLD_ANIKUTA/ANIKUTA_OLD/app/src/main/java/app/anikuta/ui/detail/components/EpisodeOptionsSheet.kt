package app.anikuta.ui.detail.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.download.Download
import app.anikuta.source.api.model.SEpisode

/**
 * Bottom sheet shown when the user long-presses an episode row.
 *
 * Displays context-appropriate actions based on the episode's current
 * download status and watched state:
 *
 * - **Downloaded**: Play downloaded / Delete download
 * - **Downloading / Queued**: Cancel download
 * - **Paused**: Resume / Cancel
 * - **Error**: Retry / Cancel
 * - **Not downloaded**: Download
 * - **Watched**: Mark as unwatched
 * - **Unwatched**: Mark as watched
 *
 * ## Why this is a separate composable
 *
 * Previously this sheet was rendered inline inside `DetailScreen` using
 * `longPressEpisode?.let { ModalBottomSheet(...) }`. Despite the state being
 * `mutableStateOf`, Compose did not reliably recompose the `?.let` block —
 * likely due to the 800-line function's complex control flow and the
 * recomposition scope boundaries.
 *
 * By extracting the sheet into its own composable function that receives
 * `episode` as a **parameter**, Compose tracks the parameter change and
 * reliably recomposes this composable whenever `episode` transitions from
 * `null` to non-null (or vice versa).
 *
 * @param episode       The selected episode, or `null` to hide the sheet.
 * @param isSeen        Whether the episode is marked as watched.
 * @param downloadState The current download status (null if not downloading).
 * @param isOnDisk      Whether the episode file exists on disk.
 * @param onDismiss     Called when the sheet is dismissed.
 * @param onPlay        Called when "Play downloaded" is tapped.
 * @param onDownload    Called when "Download" / "Retry" / "Resume" is tapped.
 * @param onDelete      Called when "Delete download" is tapped.
 * @param onCancel      Called when "Cancel download" is tapped.
 * @param onMarkSeen    Called when "Mark as watched" is tapped.
 * @param onMarkUnseen  Called when "Mark as unwatched" is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeOptionsSheet(
    episode: SEpisode?,
    isSeen: Boolean,
    downloadState: Download.State?,
    isOnDisk: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onMarkSeen: () -> Unit,
    onMarkUnseen: () -> Unit,
) {
    if (episode == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,  // removes the default white pull bar at the top
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Episode title
            Text(
                text = episode.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))

            // --- Download-related options (state-dependent) ---
            when {
                downloadState == Download.State.DOWNLOADED || isOnDisk -> {
                    SheetOption("Play downloaded", Icons.Default.DownloadDone, onClick = onPlay)
                    SheetOption(
                        "Delete download",
                        Icons.Default.Delete,
                        isDestructive = true,
                        onClick = onDelete,
                    )
                }

                downloadState == Download.State.DOWNLOADING ||
                downloadState == Download.State.QUEUE ||
                downloadState == Download.State.RESOLVING ||
                downloadState == Download.State.MUXING ||
                downloadState == Download.State.RECONNECTING -> {
                    SheetOption(
                        "Cancel download",
                        Icons.Default.Close,
                        isDestructive = true,
                        onClick = onCancel,
                    )
                }

                downloadState == Download.State.PAUSED -> {
                    SheetOption("Resume", Icons.Default.Download, onClick = onDownload)
                    SheetOption(
                        "Cancel download",
                        Icons.Default.Close,
                        isDestructive = true,
                        onClick = onCancel,
                    )
                }

                downloadState == Download.State.ERROR -> {
                    SheetOption("Retry", Icons.Default.Refresh, onClick = onDownload)
                    SheetOption(
                        "Cancel download",
                        Icons.Default.Close,
                        isDestructive = true,
                        onClick = onCancel,
                    )
                }

                else -> {
                    SheetOption("Download", Icons.Default.Download, onClick = onDownload)
                }
            }

            // --- Watched / unwatched toggle ---
            if (isSeen) {
                SheetOption("Mark as unwatched", Icons.Default.VisibilityOff, onClick = onMarkUnseen)
            } else {
                SheetOption("Mark as watched", Icons.Default.Visibility, onClick = onMarkSeen)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * A single tappable row inside the bottom sheet.
 *
 * @param label        The text label.
 * @param icon         The leading icon.
 * @param isDestructive When true, uses the error color for icon and text.
 * @param onClick       Called when the row is tapped.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetOption(
    label: String,
    icon: ImageVector,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .combinedClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
    }
}
