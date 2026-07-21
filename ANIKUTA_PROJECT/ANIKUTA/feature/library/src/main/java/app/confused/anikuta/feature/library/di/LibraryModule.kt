package app.confused.anikuta.feature.library.di

import app.confused.anikuta.core.preferences.PreferenceStore
import app.confused.anikuta.feature.library.LibraryPreferences
import app.confused.anikuta.feature.library.LibraryViewModel
import org.koin.core.module.Module
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module for the library feature.
 *
 * Registers:
 *  - [LibraryPreferences] — singleton wrapping [PreferenceStore].
 *  - [LibraryViewModel] — injected via `koinViewModel()` in [LibraryScreen].
 *
 * Must be added to the `modules(...)` list in `App.kt`'s `startKoin`.
 */
val libraryModule: Module = module {
    single { LibraryPreferences(get<PreferenceStore>()) }
    viewModelOf(::LibraryViewModel)
}
