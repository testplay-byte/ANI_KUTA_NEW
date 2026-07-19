package app.anikuta.player

import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore

/**
 * ANI-KUTA PlayerPreferences — minimal.
 *
 * Selective copy-paste from aniyomi's `PlayerPreferences` (D1): we keep only the
 * options needed for a functional player. aniyomi's version has ~40 fields spanning
 * subtitles/decoders/gestures/audio/advanced; those panels are deferred to a later
 * phase once the base player is stable.
 *
 * Source: REFERENCE/app/.../ui/player/settings/PlayerPreferences.kt (reduced).
 */
class PlayerPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun playerSpeed(): Preference<Float> =
        preferenceStore.getFloat("pref_player_speed", 1.0f)

    fun tryHWDecoding(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_try_hwdec", true)

    fun gpuNext(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_gpu_next", false)

    fun volumeBoostCap(): Preference<Int> =
        preferenceStore.getInt("pref_volume_boost_cap", 0)

    fun preferredAudioLanguages(): Preference<String> =
        preferenceStore.getString("pref_preferred_audio_lang", "jpn,eng")

    fun seekStepSeconds(): Preference<Int> =
        preferenceStore.getInt("pref_seek_step_seconds", 10)

    /** Last saved brightness for the player (-1 = follow system). */
    fun brightness(): Preference<Float> =
        preferenceStore.getFloat("pref_player_brightness", -1.0f)

    /** Whether controls auto-hide. */
    fun autoHideControls(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_auto_hide_controls", true)

    // ---- Phase 7.5: Episode list display settings ----

    /** Show episode titles (parsed from SEpisode.name). Default: true. */
    fun showEpisodeTitles(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_episode_titles", true)

    /** Show episode summaries/descriptions (from SEpisode.summary). Default: true. */
    fun showEpisodeSummaries(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_episode_summaries", true)

    /** Show episode thumbnails (from SEpisode.preview_url). Default: true. */
    fun showEpisodeThumbnails(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_episode_thumbnails", true)

    /** Show episode dates (from SEpisode.date_upload). Default: true. */
    fun showEpisodeDates(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_episode_dates", true)

    /** Show episode number badge. Default: true. */
    fun showEpisodeNumber(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_episode_number", true)

    /** Show audio availability pills (SUB/DUB/HSUB). Default: true. */
    fun showAudioPills(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_audio_pills", true)

    /**
     * Synopsis position: 'right' = right of thumbnail, below title (default).
     * 'below' = full-width below the thumbnail row.
     */
    fun synopsisPosition(): Preference<String> =
        preferenceStore.getString("pref_synopsis_position", "below")

    /**
     * Download button placement: 'episode_row' = right side of episode row (default).
     * 'synopsis' = inside episode synopsis area (right side, dedicated button).
     */
    fun downloadButtonPlacement(): Preference<String> =
        preferenceStore.getString("pref_download_button_placement", "episode_row")

    /**
     * Date position: 'right_below_synopsis' = right of thumbnail, below synopsis (default).
     * 'right_above_synopsis' = right of thumbnail, above synopsis (between title and synopsis).
     * 'below' = full-width below the thumbnail row.
     */
    fun datePosition(): Preference<String> =
        preferenceStore.getString("pref_date_position", "right_below_synopsis")

    /**
     * Thumbnail size: 'small' (100dp), 'medium' (120dp, default), 'large' (160dp).
     */
    fun thumbnailSize(): Preference<String> =
        preferenceStore.getString("pref_thumbnail_size", "medium")

    /**
     * Title position: 'right' = right of thumbnail (default).
     * 'below' = full-width below the thumbnail row.
     */
    fun titlePosition(): Preference<String> =
        preferenceStore.getString("pref_title_position", "right")

    /**
     * Episode number position: 'overlay' = overlaid on thumbnail (default).
     * 'badge' = as a badge next to the title.
     */
    fun episodeNumberPosition(): Preference<String> =
        preferenceStore.getString("pref_ep_num_position", "overlay")

    /**
     * Thumbnail position: 'left' = thumbnail on left (default).
     * 'right' = thumbnail on right.
     */
    fun thumbnailPosition(): Preference<String> =
        preferenceStore.getString("pref_thumbnail_position", "left")

    /**
     * Anime info position on detail page: 'below' = below episodes (default).
     * 'above' = above episodes (info first, then episodes).
     */
    fun animeInfoPosition(): Preference<String> =
        preferenceStore.getString("pref_anime_info_position", "below")

    /**
     * In-app episode metadata fetching: when enabled, ANI-KUTA fetches
     * episode thumbnails, titles, and descriptions from external sources
     * for extensions that don't provide this data.
     * Default: true.
     */
    fun enableInAppMetadataFetch(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_in_app_metadata_fetch", true)

    /** Fetch episode thumbnails via metadata enrichment. Default: true. */
    fun fetchMetadataThumbnails(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_fetch_metadata_thumbnails", true)

    /** Fetch episode titles via metadata enrichment. Default: true. */
    fun fetchMetadataTitles(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_fetch_metadata_titles", true)

    /** Fetch episode summaries/descriptions via metadata enrichment. Default: true. */
    fun fetchMetadataSummaries(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_fetch_metadata_summaries", true)

    /**
     * Dynamic theming for the detail page: when enabled, the detail page's
     * colors (background, episode cards, accents) are extracted from the
     * anime's cover image using the Android Palette API.
     * Default: true.
     */
    fun dynamicDetailTheming(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_dynamic_detail_theming", true)

    // ---- Watched episode appearance (grayscale + blur) ----

    /**
     * Visual treatment applied to episodes marked as watched.
     *
     * Values:
     * - `"none"`      — no visual treatment (watched episodes look the same as unwatched)
     * - `"grayscale"` — desaturate the entire episode card (text, icons, thumbnail) to black & white
     * - `"blur"`      — apply a slight blur to the entire episode card
     * - `"both"`      — apply both grayscale AND blur (maximum visual distinction)
     *
     * Default: `"grayscale"`.
     */
    fun watchedEpisodeAppearance(): Preference<String> =
        preferenceStore.getString("pref_watched_episode_appearance", "grayscale")

    /**
     * Blur radius (in dp) applied to watched episode cards when the appearance
     * mode includes blur. Higher values = more blurry.
     * Default: 2.dp (subtle blur that's still readable but clearly "watched").
     */
    fun watchedEpisodeBlurRadius(): Preference<Float> =
        preferenceStore.getFloat("pref_watched_episode_blur_radius", 2f)

    /**
     * Alpha (opacity) multiplier applied to watched episode cards when the
     * appearance mode includes grayscale. Lower values = more transparent/dimmed.
     * Default: 0.55 (45% transparent).
     */
    fun watchedEpisodeAlpha(): Preference<Float> =
        preferenceStore.getFloat("pref_watched_episode_alpha", 0.55f)

    // ---- Phase 1: Player mode + combined screen preferences ----

    /**
     * Default player view mode: "minimized", "fullscreen", or "ask".
     * When "ask", shows a prompt the first time (then remembers the user's choice
     * by switching to the selected mode).
     * Default: "minimized" (changed from "ask" per user request — most users want
     * to land in the YouTube-style minimized view, not get prompted).
     */
    fun defaultPlayerView(): Preference<String> =
        preferenceStore.getString("pref_default_player_view", "minimized")

    /**
     * Skip button duration in seconds. Used for the "skip opening" button.
     * Default: 85 (common anime opening duration).
     */
    fun skipButtonDuration(): Preference<Int> =
        preferenceStore.getInt("pref_skip_button_duration", 85)

    /**
     * Master toggle for all gesture controls in the player.
     * Default: true.
     */
    fun playerGesturesEnabled(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_player_gestures_enabled", true)

    /**
     * Whether the first-time player prompt has been shown.
     * Used to only show the "choose your default view" dialog once.
     * Default: false.
     */
    fun playerPromptShown(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_player_prompt_shown", false)

    // ---- Phase 5: Subtitle customization preferences ----

    /**
     * Show the top navigation bar in the player (minimized mode).
     * When disabled, the video player moves up to fill the space.
     * The status bar remains visible. User navigates back via system gestures.
     * Default: true.
     */
    fun showPlayerTopBar(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_player_top_bar", true)

    /**
     * P2a: Whether to automatically enter PiP mode when the user presses Home
     * while a video is playing. Default: false (off — user must enable it).
     * When disabled, pressing Home simply pauses the video and goes to background.
     * The manual PiP button in fullscreen always works regardless of this setting.
     */
    fun pipOnExit(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_pip_on_exit", false)

    /**
     * Quality sheet display mode:
     * - "current" = show only qualities for the current server + audio version (default)
     * - "all" = show all qualities from all servers and audio versions, organized into sections
     */
    fun qualitySheetDisplayMode(): Preference<String> =
        preferenceStore.getString("pref_quality_sheet_display_mode", "current")

    /** Subtitle font family. Default: "Sans Serif". */
    fun subtitleFont(): Preference<String> =
        preferenceStore.getString("pref_subtitle_font", "Sans Serif")

    /** Subtitle font size (MPV sub-font-size). Default: 55. */
    fun subtitleFontSize(): Preference<Int> =
        preferenceStore.getInt("pref_subtitle_font_size", 55)

    /** Subtitle font scale multiplier. Default: 1.0. */
    fun subtitleFontScale(): Preference<Float> =
        preferenceStore.getFloat("pref_sub_scale", 1f)

    /** Subtitle border/outline size. Default: 3. */
    fun subtitleBorderSize(): Preference<Int> =
        preferenceStore.getInt("pref_sub_border_size", 3)

    /** Bold subtitles. Default: false. */
    fun boldSubtitles(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_bold_subtitles", false)

    /** Italic subtitles. Default: false. */
    fun italicSubtitles(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_italic_subtitles", false)

    /** Subtitle text color (ARGB int). Default: White. */
    fun textColorSubtitles(): Preference<Int> =
        preferenceStore.getInt("pref_text_color_subtitles", 0xFFFFFFFF.toInt())

    /** Subtitle border/outline color (ARGB int). Default: Black. */
    fun borderColorSubtitles(): Preference<Int> =
        preferenceStore.getInt("pref_border_color_subtitles", 0xFF000000.toInt())

    /** Subtitle background color (ARGB int). Default: transparent. */
    fun backgroundColorSubtitles(): Preference<Int> =
        preferenceStore.getInt("pref_background_color_subtitles", 0x00000000)

    /** Subtitle vertical position (0-100, 100 = bottom). Default: 100. */
    fun subtitlePosition(): Preference<Int> =
        preferenceStore.getInt("pref_sub_pos", 100)

    /** Subtitle shadow offset. Default: 0. */
    fun subtitleShadowOffset(): Preference<Int> =
        preferenceStore.getInt("pref_sub_shadow_offset", 0)

    /** Override ASS/SSA subtitle styling. Default: false. */
    fun overrideSubsASS(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_override_subtitles_ass", false)

    /**
     * Default subtitle behavior when an episode starts playing:
     * - "off"  = subtitles off (do not auto-select any track)
     * - "on"   = subtitles on, auto-select the first/best-matching track
     * - "auto" = subtitles on only if a track matches [preferredSubtitleLanguage]
     *
     * This replaces the old implicit "always auto-select" behaviour that was
     * racy with the SubtitleTracksSheet and could auto-disable subs right
     * after they were selected. Default: "on".
     *
     * Wired into PlayerActivity.autoSelectSubtitleTrack.
     */
    fun defaultSubtitleMode(): Preference<String> =
        preferenceStore.getString("pref_default_subtitle_mode", "on")

    /**
     * Preferred subtitle language code(s), comma-separated (e.g. "en,eng" or
     * "jpn"). Used when [defaultSubtitleMode] is "auto" to pick a matching
     * track, and as a tiebreaker when "on" finds multiple tracks. Default:
     * "en,eng" (English).
     */
    fun preferredSubtitleLanguage(): Preference<String> =
        preferenceStore.getString("pref_preferred_subtitle_lang", "en,eng")

    /** Subtitle delay in milliseconds (can be negative). Default: 0. */
    fun subtitlesDelay(): Preference<Int> =
        preferenceStore.getInt("pref_subtitles_delay", 0)
}
