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
    private val viewPreferenceStore: app.confused.anikuta.data.extension.cache.DetailsViewPreferenceStore,
    private val appContext: Context,
    /** Fix 2 (SOURCE-SWITCH-FIXES): when `true`, the [init] block calls
     * `loadInternal(forceRefresh = true)` instead of `load()` — bypassing the
     * DB-first short-circuit + forcing a fresh fetch from the provider. Used by
     * `AppController.unlinkFromAniList` so the post-unlink page shows fresh
     * extension data (overwriting stale AniList metadata via
     * `updateMetadataFromExtension` in `persistEpisodes`). */
    private val forceInitialRefresh: Boolean = false,
) : ViewModel() {

    // ── State ──

    private val _animeState = MutableStateFlow<DetailState>(DetailState.Loading)
    val animeState: StateFlow<DetailState> = _animeState.asStateFlow()

    private val _episodeState = MutableStateFlow<EpisodeState>(EpisodeState.Idle)
    val episodeState: StateFlow<EpisodeState> = _episodeState.asStateFlow()

    /** Per-episode metadata map (episodeNumber → EpisodeMetadata). */
    private val _episodeMetadata = MutableStateFlow<Map<Int, EpisodeMetadata>>(emptyMap())
    val episodeMetadata: StateFlow<Map<Int, EpisodeMetadata>> = _episodeMetadata.asStateFlow()

    /**
     * Whether the episode-metadata fetch has completed (success OR early-return OR error).
     *
     * Drives the small spinner next to the "Episodes" heading. The spinner shows
     * while metadata is being fetched AND the metadata map is empty. For unlinked
     * extension anime (anilistId == null) the fetch is skipped — without this flag
     * the spinner would spin forever (the empty map never fills).
     */
    private val _metadataFetchComplete = MutableStateFlow(false)
    val metadataFetchComplete: StateFlow<Boolean> = _metadataFetchComplete.asStateFlow()

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
        // Fix 2 (SOURCE-SWITCH-FIXES): when forceInitialRefresh is true (passed by
        // AppController.unlinkFromAniList), bypass the DB-first short-circuit + force
        // a fresh fetch from the provider — so the post-unlink page shows fresh
        // extension data instead of stale AniList metadata.
        if (forceInitialRefresh) {
            Log.i(TAG, "init: forceInitialRefresh=true — bypassing DB-first short-circuit")
            loadInternal(forceRefresh = true)
        } else {
            load()
        }
        observeLibraryState()
        viewModelScope.launch {
            categoryRepository.observeVisible().collect { _categories.value = it }
        }
    }

    // ── Public API ──

    /** Initial load (or reload after switching data source / extension). Uses DB-first. */
    fun load() = loadInternal(forceRefresh = false)

    /** Refreshes everything (pull-to-refresh). Forces a fresh network fetch. */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        loadInternal(forceRefresh = true)
    }

    private fun loadInternal(forceRefresh: Boolean) {
        viewModelScope.launch {
            _animeState.value = DetailState.Loading
            _episodeState.value = EpisodeState.Searching
            try {
                val provider = registry.forSource(_currentDataSource.value)
                val result = withContext(Dispatchers.IO) { provider.load(activeRequest, forceRefresh = forceRefresh) }
                if (result == null) {
                    _animeState.value = DetailState.Error("Anime not found")
                    _episodeState.value = EpisodeState.NoMatch
                    return@launch
                }
                _animeState.value = DetailState.Success(result.anime)
                renderEpisodes(result.anime, result.episodes)

                // Set up the current match for the source switcher + manual search.
                setupCurrentMatch(result.anime)

                // Update the library cover to match the loaded view's cover — so the
                // library reflects the user's preferred data source (extension cover if
                // the user switched to Extension view, AniList cover if AniList view).
                launch { updateCoverFromLoadedAnime(result.anime) }

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
        // Persist the per-anime preference (so re-open respects the user's choice).
        saveViewPreference(target)
        // Force-refresh the first load after a switch — the DB-first short-circuit
        // would return the OLD cover (from the previous view's load). Forcing a fresh
        // fetch ensures the cover + metadata update to the new source's data.
        loadInternal(forceRefresh = true)
    }

    /**
     * Saves the per-anime data-source preference + updates the library cover.
     *
     * When the user prefers Extension view, the library should show the extension's
     * cover (not AniList's). When they switch back to AniList, restore the AniList cover.
     * The DB row's coverUrl/coverColor is updated so the library naturally reflects it.
     */
    private fun saveViewPreference(dataSource: DataSource) {
        viewModelScope.launch {
            try {
                val unified = (_animeState.value as? DetailState.Success)?.anime
                val anilistId = currentAnilistId()
                // Save the preference.
                if (anilistId != null) {
                    viewPreferenceStore.set(anilistId, dataSource)
                } else {
                    val sid = unified?.sourceId
                    if (sid != null) {
                        viewPreferenceStore.set(sid, unified.url, dataSource)
                    }
                }
                // Update the library cover to match the preferred source.
                if (unified != null) {
                    updateLibraryCover(unified, dataSource)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save view preference (non-fatal)", e)
            }
        }
    }

    /**
     * Updates the `Anime` DB row's coverUrl + coverColor to match the preferred
     * data source. Called from [saveViewPreference] when the user switches views.
     *
     * The [unified] is the CURRENT view's anime (before the switch); [dataSource]
     * is the TARGET view. We need to load the target view's cover after the switch
     * completes. For simplicity, we update the cover AFTER the load() succeeds —
     * the [load] method's success path calls [updateCoverFromLoadedAnime].
     */
    private suspend fun updateLibraryCover(unified: UnifiedAnime, dataSource: DataSource) {
        // The cover update happens after load() — see updateCoverFromLoadedAnime.
        // This method is a placeholder for any immediate pre-switch cover logic.
    }

    /**
     * After [load] succeeds, updates the library cover to match the loaded anime's
     * cover — so the library reflects the user's preferred data source.
     */
    private suspend fun updateCoverFromLoadedAnime(anime: UnifiedAnime) {
        try {
            val anilistId = anime.anilistId
            if (anilistId != null) {
                animeRepository.updatePreferredCoverByAnilistId(
                    anilistId = anilistId,
                    coverUrl = anime.coverUrl,
                    coverColor = anime.coverColorHex,
                )
            } else {
                val sid = anime.sourceId
                if (sid != null) {
                    animeRepository.updatePreferredCoverBySourceAndUrl(
                        sourceId = sid,
                        url = anime.url,
                        coverUrl = anime.coverUrl,
                        coverColor = anime.coverColorHex,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update library cover (non-fatal)", e)
        }
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
        val oldRequest = activeRequest
        // Hoisted to outer scope so the identity.json update below can read
        // the old sourceId/sourceUrl (used to compute the OLD contentId for
        // the folder lookup).
        val oldExt = oldRequest as? DetailsRequest.ByExtension

        // Save the new source link (so re-open skips re-matching).
        // Phase 4: SourceLinkStore + sourcePrefKey now keyed by content_id.
        if (anilistId != null) {
            val contentId = "al:$anilistId"
            sourceLinkStore.saveLink(contentId, source.id, sAnime.url, sAnime.title)
            sourcePrefs.edit().putLong(sourcePrefKey(contentId), source.id).apply()
            Log.d(TAG, "switchExtension: saved link contentId=$contentId sourceId=${source.id}")
        } else {
            // Extension-only anime: update the existing library row's source_id + url
            // so the library entry follows the new source (preserves _id, favorite,
            // category membership). Without this, the old library entry stays pointing
            // at the uninstalled source + a NEW entry is created for the new source.
            if (oldExt != null) {
                viewModelScope.launch {
                    try {
                        val oldEntry = animeRepository.getBySourceAndUrl(oldExt.sourceId, oldExt.animeUrl)
                        if (oldEntry != null) {
                            animeRepository.updateSourceAndUrl(oldEntry.id, source.id, sAnime.url)
                            Log.i(TAG, "switchExtension: updated library row id=${oldEntry.id} " +
                                "from sourceId=${oldExt.sourceId} url=${oldExt.animeUrl} " +
                                "to sourceId=${source.id} url=${sAnime.url}")
                            // Restart the library observer so it tracks the new source+url.
                            observeLibraryState()
                        } else {
                            Log.w(TAG, "switchExtension: no existing library row found for " +
                                "sourceId=${oldExt.sourceId} url=${oldExt.animeUrl} — new entry will be created")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "switchExtension: failed to update library row source (non-fatal)", e)
                    }
                }
            }
        }

        // ── DOWNLOAD-IDENTITY-STORAGE-UPDATE: rewrite the folder's identity.json ──
        // The source changed (extension switch); the folder's identity.json needs
        // its `sourceId` + `sourceUrl` fields updated so the on-disk record
        // matches the new extension. The `contentId` field is preserved as-is —
        // for linked anime (`al:X`) the contentId is source-independent by
        // construction; for unlinked anime (`aniyomi:OLD_SID:OLD_URL`) the
        // contentId STAYS at the old value (this is a known limitation of the
        // current implementation: the on-disk identity's contentId becomes
        // stale relative to the system's notion of the contentId for unlinked
        // anime after a source switch — see worklog entry for
        // DOWNLOAD-IDENTITY-STORAGE-UPDATE for details).
        //
        // Runs on Dispatchers.IO (identity.json write is synchronous SAF I/O).
        // Best-effort: failures are logged but don't block the source switch
        // (the UI's sourceId/url are already updated by the code above; a
        // missed identity write means the folder would be findable by the OLD
        // identity only, which the user can recover by re-linking).
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val identityManager = org.koin.core.context.GlobalContext.get()
                        .get<app.confused.anikuta.core.downloadidentity.DownloadIdentityManager>()
                    val contentId = if (anilistId != null) {
                        "al:$anilistId"
                    } else if (oldExt != null) {
                        "aniyomi:${oldExt.sourceId}:${oldExt.animeUrl}"
                    } else {
                        null
                    }
                    if (contentId != null) {
                        val newIdentity = app.confused.anikuta.core.downloadidentity.DownloadIdentity(
                            contentId = contentId,
                            anilistId = anilistId,
                            sourceId = source.id,
                            sourceUrl = sAnime.url,
                            title = sAnime.title,
                        )
                        val updated = identityManager.updateIdentity(contentId, newIdentity)
                        Log.i(TAG, "switchExtension: identity.json update " +
                            "${if (updated) "succeeded" else "skipped (no folder found)"} " +
                            "(contentId=$contentId, new sourceId=${source.id}, new url=${sAnime.url})")
                    } else {
                        Log.w(TAG, "switchExtension: cannot compute contentId for identity " +
                            "update (anilistId=$anilistId, oldExt=$oldExt) — skipping")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "switchExtension: identity.json update failed (non-fatal) — " +
                        "downloads may keep the old source identity", e)
                }
            }
        }

        // Update the active request to the new extension (for future "View from Extension").
        activeRequest = DetailsRequest.ByExtension(
            sourceId = source.id,
            animeUrl = sAnime.url,
            animeTitle = sAnime.title,
            anilistId = anilistId,
        )
        _currentMatch.value = SourceMatcher.SourceMatch(source, sAnime, 1.0)

        if (_currentDataSource.value == DataSource.ANILIST) {
            // ── In AniList mode: ONLY reload episodes from the new extension ──
            // The anime metadata (title, synopsis, score, etc.) stays from AniList.
            // The user explicitly chose to view AniList data; switching the extension
            // source from the episodes header should only change which extension
            // provides the episodes — NOT silently switch the entire view.
            reloadEpisodesOnly()
        } else {
            // ── In Extension mode: full reload (the extension IS the data source) ──
            // Fix 4 (SOURCE-SWITCH-FIXES): force-refresh (not load()) so the DB-first
            // short-circuit is bypassed. The provider re-fetches from the new extension
            // + calls updateMetadataFromExtension (Fix 3) to overwrite the row's
            // title/description/cover/genre/etc. with the new source's data. Without
            // this, the DB-first short-circuit would return the OLD source's stale
            // metadata even though source_id + url were updated above.
            Log.i(TAG, "switchExtension: Extension mode — calling loadInternal(forceRefresh=true) " +
                "to fetch fresh metadata from new source '${source.name}'")
            loadInternal(forceRefresh = true)
        }
    }

    /**
     * Reloads ONLY the episode list from the extension provider — the anime metadata
     * (in [_animeState]) is preserved. Used by [switchExtension] when in AniList mode.
     */
    private fun reloadEpisodesOnly() {
        viewModelScope.launch {
            val sourceName = _currentMatch.value?.source?.name ?: "Unknown"
            _episodeState.value = EpisodeState.Loading(sourceName)
            try {
                val extProvider = registry.forSource(DataSource.EXTENSION)
                val episodes = withContext(Dispatchers.IO) { extProvider.loadEpisodes(activeRequest) }
                if (episodes.isNullOrEmpty()) {
                    _episodeState.value = EpisodeState.NoMatch
                } else {
                    val sEpisodes = episodes.map { it.toSEpisode() }
                    _episodeState.value = EpisodeState.Loaded(sEpisodes, sourceName)
                    Log.i(TAG, "Reloaded ${episodes.size} episodes from extension (episodes-only)")
                    // Fetch episode metadata for the new episodes.
                    val anime = (_animeState.value as? DetailState.Success)?.anime
                    if (anime != null) launch { fetchEpisodeMetadata(anime, episodes.size) }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "reloadEpisodesOnly failed", e)
                _episodeState.value = EpisodeState.Error("Failed: ${e.message}")
            }
        }
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
            val contentId = "al:$anilistId"
            sourcePrefs.edit().putLong(sourcePrefKey(contentId), match.source.id).apply()
            Log.d(TAG, "switchSource: saved pref contentId=$contentId sourceId=${match.source.id}")
        }
        _currentMatch.value = match
        Toast.makeText(appContext, "Switched to ${match.source.name}", Toast.LENGTH_SHORT).show()
        switchExtension(match.source, match.sAnime)
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
                    // Quick-win fallback: reflect the change immediately in the UI
                    // so the save icon flips before the reactive flow emits.
                    _isSaved.value = newFav
                    Log.i(TAG, "toggleSave: updated existing id=${existing.id}, newFav=$newFav")
                    if (newFav) categoryRepository.setAnimeCategories(existing.id, listOf(Category.DEFAULT_ID))
                } else {
                    saveAnimeToLibrary(unified)
                    // Quick-win fallback: reflect the new save immediately.
                    _isSaved.value = true
                    Log.i(TAG, "toggleSave: saved new library entry for '${unified.title}'")
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

    /**
     * Determines the initial data source — reads the per-anime preference first,
     * falls back to the entry mode (AniList entry → ANILIST; Extension entry → EXTENSION).
     *
     * This makes re-opening an anime respect the user's previous "View from Extension" /
     * "View from AniList" choice.
     */
    private fun initialDataSource(): DataSource {
        // Check the per-anime preference.
        val pref = when (initialRequest) {
            is DetailsRequest.ByAniListId -> viewPreferenceStore.get(initialRequest.anilistId)
            is DetailsRequest.ByExtension -> {
                viewPreferenceStore.get(initialRequest.sourceId, initialRequest.animeUrl)
                    ?: initialRequest.anilistId?.let { viewPreferenceStore.get(it) }
            }
        }
        if (pref != null) return pref
        // Fall back to the entry mode.
        return when (initialRequest) {
            is DetailsRequest.ByAniListId -> DataSource.ANILIST
            is DetailsRequest.ByExtension -> DataSource.EXTENSION
        }
    }

    /** Rebuilds the request when switching data sources. */
    private fun requestForDataSource(target: DataSource, current: DetailsRequest): DetailsRequest {
        // Extract whatever identity we have from the current request.
        val anilistId = when (current) {
            is DetailsRequest.ByAniListId -> current.anilistId
            is DetailsRequest.ByExtension -> current.anilistId
                ?: extensionLinkStore.getAniListId(current.sourceId, current.animeUrl)
        }
        // Phase 4: SourceLinkStore keys by content_id. Compute once for the lookups.
        val contentId = anilistId?.let { "al:$it" }
        val savedLink = contentId?.let { sourceLinkStore.getLink(it) }
        val sourceId = when (current) {
            is DetailsRequest.ByAniListId -> savedLink?.sourceId
            is DetailsRequest.ByExtension -> current.sourceId
        }
        val animeUrl = when (current) {
            is DetailsRequest.ByAniListId -> savedLink?.animeUrl
            is DetailsRequest.ByExtension -> current.animeUrl
        }
        val animeTitle = when (current) {
            is DetailsRequest.ByAniListId -> savedLink?.animeTitle
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

    /**
     * Sets up [_currentMatch] from the unified anime's sourceId (for the source switcher).
     *
     * Fix 5 (SOURCE-SWITCH-FIXES): defensive guard — DON'T overwrite [_currentMatch] if
     * the load result's sourceId matches the already-set match's source.id. This prevents
     * `loadInternal` (called after `switchExtension`) from clobbering the user-picked
     * SAnime (which has the correct title from the new source) with a fresh SAnime built
     * from the (possibly stale) DB row's title. Only overwrite when the sourceId actually
     * changed (meaning a different source was loaded — e.g. on the very first load, or
     * when the DB row's sourceId differs from what the user picked).
     */
    private fun setupCurrentMatch(anime: UnifiedAnime) {
        val sourceId = anime.sourceId ?: run {
            Log.d(TAG, "setupCurrentMatch: anime.sourceId is null — not setting _currentMatch")
            return
        }
        // Defensive guard: don't clobber a freshly-set _currentMatch from switchExtension.
        // The user-picked SAnime has the correct title; the DB row's title may be stale
        // during the refresh window (between switchExtension setting _currentMatch and
        // updateMetadataFromExtension writing the new title to the DB).
        val existing = _currentMatch.value
        if (existing != null && existing.source.id == sourceId) {
            Log.d(TAG, "setupCurrentMatch: skipping overwrite — _currentMatch.source.id " +
                "($sourceId) matches the load result; preserving user-picked SAnime " +
                "(title='${existing.sAnime.title}')")
            return
        }
        val source = sourceMatcher.getSourceById(sourceId) ?: run {
            Log.w(TAG, "setupCurrentMatch: source $sourceId not installed — not setting _currentMatch")
            return
        }
        // Per user feedback: the ManualSearchSheet's "currently connected" card should
        // show the extension's title + thumbnail, NOT the AniList title. When in AniList
        // mode, anime.title comes from AniList (not the extension). Use the SourceLinkStore's
        // saved animeTitle (which is the extension's title) + the saved animeUrl for the
        // SAnime. Fall back to anime.title if no saved link exists.
        val anilistId = currentAnilistId()
        val contentId = if (anilistId != null) "al:$anilistId" else null
        val savedLink = contentId?.let { sourceLinkStore.getLink(it) }
        val extTitle = savedLink?.animeTitle ?: anime.title
        val extUrl = savedLink?.animeUrl ?: anime.url
        val sAnime = SAnimeImpl().apply {
            url = extUrl
            title = extTitle
            // Try to get the thumbnail from the extension source if possible.
            thumbnail_url = null // The ManualSearchSheet will use currentMatch.sAnime.thumbnail_url
        }
        Log.i(TAG, "setupCurrentMatch: setting _currentMatch from load result " +
            "(sourceId=$sourceId, source='${source.name}', extTitle='$extTitle', " +
            "animeTitle='${anime.title}', hasSavedLink=${savedLink != null})")
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

    /**
     * Searches all sources in the background (for the source switcher) without blocking.
     *
     * **Deferred — only runs once per anime.** Skips if [_allMatches] is already
     * populated. The sequential `matchAll()` across all installed extensions is
     * CPU-intensive (Levenshtein distance per source) + makes network calls.
     * Running it on every load/refresh/switch is wasteful — the results don't
     * change unless the user installs/uninstalls an extension (which triggers a
     * re-load via the source-change flow).
     *
     * Logcat emits a `Log.d` when the call is skipped so the deferral is
     * observable in diagnostics.
     */
    private suspend fun searchAllSourcesInBackground(title: String) {
        if (_allMatches.value.isNotEmpty()) {
            Log.d(TAG, "searchAllSourcesInBackground: skipped — allMatches already populated " +
                "(${_allMatches.value.size} match(es))")
            return
        }
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
        // Reset the completion flag at the start so the spinner can show again
        // on reload / refresh / source switch.
        _metadataFetchComplete.value = false
        try {
            val anilistId = anime.anilistId ?: run {
                Log.i(TAG, "Skipping episode metadata — no anilistId (unlinked extension anime)")
                _metadataFetchComplete.value = true
                return
            }
            // Phase 4: EpisodeMetadataCache is keyed by content_id ("al:$anilistId").
            // UnifiedAnime doesn't expose contentId yet — derive from anilistId for now.
            // (Future: when UnifiedAnime carries contentId, prefer anime.contentId here.)
            val contentId = "al:$anilistId"
            val request = EpisodeMetadataRequest(
                animeId = anilistId,
                contentId = contentId,
                animeTitle = anime.title,
                episodeNumber = 1,
                malId = anime.malId,
                bannerImage = anime.bannerUrl ?: anime.coverUrl,
                episodeCount = episodeCount,
            )
            Log.i(TAG, "Fetching episode metadata: contentId=$contentId, anilistId=$anilistId, malId=${anime.malId}")
            val metadata = episodeMetadataRepository.fetchAll(request)
            _episodeMetadata.value = metadata
            _metadataFetchComplete.value = true
        } catch (e: Exception) {
            Log.w(TAG, "Episode metadata fetch failed (non-fatal)", e)
            _metadataFetchComplete.value = true
        }
    }

    /**
     * Finds the library anime row (linked by anilistId, or unlinked by sourceId+url).
     *
     * **Fix 4 (UNLINK-LINK-SAVE-FIXES):** when `anilistId != null` but the
     * anilist-keyed lookup returns null (e.g. the row was unlinked — anilist_id
     * cleared — but is still saved as an extension-only row), falls back to a
     * `(sourceId, url)` lookup. This keeps `toggleSave` / `saveToCategories`
     * working through the unlink transition window — without it, the user would
     * see the save button as unsaved (the lookup missed) and toggling would
     * insert a duplicate row.
     */
    private suspend fun findLibraryAnime(anime: UnifiedAnime): Anime? {
        val anilistId = anime.anilistId  // local vals so Kotlin can smart-cast
        val sourceId = anime.sourceId
        return when {
            anilistId != null -> {
                val byAnilist = animeRepository.getByAnilistId(anilistId)
                if (byAnilist != null) {
                    byAnilist
                } else if (sourceId != null) {
                    val bySrc = animeRepository.getBySourceAndUrl(sourceId, anime.url)
                    if (bySrc != null) {
                        Log.d(TAG, "findLibraryAnime: anilistId=$anilistId missed — " +
                            "fallback (sourceId=$sourceId, url=${anime.url}) → id=${bySrc.id}")
                    }
                    bySrc
                } else {
                    null
                }
            }
            sourceId != null -> animeRepository.getBySourceAndUrl(sourceId, anime.url)
            else -> null
        }
    }

    /**
     * Observes the library save state (favorite flag) for this anime.
     *
     * - Linked anime (anilistId != null): reactive via `observeByAnilistId`.
     *   **Fix 3 (UNLINK-LINK-SAVE-FIXES):** falls back to `getBySourceAndUrl`
     *   when the anilist-keyed row is missing (e.g. after `unlinkFromAniList`
     *   cleared `anilist_id`). Without the fallback, the unlink transition
     *   window would briefly (or permanently, if the observer was started in
     *   AniList mode with a stale anilistId) show `_isSaved = false`.
     * - Unlinked extension anime: reactive via `observeBySourceAndUrl`
     *   (Phase fix — was previously polled after each load via `getBySourceAndUrl`).
     */
    private fun observeLibraryState() {
        viewModelScope.launch {
            val anilistId = currentAnilistId()
            val req = activeRequest
            val extSourceId = (req as? DetailsRequest.ByExtension)?.sourceId
            val extUrl = (req as? DetailsRequest.ByExtension)?.animeUrl

            if (anilistId != null) {
                Log.i(TAG, "observeLibraryState: primary=anilistId=$anilistId" +
                    (if (extSourceId != null && extUrl != null) ", fallback=(sourceId=$extSourceId, url=$extUrl)" else ""))
                // Primary: observe by anilistId. Fall back to (sourceId, url) when the
                // anilist-keyed row is missing (e.g., after unlink cleared anilist_id).
                animeRepository.observeByAnilistId(anilistId).collect { anime ->
                    if (anime != null) {
                        _isSaved.value = anime.favorite
                    } else if (extSourceId != null && extUrl != null) {
                        // Fallback: the row may have been unlinked (anilist_id cleared).
                        // Look it up by the extension's source+url.
                        val extAnime = withContext(Dispatchers.IO) {
                            animeRepository.getBySourceAndUrl(extSourceId, extUrl)
                        }
                        _isSaved.value = extAnime?.favorite == true
                        Log.d(TAG, "observeLibraryState: anilistId=$anilistId missed — " +
                            "fallback (sourceId=$extSourceId, url=$extUrl) → favorite=${extAnime?.favorite}")
                    } else {
                        _isSaved.value = false
                    }
                }
            } else if (extSourceId != null && extUrl != null) {
                // Extension-only anime — observe by source+url.
                Log.i(TAG, "observeLibraryState: observing extension-only anime " +
                    "by sourceId=$extSourceId url=$extUrl")
                animeRepository.observeBySourceAndUrl(extSourceId, extUrl).collect { anime ->
                    _isSaved.value = anime?.favorite == true
                }
            } else {
                _isSaved.value = false
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

    /**
     * SharedPreferences key for the user's preferred extension source for a content.
     * Phase 4 (ADR-050): now keyed by content_id (e.g., `"source_pref_al:154587"`)
     * instead of anilistId. Legacy keys (`"source_pref_154587"`) are not migrated
     * — the SourceLinkMigrator handles the SourceLinkStore migration; this prefs
     * file is a separate concern (the user re-selects on first open post-Phase-4).
     */
    private fun sourcePrefKey(contentId: String) = "source_pref_$contentId"

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
