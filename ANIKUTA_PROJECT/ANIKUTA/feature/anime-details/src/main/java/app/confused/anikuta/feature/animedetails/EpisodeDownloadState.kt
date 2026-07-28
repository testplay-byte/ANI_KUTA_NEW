package app.confused.anikuta.feature.animedetails

/**
 * The download state of a single episode, for the episode-row UI.
 *
 * Defined in `:feature:anime-details` (NOT `:core:download`) so the feature
 * module stays decoupled from the download engine. The host (MainActivity)
 * collects [app.confused.anikuta.core.download.DownloadManager.episodeDownloadStates]
 * and maps each [app.confused.anikuta.core.download.DownloadTask] to this
 * sealed type, then passes a lookup lambda into [EpisodesSection].
 *
 * The episode row renders different controls based on this state:
 *  - [NotDownloaded] → download button
 *  - [Downloading] → progress bar + cancel button
 *  - [Downloaded] → "Downloaded" checkmark + delete button (tapping the row plays it offline)
 *  - [Error] → error icon + retry button
 *  - [Paused] → paused icon + resume button
 *  - [Queued] → "Queued" spinner + cancel button
 */
sealed interface EpisodeDownloadState {
    /** No download exists for this episode. Shows the download button. */
    data object NotDownloaded : EpisodeDownloadState

    /**
     * Resolving video sources (the phase between tapping download + the task
     * being enqueued). Shows an immediate spinner so the user knows the tap
     * registered — the resolve takes 1-3s.
     */
    data object Resolving : EpisodeDownloadState

    /** In the queue, waiting for a download slot. Shows a spinner + cancel. */
    data object Queued : EpisodeDownloadState

    /** Actively downloading. Shows a progress bar + pause/cancel. */
    data class Downloading(val progress: Int) : EpisodeDownloadState

    /** User-paused. Shows a resume + cancel. */
    data object Paused : EpisodeDownloadState

    /** Failed. Shows an error icon + retry + cancel. */
    data class Error(val message: String?) : EpisodeDownloadState

    /** Completed — on disk, ready for offline playback. Shows a checkmark + delete. */
    data object Downloaded : EpisodeDownloadState
}
