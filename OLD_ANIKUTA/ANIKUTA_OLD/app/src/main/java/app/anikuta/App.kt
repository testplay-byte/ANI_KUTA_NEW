package app.anikuta

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.work.WorkManager
import app.anikuta.core.preference.AndroidPreferenceStore
import app.anikuta.data.notification.Notifications
import app.anikuta.di.AppModule
import app.anikuta.di.DomainModule
import app.anikuta.di.PreferenceModule
import app.anikuta.download.DownloadManager
import app.anikuta.download.DownloadNotifier
import app.anikuta.error.AnikutaCrashHandler
import app.anikuta.notification.NotificationPreferences
import app.anikuta.notification.ReleaseCheckPlanner
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * ANI-KUTA Application class.
 * Wires Injekt DI on startup + installs the global crash handler.
 *
 * The crash handler is installed FIRST, before DI, so if DI itself throws
 * the user still gets the ErrorActivity instead of a silent crash.
 *
 * Notification channels are created after DI is ready (DownloadNotifier
 * needs to be resolvable via Injekt).
 *
 * Crash loop prevention: if the last crash was a foregroundServiceType
 * error (from WorkManager trying to start the download foreground service),
 * all download WorkManager jobs are cancelled on startup to break the loop.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Install the global crash handler first — before anything that
        // might throw — so the user always gets the error screen.
        Thread.setDefaultUncaughtExceptionHandler(AnikutaCrashHandler(this))

        Log.d("AnikutaApp", "=== App.onCreate started ===")

        // ---- Crash loop prevention ----
        // Check if the previous crash was a foregroundServiceType error.
        // If so, cancel all download WorkManager jobs BEFORE DI restores
        // the queue (which would trigger the worker to restart → crash again).
        checkForForegroundServiceCrashLoop()

        try {
            val preferenceStore = AndroidPreferenceStore(this)
            Log.d("AnikutaApp", "PreferenceStore created")
            Injekt.importModule(PreferenceModule(preferenceStore))
            Log.d("AnikutaApp", "PreferenceModule imported")
            Injekt.importModule(AppModule(this))
            Log.d("AnikutaApp", "AppModule imported")
            Injekt.importModule(DomainModule())
            Log.d("AnikutaApp", "DomainModule imported — DI ready")

            // Create notification channels for the download system
            try {
                val notifier = Injekt.get<DownloadNotifier>()
                notifier.createChannels()
                Log.d("AnikutaApp", "✓ Download notification channels created")
            } catch (e: Exception) {
                Log.w("AnikutaApp", "⚠ Could not create notification channels: ${e.message}")
            }

            // Create notification channels for the new-episode system (Phase N)
            createNewEpisodeNotificationChannels()

            // Schedule the initial release-tracking check (Phase N-1)
            // The planner finds the earliest due check and schedules a WorkManager job.
            try {
                val planner = Injekt.get<ReleaseCheckPlanner>()
                val notifPrefs = Injekt.get<NotificationPreferences>()
                if (notifPrefs.globalNotifyEnabled().get()) {
                    planner.scheduleNextCheck()
                    Log.d("AnikutaApp", "✓ Release tracker initial schedule set")
                } else {
                    Log.d("AnikutaApp", "Notifications disabled — skipping initial release tracker schedule")
                }
            } catch (e: Exception) {
                Log.w("AnikutaApp", "⚠ Could not schedule release tracker: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("AnikutaApp", "❌ DI setup FAILED", e)
            // Re-throw so the crash handler catches it and shows ErrorActivity.
            throw e
        }
        Log.d("AnikutaApp", "=== App.onCreate finished ===")
    }

    /**
     * Create notification channels for the new-episode system (Phase N).
     * Two channels:
     *  - CHANNEL_NEW_EPISODES (default importance) — extension-confirmed new episodes
     *  - CHANNEL_NEW_EPISODES_ANILIST (low importance) — AniList "aired" notifications
     */
    private fun createNewEpisodeNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        try {
            // Main new-episode channel (extension-confirmed)
            manager.createNotificationChannel(
                NotificationChannel(
                    Notifications.CHANNEL_NEW_EPISODES,
                    "New Episodes",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Notifications when a new episode is available to watch."
                    enableVibration(true)
                }
            )

            // AniList-aired channel (lower importance — informational)
            manager.createNotificationChannel(
                NotificationChannel(
                    Notifications.CHANNEL_NEW_EPISODES_ANILIST,
                    "Episode Airings (AniList)",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Notifications when an episode airs according to AniList (may not be watchable yet)."
                }
            )

            Log.d("AnikutaApp", "✓ New-episode notification channels created")
        } catch (e: Exception) {
            Log.w("AnikutaApp", "⚠ Could not create new-episode channels: ${e.message}")
        }
    }

    /**
     * Check if the last crash was a foregroundServiceType mismatch error.
     * If so, cancel all WorkManager download jobs to prevent a crash loop.
     *
     * The crash happens when DownloadWorker calls setForeground() with
     * FOREGROUND_SERVICE_TYPE_DATA_SYNC but the manifest doesn't declare
     * the service with that type. WorkManager keeps retrying the worker,
     * causing the app to crash repeatedly.
     *
     * This is a safety net — the manifest fix (declaring SystemForegroundService
     * with foregroundServiceType="dataSync") is the primary fix. This check
     * handles edge cases where the manifest fix regresses or doesn't apply.
     */
    private fun checkForForegroundServiceCrashLoop() {
        try {
            val lastCrash = AnikutaCrashHandler.getLastCrash(this) ?: return
            if (lastCrash.contains("foregroundServiceType") ||
                lastCrash.contains("SystemForegroundService")) {
                Log.w("AnikutaApp", "⚠ Detected foregroundServiceType crash loop — cancelling all download work")
                val workManager = WorkManager.getInstance(this)
                workManager.cancelAllWorkByTag("AnikutaDownloader")
                workManager.cancelUniqueWork("AnikutaDownloader")
                Log.d("AnikutaApp", "✓ Download work cancelled to break crash loop")
                AnikutaCrashHandler.clearLastCrash(this)
            }
        } catch (e: Exception) {
            Log.w("AnikutaApp", "⚠ Could not check for crash loop: ${e.message}")
        }
    }
}
