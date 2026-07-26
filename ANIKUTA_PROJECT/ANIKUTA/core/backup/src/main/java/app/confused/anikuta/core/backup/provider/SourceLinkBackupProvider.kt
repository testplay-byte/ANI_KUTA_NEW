package app.confused.anikuta.core.backup.provider

import android.util.Log
import app.confused.anikuta.core.backup.BackupCategory
import app.confused.anikuta.core.backup.BackupEntry
import app.confused.anikuta.core.backup.BackupProvider
import app.confused.anikuta.core.backup.model.SourceLinkBackup
import app.confused.anikuta.core.backup.model.SourceLinkItem
import app.confused.anikuta.data.extension.cache.ExtensionLinkStore
import app.confused.anikuta.data.extension.cache.SourceLinkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AnikutaBackup"

/**
 * Backs up AniList↔extension source links.
 *
 * Combines two stores:
 * - [SourceLinkStore] — AniList ID → extension source match (sourceId, animeUrl, animeTitle)
 * - [ExtensionLinkStore] — extension anime (sourceId:animeUrl) → AniList ID
 *
 * On import, both stores are populated by iterating the backup entries and
 * calling `saveLink` / `link`. Existing links are overwritten (latest wins).
 */
class SourceLinkBackupProvider(
    private val sourceLinkStore: SourceLinkStore,
    private val extensionLinkStore: ExtensionLinkStore,
) : BackupProvider {

    override val id: String = BackupCategory.SOURCE_LINKS.id

    override suspend fun export(): BackupEntry = withContext(Dispatchers.IO) {
        try {
            val sourceLinks = sourceLinkStore.getAll().mapValues { (_, link) ->
                SourceLinkItem(
                    sourceId = link.sourceId,
                    animeUrl = link.animeUrl,
                    animeTitle = link.animeTitle,
                )
            }
            val extensionLinks = extensionLinkStore.getAll()
            Log.i(TAG, "SourceLinks export: ${sourceLinks.size} source links, ${extensionLinks.size} extension links")
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
        // Restore source links (AniList ID → extension source)
        links.sourceLinks.forEach { (anilistIdStr, link) ->
            try {
                val anilistId = anilistIdStr.toIntOrNull()
                if (anilistId != null) {
                    sourceLinkStore.saveLink(
                        anilistId = anilistId,
                        sourceId = link.sourceId,
                        animeUrl = link.animeUrl,
                        animeTitle = link.animeTitle,
                    )
                    imported++
                }
            } catch (e: Exception) {
                Log.w(TAG, "SourceLinks import: failed for anilistId=$anilistIdStr — ${e.message}")
            }
        }
        // Restore extension links (sourceId:animeUrl → AniList ID)
        links.extensionLinks.forEach { (key, anilistId) ->
            try {
                val colonIdx = key.indexOf(':')
                if (colonIdx > 0) {
                    val sourceId = key.substring(0, colonIdx).toLongOrNull()
                    val animeUrl = key.substring(colonIdx + 1)
                    if (sourceId != null) {
                        extensionLinkStore.link(sourceId, animeUrl, anilistId)
                        imported++
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "SourceLinks import: failed for key='$key' — ${e.message}")
            }
        }
        Log.i(TAG, "SourceLinks import: $imported links restored")
        imported > 0
    }
}
