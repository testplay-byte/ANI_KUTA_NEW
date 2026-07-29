package app.confused.anikuta.core.common.repository

import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.ContentId
import app.confused.anikuta.core.common.model.ContentIdPriority
import app.confused.anikuta.core.common.model.LocalId
import app.confused.anikuta.core.common.model.SourceProvenance
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for anime data access.
 *
 * Per `RULES/ai-agent-rules.md` §3: ViewModels depend on this interface only,
 * never on the implementation. The implementation lives in `:data:anime`.
 *
 * AniList-ID-based methods (Phase A — library page):
 * - [observeByAnilistId] / [getByAnilistId] — look up by AniList media ID.
 * - [updateLastWatched] — bump the last-watched timestamp (for sort).
 * - [updateAnilistMetadata] — refresh cached cover/score/episode count.
 */
interface AnimeRepository {

    fun observeAll(): Flow<List<Anime>>

    fun observeFavorites(): Flow<List<Anime>>

    fun observeById(id: Long): Flow<Anime?>

    fun observeBySource(sourceId: Long): Flow<List<Anime>>

    fun observeByAnilistId(anilistId: Int): Flow<Anime?>

    /**
     * Reactive lookup of an unlinked extension anime by its (sourceId, url) key.
     *
     * Used by [app.confused.anikuta.feature.animedetails.AnimeDetailViewModel] for
     * extension-only anime (anilistId == null) so the library save flag updates
     * reactively when the user toggles save — instead of being polled after each
     * load.
     */
    fun observeBySourceAndUrl(sourceId: Long, url: String): Flow<Anime?>

    suspend fun getById(id: Long): Anime?

    suspend fun getByAnilistId(anilistId: Int): Anime?

    suspend fun getBySourceAndUrl(sourceId: Long, url: String): Anime?

    suspend fun searchByName(query: String): List<Anime>

    suspend fun upsert(anime: Anime): Long

    suspend fun updateFavorite(id: Long, favorite: Boolean, dateAdded: Long)

    suspend fun updateFavoriteByAnilistId(anilistId: Int, favorite: Boolean, dateAdded: Long)

    suspend fun updateLastRefresh(id: Long, lastRefresh: Long)

    suspend fun updateLastMetadataFetch(id: Long, lastMetadataFetch: Long)

    suspend fun updateNextEpisodeCheck(id: Long, nextEpisodeCheck: Long?)

    suspend fun updateLastWatched(id: Long, lastWatched: Long)

    suspend fun updateLastWatchedByAnilistId(anilistId: Int, lastWatched: Long)

    /**
     * Clears the `anilist_id` column on the row with the given id (sets it to NULL).
     *
     * Used by [app.confused.anikuta.navigation.AppController.unlinkFromAniList] to
     * transition a linked library entry to extension-only — the row keeps its
     * `source_id` + `url` + `favorite` (so it stays saved in the library) but
     * severs the AniList association. Re-linking to the same AniList ID later
     * would re-attach to this row; re-linking to a different one would create a
     * new row (the old one is now extension-only, no orphan anilist_id lingering).
     */
    suspend fun clearAnilistId(id: Long)

    /**
     * Sets the `anilist_id` column on the row with the given id — WITHOUT
     * touching `favorite`, `date_added`, category membership, or any other field.
     *
     * Companion of [clearAnilistId]. Used by
     * [app.confused.anikuta.data.extension.details.ExtensionDetailsProvider.persistEpisodes]
     * when a previously-unlinked extension-only row (anilist_id = NULL) is being
     * linked to AniList for the first time — e.g. the user opened an extension
     * anime, saved it to the library, then went through the linking flow. The
     * natural-key lookup `(sourceId, url)` finds the existing row; we stamp the
     * anilist_id on it WITHOUT going through [upsert] (which would overwrite
     * `favorite` with the freshly-built `newAnime.favorite = false`).
     *
     * @param id the anime row's `_id`.
     * @param anilistId the AniList media ID to set (or null to clear, mirroring
     *   [clearAnilistId] — though [clearAnilistId] is preferred for that case
     *   because it's named after its intent).
     */
    suspend fun updateAnilistId(id: Long, anilistId: Int?)

