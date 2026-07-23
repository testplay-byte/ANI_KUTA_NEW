package app.confused.anikuta.feature.my

import app.confused.anikuta.core.tracker.ProfileStats
import app.confused.anikuta.core.tracker.TrackerUserStats

/** UI state for the My Profile page. */
data class ProfileState(
    val isLoading: Boolean = true,
    val isAniListLinked: Boolean = false,
    val anilistUsername: String? = null,
    val anilistAvatarUrl: String? = null,
    val localStats: ProfileStats? = null,
    val anilistStats: TrackerUserStats? = null,
    val error: String? = null,
) {
    /** The stats to display — prefers AniList stats when linked, falls back to local. */
    val displayStats: ProfileStats?
        get() = localStats

    /** Whether to show the AniList-enriched stats (vs local-only). */
    val showAniListStats: Boolean
        get() = isAniListLinked && anilistStats != null
}
