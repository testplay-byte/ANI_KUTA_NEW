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
