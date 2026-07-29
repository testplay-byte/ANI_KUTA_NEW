package app.confused.anikuta.migration

import android.util.Log
import app.confused.anikuta.core.download.DownloadStore
import app.confused.anikuta.core.download.DownloadTask
import app.confused.anikuta.core.download.DownloadStorageProvider

/**
 * Migrates the download system from the legacy anilistId-based identity to the
 * new content_id-based identity (Phase 6, ADR-050).
 *
 * # What it does
 *
 * 1. **Re-keys DownloadStore tasks:** For each persisted task where
 *    `anime.contentId` is empty (legacy data with `legacyAnilistId` instead),
 *    derives `contentId = "al:$legacyAnilistId"` + writes the updated list back.
 *    This must happen BEFORE the DownloadQueue reads the store (otherwise the
 *    queue would have tasks with empty contentIds).
 *
 * 2. **Moves on-disk folders:** Renames each anime folder from
 *    `<Title [anilistId]>` (e.g., `Frieren [154587]`) to
 *    `<Title [al-anilistId]>` (e.g., `Frieren [al-154587]`). This is a best-effort
 *    SAF `DocumentFile.renameTo` — if it fails (provider doesn't support rename,
 *    or the folder is locked), the old folder stays + the source-switching
 *    filesystem fallback won't find it (the user would need to re-download).
 *    Failures are logged but don't block the migration.
 *
 * # When it runs
 *
 * On first launch post-Phase-6-update, gated by `pref_download_migration_v1_done`.
 * Runs AFTER the Phase 4 metadata/source-link migration (so animes have
 * content_id populated for any lookups).
 *
 * # Idempotency
 *
 * - Task re-keying: tasks with non-empty `contentId` are skipped.
 * - Folder moves: folders ending with `[al-...]` are skipped (already migrated).
 *
 * # Crash resistance
 *
 * The task re-keying is atomic (`store.setAll` at the end). Folder moves are
 * per-folder try/catch — one failure doesn't block the others.
 *
 * Per `_ARCHITECTURE_PLAN/proposals/05_migration_strategy.md` §3 (Step 5).
 */
class DownloadMigration(
    private val downloadStore: DownloadStore,
    private val storageProvider: DownloadStorageProvider,
) {

    /**
     * Run the migration.
     *
     * @return counts of what was migrated.
     */
    suspend fun migrate(): Result {
        Log.i(TAG, "Download migration: starting")
        val taskResult = migrateTasks()
        val folderResult = migrateFolders()
        val result = Result(taskResult.migrated, taskResult.skipped, folderResult.moved, folderResult.failed)
        Log.i(TAG, "Download migration: complete — $result")
        return result
    }

    /**
     * Re-key DownloadStore tasks from legacy anilistId to content_id.
     */
    private fun migrateTasks(): TaskResult {
        val tasks = downloadStore.getAll()
        if (tasks.isEmpty()) {
            Log.i(TAG, "No download tasks to migrate")
            return TaskResult(0, 0)
        }

        var migrated = 0
        var skipped = 0
        val updated = mutableListOf<DownloadTask>()

        for (task in tasks) {
            val anime = task.request.anime
            if (anime.contentId.isNotEmpty()) {
                // Already migrated (or new data).
                updated.add(task)
                skipped++
                continue
            }

            val legacyAnilistId = anime.legacyAnilistId
            if (legacyAnilistId == null || legacyAnilistId <= 0) {
                Log.w(TAG, "Task ${task.id}: cannot derive contentId " +
                    "(contentId='${anime.contentId}', legacyAnilistId=$legacyAnilistId) — skipping")
                updated.add(task)
                skipped++
                continue
            }

            val newContentId = "al:$legacyAnilistId"
            val newAnime = anime.copy(contentId = newContentId, legacyAnilistId = null)
            val newRequest = task.request.copy(anime = newAnime)
            updated.add(task.copy(request = newRequest))
            migrated++
            Log.d(TAG, "Task ${task.id}: migrated anilistId=$legacyAnilistId → contentId=$newContentId")
        }

        if (migrated > 0) {
            downloadStore.setAll(updated)
            Log.i(TAG, "Tasks: migrated=$migrated, skipped=$skipped, total=${tasks.size}")
        } else {
            Log.i(TAG, "Tasks: no migration needed (migrated=0, skipped=$skipped)")
        }
        return TaskResult(migrated, skipped)
    }

    /**
     * Move on-disk anime folders from `<Title [anilistId]>` to
     * `<Title [al-anilistId]>`.
     *
     * Best-effort: SAF `DocumentFile.renameTo` is provider-dependent. Failures
     * are logged but don't block the migration.
     *
     * Delegates to [DownloadStorageProvider.renameLegacyAnimeFolder] (which has
     * the DocumentFile dependency; `:app` doesn't).
     */
    private fun migrateFolders(): FolderResult {
        val tasks = downloadStore.getAll()
        val toMigrate = tasks
            .filter { it.request.anime.contentId.isNotEmpty() }
            .map { it.request.anime.contentId to it.request.anime.title }
            .distinct()
            .filter { (contentId, _) -> contentId.startsWith("al:") }

        if (toMigrate.isEmpty()) {
            Log.i(TAG, "No folders to migrate")
            return FolderResult(0, 0)
        }

        var moved = 0
        var failed = 0

        for ((contentId, title) in toMigrate) {
            try {
                val anilistId = contentId.removePrefix("al:").toIntOrNull()
                if (anilistId == null) continue

                val oldSuffix = "[$anilistId]"
                val renamed = storageProvider.renameLegacyAnimeFolder(oldSuffix, contentId, title)
                if (renamed) {
                    moved++
                } else {
                    // Could be: folder not found (already migrated or never downloaded),
                    // or rename failed (provider doesn't support it).
                    failed++
                }
            } catch (e: Exception) {
                failed++
                Log.e(TAG, "Folder migration failed for contentId=$contentId", e)
            }
        }

        Log.i(TAG, "Folders: moved=$moved, failed=$failed, total=${toMigrate.size}")
        return FolderResult(moved, failed)
    }

    private data class TaskResult(val migrated: Int, val skipped: Int)
    private data class FolderResult(val moved: Int, val failed: Int)

    /** Result of the migration. */
    data class Result(
        val tasksMigrated: Int,
        val tasksSkipped: Int,
        val foldersMoved: Int,
        val foldersFailed: Int,
    )

    private companion object {
        private const val TAG = "AnikutaDownloadMigrator"
    }
}
