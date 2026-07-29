package app.confused.anikuta.data.extension.cache

import android.util.Log
import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Persists the link between a content (identified by its content_id) and its
 * extension source match.
 *
 * Stores the FULL match info (source ID + SAnime URL + SAnime title) so that
 * the details page can directly call `source.getEpisodeList(sAnime)` WITHOUT
 * re-searching on every app open.
 *
 * # Key format (Phase 4, ADR-050)
 *
 * Key: **content_id** (e.g., `"al:154587"` for AniList-linked,
 * `"aniyomi:123:url"` for unlinked extension anime).
 *
 * **Old format** (pre-Phase-4): key = `anilistId.toString()`.
 * The [SourceLinkMigrator] re-keys existing entries on first launch.
 *
 * # Why content_id (not anilistId)?
 *
 * - **Unlinked anime:** content_id works for unlinked extension anime (falls back
 *   to local_id), while anilistId was null → source links couldn't be stored.
 * - **Source switching:** content_id survives source switches (same anime from a
 *   different extension has the same content_id if linked).
 *
 * This fixes the user's report: "when I close the app and reopen it, it says
 * 'searching sources' again — the source was not saved."
 *
 * # Logging (ADR-033)
 *
 * All link save/remove operations are logged at DEBUG level with tag [TAG]
 * (`AnikutaSourceLink`). Use `adb logcat -s AnikutaSourceLink` to inspect.
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
            catch (e: Exception) {
                Log.w(TAG, "Failed to deserialize source links — starting fresh", e)
                emptyMap()
            }
        },
    )

    /**
     * Get the saved source link for a content, or null if not saved.
     *
     * @param contentId the Tier 2 per-content identity.
     */
    fun getLink(contentId: String): SourceLink? = store.get()[contentId]

    /**
     * Save/update the source link for a content.
     *
     * @param contentId the Tier 2 per-content identity.
     * @param sourceId the extension source's stable ID.
     * @param animeUrl the source-specific anime URL (SAnime.url).
     * @param animeTitle the anime's title (for display + debugging).
     */
    fun saveLink(contentId: String, sourceId: Long, animeUrl: String, animeTitle: String) {
        Log.d(TAG, "saveLink: contentId=$contentId, sourceId=$sourceId, url=$animeUrl")
        val map = store.get().toMutableMap()
        map[contentId] = SourceLink(sourceId, animeUrl, animeTitle)
        store.set(map)
    }

    /**
     * Remove the link (e.g. when the user manually links a different source).
     *
     * @param contentId the Tier 2 per-content identity.
     */
    fun removeLink(contentId: String) {
        Log.d(TAG, "removeLink: contentId=$contentId")
        val map = store.get().toMutableMap()
        map.remove(contentId)
        store.set(map)
    }

    /**
     * Get all saved source links (for backup + the migrator).
     * Key = content_id (or legacy anilistId.toString() for pre-Phase-4 entries).
     */
    fun getAll(): Map<String, SourceLink> = store.get()

    /**
     * Replace the entire link map (used by the migrator for bulk re-keying +
     * by backup restore).
     */
    fun replaceAll(newMap: Map<String, SourceLink>) {
        Log.i(TAG, "replaceAll: ${newMap.size} entries")
        store.set(newMap)
    }

    /**
     * Re-key an entry from [oldKey] to [newKey] (used by the migrator + the
     * future ContentIdMigrator for link/unlink events).
     */
    fun rekey(oldKey: String, newKey: String) {
        val map = store.get().toMutableMap()
        val link = map.remove(oldKey) ?: return
        map[newKey] = link
        store.set(map)
        Log.d(TAG, "rekey: $oldKey → $newKey")
    }

    /** Reactive stream of all source links. */
    val changes: Flow<Map<String, SourceLink>> = store.changes().map { it }

    private companion object {
        private const val TAG = "AnikutaSourceLink"
        private const val KEY = "pref_source_links"
    }
}
