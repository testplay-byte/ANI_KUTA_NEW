package app.confused.anikuta.data.extension.migration

import android.util.Log
import app.confused.anikuta.core.common.model.ContentIdGenerator
import app.confused.anikuta.core.common.model.ContentIdPriority
import app.confused.anikuta.core.common.model.LocalIdGenerator
import app.confused.anikuta.core.common.model.MetadataProviderId
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.preferences.ContentIdPreferences
import app.confused.anikuta.data.extension.cache.ExtensionLinkStore
import app.confused.anikuta.data.extension.cache.SourceLinkStore

/**
 * Migrates [SourceLinkStore] + [ExtensionLinkStore] from the legacy formats to
 * the new content_id-based formats.
 *
 * # SourceLinkStore migration
 *
 * Old key: `anilistId.toString()` (e.g., `"154587"`).
 * New key: content_id (e.g., `"al:154587"`).
 *
 * For each legacy entry: parse anilistId → resolve anime → get content_id → re-key.
 *
 * # ExtensionLinkStore migration
 *
 * Old value: anilistId (Int).
 * New value: content_id (String, e.g., `"al:154587"`).
 *
 * The key (`"$sourceId:$animeUrl"`) stays the same. Only the value changes.
 *
 * # Idempotency
 *
 * - SourceLinkStore: legacy keys are pure integers (no `:`); content_ids contain `:`.
 *   Re-running on already-migrated entries is a no-op (skipped).
 * - ExtensionLinkStore: legacy values are integers; new values are strings with `:`.
 *   The store's deserializer auto-converts old Int values to `"al:$anilistId"` strings,
 *   so the migrator only needs to handle the SourceLinkStore key change.
 *
 * # Crash resistance
 *
 * Per-entry try/catch. Atomic `replaceAll` at the end.
 *
 * Per `_ARCHITECTURE_PLAN/proposals/05_migration_strategy.md` §3 (Step 7).
 */
class SourceLinkMigrator(
    private val sourceLinkStore: SourceLinkStore,
    private val extensionLinkStore: ExtensionLinkStore,
    private val animeRepository: AnimeRepository,
    private val contentIdPreferences: ContentIdPreferences,
) {

    /**
     * Run the migration.
     *
     * @return The counts for both stores.
     */
    suspend fun migrate(): Result {
        val priority = contentIdPreferences.getPriority()
        val sourceResult = migrateSourceLinks(priority)
        // ExtensionLinkStore values are auto-converted by the deserializer
        // (old Int → "al:$int" String). No explicit migration needed — but we
        // verify + log the state.
        val extensionCount = extensionLinkStore.getAll().size
        Log.i(TAG, "ExtensionLinkStore: $extensionCount entries (auto-converted by deserializer)")
        return Result(
            sourceLinksMigrated = sourceResult.migrated,
            sourceLinksDropped = sourceResult.dropped,
            sourceLinksAlreadyMigrated = sourceResult.alreadyMigrated,
            extensionLinksCount = extensionCount,
        )
    }

    private suspend fun migrateSourceLinks(priority: ContentIdPriority): StoreResult {
        val oldMap = sourceLinkStore.getAll()
        if (oldMap.isEmpty()) {
            Log.i(TAG, "SourceLinkStore: no entries to migrate")
            return StoreResult(0, 0, 0)
        }

        var migrated = 0
        var dropped = 0
        var alreadyMigrated = 0
        val newMap = mutableMapOf<String, SourceLinkStore.SourceLink>()

        for ((oldKey, link) in oldMap) {
            // Skip already-migrated entries (content_ids contain ':').
            if (':' in oldKey) {
                newMap[oldKey] = link
                alreadyMigrated++
                continue
            }

            try {
                val anilistId = oldKey.toIntOrNull()
                if (anilistId == null || anilistId <= 0) {
                    Log.w(TAG, "SourceLinkStore: dropping malformed key (not an anilistId): $oldKey")
                    dropped++
                    continue
                }

                val anime = animeRepository.getByAnilistId(anilistId)
                if (anime == null) {
                    Log.w(TAG, "SourceLinkStore: dropping entry for anilistId=$anilistId " +
                        "(anime not in library)")
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

                newMap[contentId] = link
                migrated++
            } catch (e: Exception) {
                Log.e(TAG, "SourceLinkStore: failed to migrate key=$oldKey", e)
                dropped++
            }
        }

        sourceLinkStore.replaceAll(newMap)
        Log.i(TAG, "SourceLinkStore: migrated=$migrated, dropped=$dropped, " +
            "alreadyMigrated=$alreadyMigrated, total=${oldMap.size}")
        return StoreResult(migrated, dropped, alreadyMigrated)
    }

    private data class StoreResult(
        val migrated: Int,
        val dropped: Int,
        val alreadyMigrated: Int,
    )

    /** Result of the full migration. */
    data class Result(
        val sourceLinksMigrated: Int,
        val sourceLinksDropped: Int,
        val sourceLinksAlreadyMigrated: Int,
        val extensionLinksCount: Int,
    )

    private companion object {
        private const val TAG = "AnikutaSourceLinkMigrator"
    }
}
