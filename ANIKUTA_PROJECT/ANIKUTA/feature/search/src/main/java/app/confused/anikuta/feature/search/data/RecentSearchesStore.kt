package app.confused.anikuta.feature.search.data

import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Persists the user's recent search query strings on the Search page.
 *
 * - Order: most-recent first (we prepend on each search).
 * - Dedup: a re-searched query moves to the front (old entry removed).
 * - Capped at [MAX_ITEMS] (12) — matches the prototype's `take(12)`.
 * - Survives app restart (backed by SharedPreferences via [PreferenceStore]).
 *
 * Ported from the prototype's in-memory `recents` state, made persistent.
 */
class RecentSearchesStore(
    private val preferenceStore: PreferenceStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val store = preferenceStore.getObject(
        key = KEY,
        defaultValue = emptyList(),
        serializer = { list ->
            json.encodeToString(ListSerializer(String.serializer()), list)
        },
        deserializer = { str ->
            try {
                json.decodeFromString(ListSerializer(String.serializer()), str)
            } catch (e: Exception) {
                emptyList()
            }
        },
    )

    /** The current list of recent searches (most-recent first). */
    fun get(): List<String> = store.get()

    /**
     * Add a query to the front of the list (dedup + cap at [MAX_ITEMS]).
     * No-op if the query is blank.
     */
    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = store.get().toMutableList()
        // Dedup: remove any existing occurrence, then prepend.
        current.remove(trimmed)
        current.add(0, trimmed)
        store.set(current.take(MAX_ITEMS))
    }

    /** Remove a single recent search by value. */
    fun remove(query: String) {
        val current = store.get().toMutableList()
        current.remove(query)
        store.set(current)
    }

    /** Clear all recent searches. */
    fun clear() {
        store.set(emptyList())
    }

    companion object {
        private const val KEY = "pref_search_recents"
        const val MAX_ITEMS = 12
    }
}
