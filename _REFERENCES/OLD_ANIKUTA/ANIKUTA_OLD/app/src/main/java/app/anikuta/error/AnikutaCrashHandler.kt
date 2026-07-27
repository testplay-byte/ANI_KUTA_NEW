package app.anikuta.error

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ANI-KUTA global crash handler.
 *
 * Installs as the process-wide [Thread.UncaughtExceptionHandler]. When any
 * thread throws an uncaught exception, this handler:
 *   1. Writes a human-readable crash report to `filesDir/last_crash.txt`.
 *   2. Launches [ErrorActivity] (NEW_TASK | CLEAR_TASK) so the user sees a
 *      proper error screen instead of the app silently vanishing.
 *   3. Kills the current process so a fresh one starts for ErrorActivity.
 *
 * The report persists across the process restart (it's a file, not an in-memory
 * field), so ErrorActivity can read it on the next launch. The previous report
 * is cleared when the user taps "Restart" or "Close" on the error screen.
 *
 * This is NOT a crash-reporting SDK (no upload, no analytics) — it's purely a
 * UX safety net so the user gets a copyable error message + recovery options
 * instead of a silent crash to the home screen.
 */
class AnikutaCrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun uncaughtException(t: Thread, e: Throwable) {
        Log.e(TAG, "Uncaught exception on thread ${t.name}", e)
        // 1. Persist the crash report so ErrorActivity can read it.
        try {
            val report = buildReport(t, e)
            File(context.filesDir, CRASH_FILE).writeText(report)
        } catch (ioe: Exception) {
            Log.e(TAG, "Failed to write crash report file", ioe)
        }
        // 2. Launch ErrorActivity SYNCHRONOUSLY (not via mainHandler.post).
        //    When the crash is on the main thread (e.g. a Compose layout
        //    exception), the main Looper is already dead — a posted runnable
        //    would never execute, and the system would show its default crash
        //    dialog instead of our ErrorActivity. startActivity with NEW_TASK
        //    is safe to call from any thread.
        try {
            val intent = Intent(context, ErrorActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
        } catch (ie: Exception) {
            Log.e(TAG, "Failed to launch ErrorActivity", ie)
        }
        // 3. Kill the current process so a fresh one starts for ErrorActivity.
        Process.killProcess(Process.myPid())
        // Fallback in case killProcess returns (it usually doesn't).
        System.exit(10)
    }

    private fun buildReport(t: Thread, e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        return buildString {
            appendLine("=== ANI-KUTA Crash Report ===")
            appendLine("Time: $time")
            appendLine("Thread: ${t.name} (id=${t.id})")
            appendLine("Process PID: ${Process.myPid()}")
            appendLine("Android API: ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.VERSION.RELEASE})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine()
            appendLine("Exception: ${e.javaClass.name}")
            appendLine("Message: ${e.message}")
            appendLine()
            appendLine("Stack trace:")
            appendLine(sw.toString())
        }
    }

    companion object {
        private const val TAG = "AnikutaCrash"
        const val CRASH_FILE = "last_crash.txt"

        /** Read the most recent crash report, or null if there isn't one. */
        fun getLastCrash(context: Context): String? = try {
            val file = File(context.filesDir, CRASH_FILE)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            null
        }

        /** Delete the crash report (called after the user acknowledges it). */
        fun clearLastCrash(context: Context) {
            try {
                File(context.filesDir, CRASH_FILE).delete()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete crash report", e)
            }
        }
    }
}
