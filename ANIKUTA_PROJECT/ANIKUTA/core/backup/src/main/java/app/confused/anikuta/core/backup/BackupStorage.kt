package app.confused.anikuta.core.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "AnikutaBackup"
private const val ROOT_DIR = "ANIKUTA"
private const val BACKUPS_DIR = "backups"
private const val AUTO_BACKUP_DIR = "auto_backup"

/**
 * Manages the user-selected SAF folder and the ANIKUTA directory structure.
 *
 * The user grants access to a folder via `ACTION_OPEN_DOCUMENT_TREE`. This
 * class persists the URI, takes persistable permission, and creates the
 * `ANIKUTA/backups/` and `ANIKUTA/auto_backup/` subdirectories inside it.
 *
 * Directory structure (per FOLDER-STRUCTURE-PLAN.md):
 * ```
 * <USER_SELECTED_FOLDER>/
 * └── ANIKUTA/
 *     ├── backups/       ← manual backup files (.anikuta)
 *     └── auto_backup/   ← automatic backup files (.anikuta)
 * ```
 *
 * All file operations use [DocumentFile] (SAF) — no direct file paths.
 */
class BackupStorage(
    private val context: Context,
    private val preferences: BackupPreferences,
) {

    /**
     * Persists the SAF folder URI + takes persistable read/write permission.
     * Called when the user picks a folder via the SAF picker.
     *
     * @return true if the URI was saved + permission granted.
     */
    fun setFolderUri(uri: Uri): Boolean {
        return try {
            // Take persistable URI permission so we survive app restarts
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            preferences.folderUri.set(uri.toString())
            Log.i(TAG, "Backup folder set: $uri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set backup folder URI", e)
            false
        }
    }

    /** Returns the persisted SAF folder URI, or null if not set. */
    fun getFolderUri(): Uri? {
        val uriStr = preferences.folderUri.get()
        return if (uriStr.isNotBlank()) Uri.parse(uriStr) else null
    }

    /** Returns true if a backup folder has been selected. */
    fun hasFolder(): Boolean = getFolderUri() != null

    /**
     * Gets or creates the `ANIKUTA/backups/` directory inside the selected folder.
     * @return the [DocumentFile] for the backups directory, or null on failure.
     */
    fun getOrCreateBackupsDir(): DocumentFile? {
        val root = getOrCreateRootDir() ?: return null
        return getOrCreateSubDir(root, BACKUPS_DIR)
    }

    /**
     * Gets or creates the `ANIKUTA/auto_backup/` directory inside the selected folder.
     * @return the [DocumentFile] for the auto-backup directory, or null on failure.
     */
    fun getOrCreateAutoBackupDir(): DocumentFile? {
        val root = getOrCreateRootDir() ?: return null
        return getOrCreateSubDir(root, AUTO_BACKUP_DIR)
    }

    /** Gets or creates the `ANIKUTA/` root directory. */
    private fun getOrCreateRootDir(): DocumentFile? {
        val uri = getFolderUri() ?: run {
            Log.w(TAG, "No backup folder selected")
            return null
        }
        val tree = DocumentFile.fromTreeUri(context, uri) ?: run {
            Log.e(TAG, "Cannot access backup folder (URI stale?)")
            return null
        }
        return getOrCreateSubDir(tree, ROOT_DIR)
    }

    /** Gets or creates a subdirectory inside a parent [DocumentFile]. */
    private fun getOrCreateSubDir(parent: DocumentFile, name: String): DocumentFile? {
        return parent.findFile(name) ?: parent.createDirectory(name)
    }

    /**
     * Creates a new backup file in the `backups/` directory and returns its
     * output stream. The caller is responsible for closing the stream.
     *
     * @param fileName the file name (e.g. `backup_20260725_120000.anikuta`).
     * @return the output stream, or null on failure.
     */
    fun createManualBackupFile(fileName: String): OutputStream? {
        return try {
            val dir = getOrCreateBackupsDir() ?: return null
            val file = dir.createFile("application/octet-stream", fileName) ?: run {
                Log.e(TAG, "Failed to create backup file: $fileName")
                return null
            }
            context.contentResolver.openOutputStream(file.uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create manual backup file", e)
            null
        }
    }

    /**
     * Creates a new backup file in the `auto_backup/` directory.
     * @return the output stream, or null on failure.
     */
    fun createAutoBackupFile(fileName: String): OutputStream? {
        return try {
            val dir = getOrCreateAutoBackupDir() ?: return null
            val file = dir.createFile("application/octet-stream", fileName) ?: return null
            context.contentResolver.openOutputStream(file.uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create auto-backup file", e)
            null
        }
    }

    /**
     * Opens an input stream from a content URI (for restore).
     * @return the input stream, or null on failure.
     */
    fun openInput(uri: Uri): InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open backup file: $uri", e)
            null
        }
    }

    /**
     * Lists all backup files (manual + auto) with their sizes + last-modified dates.
     * @return list of [BackupFileInfo], sorted by last-modified descending.
     */
    fun listBackups(): List<BackupFileInfo> {
        val files = mutableListOf<BackupFileInfo>()
        try {
            getOrCreateBackupsDir()?.listFiles()?.forEach { doc ->
                if (doc.isFile && doc.name?.endsWith(".anikuta") == true) {
                    files.add(BackupFileInfo(
                        uri = doc.uri,
                        name = doc.name ?: "unknown",
                        sizeBytes = doc.length(),
                        lastModified = doc.lastModified(),
                        isAuto = false,
                    ))
                }
            }
            getOrCreateAutoBackupDir()?.listFiles()?.forEach { doc ->
                if (doc.isFile && doc.name?.endsWith(".anikuta") == true) {
                    files.add(BackupFileInfo(
                        uri = doc.uri,
                        name = doc.name ?: "unknown",
                        sizeBytes = doc.length(),
                        lastModified = doc.lastModified(),
                        isAuto = true,
                    ))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list backups", e)
        }
        return files.sortedByDescending { it.lastModified }
    }

    /**
     * Computes the total storage used by backup files (manual + auto).
     * @return total bytes, or 0 on failure.
     */
    fun getStorageUsage(): Long {
        return listBackups().sumOf { it.sizeBytes }
    }

    /** Generates a timestamped backup file name. */
    fun generateBackupName(isAuto: Boolean): String {
        val now = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val prefix = if (isAuto) "auto" else "backup"
        return "${prefix}_$now.anikuta"
    }

    /**
     * Deletes old auto-backup files, keeping only the [maxToKeep] most recent.
     *
     * Called by [AutoBackupWorker] after each successful backup to enforce the
     * user-configured retention limit. Files are sorted by last-modified
     * descending; the oldest beyond [maxToKeep] are deleted.
     *
     * @param maxToKeep how many auto-backup files to keep (1-4).
     * @return the number of files deleted.
     */
    fun cleanupOldAutoBackups(maxToKeep: Int): Int {
        if (maxToKeep <= 0) return 0
        try {
            val autoDir = getOrCreateAutoBackupDir() ?: return 0
            val autoFiles = autoDir.listFiles()
                .filter { it.isFile && it.name?.endsWith(".anikuta") == true }
                .sortedByDescending { it.lastModified() }

            if (autoFiles.size <= maxToKeep) {
                Log.d(TAG, "cleanupOldAutoBackups: ${autoFiles.size} files ≤ $maxToKeep, nothing to delete")
                return 0
            }

            val toDelete = autoFiles.drop(maxToKeep)
            var deleted = 0
            toDelete.forEach { doc ->
                try {
                    val name = doc.name ?: "unknown"
                    if (doc.delete()) {
                        deleted++
                        Log.i(TAG, "cleanupOldAutoBackups: deleted old auto-backup: $name")
                    } else {
                        Log.w(TAG, "cleanupOldAutoBackups: failed to delete: $name")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "cleanupOldAutoBackups: error deleting file", e)
                }
            }
            Log.i(TAG, "cleanupOldAutoBackups: kept ${autoFiles.size - deleted}, deleted $deleted")
            return deleted
        } catch (e: Exception) {
            Log.e(TAG, "cleanupOldAutoBackups: failed", e)
            return 0
        }
    }
}

/** Metadata about a backup file on disk. */
data class BackupFileInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isAuto: Boolean,
)
