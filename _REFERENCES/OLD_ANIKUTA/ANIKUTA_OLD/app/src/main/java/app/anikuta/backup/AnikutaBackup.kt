package app.anikuta.backup

import kotlinx.serialization.Serializable

/**
 * ANI-KUTA backup format (our own format, .anikuta extension).
 *
 * JSON-based, versioned, human-readable. Contains all user data:
 *  - Library (saved anime)
 *  - History (watch progress)
 *  - Recent searches
 *  - Categories
 *  - Settings (preference key→value map)
 *  - Release tracking state
 *  - Sub/dub cache
 *  - Extension links
 *  - Playback state (server/quality memory)
 *
 * Version history:
 *  - 1: Initial format (2026-07-16)
 *
 * Future-proof: adding new fields with defaults is backward-compatible.
 * Removing or renaming fields requires a version bump + migration logic.
 */
@Serializable
data class AnikutaBackup(
    val version: Int = CURRENT_VERSION,
    val createdAt: Long = System.currentTimeMillis(),
    val appVersion: String = "0.1.0",
    val library: List<BackupLibraryAnime> = emptyList(),
    val history: List<BackupHistoryEntry> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val categories: BackupCategories = BackupCategories(),
    val settings: Map<String, String> = emptyMap(),
    val releaseTracking: List<BackupTrackedAnime> = emptyList(),
    val subDubCache: Map<String, BackupSubDubInfo> = emptyMap(),
    val extensionLinks: Map<String, Int> = emptyMap(),
    val playbackStates: Map<String, BackupPlaybackState> = emptyMap(),
) {
    companion object {
        const val CURRENT_VERSION = 1
        /** Magic header for format detection (first 8 bytes of the file). */
        const val MAGIC = "ANIKUTA1"
    }
}

// ---- Library ----

@Serializable
data class BackupLibraryAnime(
    val id: Int,
    val titleRomaji: String? = null,
    val titleEnglish: String? = null,
    val titleNative: String? = null,
    val coverLarge: String? = null,
    val coverMedium: String? = null,
    val coverColor: String? = null,
    val bannerImage: String? = null,
    val description: String? = null,
    val averageScore: Int? = null,
    val episodes: Int? = null,
    val genres: List<String>? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val nextAiringEpisode: BackupNextAiring? = null,
    val idMal: Int? = null,
)

@Serializable
data class BackupNextAiring(
    val airingAt: Int? = null,
    val episode: Int? = null,
    val timeUntilAiring: Int? = null,
)

// ---- History ----

@Serializable
data class BackupHistoryEntry(
    val key: String,  // "$anilistId:$episodeUrl"
    val positionSeconds: Int,
    val durationSeconds: Int,
    val title: String,
    val updatedAt: Long,
    val coverUrl: String? = null,
    val animeTitle: String? = null,
    val episodeNumber: Float = -1f,
    val thumbnailUrl: String? = null,
)

// ---- Categories ----

@Serializable
data class BackupCategories(
    val categories: List<BackupCategory> = emptyList(),
    val assignments: Map<String, List<Long>> = emptyMap(),  // anilistId → categoryIds
)

@Serializable
data class BackupCategory(
    val id: Long,
    val name: String,
    val order: Int,
)

// ---- Release tracking ----

@Serializable
data class BackupTrackedAnime(
    val anilistId: Int,
    val title: String,
    val coverUrl: String? = null,
    val sourceId: Long? = null,
    val animeUrl: String? = null,
    val lastKnownEpisodeCount: Int = 0,
    val lastKnownSubCount: Int = 0,
    val lastKnownDubCount: Int = 0,
    val lastCheckTime: Long = 0L,
    val lastSeenAiringAt: Long = 0L,
    val nextScheduledCheck: Long = 0L,
    val isCompleted: Boolean = false,
    val hasPendingDub: Boolean = false,
    val subReleaseOffsetMs: Long = 0L,
    val dubReleaseOffsetMs: Long = 0L,
    val subOffsetSampleCount: Int = 0,
    val dubOffsetSampleCount: Int = 0,
    val notifyOnNew: Boolean? = null,
    val notifySub: Boolean? = null,
    val notifyDub: Boolean? = null,
    val autoDownloadNew: Boolean? = null,
    val autoDownloadSub: Boolean? = null,
    val autoDownloadDub: Boolean? = null,
    val autoDownloadQuality: String? = null,
    val autoDownloadAudio: String? = null,
)

// ---- Sub/Dub cache ----

@Serializable
data class BackupSubDubInfo(
    val hasSub: Boolean = false,
    val hasDub: Boolean = false,
    val subCount: Int = 0,
    val dubCount: Int = 0,
    val totalEpisodes: Int = 0,
    val lastUpdated: Long = 0L,
)

// ---- Playback state (server/quality memory for resume) ----

@Serializable
data class BackupPlaybackState(
    val videoUrl: String,
    val videoServer: String = "",
    val videoAudio: String = "",
    val videoQuality: Int = -1,
    val videoHeaders: String = "",
    val audioTrackId: Int = -1,
    val subtitleTrackId: Int = -1,
    val sourceId: Long = -1L,
    val updatedAt: Long = 0L,
)
