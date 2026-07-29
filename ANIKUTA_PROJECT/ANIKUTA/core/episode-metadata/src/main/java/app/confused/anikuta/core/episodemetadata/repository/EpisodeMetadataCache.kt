package app.confused.anikuta.core.episodemetadata.repository

import android.util.Log
import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata
import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Local persistent cache for episode metadata — survives app restarts.
 *
 * Uses [PreferenceStore] (SharedPreferences-backed) to persist the metadata
 * map per anime. The metadata is JSON-serialized.
 *
 * # Key format (Phase 4, ADR-050)
 *
 * Outer key: **content_id** (e.g., `"al:154587"` for AniList-linked,
 * `"aniyomi:123:url"` for unlinked extension anime).
 * Inner key: episode number (Int).
 *
 * **Old format** (pre-Phase-4): outer key = `anilistId.toString()` (Int as String).
 * The [EpisodeMetadataMigrator] re-keys existing entries on first launch.
 *
 * # Why content_id (not anilistId)?
 *
 * - **Unlinked anime:** content_id works for unlinked extension anime (falls back
 *   to local_id), while anilistId was null → metadata was skipped entirely
 *   (Doc 01 §6.2 — `AnimeDetailViewModel.kt:629-632`: `anime.anilistId ?: return`).
 * - **Source switching:** content_id survives source switches (same anime from a
 *   different extension has the same content_id if linked).
 *
 * # Logging (ADR-033)
 *
 * All cache hits/misses + save/clear operations are logged at DEBUG level with
 * tag [TAG] (`AnikutaEpisodeMetadata`). Use `adb logcat -s AnikutaEpisodeMetadata`
 * to inspect cache behavior.
 *
 * This fixes the user's report: "the metadata was not loaded like the metadata
 * should have been saved too so it should not reload again either."
 */
class EpisodeMetadataCache(
    private val store: PreferenceStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val metadataSerializer = MapSerializer(
        Int.serializer(),
        EpisodeMetadata.serializer(),
    )

    private val prefs = store.getObject(
        key = "episode_metadata_cache",
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
                Log.w(TAG, "Failed to deserialize metadata cache — starting fresh", e)
                emptyMap()
            }
        },
    )

    /**
     * Get cached metadata for an anime, or null if not cached.
     *
     * @param contentId the Tier 2 per-content identity (e.g., `"al:154587"`).
     * @return Map<episodeNumber, EpisodeMetadata> or null.
     */
    fun get(contentId: String): Map<Int, EpisodeMetadata>? {
        val jsonStr = prefs.get()[contentId]
        if (jsonStr == null) {
            Log.d(TAG, "Cache MISS: contentId=$contentId")
            return null
        }
        return try {
            val result = json.decodeFromString(metadataSerializer, jsonStr)
            Log.d(TAG, "Cache HIT: contentId=$contentId (${result.size} episodes)")
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize metadata for contentId=$contentId — clearing", e)
            clear(contentId)
            null
        }
    }

    /**
     * Save metadata for an anime.
     *
     * @param contentId the Tier 2 per-content identity.
     * @param metadata Map<episodeNumber, EpisodeMetadata>.
     */
    fun save(contentId: String, metadata: Map<Int, EpisodeMetadata>) {
        Log.d(TAG, "Save: contentId=$contentId (${metadata.size} episodes)")
        val map = prefs.get().toMutableMap()
        map[contentId] = json.encodeToString(metadataSerializer, metadata)
        prefs.set(map)
    }

    /** Clear metadata for a specific anime (by content_id). */
    fun clear(contentId: String) {
        Log.d(TAG, "Clear: contentId=$contentId")
        val map = prefs.get().toMutableMap()
        map.remove(contentId)
        prefs.set(map)
    }

    /** Clear all cached metadata. */
    fun clearAll() {
        Log.i(TAG, "ClearAll")
        prefs.set(emptyMap())
    }

    /**
     * Get all cached metadata for all anime (for backup export + the migrator).
     *
     * @return Map where outer key = content_id (or legacy anilistId.toString()),
     * inner value = JSON of Map<Int, EpisodeMetadata>.
     */
    fun getAll(): Map<String, String> = prefs.get()

    /**
     * Replace the entire cache map (used by the migrator for bulk re-keying +
     * by backup restore). More efficient than per-entry updates.
     */
    fun replaceAll(newMap: Map<String, String>) {
        Log.i(TAG, "ReplaceAll: ${newMap.size} entries")
        prefs.set(newMap)
    }

    /**
     * Re-key an entry from [oldKey] to [newKey] (used by the migrator + the
     * future ContentIdMigrator for link/unlink events).
     *
     * If [oldKey] doesn't exist, this is a no-op. If [newKey] already exists,
     * the old entry is still removed (the new entry wins).
     */
    fun rekey(oldKey: String, newKey: String) {
        val map = prefs.get().toMutableMap()
        val jsonStr = map.remove(oldKey) ?: return
        map[newKey] = jsonStr
        prefs.set(map)
        Log.d(TAG, "Rekey: $oldKey → $newKey")
    }

    private companion object {
        private const val TAG = "AnikutaEpisodeMetadata"
    }
}
