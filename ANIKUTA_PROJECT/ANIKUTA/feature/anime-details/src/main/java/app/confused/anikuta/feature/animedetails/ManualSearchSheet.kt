package app.confused.anikuta.feature.animedetails

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * A bottom sheet for manually searching ONE extension source for an anime.
 *
 * **Source selector (top):** A horizontal row of [FilterChip]s showing all
 * available (installed + trusted) sources. The user MUST pick one source
 * before searching — results from only that source are shown. This matches
 * the user's expectation: "pick a source, see only that source's results."
 *
 * **Search bar (below the selector):** A text field pre-filled with the
 * anime's display title + a search icon button. Tapping search calls
 * [onManualSearch] with the selected source's ID + the query.
 *
 * **States:**
 *  - No source selected → prompt to pick a source first
 *  - Source selected, haven't searched → prompt to type + search
 *  - Searching → spinner ("Searching {sourceName}…")
 *  - Searched, results found → scrollable results list
 *  - Searched, source failed → error card with the failure reason
 *  - Searched, no results → "No results found" message
 *
 * @param initialQuery the anime's display title (pre-fills the search field).
 * @param availableSources all installed + trusted sources (for the selector).
 * @param isSearching `true` while the search is running (drives the spinner).
 * @param results the current search results (empty until a search completes).
 * @param errors per-source errors from the most recent search.
 * @param hasSearched `true` if at least one search has been performed.
 * @param onManualSearch called with (sourceId, query) when the user taps search.
 * @param onLinkManual called when the user taps a result (links source + anime).
 * @param onDismiss called when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSearchSheet(
    initialQuery: String,
    availableSources: List<SourceMatcher.SourceInfo>,
    isSearching: Boolean,
    results: List<SourceMatcher.ManualSearchResult>,
    errors: List<Pair<String, String>>,
    hasSearched: Boolean,
    onManualSearch: suspend (Long, String) -> Unit,
    onLinkManual: (SourceMatcher.ManualSearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    // The search query — pre-filled with the anime title, saveable across rotation.
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }

    // The selected source ID — -1 means "no source selected yet".
    // Defaults to the first source if any are available (so the user can search
    // immediately without picking, but can switch via the chips).
    var selectedSourceId by rememberSaveable {
        mutableLongStateOf(availableSources.firstOrNull()?.id ?: -1L)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        // Design language principle #2: "No drag handle on bottom-up menus."
        // The owner explicitly does not want the pill-shaped drag handle at the
        // top of bottom sheets. This is a project-wide rule — always set false.
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 640.dp)
                .padding(top = 20.dp),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Search sources",
                        fontFamily = RobotoFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Pick a source, then search",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Source selector (FilterChip row, above the search bar) ──
            // The user picks ONE source. Only that source is searched.
            if (availableSources.isEmpty()) {
                Text(
                    text = "No trusted sources available. Install an extension from Settings → Extensions first.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(availableSources, key = { it.id }) { source ->
                        FilterChip(
                            selected = source.id == selectedSourceId,
                            onClick = { selectedSourceId = source.id },
                            label = {
                                Text(
                                    text = source.name,
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = if (source.id == selectedSourceId) FontWeight.ExtraBold else FontWeight.Medium,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }

            // ── Search field ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Anime title") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    enabled = selectedSourceId != -1L,
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val q = query.trim()
                        if (q.isNotBlank() && selectedSourceId != -1L) {
                            keyboard?.hide()
                            scope.launch { onManualSearch(selectedSourceId, q) }
                        }
                    },
                    enabled = !isSearching && query.isNotBlank() && selectedSourceId != -1L,
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // ── Results / states ──
            Box(modifier = Modifier.weight(1f)) {
                when {
                    selectedSourceId == -1L -> {
                        Text(
                            text = "Select a source above to start searching.",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    isSearching -> {
                        val sourceName = availableSources.firstOrNull { it.id == selectedSourceId }?.name ?: "source"
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Searching $sourceName…",
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    !hasSearched -> {
                        Text(
                            text = "Type a title and tap the search icon.\nOnly the selected source will be searched.",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    errors.isNotEmpty() -> {
                        // The selected source failed — show the error
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "Search failed.",
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            errors.forEach { (sourceName, error) ->
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Text(
                                            text = sourceName,
                                            fontFamily = RobotoFamily,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        Text(
                                            text = error,
                                            fontFamily = RobotoFamily,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    results.isEmpty() -> {
                        // Searched, source succeeded, but no results found
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "No results found.",
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Try a different title or select a different source.",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(results, key = { "${it.sourceName}_${it.sAnime.url}" }) { result ->
                                ManualSearchResultRow(
                                    result = result,
                                    onTap = {
                                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                                            onLinkManual(result)
                                            onDismiss()
                                        }
                                    },
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
 * One search-result row — thumbnail, title, source name.
 */
@Composable
private fun ManualSearchResultRow(
    result: SourceMatcher.ManualSearchResult,
    onTap: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail (or placeholder)
            val thumb = result.thumbnailUrl
            if (!thumb.isNullOrBlank()) {
                AsyncImage(
                    model = thumb,
                    contentDescription = result.title,
                    modifier = Modifier
                        .width(48.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(width = 48.dp, height = 68.dp),
                ) {}
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.sourceName,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
