package app.confused.anikuta.core.ads.di

import app.confused.anikuta.core.ads.AdManager
import app.confused.anikuta.core.ads.AdTracker
import app.confused.anikuta.core.ads.AdsPreferences
import app.confused.anikuta.core.preferences.PreferenceStore
import org.koin.dsl.module

/**
 * Koin module for the advertising system.
 *
 * Registers:
 * - [AdsPreferences] — user-configurable ad settings (singleton).
 * - [AdTracker] — on-device ad view tracking (singleton).
 * - [AdManager] — the central orchestrator (singleton).
 *
 * All three are singletons because they hold process-wide state (preferences
 * + tracking counters + the ad interaction state machine).
 */
val adsModule = module {
    single { AdsPreferences(get<PreferenceStore>()) }
    single { AdTracker(get<PreferenceStore>()) }
    single { AdManager(get(), get()) }
}
