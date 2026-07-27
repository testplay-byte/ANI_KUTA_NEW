@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.testplaybyte.animeapp.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.testplaybyte.animeapp.data.LibraryRepository
import com.testplaybyte.animeapp.data.SettingsRepository
import com.testplaybyte.animeapp.model.AppSettings
import com.testplaybyte.animeapp.model.EpisodePosition
import com.testplaybyte.animeapp.model.LibraryItem
import com.testplaybyte.animeapp.model.LibraryLayout
import com.testplaybyte.animeapp.model.LibraryStatus
import com.testplaybyte.animeapp.model.PosterStyle
import com.testplaybyte.animeapp.model.TextPlacement
import com.testplaybyte.animeapp.theme.WarnDark
import com.testplaybyte.animeapp.ui.components.CollapsingHeader
import com.testplaybyte.animeapp.ui.components.NavIcons
import kotlinx.coroutines.launch

private enum class LibTab(val label: String) {
    ALL("All"), WATCHING("Watching"), COMPLETED("Completed"), PLAN("Plan to Watch");

    fun matches(s: LibraryStatus) = when (this) {
        ALL -> true
        WATCHING -> s == LibraryStatus.WATCHING
        COMPLETED -> s == LibraryStatus.COMPLETED
        PLAN -> s == LibraryStatus.PLAN
    }
}

// Folder icon (Lucide-style) drawn inline as an ImageVector — for the
// "Category" button in the selection action bar. material-icons-core has no
// Folder, so we draw it directly instead of pulling in material-icons-extended.
private val FolderIcon: ImageVector get() = ImageVector.Builder(
    name = "Folder",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path {
        moveTo(4f, 4f)
        lineTo(9f, 4f)
        lineTo(11f, 6f)
        lineTo(20f, 6f)
        arcTo(2f, 2f, 0f, false, true, 22f, 8f)
        lineTo(22f, 18f)
        arcTo(2f, 2f, 0f, false, true, 20f, 20f)
        lineTo(4f, 20f)
        arcTo(2f, 2f, 0f, false, true, 2f, 18f)
        lineTo(2f, 6f)
        arcTo(2f, 2f, 0f, false, true, 4f, 4f)
        close()
    }
}.build()

/**
 * LibraryScreen — saved anime with multi-select + customize sheet.
 *
 * Mirrors the web prototype (`library-screen.tsx` + `library-screen.module.css`):
 *   - Pinned `CollapsingHeader("Library")` outside the scroll Column. Gear icon
 *     button (NavIcons.Settings) in the actions slot opens the customize sheet.
 *     In selection mode, the title becomes "N selected" and the gear is hidden.
 *   - Status tabs (All/Watching/Completed/Plan to Watch) — flat row, underline
 *     on active, surfaceVariant bottom border (1dp).
 *   - Grid mode: non-lazy chunked rows of N columns (settings.libraryColumns).
 *     Each card wrapped in a CardCell with long-press (combinedClickable default
 *     ~500ms) for selection mode.
 *   - List mode: vertical list of rows (52×74dp cover left, title+meta+status right).
 *   - Selection mode: checkmark circles on selected cards, floating bottom action
 *     bar (Cancel / Category / Delete) positioned ABOVE the floating nav.
 *   - Customize sheet: ModalBottomSheet (no drag handle — scrim tap closes) with
 *     sections for Layout, Columns, Text placement, Cover details, Episode badge
 *     position.
 *   - Category sheet: ModalBottomSheet showing current categories + move-to options.
 *   - Confirm-delete AlertDialog.
 *   - Empty state: book icon (NavIcons.Library) + "Your library is empty" + desc.
 *   - 110dp bottom padding for the floating nav bar.
 *
 * Layout uses `verticalScroll + Column + chunked(N).forEach { Row }` rather than
 * LazyVerticalGrid — required because CollapsingHeader takes a `ScrollState`
 * (which only works with the `verticalScroll` modifier, not lazy lists).
 */
