package app.confused.anikuta.feature.backup.di

import app.confused.anikuta.feature.backup.BackupViewModel
import org.koin.core.module.Module
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the backup feature UI (`:feature:backup`).
 *
 * Registers [BackupViewModel] — the ViewModel backing [BackupSettingsScreen].
 *
 * Must be added to `modules(...)` in `App.kt`'s `startKoin` alongside
 * [backupModule] (from `:core:backup`).
 */
val backupFeatureModule: Module = module {
    viewModel {
        BackupViewModel(
            backupManager = get(),
            backupStorage = get(),
            backupPreferences = get(),
            autoBackupScheduler = get(),
        )
    }
}
