package app.confused.anikuta.core.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import app.confused.anikuta.core.backup.format.AniyomiBackupFormat
import app.confused.anikuta.core.backup.format.AnikutaBackupFormat
import app.confused.anikuta.core.backup.format.BackupFormatDetector
import app.confused.anikuta.core.backup.model.BackupContainer
import app.confused.anikuta.core.backup.provider.CoverDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "AnikutaBackup"

/**
 * Orchestrates backup creation and restoration.
 *
 * **Create flow:**
 * 1. Collects entries from all selected [BackupProvider]s (via Koin `getAll`).
 * 2. If COVER_IMAGES is selected, downloads cover images via [CoverDownloader].
 * 3. Builds a [BackupContainer] and writes it via [AnikutaBackupFormat].
 *
 * **Restore flow:**
 * 1. Detects the backup format (ANIKUTA zip vs Aniyomi protobuf) via
 *    [BackupFormatDetector].
 * 2. Reads the [BackupContainer] via the matching [BackupFormat].
 * 3. Validates the schema version.
 * 4. For each entry, finds the matching provider by `providerId` and calls
 *    `import()`. Missing providers or entries are skipped gracefully.
 * 5. Returns a [RestoreSummary] with per-category results.
 *
 * All operations run on [Dispatchers.IO]. Errors are logged (tag `AnikutaBackup`)
 * and never crash the app — they're returned as [BackupResult.Error].
 *
 * @param providers all registered backup providers (injected via Koin).
 * @param coverDownloader HTTP cover image downloader.
 */
