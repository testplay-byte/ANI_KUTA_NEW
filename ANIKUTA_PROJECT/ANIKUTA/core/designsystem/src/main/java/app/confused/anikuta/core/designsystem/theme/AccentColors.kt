package app.confused.anikuta.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.confused.anikuta.core.preferences.AccentPreset
import kotlin.math.max
import kotlin.math.min

/**
 * The primary color roles for a single accent — used to override the primary
 * roles in a [androidx.compose.material3.ColorScheme].
 *
 * Per `DESIGN_LANGUAGE/03-themes/themes-and-colors.md` §3: every palette
 * provides full M3 role-sets for light + dark modes. This data class holds
 * just the primary-family roles (the ones the accent controls); the surface,
 * background, and secondary/tertiary roles stay as the base ANIKUTA palette.
 *
 * @param darkPrimary The primary color in dark mode (bright/saturated).
 * @param darkOnPrimary Text/icon color on [darkPrimary] (usually near-black).
 * @param darkPrimaryContainer The container color in dark mode (darker tint).
 * @param darkOnPrimaryContainer Text color on [darkPrimaryContainer] (light tint).
 * @param lightPrimary The primary color in light mode (darker/muted for contrast).
 * @param lightOnPrimary Text/icon color on [lightPrimary] (usually white).
 * @param lightPrimaryContainer The container color in light mode (light tint).
 * @param lightOnPrimaryContainer Text color on [lightPrimaryContainer] (dark tint).
 */
data class AccentScheme(
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
)

/**
 * Derives a full [AccentScheme] from a single seed [color].
 *
 * The derivation uses HSL-style manipulation:
 * - Dark mode primary = the color as-is (bright).
 * - Dark mode onPrimary = black if the color is bright enough, else white.
 * - Dark mode primaryContainer = the color darkened ~40%.
 * - Dark mode onPrimaryContainer = the color lightened ~40%.
 * - Light mode primary = the color darkened ~30% (for contrast on light surfaces).
 * - Light mode onPrimary = white.
 * - Light mode primaryContainer = the color lightened ~60%.
 * - Light mode onPrimaryContainer = the color darkened ~50%.
 *
 * This isn't a full HCT tonal palette (per the design spec, that's a future
 * enhancement), but it produces harmonious, readable results for any seed.
 */
fun accentScheme(color: Color): AccentScheme {
    val hsl = color.toHsl()

    val darkPrimary = color
    val darkOnPrimary = if (darkPrimary.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White
    val darkPrimaryContainer = hsl.copy(l = max(0f, hsl.l - 0.35f)).toColor()
    val darkOnPrimaryContainer = hsl.copy(l = min(1f, hsl.l + 0.30f)).toColor()

    val lightPrimary = hsl.copy(l = max(0f, hsl.l - 0.25f)).toColor()
    val lightOnPrimary = Color.White
    val lightPrimaryContainer = hsl.copy(l = min(1f, hsl.l + 0.45f)).toColor()
    val lightOnPrimaryContainer = hsl.copy(l = max(0f, hsl.l - 0.40f)).toColor()

    return AccentScheme(
        darkPrimary = darkPrimary,
        darkOnPrimary = darkOnPrimary,
        darkPrimaryContainer = darkPrimaryContainer,
        darkOnPrimaryContainer = darkOnPrimaryContainer,
        lightPrimary = lightPrimary,
        lightOnPrimary = lightOnPrimary,
        lightPrimaryContainer = lightPrimaryContainer,
        lightOnPrimaryContainer = lightOnPrimaryContainer,
    )
}

/** The [AccentScheme] for a given [AccentPreset] (or CUSTOM via [color]). */
fun accentSchemeFor(preset: AccentPreset, color: Color? = null): AccentScheme {
    val seed = if (preset == AccentPreset.CUSTOM && color != null) color else preset.seedColor
    return accentScheme(seed)
}

// ── HSL helpers (internal) ──────────────────────────────────────────────────

internal data class Hsl(val h: Float, val s: Float, val l: Float)

internal fun Color.toHsl(): Hsl {
    val r = red
    val g = green
    val b = blue

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f

    val h: Float
    val s: Float
    if (max == min) {
        h = 0f
        s = 0f
    } else {
        val d = max - min
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            r -> ((g - b) / d + if (g < b) 6f else 0f) / 6f
            g -> ((b - r) / d + 2f) / 6f
            else -> ((r - g) / d + 4f) / 6f
        }
    }
    return Hsl(h, s, l)
}

internal fun Hsl.toColor(): Color {
    fun hueToRgb(p: Float, q: Float, t: Float): Float {
        var tt = t
        if (tt < 0f) tt += 1f
        if (tt > 1f) tt -= 1f
        return when {
            tt < 1f / 6f -> p + (q - p) * 6f * tt
            tt < 1f / 2f -> q
            tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
            else -> p
        }
    }

    if (s == 0f) {
        val gray = l
        return Color(gray, gray, gray, 1f)
    }

    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    val r = hueToRgb(p, q, h + 1f / 3f)
    val g = hueToRgb(p, q, h)
    val b = hueToRgb(p, q, h - 1f / 3f)
    return Color(r, g, b, 1f)
}
