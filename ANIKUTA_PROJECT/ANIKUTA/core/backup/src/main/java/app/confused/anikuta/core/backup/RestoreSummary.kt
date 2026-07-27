package app.confused.anikuta.core.backup

/**
 * Summary of a backup create operation — one entry per category that was backed up.
 *
 * Used by the UI to show the user a rich grid-based summary of what was saved.
 *
 * @param category the backup category that was processed.
 * @param itemCount how many items were backed up in this category.
 */
data class CreateCategoryResult(
    val category: BackupCategory,
    val itemCount: Int,
)

/**
 * Summary of a backup create operation.
 *
 * @param filePath the path/URI of the created backup file.
 * @param sizeBytes the file size in bytes.
 * @param categoryCount how many categories were included.
 * @param itemCount approximate total number of data items backed up.
 * @param createdAt when the backup was created (epoch ms).
 * @param categories per-category breakdown (for the grid success popup).
 */
data class CreateSummary(
    val filePath: String,
    val sizeBytes: Long,
    val categoryCount: Int,
    val itemCount: Int,
    val createdAt: Long,
    val categories: List<CreateCategoryResult> = emptyList(),
)

/**
 * Summary of a restore operation — one entry per category that was processed.
 *
 * Used by the UI to show the user what was restored, what was skipped, and
 * what failed. The [BackupManager] builds this as it iterates providers.
 *
 * @param category the backup category that was processed.
 * @param importedCount how many items were successfully imported.
 * @param skippedCount how many items were skipped (missing data, conflicts, etc.).
 * @param errorCount how many items failed with an error.
 * @param note optional human-readable note (e.g. "3 anime matched by title").
 */
data class RestoreCategoryResult(
    val category: BackupCategory,
    val importedCount: Int,
    val skippedCount: Int,
    val errorCount: Int,
    val note: String? = null,
) {
    /** True if at least one item was imported. */
    val hasResults: Boolean get() = importedCount > 0 || skippedCount > 0 || errorCount > 0
}

/**
 * The full restore summary shown to the user before/after restore.
 *
 * @param formatType the detected format of the backup file.
 * @param createdAt when the backup was originally created (epoch ms), or null if unknown.
 * @param categoryResults per-category results.
 * @param totalImported sum of all [RestoreCategoryResult.importedCount].
 * @param totalSkipped sum of all [RestoreCategoryResult.skippedCount].
 * @param totalErrors sum of all [RestoreCategoryResult.errorCount].
 */
data class RestoreSummary(
    val formatType: BackupFormatType,
    val createdAt: Long? = null,
    val categoryResults: List<RestoreCategoryResult> = emptyList(),
    val totalImported: Int = categoryResults.sumOf { it.importedCount },
    val totalSkipped: Int = categoryResults.sumOf { it.skippedCount },
    val totalErrors: Int = categoryResults.sumOf { it.errorCount },
)
