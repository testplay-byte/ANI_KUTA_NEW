package app.anikuta.notification

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.workDataOf
import app.anikuta.data.cache.ReleaseTrackingStore
import java.util.concurrent.TimeUnit

/**
 * Phase N-1 (Notifications) — Plans and schedules the next release-tracking check.
 *
 * This is the scheduler. It does NOT do the checking itself — it computes WHEN
 * to check and schedules a [ReleaseTrackerWorker] via WorkManager.
 *
 * The timing uses the weighted-average formula (§2.5 of the plan):
 *   expectedCheck = (anilistAiringAt × 1 + predictedExtRelease × 2) / 3 + graceBuffer
 *
 * where predictedExtRelease = anilistAiringAt + rollingAverageOffset (learned
 * from past observations stored in ReleaseTrackingStore).
 *
 * Scheduling strategy:
 *  - ONE-TIME work, dynamically scheduled (not periodic)
 *  - Finds the earliest nextScheduledCheck across all tracked anime
 *  - Schedules a single worker for that time
 *  - When the worker fires, it checks all due anime, then re-schedules
 *  - This is the most battery-efficient approach (WorkManager only wakes when needed)
 */
class ReleaseCheckPlanner(
    private val context: Context,
    private val store: ReleaseTrackingStore,
) {

    companion object {
        private const val TAG = "ReleaseCheckPlanner"
        private const val UNIQUE_WORK_NAME = "AnikutaReleaseTracker"

        // System-level timing constants (NOT user-facing — see plan §2.2)
        /** Grace buffer added after the expected release time before first check. */
        const val GRACE_BUFFER_MS = 10L * 60 * 1000  // 10 minutes
        /** Retry interval if the episode is not yet available. */
        const val RETRY_INTERVAL_MS = 10L * 60 * 1000  // 10 minutes
        /** Max retry window before giving up. */
        const val MAX_RETRY_WINDOW_MS = 5L * 60 * 60 * 1000  // 5 hours
        /** Fallback daily check for anime with no AniList airing schedule. */
        const val FALLBACK_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000  // 24 hours

        /** Weight for AniList airing time in the weighted average. */
        const val ANILIST_WEIGHT = 1L
        /** Weight for extension-predicted release time in the weighted average. */
        const val EXTENSION_WEIGHT = 2L
    }

    /**
     * Compute the next check time for a single tracked anime.
     *
     * @param anime The tracked anime state.
     * @param anilistAiringAt The AniList airing time for the next episode (epoch millis), or 0 if unknown.
     * @param now Current time (epoch millis).
     * @return The time to schedule the next check (epoch millis).
     */
    fun computeNextCheckTime(
        anime: ReleaseTrackingStore.TrackedAnime,
        anilistAiringAt: Long,
        now: Long,
    ): Long {
        // If no AniList schedule, fall back to daily polling.
        if (anilistAiringAt <= 0L) {
            return now + FALLBACK_CHECK_INTERVAL_MS
        }

        // If we have a learned offset, use the weighted average.
        val offset = anime.subReleaseOffsetMs  // use sub offset by default
        val predictedExtRelease = anilistAiringAt + offset
        val weightedAverage = (anilistAiringAt * ANILIST_WEIGHT + predictedExtRelease * EXTENSION_WEIGHT) /
            (ANILIST_WEIGHT + EXTENSION_WEIGHT)

        val expectedCheck = weightedAverage + GRACE_BUFFER_MS

        // If the expected check is in the past (e.g. we missed it), check ASAP
        // but not in the past — give it 1 minute.
        return maxOf(expectedCheck, now + 60_000L)
    }

    /**
     * Schedule the next release-tracking check.
     *
     * Looks at all tracked anime, finds the earliest nextScheduledCheck,
     * and schedules a single WorkManager one-time job for that time.
     *
     * Call this:
     *  - On app start
     *  - After a library change (add/remove)
     *  - After a check completes (to re-schedule)
     *  - After device boot (from BootReceiver)
     */
    fun scheduleNextCheck() {
        val earliestCheck = store.getEarliestNextCheck()
        val now = System.currentTimeMillis()

        val delayMs = if (earliestCheck > now) {
            earliestCheck - now
        } else {
            // Something is due now or in the past — check ASAP (1 min minimum delay)
            60_000L
        }

        Log.d(TAG, "scheduleNextCheck: earliest=$earliestCheck, now=$now, delay=${delayMs}ms (${delayMs / 60000} min)")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ReleaseTrackerWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag("AnikutaReleaseTracker")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )

        Log.d(TAG, "✓ Scheduled release tracker work in ${delayMs / 60000} min")
    }

    /**
     * Cancel all scheduled release-tracking work.
     * Called when the user disables notifications globally.
     */
    fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        Log.d(TAG, "✓ Cancelled all release tracker work")
    }
}
