package app.confused.anikuta.feature.animedetails

import android.graphics.Color as AndroidColor
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.model.coverColorHex
import app.confused.anikuta.core.designsystem.theme.generateDynamicScheme
import app.confused.anikuta.core.preferences.ThemePreferences
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
    onOpenEpisode: (SEpisode, AnimeSource, List<SEpisode>, WatchEpisodeContext) -> Unit = { _, _, _, _ -> },
    /** Agent 2 — Downloads: enqueues a download for an episode (from the episode row button). */
    onDownloadEpisode: (SEpisode, AnimeSource, WatchEpisodeContext) -> Unit = { _, _, _ -> },
    /** Agent 2 — Downloads: per-episode download states keyed by episode URL. */
    downloadStates: Map<String, EpisodeDownloadState> = emptyMap(),
    onDownloadCancel: (String) -> Unit = {},
    onDownloadResume: (String) -> Unit = {},
    onDownloadRetry: (String) -> Unit = {},
    onDownloadDelete: (String) -> Unit = {},
) {
    val context = LocalContext.current

    // Inject repositories via Koin (for library save functionality + episode metadata).
    val animeRepository: app.confused.anikuta.core.common.repository.AnimeRepository = org.koin.core.context.GlobalContext.get().get()
    val categoryRepository: app.confused.anikuta.core.common.repository.CategoryRepository = org.koin.core.context.GlobalContext.get().get()
    val episodeMetadataRepository: app.confused.anikuta.core.episodemetadata.repository.EpisodeMetadataRepository = org.koin.core.context.GlobalContext.get().get()
    val sourceLinkStore: app.confused.anikuta.data.extension.cache.SourceLinkStore = org.koin.core.context.GlobalContext.get().get()
    val episodeRepository: app.confused.anikuta.core.common.repository.EpisodeRepository = org.koin.core.context.GlobalContext.get().get()

    // ── Dynamic cover-color theming (Task 1.3) ──
    // When adaptiveColorsDetails is ON and the anime has a cover color,
    // wrap the entire screen content in a MaterialTheme whose ColorScheme
    // is generated from the cover color. When OFF or no color, the user's
    // selected palette is used (no override).
    val themePrefs = remember { org.koin.core.context.GlobalContext.get().get<ThemePreferences>() }
    val adaptiveColorsDetails by themePrefs.adaptiveColorsDetails.changes()
        .collectAsStateWithLifecycle(initialValue = themePrefs.adaptiveColorsDetails.get())
    val themeMode by themePrefs.themeMode.changes()
        .collectAsStateWithLifecycle(initialValue = themePrefs.themeMode.get())
    val amoled by themePrefs.amoled.changes()
        .collectAsStateWithLifecycle(initialValue = themePrefs.amoled.get())

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
                    episodeRepository = episodeRepository,
                    extensionLinkStore = extensionLinkStore,
                    sourceLinkStore = sourceLinkStore,
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

    // ── Generate dynamic scheme from cover color when available ──
    val coverColorArgb: Int = remember(animeState) {
        val state = animeState
        if (state is DetailState.Success) {
            val hex = state.anime.coverColorHex
            if (hex != null) {
                runCatching { AndroidColor.parseColor(hex) }.getOrDefault(0)
            } else {
                0
            }
        } else {
            0
        }
    }
    val isDark = when (themeMode) {
        app.confused.anikuta.core.preferences.ThemeMode.LIGHT -> false
        app.confused.anikuta.core.preferences.ThemeMode.DARK -> true
        app.confused.anikuta.core.preferences.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val dynamicScheme = if (adaptiveColorsDetails && coverColorArgb != 0) {
        generateDynamicScheme(coverColorArgb, darkTheme = isDark, amoled = amoled)
    } else {
        null
    }

    val screenContent: @Composable () -> Unit = {
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
                    onDownloadEpisode = onDownloadEpisode,
                    downloadStates = downloadStates,
                    onDownloadCancel = onDownloadCancel,
                    onDownloadResume = onDownloadResume,
                    onDownloadRetry = onDownloadRetry,
                    onDownloadDelete = onDownloadDelete,
                )
            }
        }
    }

    // ── Apply dynamic theme wrap (or use the user's palette) ──
    if (dynamicScheme != null) {
        MaterialTheme(colorScheme = dynamicScheme, content = screenContent)
    } else {
        screenContent()
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
