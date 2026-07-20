package app.confused.anikuta.data.extension

import android.content.Context
import android.util.Log
import app.confused.anikuta.data.extension.api.AnimeExtensionApi
import app.confused.anikuta.data.extension.installer.AnimeExtensionInstaller
import app.confused.anikuta.data.extension.installer.ExtensionInstallReceiver
import app.confused.anikuta.data.extension.installer.InstallStep
import app.confused.anikuta.data.extension.loader.AnimeExtensionLoader
import app.confused.anikuta.data.extension.model.AnimeExtension
import app.confused.anikuta.data.extension.model.AnimeLoadResult
import app.confused.anikuta.data.extension.trust.TrustExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The public façade for the anime extension system.
 *
 * Owns:
 * - The in-memory registries of [Installed][AnimeExtension.Installed],
 *   [Available][AnimeExtension.Available], and
 *   [Untrusted][AnimeExtension.Untrusted] extensions (each a [StateFlow]).
 * - The [AnimeExtensionLoader] (disk → loaded sources) and the
 *   [AnimeExtensionInstaller] (download + PackageInstaller).
 * - The [ExtensionInstallReceiver] listener — keeps the registries in sync with
 *   system package installs / uninstalls / updates.
 *
 * Ported from the Aniyomi reference's `AnimeExtensionManager.kt`, with these
 * adaptations:
 * - Injekt → constructor-injected dependencies (ADR-023).
 * - `SourcePreferences` → not depended on (we don't have it yet); the pending
 *   updates count lives in a simple in-memory counter + a SharedPreferences pref.
 * - The reference's sub-languages-first-run logic is omitted (not needed for
 *   Phase 4B — the Browse screen isn't wired yet).
 *
 * The manager is registered in Koin as a singleton (`extensionModule` in
 * `:app`'s `di/` package). The installer is constructor-injected (not created
 * lazily by the manager), avoiding any circular DI dependency.
 */
class AnimeExtensionManager(
    private val context: Context,
    private val loader: AnimeExtensionLoader,
    private val trustExtension: TrustExtension,
    val api: AnimeExtensionApi,
    val installer: AnimeExtensionInstaller,
) {

    val scope = CoroutineScope(SupervisorJob())

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val installedMap = MutableStateFlow<Map<String, AnimeExtension.Installed>>(emptyMap())
    private val availableMap = MutableStateFlow<Map<String, AnimeExtension.Available>>(emptyMap())
    private val untrustedMap = MutableStateFlow<Map<String, AnimeExtension.Untrusted>>(emptyMap())

    /** Extensions that are installed AND signature-trusted (sources loaded). */
    val installedExtensionsFlow: StateFlow<List<AnimeExtension.Installed>> =
        installedMap.mapExtensions()
    /** Extensions available in a remote repo (not installed). */
    val availableExtensionsFlow: StateFlow<List<AnimeExtension.Available>> =
        availableMap.mapExtensions()
    /** Extensions installed but NOT yet trusted (sources NOT loaded). */
    val untrustedExtensionsFlow: StateFlow<List<AnimeExtension.Untrusted>> =
        untrustedMap.mapExtensions()

    init {
        initExtensions()
        ExtensionInstallReceiver(InstallationListener()).register(context)
    }

    /** Convenience accessor for the current installed list (snapshot). */
    fun getInstalledExtensions(): List<AnimeExtension.Installed> = installedMap.value.values.toList()
    /** Convenience accessor for the current available list (snapshot). */
    fun getAvailableExtensions(): List<AnimeExtension.Available> = availableMap.value.values.toList()
    /** Trusted extensions are the installed ones (trust is checked at load time). */
    fun getTrustedExtensions(): List<AnimeExtension.Installed> = getInstalledExtensions()

    /** Returns the package name of the extension that owns [sourceId], or null. */
    fun getExtensionPackage(sourceId: Long): String? =
        installedMap.value.values.firstOrNull { ext -> ext.sources.any { it.id == sourceId } }?.pkgName

    /**
     * Loads installed extensions from disk at construction. Called once from
     * [init]; re-called by the install receiver when a package is added/updated.
     */
    private fun initExtensions() {
        val results = loader.loadExtensions(context)
        installedMap.value = results.filterIsInstance<AnimeLoadResult.Success>()
            .associate { it.extension.pkgName to it.extension }
        untrustedMap.value = results.filterIsInstance<AnimeLoadResult.Untrusted>()
            .associate { it.extension.pkgName to it.extension }
        _isInitialized.value = true
        Log.i(TAG, "Init scan: ${installedMap.value.size} installed, ${untrustedMap.value.size} untrusted")
    }

    /**
     * Fetches the available extensions from every configured repo and updates
     * [availableExtensionsFlow]. Also recomputes `hasUpdate` / `isObsolete`
     * on every installed extension.
     */
    suspend fun findAvailableExtensions() {
        val available = try {
            api.findAvailableExtensions()
        } catch (e: Exception) {
            Log.e(TAG, "findAvailableExtensions failed", e)
            emptyList()
        }
        availableMap.value = available.associateBy { it.pkgName }
        updateInstalledStatuses(available)
    }

    /**
     * Recomputes `hasUpdate` and `isObsolete` on every installed extension based
     * on the latest available list.
     */
    private fun updateInstalledStatuses(available: List<AnimeExtension.Available>) {
        if (available.isEmpty()) return
        val current = installedMap.value.toMutableMap()
        var changed = false
        for ((pkgName, installed) in current) {
            val availableExt = available.firstOrNull { it.pkgName == pkgName }
            val updated = when {
                availableExt == null && !installed.isObsolete ->
                    installed.copy(isObsolete = true)
                availableExt != null -> {
                    val hasUpdate = availableExt.versionCode > installed.versionCode ||
                        availableExt.libVersion > installed.libVersion
                    installed.copy(hasUpdate = hasUpdate, repoUrl = availableExt.repoUrl, isObsolete = false)
                }
                else -> installed
            }
            if (updated != installed) {
                current[pkgName] = updated
                changed = true
            }
        }
        if (changed) installedMap.value = current
    }

    /**
     * Installs [extension] (download + PackageInstaller). Returns a Flow of
     * [InstallStep] so the UI can render progress.
     */
    fun installExtension(extension: AnimeExtension.Available): Flow<InstallStep> {
        val apkUrl = api.getApkUrl(extension)
        return installer.downloadAndInstall(apkUrl, extension)
    }

    /** Updates [extension] (an install that triggers PACKAGE_REPLACED). */
    fun updateExtension(extension: AnimeExtension.Installed): Flow<InstallStep> {
        val availableExt = availableMap.value[extension.pkgName] ?: return emptyFlow()
        return installExtension(availableExt)
    }

    /** Uninstalls [extension] (launches the system uninstall dialog). */
    fun uninstallExtension(extension: AnimeExtension) {
        installer.uninstallApk(extension.pkgName)
    }

    /**
     * Trusts [extension], moves it from [untrustedExtensionsFlow] to
     * [installedExtensionsFlow], and loads its sources.
     */
    suspend fun trust(extension: AnimeExtension.Untrusted) {
        trustExtension.trust(extension.pkgName, extension.versionCode, extension.signatureHash)
        untrustedMap.value = untrustedMap.value - extension.pkgName
        when (val result = loader.loadExtensionFromPkgName(context, extension.pkgName)) {
            is AnimeLoadResult.Success -> installedMap.value = installedMap.value + (result.extension.pkgName to result.extension)
            is AnimeLoadResult.Untrusted -> {
                // Still untrusted after re-load — put it back
                untrustedMap.value = untrustedMap.value + (result.extension.pkgName to result.extension)
                Log.w(TAG, "Trust: re-load still untrusted for ${extension.pkgName}")
            }
            is AnimeLoadResult.Error -> {
                // Load failed — put it back as untrusted so the user can retry
                untrustedMap.value = untrustedMap.value + (extension.pkgName to extension)
                Log.e(TAG, "Trust: re-load failed for ${extension.pkgName}: ${result.error}")
            }
        }
    }

    /**
     * Untrusts [extension], moves it from [installedExtensionsFlow] back to
     * [untrustedExtensionsFlow], and unloads its sources.
     */
    fun untrust(extension: AnimeExtension.Installed) {
        trustExtension.untrust(extension.pkgName)
        installedMap.value = installedMap.value - extension.pkgName
        // Re-add as untrusted (the loader will detect it as untrusted on next scan)
        val untrusted = AnimeExtension.Untrusted(
            name = extension.name,
            pkgName = extension.pkgName,
            versionName = extension.versionName,
            versionCode = extension.versionCode,
            libVersion = extension.libVersion,
            signatureHash = "", // Will be re-computed on next scan
            lang = extension.lang,
            isNsfw = extension.isNsfw,
            isTorrent = extension.isTorrent,
        )
        untrustedMap.value = untrustedMap.value + (extension.pkgName to untrusted)
        Log.i(TAG, "Untrusted: ${extension.pkgName}")
    }

    private fun <T : AnimeExtension> MutableStateFlow<Map<String, T>>.mapExtensions(): StateFlow<List<T>> =
        map { it.values.toList() }.stateIn(scope, SharingStarted.Lazily, value.values.toList())

    /**
     * Listener for system package broadcasts — refreshes the registries when
     * an extension APK is added / replaced / removed by the system installer.
     * Phase 4B uses the simple full-re-scan approach (see [ExtensionInstallReceiver]).
     */
    private inner class InstallationListener : ExtensionInstallReceiver.Listener {
        override fun onPackageChanged(pkgName: String) {
            // Full re-scan: picks up installs, updates, and uninstalls in one pass.
            val result = loader.loadExtensions(context)
            installedMap.value = result.filterIsInstance<AnimeLoadResult.Success>()
                .associate { it.extension.pkgName to it.extension }
            untrustedMap.value = result.filterIsInstance<AnimeLoadResult.Untrusted>()
                .associate { it.extension.pkgName to it.extension }
            // If the package was actually removed, clear its trust entry so a
            // future reinstall re-prompts for trust.
            val stillPresent = result.any {
                it is AnimeLoadResult.Success && it.extension.pkgName == pkgName ||
                    it is AnimeLoadResult.Untrusted && it.extension.pkgName == pkgName
            }
            if (!stillPresent) {
                trustExtension.untrust(pkgName)
            }
            Log.i(TAG, "Re-scanned after package change: $pkgName")
        }
    }

    companion object {
        private const val TAG = "AnikutaExtMgr"
    }
}
