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
    // Keyed by AniList ID. Prevents re-fetching when navigating back to a detail page.
    private val detailCache = mutableMapOf<Int, Pair<Long, AniListAnime>>()
    private val cacheTtlMs = 5 * 60 * 1000L // 5 minutes

    /** Fetch trending anime (for the Browse screen). */
    suspend fun fetchTrending(page: Int = 1, perPage: Int = 20): List<AniListAnime> =
        queryList(TRENDING_QUERY, page, perPage)

    /** Fetch popular anime (for the Browse screen). */
    suspend fun fetchPopular(page: Int = 1, perPage: Int = 20): List<AniListAnime> =
        queryList(POPULAR_QUERY, page, perPage)

    /** Search anime by query. */
    suspend fun searchAnime(query: String, page: Int = 1, perPage: Int = 20): List<AniListAnime> =
        queryList(SEARCH_QUERY, page, perPage, search = query)

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
