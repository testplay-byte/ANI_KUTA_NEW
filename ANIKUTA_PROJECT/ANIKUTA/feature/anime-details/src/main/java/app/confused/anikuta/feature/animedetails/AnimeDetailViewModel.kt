package app.confused.anikuta.feature.animedetails

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.AnimeStatus
import app.confused.anikuta.core.common.model.Category
import app.confused.anikuta.core.common.model.details.AnimeDetailsProviderRegistry
import app.confused.anikuta.core.common.model.details.DataSource
import app.confused.anikuta.core.common.model.details.DetailsRequest
import app.confused.anikuta.core.common.model.details.UnifiedAnime
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.common.repository.CategoryRepository
import app.confused.anikuta.core.common.repository.EpisodeRepository
import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata
import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadataRequest
import app.confused.anikuta.core.episodemetadata.repository.EpisodeMetadataRepository
import app.confused.anikuta.data.extension.cache.ExtensionLinkStore
import app.confused.anikuta.data.extension.cache.SourceLinkStore
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeImpl
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.SEpisodeImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The ViewModel for [AnimeDetailScreen] — the **unified** details page that
 * renders data from either AniList OR an extension via the
 * [AnimeDetailsProviderRegistry] translation layer (doc 05).
 *
 * # Architecture
 *
 * - **Source-agnostic.** The VM holds a [DetailsRequest] (the anime's identity)
 *   + a [currentDataSource] (AniList or Extension). It calls
 *   `registry.forSource(currentDataSource).load(request)` and renders whatever
 *   [UnifiedAnime] comes back. It never calls `AniListApi` or
 *   `source.getEpisodeList` directly — only through the provider.
 *
 * - **In-place source switching.** [switchDataSource] flips [currentDataSource]
 *   and re-queries the provider. Scroll position, library state, and watched
 *   flags survive (they're keyed on anilistId / sourceId+url, not on the
 *   displayed data source). This is the UX Animiru CANNOT do (doc 03).
 *
 * - **Extension switching.** [switchExtension] re-points the request to a
 *   different `AnimeCatalogueSource` + `SAnime` and reloads — used by the
 *   ManualSearchSheet when the user picks a different extension.
 *
 * # Library keying (doc 04 §6, owner requirement Q2)
 *
 * - **Linked anime** (anilistId != null): library row keyed by `anilistId`.
 * - **Unlinked extension anime** (anilistId == null): keyed by `sourceId + url`
 *   via `AnimeRepository.getBySourceAndUrl`. This makes unlinked extension
 *   anime visible in the library (the old `ExtensionDetailScreen` shortcoming).
 *
 * @param initialRequest the anime's identity — either [DetailsRequest.ByAniListId]
 *   (user tapped from browse/search) or [DetailsRequest.ByExtension] (user opened
 *   an extension anime, possibly unlinked).
 * @param registry the provider registry (Koin multi-binding).
 * @param animeRepository library save + library-key lookups.
 * @param categoryRepository set-categories dialog.
 * @param episodeRepository (kept for future direct-episode queries; the provider
 *   handles persistence internally now).
 * @param extensionLinkStore reverse-lookup for library keying + linking state.
 * @param sourceLinkStore saved AniList→extension links.
 * @param episodeMetadataRepository per-episode metadata enrichment (Jikan/Anikage/AniList).
 * @param sourceMatcher resolves sourceId → AnimeCatalogueSource (for ManualSearchSheet).
 * @param api AniList API (for the metadata-enrichment request + fallback).
 * @param appContext for SharedPreferences (per-anime source preference) + Toast.
 */
class AnimeDetailViewModel(
    private val initialRequest: DetailsRequest,
    private val registry: AnimeDetailsProviderRegistry,
    private val animeRepository: AnimeRepository,
    private val categoryRepository: CategoryRepository,
    private val episodeRepository: EpisodeRepository,
    private val extensionLinkStore: ExtensionLinkStore,
    private val sourceLinkStore: SourceLinkStore,
    private val episodeMetadataRepository: EpisodeMetadataRepository,
    private val sourceMatcher: SourceMatcher,
    private val api: AniListApi,
    private val appContext: Context,
) : ViewModel() {

    // ── State ──

    private val _animeState = MutableStateFlow<DetailState>(DetailState.Loading)
    val animeState: StateFlow<DetailState> = _animeState.asStateFlow()

    private val _episodeState = MutableStateFlow<EpisodeState>(EpisodeState.Idle)
    val episodeState: StateFlow<EpisodeState> = _episodeState.asStateFlow()

    /** Per-episode metadata map (episodeNumber → EpisodeMetadata). */
    private val _episodeMetadata = MutableStateFlow<Map<Int, EpisodeMetadata>>(emptyMap())
    val episodeMetadata: StateFlow<Map<Int, EpisodeMetadata>> = _episodeMetadata.asStateFlow()

    /** The currently-active data source (drives the three-dot menu). */
    private val _currentDataSource = MutableStateFlow(initialDataSource())
    val currentDataSource: StateFlow<DataSource> = _currentDataSource.asStateFlow()

    /** All sources that matched the anime (for the manual-search / extension-switcher). */
    private val _allMatches = MutableStateFlow<List<SourceMatcher.SourceMatch>>(emptyList())
    val allMatches: StateFlow<List<SourceMatcher.SourceMatch>> = _allMatches.asStateFlow()

    /** The currently-selected source match (drives episode loading + extension switching). */
    private val _currentMatch = MutableStateFlow<SourceMatcher.SourceMatch?>(null)
    val currentMatch: StateFlow<SourceMatcher.SourceMatch?> = _currentMatch.asStateFlow()

    /** In-memory watched set (keyed by episode URL). */
    private val _watchedEpisodes = MutableStateFlow<Set<String>>(emptySet())
    val watchedEpisodes: StateFlow<Set<String>> = _watchedEpisodes.asStateFlow()

    /** Whether this anime is saved in the library (favorite=true in DB). */
    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    /** Visible categories (for the set-categories dialog). */
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    /** Whether the set-categories dialog is open. */
    private val _showCategoryPicker = MutableStateFlow(false)
    val showCategoryPicker: StateFlow<Boolean> = _showCategoryPicker.asStateFlow()

    /** The category IDs currently assigned to this anime. */
    private val _currentAnimeCategoryIds = MutableStateFlow<Set<Long>>(emptySet())
    val currentAnimeCategoryIds: StateFlow<Set<Long>> = _currentAnimeCategoryIds.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _manualSearchResults = MutableStateFlow<List<SourceMatcher.ManualSearchResult>>(emptyList())
    val manualSearchResults: StateFlow<List<SourceMatcher.ManualSearchResult>> = _manualSearchResults.asStateFlow()

    private val _manualSearchErrors = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val manualSearchErrors: StateFlow<List<Pair<String, String>>> = _manualSearchErrors.asStateFlow()

    private val _autoMatchErrors = MutableStateFlow<List<Pair<String, String>>?>(null)
    val autoMatchErrors: StateFlow<List<Pair<String, String>>?> = _autoMatchErrors.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private val sourcePrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The active request (mutable — changes when the user switches extension). */
    private var activeRequest: DetailsRequest = initialRequest

    init {
        load()
        observeLibraryState()
        viewModelScope.launch {
            categoryRepository.observeVisible().collect { _categories.value = it }
        }
    }

    // ── Public API ──

    /** Initial load (or reload after switching data source / extension). */
    fun load() {
        viewModelScope.launch {
            _animeState.value = DetailState.Loading
            _episodeState.value = EpisodeState.Searching
            try {
                val provider = registry.forSource(_currentDataSource.value)
                val result = withContext(Dispatchers.IO) { provider.load(activeRequest) }
                if (result == null) {
                    _animeState.value = DetailState.Error("Anime not found")
                    _episodeState.value = EpisodeState.NoMatch
                    return@launch
                }
                _animeState.value = DetailState.Success(result.anime)
                renderEpisodes(result.anime, result.episodes)

                // Set up the current match for the source switcher + manual search.
                setupCurrentMatch(result.anime)

                // Fetch episode metadata in the background (skipped for unlinked extension anime).
                launch { fetchEpisodeMetadata(result.anime, result.episodes.size) }
                // Search other sources in the background (for the switcher).
                launch { searchAllSourcesInBackground(result.anime.title) }
            } catch (e: Throwable) {
                // Catch Throwable — extension binary-incompat throws Error subclasses.
                Log.e(TAG, "load failed", e)
                _animeState.value = DetailState.Error(e.message ?: "Unknown error")
                _episodeState.value = EpisodeState.Error("Failed: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Switch the displayed data source (AniList ↔ Extension) in-place.
     *
     * The three-dot menu calls this. Re-queries the provider with the new
     * [DataSource]; scroll position, library state, and watched flags survive
     * (keyed on anilistId / sourceId+url, not on the displayed source).
     */
    fun switchDataSource(target: DataSource) {
        if (_currentDataSource.value == target) return
        Log.i(TAG, "Switching data source: ${_currentDataSource.value} → $target")
        _currentDataSource.value = target
        // Rebuild the request for the new source.
        activeRequest = requestForDataSource(target, (activeRequest))
        load()
    }

    /**
     * Switch to a different extension source (from ManualSearchSheet).
     *
     * Saves the new source link, updates [activeRequest] to the new
     * `sourceId + sAnime`, and reloads.
     */
    fun switchExtension(source: AnimeCatalogueSource, sAnime: SAnime) {
        Log.i(TAG, "Switching extension to '${source.name}' for '${sAnime.title}'")
        val anilistId = currentAnilistId()
        // Save the new source link (so re-open skips re-matching).
        if (anilistId != null) {
            sourceLinkStore.saveLink(anilistId, source.id, sAnime.url, sAnime.title)
            sourcePrefs.edit().putLong(sourcePrefKey(anilistId), source.id).apply()
        }
        activeRequest = DetailsRequest.ByExtension(
            sourceId = source.id,
            animeUrl = sAnime.url,
            animeTitle = sAnime.title,
            anilistId = anilistId,
        )
        _currentDataSource.value = DataSource.EXTENSION
        _currentMatch.value = SourceMatcher.SourceMatch(source, sAnime, 1.0)
        load()
    }

    /** Toggles the watched state of an episode (by URL). In-memory only. */
    fun toggleWatched(episodeUrl: String) {
        _watchedEpisodes.value = _watchedEpisodes.value.toMutableSet().apply {
            if (contains(episodeUrl)) remove(episodeUrl) else add(episodeUrl)
        }
    }

    /**
     * Switches to a different source (legacy callback for the source switcher).
     * Persists the selection per-anime in SharedPreferences.
     */
    fun switchSource(match: SourceMatcher.SourceMatch) {
        val anilistId = currentAnilistId()
        if (anilistId != null) {
            sourcePrefs.edit().putLong(sourcePrefKey(anilistId), match.source.id).apply()
        }
        _currentMatch.value = match
        Toast.makeText(appContext, "Switched to ${match.source.name}", Toast.LENGTH_SHORT).show()
        switchExtension(match.source, match.sAnime)
    }

    /** Refreshes everything (pull-to-refresh). */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        load()
    }

    /** Available sources for the manual-search source selector. */
    fun getAvailableSources(): List<SourceMatcher.SourceInfo> = sourceMatcher.getAvailableSources()

    /** Manually searches ONE specific source for a custom query. */
    suspend fun manualSearch(sourceId: Long, query: String): List<SourceMatcher.ManualSearchResult> {
        Log.i(TAG, "Manual search: sourceId=$sourceId, query='$query'")
        _isSearching.value = true
        return try {
            val outcome = sourceMatcher.searchOneSource(sourceId, query)
            when (outcome) {
                is SourceMatcher.SourceSearchOutcome.Success -> {
                    _manualSearchResults.value = outcome.results
                    _manualSearchErrors.value = emptyList()
                }
                is SourceMatcher.SourceSearchOutcome.Failed -> {
                    _manualSearchResults.value = emptyList()
                    _manualSearchErrors.value = listOf(outcome.sourceName to outcome.error)
                    Toast.makeText(appContext, "${outcome.sourceName} failed: ${outcome.error}", Toast.LENGTH_LONG).show()
                }
            }
            _hasSearched.value = true
            _manualSearchResults.value
        } catch (e: Throwable) {
            Log.e(TAG, "Manual search failed for '$query'", e)
            _manualSearchResults.value = emptyList()
            _manualSearchErrors.value = listOf("(search)" to (e.message ?: e::class.java.simpleName))
            _hasSearched.value = true
            emptyList()
        } finally {
            _isSearching.value = false
        }
    }

    fun clearManualSearch() {
        _manualSearchResults.value = emptyList()
        _manualSearchErrors.value = emptyList()
        _hasSearched.value = false
    }

    /**
     * Links a specific source + SAnime to this anime (manual selection from ManualSearchSheet).
     * Delegates to [switchExtension] which persists + reloads.
     */
    fun linkManual(source: AnimeCatalogueSource, sAnime: SAnime) {
        switchExtension(source, sAnime)
    }

    // ── Library save ──

    fun toggleSave() {
        viewModelScope.launch {
            try {
                val unified = (_animeState.value as? DetailState.Success)?.anime ?: return@launch
                val existing = findLibraryAnime(unified)
                if (existing != null) {
                    val newFav = !existing.favorite
                    animeRepository.updateFavorite(
                        id = existing.id,
                        favorite = newFav,
                        dateAdded = if (newFav) System.currentTimeMillis() else existing.dateAdded,
                    )
                    if (newFav) categoryRepository.setAnimeCategories(existing.id, listOf(Category.DEFAULT_ID))
                } else {
                    saveAnimeToLibrary(unified)
                }
            } catch (e: Exception) {
                Log.e(TAG, "toggleSave failed", e)
            }
        }
    }

    fun openCategoryPicker() {
        viewModelScope.launch {
            try {
                val cats = getAnimeCategories()
                _currentAnimeCategoryIds.value = cats.map { it.id }.toSet()
            } catch (e: Exception) {
                Log.e(TAG, "openCategoryPicker: failed to load current categories", e)
                _currentAnimeCategoryIds.value = emptySet()
            }
            _showCategoryPicker.value = true
        }
    }

    fun dismissCategoryPicker() {
        _showCategoryPicker.value = false
    }

    fun saveToCategories(categoryIds: Set<Long>) {
        viewModelScope.launch {
            try {
                val unified = (_animeState.value as? DetailState.Success)?.anime ?: return@launch
                val existing = findLibraryAnime(unified)
                val animeId = if (existing != null) {
                    if (!existing.favorite) {
                        animeRepository.updateFavorite(existing.id, favorite = true, dateAdded = System.currentTimeMillis())
                    }
                    existing.id
                } else {
                    saveAnimeToLibrary(unified)
                }
                categoryRepository.setAnimeCategories(animeId, categoryIds.toList())
                _showCategoryPicker.value = false
            } catch (e: Exception) {
                Log.e(TAG, "saveToCategories failed", e)
            }
        }
    }

    suspend fun getAnimeCategories(): List<Category> {
        val unified = (_animeState.value as? DetailState.Success)?.anime ?: return emptyList()
        val existing = findLibraryAnime(unified) ?: return emptyList()
        return categoryRepository.getAnimeCategories(existing.id)
    }

    fun createCategory(name: String) {
        viewModelScope.launch { categoryRepository.create(name) }
    }

    // ── Internal helpers ──

    /** Determines the initial data source from the [initialRequest]. */
    private fun initialDataSource(): DataSource = when (initialRequest) {
        is DetailsRequest.ByAniListId -> DataSource.ANILIST
        is DetailsRequest.ByExtension -> DataSource.EXTENSION
    }

    /** Rebuilds the request when switching data sources. */
    private fun requestForDataSource(target: DataSource, current: DetailsRequest): DetailsRequest {
        // Extract whatever identity we have from the current request.
        val anilistId = when (current) {
            is DetailsRequest.ByAniListId -> current.anilistId
            is DetailsRequest.ByExtension -> current.anilistId
                ?: extensionLinkStore.getAniListId(current.sourceId, current.animeUrl)
        }
        val sourceId = when (current) {
            is DetailsRequest.ByAniListId -> sourceLinkStore.getLink(current.anilistId)?.sourceId
            is DetailsRequest.ByExtension -> current.sourceId
        }
        val animeUrl = when (current) {
            is DetailsRequest.ByAniListId -> sourceLinkStore.getLink(current.anilistId)?.animeUrl
            is DetailsRequest.ByExtension -> current.animeUrl
        }
        val animeTitle = when (current) {
            is DetailsRequest.ByAniListId -> sourceLinkStore.getLink(current.anilistId)?.animeTitle
            is DetailsRequest.ByExtension -> current.animeTitle
        }
        return when (target) {
            DataSource.ANILIST -> {
                if (anilistId != null) DetailsRequest.ByAniListId(anilistId)
                else current // can't switch to AniList if unlinked — stay put
            }
            DataSource.EXTENSION -> {
                if (sourceId != null && animeUrl != null && animeTitle != null) {
                    DetailsRequest.ByExtension(sourceId, animeUrl, animeTitle, anilistId)
                } else current // can't switch to extension if no source link — stay put
            }
        }
    }

    /** The current anilistId (from the active request or a reverse-lookup). */
    private fun currentAnilistId(): Int? {
        val req = activeRequest  // local val so Kotlin can smart-cast
        return when (req) {
            is DetailsRequest.ByAniListId -> req.anilistId
            is DetailsRequest.ByExtension -> req.anilistId
                ?: extensionLinkStore.getAniListId(req.sourceId, req.animeUrl)
        }
    }

    /** Sets up [_currentMatch] from the unified anime's sourceId (for the source switcher). */
    private fun setupCurrentMatch(anime: UnifiedAnime) {
        val sourceId = anime.sourceId ?: return
        val source = sourceMatcher.getSourceById(sourceId) ?: return
        val sAnime = SAnimeImpl().apply {
            url = anime.url
            title = anime.title
        }
        _currentMatch.value = SourceMatcher.SourceMatch(source, sAnime, 1.0)
    }

    /** Renders the episode list into [_episodeState] as SEpisodes (for the UI). */
    private fun renderEpisodes(anime: UnifiedAnime, episodes: List<app.confused.anikuta.core.common.model.Episode>) {
        if (anime.status == app.confused.anikuta.core.common.model.details.UnifiedStatus.NOT_YET_RELEASED && episodes.isEmpty()) {
            _episodeState.value = EpisodeState.NotReleased
            return
        }
        if (episodes.isEmpty()) {
            _episodeState.value = EpisodeState.NoMatch
            return
        }
        val sEpisodes = episodes.map { it.toSEpisode() }
        _episodeState.value = EpisodeState.Loaded(sEpisodes, anime.sourceName)
    }

    /** Searches all sources in the background (for the source switcher) without blocking. */
    private suspend fun searchAllSourcesInBackground(title: String) {
        try {
            val all = sourceMatcher.matchAll(title)
            _allMatches.value = all
            _autoMatchErrors.value = sourceMatcher.lastMatchAllErrors
        } catch (e: Exception) {
            Log.w(TAG, "Background source search failed (non-fatal)", e)
        }
    }

    /** Fetches per-episode metadata (titles, descriptions, thumbnails, air dates). */
    private suspend fun fetchEpisodeMetadata(anime: UnifiedAnime, episodeCount: Int) {
        try {
            val anilistId = anime.anilistId ?: run {
                Log.i(TAG, "Skipping episode metadata — no anilistId (unlinked extension anime)")
                return
            }
            val request = EpisodeMetadataRequest(
                animeId = anilistId,
                animeTitle = anime.title,
                episodeNumber = 1,
                malId = anime.malId,
                bannerImage = anime.bannerUrl ?: anime.coverUrl,
                episodeCount = episodeCount,
            )
            Log.i(TAG, "Fetching episode metadata: anilistId=$anilistId, malId=${anime.malId}")
            val metadata = episodeMetadataRepository.fetchAll(request)
            _episodeMetadata.value = metadata
        } catch (e: Exception) {
            Log.w(TAG, "Episode metadata fetch failed (non-fatal)", e)
        }
    }

    /** Finds the library anime row (linked by anilistId, or unlinked by sourceId+url). */
    private suspend fun findLibraryAnime(anime: UnifiedAnime): Anime? {
        val anilistId = anime.anilistId  // local vals so Kotlin can smart-cast
        val sourceId = anime.sourceId
        return when {
            anilistId != null -> animeRepository.getByAnilistId(anilistId)
            sourceId != null -> animeRepository.getBySourceAndUrl(sourceId, anime.url)
            else -> null
        }
    }

    /**
     * Observes the library save state (favorite flag) for this anime.
     *
     * - Linked anime (anilistId != null): reactive via `observeByAnilistId`.
     * - Unlinked extension anime: polled after each load via `getBySourceAndUrl`
     *   (no reactive Flow for sourceId+url keying today — acceptable; the flag
     *   refreshes on load/switch).
     */
    private fun observeLibraryState() {
        viewModelScope.launch {
            val anilistId = currentAnilistId()
            if (anilistId != null) {
                animeRepository.observeByAnilistId(anilistId).collect { anime ->
                    _isSaved.value = anime?.favorite == true
                }
            } else {
                // Unlinked extension anime — refresh the saved flag after each load.
                animeState.collect { state ->
                    if (state is DetailState.Success) {
                        val existing = findLibraryAnime(state.anime)
                        _isSaved.value = existing?.favorite == true
                    }
                }
            }
        }
    }

    /** Upserts the anime into the library with favorite=true. Returns the DB row id. */
    private suspend fun saveAnimeToLibrary(anime: UnifiedAnime): Long {
        val now = System.currentTimeMillis()
        val newAnime = Anime(
            id = 0,
            url = anime.url,
            title = anime.title,
            artist = anime.artist,
            author = anime.author,
            description = anime.description,
            genre = anime.genres,
            coverUrl = anime.coverUrl,
            status = when (anime.status) {
                app.confused.anikuta.core.common.model.details.UnifiedStatus.FINISHED -> AnimeStatus.COMPLETED
                app.confused.anikuta.core.common.model.details.UnifiedStatus.RELEASING -> AnimeStatus.ONGOING
                app.confused.anikuta.core.common.model.details.UnifiedStatus.CANCELLED -> AnimeStatus.CANCELLED
                app.confused.anikuta.core.common.model.details.UnifiedStatus.HIATUS -> AnimeStatus.ON_HIATUS
                app.confused.anikuta.core.common.model.details.UnifiedStatus.NOT_YET_RELEASED -> AnimeStatus.UNKNOWN
                app.confused.anikuta.core.common.model.details.UnifiedStatus.UNKNOWN -> AnimeStatus.UNKNOWN
            },
            thumbnailUrl = null,
            favorite = true,
            sourceId = anime.sourceId ?: 0L,
            dateAdded = now,
            viewerFlags = 0,
            nextUpdate = 0L,
            updateStrategy = 0,
            coverLastModified = 0L,
            releaseDate = null,
            lastRefresh = now,
            lastMetadataFetch = now,
            nextEpisodeCheck = null,
            anilistId = anime.anilistId,
            coverColor = anime.coverColorHex,
            score = anime.averageScore?.toDouble(),
            totalEpisodes = anime.episodeCount,
            lastWatched = 0L,
            nextAiringEpisode = anime.nextAiringEpisode?.episode,
        )
        val id = animeRepository.upsert(newAnime)
        categoryRepository.setAnimeCategories(id, listOf(Category.DEFAULT_ID))
        return id
    }

    private fun sourcePrefKey(anilistId: Int) = "source_pref_$anilistId"

    companion object {
        private const val TAG = "AnikutaDetailVM"
        private const val PREFS_NAME = "anikuta_source_prefs"
    }
}

/** Converts a DB [app.confused.anikuta.core.common.model.Episode] back to an [SEpisode] for the UI. */
private fun app.confused.anikuta.core.common.model.Episode.toSEpisode(): SEpisode =
    SEpisodeImpl().apply {
        url = this@toSEpisode.url ?: ""
        name = this@toSEpisode.name
        episode_number = this@toSEpisode.episodeNumber
        date_upload = this@toSEpisode.dateUpload ?: 0
        scanlator = this@toSEpisode.scanlator
        summary = this@toSEpisode.summary
        preview_url = this@toSEpisode.previewUrl
        fillermark = this@toSEpisode.fillermark == "filler"
    }

// ── State types ──

sealed interface DetailState {
    data object Loading : DetailState
    data class Success(val anime: UnifiedAnime) : DetailState
    data class Error(val message: String) : DetailState
}

sealed interface EpisodeState {
    data object Idle : EpisodeState
    data object Searching : EpisodeState
    data class Loading(val sourceName: String) : EpisodeState
    data class Loaded(val episodes: List<SEpisode>, val sourceName: String) : EpisodeState
    data object NoMatch : EpisodeState
    data object NotReleased : EpisodeState
    data class Error(val message: String) : EpisodeState
}
