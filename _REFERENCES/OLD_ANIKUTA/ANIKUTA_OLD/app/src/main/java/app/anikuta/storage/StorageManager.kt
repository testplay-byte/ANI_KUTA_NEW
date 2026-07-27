package app.anikuta.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import com.hippo.unifile.UniFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

/**
 * Manages the user-selected storage folder and its subdirectory structure.
 *
 * Resolves the persisted folder URI (from [StoragePreferences]) to a
 * [UniFile] and creates the standard subdirectory structure:
 *
 * ```
 * <base>/
 *   ├── downloads/   ← downloaded episodes (.mp4/.mkv)
 *   ├── data/        ← app data (screenshots, mpv config, future structured data)
 *   ├── backups/     ← backup files (auto + manual)
 *   └── cache/       ← temporary data with cleanup
 * ```
 *
 * A `.nomedia` file is placed in `downloads/` to prevent the Media Scanner
 * from indexing downloaded videos.
 *
 * If the user hasn't picked a custom folder, the base resolves to the
 * app-private external storage (via [FolderProvider]) — the app always has
 * a working storage location.
 *
 * Callers should handle `null` returns gracefully (storage revoked, unmounted,
 * or URI permission lost after a reinstall) — typically by falling back to
 * app-private storage or re-prompting the user.
 *
 * @see StoragePreferences
 * @see com.hippo.unifile.UniFile
 */
class StorageManager(
    private val context: Context,
    storagePreferences: StoragePreferences,
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private var baseDir: UniFile? = resolveBaseDir(storagePreferences.baseStorageDirectory().get())

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when the base directory changes (user picked a new folder). */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    init {
        // Re-resolve + create subdirs when the stored URI changes.
        storagePreferences.baseStorageDirectory().changes()
            .drop(1) // skip the initial value (already handled above)
            .distinctUntilChanged()
            .onEach { uri ->
                baseDir = resolveBaseDir(uri)
                ensureSubdirs()
                _changes.tryEmit(Unit)
            }
            .launchIn(scope)
    }

    /** The base storage directory as a UniFile (null if unresolvable). */
    fun getBaseDirectory(): UniFile? = baseDir

    /** The downloads subdirectory (created on first access). */
    fun getDownloadsDirectory(): UniFile? = baseDir?.createDirectory(DOWNLOADS_PATH)

    /** The data subdirectory (created on first access). */
    fun getDataDirectory(): UniFile? = baseDir?.createDirectory(DATA_PATH)

    /** The backups subdirectory (created on first access). */
    fun getBackupsDirectory(): UniFile? = baseDir?.createDirectory(BACKUPS_PATH)

    /** The cache subdirectory (created on first access). */
    fun getCacheDirectory(): UniFile? = baseDir?.createDirectory(CACHE_PATH)

    /**
     * Resolve a stored URI/path string to a UniFile.
     * Handles both SAF URIs ("content://...") and file paths ("/storage/...").
     */
    private fun resolveBaseDir(uriOrPath: String): UniFile? {
        return try {
            if (uriOrPath.startsWith("content://")) {
                UniFile.fromUri(context, Uri.parse(uriOrPath))
            } else {
                UniFile.fromFile(java.io.File(uriOrPath))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve base dir: $uriOrPath", e)
            null
        }
    }

    /** Create the standard subdirectory structure + .nomedia. */
    private fun ensureSubdirs() {
        val base = baseDir ?: return
        try {
            base.createDirectory(DOWNLOADS_PATH)?.createFile(".nomedia")
            base.createDirectory(DATA_PATH)
            base.createDirectory(BACKUPS_PATH)
            base.createDirectory(CACHE_PATH)
            Log.i(TAG, "Storage subdirs ensured at: ${base.filePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create subdirs", e)
        }
    }

    companion object {
        private const val TAG = "StorageManager"
        const val DOWNLOADS_PATH = "downloads"
        const val DATA_PATH = "data"
        const val BACKUPS_PATH = "backups"
        const val CACHE_PATH = "cache"
    }
}
