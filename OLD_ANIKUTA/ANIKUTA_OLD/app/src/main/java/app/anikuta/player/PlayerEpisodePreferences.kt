package app.anikuta.player

import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore

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
    private val preferenceStore: PreferenceStore,
) {
    fun showEpisodeNumber(): Preference<Boolean> =
        preferenceStore.getBoolean("player_ep_show_number", true)

    fun showEpisodeTitles(): Preference<Boolean> =
        preferenceStore.getBoolean("player_ep_show_titles", true)

    fun showEpisodeSummaries(): Preference<Boolean> =
        preferenceStore.getBoolean("player_ep_show_summaries", true)

    fun showEpisodeThumbnails(): Preference<Boolean> =
        preferenceStore.getBoolean("player_ep_show_thumbnails", true)

    fun showEpisodeDates(): Preference<Boolean> =
        preferenceStore.getBoolean("player_ep_show_dates", true)

    fun showAudioPills(): Preference<Boolean> =
        preferenceStore.getBoolean("player_ep_show_audio_pills", true)

    /**
     * Whether to render a download button on each episode card in the player's
     * episode list. Mirrors the detail page's "download button" toggle but is
     * scoped to the player (separate preference). When false, the player
     * episode list looks like it did before the download button was added.
     */
    fun showDownloadButton(): Preference<Boolean> =
        preferenceStore.getBoolean("player_ep_show_download_button", true)

    fun synopsisPosition(): Preference<String> =
        preferenceStore.getString("player_ep_synopsis_pos", "below")

    fun datePosition(): Preference<String> =
        preferenceStore.getString("player_ep_date_pos", "right_below_synopsis")

    fun thumbnailSize(): Preference<String> =
        preferenceStore.getString("player_ep_thumb_size", "medium")

    fun titlePosition(): Preference<String> =
        preferenceStore.getString("player_ep_title_pos", "right")

    fun episodeNumberPosition(): Preference<String> =
        preferenceStore.getString("player_ep_num_pos", "overlay")

    fun thumbnailPosition(): Preference<String> =
        preferenceStore.getString("player_ep_thumb_pos", "left")

    fun downloadButtonPlacement(): Preference<String> =
        preferenceStore.getString("player_ep_dl_btn_pos", "episode_row")
}
