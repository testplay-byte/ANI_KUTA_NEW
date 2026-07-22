package app.confused.anikuta.core.episodemetadata.source.jikan

import android.util.Log
import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata
import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadataRequest
import app.confused.anikuta.core.episodemetadata.source.EpisodeMetadataField
import app.confused.anikuta.core.episodemetadata.source.EpisodeMetadataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Jikan (MyAnimeList) episode metadata source.
 *
 * Fetches episode titles + air dates from the Jikan v4 API (free, no auth).
 * Jikan provides per-episode data for MAL-linked anime.
 *
 * Provides: TITLE, AIR_DATE
 * Does NOT provide: DESCRIPTION, THUMBNAIL, FILLER
 *
 * Ported from the old ANIKUTA's `fetchFromJikan()` method.
 * Includes courtesy delay (500ms) + rate limit handling (429 → skip).
 */
class JikanMalSource(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : EpisodeMetadataSource {

    override val id = "jikan"
    override val name = "Jikan (MAL)"
    override val providedFields = setOf(EpisodeMetadataField.TITLE, EpisodeMetadataField.AIR_DATE)

    override fun supports(request: EpisodeMetadataRequest): Boolean = request.malId != null && request.malId > 0

    override suspend fun fetchAll(request: EpisodeMetadataRequest): Map<Int, EpisodeMetadata> =
        withContext(Dispatchers.IO) {
            val malId = request.malId ?: return@withContext emptyMap()
            delay(500) // courtesy delay for rate limiting

            val results = mutableMapOf<Int, EpisodeMetadata>()
            try {
                // Jikan v4: paginated — fetch page 1 first, then page 2+ if needed
                var page = 1
                var hasNext = true
                while (hasNext && page <= 10) { // max 10 pages = ~200 episodes
                    val response = client.newCall(
                        Request.Builder()
                            .url("$JIKAN_BASE/anime/$malId/episodes?page=$page")
                            .header("Accept", "application/json")
                            .build()
                    ).execute()

                    if (!response.isSuccessful) {
                        Log.w(TAG, "Jikan HTTP ${response.code} for malId=$malId page=$page")
                        if (response.code == 429) {
                            Log.w(TAG, "Jikan rate limited — stopping")
                            break
                        }
                        break
                    }

                    val body = response.body?.string() ?: break
                    val jikanResponse = json.decodeFromString<JikanEpisodesResponse>(body)

                    jikanResponse.data.forEach { ep ->
                        val epNum = ep.malId ?: return@forEach
                        results[epNum] = EpisodeMetadata(
                            animeId = request.animeId,
                            episodeNumber = epNum,
                            title = ep.title?.takeIf { it.isNotBlank() },
                            airDate = ep.aired?.let { parseDate(it) },
                        )
                    }

                    // Check pagination
                    hasNext = jikanResponse.pagination?.hasNextPage == true
                    if (hasNext) {
                        page++
                        delay(400) // courtesy delay between pages
                    }
                }

                Log.d(TAG, "Jikan: ${results.size} episodes for malId=$malId")
            } catch (e: Exception) {
                Log.e(TAG, "Jikan fetch failed for malId=$malId", e)
            }
            results
        }

    private fun parseDate(isoDate: String): Long {
        return try {
            java.time.Instant.parse("${isoDate}T00:00:00+00:00").toEpochMilli() / 1000
        } catch (e: Exception) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                (sdf.parse(isoDate)?.time ?: 0L) / 1000
            } catch (e2: Exception) { 0L }
        }
    }

    @Serializable
    private data class JikanEpisodesResponse(
        val data: List<JikanEpisode> = emptyList(),
        val pagination: JikanPagination? = null,
    )

    @Serializable
    private data class JikanEpisode(
        val malId: Int? = null,
        val title: String? = null,
        val aired: String? = null,
    )

    @Serializable
    private data class JikanPagination(
        val hasNextPage: Boolean? = null,
    )

    companion object {
        private const val TAG = "JikanMetadataSource"
        private const val JIKAN_BASE = "https://api.jikan.moe/v4"
    }
}
