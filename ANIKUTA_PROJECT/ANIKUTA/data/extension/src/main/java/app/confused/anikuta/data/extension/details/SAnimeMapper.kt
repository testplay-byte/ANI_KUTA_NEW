package app.confused.anikuta.data.extension.details

import app.confused.anikuta.core.common.model.details.DataSource
import app.confused.anikuta.core.common.model.details.HtmlToPlainText
import app.confused.anikuta.core.common.model.details.UnifiedAnime
import app.confused.anikuta.core.common.model.details.mapSAnimeStatus
import eu.kanade.tachiyomi.animesource.model.SAnime

/**
 * Maps an extension `SAnime` to a [UnifiedAnime] (Extension data source).
 *
 * ~35 lines, Animiru-style (doc 03 §2.2). Extension-only fields (author,
 * artist) are populated; AniList-only fields (score, format, season,
 * studios, nextAiring, source) are null — they're merged in separately by
 * [ExtensionDetailsProvider] when the anime is linked to AniList.
 *
 * @param sAnime the (possibly enriched) SAnime from the extension.
 * @param sourceId the `AnimeCatalogueSource.id`.
 * @param sourceName the extension's display name.
 * @param anilistId the linked AniList ID (null for unlinked extension anime).
 * @param coverColorHex Palette-extracted hex color (null if extraction failed
 *   or wasn't attempted). Phase 9 integration point.
 */
fun SAnime.toUnifiedAnime(
    sourceId: Long,
    sourceName: String,
    anilistId: Int?,
    coverColorHex: String?,
): UnifiedAnime = UnifiedAnime(
    dataSource = DataSource.EXTENSION,
    // Identity
    anilistId = anilistId,
    malId = null, // extensions can't provide MAL ID
    sourceId = sourceId,
    sourceName = sourceName,
    url = url, // source-relative URL
    // Display
    title = title,
    coverUrl = thumbnail_url,
    coverColorHex = coverColorHex,
    bannerUrl = background_url,
    // Metadata
    description = HtmlToPlainText.normalize(description),
    genres = getGenres() ?: emptyList(),
    status = mapSAnimeStatus(status),
    format = null, // AniList-only
    episodeCount = null, // derived from the fetched episode list by the provider
    averageScore = null, // AniList-only
    season = null, // AniList-only
    seasonYear = null, // AniList-only
    startDate = null, // AniList-only
    studios = emptyList(), // AniList-only
    nextAiringEpisode = null, // AniList-only
    // Extension-only bonuses
    author = author,
    artist = artist,
    // AniList-only original-source metadata
    source = null,
)

/**
 * Maps a DB [Anime] row to a [UnifiedAnime] for the DB-first short-circuit path
 * (skips getAnimeDetails + Palette + AniList merge — returns instantly from DB).
 *
 * The DB row has the metadata from the previous fetch (the provider persists it).
 * The [coverColorHex] comes from the DB `coverColor` column (set by the previous
 * Palette extraction or AniList merge).
 */
fun app.confused.anikuta.core.common.model.Anime.toUnifiedAnimeFromDb(
    sourceId: Long,
    sourceName: String,
    anilistId: Int?,
): UnifiedAnime = UnifiedAnime(
    dataSource = DataSource.EXTENSION,
    anilistId = anilistId ?: this.anilistId,
    malId = null, // not stored in DB
    sourceId = sourceId,
    sourceName = sourceName,
    url = url,
    title = title,
    coverUrl = coverUrl,
    coverColorHex = coverColor,
    bannerUrl = null, // not stored in DB
    description = app.confused.anikuta.core.common.model.details.HtmlToPlainText.normalize(description),
    genres = genre,
    status = app.confused.anikuta.core.common.model.details.mapSAnimeStatus(status),
    format = null, // AniList-only — not in DB
    episodeCount = totalEpisodes,
    averageScore = score?.toInt(),
    season = null, // AniList-only — not in DB
    seasonYear = null, // AniList-only — not in DB
    startDate = null, // AniList-only — not in DB
    studios = emptyList(), // AniList-only — not in DB
    nextAiringEpisode = nextAiringEpisode?.let { ep ->
        app.confused.anikuta.core.common.model.details.NextAiringEpisode(
            episode = ep,
            airingAt = 0,
            timeUntilAiring = 0,
        )
    },
    author = author,
    artist = artist,
    source = null, // AniList-only — not in DB
)

/**
 * Merge AniList-only metadata into an extension-sourced [UnifiedAnime].
 *
 * Used by [ExtensionDetailsProvider] when an extension anime is linked to
 * AniList — gives the user the best of both sources (extension episodes +
 * author/artist + cover, AniList score/format/season/studios/next-airing).
 *
 * Preserves the extension's [UnifiedAnime.dataSource] = [DataSource.EXTENSION]
 * and the extension's [UnifiedAnime.sourceId]/[UnifiedAnime.url]/[UnifiedAnime.author]/
 * [UnifiedAnime.artist]. Fills the AniList-only fields from [anilistMerge].
 */
fun UnifiedAnime.mergeAniListMetadata(
    anilistMerge: UnifiedAnime,
): UnifiedAnime = copy(
    // AniList identity (so tracker buttons + metadata enrichment work)
    anilistId = anilistMerge.anilistId ?: anilistId,
    malId = anilistMerge.malId ?: malId,
    // AniList-only metadata (extensions can't provide these)
    format = anilistMerge.format,
    averageScore = anilistMerge.averageScore,
    season = anilistMerge.season,
    seasonYear = anilistMerge.seasonYear,
    startDate = anilistMerge.startDate,
    studios = anilistMerge.studios,
    nextAiringEpisode = anilistMerge.nextAiringEpisode,
    source = anilistMerge.source,
    episodeCount = anilistMerge.episodeCount ?: episodeCount,
    // Prefer AniList cover color if present; fall back to Palette-extracted
    coverColorHex = anilistMerge.coverColorHex ?: coverColorHex,
    // Prefer AniList cover/banner if the extension didn't provide them
    coverUrl = coverUrl ?: anilistMerge.coverUrl,
    bannerUrl = bannerUrl ?: anilistMerge.bannerUrl,
    // Prefer AniList genres if the extension didn't provide any
    genres = if (genres.isNotEmpty()) genres else anilistMerge.genres,
    // Prefer AniList description if the extension didn't provide one
    description = description ?: anilistMerge.description,
)
