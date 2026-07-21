package app.confused.anikuta.core.preferences.di

import app.confused.anikuta.core.preferences.AndroidPreferenceStore
import app.confused.anikuta.core.preferences.PreferenceStore
import org.koin.dsl.module

val preferenceModule = module {
    single<PreferenceStore> { AndroidPreferenceStore(get()) }
}
