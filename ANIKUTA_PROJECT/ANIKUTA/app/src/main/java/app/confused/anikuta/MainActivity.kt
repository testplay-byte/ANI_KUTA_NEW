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
 * when the user changes the theme mode, accent color, or palette in the
 * Appearance screen, the entire app recomposes live (no restart) with a
 * smooth cross-fade transition. See ADR-038.
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
            // Observe theme preferences reactively — the app theme updates live
            // with a smooth cross-fade transition.
            val themePrefs = koinInject<ThemePreferences>()
            val themeMode by themePrefs.themeMode.changes()
                .collectAsStateWithLifecycle(initialValue = themePrefs.themeMode.get())
            val amoled by themePrefs.amoled.changes()
                .collectAsStateWithLifecycle(initialValue = themePrefs.amoled.get())
            val accentPreset by themePrefs.accentPreset.changes()
                .collectAsStateWithLifecycle(initialValue = themePrefs.accentPreset.get())
            val customAccentArgb by themePrefs.customAccentColor.changes()
                .collectAsStateWithLifecycle(initialValue = themePrefs.customAccentColor.get())
            val customAccent = Color(customAccentArgb.toLong() and 0xFFFFFFFF)

            // Full-palette customization (Session 1 item 9.5)
            val paletteMode by themePrefs.paletteMode.changes()
                .collectAsStateWithLifecycle(initialValue = themePrefs.paletteMode.get())
            val customBgArgb by themePrefs.customBackgroundColor.changes()
                .collectAsStateWithLifecycle(initialValue = themePrefs.customBackgroundColor.get())
            val customCardArgb by themePrefs.customCardColor.changes()
                .collectAsStateWithLifecycle(initialValue = themePrefs.customCardColor.get())
            val customTextArgb by themePrefs.customTextColor.changes()
                .collectAsStateWithLifecycle(initialValue = themePrefs.customTextColor.get())

            AnikutaTheme(
                themeMode = themeMode,
                amoled = amoled,
                accentPreset = accentPreset,
                customAccentColor = customAccent,
                paletteMode = paletteMode,
                customBackground = Color(customBgArgb.toLong() and 0xFFFFFFFF),
                customCard = Color(customCardArgb.toLong() and 0xFFFFFFFF),
                customText = Color(customTextArgb.toLong() and 0xFFFFFFFF),
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
