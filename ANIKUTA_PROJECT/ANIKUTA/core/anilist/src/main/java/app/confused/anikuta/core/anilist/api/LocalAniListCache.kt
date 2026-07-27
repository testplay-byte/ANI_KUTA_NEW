package app.confused.anikuta.core.anilist.api

import app.confused.anikuta.core.anilist.model.AniListAnime
import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * Local persistent cache for AniList data — survives app restarts.
 *
 * Uses [PreferenceStore] (SharedPreferences-backed) to persist:
 * - Trending anime list (refreshed once per day or on pull-to-refresh)
 * - Popular anime list (same refresh policy)
 * - Individual anime details (by AniList ID, 24h TTL)
 *
 * This fixes the user's report: "when I close the app and open it again, it
 * reloads the whole thing again. It should properly show things from local
 * storage."
 *
 * The cache is JSON-serialized (kotlinx.serialization). AniListAnime is
 * @Serializable, so no custom mappers are needed.
 */
class LocalAniListCache(
    private val store: PreferenceStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ── List cache (trending/popular) ──

    private val trendingPref = store.getObject(
        "local_cache_trending",
        emptyList<AniListAnime>(),
        { list -> json.encodeToString(list) },
        { str -> try { json.decodeFromString<List<AniListAnime>>(str) } catch (e: Exception) { emptyList() } },
    )

    private val popularPref = store.getObject(
        "local_cache_popular",
        emptyList<AniListAnime>(),
        { list -> json.encodeToString(list) },
        { str -> try { json.decodeFromString<List<AniListAnime>>(str) } catch (e: Exception) { emptyList() } },
    )

    private val trendingTimestampPref = store.getLong("local_cache_trending_ts", 0)
    private val popularTimestampPref = store.getLong("local_cache_popular_ts", 0)

    /** 24 hours in milliseconds — the home page refreshes once per day. */
    val dailyRefreshMs = 24 * 60 * 60 * 1000L

    fun getCachedTrending(): List<AniListAnime> = trendingPref.get()
    fun getCachedPopular(): List<AniListAnime> = popularPref.get()

    fun getTrendingTimestamp(): Long = trendingTimestampPref.get()
    fun getPopularTimestamp(): Long = popularTimestampPref.get()

    fun isTrendingStale(): Boolean =
        System.currentTimeMillis() - getTrendingTimestamp() > dailyRefreshMs

    fun isPopularStale(): Boolean =
        System.currentTimeMillis() - getPopularTimestamp() > dailyRefreshMs

    fun saveTrending(list: List<AniListAnime>) {
        trendingPref.set(list)
        trendingTimestampPref.set(System.currentTimeMillis())
    }

    fun savePopular(list: List<AniListAnime>) {
        popularPref.set(list)
        popularTimestampPref.set(System.currentTimeMillis())
    }

    // ── Detail cache (individual anime by ID) ──

    private val detailCachePref = store.getObject(
        "local_cache_details",
        emptyMap<Int, String>(),
        { map -> json.encodeToString(map) },
        { str -> try { json.decodeFromString<Map<Int, String>>(str) } catch (e: Exception) { emptyMap() } },
    )

    private val detailTimestampPref = store.getObject(
        "local_cache_details_ts",
        emptyMap<Int, Long>(),
        { map -> json.encodeToString(map) },
        { str -> try { json.decodeFromString<Map<Int, Long>>(str) } catch (e: Exception) { emptyMap() } },
    )

    /** 24h TTL for detail cache. */
    private val detailTtlMs = 24 * 60 * 60 * 1000L

    fun getCachedDetail(id: Int): AniListAnime? {
        val timestamps = detailTimestampPref.get()
        val ts = timestamps[id] ?: return null
        if (System.currentTimeMillis() - ts > detailTtlMs) return null
        val jsonStr = detailCachePref.get()[id] ?: return null
        return try { json.decodeFromString<AniListAnime>(jsonStr) } catch (e: Exception) { null }
    }

    fun saveDetail(anime: AniListAnime) {
        val currentMap = detailCachePref.get().toMutableMap()
        val currentTs = detailTimestampPref.get().toMutableMap()
        currentMap[anime.id] = json.encodeToString(anime)
        currentTs[anime.id] = System.currentTimeMillis()
        detailCachePref.set(currentMap)
        detailTimestampPref.set(currentTs)
    }
}
