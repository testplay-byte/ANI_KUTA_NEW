package app.confused.anikuta.core.common.model.details

/**
 * Identifies which data source produced a [UnifiedAnime].
 *
 * The unified details page is source-agnostic: it renders whatever
 * [UnifiedAnime] the [AnimeDetailsProvider] produces. The [DataSource]
 * flag drives the three-dot menu's available options (e.g. "View from
 * AniList" / "View from Extension") and which conditional UI sections
 * are shown (score/format/season are AniList-only; author/artist are
 * extension-only).
 *
 * Future sources (Kitsu, a local metadata API, …) add a new enum value
 * + a new [AnimeDetailsProvider] implementation — zero UI changes.
 */
enum class DataSource {
    /** AniList GraphQL API — provides score, format, season, studios, next-airing, etc. */
    ANILIST,

    /** An installed extension (Aniyomi-compatible `AnimeCatalogueSource`) — provides episodes + author/artist. */
    EXTENSION,
}

/**
 * Unified anime status, reconciling AniList's string statuses with the
 * `SAnime.status` int constants.
 *
 * Collapsed per owner direction (doc 05 §9 Q4): rare extension statuses
 * (`LICENSED`) map to [UNKNOWN]; `ON_HIATUS` is preserved as [HIATUS].
 *
 * @see app.confused.anikuta.core.common.model.AnimeStatus for the raw int constants.
 */
enum class UnifiedStatus {
    FINISHED,
    RELEASING,
    NOT_YET_RELEASED,
    CANCELLED,
    HIATUS,
    UNKNOWN,
}

/**
 * The next-airing countdown shown in the details banner (AniList-only).
 *
 * @param episode the episode number that will air next.
 * @param airingAt epoch seconds when it airs.
 * @param timeUntilAiring seconds remaining until [airingAt] (AniList snapshot).
 */
data class NextAiringEpisode(
    val episode: Int,
    val airingAt: Long,
    val timeUntilAiring: Long,
)
