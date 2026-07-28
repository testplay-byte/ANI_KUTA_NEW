package app.confused.anikuta.core.player

import app.confused.anikuta.core.preferences.Preference
import app.confused.anikuta.core.preferences.PreferenceStore

/**
 * Player episode list display preferences.
 *
 * SEPARATE from the detail page's PlayerPreferences — the user can have
 * different layouts in each. All keys are prefixed with "player_ep_" to
 * avoid collision with the detail page's "pref_" keys.
 *
 * Used by:
 *  - EpisodeListView in the player (minimized mode)
 *  - Player episode display settings subpage (with live preview)
 */
class PlayerEpisodePreferences(
    private val store: PreferenceStore,
) {
    fun showEpisodeNumber(): Preference<Boolean> =
        store.getBoolean("player_ep_show_number", true)

    fun showEpisodeTitles(): Preference<Boolean> =
        store.getBoolean("player_ep_show_titles", true)

    fun showEpisodeSummaries(): Preference<Boolean> =
        store.getBoolean("player_ep_show_summaries", true)

    fun showEpisodeThumbnails(): Preference<Boolean> =
        store.getBoolean("player_ep_show_thumbnails", true)

    fun showEpisodeDates(): Preference<Boolean> =
        store.getBoolean("player_ep_show_dates", true)

    fun showAudioPills(): Preference<Boolean> =
        store.getBoolean("player_ep_show_audio_pills", true)

    /**
     * Whether to render a download button on each episode card in the player's
     * episode list. Mirrors the detail page's "download button" toggle but is
     * scoped to the player (separate preference). When false, the player
     * episode list looks like it did before the download button was added.
     */
    fun showDownloadButton(): Preference<Boolean> =
        store.getBoolean("player_ep_show_download_button", true)

    fun synopsisPosition(): Preference<String> =
        store.getString("player_ep_synopsis_pos", "below")

    fun datePosition(): Preference<String> =
        store.getString("player_ep_date_pos", "right_below_synopsis")

    fun thumbnailSize(): Preference<String> =
        store.getString("player_ep_thumb_size", "medium")

    fun titlePosition(): Preference<String> =
        store.getString("player_ep_title_pos", "right")

    fun episodeNumberPosition(): Preference<String> =
        store.getString("player_ep_num_pos", "overlay")

    fun thumbnailPosition(): Preference<String> =
        store.getString("player_ep_thumb_pos", "left")

    fun downloadButtonPlacement(): Preference<String> =
        store.getString("player_ep_dl_btn_pos", "episode_row")

    // ── Background toggles (parity with the details page's EpisodeDisplayPreferences) ──
    // Per user: "the details page and the watch page would be customizable
    // separately." These mirror the details page's background toggles but use
    // separate player_ep_* keys so the two are independently configurable.

    /** Whether the title gets a dedicated background container. */
    fun showTitleBackground(): Preference<Boolean> =
        store.getBoolean("player_ep_show_title_bg", true)

    /** Whether the date pill gets a dedicated background. */
    fun showDateBackground(): Preference<Boolean> =
        store.getBoolean("player_ep_show_date_bg", true)

    /** Whether the audio pills get a dedicated background. */
    fun showAudioBackground(): Preference<Boolean> =
        store.getBoolean("player_ep_show_audio_bg", true)

    /** Whether the synopsis gets a dedicated background container. */
    fun showSynopsisBackground(): Preference<Boolean> =
        store.getBoolean("player_ep_show_synopsis_bg", true)

    // ── Line-count prefs ──

    /** Max lines for episode title: 1 (default) or 2. */
    fun titleMaxLines(): Preference<Int> =
        store.getInt("player_ep_title_lines", 1)

    /** Max lines for episode synopsis: default 2 (per user request). */
    fun synopsisMaxLines(): Preference<Int> =
        store.getInt("player_ep_synopsis_lines", 2)
}
