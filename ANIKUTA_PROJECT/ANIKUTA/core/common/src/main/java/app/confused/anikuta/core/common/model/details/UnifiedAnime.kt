package app.confused.anikuta.core.common.model.details

import app.confused.anikuta.core.common.model.Episode

/**
 * The unified anime value consumed by `AnimeDetailScreen`.
 *
 * Produced by an [AnimeDetailsProvider] — either an AniList provider
 * (which maps `AniListAnime` → [UnifiedAnime]) or an extension provider
 * (which maps `SAnime` → [UnifiedAnime], optionally merged with AniList
 * metadata when the anime is linked).
 *
 * Every field except [title] and [url] is nullable: the details page
 * conditionally hides UI sections whose fields are null (per doc 04
 * Table 3). AniList mode fills score/format/season/studios/nextAiring;
 * extension mode fills author/artist and derives episodeCount from the
 * fetched episode list. Linked extension anime get the best of both.
 *
 * @property dataSource which provider produced this value (drives the
 *   three-dot menu + conditional sections).
 * @property anilistId the AniList ID, or null for unlinked extension anime.
 *   Drives tracker-button visibility + episode-metadata enrichment.
 * @property malId the MAL ID (AniList-only). Enables the Jikan metadata source.
 * @property sourceId the extension source ID, or null in pure-AniList mode.
 *   Drives the "View from Extension" menu option + extension switching.
 * @property sourceName display label: `"AniList"` or the extension's name.
 * @property url a display URL — `https://anilist.co/anime/{id}` for AniList,
 *   the source-relative `SAnime.url` for extensions. Used by the Share action.
 * @property title the display title (always non-null).
 * @property coverUrl cover-image URL (either source). Null → placeholder.
 * @property coverColorHex hex color (`"#RRGGBB"`) for adaptive theming.
 *   AniList mode: from `coverImage.color`. Extension mode: Palette-extracted
 *   from [coverUrl] (Phase 9). Null → default theme (no dynamic override).
 * @property bannerUrl wide banner image (AniList `bannerImage` or extension
 *   `background_url`). Null → no banner.
 * @property description synopsis (HTML — normalized to plain text by the UI).
 *   Null → `SynopsisSection` is hidden.
 * @property genres genre list (either source; extensions use `SAnime.getGenres()`).
 *   Empty → `GenresRow` is hidden.
 * @property status unified status (either source). [UnifiedStatus.UNKNOWN] →
 *   the status badge is hidden.
 * @property format AniList format (`"TV"`, `"MOVIE"`, …). Null in extension mode.
 * @property episodeCount total episode count (AniList `episodes`, or the fetched
 *   list size for extensions). Null → the count chip is hidden.
 * @property averageScore AniList 0–100 score. Null in extension mode → score hidden.
 * @property season AniList season (`"WINTER"`, …). Null in extension mode.
 * @property seasonYear AniList season year. Null in extension mode.
 * @property startDate ISO-ish `"YYYY-MM-DD"` (AniList). Null in extension mode.
 * @property studios main animation studio names (AniList). Empty in extension mode.
 * @property nextAiringEpisode next-airing countdown (AniList). Null in extension mode.
 * @property author the author (extension-only — `SAnime.author`). Null in AniList mode.
 * @property artist the artist (extension-only — `SAnime.artist`). Null in AniList mode.
 * @property source the original-work source (`"ORIGINAL"`, `"MANGA"`, … — AniList).
 *   Null in extension mode. (Different concept from [sourceId].)
 */
data class UnifiedAnime(
    val dataSource: DataSource,
    // Identity
    val anilistId: Int?,
    val malId: Int?,
    val sourceId: Long?,
    val sourceName: String,
    val url: String,
    // Display
    val title: String,
    val coverUrl: String?,
    val coverColorHex: String?,
    val bannerUrl: String?,
    // Metadata
    val description: String?,
    val genres: List<String>,
    val status: UnifiedStatus,
    val format: String?,
    val episodeCount: Int?,
    val averageScore: Int?,
    val season: String?,
    val seasonYear: Int?,
    val startDate: String?,
    val studios: List<String>,
    val nextAiringEpisode: NextAiringEpisode?,
    // Extension-only bonuses
    val author: String?,
    val artist: String?,
    // AniList-only metadata
    val source: String?,
)

/**
 * The result of an [AnimeDetailsProvider.load] call.
 *
 * Carries only types from `:core:common` so the provider interface can
 * live here without pulling in `:core:episode-metadata` / `:core:source-api`.
 * The ViewModel (which has the full dep graph) separately fetches episode
 * metadata and resolves the `AnimeSource` for the watch/download flows,
 * using [UnifiedAnime.sourceId] / [UnifiedAnime.anilistId].
 *
 * @property anime the unified anime value.
 * @property episodes the episode list (always from the extension in both modes —
 *   AniList has no playable episodes). May be empty if the source has none yet.
 */
data class DetailsResult(
    val anime: UnifiedAnime,
    val episodes: List<Episode>,
)
