package app.confused.anikuta.feature.animedetails

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.model.AniListAnime
import app.confused.anikuta.core.anilist.model.displayTitle
import app.confused.anikuta.data.extension.AnimeExtensionManager
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The ViewModel for [AnimeDetailScreen].
 *
 * Orchestrates the three-stage load (per the anime-details design spec §6.1):
 * 1. **AniList** — fetch anime metadata (title, cover, description, score, etc.).
 * 2. **Extension source match** — search trusted sources by title, find the
 *   best-matching source + SAnime.
 * 3. **Episode list** — call `source.getEpisodeList(sAnime)` on the matched
 *   source to get real episodes.
 *
 * Also handles:
 * - **Source switching** — the user can pick a different source from all
 *   matches. The selection is persisted per-anime in SharedPreferences.
 * - **Watched state** — in-memory `Set<String>` keyed by episode URL. Toggle
 *   on tap. (Will be persisted to a store in a later phase.)
 * - **Toast notifications** — surfaces errors to the user via Toast (per the
 *   Step 5 prompt: "No sources found", "Failed to load episodes", etc.).
 *
 * @param anilistId the AniList anime ID (from the browse/home screen tap).
 * @param api the AniList API client.
 * @param extensionManager provides the live list of installed + trusted sources.
 * @param sourceMatcher searches sources by title.
 * @param appContext for SharedPreferences + Toast (application-scoped).
 */
