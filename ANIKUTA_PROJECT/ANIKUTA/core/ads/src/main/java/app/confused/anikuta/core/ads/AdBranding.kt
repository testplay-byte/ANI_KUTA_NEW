package app.confused.anikuta.core.ads

/**
 * The user's preferred ad "branding" — controls the emoji + text shown
 * in the [AdDialog] when an ad is displayed.
 *
 * - [POISON] — Shows a poison bottle / skull emoji + "Your daily dose of poison is here."
 * - [PILLS] — Shows a pill emoji + "Your daily dose of pills is here."
 *
 * Set during the Setup Wizard's "Choose Your Poison" screen and persisted
 * in [AdsPreferences.adName].
 */
enum class AdName(val label: String, val emoji: String, val titleText: String) {
    POISON("Daily dose of poison", "\u2620\uFE0F", "Your daily dose of poison is here."),
    PILLS("Daily dose of pills", "\uD83D\uDC8A", "Your daily dose of pills is here."),
}

/**
 * When ads should be shown.
 *
 * - [APP_OPEN] — On app open (before navigating to an anime detail page).
 * - [EPISODE_START] — On episode start (before the player opens).
 * - [BOTH] — Both app open and episode start.
 *
 * Set during the Setup Wizard's "Choose Your Poison" screen.
 * The [AdManager] reads this to decide when to trigger [shouldShowAd].
 */
enum class AdTiming(val label: String) {
    APP_OPEN("On app open"),
    EPISODE_START("On episode start"),
    BOTH("Both"),
}
