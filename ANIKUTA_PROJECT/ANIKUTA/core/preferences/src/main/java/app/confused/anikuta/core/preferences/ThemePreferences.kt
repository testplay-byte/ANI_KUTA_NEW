package app.confused.anikuta.core.preferences

/**
 * The theme mode — which surface tone scheme the app uses.
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
 * - **10 accent-only presets** (indices 0–9): only the accent color changes.
 *   Order: Lime, Coral, Rose, Amber, Red, Teal, Blue, Cyan, Violet, Emerald.
 * - **5 full-palette presets** (indices 10–14): each has its own bg/card/text.
 *   Midnight, Sunset, Forest, Charcoal (red-themed), Coffee.
 * - [CUSTOM] (index 15): the user's custom palette. Opens the color picker.
 *   Always shown LAST in the carousel (rightmost).
 */
enum class AccentPreset(
    val displayName: String,
    val seedColorArgb: Int,
    val isFullPalette: Boolean = false,
    val backgroundArgb: Int? = null,
    val cardArgb: Int? = null,
    val textArgb: Int? = null,
) {
    // ── 10 accent-only presets (in owner-specified order) ──
    LIME("Lime", 0xFFB1F256.toInt()),
    CORAL("Coral", 0xFFFF7043.toInt()),
    ROSE("Rose", 0xFFEC407A.toInt()),
    AMBER("Amber", 0xFFFFC107.toInt()),
    RED("Red", 0xFFF44336.toInt()),
    TEAL("Teal", 0xFF009688.toInt()),
    BLUE("Blue", 0xFF2196F3.toInt()),
    CYAN("Cyan", 0xFF00BCD4.toInt()),
    VIOLET("Violet", 0xFF9C27B0.toInt()),
    EMERALD("Emerald", 0xFF2E7D32.toInt()),

    // ── 5 full-palette presets ──
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

    // Charcoal → red-themed per owner request
    CHARCOAL("Charcoal", 0xFFFF5252.toInt(), isFullPalette = true,
        backgroundArgb = 0xFF0F0A0A.toInt(),
        cardArgb = 0xFF1E1414.toInt(),
        textArgb = 0xFFF5E0E0.toInt()),

    COFFEE("Coffee", 0xFFFFCC80.toInt(), isFullPalette = true,
        backgroundArgb = 0xFF1A1410.toInt(),
        cardArgb = 0xFF2A201A.toInt(),
        textArgb = 0xFFF0E0D0.toInt()),

    CUSTOM("Custom", 0xFFB1F256.toInt()),
    ;
}

/**
 * The palette customization mode.
 */
enum class PaletteMode {
    SIMPLIFIED,
    FULL,
}

/**
 * User-selectable theme preferences — persisted via [PreferenceStore].
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

    /**
     * Remembers the user's custom palette mode (SIMPLIFIED or FULL).
     *
     * When the user selects a preset, [paletteMode] changes. But when they
     * come back to Custom, we restore [paletteMode] from this pref so their
     * advanced customization is preserved (per owner feedback: "it does not
     * stay with the advanced palette customization options").
     */
    val customPaletteMode: Preference<PaletteMode> = store.getEnum("pref_custom_palette_mode", PaletteMode.SIMPLIFIED)

    /**
     * Whether to apply dynamic cover-color theming to the AniList anime details
     * page. When `true` (default), the details page wraps in a MaterialTheme
     * whose ColorScheme is generated from the anime's cover color via
     * [generateDynamicScheme]. When `false`, the user's selected palette is used.
     */
    val adaptiveColorsDetails: Preference<Boolean> = store.getBoolean("pref_adaptive_colors_details", true)

    /**
     * Whether to apply dynamic cover-color theming to the fullscreen video
     * player controls. When `true` (default), the fullscreen controls overlay
     * uses a ColorScheme generated from the anime's cover color. When `false`,
     * the app's default theme colors are used.
     */
    val adaptiveColorsPlayer: Preference<Boolean> = store.getBoolean("pref_adaptive_colors_player", true)

    /** Convenience: set a custom accent color + switch to CUSTOM preset. */
    fun setCustomAccent(argb: Int) {
        customAccentColor.set(argb)
        accentPreset.set(AccentPreset.CUSTOM)
    }

    /**
     * Applies a full-palette preset: sets the accent, bg, card, text, and
     * switches to FULL mode.
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

    /**
     * Selects the CUSTOM preset and restores the user's saved custom palette
     * mode (SIMPLIFIED or FULL). This preserves the advanced customization
     * when the user switches away from Custom and comes back.
     */
    fun selectCustom() {
        accentPreset.set(AccentPreset.CUSTOM)
        paletteMode.set(customPaletteMode.get())
    }
}
