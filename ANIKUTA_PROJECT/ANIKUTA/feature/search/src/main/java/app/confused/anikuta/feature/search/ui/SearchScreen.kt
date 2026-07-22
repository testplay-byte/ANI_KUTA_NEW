@file:OptIn(ExperimentalMaterial3Api::class)

package app.confused.anikuta.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.data.extension.AnimeExtensionManager
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import app.confused.anikuta.feature.search.data.RecentSearchesStore
import app.confused.anikuta.feature.search.viewmodel.SearchResult
import app.confused.anikuta.feature.search.viewmodel.SearchSource
import app.confused.anikuta.feature.search.viewmodel.SearchViewModel
import app.confused.anikuta.feature.search.viewmodel.SearchUiState

/**
 * The Search screen — a dual-source search experience.
 *
 * Top bar (collapsing): title + AniList/Extension source toggle + search bar.
 * Below: quick row (Filters + Sort), then scrollable content:
 *   - AniList: RecentSearchesCard (when no query) + ResultsCard (results grid).
 *   - Extension: ExtensionResultsView (Popular + Latest rows) when no query,
 *     or ResultsCard (search results grid) when a query is typed.
 *
 * Tapping an AniList result → [onOpenAnime] (opens the existing detail page).
 * Tapping an Extension result → [onOpenExtensionResult] (starts the linking
 * flow — see ExtensionLinkingSheet, added in Phase D).
 *
 * @param anilistApi the AniList GraphQL client.
 * @param extensionManager provides installed + trusted sources.
 * @param sourceMatcher searches extension sources by query.
 * @param recentsStore persists recent search strings.
 * @param onOpenAnime called with an AniList anime ID when an AniList result is tapped.
 * @param onOpenExtensionResult called when an extension result is tapped —
 *   the caller (MainActivity) shows the linking sheet.
 */
@Composable
fun SearchScreen(
    anilistApi: AniListApi,
    extensionManager: AnimeExtensionManager,
    sourceMatcher: SourceMatcher,
    recentsStore: RecentSearchesStore,
    onOpenAnime: (Int) -> Unit,
    onOpenExtensionResult: (SearchResult.Extension) -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    val vm: SearchViewModel = viewModel(
        key = "search_screen",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(
                anilistApi = anilistApi,
                extensionManager = extensionManager,
                sourceMatcher = sourceMatcher,
                recentsStore = recentsStore,
            ) as T
        },
    )

    val state by vm.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }

    SearchContent(
        state = state,
        onQueryChange = vm::onQueryChange,
        onClearQuery = { vm.onQueryChange("") },
        onSourceSelect = vm::onSourceChange,
        onSourceRetap = { showSourcePicker = true },
        onSubmit = { /* IME search — debounce handles it */ },
        onOpenFilters = { showFilterSheet = true },
        onSortChange = vm::onSortChange,
        onPickRecent = vm::onPickRecent,
        onRemoveRecent = vm::onRemoveRecent,
        onClearRecents = vm::onClearRecents,
        onResultTap = { result ->
            when (result) {
                is SearchResult.AniList -> onOpenAnime(result.id)
                is SearchResult.Extension -> onOpenExtensionResult(result)
            }
        },
        showSourcePicker = showSourcePicker,
        onPickExtensionSource = { id ->
            vm.onPickExtensionSource(id)
            showSourcePicker = false
        },
        onDismissSourcePicker = { showSourcePicker = false },
    )

    // Filter sheet
    FilterSheet(
        show = showFilterSheet,
        filters = state.filters,
        sort = state.sort,
        onFiltersChange = vm::onFiltersChange,
        onSortChange = vm::onSortChange,
        onClearAll = vm::onClearFilters,
        onApply = { showFilterSheet = false },
        onDismiss = { showFilterSheet = false },
    )
}

