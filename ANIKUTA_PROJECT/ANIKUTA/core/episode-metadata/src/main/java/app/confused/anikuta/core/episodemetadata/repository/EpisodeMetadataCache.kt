package app.confused.anikuta.core.episodemetadata.repository

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
 * This fixes the user's report: "the metadata was not loaded like the metadata
 * should have been saved too so it should not reload again either."
 *
 * Per user: "Make sure that this metadata is also pre-saved in the application
 * and it does not get fetched every single time the user exits and re-enters."
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
            } catch (e: Exception) { emptyMap() }
        },
    )

    /**
     * Get cached metadata for an anime, or null if not cached.
     * @param animeId the AniList anime ID
     * @return Map<episodeNumber, EpisodeMetadata> or null
     */
    fun get(animeId: Int): Map<Int, EpisodeMetadata>? {
        val jsonStr = prefs.get()[animeId.toString()] ?: return null
        return try {
            json.decodeFromString(metadataSerializer, jsonStr)
        } catch (e: Exception) { null }
    }

    /**
     * Save metadata for an anime.
     * @param animeId the AniList anime ID
     * @param metadata Map<episodeNumber, EpisodeMetadata>
     */
    fun save(animeId: Int, metadata: Map<Int, EpisodeMetadata>) {
        val map = prefs.get().toMutableMap()
        map[animeId.toString()] = json.encodeToString(metadataSerializer, metadata)
        prefs.set(map)
    }

    /** Clear metadata for a specific anime. */
    fun clear(animeId: Int) {
        val map = prefs.get().toMutableMap()
        map.remove(animeId.toString())
        prefs.set(map)
    }

    /** Clear all cached metadata. */
    fun clearAll() {
        prefs.set(emptyMap())
    }

    /**
     * Get all cached metadata for all anime (for backup export).
     * @return Map where outer key = animeId (as String), inner value = JSON of Map<Int, EpisodeMetadata>.
     */
    fun getAll(): Map<String, String> = prefs.get()
}
