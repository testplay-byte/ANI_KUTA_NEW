package app.confused.anikuta.core.common.model.details

/**
 * Pluggable data-source-agnostic provider that loads the unified anime
 * details + episode list for the `AnimeDetailScreen`.
 *
 * **The bridge between raw data (AniList API / extension `SAnime`) and
 * the unified details page.** The screen calls [load] with a [DetailsRequest]
 * and renders the returned [DetailsResult] — it never knows whether the
 * data came from AniList or an extension.
 *
 * # Architecture
 *
 * - `AniListDetailsProvider` (in `:data:anime`) — wraps the existing AniList
 *   fetch + source-match + episode-fetch flow, mapping `AniListAnime` → [UnifiedAnime].
 * - `ExtensionDetailsProvider` (in `:data:extension`) — maps `SAnime` → [UnifiedAnime],
 *   calls `getAnimeDetails` to enrich (the gap ANIKUTA had), optionally merges
 *   AniList metadata for linked anime, and Palette-extracts the cover color.
 *
 * Both are registered via Koin multi-binding (`single<List<AnimeDetailsProvider>>`)
 * and resolved at runtime via [AnimeDetailsProviderRegistry.forSource]. Adding a
 * third data source (Kitsu, a local API, …) = one new class + one Koin line.
 *
 * # Threading
 *
 * Implementations MUST dispatch network/DB work to `Dispatchers.IO` internally.
 * [load] is `suspend` and safe to call from `viewModelScope`.
 *
 * # Error handling
 *
 * Return `null` only if the anime cannot be resolved at all (e.g. AniList ID
 * not found, or extension source uninstalled). Transient network errors should
 * be retried or surfaced via a thrown exception (the VM catches `Throwable` per
 * the existing binary-compat pattern — `Error` subclasses from extensions are
 * surfaced as error states, not crashes).
 */
interface AnimeDetailsProvider {
    /** Which data source this provider serves. */
    val dataSource: DataSource

    /**
     * Load the unified anime + episodes for [request].
     *
     * @param request identity of the anime to load — either [DetailsRequest.ByAniListId]
     *   or [DetailsRequest.ByExtension].
     * @return the unified result, or `null` if the anime cannot be resolved.
     * @throws Throwable on transient errors (network, extension binary-incompat).
     */
    suspend fun load(request: DetailsRequest): DetailsResult?
}
