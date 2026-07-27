package app.anikuta.core.storage

import java.io.File

/**
 * Provides the default app-private storage path. Used as a fallback when the
 * user hasn't picked a custom folder via SAF (Storage Access Framework).
 *
 * The default is the app's external files directory
 * (`context.getExternalFilesDir(null)`), which:
 *  - Is app-private (no permissions needed on API 19+)
 *  - Is automatically cleaned up by the system on uninstall
 *  - Survives app updates
 *
 * When the user picks a custom folder via onboarding, [StoragePreferences]
 * stores its SAF URI and the StorageManager resolves it to a UniFile — but if
 * the URI is unset (first launch, or the user chose "use default"), this
 * provider's path is used instead.
 */
interface FolderProvider {
    /** The default app-private storage directory. */
    fun directory(): File

    /** The absolute path of [directory]. */
    fun path(): String
}
