package app.confused.anikuta

import android.app.Application
import android.content.Context
import android.util.Log
import app.confused.anikuta.di.databaseModule
import app.confused.anikuta.di.extensionModule
import app.confused.anikuta.di.repositoryModule
import app.confused.anikuta.feature.library.di.libraryModule
import app.confused.anikuta.core.common.repository.CategoryRepository
import app.confused.anikuta.di.searchModule
import app.confused.anikuta.core.preferences.di.preferenceModule
import app.confused.anikuta.core.player.di.playerModule
import eu.kanade.tachiyomi.animesource.ExtensionAppHolder
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // ── Crash handler (install FIRST, before anything that might throw) ──
        // This ensures that if DI setup or extension loading crashes, the user
        // gets the ErrorActivity screen instead of a silent crash.
        Thread.setDefaultUncaughtExceptionHandler(
            app.confused.anikuta.error.AnikutaCrashHandler(this),
        )

        // Extension app holder — MUST be set before Koin so the extension loader
        // can hand the Application context to ConfigurableAnimeSource extensions.
        ExtensionAppHolder.init(this)

        // ── Injekt singletons (for extension compat — ADR-029) ──
        // We use Koin for our own DI (ADR-023), but extensions call
        // Injekt.get<T>() for several host-provided singletons. These MUST be
        // registered in Injekt before any extension source is loaded.
        try {
            // Application — Keiyoushi extensions call Injekt.get<Application>().
            Injekt.addSingleton(fullType<Application>(), this)
            Injekt.addSingleton(fullType<Context>(), this)

            // NetworkHelper — AnimeHttpSource resolves it via `by injectLazy()`.
            // CRITICAL: NetworkHelper MUST be a class (not interface) — otherwise
            // extension bytecode throws IncompatibleClassChangeError on .client access.
            val networkHelper = NetworkHelper(this)
            Injekt.addSingleton(fullType<NetworkHelper>(), networkHelper)
            Log.i(TAG, "Injekt: Application + Context + NetworkHelper registered")

            // Json — Keiyoushi extensions call Injekt.get<Json>() in static
            // initializers (e.g. for preference serializers). Without this,
            // any extension that uses JSON parsing crashes with
            // ExceptionInInitializerError → InjektionException.
            Injekt.addSingletonFactory(fullType<Json>()) {
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            }
            Log.i(TAG, "Injekt: Json registered")
        } catch (e: Exception) {
            Log.w(TAG, "Injekt: failed to register one or more singletons", e)
        }

        // Koin DI (ADR-023)
        startKoin {
            androidContext(this@App)
            modules(
                databaseModule,
                repositoryModule,
                extensionModule,
                searchModule,
                preferenceModule,
                playerModule,
                libraryModule,
            )
        }

        // Ensure the Default category exists (Phase A — library page).
        // The .sq file seeds it on fresh installs and the 1.sqm migration seeds
        // it on existing installs; this is a safety net called on every startup.
        try {
            val categoryRepo = GlobalContext.get().get<CategoryRepository>()
            CoroutineScope(Dispatchers.IO).launch {
                categoryRepo.ensureDefaultExists()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to ensure Default category exists", e)
        }

        Log.i(TAG, "ANIKUTA started — DI wired (Koin + Injekt for extensions)")
    }

    companion object {
        private const val TAG = "AnikutaApp"
    }
}
