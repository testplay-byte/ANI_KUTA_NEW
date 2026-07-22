package app.confused.anikuta.core.episodemetadata.source.anilist

import android.util.Log
import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata
import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadataRequest
import app.confused.anikuta.core.episodemetadata.source.EpisodeMetadataField
import app.confused.anikuta.core.episodemetadata.source.EpisodeMetadataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * AniList streaming episodes source.
 *
 * Fetches episode thumbnails + titles from AniList's `streamingEpisodes` field
 * via a GraphQL query. This is the same data the old project used — AniList
 * provides streaming episode info for some anime (usually those with official
 * streaming partners).
 *
 * Provides: TITLE, THUMBNAIL
 * Does NOT provide: DESCRIPTION, AIR_DATE, FILLER
 *
 * Ported from the old ANIKUTA's `fetchFromAniList()` method.
 */
class AniListStreamingSource(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : EpisodeMetadataSource {

    override val id = "anilist"
    override val name = "AniList Streaming"
    override val providedFields = setOf(EpisodeMetadataField.TITLE, EpisodeMetadataField.THUMBNAIL)

    override fun supports(request: EpisodeMetadataRequest): Boolean = request.animeId > 0

    override suspend fun fetchAll(request: EpisodeMetadataRequest): Map<Int, EpisodeMetadata> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<Int, EpisodeMetadata>()
            try {
                val query = """{"query":"query { Media(id: ${request.animeId}, type: ANIME) { streamingEpisodes { title thumbnail } } }"}"""
                val requestBody = query.toRequestBody("application/json".toMediaTypeOrNull())
                val response = client.newCall(
                    Request.Builder()
                        .url("https://graphql.anilist.co")
                        .post(requestBody)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .build()
                ).execute()

                if (!response.isSuccessful) {
                    Log.w(TAG, "AniList streaming HTTP ${response.code}")
                    return@withContext results
                }

                val body = response.body?.string() ?: return@withContext results
                val root = json.parseToJsonElement(body).jsonObject
                val media = root["data"]?.jsonObject?.get("Media")?.jsonObject ?: return@withContext results
                val episodes = media["streamingEpisodes"]?.jsonArray ?: return@withContext results

                episodes.forEachIndexed { idx, ep ->
                    val epNum = idx + 1
                    val epObj = ep.jsonObject
                    results[epNum] = EpisodeMetadata(
                        animeId = request.animeId,
                        episodeNumber = epNum,
                        title = epObj["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                        thumbnailUrl = epObj["thumbnail"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                    )
                }

                Log.d(TAG, "AniList streaming: ${results.size} episodes for animeId=${request.animeId}")
            } catch (e: Exception) {
                Log.e(TAG, "AniList streaming fetch failed", e)
            }
            results
        }

    companion object {
        private const val TAG = "AniListMetadataSource"
    }
}
