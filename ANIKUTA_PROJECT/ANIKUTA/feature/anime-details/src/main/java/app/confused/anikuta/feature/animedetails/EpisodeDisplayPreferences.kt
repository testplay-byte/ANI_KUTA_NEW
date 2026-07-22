package app.confused.anikuta.feature.animedetails

import app.confused.anikuta.core.preferences.Preference
import app.confused.anikuta.core.preferences.PreferenceStore

/**
 * Episode list display preferences for the detail page.
 *
 * Controls what's shown on each episode row and where elements are positioned.
 * All settings are persisted via [PreferenceStore] and reactive (observe via
 * [Preference.changes]).
 *
 * Ported from the old ANIKUTA's `PlayerPreferences` episode display fields
 * (detail-page portion), adapted to the new project's PreferenceStore.
 *
 * Used by:
 *  - EpisodeRow (renders episodes according to these settings)
 *  - EpisodeListSettingsScreen (live preview + customization UI)
 */
class EpisodeDisplayPreferences(
    private val store: PreferenceStore,
) {
    // ── Show / hide toggles ──

    fun showEpisodeNumber(): Preference<Boolean> =
        store.getBoolean("pref_ep_show_number", true)

    fun showEpisodeTitles(): Preference<Boolean> =
        store.getBoolean("pref_ep_show_titles", true)

    fun showEpisodeSummaries(): Preference<Boolean> =
        store.getBoolean("pref_ep_show_summaries", true)

    fun showEpisodeThumbnails(): Preference<Boolean> =
        store.getBoolean("pref_ep_show_thumbnails", true)

    fun showEpisodeDates(): Preference<Boolean> =
        store.getBoolean("pref_ep_show_dates", true)

    fun showAudioPills(): Preference<Boolean> =
        store.getBoolean("pref_ep_show_audio_pills", true)

    // ── Position settings ──

    /** Where the thumbnail goes: "left" or "right". */
    fun thumbnailPosition(): Preference<String> =
        store.getString("pref_ep_thumb_pos", "left")

    /** Where the title goes: "right" (beside thumbnail) or "below" (under thumbnail). */
    fun titlePosition(): Preference<String> =
        store.getString("pref_ep_title_pos", "right")

    /** Where the synopsis goes: "right" (beside thumbnail) or "below" (under title). */
    fun synopsisPosition(): Preference<String> =
        store.getString("pref_ep_synopsis_pos", "below")

    /** Where the date + audio pills go: "right_below_synopsis" or "below_synopsis". */
    fun datePosition(): Preference<String> =
        store.getString("pref_ep_date_pos", "right_below_synopsis")

    /** Where the episode number goes: "overlay" (on thumbnail) or "badge" (separate badge). */
    fun episodeNumberPosition(): Preference<String> =
        store.getString("pref_ep_num_pos", "overlay")

    // ── Size settings ──

    /** Thumbnail size: "small" (100×56), "medium" (120×68), "large" (160×90). */
    fun thumbnailSize(): Preference<String> =
        store.getString("pref_ep_thumb_size", "medium")

    /** Max lines for episode title: 1 (default, per user request — "force single line") or 2. */
    fun titleMaxLines(): Preference<Int> =
        store.getInt("pref_ep_title_lines", 1)

    /** Max lines for episode synopsis: default 2 (per user request). */
    fun synopsisMaxLines(): Preference<Int> =
        store.getInt("pref_ep_synopsis_lines", 2)

    // ── Background toggles ──
    // Per user request: "give the user the option to show or hide these fun options,
    // like the background color for the text". When ON, the element gets a dedicated
    // surface background; when OFF, it renders as plain text on the card.

    /** Whether the title gets a dedicated background container. */
    fun showTitleBackground(): Preference<Boolean> =
        store.getBoolean("pref_ep_show_title_bg", true)

    /** Whether the date pill gets a dedicated background. */
    fun showDateBackground(): Preference<Boolean> =
        store.getBoolean("pref_ep_show_date_bg", true)

    /** Whether the audio pills get a dedicated background. */
    fun showAudioBackground(): Preference<Boolean> =
        store.getBoolean("pref_ep_show_audio_bg", true)

    /** Whether the synopsis gets a dedicated background container. */
    fun showSynopsisBackground(): Preference<Boolean> =
        store.getBoolean("pref_ep_show_synopsis_bg", true)
}
