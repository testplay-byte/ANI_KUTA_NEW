package app.confused.anikuta.core.tracker

import android.util.Log
import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AnikutaTracker"

/**
 * Implementation of [TrackerBackupProvider] — reads/writes tracker OAuth tokens
 * (AniList + MAL) + animetrack table bindings.
 *
 * Token storage (SharedPreferences keys — MUST match the tracker implementations):
 * - `pref_tracker_anilist_token` — AniList access token (String)
 * - `pref_tracker_anilist_username` — AniList username (String)
 * - `pref_tracker_anilist_avatar` — AniList avatar URL (String)
 * - `pref_tracker_anilist_user_id` — AniList user ID (Int)
 * - `pref_tracker_mal_oauth` — MAL OAuth JSON (String)
 * - `pref_tracker_mal_username` — MAL username (String)
 *
 * Bindings are read/written via [TrackRepository] (the `animetrack` SQLDelight table).
 *
 * **Security:** OAuth tokens are sensitive data. They are included in backups
 * so users don't have to re-login after restore. The backup file should be
 * treated as sensitive. Future: encrypt the backup (ADR-028).
 */
class TrackerBackupProviderImpl(
    private val preferenceStore: PreferenceStore,
    private val trackRepository: TrackRepository,
) : TrackerBackupProvider {

    override suspend fun export(): TrackerBackupData = withContext(Dispatchers.IO) {
        try {
            val anilistToken = preferenceStore.getString(KEY_ANILIST_TOKEN, "").get()
            val anilistUsername = preferenceStore.getString(KEY_ANILIST_USERNAME, "").get()
            val anilistAvatar = preferenceStore.getString(KEY_ANILIST_AVATAR, "").get()
            val anilistUserId = preferenceStore.getInt(KEY_ANILIST_USER_ID, 0).get()
            val malOAuth = preferenceStore.getString(KEY_MAL_OAUTH, "").get()
            val malUsername = preferenceStore.getString(KEY_MAL_USERNAME, "").get()
            val bindings = trackRepository.getAllTracks()

            Log.i(TAG, "Tracker backup export: ${bindings.size} bindings, " +
                "anilist=${anilistToken.isNotEmpty()}, mal=${malOAuth.isNotEmpty()}")
            TrackerBackupData(
                anilistToken = anilistToken,
                anilistUsername = anilistUsername,
                anilistUserId = anilistUserId,
                malOAuthJson = malOAuth.ifBlank { null },
                malUsername = malUsername,
                bindings = bindings,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Tracker backup export failed", e)
            TrackerBackupData(
                anilistToken = "",
                anilistUsername = "",
                anilistUserId = 0,
                malOAuthJson = null,
                malUsername = "",
                bindings = emptyList(),
            )
        }
    }

    override suspend fun restore(data: TrackerBackupData) {
        withContext(Dispatchers.IO) {
            try {
                // Restore AniList credentials
                if (data.anilistToken.isNotBlank()) {
                    preferenceStore.getString(KEY_ANILIST_TOKEN, "").set(data.anilistToken)
                    preferenceStore.getString(KEY_ANILIST_USERNAME, "").set(data.anilistUsername)
                    preferenceStore.getInt(KEY_ANILIST_USER_ID, 0).set(data.anilistUserId)
                    Log.i(TAG, "Tracker restore: AniList credentials restored")
                }
                // Restore MAL credentials
                if (!data.malOAuthJson.isNullOrBlank()) {
                    preferenceStore.getString(KEY_MAL_OAUTH, "").set(data.malOAuthJson)
                    preferenceStore.getString(KEY_MAL_USERNAME, "").set(data.malUsername)
                    Log.i(TAG, "Tracker restore: MAL credentials restored")
                }
                // Restore bindings
                var bindingsRestored = 0
                data.bindings.forEach { track ->
                    try {
                        trackRepository.bind(
                            animeId = track.animeId,
                            trackerId = track.trackerId.toInt(),
                            remoteId = track.remoteId.toInt(),
                            remoteUrl = track.remoteUrl,
                            lastSeen = track.lastSeen,
                            score = track.score,
                            status = track.status,
                            totalEpisodes = track.totalEpisodes,
                            displayScore = track.displayScore,
                        )
                        bindingsRestored++
                    } catch (e: Exception) {
                        Log.w(TAG, "Tracker restore: skipped binding animeId=${track.animeId} — ${e.message}")
                    }
                }
                Log.i(TAG, "Tracker restore: $bindingsRestored/${data.bindings.size} bindings restored")
            } catch (e: Exception) {
                Log.e(TAG, "Tracker restore failed", e)
            }
        }
    }

    companion object {
        // These MUST match the keys in AniListTracker.kt and MalTracker.kt
        private const val KEY_ANILIST_TOKEN = "pref_tracker_anilist_token"
        private const val KEY_ANILIST_USERNAME = "pref_tracker_anilist_username"
        private const val KEY_ANILIST_AVATAR = "pref_tracker_anilist_avatar"
        private const val KEY_ANILIST_USER_ID = "pref_tracker_anilist_user_id"
        private const val KEY_MAL_OAUTH = "pref_tracker_mal_oauth"
        private const val KEY_MAL_USERNAME = "pref_tracker_mal_username"
    }
}
