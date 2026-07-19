package app.anikuta.data.source.anime

import androidx.paging.PagingState
import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.model.AnimeFilterList
import app.anikuta.source.api.model.AnimesPage
import app.anikuta.source.api.model.SAnime
import app.anikuta.core.util.lang.withIOContext
import app.anikuta.domain.items.episode.model.NoEpisodesException
import app.anikuta.domain.source.anime.repository.AnimeSourcePagingSourceType

class AnimeSourceSearchPagingSource(
    source: AnimeCatalogueSource,
    val query: String,
    val filters: AnimeFilterList,
) : AnimeSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): AnimesPage {
        return source.getSearchAnime(currentPage, query, filters)
    }
}

class AnimeSourcePopularPagingSource(source: AnimeCatalogueSource) : AnimeSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): AnimesPage {
        return source.getPopularAnime(currentPage)
    }
}

class AnimeSourceLatestPagingSource(source: AnimeCatalogueSource) : AnimeSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): AnimesPage {
        return source.getLatestUpdates(currentPage)
    }
}

abstract class AnimeSourcePagingSource(
    protected val source: AnimeCatalogueSource,
) : AnimeSourcePagingSourceType() {

    abstract suspend fun requestNextPage(currentPage: Int): AnimesPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, SAnime> {
        val page = params.key ?: 1

        val animesPage = try {
            withIOContext {
                requestNextPage(page.toInt())
                    .takeIf { it.animes.isNotEmpty() }
                    ?: throw NoEpisodesException()
            }
        } catch (e: Exception) {
            return LoadResult.Error(e)
        }

        return LoadResult.Page(
            data = animesPage.animes,
            prevKey = null,
            nextKey = if (animesPage.hasNextPage) page + 1 else null,
        )
    }

    override fun getRefreshKey(state: PagingState<Long, SAnime>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}
