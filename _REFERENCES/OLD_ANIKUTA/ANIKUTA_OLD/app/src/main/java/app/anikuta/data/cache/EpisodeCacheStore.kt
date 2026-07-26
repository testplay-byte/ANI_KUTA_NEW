package app.anikuta.data.cache

import android.content.Context
import android.util.Log
import app.anikuta.source.api.model.SEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistent episode cache — saves episode lists to disk as JSON files.
 *
 * Survives app restarts (unlike the in-memory companion object cache in
 * DetailViewModel). When the user reopens an anime after killing the app,
 * episodes are loaded instantly from disk, then a background refresh
 * checks for new episodes.
 *
 * Cache files are stored in: app/files/episode_cache/{anilistId}.json
 *
 * Each file contains:
 *  - anilistId
 *  - sourceName (which extension provided the episodes)
 *  - timestamp (when the cache was written)
 *  - episodes (serialized SEpisode list)
 */
class EpisodeCacheStore(
    private val context: Context,
) {
    companion object {
        private const val TAG = "EpisodeCacheStore"
        private const val CACHE_DIR = "episode_cache"
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    private val cacheDir by lazy {
        File(context.filesDir, CACHE_DIR).apply { mkdirs() }
    }

    @Serializable
    data class CachedEpisodes(
        val anilistId: Int,
        val sourceName: String,
        val timestamp: Long,
        val episodes: List<CachedEpisode>,
    )

    @Serializable
    data class CachedEpisode(
        val url: String,
        val name: String,
        val episodeNumber: Float,
        val scanlator: String?,
        val previewUrl: String?,
        val summary: String?,
        val dateUpload: Long,
    )

    /**
     * Save episodes to disk cache.
     */
    suspend fun save(anilistId: Int, sourceName: String, episodes: List<SEpisode>) {
        withContext(Dispatchers.IO) {
            try {
                val cached = CachedEpisodes(
                    anilistId = anilistId,
                    sourceName = sourceName,
                    timestamp = System.currentTimeMillis(),
                    episodes = episodes.map { ep ->
                        CachedEpisode(
                            url = ep.url,
                            name = ep.name,
                            episodeNumber = ep.episode_number,
                            scanlator = ep.scanlator,
                            previewUrl = ep.preview_url,
                            summary = ep.summary,
                            dateUpload = ep.date_upload,
                        )
                    },
                )
                val file = File(cacheDir, "$anilistId.json")
                file.writeText(json.encodeToString(CachedEpisodes.serializer(), cached))
                Log.d(TAG, "Saved ${episodes.size} episodes to disk cache for anilistId=$anilistId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save episode cache for anilistId=$anilistId", e)
            }
        }
    }

    /**
     * Load episodes from disk cache.
     * Returns null if cache doesn't exist or is corrupted.
     */
    suspend fun load(anilistId: Int): Pair<List<SEpisode>, String>? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(cacheDir, "$anilistId.json")
                if (!file.exists()) return@withContext null

                val cached = json.decodeFromString(CachedEpisodes.serializer(), file.readText())
                val episodes = cached.episodes.map { ce ->
                    SEpisode.create().apply {
                        url = ce.url
                        name = ce.name
                        episode_number = ce.episodeNumber
                        scanlator = ce.scanlator
                        preview_url = ce.previewUrl
                        summary = ce.summary
                        date_upload = ce.dateUpload
                    }
                }
                Log.d(TAG, "Loaded ${episodes.size} episodes from disk cache for anilistId=$anilistId (age=${(System.currentTimeMillis() - cached.timestamp) / 1000}s)")
                Pair(episodes, cached.sourceName)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load episode cache for anilistId=$anilistId", e)
                null
            }
        }
    }

    /**
     * Get the cache timestamp (age). Returns 0 if not cached.
     */
    fun getCacheAge(anilistId: Int): Long {
        val file = File(cacheDir, "$anilistId.json")
        if (!file.exists()) return Long.MAX_VALUE
        return try {
            val cached = json.decodeFromString(CachedEpisodes.serializer(), file.readText())
            System.currentTimeMillis() - cached.timestamp
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    /**
     * Clear cache for a specific anime.
     */
    fun clear(anilistId: Int) {
        val file = File(cacheDir, "$anilistId.json")
        if (file.exists()) file.delete()
    }

    /**
     * Clear all cached episodes.
     */
    fun clearAll() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
