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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import app.confused.anikuta.feature.search.data.SearchUiPreferences
import app.confused.anikuta.feature.search.viewmodel.SearchResult
import app.confused.anikuta.feature.search.viewmodel.SearchSource
import app.confused.anikuta.feature.search.viewmodel.SearchUiState
import app.confused.anikuta.feature.search.viewmodel.SearchViewModel

/**
 * The Search screen — a dual-source search experience.
 *
 * Top bar (collapsing): title + AniList/Extension source toggle + search bar.
 * Below: quick row (Filters + Sort), then scrollable content:
 *   - Recent searches (per-source; AniList and Extension have separate lists).
 *   - AniList: ResultsCard (results grid, paginated on scroll-to-bottom).
 *   - Extension: ExtensionResultsView (Popular + Latest rows) when blank query,
 *     or ResultsCard (search results grid) when a query is typed.
 *
 * Tapping an AniList result → [onOpenAnime] (opens the existing detail page).
 * Tapping an Extension result → [onOpenExtensionResult] (starts the linking
 * flow — see ExtensionLinkingSheet).
 */
@Composable
fun SearchScreen(
    anilistApi: AniListApi,
    extensionManager: AnimeExtensionManager,
    sourceMatcher: SourceMatcher,
    recentsStore: RecentSearchesStore,
    uiPreferences: SearchUiPreferences,
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
                uiPreferences = uiPreferences,
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
        onToggleRecentsCollapsed = vm::onToggleRecentsCollapsed,
        onResultTap = { result ->
            when (result) {
                is SearchResult.AniList -> onOpenAnime(result.id)
                is SearchResult.Extension -> onOpenExtensionResult(result)
            }
        },
        onLoadMore = vm::onLoadMore,
        showSourcePicker = showSourcePicker,
        onPickExtensionSource = { id ->
            vm.onPickExtensionSource(id)
            showSourcePicker = false
        },
        onDismissSourcePicker = { showSourcePicker = false },
    )

    // Filter sheet — edits a PENDING copy; only "Apply" syncs + re-fetches.
    FilterSheet(
        show = showFilterSheet,
        pendingFilters = vm.getPendingFilters(),
        appliedSort = state.sort,
        onPendingFiltersChange = vm::onPendingFiltersChange,
        onSortChange = vm::onSortChange, // sort is applied live (single-select, instant)
        onClearAll = vm::onClearFilters,
        onApply = {
            vm.applyFilters()
            showFilterSheet = false
        },
        onDismiss = { showFilterSheet = false },
    )

    // Extension source picker — a styled bottom sheet (no drag handle).
    if (showSourcePicker) {
        ExtensionSourcePickerSheet(
            sources = state.availableExtensionSources,
            selectedId = state.selectedExtensionSourceId,
            onPick = { id ->
                vm.onPickExtensionSource(id)
                showSourcePicker = false
            },
            onDismiss = { showSourcePicker = false },
        )
    }
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
    onToggleRecentsCollapsed: () -> Unit,
    onResultTap: (SearchResult) -> Unit,
    onLoadMore: () -> Unit,
    showSourcePicker: Boolean,
    onPickExtensionSource: (Long) -> Unit,
    onDismissSourcePicker: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val collapsed = scrollState.value > 20

    // Infinite-scroll detection — when the user is within ~600px of the bottom,
    // ask the ViewModel to load the next page (AniList only). The helper is a
    // @Composable that internally uses derivedStateOf + LaunchedEffect, so it
    // must be called directly in the composable body (not inside another
    // LaunchedEffect).
    ObserveScrollNearBottom(scrollState) { onLoadMore() }

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

        // Scrollable content — reduced top padding to bring content closer
        // to the filter/sort row (was 4dp, now 0dp — the SearchTopBar already
        // has bottom padding). Bottom padding for floating nav clearance.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = 0.dp, bottom = 110.dp),
        ) {
            val sectionLabel = when {
                state.query.isNotBlank() && state.source == SearchSource.ANILIST ->
                    "Results for \"${state.query}\""
                state.query.isNotBlank() && state.source == SearchSource.EXTENSION ->
                    "Extension results for \"${state.query}\""
                state.source == SearchSource.EXTENSION -> "Extension"
                else -> "Popular anime"
            }

            // Recent searches — per source, shown when query is blank, no
            // applied filters, and recents exist. Both AniList AND Extension
            // show their own (separate) recents list.
            val showRecent = state.query.isBlank() &&
                state.filters.isEmpty &&
                state.recents.isNotEmpty()
            if (showRecent) {
                RecentSearchesCard(
                    recents = state.recents,
                    collapsed = state.recentsCollapsed,
                    onToggleCollapsed = onToggleRecentsCollapsed,
                    onPick = onPickRecent,
                    onRemove = onRemoveRecent,
                    onClear = onClearRecents,
                )
            }

            if (state.source == SearchSource.EXTENSION && state.query.isBlank()) {
                ExtensionResultsView(
                    loading = state.loading,
                    error = state.error,
                    rows = state.extensionRows,
                    onResultTap = onResultTap,
                )
            } else {
                ResultsCard(
                    sectionLabel = sectionLabel,
                    loading = state.loading,
                    isLoadingMore = state.isLoadingMore,
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

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Emits [onBottom] whenever the scroll position is near the bottom of the
 * scrollable content. Used to trigger AniList pagination.
 *
 * Uses `derivedStateOf` so the comparison only re-runs when the scroll value
 * actually crosses the threshold — not on every pixel of scroll.
 */
@Composable
private fun ObserveScrollNearBottom(
    scrollState: androidx.compose.foundation.ScrollState,
    onBottom: () -> Unit,
) {
    val nearBottom by remember {
        derivedStateOf {
            val max = scrollState.maxValue
            max > 0 && scrollState.value >= max - 600 // within ~600px of the end
        }
    }
    LaunchedEffect(nearBottom) {
        if (nearBottom) onBottom()
    }
}
