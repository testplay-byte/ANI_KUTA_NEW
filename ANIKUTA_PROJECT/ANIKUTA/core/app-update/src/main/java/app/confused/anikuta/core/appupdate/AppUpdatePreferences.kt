package app.confused.anikuta.core.appupdate

import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Preferences for the app self-update system.
 *
 * # Stored data
 *
 * - [updateCheckEnabled] — master on/off toggle for automatic checks on app open.
 * - [lastCheckTimestamp] — when the last check ran (for throttling).
 * - [lastDismissedVersion] — the version the user last dismissed (for the 6-hour cooldown).
 * - [lastDismissedTimestamp] — when the user dismissed it (for the 6-hour cooldown).
 * - [downloadedApks] — list of downloaded APK files (for the "downloaded versions" UI).
 *
 * # 6-hour dismiss cooldown
 *
 * When the user clicks "Cancel" on the update dialog, we record the version +
 * timestamp. On the next app open, [AppUpdateManager.shouldShowDialog] checks:
 * - Is the dismissed version the same as the latest available version?
 * - Has 6 hours passed since the dismiss timestamp?
 * If same version AND < 6 hours → don't show the dialog automatically.
 * The user can still check manually in Settings → About → Updates.
 *
 * @param preferenceStore the backing preference store.
 */
class AppUpdatePreferences(
    private val preferenceStore: PreferenceStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val enabledPref = preferenceStore.getBoolean(KEY_ENABLED, true)
    private val lastCheckPref = preferenceStore.getLong(KEY_LAST_CHECK, 0L)
    private val lastDismissedVersionPref = preferenceStore.getString(KEY_DISMISSED_VERSION, "")
    private val lastDismissedTimestampPref = preferenceStore.getLong(KEY_DISMISSED_TIMESTAMP, 0L)
    private val downloadedApksPref = preferenceStore.getObject(
        key = KEY_DOWNLOADED_APKS,
        defaultValue = emptyList(),
        serializer = { list ->
            json.encodeToString(ListSerializer(DownloadedApk.serializer()), list)
        },
        deserializer = { str ->
            try {
                json.decodeFromString(ListSerializer(DownloadedApk.serializer()), str)
            } catch (e: Exception) {
                emptyList()
            }
        },
    )

    // ── Enabled ──

    fun isUpdateCheckEnabled(): Boolean = enabledPref.get()
    fun setUpdateCheckEnabled(enabled: Boolean) = enabledPref.set(enabled)
    fun observeUpdateCheckEnabled(): Flow<Boolean> = enabledPref.changes()

    // ── Last check timestamp ──

    fun getLastCheckTimestamp(): Long = lastCheckPref.get()
    fun setLastCheckTimestamp(timestamp: Long) = lastCheckPref.set(timestamp)
    fun observeLastCheckTimestamp(): Flow<Long> = lastCheckPref.changes()

    // ── Dismiss cooldown ──

    fun getLastDismissedVersion(): String = lastDismissedVersionPref.get()
    fun getLastDismissedTimestamp(): Long = lastDismissedTimestampPref.get()

    /**
     * Records that the user dismissed the update dialog for [version].
     * Sets both the version + the current timestamp (for the 6-hour cooldown).
     */
    fun recordDismissal(version: String) {
        lastDismissedVersionPref.set(version)
        lastDismissedTimestampPref.set(System.currentTimeMillis())
    }

    /**
     * Checks if the update dialog should be suppressed for [version].
     * Returns true if the user dismissed this exact version AND < 6 hours have passed.
     */
    fun isDismissedInCooldown(version: String): Boolean {
        val dismissedVersion = lastDismissedVersionPref.get()
        if (dismissedVersion != version) return false
        val dismissedAt = lastDismissedTimestampPref.get()
        if (dismissedAt == 0L) return false
        val elapsed = System.currentTimeMillis() - dismissedAt
        return elapsed < DISMISS_COOLDOWN_MS
    }

    // ── Downloaded APKs ──

    fun getDownloadedApks(): List<DownloadedApk> = downloadedApksPref.get()
    fun observeDownloadedApks(): Flow<List<DownloadedApk>> = downloadedApksPref.changes().map { it }

    /** Adds a downloaded APK to the list (dedupes by filePath). */
    fun addDownloadedApk(apk: DownloadedApk) {
        val current = downloadedApksPref.get().toMutableList()
        current.removeAll { it.filePath == apk.filePath }
        current.add(0, apk) // newest first
        downloadedApksPref.set(current)
    }

    /** Removes a downloaded APK record (does NOT delete the file). */
    fun removeDownloadedApk(filePath: String) {
        val current = downloadedApksPref.get().toMutableList()
        current.removeAll { it.filePath == filePath }
        downloadedApksPref.set(current)
    }

    private companion object {
        private const val KEY_ENABLED = "pref_app_update_enabled"
        private const val KEY_LAST_CHECK = "pref_app_update_last_check"
        private const val KEY_DISMISSED_VERSION = "pref_app_update_dismissed_version"
        private const val KEY_DISMISSED_TIMESTAMP = "pref_app_update_dismissed_timestamp"
        private const val KEY_DOWNLOADED_APKS = "pref_app_update_downloaded_apks"

        /** 6 hours in milliseconds. */
        private const val DISMISS_COOLDOWN_MS = 6 * 60 * 60 * 1000L
    }
}
