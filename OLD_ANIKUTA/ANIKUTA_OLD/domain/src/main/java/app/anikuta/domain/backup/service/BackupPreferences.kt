package app.anikuta.domain.backup.service

import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore

class BackupPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun backupInterval() = preferenceStore.getInt("backup_interval", 12)

    fun lastAutoBackupTimestamp() = preferenceStore.getLong(Preference.appStateKey("last_auto_backup_timestamp"), 0L)

    fun backupFlags() = preferenceStore.getStringSet(
        "backup_flags",
        setOf(FLAG_CATEGORIES, FLAG_CHAPTERS, FLAG_HISTORY, FLAG_TRACK),
    )
}
