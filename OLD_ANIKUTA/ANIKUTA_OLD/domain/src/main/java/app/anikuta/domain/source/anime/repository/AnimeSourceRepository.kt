package app.anikuta.domain.source.anime.repository

import androidx.paging.PagingSource
import app.anikuta.source.api.model.AnimeFilterList
import app.anikuta.source.api.model.SAnime
import kotlinx.coroutines.flow.Flow
import app.anikuta.domain.source.anime.model.AnimeSource

typealias AnimeSourcePagingSourceType = PagingSource<Long, SAnime>

interface AnimeSourceRepository {

    fun getAnimeSources(): Flow<List<AnimeSource>>

    fun getOnlineAnimeSources(): Flow<List<AnimeSource>>

    fun getAnimeSourcesWithFavoriteCount(): Flow<List<Pair<AnimeSource, Long>>>

    fun searchAnime(sourceId: Long, query: String, filterList: AnimeFilterList): AnimeSourcePagingSourceType

    fun getPopularAnime(sourceId: Long): AnimeSourcePagingSourceType

    fun getLatestAnime(sourceId: Long): AnimeSourcePagingSourceType
}
