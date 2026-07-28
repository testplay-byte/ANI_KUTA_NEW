package app.confused.anikuta.core.anilist.di

import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.details.AniListMetadataProvider
import app.confused.anikuta.core.providerapi.MetadataProvider
import app.confused.anikuta.core.providerapi.MetadataProviderRegistry
import app.confused.anikuta.core.providerapi.ProviderPreferences
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the metadata-provider abstraction (ADR-041).
 *
 * Registers:
 * - [AniListMetadataProvider] as the (currently sole) [MetadataProvider].
 * - [MetadataProviderRegistry] resolved via `List<MetadataProvider>` multi-binding
 *   (same pattern as `List<AnimeDetailsProvider>` in `DetailsModule.kt`).
 *
 * # Adding a new provider (e.g., MAL)
 *
 * 1. Create `:data:provider-mal` module.
 * 2. Implement `MALMetadataProvider : MetadataProvider, SearchProvider, …`.
 * 3. Register here:
 *    ```
 *    single<List<MetadataProvider>> {
 *        listOf(get<AniListMetadataProvider>(), get<MALMetadataProvider>())
 *    }
 *    ```
 * 4. Done — the registry auto-discovers it; the user can select MAL in Settings.
 *
 * CRITICAL: do NOT refactor into multiple `single<MetadataProvider> { ... }` calls —
 * they share the same Koin key and would overwrite each other. The
 * `single<List<MetadataProvider>>` form preserves all providers (same fix as
 * `DetailsModule.kt` + `BackupModule.kt`).
 *
 * NOTE: [AniListApi] is registered as a Koin `single` in `navModule.kt` (with its
 * [app.confused.anikuta.core.anilist.api.AniListRateLimiter] + cache). This module
 * uses `get<AniListApi>()` which resolves to that instance.
 */
val providerApiModule: Module = module {
    single { AniListMetadataProvider(get<AniListApi>()) }

    single<List<MetadataProvider>> {
        listOf(get<AniListMetadataProvider>())
    }

    single {
        MetadataProviderRegistry(
            providers = get<List<MetadataProvider>>(),
            preferences = get<ProviderPreferences>(),
        )
    }
}
