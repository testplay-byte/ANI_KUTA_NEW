package app.confused.anikuta.core.backup.provider

import android.util.Log
import app.confused.anikuta.core.backup.BackupCategory
import app.confused.anikuta.core.backup.BackupEntry
import app.confused.anikuta.core.backup.BackupProvider
import app.confused.anikuta.core.backup.model.PrefValue
import app.confused.anikuta.core.backup.model.PreferenceBackup
import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AnikutaBackup"

/**
 * Backs up all app preferences.
 *
 * Export reads [PreferenceStore.getAll] → `Map<String, *>` and wraps each value
 * in a [PrefValue] sealed variant. Import writes each value back via the
 * appropriate `PreferenceStore` setter based on its runtime type.
 *
 * **Sensitive keys:** Tracker OAuth tokens are stored in preferences but are
 * also backed up by [TrackerBackupProvider]. If the TRACKER category is
 * selected, the tracker provider handles tokens. The PreferencesBackupProvider
 * backs up ALL prefs (including tracker tokens) for completeness — on restore,
 * both providers write the same values (idempotent).
 */
class PreferencesBackupProvider(
    private val preferenceStore: PreferenceStore,
) : BackupProvider {

    override val id: String = BackupCategory.PREFERENCES.id

    override suspend fun export(): BackupEntry = withContext(Dispatchers.IO) {
        try {
            val all = preferenceStore.getAll()
            val entries = mutableMapOf<String, PrefValue>()
            all.forEach { (key, value) ->
                val wrapped = wrapValue(value)
                if (wrapped != null) {
                    entries[key] = wrapped
                } else {
                    Log.w(TAG, "Preferences export: skipping '$key' — unsupported type ${value?.javaClass?.name}")
                }
            }
            Log.i(TAG, "Preferences export: ${entries.size} prefs")
            BackupEntry.Preferences(prefs = PreferenceBackup(entries = entries))
        } catch (e: Exception) {
            Log.e(TAG, "Preferences export failed", e)
            BackupEntry.Preferences()
        }
    }

    override suspend fun import(entry: BackupEntry): Boolean = withContext(Dispatchers.IO) {
        require(entry is BackupEntry.Preferences) { "Expected Preferences entry, got ${entry.providerId}" }
        if (entry.prefs.entries.isEmpty()) return@withContext false
        var imported = 0
        var skipped = 0
        entry.prefs.entries.forEach { (key, prefValue) ->
            try {
                when (prefValue) {
                    is PrefValue.Str -> preferenceStore.getString(key, prefValue.value).set(prefValue.value)
                    is PrefValue.Num -> {
                        // Write as both Long and Int (the store keeps them in the same SharedPreferences)
                        preferenceStore.getLong(key, prefValue.value).set(prefValue.value)
                    }
                    is PrefValue.Dec -> preferenceStore.getFloat(key, prefValue.value).set(prefValue.value)
                    is PrefValue.Bool -> preferenceStore.getBoolean(key, prefValue.value).set(prefValue.value)
                    is PrefValue.StrSet -> preferenceStore.getStringSet(key, prefValue.value).set(prefValue.value)
                }
                imported++
            } catch (e: Exception) {
                Log.w(TAG, "Preferences import: failed for '$key' — ${e.message}")
                skipped++
            }
        }
        Log.i(TAG, "Preferences import: $imported imported, $skipped skipped")
        imported > 0
    }

    /** Wraps a raw preference value into a [PrefValue], or null if unsupported. */
    private fun wrapValue(value: Any?): PrefValue? {
        return when (value) {
            is String -> PrefValue.Str(value)
            is Int -> PrefValue.Num(value.toLong())
            is Long -> PrefValue.Num(value)
            is Float -> PrefValue.Dec(value)
            is Boolean -> PrefValue.Bool(value)
            is Set<*> -> {
                @Suppress("UNCHECKED_CAST")
                PrefValue.StrSet(value as Set<String>)
            }
            null -> null
            else -> null
        }
    }
}
