package app.anikuta.data.notification

/**
 * Notification channel IDs + notification IDs for the download system
 * and the new-episode notification system.
 *
 * Channels are created in [app.anikuta.App.onCreate].
 */
object Notifications {
    const val CHANNEL_EXTENSION_UPDATES = "extension_updates"

    // ---- Download channels ----

    /** Low importance — progress updates (updates frequently, silent). */
    const val CHANNEL_DOWNLOADER_PROGRESS = "downloader_progress"

    /** High importance — download errors (user should see these). */
    const val CHANNEL_DOWNLOADER_ERROR = "downloader_error"

    /** Default importance — download completion. */
    const val CHANNEL_DOWNLOADER_COMPLETE = "downloader_complete"

    // ---- New episode notification channels (Phase N) ----

    /** Default importance — new episode available. The main notification channel. */
    const val CHANNEL_NEW_EPISODES = "new_episodes"

    /** Low importance — AniList-based "episode aired" notifications (Mode 1/3). */
    const val CHANNEL_NEW_EPISODES_ANILIST = "new_episodes_anilist"

    // ---- Notification IDs ----

    /** Foreground service notification (single, reused for active downloads). */
    const val ID_DOWNLOAD_PROGRESS = -301

    /** Per-download error notifications (offset by download hashCode for uniqueness). */
    const val ID_DOWNLOAD_ERROR_BASE = -400

    /** Per-download completion notifications. */
    const val ID_DOWNLOAD_COMPLETE_BASE = -500

    /** Per-anime new-episode notifications (offset by anilistId for uniqueness). */
    const val ID_NEW_EPISODE_BASE = -600

    /** Per-anime AniList-aired notifications (offset by anilistId for uniqueness). */
    const val ID_NEW_EPISODE_ANILIST_BASE = -700

    /** Summary notification ID for grouped new-episode notifications. */
    const val ID_NEW_EPISODE_SUMMARY = -800
}
