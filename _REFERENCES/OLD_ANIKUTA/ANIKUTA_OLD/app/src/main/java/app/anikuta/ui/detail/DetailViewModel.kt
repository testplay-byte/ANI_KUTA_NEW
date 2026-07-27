package app.anikuta.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.cache.CacheManager
import app.anikuta.source.bridge.AniyomiSourceBridge
import app.anikuta.source.bridge.SourceMatchResult
import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.model.SAnime
import app.anikuta.source.api.model.SEpisode
import app.anikuta.source.api.model.Video
import app.anikuta.ui.library.LibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * ViewModel for the anime detail page.
 *
 * Two-stage load:
 *  1. Fetch full AniList metadata via CacheManager (24h TTL).
 *  2. On metadata success, search installed extension sources for a matching
 *     SAnime (via [AniyomiSourceBridge], fuzzy ≥80% title match). If matched,
 *     fetch the episode list from that source.
 *
 * Episode playback: [resolveVideoUrl] fetches the video list for a given
 * episode on demand (not upfront — it's a network call per episode) and
 * returns the best video URL for the player.
 *
 * Phase 5 task 5.3: replaces the old "No streaming source available" +
 * "Play sample" placeholder with real extension-sourced episodes. The sample
 * stream button remains as a fallback when no extension matches.
 */
class DetailViewModel(
    private val anilistId: Int,
) : ViewModel() {

    private val anilistRepo: AniListRepository? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val cacheManager: CacheManager? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val sourceBridge: AniyomiSourceBridge? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val libraryStore: LibraryStore? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val subDubStore: app.anikuta.data.cache.SubDubStore? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val json = Json { ignoreUnknownKeys = true }

    private val _anime = MutableStateFlow<DetailState>(DetailState.Loading)
    val anime: StateFlow<DetailState> = _anime.asStateFlow()

    // Initial value read from the LibraryStore so the bookmark icon reflects
    // the persisted state immediately on screen entry (Phase 5 task 5.6).
    private val _isSaved = MutableStateFlow(libraryStore?.isSaved(anilistId) ?: false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val _episodes = MutableStateFlow<EpisodeState>(EpisodeState.Idle)
    val episodes: StateFlow<EpisodeState> = _episodes.asStateFlow()

    /** Resolved video URL for the player (set by resolveVideoUrl, consumed by the UI). */
    private val _playRequest = MutableStateFlow<PlayRequest?>(null)
    val playRequest: StateFlow<PlayRequest?> = _playRequest.asStateFlow()

    /** True while resolving an episode's video URL (extension network call). */
    private val _resolvingEpisode = MutableStateFlow(false)
    val resolvingEpisode: StateFlow<Boolean> = _resolvingEpisode.asStateFlow()

    /** Current pull-to-refresh stage (for the 3-stage indicator). */
    private val _refreshStage = MutableStateFlow<RefreshStage>(RefreshStage.Idle)
    val refreshStage: StateFlow<RefreshStage> = _refreshStage.asStateFlow()

    /** True while any refresh is in progress. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** True while in-app episode metadata is being fetched (Jikan/AniList). */
    private val _isEnrichingMetadata = MutableStateFlow(false)
    val isEnrichingMetadata: StateFlow<Boolean> = _isEnrichingMetadata.asStateFlow()

    /** Videos available for the current episode — shown in the quality picker. */
    private val _videoPicker = MutableStateFlow<VideoPickerState>(VideoPickerState.Hidden)
    val videoPicker: StateFlow<VideoPickerState> = _videoPicker.asStateFlow()

    /** Expanded server section in the picker (key = serverName). Empty = all collapsed.
     *  Accordion behavior: only one server expanded at a time. */
    private val _expandedServers = MutableStateFlow<Set<String>>(emptySet())
    val expandedServers: StateFlow<Set<String>> = _expandedServers.asStateFlow()

    /**
     * Toggle a server's expand state. Accordion: if expanding, collapse all
     * others first. This ensures only one server is open at a time.
     */
    fun toggleServer(key: String) {
        _expandedServers.value = _expandedServers.value.let { current ->
            if (key in current) {
                // Already expanded → collapse it
                current - key
            } else {
                // Expanding → close all others, open only this one (accordion)
                setOf(key)
            }
        }
    }

    // Remember the matched source + SAnime so we can fetch episodes/videos later.
    private var matchedSource: AnimeCatalogueSource? = null
    private var matchedSAnime: SAnime? = null

    /**
     * Episode list cache — avoids re-fetching from the extension every time
     * the detail page is re-opened. Keyed by AniList ID. The user reported
     * that re-opening the detail page re-fetched the entire episode list,
     * which was slow + wasted network. Now we cache it in a companion object
     * (survives ViewModel destruction when navigating away + back).
     *
     * Phase 7: on cache hit, we now ALSO launch a background soft-refresh
     * to check for new episodes. The cached list is shown instantly; if the
     * background refresh finds changes, the UI updates smoothly.
     */
    companion object {
        private const val TAG = "DetailViewModel"
        private const val REFRESH_GUARD_MS = 5 * 60 * 1000L  // 5 minutes
        private const val VIDEO_CACHE_TTL_MS = 10 * 60 * 1000L  // 10 minutes
        private val episodeCache = mutableMapOf<Int, Pair<List<SEpisode>, String>>()
        /** Phase 7: short-TTL video cache. Keyed by episode.url. */
        private data class CachedVideoData(val serverSections: List<ServerSection>, val timestamp: Long)
        private val videoCache = mutableMapOf<String, CachedVideoData>()
    }

    private val preferenceStore: app.anikuta.core.preference.PreferenceStore? = try { Injekt.get() } catch (e: Exception) { null }
    private val episodeCacheStore: app.anikuta.data.cache.EpisodeCacheStore? = try { Injekt.get() } catch (e: Exception) { null }

    init {
        loadAnimeDetails()
    }

    private fun loadAnimeDetails() {
        viewModelScope.launch {
            _anime.value = DetailState.Loading
            try {
                if (cacheManager == null || anilistRepo == null) {
                    _anime.value = DetailState.Error("App not properly initialized")
                    return@launch
                }
                val data = cacheManager.getOrFetch(
                    key = "anime_detail_$anilistId",
                    ttlMs = CacheManager.TTL_DETAIL_LONG,
                    supabaseKey = "anime_$anilistId",
                    fetch = { anilistRepo.getAnimeDetails(anilistId) },
                    serialize = { json.encodeToString(AniListAnime.serializer(), it) },
                    deserialize = { json.decodeFromString(AniListAnime.serializer(), it) },
                )
                if (data != null) {
                    _anime.value = DetailState.Success(data)
                    // Stage 2: now that we have the title, search extensions.
                    findEpisodeSource(data)
                } else {
                    _anime.value = DetailState.Error("Failed to load anime details")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Details fetch failed", e)
                _anime.value = DetailState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Search installed extensions for a matching source via the bridge.
     * On match, fetch the episode list. On no-match / not-aired, set the
     * appropriate state so the UI shows the right message.
     *
     * Phase 7: on in-memory cache hit, we now ALSO launch a background
     * soft-refresh to check for new episodes.
     */
    private fun findEpisodeSource(anime: AniListAnime) {
        // Check in-memory cache first (survives navigation, not app restart)
        episodeCache[anilistId]?.let { (eps, sourceName) ->
            Log.d(TAG, "Episode cache hit (in-memory): ${eps.size} episodes from $sourceName")
            _episodes.value = EpisodeState.Loaded(eps, sourceName)
            backgroundRefreshEpisodes(anime, sourceName)
            refreshDownloadedOnDisk()
            return
        }
        // Check disk cache (survives app restart)
        episodeCacheStore?.let { store ->
            viewModelScope.launch {
                val diskCached = store.load(anilistId)
                if (diskCached != null) {
                    val (eps, sourceName) = diskCached
                    Log.d(TAG, "Episode cache hit (disk): ${eps.size} episodes from $sourceName")
                    // Populate in-memory cache too
                    episodeCache[anilistId] = Pair(eps, sourceName)
                    _episodes.value = EpisodeState.Loaded(eps, sourceName)

                    // FIX: Immediately set matchedSource so playEpisode() works
                    // without waiting for the async backgroundRefreshEpisodes.
                    // Previously, matchedSource was only set inside
                    // backgroundRefreshEpisodes (which is async + has a refresh
                    // guard that skips if recently refreshed). This caused
                    // "No source available" errors when the user tapped an
                    // episode before the background refresh completed — or if
                    // the refresh was skipped entirely by the guard.
                    viewModelScope.launch {
                        try {
                            val mgr = uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
                            // Wait for extensions to load (they load async on app start)
                            var retries = 0
                            var source = mgr.getCatalogueSources().find { it.name == sourceName }
                            while (source == null && retries < 20) {
                                kotlinx.coroutines.delay(500)
                                retries++
                                source = mgr.getCatalogueSources().find { it.name == sourceName }
                            }
                            if (source != null) {
                                matchedSource = source
                                // Reconstruct matchedSAnime from persistent cache
                                val sAnimeUrl = preferenceStore?.getString("ext_sanime_url_$anilistId", "")?.get() ?: ""
                                if (sAnimeUrl.isNotBlank()) {
                                    matchedSAnime = app.anikuta.source.api.model.SAnime.create().apply {
                                        url = sAnimeUrl
                                        title = anime.title.preferred()
                                    }
                                }
                                Log.d(TAG, "matchedSource set from disk cache: $sourceName (after $retries retries)")
                            } else {
                                Log.w(TAG, "Source '$sourceName' from disk cache not found in loaded extensions")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to set matchedSource from disk cache", e)
                        }
                    }

                    // Background refresh to check for new episodes
                    backgroundRefreshEpisodes(anime, sourceName)
                    // Background metadata enrichment (if needed)
                    enrichEpisodesWithMetadata(eps, anime)
                    return@launch
                }
                // No disk cache — fall through to extension search
                searchExtensions(anime, sourceBridge ?: run {
                    _episodes.value = EpisodeState.Error("Source bridge not available")
                    return@launch
                })
            }
            return
        }
        val bridge = sourceBridge ?: run {
            _episodes.value = EpisodeState.Error("Source bridge not available")
            return
        }
        searchExtensions(anime, bridge)
    }

    private fun searchExtensions(anime: AniListAnime, bridge: AniyomiSourceBridge) {
        // Check persistent source match cache (survives app restart).
        // This lets us skip the slow extension search if we already know
        // which source + SAnime URL matched.
        val cachedSourceName = preferenceStore?.getString("ext_match_$anilistId", "")?.get()
        val cachedSAnimeUrl = preferenceStore?.getString("ext_sanime_url_$anilistId", "")?.get()
        if (!cachedSourceName.isNullOrBlank() && !cachedSAnimeUrl.isNullOrBlank()) {
            Log.d(TAG, "Persistent source cache hit: $cachedSourceName, fetching episodes...")
            _episodes.value = EpisodeState.Searching
            viewModelScope.launch {
                try {
                    val mgr = uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
                    var retries = 0
                    var source = mgr.getCatalogueSources().find { it.name == cachedSourceName }
                    while (source == null && retries < 20) {
                        kotlinx.coroutines.delay(500)
                        retries++
                        source = mgr.getCatalogueSources().find { it.name == cachedSourceName }
                    }
                    if (source != null) {
                        Log.d(TAG, "Cached source found after $retries retries")
                        val sAnime = app.anikuta.source.api.model.SAnime.create().apply {
                            url = cachedSAnimeUrl
                            title = anime.title.preferred()
                        }
                        loadEpisodes(sAnime, anime, cachedSourceName)
                    } else {
                        Log.w(TAG, "Cached source not found, falling back to full search")
                        fullExtensionSearch(anime, bridge)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Cached source lookup failed", e)
                    fullExtensionSearch(anime, bridge)
                }
            }
            return
        }
        fullExtensionSearch(anime, bridge)
    }

    private fun fullExtensionSearch(anime: AniListAnime, bridge: AniyomiSourceBridge) {
        _episodes.value = EpisodeState.Searching
        viewModelScope.launch {
            try {
                // Wait for source manager to have sources loaded (same fix as
                // the persistent cache path — on first app launch, extensions
                // load async and getCatalogueSources() returns empty)
                val mgr = uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
                var retries = 0
                while (mgr.getCatalogueSources().isEmpty() && retries < 20) {
                    kotlinx.coroutines.delay(500)
                    retries++
                }
                if (retries > 0) {
                    Log.d(TAG, "Waited ${retries * 500}ms for ${mgr.getCatalogueSources().size} sources to load")
                }
                val result = bridge.findMatch(anime)
                when (result) {
                    is SourceMatchResult.NotAired -> {
                        _episodes.value = EpisodeState.NotAired(
                            result.title,
                            result.seasonYear,
                            result.season,
                        )
                    }
                    is SourceMatchResult.NoMatch -> {
                        _episodes.value = EpisodeState.NoMatch(result.searchedTitle)
                    }
                    is SourceMatchResult.SingleMatch -> {
                        loadEpisodes(result.sAnime, anime, result.sourceName)
                    }
                    is SourceMatchResult.MultipleMatches -> {
                        // Auto-pick the best (Q2 decision). User can override later.
                        loadEpisodes(result.best, anime, result.bestSourceName)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Source bridge failed", e)
                _episodes.value = EpisodeState.Error(e.message ?: "Failed to search extensions")
            }
        }
    }

    /**
     * Fetch the episode list from the matched source. We need to find the
     * source object from the sourceManager to call getEpisodeList.
     */
    private suspend fun loadEpisodes(
        sAnime: SAnime,
        anime: AniListAnime,
        sourceName: String,
    ) {
        try {
            val mgr = uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
            // The matched source should be a catalogue source — find it by name.
            val source = mgr.getCatalogueSources().find { it.name == sourceName }
            if (source == null) {
                _episodes.value = EpisodeState.Error("Source '$sourceName' not found")
                return
            }
            matchedSource = source
            matchedSAnime = sAnime
            _episodes.value = EpisodeState.LoadingEpisodes
            val eps = withContext(Dispatchers.IO) { source.getEpisodeList(sAnime) }
            Log.d(TAG, "Loaded ${eps.size} episodes from '$sourceName'")
            // Cache in-memory (survives navigation)
            episodeCache[anilistId] = Pair(eps, sourceName)
            // Cache source match persistently (survives app restart)
            preferenceStore?.getString("ext_match_$anilistId", "")?.set(sourceName)
            preferenceStore?.getString("ext_sanime_url_$anilistId", "")?.set(sAnime.url)
            // Cache episodes to disk (survives app restart — no re-fetch needed)
            episodeCacheStore?.save(anilistId, sourceName, eps)
            _episodes.value = EpisodeState.Loaded(eps, sourceName)

            // Scan filesystem for already-downloaded episodes (show green checkmarks)
            refreshDownloadedOnDisk()

            // Phase 7.5: In-app metadata enrichment for episodes missing thumbnails/titles/descriptions
            enrichEpisodesWithMetadata(eps, anime)
        } catch (e: Exception) {
            Log.e(TAG, "Episode list fetch failed", e)
            _episodes.value = EpisodeState.Error(e.message ?: "Failed to load episodes")
        }
    }

    /**
     * Phase 7.5: Enrich episodes with metadata from Jikan (MAL) + AniList
     * streaming episodes. Only runs if the setting is enabled AND at least
     * one episode is missing preview_url or summary.
     */
    private fun enrichEpisodesWithMetadata(
        episodes: List<SEpisode>,
        anime: AniListAnime,
    ) {
        // Check if the setting is enabled
        val prefs = try { uy.kohesive.injekt.Injekt.get<app.anikuta.player.PlayerPreferences>() } catch (e: Exception) { return }
        if (!prefs.enableInAppMetadataFetch().get()) return

        // Read the per-field fetch toggles
        val fetchThumbnails = prefs.fetchMetadataThumbnails().get()
        val fetchTitles = prefs.fetchMetadataTitles().get()
        val fetchSummaries = prefs.fetchMetadataSummaries().get()

        // Check if any episodes are missing metadata that we're configured to fetch
        val needsEnrichment = episodes.any { ep ->
            (fetchThumbnails && ep.preview_url.isNullOrBlank()) ||
            (fetchSummaries && ep.summary.isNullOrBlank()) ||
            (fetchTitles && (ep.name.isBlank() || ep.name.matches(Regex("(?i)episode\\s*\\d+"))))
        }
        if (!needsEnrichment) {
            Log.d(TAG, "All episodes have metadata — skipping in-app fetch")
            return
        }

        _isEnrichingMetadata.value = true
        viewModelScope.launch {
            try {
                val fetcher = app.anikuta.data.metadata.EpisodeMetadataFetcher()
                val metadata = fetcher.fetch(anime, anilistId, episodes.size)

                if (metadata.isEmpty()) {
                    Log.d(TAG, "No metadata found from Jikan/AniList")
                    delay(500) // brief delay so the indicator is visible
                    return@launch
                }

                // Enrich episodes with fetched metadata.
                // CRITICAL: create NEW SEpisode objects instead of mutating in place.
                // Compose's LazyColumn skips recomposition when it receives the same
                // object references (equality check passes). By creating new objects,
                // Compose detects the change and recomposes visible items immediately —
                // no need to scroll to trigger a refresh.
                var enrichedCount = 0
                val enrichedEpisodes = episodes.map { ep ->
                    val epNum = ep.episode_number.toInt().coerceAtLeast(1)
                    val meta = metadata[epNum]
                    if (meta == null) {
                        ep  // no metadata for this episode — keep original
                    } else {
                        var changed = false
                        val newPreviewUrl = if (fetchThumbnails && ep.preview_url.isNullOrBlank() && !meta.thumbnailUrl.isNullOrBlank()) {
                            changed = true; meta.thumbnailUrl
                        } else ep.preview_url

                        val newSummary = if (fetchSummaries && ep.summary.isNullOrBlank() && !meta.description.isNullOrBlank()) {
                            changed = true; meta.description
                        } else ep.summary

                        val newName = if (fetchTitles && (ep.name.isBlank() || ep.name.matches(Regex("(?i)episode\\s*\\d+"))) && !meta.title.isNullOrBlank()) {
                            changed = true; "Episode $epNum - ${meta.title}"
                        } else ep.name

                        val newDate = if (ep.date_upload <= 0 && meta.airDate != null && meta.airDate > 0) {
                            changed = true; meta.airDate
                        } else ep.date_upload

                        if (changed) enrichedCount++
                        // Create a NEW SEpisode so Compose sees a different object reference
                        app.anikuta.source.api.model.SEpisode.create().apply {
                            url = ep.url
                            name = newName
                            episode_number = ep.episode_number
                            date_upload = newDate
                            scanlator = ep.scanlator
                            summary = newSummary
                            preview_url = newPreviewUrl
                            fillermark = ep.fillermark
                        }
                    }
                }

                if (enrichedCount > 0) {
                    Log.i(TAG, "Enriched $enrichedCount/${episodes.size} episodes with in-app metadata")
                    val sourceName = (episodeCache[anilistId]?.second ?: "")
                    episodeCache[anilistId] = Pair(enrichedEpisodes, sourceName)
                    // Update disk cache with enriched episodes
                    episodeCacheStore?.save(anilistId, sourceName, enrichedEpisodes)
                    _episodes.value = EpisodeState.Loaded(enrichedEpisodes, sourceName)
                }
            } catch (e: Exception) {
                Log.w(TAG, "In-app metadata enrichment failed (keeping original data)", e)
            } finally {
                _isEnrichingMetadata.value = false
            }
        }
    }

    /**
     * Resolve the video URL for an episode. Called when the user taps an episode.
     *
     * Phase 7: checks the short-TTL video cache first. On cache hit, shows the
     * picker INSTANTLY with a "Refreshing…" badge and launches a background
     * re-resolve. On cache miss, shows the "Resolving video…" overlay.
     *
     * Videos are grouped by audio version (SUB/DUB/HSUB) using [groupVideosByAudio],
     * with qualities sorted descending within each server section.
     */
    fun playEpisode(episode: SEpisode) {
        // Check if this episode is downloaded — if so, play the local file directly
        // without resolving videos from the source (offline playback).
        val dlSource = matchedSource
        if (dlSource != null) {
            val animeTitle = getAnimeTitle()
            val localUri = downloadManager?.getDownloadedVideoUri(
                episode.name.ifBlank { "Episode ${episode.episode_number}" },
                animeTitle,
                dlSource.name,
            )
            if (localUri != null) {
                Log.d(TAG, "Playing downloaded episode: ${episode.name} → $localUri")
                _playRequest.value = PlayRequest.Play(
                    url = localUri,
                    title = episode.name,
                    episodeNumber = episode.episode_number,
                    anilistId = anilistId,
                    episodeUrl = episode.url,
                    sourceId = dlSource.id,
                )
                return
            }
        }

        // SAFETY NET: If matchedSource is null (e.g. disk cache was loaded but
        // the source lookup is still in progress), try to recover it from the
        // episode cache or persistent preference before giving up.
        if (matchedSource == null) {
            val sourceName = episodeCache[anilistId]?.second
                ?: preferenceStore?.getString("ext_match_$anilistId", "")?.get()
                ?: ""
            if (sourceName.isNotBlank()) {
                Log.d(TAG, "playEpisode: matchedSource null — trying to recover from cache: $sourceName")
                viewModelScope.launch {
                    try {
                        val mgr = uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
                        var retries = 0
                        var source = mgr.getCatalogueSources().find { it.name == sourceName }
                        while (source == null && retries < 20) {
                            kotlinx.coroutines.delay(500)
                            retries++
                            source = mgr.getCatalogueSources().find { it.name == sourceName }
                        }
                        if (source != null) {
                            matchedSource = source
                            val sAnimeUrl = preferenceStore?.getString("ext_sanime_url_$anilistId", "")?.get() ?: ""
                            if (sAnimeUrl.isNotBlank() && matchedSAnime == null) {
                                matchedSAnime = app.anikuta.source.api.model.SAnime.create().apply {
                                    url = sAnimeUrl
                                    title = episode.name
                                }
                            }
                            Log.d(TAG, "playEpisode: source recovered — retrying playEpisode")
                            playEpisode(episode) // retry now that matchedSource is set
                        } else {
                            Log.e(TAG, "playEpisode: source '$sourceName' not found after $retries retries")
                            _playRequest.value = PlayRequest.Error("Source '$sourceName' not found. Try refreshing the page.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "playEpisode: source recovery failed", e)
                        _playRequest.value = PlayRequest.Error("Failed to load source: ${e.message}")
                    }
                }
                return
            }
            _playRequest.value = PlayRequest.Error("No source available")
            return
        }

        val source = matchedSource!!

        // Phase 7: check in-memory video cache (10-min TTL)
        val cached = videoCache[episode.url]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.timestamp < VIDEO_CACHE_TTL_MS) {
            Log.d(TAG, "Video cache hit (in-memory, age=${(now - cached.timestamp) / 1000}s): ${cached.serverSections.size} server sections")
            // Show picker instantly with cached data + refreshing badge
            _videoPicker.value = VideoPickerState.Cached(episode, cached.serverSections, isRefreshing = true)
            // Background re-resolve for smooth update
            backgroundResolveVideos(episode, source)
            return
        }

        // FIX: Disk video cache removed from playback path.
        // The cached videoUrls contain localhost:PORT proxy URLs that are
        // stale after app restart (the proxy server dies with the process).
        // Showing stale URLs in the picker would let the user tap a dead URL.
        // Instead, always re-resolve from source when the in-memory cache
        // (10-min TTL, safe because proxy is alive) misses.
        // The disk cache is still written (for potential future use) but
        // not read for the picker.
        _resolvingEpisode.value = true
        _videoPicker.value = VideoPickerState.Resolving(episode)
        viewModelScope.launch {
            try {
                val audioSections = resolveVideos(episode, source)
                if (audioSections.isEmpty()) {
                    _videoPicker.value = VideoPickerState.Hidden
                    _playRequest.value = PlayRequest.Error("No playable video found for this episode")
                } else if (audioSections.flatMap { it.audioSections }.flatMap { it.videos }.size == 1) {
                    val v = audioSections.first().audioSections.first().videos.first()
                    _videoPicker.value = VideoPickerState.Hidden
                    _playRequest.value = buildPlayRequest(v, episode, source)
                } else {
                    val allVideos = audioSections.flatMap { it.audioSections }.flatMap { it.videos }
                    Log.d(TAG, "${allVideos.size} videos in ${audioSections.size} server section(s) — showing picker")
                    // Cache the result (in-memory + disk for metadata)
                    val nowMs = System.currentTimeMillis()
                    videoCache[episode.url] = CachedVideoData(audioSections, nowMs)
                    _videoPicker.value = VideoPickerState.Show(episode, audioSections)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video resolution failed", e)
                _videoPicker.value = VideoPickerState.Hidden
                _playRequest.value = PlayRequest.Error(e.message ?: "Failed to resolve video")
            } finally {
                _resolvingEpisode.value = false
            }
        }
    }

    /**
     * Phase 7: Background re-resolve for cached videos. Updates the picker
     * smoothly if the new result differs from cached.
     *
     * FIX: Previously this always set _videoPicker.value = VideoPickerState.Show(...)
     * even when the data was identical to the cached data. This caused the bottom
     * sheet to re-animate (close and reopen). Now compares the new results with
     * the cached data:
     * - If same: silently updates the cache timestamp, does NOT change picker state
     * - If different: updates the picker state (the sheet updates without re-animating)
     */
    private fun backgroundResolveVideos(episode: SEpisode, source: AnimeCatalogueSource) {
        viewModelScope.launch {
            try {
                val newSections = resolveVideos(episode, source)
                val now = System.currentTimeMillis()
                val oldCached = videoCache[episode.url]

                // Compare new results with cached data
                val isSame = oldCached != null && compareServerSections(oldCached.serverSections, newSections)

                // Update cache (in-memory + disk)
                videoCache[episode.url] = CachedVideoData(newSections, now)

                if (isSame) {
                    // Data unchanged — remove the refreshing badge by transitioning
                    // from Cached → Show (which doesn't have the refreshing indicator).
                    // Previously this did nothing, causing the refreshing animation to
                    // spin forever.
                    val currentPicker = _videoPicker.value
                    if (currentPicker is VideoPickerState.Cached) {
                        _videoPicker.value = VideoPickerState.Show(episode, newSections)
                        Log.d(TAG, "Background re-resolve: data unchanged, removing refreshing badge")
                    } else {
                        Log.d(TAG, "Background re-resolve: data unchanged, picker not in Cached state")
                    }
                } else {
                    // Data changed — update picker state smoothly
                    // Only update if the picker is currently showing (don't reopen a dismissed picker)
                    val currentPicker = _videoPicker.value
                    if (currentPicker is VideoPickerState.Show || currentPicker is VideoPickerState.Cached) {
                        _videoPicker.value = VideoPickerState.Show(episode, newSections)
                        Log.d(TAG, "Background re-resolve: data changed, updating picker smoothly")
                    } else {
                        Log.d(TAG, "Background re-resolve: data changed but picker not visible, skipping UI update")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background re-resolve failed (keeping cached data)", e)
                // Keep showing the cached data — just remove the refreshing badge
                val cached = videoCache[episode.url]
                if (cached != null) {
                    val currentPicker = _videoPicker.value
                    if (currentPicker is VideoPickerState.Cached) {
                        _videoPicker.value = VideoPickerState.Show(episode, cached.serverSections)
                    }
                }
            }
        }
    }

    /**
     * Compare two ServerSection lists for equality (same servers, audio versions,
     * and video titles + qualities). Used by backgroundResolveVideos to avoid
     * unnecessary UI updates.
     *
     * FIX: Previously compared videoUrl which contains localhost:PORT that changes
     * every resolution. Now compares by videoTitle + resolution (which are stable)
     * so the picker doesn't re-animate when the data is effectively the same.
     */
    private fun compareServerSections(a: List<ServerSection>, b: List<ServerSection>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            val sa = a[i]
            val sb = b[i]
            if (sa.serverName != sb.serverName) return false
            if (sa.audioSections.size != sb.audioSections.size) return false
            for (j in sa.audioSections.indices) {
                val aa = sa.audioSections[j]
                val ab = sb.audioSections[j]
                if (aa.audio != ab.audio) return false
                if (aa.videos.size != ab.videos.size) return false
                for (k in aa.videos.indices) {
                    // Compare by videoTitle + resolution (stable) instead of
                    // videoUrl (contains localhost:PORT which changes each time)
                    if (aa.videos[k].videoTitle != ab.videos[k].videoTitle) return false
                    if (aa.videos[k].resolution != ab.videos[k].resolution) return false
                }
            }
        }
        return true
    }

    /**
     * Fetch videos from the source (getHosterList → getVideoList fallback)
     * and group them by audio version using [groupVideosByAudio].
     */
    private suspend fun resolveVideos(episode: SEpisode, source: AnimeCatalogueSource): List<ServerSection> {
        // Try getHosterList first, fall back to getVideoList on ANY exception.
        val allVideos = try {
            val hosters = withContext(Dispatchers.IO) { source.getHosterList(episode) }
            hosters.mapNotNull { hoster ->
                hoster.videoList?.filter { it.videoUrl.isNotBlank() }
            }.flatten()
        } catch (e: Exception) {
            Log.d(TAG, "getHosterList failed (${e.javaClass.simpleName}: ${e.message}), falling back to getVideoList")
            val flat = withContext(Dispatchers.IO) { source.getVideoList(episode) }
            flat.filter { it.videoUrl.isNotBlank() }
        }
        val sections = groupVideosByServer(allVideos)

        // Phase B — detect sub/dub from the AudioVersion and cache it.
        // This runs after every video resolution so the library card badges
        // stay up-to-date as the user browses episodes.
        try {
            val audioVersions = sections
                .flatMap { it.audioSections }
                .map { it.audio }
                .toSet()
            val hasSub = audioVersions.any { it == AudioVersion.SUB || it == AudioVersion.HSUB }
            val hasDub = audioVersions.any { it == AudioVersion.DUB }
            val subCount = sections.flatMap { it.audioSections }
                .filter { it.audio == AudioVersion.SUB || it.audio == AudioVersion.HSUB }
                .sumOf { it.videos.size }
            val dubCount = sections.flatMap { it.audioSections }
                .filter { it.audio == AudioVersion.DUB }
                .sumOf { it.videos.size }
            subDubStore?.update(
                anilistId = anilistId,
                hasSub = hasSub,
                hasDub = hasDub,
                subCount = subCount,
                dubCount = dubCount,
                totalEpisodes = allVideos.size,
            )
            Log.d(TAG, "SubDub cached: hasSub=$hasSub hasDub=$hasDub subCount=$subCount dubCount=$dubCount")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache sub/dub info: ${e.message}")
        }

        return sections
    }

    /** User picked a specific video from the quality picker → launch the player. */
    fun playSpecificVideo(video: Video, episode: SEpisode) {
        Log.d(TAG, "User selected: ${video.videoTitle} → ${video.videoUrl}")
        _videoPicker.value = VideoPickerState.Hidden
        val source = matchedSource
        if (source == null) {
            _playRequest.value = PlayRequest.Error("No source available")
            return
        }
        _playRequest.value = buildPlayRequest(video, episode, source)
    }

    /**
     * Build a [PlayRequest.Play] from a [Video] + [SEpisode], including the
     * source ID and parsed video metadata (server, audio, quality) so the
     * player can re-resolve videos when switching episodes.
     */
    private fun buildPlayRequest(
        video: Video,
        episode: SEpisode,
        source: AnimeCatalogueSource,
    ): PlayRequest.Play {
        val parsed = VideoTitleParser.parse(video)
        Log.d(TAG, "Building PlayRequest: source=${source.name} (id=${source.id}) server='${parsed.server}' audio='${parsed.audio}' quality=${parsed.quality}")
        return PlayRequest.Play(
            url = video.videoUrl,
            title = episode.name,
            episodeNumber = episode.episode_number,
            anilistId = anilistId,
            episodeUrl = episode.url,
            videoHeaders = buildHeaders(video),
            sourceId = source.id,
            videoServer = parsed.server,
            videoAudio = parsed.audio.name,
            videoQuality = parsed.quality ?: -1,
        )
    }

    /**
     * Build HTTP headers string for MPV's http-header-fields option.
     * Combines the Video's headers with the source's default headers.
     * Format: "Key: Value,Key2: Value2" (commas escaped as \,).
     * Matches aniyomi's setHttpOptions().
     */
    private fun buildHeaders(video: Video): String {
        val headers = video.headers
        return if (headers != null && headers.size > 0) {
            headers.toMultimap()
                .mapValues { it.value.firstOrNull() ?: "" }
                .map { "${it.key}: ${it.value.replace(",", "\\,")}" }
                .joinToString(",")
        } else {
            // Default User-Agent so servers don't block us with 403
            "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
        }
    }

    /** Dismiss the quality picker without playing. */
    fun dismissVideoPicker() {
        _videoPicker.value = VideoPickerState.Hidden
    }

    /** Clear the play request after the UI has consumed it (launched the player). */
    fun consumePlayRequest() {
        _playRequest.value = null
    }

    // ---- Download support ----

    private val downloadManager: app.anikuta.download.DownloadManager? = try {
        uy.kohesive.injekt.Injekt.get()
    } catch (e: Exception) { null }

    /**
     * Download status per episode URL. Observed by the UI to update download button icons.
     * Reactive — re-emits whenever any download's statusFlow changes (fixes B3).
     * Keyed by episodeUrl (stable) instead of episodeName (mutable) — fixes H4.
     */
    val downloadStatus: kotlinx.coroutines.flow.StateFlow<Map<String, app.anikuta.download.Download.State>> =
        downloadManager?.downloadStatusMap
            ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())

    /**
     * Download progress per episode URL (0-100). Observed by the UI to show
     * determinate progress on the download button (fixes C5).
     * Keyed by episodeUrl (stable) — fixes H4.
     */
    val downloadProgress: kotlinx.coroutines.flow.StateFlow<Map<String, Int>> =
        downloadManager?.downloadProgressMap
            ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())

    /**
     * Set of episode URLs that are downloaded on disk (filesystem state).
     *
     * This is SEPARATE from the download queue. An episode can be on disk but
     * not in the queue (e.g. after the user removes a completed download from
     * the downloads page, or after app reinstall). The detail page uses this
     * to show the green checkmark for episodes that exist on disk, regardless
     * of queue state.
     *
     * Keyed by episodeUrl (stable) — survives metadata enrichment name changes.
     * Populated by scanning the current anime's directory via [refreshDownloadedOnDisk].
     */
    private val _downloadedOnDisk = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val downloadedOnDisk: kotlinx.coroutines.flow.StateFlow<Set<String>> = _downloadedOnDisk

    /**
     * Scan the filesystem for downloaded episodes of the current anime.
     * Returns a set of episode URLs (stable identifiers).
     *
     * Works even if [matchedSource] is not yet set — recovers the source name
     * from the in-memory or disk cache (same pattern as playEpisode).
     * Called when anime details load, when a download completes, and when
     * the detail page is entered.
     */
    fun refreshDownloadedOnDisk() {
        val title = getAnimeTitle().ifBlank { return }
        val provider = try { uy.kohesive.injekt.Injekt.get<app.anikuta.download.DownloadProvider>() } catch (e: Exception) { return }

        // Recover source name if matchedSource isn't set yet (in-memory cache path)
        val sourceName = matchedSource?.name
            ?: episodeCache[anilistId]?.second
            ?: preferenceStore?.getString("ext_match_$anilistId", "")?.get()
            ?: return

        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val downloadedMap = provider.listDownloadedEpisodesWithUrls(title, sourceName)
            _downloadedOnDisk.value = downloadedMap.keys
            Log.d(TAG, "refreshDownloadedOnDisk: ${downloadedMap.size} episodes on disk for $title (source=$sourceName)")
        }
    }

    /**
     * Observe download status changes and refresh the on-disk set when a download
     * completes. This ensures the green checkmark appears immediately when a
     * download finishes, and stays even after the download is removed from the queue.
     */
    init {
        viewModelScope.launch {
            downloadStatus.collect { statusMap ->
                if (statusMap.values.any { it == app.anikuta.download.Download.State.DOWNLOADED }) {
                    refreshDownloadedOnDisk()
                }
            }
        }
    }

    /**
     * Handle a download button click with state-aware behavior (fixes H1, H2, H3).
     *
     * Behavior per state:
     * - DOWNLOADING / QUEUE / RESOLVING / MUXING → do nothing (already in progress)
     * - PAUSED → resume the download
     * - ERROR → retry the download
     * - DOWNLOADED (queue or disk) → play the downloaded episode
     * - Not downloaded → enqueue a new download
     *
     * @param episode the episode whose download button was tapped
     */
    fun onDownloadButtonClick(episode: app.anikuta.source.api.model.SEpisode) {
        val status = downloadStatus.value[episode.url]
        val isOnDisk = downloadedOnDisk.value.contains(episode.url)

        when {
            // Already in progress — do nothing
            status == app.anikuta.download.Download.State.DOWNLOADING ||
            status == app.anikuta.download.Download.State.QUEUE ||
            status == app.anikuta.download.Download.State.RESOLVING ||
            status == app.anikuta.download.Download.State.MUXING ||
            status == app.anikuta.download.Download.State.RECONNECTING -> {
                Log.d(TAG, "onDownloadButtonClick: ${episode.name} already in progress (status=$status)")
            }

            // Paused → resume
            status == app.anikuta.download.Download.State.PAUSED -> {
                Log.d(TAG, "onDownloadButtonClick: ${episode.name} resuming")
                val dl = downloadManager?.queue?.value?.find { it.episodeUrl == episode.url }
                if (dl != null) downloadManager?.resumeDownload(dl.id)
            }

            // Error → retry
            status == app.anikuta.download.Download.State.ERROR -> {
                Log.d(TAG, "onDownloadButtonClick: ${episode.name} retrying")
                val dl = downloadManager?.queue?.value?.find { it.episodeUrl == episode.url }
                if (dl != null) downloadManager?.retryDownload(dl.id)
            }

            // Downloaded (queue or disk) → no action on single tap.
            // User must long-press to see options (Play / Delete).
            status == app.anikuta.download.Download.State.DOWNLOADED || isOnDisk -> {
                Log.d(TAG, "onDownloadButtonClick: ${episode.name} already downloaded — no action (use long-press for options)")
            }

            // Not downloaded → enqueue
            else -> {
                Log.d(TAG, "onDownloadButtonClick: ${episode.name} enqueuing download")
                downloadEpisode(episode)
            }
        }
    }

    /**
     * Delete a downloaded episode's file from disk (long-press menu action).
     * Also removes from queue if present.
     */
    fun deleteDownloadedEpisode(episode: app.anikuta.source.api.model.SEpisode) {
        val source = matchedSource ?: return
        val title = getAnimeTitle()
        Log.d(TAG, "deleteDownloadedEpisode: ${episode.name}")
        // Find in queue and cancel (which deletes everything)
        val dl = downloadManager?.queue?.value?.find { it.episodeUrl == episode.url }
        if (dl != null) {
            downloadManager?.cancelDownload(dl.id)
        } else {
            // Not in queue — delete the file directly
            downloadManager?.deleteDownloadedEpisode(
                episode.name.ifBlank { "Episode ${episode.episode_number}" },
                title,
                source.name,
            )
        }
        refreshDownloadedOnDisk()
    }

    /**
     * Remove a download from the queue but keep the file (long-press menu action).
     * Only works for completed downloads — for incomplete, use [cancelDownloadForEpisode].
     */
    fun removeDownloadFromQueue(episode: app.anikuta.source.api.model.SEpisode) {
        val dl = downloadManager?.queue?.value?.find { it.episodeUrl == episode.url } ?: return
        Log.d(TAG, "removeDownloadFromQueue: ${episode.name}")
        downloadManager?.removeDownload(dl.id)
        refreshDownloadedOnDisk()
    }

    /**
     * Cancel a download and delete all files (long-press menu action).
     */
    fun cancelDownloadForEpisode(episode: app.anikuta.source.api.model.SEpisode) {
        val dl = downloadManager?.queue?.value?.find { it.episodeUrl == episode.url } ?: return
        Log.d(TAG, "cancelDownloadForEpisode: ${episode.name}")
        downloadManager?.cancelDownload(dl.id)
        refreshDownloadedOnDisk()
    }

    /**
     * Enqueue a single episode for download. Resolves the source + anime title
     * from the current state. Recovers matchedSource from cache if needed.
     */
    fun downloadEpisode(episode: app.anikuta.source.api.model.SEpisode) {
        // Try to recover matchedSource from cache if not set (fixes: download button
        // not working after navigating away and back without refreshing)
        if (matchedSource == null) {
            val cachedSourceName = episodeCache[anilistId]?.second
                ?: preferenceStore?.getString("ext_match_$anilistId", "")?.get()
            if (cachedSourceName != null && cachedSourceName.isNotBlank()) {
                val mgr = try { uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>() } catch (e: Exception) { null }
                matchedSource = mgr?.getCatalogueSources()?.find { it.name == cachedSourceName }
                Log.d(TAG, "downloadEpisode: recovered matchedSource from cache: ${matchedSource?.name}")
            }
        }

        val source = matchedSource ?: run {
            Log.w(TAG, "downloadEpisode: matchedSource is null — cannot enqueue")
            return
        }
        val title = getAnimeTitle()
        downloadManager?.enqueueDownload(
            anilistId = anilistId,
            sourceId = source.id,
            sourceName = source.name,
            animeTitle = title,
            episode = episode,
        )
        Log.d(TAG, "Download enqueued: ${episode.name}")
    }

    /** Download all episodes in the list. */
    fun downloadAllEpisodes(episodes: List<app.anikuta.source.api.model.SEpisode>) {
        val source = matchedSource ?: return
        val title = getAnimeTitle()
        downloadManager?.enqueueDownloads(
            anilistId = anilistId,
            sourceId = source.id,
            sourceName = source.name,
            animeTitle = title,
            episodes = episodes,
        )
        Log.d(TAG, "Download all enqueued: ${episodes.size} episodes")
    }

    /** Check if an episode is downloaded. */
    fun isEpisodeDownloaded(episodeName: String): Boolean {
        val source = matchedSource ?: return false
        val title = getAnimeTitle().ifBlank { return false }
        return downloadManager?.isEpisodeDownloaded(episodeName, title, source.name) ?: false
    }

    /** Get the anime title from the current detail state. */
    private fun getAnimeTitle(): String {
        return (_anime.value as? DetailState.Success)?.anime?.title?.preferred() ?: "Unknown Anime"
    }

    /**
     * Toggle the saved state for this anime.
     *
     * Phase 5 task 5.6: persists to [LibraryStore] (PreferenceStore-backed
     * map of AniList ID → AniListAnime JSON). When saving, we need the full
     * AniListAnime object — fetched from the current [DetailState.Success].
     * If details haven't loaded yet, the toggle is a no-op (the UI button
     * is only visible after details load, so this shouldn't happen in
     * practice, but we guard anyway).
     */
    fun toggleSaved() {
        val store = libraryStore ?: run {
            Log.w(TAG, "LibraryStore unavailable — toggle is no-op")
            return
        }
        val anime = (_anime.value as? DetailState.Success)?.anime ?: run {
            Log.w(TAG, "Anime not loaded yet — cannot save")
            return
        }
        val nowSaved = !_isSaved.value
        if (nowSaved) {
            store.save(anime)
            // Phase N: Start release tracking when added to library
            try {
                val tracker = uy.kohesive.injekt.Injekt.get<app.anikuta.notification.ReleaseTracker>()
                tracker.startTracking(
                    anilistId = anilistId,
                    title = anime.title.preferred(),
                    coverUrl = anime.coverImage?.large,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not start release tracking: ${e.message}")
            }
        } else {
            store.remove(anilistId)
            // Phase N: Stop release tracking when removed from library
            try {
                val tracker = uy.kohesive.injekt.Injekt.get<app.anikuta.notification.ReleaseTracker>()
                tracker.stopTracking(anilistId)
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop release tracking: ${e.message}")
            }
        }
        _isSaved.value = nowSaved
        Log.d(TAG, "Save toggled: $nowSaved (anilistId=$anilistId)")
    }

    /**
     * Phase 7: Background soft-refresh of the episode list.
     * Guarded by [REFRESH_GUARD_MS] (5 min) per anime to avoid hammering.
     * If the refreshed list differs from cached, updates the UI smoothly.
     */
    private fun backgroundRefreshEpisodes(anime: AniListAnime, sourceName: String) {
        val lastRefresh = preferenceStore?.getLong("ext_last_refresh_$anilistId", 0L)?.get() ?: 0L
        val now = System.currentTimeMillis()
        if (now - lastRefresh < REFRESH_GUARD_MS) {
            Log.d(TAG, "Background refresh skipped (within ${REFRESH_GUARD_MS}ms guard)")
            return
        }
        viewModelScope.launch {
            try {
                val mgr = uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
                val source = mgr.getCatalogueSources().find { it.name == sourceName } ?: return@launch
                val sAnime = matchedSAnime ?: app.anikuta.source.api.model.SAnime.create().apply {
                    url = preferenceStore?.getString("ext_sanime_url_$anilistId", "")?.get() ?: ""
                    title = anime.title.preferred()
                }
                if (sAnime.url.isBlank()) return@launch
                Log.d(TAG, "Background refresh: fetching episodes from $sourceName...")
                val freshEps = withContext(Dispatchers.IO) { source.getEpisodeList(sAnime) }

                // Preserve enriched metadata from the cached episodes
                // (background refresh gets fresh episodes from the extension
                // which don't have the in-app metadata we enriched earlier)
                val cachedEps = episodeCache[anilistId]?.first
                if (cachedEps != null) {
                    freshEps.forEach { fresh ->
                        val cached = cachedEps.find { it.url == fresh.url }
                        if (cached != null) {
                            // Only preserve fields that the extension doesn't provide
                            if (fresh.preview_url.isNullOrBlank() && !cached.preview_url.isNullOrBlank()) {
                                fresh.preview_url = cached.preview_url
                            }
                            if (fresh.summary.isNullOrBlank() && !cached.summary.isNullOrBlank()) {
                                fresh.summary = cached.summary
                            }
                            // Preserve enriched name if the fresh one is generic
                            if (fresh.name.matches(Regex("(?i)episode\\s*\\d+")) && !cached.name.matches(Regex("(?i)episode\\s*\\d+"))) {
                                fresh.name = cached.name
                            }
                            if (fresh.date_upload <= 0 && cached.date_upload > 0) {
                                fresh.date_upload = cached.date_upload
                            }
                        }
                    }
                }

                // Update cache + UI
                episodeCache[anilistId] = Pair(freshEps, sourceName)
                episodeCacheStore?.save(anilistId, sourceName, freshEps)
                matchedSource = source
                matchedSAnime = sAnime
                _episodes.value = EpisodeState.Loaded(freshEps, sourceName)
                preferenceStore?.getLong("ext_last_refresh_$anilistId", 0L)?.set(now)
                Log.d(TAG, "Background refresh complete: ${freshEps.size} episodes (metadata preserved)")
            } catch (e: Exception) {
                Log.w(TAG, "Background refresh failed (keeping cached data)", e)
            }
        }
    }

    // ---- Phase 7: 3-stage pull-to-refresh methods ----

    /** Stage 1: Refresh episodes only (re-fetch from matched source). */
    fun refreshEpisodesOnly() {
        val anime = (_anime.value as? DetailState.Success)?.anime ?: return
        val sourceName = (episodeCache[anilistId]?.second) ?: return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val mgr = uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
                val source = mgr.getCatalogueSources().find { it.name == sourceName }
                val sAnime = matchedSAnime ?: return@launch
                if (source != null) {
                    val eps = withContext(Dispatchers.IO) { source.getEpisodeList(sAnime) }
                    episodeCache[anilistId] = Pair(eps, sourceName)
                    matchedSource = source
                    _episodes.value = EpisodeState.Loaded(eps, sourceName)
                    preferenceStore?.getLong("ext_last_refresh_$anilistId", 0L)?.set(System.currentTimeMillis())
                    Log.d(TAG, "Refresh episodes: ${eps.size} episodes")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Refresh episodes failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Stage 2: Refresh details only (re-fetch AniList metadata, bypass cache). */
    fun refreshDetailsOnly() {
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val repo = anilistRepo ?: return@launch
                val data = withContext(Dispatchers.IO) { repo.getAnimeDetails(anilistId) }
                if (data != null) {
                    _anime.value = DetailState.Success(data)
                    Log.d(TAG, "Refresh details: ${data.title.preferred()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Refresh details failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Stage 3: Refresh everything (episodes + details + source re-match). */
    fun refreshEverything() {
        _isRefreshing.value = true
        // Clear caches to force full re-fetch
        episodeCache.remove(anilistId)
        episodeCacheStore?.clear(anilistId)
        preferenceStore?.getString("ext_match_$anilistId", "")?.set("")
        preferenceStore?.getString("ext_sanime_url_$anilistId", "")?.set("")
        viewModelScope.launch {
            try {
                val repo = anilistRepo ?: return@launch
                val data = withContext(Dispatchers.IO) { repo.getAnimeDetails(anilistId) }
                if (data != null) {
                    _anime.value = DetailState.Success(data)
                    findEpisodeSource(data)
                    Log.d(TAG, "Refresh everything: re-fetching details + episodes")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Refresh everything failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Called by the 3-stage pull-to-refresh when the user releases. */
    fun onRefreshStage(stage: RefreshStage) {
        Log.i(TAG, "onRefreshStage: $stage — initiating refresh")
        when (stage) {
            RefreshStage.Episodes -> refreshEpisodesOnly()
            RefreshStage.Details -> refreshDetailsOnly()
            RefreshStage.Everything -> refreshEverything()
            RefreshStage.Idle -> {}
        }
    }
}

sealed class DetailState {
    data object Loading : DetailState()
    data class Success(val anime: AniListAnime) : DetailState()
    data class Error(val message: String) : DetailState()
}

sealed class EpisodeState {
    /** Initial state — nothing searched yet. */
    data object Idle : EpisodeState()
    /** Searching extension sources for a match. */
    data object Searching : EpisodeState()
    /** Found a match, fetching the episode list. */
    data object LoadingEpisodes : EpisodeState()
    /** No extension source had this anime. */
    data class NoMatch(val searchedTitle: String) : EpisodeState()
    /** Anime hasn't aired yet. */
    data class NotAired(val title: String, val seasonYear: Int?, val season: String?) : EpisodeState()
    /** Episodes loaded successfully. */
    data class Loaded(val episodeList: List<SEpisode>, val sourceName: String) : EpisodeState()
    /** Something went wrong. */
    data class Error(val message: String) : EpisodeState()
}

sealed class PlayRequest {
    data class Play(
        val url: String,
        val title: String,
        val episodeNumber: Float,
        val anilistId: Int = -1,
        val episodeUrl: String = "",
        val videoHeaders: String = "",
        // Episode switching metadata — allows the player to re-resolve videos
        // for a different episode using the same source + server/audio/quality.
        val sourceId: Long = -1L,
        val videoServer: String = "",
        val videoAudio: String = "",
        val videoQuality: Int = -1,
    ) : PlayRequest()
    data class Error(val message: String) : PlayRequest()
}

/**
 * Video quality picker state. Phase 7 adds [Resolving] and [Cached] for
 * the short-TTL video cache + smooth soft-refresh.
 */
sealed class VideoPickerState {
    data object Hidden : VideoPickerState()
    data class Resolving(val episode: SEpisode) : VideoPickerState()
    data class Cached(val episode: SEpisode, val serverSections: List<ServerSection>, val isRefreshing: Boolean) : VideoPickerState()
    data class Show(val episode: SEpisode, val serverSections: List<ServerSection>) : VideoPickerState()
}

/**
 * A group of videos from the same server/hoster. Each group shows as a
 * section in the quality picker bottom sheet with the server name as a
 * header and the available qualities listed below.
 */
data class ServerGroup(
    val serverName: String,
    val videos: List<Video>,
)

/**
 * Phase 7 — 3-stage pull-to-refresh stages.
 * The user pulls down with increasing distance to unlock higher stages.
 */
enum class RefreshStage(val label: String) {
    Idle(""),
    Episodes("Release to refresh episodes"),
    Details("Release to refresh details"),
    Everything("Release to refresh everything (details + episodes + cover)"),
}
