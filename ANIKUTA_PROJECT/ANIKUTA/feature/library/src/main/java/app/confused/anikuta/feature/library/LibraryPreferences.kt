package app.confused.anikuta.feature.library

import app.confused.anikuta.core.common.model.LibraryDisplayMode
import app.confused.anikuta.core.common.model.LibrarySortType
import app.confused.anikuta.core.preferences.Preference
import app.confused.anikuta.core.preferences.PreferenceStore
import app.confused.anikuta.core.preferences.getEnum

/**
 * Typed preferences for the library page.
 *
 * Per user decisions:
 * - Q2: sort is GLOBAL (one sort for all categories).
 * - Q3: display mode is GLOBAL (one mode for all categories).
 *
 * Persisted via [PreferenceStore] (SharedPreferences-backed). All preferences
 * are reactive — observe via [Preference.changes].
 */
class LibraryPreferences(
    private val store: PreferenceStore,
) {
    fun displayMode(): Preference<LibraryDisplayMode> =
        store.getEnum("pref_library_display_mode", LibraryDisplayMode.COMPACT_GRID)

    fun columnsPortrait(): Preference<Int> =
        store.getInt("pref_library_columns_portrait", 0)    // 0 = auto (adaptive)

    fun columnsLandscape(): Preference<Int> =
        store.getInt("pref_library_columns_landscape", 0)

    fun sortType(): Preference<LibrarySortType> =
        store.getEnum("pref_library_sort_type", LibrarySortType.TITLE)

    fun sortAscending(): Preference<Boolean> =
        store.getBoolean("pref_library_sort_ascending", true)

    fun showContinueWatching(): Preference<Boolean> =
        store.getBoolean("pref_library_show_continue_watching", true)

    fun showEpisodeBadge(): Preference<Boolean> =
        store.getBoolean("pref_library_show_episode_badge", true)

    fun showScoreBadge(): Preference<Boolean> =
        store.getBoolean("pref_library_show_score_badge", false)
}
