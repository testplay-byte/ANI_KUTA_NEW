package app.anikuta.ui.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.extension.anime.AnimeExtensionManager
import app.anikuta.extension.anime.TrustResult
import app.anikuta.extension.anime.api.AnimeExtensionApi
import app.anikuta.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException

/**
 * Phase 7 (enhanced) — ViewModel for the Extensions settings screen.
 *
 * Features:
 *  - Three lists: sources (trusted), installed (untrusted), available (from repos)
 *  - Search: filters across all three lists
 *  - Filter: by language (default: English only)
 *  - Sort: by name ascending/descending
 *  - Layout: list or grid (2 columns)
 *  - Auto-refresh: BroadcastReceiver detects package install/uninstall and
 *    reloads the extension list automatically (no manual pull-to-refresh needed)
 *  - Direct install: uses ACTION_INSTALL_PACKAGE (not ACTION_VIEW) to skip the
 *    package-installer chooser dialog when possible
 *  - Max-2-trusted popup with auto-trust after revoke
 */
class ExtensionsViewModel : ViewModel() {

    companion object {
        private const val TAG = "ExtensionsViewModel"
    }

    private val extensionManager: AnimeExtensionManager? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val context: Context? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val networkHelper: NetworkHelper? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val api: AnimeExtensionApi by lazy { AnimeExtensionApi() }

    // ---- Raw lists (from manager) ----
    private val _sources = MutableStateFlow<List<AnimeExtension.Installed>>(emptyList())
    val sources: StateFlow<List<AnimeExtension.Installed>> = _sources.asStateFlow()

    private val _installed = MutableStateFlow<List<AnimeExtension.Untrusted>>(emptyList())
    val installed: StateFlow<List<AnimeExtension.Untrusted>> = _installed.asStateFlow()

    private val _available = MutableStateFlow<List<AnimeExtension.Available>>(emptyList())
    val available: StateFlow<List<AnimeExtension.Available>> = _available.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    private val _trustResult = MutableStateFlow<TrustResult?>(null)
    val trustResult: StateFlow<TrustResult?> = _trustResult.asStateFlow()

    // ---- UI state: search, filter, sort, layout ----
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    /** Languages to show. Default: English only. */
    private val _enabledLanguages = MutableStateFlow<Set<String>>(setOf("en"))
    val enabledLanguages: StateFlow<Set<String>> = _enabledLanguages.asStateFlow()

    enum class SortMode { NAME_ASC, NAME_DESC }
    private val _sortMode = MutableStateFlow(SortMode.NAME_ASC)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    // ---- Sources priority (drag-and-drop) ----
    // MUST be declared before the init block — the init block's coroutine
    // accesses _sourcePriority.value, and Kotlin initializes properties in
    // declaration order. If this is after init, it's null when accessed.
    private val _sourcePriority = MutableStateFlow<List<String>>(emptyList())
    val sourcePriority: StateFlow<List<String>> = _sourcePriority.asStateFlow()

    /** All languages found in available extensions (for the filter UI). */
    val allLanguages: Set<String>
        get() = _available.value.mapNotNull { it.lang.takeIf { l -> l.isNotBlank() } }.toSet()

