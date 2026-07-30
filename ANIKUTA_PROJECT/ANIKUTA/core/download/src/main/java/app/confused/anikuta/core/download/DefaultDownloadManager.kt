package app.confused.anikuta.core.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
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
 * @param storage The SAF storage provider. DOWNLOAD-IDENTITY-STORAGE-UPDATE:
 *   previously constructed inline (`DownloadStorageProvider(appContext, preferences)`),
 *   now injected via Koin so that the same instance is shared with
 *   `DownloadMigration` + the `DownloadIdentityManager`'s `animeBaseDir` lambda.
 *   The injected instance carries the wired-up `DownloadIdentityManager`
 *   (see `DownloadAppModule`), enabling identity.json writes on folder creation
 *   + identity-aware folder lookup in [isEpisodeDownloaded] etc.
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
    private val storage: DownloadStorageProvider,
    scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            DownloadLogger.e("Uncaught coroutine exception in download scope (suppressed)", e)
        }
    ),
) : DownloadManager {

    private val appContext = context.applicationContext
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

    /** Reactive map keyed by `"$contentId|$episodeNumber"` → task, for episode-row UI. */
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
        val task = queue.tasks.value.firstOrNull { it.id == taskId }
        if (task != null) {
            // Task is in memory — delete from disk if completed.
            if (task.status == DownloadStatus.COMPLETED) {
                storage.deleteEpisode(task.request.anime, task.request.episode)
            }
            queue.removeCompleted(taskId)
        }
        // If the task is NOT in memory (e.g., after app restart), there's
        // nothing we can do here — the caller should use deleteAnimeDownloads
        // to delete ALL episodes for the anime by contentId.
    }

    override suspend fun deleteAnimeDownloads(contentId: String) {
        // Remove all in-memory COMPLETED tasks for this contentId.
        val tasks = queue.tasks.value.filter {
            it.request.anime.contentId == contentId && it.status == DownloadStatus.COMPLETED
        }
        tasks.forEach { queue.removeCompleted(it.id) }

        // ALWAYS delete the on-disk folder — even if there are no in-memory tasks
        // (e.g., after app restart when the queue was purged). The filesystem
        // has the actual files; the in-memory queue is just a cache.
        // The title parameter is used only for logging in the storage provider.
        val title = tasks.firstOrNull()?.request?.anime?.title ?: "Unknown"
        val deleted = storage.deleteAnime(contentId, title)
        if (!deleted) {
            // The folder might exist on disk even if findAnimeDir failed (e.g.,
            // identity.json contentId mismatch). Try a broader scan.
            Log.w("AnikutaDownload", "deleteAnimeDownloads: storage.deleteAnime returned false " +
                "for contentId=$contentId — the folder may still exist on disk")
        }
    }

    // ── Offline-playback queries ──
    //
    // Phase 6 (ADR-050): these take contentId + episodeNumber (NOT anilistId +
    // episodeUrl). The source-switching fix: when no in-memory task matches
    // (because the user switched source + the episodeUrl changed), fall back to
    // a filesystem scan by episode number.

    override suspend fun isEpisodeDownloaded(contentId: String, episodeNumber: Float): Boolean {
        // 1. Try the in-memory task lookup (fast path).
        val task = findTask(contentId, episodeNumber)
        if (task?.status == DownloadStatus.COMPLETED) return true

        // 2. Filesystem fallback (source-switching fix): scan the on-disk folder
        //    by contentId + episodeNumber. The folder structure is
        //    `<root>/ANIKUTA/downloads/anime/<Title [contentId]>/Episode NNN/`,
        //    so a match by episode number works even if the episodeUrl changed.
        return storage.findEpisodeDirByNumber(contentId, episodeNumber)?.let { epDir ->
            epDir.listFiles().any { it.isFile && it.name?.startsWith("video.") == true }
        } ?: false
    }

    override suspend fun getDownloadedVideoUri(contentId: String, episodeNumber: Float): String? {
        // 1. Try the in-memory task (has the exact videoUri).
        val task = findTask(contentId, episodeNumber)
        if (task?.status == DownloadStatus.COMPLETED) {
            return storage.getVideoUri(task.request.anime, task.request.episode) ?: task.videoUri
        }

        // 2. Filesystem fallback (source-switching): find the episode dir by
        //    number + look for a video file inside it.
        return storage.findEpisodeDirByNumber(contentId, episodeNumber)?.let { epDir ->
            epDir.listFiles().firstOrNull { it.isFile && it.name?.startsWith("video.") == true }?.uri?.toString()
        }
    }

    override suspend fun getDownloadedSubtitleUris(
        contentId: String,
        episodeNumber: Float,
    ): List<String> {
        // 1. Try the in-memory task.
        val task = findTask(contentId, episodeNumber)
        if (task?.status == DownloadStatus.COMPLETED) {
            return storage.getSubtitleUris(task.request.anime, task.request.episode)
                .ifEmpty { task.subtitleUris }
        }

        // 2. Filesystem fallback: scan the subtitles/ folder.
        return storage.findEpisodeDirByNumber(contentId, episodeNumber)?.let { epDir ->
            epDir.findFile("data")?.findFile("subtitles")?.listFiles()
                ?.filter { it.isFile }
                ?.map { it.uri.toString() }
                ?: emptyList()
        } ?: emptyList()
    }

    override suspend fun getDownloadedEpisodes(contentId: String): List<DownloadedEpisode> {
        // 1. In-memory COMPLETED tasks (fast path — has full metadata: videoUri,
        //    subtitleUris, sizeBytes, completedAt).
        val inMemory = queue.tasks.value
            .filter { it.request.anime.contentId == contentId && it.status == DownloadStatus.COMPLETED }
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

        // 2. Filesystem scan — covers episodes that exist on disk but are NOT in
        //    the in-memory DownloadStore queue. This happens after:
        //    - an app restart where the queue was purged (DownloadStore drift),
        //    - a content_id migration that re-keyed the cross-cutting stores but
        //      left the on-disk folder untouched,
        //    - manual file deletion / restoration from backup.
        //
        //    DOWNLOAD-STATUS-FILESYSTEM-FIX: without this scan, the details page
        //    would show such episodes as "not downloaded" even though the files
        //    are still on disk — silently breaking offline playback + the
        //    "Delete downloaded episodes?" prompt in toggleSave.
        val scanned = storage.scanDownloadedEpisodes(contentId)

        // 3. Merge: prefer in-memory (has full metadata). Add filesystem-only
        //    episodes — those whose episodeNumber is NOT already covered by an
        //    in-memory task — with best-effort metadata (unknown episodeUrl,
        //    sizeBytes, completedAt).
        val inMemoryEpNums = inMemory.map { it.episode.episodeNumber }.toSet()
        val filesystemOnly = scanned
            .filter { it.episodeNumber !in inMemoryEpNums }
            .map { s ->
                DownloadedEpisode(
                    episode = DownloadEpisodeInfo(
                        // The episodeUrl is not recoverable from the filesystem
                        // (we don't store it in metadata.json). An empty string
                        // signals "unknown" — callers that need the URL should
                        // fall back to the in-memory task or re-resolve from the
                        // extension.
                        episodeUrl = "",
                        episodeNumber = s.episodeNumber,
                        name = "Episode ${s.episodeNumber.toInt()}",
                    ),
                    videoUri = s.videoUri,
                    subtitleUris = s.subtitleUris,
                    // Unknown from filesystem (would require reading metadata.json
                    // + summing file lengths — not worth the I/O for the UI's
                    // "X episodes downloaded" count + the delete prompt).
                    sizeBytes = 0L,
                    completedAt = 0L,
                )
            }

        if (filesystemOnly.isNotEmpty()) {
            DownloadLogger.i("getDownloadedEpisodes: contentId=$contentId → " +
                "${inMemory.size} in-memory + ${filesystemOnly.size} filesystem-only " +
                "(${scanned.size - filesystemOnly.size} overlapping)")
        }
        return inMemory + filesystemOnly
    }

    /**
     * Find a task by content_id + episode number (source-independent).
     *
     * The key format is `"$contentId|$episodeNumber"` (3 decimal places).
     */
    private fun findTask(contentId: String, episodeNumber: Float): DownloadTask? {
        val key = "$contentId|${"%.3f".format(episodeNumber)}"
        return queue.tasks.value.firstOrNull { it.key == key }
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
