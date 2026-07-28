package app.confused.anikuta.data.extension.loader

import dalvik.system.PathClassLoader
import java.io.File

/**
 * A [PathClassLoader] that consults the extension's own DEX *before* the
 * app's classpath when resolving classes.
 *
 * This is what lets an Aniyomi extension ship its own bundled copies of
 * Jsoup / OkHttp / etc. without clashing with the versions baked into the
 * app — they only need to be binary-compatible at the `:core:source-api`
 * boundary. Ported from the Aniyomi reference's
 * `eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader`.
 *
 * "Child-first" (a.k.a. parent-last) loading:
 * 1. `findClass` first looks in the extension's own DEX (`super.findClass`).
 * 2. Only if not found does it fall back to the parent (app) classloader.
 *
 * On any [LinkageError] the loader falls back to a plain [PathClassLoader]
 * (parent-first) — see [AnimeExtensionLoader].
 *
 * @param dexPath   absolute path to the extension APK (its `sourceDir`).
 * @param libraryPath  native library search path (may be null).
 * @param parent    the app's classloader (the parent to consult on miss).
 */
internal class ChildFirstPathClassLoader(
    dexPath: String,
    libraryPath: String?,
    parent: ClassLoader?,
) : PathClassLoader(dexPath, libraryPath, parent) {

    /**
     * Override to consult this classloader first, then the parent.
     *
     * Note: `PathClassLoader.findClass` only searches the DEX files given at
     * construction — it does NOT consult the parent — so for the common case
     * this override is a no-op. We keep the override to make the child-first
     * contract explicit and to guard against the parent being consulted by
     * `loadClass` (which calls `findClass` only if `parent.loadClass` throws).
     */
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        return try {
            findClass(name)
        } catch (e: ClassNotFoundException) {
            super.loadClass(name, resolve)
        }
    }

    companion object {
        @Suppress("unused")
        private const val TAG = "AnikutaExtLoader"
    }
}
