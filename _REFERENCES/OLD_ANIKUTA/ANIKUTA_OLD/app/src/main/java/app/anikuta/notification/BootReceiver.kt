package app.anikuta.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase N-1 (Notifications) — Re-schedules the release tracker after device reboot.
 *
 * WorkManager one-time jobs do NOT survive a device reboot. This receiver
 * catches BOOT_COMPLETED and re-schedules the next check via [ReleaseCheckPlanner].
 *
 * Also handles:
 *  - MY_PACKAGE_REPLACED (app updated — re-schedule)
 *  - There's also a daily fallback periodic worker that catches anything missed
 *    (added in Phase N-2 or N-7).
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.d(TAG, "onReceive: ${intent.action} — re-scheduling release tracker")
                try {
                    val planner: ReleaseCheckPlanner = Injekt.get()
                    planner.scheduleNextCheck()
                    Log.d(TAG, "✓ Release tracker re-scheduled after boot/update")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to re-schedule release tracker", e)
                }
            }
        }
    }
}
