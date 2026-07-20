package app.confused.anikuta.core.episodemetadata.repository

import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata
import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadataRequest
import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadataResult
import app.confused.anikuta.core.episodemetadata.source.EpisodeMetadataField
import app.confused.anikuta.core.episodemetadata.source.EpisodeMetadataSourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Repository for episode metadata — the public API of the episode-metadata module.
 *
 * Per ADR-022: this module takes in anime info and returns episode metadata.
 * It uses the pluggable source registry to fetch from multiple sources in parallel,
 * then merges the results (per-field priority).
 *
 * Per ADR-011: the dual-metadata-source fallback principle applies here too —
 * if one source lacks a field, the next source fills it in.
 *
 * Usage:
 * ```kotlin
 * val repository = EpisodeMetadataRepository(registry)
 * val result = repository.fetch(EpisodeMetadataRequest(animeId, title, epNum))
 * when (result) {
 *     is EpisodeMetadataResult.Success -> { /* use result.metadata */ }
 *     is EpisodeMetadataResult.NoData -> { /* skip */ }
 *     is EpisodeMetadataResult.Error -> { /* log */ }
 * }
 * ```
 */
class EpisodeMetadataRepository(
    private val registry: EpisodeMetadataSourceRegistry,
) {
    /**
     * Fetch episode metadata from all registered sources in parallel, then merge.
     *
     * Merge priority: the first source to provide a field wins (sources are
     * ordered by registration order = priority).
     */
    suspend fun fetch(request: EpisodeMetadataRequest): EpisodeMetadataResult = withContext(Dispatchers.IO) {
        val sources = registry.getSupported(request)
        if (sources.isEmpty()) {
            return@withContext EpisodeMetadataResult.NoData
        }

        // Fetch from all sources in parallel
        val results = coroutineScope {
            sources.map { source ->
                async { runCatching { source.fetch(request) }.getOrNull() }
            }.map { it.await() }
        }

        // Merge: first non-null field wins (per-field priority)
        var merged = EpisodeMetadata(
            animeId = request.animeId,
            episodeNumber = request.episodeNumber,
            lastFetched = System.currentTimeMillis(),
        )
        var hasAnyData = false

        for (result in results) {
            if (result == null) continue
            hasAnyData = true

            if (merged.title == null && result.title != null) {
                merged = merged.copy(title = result.title)
            }
            if (merged.description == null && result.description != null) {
                merged = merged.copy(description = result.description)
            }
            if (merged.thumbnailUrl == null && result.thumbnailUrl != null) {
                merged = merged.copy(thumbnailUrl = result.thumbnailUrl)
            }
            if (merged.airDate == null && result.airDate != null) {
                merged = merged.copy(airDate = result.airDate)
            }
            if (!merged.filler && result.filler) {
                merged = merged.copy(filler = true)
            }
        }

        if (!hasAnyData) {
            EpisodeMetadataResult.NoData
        } else {
            EpisodeMetadataResult.Success(merged)
        }
    }
}
