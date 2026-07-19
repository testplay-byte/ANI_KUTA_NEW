package app.anikuta.domain.source.anime.service

import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.AnimeSource
import app.anikuta.source.api.online.AnimeHttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import app.anikuta.domain.source.anime.model.StubAnimeSource

interface AnimeSourceManager {

    val isInitialized: StateFlow<Boolean>

    val catalogueSources: Flow<List<AnimeCatalogueSource>>

    fun get(sourceKey: Long): AnimeSource?

    fun getOrStub(sourceKey: Long): AnimeSource

    fun getOnlineSources(): List<AnimeHttpSource>

    fun getCatalogueSources(): List<AnimeCatalogueSource>

    fun getStubSources(): List<StubAnimeSource>
}
