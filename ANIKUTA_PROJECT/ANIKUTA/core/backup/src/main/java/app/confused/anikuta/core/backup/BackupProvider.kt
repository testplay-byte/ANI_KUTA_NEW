package app.confused.anikuta.core.backup

/**
 * Contract for a backup data provider.
 *
 * Each data-owning concern (library, episodes, categories, watch progress,
 * source links, tracker, preferences, episode metadata, cover images)
 * implements this interface. The [BackupManager] collects all registered
 * providers via Koin and orchestrates export/import.
 *
 * **Contract:**
 * - [id] MUST match a [BackupCategory.id] — this is how the user selects
 *   which providers to include.
 * - [export] MUST be safe to call on [Dispatchers.IO]. It reads data from
 *   the backing store and returns a serializable [BackupEntry].
 * - [import] MUST be safe to call on [Dispatchers.IO]. It restores data from
 *   the entry. It MUST handle missing/malformed data gracefully (skip +
 *   continue, never throw). Returns `true` if at least one item was
 *   imported successfully.
 * - Both methods MUST log meaningful errors (tag `AnikutaBackup`) and never
 *   crash the app.
 *
 * **Adding a new provider:**
 *  1. Add a [BackupCategory] entry.
 *  2. Add a [BackupEntry] subclass.
 *  3. Implement this interface.
 *  4. Register in [BackupModule] (`core/backup/di/BackupModule.kt`).
 *  5. The [BackupManager] automatically picks it up via Koin `getAll<BackupProvider>()`.
 */
interface BackupProvider {
    /** Stable unique id (matches [BackupCategory.id]). */
    val id: String

    /**
     * Export this provider's data as a [BackupEntry].
     * Called on Dispatchers.IO. Should not throw — return an empty entry on error.
     */
    suspend fun export(): BackupEntry

    /**
     * Import data from a [BackupEntry].
     * Called on Dispatchers.IO.
     * @return true if at least one item was imported; false if nothing was imported (skipped or empty).
     * @throws ClassCastException if the entry type doesn't match (programming error).
     */
    suspend fun import(entry: BackupEntry): Boolean
}
