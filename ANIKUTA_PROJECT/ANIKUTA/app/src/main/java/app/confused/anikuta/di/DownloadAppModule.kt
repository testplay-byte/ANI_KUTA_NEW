package app.confused.anikuta.di

import android.content.Context
import app.confused.anikuta.core.download.DownloadPreferences
import app.confused.anikuta.core.download.DownloadStorageProvider
import app.confused.anikuta.core.download.DownloadStore
import app.confused.anikuta.core.download.di.downloadModule
import app.confused.anikuta.core.downloadidentity.DownloadIdentityManager
import app.confused.anikuta.download.DownloadOrchestrator
import app.confused.anikuta.feature.download.di.downloadFeatureModule
import app.confused.anikuta.core.videoresolver.ResolverService
import app.confused.anikuta.migration.DownloadMigration
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
 *  - [DownloadMigration] (`:app`) — Phase 6 one-shot migrator (re-keys
 *    DownloadStore tasks + moves on-disk folders).
 *
 *  ── DOWNLOAD-IDENTITY-STORAGE-UPDATE ──
 *  This module now ALSO owns two new singletons that decouple download folder
 *  names from anime identity:
 *
 *  - **[DownloadStorageProvider]** — registered here (previously constructed
 *    inline inside `DefaultDownloadManager`) so that both
 *    `DefaultDownloadManager` (`:core:download`) and `DownloadMigration`
 *    (`:app`) share ONE instance. The constructor takes the
 *    [DownloadIdentityManager] (nullable — null preserves legacy behavior for
 *    tests / callers that don't wire it up).
 *
 *  - **[DownloadIdentityManager]** — high-level manager for per-folder
 *    `identity.json`. Its `animeBaseDir` lambda defers the
 *    `DownloadStorageProvider` lookup so the two `single` definitions can
 *    co-exist without a construction-time cycle (the manager doesn't need
 *    the provider until `findAnimeDir` is first called — by which time both
 *    are already constructed).
 *
 * Added to `App.kt`'s `startKoin { modules(...) }` list as `downloadAppModule`.
 */
val downloadAppModule: Module = module {
    // Re-export the core + feature modules so App.kt only lists one entry.
    includes(downloadModule, downloadFeatureModule)

    // ── DownloadIdentityManager ──
    // High-level manager for per-folder identity.json. The `animeBaseDir`
    // lambda is invoked lazily (on first findAnimeDir/findAllIdentities call),
    // which breaks the construction-time cycle with DownloadStorageProvider
    // (each single references the other, but only via lambdas resolved at
    // call-time, not at construction-time).
    single {
        DownloadIdentityManager(
            animeBaseDir = {
                // Resolve DownloadStorageProvider lazily — by the time this
                // lambda runs, Koin has finished constructing both singletons.
                val storage = get<DownloadStorageProvider>()
                storage.getAnimeBaseDir()
            },
        )
    }

    // ── DownloadStorageProvider ──
    // Registered here (was previously constructed inline inside
    // DefaultDownloadManager) so that:
    //   1. DownloadMigration can `get<DownloadStorageProvider>()` (it couldn't
    //      before — the previous `get<>()` would have thrown at runtime).
    //   2. The DownloadIdentityManager + DownloadStorageProvider share ONE
    //      underlying SAF tree (no duplicate state).
    //   3. The new downloadIdentityManager constructor param can be wired in
    //      exactly one place (here).
    single {
        DownloadStorageProvider(
            context = get<Context>(),
            preferences = get<DownloadPreferences>(),
            downloadIdentityManager = get<DownloadIdentityManager>(),
        )
    }

    single { ResolverService() }
    single { DownloadOrchestrator(get(), get(), get(), get()) }

    // Phase 6: download migration (anilistId → content_id)
    single {
        DownloadMigration(
            downloadStore = get<DownloadStore>(),
            storageProvider = get<DownloadStorageProvider>(),
        )
    }
}
