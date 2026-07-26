package app.confused.anikuta.core.backup.model

import kotlinx.serialization.Serializable

/**
 * Serializable wrapper for the per-anime episode metadata cache.
 *
 * Mirrors `EpisodeMetadataCache`'s internal structure: for each anime (keyed
 * by AniList ID as a string), a map of episode number → [EpisodeMetadataItem].
 *
 * The [EpisodeMetadataItem] mirrors `app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata`
 * but is a standalone `@Serializable` class so the backup format doesn't
 * depend on the internal model's serialization stability.
 */
@Serializable
data class EpisodeMetadataBackup(
    /** Key: AniList anime ID (as string). Value: per-episode metadata. */
    val byAnime: Map<String, Map<String, EpisodeMetadataItem>> = emptyMap(),
)

/**
 * One episode's enriched metadata (mirrors the domain EpisodeMetadata model).
 */
@Serializable
data class EpisodeMetadataItem(
    val episodeNumber: Int,
    val title: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val airDate: Long? = null,
    val filler: Boolean = false,
    val lastFetched: Long = 0L,
)
