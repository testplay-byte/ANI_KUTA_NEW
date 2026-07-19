package app.confused.anikuta.di

import app.confused.anikuta.core.database.AnikutaDatabase
import app.confused.anikuta.core.database.DatabaseDriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the database layer.
 *
 * Provides:
 * - [DatabaseDriverFactory] — creates the Android SQLite driver.
 * - [AnikutaDatabase] — the SQLDelight database instance (ADR-024).
 */
val databaseModule: Module = module {
    single { DatabaseDriverFactory(get()) }
    single { AnikutaDatabase(get<DatabaseDriverFactory>().create()) }
}