    /**
     * Update the source_id + url on an existing library row (used when switching
     * extension sources — preserves _id, favorite, category membership, etc.).
     */
    suspend fun updateSourceAndUrl(id: Long, sourceId: Long, url: String)

    suspend fun updateAnilistMetadata(
        anilistId: Int,
        title: String,
        coverUrl: String?,
        coverColor: String?,
        score: Double?,
        totalEpisodes: Int?,
        nextAiringEpisode: Int?,
    )

    /**
     * Updates ONLY the cover URL + cover color for a linked anime (by anilistId).
     * Used when the user switches the per-anime data-source preference to Extension —
     * the library should reflect the extension's cover. Does NOT touch title/score/etc.
     */
    suspend fun updatePreferredCoverByAnilistId(anilistId: Int, coverUrl: String?, coverColor: String?)

    /**
     * Updates ONLY the cover URL + cover color for an unlinked extension anime
     * (by sourceId + url). Same use case as [updatePreferredCoverByAnilistId].
     */
    suspend fun updatePreferredCoverBySourceAndUrl(sourceId: Long, url: String, coverUrl: String?, coverColor: String?)

    /**
     * Updates title/description/genre/cover_url/cover_color/status/artist/author
     * on an existing anime row from the extension's (enriched) SAnime data.
     *
     * Fix 3 (SOURCE-SWITCH-FIXES): used by
     * [app.confused.anikuta.data.extension.details.ExtensionDetailsProvider.persistEpisodes]
     * to overwrite stale AniList metadata when the extension re-fetches (e.g. after
     * unlink, after switchExtension, after pull-to-refresh). Called for BOTH
     * newly-inserted AND existing rows so the row always reflects the extension's
     * view of the anime.
     *
     * @param id the anime row's `_id`.
     * @param title the extension's title (`sAnime.title`).
     * @param description the extension's description (`sAnime.description`), nullable.
     * @param genre the extension's genres as a comma-separated string (`sAnime.genre`), nullable.
     * @param coverUrl the extension's cover URL (`sAnime.thumbnail_url`), nullable.
     * @param coverColor the Palette-extracted cover color hex (e.g. "#B1F256"), nullable.
     * @param status the extension's status int (`sAnime.status`).
     * @param artist the extension's artist (`sAnime.artist`), nullable.
     * @param author the extension's author (`sAnime.author`), nullable.
     */
    suspend fun updateMetadataFromExtension(
        id: Long,
        title: String,
        description: String?,
        genre: String?,
        coverUrl: String?,
        coverColor: String?,
        status: Int,
        artist: String?,
        author: String?,
    )

    suspend fun delete(id: Long)

    // ═══ Two-tier identity (ADR-050 — Phase 1) ═══

    /** Look up an anime by its [LocalId] (Tier 1 per-source identity). */
    suspend fun getByLocalId(localId: LocalId): Anime?

    /** Look up animes by their [ContentId] (Tier 2 per-content grouping). Returns all source bindings. */
    suspend fun getByContentId(contentId: ContentId): List<Anime>

    /**
     * Set the local_id + content_id for a row. Used by the backfill (on first launch
     * post-migration) and by upsert (when a new anime is added with identity available).
     */
    suspend fun updateIdentity(id: Long, localId: LocalId, contentId: ContentId)

    /**
     * Set the full source provenance for a row. Used when an anime is linked to a
     * source (the extension metadata is captured for restore + debugging).
     */
    suspend fun updateProvenance(id: Long, provenance: SourceProvenance)

    /**
     * Backfill the local_id + content_id columns for all rows that are missing them.
     *
     * Runs on first launch post-migration (gated by a preference in the caller).
     * For each row:
     * - Computes [LocalId] from (system, sourceId, url) — defaults to ANIYOMI system
     *   for existing rows (all current rows are Aniyomi-system).
     * - Computes [ContentId] from the anilist_id (if present) using the user's
     *   configured priority, else falls back to the local_id.
     *
     * @param priority The user's [ContentIdPriority] (from [ContentIdPreferences]).
     * @return The number of rows backfilled.
     */
    suspend fun backfillIdentityColumns(priority: ContentIdPriority): Int
}
