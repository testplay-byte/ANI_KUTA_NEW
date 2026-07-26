package app.confused.anikuta.core.backup.model

import kotlinx.serialization.Serializable

/**
 * Serializable representation of a row in the `episodes` SQLDelight table.
 *
 * Keyed by [animeId] (the local DB id of the parent anime) when stored in
 * [BackupEntry.Episodes.byAnime].
 *
 * Every column from `episodes.sq` is represented. Defaults match the schema.
 */
@Serializable
data class EpisodeBackup(
    val _id: Long = 0,
    val animeId: Long,
    val url: String? = null,
    val name: String,
    val episodeNumber: Double,
    val scanlator: String? = null,
    val seen: Boolean = false,
    val bookmark: Boolean = false,
    val lastSecondSeen: Long = 0,
    val totalSeconds: Long = 0,
    val sourceOrder: Long,
    val dateFetch: Long = 0,
    val dateUpload: Long? = null,
    // Anime-specific fields
    val fillermark: String? = null,
    val summary: String? = null,
    val previewUrl: String? = null,
)
