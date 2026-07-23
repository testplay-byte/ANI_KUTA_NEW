package app.confused.anikuta.feature.history.di

import app.confused.anikuta.feature.history.HistoryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module for the History feature.
 *
 * Registers [HistoryViewModel]. The data source (`WatchProgressStore`) is
 * already provided by `playerModule` (in `:core:player`), so this module only
 * needs the ViewModel.
 *
 * Added to `App.kt`'s `startKoin { modules(...) }` list (Agent 1).
 */
val historyModule: Module = module {
    viewModelOf(::HistoryViewModel)
}
