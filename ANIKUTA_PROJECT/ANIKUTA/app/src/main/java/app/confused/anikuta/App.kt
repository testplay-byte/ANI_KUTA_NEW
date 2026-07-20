package app.confused.anikuta

import android.app.Application
import android.util.Log
import app.confused.anikuta.di.databaseModule
import app.confused.anikuta.di.extensionModule
import app.confused.anikuta.di.repositoryModule
import eu.kanade.tachiyomi.animesource.ExtensionAppHolder
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.fullType

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Extension app holder — MUST be set before Koin so the extension loader
        // can hand the Application context to ConfigurableAnimeSource extensions.
        ExtensionAppHolder.init(this)

        // Injekt — register the Application instance so Keiyoushi-family extensions
        // that call Injekt.get<Application>() can resolve it.
        // This is required because we bundle injekt for extension compat (ADR-029)
        // but use Koin as our own DI (ADR-023). Extensions that use Injekt expect
        // the Application to be registered there.
        try {
            Injekt.addSingleton(fullType<Application>(), this)
            Log.i(TAG, "Injekt: Application instance registered")
        } catch (e: Exception) {
            Log.w(TAG, "Injekt: failed to register Application (may already be registered)", e)
        }

        // Koin DI (ADR-023)
        startKoin {
            androidContext(this@App)
            modules(
                databaseModule,
                repositoryModule,
                extensionModule,
            )
        }

        Log.i(TAG, "ANIKUTA started — DI wired (Koin + Injekt for extensions)")
    }

    companion object {
        private const val TAG = "AnikutaApp"
    }
}
