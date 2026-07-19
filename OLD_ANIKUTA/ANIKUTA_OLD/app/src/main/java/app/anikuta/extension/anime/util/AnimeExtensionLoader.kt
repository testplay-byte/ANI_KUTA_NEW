package app.anikuta.extension.anime.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import app.anikuta.domain.extension.anime.interactor.TrustAnimeExtension
import app.anikuta.domain.source.service.SourcePreferences
import app.anikuta.extension.anime.model.AnimeExtension
import app.anikuta.extension.anime.model.AnimeLoadResult
import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.AnimeSource
import app.anikuta.source.api.AnimeSourceFactory
import app.anikuta.util.system.ChildFirstPathClassLoader
import dalvik.system.PathClassLoader
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * Loads anime extension APKs at runtime using DexClassLoader.
 * Clean rewrite based on aniyomi's AnimeExtensionLoader.
 *
 * Detection: extensions declare `<uses-feature android:name="tachiyomi.animeextension" />`
 * in their AndroidManifest.xml. We scan all installed packages for this feature
 * (via [PackageInfo.reqFeatures]), then load the source class from the package's
 * `<meta-data android:name="tachiyomi.animeextension.class" />` value using a
 * DexClassLoader.
 *
 * Source: REFERENCE/app/.../extension/anime/util/AnimeExtensionLoader.kt
 */
