package app.anikuta.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 5 task 5.9 — History screen.
 *
 * M3 Expressive UI following the same patterns as HomeScreen:
 *  - FloatingTopBar (statusBarsPadding + surfaceContainerHigh + RoundedCornerShape(20))
 *  - HomeSection-style header (tonal accent bar + title)
 *  - LazyRow for the "Continue Watching" carousel
 *  - Surface cards for list rows
 *
 * The screen reads from [HistoryViewModel], which loads saved watch progress
 * from WatchProgressStore. Tapping any entry calls [onResume] with the
 * (anilistId, episodeUrl, title) — the main agent wires that to launch
 * PlayerActivity at the saved position.
 *
 * @param onResume called when the user taps a continue-watching card or a
 * history row. Defaults to `{}` so the existing NavGraph call `HistoryScreen()`
 * still compiles; the main agent will pass a real callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onResume: (anilistId: Int, episodeUrl: String, title: String) -> Unit = { _, _, _ -> },
    onOpenDetail: (anilistId: Int) -> Unit = {},
) {
    val viewModel: HistoryViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    var showClearAllDialog by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<HistoryEntry?>(null) }

    // --- Clear-all confirmation dialog ---
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear all history?") },
            text = { Text("This removes all watch progress. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearAllDialog = false
                }) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Cancel") }
            },
        )
    }

    // --- Single-entry delete confirmation dialog ---
    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Remove from history?") },
            text = { Text("Remove progress for \"${entry.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearEntry(entry.anilistId, entry.episodeUrl)
                    entryToDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            },
        )
    }

    when (val s = state) {
        is HistoryState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        is HistoryState.Empty -> {
            HistoryEmptyState()
        }
        is HistoryState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Couldn't load history: ${s.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        is HistoryState.Success -> {
            HistoryContent(
                continueWatching = s.continueWatching,
                groups = s.groups,
                onResume = onResume,
                onOpenDetail = onOpenDetail,
                onClearAllClick = { showClearAllDialog = true },
                onEntryLongPress = { entry -> entryToDelete = entry },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryContent(
    continueWatching: List<HistoryEntry>,
    groups: List<HistoryGroup>,
    onResume: (Int, String, String) -> Unit,
    onOpenDetail: (Int) -> Unit,
    onClearAllClick: () -> Unit,
    onEntryLongPress: (HistoryEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 0.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "topbar") {
            HistoryTopBar(onClearAllClick = onClearAllClick)
        }

        // Continue Watching — horizontal carousel of in-progress entries
        if (continueWatching.isNotEmpty()) {
            item(key = "continue_header", contentType = "section_header") {
                HistorySectionHeader("Continue Watching")
            }
            item(key = "continue_row", contentType = "continue_row") {
                ContinueWatchingRow(continueWatching, onResume)
            }
        }

        // Chronological list — grouped by Today / Yesterday / This Week / Earlier
        groups.forEach { group ->
            item(key = "group_header_${group.label}", contentType = "group_header") {
                HistorySectionHeader(group.label)
            }
            items(
                items = group.entries,
                key = { "${it.anilistId}:${it.episodeUrl}" },
                contentType = { "history_entry" },
            ) { entry ->
                HistoryEntryRow(
                    entry = entry,
                    onClick = { onResume(entry.anilistId, entry.episodeUrl, entry.title) },
                    onThumbnailClick = { onOpenDetail(entry.anilistId) },
                    onLongPress = { onEntryLongPress(entry) },
                )
            }
        }
    }
}

/**
 * Floating top bar — mirrors HomeScreen's FloatingTopBar pattern:
 * statusBarsPadding + surfaceContainerHigh + RoundedCornerShape(20).
 * Adds a three-dot overflow menu with "Clear all history".
 */
@Composable
private fun HistoryTopBar(onClearAllClick: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { menuExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Clear all history") },
                        onClick = {
                            menuExpanded = false
                            onClearAllClick()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Section header — mirrors HomeScreen's HomeSection: tonal accent bar + title.
 */
@Composable
private fun HistorySectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Surface(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp),
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {}
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ContinueWatchingRow(
    entries: List<HistoryEntry>,
    onResume: (Int, String, String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            items = entries,
            key = { "${it.anilistId}:${it.episodeUrl}" },
            contentType = { "continue_card" },
        ) { entry ->
            ContinueWatchingCard(entry, onResume)
        }
    }
}

/**
 * Continue-watching card — shows the anime cover image (or a hue-derived
 * placeholder when no cover URL is available), with a gradient overlay,
 * title, "X min left", and a linear progress bar.
 *
 * Phase 2 revamp: now uses real cover images via Coil AsyncImage. Falls back
 * to a hue-derived placeholder + title initials for legacy entries (before
 * cover URLs were saved to WatchProgressStore).
 */
@Composable
private fun ContinueWatchingCard(
    entry: HistoryEntry,
    onResume: (Int, String, String) -> Unit,
) {
    val placeholderColor = remember(entry.anilistId) {
        val hue = (entry.anilistId * 47) % 360
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.55f, 0.55f))
        Color(argb)
    }
    val remainingMin = remember(entry.positionSeconds, entry.durationSeconds) {
        val remaining = (entry.durationSeconds - entry.positionSeconds).coerceAtLeast(0)
        ((remaining + 59) / 60).coerceAtLeast(1)
    }
    // Phase F: prioritize episode thumbnail, fall back to anime cover
    val imageUrl = entry.thumbnailUrl ?: entry.coverUrl

    Card(
        modifier = Modifier
            .width(200.dp)
            .aspectRatio(16f / 9f)  // proper 16:9 ratio (was 160×220dp portrait)
            .clickable { onResume(entry.anilistId, entry.episodeUrl, entry.title) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Episode thumbnail → anime cover → hue placeholder
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = entry.animeTitle ?: entry.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(placeholderColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = entry.title.take(2).uppercase(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            // Bottom gradient for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f),
                            ),
                        ),
                    ),
            )
            // Info column — title, "X min left", progress bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$remainingMin min left",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { entry.progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
        }
    }
}

