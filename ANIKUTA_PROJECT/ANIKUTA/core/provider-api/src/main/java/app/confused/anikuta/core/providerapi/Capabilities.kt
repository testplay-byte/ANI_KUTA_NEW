package app.confused.anikuta.core.providerapi

import app.confused.anikuta.core.common.model.details.UnifiedAnime

/**
 * Capability: trending / popular / seasonal feeds for the Browse/Home screen.
 *
 * Today AniList is the sole home-feed source (Doc 03 §3.8). This interface
 * abstracts it so future providers (TMDB, extensions contributing "popular"
 * feeds, …) can be added as one module + one Koin line.
 *
 * Implementations should cache results with a TTL (AniList uses 24h local +
 * in-memory SWR). The caller (BrowseScreen) handles pull-to-refresh by
 * calling [fetchTrending] / [fetchPopular] again.
 *
 * Per `_ARCHITECTURE_PLAN/proposals/02_provider_abstraction.md` §2.2.
 */
interface HomeFeedProvider : MetadataProvider {

    /**
     * Fetch the trending anime list.
     *
     * @param page 1-indexed page number.
     * @param perPage items per page (AniList caps at 50).
     * @return the list, possibly empty. Never null.
     * @throws Throwable on transient errors (network, rate limit).
     */
    suspend fun fetchTrending(page: Int = 1, perPage: Int = 20): List<UnifiedAnime>

    /**
     * Fetch the popular anime list (all-time popularity, distinct from trending).
     *
     * @param page 1-indexed page number.
     * @param perPage items per page.
     * @return the list, possibly empty. Never null.
     * @throws Throwable on transient errors.
     */
    suspend fun fetchPopular(page: Int = 1, perPage: Int = 20): List<UnifiedAnime>
}

/**
 * Capability: search by title + filters.
 *
 * Today the search screen is dual-source (AniList tab + Extension tab). The
 * AniList tab calls `AniListApi.searchAnime` / `searchAnimeWithFilters`. This
 * interface abstracts the provider side so MAL/TMDB can be added as additional
 * search tabs (or replace AniList as the active search provider).
 *
 * Per `_ARCHITECTURE_PLAN/proposals/02_provider_abstraction.md` §2.2.
 */
interface SearchProvider : MetadataProvider {

    /**
     * Search by free-text query.
     *
     * @param query the search string.
     * @param page 1-indexed page number.
     * @param perPage items per page.
     * @return the list, possibly empty. Never null.
     * @throws Throwable on transient errors.
     */
    suspend fun search(query: String, page: Int = 1, perPage: Int = 20): List<UnifiedAnime>

    /**
     * Search with structured filters (genre, year, season, format, status, sort, score).
     *
     * Not all providers support all filters — unsupported filters are ignored.
     * The [SearchFilters] object uses nullable fields so callers can omit any.
     *
     * @return the list, possibly empty. Never null.
     * @throws Throwable on transient errors.
     */
    suspend fun searchWithFilters(filters: SearchFilters): List<UnifiedAnime>
}

/**
 * Structured search filters — provider-agnostic.
 *
 * All fields nullable so callers can omit any. Providers ignore filters they
 * don't support (e.g., MAL has no "season" concept → ignores [season]).
 *
 * @property query free-text query (optional — can search by filters alone).
 * @property page 1-indexed page number.
 * @property perPage items per page.
 * @property genres genre names to include (AND logic on AniList).
 * @property year 4-digit airing year.
 * @property season season name (`"WINTER"`, `"SPRING"`, `"SUMMER"`, `"FALL"`).
 * @property format format (`"TV"`, `"MOVIE"`, `"OVA"`, …).
 * @property status publishing status (`"RELEASING"`, `"FINISHED"`, …).
 * @property sort sort order (`"POPULARITY"`, `"SCORE"`, `"START_DATE"`, …).
 * @property minScore minimum average score (0-100).
 */
data class SearchFilters(
    val query: String? = null,
    val page: Int = 1,
    val perPage: Int = 20,
    val genres: List<String>? = null,
    val year: Int? = null,
    val season: String? = null,
    val format: String? = null,
    val status: String? = null,
    val sort: String? = null,
    val minScore: Int? = null,
)

/**
 * Capability: airing schedule for the Updates → Schedule tab.
 *
 * Today AniList is the sole schedule source (Doc 03 §3.13). This interface
 * abstracts it so TMDB (which has season/episode air dates) or other providers
 * can supply schedule data in the future.
 *
 * Per `_ARCHITECTURE_PLAN/proposals/02_provider_abstraction.md` §2.2.
 */
interface AiringScheduleProvider : MetadataProvider {

    /**
     * Fetch the airing schedule for a set of anime (identified by their provider IDs).
     *
     * @param ids the provider-side IDs (e.g., AniList media IDs) to look up.
     * @return the schedule entries, possibly empty. Never null.
     * @throws Throwable on transient errors.
     */
    suspend fun fetchSchedule(ids: List<Int>): List<AiringScheduleInfo>
}

/**
 * A single airing-schedule entry.
 *
 * @property animeId the provider-side anime ID this entry refers to.
 * @property episode the episode number that will air next.
 * @property airingAt epoch seconds when it airs.
 * @property timeUntilAiring seconds remaining until [airingAt] (snapshot).
 */
data class AiringScheduleInfo(
    val animeId: Int,
    val episode: Int,
    val airingAt: Long,
    val timeUntilAiring: Long,
)

/**
 * Capability: cover image URL + dominant color.
 *
 * Today AniList is the sole cover source (Doc 03 §3.8). This interface
 * abstracts it so extensions or other providers can supply covers.
 *
 * Per `_ARCHITECTURE_PLAN/proposals/02_provider_abstraction.md` §2.2.
 */
interface CoverImageProvider : MetadataProvider {

    /**
     * Fetch the cover image URL + dominant color for an anime.
     *
     * @param providerId the provider-side anime ID (e.g., AniList media ID).
     * @return the cover info, or null if the anime has no cover.
     * @throws Throwable on transient errors.
     */
    suspend fun fetchCover(providerId: String): CoverImageInfo?
}

/**
 * Cover image info — URL + extracted dominant color.
 *
 * @property url the cover image URL.
 * @property colorHex dominant color as a hex string (`"#RRGGBB"`), or null if
 *   not extracted / not available.
 */
data class CoverImageInfo(
    val url: String,
    val colorHex: String? = null,
)
