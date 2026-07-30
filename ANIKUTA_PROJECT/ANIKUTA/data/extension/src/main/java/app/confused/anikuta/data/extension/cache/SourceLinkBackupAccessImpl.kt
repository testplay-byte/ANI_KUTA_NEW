package app.confused.anikuta.data.extension.cache

import android.util.Log
import app.confused.anikuta.core.backup.provider.BackupSourceLink
import app.confused.anikuta.core.backup.provider.SourceLinkBackupAccess

/**
 * Phase 8 — module-architecture fix (Doc 04 violation 1).
 *
 * `:core:backup` can't depend on `:data:extension` (core → data inversion).
 * This adapter implements the [SourceLinkBackupAccess] interface declared in
 * `:core:backup` by wrapping the concrete [SourceLinkStore] + [ExtensionLinkStore]
 * + exposing only the read/write surface the backup engine needs. The
 * `SourceLink` data-layer model is translated to the backup-specific
 * [BackupSourceLink] snapshot so the contract stays data-layer-agnostic.
 *
 * Registered in Koin (`app/.../di/ExtensionModule.kt`) as the
 * [SourceLinkBackupAccess] binding — the backup module injects the interface
 * and never touches the data-layer types directly.
 */
class SourceLinkBackupAccessImpl(
    private val sourceLinkStore: SourceLinkStore,
    private val extensionLinkStore: ExtensionLinkStore,
) : SourceLinkBackupAccess {

    override fun getAllSourceLinks(): Map<String, BackupSourceLink> =
        sourceLinkStore.getAll().mapValues { (_, link) ->
            BackupSourceLink(
                sourceId = link.sourceId,
                animeUrl = link.animeUrl,
                animeTitle = link.animeTitle,
            )
        }

    override fun saveSourceLink(contentId: String, sourceId: Long, animeUrl: String, animeTitle: String) {
        try {
            sourceLinkStore.saveLink(
                contentId = contentId,
                sourceId = sourceId,
                animeUrl = animeUrl,
                animeTitle = animeTitle,
            )
        } catch (e: Exception) {
            // Defensive: the underlying store writes to a SharedPreferences-backed
            // JSON map; failures are unexpected but we don't want to crash the
            // whole restore. The caller (SourceLinkBackupProvider.import) also
            // wraps each entry in its own try/catch — re-throw so it can log.
            Log.w(TAG, "saveSourceLink failed for contentId=$contentId — ${e.message}")
            throw e
        }
    }

    override fun getAllExtensionLinks(): Map<String, String> =
        extensionLinkStore.getAll()

    override fun saveExtensionLink(sourceId: Long, animeUrl: String, contentId: String) {
        try {
            extensionLinkStore.link(sourceId, animeUrl, contentId)
        } catch (e: Exception) {
            Log.w(TAG, "saveExtensionLink failed for sourceId=$sourceId, url=$animeUrl — ${e.message}")
            throw e
        }
    }

    private companion object {
        private const val TAG = "AnikutaSourceLinkBackupAccess"
    }
}
