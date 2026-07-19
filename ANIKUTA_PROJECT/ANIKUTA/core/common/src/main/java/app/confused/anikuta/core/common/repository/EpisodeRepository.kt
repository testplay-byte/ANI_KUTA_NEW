package app.confused.anikuta.core.common.repository

import app.confused.anikuta.core.common.model.Episode
import kotlinx.coroutines.flow.Flow

/** Repository interface for episode data access. */
interface EpisodeRepository {

    fun observeByAnimeId(animeId: Long): Flow<List<Episode>>

    fun observeById(id: Long): Flow<Episode?>

    suspend fun getByAnimeId(animeId: Long): List<Episode>

    suspend fun getById(id: Long): Episode?

    suspend fun upsert(episode: Episode): Long

    suspend fun updateSeen(id: Long, seen: Boolean, lastSecondSeen: Long, totalSeconds: Long)

    suspend fun updateBookmark(id: Long, bookmark: Boolean)

    suspend fun delete(id: Long)

    suspend fun deleteByAnimeId(animeId: Long)
}
