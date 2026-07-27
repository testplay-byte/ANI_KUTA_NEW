package app.anikuta.data.anilist.repository

import app.anikuta.data.anilist.api.AniListQueries
import app.anikuta.data.anilist.model.*
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * AniList GraphQL client.
 * Fetches anime data from the AniList API for the home page.
 *
 * This is OUR code — aniyomi doesn't have this. AniList is our discovery layer.
 * Direct fetch for now (3-step cache = Phase 2).
 */
class AniListRepository(
    private val networkHelper: NetworkHelper,
) {
    private val client get() = networkHelper.client
    private val json = Json { ignoreUnknownKeys = true }

    private val apiUrl = "https://graphql.anilist.co"
    private val jsonMime = "application/json; charset=utf-8".toMediaType()

    private suspend fun graphqlRequest(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
        responseParser: (JsonObject) -> List<AniListAnime>,
    ): List<AniListAnime> = withContext(Dispatchers.IO) {
        val jsonBody = buildJsonObject {
            put("query", query)
            if (variables.isNotEmpty()) {
                putJsonObject("variables") {
                    variables.forEach { (k, v) ->
                        when (v) {
                            is String -> put(k, v)
                            is Number -> put(k, v)
                            is Int -> put(k, v)
                            else -> put(k, v.toString())
                        }
                    }
                }
            }
        }.toString().toRequestBody(jsonMime)

        val request = Request.Builder()
            .url(apiUrl)
            .post(jsonBody)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) {
                throw RuntimeException("AniList API error: ${it.code}")
            }
            val responseBody = it.body?.string()
                ?: throw RuntimeException("Empty response from AniList")
            val jsonElement = json.parseToJsonElement(responseBody).jsonObject
            jsonElement["errors"]?.let { errors ->
                val message = errors.jsonArray.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                    ?: "Unknown GraphQL error"
                throw RuntimeException("AniList GraphQL error: $message")
            }
            responseParser(jsonElement)
        }
    }

    private fun parseMediaList(root: JsonObject): List<AniListAnime> {
        val mediaArray = root["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray
            ?: return emptyList()
        return mediaArray.map { element ->
            json.decodeFromJsonElement(AniListAnime.serializer(), element)
        }
    }

    suspend fun getTrending(page: Int = 1, perPage: Int = 20): List<AniListAnime> =
        graphqlRequest(AniListQueries.trending, mapOf("page" to page, "perPage" to perPage)) { parseMediaList(it) }

    suspend fun getPopular(page: Int = 1, perPage: Int = 20): List<AniListAnime> =
        graphqlRequest(AniListQueries.popular, mapOf("page" to page, "perPage" to perPage)) { parseMediaList(it) }

    suspend fun getFreshlyUpdated(page: Int = 1, perPage: Int = 20): List<AniListAnime> =
        graphqlRequest(AniListQueries.freshlyUpdated, mapOf("page" to page, "perPage" to perPage)) { parseMediaList(it) }

    suspend fun getGenres(): List<String> = withContext(Dispatchers.IO) {
        val jsonBody = buildJsonObject {
            put("query", AniListQueries.genres)
        }.toString().toRequestBody(jsonMime)

        val request = Request.Builder()
            .url(apiUrl)
            .post(jsonBody)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            val root = json.parseToJsonElement(responseBody).jsonObject
            root["data"]?.jsonObject?.get("GenreCollection")?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?: emptyList()
        }
    }

    suspend fun browseByGenre(genre: String, page: Int = 1, perPage: Int = 20): List<AniListAnime> =
        graphqlRequest(AniListQueries.browseByGenre, mapOf("genre" to genre, "page" to page, "perPage" to perPage)) { parseMediaList(it) }

    /**
     * Phase 5 task 5.11 — AniList search (Q5 decision: AniList-only for Phase 5;
     * extension search deferred to Phase 7).
     *
     * Issues a GraphQL `Page(search: $search, sort: SEARCH_MATCH, perPage: 25)`
     * request and parses it via [parseMediaList] (same Media field selection as
     * trending/popular). Throws on network/GraphQL errors so the caller (the
     * SearchViewModel) can surface them in the Error state.
     */
    suspend fun searchAnime(query: String, page: Int = 1, perPage: Int = 25): List<AniListAnime> =
        graphqlRequest(AniListQueries.searchAnime, mapOf("search" to query, "page" to page, "perPage" to perPage)) { parseMediaList(it) }

    /** Search including adult results (isAdult: true). */
    suspend fun searchAnimeWithAdult(query: String, page: Int = 1, perPage: Int = 25): List<AniListAnime> =
        graphqlRequest(AniListQueries.searchAnimeWithAdult, mapOf("search" to query, "page" to page, "perPage" to perPage)) { parseMediaList(it) }

    suspend fun getAnimeDetails(id: Int): AniListAnime = withContext(Dispatchers.IO) {
        val jsonBody = buildJsonObject {
            put("query", AniListQueries.animeDetails)
            putJsonObject("variables") { put("id", id) }
        }.toString().toRequestBody(jsonMime)

        val request = Request.Builder()
            .url(apiUrl)
            .post(jsonBody)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("AniList API error: ${response.code}")
            val responseBody = response.body?.string() ?: throw RuntimeException("Empty response")
            val root = json.parseToJsonElement(responseBody).jsonObject
            val media = root["data"]?.jsonObject?.get("Media")?.jsonObject
                ?: throw RuntimeException("Media not found in response")
            json.decodeFromJsonElement(AniListAnime.serializer(), media)
        }
    }
}
