package app.confused.anikuta.di

import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.common.model.details.AnimeDetailsProvider
import app.confused.anikuta.core.common.model.details.AnimeDetailsProviderRegistry
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.common.repository.EpisodeRepository
import app.confused.anikuta.data.anime.details.AniListDetailsProvider
import app.confused.anikuta.data.extension.cache.ExtensionLinkStore
import app.confused.anikuta.data.extension.cache.SourceLinkStore
import app.confused.anikuta.data.extension.details.ExtensionDetailsProvider
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the unified anime details translation layer (doc 05 §1.3).
 *
 * Registers both [AnimeDetailsProvider] implementations via a single
 * `List<AnimeDetailsProvider>` multi-binding — the SAME pattern used for
 * `List<BackupProvider>` in `BackupModule.kt` (which fixed the
 * "only 1 category saved" Koin key-collision bug).
 *
 * CRITICAL: do NOT refactor this into multiple `single<AnimeDetailsProvider> { ... }`
 * calls — they share the same Koin key `(AnimeDetailsProvider, null)` and would
 * overwrite each other, leaving only the last-registered provider. The
 * `single<List<AnimeDetailsProvider>>` form preserves both.
 *
 * The [AnimeDetailsProviderRegistry] resolves the right provider by [DataSource]
 * at runtime. Adding a third data source = one new class + one entry in the
 * `listOf(...)` below. Zero changes to the details page.
 *
 * NOTE: [AniListApi] is NOT registered here — it's constructed in
 * [app.confused.anikuta.navigation.AppController] (the Voyager nav controller)
 * and exposed as a property. This module uses `get<AniListApi>()` which resolves
 * to that instance. If a future refactor moves AniListApi into Koin, update this.
 */
val detailsModule: Module = module {
    // ── The two providers, bundled as a single list to avoid Koin key collision ──
    single<List<AnimeDetailsProvider>> {
        listOf(
            AniListDetailsProvider(
                anilistApi = get<AniListApi>(),
                sourceMatcher = get<SourceMatcher>(),
                animeRepository = get<AnimeRepository>(),
                episodeRepository = get<EpisodeRepository>(),
                sourceLinkStore = get<SourceLinkStore>(),
                extensionLinkStore = get<ExtensionLinkStore>(),
                appContext = get(),
            ),
            ExtensionDetailsProvider(
                anilistApi = get<AniListApi>(),
                sourceMatcher = get<SourceMatcher>(),
                animeRepository = get<AnimeRepository>(),
                episodeRepository = get<EpisodeRepository>(),
                sourceLinkStore = get<SourceLinkStore>(),
                extensionLinkStore = get<ExtensionLinkStore>(),
            ),
        )
    }
    single { AnimeDetailsProviderRegistry(get<List<AnimeDetailsProvider>>()) }
}
