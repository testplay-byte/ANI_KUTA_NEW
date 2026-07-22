package app.confused.anikuta.core.anilist.api

import app.confused.anikuta.core.anilist.model.AniListAnime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Minimal AniList GraphQL client (ADR-030: raw HTTP + kotlinx-serialization).
 *
 * Uses the public AniList API (no auth required for trending/popular/search).
 * Auth (for personalized data) will be added in Phase 7 (ADR-013).
 *
 * Rate limit: 90 req/min (AniList's limit). This client doesn't enforce it yet
 * — will be added when we have proper caching.
 */
class AniListApi(
    private val client: OkHttpClient = defaultClient(),
) {
    // In-memory cache for anime details (5-minute TTL).
    private val detailCache = mutableMapOf<Int, Pair<Long, AniListAnime>>()
    private val cacheTtlMs = 5 * 60 * 1000L // 5 minutes

    // In-memory cache for list queries (trending/popular) — stale-while-revalidate.
    // Key: query type + page. Value: (timestamp, results).
    private val listCache = mutableMapOf<String, Pair<Long, List<AniListAnime>>>()

    /** Fetch trending anime (with stale-while-revalidate caching). */
    suspend fun fetchTrending(page: Int = 1, perPage: Int = 20): List<AniListAnime> {
        val cacheKey = "trending_$page"
        return cachedListQuery(cacheKey) { queryList(TRENDING_QUERY, page, perPage) }
    }

    /** Fetch popular anime (with stale-while-revalidate caching). */
    suspend fun fetchPopular(page: Int = 1, perPage: Int = 20): List<AniListAnime> {
        val cacheKey = "popular_$page"
        return cachedListQuery(cacheKey) { queryList(POPULAR_QUERY, page, perPage) }
    }

    /** Search anime by query (NOT cached — search results change with the query). */
    suspend fun searchAnime(query: String, page: Int = 1, perPage: Int = 20): List<AniListAnime> =
        queryList(SEARCH_QUERY, page, perPage, search = query)

    /**
     * Search anime with filters — used by the Search page's FilterSheet.
     *
     * Builds a dynamic GraphQL query that includes only the filter arguments the
     * caller actually set (genres, year, season, format, status, sort, minScore).
     * The [query] may be null for a filter-only browse (e.g. "all Winter 2024 TV
     * anime sorted by popularity"). When [query] is non-null, it's passed as the
     * AniList `search` argument.
     *
     * **Threading:** runs on `Dispatchers.IO` (via [queryList]).
     *
     * @param query free-text search, or null to browse by filters only.
     * @param genres multi-select genre set (AniList genre names, e.g. "Action").
     *   Empty = no genre filter.
     * @param year the `seasonYear` to filter by, or null for any year.
     * @param season the AniList season enum ("WINTER"|"SPRING"|"SUMMER"|"FALL"),
     *   or null. Note: AniList requires `seasonYear` to be set when `season` is
     *   set — if season is non-null but year is null, we still send season alone
     *   (AniList tolerates this and returns cross-year season results).
     * @param format the AniList format enum ("TV"|"MOVIE"|"OVA"|"ONA"|"SPECIAL"|
     *   "TV_SHORT"), or null.
     * @param status the AniList status enum ("RELEASING"|"FINISHED"|
     *   "NOT_YET_RELEASED"|"CANCELLED"), or null.
     * @param sort the AniList sort enum (e.g. "POPULARITY_DESC", "SCORE_DESC",
     *   "START_DATE_DESC", "TITLE_ROMAJI", "TRENDING_DESC", "FAVOURITES_DESC").
     *   Defaults to "POPULARITY_DESC".
     * @param minScore the minimum `averageScore` (0-100, step 5), or 0 for no
     *   minimum.
     */
    suspend fun searchAnimeWithFilters(
        query: String?,
        page: Int = 1,
        perPage: Int = 20,
        genres: Set<String> = emptySet(),
        year: Int? = null,
        season: String? = null,
        format: String? = null,
        status: String? = null,
        sort: String = "POPULARITY_DESC",
        minScore: Int = 0,
    ): List<AniListAnime> = withContext(Dispatchers.IO) {
        // Build the media(...) argument list dynamically — only include the
        // filters the caller actually set. This keeps the GraphQL valid (AniList
        // rejects unknown/null args on some fields) and the query small.
        val mediaArgs = buildList {
            add("type: ANIME")
            add("sort: $sort")
            if (query != null && query.isNotBlank()) add("search: ${'$'}search")
            if (genres.isNotEmpty()) {
                val genreList = genres.joinToString(", ") { "\"$it\"" }
                add("genre_in: [$genreList]")
            }
            if (year != null) add("seasonYear: ${'$'}year")
            if (season != null) add("season: ${'$'}season")
            if (format != null) add("format: ${'$'}format")
            if (status != null) add("status: ${'$'}status")
            if (minScore > 0) add("averageScore_greater: ${'$'}minScore")
            // Keep adult content out of search results (matches fetchPopular/fetchTrending).
            add("isAdult: false")
        }.joinToString(", ")

        // Build the variable declarations ($var: Type) only for the variables
        // we're actually using. AniList rejects declared-but-unused variables
        // with a 400 Bad Request, so each $var below is conditional.
        val varDecls = buildList {
            add("${'$'}page: Int")
            add("${'$'}perPage: Int")
            if (query != null && query.isNotBlank()) add("${'$'}search: String")
            if (year != null) add("${'$'}year: Int")
            if (season != null) add("${'$'}season: MediaSeason")
            if (format != null) add("${'$'}format: MediaFormat")
            if (status != null) add("${'$'}status: MediaStatus")
            if (minScore > 0) add("${'$'}minScore: Int")
        }.joinToString(", ")

        val gql = """
            query ($varDecls) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media($mediaArgs) {
                  $ANIME_FIELDS
                }
              }
            }
        """.trimIndent()

        // Build the variables JSON object — only the ones we declared.
        val variables = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
            if (query != null && query.isNotBlank()) put("search", query)
            if (year != null) put("year", year)
            if (season != null) put("season", season)
            if (format != null) put("format", format)
            if (status != null) put("status", status)
            if (minScore > 0) put("minScore", minScore)
        }

        val body = buildJsonObject {
            put("query", gql)
            put("variables", variables)
        }

        val request = Request.Builder()
            .url(API_URL)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: return@withContext emptyList()

        if (!response.isSuccessful) {
            return@withContext emptyList()
        }

        parseAnimeList(responseBody)
    }

    /**
     * Get cached trending data if available (for instant display on app open).
     * Returns null if no cache exists.
     */
    fun getCachedTrending(): List<AniListAnime>? {
        val cached = listCache["trending_1"]
        return cached?.second
    }

    /**
     * Stale-while-revalidate pattern for list queries:
     * 1. If cache exists (even if stale): return it immediately.
     * 2. Always fetch fresh data from network.
     * 3. If network succeeds: update cache + return fresh data.
     * 4. If network fails: return cached data (even if stale).
     */
    private suspend fun cachedListQuery(
        cacheKey: String,
        networkCall: suspend () -> List<AniListAnime>,
    ): List<AniListAnime> {
        val cached = listCache[cacheKey]

        // If no cache, must fetch from network
        if (cached == null) {
            val fresh = networkCall()
            listCache[cacheKey] = System.currentTimeMillis() to fresh
            return fresh
        }

        // Cache exists — return it immediately (stale-while-revalidate)
        // The caller can use getCachedTrending() for instant display,
        // then call fetchTrending() to refresh in the background.
        return try {
            val fresh = networkCall()
            listCache[cacheKey] = System.currentTimeMillis() to fresh
            fresh
        } catch (e: Exception) {
            // Network failed — return stale cached data
            cached.second
        }
    }

    /** Fetch a single anime by its AniList ID (with 5-min in-memory cache). */
    suspend fun fetchById(id: Int): AniListAnime? {
        // Check cache
        val cached = detailCache[id]
        if (cached != null && System.currentTimeMillis() - cached.first < cacheTtlMs) {
            return cached.second
        }

        // Fetch from network
        val result = fetchByIdFromNetwork(id) ?: return null

        // Cache it
        detailCache[id] = System.currentTimeMillis() to result
        return result
    }

    private suspend fun fetchByIdFromNetwork(id: Int): AniListAnime? = withContext(Dispatchers.IO) {
        val variables = buildJsonObject {
            put("id", id)
        }

        val body = buildJsonObject {
            put("query", BY_ID_QUERY)
            put("variables", variables)
        }

        val request = Request.Builder()
            .url(API_URL)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: return@withContext null

        if (!response.isSuccessful) return@withContext null

        val root = Json.parseToJsonElement(responseBody).jsonObject
        val data = root["data"]?.jsonObject ?: return@withContext null
        val media = data["Media"] ?: return@withContext null

        try {
            Json.decodeFromJsonElement(AniListAnime.serializer(), media)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun queryList(
        query: String,
        page: Int,
        perPage: Int,
        search: String? = null,
    ): List<AniListAnime> = withContext(Dispatchers.IO) {
        val variables = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
            if (search != null) put("search", search)
        }

        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }

        val request = Request.Builder()
            .url(API_URL)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: return@withContext emptyList()

        if (!response.isSuccessful) {
            return@withContext emptyList()
        }

        parseAnimeList(responseBody)
    }

    private fun parseAnimeList(json: String): List<AniListAnime> {
        val root = Json.parseToJsonElement(json).jsonObject
        val data = root["data"]?.jsonObject ?: return emptyList()
        val page = data["Page"]?.jsonObject ?: return emptyList()
        val media = page["media"]?.jsonArray ?: return emptyList()

        return media.mapNotNull { element ->
            try {
                Json.decodeFromJsonElement(AniListAnime.serializer(), element)
            } catch (e: Exception) {
                null
            }
        }
    }

    companion object {
        private const val API_URL = "https://graphql.anilist.co"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        // GraphQL queries — trending, popular, search.
        // All use the same structure: Page(page, perPage) { media { ... fields } }

        private const val ANIME_FIELDS = """
            id
            title { romaji english native }
            coverImage { medium large extraLarge color }
            averageScore
            meanScore
            popularity
            favourites
            format
            episodes
            status
            description(asHtml: false)
            bannerImage
            genres
            season
            seasonYear
            startDate { year month day }
            endDate { year month day }
            studios(isMain: true) { nodes { id name isAnimationStudio } }
            nextAiringEpisode { id airingAt timeUntilAiring episode }
            source
            countryOfOrigin
            isAdult
            idMal
        """

        private const val TRENDING_QUERY = """
            query (${'$'}page: Int, ${'$'}perPage: Int) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media(sort: TRENDING_DESC, type: ANIME, isAdult: false) {
                  $ANIME_FIELDS
                }
              }
            }
        """

        private const val POPULAR_QUERY = """
            query (${'$'}page: Int, ${'$'}perPage: Int) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media(sort: POPULARITY_DESC, type: ANIME, isAdult: false) {
                  $ANIME_FIELDS
                }
              }
            }
        """

        private const val SEARCH_QUERY = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}search: String) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media(search: ${'$'}search, sort: SEARCH_MATCH, type: ANIME, isAdult: false) {
                  $ANIME_FIELDS
                }
              }
            }
        """

        private const val BY_ID_QUERY = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                $ANIME_FIELDS
              }
            }
        """
    }
}
