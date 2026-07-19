package app.anikuta.core.storage

import android.content.Context
import java.io.File

/**
 * Android implementation of [FolderProvider]. Returns the app's external
 * files directory (or internal filesDir as a fallback on devices where
 * external storage isn't available).
 */
class AndroidStorageFolderProvider(private val context: Context) : FolderProvider {

    override fun directory(): File =
        context.getExternalFilesDir(null) ?: context.filesDir

    override fun path(): String = directory().absolutePath
}
