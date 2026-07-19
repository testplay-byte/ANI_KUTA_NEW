package app.anikuta.data.cache

import app.anikuta.core.preference.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Caches the link between an extension anime (sourceId + url) and its
 * AniList ID (Phase I — extension-to-AniList linking).
 *
 * When the user taps an extension search result:
 *   1. Check this cache — if linked, go directly to the AniList DetailScreen
 *   2. If not linked, show a linking screen that searches AniList by title,
 *      finds the best match, caches the link, then opens the DetailScreen
 *
 * Key format: "$sourceId:$animeUrl" → AniList ID (Int)
 *
 * Related files:
 *   - SourceLinkingScreen.kt — the linking UI + AniList search
 *   - AnikutaNavGraph.kt — checks this cache before navigating
 *   - SearchScreen.kt — passes extension results to the linking flow
 */
class ExtensionLinkStore(
    private val preferenceStore: PreferenceStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val store = preferenceStore.getObject(
        key = "pref_extension_anilist_links",
        defaultValue = emptyMap<String, Int>(),
        serializer = { map ->
            json.encodeToString(
                MapSerializer(String.serializer(), Int.serializer()),
                map,
            )
        },
        deserializer = { str ->
            try {
                json.decodeFromString(
                    MapSerializer(String.serializer(), Int.serializer()),
                    str,
                )
            } catch (e: Exception) {
                emptyMap()
            }
        },
    )

    /** Key = "$sourceId:$animeUrl" */
    private fun key(sourceId: Long, animeUrl: String) = "$sourceId:$animeUrl"

    /** Get the linked AniList ID for an extension anime, or null if not linked. */
    fun getAniListId(sourceId: Long, animeUrl: String): Int? {
        return store.get()[key(sourceId, animeUrl)]
    }

    /** Get all links (for backup). Key format: "$sourceId:$animeUrl" → AniList ID. */
    fun getAll(): Map<String, Int> = store.get()

    /** Cache the link between an extension anime and its AniList ID. */
    fun link(sourceId: Long, animeUrl: String, anilistId: Int) {
        val map = store.get().toMutableMap()
        map[key(sourceId, animeUrl)] = anilistId
        store.set(map)
    }

    /** Remove a link (e.g. if the AniList entry was wrong). */
    fun unlink(sourceId: Long, animeUrl: String) {
        val map = store.get().toMutableMap()
        map.remove(key(sourceId, animeUrl))
        store.set(map)
    }

    /** Reactive stream of all links. */
    val changes: Flow<Map<String, Int>> = store.changes().map { it }
}
