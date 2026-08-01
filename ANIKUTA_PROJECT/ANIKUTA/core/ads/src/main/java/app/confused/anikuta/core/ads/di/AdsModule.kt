package app.confused.anikuta.core.ads.di

import app.confused.anikuta.core.ads.AdsPreferences
import org.koin.dsl.module

/**
 * Koin module for `:core:ads`.
 *
 * Binds [AdsPreferences] as a singleton — it wraps a [PreferenceStore] which is
 * itself a singleton, so multiple instances would just be wasteful.
 */
val adsModule = module {
    single { AdsPreferences(get()) }
}
