package app.confused.anikuta.data.anime

import app.confused.anikuta.core.common.model.Episode

/**
 * Maps SQLDelight query results to the [Episode] domain model.
 *
 * Parameter order and types match the `episodes` table columns.
 */
object EpisodeMapper {

    fun map(
        id: Long,
        animeId: Long,
        url: String?,
        name: String,
        episodeNumber: Double,
        scanlator: String?,
        seen: Long,
        bookmark: Long,
        lastSecondSeen: Long,
        totalSeconds: Long,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long?,
        fillermark: String?,
        summary: String?,
        previewUrl: String?,
    ): Episode = Episode(
        id = id,
        animeId = animeId,
        url = url,
        name = name,
        episodeNumber = episodeNumber.toFloat(),
        scanlator = scanlator,
        seen = seen != 0L,
        bookmark = bookmark != 0L,
        lastSecondSeen = lastSecondSeen,
        totalSeconds = totalSeconds,
        sourceOrder = sourceOrder,
        dateFetch = dateFetch,
        dateUpload = dateUpload,
        fillermark = fillermark,
        summary = summary,
        previewUrl = previewUrl,
    )
}
