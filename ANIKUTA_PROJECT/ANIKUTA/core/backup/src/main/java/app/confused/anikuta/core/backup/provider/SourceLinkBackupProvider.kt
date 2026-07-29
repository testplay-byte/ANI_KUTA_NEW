package app.confused.anikuta.core.backup.provider

import android.util.Log
import app.confused.anikuta.core.backup.BackupCategory
import app.confused.anikuta.core.backup.BackupEntry
import app.confused.anikuta.core.backup.BackupProvider
import app.confused.anikuta.core.backup.model.SourceLinkBackup
import app.confused.anikuta.core.backup.model.SourceLinkItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AnikutaBackup"

/**
 * Backs up AniList↔extension source links.
 *
 * Combines two stores (accessed via the [SourceLinkBackupAccess] abstraction —
 * Phase 8 module-architecture fix):
 * - source links — content_id → extension source match (sourceId, animeUrl, animeTitle)
 * - extension links — extension anime (sourceId:animeUrl) → content_id
 *
 * On import, both stores are populated by iterating the backup entries and
 * calling [SourceLinkBackupAccess.saveSourceLink] / [saveExtensionLink].
 * Existing links are overwritten (latest wins).
 *
 * # Phase 4 (ADR-050) — content_id keys
 *
 * Both stores now key off content_id (e.g., `"al:154587"`) instead of anilistId
 * (Int). For backward-compat with pre-Phase-4 backups:
 * - `sourceLinks` keys that parse as Int (legacy anilistId) are converted to
 *   `"al:$anilistId"` content_ids on import.
 * - `extensionLinks` Int values (legacy anilistId) are converted to
 *   `"al:$anilistId"` content_ids on read by [TolerantContentIdMapSerializer].
 *
 * # Phase 8 — module boundary fix (Doc 04 violation 1)
 *
 * This provider used to import `SourceLinkStore` + `ExtensionLinkStore` directly
 * from `:data:extension`, which is a `:core` → `:data` inversion. It now
 * injects the [SourceLinkBackupAccess] interface (defined in `:core:backup`)
 * whose implementation lives in `:data:extension` + is Koin-bound there. The
 * `:core:backup` module no longer depends on `:data:extension`.
 */
class SourceLinkBackupProvider(
    private val access: SourceLinkBackupAccess,
) : BackupProvider {

    override val id: String = BackupCategory.SOURCE_LINKS.id

    override suspend fun export(): BackupEntry = withContext(Dispatchers.IO) {
        try {
            // source links: content_id → BackupSourceLink.
            val sourceLinks = access.getAllSourceLinks().mapValues { (_, link) ->
                SourceLinkItem(
                    sourceId = link.sourceId,
                    animeUrl = link.animeUrl,
                    animeTitle = link.animeTitle,
                )
            }
            // extension links: "$sourceId:$animeUrl" → content_id.
            val extensionLinks = access.getAllExtensionLinks()
            Log.i(TAG, "SourceLinks export: ${sourceLinks.size} source links, ${extensionLinks.size} extension links (Phase 4 content_id format)")
            BackupEntry.SourceLinks(links = SourceLinkBackup(
                sourceLinks = sourceLinks,
                extensionLinks = extensionLinks,
            ))
        } catch (e: Exception) {
            Log.e(TAG, "SourceLinks export failed", e)
            BackupEntry.SourceLinks()
        }
    }

    override suspend fun import(entry: BackupEntry): Boolean = withContext(Dispatchers.IO) {
        require(entry is BackupEntry.SourceLinks) { "Expected SourceLinks entry, got ${entry.providerId}" }
        val links = entry.links
        if (links.sourceLinks.isEmpty() && links.extensionLinks.isEmpty()) return@withContext false

        var imported = 0
        // Restore source links (content_id → extension source).
        // Handles both new (content_id keys like "al:154587") + legacy (anilistId-as-string keys like "154587") formats.
        links.sourceLinks.forEach { (keyStr, link) ->
            try {
                val contentId = resolveContentId(keyStr) ?: run {
                    Log.w(TAG, "SourceLinks import: cannot resolve content_id from key='$keyStr' — skipping")
                    return@forEach
                }
                access.saveSourceLink(
                    contentId = contentId,
                    sourceId = link.sourceId,
                    animeUrl = link.animeUrl,
                    animeTitle = link.animeTitle,
                )
                imported++
            } catch (e: Exception) {
                Log.w(TAG, "SourceLinks import: failed for key='$keyStr' — ${e.message}")
            }
        }
        // Restore extension links (sourceId:animeUrl → content_id).
        // The values are already content_id strings (TolerantContentIdMapSerializer auto-converts legacy Int values).
        links.extensionLinks.forEach { (key, contentId) ->
            try {
                val colonIdx = key.indexOf(':')
                if (colonIdx > 0) {
                    val sourceId = key.substring(0, colonIdx).toLongOrNull()
                    val animeUrl = key.substring(colonIdx + 1)
                    if (sourceId != null) {
                        access.saveExtensionLink(sourceId, animeUrl, contentId)
                        imported++
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "SourceLinks import: failed for key='$key' — ${e.message}")
            }
        }
        Log.i(TAG, "SourceLinks import: $imported links restored (Phase 4 content_id format)")
        imported > 0
    }

    /**
     * Resolves a backup key into a content_id.
     *
     * - New format (Phase 4+): key IS the content_id (e.g., `"al:154587"` —
     *   contains `:`). Use as-is.
     * - Legacy format (pre-Phase-4): key is anilistId.toString() (e.g.,
     *   `"154587"` — parses as Int). Convert to `"al:$anilistId"`.
     * - Else: return null (can't resolve — skip the entry).
     */
    private fun resolveContentId(keyStr: String): String? {
        // New content_id format contains a ':' (e.g., "al:154587", "aniyomi:123:url").
        if (keyStr.contains(':')) return keyStr
        // Legacy anilistId-as-string format.
        val anilistId = keyStr.toIntOrNull() ?: return null
        return "al:$anilistId"
    }
}
