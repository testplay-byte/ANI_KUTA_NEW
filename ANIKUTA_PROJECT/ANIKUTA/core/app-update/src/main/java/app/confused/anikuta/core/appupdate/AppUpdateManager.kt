package app.confused.anikuta.core.appupdate

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The central orchestrator for the app self-update system.
 *
 * # Responsibilities
 *
 * 1. **Check for updates** — queries all registered [UpdateSource]s for the
 *    latest release. Returns the first non-null result (source priority order).
 * 2. **Decide whether to show the update dialog** — checks the 6-hour dismiss
 *    cooldown + the auto-check enabled setting.
 * 3. **Download updates** — delegates to [UpdateDownloader], tracks progress
 *    via a [StateFlow].
 * 4. **Install updates** — delegates to [ApkInstaller] to launch the system
 *    installer after download completes.
 * 5. **Track downloaded versions** — records each completed download in
 *    [AppUpdatePreferences] for the "downloaded versions" UI.
 *
 * # State
 *
 * - [latestUpdate] — the most recent update info found (or null).
 * - [downloadProgress] — live progress of the current download (or null).
 * - [isChecking] — true while a check is in progress.
 *
 * # Integration
 *
 * - **App open** → [checkForUpdateOnStartup] (respects auto-check setting +
 *   dismiss cooldown).
 * - **Manual check** → [checkForUpdate] (from Settings → About → Updates).
 * - **Download** → [startDownload] (from the update dialog or settings).
 * - **Install** → [installDownloadedApk] (after download completes, or from
 *   the "downloaded versions" list).
 *
 * @param context the app context.
 * @param preferences the update preferences.
 * @param sources the registered update sources (priority order).
 */
