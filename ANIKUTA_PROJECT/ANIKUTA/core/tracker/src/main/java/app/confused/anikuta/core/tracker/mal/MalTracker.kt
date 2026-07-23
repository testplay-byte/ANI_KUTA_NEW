package app.confused.anikuta.core.tracker.mal

import android.net.Uri
import android.util.Log
import app.confused.anikuta.core.preferences.PreferenceStore
import app.confused.anikuta.core.tracker.TrackAnimeEntry
import app.confused.anikuta.core.tracker.TrackStatus
import app.confused.anikuta.core.tracker.Tracker
import app.confused.anikuta.core.tracker.TrackerUserStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * MAL tracker implementation.
 *
 * OAuth: PKCE (authorization-code grant). The code_verifier is held in a
 * static var between [getAuthUrl] and [handleAuthCallback]; if the process
 * is killed mid-OAuth, the exchange fails (the user must restart).
 *
 * Token storage: [PreferenceStore] with serialized [MalOAuth] (includes
 * refresh token). Access tokens expire in 1 hour — [ensureFreshToken]
 * refreshes automatically before API calls.
 *
 * All network calls run on Dispatchers.IO (inside [MalTrackApi]).
 */
class MalTracker(
    private val preferences: PreferenceStore,
    private val api: MalTrackApi = MalTrackApi(),
    private val clientId: String = CLIENT_ID,
) : Tracker {

    override val id: Int = Tracker.MAL_ID
    override val name: String = "MyAnimeList"

    private val json = Json { ignoreUnknownKeys = true }

    private val oauthPref = preferences.getObject(
        OAUTH_KEY, null as MalOAuth?,
        { j -> if (j != null) json.encodeToString(MalOAuth.serializer(), j) else "" },
        { s -> try { if (s.isBlank()) null else json.decodeFromString(MalOAuth.serializer(), s) } catch (e: Exception) { null } },
    )
    private val usernamePref = preferences.getString(USERNAME_KEY, "")

    /** The PKCE code_verifier for the current OAuth attempt. */
    private var pendingCodeVerifier: String = ""

    override val isLoggedIn: Boolean
        get() = oauthPref.get() != null

    override val username: Flow<String?> =
        usernamePref.changes().map { it.ifEmpty { null } }

    override fun getAuthUrl(): String {
        pendingCodeVerifier = PkceUtil.generateCodeVerifier()
        return Uri.parse("https://myanimelist.net/v1/oauth2/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("code_challenge", pendingCodeVerifier)
            .appendQueryParameter("code_challenge_method", "plain")
            .build()
            .toString()
    }

    override suspend fun handleAuthCallback(callbackUrl: String): Boolean {
        // MAL redirects with the auth code in the query string:
        // anikuta://tracker-callback?code=<AUTH_CODE>
        val uri = Uri.parse(callbackUrl)
        val code = uri.getQueryParameter("code") ?: return false
        val verifier = pendingCodeVerifier
        if (verifier.isEmpty()) {
            Log.e(TAG, "No pending code_verifier — process may have been killed")
            return false
        }

        return try {
            val oauth = api.exchangeCodeForToken(code, verifier, clientId) ?: return false
            oauthPref.set(oauth)
            // Fetch username
            val user = api.fetchUser(oauth.accessToken)
            if (user != null) {
                usernamePref.set(user.name)
            }
            pendingCodeVerifier = ""
            Log.i(TAG, "MAL login successful: ${user?.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "MAL auth callback failed", e)
            logout()
            false
        }
    }

    override fun logout() {
        oauthPref.set(null)
        usernamePref.set("")
        pendingCodeVerifier = ""
        Log.i(TAG, "MAL logged out")
    }

    override suspend fun updateProgress(remoteAnimeId: Int, episodeNumber: Int, status: TrackStatus) {
        val token = ensureFreshToken() ?: run {
            Log.w(TAG, "updateProgress: not logged in or token refresh failed")
            return
        }
        try {
            api.updateProgress(token, remoteAnimeId, episodeNumber, status)
        } catch (e: Exception) {
            Log.e(TAG, "updateProgress failed for animeId=$remoteAnimeId", e)
        }
    }

    override suspend fun fetchUserAnimeList(): List<TrackAnimeEntry> {
        val token = ensureFreshToken() ?: return emptyList()
        return api.fetchUserAnimeList(token)
    }

    override suspend fun fetchUserStats(): TrackerUserStats {
        val token = ensureFreshToken() ?: return emptyStats()
        val list = api.fetchUserAnimeList(token)
        return api.deriveStatsFromList(list)
    }

    /**
     * Returns a valid (non-expired) access token, refreshing if needed.
     * Returns null if not logged in or refresh fails.
     */
    private suspend fun ensureFreshToken(): String? {
        val oauth = oauthPref.get() ?: return null
        if (!oauth.isExpired()) return oauth.accessToken
        Log.i(TAG, "Access token expired, refreshing...")
        return try {
            val refreshed = api.refreshToken(oauth.refreshToken, clientId) ?: run {
                Log.e(TAG, "Token refresh failed — logging out")
                logout()
                return null
            }
            oauthPref.set(refreshed)
            refreshed.accessToken
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh exception", e)
            logout()
            null
        }
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
        private const val TAG = "AnikutaMalTracker"
        private const val OAUTH_KEY = "pref_tracker_mal_oauth"
        private const val USERNAME_KEY = "pref_tracker_mal_username"
        private const val CLIENT_ID = "686b980ff4240fccce7f6a654cea07ce"
    }
}
