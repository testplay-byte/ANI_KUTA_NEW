package app.anikuta.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.download.AudioFallback
import app.anikuta.download.Download
import app.anikuta.download.DownloadManager
import app.anikuta.download.DownloadPreferences
import app.anikuta.download.PriorityMode
import app.anikuta.download.DownloadStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 7 — ViewModel for the Downloads settings screen.
 *
 * Exposes ordered priority lists (quality, audio, server) as StateFlows so
 * the UI can drag-and-drop reorder them. Also exposes the quality-vs-audio
 * priority toggle and the audio fallback mode.
 */
class DownloadsViewModel : ViewModel() {

    companion object { private const val TAG = "DownloadsVM" }

    private val store: DownloadStore? = try { Injekt.get() } catch (e: Exception) { null }
    private val manager: DownloadManager? = try { Injekt.get() } catch (e: Exception) { null }
    private val prefs: DownloadPreferences? = try { Injekt.get() } catch (e: Exception) { null }

    private val _queue = MutableStateFlow<List<Download>>(emptyList())
    val queue: StateFlow<List<Download>> = _queue.asStateFlow()

    // ---- Ordered priority lists (drag-and-drop) ----
    private val _qualityOrder = MutableStateFlow<List<String>>(emptyList())
    val qualityOrder: StateFlow<List<String>> = _qualityOrder.asStateFlow()

    private val _audioOrder = MutableStateFlow<List<String>>(emptyList())
    val audioOrder: StateFlow<List<String>> = _audioOrder.asStateFlow()

    private val _serverOrder = MutableStateFlow<List<String>>(emptyList())
    val serverOrder: StateFlow<List<String>> = _serverOrder.asStateFlow()

    private val _priorityMode = MutableStateFlow(PriorityMode.QUALITY_FIRST)
    val priorityMode: StateFlow<PriorityMode> = _priorityMode.asStateFlow()

    private val _audioFallback = MutableStateFlow(AudioFallback.NEXT)
    val audioFallback: StateFlow<AudioFallback> = _audioFallback.asStateFlow()

    // ---- Toggles (StateFlow so the UI updates live) ----
    private val _downloadOverWifiOnly = MutableStateFlow(true)
    val downloadOverWifiOnly: StateFlow<Boolean> = _downloadOverWifiOnly.asStateFlow()

    private val _deleteAfterWatching = MutableStateFlow(false)
    val deleteAfterWatching: StateFlow<Boolean> = _deleteAfterWatching.asStateFlow()

    private val _showDownloadSize = MutableStateFlow(true)
    val showDownloadSize: StateFlow<Boolean> = _showDownloadSize.asStateFlow()

    private val _downloadMethod = MutableStateFlow("single_pass")
    val downloadMethod: StateFlow<String> = _downloadMethod.asStateFlow()

    init {
        // Migrate old single-value prefs if needed
        prefs?.migrateFromPhase6()

        // Load current values from prefs
        _qualityOrder.value = prefs?.preferredQualityOrder()?.get() ?: listOf("1080p", "720p", "360p")
        _audioOrder.value = prefs?.preferredAudioOrder()?.get() ?: listOf("sub", "dub")
        _serverOrder.value = prefs?.preferredServerOrder()?.get() ?: emptyList()
        _priorityMode.value = PriorityMode.fromValue(prefs?.qualityVsAudioPriority()?.get() ?: "")
        _audioFallback.value = AudioFallback.fromValue(prefs?.audioFallbackMode()?.get() ?: "")
        _downloadOverWifiOnly.value = prefs?.downloadOverWifiOnly()?.get() ?: true
        _deleteAfterWatching.value = prefs?.deleteAfterWatching()?.get() ?: false
        _showDownloadSize.value = prefs?.showDownloadSize()?.get() ?: true
        _downloadMethod.value = prefs?.downloadMethod()?.get() ?: "single_pass"

        // Observe download queue
        viewModelScope.launch {
            manager?.queue?.collect { downloads ->
                _queue.value = downloads
            }
        }
    }

    // ---- Reorder methods ----
    fun reorderQuality(from: Int, to: Int) {
        val list = _qualityOrder.value.toMutableList()
        if (from in list.indices && to in list.indices) {
            val item = list.removeAt(from)
            list.add(to, item)
            _qualityOrder.value = list
            prefs?.preferredQualityOrder()?.set(list)
        }
    }

    fun reorderAudio(from: Int, to: Int) {
        val list = _audioOrder.value.toMutableList()
        if (from in list.indices && to in list.indices) {
            val item = list.removeAt(from)
            list.add(to, item)
            _audioOrder.value = list
            prefs?.preferredAudioOrder()?.set(list)
        }
    }

    fun reorderServer(from: Int, to: Int) {
        val list = _serverOrder.value.toMutableList()
        if (from in list.indices && to in list.indices) {
            val item = list.removeAt(from)
            list.add(to, item)
            _serverOrder.value = list
            prefs?.preferredServerOrder()?.set(list)
        }
    }

    fun setPriorityMode(mode: PriorityMode) {
        _priorityMode.value = mode
        prefs?.qualityVsAudioPriority()?.set(mode.value)
    }

    fun setAudioFallback(mode: AudioFallback) {
        _audioFallback.value = mode
        prefs?.audioFallbackMode()?.set(mode.value)
    }

    // ---- Toggles setters (StateFlow declarations are above, near the others) ----
    fun setDownloadOverWifiOnly(v: Boolean) {
        _downloadOverWifiOnly.value = v
        prefs?.downloadOverWifiOnly()?.set(v)
    }
    fun setDeleteAfterWatching(v: Boolean) {
        _deleteAfterWatching.value = v
        prefs?.deleteAfterWatching()?.set(v)
    }

    fun setShowDownloadSize(v: Boolean) {
        _showDownloadSize.value = v
        prefs?.showDownloadSize()?.set(v)
    }

    fun setDownloadMethod(method: String) {
        _downloadMethod.value = method
        prefs?.downloadMethod()?.set(method)
    }

    // ---- Download queue actions ----
    fun cancelDownload(id: String) { manager?.cancelDownload(id) }
    fun removeDownload(id: String) { manager?.removeDownload(id) }
    fun clearCompleted() { manager?.clearCompleted() }
}
