package app.confused.anikuta.core.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import app.confused.anikuta.core.download.advanced.AdvancedHttpDownloader
import app.confused.anikuta.core.download.advanced.DownloadResumeManager

/**
 * The DEFAULT-method download manager (standard OkHttp HTTP download — ADR-020).
 *
 * Wires together the [DownloadQueue] (state machine + concurrency), the
 * [HttpDownloader] (file I/O), the [DownloadStore] (persistence), the
 * [DownloadStorageProvider] (SAF folder), and the
 * [DownloadNotificationManager] (Android notifications). Implements the
 * [DownloadManager] interface; a future `OneDmDownloadManager` will implement
 * the same interface for the multi-threaded method.
 *
 * **Reactive split.** The queue owns one [StateFlow] of ALL tasks; this class
 * derives [activeDownloads] (in-queue) and [completedDownloads] (on-disk) from
 * it via `map`. Both are cold flows derived from the hot queue state — collect
 * on the main thread.
 *
 * **Notification driving.** A background collector observes the queue and
 * updates the notification manager (progress throttled inside the notifier).
 *
 * **Connectivity.** The [connectivityCheck] honours the Wi-Fi-only pref. It is
 * re-evaluated on every `tryStartNext`; a future enhancement will re-trigger
 * on `CONNECTIVITY_ACTION` broadcasts.
 *
 * @param context Application context (for SAF + notifications + connectivity).
 * @param okHttp Shared OkHttp client (connection-pooled; injected by DI).
 * @param preferences Download settings (folder URI, method, Wi-Fi-only, concurrency).
 * @param store The persisted task list (injected so it shares the PreferenceStore).
 * @param scope Long-lived coroutine scope (default: app-scoped IO supervisor).
 */
