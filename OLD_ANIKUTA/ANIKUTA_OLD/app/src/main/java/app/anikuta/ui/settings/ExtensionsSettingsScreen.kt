package app.anikuta.ui.settings

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import app.anikuta.extension.anime.TrustResult
import app.anikuta.extension.anime.model.AnimeExtension
import app.anikuta.ui.settings.ExtensionsViewModel.SortMode
import coil3.compose.AsyncImage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Phase 7 (enhanced) — Extensions settings screen.
 *
 * Features:
 *  - 3 sections: Sources (trusted, max 2), Installed (untrusted), Available
 *  - Search bar (expands from the right with animation)
 *  - Filter bottom sheet (language filter, sort mode, layout mode)
 *  - List layout (default) and Grid layout (2 columns)
 *  - Compact circular install button (no blue badge)
 *  - Max-2 popup with extension logos + auto-trust after revoke
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsSettingsScreen(
    onBack: () -> Unit,
    onManageRepos: () -> Unit,
    onOpenExtensionDetails: (String) -> Unit = {},
) {
    val viewModel: ExtensionsViewModel = viewModel()
    val sources by viewModel.sources.collectAsState()
    val installed by viewModel.installed.collectAsState()
    val available by viewModel.available.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val downloading by viewModel.downloading.collectAsState()
    val trustResult by viewModel.trustResult.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val enabledLanguages by viewModel.enabledLanguages.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()

    val filteredSources = remember(sources, searchQuery) { viewModel.filteredSources() }
    val filteredInstalled = remember(installed, searchQuery) { viewModel.filteredInstalled() }
    val filteredAvailable = remember(available, searchQuery, enabledLanguages, sortMode) { viewModel.filteredAvailable() }

    var showFilterSheet by remember { mutableStateOf(false) }

    // Max-2-trusted popup
    trustResult?.let { result ->
        if (result is TrustResult.LimitExceeded) {
            MaxTrustedSourcesDialog(
                currentTrusted = result.currentTrusted,
                sourcesList = sources,
                onDismiss = { viewModel.dismissTrustResult() },
                onRevokeAndAutoTrust = { pkgToRevoke ->
                    viewModel.revokeAndAutoTrust(pkgToRevoke)
                },
            )
        }
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            viewModel = viewModel,
            onDismiss = { showFilterSheet = false },
        )
    }

    SettingsSubpageScaffold(
        title = "Extensions",
        onBack = onBack,
        actions = {
            // Action icons always visible — search bar shows BELOW them
            IconButton(onClick = { viewModel.setSearchActive(!isSearchActive) }) {
                Icon(
                    if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (isSearchActive) "Close search" else "Search",
                )
            }
            IconButton(onClick = { showFilterSheet = true }) {
                Icon(Icons.Default.Tune, contentDescription = "Filter")
            }
            IconButton(onClick = onManageRepos) {
                Icon(Icons.Outlined.Public, contentDescription = "Manage repositories")
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search bar — shows below the action icons, slides down smoothly
            AnimatedVisibility(
                visible = isSearchActive,
                enter = expandVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)) + fadeOut(),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search extensions…", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.weight(1f),
            ) {
                ExtensionsListContent(
                    sources = filteredSources,
                    installed = filteredInstalled,
                    available = filteredAvailable,
                    isLoading = isLoading,
                    downloading = downloading,
                    sourcePriority = viewModel.sourcePriority.collectAsState().value,
                    onReorderPriority = { from, to -> viewModel.reorderSourcePriority(from, to) },
                    onOpenDetails = onOpenExtensionDetails,
                    onTrust = { viewModel.trustExtension(it) },
                    onRevoke = { viewModel.revokeTrust(it) },
                    onInstall = { viewModel.installExtension(it) },
                    onUninstall = { viewModel.uninstallExtension(it) },
                    isSearching = isSearchActive && searchQuery.isNotBlank(),
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* List layout                                                         */
/* ------------------------------------------------------------------ */

@Composable
private fun ExtensionsListContent(
    sources: List<AnimeExtension.Installed>,
    installed: List<AnimeExtension.Untrusted>,
    available: List<AnimeExtension.Available>,
    isLoading: Boolean,
    downloading: Set<String>,
    sourcePriority: List<String>,
    onReorderPriority: (Int, Int) -> Unit,
    onOpenDetails: (String) -> Unit,
    onTrust: (AnimeExtension.Untrusted) -> Unit,
    onRevoke: (AnimeExtension.Installed) -> Unit,
    onInstall: (AnimeExtension.Available) -> Unit,
    onUninstall: (String) -> Unit,
    isSearching: Boolean,
) {
    // Sort sources by the priority order (if set)
    val sortedSources = remember(sources, sourcePriority) {
        if (sourcePriority.isEmpty()) sources
        else sources.sortedBy { ext -> sourcePriority.indexOf(ext.pkgName).let { if (it == -1) Int.MAX_VALUE else it } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Sources section — with drag-and-drop priority
        if (sortedSources.isNotEmpty() || !isSearching) {
            item(key = "sources_section") {
                SettingsGroupCard(title = "Sources · ${sortedSources.size}/2") {
                    if (sortedSources.isEmpty()) {
                        EmptySectionBody("No trusted sources. Install an extension, then tap Trust to add it here.")
                    } else {
                        // Drag-and-drop reorderable list for source priority.
                        // Uses a LazyColumn with heightIn(max) so it doesn't
                        // scroll unnecessarily (max 2 items = ~128dp) but
                        // still supports ReorderableItem drag-and-drop.
                        val lazyListState = rememberLazyListState()
                        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                            onReorderPriority(from.index, to.index)
                        }
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = (sortedSources.size * 72).dp),
                            userScrollEnabled = false,  // disable scroll — just for layout
                        ) {
                            items(sortedSources, key = { it.pkgName }) { ext ->
                                ReorderableItem(reorderableState, key = ext.pkgName) {
                                    SourceExtensionRow(
                                        ext = ext,
                                        onClick = { onOpenDetails(ext.pkgName) },
                                        onUntrust = { onRevoke(ext) },
                                        showDragHandle = sortedSources.size > 1,
                                        dragModifier = if (sortedSources.size > 1) Modifier.draggableHandle() else Modifier,
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Installed section
        if (installed.isNotEmpty() || !isSearching) {
            item(key = "installed_section") {
                SettingsGroupCard(title = "Installed · ${installed.size}") {
                    if (installed.isEmpty()) {
                        EmptySectionBody("No untrusted extensions installed.")
                    } else {
                        Column {
                            installed.forEachIndexed { idx, ext ->
                                UntrustedExtensionRow(
                                    ext = ext,
                                    onClick = { onOpenDetails(ext.pkgName) },
                                    onTrust = { onTrust(ext) },
                                    onDelete = { onUninstall(ext.pkgName) },
                                )
                                if (idx < installed.size - 1) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Available section
        if (available.isNotEmpty() || !isSearching) {
            item(key = "available_section") {
                SettingsGroupCard(title = "Available · ${available.size}") {
                    when {
                        isLoading && available.isEmpty() -> LoadingBody()
                        available.isEmpty() -> EmptySectionBody("No extensions found. Tap the globe icon above to add a repository.")
                        else -> {
                            Column {
                                available.forEachIndexed { idx, ext ->
                                    val isInstalled = sources.any { it.pkgName == ext.pkgName } ||
                                        installed.any { it.pkgName == ext.pkgName }
                                    AvailableExtensionRow(
                                        ext = ext,
                                        isInstalled = isInstalled,
                                        isDownloading = ext.pkgName in downloading,
                                        onInstall = { onInstall(ext) },
                                        onUninstall = { onUninstall(ext.pkgName) },
                                    )
                                    if (idx < available.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Filter bottom sheet (M3 Expressive redesign)                        */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    viewModel: ExtensionsViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val enabledLanguages by viewModel.enabledLanguages.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val allLangs = remember { viewModel.allLanguages + setOf("en") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Filter & sort", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }

            // ---- Sort section ----
            item {
                FilterSection(title = "Sort by") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentedOption(
                            label = "Name A-Z",
                            icon = null,
                            selected = sortMode == SortMode.NAME_ASC,
                            onClick = { viewModel.setSortMode(SortMode.NAME_ASC) },
                            modifier = Modifier.weight(1f),
                        )
                        SegmentedOption(
                            label = "Name Z-A",
                            icon = null,
                            selected = sortMode == SortMode.NAME_DESC,
                            onClick = { viewModel.setSortMode(SortMode.NAME_DESC) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ---- Language section ----
            item {
                FilterSection(title = "Languages") {
                    // FlowRow-like layout using Row chunks
                    val langList = allLangs.sorted()
                    langList.chunked(3).forEach { rowLangs ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            rowLangs.forEach { lang ->
                                SegmentedOption(
                                    label = langLabel(lang),
                                    icon = null,
                                    selected = lang in enabledLanguages,
                                    onClick = {
                                        val newSet = if (lang in enabledLanguages) {
                                            enabledLanguages - lang
                                        } else {
                                            enabledLanguages + lang
                                        }
                                        viewModel.setEnabledLanguages(newSet)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - rowLangs.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // Bottom padding
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** A section with a title label + content below. */
@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

/**
 * A segmented-option button — a pill-shaped Surface that fills its weight.
 * Selected state uses secondaryContainer; unselected uses surfaceVariant with
 * lower alpha. Clean M3 look.
 */
@Composable
private fun SegmentedOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Max-2-trusted popup (with logos + auto-trust)                       */
/* ------------------------------------------------------------------ */

@Composable
private fun MaxTrustedSourcesDialog(
    currentTrusted: Set<String>,
    sourcesList: List<AnimeExtension.Installed>,
    onDismiss: () -> Unit,
    onRevokeAndAutoTrust: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sources limit reached") },
        text = {
            Column {
                Text(
                    "You can only have 2 extensions in Sources. Remove one to add the new extension:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                currentTrusted.forEach { pkgName ->
                    val source = sourcesList.find { it.pkgName == pkgName }
                    val displayName = source?.name ?: pkgName.substringAfterLast('.')
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Show the extension logo
                        ExtensionIconSlot(
                            drawable = source?.icon,
                            iconUrl = null,
                            contentDescription = displayName,
                            size = 32,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onRevokeAndAutoTrust(pkgName) }) {
                            Text("Remove")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/* ------------------------------------------------------------------ */
/* List rows                                                           */
/* ------------------------------------------------------------------ */

@Composable
private fun SourceExtensionRow(
    ext: AnimeExtension.Installed,
    onClick: () -> Unit,
    onUntrust: () -> Unit,
    showDragHandle: Boolean = false,
    dragModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drag handle (only shown when there are multiple sources)
        if (showDragHandle) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                modifier = dragModifier
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
        ExtensionIconSlot(drawable = ext.icon, iconUrl = null, contentDescription = ext.name)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ext.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ExtensionMetaRow(ext.versionName, ext.sources.size, ext.lang)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onUntrust) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = "Remove from Sources",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun UntrustedExtensionRow(
    ext: AnimeExtension.Untrusted,
    onClick: () -> Unit,
    onTrust: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionIconSlot(drawable = ext.icon, iconUrl = null, contentDescription = ext.name)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ext.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ExtensionMetaRow(ext.versionName, 0, ext.lang)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onTrust) {
            Icon(Icons.Default.VerifiedUser, contentDescription = "Trust ${ext.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete ${ext.name}", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AvailableExtensionRow(
    ext: AnimeExtension.Available,
    isInstalled: Boolean,
    isDownloading: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete extension?") },
            text = { Text("Do you want to delete ${ext.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onUninstall()
                }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("No") }
            },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isInstalled) { showDeleteDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionIconSlot(drawable = null, iconUrl = ext.iconUrl, contentDescription = ext.name)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ext.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ExtensionMetaRow(ext.versionName, ext.sources.size, ext.lang)
        }
        Spacer(Modifier.width(8.dp))
        when {
            isInstalled -> Icon(
                Icons.Default.Check,
                contentDescription = "Installed — tap to delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            isDownloading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            else -> {
                // Compact circular install button (not a full text button)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onInstall,
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Install ${ext.name}",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionMetaRow(versionName: String, sourceCount: Int, lang: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("v$versionName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (sourceCount > 0) {
            Text("· $sourceCount source${if (sourceCount > 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        lang?.takeIf { it.isNotBlank() }?.let {
            Text("· ${langLabel(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExtensionIconSlot(
    drawable: Drawable?,
    iconUrl: String?,
    contentDescription: String,
    size: Int = 48,
) {
    // Squircle-like shape: RoundedCornerShape with 28% corner radius gives
    // the smooth superellipse look (not a plain circle, not a sharp square).
    val squircleShape = RoundedCornerShape(percent = 28)
    Surface(
        shape = squircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val painter = drawable?.let { remember(it) { it.toPainterOrNull() } }
            when {
                painter != null -> androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = contentDescription,
                    modifier = Modifier.size((size * 0.75f).dp),
                )
                iconUrl != null -> AsyncImage(
                    model = iconUrl,
                    contentDescription = contentDescription,
                    modifier = Modifier.size((size * 0.75f).dp),
                )
                else -> Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size((size * 0.55f).dp),
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Section bodies                                                      */
/* ------------------------------------------------------------------ */

@Composable
private fun EmptySectionBody(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LoadingBody() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Loading extensions from repo…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ------------------------------------------------------------------ */
/* Helpers                                                             */
/* ------------------------------------------------------------------ */

private fun Drawable.toPainterOrNull(): Painter? = try {
    BitmapPainter(this.toBitmap().asImageBitmap())
} catch (e: Exception) {
    null
}

private fun langLabel(code: String): String = when (code.lowercase()) {
    "en" -> "EN"
    "ja" -> "JP"
    "zh" -> "ZH"
    "ko" -> "KO"
    "es" -> "ES"
    "fr" -> "FR"
    "de" -> "DE"
    "pt" -> "PT"
    "pt-br" -> "PT-BR"
    "it" -> "IT"
    "ru" -> "RU"
    "ar" -> "AR"
    "id" -> "ID"
    else -> code.uppercase()
}
