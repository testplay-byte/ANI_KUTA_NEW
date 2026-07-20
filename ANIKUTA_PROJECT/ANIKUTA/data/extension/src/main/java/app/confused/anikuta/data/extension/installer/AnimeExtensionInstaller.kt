package app.confused.anikuta.data.extension.installer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import app.confused.anikuta.data.extension.model.AnimeExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * The orchestrator for downloading an extension APK and dispatching it to
 * [ExtensionInstallService] (which uses [PackageInstallerBackend]).
 *
 * Phase 4B scope: download via OkHttp (not Android's DownloadManager — simpler
 * and avoids the DownloadCompletionReceiver plumbing). The reference uses
 * DownloadManager for its pause/resume + DownloadManager UI integration; we
 * don't need that for Phase 4B.
 *
 * Guarantees:
 * - One install at a time ([installMutex] serializes concurrent calls).
 * - The temp APK is ALWAYS deleted — on success, on failure, and on cancellation.
 *   The service handles the post-install delete; this class deletes on the
 *   pre-service failure paths.
 * - Emits a [Flow]<[InstallStep]> so the UI can render progress.
 *
 * @param context      application context (for cache dir + service start).
 * @param client       the OkHttp client for APK downloads.
 */
class AnimeExtensionInstaller(
    private val context: Context,
    private val client: OkHttpClient,
) {

    /** Serializes installs — one at a time. */
    private val installMutex = Mutex()

    /**
     * Downloads [extension]'s APK from [apkUrl] and installs it via the
     * PackageInstaller service. Emits [InstallStep] progress.
     *
     * The flow completes when the install reaches a terminal state
     * ([InstallStep.Installed] / [InstallStep.Error] / [InstallStep.Idle]).
     */
    fun downloadAndInstall(
        apkUrl: String,
        extension: AnimeExtension.Available,
    ): Flow<InstallStep> = flow {
        installMutex.withLock {
            val downloadId = extension.pkgName.hashCode().toLong()
            emit(InstallStep.Pending)

            val tempFile = File(context.cacheDir, "ext-${extension.pkgName}-${extension.apkName}")
            try {
                emit(InstallStep.Downloading)
                val downloaded = downloadApk(apkUrl, tempFile)
                if (!downloaded) {
                    emit(InstallStep.Error)
                    return@flow
                }
                emit(InstallStep.Installing)

                // Dispatch to the foreground service. The service installs + deletes the temp file.
                val serviceIntent = ExtensionInstallService.newIntent(
                    context = context,
                    apkPath = tempFile.absolutePath,
                    pkgName = extension.pkgName,
                    downloadId = downloadId,
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                // The service handles the install + temp-file cleanup. The final
                // Installed/Error step arrives via the ExtensionInstallReceiver
                // (system PACKAGE_ADDED broadcast) → manager.refresh().
            } catch (e: Exception) {
                Log.e(TAG, "Download+install failed for ${extension.pkgName}", e)
                tempFile.takeIf { it.exists() }?.delete()
                Toast.makeText(context, "Extension install failed: ${e.message}", Toast.LENGTH_LONG).show()
                emit(InstallStep.Error)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Downloads [url] to [dest] using OkHttp. Returns `true` on success.
     * Streams the body to disk (does NOT hold the whole APK in memory).
     */
    private suspend fun downloadApk(url: String, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            response.use {
                if (!it.isSuccessful) {
                    Log.e(TAG, "HTTP ${it.code} downloading $url")
                    return@use false
                }
                val body = it.body ?: return@use false
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Downloaded $url → ${dest.absolutePath} (${dest.length()} bytes)")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $url", e)
            dest.takeIf { it.exists() }?.delete()
            false
        }
    }

    /**
     * Uninstalls an extension by package name using the system uninstall intent.
     * (Direct `PackageInstaller.uninstall` is reserved for Phase 4B+ — it needs
     * the same PendingIntent dance as install. The system intent is simpler and
     * shows the standard uninstall confirmation dialog.)
     */
    fun uninstallApk(pkgName: String) {
        val uri = Uri.fromParts("package", pkgName, null)
        val intent = Intent(Intent.ACTION_DELETE, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            // Check if the intent can be resolved (some ROMs don't support ACTION_DELETE)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.i(TAG, "Uninstall intent sent for $pkgName")
            } else {
                // Fallback: open the app details settings page where the user can uninstall manually
                Log.w(TAG, "ACTION_DELETE not resolved for $pkgName, opening app settings")
                val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = uri
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                Toast.makeText(context, "Open the app info to uninstall", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Uninstall failed for $pkgName", e)
            Toast.makeText(context, "Uninstall failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "AnikutaExtInstaller"
    }
}
