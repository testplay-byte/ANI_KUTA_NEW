package eu.kanade.tachiyomi.animesource

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

/**
 * ConfigurableAnimeSource — interface for sources that have a preference screen.
 *
 * NOTE: The original Aniyomi version uses Injekt for DI. We replaced Injekt with
 * a static application holder since we use Koin (ADR-023). Extensions call these
 * functions to get their SharedPreferences — the Application instance is provided
 * by [ExtensionAppHolder] which is set during app startup.
 */
interface ConfigurableAnimeSource : AnimeSource {

    /**
     * Gets instance of [SharedPreferences] scoped to the specific source.
     *
     * @since extensions-lib 1.5
     */
    fun getSourcePreferences(): SharedPreferences =
        ExtensionAppHolder.app.getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)

    fun setupPreferenceScreen(screen: PreferenceScreen)
}

fun ConfigurableAnimeSource.preferenceKey(): String = "source_$id"

// TODO: use getSourcePreferences once all extensions are on ext-lib 1.5
fun ConfigurableAnimeSource.sourcePreferences(): SharedPreferences =
    ExtensionAppHolder.app.getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)

fun sourcePreferences(key: String): SharedPreferences =
    ExtensionAppHolder.app.getSharedPreferences(key, Context.MODE_PRIVATE)

/**
 * Static holder for the Application instance.
 *
 * This replaces Injekt.get<Application>() in the original Aniyomi code.
 * Extensions call [ExtensionAppHolder.app] to access the Application context
 * for SharedPreferences. The app sets this during startup.
 *
 * This MUST be set before any extension is loaded.
 */
object ExtensionAppHolder {
    lateinit var app: Application
        private set

    fun init(application: Application) {
        app = application
    }
}
