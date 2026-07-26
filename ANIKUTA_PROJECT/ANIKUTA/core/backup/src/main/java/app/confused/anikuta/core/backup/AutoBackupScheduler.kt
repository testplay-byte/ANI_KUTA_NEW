package app.confused.anikuta.core.backup

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val TAG = "AnikutaBackup"

/**
 * Schedules/cancels the [AutoBackupWorker] via WorkManager.
 *
 * Call [reschedule] whenever the auto-backup settings change (enabled,
 * frequency). WorkManager handles the periodic execution — the worker runs
 * even if the app is closed (subject to system battery optimizations).
 *
 * **Minimum interval:** WorkManager enforces a 15-minute minimum for periodic
 * work. All [AutoBackupFrequency] values are well above this.
 *
 * **ExistingPeriodicWorkPolicy:** uses [ExistingPeriodicWorkPolicy.UPDATE] so
 * changing the frequency replaces the existing schedule without losing the
 * next-fire-time estimate.
 */
class AutoBackupScheduler(
    private val context: Context,
) {

    /**
     * Reschedules the auto-backup worker based on current preferences.
     *
     * - If auto-backup is enabled: enqueues a periodic work request with the
     *   configured frequency.
     * - If disabled: cancels any existing periodic work.
     *
     * @param enabled whether auto-backup is on.
     * @param frequency the backup frequency.
     */
    fun reschedule(enabled: Boolean, frequency: AutoBackupFrequency) {
        val workManager = WorkManager.getInstance(context)

        if (!enabled) {
            workManager.cancelUniqueWork(AutoBackupWorker.WORK_NAME)
            Log.i(TAG, "Auto-backup scheduler: cancelled (disabled)")
            return
        }

        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            frequency.intervalHours,
            TimeUnit.HOURS,
        ).build()

        workManager.enqueueUniquePeriodicWork(
            AutoBackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        Log.i(TAG, "Auto-backup scheduler: scheduled every ${frequency.intervalHours}h")
    }
}
