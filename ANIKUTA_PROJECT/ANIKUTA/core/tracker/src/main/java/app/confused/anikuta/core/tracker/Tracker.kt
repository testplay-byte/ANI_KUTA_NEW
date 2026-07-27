package app.confused.anikuta.core.tracker

import kotlinx.coroutines.flow.Flow

/**
 * Contract for a tracker service (AniList, MAL, etc.).
 *
 * Each tracker handles its own OAuth flow, token storage, and API calls.
 * All network calls MUST be on `Dispatchers.IO` (per RULES §9).
 */
interface Tracker {
    /** Unique tracker ID (AniList = 2, MAL = 1 — matches Aniyomi conventions). */
    val id: Int
    val name: String
    val isLoggedIn: Boolean
    val username: Flow<String?>

    /** Returns the OAuth login URL for this tracker. */
    fun getAuthUrl(): String

    /**
     * Handles the OAuth callback. Exchanges the code/token for an access token + stores it.
     * @param callbackUrl the full redirect URL (including fragment/query).
     * @return true if authentication succeeded.
     */
    suspend fun handleAuthCallback(callbackUrl: String): Boolean

    /** Logs out (clears the stored token). */
    fun logout()

    /** Updates the progress for an anime on this tracker. */
    suspend fun updateProgress(remoteAnimeId: Int, episodeNumber: Int, status: TrackStatus)

    /** Fetches the user's anime list from the tracker (for Profile stats). */
    suspend fun fetchUserAnimeList(): List<TrackAnimeEntry>

    /** Fetches the user's stats from the tracker. */
    suspend fun fetchUserStats(): TrackerUserStats

    companion object {
        const val ANILIST_ID = 2
        const val MAL_ID = 1
    }
}

/** Tracker list status (common across all trackers). */
enum class TrackStatus {
    WATCHING,
    COMPLETED,
    ON_HOLD,
    DROPPED,
    PLAN_TO_WATCH,
    REPEATING,
}

/** A single anime entry from a tracker's user list. */
data class TrackAnimeEntry(
    val remoteId: Int,
    val title: String,
    val coverUrl: String?,
    val status: TrackStatus,
    val episodesWatched: Int,
    val totalEpisodes: Int?,
    val score: Int?,
    val format: String?,
    val country: String?,
    val genres: List<String>,
)

/** Aggregated stats from a tracker. */
data class TrackerUserStats(
    val totalAnime: Int,
    val totalEpisodes: Int,
    val totalMinutesWatched: Int,
    val meanScore: Double,
    val formatDistribution: Map<String, Int>,
    val statusDistribution: Map<TrackStatus, Int>,
    val genreDistribution: Map<String, Int>,
    val countryDistribution: Map<String, Int>,
    val scoreDistribution: Map<Int, Int>,
)
