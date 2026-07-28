package app.confused.anikuta.core.common.model

/**
 * Domain model for an episode.
 *
 * Anime-specific fields: [fillermark], [summary], [previewUrl].
 * Watch progress: [seen], [lastSecondSeen], [totalSeconds].
 *
 * Status-tracking columns (ADR-024, added in 2.sqm):
 * - [releaseDate] — when the episode was first released.
 * - [lastRefresh] — last time the episode was refreshed from source.
 * - [lastMetadataFetch] — last time metadata was fetched for this episode.
 * - [nextEpisodeCheck] — reserved for future per-episode scheduling.
 *
 * All four fields have defaults so existing constructors (e.g. backup import,
 * tests) keep compiling without specifying them.
 */
data class Episode(
    val id: Long,
    val animeId: Long,
    val url: String?,
    val name: String,
    val episodeNumber: Float,
    val scanlator: String?,
    val seen: Boolean,
    val bookmark: Boolean,
    val lastSecondSeen: Long,
    val totalSeconds: Long,
    val sourceOrder: Long,
    val dateFetch: Long,
    val dateUpload: Long?,
    // Anime-specific fields
    val fillermark: String?,
    val summary: String?,
    val previewUrl: String?,
    // Status-tracking columns (ADR-024) — defaults preserve binary compat with
    // existing call sites that don't yet populate these fields.
    val releaseDate: Long? = null,
    val lastRefresh: Long = 0L,
    val lastMetadataFetch: Long? = null,
    val nextEpisodeCheck: Long? = null,
)
