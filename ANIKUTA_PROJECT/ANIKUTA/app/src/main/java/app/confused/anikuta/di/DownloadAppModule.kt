package app.confused.anikuta.di

import app.confused.anikuta.core.download.di.downloadModule
import app.confused.anikuta.download.DownloadOrchestrator
import app.confused.anikuta.feature.download.di.downloadFeatureModule
import app.confused.anikuta.feature.videoresolver.ResolverService
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * App-level DI wiring for the Downloads subsystem.
 *
 * Aggregates:
 *  - [downloadModule] (`:core:download`) — DownloadManager, DownloadPreferences,
 *    DownloadStore, the download OkHttp client.
 *  - [downloadFeatureModule] (`:feature:download`) — DownloadViewModel.
 *  - [DownloadOrchestrator] (`:app`) — bridges `:feature:video-resolver` +
 *    `:core:download` (lives in `:app` because `:core:download` can't import
 *    `:feature:video-resolver` — Rule §14 feature isolation).
 *
 * Added to `App.kt`'s `startKoin { modules(...) }` list as `downloadAppModule`.
 */
val downloadAppModule: Module = module {
    // Re-export the core + feature modules so App.kt only lists one entry.
    includes(downloadModule, downloadFeatureModule)

    single { ResolverService() }
    single { DownloadOrchestrator(get(), get(), get(), get()) }
}