class AnimeExtensionLoader(
    private val context: Context,
) {
    companion object {
        private const val TAG = "AnimeExtLoader"
        const val LIB_VERSION_MIN = 12.0
        const val LIB_VERSION_MAX = 16.0

        /** The <uses-feature> name that identifies an anime extension. */
        private const val EXTENSION_FEATURE = "tachiyomi.animeextension"

        /** Metadata key for the source class name (or factory class name). */
        private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
        private const val METADATA_SOURCE_FACTORY = "tachiyomi.animeextension.factory"

        /** Old fallback metadata keys (pre-lib-1.5 extensions). */
        private const val METADATA_SOURCE_CLASS_FALLBACK = "extension.class"

        private const val METADATA_LIB_VERSION = "tachiyomi.animeextension.lib.version"
        private const val METADATA_LIB_VERSION_FALLBACK = "extension.libVersion"
        private const val METADATA_NSFW = "tachiyomi.animeextension.nsfw"

        /** Package-info flags matching aniyomi: need metaData + signing certs. */
        @Suppress("DEPRECATION")
        private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)
    }

    private val trustExtension: TrustAnimeExtension by injectLazy()
    private val preferences: SourcePreferences by injectLazy()

    /**
     * Finds all installed anime extensions on the device.
     *
     * Uses three detection methods (belt + suspenders — if one fails on a
     * specific device/Android version, the others catch it):
     *  1. reqFeatures scan (aniyomi's primary method) — checks
     *     `<uses-feature android:name="tachiyomi.animeextension" />`
     *  2. metadata scan (fallback) — checks `<meta-data android:name=
     *     "tachiyomi.animeextension.class" />`
     *  3. intent query (fallback) — queries for packages that handle the
     *     `tachiyomi.animeextension` intent action
     *
     * All three are logged so the Debug screen shows which method found what.
     */
    @SuppressLint("QueryAllPackagesPermission")
    fun loadAll(): List<AnimeLoadResult> {
        val pkgManager = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }
        Log.d(TAG, "=== Extension scan started ===")
        Log.d(TAG, "Scanned ${packages.size} installed package(s) with QUERY_ALL_PACKAGES")

        // Method 1: reqFeatures
        val byReqFeatures = packages.filter { isPackageAnExtensionByReqFeatures(it) }
        Log.d(TAG, "Method 1 (reqFeatures): found ${byReqFeatures.size} → ${byReqFeatures.map { it.packageName }}")

        // Method 2: metadata
        val byMetadata = packages.filter { isPackageAnExtensionByMetadata(it) }
        Log.d(TAG, "Method 2 (metadata): found ${byMetadata.size} → ${byMetadata.map { it.packageName }}")

        // Method 3: intent query (doesn't need QUERY_ALL_PACKAGES on Android 11+)
        val byIntent = findByIntent()
        Log.d(TAG, "Method 3 (intent query): found ${byIntent.size} → ${byIntent.map { it.packageName }}")

        // Union of all methods (dedup by packageName)
        val allExtensionPkgs = (byReqFeatures + byMetadata + byIntent)
            .distinctBy { it.packageName }
        Log.d(TAG, "Union of all methods: ${allExtensionPkgs.size} unique extension(s): ${allExtensionPkgs.map { it.packageName }}")

        return allExtensionPkgs.map { loadExtension(it) }
    }

    /**
     * Intent-based fallback: query for packages that declare an intent filter
     * with the extension action. This doesn't require QUERY_ALL_PACKAGES on
     * Android 11+ (intent visibility rules allow this query if the extension
     * declares the action in an intent-filter). aniyomi doesn't use this, but
     * it's a robust fallback if the reqFeatures/metadata scans are filtered.
     */
    private fun findByIntent(): List<PackageInfo> {
        return try {
            // Some extensions declare an intent-filter with this action.
            // queryIntentActivities respects <queries> on Android 11+ but also
            // returns packages with matching intent-filters without needing
            // QUERY_ALL_PACKAGES.
            val intent = android.content.Intent(EXTENSION_FEATURE)
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PACKAGE_FLAGS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(intent, PACKAGE_FLAGS)
            }
            resolveInfos.mapNotNull { ri ->
                try {
                    context.packageManager.getPackageInfo(
                        ri.activityInfo.packageName,
                        PACKAGE_FLAGS,
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Intent-based query failed", e)
            emptyList()
        }
    }

    /**
     * Loads a single extension from its PackageInfo.
     */
    fun loadExtension(pkgInfo: PackageInfo): AnimeLoadResult {
        val pkgName = pkgInfo.packageName
        Log.d(TAG, "Loading extension: $pkgName")
        val sources = getSourcesFromPackage(pkgInfo)

        if (sources.isEmpty()) {
            Log.w(TAG, "No sources found in extension $pkgName")
            return AnimeLoadResult.Error
        }

        val appInfo = pkgInfo.applicationInfo
        // Strip "Aniyomi: " prefix from the extension name (extensions are
        // built with this prefix in their app label, but we don't want it
        // shown in the UI — matches aniyomi's AnimeExtensionApi behavior
        // which strips it for Available extensions).
        val rawName = appInfo?.loadLabel(context.packageManager)?.toString() ?: pkgName
        val cleanName = rawName.removePrefix("Aniyomi: ").trim()
        val extension = AnimeExtension.Installed(
            name = cleanName,
            pkgName = pkgName,
            versionName = pkgInfo.versionName ?: "unknown",
            versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo),
            libVersion = getLibVersionFromMetadata(pkgInfo),
            lang = getLangFromPackageName(pkgName),
            isNsfw = appInfo?.metaData?.getInt(METADATA_NSFW, 0) == 1,
            pkgFactory = appInfo?.metaData?.getString(METADATA_SOURCE_FACTORY),
            sources = sources,
            icon = appInfo?.loadIcon(context.packageManager),
            isShared = false,
        )
        Log.d(TAG, "✅ Loaded '${extension.name}' (lang=${extension.lang}, sources=${sources.size}, libVersion=${extension.libVersion})")

        return if (isExtensionTrusted(pkgInfo)) {
            AnimeLoadResult.Success(extension)
        } else {
            val signatures = getSignatures(pkgInfo)
            Log.w(TAG, "Extension $pkgName is untrusted")
            AnimeLoadResult.Untrusted(
                AnimeExtension.Untrusted(
                    name = extension.name,
                    pkgName = extension.pkgName,
                    versionName = extension.versionName,
                    versionCode = extension.versionCode,
                    libVersion = extension.libVersion,
                    signatureHash = signatures.lastOrNull() ?: "unknown",
                    lang = extension.lang,
                    isNsfw = extension.isNsfw,
                    icon = extension.icon,
                )
            )
        }
    }

    /**
     * Method 1: Detect via `<uses-feature>` declaration (aniyomi's primary method).
     * Extensions declare `<uses-feature android:name="tachiyomi.animeextension" />`.
     */
    private fun isPackageAnExtensionByReqFeatures(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
    }

    /**
     * Method 2: Detect via `<meta-data>` tag (fallback). Some extensions or
     * older versions may not declare the uses-feature but still have the
     * source-class metadata. We check for the source-class key.
     */
    private fun isPackageAnExtensionByMetadata(pkgInfo: PackageInfo): Boolean {
        val metaData = pkgInfo.applicationInfo?.metaData ?: return false
        return metaData.containsKey(METADATA_SOURCE_CLASS) ||
            metaData.containsKey(METADATA_SOURCE_CLASS_FALLBACK)
    }

    /**
     * Extracts the language code from the package name.
     * Extension package names follow: eu.kanade.tachiyomi.animeextension.{lang}.{name}
     * e.g., eu.kanade.tachiyomi.animeextension.en.anikoto180 → "en"
     */
    private fun getLangFromPackageName(pkgName: String): String {
        val parts = pkgName.split(".")
        return parts.getOrNull(4) ?: ""
    }

    private fun isExtensionTrusted(pkgInfo: PackageInfo): Boolean {
        return try {
            trustExtension.isTrusted(pkgInfo.packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Trust check failed for ${pkgInfo.packageName}, treating as untrusted", e)
            false
        }
    }

    /**
     * Computes the SHA-256 hashes of the extension's signing certificates.
     * Used for the [AnimeExtension.Untrusted.signatureHash] field.
     */
    private fun getSignatures(pkgInfo: PackageInfo): List<String> {
        return try {
            pkgInfo.signingInfo?.signingCertificateHistory?.map {
                app.anikuta.util.lang.Hash.sha256(it.toByteArray())
            } ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Could not get signatures for ${pkgInfo.packageName}", e)
            emptyList()
        }
    }

    private fun getSourcesFromPackage(pkgInfo: PackageInfo): List<AnimeSource> {
        val appInfo = pkgInfo.applicationInfo ?: return emptyList()
        val sourceClass = appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
            ?: appInfo.metaData?.getString(METADATA_SOURCE_CLASS_FALLBACK)
            ?: run {
                Log.w(TAG, "No source class metadata in ${pkgInfo.packageName}")
                return emptyList()
            }

        Log.d(TAG, "Loading source class '$sourceClass' from ${pkgInfo.packageName}")
        val classLoader = createClassLoader(appInfo)
        val sources = mutableListOf<AnimeSource>()

        // Source classes can be relative (".Anikoto") or absolute.
        // Relative names must be prefixed with the package name (aniyomi does
        // the same). Multiple classes are separated by ";".
        sourceClass.split(";").forEach { rawClassName ->
            val className = rawClassName.trim()
            // Resolve relative class names: ".Anikoto" → "pkg.Anikoto"
            val resolvedName = if (className.startsWith(".")) {
                pkgInfo.packageName + className
            } else {
                className
            }
            Log.d(TAG, "  Resolved class: $className → $resolvedName")
            try {
                val clazz = Class.forName(resolvedName, false, classLoader)
                val instance = clazz.getDeclaredConstructor().newInstance()

                when (instance) {
                    is AnimeSource -> {
                        sources.add(instance)
                        Log.d(TAG, "  ✓ Loaded source: ${instance.name} (id=${instance.id})")
                    }
                    is AnimeSourceFactory -> {
                        val factorySources = instance.createSources()
                        sources.addAll(factorySources)
                        Log.d(TAG, "  ✓ Loaded factory with ${factorySources.size} source(s)")
                    }
                    else -> Log.w(TAG, "  ✗ Unknown source type: ${clazz.name}")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "  ✗ Failed to load source class: $resolvedName", e)
            }
        }

        return sources
    }

    private fun createClassLoader(appInfo: android.content.pm.ApplicationInfo): ClassLoader {
        val dexPath = appInfo.sourceDir
        val libraryPath = appInfo.nativeLibraryDir
        return try {
            ChildFirstPathClassLoader(dexPath, libraryPath, context.classLoader)
        } catch (e: Exception) {
            Log.w(TAG, "ChildFirstPathClassLoader failed, falling back to PathClassLoader", e)
            PathClassLoader(dexPath, libraryPath, context.classLoader)
        }
    }

    private fun getLibVersionFromMetadata(pkgInfo: PackageInfo): Double {
        val version = pkgInfo.applicationInfo?.metaData?.getString(METADATA_LIB_VERSION)
            ?: pkgInfo.applicationInfo?.metaData?.getString(METADATA_LIB_VERSION_FALLBACK)
            ?: return 0.0
        return try {
            version.substringBeforeLast('.').toDouble()
        } catch (e: Exception) {
            0.0
        }
    }
}
