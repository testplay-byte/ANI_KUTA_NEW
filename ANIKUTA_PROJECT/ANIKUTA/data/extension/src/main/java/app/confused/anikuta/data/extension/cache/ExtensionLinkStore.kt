package app.confused.anikuta.data.extension.cache

import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Caches the link between an extension anime (sourceId + url) and its AniList
 * ID — used by the Search page's extension→AniList linking flow.
 *
 * When the user taps an extension search result on the Search page:
 *   1. Check this cache — if a link exists, skip the linking sheet and go
 *      straight to the AniList detail page for that ID.
 *   2. If no link exists, show [ExtensionLinkingSheet] (in `:feature:search`)
 *      which searches AniList by the extension anime's title, lets the user
 *      pick a match (or auto-links the first result), then caches the link here
 *      via [link].
 *
 * Key format: `"$sourceId:$animeUrl"` (stable across launches — sourceId is the
 * extension source's stable ID, animeUrl is the source-specific anime URL).
 * Value: the AniList anime ID (Int).
 *
 * Ported from the old ANIKUTA project's `ExtensionLinkStore.kt`, adapted to the
 * new project's [PreferenceStore] API (which has the same `getObject` shape).
 *
 * Placed in `:data:extension` (not `:feature:search`) so both the search page
 * and the (future) extension-only detail page can share it — per
 * `RULES/ai-agent-rules.md` §4, shared code lives in `:core`/`:data`, not a
 * feature module.
 */
class ExtensionLinkStore(
    private val preferenceStore: PreferenceStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val store = preferenceStore.getObject(
        key = KEY,
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

    /** Build the cache key for an extension anime. */
    private fun key(sourceId: Long, animeUrl: String) = "$sourceId:$animeUrl"

    /**
     * Get the linked AniList ID for an extension anime, or null if not linked.
     * Call this BEFORE showing the linking sheet — a hit skips the sheet.
     */
    fun getAniListId(sourceId: Long, animeUrl: String): Int? {
        return store.get()[key(sourceId, animeUrl)]
    }

    /**
     * Reverse lookup: given an AniList ID, return the extension source ID that
     * was used to link it most recently (or null if no link exists).
     *
     * Used by `AnimeDetailViewModel` to prefer the source the user originally
     * came from when loading episodes — fixes the owner's report: "it does not
     * load the episodes from the exact same extension from which I went to the
     * details page. Instead it sometimes picks a completely different extension."
     *
     * The key format is `"$sourceId:$animeUrl"`, so we parse the sourceId out
     * of the first matching key.
     */
    fun getPreferredSourceForAnilist(anilistId: Int): Long? {
        val map = store.get()
        val entry = map.entries.firstOrNull { it.value == anilistId } ?: return null
        val k = entry.key
        val sourceIdStr = k.substringBefore(':')
        return sourceIdStr.toLongOrNull()
    }

    /** All links (for backup / debugging). Key = "$sourceId:$animeUrl". */
    fun getAll(): Map<String, Int> = store.get()

    /** Cache the link between an extension anime and its AniList ID. */
    fun link(sourceId: Long, animeUrl: String, anilistId: Int) {
        val map = store.get().toMutableMap()
        map[key(sourceId, animeUrl)] = anilistId
        store.set(map)
    }

    /** Remove a link (e.g. if the AniList entry was wrong and the user wants to re-link). */
    fun unlink(sourceId: Long, animeUrl: String) {
        val map = store.get().toMutableMap()
        map.remove(key(sourceId, animeUrl))
        store.set(map)
    }

    /** Reactive stream of all links — for observing link changes. */
    val changes: Flow<Map<String, Int>> = store.changes().map { it }

    companion object {
        private const val KEY = "pref_extension_anilist_links"
    }
}
