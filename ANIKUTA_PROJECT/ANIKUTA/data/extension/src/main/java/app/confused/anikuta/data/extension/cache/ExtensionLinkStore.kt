package app.confused.anikuta.data.extension.cache

import android.util.Log
import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Caches the link between an extension anime (sourceId + url) and its
 * content_id — used by the Search page's extension→AniList linking flow.
 *
 * When the user taps an extension search result on the Search page:
 *   1. Check this cache — if a link exists, skip the linking sheet and go
 *      straight to the detail page for that content_id.
 *   2. If no link exists, show [ExtensionLinkingSheet] (in `:feature:search`)
 *      which searches AniList by the extension anime's title, lets the user
 *      pick a match (or auto-links the first result), then caches the link here
 *      via [link].
 *
 * # Key + value format (Phase 4, ADR-050)
 *
 * Key: `"$sourceId:$animeUrl"` (the local_id's source components — stable across
 * launches; sourceId is the extension source's stable ID, animeUrl is the
 * source-specific anime URL).
 *
 * Value: **content_id** (String, e.g., `"al:154587"` for AniList-linked).
 *
 * **Old format** (pre-Phase-4): value = `anilistId` (Int).
 * The [SourceLinkMigrator] re-keys existing entries on first launch (converts
 * the Int value to a `"al:$anilistId"` String).
 *
 * # Why content_id (not anilistId)?
 *
 * - **Unlinked anime:** content_id works for unlinked extension anime (the value
 *   would be the local_id `"aniyomi:sourceId:url"`), while anilistId was null.
 * - **Future-proof:** if the user links to MAL instead of AniList, the value
 *   becomes `"mal:<malId>"` without a schema change.
 *
 * # Logging (ADR-033)
 *
 * All link/unlink operations are logged at DEBUG level with tag [TAG]
 * (`AnikutaExtensionLink`). Use `adb logcat -s AnikutaExtensionLink` to inspect.
 */
class ExtensionLinkStore(
    private val preferenceStore: PreferenceStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val store = preferenceStore.getObject(
        key = KEY,
        defaultValue = emptyMap<String, String>(),
        serializer = { map ->
            json.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                map,
            )
        },
        deserializer = { str ->
            try {
                json.decodeFromString(
                    MapSerializer(String.serializer(), String.serializer()),
                    str,
                )
            } catch (e: Exception) {
                // Migration path: the old format was Map<String, Int>. If the
                // new String format fails to deserialize, try the old Int format
                // + convert to "al:$anilistId" strings.
                try {
                    json.decodeFromString(
                        MapSerializer(String.serializer(), Int.serializer()),
                        str,
                    ).mapValues { (_, anilistId) -> "al:$anilistId" }
                } catch (e2: Exception) {
                    Log.w(TAG, "Failed to deserialize extension links — starting fresh", e2)
                    emptyMap()
                }
            }
        },
    )

    /** Build the cache key for an extension anime. */
    private fun key(sourceId: Long, animeUrl: String) = "$sourceId:$animeUrl"

    /**
     * Get the linked content_id for an extension anime, or null if not linked.
     * Call this BEFORE showing the linking sheet — a hit skips the sheet.
     *
     * @return the content_id (e.g., `"al:154587"`), or null.
     */
    fun getContentId(sourceId: Long, animeUrl: String): String? {
        return store.get()[key(sourceId, animeUrl)]
    }

    /**
     * Get the linked AniList ID for an extension anime, or null if not linked
     * (or if linked to a non-AniList content_id).
     *
     * Convenience method for callers that still expect an anilistId. Parses the
     * content_id: if it starts with `"al:"`, returns the rest as an Int.
     */
    fun getAniListId(sourceId: Long, animeUrl: String): Int? {
        val contentId = getContentId(sourceId, animeUrl) ?: return null
        return contentId.removePrefix("al:").toIntOrNull()
    }

    /**
     * Reverse lookup: given an AniList ID, return the extension source ID that
     * was used to link it most recently (or null if no link exists).
     *
     * Used by `AnimeDetailViewModel` to prefer the source the user originally
     * came from when loading episodes — fixes the owner's report: "it does not
     * load the episodes from the exact same extension from which I went to the
     * details page."
     */
    fun getPreferredSourceForAnilist(anilistId: Int): Long? {
        val targetContentId = "al:$anilistId"
        val map = store.get()
        val entry = map.entries.firstOrNull { it.value == targetContentId } ?: return null
        val k = entry.key
        val sourceIdStr = k.substringBefore(':')
        return sourceIdStr.toLongOrNull()
    }

    /**
     * All links (for backup / debugging / migrator).
     * Key = "$sourceId:$animeUrl", value = content_id.
     */
    fun getAll(): Map<String, String> = store.get()

    /**
     * Cache the link between an extension anime and its content_id.
     *
     * @param sourceId the extension source's stable ID.
     * @param animeUrl the source-specific anime URL.
     * @param contentId the Tier 2 per-content identity (e.g., `"al:154587"`).
     */
    fun link(sourceId: Long, animeUrl: String, contentId: String) {
        Log.d(TAG, "link: sourceId=$sourceId, url=$animeUrl → contentId=$contentId")
        val map = store.get().toMutableMap()
        map[key(sourceId, animeUrl)] = contentId
        store.set(map)
    }

    /**
     * Convenience method for callers that still have an anilistId. Converts to
     * `"al:$anilistId"` content_id + calls [link].
     */
    fun linkByAnilistId(sourceId: Long, animeUrl: String, anilistId: Int) {
        link(sourceId, animeUrl, "al:$anilistId")
    }

    /** Remove a link (e.g. if the AniList entry was wrong and the user wants to re-link). */
    fun unlink(sourceId: Long, animeUrl: String) {
        Log.d(TAG, "unlink: sourceId=$sourceId, url=$animeUrl")
        val map = store.get().toMutableMap()
        map.remove(key(sourceId, animeUrl))
        store.set(map)
    }

    /**
     * Replace the entire link map (used by the migrator + backup restore).
     */
    fun replaceAll(newMap: Map<String, String>) {
        Log.i(TAG, "replaceAll: ${newMap.size} entries")
        store.set(newMap)
    }

    /** Reactive stream of all links — for observing link changes. */
    val changes: Flow<Map<String, String>> = store.changes().map { it }

    private companion object {
        private const val TAG = "AnikutaExtensionLink"
        private const val KEY = "pref_extension_anilist_links"
    }
}
