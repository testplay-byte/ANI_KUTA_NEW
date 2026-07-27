package app.confused.anikuta.feature.animedetails

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.Category
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.common.repository.CategoryRepository
import app.confused.anikuta.core.common.repository.EpisodeRepository
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the extension-only details page.
 *
 * Used when the user taps "Go without linking" on the search page — the anime
 * is not on AniList, so we show a details page using only the extension's
 * SAnime data + the source's episode list.
 *
 * Per user: "the extension provides quite a lot of details too, like the title,
 * the cover, the genres, the synopsis, and the details and information of the
 * anime too."
 *
 * Differences from [AnimeDetailViewModel]:
 * - No AniList ID (anilistId = null)
 * - No source search (the source is already known)
 * - No metadata enrichment (episode metadata requires AniList ID)
 * - Saves to library with sourceId + url (not anilistId)
 */
class ExtensionDetailViewModel(
    private val source: AnimeCatalogueSource,
    private val sAnime: SAnime,
    private val animeRepository: AnimeRepository,
    private val categoryRepository: CategoryRepository,
    private val episodeRepository: EpisodeRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _animeState = MutableStateFlow<ExtensionDetailState>(ExtensionDetailState.Loading)
    val animeState: StateFlow<ExtensionDetailState> = _animeState.asStateFlow()

    private val _episodeState = MutableStateFlow<EpisodeState>(EpisodeState.Idle)
    val episodeState: StateFlow<EpisodeState> = _episodeState.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _currentMatch = MutableStateFlow<SourceMatcher.SourceMatch?>(null)
    val currentMatch: StateFlow<SourceMatcher.SourceMatch?> = _currentMatch.asStateFlow()

    private val _watchedEpisodes = MutableStateFlow<Set<String>>(emptySet())
    val watchedEpisodes: StateFlow<Set<String>> = _watchedEpisodes.asStateFlow()

    init {
        loadExtensionAnime()
        // Check if this anime is already in the library (by sourceId + url)
        viewModelScope.launch {
            val existing = animeRepository.getBySourceAndUrl(source.id, sAnime.url)
            _isSaved.value = existing?.favorite == true
            if (existing != null) {
                // Load episodes from DB first
                val dbEpisodes = episodeRepository.getByAnimeId(existing.id)
                if (dbEpisodes.isNotEmpty()) {
                    val sEpisodes = dbEpisodes.map { it.toExtensionSEpisode() }
                    _episodeState.value = EpisodeState.Loaded(sEpisodes, source.name)
                    Log.i(TAG, "Loaded ${dbEpisodes.size} episodes from DB for extension anime ${sAnime.title}")
                }
            }
        }
    }

    private fun loadExtensionAnime() {
        viewModelScope.launch {
            _animeState.value = ExtensionDetailState.Success(
                ExtensionAnime(
                    title = sAnime.title,
                    description = sAnime.description,
                    genre = sAnime.genre?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
                    coverUrl = sAnime.thumbnail_url,
                    backgroundUrl = sAnime.background_url,
                    status = sAnime.status,
                    sourceName = source.name,
                    url = sAnime.url,
                    sourceId = source.id,
                ),
            )
            _currentMatch.value = SourceMatcher.SourceMatch(source, sAnime, 1.0)

            // Check DB for episodes first
            val existing = animeRepository.getBySourceAndUrl(source.id, sAnime.url)
            if (existing == null || episodeRepository.getByAnimeId(existing.id).isEmpty()) {
                // No DB episodes — fetch from source
                loadEpisodesFromSource()
            }
        }
    }

    private fun loadEpisodesFromSource() {
        viewModelScope.launch {
            _episodeState.value = EpisodeState.Loading(source.name)
            try {
                val episodes = withContext(Dispatchers.IO) {
                    source.getEpisodeList(sAnime)
                }
                if (episodes.isEmpty()) {
                    _episodeState.value = EpisodeState.NoMatch
                } else {
                    _episodeState.value = EpisodeState.Loaded(episodes, source.name)
                    Log.i(TAG, "Loaded ${episodes.size} episodes from '${source.name}'")
                    saveEpisodesToDb(episodes)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load episodes from '${source.name}'", e)
                _episodeState.value = EpisodeState.Error("Failed to load episodes: ${e.message}")
                Toast.makeText(appContext, "Failed to load episodes: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun saveEpisodesToDb(episodes: List<SEpisode>) {
        try {
            var dbAnime = animeRepository.getBySourceAndUrl(source.id, sAnime.url)
            if (dbAnime == null) {
                // Create a minimal anime entry
                val newAnime = Anime(
                    id = 0,
                    url = sAnime.url,
                    title = sAnime.title,
                    artist = null,
                    author = null,
                    description = sAnime.description,
                    genre = sAnime.genre?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
                    coverUrl = sAnime.thumbnail_url,
                    status = sAnime.status,
                    thumbnailUrl = sAnime.thumbnail_url,
                    favorite = false,
                    sourceId = source.id,
                    dateAdded = System.currentTimeMillis(),
                    viewerFlags = 0,
                    nextUpdate = 0,
                    updateStrategy = 0,
                    coverLastModified = 0,
                    releaseDate = null,
                    lastRefresh = System.currentTimeMillis(),
                    lastMetadataFetch = null,
                    nextEpisodeCheck = null,
                    anilistId = null, // No AniList ID for extension-only anime
                    coverColor = null,
                    score = null,
                    totalEpisodes = null,
                    lastWatched = 0,
                    nextAiringEpisode = null,
                )
                val newId = animeRepository.upsert(newAnime)
                Log.i(TAG, "Created extension anime in DB: ${sAnime.title}, dbId=$newId")
                dbAnime = animeRepository.getById(newId)
            }

            if (dbAnime != null) {
                episodeRepository.deleteByAnimeId(dbAnime.id)
                episodes.forEachIndexed { index, ep ->
                    episodeRepository.upsert(
                        app.confused.anikuta.core.common.model.Episode(
                            id = 0,
                            animeId = dbAnime.id,
                            url = ep.url,
                            name = ep.name,
                            episodeNumber = ep.episode_number,
                            scanlator = ep.scanlator,
                            seen = false,
                            bookmark = false,
                            lastSecondSeen = 0,
                            totalSeconds = 0,
                            sourceOrder = index.toLong(),
                            dateFetch = System.currentTimeMillis(),
                            dateUpload = ep.date_upload.takeIf { it > 0 },
                            fillermark = if (ep.fillermark) "filler" else null,
                            summary = ep.summary,
                            previewUrl = ep.preview_url,
                        ),
                    )
                }
                Log.i(TAG, "Saved ${episodes.size} episodes to DB for extension animeId=${dbAnime.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save episodes to DB (non-fatal)", e)
        }
    }

    fun toggleSave() {
        viewModelScope.launch {
            try {
                val existing = animeRepository.getBySourceAndUrl(source.id, sAnime.url)
                if (existing != null) {
                    val newFav = !existing.favorite
                    animeRepository.updateFavorite(
                        id = existing.id,
                        favorite = newFav,
                        dateAdded = if (newFav) System.currentTimeMillis() else existing.dateAdded,
                    )
                    _isSaved.value = newFav
                    if (newFav) {
                        categoryRepository.setAnimeCategories(existing.id, listOf(Category.DEFAULT_ID))
                    }
                } else {
                    // Create + save
                    val newAnime = Anime(
                        id = 0,
                        url = sAnime.url,
                        title = sAnime.title,
                        artist = null,
                        author = null,
                        description = sAnime.description,
                        genre = sAnime.genre?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
                        coverUrl = sAnime.thumbnail_url,
                        status = sAnime.status,
                        thumbnailUrl = sAnime.thumbnail_url,
                        favorite = true,
                        sourceId = source.id,
                        dateAdded = System.currentTimeMillis(),
                        viewerFlags = 0,
                        nextUpdate = 0,
                        updateStrategy = 0,
                        coverLastModified = 0,
                        releaseDate = null,
                        lastRefresh = System.currentTimeMillis(),
                        lastMetadataFetch = null,
                        nextEpisodeCheck = null,
                        anilistId = null,
                        coverColor = null,
                        score = null,
                        totalEpisodes = null,
                        lastWatched = 0,
                        nextAiringEpisode = null,
                    )
                    val newId = animeRepository.upsert(newAnime)
                    categoryRepository.setAnimeCategories(newId, listOf(Category.DEFAULT_ID))
                    _isSaved.value = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "toggleSave failed", e)
            }
        }
    }

    fun refresh() {
        _isRefreshing.value = true
        loadEpisodesFromSource()
        viewModelScope.launch {
            // Wait a bit then clear refreshing
            kotlinx.coroutines.delay(500)
            _isRefreshing.value = false
        }
    }

    companion object {
        private const val TAG = "ExtensionDetailVM"
    }
}

/** State for the extension-only details page. */
sealed interface ExtensionDetailState {
    data object Loading : ExtensionDetailState
    data class Success(val anime: ExtensionAnime) : ExtensionDetailState
    data class Error(val message: String) : ExtensionDetailState
}

/** Extension anime data (from SAnime, no AniList enrichment). */
data class ExtensionAnime(
    val title: String,
    val description: String?,
    val genre: List<String>,
    val coverUrl: String?,
    val backgroundUrl: String?,
    val status: Int,
    val sourceName: String,
    val url: String,
    val sourceId: Long,
)

/** Converts a DB Episode back to an SEpisode for the UI. */
private fun app.confused.anikuta.core.common.model.Episode.toExtensionSEpisode(): SEpisode {
    return eu.kanade.tachiyomi.animesource.model.SEpisodeImpl().apply {
        url = this@toExtensionSEpisode.url ?: ""
        name = this@toExtensionSEpisode.name
        episode_number = this@toExtensionSEpisode.episodeNumber
        date_upload = this@toExtensionSEpisode.dateUpload ?: 0
        scanlator = this@toExtensionSEpisode.scanlator
        summary = this@toExtensionSEpisode.summary
        preview_url = this@toExtensionSEpisode.previewUrl
        fillermark = this@toExtensionSEpisode.fillermark == "filler"
    }
}
