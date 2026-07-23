package app.confused.anikuta.feature.my

import app.confused.anikuta.core.preferences.PreferenceStore

/**
 * User-customizable Profile page preferences (ADR-018: feature parity with
 * customizable defaults).
 *
 * Controls which sections of the Profile page are visible and holds the
 * "last reset" timestamp for the reset-stats feature.
 */
class ProfilePreferences(
    private val store: PreferenceStore,
) {
    val showQuickStats = store.getBoolean("pref_profile_show_quick_stats", true)
    val showGenreChart = store.getBoolean("pref_profile_show_genre", true)
    val showFormatChart = store.getBoolean("pref_profile_show_format", true)
    val showStatusChart = store.getBoolean("pref_profile_show_status", true)
    val showScoreChart = store.getBoolean("pref_profile_show_score", true)
    val showCountryChart = store.getBoolean("pref_profile_show_country", true)
    val showBehindStatus = store.getBoolean("pref_profile_show_behind", true)
    val showRecentlyWatched = store.getBoolean("pref_profile_show_recent", true)

    /** All section visibility preferences (for the customization sheet). */
    val allSections = listOf(
        "Quick Stats" to showQuickStats,
        "Genres" to showGenreChart,
        "Formats" to showFormatChart,
        "Status" to showStatusChart,
        "Scores" to showScoreChart,
        "Countries" to showCountryChart,
        "Behind Status" to showBehindStatus,
        "Recently Watched" to showRecentlyWatched,
    )
}
