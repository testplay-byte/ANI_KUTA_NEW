package app.confused.anikuta.feature.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.download.DownloadStatus
import app.confused.anikuta.core.download.DownloadTask
import org.koin.androidx.compose.koinViewModel

/**
 * The Downloads screen — redesigned per the OLD_ANIKUTA's download queue screen.
 *
 * **Design (inspired by OLD_ANIKUTA, adapted to our design language):**
 * - CollapsingHeader with a settings gear + a "Downloaded" icon (only shows
 *   if the user has at least one completed download).
 * - Action bar (bulk operations: pause all, resume all, retry all, cancel all).
 * - Summary chips (downloading / queued / paused / done / failed counts).
 * - Anime-sectioned cards: ONE card per anime, containing the header (accent
 *   bar + anime name + episode count badge) + all episode rows inside.
 * - Episode rows: episode name + info pills (server/audio/quality) + progress
 *   bar + 3-dot menu (pause/resume/cancel/retry/delete).
 * - Completed entries auto-clear after 10 seconds (per owner's request).
 * - Empty state: "No downloads yet" with a hint.
 *
 * **Design language:** #B1F256 primary, RobotoFamily, surfaceVariant cards,
 * RoundedCornerShape(12dp), no indigo/blue.
 */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenDownloaded: () -> Unit = {},
    viewModel: DownloadViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemIndex > 0 ||
        lazyListState.firstVisibleItemScrollOffset > 20

    // ── Request POST_NOTIFICATIONS permission (Android 13+) ──
    // Per the owner's request: request notification permission when the user
    // opens the Downloads page, so download progress notifications work.
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { _ -> }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Derive status counts from the queue
    val queue = state.queue
    val downloading = queue.count { it.status == DownloadStatus.DOWNLOADING }
    val queued = queue.count { it.status == DownloadStatus.QUEUED }
    val paused = queue.count { it.status == DownloadStatus.PAUSED }
    val failed = queue.count { it.status == DownloadStatus.ERROR }
    val hasActive = downloading > 0 || queued > 0

    // Group queue by anime title
    val groupedByAnime = remember(queue) {
        queue.groupBy { it.request.anime.title }.toList()
    }

    // Track which episode's 3-dot menu is open
    var menuTaskId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(
            title = "Downloads",
            collapsed = collapsed,
            actions = {
                // "Downloaded" icon — only shows if the user has completed downloads
                if (state.downloaded.isNotEmpty()) {
                    androidx.compose.material3.IconButton(onClick = onOpenDownloaded) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Downloaded files",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                androidx.compose.material3.IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Download settings",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            },
        )

        // Action bar (bulk operations)
        if (queue.isNotEmpty()) {
            DownloadActionBar(
                hasActive = hasActive,
                hasPaused = paused > 0,
                hasFailed = failed > 0,
                hasAny = queue.isNotEmpty(),
                onPauseAll = { queue.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }.forEach { viewModel.pause(it.id) } },
                onResumeAll = { queue.filter { it.status == DownloadStatus.PAUSED }.forEach { viewModel.resume(it.id) } },
                onRetryAll = { queue.filter { it.status == DownloadStatus.ERROR }.forEach { viewModel.retry(it.id) } },
                onCancelAll = { queue.forEach { viewModel.cancel(it.id) } },
            )
        }

        // Summary chips
        if (queue.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (downloading > 0) StatChip("$downloading", "downloading", MaterialTheme.colorScheme.primary)
                if (queued > 0) StatChip("$queued", "queued", MaterialTheme.colorScheme.onSurfaceVariant)
                if (paused > 0) StatChip("$paused", "paused", MaterialTheme.colorScheme.onSurfaceVariant)
                if (failed > 0) StatChip("$failed", "failed", MaterialTheme.colorScheme.error)
            }
        }

        if (queue.isEmpty() && state.downloaded.isEmpty()) {
            // Full empty state
            DownloadsEmptyStateContent()
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                groupedByAnime.forEach { (animeTitle, downloads) ->
                    item(key = "section_$animeTitle") {
                        AnimeSectionCard(
                            animeTitle = animeTitle,
                            downloads = downloads,
                            onPause = { viewModel.pause(it) },
                            onResume = { viewModel.resume(it) },
                            onCancel = { viewModel.cancel(it) },
                            onRetry = { viewModel.retry(it) },
                            onMenu = { menuTaskId = it },
                        )
                    }
                }
            }
        }
    }

    // 3-dot menu bottom sheet
    if (menuTaskId != null) {
        val task = queue.firstOrNull { it.id == menuTaskId }
        if (task != null) {
            EpisodeMenuSheet(
                task = task,
                onDismiss = { menuTaskId = null },
                onPause = { viewModel.pause(task.id); menuTaskId = null },
                onResume = { viewModel.resume(task.id); menuTaskId = null },
                onCancel = { viewModel.cancel(task.id); menuTaskId = null },
                onRetry = { viewModel.retry(task.id); menuTaskId = null },
            )
        } else {
            menuTaskId = null
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Action bar
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun DownloadActionBar(
    hasActive: Boolean, hasPaused: Boolean, hasFailed: Boolean, hasAny: Boolean,
    onPauseAll: () -> Unit, onResumeAll: () -> Unit, onRetryAll: () -> Unit, onCancelAll: () -> Unit,
) {
    val actions = mutableListOf<Pair<androidx.compose.ui.graphics.vector.ImageVector, () -> Unit>>()
    if (hasActive) actions.add(Icons.Filled.Pause to onPauseAll)
    if (hasPaused) actions.add(Icons.Filled.PlayArrow to onResumeAll)
    if (hasFailed) actions.add(Icons.Filled.Refresh to onRetryAll)
    if (hasAny) actions.add(Icons.Filled.Close to onCancelAll)
    if (actions.isEmpty()) return

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            actions.forEach { (icon, action) ->
                Surface(
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    onClick = action,
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Stat chip
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun StatChip(count: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.12f)) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(count, fontFamily = RobotoFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontFamily = RobotoFamily, fontSize = 10.sp, color = color.copy(alpha = 0.8f))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Anime section card (one card per anime, containing all episode rows)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun AnimeSectionCard(
    animeTitle: String,
    downloads: List<DownloadTask>,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onMenu: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column {
            // Header: accent bar + anime name + episode count badge
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.width(3.dp).height(20.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {}
                Spacer(Modifier.width(10.dp))
                Text(animeTitle, fontFamily = RobotoFamily, fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("${downloads.size}", fontFamily = RobotoFamily, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }

            // Episode rows inside the same card
            downloads.forEachIndexed { index, task ->
                if (index > 0) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                }
                Surface(modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)) {
                    EpisodeRow(task = task, onMenu = { onMenu(task.id) })
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Episode row (inside the anime section card)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun EpisodeRow(task: DownloadTask, onMenu: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // Left: episode info + progress
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp)) {
            // Episode name (not just the number — the actual episode name)
            val epName = task.request.episode.name.ifBlank {
                "Episode ${task.request.episode.episodeNumber.toInt()}"
            }
            Text(epName, fontFamily = RobotoFamily, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)

            // Info pills row: server / audio / quality / size / percentage / status
            // All on ONE row (per owner's request: "queued text should show on
            // the very right of the video server, the quality and such")
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                // Server pill
                if (task.request.videoServer.isNotBlank()) {
                    InfoPill(task.request.videoServer)
                }
                // Audio pill
                if (task.request.videoAudio.isNotBlank()) {
                    InfoPill(task.request.videoAudio.uppercase())
                }
                // Quality pill
                if (task.request.videoQuality.isNotBlank()) {
                    InfoPill(task.request.videoQuality)
                }
                // Size pill (downloaded / total) — in a dedicated pill
                if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED) {
                    val sizeText = if (task.totalBytes > 0)
                        "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}"
                    else formatBytes(task.downloadedBytes)
                    SizePill(sizeText)
                }
                Spacer(Modifier.weight(1f))
                // Right side: percentage or status text (in a pill)
                when (task.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED -> {
                        PercentagePill("${task.progress}%")
                    }
                    DownloadStatus.QUEUED -> {
                        InfoPill("Queued")
                    }
                    DownloadStatus.ERROR -> {
                        ErrorPill("Failed")
                    }
                    DownloadStatus.COMPLETED -> {
                        InfoPill("Done", highlight = true)
                    }
                    else -> {}
                }
            }

            // Progress bar (below the pills row)
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (task.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface,
                )
            }

            // Error message (below the bar)
            if (task.status == DownloadStatus.ERROR) {
                task.errorMessage?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, fontFamily = RobotoFamily, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Right: 3-dot menu button
        Box(modifier = Modifier.padding(top = 6.dp, end = 6.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                onClick = onMenu,
            ) {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** A size pill (downloaded / total) — in a dedicated surface. */
@Composable
private fun SizePill(text: String) {
    Surface(shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface) {
        Text(text, fontFamily = RobotoFamily, fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** A percentage pill — primary-tinted. */
@Composable
private fun PercentagePill(text: String) {
    Surface(shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
        Text(text, fontFamily = RobotoFamily, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** An error pill — error-tinted. */
@Composable
private fun ErrorPill(text: String) {
    Surface(shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)) {
        Text(text, fontFamily = RobotoFamily, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Episode 3-dot menu bottom sheet
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeMenuSheet(
    task: DownloadTask,
    onDismiss: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null, // design principle #2
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(task.request.episode.name, fontFamily = RobotoFamily, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            when (task.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                    MenuOption("Pause", Icons.Filled.Pause) { onPause() }
                    MenuOption("Cancel", Icons.Filled.Close, true) { onCancel() }
                }
                DownloadStatus.PAUSED -> {
                    MenuOption("Resume", Icons.Filled.PlayArrow) { onResume() }
                    MenuOption("Cancel", Icons.Filled.Close, true) { onCancel() }
                }
                DownloadStatus.ERROR -> {
                    MenuOption("Retry", Icons.Filled.Refresh) { onRetry() }
                    MenuOption("Cancel", Icons.Filled.Close, true) { onCancel() }
                }
                else -> {}
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MenuOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isDestructive: Boolean = false, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontFamily = RobotoFamily, fontSize = 14.sp,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Info pill + empty state + helpers
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun InfoPill(text: String, highlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(text, fontFamily = RobotoFamily, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun DownloadsEmptyStateContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(96.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("No downloads yet", fontFamily = RobotoFamily, fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("Download episodes from the anime detail page", fontFamily = RobotoFamily, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}
