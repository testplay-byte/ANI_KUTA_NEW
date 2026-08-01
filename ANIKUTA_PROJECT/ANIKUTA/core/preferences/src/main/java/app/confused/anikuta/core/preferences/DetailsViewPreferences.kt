package app.confused.anikuta.core.preferences

import app.confused.anikuta.core.common.model.details.DataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * User preferences for the anime details page default data source.
 *
 * # Global default vs per-anime override
 *
 * The details page can show data from two sources: **AniList** (metadata-rich —
 * score, format, season, studios, next-airing) or **Extension** (episodes,
 * author/artist, source-specific metadata). When the user opens an anime that
 * has BOTH sources available (i.e., it's linked — has an AniList ID + a source
 * link), the app needs to decide which source to show first.
 *
 * The decision cascade is:
 *
 * 1. **Per-anime preference** (`DetailsViewPreferenceStore` in `:data:extension`) —
 *    if the user previously manually switched for THIS anime (via "View from
 *    AniList" / "View from Extension" in the three-dot menu), that choice is
 *    remembered and respected on re-open. This takes highest priority.
 * 2. **Global default** (this class, [defaultDataSource]) — the user's
 *    app-wide preference set in Settings → General → "Default details view".
 *    Applies when there's no per-anime preference (first open).
 * 3. **Entry mode fallback** — if no global default is set (or the preferred
 *    source isn't available for this anime), the page opens in the mode
 *    matching how the user entered (AniList browse → AniList mode; extension
 *    search → Extension mode).
 *
 * # Per-anime override behavior
 *
 * If the user sets the global default to "AniList" but then manually switches
 * a specific anime to "Extension", that anime will ALWAYS open in Extension
 * mode on re-open — the per-anime preference wins over the global default.
 * This is by design: the user's explicit per-anime choice should always be
 * respected over a broad default.
 *
 * # Availability check
 *
 * The global default only applies when the preferred source is actually
 * available for the anime being opened:
 * - If the default is "AniList" but the anime has no AniList ID (unlinked
 *   extension anime), the page opens in Extension mode.
 * - If the default is "Extension" but the anime has no source link (AniList-
 *   only anime), the page opens in AniList mode.
 *
 * This check is performed in `AnimeDetailViewModel.initialDataSource()`.
 *
 * @param preferenceStore the backing preference store.
 */
class DetailsViewPreferences(
    private val preferenceStore: PreferenceStore,
) {
    /**
     * The default data source for the details page when no per-anime
     * preference exists.
     *
     * Values: `"anilist"`, `"extension"`, or `"entry_mode"` (the default —
     * uses the entry mode, preserving the pre-setting behavior).
     */
    private val defaultDataSourcePref = preferenceStore.getString(
        KEY_DEFAULT_DATA_SOURCE,
        VALUE_ANILIST,  // Default: AniList (per user spec)
    )

    /**
     * Get the configured default data source, or `null` if the user hasn't
     * set one (entry mode fallback).
     *
     * Returns `null` for `"entry_mode"` so the caller knows to use the entry
     * mode. Returns [DataSource.ANILIST] or [DataSource.EXTENSION] otherwise.
     */
    fun getDefaultDataSource(): DataSource? {
        return when (defaultDataSourcePref.get()) {
            VALUE_ANILIST -> DataSource.ANILIST
            VALUE_EXTENSION -> DataSource.EXTENSION
            else -> null // entry_mode or unknown → null (use entry mode)
        }
    }

    /** Set the default data source. Pass `null` to reset to entry mode. */
    fun setDefaultDataSource(source: DataSource?) {
        defaultDataSourcePref.set(
            when (source) {
                DataSource.ANILIST -> VALUE_ANILIST
                DataSource.EXTENSION -> VALUE_EXTENSION
                null -> DEFAULT_ENTRY_MODE
            },
        )
    }

    /** Observe the default data source reactively. */
    fun observeDefaultDataSource(): Flow<DataSource?> =
        defaultDataSourcePref.changes().map { value ->
            when (value) {
                VALUE_ANILIST -> DataSource.ANILIST
                VALUE_EXTENSION -> DataSource.EXTENSION
                else -> null
            }
        }

    private companion object {
        private const val KEY_DEFAULT_DATA_SOURCE = "pref_default_details_data_source"
        private const val VALUE_ANILIST = "anilist"
        private const val VALUE_EXTENSION = "extension"
        private const val DEFAULT_ENTRY_MODE = "entry_mode"
    }
}
