package app.confused.anikuta.core.episodemetadata

import app.confused.anikuta.core.preferences.Preference
import app.confused.anikuta.core.preferences.PreferenceStore

/**
 * Preferences for episode metadata fetching.
 *
 * Controls whether metadata is fetched at all, and which fields are fetched.
 * Used by [EpisodeMetadataRepository] to skip sources/fields the user disabled.
 *
 * Ported from the old ANIKUTA's `PlayerPreferences` metadata fields.
 */
class EpisodeMetadataPreferences(
    private val store: PreferenceStore,
) {
    /** Master toggle: if false, no metadata is fetched at all. */
    fun enabled(): Preference<Boolean> =
        store.getBoolean("pref_ep_metadata_enabled", true)

    /** Whether to fetch episode thumbnails. */
    fun fetchThumbnails(): Preference<Boolean> =
        store.getBoolean("pref_ep_metadata_thumbnails", true)

    /** Whether to fetch episode titles. */
    fun fetchTitles(): Preference<Boolean> =
        store.getBoolean("pref_ep_metadata_titles", true)

    /** Whether to fetch episode descriptions/synopses. */
    fun fetchSummaries(): Preference<Boolean> =
        store.getBoolean("pref_ep_metadata_summaries", true)

    /** Whether to fetch episode air dates. */
    fun fetchAirDates(): Preference<Boolean> =
        store.getBoolean("pref_ep_metadata_airdates", true)
}
