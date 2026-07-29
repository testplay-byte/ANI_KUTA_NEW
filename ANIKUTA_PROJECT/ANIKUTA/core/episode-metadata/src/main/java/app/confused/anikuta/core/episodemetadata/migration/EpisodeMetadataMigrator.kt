package app.confused.anikuta.core.episodemetadata.migration

import android.util.Log
import app.confused.anikuta.core.common.model.ContentIdGenerator
import app.confused.anikuta.core.common.model.ContentIdPriority
import app.confused.anikuta.core.common.model.LocalIdGenerator
import app.confused.anikuta.core.common.model.MetadataProviderId
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.episodemetadata.repository.EpisodeMetadataCache
import app.confused.anikuta.core.preferences.ContentIdPreferences

/**
 * Migrates [EpisodeMetadataCache] from the legacy key format
 * (`anilistId.toString()`) to the new content_id-based format
 * (e.g., `"al:154587"`).
 *
 * # What it does
 *
 * For each legacy entry (key = anilistId as String):
 * 1. Parse the anilistId from the key.
 * 2. If the key is already a content_id (contains `:`), skip it (already migrated).
 * 3. Resolve the anime by anilistId → get its content_id.
 * 4. Re-key to the content_id.
 *
 * Entries that can't be resolved (anime not in library, invalid anilistId) are
 * DROPPED with a warning log — they're stale.
 *
 * # When it runs
 *
 * On first launch post-Phase-4-update, gated by a preference. Idempotent —
 * re-running on already-migrated entries is a no-op (content_ids contain `:`,
 * legacy anilistId keys don't).
 *
 * # Crash resistance
 *
 * Per-entry try/catch. The old map is only overwritten after the new map is
 * complete (atomic `replaceAll`).
 *
 * Per `_ARCHITECTURE_PLAN/proposals/05_migration_strategy.md` §3 (Step 6).
 */
class EpisodeMetadataMigrator(
    private val metadataCache: EpisodeMetadataCache,
    private val animeRepository: AnimeRepository,
    private val contentIdPreferences: ContentIdPreferences,
) {

    /**
     * Run the migration.
     *
     * @return The counts (migrated, dropped, already-migrated).
     */
    suspend fun migrate(): Result {
        val priority = contentIdPreferences.getPriority()
        val oldMap = metadataCache.getAll()
        if (oldMap.isEmpty()) {
            Log.i(TAG, "No metadata entries to migrate")
            return Result(0, 0, 0)
        }

        var migrated = 0
        var dropped = 0
        var alreadyMigrated = 0
        val newMap = mutableMapOf<String, String>()

        for ((oldKey, jsonValue) in oldMap) {
            // Skip already-migrated entries (content_ids contain ':').
            if (':' in oldKey) {
                newMap[oldKey] = jsonValue
                alreadyMigrated++
                continue
            }

            try {
                val anilistId = oldKey.toIntOrNull()
                if (anilistId == null || anilistId <= 0) {
                    Log.w(TAG, "Dropping malformed key (not an anilistId): $oldKey")
                    dropped++
                    continue
                }

                val anime = animeRepository.getByAnilistId(anilistId)
                if (anime == null) {
                    Log.w(TAG, "Dropping entry for anilistId=$anilistId (anime not in library)")
                    dropped++
                    continue
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

                newMap[contentId] = jsonValue
                migrated++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate key=$oldKey", e)
                dropped++
            }
        }

        metadataCache.replaceAll(newMap)
        Log.i(TAG, "Migration complete: migrated=$migrated, dropped=$dropped, " +
            "alreadyMigrated=$alreadyMigrated, total=${oldMap.size}")
        return Result(migrated, dropped, alreadyMigrated)
    }

    /** Result of the migration. */
    data class Result(
        val migrated: Int,
        val dropped: Int,
        val alreadyMigrated: Int,
    )

    private companion object {
        private const val TAG = "AnikutaEpMetaMigrator"
    }
}
