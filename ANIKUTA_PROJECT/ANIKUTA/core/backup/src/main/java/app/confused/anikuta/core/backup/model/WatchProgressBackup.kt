package app.confused.anikuta.core.backup.model

import kotlinx.serialization.Serializable

/**
 * Serializable wrapper for the watch-progress store data.
 *
 * Mirrors `WatchProgressStore.getAll()` → `Map<String, Progress>`. The value is
 * a [WatchProgressItem] mirroring `WatchProgressStore.Progress` (which is
 * already `@Serializable` — we copy the fields here to keep the backup schema
 * independent of the internal model).
 *
 * # Key format (Phase 3, ADR-050)
 *
 * The map key is `"$contentId|$episodeNumber"` (e.g., `"al:154587|1.000"`).
 * This is the same format the [WatchProgressStore] uses internally — the
 * export preserves keys verbatim, the import parses them via
 * `WatchProgressStore.parseKey`.
 *
 * **Backward compat:** pre-Phase-3 backups used the key format
 * `"$anilistId:$episodeUrl"`. The import path detects this (parseKey returns
 * null) and falls back to the old parsing — converting `(anilistId, episodeNumber)`
 * into the new `("al:$anilistId", episodeNumber)` key. Entries with `anilistId == 0`
 * (the degenerate unlinked-anime case) are skipped. The [WatchProgressItem.contentId]
 * field (Phase 3) is preferred when present — it carries the identity explicitly,
 * independent of the key format.
 */
@Serializable
data class WatchProgressBackup(
    /** Key: "$contentId|$episodeNumber" (Phase 3) or "$anilistId:$episodeUrl" (legacy). */
    val entries: Map<String, WatchProgressItem> = emptyMap(),
)

/**
 * One episode's watch progress (mirrors WatchProgressStore.Progress).
 *
 * [contentId] is new in Phase 3 — when present, the import uses it directly
 * (rather than parsing the key). Nullable for backward compat with pre-Phase-3
 * backups (which only had the key).
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
    /** Phase 3: the content_id this progress belongs to. Nullable for legacy backups. */
    val contentId: String? = null,
)
