package app.anikuta.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.animesource.model.Video
import app.anikuta.source.api.model.SEpisode

/**
 * Phase 7 (revised) — Video picker with Server → Audio → Quality hierarchy.
 *
 * Per user feedback:
 *  - **Servers** are the top-level sections (collapsible).
 *  - Inside each server, **audio versions** (SUB/DUB/HSUB) are expandable sub-sections.
 *  - Inside each audio, **qualities** are listed with the resolution chip on the RIGHT.
 *  - No blue colors — uses surfaceVariant/onSurfaceVariant for audio chips.
 *
 * Uses LazyColumn so the list scrolls when long (fixes the original scroll bug).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPickerSheet(
    state: VideoPickerState,
    expandedServers: Set<String>,
    onToggleServer: (String) -> Unit,
    onPickVideo: (Video, SEpisode) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is VideoPickerState.Resolving -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Resolving video…", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Fetching servers from the extension",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        is VideoPickerState.Cached -> {
            VideoPickerBottomSheet(
                episode = state.episode,
                serverSections = state.serverSections,
                isRefreshing = state.isRefreshing,
                expandedServers = expandedServers,
                onToggleServer = onToggleServer,
                onPickVideo = { video -> onPickVideo(video, state.episode) },
                onDismiss = onDismiss,
            )
        }
        is VideoPickerState.Show -> {
            VideoPickerBottomSheet(
                episode = state.episode,
                serverSections = state.serverSections,
                isRefreshing = false,
                expandedServers = expandedServers,
                onToggleServer = onToggleServer,
                onPickVideo = { video -> onPickVideo(video, state.episode) },
                onDismiss = onDismiss,
            )
        }
        VideoPickerState.Hidden -> { /* nothing */ }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPickerBottomSheet(
    episode: SEpisode,
    serverSections: List<ServerSection>,
    isRefreshing: Boolean,
    expandedServers: Set<String>,
    onToggleServer: (String) -> Unit,
    onPickVideo: (Video) -> Unit,
    onDismiss: () -> Unit,
) {
    // Don't skip partially expanded — allows the sheet to sit at a natural
    // height instead of jumping to full-screen (which caused the auto-close
    // glitch when the user tried to drag it down).
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 24.dp)
                .animateContentSize(),  // smooth height changes when servers expand/collapse
        ) {
            // Header: title + refreshing badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Select quality",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (isRefreshing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Refreshing…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Scrollable list — heightIn(max) so it shrinks when content is
            // small (all collapsed) and grows up to the max when expanded.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                serverSections.forEach { section ->
                    val isExpanded = section.serverName in expandedServers

                    item(key = "server_${section.serverName}") {
                        ServerHeader(
                            serverName = section.serverName,
                            audioTags = section.audioSections.map { it.audio },
                            isExpanded = isExpanded,
                            onClick = { onToggleServer(section.serverName) },
                        )
                    }

                    // Animated expand/collapse for the audio + video rows
                    if (isExpanded) {
                        section.audioSections.forEach { audioSection ->
                            item(key = "audio_${section.serverName}_${audioSection.audio.name}") {
                                AudioSubHeader(
                                    audio = audioSection.audio,
                                    videoCount = audioSection.videos.size,
                                )
                            }
                            items(audioSection.videos, key = { v -> v.videoUrl + v.videoTitle }) { video ->
                                VideoRow(
                                    video = video,
                                    onClick = { onPickVideo(video) },
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Server header — top-level collapsible section. Shows the server name +
 * small audio tag chips (SUB, DUB, HSUB) on the right. Tap to expand/collapse.
 */
@Composable
private fun ServerHeader(
    serverName: String,
    audioTags: List<AudioVersion>,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = serverName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // Audio tag chips — order: HSUB leftmost, DUB middle, SUB rightmost
            // (reversed from the default SUB-first order so SUB is on the right
            // per the user's preference).
            audioTags.reversed().forEach { audio ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                ) {
                    Text(
                        text = audio.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

/**
 * Audio sub-header — shows the audio version (SUB/DUB/HSUB) + video count.
 * Uses surfaceVariant (NOT blue/primary) per user's request.
 */
@Composable
private fun AudioSubHeader(
    audio: AudioVersion,
    videoCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        ) {
            Text(
                text = audio.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$videoCount quality${if (videoCount != 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Video row — quality label on the left (weighted), quality chip on the RIGHT.
 * Only ONE quality representation is shown as text; the chip is the visual
 * indicator. No duplicate plain text.
 */
@Composable
private fun VideoRow(
    video: Video,
    onClick: () -> Unit,
) {
    val parsed = VideoTitleParser.parse(video)
    val qualityLabel = parsed.quality?.let { "${it}p" } ?: "Unknown"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 56.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Play icon on the left
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        // Spacer to push the quality chip to the right
        Spacer(Modifier.weight(1f))
        // Quality chip on the RIGHT side
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = qualityLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}