class AppUpdateManager(
    private val context: Context,
    private val preferences: AppUpdatePreferences,
    private val sources: List<UpdateSource>,
) {
    private val downloader = UpdateDownloader(context, createOkHttpClient())
    private val installer = ApkInstaller(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _latestUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val latestUpdate: StateFlow<AppUpdateInfo?> = _latestUpdate.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _lastCheckError = MutableStateFlow<String?>(null)
    val lastCheckError: StateFlow<String?> = _lastCheckError.asStateFlow()

    /**
     * Gets the installed app's version name from the package manager.
     */
    private fun getInstalledVersionName(): String = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        info.versionName ?: "0.0.0"
    } catch (e: Exception) {
        Log.w(TAG, "getInstalledVersionName: failed", e)
        "0.0.0"
    }

    /**
     * Gets the installed app's version code from the package manager.
     */
    private fun getInstalledVersionCode(): Long = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    } catch (e: Exception) {
        Log.w(TAG, "getInstalledVersionCode: failed", e)
        0L
    }

    /**
     * Checks for updates on app startup.
     *
     * Respects:
     * - [AppUpdatePreferences.isUpdateCheckEnabled] — if OFF, does nothing.
     * - [AppUpdatePreferences.isDismissedInCooldown] — if the latest version
     *   was dismissed < 6 hours ago, does nothing.
     *
     * On success, sets [latestUpdate]. The caller (UI) can then check
     * [shouldShowDialog] to decide whether to show the update dialog.
     */
    fun checkForUpdateOnStartup() {
        if (!preferences.isUpdateCheckEnabled()) {
            Log.d(TAG, "checkForUpdateOnStartup: auto-check disabled — skipping")
            return
        }
        scope.launch {
            val update = checkForUpdate()
            if (update != null && preferences.isDismissedInCooldown(update.versionName)) {
                Log.i(TAG, "checkForUpdateOnStartup: update ${update.versionName} is in dismiss cooldown — not surfacing")
                // Still store it (so manual check in settings is instant), just don't show the dialog.
                _latestUpdate.value = update
            }
        }
    }

    /**
     * Manually checks for updates (from Settings → About → "Check for updates").
     *
     * Always runs regardless of the auto-check setting or dismiss cooldown.
     * Sets [latestUpdate] + returns the result.
     */
    suspend fun checkForUpdate(): AppUpdateInfo? {
        _isChecking.value = true
        _lastCheckError.value = null
        try {
            val currentCode = getInstalledVersionCode()
            val currentName = getInstalledVersionName()
            Log.i(TAG, "checkForUpdate: current=$currentName/$currentCode, sources=${sources.map { it.id }}")

            for (source in sources) {
                try {
                    val update = source.fetchLatestUpdate(currentCode, currentName)
                    if (update != null) {
                        _latestUpdate.value = update
                        preferences.setLastCheckTimestamp(System.currentTimeMillis())
                        Log.i(TAG, "checkForUpdate: found update ${update.versionName} from ${source.id}")
                        return update
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "checkForUpdate: source ${source.id} failed", e)
                }
            }

            // No update found from any source.
            _latestUpdate.value = null
            preferences.setLastCheckTimestamp(System.currentTimeMillis())
            Log.i(TAG, "checkForUpdate: no update available")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "checkForUpdate: failed", e)
            _lastCheckError.value = e.message ?: "Check failed"
            return null
        } finally {
            _isChecking.value = false
        }
    }

    /**
     * Determines if the update dialog should be shown automatically on app open.
     *
     * Returns true only if:
     * 1. [latestUpdate] is non-null.
     * 2. The update is NOT in the dismiss cooldown.
     */
    fun shouldShowDialog(): Boolean {
        val update = _latestUpdate.value ?: return false
        return !preferences.isDismissedInCooldown(update.versionName)
    }

    /**
     * Records that the user dismissed the update dialog.
     *
     * This triggers the 6-hour cooldown for the current version.
     */
    fun dismissUpdate() {
        val update = _latestUpdate.value ?: return
        preferences.recordDismissal(update.versionName)
        Log.i(TAG, "dismissUpdate: user dismissed ${update.versionName} (6h cooldown)")
    }

    /**
     * Starts downloading the update APK.
     *
     * Progress is reported via [downloadProgress]. When complete, the APK is
     * recorded in [AppUpdatePreferences] for the "downloaded versions" list.
     * The caller should observe [downloadProgress] + call [installDownloadedApk]
     * when [DownloadProgress.isComplete] is true.
     *
     * If a download is already in progress, this is a no-op.
     */
    fun startDownload() {
        val update = _latestUpdate.value ?: run {
            Log.w(TAG, "startDownload: no update to download")
            return
        }
        if (_downloadProgress.value != null && _downloadProgress.value?.isComplete == false) {
            Log.w(TAG, "startDownload: download already in progress")
            return
        }

        Log.i(TAG, "startDownload: starting download of ${update.versionName}")
        scope.launch {
            downloader.download(update).collectLatest { progress ->
                _downloadProgress.value = progress
                if (progress.isComplete && progress.error == null) {
                    // Record the downloaded APK.
                    val apkFile = downloader.getApkFile(update.versionName)
                    preferences.addDownloadedApk(
                        DownloadedApk(
                            versionName = update.versionName,
                            filePath = apkFile.absolutePath,
                            downloadedAt = System.currentTimeMillis(),
                            sizeBytes = apkFile.length(),
                            source = update.source,
                        ),
                    )
                    Log.i(TAG, "startDownload: download complete + recorded — ${apkFile.absolutePath}")
                }
            }
        }
    }

    /**
     * Installs a downloaded APK file.
     *
     * Launches the system installer. After the user confirms, the app is
     * updated (and likely restarted).
     *
     * @param apkPath the absolute path to the APK file. If null, uses the
     *   latest update's APK path.
     * @return true if the installer was launched successfully.
     */
    fun installDownloadedApk(apkPath: String? = null): Boolean {
        val path = apkPath ?: run {
            val update = _latestUpdate.value ?: return false
            downloader.getApkFile(update.versionName).absolutePath
        }
        return installer.installApk(path)
    }

    /**
     * Clears the download progress state (for UI reset after the dialog closes).
     */
    fun clearDownloadProgress() {
        _downloadProgress.value = null
    }

    /**
     * Clears the latest update (for UI reset).
     */
    fun clearLatestUpdate() {
        _latestUpdate.value = null
    }

    // ── Downloaded state helpers ──

    /**
     * Checks if the latest update has already been downloaded.
     *
     * Returns true if [latestUpdate] is non-null AND an APK file for that
     * version exists on disk. The UI uses this to show "Install" instead of
     * "Download" when the user re-opens the update sheet.
     */
    fun isLatestUpdateDownloaded(): Boolean {
        val update = _latestUpdate.value ?: return false
        return preferences.isVersionDownloaded(update.versionName)
    }

    /**
     * Gets the file path for the latest update's downloaded APK.
     * Returns null if not downloaded.
     */
    fun getDownloadedApkPath(): String? {
        val update = _latestUpdate.value ?: return null
        return preferences.getDownloadedApkPath(update.versionName)
    }

    /**
     * Deletes a downloaded APK file AND removes its record.
     * Also clears download progress if it matches the current download.
     *
     * @param filePath the file path of the APK to delete.
     * @return true if the file was deleted (or didn't exist).
     */
    fun deleteDownloadedApk(filePath: String): Boolean {
        // Clear download progress if it's for the same file
        _downloadProgress.value = null
        return preferences.deleteDownloadedApk(filePath)
    }

    /**
     * Cleans up old downloaded APKs — deletes any APK whose version is older
     * than or equal to the currently installed version.
     *
     * Called on app startup to prevent storage bloat. After a successful
     * update install, the old APK files are no longer needed.
     *
     * # The "just installed" case
     *
     * After the user installs an update, the app restarts with the new version.
     * The downloaded APK file is still on disk. This method detects it by
     * checking if the APK's actual version code (read from the APK's manifest
     * via PackageManager) matches the installed version code — if so, the APK
     * was just installed and should be deleted.
     *
     * However, reading the APK's version code requires `PackageParser` (deprecated)
     * or `PackageInstaller` which is complex. Instead, we use a simpler heuristic:
     * if the APK file's version name (from the GitHub release tag) does NOT match
     * any known future update, AND the installed version code is >= the APK's
     * parsed version code, delete it.
     *
     * For the testing loop (where the release tag v0.3.0 has versionCode 300 but
     * the actual APK has versionCode 5), we ALSO delete the APK if its version
     * name matches the installed version name — this handles the "just installed"
     * case correctly.
     */
    fun cleanupOldDownloads() {
        val currentCode = getInstalledVersionCode()
        val currentName = getInstalledVersionName()
        val downloaded = preferences.getDownloadedApks()
        if (downloaded.isEmpty()) return

        Log.i(TAG, "cleanupOldDownloads: checking ${downloaded.size} downloaded APKs against current=$currentName/$currentCode")
        var cleaned = 0
        downloaded.forEach { apk ->
            val apkCode = parseVersionCode(apk.versionName)
            // Delete if:
            // 1. The APK's version code <= current (it's an old version), OR
            // 2. The APK's version name matches the installed version name
            //    (the user just installed it — the file is no longer needed).
            val shouldDelete = apkCode <= currentCode || apk.versionName == currentName
            if (shouldDelete) {
                if (preferences.deleteDownloadedApk(apk.filePath)) {
                    cleaned++
                    Log.d(TAG, "cleanupOldDownloads: deleted ${apk.versionName} (code=$apkCode, current=$currentCode)")
                }
            }
        }
        if (cleaned > 0) {
            Log.i(TAG, "cleanupOldDownloads: cleaned up $cleaned old APK(s)")
        }
    }

    /**
     * Clears the download progress + latest update state.
     *
     * Called after the user successfully installs an update (the app restarts,
     * so this is called on the next startup to reset the UI state).
     */
    fun clearUpdateState() {
        _downloadProgress.value = null
        _latestUpdate.value = null
    }

    /**
     * Parses a semantic version string ("MAJOR.MINOR.PATCH") into a comparable
     * long: `major * 10000 + minor * 100 + patch`.
     */
    private fun parseVersionCode(versionName: String): Long {
        val cleanName = versionName.removePrefix("v").removePrefix("V")
            .substringBefore("-").substringBefore("+").trim()
        val parts = cleanName.split(".")
        return try {
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            major * 10000L + minor * 100L + patch
        } catch (e: Exception) {
            0L
        }
    }

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private companion object {
        private const val TAG = "AnikutaAppUpdateManager"
    }
}
