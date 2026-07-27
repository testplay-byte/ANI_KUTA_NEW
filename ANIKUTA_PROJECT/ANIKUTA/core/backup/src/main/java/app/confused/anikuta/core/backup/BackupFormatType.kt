package app.confused.anikuta.core.backup

/**
 * The backup file format type.
 *
 * - [ANIKUTA] — our custom format (gzipped JSON in a zip container, `.anikuta`
 *   extension). Used for both create and restore.
 * - [ANIYOMI] — Aniyomi's protobuf format (`.tachibk`). Restore-only; we never
 *   export in this format.
 */
enum class BackupFormatType(val extension: String, val displayName: String) {
    ANIKUTA(extension = "anikuta", displayName = "ANIKUTA format (recommended)"),
    ANIYOMI(extension = "tachibk", displayName = "Aniyomi format (compatibility)"),
}
