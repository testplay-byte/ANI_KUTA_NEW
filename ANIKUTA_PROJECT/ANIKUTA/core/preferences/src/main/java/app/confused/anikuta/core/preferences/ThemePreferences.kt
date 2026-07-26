package app.confused.anikuta.core.preferences

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * The theme mode — which surface tone scheme the app uses.
 *
 * Per `DESIGN_LANGUAGE/03-themes/themes-and-colors.md` §2:
 * - [LIGHT] — light surfaces.
 * - [DARK] — dark surfaces with tonal elevation.
 * - [SYSTEM] — follows the OS dark-mode setting (default).
 *
 * AMOLED is a separate toggle ([ThemePreferences.amoled]) that only applies
 * when the resolved mode is DARK — it's an opinion, not a mode (per the spec).
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

/**
 * The accent palette — the primary color family used throughout the app.
 *
 * Per `DESIGN_LANGUAGE/03-themes/themes-and-colors.md` §2:
 * - A curated set of named presets (the owner picks which to ship).
 * - [CUSTOM] lets the user pick an arbitrary color via a color picker.
 *
 * **No blue/indigo presets** per the design rules (`RULES/ai-agent-rules.md`
 * §6 + owner preference). The presets are warm/green tones.
 */
enum class AccentPreset(val displayName: String, val seedColorArgb: Int) {
    LIME("Lime", 0xFFB1F256.toInt()),
    AMBER("Amber", 0xFFFFC107.toInt()),
    ROSE("Rose", 0xFFEC407A.toInt()),
    CORAL("Coral", 0xFFFF7043.toInt()),
    SAGE("Sage", 0xFF8BC34A.toInt()),
    CUSTOM("Custom", 0xFFB1F256.toInt()), // seedColorArgb is unused for CUSTOM; the actual color is in customAccentColor.

    /** Compose [Color] for this preset's seed color. */
    val seedColor: Color get() = Color(seedColorArgb.toLong() and 0xFFFFFFFF)
}

/**
 * User-selectable theme preferences — persisted via [PreferenceStore].
 *
 * Three independent axes:
 * 1. [themeMode] — Light / Dark / System (surface tone).
 * 2. [amoled] — when true + resolved mode is Dark, forces pure-black surfaces.
 * 3. [accentPreset] + [customAccentColor] — the primary color family.
 *
 * All preferences are reactive via `Preference.changes()`, so the app
 * recomposes live when the user changes a setting in the Appearance screen.
 *
 * Registered in [app.confused.anikuta.core.preferences.di.preferenceModule]
 * (or a dedicated module) as a Koin singleton.
 */
class ThemePreferences(
    private val store: PreferenceStore,
) {
    /** The theme mode (Light / Dark / System). Default: [ThemeMode.SYSTEM]. */
    val themeMode: Preference<ThemeMode> = store.getEnum("pref_theme_mode", ThemeMode.SYSTEM)

    /** AMOLED black surfaces (only applies in dark mode). Default: false. */
    val amoled: Preference<Boolean> = store.getBoolean("pref_theme_amoled", false)

    /** The accent preset. Default: [AccentPreset.LIME]. */
    val accentPreset: Preference<AccentPreset> = store.getEnum("pref_theme_accent_preset", AccentPreset.LIME)

    /**
     * The custom accent color (ARGB as Int) — used when [accentPreset] is [AccentPreset.CUSTOM].
     * Default: the lime seed color (#B1F256).
     */
    val customAccentColor: Preference<Int> = store.getInt("pref_theme_custom_accent", AccentPreset.LIME.seedColorArgb)

    /** Convenience: the effective accent color (preset seed or custom). */
    fun effectiveAccentColor(): Color {
        val preset = accentPreset.get()
        return if (preset == AccentPreset.CUSTOM) {
            Color(customAccentColor.get().toLong() and 0xFFFFFFFF)
        } else {
            preset.seedColor
        }
    }

    /** Convenience: set a custom accent color + switch to CUSTOM preset. */
    fun setCustomAccent(color: Color) {
        customAccentColor.set(color.toArgb())
        accentPreset.set(AccentPreset.CUSTOM)
    }
}
