package app.confused.anikuta.core.tracker.anilist

/** Viewer data returned by the AniList GraphQL Viewer query. */
data class AniListViewer(
    val id: Int,
    val name: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val scoreFormat: String,
)
