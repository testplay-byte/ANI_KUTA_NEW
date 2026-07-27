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
import app.confused.anikuta.core.common.model.details.AnimeDetailsProviderRegistry
import app.confused.anikuta.core.common.model.details.DataSource
import app.confused.anikuta.core.common.model.details.DetailsRequest
import app.confused.anikuta.core.designsystem.theme.generateDynamicScheme
import app.confused.anikuta.core.preferences.ThemePreferences
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import org.koin.core.context.GlobalContext

/**
 * The **unified** anime detail screen — renders data from either AniList OR
 * an extension via the [AnimeDetailsProviderRegistry] translation layer
 * (doc 05). ONE screen serves both data sources; a three-dot menu
 * ([SourceSwitcherMenu]) switches the source in-place.
 *
 * # Entry modes
 *
 * - **AniList entry** ([animeId] != null): the user tapped an anime from
 *   browse/search. Initial data source = ANILIST.
 * - **Extension entry** ([extensionSource] != null + [extensionSAnime] != null):
 *   the user opened an extension anime (possibly unlinked). Initial data
 *   source = EXTENSION. `anilistId` is null for unlinked anime.
 *
 * # Phase 9 adaptive theming
 *
 * The `MaterialTheme(dynamicScheme)` wrap (lines ~138-160) is PRESERVED from
 * the pre-refactor screen. `coverColorHex` now flows from `UnifiedAnime`
 * (AniList's `coverImage.color` in AniList mode, or Palette-extracted in
 * extension mode via the provider). When null, the user's palette is used.
 *
 * @param animeId the AniList anime ID (AniList entry), or null for extension entry.
 * @param extensionSource the extension source (extension entry), or null.
 * @param extensionSAnime the SAnime (extension entry), or null.
 * @param extensionAnilistId the linked AniList ID for an extension entry (nullable).
 */
@Composable
fun AnimeDetailScreen(
    animeId: Int? = null,
    extensionSource: AnimeCatalogueSource? = null,
    extensionSAnime: SAnime? = null,
    extensionAnilistId: Int? = null,
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
    /** Called when the user picks "Link to AniList" / "Switch anime" (extension mode) —
     *  opens the AniList linking sheet overlay via AppController.startLinking. */
    onLinkToAniList: () -> Unit = {},
    /** Called when the user picks a new anime from the AniList search sheet (AniList mode
     *  "Switch anime"). The destination navigates to the new AniList anime's details page. */
    onNavigateToAnilistAnime: (Int) -> Unit = {},
) {
    val context = LocalContext.current

    // Inject the provider registry + repositories via Koin.
    val registry: AnimeDetailsProviderRegistry = remember { GlobalContext.get().get() }
    val animeRepository: app.confused.anikuta.core.common.repository.AnimeRepository = remember { GlobalContext.get().get() }
    val categoryRepository: app.confused.anikuta.core.common.repository.CategoryRepository = remember { GlobalContext.get().get() }
    val episodeMetadataRepository: app.confused.anikuta.core.episodemetadata.repository.EpisodeMetadataRepository = remember { GlobalContext.get().get() }
    val sourceLinkStore: app.confused.anikuta.data.extension.cache.SourceLinkStore = remember { GlobalContext.get().get() }
    val extensionLinkStore: app.confused.anikuta.data.extension.cache.ExtensionLinkStore = remember { GlobalContext.get().get() }
    val episodeRepository: app.confused.anikuta.core.common.repository.EpisodeRepository = remember { GlobalContext.get().get() }
    val sourceMatcher: SourceMatcher = remember { GlobalContext.get().get() }
    val api: AniListApi = remember { GlobalContext.get().get() }

    // ── Build the initial request from the entry mode ──
    val initialRequest: DetailsRequest = remember(animeId, extensionSource, extensionSAnime) {
        when {
            animeId != null -> DetailsRequest.ByAniListId(animeId)
            extensionSource != null && extensionSAnime != null -> DetailsRequest.ByExtension(
                sourceId = extensionSource.id,
                animeUrl = extensionSAnime.url,
                animeTitle = extensionSAnime.title,
                anilistId = extensionAnilistId,
            )
            else -> error("AnimeDetailScreen requires either animeId or (extensionSource + extensionSAnime)")
        }
    }

    // ── Dynamic cover-color theming (Phase 9 — preserved) ──
    val themePrefs = remember { GlobalContext.get().get<ThemePreferences>() }
    val adaptiveColorsDetails by themePrefs.adaptiveColorsDetails.changes()
        .collectAsStateWithLifecycle(initialValue = themePrefs.adaptiveColorsDetails.get())
    val themeMode by themePrefs.themeMode.changes()
        .collectAsStateWithLifecycle(initialValue = themePrefs.themeMode.get())
    val amoled by themePrefs.amoled.changes()
        .collectAsStateWithLifecycle(initialValue = themePrefs.amoled.get())

    @Suppress("UNCHECKED_CAST")
    val vm: AnimeDetailViewModel = viewModel(
        key = "detail_${animeId ?: "${extensionSource?.id}_${extensionSAnime?.url}"}",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AnimeDetailViewModel(
                    initialRequest = initialRequest,
                    registry = registry,
                    animeRepository = animeRepository,
                    categoryRepository = categoryRepository,
                    episodeRepository = episodeRepository,
                    extensionLinkStore = extensionLinkStore,
                    sourceLinkStore = sourceLinkStore,
                    episodeMetadataRepository = episodeMetadataRepository,
                    sourceMatcher = sourceMatcher,
                    api = api,
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
    val currentDataSource by vm.currentDataSource.collectAsState()
    val availableSources = remember { vm.getAvailableSources() }

    // ── AniList search sheet state (for the three-dot menu's "Switch anime" option in AniList mode) ──
    var showAniListSearch by remember { mutableStateOf(false) }

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
                    currentDataSource = currentDataSource,
                    onSwitchDataSource = vm::switchDataSource,
                    onLinkToAniList = onLinkToAniList,
                    onSwitchAnilistAnime = { showAniListSearch = true },
                    onRefresh = vm::refresh,
                    onOpenEpisode = onOpenEpisode,
                    onToggleWatched = vm::toggleWatched,
                    onSwitchSource = vm::switchSource,
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

    // ── AniList search sheet (for the three-dot menu's "Switch anime" option in AniList mode) ──
    val successState = animeState as? DetailState.Success
    if (showAniListSearch) {
        AniListSearchSheet(
            anilistApi = api,
            initialQuery = successState?.anime?.title ?: "",
            onPicked = { newId -> onNavigateToAnilistAnime(newId) },
            onDismiss = { showAniListSearch = false },
        )
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
