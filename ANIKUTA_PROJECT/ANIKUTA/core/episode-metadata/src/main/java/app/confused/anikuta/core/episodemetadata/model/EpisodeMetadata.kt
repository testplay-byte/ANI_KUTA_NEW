package app.confused.anikuta.core.episodemetadata.model

/**
 * Per-episode metadata — the enriched data fetched from metadata sources.
 *
 * Per ADR-022: this module is pluggable. Sources (AniList, Jikan, Kitsu, TMDB, etc.)
 * are registered in a [app.confused.anikuta.core.episodemetadata.source.EpisodeMetadataSourceRegistry]
 * and can be added/removed without changing the module.
 *
 * Per ADR-024: the `lastFetched` field tracks when this metadata was last refreshed.
 */
data class EpisodeMetadata(
    val animeId: Int,
    val episodeNumber: Int,
    val title: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val airDate: Long? = null, // Unix timestamp (seconds)
    val filler: Boolean = false,
    val lastFetched: Long = 0L, // System.currentTimeMillis()
)

/**
 * Request for episode metadata.
 *
 * @param animeId The AniList anime ID.
 * @param animeTitle The anime's display title (for search).
 * @param episodeNumber The episode number.
 * @param malId The MyAnimeList ID (for Jikan + Kitsu sources). Null if unknown.
 * @param bannerImage Fallback thumbnail (anime banner/cover) for sources that
 *   don't provide per-episode thumbnails.
 * @param episodeCount Total episode count (for batch fetching).
 */
data class EpisodeMetadataRequest(
    val animeId: Int,
    val animeTitle: String,
    val episodeNumber: Int,
    val malId: Int? = null,
    val bannerImage: String? = null,
    val episodeCount: Int = 0,
)

/**
 * Result of a metadata fetch.
 */
sealed interface EpisodeMetadataResult {
    data class Success(val metadata: EpisodeMetadata) : EpisodeMetadataResult
    data object NoData : EpisodeMetadataResult
    data class Error(val message: String) : EpisodeMetadataResult
}
