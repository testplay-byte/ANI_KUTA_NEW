package app.confused.anikuta.data.extension.installer

/**
 * The lifecycle steps of an extension install / update / uninstall operation.
 *
 * Emitted as a `Flow<InstallStep>` by [app.confused.anikuta.data.extension.AnimeExtensionManager.installExtension]
 * so the UI can render progress. `Idle` is the pre-start state; `Installed` and
 * `Error` are terminal.
 *
 * Ported from the Aniyomi reference's `InstallStep.kt` (shared by manga + anime).
 */
enum class InstallStep {
    /** Not started (or cancelled back to neutral). */
    Idle,

    /** Queued, waiting for the download slot. */
    Pending,

    /** APK is downloading. */
    Downloading,

    /** APK downloaded, PackageInstaller session in progress. */
    Installing,

    /** Install completed successfully. */
    Installed,

    /** Install failed (network error, user cancelled, parse error, etc.). */
    Error;

    /** Whether this step is terminal (no further emissions expected). */
    fun isCompleted(): Boolean = this in setOf(Installed, Error, Idle)
}
