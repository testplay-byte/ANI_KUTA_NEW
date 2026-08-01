package app.confused.anikuta.core.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Tracks whether the setup wizard has been completed.
 *
 * On first app launch, [isCompleted] returns `false` → the app shows the
 * `SetupWizardApp` composable instead of the main `Navigator`. When the user
 * finishes the wizard, [setCompleted] is called with `true` → the app
 * recomposes to show the main UI.
 *
 * The user can re-run the wizard from Settings → General → "Run setup wizard
 * again", which calls [setCompleted]`(false)` + recomposes.
 */
class SetupWizardPreferences(
    private val preferenceStore: PreferenceStore,
) {
    private val completedPref = preferenceStore.getBoolean(KEY_COMPLETED, false)

    fun isCompleted(): Boolean = completedPref.get()

    fun setCompleted(done: Boolean) = completedPref.set(done)

    fun observeCompleted(): Flow<Boolean> = completedPref.changes()

    private companion object {
        private const val KEY_COMPLETED = "pref_setup_wizard_completed"
    }
}
