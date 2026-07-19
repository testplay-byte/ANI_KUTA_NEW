package app.anikuta.data

import android.content.Context
import android.util.Log
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import dataanime.Animehistory
import dataanime.Animes

/**
 * Factory for creating the AnimeDatabase + its SqlDriver.
 *
 * Phase 7: [createWithDriver] returns BOTH the driver and the database so
 * they can be registered as separate singletons in Injekt. This is needed
 * because [AndroidAnimeDatabaseHandler] requires both the AnimeDatabase
 * (for queries) AND the SqlDriver (for transaction checks).
 */
object AnimeDatabaseFactory {
    private const val TAG = "AnimeDBFactory"

    fun create(context: Context): AnimeDatabase {
        return createWithDriver(context).second
    }

    /**
     * Create the driver + database together. Returns (driver, database) so
     * both can be registered in Injekt.
     */
    fun createWithDriver(context: Context): Pair<SqlDriver, AnimeDatabase> {
        Log.d(TAG, "Creating AnimeDatabase + driver...")
        try {
            val driver = AndroidSqliteDriver(
                schema = AnimeDatabase.Schema,
                context = context,
                name = "tachiyomi.animedb",
            )
            Log.d(TAG, "Driver created. Schema version: ${AnimeDatabase.Schema.version}")

            val db = AnimeDatabase(
                driver = driver,
                animehistoryAdapter = Animehistory.Adapter(
                    last_seenAdapter = DateColumnAdapter,
                ),
                animesAdapter = Animes.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                    fetch_typeAdapter = FetchTypeColumnAdapter,
                ),
            )
            Log.d(TAG, "✅ AnimeDatabase + driver created successfully")
            return Pair(driver, db)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create AnimeDatabase", e)
            throw e
        }
    }
}
