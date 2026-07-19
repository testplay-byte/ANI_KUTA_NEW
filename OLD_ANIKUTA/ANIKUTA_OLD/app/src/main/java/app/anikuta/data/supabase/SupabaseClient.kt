package app.anikuta.data.supabase

import android.util.Log
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Supabase REST client for the cache layer.
 *
 * Reads use the anon key (RLS-protected, safe to embed).
 * Writes use the service_role key (bypasses RLS — needed because anon can't write
 * to the cache tables without auth).
 *
 * If the tables don't exist yet (not created in Supabase), requests return errors
 * and the CacheManager gracefully falls back to Local + AniList.
 */
class SupabaseClient(
    private val networkHelper: NetworkHelper,
) {
    companion object {
        private const val TAG = "SupabaseClient"
    }

    private val client get() = networkHelper.client
    private val json = Json { ignoreUnknownKeys = true }

    private val projectUrl = "https://jqdgdonunmqxyxmohcvj.supabase.co"

    // anon key — for reads (RLS-protected)
    private val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImpxZGdkb251bm1xeHl4bW9oY3ZqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODMyNzMwOTEsImV4cCI6MjA5ODg0OTA5MX0.9KxORC4qdDwae2-Ena9B3iGL98Zj2fnEvwat19Gxhj8"

    // service_role key — for writes (bypasses RLS)
    // TODO (later): when we add Supabase Auth, switch to authenticated user tokens
    private val serviceRoleKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImpxZGdkb251bm1xeHl4bW9oY3ZqIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4MzI3MzA5MSwiZXhwIjoyMDk4ODQ5MDkxfQ.LIYodzwl6EgZDr1jxI7w_lC0DfPxqDYTLLnGrCDlW3Y"

    private val jsonMime = "application/json".toMediaType()

    /** Read from the homepage_cache table. */
    suspend fun getHomepageCache(cacheKey: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$projectUrl/rest/v1/homepage_cache?cache_key=eq.$cacheKey&select=cache_value")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Read failed: HTTP ${response.code} (table may not exist yet)")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val jsonArray = json.parseToJsonElement(body) as? JsonArray ?: return@withContext null
                if (jsonArray.isEmpty()) return@withContext null
                val cacheValue = jsonArray[0].jsonObject["cache_value"]
                cacheValue?.jsonPrimitive?.contentOrNull
            }
        } catch (e: Exception) {
            Log.w(TAG, "Read failed: ${e.message}")
            null  // Graceful fallback
        }
    }

    /** Write to the homepage_cache table (uses service_role key for write access). */
    suspend fun putHomepageCache(cacheKey: String, value: String, ttlMs: Long) = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("cache_key", cacheKey)
                put("cache_value", value)
                put("ttl_ms", ttlMs)
                put("updated_at", System.currentTimeMillis())
            }.toString().toRequestBody(jsonMime)

            val request = Request.Builder()
                .url("$projectUrl/rest/v1/homepage_cache")
                .header("apikey", serviceRoleKey)
                .header("Authorization", "Bearer $serviceRoleKey")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Cache written to Supabase: $cacheKey")
                } else {
                    Log.w(TAG, "Write failed: HTTP ${response.code} (table may not exist yet)")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Write failed: ${e.message}")
            // Graceful fallback — Supabase write failure doesn't break the app
        }
    }
}
