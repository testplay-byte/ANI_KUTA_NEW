package app.confused.anikuta.core.backup.provider

/**
 * Phase 8 — module-architecture fix (Doc 04 violation 1).
 *
 * `:core:backup` previously imported `SourceLinkStore` + `ExtensionLinkStore`
 * directly from `:data:extension`. Core modules must not depend on data modules
 * (ARCHITECTURE §3). This interface exposes only the read/write surface that
 * [SourceLinkBackupProvider] needs, expressed in backup-specific data types so
 * the contract doesn't leak the data-layer `SourceLink` model.
 *
 * The implementation ([SourceLinkBackupAccessImpl]) lives in `:data:extension`
 * and is Koin-bound in `app/.../di/ExtensionModule.kt`. `:core:backup` injects
 * this interface — no `:data:extension` dependency needed.
 */
interface SourceLinkBackupAccess {

    /**
     * All source links (content_id → backup snapshot).
     *
     * Key = content_id (e.g., `"al:154587"`). Pre-Phase-4 backups used
     * anilistId.toString() as the key — the import path in
     * [SourceLinkBackupProvider] detects + converts those.
     */
    fun getAllSourceLinks(): Map<String, BackupSourceLink>

    /**
     * Persist a source link (the data layer wraps its concrete store).
     */
    fun saveSourceLink(contentId: String, sourceId: Long, animeUrl: String, animeTitle: String)

    /**
     * All extension links (`"$sourceId:$animeUrl"` → content_id).
     */
    fun getAllExtensionLinks(): Map<String, String>

    /**
     * Persist an extension link (the data layer wraps its concrete store).
     */
    fun saveExtensionLink(sourceId: Long, animeUrl: String, contentId: String)
}

/**
 * Backup-specific snapshot of a source link (mirrors the persisted fields of
 * `SourceLinkStore.SourceLink` without leaking the data-layer type).
 */
data class BackupSourceLink(
    val sourceId: Long,
    val animeUrl: String,
    val animeTitle: String,
)