class AnimeDetailViewModel(
    private val anilistId: Int,
    private val api: AniListApi,
    private val extensionManager: AnimeExtensionManager,
    private val sourceMatcher: SourceMatcher,
    private val appContext: Context,
) : ViewModel() {

    // ── State ──

    private val _animeState = MutableStateFlow<DetailState>(DetailState.Loading)
    val animeState: StateFlow<DetailState> = _animeState.asStateFlow()

    private val _episodeState = MutableStateFlow<EpisodeState>(EpisodeState.Idle)
    val episodeState: StateFlow<EpisodeState> = _episodeState.asStateFlow()

    /** All sources that matched the anime (for the source switcher). */
    private val _allMatches = MutableStateFlow<List<SourceMatcher.SourceMatch>>(emptyList())
    val allMatches: StateFlow<List<SourceMatcher.SourceMatch>> = _allMatches.asStateFlow()

    /** The currently-selected source match (drives episode loading). */
    private val _currentMatch = MutableStateFlow<SourceMatcher.SourceMatch?>(null)
    val currentMatch: StateFlow<SourceMatcher.SourceMatch?> = _currentMatch.asStateFlow()

    /** In-memory watched set (keyed by episode URL). Phase 5 = no persistence. */
    private val _watchedEpisodes = MutableStateFlow<Set<String>>(emptySet())
    val watchedEpisodes: StateFlow<Set<String>> = _watchedEpisodes.asStateFlow()

    private val sourcePrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        loadAnimeDetails()
    }

    // ── Public API ──

    /**
     * Toggles the watched state of an episode (by URL). In-memory only for
     * Phase 5 — will be persisted to `EpisodeSeenStore` in a later phase.
     */
    fun toggleWatched(episodeUrl: String) {
        _watchedEpisodes.value = _watchedEpisodes.value.toMutableSet().apply {
            if (contains(episodeUrl)) remove(episodeUrl) else add(episodeUrl)
        }
    }

    /**
     * Switches to a different source and reloads episodes.
     * Persists the selection per-anime in SharedPreferences.
     */
    fun switchSource(match: SourceMatcher.SourceMatch) {
        _currentMatch.value = match
        sourcePrefs.edit().putLong(sourcePrefKey(anilistId), match.source.id).apply()
        Toast.makeText(appContext, "Switched to ${match.source.name}", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Switched source to '${match.source.name}' for anime $anilistId")
        loadEpisodes(match)
    }

    /**
     * Refreshes everything: AniList data + source matching + episodes.
     * Called when the user pulls to refresh.
     */
    fun refresh() {
        Log.i(TAG, "Refreshing anime $anilistId")
        loadAnimeDetails()
    }

    /**
     * Manually searches all sources with a custom query.
     * Returns all results so the user can pick the right one.
     */
    suspend fun manualSearch(query: String): List<ManualSearchResult> {
        Log.i(TAG, "Manual search: '$query'")
        return sourceMatcher.searchAllSources(query)
    }

    /**
     * Links a specific source + SAnime to this anime (manual selection).
     * Persists the source preference and loads episodes.
     */
    fun linkManual(source: eu.kanade.tachiyomi.animesource.AnimeCatalogueSource, sAnime: eu.kanade.tachiyomi.animesource.model.SAnime) {
        val match = SourceMatcher.SourceMatch(source, sAnime, 1.0)
        _currentMatch.value = match
        _allMatches.value = listOf(match)
        sourcePrefs.edit().putLong(sourcePrefKey(anilistId), source.id).apply()
        Toast.makeText(appContext, "Linked to ${source.name}", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Manual link: '${sAnime.title}' from '${source.name}'")
        loadEpisodes(match)
    }

    // ── Internal: Stage 1 — Load AniList data ──

    private fun loadAnimeDetails() {
        viewModelScope.launch {
            _animeState.value = DetailState.Loading
            try {
                val anime = api.fetchById(anilistId)
                if (anime != null) {
                    _animeState.value = DetailState.Success(anime)
                    findAndLoadEpisodes(anime)
                } else {
                    _animeState.value = DetailState.Error("Anime not found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load anime $anilistId", e)
                _animeState.value = DetailState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ── Internal: Stage 2 — Find matching source ──

    private fun findAndLoadEpisodes(anime: AniListAnime) {
        viewModelScope.launch {
            _episodeState.value = EpisodeState.Searching
            val title = anime.displayTitle
            Log.i(TAG, "Searching sources for '$title' (anilistId=$anilistId)")

            try {
                // Search all sources (for the switcher) + get all matches.
                val all = sourceMatcher.matchAll(title)
                _allMatches.value = all

                if (all.isEmpty()) {
                    _episodeState.value = EpisodeState.NoMatch
                    Toast.makeText(appContext, "No sources found for this anime", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Check if the user has a persisted source preference for this anime.
                val preferredSourceId = sourcePrefs.getLong(sourcePrefKey(anilistId), -1L)
                val selected = all.firstOrNull { it.source.id == preferredSourceId } ?: all.first()

                _currentMatch.value = selected
                Log.i(TAG, "Selected source: '${selected.source.name}' (score=${selected.score})")
                loadEpisodes(selected)
            } catch (e: Exception) {
                Log.e(TAG, "Source matching failed for '$title'", e)
                _episodeState.value = EpisodeState.Error("Search failed: ${e.message}")
                Toast.makeText(appContext, "Failed to search sources: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Internal: Stage 3 — Load episodes from the matched source ──

    private fun loadEpisodes(match: SourceMatcher.SourceMatch) {
        viewModelScope.launch {
            _episodeState.value = EpisodeState.Loading(match.source.name)
            try {
                val episodes = withContext(Dispatchers.IO) {
                    match.source.getEpisodeList(match.sAnime)
                }
                if (episodes.isEmpty()) {
                    _episodeState.value = EpisodeState.NoMatch
                } else {
                    _episodeState.value = EpisodeState.Loaded(episodes, match.source.name)
                    Log.i(TAG, "Loaded ${episodes.size} episodes from '${match.source.name}'")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load episodes from '${match.source.name}'", e)
                val msg = "Failed to load episodes: ${e.message}"
                _episodeState.value = EpisodeState.Error(msg)
                Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sourcePrefKey(anilistId: Int) = "source_pref_$anilistId"

    companion object {
        private const val TAG = "AnikutaDetailVM"
        private const val PREFS_NAME = "anikuta_source_prefs"
    }
}

// ── State types ──

sealed interface DetailState {
    data object Loading : DetailState
    data class Success(val anime: AniListAnime) : DetailState
    data class Error(val message: String) : DetailState
}

sealed interface EpisodeState {
    data object Idle : EpisodeState
    data object Searching : EpisodeState
    data class Loading(val sourceName: String) : EpisodeState
    data class Loaded(val episodes: List<SEpisode>, val sourceName: String) : EpisodeState
    data object NoMatch : EpisodeState
    data class Error(val message: String) : EpisodeState
}
