package app.confused.anikuta.feature.search.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.model.AniListAnime
import app.confused.anikuta.core.anilist.model.displayTitle
import app.confused.anikuta.data.extension.cache.ExtensionLinkStore
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The outcome of the extension→AniList linking flow.
 *
 * - [Linked] — a cached link existed, OR the auto-search found a match and was
 *   auto-linked. The caller opens the AniList detail page for [anilistId].
 * - [NeedsManualLink] — the auto-search found no good match. The caller shows
 *   [ExtensionLinkingSheet] so the user can pick a result or search manually
 *   or "go without linking".
 * - [GoWithoutLinking] — the user chose to open a minimal detail page using
 *   only the extension's SAnime data (no AniList ID).
 * - [Error] — the AniList search itself failed (network). The caller shows the
 *   sheet with the error so the user can retry or go without linking.
 */
sealed class ExtensionLinkingState {
    /** Loading — the auto-search is running. */
    data object Loading : ExtensionLinkingState()

    /** A link was resolved (cached or auto-linked). Open the AniList detail. */
    data class Linked(val anilistId: Int) : ExtensionLinkingState()

    /**
     * Auto-search done, no confident match. Show the linking sheet with
     * [results] for manual selection (+ manual search field + "go without").
     */
    data class NeedsManualLink(
        val results: List<AniListAnime>,
        val error: String? = null,
    ) : ExtensionLinkingState()

    /** The user picked "go without linking" — open the extension-only detail. */
    data class GoWithoutLinking(
        val source: AnimeCatalogueSource,
        val sAnime: SAnime,
    ) : ExtensionLinkingState()
}

/**
 * Drives the extension→AniList linking flow for one extension result.
 *
 * Lifecycle:
 * 1. Constructed with the tapped extension result (source + sAnime).
 * 2. Checks [ExtensionLinkStore] for a cached link — if found, emits [Linked].
 * 3. Otherwise searches AniList by the SAnime title (Dispatchers.IO).
 *    - If results found → auto-links the first result → emits [Linked].
 *    - If no results / search failed → emits [NeedsManualLink] (caller shows
 *      the sheet).
 * 4. While in [NeedsManualLink], the user can:
 *    - Tap a result → [selectManual] → link + [Linked].
 *    - Type a different query → [manualSearch] → new results in [NeedsManualLink].
 *    - Tap "go without linking" → [goWithoutLinking] → [GoWithoutLinking].
 *
 * @param source the extension source the result came from.
 * @param sAnime the tapped extension anime.
 * @param anilistApi the AniList client (for the title search).
 * @param linkStore persists the link (cache check + write).
 */
class ExtensionLinkingViewModel(
    private val source: AnimeCatalogueSource,
    private val sAnime: SAnime,
    private val anilistApi: AniListApi,
    private val linkStore: ExtensionLinkStore,
) : ViewModel() {

    private val _state = MutableStateFlow<ExtensionLinkingState>(ExtensionLinkingState.Loading)
    val state: StateFlow<ExtensionLinkingState> = _state.asStateFlow()

    /** The extension anime being linked (read by the UI for the header card). */
    val extensionTitle: String get() = sAnime.title
    val extensionThumbnailUrl: String? get() = sAnime.thumbnail_url
    val extensionSourceName: String get() = source.name

    init {
        attemptLink()
    }

    /** Entry point — cache check, then AniList auto-search. */
    private fun attemptLink() {
        // 1. Cache check — skip the sheet entirely if we've linked this before.
        val cached = linkStore.getAniListId(source.id, sAnime.url)
        if (cached != null) {
            Log.i(TAG, "Cache hit: ${sAnime.title} → AniList $cached (no sheet shown)")
            _state.value = ExtensionLinkingState.Linked(cached)
            return
        }

        // 2. Auto-search AniList by the extension title.
        viewModelScope.launch {
            _state.value = ExtensionLinkingState.Loading
            try {
                val results = withContext(Dispatchers.IO) {
                    anilistApi.searchAnime(sAnime.title, perPage = 10)
                }
                if (results.isNotEmpty()) {
                    // Auto-link the first result (AniList's SEARCH_MATCH sort
                    // already orders by relevance).
                    val best = results.first()
                    linkStore.link(source.id, sAnime.url, best.id)
                    Log.i(TAG, "Auto-linked: '${sAnime.title}' → AniList ${best.id} ('${best.displayTitle()}')")
                    _state.value = ExtensionLinkingState.Linked(best.id)
                } else {
                    Log.i(TAG, "No AniList match for '${sAnime.title}' — showing manual-link sheet")
                    _state.value = ExtensionLinkingState.NeedsManualLink(results = emptyList())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-search failed for '${sAnime.title}'", e)
                _state.value = ExtensionLinkingState.NeedsManualLink(
                    results = emptyList(),
                    error = e.message ?: "Search failed",
                )
            }
        }
    }

    /** User typed a different query + tapped search (from the linking sheet). */
    fun manualSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _state.value = ExtensionLinkingState.Loading
            try {
                val results = withContext(Dispatchers.IO) {
                    anilistApi.searchAnime(q, perPage = 10)
                }
                _state.value = ExtensionLinkingState.NeedsManualLink(results = results)
            } catch (e: Exception) {
                _state.value = ExtensionLinkingState.NeedsManualLink(
                    results = emptyList(),
                    error = e.message ?: "Search failed",
                )
            }
        }
    }

    /** User tapped an AniList result → link it + open detail. */
    fun selectManual(anime: AniListAnime) {
        linkStore.link(source.id, sAnime.url, anime.id)
        Log.i(TAG, "Manual-linked: '${sAnime.title}' → AniList ${anime.id} ('${anime.displayTitle()}')")
        _state.value = ExtensionLinkingState.Linked(anime.id)
    }

    /** User tapped "go without linking" → open the extension-only detail page. */
    fun goWithoutLinking() {
        _state.value = ExtensionLinkingState.GoWithoutLinking(source, sAnime)
    }

    companion object {
        private const val TAG = "AnikutaExtLinkingVM"
    }
}
