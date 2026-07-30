package app.confused.anikuta.core.providerapi

import app.confused.anikuta.core.common.model.MetadataProviderId

/**
 * The umbrella interface for all metadata providers.
 *
 * A metadata provider is an external service that supplies anime metadata
 * (titles, covers, scores, schedules, etc.) and optionally tracking. ANIKUTA
 * supports multiple providers (AniList, MAL, TMDB, Kitsu, …) — each implements
 * this interface + the capability sub-interfaces it supports.
 *
 * # Architecture (ADR-041)
 *
 * - [MetadataProvider] is the umbrella — every provider implements it.
 * - Capability sub-interfaces ([HomeFeedProvider], [SearchProvider],
 *   [AiringScheduleProvider], [CoverImageProvider], and the existing
 *   `AnimeDetailsProvider` + `EpisodeMetadataSource`) extend this interface.
 *   A provider declares which capabilities it supports via [capabilities].
 * - [MetadataProviderRegistry] resolves the active provider per capability at
 *   runtime, with a fallback chain if the primary is unavailable.
 *
 * # Adding a new provider
 *
 * 1. Create a new module (e.g., `:data:provider-mal`).
 * 2. Implement [MetadataProvider] + the capability sub-interfaces MAL supports.
 * 3. Register it in Koin: `single<List<MetadataProvider>> { listOf(get<AniListMetadataProvider>(), get<MALMetadataProvider>()) }`.
 * 4. Done. The registry auto-discovers it. The user can select MAL as their
 *    active provider in Settings → Data & Storage → Metadata Providers.
 *
 * # Threading
 *
 * Implementations MUST dispatch network/DB work to `Dispatchers.IO` internally.
 * All capability methods are `suspend` and safe to call from `viewModelScope`.
 *
 * # Availability + fallback
 *
 * [isAvailable] is checked by the registry when resolving a provider. If the
 * primary is unavailable (network down, not authenticated, rate-limited), the
 * registry tries the next provider in the fallback chain. Implementations should
 * cache [isAvailable] results with a short TTL to avoid checking on every call.
 *
 * Per `_ARCHITECTURE_PLAN/proposals/02_provider_abstraction.md`.
 */
interface MetadataProvider {

    /** The stable identifier for this provider. */
    val id: MetadataProviderId

    /** Human-readable name for UI display (e.g., "AniList", "MyAnimeList"). */
    val displayName: String

    /**
     * Whether this provider requires authentication (OAuth, API key, etc.).
     *
     * If `true`, the registry checks [isAvailable] before routing a capability
     * call to this provider. Unauthenticated browse-only providers (like AniList's
     * public GraphQL) return `false`.
     */
    val requiresAuth: Boolean

    /**
     * The set of capabilities this provider implements.
     *
     * Used by [MetadataProviderRegistry.allForCapability] to filter providers
     * when building the fallback chain. A provider that only implements search
     * + details (e.g., a future MAL provider with no airing schedule) returns
     * `setOf(SEARCH, DETAILS)`.
     */
    val capabilities: Set<MetadataCapability>

    /**
     * Whether this provider is currently usable.
     *
     * Checked by the registry before routing a capability call. Returns `false`
     * if the provider is unreachable (network down), not authenticated (when
     * [requiresAuth] is `true` and the user hasn't logged in), or rate-limited.
     *
     * Implementations should cache this with a short TTL (e.g., 30s) to avoid
     * checking on every call.
     */
    suspend fun isAvailable(): Boolean
}

/**
 * A capability that a [MetadataProvider] can implement.
 *
 * Each capability corresponds to a sub-interface:
 * - [HOME_FEED] → [HomeFeedProvider]
 * - [SEARCH] → [SearchProvider]
 * - [AIRING_SCHEDULE] → [AiringScheduleProvider]
 * - [COVER_IMAGES] → [CoverImageProvider]
 * - [DETAILS] → `AnimeDetailsProvider` (existing, in `:core:common`)
 * - [EPISODE_METADATA] → `EpisodeMetadataSource` (existing, in `:core:episode-metadata`)
 *
 * Adding a new capability = one enum value + one sub-interface + update the
 * registry's capability dispatch. Providers opt-in by adding the value to
 * their [MetadataProvider.capabilities] set.
 */
enum class MetadataCapability {
    /** Trending / popular / seasonal feeds for the Browse/Home screen. */
    HOME_FEED,

    /** Search by title + filters. */
    SEARCH,

    /** Airing schedule for the Updates → Schedule tab. */
    AIRING_SCHEDULE,

    /** Cover image URL + dominant color extraction. */
    COVER_IMAGES,

    /** Full anime details + episode list (the details page). */
    DETAILS,

    /** Per-episode metadata (thumbnails, titles, summaries, air dates). */
    EPISODE_METADATA,
}
