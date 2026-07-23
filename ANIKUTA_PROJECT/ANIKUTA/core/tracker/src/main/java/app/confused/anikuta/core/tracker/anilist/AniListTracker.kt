package app.confused.anikuta.core.tracker.anilist

import android.net.Uri
import android.util.Log
import app.confused.anikuta.core.preferences.PreferenceStore
import app.confused.anikuta.core.tracker.TrackAnimeEntry
import app.confused.anikuta.core.tracker.TrackStatus
import app.confused.anikuta.core.tracker.Tracker
import app.confused.anikuta.core.tracker.TrackerUserStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * AniList tracker implementation.
 *
 * OAuth: implicit grant (response_type=token). The token is returned in the
 * URL hash fragment (#access_token=...), NOT the query string. We extract it
 * via regex against the fragment.
 *
 * Token storage: [PreferenceStore] with key [TOKEN_KEY]. AniList implicit-grant
 * tokens don't expire (1-year validity), so no refresh logic is needed.
 *
 * All network calls run on Dispatchers.IO (inside [AniListTrackApi]).
 */
class AniListTracker(
    private val preferences: PreferenceStore,
    private val api: AniListTrackApi = AniListTrackApi(),
) : Tracker {

    override val id: Int = Tracker.ANILIST_ID
    override val name: String = "AniList"

    private val tokenPref = preferences.getString(TOKEN_KEY, "")
    private val usernamePref = preferences.getString(USERNAME_KEY, "")
    private val avatarPref = preferences.getString(AVATAR_KEY, "")
    private val userIdPref = preferences.getInt(USER_ID_KEY, 0)

    override val isLoggedIn: Boolean
        get() = tokenPref.get().isNotEmpty()

    override val username: Flow<String?> =
        usernamePref.changes().map { it.ifEmpty { null } }

    /** The avatar URL (for Profile header). Emits null when not logged in. */
    val avatar: Flow<String?> =
        avatarPref.changes().map { it.ifEmpty { null } }

    /** The stored AniList user ID (for API calls). */
    private val userId: Int get() = userIdPref.get()

    override fun getAuthUrl(): String {
        return Uri.parse("https://anilist.co/api/v2/oauth/authorize").buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "token")
            .build()
            .toString()
    }

    override suspend fun handleAuthCallback(callbackUrl: String): Boolean {
        // AniList implicit grant returns the token in the URL hash fragment:
        // anikuta://tracker-callback#access_token=<TOKEN>&token_type=Bearer&expires_in=...
        // Uri.getQueryParameter() cannot read fragments, so we use regex.
        val uri = Uri.parse(callbackUrl)
        val fragment = uri.fragment ?: return false
        val regex = "(?:access_token=)(.*?)(?:&|\$)".toRegex()
        val match = regex.find(fragment) ?: return false
        val token = match.groups[1]?.value ?: return false

        return try {
            // Fetch the viewer to get username + user ID + avatar.
            val viewer = api.fetchViewer(token)
                ?: run { Log.e(TAG, "Failed to fetch viewer after auth"); return false }

            tokenPref.set(token)
            usernamePref.set(viewer.name)
            avatarPref.set(viewer.avatar ?: "")
            userIdPref.set(viewer.id)
            Log.i(TAG, "AniList login successful: ${viewer.name} (id=${viewer.id})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "AniList auth callback failed", e)
            logout()
            false
        }
    }

    override fun logout() {
        tokenPref.set("")
        usernamePref.set("")
        avatarPref.set("")
        userIdPref.set(0)
        Log.i(TAG, "AniList logged out")
    }

    override suspend fun updateProgress(remoteAnimeId: Int, episodeNumber: Int, status: TrackStatus) {
        val token = tokenPref.get()
        if (token.isEmpty()) {
            Log.w(TAG, "updateProgress: not logged in")
            return
        }
        try {
            api.updateProgress(token, remoteAnimeId, episodeNumber, status)
        } catch (e: Exception) {
            Log.e(TAG, "updateProgress failed for mediaId=$remoteAnimeId", e)
        }
    }

    override suspend fun fetchUserAnimeList(): List<TrackAnimeEntry> {
        val token = tokenPref.get()
        if (token.isEmpty()) return emptyList()
        val uid = userId
        if (uid == 0) return emptyList()
        return api.fetchUserAnimeList(token, uid)
    }

    override suspend fun fetchUserStats(): TrackerUserStats {
        val token = tokenPref.get()
        if (token.isEmpty()) return emptyStats()
        val uid = userId
        if (uid == 0) return emptyStats()
        return api.fetchUserStats(token, uid) ?: emptyStats()
    }

    private fun emptyStats() = TrackerUserStats(
        totalAnime = 0,
        totalEpisodes = 0,
        totalMinutesWatched = 0,
        meanScore = 0.0,
        formatDistribution = emptyMap(),
        statusDistribution = emptyMap(),
        genreDistribution = emptyMap(),
        countryDistribution = emptyMap(),
        scoreDistribution = emptyMap(),
    )

    companion object {
        private const val TAG = "AnikutaAniListTracker"
        private const val TOKEN_KEY = "pref_tracker_anilist_token"
        private const val USERNAME_KEY = "pref_tracker_anilist_username"
        private const val AVATAR_KEY = "pref_tracker_anilist_avatar"
        private const val USER_ID_KEY = "pref_tracker_anilist_user_id"

        // Client ID is in BuildConfig but we also hardcode the fallback here
        // for simplicity (matches Aniyomi's value — ADR-013).
        private const val CLIENT_ID = "5338"
    }
}
