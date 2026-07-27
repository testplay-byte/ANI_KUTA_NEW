package app.anikuta.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase N-1 (Notifications) — WorkManager worker for release tracking.
 *
 * This is the entry point that WorkManager calls when a check is due.
 * It delegates the actual checking to [ReleaseTracker] (implemented in Phase N-2).
 *
 * Flow:
 *  1. WorkManager wakes this worker at the scheduled time.
 *  2. The worker calls ReleaseTracker.checkDueAnime().
 *  3. The tracker checks all due anime, fires notifications, triggers auto-downloads.
 *  4. The planner re-schedules the next check.
 *
 * This is a skeleton in Phase N-1 — the actual detection logic is added in
 * Phase N-2. For now, it just logs and reschedules.
 */
class ReleaseTrackerWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ReleaseTrackerWorker"
    }

    private val planner: ReleaseCheckPlanner = Injekt.get()
    private val store: app.anikuta.data.cache.ReleaseTrackingStore = Injekt.get()
    private val prefs: NotificationPreferences = Injekt.get()

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork: → release tracker worker started")

        // If notifications are globally disabled, don't check — just reschedule
        // in case the user re-enables them.
        if (!prefs.globalNotifyEnabled().get()) {
            Log.d(TAG, "doWork: notifications disabled globally — skipping check, rescheduling")
            planner.scheduleNextCheck()
            return Result.success()
        }

        // Phase N-2: delegate to the ReleaseTracker for actual detection.
        val tracker: ReleaseTracker = Injekt.get()
        val checked = tracker.checkDueAnime()

        // Re-schedule the next check.
        planner.scheduleNextCheck()

        Log.d(TAG, "doWork: ← worker finished (checked $checked anime)")
        return Result.success()
    }
}
