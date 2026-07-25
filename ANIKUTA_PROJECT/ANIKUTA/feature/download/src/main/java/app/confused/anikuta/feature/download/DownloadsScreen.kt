package app.confused.anikuta.feature.download

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.download.components.DownloadedAnimeCard
import app.confused.anikuta.feature.download.components.DownloadsEmptyState
import app.confused.anikuta.feature.download.components.QueueRow
import org.koin.androidx.compose.koinViewModel

/**
 * The Downloads screen — queue + downloaded library.
 *
 * Layout (per the implementation prompt + DESIGN_LANGUAGE):
 *  - `CollapsingHeader(title = "Downloads")` (pinned, collapses on scroll).
 *  - **Queue** section (active/pending/paused/errored tasks) — each row shows
 *    cover + title + episode + progress + pause/resume/cancel.
 *  - **Downloaded** section (completed, grouped by anime) — expandable cards
 *    with per-episode delete + delete-all.
 *  - **Empty state**: folder-setup prompt (if no folder) or "No downloads yet".
 *
 * Pull-to-refresh is not needed (state is reactive). The screen uses a single
 * LazyColumn (NO nested LazyColumn — the expanded episode lists are plain
 * Columns, per the hard rules).
 *
 * Design: #B1F256 primary, RobotoFamily, surfaceVariant 0.4f cards,
 * RoundedCornerShape(12dp), section headers ExtraBold 11sp uppercase.
 */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: DownloadViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val collapsed = lazyListState.firstVisibleItemIndex > 0 ||
        lazyListState.firstVisibleItemScrollOffset > 20

    // SAF folder picker launcher.
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.setDownloadFolder(uri.toString())
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CollapsingHeader(
                    title = "Downloads",
                    collapsed = collapsed,
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Download settings",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    },
                )

                if (state.isEmpty) {
                    DownloadsEmptyState(
                        needsFolder = !state.folderReady,
                        onPickFolder = { folderLauncher.launch(null) },
                    )
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 110.dp),
                    ) {
                        // ── Queue section ──
                        if (state.queue.isNotEmpty()) {
                            item(key = "queue_header") {
                                SectionHeader("Queue (${state.queue.size})")
                            }
                            items(state.queue.size, key = { "q_$it" }) { index ->
                                val task = state.queue[index]
                                QueueRow(
                                    task = task,
                                    onPause = { viewModel.pause(task.id) },
                                    onResume = { viewModel.resume(task.id) },
                                    onCancel = { viewModel.cancel(task.id) },
                                    onRetry = { viewModel.retry(task.id) },
                                )
                            }
                        }

                        // ── Downloaded section ──
                        if (state.downloaded.isNotEmpty()) {
                            item(key = "downloaded_header") {
                                SectionHeader("Downloaded (${state.downloaded.values.sumOf { it.size }})")
                            }
                            items(state.downloaded.entries.toList().size, key = { "d_$it" }) { index ->
                                val (key, episodes) = state.downloaded.entries.toList()[index]
                                DownloadedAnimeCard(
                                    key = key,
                                    episodes = episodes,
                                    onDeleteEpisode = { taskId -> viewModel.deleteEpisode(taskId) },
                                    onDeleteAll = { viewModel.deleteAnime(key.anilistId) },
                                )
                            }
                        }

                        // ── Folder-not-set hint (when there's a queue but no folder — rare) ──
                        if (!state.folderReady && state.queue.isEmpty() && state.downloaded.isEmpty()) {
                            item(key = "setup_hint") {
                                DownloadsEmptyState(
                                    needsFolder = true,
                                    onPickFolder = { folderLauncher.launch(null) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A section header — RobotoFamily ExtraBold 11sp uppercase, onSurfaceVariant. */
@Composable
private fun SectionHeader(text: String) {
    androidx.compose.material3.Text(
        text = text.uppercase(),
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.06.sp,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}
