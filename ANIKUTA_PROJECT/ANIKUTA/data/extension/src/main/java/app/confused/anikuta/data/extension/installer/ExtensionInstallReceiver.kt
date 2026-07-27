package app.confused.anikuta.data.extension.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import app.confused.anikuta.data.extension.model.AnimeExtension

/**
 * Listens for `ACTION_PACKAGE_ADDED / REPLACED / REMOVED` system broadcasts and
 * signals the manager to refresh its in-memory registries.
 *
 * Phase 4B simplification: rather than loading the specific package (the
 * reference's approach), we trigger a full re-scan via [Listener.onPackageChanged]
 * for every event. This is slightly less efficient but dramatically simpler and
 * avoids the "is this an extension?" re-check inside the receiver. The manager's
 * re-scan is fast (PackageManager query is in-memory; class loading only happens
 * for trusted extensions).
 *
 * The receiver is registered dynamically by [app.confused.anikuta.data.extension.AnimeExtensionManager]
 * at construction (it needs a [Listener] constructor arg, so it can't be declared
 * statically in the manifest).
 *
 * Ported from `AnimeExtensionInstallReceiver.kt`.
 */
class ExtensionInstallReceiver(private val listener: Listener) : BroadcastReceiver() {

    /**
     * The manager implements this. [onPackageChanged] is the only method — it
     * triggers a full re-scan. The other methods are kept for future per-event
     * optimization (matching the reference's API).
     */
    interface Listener {
        /** A package was added / replaced / removed — refresh all registries. */
        fun onPackageChanged(pkgName: String)

        // Reserved for future per-event optimization (not called in Phase 4B):
        fun onExtensionInstalled(extension: AnimeExtension.Installed) {}
        fun onExtensionUpdated(extension: AnimeExtension.Installed) {}
        fun onExtensionUntrusted(extension: AnimeExtension.Untrusted) {}
    }

    /** Registers this receiver for the system package-action intents. */
    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pkgName = intent.data?.schemeSpecificPart ?: return
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                if (!isReplacing(intent)) {
                    Log.i(TAG, "Package added: $pkgName — triggering refresh")
                    listener.onPackageChanged(pkgName)
                }
            }
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.i(TAG, "Package replaced: $pkgName — triggering refresh")
                listener.onPackageChanged(pkgName)
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                if (!isReplacing(intent)) {
                    Log.i(TAG, "Package removed: $pkgName — triggering refresh")
                    listener.onPackageChanged(pkgName)
                }
            }
        }
    }

    /**
     * `ACTION_PACKAGE_REMOVED` fires with `EXTRA_REPLACING=true` right before
     * `ACTION_PACKAGE_ADDED` during an update — we suppress the spurious
     * removed+added pair so we only refresh once.
     */
    private fun isReplacing(intent: Intent): Boolean =
        intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)

    companion object {
        private const val TAG = "AnikutaExtInstaller"
    }
}
