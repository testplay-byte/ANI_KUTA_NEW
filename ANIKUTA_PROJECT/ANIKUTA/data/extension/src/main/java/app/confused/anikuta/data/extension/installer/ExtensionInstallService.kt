package app.confused.anikuta.data.extension.installer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service that owns a single [PackageInstallerBackend] and processes
 * one install request at a time.
 *
 * The Aniyomi reference uses a long-running service with a queue (`InstallerAnime`
 * base class). For Phase 4B we keep it simple: each `startService` delivers one
 * install request, the service runs it to completion, then stops itself. Serial
 * ordering is enforced by [AnimeExtensionInstaller]'s `Mutex` — concurrent
 * `installExtension` calls queue there, not here.
 *
 * **Foreground requirement (Android 12+):** when started via `startForegroundService`,
 * the service MUST call [startForeground] within 5 seconds or the system throws
 * `ForegroundServiceDidNotStartInTimeException`. We call it immediately in
 * [onStartCommand] with a low-priority notification.
 *
 * Input extras:
 * - [EXTRA_APK_PATH]   — absolute path to the downloaded temp APK.
 * - [EXTRA_PKG_NAME]   — the extension's package name (for the session).
 * - [EXTRA_DOWNLOAD_ID] — the manager's download id (for log correlation).
 *
 * On completion (success or failure) the temp APK is deleted and the service
 * stops itself.
 */
class ExtensionInstallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var backend: PackageInstallerBackend

    override fun onCreate() {
        super.onCreate()
        backend = PackageInstallerBackend(this)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apkPath = intent?.getStringExtra(EXTRA_APK_PATH)
        val pkgName = intent?.getStringExtra(EXTRA_PKG_NAME)
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L

        // Promote to foreground IMMEDIATELY (Android 12+ requires startForeground within 5s).
        startForegroundCompat("Installing extension\u2026")

        if (apkPath == null || pkgName == null) {
            Log.e(TAG, "Missing extras: apkPath=$apkPath pkgName=$pkgName downloadId=$downloadId")
            stopSelf()
            return START_NOT_STICKY
        }

        scope.launch {
            val apkFile = File(apkPath)
            try {
                val result = backend.install(apkFile, pkgName)
                Log.i(TAG, "Install finished for $pkgName: $result (downloadId=$downloadId)")
            } catch (e: Exception) {
                Log.e(TAG, "Install threw for $pkgName (downloadId=$downloadId)", e)
            } finally {
                // ALWAYS delete the temp APK — success OR failure (per prompt requirement).
                if (apkFile.exists()) {
                    val deleted = apkFile.delete()
                    if (!deleted) {
                        Log.w(TAG, "Could not delete temp APK: ${apkFile.absolutePath}")
                    } else {
                        Log.d(TAG, "Deleted temp APK: ${apkFile.absolutePath}")
                    }
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /** Calls [startForeground] using the right API for the device's SDK. */
    private fun startForegroundCompat(contentText: String) {
        val notification = buildNotification(contentText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires the foregroundServiceType in startForeground.
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ANIKUTA")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Extension installs",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progress for extension APK installations"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_APK_PATH = "app.confused.anikuta.extra.APK_PATH"
        const val EXTRA_PKG_NAME = "app.confused.anikuta.extra.PKG_NAME"
        const val EXTRA_DOWNLOAD_ID = "app.confused.anikuta.extra.DOWNLOAD_ID"

        /** Builds the start-intent for the service. */
        fun newIntent(context: Context, apkPath: String, pkgName: String, downloadId: Long): Intent =
            Intent(context, ExtensionInstallService::class.java).apply {
                putExtra(EXTRA_APK_PATH, apkPath)
                putExtra(EXTRA_PKG_NAME, pkgName)
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }

        private const val TAG = "AnikutaExtInstaller"
        private const val CHANNEL_ID = "anikuta_extension_installs"
        private const val NOTIFICATION_ID = 0xA1
    }
}
