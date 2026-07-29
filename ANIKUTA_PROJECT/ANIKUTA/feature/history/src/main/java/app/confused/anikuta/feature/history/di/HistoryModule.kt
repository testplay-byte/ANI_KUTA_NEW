package app.confused.anikuta.feature.history.di

import app.confused.anikuta.feature.history.HistoryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module for the History feature.
 *
 * Registers [HistoryViewModel]. The constructor deps (`WatchProgressStore` +
 * `AnimeRepository`) are auto-resolved by Koin — `WatchProgressStore` is
 * provided by `playerModule` (in `:core:player`), `AnimeRepository` by
 * `repositoryModule` (in `:app`). The `AnimeRepository` is used to resolve
 * content_id keys back to [app.confused.anikuta.core.common.model.Anime] for
 * row-tap navigation (Phase 3, ADR-050).
 *
 * Added to `App.kt`'s `startKoin { modules(...) }` list (Agent 1).
 */
val historyModule: Module = module {
    viewModelOf(::HistoryViewModel)
}
