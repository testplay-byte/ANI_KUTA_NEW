package app.confused.anikuta.core.anilist.details

import app.confused.anikuta.core.anilist.model.AniListAnime
import app.confused.anikuta.core.anilist.model.coverColorHex
import app.confused.anikuta.core.anilist.model.coverUrl
import app.confused.anikuta.core.anilist.model.displayTitle
import app.confused.anikuta.core.anilist.model.startDateDisplay
import app.confused.anikuta.core.anilist.model.studioName
import app.confused.anikuta.core.common.model.details.DataSource
import app.confused.anikuta.core.common.model.details.HtmlToPlainText
import app.confused.anikuta.core.common.model.details.NextAiringEpisode
import app.confused.anikuta.core.common.model.details.UnifiedAnime
import app.confused.anikuta.core.common.model.details.mapAniListStatus

/**
 * Maps an [AniListAnime] to a [UnifiedAnime] (AniList data source).
 *
 * Lives in `:core:anilist` so BOTH data modules (`:data:anime` for the
 * `AniListDetailsProvider`, `:data:extension` for the merge path in
 * `ExtensionDetailsProvider`) can use it without depending on each other.
 *
 * ~40 lines, Animiru-style (doc 03 §2.2). All AniList-only fields (score,
 * format, season, studios, nextAiring, source) are populated; extension-only
 * fields (author, artist, sourceId) are null.
 *
 * @param matchedSourceId / @param matchedSourceName filled by `AniListDetailsProvider`
 *   AFTER stage-2 source match — so the unified value carries the matched
 *   extension's identity too (enables "View from Extension" in the three-dot
 *   menu even in AniList mode).
 */
fun AniListAnime.toUnifiedAnime(
    matchedSourceId: Long? = null,
    matchedSourceName: String? = null,
): UnifiedAnime {
    val anilistStatus = mapAniListStatus(status)
    return UnifiedAnime(
        dataSource = DataSource.ANILIST,
        // Identity
        anilistId = id,
        malId = idMal,
        sourceId = matchedSourceId,
        sourceName = matchedSourceName ?: "AniList",
        url = "https://anilist.co/anime/$id",
        // Display
        title = displayTitle,
        coverUrl = coverUrl,
        coverColorHex = coverColorHex,
        bannerUrl = bannerImage,
        // Metadata
        description = HtmlToPlainText.normalize(description),
        genres = genres ?: emptyList(),
        status = anilistStatus,
        format = format,
        episodeCount = episodes,
        averageScore = averageScore,
        season = season,
        seasonYear = seasonYear,
        startDate = startDateDisplay,
        studios = studios?.nodes
            ?.filter { it.isAnimationStudio }
            ?.map { it.name }
            ?: emptyList(),
        nextAiringEpisode = nextAiringEpisode?.let { airing ->
            val ep = airing.episode
            val at = airing.airingAt
            val until = airing.timeUntilAiring
            if (ep != null && at != null && until != null) {
                NextAiringEpisode(episode = ep, airingAt = at.toLong(), timeUntilAiring = until.toLong())
            } else null
        },
        // Extension-only bonuses — not available from AniList on the details page today
        author = null,
        artist = null,
        // AniList-only original-source metadata
        source = source,
    )
}
