package app.confused.anikuta.data.anime

import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.database.AnikutaDatabase
import app.cash.sqldelight.coroutines.extensions.asFlow
import app.cash.sqldelight.coroutines.extensions.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import android.util.Log

/**
 * SQLDelight-backed implementation of [AnimeRepository].
 *
 * Per `RULES/ai-agent-rules.md` §3: this implements the interface defined in
 * `:core:common`. The ViewModel never sees this class — only the interface.
 *
 * Logging (ADR-033): uses tag [TAG] for filterable logcat output.
 */
class AnimeRepositoryImpl(
    private val database: AnikutaDatabase,
) : AnimeRepository {

    override fun observeAll(): Flow<List<Anime>> =
        database.animesQueries.selectAll(AnimeMapper::map)
            .asFlow()
            .mapToList(Dispatchers.IO)

    override fun observeFavorites(): Flow<List<Anime>> =
        database.animesQueries.selectFavorites(AnimeMapper::map)
            .asFlow()
            .mapToList(Dispatchers.IO)

    override fun observeById(id: Long): Flow<Anime?> =
        database.animesQueries.selectById(id, AnimeMapper::map)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { it.firstOrNull() }

    override fun observeBySource(sourceId: Long): Flow<List<Anime>> =
        database.animesQueries.selectBySource(sourceId, AnimeMapper::map)
            .asFlow()
            .mapToList(Dispatchers.IO)

    override suspend fun getById(id: Long): Anime? =
        database.animesQueries.selectById(id, AnimeMapper::map)
            .executeAsOneOrNull()

    override suspend fun searchByName(query: String): List<Anime> =
        database.animesQueries.searchByName(query, AnimeMapper::map)
            .executeAsList()

    override suspend fun upsert(anime: Anime): Long {
        Log.d(TAG, "upsert: anime=${anime.title}, id=${anime.id}")
        return if (anime.id > 0) {
            database.animesQueries.update(
                id = anime.id,
                url = anime.url,
                title = anime.title,
                artist = anime.artist,
                author = anime.author,
                description = anime.description,
                genre = anime.genre.takeIf { it.isNotEmpty() }?.joinToString(","),
                coverUrl = anime.coverUrl,
                status = anime.status.toLong(),
                thumbnailUrl = anime.thumbnailUrl,
                favorite = if (anime.favorite) 1L else 0L,
                viewerFlags = anime.viewerFlags.toLong(),
                nextUpdate = anime.nextUpdate,
                updateStrategy = anime.updateStrategy.toLong(),
                coverLastModified = anime.coverLastModified,
                releaseDate = anime.releaseDate,
                lastRefresh = anime.lastRefresh,
                lastMetadataFetch = anime.lastMetadataFetch,
                nextEpisodeCheck = anime.nextEpisodeCheck,
            )
            anime.id
        } else {
            database.animesQueries.insert(
                url = anime.url,
                title = anime.title,
                artist = anime.artist,
                author = anime.author,
                description = anime.description,
                genre = anime.genre.takeIf { it.isNotEmpty() }?.joinToString(","),
                coverUrl = anime.coverUrl,
                status = anime.status.toLong(),
                thumbnailUrl = anime.thumbnailUrl,
                favorite = if (anime.favorite) 1L else 0L,
                sourceId = anime.sourceId,
                dateAdded = anime.dateAdded,
                viewerFlags = anime.viewerFlags.toLong(),
                nextUpdate = anime.nextUpdate,
                updateStrategy = anime.updateStrategy.toLong(),
                coverLastModified = anime.coverLastModified,
                releaseDate = anime.releaseDate,
                lastRefresh = anime.lastRefresh,
                lastMetadataFetch = anime.lastMetadataFetch,
                nextEpisodeCheck = anime.nextEpisodeCheck,
            )
            -1L // TODO: return the actual inserted ID via last_insert_rowid()
        }
    }

    override suspend fun updateFavorite(id: Long, favorite: Boolean, dateAdded: Long) {
        Log.d(TAG, "updateFavorite: id=$id, favorite=$favorite")
        database.animesQueries.updateFavorite(
            id = id,
            favorite = if (favorite) 1L else 0L,
            dateAdded = dateAdded,
        )
    }

    override suspend fun updateLastRefresh(id: Long, lastRefresh: Long) {
        database.animesQueries.updateLastRefresh(lastRefresh = lastRefresh, id = id)
    }

    override suspend fun updateLastMetadataFetch(id: Long, lastMetadataFetch: Long) {
        database.animesQueries.updateLastMetadataFetch(lastMetadataFetch = lastMetadataFetch, id = id)
    }

    override suspend fun updateNextEpisodeCheck(id: Long, nextEpisodeCheck: Long?) {
        database.animesQueries.updateNextEpisodeCheck(nextEpisodeCheck = nextEpisodeCheck, id = id)
    }

    override suspend fun delete(id: Long) {
        Log.d(TAG, "delete: id=$id")
        database.animesQueries.delete(id)
    }

    companion object {
        private const val TAG = "AnikutaAnimeRepo"
    }
}
