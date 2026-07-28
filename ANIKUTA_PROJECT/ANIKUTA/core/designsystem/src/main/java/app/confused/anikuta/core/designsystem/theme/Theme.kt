package app.confused.anikuta.core.designsystem.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.confused.anikuta.core.preferences.AccentPreset
import app.confused.anikuta.core.preferences.PaletteMode
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
 * **Animated transition (Session 1 item 2):** the color scheme cross-fades
 * smoothly when the user switches theme mode or accent — no sudden change.
 * Each color role is animated via `animateColorAsState` (~400ms tween).
 *
 * **Full palette customization (Session 1 item 9.5):** when [paletteMode] is
 * [PaletteMode.FULL], the [customBackground], [customCard], and [customText]
 * colors override the base surface/background/text roles. When SIMPLIFIED,
 * only the accent is overridden.
 *
 * @param themeMode The theme mode (Light / Dark / System). Default: [ThemeMode.SYSTEM].
 * @param amoled If true + resolved mode is Dark, forces pure-black surfaces.
 * @param accentPreset The accent palette preset. Default: [AccentPreset.LIME].
 * @param customAccentColor Used when [accentPreset] is [AccentPreset.CUSTOM].
 * @param paletteMode SIMPLIFIED (accent only) or FULL (all colors). Default: SIMPLIFIED.
 * @param customBackground Used when [paletteMode] is FULL — overrides background.
 * @param customCard Used when [paletteMode] is FULL — overrides surface/card.
 * @param customText Used when [paletteMode] is FULL — overrides text.
 */
@Composable
fun AnikutaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    amoled: Boolean = false,
    accentPreset: AccentPreset = AccentPreset.LIME,
    customAccentColor: Color = Color(AccentPreset.LIME.seedColorArgb.toLong() and 0xFFFFFFFF),
    paletteMode: PaletteMode = PaletteMode.SIMPLIFIED,
    customBackground: Color = Color(0xFF14111F),
    customCard: Color = Color(0xFF221E33),
    customText: Color = Color(0xFFECE6F5),
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

    // Build the target color scheme (before animation).
    val targetScheme = when {
        isDark && amoled -> buildDarkColorScheme(accent).copy(
            background = BgAmoled,
            surface = Surface1Amoled,
            surfaceVariant = Surface3Amoled,
        )
        isDark -> buildDarkColorScheme(accent)
        else -> buildLightColorScheme(accent)
    }

    // Apply full-palette overrides (if FULL mode).
    val fullPaletteScheme = if (paletteMode == PaletteMode.FULL) {
        targetScheme.copy(
            background = customBackground,
            surface = customCard,
            onBackground = customText,
            onSurface = customText,
        )
    } else {
        targetScheme
    }

    // ── Animated transition: cross-fade each color role ──
    // Each role animates independently via animateColorAsState. This produces
    // a smooth dark↔light cross-fade rather than a sudden switch.
    val animatedScheme = animateColorScheme(fullPaletteScheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = animatedScheme,
        typography = AnikutaTypography,
        shapes = AnikutaShapes,
        content = content,
    )
}

/**
 * Animates each color role of a [ColorScheme] via [animateColorAsState].
 *
 * Used for smooth dark↔light transitions. The animation duration is ~400ms
 * with `FastOutSlowInEasing`-equivalent (default tween easing).
 */
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val durationMs = 400
    val primary by animateColorAsState(target.primary, tween(durationMs), label = "primary")
    val onPrimary by animateColorAsState(target.onPrimary, tween(durationMs), label = "onPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, tween(durationMs), label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, tween(durationMs), label = "onPrimaryContainer")
    val secondary by animateColorAsState(target.secondary, tween(durationMs), label = "secondary")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, tween(durationMs), label = "secondaryContainer")
    val tertiary by animateColorAsState(target.tertiary, tween(durationMs), label = "tertiary")
    val tertiaryContainer by animateColorAsState(target.tertiaryContainer, tween(durationMs), label = "tertiaryContainer")
    val error by animateColorAsState(target.error, tween(durationMs), label = "error")
    val errorContainer by animateColorAsState(target.errorContainer, tween(durationMs), label = "errorContainer")
    val background by animateColorAsState(target.background, tween(durationMs), label = "background")
    val onBackground by animateColorAsState(target.onBackground, tween(durationMs), label = "onBackground")
    val surface by animateColorAsState(target.surface, tween(durationMs), label = "surface")
    val onSurface by animateColorAsState(target.onSurface, tween(durationMs), label = "onSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, tween(durationMs), label = "surfaceVariant")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, tween(durationMs), label = "onSurfaceVariant")
    val outline by animateColorAsState(target.outline, tween(durationMs), label = "outline")
    val outlineVariant by animateColorAsState(target.outlineVariant, tween(durationMs), label = "outlineVariant")

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        secondaryContainer = secondaryContainer,
        tertiary = tertiary,
        tertiaryContainer = tertiaryContainer,
        error = error,
        errorContainer = errorContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
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
