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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                TextButton(onClick = { viewModel.clearAllHistory() }) {
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
 * One history row — cover thumbnail, title, episode label, progress bar,
 * relative time. Background is `surfaceVariant.copy(alpha = 0.2f)` (no zebra
 * stripe — all rows use the same background, per the design spec).
 */
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
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

            // Text stack + progress bar.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayTitle,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.episodeLabel,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { entry.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Watched ${formatTimeAgo(entry.progress.updatedAt)}",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
