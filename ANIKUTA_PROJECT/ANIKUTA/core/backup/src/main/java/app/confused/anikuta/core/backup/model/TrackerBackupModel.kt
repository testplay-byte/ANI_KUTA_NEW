package app.confused.anikuta.core.backup.model

import kotlinx.serialization.Serializable

/**
 * Serializable wrapper for tracker data (OAuth tokens + animetrack bindings).
 *
 * Mirrors `TrackerBackupData` from `:core:tracker`'s [TrackerBackupProvider]
 * interface, but as a standalone `@Serializable` class so the backup format
 * is independent of the tracker module's internal model.
 *
 * **Security note:** OAuth tokens are sensitive. They are included in backups
 * so users don't have to re-login after restore, but the backup file should be
 * treated as sensitive. Future enhancement: encrypt the backup (ADR-028 notes
 * "no encryption initially").
 */
@Serializable
data class TrackerBackupModel(
    val anilistToken: String = "",
    val anilistUsername: String = "",
    val anilistUserId: Long = 0,
    val malOAuthJson: String? = null,
    val malUsername: String = "",
    /** Tracker bindings (mirrors the `animetrack` SQLDelight table rows). */
    val bindings: List<TrackerTrackItem> = emptyList(),
)

/**
 * One tracker binding (mirrors `AnimeTrack` from `:core:tracker`).
 */
@Serializable
data class TrackerTrackItem(
    val animeId: Long,
    val trackerId: Long,
    val remoteId: Long,
    val remoteUrl: String? = null,
    val lastSeen: Long = 0,
    val score: Double = 0.0,
    val status: Long = 0,
    val totalEpisodes: Long = 0,
    val displayScore: String? = null,
)
