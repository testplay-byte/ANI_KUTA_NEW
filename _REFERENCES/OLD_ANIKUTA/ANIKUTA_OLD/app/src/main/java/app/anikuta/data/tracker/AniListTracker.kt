package app.anikuta.data.tracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import app.anikuta.core.preference.PreferenceStore
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * ANI-KUTA AniList tracker — OAuth login + progress sync (Phase 6 tasks 6.7-6.11).
 *
 * Uses aniyomi's AniList client ID (5338) for fast start (Q2 decision).
 * The client ID is a single config constant — swapping to our own later is
 * one line. The redirect URI must match what client ID 5338 is registered
 * with on AniList, which is `aniyomi://anilist-auth`.
 */
class AniListTracker(
    private val preferenceStore: PreferenceStore,
) {
    companion object {
        private const val TAG = "AniListTracker"
        private const val CLIENT_ID = "5338"
        private const val API_URL = "https://graphql.anilist.co/"
        private const val AUTH_URL = "https://anilist.co/api/v2/oauth/authorize?client_id=$CLIENT_ID&response_type=token"
    }

    private val networkHelper: NetworkHelper = Injekt.get()

    fun accessToken(): String? = preferenceStore.getString("anilist_access_token", "").get().takeIf { it.isNotBlank() }
    fun username(): String? = preferenceStore.getString("anilist_username", "").get().takeIf { it.isNotBlank() }
    fun isLoggedIn(): Boolean = accessToken() != null

    fun startLogin(context: Context) {
        Log.d(TAG, "Starting AniList OAuth login")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AUTH_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun handleOAuthCallback(uri: Uri): Boolean {
        val fragment = uri.fragment ?: return false
        Log.d(TAG, "OAuth callback fragment received")
        val params = fragment.split("&").associate {
            val (key, value) = it.split("=", limit = 2)
            key to value
        }
        val token = params["access_token"] ?: return false
        preferenceStore.getString("anilist_access_token", "").set(token)
        Log.d(TAG, "✅ AniList access token stored")
        return true
    }

    fun logout() {
        preferenceStore.getString("anilist_access_token", "").set("")
        preferenceStore.getString("anilist_username", "").set("")
        Log.d(TAG, "Logged out")
    }

    suspend fun updateProgress(anilistId: Int, episodeNumber: Int): Boolean = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext false
        try {
            val mutation = "mutation { SaveMediaListEntry(mediaId: $anilistId, progress: $episodeNumber, status: CURRENT) { id progress } }"
            val jsonBody = """{"query":"${mutation.replace("\"", "\\\"")}"}"""
            val response = networkHelper.client.newCall(
                POST(API_URL, body = jsonBody.toRequestBody("application/json".toMediaType()),
                    headers = okhttp3.Headers.headersOf("Authorization", "Bearer $token"))
            ).execute()
            if (response.isSuccessful) { Log.d(TAG, "✅ Progress updated: anime=$anilistId ep=$episodeNumber"); true }
            else { Log.e(TAG, "Progress update failed: ${response.code}"); false }
        } catch (e: Exception) { Log.e(TAG, "Progress update failed", e); false }
    }

    suspend fun updateStatus(anilistId: Int, status: String): Boolean = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext false
        try {
            val mutation = "mutation { SaveMediaListEntry(mediaId: $anilistId, status: $status) { id status } }"
            val jsonBody = """{"query":"${mutation.replace("\"", "\\\"")}"}"""
            val response = networkHelper.client.newCall(
                POST(API_URL, body = jsonBody.toRequestBody("application/json".toMediaType()),
                    headers = okhttp3.Headers.headersOf("Authorization", "Bearer $token"))
            ).execute()
            response.isSuccessful
        } catch (e: Exception) { Log.e(TAG, "Status update failed", e); false }
    }

    suspend fun fetchUsername(): String? = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext null
        try {
            val query = """{"query":"{ Viewer { name } }"}"""
            val response = networkHelper.client.newCall(
                POST(API_URL, body = query.toRequestBody("application/json".toMediaType()),
                    headers = okhttp3.Headers.headersOf("Authorization", "Bearer $token"))
            ).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val nameRegex = """"name"\s*:\s*"([^"]+)"""".toRegex()
                val name = nameRegex.find(body)?.groupValues?.get(1)
                if (name != null) preferenceStore.getString("anilist_username", "").set(name)
                name
            } else null
        } catch (e: Exception) { Log.e(TAG, "Fetch username failed", e); null }
    }
}
