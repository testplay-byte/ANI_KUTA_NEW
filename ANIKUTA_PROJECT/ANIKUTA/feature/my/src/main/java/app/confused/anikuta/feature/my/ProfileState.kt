package app.confused.anikuta.feature.my

import app.confused.anikuta.core.tracker.ProfileStats
import app.confused.anikuta.core.tracker.TrackerUserStats

/** UI state for the My Profile page. */
data class ProfileState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isAniListLinked: Boolean = false,
    val anilistUsername: String? = null,
    val anilistAvatarUrl: String? = null,
    val displayName: String = "",
    val displayAvatarUrl: String = "",
    val useTrackerStats: Boolean = true,
    val localStats: ProfileStats? = null,
    val anilistStats: TrackerUserStats? = null,
    val error: String? = null,
) {
    /** The display name: user-customized name if set, else AniList username, else "Local User". */
    val effectiveDisplayName: String?
        get() = displayName.ifBlank { anilistUsername }

    /** The display avatar: user-customized URL if set, else AniList avatar. */
    val effectiveAvatarUrl: String?
        get() = displayAvatarUrl.ifBlank { anilistAvatarUrl }

    /** The stats to display — local stats (always available). */
    val displayStats: ProfileStats?
        get() = localStats

    /** Whether to show the AniList-enriched stats (vs local-only). */
    val showAniListStats: Boolean
        get() = useTrackerStats && isAniListLinked && anilistStats != null
}
