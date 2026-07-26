package app.confused.anikuta.feature.backup.di

import app.confused.anikuta.feature.backup.aniyomi.AniyomiRestoreViewModel
import org.koin.core.module.Module
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the Aniyomi restore flow ViewModel.
 *
 * Must be added to `modules(...)` in `App.kt` alongside the other backup modules.
 */
val aniyomiRestoreModule: Module = module {
    viewModel { AniyomiRestoreViewModel(get(), get(), get()) }
}
