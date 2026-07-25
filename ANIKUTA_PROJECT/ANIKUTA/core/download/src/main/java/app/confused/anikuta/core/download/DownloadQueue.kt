package app.confused.anikuta.core.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicLong

/**
 * The download queue state machine + concurrency manager.
 *
 * Holds the full task list (active + completed) in a [StateFlow], persisted to
 * [DownloadStore] (throttled) so the queue survives restarts. Enforces the
 * concurrency limit from [DownloadPreferences] via a [Semaphore]. Drives the
 * [HttpDownloader] for each active task in its own coroutine Job.
 *
 * **Concurrency model.** A [Semaphore] with `concurrentDownloads` permits
 * guarantees at most N tasks download simultaneously. QUEUED tasks wait for a
 * permit; when one frees up (completion/pause/cancel/error), the next QUEUED
 * task acquires it. Pause/cancel cancels the Job → `CancellationException` →
 * the task moves to PAUSED/CANCELLED and the permit is released.
 *
 * **Progress throttling.** The StateFlow updates on every byte tick (cheap —
 * in-memory), but persistence to [DownloadStore] is throttled to once per
 * [PERSIST_INTERVAL_MS] so we don't hammer SharedPreferences during a big file.
 *
 * **Connectivity.** Before starting a task, [connectivityCheck] is consulted;
 * if Wi-Fi-only is on and we're not on Wi-Fi, the task stays QUEUED (the
 * manager surfaces a UI hint). The check is re-evaluated whenever a task is
 * enqueued/resumed and (in future) on connectivity-change broadcasts.
 *
 * **Thread-safety.** All mutations happen on the [scope]'s dispatcher
 * (Dispatchers.IO); the StateFlow is thread-safe to collect.
 */
