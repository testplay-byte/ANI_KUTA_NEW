package app.confused.anikuta.data.extension.repo

import android.util.Log
import app.confused.anikuta.data.extension.model.AnimeExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches a single extension repo's `index.json` and parses it into a list of
 * [AnimeExtension.Available] entries.
 *
 * The index JSON shape (per the Aniyomi reference) is an array of objects:
 * ```
 * [{ "name": "Aniyomi: Foo", "pkg": "eu.kanade.tachiyomi.animeextension.en.foo",
 *    "apk": "foo-v1.4.1.apk", "lang": "en", "code": 12, "version": "1.4.1",
 *    "nsfw": 0, "torrent": 0,
 *    "sources": [{ "id": 12345, "lang": "en", "name": "Foo", "baseUrl": "https://..." }]
 * }]
 * ```
 *
 * Each repo's index is fetched independently (the manager calls this per repo
 * and merges results by `pkgName`).
 *
 * @param client the OkHttp client to use (shared with the rest of the app).
 * @param json   the JSON decoder (lenient: ignores unknown keys).
 */
class ExtensionRepoApi(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /**
     * Fetches [repo]'s index and returns its available extensions.
     * Returns an empty list on any network/parse error (the error is logged).
     */
    suspend fun fetchExtensions(repo: ExtensionRepo): List<AnimeExtension.Available> =
        withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(Request.Builder().url(repo.indexUrl).build()).execute()
                response.use {
                    if (!it.isSuccessful) {
                        Log.e(TAG, "HTTP ${it.code} fetching ${repo.indexUrl}")
                        return@use emptyList()
                    }
                    val body = it.body?.string().orEmpty()
                    if (body.isEmpty()) {
                        Log.w(TAG, "Empty body from ${repo.indexUrl}")
                        return@use emptyList()
                    }
                    parseIndex(body, repo.baseUrl)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch repo ${repo.baseUrl}", e)
                emptyList()
            }
        }

    /**
     * Verifies that [baseUrl] is a valid extension repository by fetching its
     * index.json and checking that it parses correctly.
     *
     * @return a [RepoVerificationResult] indicating success or failure with a message.
     */
    suspend fun verifyRepo(baseUrl: String): RepoVerificationResult = withContext(Dispatchers.IO) {
        // Normalize URL: strip trailing /index.json or /index.min.json
        val cleanUrl = baseUrl
            .removeSuffix("/index.json")
            .removeSuffix("/index.min.json")
            .trimEnd('/')

        // Try index.min.json first (smaller), then index.json
        val urls = listOf("$cleanUrl/index.min.json", "$cleanUrl/index.json")

        for (url in urls) {
            try {
                Log.i(TAG, "Verifying repo at: $url")
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                response.use {
                    if (!it.isSuccessful) {
                        Log.w(TAG, "HTTP ${it.code} for $url")
                        return@use  // Try next URL
                    }
                    val body = it.body?.string().orEmpty()
                    if (body.isEmpty()) {
                        Log.w(TAG, "Empty body from $url")
                        return@use  // Try next URL
                    }
                    // Try to parse
                    val entries = try {
                        json.decodeFromString<List<RepoIndexEntry>>(body)
                    } catch (e: Exception) {
                        Log.w(TAG, "Parse failed for $url: ${e.message}")
                        return@use  // Try next URL
                    }
                    if (entries.isEmpty()) {
                        Log.w(TAG, "Index is empty at $url")
                        return@use  // Try next URL
                    }
                    Log.i(TAG, "Repo verified: $cleanUrl (${entries.size} extensions)")
                    return@withContext RepoVerificationResult.Success(cleanUrl, entries.size)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fetch failed for $url: ${e.message}")
                // Try next URL
            }
        }

        RepoVerificationResult.Error("Could not fetch a valid index from this URL. Make sure it's a valid extension repository.")
    }

    /** Parses the index JSON body into [AnimeExtension.Available] entries. */
    internal fun parseIndex(jsonBody: String, repoBaseUrl: String): List<AnimeExtension.Available> {
        val entries = try {
            json.decodeFromString<List<RepoIndexEntry>>(jsonBody)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse index from $repoBaseUrl", e)
            return emptyList()
        }
        return entries
            .filter { entry ->
                val lib = entry.extractLibVersion()
                lib in LIB_MIN.toDouble()..LIB_MAX.toDouble()
            }
            .map { entry ->
                AnimeExtension.Available(
                    name = entry.name.substringAfter("Aniyomi: "),
                    pkgName = entry.pkg,
                    versionName = entry.version,
                    versionCode = entry.code,
                    libVersion = entry.extractLibVersion(),
                    lang = entry.lang,
                    isNsfw = entry.nsfw == 1,
                    isTorrent = entry.torrent == 1,
                    sources = entry.sources?.map { it.toMetadata() } ?: emptyList(),
                    apkName = entry.apk,
                    iconUrl = "$repoBaseUrl/icon/${entry.pkg}.png",
                    repoUrl = repoBaseUrl,
                )
            }
    }

    @Serializable
    internal data class RepoIndexEntry(
        val name: String,
        val pkg: String,
        val apk: String,
        val lang: String,
        val code: Long,
        val version: String,
        val nsfw: Int = 0,
        val torrent: Int = 0,
        val sources: List<RepoIndexSource>? = null,
    ) {
        fun extractLibVersion(): Double = version.substringBeforeLast('.').toDoubleOrNull() ?: -1.0
    }

    @Serializable
    internal data class RepoIndexSource(
        val id: Long,
        val lang: String,
        val name: String,
        val baseUrl: String,
    ) {
        fun toMetadata() = AnimeExtension.Available.AnimeSourceMetadata(id, lang, name, baseUrl)
    }

    companion object {
        private const val TAG = "AnikutaExtApi"
        private const val LIB_MIN = 12
        private const val LIB_MAX = 16
    }
}

/**
 * Result of verifying a repo URL.
 * The UI uses this to show feedback to the user before adding the repo.
 */
sealed interface RepoVerificationResult {
    data class Success(val cleanUrl: String, val extensionCount: Int) : RepoVerificationResult
    data class Error(val message: String) : RepoVerificationResult
}

/** Builds a default OkHttpClient suitable for repo-index fetching. */
fun defaultRepoOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()
