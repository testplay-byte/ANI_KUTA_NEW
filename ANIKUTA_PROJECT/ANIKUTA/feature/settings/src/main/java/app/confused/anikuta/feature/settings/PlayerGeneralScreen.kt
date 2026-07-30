package app.confused.anikuta.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.player.PlayerPreferences
import org.koin.compose.koinInject

/**
 * The Player settings page — reached from Settings → Player.
 *
 * Currently contains:
 * - **Auto-play toggle** — controls whether playback starts automatically
 *   when an episode is loaded.
 *
 * Structured so future settings (default quality, default speed, hardware
 * decoding toggle, etc.) can be easily added.
 *
 * @param onBack Pops this screen.
 */
@Composable
fun PlayerGeneralScreen(
    onBack: () -> Unit,
) {
    val prefs = koinInject<PlayerPreferences>()

    val autoPlay by prefs.autoPlay().changes()
        .collectAsStateWithLifecycle(initialValue = prefs.autoPlay().get())

    val pauseOnAppExit by prefs.pauseOnAppExit().changes()
        .collectAsStateWithLifecycle(initialValue = prefs.pauseOnAppExit().get())

    val resumeOnAppReturn by prefs.resumeOnAppReturn().changes()
        .collectAsStateWithLifecycle(initialValue = prefs.resumeOnAppReturn().get())

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 || lazyListState.firstVisibleItemIndex > 0

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = "Player", collapsed = collapsed)
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            // ── Playback section ──
            item {
                SettingsSectionLabel("Playback")
            }
            item {
                PlayerToggleCard(
                    title = "Auto-play",
                    subtitle = "Start playing automatically when an episode loads",
                    checked = autoPlay,
                    onCheckedChange = { prefs.autoPlay().set(it) },
                )
            }

            // ── App exit behavior section ──
            // Per owner request (2026-07-28): when the user exits the app
            // (closes but doesn't remove from recents), playback should pause
            // by default — and there should be a setting to configure this.
            item {
                SettingsSectionLabel("App exit")
            }
            item {
                PlayerToggleCard(
                    title = "Pause on app exit",
                    subtitle = "Pause playback when you leave the app (press Home / switch apps). Off = audio continues in background.",
                    checked = pauseOnAppExit,
                    onCheckedChange = { prefs.pauseOnAppExit().set(it) },
                )
            }
            // "Resume on return" only makes sense if pause-on-exit is enabled.
            // Greyed-out (but still visible) when pause-on-exit is off, so the
            // user can see the option exists and enable both at once.
            item {
                PlayerToggleCard(
                    title = "Resume on return",
                    subtitle = "When you come back to the app, automatically resume from where you left off.",
                    checked = resumeOnAppReturn && pauseOnAppExit,
                    onCheckedChange = { newValue ->
                        // Writing is only meaningful when pause-on-exit is on;
                        // if the user toggles this while pause-on-exit is off,
                        // we still persist the value so it takes effect when
                        // they later enable pause-on-exit.
                        prefs.resumeOnAppReturn().set(newValue)
                        if (newValue && !pauseOnAppExit) {
                            // Auto-enable pause-on-exit so this toggle has an
                            // immediate visible effect (otherwise the card
                            // would appear stuck off).
                            prefs.pauseOnAppExit().set(true)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun PlayerToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
