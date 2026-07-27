package app.confused.anikuta.data.extension.loader

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import app.confused.anikuta.data.extension.model.AnimeExtension
import app.confused.anikuta.data.extension.model.AnimeLoadResult
import app.confused.anikuta.data.extension.trust.TrustExtension
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

/**
 * Loads Aniyomi-compatible anime extensions installed on the device.
 *
 * An extension is an ordinary APK whose manifest declares the
 * `tachiyomi.animeextension` `<uses-feature>` and a `tachiyomi.animeextension.class`
 * `<meta-data>` listing the FQCNs of its [AnimeSource] / [AnimeSourceFactory]
 * subclasses. The loader:
 *
 * 1. Queries [PackageManager] for every installed package that declares the
 *    extension feature flag.
 * 2. Validates the APK's `versionName` encodes a lib version in
 *    [LIB_VERSION_MIN]..[LIB_VERSION_MAX].
 * 3. SHA-256 hashes the APK's signing certificate(s) and asks [TrustExtension]
 *    whether the signature is trusted.
 * 4. Builds a [ChildFirstPathClassLoader] so the extension's bundled deps win
 *    over the app's classpath (binary-compat at the source-api boundary only).
 * 5. Instantiates each declared source class; if it's an [AnimeSourceFactory],
 *    calls `createSources()`.
 * 6. Returns an [AnimeLoadResult] (Success / Untrusted / Error).
 *
 * Ported from the Aniyomi reference's `AnimeExtensionLoader.kt` (435 lines).
 * Notable adaptations for ANIKUTA:
 * - Injekt replaced with constructor-injected [TrustExtension] (ADR-023).
 * - Private-extension (`.ext`) file installs are NOT supported in Phase 4B —
 *   only system-installed (shared) extensions. The reference's private-extension
 *   machinery is omitted to keep this file focused (Rule §10: < 3 responsibilities).
 * - [AnimeLoadResult.UnrecognizedExtension] is returned for packages missing
 *   the feature flag (the reference returns `Error` for the same case).
 */