@Composable
private fun SearchContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSourceSelect: (SearchSource) -> Unit,
    onSourceRetap: () -> Unit,
    onSubmit: () -> Unit,
    onOpenFilters: () -> Unit,
    onSortChange: (String) -> Unit,
    onPickRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onResultTap: (SearchResult) -> Unit,
    showSourcePicker: Boolean,
    onPickExtensionSource: (Long) -> Unit,
    onDismissSourcePicker: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val collapsed = scrollState.value > 20

    Column(modifier = Modifier.fillMaxSize()) {
        SearchTopBar(
            collapsed = collapsed,
            query = state.query,
            onQueryChange = onQueryChange,
            onClearQuery = onClearQuery,
            source = state.source,
            onSourceSelect = onSourceSelect,
            onSourceRetap = onSourceRetap,
            onSubmit = onSubmit,
            activeFilterCount = state.filters.activeCount,
            onOpenFilters = onOpenFilters,
            sort = state.sort,
            onSortChange = onSortChange,
        )

        // Extension source picker (shown when the user re-taps the Extension toggle)
        ExtensionSourcePicker(
            expanded = showSourcePicker,
            sources = state.availableExtensionSources,
            selectedId = state.selectedExtensionSourceId,
            onPick = onPickExtensionSource,
            onDismiss = onDismissSourcePicker,
        )

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 110.dp), // floating nav clearance
        ) {
            val sectionLabel = when {
                state.query.isNotBlank() && state.source == SearchSource.ANILIST ->
                    "Results for \"${state.query}\""
                state.query.isNotBlank() && state.source == SearchSource.EXTENSION ->
                    "Extension results for \"${state.query}\""
                state.source == SearchSource.EXTENSION -> "Extension"
                else -> "Popular anime"
            }

            // Recent searches (only when AniList, blank query, no filters, recents exist)
            val showRecent = state.source == SearchSource.ANILIST &&
                state.query.isBlank() &&
                state.filters.activeCount == 0 &&
                state.recents.isNotEmpty()
            if (showRecent) {
                RecentSearchesCard(
                    recents = state.recents,
                    onPick = onPickRecent,
                    onRemove = onRemoveRecent,
                    onClear = onClearRecents,
                )
            }

            if (state.source == SearchSource.EXTENSION && state.query.isBlank()) {
                // Extension default view: Popular + Latest rows.
                ExtensionResultsView(
                    loading = state.loading,
                    error = state.error,
                    rows = state.extensionRows,
                    onResultTap = onResultTap,
                )
            } else {
                // Results grid (AniList search OR extension search).
                ResultsCard(
                    sectionLabel = sectionLabel,
                    loading = state.loading,
                    error = state.error,
                    hasSearched = state.hasSearched,
                    query = state.query,
                    results = state.results,
                    onResultTap = onResultTap,
                )
            }
        }
    }
}

/**
 * The extension source picker — a `DropdownMenu` anchored to the top-right,
 * listing all trusted extension sources. Shown when the user re-taps the
 * Extension toggle (per Q2: "tap Extension while selected → menu").
 *
 * Not a ModalBottomSheet because it's a quick inline picker, not a modal flow.
 * The design language's "no drag handle" rule applies to ModalBottomSheets;
 * this dropdown is a different component and follows M3 DropdownMenu styling.
 */
@Composable
private fun ExtensionSourcePicker(
    expanded: Boolean,
    sources: List<SourceMatcher.SourceInfo>,
    selectedId: Long?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
        ) {
            if (sources.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "No trusted extensions installed.\nInstall one from More → Settings → Extensions.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = onDismiss,
                )
            } else {
                sources.forEach { source ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = source.name,
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = if (source.id == selectedId) FontWeight.ExtraBold
                                else FontWeight.Normal,
                            )
                        },
                        leadingIcon = if (source.id == selectedId) {
                            {
                                Text(
                                    text = "●",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        } else null,
                        onClick = { onPick(source.id) },
                    )
                }
            }
        }
    }
}
