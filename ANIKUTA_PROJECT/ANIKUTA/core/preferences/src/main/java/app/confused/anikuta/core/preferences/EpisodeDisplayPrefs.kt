package app.confused.anikuta.core.preferences

/**
 * Display preferences passed to EpisodeRow (simplified snapshot of
 * [EpisodeDisplayPreferences] for the render path).
 *
 * Built by `rememberEpisodeDisplaySnapshot` in `:feature:anime-details`
 * (EpisodesSection) and `rememberEpisodeDisplayPrefs` in
 * `:feature:episode-settings` (the live preview's snapshot helper) — both
 * collect each [Preference.changes] flow and assemble a stable snapshot the
 * Compose layer can render against without re-reading the prefs every frame.
 *
 * # Phase 8 — module boundary fix (Doc 04 violation 2)
 *
 * This data class was previously declared at the bottom of
 * `:feature:anime-details`'s `EpisodesSection.kt`. Because
 * `:feature:episode-settings` imported it from there (feature→feature), it now
 * lives here in `:core:preferences` next to [EpisodeDisplayPreferences]. Both
 * feature modules import it from this package. Pure move — no field changes.
 */
data class EpisodeDisplayPrefs(
    val showThumbnails: Boolean = true,
    val showTitles: Boolean = true,
    val showSummaries: Boolean = true,
    val showDates: Boolean = true,
    val showEpisodeNumber: Boolean = true,
    val showAudioPills: Boolean = true,
    val thumbnailPosition: String = "left",
    val titlePosition: String = "right",
    val synopsisPosition: String = "below",
    val datePosition: String = "right_below_synopsis",
    val episodeNumberPosition: String = "overlay",
    val thumbnailSize: String = "medium",
    val titleMaxLines: Int = 1,
    val synopsisMaxLines: Int = 2,
    // ── Background toggles (per user request: show/hide element backgrounds) ──
    val showTitleBackground: Boolean = true,
    val showDateBackground: Boolean = true,
    val showAudioBackground: Boolean = true,
    val showSynopsisBackground: Boolean = true,
    /** Whether the download button is shown on the row (Agent 2 — Downloads). */
    val showDownloadButton: Boolean = true,
)
