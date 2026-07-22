package app.confused.anikuta.feature.animedetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.data.extension.AnimeExtensionManager
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SEpisode

/**
 * The anime detail screen — shows AniList metadata + real episodes from a
 * matched extension source.
 *
 * Three-stage load (per design spec §6.1):
 * 1. AniList → anime metadata (title, cover, description, score).
 * 2. Extension source match → [SourceMatcher] searches trusted sources.
 * 3. Episode list → `source.getEpisodeList(sAnime)` on the matched source.
 *
 * The screen creates an [AnimeDetailViewModel] scoped to `animeId` (survives
 * configuration changes) and observes its state flows.
 *
 * UI features:
 * - **Pull-to-refresh** — wraps the content in `PullToRefreshBox`; pulling
 *   down triggers [AnimeDetailViewModel.refresh] (re-runs all three stages).
 * - **Source indicator** — next to the "Episodes" header, shows the matched
 *   source name (or a "Search manually" button when no source matched).
 * - **Manual search** — a search icon button opens [ManualSearchSheet], where
 *   the user can search extensions with a custom query and link a result.
 *
 * @param animeId the AniList anime ID.
 * @param api the AniList API client.
 * @param extensionManager provides installed + trusted sources.
 * @param sourceMatcher searches sources by title.
 * @param onBack called when the user navigates back.
 * @param onOpenEpisode called when an episode is tapped (episode + source).
 */
@Composable
fun AnimeDetailScreen(
    animeId: Int,
    api: AniListApi,
    extensionManager: AnimeExtensionManager,
    sourceMatcher: SourceMatcher,
    extensionLinkStore: app.confused.anikuta.data.extension.cache.ExtensionLinkStore,
    onBack: () -> Unit,
    onOpenEpisode: (SEpisode, AnimeSource, List<SEpisode>) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current

    // Inject repositories via Koin (for library save functionality + episode metadata).
    val animeRepository: app.confused.anikuta.core.common.repository.AnimeRepository = org.koin.core.context.GlobalContext.get().get()
    val categoryRepository: app.confused.anikuta.core.common.repository.CategoryRepository = org.koin.core.context.GlobalContext.get().get()
    val episodeMetadataRepository: app.confused.anikuta.core.episodemetadata.repository.EpisodeMetadataRepository = org.koin.core.context.GlobalContext.get().get()

    @Suppress("UNCHECKED_CAST")
    val vm: AnimeDetailViewModel = viewModel(
        key = "detail_$animeId",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AnimeDetailViewModel(
                    anilistId = animeId,
                    api = api,
                    extensionManager = extensionManager,
                    sourceMatcher = sourceMatcher,
                    animeRepository = animeRepository,
                    categoryRepository = categoryRepository,
                    extensionLinkStore = extensionLinkStore,
                    episodeMetadataRepository = episodeMetadataRepository,
                    appContext = context.applicationContext,
                ) as T
        },
    )

    val animeState by vm.animeState.collectAsState()
    val episodeState by vm.episodeState.collectAsState()
    val currentMatch by vm.currentMatch.collectAsState()
    val allMatches by vm.allMatches.collectAsState()
    val watchedEpisodes by vm.watchedEpisodes.collectAsState()
    val episodeMetadata by vm.episodeMetadata.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val isSearching by vm.isSearching.collectAsState()
    val manualSearchResults by vm.manualSearchResults.collectAsState()
    val manualSearchErrors by vm.manualSearchErrors.collectAsState()
    val autoMatchErrors by vm.autoMatchErrors.collectAsState()
    val hasSearched by vm.hasSearched.collectAsState()
    val isSaved by vm.isSaved.collectAsState()
    val categories by vm.categories.collectAsState()
    val showCategoryPicker by vm.showCategoryPicker.collectAsState()
    val currentAnimeCategoryIds by vm.currentAnimeCategoryIds.collectAsState()
    // Available sources for the manual-search source selector. Computed once
    // (not a StateFlow — the list doesn't change while the screen is open).
    val availableSources = remember { vm.getAvailableSources() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val state = animeState) {
            is DetailState.Loading -> LoadingState()
            is DetailState.Error -> ErrorState(message = state.message)
            is DetailState.Success -> DetailContent(
                anime = state.anime,
                episodeState = episodeState,
                currentMatch = currentMatch,
                allMatches = allMatches,
                watchedEpisodes = watchedEpisodes,
                episodeMetadata = episodeMetadata,
                isRefreshing = isRefreshing,
                isSearching = isSearching,
                manualSearchResults = manualSearchResults,
                manualSearchErrors = manualSearchErrors,
                autoMatchErrors = autoMatchErrors,
                hasSearched = hasSearched,
                availableSources = availableSources,
                saved = isSaved,
                onToggleSave = vm::toggleSave,
                onLongPressSave = vm::openCategoryPicker,
                onBack = onBack,
                onOpenEpisode = onOpenEpisode,
                onToggleWatched = vm::toggleWatched,
                onSwitchSource = vm::switchSource,
                onRefresh = vm::refresh,
                onManualSearch = { sourceId, query -> vm.manualSearch(sourceId, query) },
                onLinkManual = vm::linkManual,
                onClearManualSearch = vm::clearManualSearch,
            )
        }
    }

    // Category picker dialog (long-press on bookmark button).
    if (showCategoryPicker) {
        var showAddCategory by remember { mutableStateOf(false) }
        if (!showAddCategory) {
            app.confused.anikuta.core.designsystem.component.CategoryPickerDialog(
                categories = categories,
                selectedCategoryIds = currentAnimeCategoryIds,
                onConfirm = { ids -> vm.saveToCategories(ids) },
                onDismiss = { vm.dismissCategoryPicker() },
                onAddNewCategory = { showAddCategory = true },
            )
        } else {
            app.confused.anikuta.core.designsystem.component.AddCategoryDialog(
                onConfirm = { name ->
                    vm.createCategory(name)
                    showAddCategory = false
                },
                onDismiss = { showAddCategory = false },
            )
        }
    }
}
