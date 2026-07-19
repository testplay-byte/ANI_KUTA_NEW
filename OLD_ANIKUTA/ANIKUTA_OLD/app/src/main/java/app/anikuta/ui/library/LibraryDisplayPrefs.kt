package app.anikuta.ui.library

import app.anikuta.core.preference.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Persists the Library display customization settings (Phase E).
 *
 * Solves two user complaints:
 *  1. "If I switch the screens and go back to the library page, the view
 *     switches back again" → display mode is now persisted.
 *  2. "Give me more customization options" → all grid options below are
 *     persisted + exposed via a customization bottom sheet.
 *
 * Settings:
 *  - displayMode: GRID or LIST (removed GRID_3 per user request — only 2 layouts)
 *  - gridColumns: 2, 3, 4, or 5
 *  - titlePosition: BELOW or OVERLAY (on the cover)
 *  - titleMaxLines: 1, 2, or 3
 *  - showRating: toggle
 *  - showYear: toggle
 *  - showEpisodes: toggle
 *  - showSubDub: toggle
 *  - showUnwatchedBadge: toggle
 *  - cardBorder: NONE, THIN, or THICK
 *
 * Related files:
 *   - LibraryViewModel.kt — reads these prefs
 *   - LibraryScreen.kt — renders the customization sheet + applies settings
 */
class LibraryDisplayPrefs(
    private val preferenceStore: PreferenceStore,
) {

    enum class DisplayMode { GRID, LIST }
    enum class TitlePosition { BELOW, OVERLAY }
    enum class CardBorder { NONE, THIN, THICK }

    private val displayModePref = preferenceStore.getString("lib_display_mode", "GRID")
    private val gridColumnsPref = preferenceStore.getInt("lib_grid_columns", 2)
    private val titlePositionPref = preferenceStore.getString("lib_title_position", "BELOW")
    private val titleMaxLinesPref = preferenceStore.getInt("lib_title_max_lines", 2)
    private val showRatingPref = preferenceStore.getBoolean("lib_show_rating", true)
    private val showYearPref = preferenceStore.getBoolean("lib_show_year", true)
    private val showEpisodesPref = preferenceStore.getBoolean("lib_show_episodes", true)
    private val showSubDubPref = preferenceStore.getBoolean("lib_show_subdub", true)
    private val showUnwatchedPref = preferenceStore.getBoolean("lib_show_unwatched", true)
    private val cardBorderPref = preferenceStore.getString("lib_card_border", "THIN")

    /**
     * Reactive stream of all display settings.
     *
     * Uses a MutableStateFlow that we update on each setter call (simpler than
     * combine() for 10+ flows of different types — Kotlin's combine only
     * supports up to 5 typed flows; vararg requires same type).
     */
    private val _settings = MutableStateFlow(getSettings())
    val changes: Flow<Settings> = _settings

    private fun refresh() { _settings.value = getSettings() }

    fun getSettings(): Settings = Settings(
        displayMode = runCatching { DisplayMode.valueOf(displayModePref.get()) }.getOrDefault(DisplayMode.GRID),
        gridColumns = gridColumnsPref.get(),
        titlePosition = runCatching { TitlePosition.valueOf(titlePositionPref.get()) }.getOrDefault(TitlePosition.BELOW),
        titleMaxLines = titleMaxLinesPref.get(),
        showRating = showRatingPref.get(),
        showYear = showYearPref.get(),
        showEpisodes = showEpisodesPref.get(),
        showSubDub = showSubDubPref.get(),
        showUnwatchedBadge = showUnwatchedPref.get(),
        cardBorder = runCatching { CardBorder.valueOf(cardBorderPref.get()) }.getOrDefault(CardBorder.THIN),
    )

    fun setDisplayMode(mode: DisplayMode) { displayModePref.set(mode.name); refresh() }
    fun setGridColumns(columns: Int) { gridColumnsPref.set(columns.coerceIn(2, 5)); refresh() }
    fun setTitlePosition(pos: TitlePosition) { titlePositionPref.set(pos.name); refresh() }
    fun setTitleMaxLines(lines: Int) { titleMaxLinesPref.set(lines.coerceIn(1, 3)); refresh() }
    fun setShowRating(show: Boolean) { showRatingPref.set(show); refresh() }
    fun setShowYear(show: Boolean) { showYearPref.set(show); refresh() }
    fun setShowEpisodes(show: Boolean) { showEpisodesPref.set(show); refresh() }
    fun setShowSubDub(show: Boolean) { showSubDubPref.set(show); refresh() }
    fun setShowUnwatchedBadge(show: Boolean) { showUnwatchedPref.set(show); refresh() }
    fun setCardBorder(border: CardBorder) { cardBorderPref.set(border.name); refresh() }

    data class Settings(
        val displayMode: DisplayMode,
        val gridColumns: Int,
        val titlePosition: TitlePosition,
        val titleMaxLines: Int,
        val showRating: Boolean,
        val showYear: Boolean,
        val showEpisodes: Boolean,
        val showSubDub: Boolean,
        val showUnwatchedBadge: Boolean,
        val cardBorder: CardBorder,
    )
}
