package app.confused.anikuta.core.download.di

import android.content.Context
import app.confused.anikuta.core.download.DefaultDownloadManager
import app.confused.anikuta.core.download.DownloadManager
import app.confused.anikuta.core.download.DownloadPreferences
import app.confused.anikuta.core.download.DownloadStorageProvider
import app.confused.anikuta.core.download.DownloadStore
import app.confused.anikuta.core.download.HttpDownloader
import app.confused.anikuta.core.download.ServerDiscoveryStore
import app.confused.anikuta.core.download.TempDownloadCache
import app.confused.anikuta.core.download.advanced.AdvancedHttpDownloader
import app.confused.anikuta.core.download.advanced.DownloadResumeManager
import app.confused.anikuta.core.preferences.PreferenceStore
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Koin DI module for the download engine (`:core:download`).
 *
 * Registers:
 *  - [DownloadPreferences] — backed by the shared [PreferenceStore].
 *  - [DownloadStore] — backed by the SAME [PreferenceStore] (so the queue's
 *    persisted state lives alongside other prefs).
 *  - [TempDownloadCache] — the internal-cache manager for partial downloads
 *    (v2 pipeline: download → validate → publish to SAF). Cleans up stale
 *    temp dirs from a previous crash on first resolution.
 *  - A download-dedicated [OkHttpClient] (qualifier `"download"`) — long
 *    timeouts for large files, separate from the extension NetworkHelper
 *    client so a stuck download can't starve extension HTTP calls.
 *  - [DownloadManager] → [DefaultDownloadManager] (single binding; swapping to
 *    a future `OneDmDownloadManager` is a one-line change here, gated on the
 *    `DownloadPreferences.method()` pref — ADR-020 future-proofing).
 *
 * Added to `App.kt`'s `startKoin { modules(...) }` list.
 *
 * DOWNLOAD-IDENTITY-STORAGE-UPDATE: [DownloadStorageProvider] is NO LONGER
 * registered here — it moved to `:app`'s `DownloadAppModule` so it can be
 * wired up with the `DownloadIdentityManager` (which itself needs the
 * provider via an `animeBaseDir` lambda — the two are mutually dependent at
 * construction-time, so they live in the same module).
 * `DefaultDownloadManager`'s constructor now takes `storage: DownloadStorageProvider`
 * via `get()` — Koin resolves it from the `:app` module at runtime (both
 * modules are loaded together in `App.kt`'s `startKoin`).
 */
val downloadModule: Module = module {
    single { DownloadPreferences(get<PreferenceStore>()) }
    single { DownloadStore(get<PreferenceStore>()) }
    single { ServerDiscoveryStore(get<PreferenceStore>()) }

    // TempDownloadCache — clean up stale dirs from a previous crash on creation.
    single { TempDownloadCache(get<Context>()).also { it.cleanupStale() } }

    // Advanced downloader dependencies
    single { DownloadResumeManager(get()) }
    single { AdvancedHttpDownloader(get(named("download")), get(), get(), get()) }

    single(named("download")) {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    single<DownloadManager> {
        DefaultDownloadManager(
            context = get<Context>(),
            okHttp = get(named("download")),
            preferences = get(),
            store = get(),
            tempCache = get(),
            advancedDownloader = get(),
            resumeManager = get(),
            // Resolved from :app's DownloadAppModule (which owns the
            // DownloadStorageProvider single + injects the DownloadIdentityManager).
            storage = get<DownloadStorageProvider>(),
        )
    }
}
