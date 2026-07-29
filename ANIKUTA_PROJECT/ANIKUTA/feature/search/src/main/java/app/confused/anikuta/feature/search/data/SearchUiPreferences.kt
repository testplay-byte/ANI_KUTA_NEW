package app.confused.anikuta.feature.search.data

import app.confused.anikuta.core.preferences.PreferenceStore
import app.confused.anikuta.feature.search.viewmodel.SearchSource

/**
 * Persisted UI state for the Search page that must survive screen changes +
 * app restarts.
 *
 * Holds:
 * - [recentsCollapsed] — whether the RecentSearchesCard is collapsed.
 * - [searchSource] — the active search section (AniList vs Extensions).
 * - [selectedExtensionSourceId] — the last-selected extension source ID (null = none).
 *
 * The source + extension persistence is the search-state-survival fix from the
 * scroll-blur branch: when the user navigates away from Search (e.g. opens an
 * anime detail page) and comes back, OR fully closes the app + reopens it, the
 * Search page restores the previously-selected source + extension (rather than
 * always defaulting to AniList + the first installed extension). The persisted
 * extension ID is validated against the still-installed sources on restore —
 * if the user uninstalled it, we fall back to the first available source.
 *
 * Backed by [PreferenceStore] (SharedPreferences). Cheap to read/write.
 */
class SearchUiPreferences(
    private val preferenceStore: PreferenceStore,
) {
    private val recentsCollapsedPref = preferenceStore.getBoolean(
        key = KEY_RECENTS_COLLAPSED,
        defaultValue = false,
    )

    /** Whether the RecentSearchesCard should render collapsed. */
    fun isRecentsCollapsed(): Boolean = recentsCollapsedPref.get()

    /** Persist the collapsed state (called when the user toggles the card). */
    fun setRecentsCollapsed(collapsed: Boolean) {
        recentsCollapsedPref.set(collapsed)
    }

    // ── Search source persistence (AniList vs Extensions) ──

    private val searchSourcePref = preferenceStore.getString(
        key = KEY_SEARCH_SOURCE,
        defaultValue = SearchSource.ANILIST.name,
    )

    /** The last-selected search section (AniList or Extensions). */
    fun getSearchSource(): SearchSource = try {
        SearchSource.valueOf(searchSourcePref.get())
    } catch (e: IllegalArgumentException) {
        // Tolerate legacy/unknown values — fall back to ANILIST.
        SearchSource.ANILIST
    }

    /** Persist the search source (called when the user switches sections). */
    fun setSearchSource(source: SearchSource) {
        searchSourcePref.set(source.name)
    }

    // ── Selected extension source persistence ──

    private val selectedExtensionSourceIdPref = preferenceStore.getLong(
        key = KEY_SELECTED_EXTENSION_SOURCE,
        defaultValue = -1L,
    )

    /** The last-selected extension source ID, or null if none was selected. */
    fun getSelectedExtensionSourceId(): Long? {
        val id = selectedExtensionSourceIdPref.get()
        return if (id > 0) id else null
    }

    /** Persist the selected extension source (called when the user picks one). */
    fun setSelectedExtensionSourceId(id: Long?) {
        selectedExtensionSourceIdPref.set(id ?: -1L)
    }

    companion object {
        private const val KEY_RECENTS_COLLAPSED = "pref_search_recents_collapsed"
        private const val KEY_SEARCH_SOURCE = "pref_search_source"
        private const val KEY_SELECTED_EXTENSION_SOURCE = "pref_search_selected_extension"
    }
}
