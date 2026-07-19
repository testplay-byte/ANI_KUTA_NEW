package app.confused.anikuta.core.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Creates the [SqlDriver] for ANIKUTA's database on Android.
 *
 * The database file is `anikuta.db` (anime-side). A separate manga database
 * will be added when manga is implemented (ADR-009).
 *
 * Logging (ADR-033): logs driver creation with tag [TAG].
 */
class DatabaseDriverFactory(private val context: Context) {
    fun create(): SqlDriver {
        android.util.Log.i(TAG, "Creating AnikutaDatabase driver")
        return AndroidSqliteDriver(
            schema = AnikutaDatabase.Schema,
            context = context,
            name = "anikuta.db",
        )
    }

    companion object {
        private const val TAG = "AnikutaDb"
    }
}
