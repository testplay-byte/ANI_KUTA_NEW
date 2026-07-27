package app.confused.anikuta.feature.download.di

import app.confused.anikuta.feature.download.DownloadViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module for the Downloads feature.
 *
 * Registers [DownloadViewModel]. The data source (`DownloadManager`) is
 * provided by `downloadModule` (in `:core:download`), so this module only
 * needs the ViewModel.
 *
 * Added to `App.kt`'s `startKoin { modules(...) }` list.
 */
val downloadFeatureModule: Module = module {
    viewModelOf(::DownloadViewModel)
}