class BackupManager(
    private val providers: List<BackupProvider>,
    private val coverDownloader: CoverDownloader,
) {

    private val anikutaFormat = AnikutaBackupFormat()
    private val aniyomiFormat = AniyomiBackupFormat()

    /**
     * Creates a backup and writes it to the output stream.
     *
     * @param options what categories to include + which format.
     * @param output the destination stream (caller closes).
     * @return [BackupResult.Success] with a [CreateSummary], or [BackupResult.Error].
     */
    suspend fun createBackup(options: BackupOptions, output: OutputStream): BackupResult<CreateSummary> =
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "═══ Creating backup ═══")
                Log.i(TAG, "  Selected categories: ${options.categories}")
                Log.i(TAG, "  Format: ${options.format}")
                Log.i(TAG, "  Registered providers: ${providers.size} — ids=${providers.map { it.id }}")

                val entries = mutableListOf<BackupEntry>()
                val categoryResults = mutableListOf<CreateCategoryResult>()
                var totalItems = 0

                // Collect entries from selected providers
                providers.forEach { provider ->
                    if (options.includes(provider.id)) {
                        try {
                            Log.d(TAG, "  → Exporting provider: ${provider.id}")
                            val entry = provider.export()
                            val itemCount = countItems(entry)
                            entries.add(entry)
                            totalItems += itemCount
                            val category = BackupCategory.fromId(provider.id)
                            if (category != null) {
                                categoryResults.add(CreateCategoryResult(category, itemCount))
                            }
                            Log.i(TAG, "    ✓ ${provider.id}: $itemCount items")
                        } catch (e: Exception) {
                            Log.e(TAG, "    ✗ ${provider.id}: export FAILED", e)
                            // Continue — one provider failing shouldn't abort the whole backup
                        }
                    } else {
                        Log.d(TAG, "  ⊘ ${provider.id}: not selected, skipping")
                    }
                }

                Log.i(TAG, "  Total entries collected: ${entries.size}")
                Log.i(TAG, "  Total items: $totalItems")

                // Download cover images if selected
                val covers = mutableMapOf<Int, ByteArray>()
                val coverEntry = entries.firstOrNull { it is BackupEntry.CoverImages } as? BackupEntry.CoverImages
                if (coverEntry != null && coverEntry.covers.isNotEmpty()) {
                    Log.i(TAG, "  Downloading ${coverEntry.covers.size} cover images...")
                    val urlMap = coverEntry.covers.mapNotNull { (idStr, url) ->
                        idStr.toIntOrNull()?.let { it to url }
                    }.toMap()
                    covers.putAll(coverDownloader.downloadAll(urlMap))
                    Log.i(TAG, "  Covers downloaded: ${covers.size}/${urlMap.size}")
                }

                // Build + write the container
                val container = BackupContainer(
                    schemaVersion = BackupContainer.CURRENT_SCHEMA_VERSION,
                    createdAt = System.currentTimeMillis(),
                    appVersion = getAppVersion(),
                    deviceName = android.os.Build.MODEL,
                    entries = entries,
                )
                Log.i(TAG, "  Writing container with ${container.entries.size} entries...")
                anikutaFormat.write(container, covers, output)

                Log.i(TAG, "═══ Backup created: ${entries.size} entries, $totalItems items, ${covers.size} covers ═══")
                BackupResult.Success(CreateSummary(
                    filePath = "", // set by the caller (BackupStorage knows the URI)
                    sizeBytes = 0, // set by the caller
                    categoryCount = entries.size,
                    itemCount = totalItems,
                    createdAt = container.createdAt,
                    categories = categoryResults,
                ))
            } catch (e: BackupException) {
                Log.e(TAG, "Backup creation failed: ${e.message}", e)
                BackupResult.Error(e.message ?: "Backup creation failed", e)
            } catch (e: Exception) {
                Log.e(TAG, "Backup creation failed (unexpected)", e)
                BackupResult.Error("Backup creation failed: ${e.message}", e)
            }
        }

    /**
     * Detects the format of a backup file and returns a summary without
     * restoring. Used by the UI to show the user what will be restored before
     * they confirm.
     *
     * @param input the backup file stream (caller closes).
     * @return [BackupResult.Success] with a [RestoreSummary], or [BackupResult.Error].
     */
    suspend fun readSummary(input: InputStream): BackupResult<RestoreSummary> = withContext(Dispatchers.IO) {
        try {
            val bytes = input.readBytes()
            Log.i(TAG, "═══ Reading backup summary ═══")
            Log.i(TAG, "  File size: ${bytes.size} bytes")

            val formatType = BackupFormatDetector.detect(bytes)
                ?: throw BackupException.UnknownFormat("unrecognized magic bytes")
            Log.i(TAG, "  Detected format: $formatType")

            val format = when (formatType) {
                BackupFormatType.ANIKUTA -> anikutaFormat
                BackupFormatType.ANIYOMI -> aniyomiFormat
            }
            val container = format.read(bytes.inputStream())

            // Validate schema version
            if (container.schemaVersion !in BackupContainer.SUPPORTED_VERSIONS) {
                throw BackupException.UnsupportedVersion(
                    container.schemaVersion,
                    BackupContainer.SUPPORTED_VERSIONS,
                )
            }

            Log.i(TAG, "  Container: schema=${container.schemaVersion}, ${container.entries.size} entries, createdAt=${container.createdAt}")

            // Build a summary from the entries — show ALL categories present in the backup
            val categoryResults = container.entries.map { entry ->
                val category = BackupCategory.fromId(entry.providerId)
                val itemCount = countItems(entry)
                Log.i(TAG, "    • ${entry.providerId}: $itemCount items ${if (category == null) "(UNKNOWN CATEGORY)" else ""}")
                RestoreCategoryResult(
                    category = category ?: BackupCategory.PREFERENCES, // fallback
                    importedCount = 0, // not yet imported
                    skippedCount = itemCount,
                    errorCount = 0,
                    note = if (category == null) "Unknown provider: ${entry.providerId}" else null,
                )
            }

            BackupResult.Success(RestoreSummary(
                formatType = formatType,
                createdAt = container.createdAt,
                categoryResults = categoryResults,
            ))
        } catch (e: BackupException) {
            Log.e(TAG, "Read summary failed: ${e.message}", e)
            BackupResult.Error(e.message ?: "Failed to read backup", e)
        } catch (e: Exception) {
            Log.e(TAG, "Read summary failed (unexpected)", e)
            BackupResult.Error("Failed to read backup: ${e.message}", e)
        }
    }

    /**
     * Restores a backup from an input stream.
     *
     * @param input the backup file stream (caller closes).
     * @param options which categories to restore (if null, restores all).
     * @return [BackupResult.Success] with a [RestoreSummary], or [BackupResult.Error].
     */
    suspend fun restoreBackup(
        input: InputStream,
        options: BackupOptions? = null,
    ): BackupResult<RestoreSummary> = withContext(Dispatchers.IO) {
        try {
            val bytes = input.readBytes()
            val formatType = BackupFormatDetector.detect(bytes)
                ?: throw BackupException.UnknownFormat("unrecognized magic bytes")

            val format = when (formatType) {
                BackupFormatType.ANIKUTA -> anikutaFormat
                BackupFormatType.ANIYOMI -> aniyomiFormat
            }
            val container = format.read(bytes.inputStream())

            // Validate schema version
            if (container.schemaVersion !in BackupContainer.SUPPORTED_VERSIONS) {
                throw BackupException.UnsupportedVersion(
                    container.schemaVersion,
                    BackupContainer.SUPPORTED_VERSIONS,
                )
            }

            Log.i(TAG, "═══ Restoring backup ═══")
            Log.i(TAG, "  Format: $formatType")
            Log.i(TAG, "  Container: ${container.entries.size} entries, created=${container.createdAt}")
            Log.i(TAG, "  Registered providers: ${providers.size} — ids=${providers.map { it.id }}")

            // Build a provider lookup map
            val providerMap = providers.associateBy { it.id }

            val categoryResults = mutableListOf<RestoreCategoryResult>()
            var totalImported = 0
            var totalSkipped = 0
            var totalErrors = 0

            container.entries.forEach { entry ->
                val category = BackupCategory.fromId(entry.providerId)
                val provider = providerMap[entry.providerId]

                if (provider == null) {
                    Log.w(TAG, "  ✗ ${entry.providerId}: no provider registered — skipping")
                    categoryResults.add(RestoreCategoryResult(
                        category = category ?: BackupCategory.PREFERENCES,
                        importedCount = 0,
                        skippedCount = countItems(entry),
                        errorCount = 0,
                        note = "No provider for '${entry.providerId}'",
                    ))
                    totalSkipped += countItems(entry)
                    return@forEach
                }

                // If options specifies categories, check if this one is included
                if (options != null && !options.includes(entry.providerId)) {
                    Log.d(TAG, "  ⊘ ${entry.providerId}: not selected — skipping")
                    categoryResults.add(RestoreCategoryResult(
                        category = category!!,
                        importedCount = 0,
                        skippedCount = countItems(entry),
                        errorCount = 0,
                        note = "Not selected for restore",
                    ))
                    totalSkipped += countItems(entry)
                    return@forEach
                }

                try {
                    val success = provider.import(entry)
                    val itemCount = countItems(entry)
                    if (success) {
                        Log.i(TAG, "  ✓ ${entry.providerId}: restored ($itemCount items)")
                        categoryResults.add(RestoreCategoryResult(
                            category = category!!,
                            importedCount = itemCount,
                            skippedCount = 0,
                            errorCount = 0,
                        ))
                        totalImported += itemCount
                    } else {
                        Log.w(TAG, "  ~ ${entry.providerId}: nothing imported (empty or skipped)")
                        categoryResults.add(RestoreCategoryResult(
                            category = category!!,
                            importedCount = 0,
                            skippedCount = itemCount,
                            errorCount = 0,
                            note = "Nothing to import",
                        ))
                        totalSkipped += itemCount
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "  ✗ ${entry.providerId}: import failed", e)
                    val itemCount = countItems(entry)
                    categoryResults.add(RestoreCategoryResult(
                        category = category!!,
                        importedCount = 0,
                        skippedCount = 0,
                        errorCount = itemCount,
                        note = e.message,
                    ))
                    totalErrors += itemCount
                }
            }

            val summary = RestoreSummary(
                formatType = formatType,
                createdAt = container.createdAt,
                categoryResults = categoryResults,
                totalImported = totalImported,
                totalSkipped = totalSkipped,
                totalErrors = totalErrors,
            )
            Log.i(TAG, "Restore complete: $totalImported imported, $totalSkipped skipped, $totalErrors errors")
            BackupResult.Success(summary)
        } catch (e: BackupException) {
            Log.e(TAG, "Restore failed: ${e.message}", e)
            BackupResult.Error(e.message ?: "Restore failed", e)
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed (unexpected)", e)
            BackupResult.Error("Restore failed: ${e.message}", e)
        }
    }

    /** Counts the approximate number of data items in an entry (for summary display). */
    private fun countItems(entry: BackupEntry): Int {
        return when (entry) {
            is BackupEntry.Library -> entry.animes.size
            is BackupEntry.AnimeDetails -> entry.animes.size
            is BackupEntry.Episodes -> entry.byAnime.values.sumOf { it.size }
            is BackupEntry.EpisodeMetadata -> entry.byAnime.values.sumOf { it.size }
            is BackupEntry.WatchProgress -> entry.progress.entries.size
            is BackupEntry.SourceLinks -> entry.links.sourceLinks.size + entry.links.extensionLinks.size
            is BackupEntry.Tracker -> entry.data.bindings.size
            is BackupEntry.Categories -> entry.categories.size + entry.links.size
            is BackupEntry.Preferences -> entry.prefs.entries.size
            is BackupEntry.CoverImages -> entry.covers.size
        }
    }

    private fun getAppVersion(): String {
        return try {
            "1.0.0" // Placeholder — could read from BuildConfig if needed
        } catch (e: Exception) {
            "unknown"
        }
    }
}
