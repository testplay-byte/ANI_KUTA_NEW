package app.confused.anikuta.feature.library

import app.confused.anikuta.core.common.model.BadgePosition
import app.confused.anikuta.core.common.model.EpisodeBadgeMode
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

    /** Controls what the episode badge shows: total, released, or off. */
    fun episodeBadgeMode(): Preference<EpisodeBadgeMode> =
        store.getEnum("pref_library_episode_badge_mode", EpisodeBadgeMode.RELEASED)

    fun showScoreBadge(): Preference<Boolean> =
        store.getBoolean("pref_library_show_score_badge", false)

    /** When true, the library header shows "N in Library" instead of just "Library". */
    fun showTotalEntries(): Preference<Boolean> =
        store.getBoolean("pref_library_show_total_entries", false)

    /** Number of lines for anime titles in grid/list (1, 2, or 3). */
    fun titleLines(): Preference<Int> =
        store.getInt("pref_library_title_lines", 2)

    /** Where the episode badge sits on the card. */
    fun episodeBadgePosition(): Preference<BadgePosition> =
        store.getEnum("pref_library_episode_badge_pos", BadgePosition.TOP_END)

    /** Where the score badge sits on the card. */
    fun scoreBadgePosition(): Preference<BadgePosition> =
        store.getEnum("pref_library_score_badge_pos", BadgePosition.BOTTOM_END)
}
