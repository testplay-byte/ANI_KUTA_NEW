package app.confused.anikuta.core.appupdate

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * GitHub Releases-based update source.
 *
 * # How it works
 *
 * 1. Fetches the latest release from the GitHub API:
 *    `GET https://api.github.com/repos/{owner}/{repo}/releases/latest`
 * 2. Parses the response for:
 *    - `tag_name` → version name (strips `v` prefix)
 *    - `name` → release name
 *    - `body` → changelog
 *    - `published_at` → release date (ISO 8601 → epoch ms)
 *    - `assets` → finds the first `.apk` asset → `browser_download_url` + `size`
 * 3. Compares the release's version code (derived from version name) with the
 *    installed version code. If newer → returns [AppUpdateInfo].
 *
 * # Version comparison
 *
 * GitHub releases use semantic versioning (`vMAJOR.MINOR.PATCH`). This source
 * converts the version name to a version code by:
 * `code = major * 10000 + minor * 100 + patch`
 * (e.g., "0.2.1" → 201, "1.0.0" → 10000)
 *
 * If the version name can't be parsed, the source falls back to string comparison.
 *
 * # Rate limiting
 *
 * The GitHub API has a rate limit of 60 requests/hour for unauthenticated
 * requests. This is sufficient for an app that checks once on startup. If
 * more frequent checks are needed, add a GitHub token via the `Authorization`
 * header (raises to 5000/hour).
 *
 * @param owner the GitHub repo owner (e.g., "testplay-byte").
 * @param repo the GitHub repo name (e.g., "ANI_KUTA_NEW").
 * @param client the OkHttp client (shared with the app for connection pooling).
 */
class GitHubUpdateSource(
    private val owner: String,
    private val repo: String,
    private val client: OkHttpClient,
) : UpdateSource {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override val id: String = "github"

    override suspend fun fetchLatestUpdate(
        currentVersionCode: Long,
        currentVersionName: String,
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        Log.i(TAG, "fetchLatestUpdate: GET $url (current=$currentVersionName/$currentVersionCode)")

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ANIKUTA-App-Update-Checker")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "fetchLatestUpdate: HTTP ${response.code} ${response.message}")
                response.close()
                return@withContext null
            }

            val body = response.body?.string() ?: run {
                Log.w(TAG, "fetchLatestUpdate: empty response body")
                return@withContext null
            }

            val release = try {
                json.decodeFromString<GitHubRelease>(body)
            } catch (e: Exception) {
                Log.e(TAG, "fetchLatestUpdate: failed to parse JSON", e)
                return@withContext null
            }

            // Extract version name from tag_name (strip optional 'v' prefix).
            val versionName = release.tagName.removePrefix("v").removePrefix("V").trim()
            if (versionName.isBlank()) {
                Log.w(TAG, "fetchLatestUpdate: empty tag_name '${release.tagName}'")
                return@withContext null
            }

            // Derive version code from version name.
            val versionCode = parseVersionCode(versionName)
            if (versionCode <= currentVersionCode) {
                Log.i(TAG, "fetchLatestUpdate: no update available " +
                    "(latest=$versionName/$versionCode <= current=$currentVersionName/$currentVersionCode)")
                return@withContext null
            }

            // Find the first .apk asset.
            val apkAsset = release.assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            if (apkAsset == null) {
                Log.w(TAG, "fetchLatestUpdate: no APK asset in release ${release.tagName}")
                return@withContext null
            }

            val releaseDate = parseIsoDate(release.publishedAt)

            Log.i(TAG, "fetchLatestUpdate: update available! " +
                "$versionName/$versionCode (apk=${apkAsset.name}, ${apkAsset.size} bytes)")

            AppUpdateInfo(
                versionName = versionName,
                versionCode = versionCode,
                downloadUrl = apkAsset.browserDownloadUrl,
                changelog = release.body ?: "No changelog provided.",
                releaseDate = releaseDate,
                source = id,
                apkSizeBytes = apkAsset.size,
                releaseName = release.name,
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchLatestUpdate: network error", e)
            null
        }
    }

    /**
     * Parses a semantic version string ("MAJOR.MINOR.PATCH") into a comparable
     * long: `major * 10000 + minor * 100 + patch`.
     *
     * Handles pre-release suffixes (e.g., "1.0.0-beta1" → 10000, ignoring the suffix).
     * Returns 0 if parsing fails.
     */
    private fun parseVersionCode(versionName: String): Long {
        val cleanName = versionName.substringBefore("-").substringBefore("+").trim()
        val parts = cleanName.split(".")
        return try {
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            major * 10000L + minor * 100L + patch
        } catch (e: Exception) {
            Log.w(TAG, "parseVersionCode: failed to parse '$versionName'", e)
            0L
        }
    }

    /** Parses an ISO 8601 date string (e.g., "2025-01-15T10:30:00Z") to epoch ms. */
    private fun parseIsoDate(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return try {
            // Use java.time (available on API 26+, which is our minSdk).
            java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                java.time.LocalDateTime.parse(iso).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            } catch (e2: Exception) {
                Log.w(TAG, "parseIsoDate: failed to parse '$iso'", e2)
                0L
            }
        }
    }

    @Serializable
    private data class GitHubRelease(
        val tagName: String,
        val name: String? = null,
        val body: String? = null,
        val publishedAt: String? = null,
        val assets: List<GitHubAsset>? = null,
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        val size: Long,
        val browserDownloadUrl: String,
    )

    private companion object {
        private const val TAG = "AnikutaGitHubUpdate"
    }
}
