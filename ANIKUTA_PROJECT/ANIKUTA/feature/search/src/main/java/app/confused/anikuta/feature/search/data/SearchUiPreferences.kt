package app.confused.anikuta.feature.search.data

import app.confused.anikuta.core.preferences.PreferenceStore

/**
 * Persisted UI state for the Search page that must survive screen changes +
 * app restarts (per owner request: "it should stay collapsed if it was
 * previously collapsed, even if the app is closed and reopened again or the
 * user goes to another screen").
 *
 * Currently holds:
 * - [recentsCollapsed] — whether the RecentSearchesCard is collapsed.
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

    companion object {
        private const val KEY_RECENTS_COLLAPSED = "pref_search_recents_collapsed"
    }
}
