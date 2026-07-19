package app.anikuta.data.cache

import app.anikuta.data.AnimeDatabase
import dataanime.Anilist_cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local cache layer (SQLDelight).
 * Lives in :data so generated SQLDelight types are directly accessible.
 */
class LocalCache(
    private val db: AnimeDatabase,
) {
    suspend fun get(key: String): CacheEntry? = withContext(Dispatchers.IO) {
        val row = db.anilistcacheQueries.getCacheEntry(key).executeAsOneOrNull() ?: return@withContext null
        val now = System.currentTimeMillis()
        if (now - row.created_at > row.ttl_ms) {
            return@withContext null
        }
        CacheEntry(key, row.cache_value, row.created_at, row.ttl_ms)
    }

    suspend fun getStale(key: String): CacheEntry? = withContext(Dispatchers.IO) {
        val row = db.anilistcacheQueries.getCacheEntry(key).executeAsOneOrNull() ?: return@withContext null
        CacheEntry(key, row.cache_value, row.created_at, row.ttl_ms)
    }

    suspend fun put(key: String, value: String, ttlMs: Long) = withContext(Dispatchers.IO) {
        db.anilistcacheQueries.upsertCacheEntry(key, value, System.currentTimeMillis(), ttlMs)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.anilistcacheQueries.clearCache()
    }
}

data class CacheEntry(
    val key: String,
    val value: String,
    val createdAt: Long,
    val ttlMs: Long,
)
