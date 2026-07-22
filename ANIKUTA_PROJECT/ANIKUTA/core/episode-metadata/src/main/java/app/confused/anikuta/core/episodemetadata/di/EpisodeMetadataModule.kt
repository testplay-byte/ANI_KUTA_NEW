package app.confused.anikuta.core.episodemetadata.di

import app.confused.anikuta.core.episodemetadata.repository.EpisodeMetadataRepository
import app.confused.anikuta.core.episodemetadata.source.EpisodeMetadataSourceRegistry
import app.confused.anikuta.core.episodemetadata.source.anikage.AnikageCcSource
import app.confused.anikuta.core.episodemetadata.source.anilist.AniListStreamingSource
import app.confused.anikuta.core.episodemetadata.source.jikan.JikanMalSource
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Koin DI module for the episode-metadata module.
 *
 * Source priority (registration order = merge priority):
 * 1. Jikan (TITLE, AIR_DATE)
 * 2. Anikage (TITLE, DESCRIPTION, THUMBNAIL, AIR_DATE)
 * 3. AniList (TITLE, THUMBNAIL)
 *
 * NOTE: The OkHttpClient and Json are created locally in this module (not
 * injected from elsewhere) because the app's main OkHttpClient is registered
 * in Injekt (as NetworkHelper), not in Koin. Creating a dedicated client here
 * keeps the metadata module self-contained and avoids cross-DI dependencies.
 */
val episodeMetadataModule: Module = module {
    // Dedicated OkHttpClient for metadata API calls
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    // Shared JSON parser
    single { Json { ignoreUnknownKeys = true } }

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
