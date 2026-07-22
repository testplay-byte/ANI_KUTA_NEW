package app.confused.anikuta.feature.animedetails

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.component.CustomToggle
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage

/**
 * Episode List Settings Sheet — a bottom-up sheet with:
 * 1. LIVE PREVIEW at the top (shows a sample episode row with current settings)
 * 2. Show/hide toggles (episode number, title, summary, thumbnails, dates, audio pills)
 * 3. Position settings (thumbnail left/right, episode number overlay/badge)
 * 4. Size settings (thumbnail small/medium/large, title lines, synopsis lines)
 *
 * Per design language: dragHandle = null (principle #2), max 70% screen height.
 *
 * All changes apply immediately (live preview updates as the user toggles).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListSettingsSheet(
    prefs: EpisodeDisplayPreferences,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = screenHeight * 0.7f

    // Read current preference values
    var showNumber by remember { mutableStateOf(prefs.showEpisodeNumber().get()) }
    var showTitles by remember { mutableStateOf(prefs.showEpisodeTitles().get()) }
    var showSummaries by remember { mutableStateOf(prefs.showEpisodeSummaries().get()) }
    var showThumbnails by remember { mutableStateOf(prefs.showEpisodeThumbnails().get()) }
    var showDates by remember { mutableStateOf(prefs.showEpisodeDates().get()) }
    var showAudioPills by remember { mutableStateOf(prefs.showAudioPills().get()) }
    var thumbPos by remember { mutableStateOf(prefs.thumbnailPosition().get()) }
    var epNumPos by remember { mutableStateOf(prefs.episodeNumberPosition().get()) }
    var thumbSize by remember { mutableStateOf(prefs.thumbnailSize().get()) }
    var titleLines by remember { mutableStateOf(prefs.titleMaxLines().get()) }
    var synopsisLines by remember { mutableStateOf(prefs.synopsisMaxLines().get()) }

    // Build the display prefs for the preview
    val previewPrefs = EpisodeDisplayPrefs(
        showThumbnails = showThumbnails,
        showTitles = showTitles,
        showSummaries = showSummaries,
        showDates = showDates,
        showEpisodeNumber = showNumber,
        showAudioPills = showAudioPills,
        thumbnailPosition = thumbPos,
        episodeNumberPosition = epNumPos,
        thumbnailSize = thumbSize,
        titleMaxLines = titleLines,
        synopsisMaxLines = synopsisLines,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Episode Display",
                    fontFamily = RobotoFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(32.dp).clickable { onDismiss() },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── LIVE PREVIEW ──
            Text(
                text = "LIVE PREVIEW",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            EpisodeRowPreview(previewPrefs)
            Spacer(Modifier.height(20.dp))

            // ── Show / Hide Toggles ──
            SettingsSectionLabel("Show / Hide")
            ToggleRow("Episode number", showNumber) { v -> showNumber = v; prefs.showEpisodeNumber().set(v) }
            ToggleRow("Episode titles", showTitles) { v -> showTitles = v; prefs.showEpisodeTitles().set(v) }
            ToggleRow("Episode summaries", showSummaries) { v -> showSummaries = v; prefs.showEpisodeSummaries().set(v) }
            ToggleRow("Episode thumbnails", showThumbnails) { v -> showThumbnails = v; prefs.showEpisodeThumbnails().set(v) }
            ToggleRow("Episode dates", showDates) { v -> showDates = v; prefs.showEpisodeDates().set(v) }
            ToggleRow("Audio pills (SUB/DUB)", showAudioPills) { v -> showAudioPills = v; prefs.showAudioPills().set(v) }

            Spacer(Modifier.height(16.dp))

            // ── Positions ──
            SettingsSectionLabel("Positions")
            SegmentedRow(
                label = "Thumbnail position",
                options = listOf("Left" to "left", "Right" to "right"),
                selected = thumbPos,
                onSelect = { v -> thumbPos = v; prefs.thumbnailPosition().set(v) },
            )
            Spacer(Modifier.height(8.dp))
            SegmentedRow(
                label = "Episode number position",
                options = listOf("Overlay" to "overlay", "Badge" to "badge"),
                selected = epNumPos,
                onSelect = { v -> epNumPos = v; prefs.episodeNumberPosition().set(v) },
            )

            Spacer(Modifier.height(16.dp))

            // ── Sizes ──
            SettingsSectionLabel("Sizes")
            SegmentedRow(
                label = "Thumbnail size",
                options = listOf("Small" to "small", "Medium" to "medium", "Large" to "large"),
                selected = thumbSize,
                onSelect = { v -> thumbSize = v; prefs.thumbnailSize().set(v) },
            )
            Spacer(Modifier.height(8.dp))
            SegmentedRow(
                label = "Title max lines",
                options = listOf("1" to 1, "2" to 2, "3" to 3),
                selected = titleLines,
                onSelect = { v -> titleLines = v; prefs.titleMaxLines().set(v) },
            )
            Spacer(Modifier.height(8.dp))
            SegmentedRow(
                label = "Synopsis max lines",
                options = listOf("1" to 1, "2" to 2, "3" to 3, "∞" to 10),
                selected = synopsisLines,
                onSelect = { v -> synopsisLines = v; prefs.synopsisMaxLines().set(v) },
            )
        }
    }
}

// ── Preview component ──

@Composable
private fun EpisodeRowPreview(prefs: EpisodeDisplayPrefs) {
    // Dummy data for the preview
    val demoTitle = "The Dragon's Labyrinth"
    val demoSynopsis = "A young adventurer discovers a hidden labyrinth beneath the ancient city, where a mysterious dragon guards a long-forgotten secret."
    val demoDate = "Mar 15, 2024"
    val demoEpNum = "EP 5"

    val (thumbW, thumbH) = when (prefs.thumbnailSize) {
        "small" -> 100.dp to 56.dp
        "large" -> 160.dp to 90.dp
        else -> 120.dp to 68.dp
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // Thumbnail (left)
                if (prefs.showThumbnails && prefs.thumbnailPosition == "left") {
                    Box {
                        // Gradient placeholder for thumbnail
                        Surface(
                            modifier = Modifier.size(width = thumbW, height = thumbH).clip(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {}
                        if (prefs.showEpisodeNumber && prefs.episodeNumberPosition == "overlay") {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.align(Alignment.TopStart).padding(3.dp),
                            ) {
                                Text(
                                    text = demoEpNum,
                                    fontFamily = RobotoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(10.dp))
                } else if (prefs.showEpisodeNumber && prefs.episodeNumberPosition == "badge") {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                        Text(demoEpNum, fontFamily = RobotoFamily, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                    Spacer(Modifier.size(10.dp))
                }

                // Title
                if (prefs.showTitles) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
                        Text(demoTitle, fontFamily = RobotoFamily, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, maxLines = prefs.titleMaxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                    }
                }

                // Thumbnail (right)
                if (prefs.showThumbnails && prefs.thumbnailPosition == "right") {
                    Spacer(Modifier.size(10.dp))
                    Box {
                        Surface(modifier = Modifier.size(width = thumbW, height = thumbH).clip(RoundedCornerShape(8.dp)), color = MaterialTheme.colorScheme.primaryContainer) {}
                        if (prefs.showEpisodeNumber && prefs.episodeNumberPosition == "overlay") {
                            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.TopEnd).padding(3.dp)) {
                                Text(demoEpNum, fontFamily = RobotoFamily, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                }
            }

            // Date + audio pills
            if (prefs.showDates || prefs.showAudioPills) {
                Spacer(Modifier.size(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (prefs.showDates) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)) {
                            Text(demoDate, fontFamily = RobotoFamily, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    if (prefs.showAudioPills) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("SUB", fontFamily = RobotoFamily, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            // Synopsis
            if (prefs.showSummaries) {
                Spacer(Modifier.size(6.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(demoSynopsis, fontFamily = RobotoFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (prefs.synopsisMaxLines >= 10) Int.MAX_VALUE else prefs.synopsisMaxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                }
            }
        }
    }
}

// ── Shared components ──

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        CustomToggle(checked = checked, onChange = onChange)
    }
}

@Composable
private fun <T> SegmentedRow(
    label: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (label, value) ->
                val isSelected = selected == value
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).clickable { onSelect(value) },
                ) {
                    Text(
                        text = label,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
