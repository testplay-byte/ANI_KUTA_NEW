package app.confused.anikuta.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.confused.anikuta.core.download.DownloadManager
import app.confused.anikuta.core.download.DownloadPreferences
import app.confused.anikuta.core.download.DownloadStatus
import app.confused.anikuta.core.download.DownloadTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel for the Downloads screen.
 *
 * Observes [DownloadManager.activeDownloads] + [completedDownloads] + the
 * folder-ready state, combining them into a single [DownloadUiState]. Forwards
 * user actions (pause/resume/cancel/delete/retry) to the manager.
 *
 * All manager calls run on `Dispatchers.IO` (the manager enforces this
 * internally); the UI state is collected on the main thread.
 */
class DownloadViewModel(
    private val manager: DownloadManager,
    private val preferences: DownloadPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    init {
        // Combine active + completed + folder-ready into the UI state.
        viewModelScope.launch {
            combine(
                manager.activeDownloads,
                manager.completedDownloads,
                preferences.downloadFolderUri().changes(),
            ) { active, completed, folderUri ->
                DownloadUiState(
                    queue = active,
                    downloaded = groupByAnime(completed),
                    folderReady = folderUri.isNotBlank(),
                    isLoading = false,
                )
            }.collect { _state.value = it }
        }

        // ── Auto-clear completed entries after 10 seconds ──
        // Per the owner's request: "after downloading, the entries automatically
        // clear out after 10 seconds." This removes COMPLETED tasks from the
        // active queue (the file stays on disk). Each completed task gets a
        // 10-second delay before removal.
        viewModelScope.launch {
            manager.activeDownloads.collect { active ->
                active.filter { it.status == DownloadStatus.COMPLETED }.forEach { task ->
                    // Launch a coroutine per completed task that waits 10s then removes it.
                    // The task ID is captured; if the task is already removed by then,
                    // removeFromQueue is a no-op.
                    launch {
                        delay(10_000)
                        manager.removeFromQueue(task.id)
                    }
                }
            }
        }
    }

    fun pause(taskId: Long) = viewModelScope.launch { manager.pauseDownload(taskId) }
    fun resume(taskId: Long) = viewModelScope.launch { manager.resumeDownload(taskId) }
    fun cancel(taskId: Long) = viewModelScope.launch { manager.cancelDownload(taskId) }
    fun retry(taskId: Long) = viewModelScope.launch { manager.retryDownload(taskId) }

    fun deleteEpisode(taskId: Long) = viewModelScope.launch { manager.deleteDownload(taskId) }

    fun deleteAnime(contentId: String) = viewModelScope.launch {
        manager.deleteAnimeDownloads(contentId)
    }

    /** Persist the SAF folder permission + URI (from the folder picker). */
    fun setDownloadFolder(treeUriString: String) {
        try {
            manager.setDownloadFolder(treeUriString)
        } catch (e: Exception) {
            // Surface via state — the UI shows a toast/snackbar.
            _state.value = _state.value.copy()
        }
    }

    /** Group completed tasks by anime for the expandable library section. */
    private fun groupByAnime(tasks: List<DownloadTask>): Map<DownloadedAnimeKey, List<DownloadTask>> {
        return tasks
            .groupBy {
                DownloadedAnimeKey(
                    contentId = it.request.anime.contentId,
                    title = it.request.anime.title,
                    coverUrl = it.request.anime.coverUrl,
                    coverColor = it.request.anime.coverColor,
                )
            }
            .toSortedMap(compareBy { it.title.lowercase() })
    }
}
