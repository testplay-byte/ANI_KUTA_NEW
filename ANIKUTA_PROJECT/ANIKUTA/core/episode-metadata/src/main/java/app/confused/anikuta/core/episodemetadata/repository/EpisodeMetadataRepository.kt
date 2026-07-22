package app.confused.anikuta.core.episodemetadata.repository

import android.util.Log
import app.confused.anikuta.core.episodemetadata.EpisodeMetadataPreferences
import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata
import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadataRequest
import app.confused.anikuta.core.episodemetadata.source.EpisodeMetadataSourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Repository for episode metadata — the public API of the episode-metadata module.
 *
 * Fetches ALL episode metadata for an anime from all registered sources in
 * parallel, then merges the results per-field (first non-null wins, ordered
 * by source registration priority).
 *
 * Ported from the old ANIKUTA's `EpisodeMetadataFetcher` but adapted to the
 * new project's pluggable source architecture (ADR-022).
 *
 * Merge priority (matching the old project):
 * - Title:       Jikan → Anikage → Kitsu → AniList
 * - Description: Anikage → Kitsu
 * - Thumbnail:   Anikage → AniList → Kitsu → banner fallback
 * - Air date:    Jikan → Anikage → Kitsu
 *
 * Usage:
 * ```kotlin
 * val repository = EpisodeMetadataRepository(registry)
 * val metadata = repository.fetchAll(EpisodeMetadataRequest(
 *     animeId = 178789,
 *     animeTitle = "Mushoku Tensei",
 *     episodeNumber = 1,
 *     malId = 45889,
 *     bannerImage = "https://...",
 *     episodeCount = 12,
 * ))
 * // metadata = Map<episodeNumber, EpisodeMetadata>
 * ```
 */
class EpisodeMetadataRepository(
    private val registry: EpisodeMetadataSourceRegistry,
    private val preferences: EpisodeMetadataPreferences,
) {
    private val cache = mutableMapOf<Int, Map<Int, EpisodeMetadata>>()

    /**
     * Fetch ALL episode metadata from all registered sources in parallel,
     * then merge per-field.
     *
     * @param request The request containing anime ID, title, MAL ID, etc.
     * @return Map<episodeNumber (1-based), EpisodeMetadata>. Empty if no data.
     */
    suspend fun fetchAll(request: EpisodeMetadataRequest): Map<Int, EpisodeMetadata> =
        withContext(Dispatchers.IO) {
            // Check if metadata fetching is enabled
            if (!preferences.enabled().get()) {
                Log.d(TAG, "Metadata fetching disabled by user — skipping")
                return@withContext emptyMap()
            }

            // Check cache first
            cache[request.animeId]?.let { cached ->
                Log.d(TAG, "Cache hit for animeId=${request.animeId} (${cached.size} episodes)")
                return@withContext cached
            }

            val sources = registry.getSupported(request)
            if (sources.isEmpty()) {
                Log.w(TAG, "No metadata sources support this request")
                return@withContext emptyMap()
            }

            Log.i(TAG, "Fetching from ${sources.size} sources for animeId=${request.animeId}: ${sources.map { it.id }}")

            // Fetch from all sources in parallel
            val results = coroutineScope {
                sources.map { source ->
                    async {
                        try {
                            source.fetchAll(request)
                        } catch (e: Exception) {
                            Log.w(TAG, "Source '${source.id}' failed: ${e.message}")
                            emptyMap()
                        }
                    }
                }.awaitAll()
            }.toMutableList()

            // Read field-level preferences
            val fetchTitles = preferences.fetchTitles().get()
            val fetchSummaries = preferences.fetchSummaries().get()
            val fetchThumbnails = preferences.fetchThumbnails().get()
            val fetchAirDates = preferences.fetchAirDates().get()

            // Merge per-field (first non-null wins, in source registration order)
            // Fields the user disabled are set to null (not fetched)
            val episodeCount = request.episodeCount.coerceAtLeast(1)
            val merged = mutableMapOf<Int, EpisodeMetadata>()
            val fallbackThumb = if (fetchThumbnails) request.bannerImage else null

            for (epNum in 1..episodeCount) {
                var title: String? = null
                var description: String? = null
                var thumbnailUrl: String? = null
                var airDate: Long? = null
                var filler = false
                var hasAnyData = false

                for (sourceResult in results) {
                    val ep = sourceResult[epNum] ?: continue
                    hasAnyData = true
                    if (fetchTitles && title == null && ep.title != null) title = ep.title
                    if (fetchSummaries && description == null && ep.description != null) description = ep.description
                    if (fetchThumbnails && thumbnailUrl == null && ep.thumbnailUrl != null) thumbnailUrl = ep.thumbnailUrl
                    if (fetchAirDates && airDate == null && ep.airDate != null) airDate = ep.airDate
                    if (ep.filler) filler = true
                }

                // Fallback thumbnail: use banner if no source had a per-episode thumbnail
                if (thumbnailUrl == null && fallbackThumb != null && hasAnyData) {
                    thumbnailUrl = fallbackThumb
                }

                if (hasAnyData) {
                    merged[epNum] = EpisodeMetadata(
                        animeId = request.animeId,
                        episodeNumber = epNum,
                        title = title,
                        description = description,
                        thumbnailUrl = thumbnailUrl,
                        airDate = airDate,
                        filler = filler,
                        lastFetched = System.currentTimeMillis(),
                    )
                }
            }

            Log.i(TAG, "Merged ${merged.size} episodes for animeId=${request.animeId}")

            // Cache the result
            cache[request.animeId] = merged

            merged
        }

    /** Clear the in-memory cache (e.g. on pull-to-refresh). */
    fun clearCache() {
        cache.clear()
    }

    companion object {
        private const val TAG = "EpisodeMetadataRepo"
    }
}
