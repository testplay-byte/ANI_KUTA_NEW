package app.confused.anikuta.core.episodemetadata.source.anikage

import android.util.Log
import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata
import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadataRequest
import app.confused.anikuta.core.episodemetadata.source.EpisodeMetadataField
import app.confused.anikuta.core.episodemetadata.source.EpisodeMetadataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Anikage.cc (TheTVDB-based) episode metadata source.
 *
 * The PRIMARY source for episode metadata — provides titles, descriptions,
 * thumbnails, and air dates. Not behind Cloudflare — works with OkHttp directly.
 *
 * Provides: TITLE, DESCRIPTION, THUMBNAIL, AIR_DATE
 * Does NOT provide: FILLER
 *
 * Ported from the old ANIKUTA's `fetchFromAnikage()` method.
 * Endpoint: https://anikage.cc/api/media/anime/{anilistId}/episodes
 */
class AnikageCcSource(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : EpisodeMetadataSource {

    override val id = "anikage"
    override val name = "Anikage.cc"
    override val providedFields = setOf(
        EpisodeMetadataField.TITLE,
        EpisodeMetadataField.DESCRIPTION,
        EpisodeMetadataField.THUMBNAIL,
        EpisodeMetadataField.AIR_DATE,
    )

    override fun supports(request: EpisodeMetadataRequest): Boolean = request.animeId > 0

    override suspend fun fetchAll(request: EpisodeMetadataRequest): Map<Int, EpisodeMetadata> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<Int, EpisodeMetadata>()
            try {
                val response = client.newCall(
                    Request.Builder()
                        .url("https://anikage.cc/api/media/anime/${request.animeId}/episodes")
                        .headers(
                            Headers.Builder()
                                .set("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                                .set("Accept", "application/json")
                                .build()
                        )
                        .build()
                ).execute()

                if (!response.isSuccessful) {
                    Log.w(TAG, "Anikage HTTP ${response.code} for anilistId=${request.animeId}")
                    return@withContext results
                }

                val body = response.body?.string() ?: return@withContext results
                val anikageResponse = json.decodeFromString<AnikageResponse>(body)

                anikageResponse.episodes.forEach { ep ->
                    val num = ep.number ?: return@forEach
                    results[num] = EpisodeMetadata(
                        animeId = request.animeId,
                        episodeNumber = num,
                        title = ep.title?.takeIf { it.isNotBlank() },
                        description = ep.description?.takeIf { it.isNotBlank() }?.let { stripHtml(it) },
                        thumbnailUrl = ep.image?.takeIf { it.isNotBlank() },
                        airDate = ep.airDate?.takeIf { it.isNotBlank() }?.let { parseDate(it) },
                    )
                }

                Log.d(TAG, "Anikage: ${results.size} episodes for anilistId=${request.animeId}")
            } catch (e: Exception) {
                Log.e(TAG, "Anikage fetch failed for anilistId=${request.animeId}", e)
            }
            results
        }

    private fun stripHtml(text: String): String {
        return text.replace(Regex("<[^>]+>"), "").trim()
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            java.time.Instant.parse("${dateStr}T00:00:00+00:00").toEpochMilli() / 1000
        } catch (e: Exception) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                (sdf.parse(dateStr)?.time ?: 0L) / 1000
            } catch (e2: Exception) { 0L }
        }
    }

    @Serializable
    private data class AnikageResponse(
        val episodes: List<AnikageEpisode> = emptyList(),
    )

    @Serializable
    private data class AnikageEpisode(
        val number: Int? = null,
        val title: String? = null,
        val description: String? = null,
        val image: String? = null,
        val airDate: String? = null,
    )

    companion object {
        private const val TAG = "AnikageMetadataSource"
    }
}
