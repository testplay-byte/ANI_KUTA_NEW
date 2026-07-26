package app.confused.anikuta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.designsystem.theme.AnikutaTheme
import app.confused.anikuta.core.preferences.ThemePreferences
import app.confused.anikuta.navigation.AnikutaRoot
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.koinInject

/**
 * The single activity for the ANIKUTA app.
 *
 * Uses Voyager for navigation (ADR-037). The root composable [AnikutaRoot]
 * sets up a single [Navigator][cafe.adriel.voyager.navigator.Navigator] with
 * the 4 bottom-nav tabs as root screens, and pushed screens (detail, watch,
 * settings, etc.) on top.
 *
 * **Theme:** The [AnikutaTheme] is wired to [ThemePreferences] reactively —
 * when the user changes the theme mode or accent color in the Appearance
 * screen, the entire app recomposes live (no restart). See ADR-038.
 *
 * **OAuth callback handling:** Tracker OAuth redirects come back as
 * `ACTION_VIEW` intents. This activity receives them (via `singleTask` launch
 * mode) and publishes the callback URL to [pendingOAuthCallback], which
 * [AnikutaRoot] collects and processes via [AppController.handleOAuthCallback].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Observe theme preferences reactively — the app theme updates live.
            val themePrefs = koinInject<ThemePreferences>()
            val themeMode by themePrefs.themeMode.changes()
                .collectAsStateWithLifecycle(initial = themePrefs.themeMode.get())
            val amoled by themePrefs.amoled.changes()
                .collectAsStateWithLifecycle(initial = themePrefs.amoled.get())
            val accentPreset by themePrefs.accentPreset.changes()
                .collectAsStateWithLifecycle(initial = themePrefs.accentPreset.get())
            val customAccentArgb by themePrefs.customAccentColor.changes()
                .collectAsStateWithLifecycle(initial = themePrefs.customAccentColor.get())
            val customAccent = Color(customAccentArgb.toLong() and 0xFFFFFFFF)

            AnikutaTheme(
                themeMode = themeMode,
                amoled = amoled,
                accentPreset = accentPreset,
                customAccentColor = customAccent,
            ) {
                AnikutaRoot()
            }
        }
        // Handle OAuth callback intent (initial launch via redirect).
        handleOAuthIntent(intent)
    }

    // Handle OAuth callback when app is already running (singleTask).
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: android.content.Intent?) {
        if (intent?.action == android.content.Intent.ACTION_VIEW) {
            val data = intent.data?.toString() ?: return
            pendingOAuthCallback.value = data
        }
    }

    companion object {
        // Holds the OAuth callback URL for the composable to process.
        val pendingOAuthCallback = MutableStateFlow<String?>(null)
    }
}
