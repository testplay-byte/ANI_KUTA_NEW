package app.confused.anikuta.feature.updates.di

import app.confused.anikuta.feature.updates.UpdatesViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module for the Updates feature.
 *
 * Registers [UpdatesViewModel]. Its dependencies — [UpdateChecker]
 * (`:core:update-checker`) and `AniListApi` (`:core:anilist`) — are provided
 * by `updateCheckerModule` (registered in `:core:update-checker`) and the
 * `AnimeRepository` binding (in `repositoryModule`). So this module only needs
 * the ViewModel.
 *
 * Added to `App.kt`'s `startKoin { modules(...) }` list (Agent 1).
 */
val updatesModule: Module = module {
    viewModelOf(::UpdatesViewModel)
}
