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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
 * A bottom sheet for manually searching extension sources for an anime.
 *
 * Shown when the user taps the search icon next to the "Episodes" header
 * (or the "Search manually" button when no source matched). Lets the user:
 *  1. Type a custom query (pre-filled with the anime's display title).
 *  2. Search all trusted extension sources concurrently.
 *  3. Pick the right result from the list — tapping one links it to this
 *     anime and loads its episode list.
 *
 * The sheet shows these states:
 *  - **Haven't searched:** placeholder prompt ("Type a title and tap search")
 *  - **Searching:** spinner ("Searching all sources…")
 *  - **Searched, results found:** scrollable results list
 *  - **Searched, no results, all sources failed:** error cards showing
 *    per-source failure reasons — so the user knows WHY, not just that it failed
 *  - **Searched, no results, sources succeeded:** "No results found" message
 *
 * @param initialQuery the anime's display title (pre-fills the search field).
 * @param isSearching `true` while the search is running (drives the spinner).
 * @param results the current search results (empty until a search completes).
 * @param errors per-source errors from the most recent search. Each pair is
 *   (sourceName, errorMessage). Empty if all sources succeeded.
 * @param hasSearched `true` if at least one search has been performed.
 * @param onManualSearch called when the user taps search (suspend — the VM
 *   updates [results] + [errors] + [isSearching]).
 * @param onLinkManual called when the user taps a result (links source + anime).
 * @param onDismiss called when the sheet is dismissed (swipe-down or back).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSearchSheet(
    initialQuery: String,
    isSearching: Boolean,
    results: List<SourceMatcher.ManualSearchResult>,
    errors: List<Pair<String, String>>,
    hasSearched: Boolean,
    onManualSearch: suspend (String) -> Unit,
    onLinkManual: (SourceMatcher.ManualSearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    // The search query — pre-filled with the anime title, saveable across rotation.
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 640.dp),
        ) {
            // ── Header ──
            Text(
                text = "Search sources manually",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Search every trusted extension for this anime.",
                modifier = Modifier.padding(horizontal = 20.dp),
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── Search field ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Anime title") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val q = query.trim()
                        if (q.isNotBlank()) {
                            keyboard?.hide()
                            scope.launch { onManualSearch(q) }
                        }
                    },
                    enabled = !isSearching && query.isNotBlank(),
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
                    isSearching && results.isEmpty() -> {
                        // First-search loading state
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
                                text = "Searching all sources…",
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    !hasSearched -> {
                        // Haven't searched yet — prompt the user
                        Text(
                            text = "Type a title and tap the search icon.\nResults from every trusted extension will appear here.",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    results.isEmpty() && errors.isNotEmpty() -> {
                        // Searched but ALL sources failed — show per-source errors
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "All sources failed to search.",
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Check the error details below — this usually means the app is missing classes the extension needs.",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
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
                        // Searched, sources succeeded, but no results found
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
                                text = "Try a different title or spelling.",
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
                            // Show per-source errors at the top (if any sources failed)
                            if (errors.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "${errors.size} source(s) failed — showing results from successful sources only.",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                        fontFamily = RobotoFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
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
