package app.confused.anikuta.feature.episodesettings

/**
 * The 4 screens of the Episode Settings flow.
 *
 * Navigation is driven by a hand-rolled state-machine in `MainActivity.kt`
 * (the app does NOT use Voyager or Compose Navigation). A `var episodeSettingsPage:
 * EpisodeSettingsPage?` state holds the current sub-page; `null` means "not in
 * the episode-settings flow at all".
 *
 * Flow:
 * ```
 * More → Settings → "Episode settings" row
 *   → EpisodeSettingsPage.Hub        (live preview + 3 links)
 *     → EpisodeSettingsPage.Display  (show/hide toggles)
 *     → EpisodeSettingsPage.Layout   (position knobs)
 *     → EpisodeSettingsPage.Metadata (fetch toggles)
 * ```
 *
 * Back is handled by `BackHandler` which pops to the previous page (Hub) or exits
 * the flow entirely (when on Hub).
 */
sealed interface EpisodeSettingsPage {
    /** Hub: live preview + 3 clickable rows (Display / Layout / Metadata). */
    data object Hub : EpisodeSettingsPage

    /** Show/hide toggles for episode-number, titles, summaries, thumbnails, dates, audio pills + title maxLines. */
    data object Display : EpisodeSettingsPage

    /** Position knobs: thumbnail side, title position, synopsis position, date position, ep-number position, thumbnail size. */
    data object Layout : EpisodeSettingsPage

    /** Master toggle + per-field fetch toggles for episode metadata. */
    data object Metadata : EpisodeSettingsPage
}
