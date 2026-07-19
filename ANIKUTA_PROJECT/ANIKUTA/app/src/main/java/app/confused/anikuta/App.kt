package app.confused.anikuta

import android.app.Application
import android.util.Log
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Koin DI (ADR-023)
        startKoin {
            androidContext(this@App)
            // modules will be added here as features are implemented
        }

        // Logging (ADR-033) — tag-based, filterable via `adb logcat -s AnikutaApp:*`
        Log.i("AnikutaApp", "ANIKUTA started")
    }
}
