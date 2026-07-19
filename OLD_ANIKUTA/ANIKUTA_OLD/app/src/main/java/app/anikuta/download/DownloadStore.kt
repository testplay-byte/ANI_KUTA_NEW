package app.anikuta.download

import android.util.Log
import app.anikuta.core.preference.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Persistent download queue store. Survives app restart.
 *
 * Stores a list of [DownloadStoreEntry] (serializable snapshot of a Download)
 * in PreferenceStore. On app start, the DownloadManager restores these into
 * live [Download] objects (with StateFlows) via [restore].
 *
 * The actual video resolution + download is handled by [DownloadDownloader].
 * This store only tracks the metadata + queue state.
 */
class DownloadStore(
    private val preferenceStore: PreferenceStore,
) {
    companion object {
        private const val TAG = "DownloadStore"
        private const val KEY = "active_anime_downloads"
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class DownloadStoreEntry(
        val id: String,
        val anilistId: Int,
        val sourceId: Long,
        val sourceName: String,
        val animeTitle: String,
        val episodeUrl: String,
        val episodeName: String,
        val episodeNumber: Float,
        val order: Int = 0,
        val status: Int = 1, // Download.State.QUEUE.value
        val progress: Int = 0,
        val totalSize: Long = -1L,
        val downloadedBytes: Long = 0L,
        val error: String? = null,
    )

    private val queuePref = preferenceStore.getObject(
        key = KEY,
        defaultValue = emptyList(),
        serializer = { list ->
            json.encodeToString(ListSerializer(DownloadStoreEntry.serializer()), list)
        },
        deserializer = { str ->
            try { json.decodeFromString(ListSerializer(DownloadStoreEntry.serializer()), str) }
            catch (e: Exception) { emptyList() }
        },
    )

    val queue: Flow<List<DownloadStoreEntry>> = queuePref.changes()

    fun getAll(): List<DownloadStoreEntry> = queuePref.get()

    /**
     * Add an entry to the store. Deduplicates by episodeUrl.
     * @return true if the entry was added, false if it was a duplicate.
     */
    fun add(entry: DownloadStoreEntry): Boolean {
        val list = queuePref.get().toMutableList()
        return if (list.none { it.episodeUrl == entry.episodeUrl }) {
            list.add(entry)
            queuePref.set(list)
            Log.d(TAG, "Added download: ${entry.episodeName}")
            true
        } else {
            Log.d(TAG, "Skipped duplicate download: ${entry.episodeName}")
            false
        }
    }

    fun addAll(entries: List<DownloadStoreEntry>) {
        val list = queuePref.get().toMutableList()
        val existingUrls = list.map { it.episodeUrl }.toSet()
        entries.filter { it.episodeUrl !in existingUrls }.forEach { list.add(it) }
        queuePref.set(list)
        Log.d(TAG, "Added ${entries.size} downloads (${list.size} total in queue)")
    }

    fun update(id: String, status: Int? = null, progress: Int? = null, error: String? = null) {
        val list = queuePref.get().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val existing = list[idx]
            list[idx] = existing.copy(
                status = status ?: existing.status,
                progress = progress ?: existing.progress,
                error = error ?: existing.error,
            )
            queuePref.set(list)
        }
    }

    fun remove(id: String) {
        val list = queuePref.get().toMutableList()
        list.removeAll { it.id == id }
        queuePref.set(list)
        Log.d(TAG, "Removed download: $id")
    }

    fun removeByEpisodeUrl(episodeUrl: String) {
        val list = queuePref.get().toMutableList()
        list.removeAll { it.episodeUrl == episodeUrl }
        queuePref.set(list)
    }

    fun clearCompleted() {
        val list = queuePref.get().filter { it.status != Download.State.DOWNLOADED.value }
        queuePref.set(list)
    }

    fun clearAll() {
        queuePref.set(emptyList())
    }

    fun findByEpisodeUrl(episodeUrl: String): DownloadStoreEntry? {
        return getAll().find { it.episodeUrl == episodeUrl }
    }
}
