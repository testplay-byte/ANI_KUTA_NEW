package app.confused.anikuta.core.common.model

/**
 * Domain model for an episode.
 *
 * Anime-specific fields: [fillermark], [summary], [previewUrl].
 * Watch progress: [seen], [lastSecondSeen], [totalSeconds].
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
)
