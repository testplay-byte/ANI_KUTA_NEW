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
     * @param forceRefresh if `true`, skip the DB-first short-circuit and fetch fresh
     *   data from the network (used by pull-to-refresh). If `false` (default), the
     *   provider MAY return cached DB data instantly when available.
     * @return the unified result, or `null` if the anime cannot be resolved.
     * @throws Throwable on transient errors (network, extension binary-incompat).
     */
    suspend fun load(request: DetailsRequest, forceRefresh: Boolean = false): DetailsResult?

    /**
     * Load ONLY the episode list for [request] — without re-fetching the anime
     * metadata (title, description, cover, score, etc.).
     *
     * Used when the user switches the extension source from the episodes header
     * **while in AniList mode**: the anime metadata stays (from AniList), only
     * the episodes refresh from the newly-selected extension. This prevents the
     * bug where switching extensions from the episodes header would silently
     * switch the entire view (title, synopsis, etc.) to the new extension's data.
     *
     * @return the episode list, or `null` if the source cannot be resolved.
     * @throws Throwable on transient errors.
     */
    suspend fun loadEpisodes(request: DetailsRequest): List<app.confused.anikuta.core.common.model.Episode>?
}
