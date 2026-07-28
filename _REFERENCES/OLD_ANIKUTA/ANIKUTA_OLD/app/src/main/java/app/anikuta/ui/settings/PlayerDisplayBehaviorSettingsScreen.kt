package app.anikuta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Player → Display & Behavior subpage: top bar, auto-hide, gestures, PiP, skip.
 */
@Composable
fun PlayerDisplayBehaviorSettingsScreen(onBack: () -> Unit) {
    val prefs = remember { Injekt.get<PlayerPreferences>() }
    val scope = rememberCoroutineScope()
    val showTopBar by prefs.showPlayerTopBar().stateIn(scope).collectAsState()
    val autoHide by prefs.autoHideControls().stateIn(scope).collectAsState()
    val gesturesEnabled by prefs.playerGesturesEnabled().stateIn(scope).collectAsState()
    val pipOnExit by prefs.pipOnExit().stateIn(scope).collectAsState()
    val skipDuration by prefs.skipButtonDuration().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "Display & Behavior", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsGroupCard(title = "Display") {
                    SwitchSettingsRow(
                        icon = Icons.Default.Visibility,
                        title = "Show top bar",
                        subtitle = "Floating navigation bar in minimized mode",
                        checked = showTopBar,
                        onCheckedChange = { prefs.showPlayerTopBar().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.Visibility,
                        title = "Auto-hide controls",
                        subtitle = "Hide player controls after inactivity",
                        checked = autoHide,
                        onCheckedChange = { prefs.autoHideControls().set(it) },
                    )
                }
            }
            item {
                SettingsGroupCard(title = "Gestures & PiP") {
                    SwitchSettingsRow(
                        icon = Icons.Default.TouchApp,
                        title = "Gestures",
                        subtitle = "Swipe to seek, brightness, volume, double-tap",
                        checked = gesturesEnabled,
                        onCheckedChange = { prefs.playerGesturesEnabled().set(it) },
                    )
                    HorizontalDivider()
                    SwitchSettingsRow(
                        icon = Icons.Default.PictureInPicture,
                        title = "Auto PiP on exit",
                        subtitle = "Enter picture-in-picture when pressing Home",
                        checked = pipOnExit,
                        onCheckedChange = { prefs.pipOnExit().set(it) },
                    )
                }
            }
            item {
                SettingsGroupCard(title = "Skip") {
                    SwitchSettingsRow(
                        icon = Icons.Default.SkipNext,
                        title = "Skip button",
                        subtitle = "Skip opening duration: ${skipDuration}s",
                        checked = skipDuration > 0,
                        onCheckedChange = { prefs.skipButtonDuration().set(if (it) 85 else 0) },
                    )
                }
            }
        }
    }
}
