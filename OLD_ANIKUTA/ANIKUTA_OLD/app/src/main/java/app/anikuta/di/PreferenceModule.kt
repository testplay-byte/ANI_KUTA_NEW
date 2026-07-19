package app.anikuta.di

import app.anikuta.core.preference.PreferenceStore
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory

/**
 * Minimal PreferenceModule — just registers the preference store.
 * More preference façades added as we copy more subsystems.
 */
class PreferenceModule(val preferenceStore: PreferenceStore) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory { preferenceStore }
    }
}
