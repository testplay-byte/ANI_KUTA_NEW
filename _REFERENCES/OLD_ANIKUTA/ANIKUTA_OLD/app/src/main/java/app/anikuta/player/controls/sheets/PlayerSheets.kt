package app.anikuta.player.controls.sheets

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.player.PlayerViewModel
import app.anikuta.player.VideoTrack
import app.anikuta.source.api.model.Video

// ═══════════════════════════════════════════════════════════════════
// Phase 3.1 — Quality Sheet
// ═══════════════════════════════════════════════════════════════════

/**
 * Quality selection bottom sheet.
 * Lists available video qualities for the current episode.
 * Tap to switch — the Activity reloads the video at the new quality.
 *
 * Two display modes (controlled by qualitySheetDisplayMode preference):
 * - "current": shows only qualities for the current server + audio version
 * - "all": shows all qualities from all servers/audio versions, organized
 *   into sections by server → audio version
 *
 * The current video is highlighted with a checkmark. Selection is based on
 * matching both videoUrl AND videoTitle to handle cases where multiple videos
 * share the same URL.
 */
@Composable
fun QualitySheet(
    videos: List<Video>,
    currentVideoUrl: String,
    currentVideoTitle: String = "",
    currentVideoServer: String = "",
    currentAudioVersion: String = "",
    displayMode: String = "current",
    onSelect: (Video) -> Unit,
    onDismiss: () -> Unit,
) {
    // Parse all videos for filtering and display
    val allParsed = remember(videos) {
        videos.map { app.anikuta.ui.detail.VideoTitleParser.parse(it) }
    }

    PlayerSheet(title = "Quality", onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            if (displayMode == "all") {
                // ---- "All" mode: organize by server → audio version sections ----
                val byServer = allParsed.groupBy { it.server }
                byServer.entries.sortedBy { it.key }.forEach { (serverName, serverVideos) ->
                    // Server section header
                    item(key = "server_header_$serverName") {
                        Text(
                            text = serverName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                    // Group by audio version within this server
                    val byAudio = serverVideos.groupBy { it.audio }
                    byAudio.entries.sortedBy { it.key.ordinal }.forEach { (audio, audioVideos) ->
                        // Audio version subheader
                        item(key = "audio_header_${serverName}_${audio.name}") {
                            Text(
                                text = audio.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        // Quality options (sorted by quality descending)
                        audioVideos.sortedByDescending { it.quality ?: 0 }.forEachIndexed { index, parsed ->
                            item(key = "quality_${serverName}_${audio.name}_$index") {
                                QualityOption(
                                    parsed = parsed,
                                    currentVideoTitle = currentVideoTitle,
                                    onSelect = { onSelect(parsed.video); onDismiss() },
                                )
                            }
                        }
                    }
                }
            } else {
                // ---- "Current" mode: only show qualities for current server + audio ----
                val filtered = allParsed.filter {
                    (currentVideoServer.isBlank() || it.server == currentVideoServer) &&
                    (currentAudioVersion.isBlank() || it.audio.name == currentAudioVersion)
                }
                if (filtered.isEmpty()) {
                    // Fallback: show all if no matches (shouldn't happen normally)
                    itemsIndexed(allParsed, key = { index, _ -> "quality_all_$index" }) { index, parsed ->
                        QualityOption(
                            parsed = parsed,
                            currentVideoTitle = currentVideoTitle,
                            onSelect = { onSelect(parsed.video); onDismiss() },
                        )
                    }
                } else {
                    itemsIndexed(filtered, key = { index, _ -> "quality_current_$index" }) { index, parsed ->
                        QualityOption(
                            parsed = parsed,
                            currentVideoTitle = currentVideoTitle,
                            onSelect = { onSelect(parsed.video); onDismiss() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityOption(
    parsed: app.anikuta.ui.detail.ParsedVideo,
    currentVideoTitle: String,
    onSelect: () -> Unit,
) {
    val qualityLabel = parsed.quality?.let { "${it}p" } ?: "Unknown"
    val subtitle = buildString {
        append(parsed.server)
        append(" • ")
        append(parsed.audio.label)
    }
    // FIX: Highlight by videoTitle (stable across re-resolutions) instead of
    // videoUrl (localhost:PORT changes between resolutions).
    val isSelected = currentVideoTitle.isNotBlank() &&
        parsed.video.videoTitle == currentVideoTitle
    SheetOption(
        title = qualityLabel,
        subtitle = subtitle,
        selected = isSelected,
        onClick = onSelect,
    )
}

// ═══════════════════════════════════════════════════════════════════
// Phase 3.2 — Subtitle Tracks Sheet
// ═══════════════════════════════════════════════════════════════════

/**
 * Subtitle track selection bottom sheet.
 * Lists available subtitle tracks from MPV's track-list.
 * Includes an "Off" option (id = -1).
 *
 * The "Subtitle settings" row opens a SEPARATE sheet that is height-constrained
 * (not full screen) so the video player remains visible behind it. The settings
 * panel scrolls internally if needed.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SubtitleTracksSheet(
    viewModel: PlayerViewModel,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    onApplySettings: () -> Unit = {},
) {
    val tracks by viewModel.subtitleTracks.collectAsState()
    val currentId by viewModel.currentSubtitleId.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        // Subtitle settings panel — height-constrained sheet (not full screen).
        // When the settings sheet is dismissed (tap outside or swipe down), it
        // also dismisses the parent subtitle track sheet — both close together.
        SubtitleSettingsSheet(
            onDismiss = {
                showSettings = false
                onDismiss() // also close the subtitle track sheet
            },
            onApplySettings = onApplySettings,
        )
        return
    }

    PlayerSheet(title = "Subtitles", onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // "Subtitle settings" button at top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSettings = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Subtitle Settings",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // ---- Button-style track selection (chips) ----
            // The "Off" button is visually distinct (outline style) from the
            // language buttons (filled style when selected). This matches the
            // modern streaming-app pattern (YouTube, Netflix, etc.).
            if (tracks.size <= 1) {
                // Only "Off" exists — no real subtitle tracks.
                Text(
                    text = "No subtitles found in this stream.\n" +
                        "The extension may not provide external subtitles for this episode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            } else {
                Text(
                    text = "Subtitle track",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                )
                // Wrap chips in a FlowRow so they reflow on narrow screens.
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tracks.forEach { track ->
                        val isSelected = track.id == currentId
                        if (track.id <= 0) {
                            // "Off" — outline style (secondary action)
                            androidx.compose.material3.AssistChip(
                                onClick = { onSelect(track.id); onDismiss() },
                                label = { Text("Off", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                                leadingIcon = if (isSelected) {
                                    { androidx.compose.material3.Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                ),
                            )
                        } else {
                            // Language track — filled when selected, tonal when not
                            androidx.compose.material3.FilterChip(
                                selected = isSelected,
                                onClick = { onSelect(track.id); onDismiss() },
                                label = { Text(track.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                                leadingIcon = if (isSelected) {
                                    { androidx.compose.material3.Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Phase 3.3 — Audio Tracks Sheet
// ═══════════════════════════════════════════════════════════════════

/**
 * Audio track selection bottom sheet.
 * Lists available audio tracks from MPV's track-list.
 * Includes an "Off" option (id = -1).
 */
@Composable
fun AudioTracksSheet(
    viewModel: PlayerViewModel,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val tracks by viewModel.audioTracks.collectAsState()
    val currentId by viewModel.currentAudioId.collectAsState()

    PlayerSheet(title = "Audio", onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(tracks, key = { it.id }) { track ->
                SheetOption(
                    title = track.name,
                    subtitle = track.language,
                    selected = track.id == currentId,
                    onClick = { onSelect(track.id); onDismiss() },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Phase 3.4 — Server Sheet
// ═══════════════════════════════════════════════════════════════════

/**
 * Server selection bottom sheet.
 * Lists available servers for the current episode.
 */
@Composable
fun ServerSheet(
    servers: List<String>,
    currentServer: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerSheet(title = "Server", onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(servers, key = { it }) { server ->
                SheetOption(
                    title = server,
                    selected = server == currentServer,
                    onClick = { onSelect(server); onDismiss() },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Phase 3.5 — Speed Sheet
// ═══════════════════════════════════════════════════════════════════

/**
 * Playback speed bottom sheet.
 * Slider from 0.25x to 4.0x + preset buttons.
 */
@Composable
fun SpeedSheet(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var speed by remember { mutableFloatStateOf(currentSpeed) }

    PlayerSheet(title = "Playback Speed", onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "%.2fx".format(speed),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Slider(
                value = speed,
                onValueChange = { speed = it },
                onValueChangeFinished = { onSelect(speed) },
                valueRange = 0.25f..4.0f,
                steps = 14, // 0.25 increments
            )
            // Presets
            val presets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            ) {
                presets.forEach { preset ->
                    androidx.compose.material3.TextButton(
                        onClick = {
                            speed = preset
                            onSelect(preset)
                        },
                    ) {
                        Text(
                            "${preset}x",
                            color = if (preset == speed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (preset == speed) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Phase 3.6 — More Options Sheet
// ═══════════════════════════════════════════════════════════════════

/**
 * More options bottom sheet (⋯ button).
 * Advanced settings: subtitle delay, audio delay, screenshot, sleep timer.
 */
@Composable
fun MoreOptionsSheet(
    onSubtitleDelay: () -> Unit,
    onAudioDelay: () -> Unit,
    onScreenshot: () -> Unit,
    onSleepTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerSheet(title = "More Options", onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MoreOptionRow(title = "Subtitle delay", subtitle = "Adjust subtitle timing", onClick = { onSubtitleDelay(); onDismiss() })
            MoreOptionRow(title = "Audio delay", subtitle = "Adjust audio timing", onClick = { onAudioDelay(); onDismiss() })
            MoreOptionRow(title = "Screenshot", subtitle = "Capture current frame", onClick = { onScreenshot(); onDismiss() })
            MoreOptionRow(title = "Sleep timer", subtitle = "Stop playback after N minutes", onClick = { onSleepTimer(); onDismiss() })
        }
    }
}

@Composable
private fun MoreOptionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Subtitle Settings Sheet — height-constrained (not full screen)
// ═══════════════════════════════════════════════════════════════════

/**
 * Height-constrained bottom sheet for subtitle settings.
 *
 * Improvements (player-experiment):
 *  - Max height reduced to 420dp (was 480dp — user said too tall). Tuned so
 *    the key settings are visible without scrolling but the video stays
 *    visible behind the sheet.
 *  - The "Subtitle Settings" title is at the very top-left of the sheet.
 *    Removed the custom drag-handle indicator on the right (user found it
 *    unnecessary) — the sheet is still draggable via the standard
 *    ModalBottomSheet gesture (the system draws its own drag handle at the
 *    very top center of the sheet).
 *
 * The video player remains visible behind the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSettingsSheet(
    onDismiss: () -> Unit,
    onApplySettings: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Increased from 420dp to 450dp — user wanted slightly taller.
                .heightIn(max = 450.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            // Title at the very top-left of the sheet (minimal top padding
            // so it sits right under the system drag handle).
            Text(
                text = "Subtitle Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            // The settings panel scrolls internally
            app.anikuta.player.controls.SubtitleSettingsPanel(
                onSettingsChanged = {
                    onApplySettings()
                    Log.d("PlayerSheets", "Subtitle settings changed — applying live")
                },
            )
        }
    }
}
