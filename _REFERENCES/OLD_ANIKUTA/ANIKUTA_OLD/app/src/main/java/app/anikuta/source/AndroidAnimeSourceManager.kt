package app.anikuta.source

import android.content.Context
import app.anikuta.domain.source.anime.model.StubAnimeSource
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import app.anikuta.extension.anime.AnimeExtensionManager
import app.anikuta.extension.anime.model.AnimeExtension
import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.AnimeSource
import app.anikuta.source.api.online.AnimeHttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * ANI-KUTA AndroidAnimeSourceManager — wires extension sources into a live map.
 *
 * Selective copy-paste from aniyomi's `AndroidAnimeSourceManager` (D1): we keep
 * the core pattern — collect `extensionManager.installedExtensions` and register
 * each source by its `id` into a `ConcurrentHashMap` backed by a StateFlow. We
 * drop the `AnimeStubSourceRepository`, `LocalAnimeSource`, and
 * `AnimeDownloadManager` dependencies (those land in later phases).
 *
 * Source: REFERENCE/app/.../source/anime/AndroidAnimeSourceManager.kt (reduced).
 */
class AndroidAnimeSourceManager(
    private val context: Context,
    private val extensionManager: AnimeExtensionManager,
) : AnimeSourceManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, AnimeSource>())

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized = _isInitialized.asStateFlow()

    override val catalogueSources: Flow<List<AnimeCatalogueSource>> =
        kotlinx.coroutines.flow.flow {
            sourcesMapFlow.collect { map ->
                emit(map.values.filterIsInstance<AnimeCatalogueSource>())
            }
        }

    init {
        // Whenever the installed-extensions list changes (extensions installed,
        // removed, or updated), rebuild the source map. This matches aniyomi's
        // collectLatest pattern.
        scope.launch {
            extensionManager.installedExtensions.collectLatest { extensions ->
                val mutableMap = ConcurrentHashMap<Long, AnimeSource>()
                extensions.forEach { extension: AnimeExtension.Installed ->
                    extension.sources.forEach { source ->
                        mutableMap[source.id] = source
                    }
                }
                sourcesMapFlow.value = mutableMap
                _isInitialized.value = true
            }
        }
    }

    override fun get(sourceKey: Long): AnimeSource? = sourcesMapFlow.value[sourceKey]

    override fun getOrStub(sourceKey: Long): AnimeSource {
        return get(sourceKey) ?: StubAnimeSource(sourceKey, "", "")
    }

    override fun getOnlineSources(): List<AnimeHttpSource> =
        sourcesMapFlow.value.values.filterIsInstance<AnimeHttpSource>()

    override fun getCatalogueSources(): List<AnimeCatalogueSource> =
        sourcesMapFlow.value.values.filterIsInstance<AnimeCatalogueSource>()

    override fun getStubSources(): List<StubAnimeSource> = emptyList()

    /**
     * All registered sources (online + catalogue). Used by the Debug screen to
     * list what's available. Not part of the aniyomi interface — ANI-KUTA addition.
     */
    fun getAllSources(): List<AnimeSource> = sourcesMapFlow.value.values.toList()
}
