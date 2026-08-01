package app.confused.anikuta.core.preferences

/**
 * One-shot preference gate for the Setup Wizard.
 *
 * Tracks whether the user has completed the first-launch Setup Wizard flow.
 * - On a fresh install, [isCompleted] returns `false` → the app shows the wizard
 *   instead of the main `AnikutaRoot` UI.
 * - On wizard completion, the wizard calls [setCompleted]`(true)` and the app
 *   recomposes to show the main UI.
 * - The "Run setup wizard again" entry in Settings → General sets it back to
 *   `false` to re-trigger the wizard.
 *
 * Lives in `:core:preferences` (not `:feature:setup-wizard`) so that `:app`
 * can read it on startup WITHOUT depending on the wizard feature module
 * (Rule §14 — feature modules are leaf deps).
 */
class SetupWizardPreferences(
    private val store: PreferenceStore,
) {
    private val completedPref = store.getBoolean("pref_setup_wizard_completed", false)

    /** Whether the user has already finished the Setup Wizard. */
    fun isCompleted(): Boolean = completedPref.get()

    /** Mark the wizard as completed (or, with `false`, re-arm it). */
    fun setCompleted(done: Boolean) = completedPref.set(done)
}
