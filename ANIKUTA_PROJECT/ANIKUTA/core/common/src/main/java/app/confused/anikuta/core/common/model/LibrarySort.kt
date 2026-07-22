package app.confused.anikuta.core.common.model

/**
 * Sort options for the library page.
 *
 * Per user decision (Q2): sort is GLOBAL — changing it affects all categories.
 * The sort type and direction are stored together in [LibraryPreferences].
 *
 * [Progress] sort is computed in Kotlin (not SQL) because watch progress
 * lives in [WatchProgressStore] (JSON-in-prefs), not in the SQLDelight database.
 */
enum class LibrarySortType(val displayName: String) {
    TITLE("Title"),
    DATE_ADDED("Date Added"),
    LAST_WATCHED("Last Watched"),
    PROGRESS("Progress"),
    TOTAL_EPISODES("Total Episodes"),
}

/**
 * A sort selection: type + ascending/descending direction.
 *
 * Default: TITLE, ascending (A-Z).
 */
data class LibrarySort(
    val type: LibrarySortType,
    val ascending: Boolean,
) {
    companion object {
        val DEFAULT = LibrarySort(LibrarySortType.TITLE, ascending = true)
    }
}