class DownloadQueue(
    private val downloader: HttpDownloader,
    private val store: DownloadStore,
    private val preferences: DownloadPreferences,
    private val connectivityCheck: () -> Boolean = { true },
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val scope = scope

    // ── State ──
    // IMPORTANT: initialization order matters. [_tasks] MUST be initialized
    // before [idCounter] (which calls [loadMaxId]). Kotlin runs property
    // initializers top-to-bottom; if [idCounter] came first, [loadMaxId] would
    // read a null [_tasks] and NPE (this was the startup crash). [loadMaxId]
    // reads from the store directly so it has no dependency on [_tasks] —
    // defensive against future reordering.
    private val _tasks = MutableStateFlow(store.purgeCancelled())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val idCounter = AtomicLong(loadMaxId() + 1)

    private val jobs = mutableMapOf<Long, Job>()
    private var lastPersistAt = 0L

    /** Fired once when a task reaches COMPLETED (manager posts a notification). */
    var onTaskCompleted: ((DownloadTask) -> Unit)? = null

    /** Fired once when a task reaches ERROR (manager posts a notification). */
    var onTaskError: ((DownloadTask) -> Unit)? = null

    /** The concurrency permit gate (rebuilt when the pref changes — see [refreshConcurrency]). */
    @Volatile
    private var permits: Semaphore = Semaphore(currentConcurrentLimit())

    /** The last-applied concurrency limit (so [refreshConcurrency] detects changes). */
    @Volatile
    private var currentLimit: Int = currentConcurrentLimit()

    // ── Public queue operations (called by DefaultDownloadManager) ──

    /**
     * Enqueue a download. Dedup: returns the existing task ID if the same
     * episode is already queued/completed.
     */
    fun enqueue(request: DownloadRequest): Long {
        val existing = _tasks.value.firstOrNull { it.key == keyFor(request) }
        if (existing != null) {
            DownloadLogger.d("Download already exists (id=${existing.id}, status=${existing.status})")
            // If it was completed, keep it completed (don't re-download). If errored, retry.
            if (existing.status == DownloadStatus.ERROR) {
                resumeInternal(existing.id)
            }
            return existing.id
        }

        val task = DownloadTask(
            id = idCounter.getAndIncrement(),
            request = request,
            status = DownloadStatus.QUEUED,
            createdAt = System.currentTimeMillis(),
        )
        updateTasks(_tasks.value + task)
        persistNow()
        DownloadLogger.i("Enqueued: ${request.anime.title} EP ${request.episode.episodeNumber} (id=${task.id})")
        tryStartNext()
        return task.id
    }

    fun pause(taskId: Long) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (task.status != DownloadStatus.DOWNLOADING &&
            task.status != DownloadStatus.QUEUED) return
        jobs.remove(taskId)?.cancel()
        mutateTask(taskId) { it.copy(status = DownloadStatus.PAUSED, updatedAt = now()) }
        persistNow()
        DownloadLogger.i("Paused: id=$taskId")
        tryStartNext()
    }

    fun resume(taskId: Long) = resumeInternal(taskId)

    fun cancel(taskId: Long) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        jobs.remove(taskId)?.cancel()
        // Note: partial-file cleanup for a cancelled in-progress download is
        // handled by the manager's deleteDownload (storage.deleteEpisode). A
        // fresh re-download also overwrites the partial video file via
        // openVideoOutputStream (which deletes any existing file first).
        // Remove from the list entirely (cancelled = not kept).
        updateTasks(_tasks.value.filterNot { it.id == taskId })
        persistNow()
        DownloadLogger.i("Cancelled + removed: id=$taskId")
        tryStartNext()
    }

    fun retry(taskId: Long) {
        mutateTask(taskId) {
            if (it.status != DownloadStatus.ERROR) return@mutateTask it
            it.copy(status = DownloadStatus.QUEUED, progress = 0, errorMessage = null, updatedAt = now())
        }
        persistNow()
        DownloadLogger.i("Retrying: id=$taskId")
        tryStartNext()
    }

    /** Mark a completed task removed (keeps the list consistent; file deletion is the manager's job). */
    fun removeCompleted(taskId: Long) {
        updateTasks(_tasks.value.filterNot { it.id == taskId })
        persistNow()
    }

    /** Re-evaluate the concurrency limit (call when the pref changes). */
    fun refreshConcurrency() {
        val newLimit = currentConcurrentLimit()
        if (newLimit != currentLimit) {
            currentLimit = newLimit
            permits = Semaphore(newLimit)
            DownloadLogger.d("Concurrency limit: $newLimit")
            tryStartNext()
        }
    }

    // ── Internals ──

    private fun resumeInternal(taskId: Long) {
        mutateTask(taskId) {
            if (it.status != DownloadStatus.PAUSED && it.status != DownloadStatus.ERROR) return@mutateTask it
            it.copy(status = DownloadStatus.QUEUED, updatedAt = now())
        }
        persistNow()
        DownloadLogger.i("Resumed: id=$taskId")
        tryStartNext()
    }

    /**
     * Starts the next QUEUED task if a permit is free + connectivity allows.
     * Called after every state change. Cheap if nothing to start.
     */
    private fun tryStartNext() {
        if (!connectivityCheck()) {
            DownloadLogger.d("Skipping start — connectivity check failed (Wi-Fi-only?)")
            return
        }
        val next = _tasks.value.firstOrNull { it.status == DownloadStatus.QUEUED } ?: return
        if (jobs.containsKey(next.id)) return // already launching
        launchDownload(next)
    }

    private fun launchDownload(task: DownloadTask) {
        val job = scope.launch {
            try {
                permits.withPermit {
                    // Re-confirm status (may have been paused before the permit was acquired).
                    val current = _tasks.value.firstOrNull { it.id == task.id }
                    if (current?.status != DownloadStatus.QUEUED) return@withPermit
                    mutateTask(task.id) {
                        it.copy(status = DownloadStatus.DOWNLOADING, updatedAt = now())
                    }
                    persistNow()

                    val completed = downloader.download(task) { downloaded, total ->
                        val pct = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 99)
                        else if (downloaded > 0) -1 else 0
                        mutateTask(task.id) {
                            it.copy(
                                progress = if (pct < 0) it.progress else pct,
                                downloadedBytes = downloaded,
                                totalBytes = total,
                                updatedAt = now(),
                            )
                        }
                        persistThrottled()
                    }
                    mutateTask(task.id) { completed }
                    persistNow()
                    DownloadLogger.i("Completed: id=${task.id}")
                    onTaskCompleted?.invoke(completed)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Pause/cancel — the pause()/cancel() handlers already set the status.
                DownloadLogger.d("Job cancelled: id=${task.id}")
            } catch (e: DownloadException) {
                val errorTask = _tasks.value.firstOrNull { it.id == task.id }?.copy(
                    status = DownloadStatus.ERROR, errorMessage = e.message, updatedAt = now(),
                )
                if (errorTask != null) {
                    mutateTask(task.id) { errorTask }
                    persistNow()
                    onTaskError?.invoke(errorTask)
                }
                DownloadLogger.e("Download error: id=${task.id}", e)
            } catch (e: Exception) {
                val errorTask = _tasks.value.firstOrNull { it.id == task.id }?.copy(
                    status = DownloadStatus.ERROR,
                    errorMessage = e.message ?: e.javaClass.simpleName, updatedAt = now(),
                )
                if (errorTask != null) {
                    mutateTask(task.id) { errorTask }
                    persistNow()
                    onTaskError?.invoke(errorTask)
                }
                DownloadLogger.e("Unexpected error: id=${task.id}", e)
            } finally {
                jobs.remove(task.id)
                // A permit freed up — start the next queued task.
                tryStartNext()
            }
        }
        jobs[task.id] = job
    }

    private fun mutateTask(taskId: Long, transform: (DownloadTask) -> DownloadTask) {
        val current = _tasks.value
        val updated = current.map { if (it.id == taskId) transform(it) else it }
        if (updated != current) updateTasks(updated)
    }

    private fun updateTasks(newList: List<DownloadTask>) {
        _tasks.value = newList
    }

    private fun persistThrottled() {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastPersistAt >= PERSIST_INTERVAL_MS) {
            store.setAll(_tasks.value)
            lastPersistAt = nowMs
        }
    }

    private fun persistNow() {
        store.setAll(_tasks.value)
        lastPersistAt = System.currentTimeMillis()
    }

    private fun currentConcurrentLimit(): Int =
        preferences.concurrentDownloads().get().coerceIn(1, 5)

    /**
     * The highest task ID ever assigned (so new IDs don't collide with
     * persisted ones after a restart). Reads from the store directly (NOT
     * [_tasks]) so it's safe to call during construction before [_tasks] is
     * initialized — defensive against init-order bugs.
     */
    private fun loadMaxId(): Long = store.getAll().maxOfOrNull { it.id } ?: 0L

    private fun now() = System.currentTimeMillis()

    private fun keyFor(request: DownloadRequest): String =
        "${request.anime.anilistId}:${request.episode.episodeUrl}"

    companion object {
        private const val PERSIST_INTERVAL_MS = 1_000L // throttle SharedPreferences writes
    }
}
