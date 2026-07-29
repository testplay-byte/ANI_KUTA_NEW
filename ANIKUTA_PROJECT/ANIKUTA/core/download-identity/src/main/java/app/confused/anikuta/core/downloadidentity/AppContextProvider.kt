package app.confused.anikuta.core.downloadidentity

import android.content.Context

/**
 * Provides the application context to [DownloadIdentityStore] (which needs it for
 * ContentResolver access to SAF URIs).
 *
 * Initialized in [app.confused.anikuta.App.onCreate].
 */
object AppContextProvider {
    lateinit var context: Context
        private set

    fun init(context: Context) {
        this.context = context
    }
}
