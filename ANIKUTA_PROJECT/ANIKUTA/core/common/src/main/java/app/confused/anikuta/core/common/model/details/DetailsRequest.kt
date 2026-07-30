package app.confused.anikuta.core.common.model.details

/**
 * A request to load anime details from a specific data source.
 *
 * Sealed so the compiler enforces exhaustive handling. Carries only
 * primitive identity fields so the [AnimeDetailsProvider] interface
 * can live in `:core:common` without a dependency on `:core:source-api`
 * (the `SAnime`/`AnimeCatalogueSource` types are resolved inside the
 * `:data:extension` provider implementation, which has that dependency).
 *
 * @see AnimeDetailsProvider.load
 */
sealed interface DetailsRequest {
    /**
     * Load by AniList ID. Used when the user taps an anime from browse/search
     * (AniList data first), OR when switching back to AniList mode from the
     * three-dot menu on a linked extension anime.
     */
    data class ByAniListId(val anilistId: Int) : DetailsRequest

    /**
     * Load by extension source + anime URL. Used when the user opens an
     * extension anime from search (linked or unlinked), OR when switching
     * to extension mode from the three-dot menu, OR when switching to a
     * different extension via ManualSearchSheet.
     *
     * @param sourceId the `AnimeCatalogueSource.id`.
     * @param animeUrl the `SAnime.url` (source-relative).
     * @param animeTitle the `SAnime.title` (for display + AniList reverse-search).
     * @param anilistId the linked AniList ID, or null for unlinked extension anime.
     *   When non-null, the extension provider merges AniList metadata (score,
     *   format, season, studios, next-airing) into the [UnifiedAnime].
     */
    data class ByExtension(
        val sourceId: Long,
        val animeUrl: String,
        val animeTitle: String,
        val anilistId: Int? = null,
    ) : DetailsRequest
}
