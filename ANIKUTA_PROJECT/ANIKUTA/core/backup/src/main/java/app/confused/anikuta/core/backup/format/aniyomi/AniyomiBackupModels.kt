package app.confused.anikuta.core.backup.format.aniyomi

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Minimal Aniyomi protobuf models — restore-only.
 *
 * **Design principle:** Only declare fields we actually need to restore
 * (anime, episodes, categories, tracking, history). Fields we don't need
 * (preferences, extensions, repos, custom buttons) are NOT declared — the
 * protobuf decoder skips unknown fields at the wire level, so this is safe
 * and robust against schema changes in those areas.
 *
 * **Why not declare all fields?** Aniyomi's `PreferenceValue` is a sealed
 * class with a specific wire format. If our model declares it with a different
 * structure, the decoder fails. By not declaring preference/extension fields
 * at all, we avoid this issue entirely.
 *
 * **Two root models:** Aniyomi has both a modern `Backup` format (anime at
 * proto field 501) and a `LegacyBackup` format (anime at proto field 3).
 * We try modern first, then fall back to legacy (matching Aniyomi's own
 * `BackupDecoder` logic).
 *
 * Proto numbers match Aniyomi's source exactly for the fields we declare.
 * Never change an existing proto number — that breaks decoding.
 */

// ── Root models ──

/**
 * Modern Aniyomi backup format (current).
 * Anime are at proto field 501.
 */
@Serializable
data class AniyomiBackup(
    @ProtoNumber(1) val backupManga: List<AniyomiMangaStub> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<AniyomiBackupCategory> = emptyList(),
    @ProtoNumber(500) val isLegacy: Boolean = true,
    @ProtoNumber(501) val backupAnime: List<AniyomiBackupAnime> = emptyList(),
    @ProtoNumber(502) val backupAnimeCategories: List<AniyomiBackupCategory> = emptyList(),
    @ProtoNumber(503) val backupAnimeSources: List<AniyomiBackupAnimeSource> = emptyList(),
)

/**
 * Legacy Aniyomi backup format (older versions).
 * Anime are at proto field 3.
 */
@Serializable
data class AniyomiLegacyBackup(
    @ProtoNumber(1) val backupManga: List<AniyomiMangaStub> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<AniyomiBackupCategory> = emptyList(),
    @ProtoNumber(3) val backupAnime: List<AniyomiBackupAnime> = emptyList(),
    @ProtoNumber(4) val backupAnimeCategories: List<AniyomiBackupCategory> = emptyList(),
    @ProtoNumber(103) val backupAnimeSources: List<AniyomiBackupAnimeSource> = emptyList(),
)

/**
 * Minimal stub for manga entries — we only count them (for the "manga not supported"
 * warning), we don't process manga data.
 */
@Serializable
data class AniyomiMangaStub(
    @ProtoNumber(3) val title: String = "",
)

// ── Anime model (matches Aniyomi's BackupAnime exactly for declared fields) ──

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
    @ProtoNumber(18) val tracking: List<AniyomiBackupAnimeTracking> = emptyList(),
    @ProtoNumber(100) val favorite: Boolean = true,
    @ProtoNumber(101) val episode_flags: Int = 0,
    @ProtoNumber(103) val viewer_flags: Int = 0,
    @ProtoNumber(104) val history: List<AniyomiBackupAnimeHistory> = emptyList(),
    @ProtoNumber(105) val updateStrategy: Int = 0,
    @ProtoNumber(106) val lastModifiedAt: Long = 0,
    @ProtoNumber(107) val favoriteModifiedAt: Long? = null,
    @ProtoNumber(109) val version: Long = 0,
)

@Serializable
data class AniyomiBackupEpisode(
    @ProtoNumber(1) val url: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val scanlator: String? = null,
    @ProtoNumber(4) val seen: Boolean = false,
    @ProtoNumber(5) val bookmark: Boolean = false,
    @ProtoNumber(6) val lastSecondSeen: Long = 0,
    @ProtoNumber(7) val dateFetch: Long = 0,
    @ProtoNumber(8) val dateUpload: Long = 0,
    @ProtoNumber(9) val episodeNumber: Float = 0F,
    @ProtoNumber(10) val sourceOrder: Long = 0,
    @ProtoNumber(11) val lastModifiedAt: Long = 0,
    @ProtoNumber(12) val version: Long = 0,
    @ProtoNumber(16) val totalSeconds: Long = 0,
    @ProtoNumber(501) val fillermark: Boolean = false,
    @ProtoNumber(502) val summary: String? = null,
    @ProtoNumber(503) val previewUrl: String? = null,
)

@Serializable
data class AniyomiBackupCategory(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val order: Long = 0,
    @ProtoNumber(3) val id: Long = 0,
    @ProtoNumber(100) val flags: Long = 0,
)

@Serializable
data class AniyomiBackupAnimeTracking(
    @ProtoNumber(1) val syncId: Int = 0,
    @ProtoNumber(2) val libraryId: Long = 0,
    @ProtoNumber(3) val mediaIdInt: Int = 0,
    @ProtoNumber(4) val trackingUrl: String = "",
    @ProtoNumber(5) val title: String = "",
    @ProtoNumber(6) val lastEpisodeSeen: Float = 0F,
    @ProtoNumber(7) val totalEpisodes: Int = 0,
    @ProtoNumber(8) val score: Float = 0F,
    @ProtoNumber(9) val status: Int = 0,
    @ProtoNumber(10) val startedWatchingDate: Long = 0,
    @ProtoNumber(11) val finishedWatchingDate: Long = 0,
    @ProtoNumber(12) val private: Boolean = false,
    @ProtoNumber(100) val mediaId: Long = 0,
)

@Serializable
data class AniyomiBackupAnimeHistory(
    @ProtoNumber(1) val url: String = "",
    @ProtoNumber(2) val lastRead: Long = 0,
    @ProtoNumber(3) val readDuration: Long = 0,
)

@Serializable
data class AniyomiBackupAnimeSource(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val sourceId: Long = 0,
)
