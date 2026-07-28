package app.confused.anikuta.core.download

import android.content.Context
import java.io.File

/**
 * Manages the app-internal cache directory for in-progress downloads.
 *
 * **Why internal storage first?** Per the owner's requirement: the video
 * downloads to the app's internal cache directory first (fast, private, no SAF
 * overhead per byte). Only after the download is fully validated (correct
 * content-type, non-trivial file size, not corrupt) is it copied to the user's
 * selected SAF folder. Benefits:
 *
 *  - **No pollution.** Partial/corrupt downloads never appear in the user's
 *    folder. If the download fails or is cancelled, we just delete the temp
 *    file — the user's folder stays clean.
 *  - **Performance.** Writing to internal cache is faster than SAF per-byte
 *    writes (no ContentResolver round-trips per buffer flush). The final copy
 *    to SAF is a single sequential write.
 *  - **Validation.** We can inspect the downloaded bytes (content-type, size,
 *    magic bytes) BEFORE committing to the user's folder.
 *  - **Atomicity.** The user's folder only ever contains complete, valid files.
 *
 * Directory layout (internal cache, NOT user-selected):
 * ```
 * <cacheDir>/anikuta_downloads/<taskId>/
 *   video.<ext>          ← the temp video file
 *   subtitles/
 *     <lang>_0.<ext>     ← temp subtitle files
 *   metadata.json        ← temp metadata
 * ```
 *
 * Cleaned up on: download completion (after the SAF copy), download failure,
 * cancellation, and app startup (stale temp dirs from a previous crash).
 */
class TempDownloadCache(
    context: Context,
) {
    private val rootDir = File(context.cacheDir, "anikuta_downloads").also { it.mkdirs() }

    /** The temp directory for a specific task. Created if it doesn't exist. */
    fun taskDir(taskId: Long): File {
        return File(rootDir, taskId.toString()).also { it.mkdirs() }
    }

    /** The temp video file for a task. */
    fun videoFile(taskId: Long, extension: String): File {
        return File(taskDir(taskId), "video.$extension")
    }

    /** The temp subtitles directory for a task. Created if it doesn't exist. */
    fun subtitlesDir(taskId: Long): File {
        return File(taskDir(taskId), "subtitles").also { it.mkdirs() }
    }

    /** The temp metadata file for a task. */
    fun metadataFile(taskId: Long): File {
        return File(taskDir(taskId), "metadata.json")
    }

    /** Delete the entire temp directory for a task (on completion/failure/cancel). */
    fun cleanupTask(taskId: Long) {
        try {
            val dir = File(rootDir, taskId.toString())
            if (dir.exists()) {
                dir.deleteRecursively()
                DownloadLogger.d("Cleaned up temp dir for task $taskId")
            }
        } catch (e: Exception) {
            DownloadLogger.w("Failed to clean up temp dir for task $taskId (non-fatal)", e)
        }
    }

    /**
     * Delete ALL stale temp directories (call on app startup). This handles
     * temp dirs left behind by a previous crash/force-kill during download.
     */
    fun cleanupStale() {
        try {
            val taskDirs = rootDir.listFiles { f -> f.isDirectory } ?: return
            var cleaned = 0
            for (dir in taskDirs) {
                if (dir.deleteRecursively()) cleaned++
            }
            if (cleaned > 0) {
                DownloadLogger.i("Cleaned up $cleaned stale temp download dir(s) on startup")
            }
        } catch (e: Exception) {
            DownloadLogger.w("Stale temp cleanup failed (non-fatal)", e)
        }
    }
}
