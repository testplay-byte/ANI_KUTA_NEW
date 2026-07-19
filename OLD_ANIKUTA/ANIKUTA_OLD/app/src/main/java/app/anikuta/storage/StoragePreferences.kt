package app.anikuta.storage

import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore
import app.anikuta.core.storage.FolderProvider

/**
 * Persists the SAF (Storage Access Framework) folder URI selected by the user
 * during onboarding (or via Settings).
 *
 * Uses [Preference.appStateKey] so the URI is excluded from backups — it's
 * device-specific and not portable across devices.
 *
 * If the user never picks a folder (or chose "use default"), the value falls
 * back to [FolderProvider.path] (the app-private external storage path).
 * This means the app always has a working storage location, even without an
 * explicit SAF selection.
 */
class StoragePreferences(
    private val folderProvider: FolderProvider,
    private val preferenceStore: PreferenceStore,
) {
    /**
     * The base storage directory. Either:
     *  - A SAF URI string (e.g. "content://com.android.externalstorage.documents/tree/primary%3AANI-KUTA")
     *    if the user picked a custom folder, OR
     *  - An absolute file path (e.g. "/storage/emulated/0/Android/data/app.anikuta/files")
     *    if using the default app-private storage.
     *
     * The [StorageManager] resolves this to a UniFile on access.
     */
    fun baseStorageDirectory(): Preference<String> =
        preferenceStore.getString(Preference.appStateKey("storage_dir"), folderProvider.path())
}
