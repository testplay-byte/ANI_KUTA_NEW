package app.confused.anikuta.core.backup.provider

import android.util.Log
import app.confused.anikuta.core.backup.BackupCategory
import app.confused.anikuta.core.backup.BackupEntry
import app.confused.anikuta.core.backup.BackupProvider
import app.confused.anikuta.core.backup.model.WatchProgressBackup
import app.confused.anikuta.core.backup.model.WatchProgressItem
import app.confused.anikuta.core.player.WatchProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AnikutaBackup"

/**
 * Backs up watch progress (playback positions per episode).
 *
 * Export reads [WatchProgressStore.getAll] → `Map<String, Progress>` and
 * preserves the keys verbatim. Import merges by key: for each entry, if a
 * local entry exists with a newer `updatedAt`, keep the local one; otherwise
 * overwrite with the backup entry.
 *
 * # Phase 3 (ADR-050) — content_id keys
 *
 * The store now keys progress by `"$contentId|$episodeNumber"` (was
 * `"$anilistId:$episodeUrl"`). The export preserves keys verbatim, so new
 * backups use the new format. The import is robust to BOTH formats:
 *
 * 1. **New format (preferred):** `item.contentId` is non-null (Phase 3 field) →
 *    use it directly with `item.episodeNumber`.
 * 2. **New format, no contentId field (rare):** parse the key via
 *    [WatchProgressStore.parseKey] → `(contentId, episodeNumber)`.
 * 3. **Legacy format (pre-Phase-3 backups):** the key is `"$anilistId:$episodeUrl"`.
 *    `parseKey` returns null → fall back to the legacy parsing. Convert
 *    `(anilistId, episodeNumber)` into `("al:$anilistId", episodeNumber)`.
 *    Entries with `anilistId == 0` (the degenerate unlinked-anime case) are
 *    skipped — same behavior as the old code.
 *
 * No backup schema bump is required: the import path handles both key formats
 * transparently, and the [WatchProgressItem.contentId] field is nullable with
 * a default, so old backups deserialize cleanly.
 */
class WatchProgressBackupProvider(
    private val watchProgressStore: WatchProgressStore,
) : BackupProvider {

    override val id: String = BackupCategory.WATCH_PROGRESS.id

    override suspend fun export(): BackupEntry = withContext(Dispatchers.IO) {
        try {
            val all = watchProgressStore.getAll()
            val items = all.mapValues { (_, progress) ->
                WatchProgressItem(
                    positionSeconds = progress.positionSeconds,
                    durationSeconds = progress.durationSeconds,
                    title = progress.title,
                    updatedAt = progress.updatedAt,
                    coverUrl = progress.coverUrl,
                    animeTitle = progress.animeTitle,
                    episodeNumber = progress.episodeNumber,
                    thumbnailUrl = progress.thumbnailUrl,
                    // Phase 3: serialize the content_id explicitly so future imports
                    // don't depend on key parsing.
                    contentId = progress.contentId,
                )
            }
            Log.i(TAG, "WatchProgress export: ${items.size} entries")
            BackupEntry.WatchProgress(progress = WatchProgressBackup(entries = items))
        } catch (e: Exception) {
            Log.e(TAG, "WatchProgress export failed", e)
            BackupEntry.WatchProgress()
        }
    }

    override suspend fun import(entry: BackupEntry): Boolean = withContext(Dispatchers.IO) {
        require(entry is BackupEntry.WatchProgress) { "Expected WatchProgress entry, got ${entry.providerId}" }
        if (entry.progress.entries.isEmpty()) return@withContext false

        val existing = watchProgressStore.getAll()
        var imported = 0
        var skipped = 0

        entry.progress.entries.forEach { (key, item) ->
            try {
                val local = existing[key]
                if (local != null && local.updatedAt >= item.updatedAt) {
                    // Local entry is newer — skip (don't overwrite with older backup data)
                    skipped++
                    return@forEach
                }
                val resolved = resolveContentIdAndEpisode(key, item)
                if (resolved != null) {
                    val (contentId, episodeNumber) = resolved
                    watchProgressStore.save(
                        contentId = contentId,
                        episodeNumber = episodeNumber,
                        positionSeconds = item.positionSeconds,
                        durationSeconds = item.durationSeconds,
                        title = item.title,
                        coverUrl = item.coverUrl,
                        animeTitle = item.animeTitle,
                        thumbnailUrl = item.thumbnailUrl,
                    )
                    imported++
                } else {
                    skipped++
                }
            } catch (e: Exception) {
                Log.w(TAG, "WatchProgress import: skipped key '$key' — ${e.message}")
                skipped++
            }
        }
        Log.i(TAG, "WatchProgress import: $imported imported, $skipped skipped")
        imported > 0
    }

    /**
     * Resolves a backup entry to `(contentId, episodeNumber)` for the new store API.
     * Returns null for entries that can't be migrated (e.g., legacy `anilistId == 0`).
     *
     * Resolution order:
     * 1. [WatchProgressItem.contentId] (Phase 3 field) — preferred when present.
     * 2. [WatchProgressStore.parseKey] (new key format `"$contentId|$episodeNumber"`).
     * 3. Legacy key format `"$anilistId:$episodeUrl"` — convert anilistId →
     *    `"al:$anilistId"` (skip if anilistId <= 0).
     *
     * The episode number comes from [WatchProgressItem.episodeNumber] for paths 1 + 3,
     * and from the key for path 2 (the new key format encodes it).
     */
    private fun resolveContentIdAndEpisode(
        key: String,
        item: WatchProgressItem,
    ): Pair<String, Float>? {
        // Path 1: explicit contentId on the item (Phase 3 backups).
        if (!item.contentId.isNullOrBlank() && item.episodeNumber >= 0f) {
            return item.contentId to item.episodeNumber
        }

        // Path 2: new key format "$contentId|$episodeNumber".
        val parsed = watchProgressStore.parseKey(key)
        if (parsed != null) {
            val (contentId, episodeNumber) = parsed
            // Prefer the item's episodeNumber if it's more precise (>= 0); else use the key's.
            val ep = if (item.episodeNumber >= 0f) item.episodeNumber else episodeNumber
            return contentId to ep
        }

        // Path 3: legacy key format "$anilistId:$episodeUrl".
        val idx = key.indexOf(':')
        if (idx < 0) return null
        val anilistId = key.substring(0, idx).toIntOrNull() ?: return null
        if (anilistId <= 0) return null // Skip degenerate "0:<url>" entries (Doc 01 §6.3).
        if (item.episodeNumber < 0f) return null // No episode number to key on.
        return "al:$anilistId" to item.episodeNumber
    }
}
