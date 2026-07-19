package app.anikuta.ui.settings

/**
 * Phase 6 task 6.17 — MoreScreen is now a thin wrapper around SettingsHomeScreen.
 *
 * The old 551-line single-page settings has been split into:
 *  - SettingsHomeScreen (category list — this file delegates to it)
 *  - GeneralSettingsScreen, PlayerSettingsScreen, ExtensionsSettingsScreen,
 *    DownloadsSettingsScreen, TrackingSettingsScreen, AboutSettingsScreen
 *
 * The shared UI components (SettingsGroupCard, LeadingIcon, ClickableSettingsRow,
 * SwitchSettingsRow) are in SettingsComponents.kt.
 */
@androidx.compose.runtime.Composable
fun MoreScreen(
    onOpenDebug: () -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    SettingsHomeScreen(onNavigate = onNavigate)
}
