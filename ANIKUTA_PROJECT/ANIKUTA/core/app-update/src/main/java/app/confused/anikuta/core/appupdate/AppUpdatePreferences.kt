package app.confused.anikuta.core.appupdate

import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

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
 * # Downloaded APK lifecycle
 *
 * When a download completes, the APK file path is recorded in [downloadedApks].
 * The user can:
 * - **Install** — opens the system installer via [ApkInstaller].
 * - **Delete** — [deleteDownloadedApk] removes both the file from disk AND the
 *   record from the list. This frees up storage.
 *
 * Old downloaded APKs (for versions older than the currently installed one) are
 * automatically cleaned up by [AppUpdateManager.cleanupOldDownloads] on app
 * startup to prevent storage bloat.
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

    /**
     * The version name of the APK the user is *about to* install.
     *
     * Set by [setPendingPostInstall] just before the system installer is
     * launched (see [AppUpdateManager.installDownloadedApk]). On the next
     * app startup, [AnikutaRoot] checks [getPendingPostInstall]: if non-empty,
     * it means the user just installed an update — the post-install success
     * popup is shown + the value is cleared via [clearPendingPostInstall].
     */
    private val pendingPostInstallPref = preferenceStore.getString(KEY_PENDING_POST_INSTALL, "")

    /**
     * Records the version the user is about to install. Called right before
     * the system installer is launched.
     */
    fun setPendingPostInstall(version: String) {
        pendingPostInstallPref.set(version)
    }

    /**
     * Returns the version name of the most recent "about to install" record,
     * or an empty string if none. The caller should clear it via
     * [clearPendingPostInstall] after handling it.
     */
    fun getPendingPostInstall(): String = pendingPostInstallPref.get()

    /** Clears the pending-post-install marker (after the popup has been shown). */
    fun clearPendingPostInstall() {
        pendingPostInstallPref.set("")
    }

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

    fun recordDismissal(version: String) {
        lastDismissedVersionPref.set(version)
        lastDismissedTimestampPref.set(System.currentTimeMillis())
    }

    fun isDismissedInCooldown(version: String): Boolean {
        val dismissedVersion = lastDismissedVersionPref.get()
        if (dismissedVersion != version) return false
        val dismissedAt = lastDismissedTimestampPref.get()
        if (dismissedAt == 0L) return false
        val elapsed = System.currentTimeMillis() - dismissedAt
        return elapsed < DISMISS_COOLDOWN_MS
    }

    /** Clears the dismiss cooldown (for testing). */
    fun clearDismissCooldown() {
        lastDismissedVersionPref.set("")
        lastDismissedTimestampPref.set(0L)
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

    /**
     * Removes a downloaded APK record from the list (does NOT delete the file).
     * Use [deleteDownloadedApk] to also delete the file from disk.
     */
    fun removeDownloadedApk(filePath: String) {
        val current = downloadedApksPref.get().toMutableList()
        current.removeAll { it.filePath == filePath }
        downloadedApksPref.set(current)
    }

    /**
     * Deletes the downloaded APK file from disk AND removes its record.
     * Returns true if the file was deleted (or didn't exist).
     */
    fun deleteDownloadedApk(filePath: String): Boolean {
        var deleted = true
        try {
            val file = File(filePath)
            if (file.exists()) {
                deleted = file.delete()
            }
        } catch (e: Exception) {
            // Non-fatal — still remove from the list
            deleted = false
        }
        removeDownloadedApk(filePath)
        return deleted
    }

    /**
     * Checks if an APK for [versionName] has been downloaded.
     * Verifies both the record exists AND the file is present on disk.
     */
    fun isVersionDownloaded(versionName: String): Boolean {
        return downloadedApksPref.get().any { apk ->
            apk.versionName == versionName && File(apk.filePath).exists()
        }
    }

    /**
     * Gets the file path for a downloaded APK by version name.
     * Returns null if not downloaded or file doesn't exist.
     */
    fun getDownloadedApkPath(versionName: String): String? {
        return downloadedApksPref.get().firstOrNull { apk ->
            apk.versionName == versionName && File(apk.filePath).exists()
        }?.filePath
    }

    private companion object {
        private const val KEY_ENABLED = "pref_app_update_enabled"
        private const val KEY_LAST_CHECK = "pref_app_update_last_check"
        private const val KEY_DISMISSED_VERSION = "pref_app_update_dismissed_version"
        private const val KEY_DISMISSED_TIMESTAMP = "pref_app_update_dismissed_timestamp"
        private const val KEY_DOWNLOADED_APKS = "pref_app_update_downloaded_apks"
        private const val KEY_PENDING_POST_INSTALL = "pref_app_update_pending_post_install"

        /** 6 hours in milliseconds. */
        private const val DISMISS_COOLDOWN_MS = 6 * 60 * 60 * 1000L
    }
}
