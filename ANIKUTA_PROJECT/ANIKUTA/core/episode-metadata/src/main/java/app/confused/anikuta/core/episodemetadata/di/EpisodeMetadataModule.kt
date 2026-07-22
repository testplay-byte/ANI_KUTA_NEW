package app.confused.anikuta.core.episodemetadata.di

import app.confused.anikuta.core.episodemetadata.repository.EpisodeMetadataRepository
import app.confused.anikuta.core.episodemetadata.source.EpisodeMetadataSourceRegistry
import app.confused.anikuta.core.episodemetadata.source.anikage.AnikageCcSource
import app.confused.anikuta.core.episodemetadata.source.anilist.AniListStreamingSource
import app.confused.anikuta.core.episodemetadata.source.jikan.JikanMalSource
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin DI module for the episode-metadata module.
 *
 * Source priority (registration order = merge priority):
 * 1. Jikan (TITLE, AIR_DATE)
 * 2. Anikage (TITLE, DESCRIPTION, THUMBNAIL, AIR_DATE)
 * 3. AniList (TITLE, THUMBNAIL)
 */
val episodeMetadataModule: Module = module {
    single { EpisodeMetadataSourceRegistry() }
    single { JikanMalSource(get(), get()) }
    single { AnikageCcSource(get(), get()) }
    single { AniListStreamingSource(get(), get()) }
    single {
        val registry = get<EpisodeMetadataSourceRegistry>()
        registry.register(get<JikanMalSource>())
        registry.register(get<AnikageCcSource>())
        registry.register(get<AniListStreamingSource>())
        EpisodeMetadataRepository(registry)
    }
}
