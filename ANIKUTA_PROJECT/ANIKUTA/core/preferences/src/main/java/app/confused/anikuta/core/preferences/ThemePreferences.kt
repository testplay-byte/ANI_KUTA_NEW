package app.confused.anikuta.core.preferences

/**
 * The theme mode — which surface tone scheme the app uses.
 *
 * Per `DESIGN_LANGUAGE/03-themes/themes-and-colors.md` §2:
 * - [LIGHT] — light surfaces.
 * - [DARK] — dark surfaces with tonal elevation.
 * - [SYSTEM] — follows the OS dark-mode setting (default).
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

/**
 * The accent palette — the primary color family used throughout the app.
 *
 * Per owner spec (Session 1 + feedback): 15 curated presets + 1 custom:
 * - **10 accent-only presets** (indices 0–9): only the accent color changes;
 *   the background, card, and text colors use the base ANIKUTA palette.
 * - **5 full-palette presets** (indices 10–14): highly different — each has
 *   its own background, card, and text colors in addition to the accent.
 *   Selecting one sets [PaletteMode.FULL] + writes the preset's colors to
 *   the custom color prefs.
 * - [CUSTOM] (index 15): the user's custom palette. Opens the color picker.
 *
 * **No blue/indigo presets** per the design rules. All colors are warm/green/
 * earth tones, carefully curated for visual harmony.
 *
 * @param displayName The name shown in the picker UI.
 * @param seedColorArgb The accent (primary) color as ARGB Int.
 * @param isFullPalette Whether this preset overrides bg/card/text (true for
 *   the 5 advanced presets, false for the 10 accent-only presets).
 * @param backgroundArgb The background color (only for full-palette presets).
 * @param cardArgb The card/surface color (only for full-palette presets).
 * @param textArgb The text color (only for full-palette presets).
 */
enum class AccentPreset(
    val displayName: String,
    val seedColorArgb: Int,
    val isFullPalette: Boolean = false,
    val backgroundArgb: Int? = null,
    val cardArgb: Int? = null,
    val textArgb: Int? = null,
) {
    // ── 10 accent-only presets (warm/green/earth tones — NO blue/indigo) ──
    LIME("Lime", 0xFFB1F256.toInt()),
    AMBER("Amber", 0xFFFFC107.toInt()),
    ROSE("Rose", 0xFFEC407A.toInt()),
    CORAL("Coral", 0xFFFF7043.toInt()),
    SAGE("Sage", 0xFF8BC34A.toInt()),
    PINK("Pink", 0xFFE91E63.toInt()),
    CRIMSON("Crimson", 0xFFD32F2F.toInt()),
    TANGERINE("Tangerine", 0xFFFF9800.toInt()),
    CHARTREUSE("Chartreuse", 0xFFCDDC39.toInt()),
    EMERALD("Emerald", 0xFF2E7D32.toInt()),

    // ── 5 full-palette presets (highly different — custom bg/card/text) ──
    // Each is a cohesive theme with its own mood. Carefully curated.
    MIDNIGHT("Midnight", 0xFFB1F256.toInt(), isFullPalette = true,
        backgroundArgb = 0xFF0A0A0F.toInt(),
        cardArgb = 0xFF16161E.toInt(),
        textArgb = 0xFFE8E8F0.toInt()),

    SUNSET("Sunset", 0xFFFFAB40.toInt(), isFullPalette = true,
        backgroundArgb = 0xFF1A0F0A.toInt(),
        cardArgb = 0xFF2A1C14.toInt(),
        textArgb = 0xFFF5E6D3.toInt()),

    FOREST("Forest", 0xFF66BB6A.toInt(), isFullPalette = true,
        backgroundArgb = 0xFF0A140D.toInt(),
        cardArgb = 0xFF13241A.toInt(),
        textArgb = 0xFFD4E8D4.toInt()),

    CHARCOAL("Charcoal", 0xFFFFC107.toInt(), isFullPalette = true,
        backgroundArgb = 0xFF121212.toInt(),
        cardArgb = 0xFF1E1E1E.toInt(),
        textArgb = 0xFFEEEEEE.toInt()),

    COFFEE("Coffee", 0xFFFFCC80.toInt(), isFullPalette = true,
        backgroundArgb = 0xFF1A1410.toInt(),
        cardArgb = 0xFF2A201A.toInt(),
        textArgb = 0xFFF0E0D0.toInt()),

    CUSTOM("Custom", 0xFFB1F256.toInt()),
    ;

    /** Compose [Color] for this preset's seed color. The UI layer converts. */
    val seedColor: androidx.compose.ui.graphics.Color
        get() = androidx.compose.ui.graphics.Color(seedColorArgb.toLong() and 0xFFFFFFFF)
}

