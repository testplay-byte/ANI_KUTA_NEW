package app.confused.anikuta.data.extension.cache

import app.confused.anikuta.core.common.model.details.DataSource
import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Remembers the user's preferred data source (AniList vs Extension) for each anime,
 * so re-opening an anime respects the user's previous choice.
 *
 * # Keys
 * - **Linked anime**: `anilistId.toString()` (e.g. `"12345"`)
 * - **Unlinked extension anime**: `"ext:{sourceId}:{url}"` (e.g. `"ext:6789:anime/some-url"`)
 *
 * # Values
 * `DataSource.ANILIST` or `DataSource.EXTENSION` (stored as the enum name string).
 *
 * # Backup
 * The store is a PreferenceStore-backed JSON map — easy to serialize for backup/restore.
 * A future `DetailsViewPreferenceBackupProvider` can include this map in the backup
 * container (alongside `SourceLinkBackupProvider` + `ExtensionLinkStore`). The data
 * is small (one string per anime) and self-contained.
 *
 * # Library cover reflection
 * When the user prefers Extension view, the library should show the extension's cover.
 * The [AnimeDetailViewModel] handles this by updating the `Anime` DB row's `coverUrl`
 * + `coverColor` when the preference changes (via `AnimeRepository.updatePreferredCover`).
 */
class DetailsViewPreferenceStore(
    private val preferenceStore: PreferenceStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val store = preferenceStore.getObject(
        key = KEY,
        defaultValue = emptyMap<String, String>(),
        serializer = { map -> json.encodeToString(MapSerializer(String.serializer(), String.serializer()), map) },
        deserializer = { str ->
            try {
                json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), str)
            } catch (e: Exception) {
                emptyMap()
            }
        },
    )

    /** Get the preferred data source for a linked anime (by anilistId). */
    fun get(anilistId: Int): DataSource? = store.get()[anilistId.toString()]?.toDataSource()

    /** Get the preferred data source for an unlinked extension anime (by sourceId + url). */
    fun get(sourceId: Long, url: String): DataSource? = store.get()[extKey(sourceId, url)]?.toDataSource()

    /** Set the preferred data source for a linked anime (by anilistId). */
    fun set(anilistId: Int, dataSource: DataSource) {
        val current = store.get().toMutableMap()
        current[anilistId.toString()] = dataSource.name
        store.set(current)
    }

    /** Set the preferred data source for an unlinked extension anime (by sourceId + url). */
    fun set(sourceId: Long, url: String, dataSource: DataSource) {
        val current = store.get().toMutableMap()
        current[extKey(sourceId, url)] = dataSource.name
        store.set(current)
    }

    /** Remove the preference for a linked anime (e.g. when unlinked). */
    fun remove(anilistId: Int) {
        val current = store.get().toMutableMap()
        current.remove(anilistId.toString())
        store.set(current)
    }

    /** Get all preferences (for backup). */
    fun getAll(): Map<String, String> = store.get()

    /** Reactive flow of all preferences (for future reactive UI). */
    val changes: Flow<Map<String, String>> = store.changes().map { it }

    private fun extKey(sourceId: Long, url: String) = "ext:$sourceId:$url"

    private fun String.toDataSource(): DataSource? = when (this) {
        DataSource.ANILIST.name -> DataSource.ANILIST
        DataSource.EXTENSION.name -> DataSource.EXTENSION
        else -> null
    }

    companion object {
        private const val KEY = "pref_details_view_preference"
    }
}
