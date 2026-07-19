package app.anikuta.ui.debug

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.model.AnimeFilterList
import app.anikuta.source.api.model.SAnime
import app.anikuta.source.api.model.SEpisode
import app.anikuta.source.api.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * ViewModel for the hidden Debug / Extension-test screen (Phase 5 task 5.1).
 *
 * Manually exercises the extension chain so we can verify — step by step —
 * that an installed extension can: search by title → return episodes → return
 * video URLs. Every step is logged + surfaced as state so we can see exactly
 * where the chain breaks (if it does) before wiring it into the detail page.
 *
 * This is a developer tool, built behind a flag (accessible via long-press on
 * the version number in Settings). It's intentionally verbose. Easily removed
 * for release: delete this file + the nav route + the MoreScreen long-press.
 */
class DebugViewModel : ViewModel() {

    companion object {
        private const val TAG = "DebugExtTest"
    }

    private val sourceManager: AnimeSourceManager? = try { Injekt.get() } catch (e: Exception) {
        Log.e(TAG, "SourceManager not available", e); null
    }

    private val extensionManager: app.anikuta.extension.anime.AnimeExtensionManager? =
        try { Injekt.get() } catch (e: Exception) {
            Log.e(TAG, "ExtensionManager not available", e); null
        }

    private val _sources = MutableStateFlow<List<AnimeCatalogueSource>>(emptyList())
    val sources: StateFlow<List<AnimeCatalogueSource>> = _sources.asStateFlow()

    private val _selectedSource = MutableStateFlow<AnimeCatalogueSource?>(null)
    val selectedSource: StateFlow<AnimeCatalogueSource?> = _selectedSource.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SAnime>>(emptyList())
    val searchResults: StateFlow<List<SAnime>> = _searchResults.asStateFlow()

    private val _episodes = MutableStateFlow<List<SEpisode>>(emptyList())
    val episodes: StateFlow<List<SEpisode>> = _episodes.asStateFlow()

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        // Reload extensions first, then refresh the source list.
        // The reload is async (scope.launch) but we also do an immediate
        // refreshSources() so the UI doesn't start empty.
        extensionManager?.reload()
        refreshSources()
    }

    fun refreshSources() {
        val mgr = sourceManager ?: run {
            log("❌ SourceManager not available", error = true); return
        }
        val all = mgr.getCatalogueSources()
        _sources.value = all
        if (all.isEmpty()) {
            log("No catalogue sources found. Extensions installed: ${extensionManager?.installedExtensions?.value?.size ?: 0}")
            log("If you just installed an extension, tap Refresh to re-scan.")
        } else {
            log("Found ${all.size} catalogue source(s): ${all.joinToString { "${it.name} (id=${it.id})" }}")
        }
    }

    /**
     * Force a full re-scan of installed extensions, then refresh the source list.
     * Called by the Debug screen's Refresh button.
     */
    fun reloadExtensions() {
        log("→ Reloading extensions…")
        // Log the raw loader results for diagnosis.
        val installed = extensionManager?.installedExtensions?.value ?: emptyList()
        log("  Currently known installed extensions: ${installed.size}")
        installed.forEach { ext ->
            log("  • ${ext.name} (pkg=${ext.pkgName}, lang=${ext.lang}, sources=${ext.sources.size})")
        }
        extensionManager?.reload()
        // Give the reload a moment to complete, then refresh.
        viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            val after = extensionManager?.installedExtensions?.value ?: emptyList()
            log("  After reload: ${after.size} installed extension(s)")
            after.forEach { ext ->
                log("  • ${ext.name} (pkg=${ext.pkgName}, sources=${ext.sources.size})")
            }
            refreshSources()
        }
    }

    fun selectSource(source: AnimeCatalogueSource) {
        _selectedSource.value = source
        _searchResults.value = emptyList()
        _episodes.value = emptyList()
        _videos.value = emptyList()
        log("Selected source: ${source.name} (id=${source.id}, lang=${source.lang})")
    }

    fun search(query: String) {
        val source = _selectedSource.value ?: run {
            log("❌ No source selected", error = true); return
        }
        if (query.isBlank()) {
            log("❌ Query is blank", error = true); return
        }
        viewModelScope.launch {
            _loading.value = true
            log("→ Searching '${source.name}' for \"$query\"…")
            try {
                val page = withContext(Dispatchers.IO) {
                    source.getSearchAnime(1, query, AnimeFilterList())
                }
                _searchResults.value = page.animes
                log("✓ Search returned ${page.animes.size} result(s)${if (page.hasNextPage) " (has next page)" else ""}")
                page.animes.take(3).forEach {
                    log("  • ${it.title}  →  url=${it.url}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                log("❌ Search failed: ${e.javaClass.simpleName}: ${e.message}", error = true)
            } finally {
                _loading.value = false
            }
        }
    }

    fun fetchEpisodes(anime: SAnime) {
        val source = _selectedSource.value ?: run {
            log("❌ No source selected", error = true); return
        }
        viewModelScope.launch {
            _loading.value = true
            _videos.value = emptyList()
            log("→ Fetching episodes for '${anime.title}' (url=${anime.url})…")
            try {
                val eps = withContext(Dispatchers.IO) { source.getEpisodeList(anime) }
                _episodes.value = eps
                log("✓ Got ${eps.size} episode(s)")
                eps.take(3).forEach {
                    log("  • Ep ${it.episode_number} — ${it.name}  →  url=${it.url}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Episode list failed", e)
                log("❌ Episode list failed: ${e.javaClass.simpleName}: ${e.message}", error = true)
            } finally {
                _loading.value = false
            }
        }
    }

    fun fetchVideos(episode: SEpisode) {
        val source = _selectedSource.value ?: run {
            log("❌ No source selected", error = true); return
        }
        viewModelScope.launch {
            _loading.value = true
            log("→ Fetching videos for '${episode.name}' (url=${episode.url})…")
            try {
                val vids = withContext(Dispatchers.IO) { source.getVideoList(episode) }
                _videos.value = vids
                log("✓ Got ${vids.size} video(s)")
                vids.take(3).forEach {
                    log("  • ${it.videoTitle.ifBlank { "untitled" }}  →  ${it.videoUrl}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video list failed", e)
                log("❌ Video list failed: ${e.javaClass.simpleName}: ${e.message}", error = true)
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    private fun log(message: String, error: Boolean = false) {
        val entry = LogEntry(System.currentTimeMillis(), message, error)
        _log.value = _log.value + entry
        if (error) Log.e(TAG, message) else Log.d(TAG, message)
    }

    data class LogEntry(val time: Long, val message: String, val error: Boolean)
}
