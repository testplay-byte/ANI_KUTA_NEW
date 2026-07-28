package app.confused.anikuta.core.backup.model

import app.confused.anikuta.core.backup.BackupEntry
import kotlinx.serialization.Serializable

/**
 * The root JSON model of an ANIKUTA backup file.
 *
 * Stored as `meta.json` inside the `.anikuta` zip container. Each [entries]
 * item is a polymorphic [BackupEntry] — kotlinx-serialization adds a `"type"`
 * discriminator so the correct subclass is reconstructed on restore.
 *
 * @param schemaVersion the backup schema version. Bumped when the format
 *   changes in a breaking way. The [BackupManager] rejects unsupported versions.
 * @param createdAt epoch milliseconds when the backup was created.
 * @param appVersion the app version that created the backup (for debugging).
 * @param deviceName the device model that created the backup (for debugging).
 * @param entries the list of provider payloads. One entry per included category.
 */
@Serializable
data class BackupContainer(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val createdAt: Long,
    val appVersion: String = "",
    val deviceName: String = "",
    val entries: List<BackupEntry> = emptyList(),
) {
    companion object {
        /** The current backup schema version. Increment on breaking format changes. */
        const val CURRENT_SCHEMA_VERSION = 1

        /** The range of schema versions this app can restore. */
        val SUPPORTED_VERSIONS = 1..1
    }
}
