package app.anikuta.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.cache.CacheManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * ViewModel for the home page.
 * Uses the CacheManager to fetch AniList data with the 3-step cache.
 * Crash-resistant: if DI or data loading fails, shows error state instead of crashing.
 */
class HomeViewModel : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val anilistRepo: AniListRepository?
    private val cacheManager: CacheManager?
    private val json = Json { ignoreUnknownKeys = true }

    init {
        // Try to get dependencies from DI — don't crash if they're not available
        anilistRepo = try {
            Injekt.get<AniListRepository>().also { Log.d(TAG, "✅ AniListRepository obtained") }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get AniListRepository from DI", e)
            null
        }
        cacheManager = try {
            Injekt.get<CacheManager>().also { Log.d(TAG, "✅ CacheManager obtained") }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get CacheManager from DI", e)
            null
        }
    }

    private val _trending = MutableStateFlow<HomeSectionState>(HomeSectionState.Loading)
    val trending: StateFlow<HomeSectionState> = _trending.asStateFlow()

    private val _popular = MutableStateFlow<HomeSectionState>(HomeSectionState.Loading)
    val popular: StateFlow<HomeSectionState> = _popular.asStateFlow()

    private val _fresh = MutableStateFlow<HomeSectionState>(HomeSectionState.Loading)
    val fresh: StateFlow<HomeSectionState> = _fresh.asStateFlow()

    private val _genres = MutableStateFlow<HomeSectionState>(HomeSectionState.Loading)
    val genres: StateFlow<HomeSectionState> = _genres.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadAll()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Await ALL loads before clearing the flag. Previously loadAll()
                // was fire-and-forget (each loadXxx() launched its own coroutine
                // and returned immediately), so isRefreshing flipped false before
                // any load finished — which confused PullToRefreshBox's internal
                // state and left the indicator stuck until a scroll forced a
                // recomposition. coroutineScope { launch ... } waits for all
                // children to complete.
                coroutineScope {
                    launch { doLoadTrending() }
                    launch { doLoadPopular() }
                    launch { doLoadFresh() }
                    launch { doLoadGenres() }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun loadAll() {
        Log.d(TAG, "Loading all sections...")
        if (cacheManager == null || anilistRepo == null) {
            Log.e(TAG, "Dependencies not available — setting all sections to error")
            _trending.value = HomeSectionState.Error("App not properly initialized")
            _popular.value = HomeSectionState.Error("App not properly initialized")
            _fresh.value = HomeSectionState.Error("App not properly initialized")
            _genres.value = HomeSectionState.Error("App not properly initialized")
            return
        }
        // Fire-and-forget for the initial load (parallel, no waiting).
        viewModelScope.launch { doLoadTrending() }
        viewModelScope.launch { doLoadPopular() }
        viewModelScope.launch { doLoadFresh() }
        viewModelScope.launch { doLoadGenres() }
    }

    private suspend fun doLoadTrending() {
        _trending.value = HomeSectionState.Loading
        try {
            Log.d(TAG, "Fetching trending...")
            val data = cacheManager!!.getOrFetch(
                key = "trending",
                ttlMs = CacheManager.TTL_HOME_SHORT,
                fetch = { anilistRepo!!.getTrending() },
                serialize = { json.encodeToString(ListSerializer(AniListAnime.serializer()), it) },
                deserialize = { json.decodeFromString(ListSerializer(AniListAnime.serializer()), it) },
            )
            Log.d(TAG, "Trending: ${data?.size ?: 0} items")
            _trending.value = if (data != null) HomeSectionState.Success(data) else HomeSectionState.Error("No data")
        } catch (e: Exception) {
            Log.e(TAG, "Trending failed", e)
            _trending.value = HomeSectionState.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun doLoadPopular() {
        _popular.value = HomeSectionState.Loading
        try {
            Log.d(TAG, "Fetching popular...")
            val data = cacheManager!!.getOrFetch(
                key = "popular",
                ttlMs = CacheManager.TTL_HOME_SHORT,
                fetch = { anilistRepo!!.getPopular() },
                serialize = { json.encodeToString(ListSerializer(AniListAnime.serializer()), it) },
                deserialize = { json.decodeFromString(ListSerializer(AniListAnime.serializer()), it) },
            )
            Log.d(TAG, "Popular: ${data?.size ?: 0} items")
            _popular.value = if (data != null) HomeSectionState.Success(data) else HomeSectionState.Error("No data")
        } catch (e: Exception) {
            Log.e(TAG, "Popular failed", e)
            _popular.value = HomeSectionState.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun doLoadFresh() {
        _fresh.value = HomeSectionState.Loading
        try {
            Log.d(TAG, "Fetching fresh...")
            val data = cacheManager!!.getOrFetch(
                key = "freshly_updated",
                ttlMs = CacheManager.TTL_HOME_SHORT,
                fetch = { anilistRepo!!.getFreshlyUpdated() },
                serialize = { json.encodeToString(ListSerializer(AniListAnime.serializer()), it) },
                deserialize = { json.decodeFromString(ListSerializer(AniListAnime.serializer()), it) },
            )
            Log.d(TAG, "Fresh: ${data?.size ?: 0} items")
            _fresh.value = if (data != null) HomeSectionState.Success(data) else HomeSectionState.Error("No data")
        } catch (e: Exception) {
            Log.e(TAG, "Fresh failed", e)
            _fresh.value = HomeSectionState.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun doLoadGenres() {
        _genres.value = HomeSectionState.Loading
        try {
            Log.d(TAG, "Fetching genres...")
            val data = cacheManager!!.getOrFetch(
                key = "genres",
                ttlMs = CacheManager.TTL_GENRES,
                fetch = { anilistRepo!!.getGenres() },
                serialize = { json.encodeToString(ListSerializer(String.serializer()), it) },
                deserialize = { json.decodeFromString(ListSerializer(String.serializer()), it) },
            )
            Log.d(TAG, "Genres: ${data?.size ?: 0} items")
            _genres.value = if (data != null) HomeSectionState.GenresSuccess(data) else HomeSectionState.Error("No data")
        } catch (e: Exception) {
            Log.e(TAG, "Genres failed", e)
            _genres.value = HomeSectionState.Error(e.message ?: "Unknown error")
        }
    }
}

sealed class HomeSectionState {
    data object Loading : HomeSectionState()
    data class Success(val anime: List<AniListAnime>) : HomeSectionState()
    data class GenresSuccess(val genres: List<String>) : HomeSectionState()
    data class Error(val message: String) : HomeSectionState()
}
