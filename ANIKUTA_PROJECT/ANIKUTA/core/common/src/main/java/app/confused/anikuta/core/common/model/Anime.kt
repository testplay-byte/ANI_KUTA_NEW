package app.confused.anikuta.core.common.model

/**
 * Domain model for an anime entry.
 *
 * Status-tracking columns (ADR-024) support the notification/auto-download
 * features and let us debug stale data:
 * - [releaseDate] — when the anime was first released.
 * - [lastRefresh] — last time the library entry was refreshed from the source.
 * - [lastMetadataFetch] — last time metadata was fetched (AniList/extension).
 * - [nextEpisodeCheck] — when to next check for a new episode (ADR-014).
 *
 * Library columns (Phase A — library page):
 * - [anilistId] — the AniList media ID, used to link WatchProgressStore entries.
 * - [coverColor] — hex color extracted from the cover for dynamic theming.
 * - [score] — AniList average score (0-100).
 * - [totalEpisodes] — AniList total episodes count.
 * - [lastWatched] — epoch ms of last watch activity (for "Last watched" sort).
 */
data class Anime(
    val id: Long,
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genre: List<String>,
    val coverUrl: String?,
    val status: Int,
    val thumbnailUrl: String?,
    val favorite: Boolean,
    val sourceId: Long,
    val dateAdded: Long,
    val viewerFlags: Int,
    val nextUpdate: Long,
    val updateStrategy: Int,
    val coverLastModified: Long,
    // Status-tracking columns (ADR-024)
    val releaseDate: Long?,
    val lastRefresh: Long,
    val lastMetadataFetch: Long?,
    val nextEpisodeCheck: Long?,
    // Library columns (Phase A)
    val anilistId: Int?,
    val coverColor: String?,
    val score: Double?,
    val totalEpisodes: Int?,
    val lastWatched: Long,
    val nextAiringEpisode: Int?,
) {
    /**
     * The number of episodes that have aired (released).
     *
     * If [nextAiringEpisode] is not null, the anime is still airing and
     * released = nextAiringEpisode - 1. If null, the anime is finished and
     * released = [totalEpisodes].
     */
    val releasedEpisodes: Int?
        get() = when {
            nextAiringEpisode != null && nextAiringEpisode > 0 -> nextAiringEpisode - 1
            totalEpisodes != null -> totalEpisodes
            else -> null
        }
}

/** Anime publishing status. */
object AnimeStatus {
    const val UNKNOWN = 0
    const val ONGOING = 1
    const val COMPLETED = 2
    const val LICENSED = 3
    const val PUBLISHING_FINISHED = 4
    const val CANCELLED = 5
    const val ON_HIATUS = 6
}
