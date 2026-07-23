package app.confused.anikuta.feature.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.common.util.formatPlaybackTimestamp
import app.confused.anikuta.core.common.util.formatTimeAgo
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.component.EmptyState
import app.confused.anikuta.core.designsystem.component.ListSectionHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel

/**
 * The History screen — a chronological list of recently-watched episodes,
 * grouped by day (Today / Yesterday / This Week / Earlier).
 *
 * Data source: [HistoryViewModel] collects `WatchProgressStore.changes`.
 *
 * Per `DESIGN_LANGUAGE/04-screens/history.md` (simplified to the list format
 * the implementation prompt specifies — the carousel is deferred). Each row
 * shows: cover thumbnail, anime title, episode label, a thin progress bar, and
 * a relative "watched X ago" timestamp. Tap → opens the anime detail page.
 *
 * @param onBack Pop the History screen.
 * @param onOpenAnime Open the anime detail page by AniList ID.
 * @param viewModel Injected via Koin.
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenAnime: (Int) -> Unit,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Back gesture → pop the History screen.
    BackHandler(onBack = onBack)

    // Collapsed-state for the header: collapse once the list scrolls past the
    // first item's top offset (same heuristic the Library screen uses).
    val collapsed = listState.firstVisibleItemScrollOffset > 20 || listState.firstVisibleItemIndex > 0

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(
            title = "History",
            collapsed = collapsed,
            actions = {
                if (!state.isEmpty) {
                    IconButton(onClick = { viewModel.showClearConfirm() }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Clear all history",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            },
        )

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Loading…",
                        fontFamily = RobotoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.isEmpty -> {
                EmptyState(
                    title = "No watch history yet",
                    description = "Episodes you watch will show up here.",
                    icon = Icons.Filled.History,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    state.visibleSections.forEach { section ->
                        item(key = "header_${section.name}") {
                            ListSectionHeader(text = section.label)
                        }
                        val entries = state.groupedHistory[section].orEmpty()
                        items(
                            count = entries.size,
                            key = { idx ->
                                entries[idx].anilistId.toString() + ":" + entries[idx].episodeUrl
                            },
                        ) { idx ->
                            val entry = entries[idx]
                            HistoryRow(
                                entry = entry,
                                onClick = { onOpenAnime(entry.anilistId) },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Clear-all confirmation dialog ──
    // Per user request: the Delete button is red + has a button feel (filled
    // Button, not a bare TextButton) so the destructive action is visually
    // distinct from Cancel.
    if (state.showClearConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearConfirm() },
            title = {
                Text(
                    text = "Delete all watch history?",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Text(
                    text = "This will remove every entry from your watch history. This cannot be undone.",
                    fontFamily = RobotoFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = { viewModel.clearAllHistory() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFFE53935),
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Delete", fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearConfirm() }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }
}

/**
 * One history row — cover thumbnail, title, an episode+watched-time pill, a
 * playback timestamp, and a progress bar at the very bottom of the row.
 *
 * Per user feedback (round 2):
 *  - Background is `surfaceVariant.copy(alpha = 0.4f)` — matches the More
 *    entries so it no longer blends into the screen background.
 *  - Title is 16sp Bold for readability.
 *  - Episode + watched-time are combined into a single pill below the title:
 *    "Episode 10 · watched 45m ago".
 *  - A playback timestamp "12:34 / 24:00" shows how far the user watched.
 *    When progress is very small (< 5%), the bar would be too short to sit a
 *    timestamp above cleanly — so the timestamp renders to the RIGHT of the
 *    cover (under the title area) instead of above the bar, avoiding a cramped
 *    sliver above a near-empty bar. When progress >= 5%, the timestamp sits
 *    just above the bottom progress bar (full-width, right-aligned).
 *  - The progress bar is a 5dp strip along the very bottom edge (thicker than
 *    the old 3dp for a better feel), full-width, with rounded ends.
 */
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onClick: () -> Unit,
) {
    val fraction = entry.progressFraction
    // When the bar is a near-empty sliver (< 5%), the timestamp above it would
    // look cramped. Instead, show the timestamp to the right (in the text
    // column) so it has room. Otherwise, show it just above the bottom bar.
    val showTimestampAboveBar = fraction >= 0.05f
    val timestamp = formatPlaybackTimestamp(
        entry.progress.positionSeconds,
        entry.progress.durationSeconds,
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover thumbnail (56×80dp portrait, 8dp rounded).
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    val cover = entry.progress.coverUrl
                    if (!cover.isNullOrEmpty()) {
                        AsyncImage(
                            model = cover,
                            contentDescription = entry.displayTitle,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))

                // Text stack: title + episode/watched-time pill.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.displayTitle,
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // Combined pill: "Episode 10 · watched 45m ago".
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "${entry.episodeLabel} · watched ${formatTimeAgo(entry.progress.updatedAt)}",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    // When the bar is a sliver, show the timestamp here (right
                    // of cover, under the pill) so it isn't cramped above the bar.
                    if (!showTimestampAboveBar && entry.progress.durationSeconds > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = timestamp,
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // When the bar is meaningfully filled, show the timestamp just
            // above the bottom progress bar (right-aligned).
            if (showTimestampAboveBar) {
                Text(
                    text = timestamp,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
            // Progress bar — 5dp strip along the very bottom edge (thicker for
            // a better feel), full-width, with rounded ends via clip.
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}