    // ---- Package install/uninstall receiver for auto-refresh ----
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val pkg = intent?.data?.schemeSpecificPart ?: return
            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> {
                    Log.d(TAG, "Package added/replaced: $pkg — reloading extensions")
                    extensionManager?.reload()
                }
                Intent.ACTION_PACKAGE_REMOVED -> {
                    Log.d(TAG, "Package removed: $pkg — reloading extensions")
                    extensionManager?.reload()
                }
            }
        }
    }

    init {
        // Subscribe to trusted (sources) and untrusted (installed) flows.
        viewModelScope.launch {
            extensionManager?.installedExtensions?.collect { installedList ->
                _sources.value = installedList
                // Sync the priority list with the current trusted sources.
                // Add any new trusted sources to the end; remove any that were untrusted.
                val trustedPkgs = installedList.map { it.pkgName }.toSet()
                val currentPriority = _sourcePriority.value.filter { it in trustedPkgs }
                val newPriority = currentPriority + (trustedPkgs - currentPriority.toSet())
                if (newPriority != _sourcePriority.value) {
                    _sourcePriority.value = newPriority
                }
            }
        }
        loadSourcePriority()
        viewModelScope.launch {
            extensionManager?.untrustedExtensions?.collect { untrusted ->
                _installed.value = untrusted
            }
        }

        // Register the package receiver for auto-refresh.
        context?.let { ctx ->
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
            try {
                ctx.registerReceiver(packageReceiver, filter)
                Log.d(TAG, "Package receiver registered for auto-refresh")
            } catch (e: Exception) {
                Log.w(TAG, "Could not register package receiver", e)
            }
        }

        // Auto-refresh: always fetch available extensions on init. This ensures
        // the list populates when the user returns from the Repos screen (where
        // they may have just added a repo). The previous behavior required a
        // manual pull-to-refresh, which was poor UX.
        refresh()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context?.unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Could not unregister package receiver", e)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            if (_available.value.isEmpty()) _isLoading.value = true
            try {
                extensionManager?.reload()
                val availableList = withContext(Dispatchers.IO) { api.findExtensions() }
                _available.value = availableList
                Log.i(TAG, "Loaded ${availableList.size} available extensions")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh extensions", e)
                Toast.makeText(context, "Failed to load extensions", Toast.LENGTH_SHORT).show()
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    // ---- Search ----
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) _searchQuery.value = ""
    }

    // ---- Sources priority methods (declaration is above, near other StateFlows) ----

    fun reorderSourcePriority(from: Int, to: Int) {
        val list = _sourcePriority.value.toMutableList()
        if (from in list.indices && to in list.indices) {
            val item = list.removeAt(from)
            list.add(to, item)
            _sourcePriority.value = list
            try {
                val prefs = Injekt.get<app.anikuta.domain.source.service.SourcePreferences>()
                val json = kotlinx.serialization.json.Json.encodeToString(list)
                prefs.sourcePriorityOrder().set(json)
            } catch (e: Exception) {
                Log.w(TAG, "Could not persist source priority", e)
            }
        }
    }

    private fun loadSourcePriority() {
        try {
            val prefs = Injekt.get<app.anikuta.domain.source.service.SourcePreferences>()
            val json = prefs.sourcePriorityOrder().get()
            _sourcePriority.value = try {
                kotlinx.serialization.json.Json.decodeFromString<List<String>>(json)
            } catch (e: Exception) { emptyList() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load source priority", e)
        }
    }

    // ---- Filter ----
    fun setEnabledLanguages(langs: Set<String>) { _enabledLanguages.value = langs }

    // ---- Sort ----
    fun setSortMode(mode: SortMode) { _sortMode.value = mode }

    /**
     * Filtered + sorted available extensions based on search query, language
     * filter, and sort mode. Installed extensions are moved to the BOTTOM
     * of the list (they still show, but at the end).
     */
    fun filteredAvailable(): List<AnimeExtension.Available> {
        val query = _searchQuery.value.lowercase().trim()
        val langs = _enabledLanguages.value
        val sortMode = _sortMode.value
        val installedPkgs = _sources.value.map { it.pkgName } + _installed.value.map { it.pkgName }

        return _available.value
            .filter { ext ->
                // Language filter
                if (langs.isNotEmpty()) ext.lang in langs else true
            }
            .filter { ext ->
                // Search query filter
                if (query.isBlank()) true
                else ext.name.lowercase().contains(query) || ext.pkgName.lowercase().contains(query)
            }
            .sortedWith(
                when (sortMode) {
                    SortMode.NAME_ASC -> compareBy { it.name.lowercase() }
                    SortMode.NAME_DESC -> compareByDescending { it.name.lowercase() }
                },
            )
            .sortedBy { it.pkgName in installedPkgs }  // installed → bottom (false < true)
    }

    /**
     * Filtered sources (for search).
     */
    fun filteredSources(): List<AnimeExtension.Installed> {
        val query = _searchQuery.value.lowercase().trim()
        if (query.isBlank()) return _sources.value
        return _sources.value.filter { it.name.lowercase().contains(query) || it.pkgName.lowercase().contains(query) }
    }

    /**
     * Filtered installed (for search).
     */
    fun filteredInstalled(): List<AnimeExtension.Untrusted> {
        val query = _searchQuery.value.lowercase().trim()
        if (query.isBlank()) return _installed.value
        return _installed.value.filter { it.name.lowercase().contains(query) || it.pkgName.lowercase().contains(query) }
    }

    // ---- Trust management ----

    /** Pending extension to trust after a revoke (for auto-trust). */
    private var pendingTrustPkg: String? = null

    fun trustExtension(ext: AnimeExtension.Untrusted) {
        val result = extensionManager?.trust(ext.pkgName) ?: return
        _trustResult.value = result
        if (result is TrustResult.LimitExceeded) {
            pendingTrustPkg = ext.pkgName
        }
    }

    fun revokeTrust(ext: AnimeExtension.Installed) {
        extensionManager?.revokeTrust(ext.pkgName)
        Log.i(TAG, "Revoked trust from ${ext.name}")
    }

    fun dismissTrustResult() {
        _trustResult.value = null
        pendingTrustPkg = null
    }

    /**
     * Revoke trust from a source AND auto-trust the pending extension.
     * Called from the max-2 popup when the user taps "Remove" on one of the
     * current sources.
     */
    fun revokeAndAutoTrust(pkgToRevoke: String) {
        extensionManager?.revokeTrust(pkgToRevoke)
        // Wait a moment for the reload to process, then trust the pending one.
        val pending = pendingTrustPkg
        if (pending != null) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)  // wait for revoke to process
                val result = extensionManager?.trust(pending)
                if (result is TrustResult.Success) {
                    Log.i(TAG, "Auto-trusted $pending after revoking $pkgToRevoke")
                }
                _trustResult.value = null
                pendingTrustPkg = null
            }
        } else {
            _trustResult.value = null
        }
    }

    // ---- Install / Uninstall ----

    /**
     * Download the APK and launch the installer. Uses ACTION_INSTALL_PACKAGE
     * (not ACTION_VIEW) to skip the package-installer chooser dialog when
     * possible. Falls back to ACTION_VIEW if the system doesn't support it.
     */
    fun installExtension(ext: AnimeExtension.Available) {
        if (ext.pkgName in _downloading.value) return

        val ctx = context ?: return
        val client = networkHelper?.client ?: run {
            Toast.makeText(ctx, "Network not available", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _downloading.value = _downloading.value + ext.pkgName
            try {
                val apkUrl = "${ext.repoUrl}/apk/${ext.apkName}"
                val apkFile = File(ctx.cacheDir, "ext_${ext.pkgName}_${ext.versionCode}.apk")

                withContext(Dispatchers.IO) {
                    val response = client.newCall(GET(apkUrl)).execute()
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body?.byteStream()?.use { input ->
                        apkFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IOException("Empty response body")
                }

                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apkFile)
                // Use ACTION_INSTALL_PACKAGE for direct install (no chooser).
                // Fall back to ACTION_VIEW if the action doesn't resolve.
                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // Don't show the chooser — go straight to the system installer
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    putExtra(Intent.EXTRA_RETURN_RESULT, false)
                }
                if (intent.resolveActivity(ctx.packageManager) != null) {
                    ctx.startActivity(intent)
                } else {
                    // Fallback: ACTION_VIEW
                    val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(fallbackIntent)
                }
                Toast.makeText(ctx, "Installing ${ext.name}…", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Install failed for ${ext.pkgName}", e)
                Toast.makeText(ctx, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _downloading.value = _downloading.value - ext.pkgName
            }
        }
    }

    fun uninstallExtension(pkgName: String) {
        val ctx = context ?: return
        try {
            val uri = Uri.parse("package:$pkgName")
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(intent)
            } else {
                val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = uri
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(settingsIntent)
                Toast.makeText(ctx, "Open the app info to uninstall", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Uninstall failed for $pkgName", e)
            Toast.makeText(ctx, "Uninstall failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
