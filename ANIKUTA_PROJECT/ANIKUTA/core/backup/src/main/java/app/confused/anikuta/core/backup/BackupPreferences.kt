package app.confused.anikuta.core.backup

import app.confused.anikuta.core.preferences.PreferenceStore

/**
 * Auto-backup frequency options.
 *
 * Maps to WorkManager periodic work repeat intervals. The minimum WorkManager
 * periodic interval is 15 minutes; these values are well above that.
 */
enum class AutoBackupFrequency(val displayName: String, val intervalHours: Long) {
    EVERY_6H("Every 6 hours", 6),
    EVERY_12H("Every 12 hours", 12),
    EVERY_24H("Every 24 hours", 24),
    WEEKLY("Weekly", 24 * 7);

    companion object {
        /** Parse a frequency from its name (stored in preferences). */
        fun fromName(name: String?): AutoBackupFrequency =
            entries.firstOrNull { it.name == name } ?: EVERY_24H
    }
}

/**
 * Persists backup-related preferences (auto-backup config + SAF folder URI).
 *
 * Uses [PreferenceStore] (SharedPreferences-backed, reactive). All getters return
 * [Preference] objects so the UI can observe changes reactively.
 *
 * Preference keys:
 * - `pref_backup_auto_enabled` — Boolean
 * - `pref_backup_auto_frequency` — String (enum name)
 * - `pref_backup_auto_categories` — Set<String> (category ids)
 * - `pref_backup_auto_max_keep` — Int (1-4, how many auto-backups to keep)
 * - `pref_backup_folder_uri` — String (SAF tree URI)
 * - `pref_backup_last_auto` — Long (epoch ms of last successful auto-backup)
 * - `pref_backup_last_manual` — Long (epoch ms of last manual backup)
 */
class BackupPreferences(
    private val store: PreferenceStore,
) {
    val autoEnabled = store.getBoolean(KEY_AUTO_ENABLED, false)
    val autoFrequency = store.getString(KEY_AUTO_FREQUENCY, AutoBackupFrequency.EVERY_24H.name)
    val autoCategories = store.getStringSet(KEY_AUTO_CATEGORIES, BackupCategory.defaultSelection)
    val autoMaxKeep = store.getInt(KEY_AUTO_MAX_KEEP, 3)
    val folderUri = store.getString(KEY_FOLDER_URI, "")
    val lastAutoBackup = store.getLong(KEY_LAST_AUTO, 0L)
    val lastManualBackup = store.getLong(KEY_LAST_MANUAL, 0L)

    companion object {
        private const val KEY_AUTO_ENABLED = "pref_backup_auto_enabled"
        private const val KEY_AUTO_FREQUENCY = "pref_backup_auto_frequency"
        private const val KEY_AUTO_CATEGORIES = "pref_backup_auto_categories"
        private const val KEY_AUTO_MAX_KEEP = "pref_backup_auto_max_keep"
        private const val KEY_FOLDER_URI = "pref_backup_folder_uri"
        private const val KEY_LAST_AUTO = "pref_backup_last_auto"
        private const val KEY_LAST_MANUAL = "pref_backup_last_manual"

        /** The allowed range for max auto-backups to keep. */
        const val MAX_KEEP_MIN = 1
        const val MAX_KEEP_MAX = 4
        const val MAX_KEEP_DEFAULT = 3
    }
}
