package app.confused.anikuta.core.appupdate.di

import app.confused.anikuta.core.appupdate.AppUpdateManager
import app.confused.anikuta.core.appupdate.AppUpdatePreferences
import app.confused.anikuta.core.appupdate.GitHubUpdateSource
import app.confused.anikuta.core.appupdate.UpdateSource
import app.confused.anikuta.core.preferences.PreferenceStore
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Koin module for the app self-update system.
 *
 * Registers:
 * - [AppUpdatePreferences] — settings singleton.
 * - [GitHubUpdateSource] — the GitHub-based update source.
 * - `List<UpdateSource>` — all registered sources (priority order). Adding a
 *   new source = one `single<UpdateSource>` binding + add to the list.
 * - [AppUpdateManager] — the orchestrator singleton.
 *
 * # Adding a new update source
 *
 * 1. Implement [UpdateSource] (e.g., `CustomJsonUpdateSource`).
 * 2. Register it here:
 *    ```kotlin
 *    single<UpdateSource>(named("custom")) { CustomJsonUpdateSource(...) }
 *    ```
 * 3. Add it to the `List<UpdateSource>` binding.
 * The [AppUpdateManager] will automatically query it on the next check.
 */
val appUpdateModule = module {
    single { AppUpdatePreferences(get<PreferenceStore>()) }

    // GitHub update source — configured for the beta repo.
    single<UpdateSource>(named("github")) {
        GitHubUpdateSource(
            owner = "Confused-Creature-180",
            repo = "APP_BETA",
            client = get(named("appUpdate")),
        )
    }

    // Dedicated OkHttp client for update checks/downloads (separate from extension network).
    single(named("appUpdate")) {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // All registered update sources (priority order — first non-null wins).
    single<List<UpdateSource>> {
        listOf(get<UpdateSource>(named("github")))
    }

    single {
        AppUpdateManager(
            context = get(),
            preferences = get(),
            sources = get<List<UpdateSource>>(),
        )
    }
}
