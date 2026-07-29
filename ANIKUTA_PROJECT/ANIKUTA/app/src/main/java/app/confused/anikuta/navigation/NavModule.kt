package app.confused.anikuta.navigation

import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.api.AniListRateLimiter
import app.confused.anikuta.core.anilist.api.LocalAniListCache
import app.confused.anikuta.core.preferences.PreferenceStore
import app.confused.anikuta.core.preferences.ThemePreferences
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the Voyager navigation layer.
 *
 * Registers:
 * - [AniListApi] — the full-featured instance (with persistent cache +
 *   rate limiter). This supersedes the no-arg `AniListApi()` previously
 *   registered in `updateCheckerModule`. Having a single shared instance
 *   is correct (the in-memory caches are process-wide) and ensures all
 *   consumers (Browse, Search, Details, Updates, UpdateChecker) share the
 *   same persistent cache + rate limiter.
 * - [AppController] — the central state holder + business-logic coordinator
 *   for the app shell. Injected into Voyager `Screen` classes + `AnikutaRoot`.
 *
 * **Note on AniListApi consolidation:** Previously, `updateCheckerModule`
 * registered `AniListApi()` (no cache/rate-limiter) while `MainActivity`
 * created a separate `AniListApi(localCache, rateLimiter)` instance. This
 * migration consolidates to a single full-featured instance registered here.
 * The `updateCheckerModule` no longer registers `AniListApi` — it resolves
 * it from here via `get<AniListApi>()`.
 */
val navModule: Module = module {
    // AniListApi with persistent cache + rate limiter (shared app-wide).
    single {
        val prefStore = get<PreferenceStore>()
        AniListApi(
            localCache = LocalAniListCache(prefStore),
            rateLimiter = AniListRateLimiter(),
        )
    }

    // AppController — the central state holder + business logic coordinator.
    single {
        AppController(
            resolverService = get(),
            downloadManager = get(),
            downloadOrchestrator = get(),
            trackerManager = get(),
            anilistApi = get(),
            extensionManager = get(),
            sourceMatcher = get(),
            extensionLinkStore = get(),
            sourceLinkStore = get(),
            recentsStore = get(),
            searchUiPreferences = get(),
            repoRepository = get(),
            repoApi = get(),
            serverDiscoveryStore = get(),
            themePrefs = get<ThemePreferences>(),
            linkingPreferences = get(),
            context = get(),
        )
    }
}
