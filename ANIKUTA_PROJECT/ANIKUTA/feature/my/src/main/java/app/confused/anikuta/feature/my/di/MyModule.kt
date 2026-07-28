package app.confused.anikuta.feature.my.di

import app.confused.anikuta.core.preferences.PreferenceStore
import app.confused.anikuta.feature.my.ProfilePreferences
import app.confused.anikuta.feature.my.ProfileViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module for the My Profile feature.
 *
 * Registers [ProfilePreferences] and [ProfileViewModel].
 * Must be added to the `modules(...)` list in `App.kt`'s `startKoin`.
 */
val myModule: Module = module {
    single { ProfilePreferences(get<PreferenceStore>()) }
    viewModelOf(::ProfileViewModel)
}
