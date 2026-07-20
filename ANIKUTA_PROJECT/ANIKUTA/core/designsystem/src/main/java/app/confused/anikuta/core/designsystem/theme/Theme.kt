package app.confused.anikuta.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * ANIKUTA theme — the single entry point for theming the app.
 *
 * Per `DESIGN_LANGUAGE/03-themes/`:
 * - Dark theme is the default (per owner preference — `darkTheme` defaults to true).
 * - Primary color: #B1F256 (lime green).
 * - Surface tonal tiers (surface1–5).
 * - AMOLED mode forces pure black background + near-black surface ramp.
 *
 * Status bar appearance is handled by `enableEdgeToEdge()` in the Activity
 * (the modern API 35+ approach — `window.statusBarColor` is deprecated).
 * Here we only set the system bar icon appearance (light/dark icons).
 *
 * Usage:
 * ```kotlin
 * AnikutaTheme {
 *     // your composables
 * }
 * ```
 *
 * @param darkTheme If true, uses the dark color scheme. Default: true (owner preference).
 * @param amoled If true, overrides the dark scheme with pure-black surfaces.
 */
@Composable
fun AnikutaTheme(
    darkTheme: Boolean = true,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        darkTheme && amoled -> DarkColorSchemeAmoled
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            // Modern approach: only control icon appearance, not the color.
            // enableEdgeToEdge() in the Activity handles the transparent system bars.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AnikutaTypography,
        shapes = AnikutaShapes,
        content = content,
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = PrimaryFgDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    tertiary = TertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
    background = BgDark,
    onBackground = TextDark,
    surface = Surface1Dark,
    onSurface = TextDark,
    surfaceVariant = Surface3Dark,
    onSurfaceVariant = TextMutedDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)

private val DarkColorSchemeAmoled = DarkColorScheme.copy(
    background = BgAmoled,
    surface = Surface1Amoled,
    surfaceVariant = Surface3Amoled,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = PrimaryFgLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    tertiary = TertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    background = BgLight,
    onBackground = TextLight,
    surface = Surface1Light,
    onSurface = TextLight,
    surfaceVariant = Surface3Light,
    onSurfaceVariant = TextMutedLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)
