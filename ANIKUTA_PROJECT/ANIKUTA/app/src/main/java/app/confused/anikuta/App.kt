package app.confused.anikuta

import android.app.Application
import android.util.Log
import app.confused.anikuta.di.databaseModule
import app.confused.anikuta.di.extensionModule
import app.confused.anikuta.di.repositoryModule
import eu.kanade.tachiyomi.animesource.ExtensionAppHolder
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Extension app holder — MUST be set before Koin so the extension loader
        // (instantiated as a Koin singleton) can hand the Application context to
        // ConfigurableAnimeSource extensions for their SharedPreferences.
        // Replaces Injekt.get<Application>() from the Aniyomi reference (ADR-023).
        ExtensionAppHolder.init(this)

        // Koin DI (ADR-023)
        startKoin {
            androidContext(this@App)
            modules(
                databaseModule,
                repositoryModule,
                extensionModule,
            )
        }

        // Logging (ADR-033) — tag-based, filterable via `adb logcat -s AnikutaApp:*`
        Log.i(TAG, "ANIKUTA started — DI wired (incl. extensionModule)")
    }

    companion object {
        private const val TAG = "AnikutaApp"
    }
}
