package app.confused.anikuta.core.tracker.mal

import kotlinx.serialization.Serializable

/**
 * MAL OAuth token response (stored serialized in PreferenceStore).
 *
 * MAL uses epoch SECONDS for [createdAt] (unlike AniList which uses millis).
 * [expiresIn] is in seconds (typically 3600 = 1 hour).
 * [refreshToken] is valid for 31 days.
 */
@Serializable
data class MalOAuth(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Int = 3600,
    val createdAt: Long = System.currentTimeMillis() / 1000,
) {
    /** True if the access token has expired (with a 60-second safety margin). */
    fun isExpired(): Boolean {
        val now = System.currentTimeMillis() / 1000
        return createdAt + (expiresIn - 60) < now
    }
}

/** MAL user info returned by GET /users/@me. */
data class MalUser(
    val id: Int,
    val name: String,
)
