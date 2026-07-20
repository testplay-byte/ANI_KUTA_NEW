package app.confused.anikuta.di

import android.content.Context
import app.confused.anikuta.data.extension.AnimeExtensionManager
import app.confused.anikuta.data.extension.api.AnimeExtensionApi
import app.confused.anikuta.data.extension.installer.AnimeExtensionInstaller
import app.confused.anikuta.data.extension.loader.AnimeExtensionLoader
import app.confused.anikuta.data.extension.repo.ExtensionRepoApi
import app.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import app.confused.anikuta.data.extension.repo.defaultRepoOkHttpClient
import app.confused.anikuta.data.extension.trust.TrustExtension
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module for the anime extension system (Phase 4B).
 *
 * Wires the full `:data:extension` dependency graph:
 *
 * ```
 * TrustExtension ──┐
 *                  ├─→ AnimeExtensionLoader ──┐
 * ExtensionRepoApi ┤                          ├─→ AnimeExtensionManager ──→ AnimeExtensionApi
 * ExtensionRepoRepository ──────→ AnimeExtensionApi                  ↑
 * OkHttpClient (repo) ───────────┘                                     │
 * AnimeExtensionInstaller ───────────────────────────────────────────────┘
 * ```
 *
 * All bindings are singletons (the manager owns a `CoroutineScope(SupervisorJob())`
 * so it must be a singleton to avoid leaking scopes). Registered in `App.kt`'s
 * `startKoin { }` block (ADR-023).
 */
val extensionModule: Module = module {

    // ── HTTP + JSON for repo index fetching ──
    single<OkHttpClient>(named("extensionRepo")) { defaultRepoOkHttpClient() }
    single<Json>(named("extensionJson")) { Json { ignoreUnknownKeys = true } }

    // ── Trust ──
    single { TrustExtension(get<Context>()) }

    // ── Loader ──
    single { AnimeExtensionLoader(get()) }

    // ── Repo layer ──
    single { ExtensionRepoRepository(get<Context>()) }
    single { ExtensionRepoApi(get(named("extensionRepo")), get(named("extensionJson"))) }

    // ── API (orchestrator over repos) ──
    single { AnimeExtensionApi(get(), get()) }

    // ── Installer (needs its own OkHttpClient for APK downloads) ──
    single { AnimeExtensionInstaller(get<Context>(), get(named("extensionRepo"))) }

    // ── Manager (the public façade — depends on loader, trust, api, installer) ──
    single { AnimeExtensionManager(get(), get(), get(), get(), get()) }
}