/**
 * The palette customization mode.
 *
 * - [SIMPLIFIED] — only the accent color is set; bg/card/text use the base palette.
 * - [FULL] — the user (or a full-palette preset) has set custom bg/card/text.
 */
enum class PaletteMode {
    SIMPLIFIED,
    FULL,
}

/**
 * User-selectable theme preferences — persisted via [PreferenceStore].
 *
 * Three independent axes:
 * 1. [themeMode] — Light / Dark / System.
 * 2. [amoled] — pure-black surfaces in dark mode.
 * 3. [accentPreset] + [customAccentColor] — the primary color family.
 *
 * **Palette customization** ([paletteMode]): when [PaletteMode.FULL], the user
 * can override the background, card surface, and text colors individually.
 *
 * **No Compose dependency** — colors are stored as ARGB Int.
 */
class ThemePreferences(
    private val store: PreferenceStore,
) {
    val themeMode: Preference<ThemeMode> = store.getEnum("pref_theme_mode", ThemeMode.SYSTEM)
    val amoled: Preference<Boolean> = store.getBoolean("pref_theme_amoled", false)
    val accentPreset: Preference<AccentPreset> = store.getEnum("pref_theme_accent_preset", AccentPreset.LIME)
    val customAccentColor: Preference<Int> = store.getInt("pref_theme_custom_accent", AccentPreset.LIME.seedColorArgb)

    val paletteMode: Preference<PaletteMode> = store.getEnum("pref_palette_mode", PaletteMode.SIMPLIFIED)
    val customBackgroundColor: Preference<Int> = store.getInt("pref_theme_custom_bg", 0xFF14111F.toInt())
    val customCardColor: Preference<Int> = store.getInt("pref_theme_custom_card", 0xFF221E33.toInt())
    val customTextColor: Preference<Int> = store.getInt("pref_theme_custom_text", 0xFFECE6F5.toInt())

    /** Convenience: the effective accent color ARGB (preset seed or custom). */
    fun effectiveAccentColorArgb(): Int {
        val preset = accentPreset.get()
        return if (preset == AccentPreset.CUSTOM) customAccentColor.get() else preset.seedColorArgb
    }

    /** Convenience: set a custom accent color (ARGB Int) + switch to CUSTOM preset. */
    fun setCustomAccent(argb: Int) {
        customAccentColor.set(argb)
        accentPreset.set(AccentPreset.CUSTOM)
    }

    /**
     * Applies a full-palette preset: sets the accent, bg, card, text, and
     * switches to FULL mode. Used when the user selects one of the 5
     * full-palette presets (Midnight, Sunset, Forest, Charcoal, Coffee).
     */
    fun applyFullPalettePreset(preset: AccentPreset) {
        require(preset.isFullPalette) { "applyFullPalettePreset requires a full-palette preset, got $preset" }
        accentPreset.set(preset)
        preset.backgroundArgb?.let { customBackgroundColor.set(it) }
        preset.cardArgb?.let { customCardColor.set(it) }
        preset.textArgb?.let { customTextColor.set(it) }
        customAccentColor.set(preset.seedColorArgb)
        paletteMode.set(PaletteMode.FULL)
    }
}
