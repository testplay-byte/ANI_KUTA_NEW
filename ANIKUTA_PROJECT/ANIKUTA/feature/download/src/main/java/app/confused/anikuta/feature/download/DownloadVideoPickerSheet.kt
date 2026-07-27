package app.confused.anikuta.feature.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.videoresolver.ResolverServer
import app.confused.anikuta.feature.videoresolver.ResolverVideo

/**
 * A bottom-up sheet that shows the resolved server/audio/quality hierarchy and
 * lets the user pick which video to download (manual mode — when auto-download
 * is OFF or the fallback strategy is ASK).
 *
 * Reuses the resolver's 3-tier model (Server → Audio → Quality). The user
 * expands a server → taps a quality button → the download starts.
 *
 * **Design:** `dragHandle = null` (design principle #2), partial height (the
 * sheet caps at ~70% of the viewport). #B1F256 primary accents, RobotoFamily,
 * surfaceVariant cards. Mirrors the `VideoResolverSheet`'s `ShowContent`.
 *
 * @param servers The resolved server/audio/quality hierarchy.
 * @param animeTitle The anime title (for the sheet header).
 * @param episodeName The episode name (for the sheet header).
 * @param onVideoSelected Called when the user picks a video. Receives the
 *   [ResolverVideo] + the server name + audio label (for the download record).
 * @param onDismiss Called when the user dismisses the sheet (back, scrim tap).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadVideoPickerSheet(
    servers: List<ResolverServer>,
    animeTitle: String,
    episodeName: String,
    onVideoSelected: (ResolverVideo, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expandedServer by remember { mutableStateOf<String?>(servers.firstOrNull()?.name) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null, // Hard rule — design principle #2
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            // Header
            Text(
                text = "Choose video to download",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$animeTitle — $episodeName",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(16.dp))

            // Server accordion (single-expand — mirrors the resolver's ShowContent)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(servers.size) { index ->
                    val server = servers[index]
                    val isExpanded = expandedServer == server.name
                    ServerCard(
                        server = server,
                        isExpanded = isExpanded,
                        onToggle = {
                            expandedServer = if (isExpanded) null else server.name
                        },
                        onVideoSelected = { video, audioLabel ->
                            onVideoSelected(video, server.name, audioLabel)
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerCard(
    server: ResolverServer,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onVideoSelected: (ResolverVideo, String) -> Unit,
) {
    Surface(
        color = if (isExpanded) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Server header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = server.name,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            // Expanded: audio versions + quality buttons
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                ) {
                    server.audioVersions.forEach { audio ->
                        Text(
                            text = audio.label,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            audio.videos.forEach { video ->
                                QualityButton(video) { onVideoSelected(video, audio.label) }
                            }
                        }
                        if (audio != server.audioVersions.last()) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityButton(
    video: ResolverVideo,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = video.quality,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
