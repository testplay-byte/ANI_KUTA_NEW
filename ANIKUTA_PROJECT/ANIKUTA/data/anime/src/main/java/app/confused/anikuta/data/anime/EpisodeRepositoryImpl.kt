package app.confused.anikuta.data.anime

import app.confused.anikuta.core.common.model.Episode
import app.confused.anikuta.core.common.repository.EpisodeRepository
import app.confused.anikuta.core.common.di.DispatcherProvider
import app.confused.anikuta.core.database.AnikutaDatabase
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.Flow

class EpisodeRepositoryImpl(
    private val database: AnikutaDatabase,
    private val dispatchers: DispatcherProvider,
) : EpisodeRepository {

    override fun observeByAnimeId(animeId: Long): Flow<List<Episode>> =
        database.episodesQueries.selectByAnimeId(animeId, EpisodeMapper::map)
            .asFlow()
            .mapToList(dispatchers.io)

    override fun observeById(id: Long): Flow<Episode?> =
        database.episodesQueries.selectById(id, EpisodeMapper::map)
            .asFlow()
            .mapToOneOrNull(dispatchers.io)

    override suspend fun getByAnimeId(animeId: Long): List<Episode> =
        database.episodesQueries.selectByAnimeId(animeId, EpisodeMapper::map)
            .executeAsList()

    override suspend fun getById(id: Long): Episode? =
        database.episodesQueries.selectById(id, EpisodeMapper::map)
            .executeAsOneOrNull()

    override suspend fun upsert(episode: Episode): Long {
        return if (episode.id > 0) {
            database.episodesQueries.update(
                id = episode.id,
                url = episode.url,
                name = episode.name,
                episodeNumber = episode.episodeNumber.toDouble(),
                scanlator = episode.scanlator,
                seen = if (episode.seen) 1L else 0L,
                bookmark = if (episode.bookmark) 1L else 0L,
                lastSecondSeen = episode.lastSecondSeen,
                totalSeconds = episode.totalSeconds,
                dateFetch = episode.dateFetch,
                dateUpload = episode.dateUpload,
                fillermark = episode.fillermark,
                summary = episode.summary,
                previewUrl = episode.previewUrl,
            )
            episode.id
        } else {
            database.episodesQueries.insert(
                animeId = episode.animeId,
                url = episode.url,
                name = episode.name,
                episodeNumber = episode.episodeNumber.toDouble(),
                scanlator = episode.scanlator,
                seen = if (episode.seen) 1L else 0L,
                bookmark = if (episode.bookmark) 1L else 0L,
                lastSecondSeen = episode.lastSecondSeen,
                totalSeconds = episode.totalSeconds,
                sourceOrder = episode.sourceOrder,
                dateFetch = episode.dateFetch,
                dateUpload = episode.dateUpload,
                fillermark = episode.fillermark,
                summary = episode.summary,
                previewUrl = episode.previewUrl,
            )
            -1L
        }
    }

    override suspend fun updateSeen(id: Long, seen: Boolean, lastSecondSeen: Long, totalSeconds: Long) {
        database.episodesQueries.updateSeen(
            id = id,
            seen = if (seen) 1L else 0L,
            lastSecondSeen = lastSecondSeen,
            totalSeconds = totalSeconds,
        )
    }

    override suspend fun updateBookmark(id: Long, bookmark: Boolean) {
        database.episodesQueries.updateBookmark(bookmark = if (bookmark) 1L else 0L, id = id)
    }

    override suspend fun delete(id: Long) {
        database.episodesQueries.delete(id)
    }

    override suspend fun deleteByAnimeId(animeId: Long) {
        database.episodesQueries.deleteByAnimeId(animeId)
    }
}
