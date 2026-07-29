package app.confused.anikuta.core.downloadidentity

import kotlinx.serialization.Serializable

/**
 * The per-anime identity file (`identity.json`) stored inside each download folder.
 *
 * **Purpose:** decouples the download folder name from the anime's identity. The folder
 * name is just the sanitized title (`<Title>/Episode NNN/video.mp4`); all identity info
 * (contentId, anilistId, sourceId, extension metadata, etc.) lives in this file.
 *
 * When the user links/unlinks/switches sources, only this file is updated — the folder
 * is never renamed. This means downloads are NEVER orphaned by identity changes.
 *
 * # Fields
 *
 * - [schemaVersion]: for future migrations of the identity file format itself.
 * - [contentId]: the Tier 2 per-content identity (e.g., `"al:154587"` or
 *   `"aniyomi:123:url"`). This is the primary key for finding the folder.
 * - [anilistId]: the AniList media ID, or null for unlinked extension anime.
 * - [sourceId]: the extension source's stable ID.
 * - [sourceUrl]: the extension's URL for this anime (SAnime.url).
 * - [title]: the canonical (unsanitized) anime title — used for display + folder naming.
 * - [coverUrl], [coverColor]: for the Downloads screen UI.
 * - [extensionSystem]: `"aniyomi"` or `"cloudstream"` (future).
 * - [extensionName], [extensionPkgName], etc.: provenance for the "Extension Unavailable" dialog.
 * - [repoUrl]: which extension repo this came from.
 * - [createdAt], [updatedAt]: timestamps.
 * - [migrationHistory]: a log of identity changes (for debugging + audit).
 *
 * Per `_DOWNLOAD_IDENTITY_PLAN/ARCHITECTURE.md`.
 */
@Serializable
data class DownloadIdentity(
    val schemaVersion: Int = 1,
    val contentId: String,
    val anilistId: Int? = null,
    val sourceId: Long,
    val sourceUrl: String,
    val title: String,
    val coverUrl: String? = null,
    val coverColor: String? = null,
    val extensionSystem: String = "aniyomi",
    val extensionName: String? = null,
    val extensionPkgName: String? = null,
    val extensionVersionName: String? = null,
    val extensionVersionCode: Long? = null,
    val extensionLang: String? = null,
    val repoUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val migrationHistory: List<MigrationEntry> = emptyList(),
) {
    /**
     * Create a copy with an updated contentId + anilistId, and a migration history entry.
     * Does NOT modify the original (returns a new instance).
     */
    fun withUpdatedIdentity(
        newContentId: String,
        newAnilistId: Int? = null,
        reason: String,
    ): DownloadIdentity {
        val entry = MigrationEntry(
            from = contentId,
            to = newContentId,
            reason = reason,
            at = System.currentTimeMillis(),
        )
        return copy(
            contentId = newContentId,
            anilistId = newAnilistId ?: anilistId,
            updatedAt = System.currentTimeMillis(),
            migrationHistory = migrationHistory + entry,
        )
    }

    /**
     * Create a copy with an updated source (extension switch).
     */
    fun withUpdatedSource(
        newSourceId: Long,
        newSourceUrl: String,
        newExtensionName: String? = null,
        newExtensionPkgName: String? = null,
        newExtensionVersionName: String? = null,
        newExtensionVersionCode: Long? = null,
        newExtensionLang: String? = null,
        newRepoUrl: String? = null,
    ): DownloadIdentity {
        return copy(
            sourceId = newSourceId,
            sourceUrl = newSourceUrl,
            extensionName = newExtensionName ?: extensionName,
            extensionPkgName = newExtensionPkgName ?: extensionPkgName,
            extensionVersionName = newExtensionVersionName ?: extensionVersionName,
            extensionVersionCode = newExtensionVersionCode ?: extensionVersionCode,
            extensionLang = newExtensionLang ?: extensionLang,
            repoUrl = newRepoUrl ?: repoUrl,
            updatedAt = System.currentTimeMillis(),
        )
    }

    @Serializable
    data class MigrationEntry(
        val from: String,
        val to: String,
        val reason: String,
        val at: Long,
    )
}
