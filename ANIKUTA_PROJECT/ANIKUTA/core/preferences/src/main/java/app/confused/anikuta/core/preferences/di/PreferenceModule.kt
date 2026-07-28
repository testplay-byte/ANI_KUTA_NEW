package app.confused.anikuta.core.preferences.di

import app.confused.anikuta.core.preferences.AndroidPreferenceStore
import app.confused.anikuta.core.preferences.AndroidProviderPreferences
import app.confused.anikuta.core.preferences.ContentIdPreferences
import app.confused.anikuta.core.preferences.PreferenceStore
import app.confused.anikuta.core.preferences.ThemePreferences
import app.confused.anikuta.core.providerapi.ProviderPreferences
import org.koin.dsl.module

val preferenceModule = module {
    single<PreferenceStore> { AndroidPreferenceStore(get()) }
    single { ThemePreferences(get()) }
    single { ContentIdPreferences(get()) }
    single<ProviderPreferences> { AndroidProviderPreferences(get()) }
}
