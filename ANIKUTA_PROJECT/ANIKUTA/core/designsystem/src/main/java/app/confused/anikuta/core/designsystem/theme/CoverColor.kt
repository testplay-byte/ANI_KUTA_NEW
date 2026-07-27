package app.confused.anikuta.core.designsystem.theme

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.palette.graphics.Palette

/**
 * Generates a dynamic [ColorScheme] from an anime's cover color for
 * cover-color theming (per watch-page.md §7 + themes-and-colors.md §6).
 *
 * The watch page + fullscreen player wrap their subtree in
 * `MaterialTheme(colorScheme = generateDynamicScheme(coverColor))` so the
 * player chrome is tinted with the anime's identity. Backing out restores
 * the user's selected palette.
 *
 * @param coverColor the ARGB int color extracted from the cover art
 *   (e.g. from AniList's `coverImage.color` field, or from Palette)
 * @param darkTheme whether to generate a dark scheme (default true — ANIKUTA
 *   defaults to dark)
 * @param amoled whether to use pure-black surfaces (AMOLED mode)
 */
fun generateDynamicScheme(
    coverColor: Int,
    darkTheme: Boolean = true,
    amoled: Boolean = false,
): ColorScheme {
    if (coverColor == 0) {
        // No cover color — return the default ANIKUTA scheme
        return if (darkTheme) AnikutaDarkColorScheme else AnikutaLightColorScheme
    }

    // Use the cover color directly (AniList provides it as an ARGB hex string)
    val dominant = ComposeColor(coverColor)

    // Derive a harmonious palette from the dominant color
    val primary = dominant
    val onPrimary = if (dominant.luminance() > 0.5f) ComposeColor.Black else ComposeColor.White
    val primaryContainer = dominant.copy(alpha = 0.3f).compositeOver(ComposeColor.Black)
    val onPrimaryContainer = ComposeColor.White

    return if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = dominant.copy(alpha = 0.8f),
            onSecondary = onPrimary,
            secondaryContainer = dominant.copy(alpha = 0.2f).compositeOver(ComposeColor.Black),
            onSecondaryContainer = ComposeColor.White,
            tertiary = dominant.copy(alpha = 0.6f),
            onTertiary = onPrimary,
            background = if (amoled) ComposeColor.Black else ComposeColor(0xFF111111),
            onBackground = ComposeColor.White,
            surface = if (amoled) ComposeColor.Black else ComposeColor(0xFF1A1A1A),
            onSurface = ComposeColor.White,
            surfaceVariant = ComposeColor(0xFF222222),
            onSurfaceVariant = ComposeColor(0xFFAAAAAA),
            surfaceContainerLow = if (amoled) ComposeColor(0xFF0A0A0A) else ComposeColor(0xFF1E1E1E),
            surfaceContainerHigh = if (amoled) ComposeColor(0xFF111111) else ComposeColor(0xFF282828),
            surfaceContainerHighest = if (amoled) ComposeColor(0xFF1A1A1A) else ComposeColor(0xFF333333),
            outline = ComposeColor(0xFF444444),
            outlineVariant = ComposeColor(0xFF333333),
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = dominant.copy(alpha = 0.2f).compositeOver(ComposeColor.White),
            onPrimaryContainer = ComposeColor.Black,
            background = ComposeColor.White,
            onBackground = ComposeColor.Black,
            surface = ComposeColor(0xFFFAFAFA),
            onSurface = ComposeColor.Black,
        )
    }
}

/**
 * Extract the dominant color from a Bitmap using Palette.
 * Returns ARGB int, or 0 if extraction fails.
 */
fun extractDominantColor(bitmap: Bitmap?): Int {
    if (bitmap == null) return 0
    return try {
        val palette = Palette.from(bitmap).generate()
        palette.getDominantColor(0)
    } catch (e: Exception) {
        0
    }
}

// ── Helpers ──

private fun ComposeColor.luminance(): Float {
    val r = red * 0.299f
    val g = green * 0.587f
    val b = blue * 0.114f
    return r + g + b
}

private fun ComposeColor.compositeOver(background: ComposeColor): ComposeColor {
    val alpha = this.alpha
    return ComposeColor(
        red = this.red * alpha + background.red * (1 - alpha),
        green = this.green * alpha + background.green * (1 - alpha),
        blue = this.blue * alpha + background.blue * (1 - alpha),
        alpha = 1f,
    )
}

// Default ANIKUTA color scheme (lime green #B1F256)
private val AnikutaPrimary = ComposeColor(0xFFB1F256)
private val AnikutaDarkColorScheme = darkColorScheme(
    primary = AnikutaPrimary,
    onPrimary = ComposeColor.Black,
    primaryContainer = ComposeColor(0xFF1B1729),
    onPrimaryContainer = AnikutaPrimary,
    background = ComposeColor(0xFF0D0D0D),
    onBackground = ComposeColor.White,
    surface = ComposeColor(0xFF1A1A1A),
    onSurface = ComposeColor.White,
    surfaceVariant = ComposeColor(0xFF222222),
    onSurfaceVariant = ComposeColor(0xFFAAAAAA),
)
private val AnikutaLightColorScheme = lightColorScheme(
    primary = AnikutaPrimary,
    onPrimary = ComposeColor.Black,
)
