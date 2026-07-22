package app.confused.anikuta.di

import app.confused.anikuta.feature.search.data.RecentSearchesStore
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the Search feature's shared stores.
 *
 * `RecentSearchesStore` is feature-specific (lives in `:feature:search`) but is
 * registered here in `:app` (per the project's DI convention — feature modules
 * don't self-register Koin; `:app` wires everything in `App.kt`'s `startKoin`).
 *
 * `ExtensionLinkStore` is registered in [extensionModule] (it lives in
 * `:data:extension` and is shared with the future extension-only detail page).
 */
val searchModule: Module = module {
    single { RecentSearchesStore(get()) }
}
