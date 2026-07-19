package app.anikuta.data.source.anime

import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.AnimeSource
import app.anikuta.source.api.model.AnimeFilterList
import app.anikuta.source.api.online.AnimeHttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import app.anikuta.data.handlers.anime.AnimeDatabaseHandler
import app.anikuta.domain.source.anime.model.StubAnimeSource
import app.anikuta.domain.source.anime.repository.AnimeSourcePagingSourceType
import app.anikuta.domain.source.anime.repository.AnimeSourceRepository
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import app.anikuta.domain.source.anime.model.AnimeSource as DomainSource

class AnimeSourceRepositoryImpl(
    private val sourceManager: AnimeSourceManager,
    private val handler: AnimeDatabaseHandler,
) : AnimeSourceRepository {

    override fun getAnimeSources(): Flow<List<DomainSource>> {
        return sourceManager.catalogueSources.map { sources ->
            sources.map {
                mapSourceToDomainSource(it).copy(
                    supportsLatest = it.supportsLatest,
                )
            }
        }
    }

    override fun getOnlineAnimeSources(): Flow<List<DomainSource>> {
        return sourceManager.catalogueSources.map { sources ->
            sources
                .filterIsInstance<AnimeHttpSource>()
                .map(::mapSourceToDomainSource)
        }
    }

    override fun getAnimeSourcesWithFavoriteCount(): Flow<List<Pair<DomainSource, Long>>> {
        return combine(
            handler.subscribeToList { animesQueries.getAnimeSourceIdWithFavoriteCount() },
            sourceManager.catalogueSources,
        ) { sourceIdWithFavoriteCount, _ -> sourceIdWithFavoriteCount }
            .map {
                it.map { (sourceId, count) ->
                    val source = sourceManager.getOrStub(sourceId)
                    val domainSource = mapSourceToDomainSource(source).copy(
                        isStub = source is StubAnimeSource,
                    )
                    domainSource to count
                }
            }
    }

    override fun searchAnime(
        sourceId: Long,
        query: String,
        filterList: AnimeFilterList,
    ): AnimeSourcePagingSourceType {
        val source = sourceManager.get(sourceId) as AnimeCatalogueSource
        return AnimeSourceSearchPagingSource(source, query, filterList)
    }

    override fun getPopularAnime(sourceId: Long): AnimeSourcePagingSourceType {
        val source = sourceManager.get(sourceId) as AnimeCatalogueSource
        return AnimeSourcePopularPagingSource(source)
    }

    override fun getLatestAnime(sourceId: Long): AnimeSourcePagingSourceType {
        val source = sourceManager.get(sourceId) as AnimeCatalogueSource
        return AnimeSourceLatestPagingSource(source)
    }
}

fun mapSourceToDomainSource(source: AnimeSource): DomainSource = DomainSource(
    id = source.id,
    lang = source.lang,
    name = source.name,
    supportsLatest = false,
    isStub = false,
)
