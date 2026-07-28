package app.confused.anikuta.core.anilist.details

import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.model.AiringScheduleInfo
import app.confused.anikuta.core.common.model.MetadataProviderId
import app.confused.anikuta.core.common.model.details.UnifiedAnime
import app.confused.anikuta.core.providerapi.AiringScheduleProvider
import app.confused.anikuta.core.providerapi.AiringScheduleInfo as ProviderAiringScheduleInfo
import app.confused.anikuta.core.providerapi.CoverImageInfo
import app.confused.anikuta.core.providerapi.CoverImageProvider
import app.confused.anikuta.core.providerapi.HomeFeedProvider
import app.confused.anikuta.core.providerapi.MetadataCapability
import app.confused.anikuta.core.providerapi.MetadataProvider
import app.confused.anikuta.core.providerapi.SearchFilters
import app.confused.anikuta.core.providerapi.SearchProvider

/**
 * AniList adapter for the [MetadataProvider] umbrella (ADR-041).
 *
 * **Wraps the existing [AniListApi] — does NOT rewrite it.** The GraphQL client,
 * rate limiter, and cache stay unchanged. This class is a thin adapter that
 * maps [AniListApi] methods to the capability interfaces, producing
 * [UnifiedAnime] via the existing [toUnifiedAnime] mapper.
 *
 * # Capabilities
 *
 * AniList supports: [MetadataCapability.HOME_FEED], [MetadataCapability.SEARCH],
 * [MetadataCapability.AIRING_SCHEDULE], [MetadataCapability.COVER_IMAGES].
 * AniList does NOT implement [MetadataCapability.DETAILS] here — the existing
 * `AniListDetailsProvider` (in `:data:anime`) handles the details page via the
 * separate `AnimeDetailsProvider` interface (ADR-039). The two abstractions
 * coexist; a future phase may consolidate them.
 *
 * # Availability
 *
 * AniList's browse/search/schedule endpoints are unauthenticated, so
 * [isAvailable] returns `true` (the [AniListApi] handles rate-limit backoff
 * internally via its [app.confused.anikuta.core.anilist.api.AniListRateLimiter]).
 * When a second provider is registered (e.g., MAL), the registry's fallback
 * chain handles the case where AniList network calls fail.
 *
 * Per `_ARCHITECTURE_PLAN/proposals/02_provider_abstraction.md` §3.
 */
class AniListMetadataProvider(
    private val api: AniListApi,
) : MetadataProvider, HomeFeedProvider, SearchProvider, AiringScheduleProvider, CoverImageProvider {

    override val id: MetadataProviderId = MetadataProviderId.ANILIST
    override val displayName: String = "AniList"
    override val requiresAuth: Boolean = false
    override val capabilities: Set<MetadataCapability> = setOf(
        MetadataCapability.HOME_FEED,
        MetadataCapability.SEARCH,
        MetadataCapability.AIRING_SCHEDULE,
        MetadataCapability.COVER_IMAGES,
    )

    override suspend fun isAvailable(): Boolean {
        // AniList browse is unauthenticated + the API handles rate-limit backoff
        // internally. We're always "available" — transient network errors surface
        // as thrown exceptions from the capability methods, which callers handle.
        return true
    }

    // ── HomeFeedProvider ──

    override suspend fun fetchTrending(page: Int, perPage: Int): List<UnifiedAnime> =
        api.fetchTrending(page, perPage).map { it.toUnifiedAnime() }

    override suspend fun fetchPopular(page: Int, perPage: Int): List<UnifiedAnime> =
        api.fetchPopular(page, perPage).map { it.toUnifiedAnime() }

    // ── SearchProvider ──

    override suspend fun search(query: String, page: Int, perPage: Int): List<UnifiedAnime> =
        api.searchAnime(query, page, perPage).map { it.toUnifiedAnime() }

    override suspend fun searchWithFilters(filters: SearchFilters): List<UnifiedAnime> =
        api.searchAnimeWithFilters(
            query = filters.query,
            page = filters.page,
            perPage = filters.perPage,
            genres = filters.genres?.toSet() ?: emptySet(),
            year = filters.year,
            season = filters.season,
            format = filters.format,
            status = filters.status,
            sort = filters.sort ?: "POPULARITY_DESC",
            minScore = filters.minScore ?: 0,
        ).map { it.toUnifiedAnime() }

    // ── AiringScheduleProvider ──
    //
    // AniList's AiringScheduleInfo is per-anime (nextAiringEpisode + upcomingEpisodes).
    // We flatten to one ProviderAiringScheduleInfo per upcoming episode, keyed by anilistId.

    override suspend fun fetchSchedule(ids: List<Int>): List<ProviderAiringScheduleInfo> {
        val schedule = api.fetchAiringSchedule(ids)
        return schedule.flatMap { it.toProviderInfos() }
    }

    private fun AiringScheduleInfo.toProviderInfos(): List<ProviderAiringScheduleInfo> {
        val result = mutableListOf<ProviderAiringScheduleInfo>()
        nextAiringEpisode?.let { next ->
            result.add(
                ProviderAiringScheduleInfo(
                    animeId = anilistId,
                    episode = next.episode ?: 0,
                    airingAt = next.airingAt?.toLong() ?: 0L,
                    timeUntilAiring = next.timeUntilAiring?.toLong() ?: 0L,
                )
            )
        }
        for (upcoming in upcomingEpisodes) {
            result.add(
                ProviderAiringScheduleInfo(
                    animeId = anilistId,
                    episode = upcoming.episode ?: 0,
                    airingAt = upcoming.airingAt?.toLong() ?: 0L,
                    timeUntilAiring = upcoming.timeUntilAiring?.toLong() ?: 0L,
                )
            )
        }
        return result
    }

    // ── CoverImageProvider ──

    override suspend fun fetchCover(providerId: String): CoverImageInfo? {
        val anilistId = providerId.toIntOrNull() ?: return null
        val anime = api.fetchById(anilistId) ?: return null
        val url = anime.coverUrl ?: return null
        return CoverImageInfo(
            url = url,
            colorHex = anime.coverColorHex,
        )
    }
}
