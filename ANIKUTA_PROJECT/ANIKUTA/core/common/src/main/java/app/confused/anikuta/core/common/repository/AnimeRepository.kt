package app.confused.anikuta.core.common.repository

import app.confused.anikuta.core.common.model.Anime
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

    suspend fun getById(id: Long): Anime?

    suspend fun getByAnilistId(anilistId: Int): Anime?

    suspend fun searchByName(query: String): List<Anime>

    suspend fun upsert(anime: Anime): Long

    suspend fun updateFavorite(id: Long, favorite: Boolean, dateAdded: Long)

    suspend fun updateFavoriteByAnilistId(anilistId: Int, favorite: Boolean, dateAdded: Long)

    suspend fun updateLastRefresh(id: Long, lastRefresh: Long)

    suspend fun updateLastMetadataFetch(id: Long, lastMetadataFetch: Long)

    suspend fun updateNextEpisodeCheck(id: Long, nextEpisodeCheck: Long?)

    suspend fun updateLastWatched(id: Long, lastWatched: Long)

    suspend fun updateLastWatchedByAnilistId(anilistId: Int, lastWatched: Long)

    suspend fun updateAnilistMetadata(
        anilistId: Int,
        title: String,
        coverUrl: String?,
        coverColor: String?,
        score: Double?,
        totalEpisodes: Int?,
    )

    suspend fun delete(id: Long)
}
