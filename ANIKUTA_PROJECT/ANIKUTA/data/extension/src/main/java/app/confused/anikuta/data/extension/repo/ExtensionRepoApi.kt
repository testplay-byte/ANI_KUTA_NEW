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
     * index.json AND repo.json (for metadata).
     *
     * Per the old ANIKUTA project's approach:
     * 1. The URL can be a base URL or end with /index.min.json
     * 2. Fetch $baseUrl/index.min.json to verify it's a valid extension index
     * 3. Fetch $baseUrl/repo.json to get the repo's proper name + website
     *
     * @return a [RepoVerificationResult] indicating success or failure with a message.
     */
    suspend fun verifyRepo(baseUrl: String): RepoVerificationResult = withContext(Dispatchers.IO) {
        // Normalize URL: strip trailing /index.json or /index.min.json
        val cleanUrl = baseUrl
            .removeSuffix("/index.json")
            .removeSuffix("/index.min.json")
            .trimEnd('/')

        if (!cleanUrl.startsWith("http")) {
            return@withContext RepoVerificationResult.Error("URL must start with http:// or https://")
        }

        // Step 1: Verify the index exists and is valid
        val indexUrls = listOf("$cleanUrl/index.min.json", "$cleanUrl/index.json")
        var indexVerified = false
        var extensionCount = 0

        for (url in indexUrls) {
            try {
                Log.i(TAG, "Verifying repo index at: $url")
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                response.use {
                    if (!it.isSuccessful) {
                        Log.w(TAG, "HTTP ${it.code} for $url")
                        return@use
                    }
                    val body = it.body?.string().orEmpty()
                    if (body.isEmpty()) {
                        Log.w(TAG, "Empty body from $url")
                        return@use
                    }
                    val entries = try {
                        json.decodeFromString<List<RepoIndexEntry>>(body)
                    } catch (e: Exception) {
                        Log.w(TAG, "Parse failed for $url: ${e.message}")
                        return@use
                    }
                    if (entries.isEmpty()) {
                        Log.w(TAG, "Index is empty at $url")
                        return@use
                    }
                    indexVerified = true
                    extensionCount = entries.size
                    Log.i(TAG, "Index verified: $cleanUrl ($extensionCount extensions)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fetch failed for $url: ${e.message}")
            }
            if (indexVerified) break
        }

        if (!indexVerified) {
            return@withContext RepoVerificationResult.Error("Could not fetch a valid extension index from this URL. Make sure it's a valid extension repository.")
        }

        // Step 2: Fetch repo.json for the proper name + website
        var repoName = cleanUrl.substringAfterLast("/").ifEmpty { cleanUrl }
        var repoWebsite = ""

        try {
            Log.i(TAG, "Fetching repo.json from: $cleanUrl/repo.json")
            val response = client.newCall(Request.Builder().url("$cleanUrl/repo.json").build()).execute()
            response.use {
                if (it.isSuccessful) {
                    val body = it.body?.string().orEmpty()
                    if (body.isNotEmpty()) {
                        try {
                            val meta = json.decodeFromString<RepoMetaDto>(body)
                            repoName = meta.meta.name
                            repoWebsite = meta.meta.website
                            Log.i(TAG, "Repo metadata: name=$repoName, website=$repoWebsite")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse repo.json: ${e.message}")
                            // Use fallback name — not an error
                        }
                    }
                } else {
                    Log.w(TAG, "HTTP ${it.code} for repo.json — using fallback name")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch repo.json: ${e.message}")
            // Use fallback name — not an error
        }

        RepoVerificationResult.Success(cleanUrl, repoName, repoWebsite, extensionCount)
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
    data class Success(
        val cleanUrl: String,
        val repoName: String,
        val website: String,
        val extensionCount: Int,
    ) : RepoVerificationResult
    data class Error(val message: String) : RepoVerificationResult
}

/** DTO for repo.json — contains the repo's display metadata. */
@Serializable
data class RepoMetaDto(
    val meta: RepoMetaContent,
)

@Serializable
data class RepoMetaContent(
    val name: String,
    val shortName: String? = null,
    val website: String = "",
    val signingKeyFingerprint: String = "",
)

/** Builds a default OkHttpClient suitable for repo-index fetching. */
fun defaultRepoOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()
