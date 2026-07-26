package app.confused.anikuta.core.download

import app.confused.anikuta.core.preferences.Preference
import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Persists the download queue + completed list across app restarts.
 *
 * Mirrors the `WatchProgressStore` pattern: a single JSON-serialized
 * `List<DownloadTask>` held in [PreferenceStore.getObject], with a reactive
 * [changes] Flow so the [DownloadQueue] + ViewModels update in real time.
 *
 * Why not SQLDelight? The download state is small (tens of tasks, not
 * thousands) and highly mutable (progress ticks). A pref-backed JSON list is
 * simpler, has no migration cost, and matches how `WatchProgressStore` already
 * works. The plan's status-tracking columns (ADR-024) apply to anime/episode
 * DB rows, not to the transient download queue. A SQLDelight migration is a
 * documented future option if the queue grows.
 *
 * **Writes are coalesced** by the queue: progress ticks don't write on every
 * byte — the queue writes on state CHANGES (queued/started/paused/completed/
 * error) + a throttled progress snapshot, so we don't hammer SharedPreferences.
 */
class DownloadStore(
    store: PreferenceStore,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val tasksPref: Preference<List<DownloadTask>> = store.getObject(
        KEY_TASKS,
        emptyList(),
        { list -> json.encodeToString(ListSerializer(DownloadTask.serializer()), list) },
        { str ->
            try {
                json.decodeFromString(ListSerializer(DownloadTask.serializer()), str)
            } catch (e: Exception) {
                DownloadLogger.w("Failed to decode download store, starting fresh", e)
                emptyList()
            }
        },
    )

    /** Reactive stream of ALL persisted tasks (active + completed). */
    val changes: Flow<List<DownloadTask>> = tasksPref.changes().map { it }

    /** Snapshot of all tasks. */
    fun getAll(): List<DownloadTask> = tasksPref.get()

    /** Replace the entire task list (used by the queue on every state change). */
    fun setAll(tasks: List<DownloadTask>) {
        tasksPref.set(tasks)
    }

    /** Remove terminal tasks the user cleared (cancelled) on startup. */
    fun purgeCancelled(): List<DownloadTask> {
        val current = tasksPref.get()
        val kept = current.filter { it.status != DownloadStatus.CANCELLED }
        if (kept.size != current.size) {
            tasksPref.set(kept)
            DownloadLogger.i("Purged ${current.size - kept.size} cancelled task(s) on startup")
        }
        return kept
    }

    companion object {
        private const val KEY_TASKS = "pref_download_tasks_v1"
    }
}
