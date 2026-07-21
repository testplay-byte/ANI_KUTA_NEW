package app.confused.anikuta.data.anime

import app.confused.anikuta.core.common.model.Anime

/**
 * Maps SQLDelight query results to the [Anime] domain model.
 *
 * Parameter order and types match the `animes` table columns (CREATE TABLE order).
 * SQLDelight calls this mapper with the column values; we convert types here.
 *
 * Phase A: added anilistId, coverColor, score, totalEpisodes, lastWatched.
 */
object AnimeMapper {

    @Suppress("UNUSED_PARAMETER")
    fun map(
        id: Long,
        url: String,
        title: String,
        artist: String?,
        author: String?,
        description: String?,
        genre: String?,
        coverUrl: String?,
        status: Long,
        thumbnailUrl: String?,
        favorite: Long,
        sourceId: Long,
        dateAdded: Long,
        viewerFlags: Long,
        nextUpdate: Long,
        updateStrategy: Long,
        coverLastModified: Long,
        releaseDate: Long?,
        lastRefresh: Long,
        lastMetadataFetch: Long?,
        nextEpisodeCheck: Long?,
        anilistId: Long?,
        coverColor: String?,
        score: Double?,
        totalEpisodes: Long?,
        lastWatched: Long,
    ): Anime = Anime(
        id = id,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
        coverUrl = coverUrl,
        status = status.toInt(),
        thumbnailUrl = thumbnailUrl,
        favorite = favorite != 0L,
        sourceId = sourceId,
        dateAdded = dateAdded,
        viewerFlags = viewerFlags.toInt(),
        nextUpdate = nextUpdate,
        updateStrategy = updateStrategy.toInt(),
        coverLastModified = coverLastModified,
        releaseDate = releaseDate,
        lastRefresh = lastRefresh,
        lastMetadataFetch = lastMetadataFetch,
        nextEpisodeCheck = nextEpisodeCheck,
        anilistId = anilistId?.toInt(),
        coverColor = coverColor,
        score = score,
        totalEpisodes = totalEpisodes?.toInt(),
        lastWatched = lastWatched,
    )
}
