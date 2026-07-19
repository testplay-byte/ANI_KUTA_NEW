package app.confused.anikuta.core.common.repository

import app.confused.anikuta.core.common.model.Anime
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for anime data access.
 *
 * Per `RULES/ai-agent-rules.md` §3: ViewModels depend on this interface only,
 * never on the implementation. The implementation lives in `:data:anime`.
 */
interface AnimeRepository {

    fun observeAll(): Flow<List<Anime>>

    fun observeFavorites(): Flow<List<Anime>>

    fun observeById(id: Long): Flow<Anime?>

    fun observeBySource(sourceId: Long): Flow<List<Anime>>

    suspend fun getById(id: Long): Anime?

    suspend fun searchByName(query: String): List<Anime>

    suspend fun upsert(anime: Anime): Long

    suspend fun updateFavorite(id: Long, favorite: Boolean, dateAdded: Long)

    suspend fun updateLastRefresh(id: Long, lastRefresh: Long)

    suspend fun updateLastMetadataFetch(id: Long, lastMetadataFetch: Long)

    suspend fun updateNextEpisodeCheck(id: Long, nextEpisodeCheck: Long?)

    suspend fun delete(id: Long)
}
