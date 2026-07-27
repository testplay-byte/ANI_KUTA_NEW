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
 * Export reads [WatchProgressStore.getAll] → `Map<String, Progress>`. Import
 * merges by key: for each entry, if a local entry exists with a newer
 * `updatedAt`, keep the local one; otherwise overwrite with the backup entry.
 *
 * The key format is `"$anilistId:$episodeUrl"` — stable across devices and
 * sessions. See [HistoryViewModel] for the backup contract documentation.
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
                } else {
                    val (anilistId, episodeUrl) = parseKey(key)
                    if (anilistId > 0 && episodeUrl.isNotEmpty()) {
                        watchProgressStore.save(
                            anilistId = anilistId,
                            episodeUrl = episodeUrl,
                            positionSeconds = item.positionSeconds,
                            durationSeconds = item.durationSeconds,
                            title = item.title,
                            coverUrl = item.coverUrl,
                            animeTitle = item.animeTitle,
                            episodeNumber = item.episodeNumber,
                            thumbnailUrl = item.thumbnailUrl,
                        )
                        imported++
                    } else {
                        skipped++
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "WatchProgress import: skipped key '$key' — ${e.message}")
                skipped++
            }
        }
        Log.i(TAG, "WatchProgress import: $imported imported, $skipped skipped")
        imported > 0
    }

    /** Parses "anilistId:episodeUrl" → (anilistId, episodeUrl). Splits on FIRST colon. */
    private fun parseKey(key: String): Pair<Int, String> {
        val idx = key.indexOf(':')
        if (idx < 0) return 0 to key
        val idPart = key.substring(0, idx)
        val urlPart = key.substring(idx + 1)
        return (idPart.toIntOrNull() ?: 0) to urlPart
    }
}
