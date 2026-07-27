package app.confused.anikuta.feature.my

import app.confused.anikuta.core.preferences.PreferenceStore

/**
 * User-customizable Profile page preferences (ADR-018: feature parity with
 * customizable defaults).
 *
 * Controls:
 * - Which sections of the Profile page are visible.
 * - The display name and avatar URL (user-customizable, overrides AniList defaults).
 * - Whether to use tracker (AniList) stats or local stats.
 */
class ProfilePreferences(
    private val store: PreferenceStore,
) {
    // Section visibility toggles
    val showQuickStats = store.getBoolean("pref_profile_show_quick_stats", true)
    val showGenreChart = store.getBoolean("pref_profile_show_genre", true)
    val showStatusChart = store.getBoolean("pref_profile_show_status", true)
    val showBehindStatus = store.getBoolean("pref_profile_show_behind", true)
    val showRecentlyWatched = store.getBoolean("pref_profile_show_recent", true)

    // User-customizable display name + avatar (overrides AniList defaults when set)
    val displayName = store.getString("pref_profile_display_name", "")
    val displayAvatarUrl = store.getString("pref_profile_display_avatar_url", "")

    // Stats source: true = use tracker (AniList) stats when linked, false = local only
    val useTrackerStats = store.getBoolean("pref_profile_use_tracker_stats", true)
}
