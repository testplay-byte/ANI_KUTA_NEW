package app.confused.anikuta.core.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext

private const val TAG = "AnikutaBackup"

/**
 * WorkManager periodic worker that performs automatic backups.
 *
 * Reads [BackupPreferences] for the auto-backup category selection, creates a
 * backup via [BackupManager], and writes it to the `ANIKUTA/auto_backup/`
 * directory. Runs on a periodic schedule (see [AutoBackupScheduler]).
 *
 * If no folder is selected or auto-backup is disabled, the worker exits with
 * [Result.success] (no-op) — it doesn't retry, since the condition won't change
 * without user action.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "AutoBackupWorker: starting")
        return try {
            val koin = GlobalContext.get()
            val backupManager = koin.get<BackupManager>()
            val storage = koin.get<BackupStorage>()
            val preferences = koin.get<BackupPreferences>()

            // Check if a folder is selected
            if (!storage.hasFolder()) {
                Log.w(TAG, "AutoBackupWorker: no folder selected — skipping")
                return Result.success()
            }

            // Build options from auto-backup preferences
            val categories = preferences.autoCategories.get()
            val options = BackupOptions(
                categories = categories,
                format = BackupFormatType.ANIKUTA,
            )

            // Create the backup file
            val fileName = storage.generateBackupName(isAuto = true)
            val output = storage.createAutoBackupFile(fileName)
            if (output == null) {
                Log.e(TAG, "AutoBackupWorker: failed to create output file")
                return Result.success() // Don't retry — likely a folder permission issue
            }

            output.use { stream ->
                when (val result = backupManager.createBackup(options, stream)) {
                    is BackupResult.Success -> {
                        preferences.lastAutoBackup.set(System.currentTimeMillis())
                        Log.i(TAG, "AutoBackupWorker: success — ${result.data.itemCount} items")

                        // Enforce the retention limit — delete old auto-backups beyond maxKeep
                        val maxKeep = preferences.autoMaxKeep.get()
                        val deleted = storage.cleanupOldAutoBackups(maxKeep)
                        if (deleted > 0) {
                            Log.i(TAG, "AutoBackupWorker: cleaned up $deleted old auto-backup(s), keeping $maxKeep")
                        }
                    }
                    is BackupResult.Error -> {
                        Log.e(TAG, "AutoBackupWorker: failed — ${result.message}")
                    }
                    is BackupResult.InProgress -> { /* shouldn't happen */ }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "AutoBackupWorker: exception", e)
            Result.success() // Don't retry indefinitely — log + move on
        }
    }

    companion object {
        const val WORK_NAME = "anikuta_auto_backup"
    }
}
