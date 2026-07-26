package app.confused.anikuta.core.backup.provider

import android.util.Log
import app.confused.anikuta.core.backup.BackupCategory
import app.confused.anikuta.core.backup.BackupEntry
import app.confused.anikuta.core.backup.BackupProvider
import app.confused.anikuta.core.database.AnikutaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "AnikutaBackup"

/**
 * Collects cover image URLs for library anime (the actual image download is
 * performed by [app.confused.anikuta.core.backup.CoverDownloader] in the
 * BackupManager, which passes the bytes to the format's `write()` method).
 *
 * Export reads library anime cover URLs. Import is a no-op — cover image bytes
 * are extracted from the backup zip by the BackupManager (via
 * `format.readCovers()`) and saved to the app's cache directory separately.
 *
 * The [BackupEntry.CoverImages] entry records which anilistIds have bundled
 * covers + their original URLs (for reference/debugging).
 */
class CoverImageProvider(
    private val database: AnikutaDatabase,
) : BackupProvider {

    override val id: String = BackupCategory.COVER_IMAGES.id

    override suspend fun export(): BackupEntry = withContext(Dispatchers.IO) {
        try {
            val favorites = database.animesQueries
                .selectFavorites(BackupMappers::mapAnime)
                .executeAsList()
            val covers = mutableMapOf<String, String>()
            favorites.forEach { anime ->
                val anilistId = anime.anilistId
                val coverUrl = anime.coverUrl ?: anime.thumbnailUrl
                if (anilistId != null && !coverUrl.isNullOrBlank()) {
                    covers[anilistId.toString()] = coverUrl
                }
            }
            Log.i(TAG, "CoverImages export: ${covers.size} cover URLs collected")
            BackupEntry.CoverImages(covers = covers)
        } catch (e: Exception) {
            Log.e(TAG, "CoverImages export failed", e)
            BackupEntry.CoverImages()
        }
    }

    override suspend fun import(entry: BackupEntry): Boolean = withContext(Dispatchers.IO) {
        require(entry is BackupEntry.CoverImages) { "Expected CoverImages entry, got ${entry.providerId}" }
        // Cover bytes are handled by the BackupManager (format.readCovers()).
        // This provider just records the URL mapping for reference.
        Log.i(TAG, "CoverImages import: ${entry.covers.size} cover references (bytes handled separately)")
        entry.covers.isNotEmpty()
    }
}

/**
 * HTTP downloader for cover images. Uses OkHttp with a short timeout.
 *
 * Created as a standalone class (not a BackupProvider) so the BackupManager
 * can call it during the backup write phase to download + bundle cover bytes.
 */
class CoverDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Downloads a cover image from the given URL.
     * @return the image bytes, or null if the download failed.
     */
    suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Cover download failed: HTTP ${response.code} for $url")
                    return@withContext null
                }
                response.body?.bytes()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Cover download failed: ${e.message} for $url")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Cover download error: ${e.message} for $url")
            null
        }
    }

    /**
     * Downloads multiple covers concurrently (bounded parallelism).
     * @param urls map of anilistId → cover URL.
     * @return map of anilistId → image bytes (only successful downloads).
     */
    suspend fun downloadAll(urls: Map<Int, String>): Map<Int, ByteArray> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<Int, ByteArray>()
        // Download sequentially to avoid hammering the AniList CDN.
        // (Could parallelize with a bounded coroutine dispatcher if needed.)
        urls.forEach { (anilistId, url) ->
            val bytes = download(url)
            if (bytes != null && bytes.isNotEmpty()) {
                results[anilistId] = bytes
            }
        }
        Log.i(TAG, "CoverDownloader: ${results.size}/${urls.size} covers downloaded")
        results
    }
}
