package app.confused.anikuta.feature.search.data

import app.confused.anikuta.core.preferences.PreferenceStore
import app.confused.anikuta.feature.search.viewmodel.SearchSource
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Persists the user's recent search query strings on the Search page — **per
 * source** (AniList and Extension each have their own recents list).
 *
 * - Order: most-recent first (we prepend on each search).
 * - Dedup: a re-searched query moves to the front (old entry removed).
 * - Capped at [MAX_ITEMS] (12) per source.
 * - Survives app restart (backed by SharedPreferences via [PreferenceStore]).
 *
 * The store is a `Map<String, List<String>>` keyed by the source's name
 * (`"ANILIST"` or `"EXTENSION"`). This keeps the two lists independent so
 * switching sources doesn't cross-contaminate recents (per owner request:
 * "keep the recent searches of the AniList separate and the recent searches
 * of the extensions separate").
 *
 * Ported from the prototype's in-memory `recents` state, made persistent +
 * per-source.
 */
class RecentSearchesStore(
    private val preferenceStore: PreferenceStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val store = preferenceStore.getObject(
        key = KEY,
        defaultValue = emptyMap<String, List<String>>(),
        serializer = { map ->
            json.encodeToString(
                MapSerializer(String.serializer(), ListSerializer(String.serializer())),
                map,
            )
        },
        deserializer = { str ->
            try {
                json.decodeFromString(
                    MapSerializer(String.serializer(), ListSerializer(String.serializer())),
                    str,
                )
            } catch (e: Exception) {
                emptyMap()
            }
        },
    )

    /** The key under which a source's recents are stored. */
    private fun key(source: SearchSource): String = source.name

    /** The recents list for [source] (most-recent first). Empty if none. */
    fun get(source: SearchSource): List<String> = store.get()[key(source)] ?: emptyList()

    /**
     * Add a query to the front of [source]'s recents (dedup + cap at [MAX_ITEMS]).
     * No-op if the query is blank.
     */
    fun add(source: SearchSource, query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val map = store.get().toMutableMap()
        val current = (map[key(source)] ?: emptyList()).toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        map[key(source)] = current.take(MAX_ITEMS)
        store.set(map)
    }

    /** Remove a single recent search by value, for [source]. */
    fun remove(source: SearchSource, query: String) {
        val map = store.get().toMutableMap()
        val current = (map[key(source)] ?: emptyList()).toMutableList()
        current.remove(query)
        map[key(source)] = current
        store.set(map)
    }

    /** Clear all recent searches for [source]. */
    fun clear(source: SearchSource) {
        val map = store.get().toMutableMap()
        map.remove(key(source))
        store.set(map)
    }

    companion object {
        private const val KEY = "pref_search_recents_v2"
        const val MAX_ITEMS = 12
    }
}
