package app.anikuta.extension.anime.api

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.model.ExtensionRepo
import app.anikuta.extension.ExtensionUpdateNotifier
import app.anikuta.extension.anime.AnimeExtensionManager
import app.anikuta.extension.anime.model.AnimeExtension
import app.anikuta.extension.anime.util.AnimeExtensionLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.injectLazy

/**
 * Fetches extension listings from extension repos.
 * Clean rewrite based on aniyomi's AnimeExtensionApi.
 */
internal class AnimeExtensionApi {

    private val networkService: NetworkHelper by injectLazy()
    private val getExtensionRepo: GetAnimeExtensionRepo by injectLazy()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun findExtensions(): List<AnimeExtension.Available> = withContext(Dispatchers.IO) {
        try {
            val repos = getExtensionRepo.getAll()
            repos.map { repo ->
                async { getExtensionsFromRepo(repo) }
            }.awaitAll().flatten()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find extensions", e)
            emptyList()
        }
    }

    private suspend fun getExtensionsFromRepo(repo: ExtensionRepo): List<AnimeExtension.Available> {
        val repoBaseUrl = repo.baseUrl
        return try {
            val response = networkService.client
                .newCall(GET("$repoBaseUrl/index.min.json"))
                .execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to fetch extensions from $repoBaseUrl: ${response.code}")
                return emptyList()
            }

            val body = response.body?.string() ?: return emptyList()
            val extensions = json.decodeFromString(
                ListSerializer(AnimeExtensionJsonObject.serializer()),
                body,
            )

            extensions
                .filter {
                    val libVersion = it.extractLibVersion()
                    libVersion >= AnimeExtensionLoader.LIB_VERSION_MIN &&
                    libVersion <= AnimeExtensionLoader.LIB_VERSION_MAX
                }
                .map {
                    AnimeExtension.Available(
                        name = it.name.substringAfter("Aniyomi: "),
                        pkgName = it.pkg,
                        versionName = it.version,
                        versionCode = it.code,
                        libVersion = it.extractLibVersion(),
                        lang = it.lang,
                        isNsfw = it.nsfw == 1,
                        sources = it.sources?.map { src ->
                            AnimeExtension.Available.AnimeSource(
                                id = src.id,
                                lang = src.lang,
                                name = src.name,
                                baseUrl = src.baseUrl,
                            )
                        } ?: emptyList(),
                        apkName = it.apk,
                        iconUrl = "$repoBaseUrl/icon/${it.pkg}.png",
                        repoUrl = repoBaseUrl,
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get extensions from $repoBaseUrl", e)
            emptyList()
        }
    }

    fun getApkUrl(extension: AnimeExtension.Available): String {
        return "${extension.repoUrl}/apk/${extension.apkName}"
    }

    private fun AnimeExtensionJsonObject.extractLibVersion(): Double {
        return try {
            version.substringBeforeLast('.').toDouble()
        } catch (e: Exception) {
            0.0
        }
    }

    companion object {
        private const val TAG = "AnimeExtApi"
    }
}

@Serializable
private data class AnimeExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<AnimeExtensionSourceJsonObject>?,
)

@Serializable
private data class AnimeExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)
