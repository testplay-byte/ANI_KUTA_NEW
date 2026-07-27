package app.confused.anikuta.core.backup.model

import kotlinx.serialization.Serializable

/**
 * Serializable representation of a row in the `animes` SQLDelight table.
 *
 * Used by both [BackupEntry.Library] (favorites only) and
 * [BackupEntry.AnimeDetails] (full details — description, genres, scores).
 *
 * Every column from `animes.sq` is represented here so the backup is
 * self-contained. Nullable columns are nullable here too. Default values
 * match the `.sq` schema so partial backups deserialize cleanly.
 *
 * **Never remove a field** — mark it `@Deprecated` with a default instead.
 * Removing a field breaks deserialization of old backups.
 */
@Serializable
data class AnimeBackup(
    val _id: Long = 0,
    val url: String,
    val title: String,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: String? = null,        // comma-separated (matches DB)
    val coverUrl: String? = null,
    val status: Long = 0,             // 0=unknown, 1=ongoing, 2=completed, ...
    val thumbnailUrl: String? = null,
    val favorite: Boolean = false,
    val sourceId: Long = 0,
    val dateAdded: Long = 0,
    val viewerFlags: Long = 0,
    val nextUpdate: Long = 0,
    val updateStrategy: Long = 0,
    val coverLastModified: Long = 0,
    // Status-tracking columns (ADR-024)
    val releaseDate: Long? = null,
    val lastRefresh: Long = 0,
    val lastMetadataFetch: Long? = null,
    val nextEpisodeCheck: Long? = null,
    // Library columns
    val anilistId: Long? = null,
    val coverColor: String? = null,
    val score: Double? = null,
    val totalEpisodes: Long? = null,
    val lastWatched: Long = 0,
    val nextAiringEpisode: Long? = null,
)