@Composable
fun LibraryScreen(
    onOpenAnime: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val libraryRepo = remember { LibraryRepository(context) }
    val settingsRepo = remember { SettingsRepository(context) }
    val scrollState = rememberScrollState()

    val items by libraryRepo.items.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    var activeTab by remember { mutableStateOf(LibTab.ALL) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Int>() }
    var showCustomize by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<List<Int>?>(null) }

    val filtered = remember(items, activeTab) {
        if (activeTab == LibTab.ALL) items else items.filter { activeTab.matches(it.status) }
    }

    fun toggleSelect(id: Int) {
        if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
        showCategoryMenu = false
    }

    // Outer Box lets the floating selection action bar overlay the content.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Pinned collapsing header — sits OUTSIDE the scroll Column.
            CollapsingHeader(
                title = if (selectionMode) "${selectedIds.size} selected" else "Library",
                scrollState = scrollState,
                actions = {
                    // Gear button — surfaceVariant circle, NavIcons.Settings icon.
                    // Hidden in selection mode (per prototype).
                    if (!selectionMode) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            onClick = { showCustomize = true },
                            modifier = Modifier.size(38.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = NavIcons.Settings,
                                    contentDescription = "Customize library",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                },
            )

            // Scrollable content (tabs + grid/list).
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(bottom = 110.dp),
            ) {
                // Status tabs (normal mode) OR select-all/clear bar (selection mode).
                // Both scroll with the content (per prototype layout).
                if (!selectionMode) {
                    val tabCounts = remember(items) {
                        mapOf(
                            LibTab.ALL to items.size,
                            LibTab.WATCHING to items.count { it.status == LibraryStatus.WATCHING },
                            LibTab.COMPLETED to items.count { it.status == LibraryStatus.COMPLETED },
                            LibTab.PLAN to items.count { it.status == LibraryStatus.PLAN },
                        )
                    }
                    StatusTabRow(active = activeTab, onSelect = { activeTab = it }, counts = tabCounts)
                } else {
                    SelectBar(
                        onSelectAll = {
                            selectedIds.clear()
                            selectedIds.addAll(filtered.map { it.id })
                        },
                        onClear = { selectedIds.clear() },
                    )
                }

                when {
                    filtered.isEmpty() -> LibraryEmptyState(
                        title = if (items.isEmpty()) "Your library is empty"
                        else "No ${activeTab.label.lowercase()} items",
                        description = if (items.isEmpty())
                            "Browse anime and add them to your library."
                        else "Try switching to another category.",
                    )
                    settings.libraryLayout == LibraryLayout.GRID -> LibraryGridLayout(
                        items = filtered,
                        settings = settings,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onTap = { id ->
                            if (selectionMode) toggleSelect(id) else onOpenAnime(id)
                        },
                        onLongPress = { id ->
                            if (!selectionMode) {
                                selectionMode = true
                                selectedIds.clear()
                                selectedIds.add(id)
                            }
                        },
                    )
                    else -> LibraryListLayout(
                        items = filtered,
                        settings = settings,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onTap = { id ->
                            if (selectionMode) toggleSelect(id) else onOpenAnime(id)
                        },
                        onLongPress = { id ->
                            if (!selectionMode) {
                                selectionMode = true
                                selectedIds.clear()
                                selectedIds.add(id)
                            }
                        },
                    )
                }
            }
        }

        // ── Floating selection action bar ───────────────────────────────────
        // Positioned ABOVE the floating bottom nav (which sits at bottom=16dp
        // with 58dp height → top at bottom=74dp; action bar at bottom=90dp
        // leaves a 16dp gap above the nav).
        if (selectionMode) {
            SelectionActionBar(
                selectedCount = selectedIds.size,
                onCancel = { exitSelection() },
                onCategory = { showCategoryMenu = true },
                onDelete = { pendingRemove = selectedIds.toList() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 90.dp),
            )
        }
    }

    // ── Customize sheet ──────────────────────────────────────────────────────
    if (showCustomize) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCustomize = false },
            sheetState = sheetState,
            dragHandle = null, // no dismiss handle — scrim tap closes.
        ) {
            CustomizeSheetContent(
                settings = settings,
                onUpdate = { patch -> scope.launch { settingsRepo.update(patch) } },
                onDone = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) showCustomize = false
                    }
                },
            )
        }
    }

    // ── Category sheet (move selected items to another status) ──────────────
    if (showCategoryMenu) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCategoryMenu = false },
            sheetState = sheetState,
            dragHandle = null,
        ) {
            CategorySheetContent(
                selectedItems = items.filter { it.id in selectedIds },
                onSelect = { status ->
                    scope.launch {
                        libraryRepo.setStatusForIds(selectedIds.toSet(), status)
                        exitSelection()
                    }
                },
            )
        }
    }

    // ── Confirm-delete dialog ────────────────────────────────────────────────
    pendingRemove?.let { toRemove ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove ${toRemove.size} anime?") },
            text = { Text("These will be removed from your library.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        libraryRepo.remove(toRemove.toSet())
                        exitSelection()
                    }
                    pendingRemove = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Cancel") }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StatusTabRow — flat row of tabs with underline on active.
// Matches the prototype: no card background, 1px bottom divider,
// active tab has primary text + 2px primary bottom border,
// inactive tabs have onSurfaceVariant text + transparent border.
// Each tab shows its item count in parentheses.

@Composable
private fun StatusTabRow(active: LibTab, onSelect: (LibTab) -> Unit, counts: Map<LibTab, Int>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            LibTab.values().forEach { tab ->
                val isActive = tab == active
                val count = counts[tab] ?: 0
                Column(
                    modifier = Modifier
                        .combinedClickable(onClick = { onSelect(tab) })
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tab.label,
                            fontSize = 14.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        if (count > 0) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = count.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // Underline — matches prototype: 2px thick, full tab width
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(2.dp)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                            ),
                    )
                }
            }
        }
        // Row's 1dp bottom divider (surface-3 look).
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(Modifier.height(12.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SelectBar — Select all / Clear pills (shown in selection mode).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SelectBar(onSelectAll: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SelectPill(text = "Select all", onClick = onSelectAll)
        SelectPill(text = "Clear", onClick = onClear)
    }
}

@Composable
private fun SelectPill(text: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
        onClick = onClick,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LibraryEmptyState — book icon (NavIcons.Library) + title + description.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryEmptyState(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            // NavIcons.Library — book icon, matches prototype's empty-state SVG.
            Icon(
                imageVector = NavIcons.Library,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LibraryGridLayout — non-lazy chunked-rows grid (N columns from settings).
// Each card wrapped in CardCell with combinedClickable for long-press selection.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryGridLayout(
    items: List<LibraryItem>,
    settings: AppSettings,
    selectionMode: Boolean,
    selectedIds: List<Int>,
    onTap: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
) {
    val cols = settings.libraryColumns.coerceIn(2, 5)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.chunked(cols).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                rowItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        LibraryGridCard(
                            item = item,
                            settings = settings,
                            selected = item.id in selectedIds,
                            selectionMode = selectionMode,
                            onClick = { onTap(item.id) },
                            onLongPress = { onLongPress(item.id) },
                        )
                    }
                }
                // Fill remaining slots so columns stay aligned.
                repeat(cols - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LibraryGridCard — single grid card with library-specific styling.
//   - Cover with aspectRatio 2/3, rounded per posterStyle setting.
//   - Episode badge (corner depends on settings.libraryEpisodePosition).
//   - Optional overlay text (settings.libraryTextPlacement == OVERLAY) with
//     bottom gradient.
//   - Below-cover text (default): title (12sp SemiBold) + format/episodes meta.
//   - Selection checkmark circle (top-right) when in selection mode.
//   - Selected card scales to 0.93 + alpha 0.7 (matches prototype's .cardWrapSelected).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryGridCard(
    item: LibraryItem,
    settings: AppSettings,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val cornerRadius = when (settings.posterStyle) {
        PosterStyle.ROUNDED -> 12.dp
        PosterStyle.SOFT -> 6.dp
        PosterStyle.SHARP -> 2.dp
    }

    Box(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongPress,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (selected) 0.7f else 1f),
        ) {
            // ── Cover box ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = item.cover,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                // Episode badge (corner depends on settings.libraryEpisodePosition).
                if (settings.libraryShowEpisodes && item.episodes != null &&
                    settings.libraryEpisodePosition != EpisodePosition.HIDDEN
                ) {
                    val badgeAlign = when (settings.libraryEpisodePosition) {
                        EpisodePosition.TOP_LEFT -> Alignment.TopStart
                        EpisodePosition.TOP_RIGHT -> Alignment.TopEnd
                        EpisodePosition.BOTTOM_LEFT -> Alignment.BottomStart
                        EpisodePosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                        EpisodePosition.HIDDEN -> Alignment.TopEnd // unused
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(badgeAlign)
                            .padding(4.dp),
                    ) {
                        Text(
                            text = "EP ${item.episodes}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 0.02.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }

                // Overlay text placement — title at bottom with vertical gradient.
                if (settings.libraryTextPlacement == TextPlacement.OVERLAY) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.6f to Color.Black.copy(alpha = 0.4f),
                                    1f to Color.Black.copy(alpha = 0.88f),
                                ),
                            )
                            .padding(top = 28.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
                    ) {
                        Text(
                            text = item.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Selection checkmark — top-right circle, primary when selected.
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color.Black.copy(alpha = 0.5f),
                            )
                            .border(
                                width = 2.dp,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else Color.White.copy(alpha = 0.4f),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            // Below-cover text (only when placement == BELOW).
            if (settings.libraryTextPlacement == TextPlacement.BELOW) {
                Text(
                    text = item.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = if (settings.singleLineTitles) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                )
                val meta = buildList {
                    if (settings.libraryShowFormat && item.format != null) add(item.format)
                    if (settings.libraryShowEpisodes && item.episodes != null) add("${item.episodes} ep")
                }.joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LibraryListLayout — vertical Column of list rows.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryListLayout(
    items: List<LibraryItem>,
    settings: AppSettings,
    selectionMode: Boolean,
    selectedIds: List<Int>,
    onTap: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            LibraryListRow(
                item = item,
                settings = settings,
                selected = item.id in selectedIds,
                selectionMode = selectionMode,
                onClick = { onTap(item.id) },
                onLongPress = { onLongPress(item.id) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LibraryListRow — single list row.
//   - Container: surfaceVariant tinted background, 10dp corners, 8dp padding.
//   - Cover: 52×74dp, 4dp corners.
//   - Title: 14sp SemiBold, 1 line.
//   - Meta row: format / ep / score (★ amber). 11sp.
//   - Status: 11sp Bold primary ("Watching" / "Completed" / "Plan to Watch").
//   - Selected row: alpha 0.6.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryListRow(
    item: LibraryItem,
    settings: AppSettings,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(8.dp)
            .alpha(if (selected) 0.6f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Cover (52×74dp) ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 74.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = item.cover,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Selection checkmark on the cover (top-right).
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.Black.copy(alpha = 0.5f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))

        // ── Title + meta + status ────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            // Meta row — format, ep count, score (★ amber).
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (settings.libraryShowFormat && item.format != null) {
                    Text(
                        text = item.format,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (settings.libraryShowEpisodes && item.episodes != null) {
                    Text(
                        text = "${item.episodes} ep",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.score != null) {
                    // \u2605 = ★ BLACK STAR (Unicode symbol, not an emoji).
                    Text(
                        text = "\u2605 ${"%.1f".format(item.score / 10.0)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WarnDark,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            // Status label — Watching / Completed / Plan to Watch (primary, bold).
            Text(
                text = when (item.status) {
                    LibraryStatus.WATCHING -> "Watching"
                    LibraryStatus.COMPLETED -> "Completed"
                    LibraryStatus.PLAN -> "Plan to Watch"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SelectionActionBar — floating pill with Cancel / Category / Delete.
// 58dp tall, RoundedCornerShape(28dp), surfaceVariant background, 8dp shadow.
// Delete is rendered in error color. Disabled (alpha 0.35) when no selection.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onCategory: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton(
                icon = Icons.Filled.Close,
                label = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                icon = FolderIcon,
                label = "Category",
                onClick = onCategory,
                enabled = selectedCount > 0,
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                icon = Icons.Filled.Delete,
                label = "Delete",
                onClick = onDelete,
                enabled = selectedCount > 0,
                isDanger = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDanger: Boolean = false,
) {
    val color = if (isDanger) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxHeight()
            .combinedClickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CustomizeSheetContent — ModalBottomSheet body for library display options.
//   - Title "Customize Library" (18sp bold).
//   - Sections (uppercase 11sp bold labels):
//       1. Layout: Grid | List (segmented)
//       2. Columns per row (grid only): 2 | 3 | 4 | 5 (segmented)
//       3. Text placement (grid only): Below cover | On cover (segmented)
//       4. Cover details: Show format toggle + Show episode count toggle
//       5. Episode badge position (grid only, only if showEpisodes):
//          Top L | Top R | Bot L | Bot R | Off (segmented)
//   - "Done" button at the bottom (48dp pill, primary background, onPrimary text).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CustomizeSheetContent(
    settings: AppSettings,
    onUpdate: (AppSettings.() -> AppSettings) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Text(
            text = "Customize Library",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 20.dp),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 1. Layout
            CustomizeSection(label = "Layout") {
                Segmented(
                    options = listOf("Grid" to LibraryLayout.GRID, "List" to LibraryLayout.LIST),
                    selected = settings.libraryLayout,
                    onSelect = { v -> onUpdate { copy(libraryLayout = v) } },
                )
            }

            // 2. Columns per row (grid only)
            if (settings.libraryLayout == LibraryLayout.GRID) {
                CustomizeSection(label = "Columns per row") {
                    Segmented(
                        options = listOf("2" to 2, "3" to 3, "4" to 4, "5" to 5),
                        selected = settings.libraryColumns,
                        onSelect = { v -> onUpdate { copy(libraryColumns = v) } },
                    )
                }
            }

            // 3. Text placement (grid only)
            if (settings.libraryLayout == LibraryLayout.GRID) {
                CustomizeSection(label = "Text placement") {
                    Segmented(
                        options = listOf(
                            "Below cover" to TextPlacement.BELOW,
                            "On cover" to TextPlacement.OVERLAY,
                        ),
                        selected = settings.libraryTextPlacement,
                        onSelect = { v -> onUpdate { copy(libraryTextPlacement = v) } },
                    )
                }
            }

            // 4. Cover details
            CustomizeSection(label = "Cover details") {
                Column {
                    CustomizeToggleRow(
                        label = "Show format (TV / Movie / OVA)",
                        checked = settings.libraryShowFormat,
                        onChange = { v -> onUpdate { copy(libraryShowFormat = v) } },
                        showDivider = true,
                    )
                    CustomizeToggleRow(
                        label = "Show episode count",
                        checked = settings.libraryShowEpisodes,
                        onChange = { v -> onUpdate { copy(libraryShowEpisodes = v) } },
                    )
                }
            }

            // 5. Episode badge position (grid only, only if showEpisodes)
            if (settings.libraryLayout == LibraryLayout.GRID && settings.libraryShowEpisodes) {
                CustomizeSection(label = "Episode badge position") {
                    Segmented(
                        options = listOf(
                            "Top L" to EpisodePosition.TOP_LEFT,
                            "Top R" to EpisodePosition.TOP_RIGHT,
                            "Bot L" to EpisodePosition.BOTTOM_LEFT,
                            "Bot R" to EpisodePosition.BOTTOM_RIGHT,
                            "Off" to EpisodePosition.HIDDEN,
                        ),
                        selected = settings.libraryEpisodePosition,
                        onSelect = { v -> onUpdate { copy(libraryEpisodePosition = v) } },
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Done button — full-width pill, primary background.
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = "Done",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CategorySheetContent — ModalBottomSheet for moving selected items to a status.
//   - "Current" section: chips showing the current categories of selected items.
//   - Divider.
//   - "Move to category" header.
//   - List of options (Watching / Completed / Plan to Watch) with a primary dot.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategorySheetContent(
    selectedItems: List<LibraryItem>,
    onSelect: (LibraryStatus) -> Unit,
) {
    val currentStatuses = selectedItems.map { it.status }.distinct()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        // Current categories section.
        Text(
            text = "Current".uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.06.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            if (currentStatuses.isEmpty()) {
                Text(
                    text = "None",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                )
            } else {
                currentStatuses.forEach { status ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = statusLabel(status),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Text(
            text = "Move to category",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                LibraryStatus.WATCHING to "Watching",
                LibraryStatus.COMPLETED to "Completed",
                LibraryStatus.PLAN to "Plan to Watch",
            ).forEach { (status, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(onClick = { onSelect(status) })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Primary dot indicator.
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

private fun statusLabel(s: LibraryStatus): String = when (s) {
    LibraryStatus.WATCHING -> "Watching"
    LibraryStatus.COMPLETED -> "Completed"
    LibraryStatus.PLAN -> "Plan to Watch"
}

// ─────────────────────────────────────────────────────────────────────────────
// CustomizeSection — small uppercase label above its content.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CustomizeSection(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.06.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Segmented — generic pill segmented control.
// Surface with surfaceVariant tinted background + 4dp padding. Each option is
// weight(1f), 36dp tall. Active: primary background + onPrimary text + 2dp
// shadow. Inactive: transparent background + onSurfaceVariant text.
// Matches SettingsScreen's TextSelector pattern.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun <T> Segmented(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (label, value) ->
                val active = selected == value
                val bg = if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                val fg = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .then(
                            if (active) Modifier.shadow(
                                elevation = 2.dp,
                                shape = RoundedCornerShape(8.dp),
                                clip = true,
                            ) else Modifier.clip(RoundedCornerShape(8.dp)),
                        )
                        .background(bg)
                        .combinedClickable(onClick = { onSelect(value) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = fg,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CustomizeToggleRow — label + custom toggle, with optional divider.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CustomizeToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    showDivider: Boolean = false,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            CustomToggle(checked = checked, onChange = onChange)
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CustomToggle — 44×26dp pill, 20×20dp knob. Off: surfaceVariant track +
// onSurfaceVariant knob. On: primary track + onPrimary knob. Knob translates
// 18dp right (from 2dp to 20dp) with an emphasized ease.
// NOT the default Material Switch — matches the prototype's .toggle / .toggleKnob.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CustomToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val knobStart by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "toggleKnobStart",
    )
    val trackColor = if (checked) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant
    val knobColor = if (checked) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(trackColor)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(13.dp),
            )
            .combinedClickable(onClick = { onChange(!checked) }),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = knobStart, top = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(knobColor),
        )
    }
}
