package app.confused.anikuta.core.player.migration

import android.util.Log
import app.confused.anikuta.core.common.model.ContentIdGenerator
import app.confused.anikuta.core.common.model.ContentIdPriority
import app.confused.anikuta.core.common.model.LocalIdGenerator
import app.confused.anikuta.core.common.model.MetadataProviderId
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.common.repository.EpisodeRepository
import app.confused.anikuta.core.preferences.ContentIdPreferences
import app.confused.anikuta.core.player.PlaybackStateStore
import app.confused.anikuta.core.player.WatchProgressStore

/**
 * Migrates WatchProgressStore + PlaybackStateStore from the legacy key format
 * (`"$anilistId:$episodeUrl"`) to the new content_id-based format
 * (`"$contentId|$episodeNumber"`).
 *
 * # What it does
 *
 * For each legacy entry:
 * 1. Parse `anilistId` + `episodeUrl` from the old key.
 * 2. If `anilistId == 0` (the polluted unlinked-anime entries — Doc 01 §6.3),
 *    DROP it (these were already unopenable history rows).
 * 3. Resolve the anime by `anilistId` → get its `content_id`.
 * 4. Resolve the episode by `animeId + url` → get its `episodeNumber`.
 * 5. Re-key to `"$contentId|$episodeNumber"`.
 *
 * Entries that can't be resolved (anime not in library, episode not in DB) are
 * DROPPED with a warning log — they're stale (the anime was removed from the
 * library, or the source switched + the old episodeUrl no longer matches).
 *
 * # When it runs
 *
 * On first launch post-Phase-3-update, gated by `pref_watch_progress_migration_v1_done`.
 * The migration is idempotent (re-running it on already-migrated entries is a no-op
 * because the new keys use `|`, not `:`).
 *
 * # Crash resistance
 *
 * The whole migration is wrapped in a try/catch per entry. If one entry fails,
 * the others still migrate. The old map is only overwritten after the new map
 * is complete (atomic `replaceAll`).
 *
 * Per `_ARCHITECTURE_PLAN/proposals/05_migration_strategy.md` §3 (Step 3).
 */
class WatchProgressMigrator(
    private val watchProgressStore: WatchProgressStore,
    private val playbackStateStore: PlaybackStateStore,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val contentIdPreferences: ContentIdPreferences,
) {

    /**
     * Run the migration.
     *
     * @return The result counts (how many entries were migrated, dropped, skipped).
     */
    suspend fun migrate(): Result {
        val priority = contentIdPreferences.getPriority()
        val watchResult = migrateProgressMap(priority)
        val playbackResult = migratePlaybackMap(priority)
        return Result(
            watchProgressMigrated = watchResult.migrated,
            watchProgressDropped = watchResult.dropped,
            playbackStateMigrated = playbackResult.migrated,
            playbackStateDropped = playbackResult.dropped,
        )
    }

    /**
     * Migrate the WatchProgressStore.
     * Old key: `"$anilistId:$episodeUrl"`. New key: `"$contentId|$episodeNumber"`.
     */
    private suspend fun migrateProgressMap(priority: ContentIdPriority): StoreResult {
        val oldMap = watchProgressStore.getAll()
        if (oldMap.isEmpty()) return StoreResult(0, 0)

        var migrated = 0
        var dropped = 0
        val newMap = mutableMapOf<String, WatchProgressStore.Progress>()

        for ((oldKey, progress) in oldMap) {
            val newKey = migrateKey(oldKey, priority, tag = "WatchProgress")
            if (newKey != null) {
                newMap[newKey] = progress.copy(contentId = newKey.substringBeforeLast('|'))
                migrated++
            } else {
                dropped++
            }
        }

        watchProgressStore.replaceAll(newMap)
        Log.i(TAG, "WatchProgress: migrated=$migrated, dropped=$dropped, total=${oldMap.size}")
        return StoreResult(migrated, dropped)
    }

    /**
     * Migrate the PlaybackStateStore.
     * Same key format change as WatchProgress.
     */
    private suspend fun migratePlaybackMap(priority: ContentIdPriority): StoreResult {
        val oldMap = playbackStateStore.getAll()
        if (oldMap.isEmpty()) return StoreResult(0, 0)

        var migrated = 0
        var dropped = 0
        val newMap = mutableMapOf<String, PlaybackStateStore.PlaybackState>()

        for ((oldKey, state) in oldMap) {
            val newKey = migrateKey(oldKey, priority, tag = "PlaybackState")
            if (newKey != null) {
                newMap[newKey] = state
                migrated++
            } else {
                dropped++
            }
        }

        playbackStateStore.replaceAll(newMap)
        Log.i(TAG, "PlaybackState: migrated=$migrated, dropped=$dropped, total=${oldMap.size}")
        return StoreResult(migrated, dropped)
    }

    /**
     * Migrate a single key from the old format to the new format.
     *
     * @return the new key, or null if the entry should be dropped.
     */
    private suspend fun migrateKey(
        oldKey: String,
        priority: ContentIdPriority,
        tag: String,
    ): String? {
        // Skip already-migrated entries (new format uses '|').
        if ('|' in oldKey) return oldKey

        // Parse the old key: "$anilistId:$episodeUrl"
        val colonIdx = oldKey.indexOf(':')
        if (colonIdx < 0) {
            Log.w(TAG, "$tag: dropping malformed key (no ':'): $oldKey")
            return null
        }
        val anilistIdStr = oldKey.substring(0, colonIdx)
        val episodeUrl = oldKey.substring(colonIdx + 1)
        val anilistId = anilistIdStr.toIntOrNull()

        if (anilistId == null || anilistId <= 0) {
            // Polluted "0:<url>" entries (unlinked anime) — already unopenable.
            Log.d(TAG, "$tag: dropping anilistId=$anilistId entry (unlinked/invalid)")
            return null
        }

        return try {
            // Resolve the anime by anilistId → get content_id.
            val anime = animeRepository.getByAnilistId(anilistId)
            if (anime == null) {
                Log.w(TAG, "$tag: dropping entry for anilistId=$anilistId (anime not in library)")
                return null
            }

            val contentId = anime.contentId?.value
                ?: ContentIdGenerator.generate(
                    anilistId = anime.anilistId,
                    localId = anime.localId ?: LocalIdGenerator.forProvider(
                        MetadataProviderId.ANILIST,
                        anilistId.toString(),
                    ),
                    priority = priority,
                ).value

            // Resolve the episode by animeId + url → get episodeNumber.
            val episodes = episodeRepository.getByAnimeId(anime.id)
            val episode = episodes.firstOrNull { it.url == episodeUrl }
            if (episode == null) {
                Log.w(TAG, "$tag: dropping entry for anilistId=$anilistId url=$episodeUrl " +
                    "(episode not found — source may have switched)")
                return null
            }

            "$contentId|${"%.3f".format(episode.episodeNumber)}"
        } catch (e: Exception) {
            Log.e(TAG, "$tag: failed to migrate key=$oldKey", e)
            null
        }
    }

    private data class StoreResult(val migrated: Int, val dropped: Int)

    /** Aggregate result of the full migration (both stores). */
    data class Result(
        val watchProgressMigrated: Int,
        val watchProgressDropped: Int,
        val playbackStateMigrated: Int,
        val playbackStateDropped: Int,
    )

    private companion object {
        private const val TAG = "AnikutaProgressMigrator"
    }
}
