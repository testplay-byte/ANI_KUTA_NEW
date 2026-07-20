package com.testplaybyte.animeapp.data

import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import com.testplaybyte.animeapp.model.*

/**
 * AniListClient — sends GraphQL queries to the AniList API.
 * Uses Ktor (Android engine) + kotlinx.serialization for JSON.
 */
class AniListClient {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private suspend fun gql(query: String, variables: Map<String, JsonElement> = emptyMap()): JsonObject {
        val response = client.post("https://graphql.anilist.co") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("query", JsonPrimitive(query))
                put("variables", buildJsonObject {
                    variables.forEach { (k, v) -> put(k, v) }
                })
            })
        }
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    private fun parseMedia(media: JsonElement): Anime {
        val m = media.jsonObject
        return Anime(
            id = m["id"]!!.jsonPrimitive.int,
            title = AnimeTitle(
                romaji = m["title"]?.jsonObject?.get("romaji")?.jsonPrimitive?.contentOrNull,
                english = m["title"]?.jsonObject?.get("english")?.jsonPrimitive?.contentOrNull,
            ),
            coverImage = CoverImage(
                large = m["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull,
                extraLarge = m["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.contentOrNull,
            ),
            bannerImage = m["bannerImage"]?.jsonPrimitive?.contentOrNull,
            averageScore = m["averageScore"]?.jsonPrimitive?.intOrNull,
            episodes = m["episodes"]?.jsonPrimitive?.intOrNull,
            format = m["format"]?.jsonPrimitive?.contentOrNull,
            season = m["season"]?.jsonPrimitive?.contentOrNull,
            seasonYear = m["seasonYear"]?.jsonPrimitive?.intOrNull,
            genres = m["genres"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            status = m["status"]?.jsonPrimitive?.contentOrNull,
            description = m["description"]?.jsonPrimitive?.contentOrNull,
            nextAiringEpisode = m["nextAiringEpisode"]?.jsonObject?.let {
                NextAiringEpisode(
                    airingAt = it["airingAt"]?.jsonPrimitive?.longOrNull,
                    episode = it["episode"]?.jsonPrimitive?.intOrNull,
                )
            },
            siteUrl = m["siteUrl"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private val mediaFields = "id title{romaji english} coverImage{large extraLarge} averageScore episodes format season seasonYear genres status"

    suspend fun fetchTrending(): List<Anime> {
        val q = "query{Page(page:1,perPage:5){media(type:ANIME,sort:TRENDING_DESC){id title{romaji english} coverImage{large extraLarge} bannerImage averageScore}}}"
        val data = gql(q)["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray
        return data?.map { parseMedia(it) } ?: emptyList()
    }

    suspend fun fetchSeasonal(season: String, year: Int): List<Anime> {
        val q = "query(\$season:MediaSeason,\$year:Int){Page(page:1,perPage:12){media(type:ANIME,season:\$season,seasonYear:\$year,sort:POPULARITY_DESC){$mediaFields}}}"
        val data = gql(q, mapOf(
            "season" to JsonPrimitive(season),
            "year" to JsonPrimitive(year),
        ))["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray
        return data?.map { parseMedia(it) } ?: emptyList()
    }

    suspend fun fetchTopRated(): List<Anime> {
        val q = "query{Page(page:1,perPage:9){media(type:ANIME,sort:SCORE_DESC){$mediaFields}}}"
        val data = gql(q)["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray
        return data?.map { parseMedia(it) } ?: emptyList()
    }

    /**
     * Fetch popular anime (30 results, sorted by popularity).
     * Used as the default view when the user opens Search with no query
     * and the source is "anilist".
     */
    suspend fun fetchPopular(): List<Anime> {
        val q = "query{Page(page:1,perPage:30){media(type:ANIME,sort:POPULARITY_DESC){$mediaFields}}}"
        val data = gql(q)["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray
        return data?.map { parseMedia(it) } ?: emptyList()
    }

    /**
     * Fetch trending anime (30 results, sorted by trending).
     * Used as the default view when the source is "extension".
     */
    suspend fun fetchTrendingFull(): List<Anime> {
        val q = "query{Page(page:1,perPage:30){media(type:ANIME,sort:TRENDING_DESC){$mediaFields}}}"
        val data = gql(q)["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray
        return data?.map { parseMedia(it) } ?: emptyList()
    }

    suspend fun search(query: String): List<Anime> {
        val q = "query(\$search:String){Page(page:1,perPage:30){media(type:ANIME,search:\$search,sort:POPULARITY_DESC){$mediaFields}}}"
        val data = gql(q, mapOf("search" to JsonPrimitive(query)))["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray
        return data?.map { parseMedia(it) } ?: emptyList()
    }

    suspend fun fetchDetail(id: Int): Anime? {
        val q = "query(\$id:Int){Media(id:\$id,type:ANIME){id title{romaji english} coverImage{large extraLarge} bannerImage averageScore episodes format season seasonYear genres status description nextAiringEpisode{airingAt episode} siteUrl}}"
        val data = gql(q, mapOf("id" to JsonPrimitive(id)))["data"]?.jsonObject?.get("Media")
        return data?.let { parseMedia(it) }
    }

    suspend fun fetchAiringSchedule(weekStart: Long, weekEnd: Long): List<AiringSchedule> {
        val q = "query(\$s:Int!,\$e:Int!){Page(page:1,perPage:100){airingSchedules(airingAt_greater:\$s,airingAt_lesser:\$e){id airingAt episode media{id title{romaji english} coverImage{large extraLarge} averageScore episodes format seasonYear}}}}"
        val data = gql(q, mapOf(
            "s" to JsonPrimitive(weekStart),
            "e" to JsonPrimitive(weekEnd),
        ))["data"]?.jsonObject?.get("Page")?.jsonObject?.get("airingSchedules")?.jsonArray
        return data?.map {
            val obj = it.jsonObject
            AiringSchedule(
                id = obj["id"]!!.jsonPrimitive.int,
                airingAt = obj["airingAt"]!!.jsonPrimitive.long,
                episode = obj["episode"]!!.jsonPrimitive.int,
                media = parseMedia(obj["media"]!!),
            )
        }?.sortedBy { it.airingAt } ?: emptyList()
    }
}

// Extension to get contentOrNull from JsonPrimitive (checks for JsonNull)
val JsonPrimitive.contentOrNull: String? get() = if (this is JsonNull) null else content
