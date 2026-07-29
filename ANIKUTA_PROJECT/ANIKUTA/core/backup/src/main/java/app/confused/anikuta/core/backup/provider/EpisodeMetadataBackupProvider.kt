package app.confused.anikuta.core.backup.provider

import android.util.Log
import app.confused.anikuta.core.backup.BackupCategory
import app.confused.anikuta.core.backup.BackupEntry
import app.confused.anikuta.core.backup.BackupProvider
import app.confused.anikuta.core.backup.model.EpisodeMetadataItem
import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata
import app.confused.anikuta.core.episodemetadata.repository.EpisodeMetadataCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private const val TAG = "AnikutaBackup"

/**
 * Backs up enriched episode metadata (titles, descriptions, thumbnails, air
 * dates from Jikan/MAL/AniList sources).
 *
 * Export reads [EpisodeMetadataCache.getAll] — `Map<String, String>` where the
 * outer key is the content_id (Phase 4, ADR-050) and the inner value is a JSON
 * string of `Map<Int, EpisodeMetadata>`. We parse each JSON string and convert
 * to `Map<String, EpisodeMetadataItem>` for serialization.
 *
 * Import overwrites the cache for each content. This is optional (default off)
 * because metadata can be re-fetched from sources.
 *
 * # Phase 4 — content_id keys
 *
 * Outer key on the backup map is now content_id (e.g., `"al:154587"`).
 * Pre-Phase-4 backups used anilistId.toString() as the key — the import path
 * detects + converts those to `"al:$anilistId"` content_ids.
 *
 * The `EpisodeMetadata.animeId` field is best-effort reconstructed from the
 * `"al:$int"` content_id pattern; for non-AniList content_ids (e.g.,
 * `"aniyomi:123:url"`), `animeId = 0` (the field is non-nullable in the domain
 * model — kept for legacy callers; Phase 4+ code should prefer contentId).
 */
class EpisodeMetadataBackupProvider(
    private val metadataCache: EpisodeMetadataCache,
) : BackupProvider {

    override val id: String = BackupCategory.EPISODE_METADATA.id

    private val json = Json { ignoreUnknownKeys = true }
    private val metadataSerializer = MapSerializer(
        Int.serializer(),
        EpisodeMetadata.serializer(),
    )

    override suspend fun export(): BackupEntry = withContext(Dispatchers.IO) {
        try {
            val rawCache = metadataCache.getAll()
            val byAnime = mutableMapOf<String, Map<String, EpisodeMetadataItem>>()
            rawCache.forEach { (contentIdStr, jsonStr) ->
                try {
                    val metadataMap = json.decodeFromString(metadataSerializer, jsonStr)
                    val items = metadataMap.map { (epNum, meta) ->
                        epNum.toString() to EpisodeMetadataItem(
                            episodeNumber = epNum,
                            title = meta.title,
                            description = meta.description,
                            thumbnailUrl = meta.thumbnailUrl,
                            airDate = meta.airDate,
                            filler = meta.filler,
                            lastFetched = meta.lastFetched,
                        )
                    }.toMap()
                    byAnime[contentIdStr] = items
                } catch (e: Exception) {
                    Log.w(TAG, "EpisodeMetadata export: failed to parse cache for contentId=$contentIdStr — ${e.message}")
                }
            }
            Log.i(TAG, "EpisodeMetadata export: ${byAnime.size} anime with metadata (Phase 4 content_id keys)")
            BackupEntry.EpisodeMetadata(byAnime = byAnime)
        } catch (e: Exception) {
            Log.e(TAG, "EpisodeMetadata export failed", e)
            BackupEntry.EpisodeMetadata()
        }
    }

    override suspend fun import(entry: BackupEntry): Boolean = withContext(Dispatchers.IO) {
        require(entry is BackupEntry.EpisodeMetadata) { "Expected EpisodeMetadata entry, got ${entry.providerId}" }
        if (entry.byAnime.isEmpty()) return@withContext false
        var imported = 0
        entry.byAnime.forEach { (keyStr, episodes) ->
            try {
                // Phase 4: resolve the content_id from the backup key.
                // New format: key IS the content_id (contains ':'). Legacy: anilistId-as-string.
                val contentId = resolveContentId(keyStr) ?: run {
                    Log.w(TAG, "EpisodeMetadata import: cannot resolve content_id from key='$keyStr' — skipping")
                    return@forEach
                }
                if (episodes.isEmpty()) return@forEach
                // Best-effort: extract anilistId from "al:$int" content_id for the EpisodeMetadata.animeId field.
                val animeId = extractAnilistId(contentId) ?: 0
                val metadataMap = mutableMapOf<Int, EpisodeMetadata>()
                episodes.forEach { (epNumStr, item) ->
                    val epNum = epNumStr.toIntOrNull() ?: return@forEach
                    metadataMap[epNum] = EpisodeMetadata(
                        animeId = animeId,
                        episodeNumber = epNum,
                        title = item.title,
                        description = item.description,
                        thumbnailUrl = item.thumbnailUrl,
                        airDate = item.airDate,
                        filler = item.filler,
                        lastFetched = item.lastFetched,
                    )
                }
                if (metadataMap.isNotEmpty()) {
                    metadataCache.save(contentId, metadataMap)
                    imported++
                }
            } catch (e: Exception) {
                Log.w(TAG, "EpisodeMetadata import: failed for key='$keyStr' — ${e.message}")
            }
        }
        Log.i(TAG, "EpisodeMetadata import: $imported anime restored (Phase 4 content_id keys)")
        imported > 0
    }

    /**
     * Resolves a backup key into a content_id.
     *
     * - New format (Phase 4+): key IS the content_id (contains `:`). Use as-is.
     * - Legacy format (pre-Phase-4): key is anilistId.toString() (parses as
     *   Int). Convert to `"al:$anilistId"`.
     * - Else: return null (can't resolve — skip the entry).
     */
    private fun resolveContentId(keyStr: String): String? {
        if (keyStr.contains(':')) return keyStr
        val anilistId = keyStr.toIntOrNull() ?: return null
        return "al:$anilistId"
    }

    /** Extracts the anilistId from an `"al:$int"` content_id, or null if not AniList-linked. */
    private fun extractAnilistId(contentId: String): Int? {
        if (!contentId.startsWith("al:")) return null
        return contentId.removePrefix("al:").toIntOrNull()
    }
}
