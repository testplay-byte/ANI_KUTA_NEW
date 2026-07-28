package app.confused.anikuta.feature.trackers.di

import app.confused.anikuta.feature.trackers.TrackersViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module for the Trackers settings feature.
 *
 * Registers [TrackersViewModel].
 * Must be added to the `modules(...)` list in `App.kt`'s `startKoin`.
 */
val trackersModule: Module = module {
    viewModelOf(::TrackersViewModel)
}
