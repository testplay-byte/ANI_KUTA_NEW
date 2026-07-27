package app.confused.anikuta.data.extension.trust

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo

/**
 * Decides whether an installed extension's signing certificate has been
 * trusted by the user.
 *
 * The Aniyomi reference uses a `SourcePreferences.trustedExtensions()`
 * `Set<String>` preference holding `"pkgName:versionCode:signatureHash"`
 * entries. We mirror that exact format (ADR-029: extension compatibility)
 * but back it with a plain [SharedPreferences] (no PreferenceStore dependency
 * yet — `:core:preferences` is still an empty skeleton in this project).
 *
 * @param context  the application context (used to open the prefs file).
 */
class TrustExtension(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns `true` iff [pkgInfo]'s signature is in the trusted set AND the
     * stored version code matches (an updated extension with a new version
     * code must be re-trusted).
     *
     * @param pkgInfo     the package being checked.
     * @param signatures  the SHA-256 hex hashes of the package's signing certs.
     */
    fun isTrusted(pkgInfo: PackageInfo, signatures: List<String>): Boolean {
        if (signatures.isEmpty()) return false
        val trusted = prefs.getStringSet(KEY_TRUSTED, emptySet()) ?: emptySet()
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
        return signatures.any { sig ->
            trusted.contains("${pkgInfo.packageName}:$versionCode:$sig")
        }
    }

    /**
     * Adds an extension to the trusted set so the loader will accept it on
     * the next scan.
     */
    fun trust(pkgName: String, versionCode: Long, signatureHash: String) {
        val trusted = (prefs.getStringSet(KEY_TRUSTED, emptySet()) ?: emptySet())
            .toMutableSet()
        trusted.add("$pkgName:$versionCode:$signatureHash")
        prefs.edit().putStringSet(KEY_TRUSTED, trusted).apply()
    }

    /**
     * Removes every trusted entry for [pkgName] (any version, any signature).
     * Called when the extension is uninstalled so stale trust entries don't
     * linger.
     */
    fun untrust(pkgName: String) {
        val trusted = (prefs.getStringSet(KEY_TRUSTED, emptySet()) ?: emptySet())
            .filterNot { it.startsWith("$pkgName:") }
            .toSet()
        prefs.edit().putStringSet(KEY_TRUSTED, trusted).apply()
    }

    /** Whether the user has opted to load NSFW-tagged extensions. Default: true. */
    fun loadNsfwSources(): Boolean = prefs.getBoolean(KEY_LOAD_NSFW, true)

    companion object {
        private const val PREFS_NAME = "anikuta_extension_trust"
        private const val KEY_TRUSTED = "trusted_extensions"
        private const val KEY_LOAD_NSFW = "load_nsfw_sources"
    }
}

/** Compatibility shim for `PackageInfoCompat.getLongVersionCode` (avoids androidx dep). */
private object PackageInfoCompat {
    fun getLongVersionCode(pkgInfo: PackageInfo): Long {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }
    }
}