/**
 * A history list row — Surface card with a 16:9 episode thumbnail (or anime
 * cover fallback), the episode title, a relative timestamp, and a percentage
 * readout.
 *
 * Phase 2 revamp: replaced the play-icon circle with a real thumbnail image.
 * Uses [HistoryEntry.thumbnailUrl] if available, falls back to
 * [HistoryEntry.coverUrl], then to a hue-derived placeholder.
 *
 * Long-press triggers [onLongPress] (with haptic feedback) so the user can
 * remove a single entry without affecting the rest of their history.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryEntryRow(
    entry: HistoryEntry,
    onClick: () -> Unit,
    onThumbnailClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // Fallback placeholder color (for entries without any image URL).
    val placeholderColor = remember(entry.anilistId) {
        val hue = (entry.anilistId * 47) % 360
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.45f, 0.45f))
        Color(argb)
    }
    // Best available image: episode thumbnail → anime cover → placeholder.
    val imageUrl = entry.thumbnailUrl ?: entry.coverUrl

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            ),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 16:9 episode thumbnail (or anime cover fallback) — 72dp wide.
            // Tapping the thumbnail opens the detail page (no auto-play).
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (imageUrl.isNullOrBlank()) placeholderColor else Color.Transparent)
                    .clickable { onThumbnailClick() },
                contentAlignment = Alignment.Center,
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = entry.animeTitle ?: entry.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    relativeTime(entry.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "${(entry.progressFraction * 100).toInt().coerceIn(0, 100)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HistoryEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No watch history yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Watch an episode to see it here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/** "X min ago" / "X hr ago" / "X days ago" / formatted date for older entries. */
private fun relativeTime(updatedAt: Long, now: Long = System.currentTimeMillis()): String {
    val diffSec = (now - updatedAt) / 1000
    return when {
        diffSec < 60 -> "Just now"
        diffSec < 3_600 -> "${diffSec / 60} min ago"
        diffSec < 86_400 -> "${diffSec / 3_600} hr ago"
        diffSec < 604_800 -> "${diffSec / 86_400} days ago"
        else -> historyDateFormat.format(Date(updatedAt))
    }
}

private val historyDateFormat by lazy {
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
}