class DefaultDownloadManager(
    context: Context,
    private val okHttp: OkHttpClient,
    private val preferences: DownloadPreferences,
    private val store: DownloadStore,
    private val tempCache: TempDownloadCache,
    private val advancedDownloader: AdvancedHttpDownloader,
    private val resumeManager: DownloadResumeManager,
    scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            DownloadLogger.e("Uncaught coroutine exception in download scope (suppressed)", e)
        }
    ),
) : DownloadManager {

    private val appContext = context.applicationContext
    private val storage = DownloadStorageProvider(appContext, preferences)
    private val downloader = HttpDownloader(okHttp, storage, tempCache, preferences, advancedDownloader)
    private val notifier = DownloadNotificationManager(appContext)

    private val queue = DownloadQueue(
        downloader = downloader,
        store = store,
        preferences = preferences,
        connectivityCheck = { isNetworkAllowed() },
        scope = scope,
    ).also {
        it.onTaskCompleted = { task -> notifier.notifyCompleted(task) }
        it.onTaskError = { task -> notifier.notifyError(task) }
    }

    // Drive the progress summary notification from the queue state.
    // (One-shot completion/error notifications are posted by the queue's job
    // completion handler via the notifier — kept out of this hot collector to
    // avoid re-posting on every state emission.)
    //
    // **Resilience.** The collector body is wrapped in try/catch so a
    // notification/observer failure NEVER crashes the download engine. Without
    // this, an uncaught exception in the collector propagates to the
    // CoroutineExceptionHandler (none installed) → app crash. This was the
    // path of the enqueue-time `first{}` crash; the guard is defense-in-depth
    // on top of the notifier's own internal guards.
    private val observeJob = scope.launch {
        queue.tasks.collect { all ->
            try {
                val active = all.filter { it.isInQueue }
                notifier.updateProgress(active)
                if (active.isEmpty()) notifier.cancelActive()
            } catch (e: Exception) {
                DownloadLogger.e("Download state observer failed (non-fatal)", e)
            }
        }
    }

    override val activeDownloads: Flow<List<DownloadTask>> =
        queue.tasks.map { list -> list.filter { it.isInQueue } }

    override val completedDownloads: Flow<List<DownloadTask>> =
        queue.tasks.map { list -> list.filter { it.status == DownloadStatus.COMPLETED } }

    override val allDownloads: Flow<List<DownloadTask>> = queue.tasks

    /** Reactive map keyed by `"$anilistId:$episodeUrl"` → task, for episode-row UI. */
    override val episodeDownloadStates: Flow<Map<String, DownloadTask>> =
        queue.tasks.map { list -> list.associateBy { it.key } }

    override suspend fun enqueueDownload(request: DownloadRequest): Long {
        if (request.videoUrl.isBlank()) {
            DownloadLogger.e("enqueueDownload rejected: blank videoUrl")
            return -1L
        }
        if (!storage.isFolderReady()) {
            DownloadLogger.e("enqueueDownload rejected: no download folder configured")
            return -1L
        }
        return queue.enqueue(request)
    }

    override suspend fun pauseDownload(taskId: Long) = queue.pause(taskId)
    override suspend fun resumeDownload(taskId: Long) = queue.resume(taskId)
    override suspend fun cancelDownload(taskId: Long) = queue.cancel(taskId)
    override suspend fun retryDownload(taskId: Long) = queue.retry(taskId)

    override suspend fun removeFromQueue(taskId: Long) {
        // Only remove if the task is COMPLETED (don't remove active/queued tasks).
        val task = queue.tasks.value.firstOrNull { it.id == taskId } ?: return
        if (task.status == DownloadStatus.COMPLETED) {
            queue.removeCompleted(taskId)
        }
    }

    override fun setDownloadFolder(treeUriString: String) {
        storage.takeFolderPermission(android.net.Uri.parse(treeUriString))
    }

    override fun isFolderReady(): Boolean = storage.isFolderReady()

    override suspend fun deleteDownload(taskId: Long) {
        val task = queue.tasks.value.firstOrNull { it.id == taskId } ?: return
        if (task.status == DownloadStatus.COMPLETED) {
            storage.deleteEpisode(task.request.anime, task.request.episode)
        }
        queue.removeCompleted(taskId)
    }

    override suspend fun deleteAnimeDownloads(anilistId: Int) {
        val tasks = queue.tasks.value.filter {
            it.request.anime.anilistId == anilistId && it.status == DownloadStatus.COMPLETED
        }
        val first = tasks.firstOrNull()
        tasks.forEach { queue.removeCompleted(it.id) }
        if (first != null) {
            storage.deleteAnime(anilistId, first.request.anime.title)
        }
    }

    // ── Offline-playback queries ──

    override suspend fun isEpisodeDownloaded(anilistId: Int, episodeUrl: String): Boolean {
        val task = findTask(anilistId, episodeUrl)
        if (task?.status == DownloadStatus.COMPLETED) return true
        // Fallback: check the filesystem (covers files from a prior install).
        if (task == null) return false
        return storage.isEpisodeDownloaded(task.request.anime, task.request.episode)
    }

    override suspend fun getDownloadedVideoUri(anilistId: Int, episodeUrl: String): String? {
        val task = findTask(anilistId, episodeUrl) ?: return null
        if (task.status != DownloadStatus.COMPLETED) return null
        return storage.getVideoUri(task.request.anime, task.request.episode) ?: task.videoUri
    }

    override suspend fun getDownloadedSubtitleUris(
        anilistId: Int,
        episodeUrl: String,
    ): List<String> {
        val task = findTask(anilistId, episodeUrl) ?: return emptyList()
        if (task.status != DownloadStatus.COMPLETED) return emptyList()
        return storage.getSubtitleUris(task.request.anime, task.request.episode)
            .ifEmpty { task.subtitleUris }
    }

    override suspend fun getDownloadedEpisodes(anilistId: Int): List<DownloadedEpisode> {
        return queue.tasks.value
            .filter { it.request.anime.anilistId == anilistId && it.status == DownloadStatus.COMPLETED }
            .map { task ->
                DownloadedEpisode(
                    episode = task.request.episode,
                    videoUri = task.videoUri
                        ?: storage.getVideoUri(task.request.anime, task.request.episode) ?: "",
                    subtitleUris = task.subtitleUris,
                    sizeBytes = storage.episodeFolderSize(task.request.anime, task.request.episode),
                    completedAt = task.updatedAt,
                )
            }
    }

    private fun findTask(anilistId: Int, episodeUrl: String): DownloadTask? {
        return queue.tasks.value.firstOrNull { it.key == "$anilistId:$episodeUrl" }
    }

    /** Wi-Fi-only-aware connectivity check (best-effort; fails open on error). */
    private fun isNetworkAllowed(): Boolean {
        if (!preferences.wifiOnly().get()) return true
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            DownloadLogger.w("Connectivity check failed — allowing download", e)
            true
        }
    }
}
