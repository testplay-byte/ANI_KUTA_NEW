package app.confused.anikuta.core.tracker.anilist

import android.util.Log
import app.confused.anikuta.core.tracker.TrackAnimeEntry
import app.confused.anikuta.core.tracker.TrackStatus
import app.confused.anikuta.core.tracker.TrackerUserStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
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
 * Authenticated AniList GraphQL API for tracker operations.
 *
 * Uses the implicit-grant access token (ADR-013: auth enhances).
 * All calls run on Dispatchers.IO.
 *
 * Rate limit: AniList allows 90 req/min. This client doesn't enforce rate
 * limiting yet — the [TrackSyncManager] debounces to avoid spamming.
 */
class AniListTrackApi(
    private val client: OkHttpClient = defaultClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Fetch the current user's ID, username, avatar, and score format. */
    suspend fun fetchViewer(token: String): AniListViewer? = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("query", VIEWER_QUERY)
            put("variables", buildJsonObject { })
        }
        val root = executeGraphQL(token, body) ?: return@withContext null
        val viewer = root["data"]?.jsonObject?.get("Viewer")?.jsonObject ?: return@withContext null
        try {
            val id = viewer["id"]?.jsonPrimitive?.intOrNull ?: return@withContext null
            val name = viewer["name"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
            val avatar = viewer["avatar"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull
            val banner = viewer["bannerImage"]?.jsonPrimitive?.contentOrNull
            val scoreFormat = viewer["mediaListOptions"]?.jsonObject
                ?.get("scoreFormat")?.jsonPrimitive?.contentOrNull ?: "POINT_100"
            AniListViewer(id, name, avatar, banner, scoreFormat)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Viewer", e)
            null
        }
    }

    /** Fetch the user's entire anime list (all statuses) via MediaListCollection. */
    suspend fun fetchUserAnimeList(token: String, userId: Int): List<TrackAnimeEntry> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("query", MEDIA_LIST_COLLECTION_QUERY)
            put("variables", buildJsonObject {
                put("userId", userId)
            })
        }
        val root = executeGraphQL(token, body) ?: return@withContext emptyList()
        val lists = root["data"]?.jsonObject?.get("MediaListCollection")?.jsonObject
            ?.get("lists")?.jsonArray ?: return@withContext emptyList()

        val result = mutableListOf<TrackAnimeEntry>()
        for (list in lists) {
            val entries = list.jsonObject["entries"]?.jsonArray ?: continue
            for (entry in entries) {
                val parsed = parseListEntry(entry.jsonObject) ?: continue
                result.add(parsed)
            }
        }
        result
    }

    /** Fetch built-in AniList anime statistics for the viewer. */
    suspend fun fetchUserStats(token: String, userId: Int): TrackerUserStats? = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("query", STATS_QUERY)
            put("variables", buildJsonObject {
                put("userId", userId)
            })
        }
        val root = executeGraphQL(token, body) ?: return@withContext null
        val stats = root["data"]?.jsonObject?.get("User")?.jsonObject
            ?.get("statistics")?.jsonObject?.get("anime")?.jsonObject ?: return@withContext null

        val totalAnime = stats["count"]?.jsonPrimitive?.intOrNull ?: 0
        val totalEpisodes = stats["episodesWatched"]?.jsonPrimitive?.intOrNull ?: 0
        val totalMinutes = stats["minutesWatched"]?.jsonPrimitive?.intOrNull ?: 0
        val meanScore = stats["meanScore"]?.jsonPrimitive?.doubleOrNull ?: 0.0

        val formatDist = mutableMapOf<String, Int>()
        stats["formats"]?.jsonArray?.forEach { fmt ->
            val f = fmt.jsonObject
            val format = f["format"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN"
            formatDist[format] = f["count"]?.jsonPrimitive?.intOrNull ?: 0
        }

        val statusDist = mutableMapOf<TrackStatus, Int>()
        stats["statuses"]?.jsonArray?.forEach { st ->
            val s = st.jsonObject
            val status = mapAniListStatus(s["status"]?.jsonPrimitive?.contentOrNull)
            statusDist[status] = s["count"]?.jsonPrimitive?.intOrNull ?: 0
        }

        val genreDist = mutableMapOf<String, Int>()
        stats["genres"]?.jsonArray?.forEach { g ->
            val gn = g.jsonObject
            genreDist[gn["genre"]?.jsonPrimitive?.contentOrNull ?: "Unknown"] = gn["count"]?.jsonPrimitive?.intOrNull ?: 0
        }

        val countryDist = mutableMapOf<String, Int>()
        stats["countries"]?.jsonArray?.forEach { c ->
            val cn = c.jsonObject
            countryDist[cn["country"]?.jsonPrimitive?.contentOrNull ?: "Unknown"] = cn["count"]?.jsonPrimitive?.intOrNull ?: 0
        }

        val scoreDist = mutableMapOf<Int, Int>()
        stats["scoreDistribution"]?.jsonArray?.forEach { sc ->
            val s = sc.jsonObject
            scoreDist[s["score"]?.jsonPrimitive?.intOrNull ?: 0] = s["count"]?.jsonPrimitive?.intOrNull ?: 0
        }

        TrackerUserStats(
            totalAnime = totalAnime,
            totalEpisodes = totalEpisodes,
            totalMinutesWatched = totalMinutes,
            meanScore = meanScore,
            formatDistribution = formatDist,
            statusDistribution = statusDist,
            genreDistribution = genreDist,
            countryDistribution = countryDist,
            scoreDistribution = scoreDist,
        )
    }

    /** Update or create a list entry (SaveMediaListEntry mutation). */
    suspend fun updateProgress(
        token: String,
        mediaId: Int,
        progress: Int,
        status: TrackStatus,
    ): Boolean = withContext(Dispatchers.IO) {
        val apiStatus = toApiStatus(status)
        val body = buildJsonObject {
            put("query", SAVE_ENTRY_MUTATION)
            put("variables", buildJsonObject {
                put("mediaId", mediaId)
                put("progress", progress)
                put("status", apiStatus)
            })
        }
        val root = executeGraphQL(token, body) ?: return@withContext false
        root["data"]?.jsonObject?.containsKey("SaveMediaListEntry") == true
    }

    // ── Internals ──

    private suspend fun executeGraphQL(token: String, body: JsonObject): JsonObject? {
        val request = Request.Builder()
            .url(API_URL)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            if (!response.isSuccessful) {
                Log.e(TAG, "GraphQL error ${response.code}: $responseBody")
                return null
            }
            Json.parseToJsonElement(responseBody).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "GraphQL request failed", e)
            null
        }
    }

    private fun parseListEntry(entry: JsonObject): TrackAnimeEntry? {
        val media = entry["media"]?.jsonObject ?: return null
        val remoteId = media["id"]?.jsonPrimitive?.intOrNull ?: return null
        val title = media["title"]?.jsonObject?.get("userPreferred")?.jsonPrimitive?.contentOrNull
            ?: media["title"]?.jsonObject?.get("romaji")?.jsonPrimitive?.contentOrNull
            ?: "Unknown"
        val coverUrl = media["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull
        val format = media["format"]?.jsonPrimitive?.contentOrNull
        val country = media["countryOfOrigin"]?.jsonPrimitive?.contentOrNull
        val totalEpisodes = media["episodes"]?.jsonPrimitive?.intOrNull
        val genres = media["genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val status = mapAniListStatus(entry["status"]?.jsonPrimitive?.contentOrNull)
        val episodesWatched = entry["progress"]?.jsonPrimitive?.intOrNull ?: 0
        val score = entry["scoreRaw"]?.jsonPrimitive?.intOrNull ?: entry["score"]?.jsonPrimitive?.intOrNull
        return TrackAnimeEntry(remoteId, title, coverUrl, status, episodesWatched, totalEpisodes, score, format, country, genres)
    }

    private fun toApiStatus(status: TrackStatus): String = when (status) {
        TrackStatus.WATCHING -> "CURRENT"
        TrackStatus.COMPLETED -> "COMPLETED"
        TrackStatus.ON_HOLD -> "PAUSED"
        TrackStatus.DROPPED -> "DROPPED"
        TrackStatus.PLAN_TO_WATCH -> "PLANNING"
        TrackStatus.REPEATING -> "REPEATING"
    }

    private fun mapAniListStatus(apiStatus: String?): TrackStatus = when (apiStatus) {
        "CURRENT" -> TrackStatus.WATCHING
        "COMPLETED" -> TrackStatus.COMPLETED
        "PAUSED" -> TrackStatus.ON_HOLD
        "DROPPED" -> TrackStatus.DROPPED
        "PLANNING" -> TrackStatus.PLAN_TO_WATCH
        "REPEATING" -> TrackStatus.REPEATING
        else -> TrackStatus.WATCHING
    }

    companion object {
        private const val TAG = "AnikutaAniListTrack"
        private const val API_URL = "https://graphql.anilist.co"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        private const val VIEWER_QUERY = """
            query {
              Viewer {
                id
                name
                avatar { large }
                bannerImage
                mediaListOptions { scoreFormat }
              }
            }
        """

        private const val MEDIA_LIST_COLLECTION_QUERY = """
            query (${'$'}userId: Int!) {
              MediaListCollection(userId: ${'$'}userId, type: ANIME) {
                lists {
                  entries {
                    id
                    status
                    progress
                    score(format: POINT_100)
                    media {
                      id
                      title { userPreferred romaji }
                      coverImage { large }
                      format
                      countryOfOrigin
                      episodes
                      genres
                    }
                  }
                }
              }
            }
        """

        private const val STATS_QUERY = """
            query (${'$'}userId: Int!) {
              User(id: ${'$'}userId) {
                statistics {
                  anime {
                    count
                    episodesWatched
                    minutesWatched
                    meanScore
                    formats { format count }
                    statuses { status count }
                    genres { genre count }
                    countries { country count }
                    scoreDistribution { score count }
                  }
                }
              }
            }
        """

        private const val SAVE_ENTRY_MUTATION = """
            mutation (${'$'}mediaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress, status: ${'$'}status) {
                id
                status
                progress
              }
            }
        """
    }
}
