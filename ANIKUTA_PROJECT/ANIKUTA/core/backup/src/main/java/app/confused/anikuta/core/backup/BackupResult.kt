package app.confused.anikuta.core.backup

/**
 * Result of a backup create or restore operation.
 *
 * Sealed to force exhaustive handling in the UI.
 */
sealed class BackupResult<out T> {

    /** Operation succeeded. Carries the result payload. */
    data class Success<T>(val data: T) : BackupResult<T>()

    /**
     * Operation failed. Carries a user-facing message + optional throwable.
     * The [recoverable] flag indicates whether the user can retry (e.g. re-select
     * a folder) vs. a permanent error (e.g. corrupt backup file).
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val recoverable: Boolean = true,
    ) : BackupResult<Nothing>()

    /** Operation is in progress. Carries a 0..1 progress fraction (or null if indeterminate). */
    data class InProgress(val progress: Float? = null, val message: String = "") : BackupResult<Nothing>()
}

/**
 * Base exception for all backup-related errors.
 *
 * Subclasses allow the UI + BackupManager to handle specific failure modes
 * with appropriate user messaging.
 */
sealed class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The backup file is not a recognized format. */
    class UnknownFormat(detail: String) : BackupException("Unknown backup format: $detail")

    /** The backup file is corrupt or could not be parsed. */
    class CorruptFile(detail: String, cause: Throwable? = null) : BackupException("Corrupt backup file: $detail", cause)

    /** The backup file is a valid format but the schema version is unsupported. */
    class UnsupportedVersion(found: Int, supported: IntRange) :
        BackupException("Backup schema version $found is unsupported (supported: $supported)")

    /** A SAF folder has not been selected. */
    class NoFolderSelected : BackupException("No backup folder selected. Choose a folder in Storage settings.")

    /** A SAF folder URI is stale (the user revoked permission or deleted the folder). */
    class FolderAccessFailed(detail: String, cause: Throwable? = null) :
        BackupException("Cannot access backup folder: $detail", cause)

    /** The Aniyomi backup format is detected but not supported (e.g. protobuf without schema). */
    class AniyomiNotSupported(detail: String) : BackupException("Aniyomi backup not supported: $detail")
}
