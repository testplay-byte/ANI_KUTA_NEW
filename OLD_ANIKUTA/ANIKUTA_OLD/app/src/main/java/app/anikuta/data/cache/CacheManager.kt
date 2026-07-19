package app.anikuta.data.cache

import app.anikuta.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 3-step cache manager: Local → Supabase → AniList.
 *
 * Read flow:
 * 1. Try Local cache (fastest, 5 min TTL for home, 24h for detail)
 * 2. If miss → try Supabase (shared cache, 30 min TTL)
 * 3. If miss → fetch from AniList (source of truth)
 * 4. On AniList success: write back to Local + Supabase
 *
 * Fallback:
 * - AniList down → serve stale Supabase → stale Local
 * - Supabase down → skip to AniList
 * - All fail → error
 */
class CacheManager(
    private val localCache: LocalCache,
    private val supabaseClient: SupabaseClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val TTL_HOME_SHORT = 5 * 60 * 1000L     // 5 min
        const val TTL_DETAIL_LONG = 24 * 60 * 60 * 1000L  // 24h
        const val TTL_GENRES = 24 * 60 * 60 * 1000L   // 24h
    }

    /**
     * Get cached data or fetch from AniList.
     * @param key Cache key (e.g. "trending", "popular", "anime_123")
     * @param ttlMs TTL in milliseconds
     * @param supabaseKey Key for Supabase cache (same as key if null)
     * @param fetch Suspend function to fetch from AniList
     * @return The data (as a JSON string) or null if all sources fail
     */
    suspend fun <T> getOrFetch(
        key: String,
        ttlMs: Long = TTL_HOME_SHORT,
        supabaseKey: String? = key,
        fetch: suspend () -> T,
        serialize: (T) -> String,
        deserialize: (String) -> T,
    ): T? = withContext(Dispatchers.IO) {
        // Step 1: Try Local cache
        val localEntry = localCache.get(key)
        if (localEntry != null) {
            return@withContext try {
                deserialize(localEntry.value)
            } catch (e: Exception) {
                null
            }
        }

        // Step 2: Try Supabase
        if (supabaseKey != null) {
            val supabaseValue = supabaseClient.getHomepageCache(supabaseKey)
            if (supabaseValue != null) {
                // Write back to Local
                localCache.put(key, supabaseValue, ttlMs)
                return@withContext try {
                    deserialize(supabaseValue)
                } catch (e: Exception) {
                    null
                }
            }
        }

        // Step 3: Fetch from source (AniList)
        try {
            val data = fetch()
            val serialized = serialize(data)
            // Write back to Local + Supabase
            localCache.put(key, serialized, ttlMs)
            if (supabaseKey != null) {
                supabaseClient.putHomepageCache(supabaseKey, serialized, ttlMs)
            }
            return@withContext data
        } catch (e: Exception) {
            // AniList failed — try stale fallback
            // Stale Supabase
            if (supabaseKey != null) {
                val staleSupabase = supabaseClient.getHomepageCache(supabaseKey)
                if (staleSupabase != null) {
                    return@withContext try {
                        deserialize(staleSupabase)
                    } catch (e2: Exception) {
                        null
                    }
                }
            }
            // Stale Local
            val staleLocal = localCache.getStale(key)
            if (staleLocal != null) {
                return@withContext try {
                    deserialize(staleLocal.value)
                } catch (e2: Exception) {
                    null
                }
            }
            null  // All sources failed
        }
    }
}
