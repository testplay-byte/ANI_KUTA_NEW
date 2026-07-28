package app.confused.anikuta.data.extension.cache

import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Persists the link between an AniList anime and its extension source match.
 *
 * Stores the FULL match info (source ID + SAnime URL + SAnime title) so that
 * the details page can directly call `source.getEpisodeList(sAnime)` WITHOUT
 * re-searching on every app open.
 *
 * This fixes the user's report: "when I close the app and reopen it, it says
 * 'searching sources' again — the source was not saved."
 *
 * Key: anilistId (Int)
 * Value: SourceLink (sourceId, animeUrl, animeTitle)
 */
class SourceLinkStore(
    private val preferenceStore: PreferenceStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class SourceLink(
        val sourceId: Long,
        val animeUrl: String,
        val animeTitle: String,
    )

    private val store = preferenceStore.getObject(
        key = KEY,
        defaultValue = emptyMap<String, SourceLink>(),
        serializer = { map -> json.encodeToString(map) },
        deserializer = { str ->
            try { json.decodeFromString<Map<String, SourceLink>>(str) }
            catch (e: Exception) { emptyMap() }
        },
    )

    /** Get the saved source link for an AniList anime, or null if not saved. */
    fun getLink(anilistId: Int): SourceLink? = store.get()[anilistId.toString()]

    /** Save/update the source link for an AniList anime. */
    fun saveLink(anilistId: Int, sourceId: Long, animeUrl: String, animeTitle: String) {
        val map = store.get().toMutableMap()
        map[anilistId.toString()] = SourceLink(sourceId, animeUrl, animeTitle)
        store.set(map)
    }

    /** Remove the link (e.g. when the user manually links a different source). */
    fun removeLink(anilistId: Int) {
        val map = store.get().toMutableMap()
        map.remove(anilistId.toString())
        store.set(map)
    }

    /** Get all saved source links (for backup). Key = AniList anime ID (as String). */
    fun getAll(): Map<String, SourceLink> = store.get()

    val changes: Flow<Map<String, SourceLink>> = store.changes().map { it }

    companion object {
        private const val KEY = "pref_source_links"
    }
}
