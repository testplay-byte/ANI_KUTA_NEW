package app.confused.anikuta.core.updatechecker.di

import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.preferences.PreferenceStore
import app.confused.anikuta.core.updatechecker.EpisodeFetchGateway
import app.confused.anikuta.core.updatechecker.UpdateChecker
import app.confused.anikuta.core.updatechecker.UpdateCheckerPreferences
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the `:core:update-checker` module.
 *
 * Registers:
 *  - [UpdateCheckerPreferences] — PreferenceStore-backed prefs (singleton).
 *  - [UpdateChecker] — the check orchestrator (singleton, so its internal
 *    StateFlow survives config changes).
 *
 * [EpisodeFetchGateway] is NOT registered here — its implementation lives in
 * `:data:extension` (`EpisodeFetchGatewayImpl`) and is bound there in
 * `extensionModule`. This split keeps `:core:update-checker` free of any
 * `:data:*` dependency (ARCHITECTURE.md §3) while still allowing Koin to
 * resolve the gateway at runtime.
 *
 * [AniListApi] is constructed in `:feature:browse` / `MainActivity` today
 * (`remember { AniListApi() }`). For the Updates page to inject it here, we
 * register a singleton instance — the in-memory caches (detailCache,
 * listCache) are process-wide anyway, so a single shared instance is correct
 * and avoids duplicate caches.
 */
val updateCheckerModule: Module = module {
    single { UpdateCheckerPreferences(get<PreferenceStore>()) }
    single { AniListApi() }
    single {
        UpdateChecker(
            animeRepository = get<AnimeRepository>(),
            anilistApi = get<AniListApi>(),
            episodeFetchGateway = get<EpisodeFetchGateway>(),
            preferences = get<UpdateCheckerPreferences>(),
        )
    }
}
