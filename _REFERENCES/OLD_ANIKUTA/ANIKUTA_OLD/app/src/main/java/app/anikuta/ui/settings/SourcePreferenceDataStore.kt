package app.anikuta.ui.settings

import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore

/**
 * Bridges a [SharedPreferences] instance to a [PreferenceDataStore] so that
 * [PreferenceFragmentCompat] reads/writes go to the extension's own
 * `"source_$id"` SharedPreferences file.
 *
 * Source: REFERENCE/app/.../data/SharedPreferencesDataStore.kt
 */
class SourcePreferenceDataStore(
    private val prefs: SharedPreferences,
) : PreferenceDataStore() {

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return prefs.getBoolean(key, defValue)
    }

    override fun putBoolean(key: String?, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getString(key: String?, defValue: String?): String? {
        return prefs.getString(key, defValue)
    }

    override fun putString(key: String?, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? {
        return prefs.getStringSet(key, defValues)
    }

    override fun putStringSet(key: String?, values: Set<String>?) {
        prefs.edit().putStringSet(key, values).apply()
    }

    override fun getInt(key: String?, defValue: Int): Int {
        return prefs.getInt(key, defValue)
    }

    override fun putInt(key: String?, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getLong(key: String?, defValue: Long): Long {
        return prefs.getLong(key, defValue)
    }

    override fun putLong(key: String?, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        return prefs.getFloat(key, defValue)
    }

    override fun putFloat(key: String?, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }
}
