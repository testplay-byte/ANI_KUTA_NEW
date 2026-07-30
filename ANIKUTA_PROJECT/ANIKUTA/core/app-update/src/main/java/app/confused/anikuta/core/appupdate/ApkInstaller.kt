package app.confused.anikuta.core.appupdate

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Installs a downloaded APK file using the Android system installer.
 *
 * # How it works
 *
 * Uses a `FileProvider` to share the APK file with the system package installer,
 * then launches an `ACTION_VIEW` intent with `application/vnd.android.package-archive`
 * MIME type. This shows the standard Android "Install app" dialog.
 *
 * # Requirements
 *
 * 1. **`REQUEST_INSTALL_PACKAGES` permission** — must be declared in the app's
 *    `AndroidManifest.xml` (added to `:app`'s manifest). On Android 8+, the user
 *    must grant this permission via system settings on first install.
 *
 * 2. **FileProvider configuration** — the app must declare a `FileProvider` in
 *    its manifest with a `file_paths.xml` that includes the cache directory.
 *    This is configured in `:app`'s manifest + `res/xml/file_paths.xml`.
 *
 * # Alternative: PackageInstaller (silent install)
 *
 * For silent installs (no system dialog), use `PackageInstaller` from the
 * `packageManager`. This requires the `REQUEST_INSTALL_PACKAGES` permission
 * AND the app to be the "package installer" for the device (which a normal
 * app cannot be). So for self-updates, the `ACTION_VIEW` + system dialog
 * approach is the standard.
 *
 * @param context the app context.
 */
class ApkInstaller(
    private val context: Context,
) {

    /**
     * Launches the system installer for the APK at [apkPath].
     *
     * This opens the standard Android "Install app" dialog. The user must
     * confirm the installation. After installation, the system returns to
     * the app (or the app is killed + restarted if it's a self-update).
     *
     * @param apkPath the absolute path to the downloaded APK file.
     * @return true if the intent was successfully launched, false on error.
     */
    fun installApk(apkPath: String): Boolean {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            Log.e(TAG, "installApk: file does not exist: $apkPath")
            return false
        }

        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            Log.i(TAG, "installApk: launching installer for $apkPath (${apkFile.length()} bytes)")
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "installApk: failed to launch installer", e)
            false
        }
    }

    private companion object {
        private const val TAG = "AnikutaApkInstaller"
    }
}
