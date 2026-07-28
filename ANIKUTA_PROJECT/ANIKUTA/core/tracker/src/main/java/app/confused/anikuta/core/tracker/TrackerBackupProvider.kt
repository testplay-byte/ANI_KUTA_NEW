package app.confused.anikuta.core.tracker

/**
 * Interface for backup/restore of tracker data.
 *
 * Documents the data shape that must be backed up so the user doesn't lose
 * tracker logins or bindings across reinstalls. The actual backup system is
 * NOT built here — this interface is provided for future integration with
 * the backup module (ADR-028).
 *
 * Data that MUST be backed up:
 * 1. **OAuth tokens** — AniList access token, MAL OAuth object (access +
 *    refresh). Stored in [PreferenceStore] with keys:
 *    - `pref_tracker_anilist_token` (String)
 *    - `pref_tracker_anilist_username` (String)
 *    - `pref_tracker_anilist_avatar` (String)
 *    - `pref_tracker_anilist_user_id` (Int)
 *    - `pref_tracker_mal_oauth` (serialized JSON)
 *    - `pref_tracker_mal_username` (String)
 * 2. **Tracker bindings** — the `animetrack` SQLDelight table rows. Each row
 *    links a local anime to a remote tracker entry.
 */
interface TrackerBackupProvider {
    /** Export all tracker data (tokens + bindings) as a serializable object. */
    suspend fun export(): TrackerBackupData

    /** Restore tracker data from a backup. */
    suspend fun restore(data: TrackerBackupData)
}

/** Serializable backup payload for tracker data. */
data class TrackerBackupData(
    val anilistToken: String,
    val anilistUsername: String,
    val anilistUserId: Int,
    val malOAuthJson: String?,
    val malUsername: String,
    val bindings: List<AnimeTrack>,
)
