package app.anikuta.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Aniyomi-compatible backup format (Protocol Buffers).
 *
 * Matches aniyomi's `Backup` model structure so our backups can be imported
 * by aniyomi, and aniyomi backups can be imported by us.
 *
 * Reference: REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/backup/full/models/Backup.kt
 *
 * Only anime-side data is included (no manga). We map our AniList-based data
 * to aniyomi's source-based model for interoperability.
 */
@Serializable
data class AniyomiBackup(
    @ProtoNumber(1) val backupManga: List<AniyomiBackupAnime> = emptyList(),  // We don't have manga, reuse anime model
    @ProtoNumber(2) val backupCategories: List<AniyomiBackupCategory> = emptyList(),
    @ProtoNumber(3) val backupAnime: List<AniyomiBackupAnime> = emptyList(),
    @ProtoNumber(4) val backupAnimeCategories: List<AniyomiBackupCategory> = emptyList(),
    @ProtoNumber(101) val backupSources: List<AniyomiBackupSource> = emptyList(),
    @ProtoNumber(103) val backupAnimeSources: List<AniyomiBackupAnimeSource> = emptyList(),
    @ProtoNumber(104) val backupPreferences: List<AniyomiBackupPreference> = emptyList(),
)

@Serializable
data class AniyomiBackupCategory(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val order: Long = 0,
    @ProtoNumber(3) val flags: Long = 0,
)

@Serializable
data class AniyomiBackupAnime(
    @ProtoNumber(1) val source: Long = 0,
    @ProtoNumber(2) val url: String = "",
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val artist: String? = null,
    @ProtoNumber(5) val author: String? = null,
    @ProtoNumber(6) val description: String? = null,
    @ProtoNumber(7) val genre: List<String> = emptyList(),
    @ProtoNumber(8) val status: Int = 0,
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    @ProtoNumber(13) val dateAdded: Long = 0,
    @ProtoNumber(16) val episodes: List<AniyomiBackupEpisode> = emptyList(),
    @ProtoNumber(17) val categories: List<Long> = emptyList(),
    @ProtoNumber(18) val tracking: List<AniyomiBackupTracking> = emptyList(),
    @ProtoNumber(100) val favorite: Boolean = true,
    @ProtoNumber(101) val episodeFlags: Int = 0,
    @ProtoNumber(103) val viewer_flags: Int = 0,
    @ProtoNumber(104) val history: List<AniyomiBackupHistory> = emptyList(),
    @ProtoNumber(106) val lastModifiedAt: Long = 0,
    @ProtoNumber(107) val favoriteModifiedAt: Long? = null,
    @ProtoNumber(109) val version: Long = 0,
)

@Serializable
data class AniyomiBackupEpisode(
    @ProtoNumber(1) val url: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val episodeNumber: Float = 0f,
    @ProtoNumber(4) val scanlator: String? = null,
    @ProtoNumber(5) val dateUpload: Long = 0,
    @ProtoNumber(6) val dateFetch: Long = 0,
    @ProtoNumber(7) val seen: Boolean = false,
    @ProtoNumber(8) val bookmark: Boolean = false,
    @ProtoNumber(9) val lastSecondSeen: Long = 0,
    @ProtoNumber(10) val totalSeconds: Long = 0,
)

@Serializable
data class AniyomiBackupHistory(
    @ProtoNumber(1) val url: String = "",
    @ProtoNumber(2) val lastRead: Long = 0,
    @ProtoNumber(3) val readDuration: Long = 0,
)

@Serializable
data class AniyomiBackupTracking(
    @ProtoNumber(1) val syncId: Int = 0,
    @ProtoNumber(2) val libraryId: Long? = null,
    @ProtoNumber(3) val mediaId: Long = 0,
    @ProtoNumber(4) val title: String = "",
    @ProtoNumber(5) val lastEpisodeSeen: Float = 0f,
    @ProtoNumber(6) val totalEpisodes: Int = 0,
    @ProtoNumber(7) val score: Float = 0f,
    @ProtoNumber(8) val status: Int = 0,
    @ProtoNumber(9) val startedWatchingDate: Long = 0,
    @ProtoNumber(10) val finishedWatchingDate: Long = 0,
)

@Serializable
data class AniyomiBackupSource(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val sourceId: Long = 0,
)

@Serializable
data class AniyomiBackupAnimeSource(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val sourceId: Long = 0,
    @ProtoNumber(3) val apiVersion: Long = 0,
)

@Serializable
data class AniyomiBackupPreference(
    @ProtoNumber(1) val key: String = "",
    @ProtoNumber(2) val value: String = "",
)
