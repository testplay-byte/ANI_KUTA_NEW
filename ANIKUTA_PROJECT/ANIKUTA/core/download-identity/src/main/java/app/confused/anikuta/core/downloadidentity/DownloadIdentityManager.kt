package app.confused.anikuta.core.downloadidentity

import android.util.Log
import androidx.documentfile.provider.DocumentFile

/**
 * High-level manager for download folder identity.
 *
 * Provides:
 * - [findAnimeDir] — find a download folder by contentId (scan + read identity.json)
 * - [ensureIdentity] — write/update identity.json in a folder
 * - [updateIdentity] — atomically update the identity when linking/unlinking/switching
 * - [findAllIdentities] — scan all folders (for backup/restore + migration)
 * - [migrateLegacyFolder] — create identity.json for old-style folders (with [contentId] brackets)
 *
 * Per `_DOWNLOAD_IDENTITY_PLAN/ARCHITECTURE.md`.
 *
 * @param animeBaseDir a function returning the `anime/` directory inside the download root
 */
class DownloadIdentityManager(
    private val animeBaseDir: () -> DocumentFile?,
) {
    private companion object {
        const val TAG = "AnikutaDlIdentityMgr"
    }

    /**
     * Find a download folder by contentId.
     *
     * Scans the `anime/` directory, reads `identity.json` from each folder, and returns
     * the first folder whose `contentId` matches.
     *
     * **Legacy fallback:** if a folder has no `identity.json`, parses the `[contentId]`
     * suffix from the folder name (old-style: `<Title [al-154587]>`). This handles
     * folders created before this refactor.
     *
     * @param contentId the content ID to find (e.g., `"al:154587"`)
     * @return the matching [DocumentFile], or null if not found.
     */
    fun findAnimeDir(contentId: String): DocumentFile? {
        val baseDir = animeBaseDir() ?: return null
        val sanitizedContentId = contentId.replace(":", "-").replace("/", "-")
        val legacySuffix = "[$sanitizedContentId]"

        for (folder in baseDir.listFiles()) {
            if (!folder.isDirectory) continue

            // Try identity.json first (new system)
            val identity = DownloadIdentityStore.read(folder)
            if (identity != null) {
                if (identity.contentId == contentId) {
                    Log.d(TAG, "findAnimeDir: found by identity.json (contentId=$contentId, " +
                        "folder='${folder.name}')")
                    return folder
                }
                continue
            }

            // Legacy fallback: parse [contentId] from folder name
            if (folder.name?.endsWith(legacySuffix) == true) {
                Log.d(TAG, "findAnimeDir: found by legacy suffix (contentId=$contentId, " +
                    "folder='${folder.name}') — no identity.json (will create on next write)")
                return folder
            }
        }

        Log.d(TAG, "findAnimeDir: not found (contentId=$contentId)")
        return null
    }

    /**
     * Ensure an identity file exists in a folder. If it doesn't exist, create it.
     * If it exists, update it with the latest identity data (in case some fields changed).
     *
     * @param animeDir the anime's download folder
     * @param identity the identity to write
     */
    fun ensureIdentity(animeDir: DocumentFile, identity: DownloadIdentity) {
        val existing = DownloadIdentityStore.read(animeDir)
        if (existing == null) {
            // New folder — write identity.json
            DownloadIdentityStore.write(animeDir, identity)
            Log.i(TAG, "ensureIdentity: created identity.json (contentId='${identity.contentId}', " +
                "title='${identity.title}')")
        } else if (existing.contentId != identity.contentId ||
            existing.anilistId != identity.anilistId ||
            existing.sourceId != identity.sourceId
        ) {
            // Identity changed (link/unlink/switch) — update identity.json
            val updated = identity.copy(
                createdAt = existing.createdAt,
                migrationHistory = existing.migrationHistory +
                    DownloadIdentity.MigrationEntry(
                        from = existing.contentId,
                        to = identity.contentId,
                        reason = "updated_by_ensureIdentity",
                        at = System.currentTimeMillis(),
                    ),
            )
            DownloadIdentityStore.write(animeDir, updated)
            Log.i(TAG, "ensureIdentity: updated identity.json (old contentId='${existing.contentId}' " +
                "→ new contentId='${identity.contentId}')")
        }
    }

    /**
     * Update the identity in a folder. Used when linking/unlinking/switching.
     *
     * Finds the folder by [oldContentId], updates the identity.json with [newIdentity],
     * and returns true if successful.
     *
     * @param oldContentId the current contentId (to find the folder)
     * @param newIdentity the new identity to write
     * @return true if the folder was found + identity.json was updated
     */
    fun updateIdentity(oldContentId: String, newIdentity: DownloadIdentity): Boolean {
        val folder = findAnimeDir(oldContentId) ?: run {
            Log.w(TAG, "updateIdentity: folder not found for oldContentId=$oldContentId")
            return false
        }

        val existing = DownloadIdentityStore.read(folder)
        val updated = if (existing != null) {
            // Preserve createdAt + append migration history
            newIdentity.copy(
                createdAt = existing.createdAt,
                migrationHistory = existing.migrationHistory +
                    DownloadIdentity.MigrationEntry(
                        from = existing.contentId,
                        to = newIdentity.contentId,
                        reason = "updateIdentity",
                        at = System.currentTimeMillis(),
                    ),
            )
        } else {
            // Legacy folder (no identity.json) — create one
            newIdentity.copy(createdAt = System.currentTimeMillis())
        }

        DownloadIdentityStore.write(folder, updated)
        Log.i(TAG, "updateIdentity: updated (oldContentId='$oldContentId' → " +
            "newContentId='${newIdentity.contentId}', folder='${folder.name}')")
        return true
    }

    /**
     * Scan all download folders and return their identities.
     * Used for: backup/restore (find all downloads on disk), migration (create
     * identity.json for legacy folders), the Downloads screen (list all anime).
     *
     * @return a list of (folder, identity?) pairs. identity is null for legacy folders
     *   that don't have identity.json yet.
     */
    fun findAllIdentities(): List<Pair<DocumentFile, DownloadIdentity?>> {
        val baseDir = animeBaseDir() ?: return emptyList()
        return baseDir.listFiles()
            .filter { it.isDirectory }
            .map { folder -> folder to DownloadIdentityStore.read(folder) }
    }

    /**
     * Migrate a legacy folder (with `[contentId]` brackets in the name) to the new system.
     * Creates an identity.json inside the folder. Does NOT rename the folder.
     *
     * @param folder the legacy folder
     * @param contentId the contentId parsed from the folder name
     * @param title the title parsed from the folder name (before the brackets)
     * @return the created [DownloadIdentity], or null if the write failed
     */
    fun migrateLegacyFolder(
        folder: DocumentFile,
        contentId: String,
        title: String,
    ): DownloadIdentity? {
        if (DownloadIdentityStore.exists(folder)) {
            return DownloadIdentityStore.read(folder)
        }

        val identity = DownloadIdentity(
            contentId = contentId,
            sourceId = 0L,
            sourceUrl = "",
            title = title,
            createdAt = System.currentTimeMillis(),
        )
        DownloadIdentityStore.write(folder, identity)
        Log.i(TAG, "migrateLegacyFolder: created identity.json for legacy folder " +
            "'${folder.name}' (contentId='$contentId', title='$title')")
        return identity
    }
}
