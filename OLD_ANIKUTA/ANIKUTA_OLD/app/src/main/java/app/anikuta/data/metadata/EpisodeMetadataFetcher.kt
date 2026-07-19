package app.anikuta.data.metadata

import android.util.Log
import app.anikuta.data.anilist.model.AniListAnime
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 7.5 — In-app episode metadata fetcher.
 *
 * Fetches episode thumbnails, titles, descriptions, and air dates from
 * multiple sources in parallel, for extensions that don't provide this data.
 *
 * Sources (priority order):
 * 1. AniList streaming episodes (thumbnails + titles) — already in anime object
 * 2. Jikan/MAL API (titles + air dates) — free, no auth, has rate limiting
 * 3. AniList banner image — fallback thumbnail (same for all episodes)
 *
 * Merge priority:
 * - Title: Jikan → AniList streaming
 * - Thumbnail: AniList streaming → AniList banner (fallback)
 * - Description: Jikan (rarely available) → null
 * - Air date: Jikan
 */
class EpisodeMetadataFetcher(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    companion object {
        private const val TAG = "EpisodeMetadata"
        private const val JIKAN_BASE = "https://api.jikan.moe/v4"
        private const val JIKAN_MAX_RETRIES = 5
        private const val JIKAN_RETRY_DELAY_MS = 3000L
    }

    data class EpisodeMetadata(
        val title: String?,
        val description: String?,
        val thumbnailUrl: String?,
        val airDate: Long?,
    )

    /**
     * Fetch episode metadata for an anime.
     *
     * @param anime The AniList anime (uses idMal for Jikan, streamingEpisodes + bannerImage for AniList)
     * @param anilistId The AniList anime ID (used to look up idMal if not in the anime object)
     * @param episodeCount Number of episodes to fetch metadata for
     * @return Map<episodeNumber (1-based), EpisodeMetadata>
     */
    suspend fun fetch(anime: AniListAnime, anilistId: Int, episodeCount: Int): Map<Int, EpisodeMetadata> {
        val results = mutableMapOf<Int, EpisodeMetadata>()

        // Fallback thumbnail: use the anime's banner or cover image
        val fallbackThumbnail = anime.bannerImage ?: anime.coverImage.best()

        // If idMal is missing from the cached anime object, look it up from AniList
        val malId = anime.idMal ?: lookupMalId(anilistId)
        if (malId == null) {
            Log.w(TAG, "Could not determine MAL ID — skipping Jikan + Kitsu fetch")
        }

        coroutineScope {
            // Source 1: Anikage.cc (TheTVDB) — PRIMARY: titles + descriptions + thumbnails + air dates
            // Not behind Cloudflare — works with OkHttp directly (verified).
            val anikageResults = async {
                try {
                    fetchFromAnikage(anilistId)
                } catch (e: Exception) {
                    Log.w(TAG, "Anikage fetch failed", e)
                    emptyMap()
                }
            }

            // Source 2: AniList streaming episodes (already in the anime object)
            val anilistResults = async {
                try {
                    fetchFromAniList(anime)
                } catch (e: Exception) {
                    Log.w(TAG, "AniList streaming episodes fetch failed", e)
                    emptyMap()
                }
            }

            // Source 3: Jikan/MAL API (titles + air dates) — single attempt, no retries
            val jikanResults = async {
                try {
                    malId?.let { fetchFromJikanWithRetry(it) } ?: run {
                        Log.w(TAG, "No malId available — skipping Jikan fetch")
                        emptyMap()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Jikan fetch failed", e)
                    emptyMap()
                }
            }

            // Source 4: Kitsu (thumbnails + descriptions + titles) — needs MAL→Kitsu ID mapping
            val kitsuResults = async {
                try {
                    malId?.let { fetchFromKitsu(it) } ?: run {
                        Log.w(TAG, "No malId available — skipping Kitsu fetch")
                        emptyMap()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Kitsu fetch failed", e)
                    emptyMap()
                }
            }

            val anikage = anikageResults.await()
            val anilist = anilistResults.await()
            val jikan = jikanResults.await()
            val kitsu = kitsuResults.await()

            Log.i(TAG, "Sources: Anikage=${anikage.size}, AniList=${anilist.size}, Jikan=${jikan.size}, Kitsu=${kitsu.size}, malId=$malId")

            // Merge priority (matching AniKoto extension):
            // Title:       Jikan → Anikage → Kitsu → AniList
            // Description: Anikage → Kitsu
            // Thumbnail:   Anikage → AniList → Kitsu → banner fallback
            // Air date:    Jikan → Anikage → Kitsu
            val hasAnyRealData = anikage.isNotEmpty() || jikan.isNotEmpty() || anilist.isNotEmpty() || kitsu.isNotEmpty()
            for (i in 1..episodeCount) {
                val ag = anikage[i]
                val a = anilist[i]
                val j = jikan[i]
                val k = kitsu[i]
                val thumb = ag?.thumbnailUrl ?: a?.thumbnailUrl ?: k?.thumbnailUrl ?: if (hasAnyRealData) fallbackThumbnail else null
                results[i] = EpisodeMetadata(
                    title = j?.title ?: ag?.title ?: k?.title ?: a?.title,
                    description = ag?.description ?: k?.description,
                    thumbnailUrl = thumb,
                    airDate = j?.airDate ?: ag?.airDate ?: k?.airDate,
                )
            }
        }

        return results
    }

    /**
     * Fetch from Anikage.cc (TheTVDB-based) — PRIMARY source.
     * Provides: titles, descriptions, thumbnails, air dates.
     * Not behind Cloudflare — works with OkHttp directly.
     * Endpoint: https://anikage.cc/api/media/anime/{anilistId}/episodes
     */
    private suspend fun fetchFromAnikage(anilistId: Int): Map<Int, EpisodeMetadata> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<Int, EpisodeMetadata>()
            try {
                val response = networkHelper.client
                    .newCall(
                        GET(
                            "https://anikage.cc/api/media/anime/$anilistId/episodes",
                            headers = Headers.Builder()
                                .set("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                                .set("Accept", "application/json")
                                .build(),
                        )
                    )
                    .execute()

                if (!response.isSuccessful) {
                    Log.w(TAG, "Anikage HTTP ${response.code} for anilistId=$anilistId")
                    return@withContext results
                }

                val body = response.body?.string() ?: return@withContext results
                val anikageResponse = json.decodeFromString<AnikageResponse>(body)

                anikageResponse.episodes.forEach { ep ->
                    val num = ep.number ?: return@forEach
                    results[num] = EpisodeMetadata(
                        title = ep.title?.takeIf { it.isNotBlank() },
                        description = ep.description?.takeIf { it.isNotBlank() }?.let { stripHtml(it) },
                        thumbnailUrl = ep.image?.takeIf { it.isNotBlank() },
                        airDate = ep.airDate?.takeIf { it.isNotBlank() }?.let { parseDate("${it}T00:00:00+00:00") },
                    )
                }

                Log.d(TAG, "Anikage returned ${results.size} episodes for anilistId=$anilistId")
            } catch (e: Exception) {
                Log.e(TAG, "Anikage fetch failed for anilistId=$anilistId", e)
            }
            results
        }

    /**
     * Strip HTML tags from a string.
     */
    private fun stripHtml(text: String): String {
        return text.replace(Regex("<[^>]+>"), "").trim()
    }

    /**
     * Look up the MAL ID for an anime from AniList GraphQL API.
     * Used when the cached anime object doesn't have idMal.
     */
    private suspend fun lookupMalId(anilistId: Int): Int? =
        withContext(Dispatchers.IO) {
            try {
                val query = """
                    query {
                      Media(id: $anilistId, type: ANIME) {
                        idMal
                      }
                    }
                """.trimIndent()
                val requestBody = """{"query":"query { Media(id: $anilistId, type: ANIME) { idMal } }"}"""
                    .toRequestBody("application/json".toMediaTypeOrNull())
                val response = networkHelper.client
                    .newCall(
                        okhttp3.Request.Builder()
                            .url("https://graphql.anilist.co")
                            .post(requestBody)
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .build()
                    )
                    .execute()

                if (!response.isSuccessful) {
                    Log.w(TAG, "AniList idMal lookup failed: HTTP ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val root = json.parseToJsonElement(body).jsonObject
                val media = root["data"]?.jsonObject?.get("Media")?.jsonObject
                val idMal = media?.get("idMal")?.toString()?.toIntOrNull()
                Log.d(TAG, "AniList idMal lookup for anilistId=$anilistId → $idMal")
                idMal
            } catch (e: Exception) {
                Log.e(TAG, "Failed to look up idMal for anilistId=$anilistId", e)
                null
            }
        }

    /**
     * Fetch from AniList streaming episodes (already in the anime object).
     */
    private fun fetchFromAniList(anime: AniListAnime): Map<Int, EpisodeMetadata> {
        val results = mutableMapOf<Int, EpisodeMetadata>()
        anime.streamingEpisodes?.forEachIndexed { idx, ep ->
            val epNum = idx + 1
            results[epNum] = EpisodeMetadata(
                title = ep.title,
                description = null,
                thumbnailUrl = ep.thumbnail,
                airDate = null,
            )
        }
        if (results.isNotEmpty()) {
            Log.d(TAG, "AniList streaming: ${results.size} episodes")
        }
        return results
    }

    /**
     * Fetch from Jikan (MAL API) — single attempt, no retries.
     */
    private suspend fun fetchFromJikanWithRetry(malId: Int): Map<Int, EpisodeMetadata> =
        withContext(Dispatchers.IO) {
            delay(500) // courtesy delay
            try {
                fetchFromJikan(malId)
            } catch (e: Exception) {
                Log.w(TAG, "Jikan fetch failed: ${e.message}")
                emptyMap()
            }
        }

    /**
     * Fetch from Jikan (MAL API).
     * Endpoint: https://api.jikan.moe/v4/anime/{malId}/episodes
     */
    private suspend fun fetchFromJikan(malId: Int): Map<Int, EpisodeMetadata> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<Int, EpisodeMetadata>()
            val response = networkHelper.client
                .newCall(GET("$JIKAN_BASE/anime/$malId/episodes"))
                .execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Jikan HTTP ${response.code} for malId=$malId")
                if (response.code == 429 || response.code == 504) {
                    throw Exception("Jikan rate limited: HTTP ${response.code}")
                }
                return@withContext results
            }

            val body = response.body?.string() ?: return@withContext results
            val jikanResponse = json.decodeFromString<JikanEpisodesResponse>(body)

            jikanResponse.data.forEach { ep ->
                val epNum = ep.malId ?: return@forEach
                results[epNum] = EpisodeMetadata(
                    title = ep.title,
                    description = null, // Jikan v4 episodes endpoint doesn't provide synopsis
                    thumbnailUrl = null,
                    airDate = ep.aired?.let { parseDate(it) },
                )
            }

            Log.d(TAG, "Jikan returned ${results.size} episodes for malId=$malId")
            results
        }

    /**
     * Fetch from Kitsu API.
     * Step 1: Look up Kitsu ID from MAL ID via mappings endpoint
     * Step 2: Fetch episodes from Kitsu anime endpoint
     *
     * Kitsu provides: titles, descriptions (synopses), thumbnails, and air dates.
     */
    private suspend fun fetchFromKitsu(malId: Int): Map<Int, EpisodeMetadata> =
        withContext(Dispatchers.IO) {
            delay(500) // Rate limit courtesy

            // Step 1: MAL → Kitsu ID mapping
            val kitsuHeaders = Headers.Builder()
                .set("Accept", "application/vnd.api+json")
                .build()
            val mappingResponse = networkHelper.client
                .newCall(
                    GET(
                        "https://kitsu.app/api/edge/mappings" +
                            "?filter%5BexternalSite%5D=myanimelist/anime" +
                            "&filter%5BexternalId%5D=$malId" +
                            "&include=item",
                        headers = kitsuHeaders,
                    )
                )
                .execute()

            if (!mappingResponse.isSuccessful) {
                Log.w(TAG, "Kitsu mapping HTTP ${mappingResponse.code} for malId=$malId")
                return@withContext emptyMap()
            }

            val mappingBody = mappingResponse.body?.string() ?: return@withContext emptyMap()
            val mappingJson = json.parseToJsonElement(mappingBody).jsonObject
            val included = mappingJson["included"]?.jsonArray
            val kitsuId = included?.firstOrNull()?.jsonObject?.get("id")?.toString()?.trim('"')
            if (kitsuId.isNullOrBlank()) {
                Log.d(TAG, "Kitsu: no mapping found for malId=$malId")
                return@withContext emptyMap()
            }

            Log.d(TAG, "Kitsu: malId=$malId → kitsuId=$kitsuId")

            // Step 2: Fetch episodes
            delay(500) // Rate limit courtesy
            val epsResponse = networkHelper.client
                .newCall(
                    GET(
                        "https://kitsu.app/api/edge/anime/$kitsuId/episodes?page%5Blimit%5D=20&sort=number",
                        headers = kitsuHeaders,
                    )
                )
                .execute()

            if (!epsResponse.isSuccessful) {
                Log.w(TAG, "Kitsu episodes HTTP ${epsResponse.code} for kitsuId=$kitsuId")
                return@withContext emptyMap()
            }

            val epsBody = epsResponse.body?.string() ?: return@withContext emptyMap()
            val results = mutableMapOf<Int, EpisodeMetadata>()

            try {
                val epsJson = json.parseToJsonElement(epsBody).jsonObject
                val dataArray = epsJson["data"]?.jsonArray ?: return@withContext emptyMap()

                dataArray.forEach { item ->
                    val attrs = item.jsonObject["attributes"]?.jsonObject ?: return@forEach
                    val number = attrs["number"]?.toString()?.toIntOrNull() ?: return@forEach
                    val title = attrs["canonicalTitle"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }
                    val synopsis = attrs["synopsis"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }
                    val thumbnail = attrs["thumbnail"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }
                    val airdate = attrs["airdate"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }

                    results[number] = EpisodeMetadata(
                        title = title,
                        description = synopsis,
                        thumbnailUrl = thumbnail,
                        airDate = airdate?.let { parseDate("${it}T00:00:00+00:00") },
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Kitsu episode parsing failed", e)
            }

            Log.d(TAG, "Kitsu returned ${results.size} episodes for kitsuId=$kitsuId")
            results
        }

    /**
     * Parse an ISO 8601 date string to epoch millis.
     */
    private fun parseDate(isoDate: String): Long {
        return try {
            java.time.Instant.parse(isoDate).toEpochMilli()
        } catch (e: Exception) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                sdf.parse(isoDate)?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }

    // Jikan API response models
    @Serializable
    private data class JikanEpisodesResponse(
        val data: List<JikanEpisode> = emptyList(),
    )

    @Serializable
    private data class JikanEpisode(
        val malId: Int? = null,
        val title: String? = null,
        val aired: String? = null,
    )

    // Anikage.cc (TheTVDB) response models
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
}
