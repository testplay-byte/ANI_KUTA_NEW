package app.confused.anikuta.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.confused.anikuta.core.preferences.AccentPreset
import app.confused.anikuta.core.preferences.ThemeMode

/**
 * ANIKUTA theme — the single entry point for theming the app.
 *
 * Per `DESIGN_LANGUAGE/03-themes/themes-and-colors.md`:
 * - Two independent axes: [themeMode] (Light/Dark/System) + [accentPreset]
 *   (the primary color family).
 * - [amoled] forces pure-black surfaces when the resolved mode is Dark.
 * - [customAccentColor] is used when [accentPreset] is [AccentPreset.CUSTOM].
 *
 * The accent overrides the primary-family roles in the base ANIKUTA palette;
 * the surface, background, and secondary/tertiary roles stay as the curated
 * base palette. This keeps the design language cohesive across accents.
 *
 * Status bar appearance is handled by `enableEdgeToEdge()` in the Activity
 * (the modern API 35+ approach — `window.statusBarColor` is deprecated).
 * Here we only set the system bar icon appearance (light/dark icons).
 *
 * @param themeMode The theme mode (Light / Dark / System). Default: [ThemeMode.SYSTEM].
 * @param amoled If true + resolved mode is Dark, forces pure-black surfaces.
 * @param accentPreset The accent palette preset. Default: [AccentPreset.LIME].
 * @param customAccentColor Used when [accentPreset] is [AccentPreset.CUSTOM].
 */
@Composable
fun AnikutaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    amoled: Boolean = false,
    accentPreset: AccentPreset = AccentPreset.LIME,
    customAccentColor: Color = Color(AccentPreset.LIME.seedColorArgb.toLong() and 0xFFFFFFFF),
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val accent = accentSchemeFor(
        preset = accentPreset,
        color = if (accentPreset == AccentPreset.CUSTOM) customAccentColor else null,
    )

    val colorScheme = when {
        isDark && amoled -> buildDarkColorScheme(accent).copy(
            background = BgAmoled,
            surface = Surface1Amoled,
            surfaceVariant = Surface3Amoled,
        )
        isDark -> buildDarkColorScheme(accent)
        else -> buildLightColorScheme(accent)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AnikutaTypography,
        shapes = AnikutaShapes,
        content = content,
    )
}

/** Builds the dark color scheme with the accent's primary-family roles overridden. */
private fun buildDarkColorScheme(accent: AccentScheme) = darkColorScheme(
    primary = accent.darkPrimary,
    onPrimary = accent.darkOnPrimary,
    primaryContainer = accent.darkPrimaryContainer,
    onPrimaryContainer = accent.darkOnPrimaryContainer,
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

/** Builds the light color scheme with the accent's primary-family roles overridden. */
private fun buildLightColorScheme(accent: AccentScheme) = lightColorScheme(
    primary = accent.lightPrimary,
    onPrimary = accent.lightOnPrimary,
    primaryContainer = accent.lightPrimaryContainer,
    onPrimaryContainer = accent.lightOnPrimaryContainer,
    secondary = SecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    tertiary = TertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    background = BgLight,
    onBackground = TextLight,
    surface = Surface1Light,
    onSurface = TextLight,
    surfaceVariant = Surface3Light,
    onSurfaceVariant = TextMutedLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)
