package app.confused.anikuta

import android.app.Application
import android.util.Log
import app.confused.anikuta.di.databaseModule
import app.confused.anikuta.di.repositoryModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Koin DI (ADR-023)
        startKoin {
            androidContext(this@App)
            modules(
                databaseModule,
                repositoryModule,
            )
        }

        // Logging (ADR-033) — tag-based, filterable via `adb logcat -s AnikutaApp:*`
        Log.i(TAG, "ANIKUTA started — DI wired")
    }

    companion object {
        private const val TAG = "AnikutaApp"
    }
}
