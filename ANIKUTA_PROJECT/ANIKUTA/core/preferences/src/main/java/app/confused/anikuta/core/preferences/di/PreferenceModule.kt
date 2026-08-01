package app.confused.anikuta.core.preferences.di

import app.confused.anikuta.core.preferences.AndroidPreferenceStore
import app.confused.anikuta.core.preferences.AndroidProviderPreferences
import app.confused.anikuta.core.preferences.ContentIdPreferences
import app.confused.anikuta.core.preferences.DetailsViewPreferences
import app.confused.anikuta.core.preferences.EpisodeDisplayPreferences
import app.confused.anikuta.core.preferences.LinkingPreferences
import app.confused.anikuta.core.preferences.PreferenceStore
import app.confused.anikuta.core.preferences.SetupWizardPreferences
import app.confused.anikuta.core.preferences.ThemePreferences
import app.confused.anikuta.core.providerapi.ProviderPreferences
import org.koin.dsl.module

val preferenceModule = module {
    single<PreferenceStore> { AndroidPreferenceStore(get()) }
    single { ThemePreferences(get()) }
    single { ContentIdPreferences(get()) }
    single { LinkingPreferences(get()) }
    single { DetailsViewPreferences(get()) }
    single<ProviderPreferences> { AndroidProviderPreferences(get()) }

    // ── Phase 8 (Doc 04 violation 2): EpisodeDisplayPreferences ──
    // Moved here from app/.../di/RepositoryModule.kt. Lives next to the class
    // (now in :core:preferences) so :feature:anime-details +
    // :feature:episode-settings can koinInject it without a feature→feature dep.
    single { EpisodeDisplayPreferences(get()) }

    // ── Setup Wizard gate ──
    // Tracks whether the user has completed the first-launch Setup Wizard.
    // :app reads this on startup to decide whether to show the wizard or the
    // main UI. Lives here so :app doesn't need a feature→feature dep.
    single { SetupWizardPreferences(get()) }
}
