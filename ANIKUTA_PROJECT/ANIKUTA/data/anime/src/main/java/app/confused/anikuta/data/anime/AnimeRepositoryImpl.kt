package app.confused.anikuta.data.anime

import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.common.di.DispatcherProvider
import app.confused.anikuta.core.database.AnikutaDatabase
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import android.util.Log

/**
 * SQLDelight-backed implementation of [AnimeRepository].
 *
 * Per `RULES/ai-agent-rules.md` §3: this implements the interface defined in
 * `:core:common`. The ViewModel never sees this class — only the interface.
 *
 * Logging (ADR-033): uses tag [TAG] for filterable logcat output.
 *
 * Phase A: added AniList-ID-based lookups + lastWatched + metadata updates.
 */
class AnimeRepositoryImpl(
    private val database: AnikutaDatabase,
    private val dispatchers: DispatcherProvider,
) : AnimeRepository {

    override fun observeAll(): Flow<List<Anime>> =
        database.animesQueries.selectAll(AnimeMapper::map)
            .asFlow()
            .mapToList(dispatchers.io)

    override fun observeFavorites(): Flow<List<Anime>> =
        database.animesQueries.selectFavorites(AnimeMapper::map)
            .asFlow()
            .mapToList(dispatchers.io)

    override fun observeById(id: Long): Flow<Anime?> =
        database.animesQueries.selectById(id, AnimeMapper::map)
            .asFlow()
            .mapToOneOrNull(dispatchers.io)

    override fun observeBySource(sourceId: Long): Flow<List<Anime>> =
        database.animesQueries.selectBySource(sourceId, AnimeMapper::map)
            .asFlow()
            .mapToList(dispatchers.io)

    override fun observeByAnilistId(anilistId: Int): Flow<Anime?> =
        database.animesQueries.selectByAnilistId(anilistId.toLong(), AnimeMapper::map)
            .asFlow()
            .mapToOneOrNull(dispatchers.io)

    override suspend fun getById(id: Long): Anime? =
        database.animesQueries.selectById(id, AnimeMapper::map)
            .executeAsOneOrNull()

    override suspend fun getByAnilistId(anilistId: Int): Anime? =
        database.animesQueries.selectByAnilistId(anilistId.toLong(), AnimeMapper::map)
            .executeAsOneOrNull()

    override suspend fun searchByName(query: String): List<Anime> =
        database.animesQueries.searchByName(query, AnimeMapper::map)
            .executeAsList()

    override suspend fun upsert(anime: Anime): Long {
        Log.d(TAG, "upsert: anime=${anime.title}, id=${anime.id}, anilistId=${anime.anilistId}")
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
                anilistId = anime.anilistId?.toLong(),
                coverColor = anime.coverColor,
                score = anime.score,
                totalEpisodes = anime.totalEpisodes?.toLong(),
                lastWatched = anime.lastWatched,
            )
            anime.id
        } else {
            database.transactionWithResult {
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
                    anilistId = anime.anilistId?.toLong(),
                    coverColor = anime.coverColor,
                    score = anime.score,
                    totalEpisodes = anime.totalEpisodes?.toLong(),
                    lastWatched = anime.lastWatched,
                )
                database.animesQueries.lastInsertedRowId().executeAsOne()
            }
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

    override suspend fun updateFavoriteByAnilistId(anilistId: Int, favorite: Boolean, dateAdded: Long) {
        Log.d(TAG, "updateFavoriteByAnilistId: anilistId=$anilistId, favorite=$favorite")
        database.animesQueries.updateFavoriteByAnilistId(
            anilistId = anilistId.toLong(),
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

    override suspend fun updateLastWatched(id: Long, lastWatched: Long) {
        database.animesQueries.updateLastWatched(lastWatched = lastWatched, id = id)
    }

    override suspend fun updateLastWatchedByAnilistId(anilistId: Int, lastWatched: Long) {
        database.animesQueries.updateLastWatchedByAnilistId(
            lastWatched = lastWatched,
            anilistId = anilistId.toLong(),
        )
    }

    override suspend fun updateAnilistMetadata(
        anilistId: Int,
        title: String,
        coverUrl: String?,
        coverColor: String?,
        score: Double?,
        totalEpisodes: Int?,
    ) {
        database.animesQueries.updateAnilistMetadataByAnilistId(
            anilistId = anilistId.toLong(),
            title = title,
            coverUrl = coverUrl,
            coverColor = coverColor,
            score = score,
            totalEpisodes = totalEpisodes?.toLong(),
        )
    }

    override suspend fun delete(id: Long) {
        Log.d(TAG, "delete: id=$id")
        database.animesQueries.delete(id)
    }

    companion object {
        private const val TAG = "AnikutaAnimeRepo"
    }
}