@SuppressLint("PackageManagerGetSignatures")
class AnimeExtensionLoader(
    private val trustExtension: TrustExtension,
) {

    /** Acceptable source-api library version range (matches the reference). */
    val libVersionMin: Double = LIB_VERSION_MIN.toDouble()
    val libVersionMax: Double = LIB_VERSION_MAX.toDouble()

    /**
     * Scans the device for installed extensions and loads each concurrently.
     * Safe to call from any thread (uses [runBlocking] internally per the
     * reference; the manager calls it from a coroutine scope).
     */
    fun loadExtensions(context: Context): List<AnimeLoadResult> {
        val pkgManager = context.packageManager
        val flags = packageQueryFlags()

        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pkgManager.getInstalledPackages(flags)
        }

        val extPkgs = installedPkgs.filter { isPackageAnExtension(it) }
        if (extPkgs.isEmpty()) return emptyList()

        return runBlocking {
            extPkgs.map { async { loadExtension(context, it) } }.awaitAll()
        }
    }

    /**
     * Loads a single extension by package name (used after the user trusts an
     * untrusted extension or after a fresh install broadcast).
     */
    suspend fun loadExtensionFromPkgName(context: Context, pkgName: String): AnimeLoadResult {
        val flags = packageQueryFlags()
        val pkgInfo = try {
            context.packageManager.getPackageInfo(pkgName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Extension package not found: $pkgName")
            return AnimeLoadResult.Error
        }
        if (!isPackageAnExtension(pkgInfo)) {
            return AnimeLoadResult.UnrecognizedExtension
        }
        return loadExtension(context, pkgInfo)
    }

    /** Core load routine for one extension. See class KDoc for the algorithm. */
    private suspend fun loadExtension(context: Context, pkgInfo: PackageInfo): AnimeLoadResult {
        val pkgManager = context.packageManager
        val appInfo = pkgInfo.applicationInfo ?: run {
            Log.w(TAG, "Missing ApplicationInfo for ${pkgInfo.packageName}")
            return AnimeLoadResult.Error
        }
        val pkgName = pkgInfo.packageName

        val extName = pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Aniyomi: ")
        val versionName = pkgInfo.versionName
        val versionCode = getLongVersionCode(pkgInfo)

        if (versionName.isNullOrEmpty()) {
            Log.w(TAG, "Missing versionName for extension $extName")
            return AnimeLoadResult.Error
        }

        // Validate lib version (versionName = "<libVersion>.<patch>")
        val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            Log.w(TAG, "Lib version $libVersion out of range [$LIB_VERSION_MIN..$LIB_VERSION_MAX] for $extName")
            return AnimeLoadResult.Error
        }

        val signatures = getSignatures(pkgInfo)
        if (signatures.isNullOrEmpty()) {
            Log.w(TAG, "Package $pkgName isn't signed")
            return AnimeLoadResult.Error
        }
        if (!trustExtension.isTrusted(pkgInfo, signatures)) {
            val untrusted = AnimeExtension.Untrusted(
                name = extName,
                pkgName = pkgName,
                versionName = versionName,
                versionCode = versionCode,
                libVersion = libVersion,
                signatureHash = signatures.last(),
            )
            Log.w(TAG, "Extension $pkgName isn't trusted (signature=${signatures.last()})")
            return AnimeLoadResult.Untrusted(untrusted)
        }

        val isNsfw = appInfo.metaData?.getInt(METADATA_NSFW, 0) == 1
        if (isNsfw && !trustExtension.loadNsfwSources()) {
            Log.w(TAG, "NSFW extension $pkgName skipped (NSFW disabled)")
            return AnimeLoadResult.Error
        }
        val isTorrent = appInfo.metaData?.getInt(METADATA_TORRENT, 0) == 1

        val classLoader = try {
            ChildFirstPathClassLoader(appInfo.sourceDir, null, context.classLoader)
        } catch (e: Exception) {
            Log.e(TAG, "Extension load error: $extName ($pkgName)", e)
            return AnimeLoadResult.Error
        }

        val sourceClasses = appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
            ?: run {
                Log.w(TAG, "No source class metadata on $pkgName")
                return AnimeLoadResult.Error
            }

        val sources = sourceClasses.split(";").map { it.trim() }.flatMap { fqcn ->
            val resolved = if (fqcn.startsWith(".")) pkgName + fqcn else fqcn
            instantiateSource(resolved, appInfo, context, extName)
        }
        if (sources.isEmpty()) {
            Log.w(TAG, "No sources instantiated from $pkgName")
            return AnimeLoadResult.Error
        }

        val langs = sources.filterIsInstance<AnimeCatalogueSource>().map { it.lang }.toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        val installed = AnimeExtension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            isTorrent = isTorrent,
            sources = sources,
            pkgFactory = appInfo.metaData?.getString(METADATA_SOURCE_FACTORY),
            icon = appInfo.loadIcon(pkgManager),
            isShared = true,
        )
        return AnimeLoadResult.Success(installed)
    }

    /** Instantiates one source class, with [PathClassLoader] fallback on [LinkageError]. */
    private fun instantiateSource(
        fqcn: String,
        appInfo: ApplicationInfo,
        context: Context,
        extName: String,
    ): List<AnimeSource> {
        return try {
            val cl = ChildFirstPathClassLoader(appInfo.sourceDir, null, context.classLoader)
            when (val obj = Class.forName(fqcn, false, cl).getDeclaredConstructor().newInstance()) {
                is AnimeSource -> listOf(obj)
                is AnimeSourceFactory -> obj.createSources()
                else -> {
                    Log.e(TAG, "Unknown source class type for $extName: ${obj.javaClass}")
                    emptyList()
                }
            }
        } catch (e: LinkageError) {
            // Fall back to a parent-first PathClassLoader (some extensions need this).
            try {
                val cl = PathClassLoader(appInfo.sourceDir, null, context.classLoader)
                when (val obj = Class.forName(fqcn, false, cl).getDeclaredConstructor().newInstance()) {
                    is AnimeSource -> listOf(obj)
                    is AnimeSourceFactory -> obj.createSources()
                    else -> {
                        Log.e(TAG, "Unknown source class type (fallback) for $extName: ${obj.javaClass}")
                        emptyList()
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Extension load error: $extName ($fqcn)", e)
                emptyList()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Extension load error: $extName ($fqcn)", e)
            emptyList()
        }
    }

    /** Returns `true` if the package declares the `tachiyomi.animeextension` feature. */
    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
    }

    /** SHA-256 hashes the package's signing certificates. */
    private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo ?: return null
            val certs = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            certs?.map { HashUtil.sha256(it.toByteArray()) }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures?.map { HashUtil.sha256(it.toByteArray()) }
        }
    }

    private fun packageQueryFlags(): Int =
        PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private fun getLongVersionCode(pkgInfo: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode
        else @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()

    companion object {
        private const val TAG = "AnikutaExtLoader"

        const val LIB_VERSION_MIN = 12
        const val LIB_VERSION_MAX = 16

        private const val EXTENSION_FEATURE = "tachiyomi.animeextension"
        private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
        private const val METADATA_SOURCE_FACTORY = "tachiyomi.animeextension.factory"
        private const val METADATA_NSFW = "tachiyomi.animeextension.nsfw"
        private const val METADATA_TORRENT = "tachiyomi.animeextension.torrent"
    }
}
