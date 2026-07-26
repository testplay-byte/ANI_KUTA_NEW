package app.confused.anikuta.core.backup.model

import kotlinx.serialization.Serializable

/**
 * Serializable wrapper for the watch-progress store data.
 *
 * Mirrors `WatchProgressStore.getAll()` → `Map<String, Progress>`.
 * The key is `"$anilistId:$episodeUrl"` (stable across sessions — preserved
 * verbatim in the backup). The value is a [WatchProgressItem] mirroring
 * `WatchProgressStore.Progress` (which is already `@Serializable` — we copy
 * the fields here to keep the backup schema independent of the internal model).
 */
@Serializable
data class WatchProgressBackup(
    /** Key: "$anilistId:$episodeUrl". Value: playback position + metadata. */
    val entries: Map<String, WatchProgressItem> = emptyMap(),
)

/**
 * One episode's watch progress (mirrors WatchProgressStore.Progress).
 */
@Serializable
data class WatchProgressItem(
    val positionSeconds: Int,
    val durationSeconds: Int,
    val title: String,
    val updatedAt: Long,
    val coverUrl: String? = null,
    val animeTitle: String? = null,
    val episodeNumber: Float = -1f,
    val thumbnailUrl: String? = null,
)
