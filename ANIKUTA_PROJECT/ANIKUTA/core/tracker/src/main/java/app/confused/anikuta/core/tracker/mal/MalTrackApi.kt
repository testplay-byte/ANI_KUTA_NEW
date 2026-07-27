package app.confused.anikuta.core.tracker.mal

import android.util.Log
import app.confused.anikuta.core.tracker.TrackAnimeEntry
import app.confused.anikuta.core.tracker.TrackStatus
import app.confused.anikuta.core.tracker.TrackerUserStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * MAL REST API for tracker operations.
 *
 * Uses PKCE OAuth (authorization-code grant with code_verifier).
 * Access tokens expire in 1 hour; refresh tokens last 31 days.
 * All calls run on Dispatchers.IO.
 */
class MalTrackApi(
    private val client: OkHttpClient = defaultClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Exchange the auth code for access + refresh tokens (PKCE). */
    suspend fun exchangeCodeForToken(
        authCode: String,
        codeVerifier: String,
        clientId: String,
    ): MalOAuth? = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("code", authCode)
            .add("code_verifier", codeVerifier)
            .add("grant_type", "authorization_code")
            .build()
        val request = Request.Builder()
            .url("$OAUTH_BASE_URL/token")
            .post(formBody)
            .build()
        parseOAuthResponse(request)
    }

    /** Refresh an expired access token using the refresh token. */
    suspend fun refreshToken(
        refreshToken: String,
        clientId: String,
    ): MalOAuth? = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build()
        val request = Request.Builder()
            .url("$OAUTH_BASE_URL/token")
            .post(formBody)
            .build()
        parseOAuthResponse(request)
    }

    /** Fetch the current user's name + ID. */
    suspend fun fetchUser(accessToken: String): MalUser? = withContext(Dispatchers.IO) {
        val request = buildAuthRequest(accessToken, "$API_BASE_URL/users/@me?fields=id,name")
        val responseBody = executeRequest(request) ?: return@withContext null
        try {
            val obj = Json.parseToJsonElement(responseBody).jsonObject
            MalUser(
                id = obj["id"]?.jsonPrimitive?.intOrNull ?: 0,
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse MAL user", e)
            null
        }
    }

    /** Fetch the user's anime list with detailed fields. */
    suspend fun fetchUserAnimeList(accessToken: String): List<TrackAnimeEntry> = withContext(Dispatchers.IO) {
        val fields = "list_status{start_date,finish_date,num_episodes_watched,score,status,is_rewatching}," +
            "num_episodes,genres{name},main_picture{medium,large},media_type,start_season"
        val request = buildAuthRequest(accessToken, "$API_BASE_URL/users/@me/animelist?fields=$fields&limit=1000")
        val responseBody = executeRequest(request) ?: return@withContext emptyList()
        try {
            val root = Json.parseToJsonElement(responseBody).jsonObject
            val data = root["data"]?.jsonArray ?: return@withContext emptyList()
            data.mapNotNull { node -> parseListEntry(node.jsonObject) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse MAL anime list", e)
            emptyList()
        }
    }

    /** PUT /anime/{id}/my_list_status — updates progress + status. */
    suspend fun updateProgress(
        accessToken: String,
        animeId: Int,
        episodesWatched: Int,
        status: TrackStatus,
    ): Boolean = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("status", toApiStatus(status) ?: "watching")
            .add("num_watched_episodes", episodesWatched.toString())
            .build()
        val request = Request.Builder()
            .url("$API_BASE_URL/anime/$animeId/my_list_status")
            .put(formBody)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        val response = client.newCall(request).execute()
        response.isSuccessful
    }

    // ── Internals ──

    private suspend fun parseOAuthResponse(request: Request): MalOAuth? {
        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            if (!response.isSuccessful) {
                Log.e(TAG, "OAuth error ${response.code}: $responseBody")
                return null
            }
            val obj = Json.parseToJsonElement(responseBody).jsonObject
            MalOAuth(
                accessToken = obj["access_token"]?.jsonPrimitive?.contentOrNull ?: return null,
                refreshToken = obj["refresh_token"]?.jsonPrimitive?.contentOrNull ?: return null,
                tokenType = obj["token_type"]?.jsonPrimitive?.contentOrNull ?: "Bearer",
                expiresIn = obj["expires_in"]?.jsonPrimitive?.intOrNull ?: 3600,
                createdAt = System.currentTimeMillis() / 1000,
            )
        } catch (e: Exception) {
            Log.e(TAG, "OAuth request failed", e)
            null
        }
    }

    private fun buildAuthRequest(token: String, url: String): Request =
        Request.Builder().url(url).addHeader("Authorization", "Bearer $token").build()

    private suspend fun executeRequest(request: Request): String? {
        return try {
            val response = client.newCall(request).execute()
            response.body?.string()
        } catch (e: Exception) {
            Log.e(TAG, "Request failed: ${request.url}", e)
            null
        }
    }

    private fun parseListEntry(node: JsonObject): TrackAnimeEntry? {
        val nodeObj = node["node"]?.jsonObject ?: return null
        val remoteId = nodeObj["id"]?.jsonPrimitive?.intOrNull ?: return null
        val title = nodeObj["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
        val mainPicture = nodeObj["main_picture"]?.jsonObject
        val coverUrl = mainPicture?.get("large")?.jsonPrimitive?.contentOrNull
            ?: mainPicture?.get("medium")?.jsonPrimitive?.contentOrNull
        val format = nodeObj["media_type"]?.jsonPrimitive?.contentOrNull?.uppercase()
        val totalEpisodes = nodeObj["num_episodes"]?.jsonPrimitive?.intOrNull
        val genres = nodeObj["genres"]?.jsonArray?.mapNotNull {
            it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
        } ?: emptyList()

        val listStatus = node["list_status"]?.jsonObject ?: return TrackAnimeEntry(
            remoteId, title, coverUrl, TrackStatus.WATCHING, 0, totalEpisodes, null, format, null, genres,
        )
        val status = mapMalStatus(listStatus["status"]?.jsonPrimitive?.contentOrNull)
        val episodesWatched = listStatus["num_episodes_watched"]?.jsonPrimitive?.intOrNull ?: 0
        val score = listStatus["score"]?.jsonPrimitive?.intOrNull

        return TrackAnimeEntry(
            remoteId = remoteId,
            title = title,
            coverUrl = coverUrl,
            status = status,
            episodesWatched = episodesWatched,
            totalEpisodes = totalEpisodes,
            score = score,
            format = format,
            country = null, // MAL doesn't provide country in the list endpoint
            genres = genres,
        )
    }

    private fun toApiStatus(status: TrackStatus): String? = when (status) {
        TrackStatus.WATCHING -> "watching"
        TrackStatus.COMPLETED -> "completed"
        TrackStatus.ON_HOLD -> "on_hold"
        TrackStatus.DROPPED -> "dropped"
        TrackStatus.PLAN_TO_WATCH -> "plan_to_watch"
        TrackStatus.REPEATING -> "watching" // is_rewatching=true handled separately
    }

    private fun mapMalStatus(apiStatus: String?): TrackStatus = when (apiStatus) {
        "watching" -> TrackStatus.WATCHING
        "completed" -> TrackStatus.COMPLETED
        "on_hold" -> TrackStatus.ON_HOLD
        "dropped" -> TrackStatus.DROPPED
        "plan_to_watch" -> TrackStatus.PLAN_TO_WATCH
        else -> TrackStatus.WATCHING
    }

    /** Derive stats from the user's anime list (MAL has no dedicated stats endpoint). */
    fun deriveStatsFromList(list: List<TrackAnimeEntry>): TrackerUserStats {
        val totalAnime = list.size
        val totalEpisodes = list.sumOf { it.episodesWatched }
        val totalMinutes = totalEpisodes * 24 // rough estimate: 24 min/episode

        val formatDist = list.groupBy { it.format ?: "UNKNOWN" }.mapValues { it.value.size }
        val statusDist = list.groupBy { it.status }.mapValues { it.value.size }
        val genreDist = list.flatMap { it.genres }.groupingBy { it }.eachCount()
        val countryDist = list.mapNotNull { it.country }.groupingBy { it }.eachCount()
        val scoreDist = list.mapNotNull { it.score }.filter { it > 0 }.groupingBy { it }.eachCount()
        val scored = list.mapNotNull { it.score }.filter { it > 0 }
        val meanScore = if (scored.isNotEmpty()) scored.average() else 0.0

        return TrackerUserStats(
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

    companion object {
        private const val TAG = "AnikutaMalTrackApi"
        private const val OAUTH_BASE_URL = "https://myanimelist.net/v1/oauth2"
        private const val API_BASE_URL = "https://api.myanimelist.net/v2"

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
