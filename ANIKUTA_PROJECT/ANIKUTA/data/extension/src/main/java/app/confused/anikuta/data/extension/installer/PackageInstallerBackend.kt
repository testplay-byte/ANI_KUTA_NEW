package app.confused.anikuta.data.extension.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/**
 * The Android `PackageInstaller` backend for installing a downloaded extension APK.
 *
 * Phase 4B scope: ONLY the PackageInstaller backend is implemented (per the
 * implementation prompt). The reference also has Legacy / Private / Shizuku
 * backends — those are deferred.
 *
 * Flow (driven by [ExtensionInstallService]):
 * 1. [install] opens a [PackageInstaller.Session] with `MODE_FULL_INSTALL`.
 * 2. Streams the APK file into the session via `openWrite` + `copyTo`.
 * 3. Commits the session with a [PendingIntent] broadcast for the result.
 * 4. [resultReceiver] receives `STATUS_PENDING_USER_ACTION` (starts the confirm
 *    activity), `STATUS_SUCCESS`, or `STATUS_FAILURE_*`.
 * 5. The [CompletableDeferred] resolves with the final [InstallStep].
 *
 * Temp APK cleanup is the service's responsibility (not this backend's), so that
 * cleanup happens on both success and failure paths regardless of which
 * component invoked the install.
 */
class PackageInstallerBackend(private val context: Context) {

    private val packageInstaller = context.packageManager.packageInstaller
    private var activeSessionId: Int? = null
    private var resultDeferred: CompletableDeferred<InstallStep>? = null

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE,
            )
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val userAction = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (userAction == null) {
                        Log.e(TAG, "STATUS_PENDING_USER_ACTION but no user action intent")
                        resolve(InstallStep.Error)
                        return
                    }
                    userAction.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(userAction)
                    // Wait for the confirm-activity result (fires another broadcast).
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    Log.i(TAG, "Install succeeded")
                    resolve(InstallStep.Installed)
                }
                PackageInstaller.STATUS_FAILURE_ABORTED -> {
                    Log.w(TAG, "Install aborted by user")
                    resolve(InstallStep.Idle)
                }
                else -> {
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                    Log.e(TAG, "Install failed: status=$status msg=$msg")
                    resolve(InstallStep.Error)
                }
            }
        }

        private fun resolve(step: InstallStep) {
            resultDeferred?.complete(step)
            resultDeferred = null
            activeSessionId?.let { sid ->
                try { packageInstaller.abandonSession(sid) } catch (_: Exception) {}
            }
            activeSessionId = null
            try { context.unregisterReceiver(this) } catch (_: Exception) {}
        }
    }

    /**
     * Installs [apkFile]. Returns a [CompletableDeferred] that resolves to the
     * final [InstallStep] (`Installed`, `Idle` for abort, or `Error`).
     *
     * The caller (the install service) is responsible for deleting [apkFile]
     * after the deferred completes — on both success and failure paths.
     */
    suspend fun install(apkFile: File, pkgName: String): InstallStep {
        if (!apkFile.isFile) {
            Log.e(TAG, "APK file missing: ${apkFile.absolutePath}")
            return InstallStep.Error
        }
        val deferred = CompletableDeferred<InstallStep>()
        resultDeferred = deferred

        ContextCompat.registerReceiver(
            context,
            resultReceiver,
            IntentFilter(INSTALL_ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )

        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(pkgName)
                setSize(apkFile.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = packageInstaller.createSession(params)
            activeSessionId = sessionId

            packageInstaller.openSession(sessionId).use { session ->
                context.contentResolver.openInputStream(Uri.fromFile(apkFile))?.use { input ->
                    session.openWrite(pkgName, 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                } ?: run {
                    Log.e(TAG, "Could not open APK input stream")
                    resolveError()
                    return deferred.await()
                }
                val intentSender = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(INSTALL_ACTION).setPackage(context.packageName),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
                ).intentSender
                session.commit(intentSender)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Install failed for $pkgName", e)
            resolveError()
        }

        return deferred.await()
    }

    private fun resolveError() {
        resultDeferred?.complete(InstallStep.Error)
        resultDeferred = null
        activeSessionId?.let { sid ->
            try { packageInstaller.abandonSession(sid) } catch (_: Exception) {}
        }
        activeSessionId = null
        try { context.unregisterReceiver(resultReceiver) } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "AnikutaExtInstaller"
        private const val INSTALL_ACTION = "app.confused.anikuta.action.INSTALL_RESULT"
    }
}
