package app.confused.anikuta.core.appupdate

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Downloads APK files for app updates with progress reporting.
 *
 * # Design
 *
 * Uses OkHttp to stream the APK to a temporary file in the app's cache
 * directory. Progress is reported via a [Flow] of [DownloadProgress].
 *
 * # File location
 *
 * Downloaded APKs are stored in `context.cacheDir/updates/` with the filename
 * `anikuta-<versionName>.apk`. After download completes, the file path is
 * returned so the caller can install it via [ApkInstaller].
 *
 * # Resilience
 *
 * - Network errors → emits [DownloadProgress.error] + cleans up the partial file.
 * - Cancellation → cleans up the partial file.
 * - The caller can observe the flow and cancel the coroutine to abort.
 *
 * @param context the app context (for cache directory access).
 * @param client the OkHttp client (shared with the app).
 */
class UpdateDownloader(
    private val context: Context,
    private val client: OkHttpClient,
) {

    /**
     * Downloads the APK from [info.downloadUrl] + emits progress.
     *
     * The final emission will have [DownloadProgress.isComplete] = true (on
     * success) or [DownloadProgress.error] non-null (on failure).
     *
     * On success, the APK file path is available via the returned flow's
     * final emission's `totalBytes` (the file exists at [getApkFilePath]).
     *
     * @param info the update info (provides the download URL + version name).
     * @return a Flow of [DownloadProgress]. The caller should collect it to
     *   track progress + detect completion/errors.
     */
    fun download(info: AppUpdateInfo): Flow<DownloadProgress> = flow {
        val apkFile = getApkFile(info.versionName)
        Log.i(TAG, "download: starting download of ${info.downloadUrl} → ${apkFile.absolutePath}")

        // Clean up any existing partial file.
        if (apkFile.exists()) apkFile.delete()

        val request = Request.Builder()
            .url(info.downloadUrl)
            .header("User-Agent", "ANIKUTA-App-Update-Downloader")
            .build()

        var success = false
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(DownloadProgress.error("HTTP ${response.code} ${response.message}"))
                response.close()
                return@flow
            }

            val responseBody = response.body ?: run {
                emit(DownloadProgress.error("Empty response body"))
                response.close()
                return@flow
            }

            val totalBytes = responseBody.contentLength().takeIf { it > 0 }
            Log.i(TAG, "download: total size = ${totalBytes ?: "unknown"} bytes")

            responseBody.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesDownloaded = 0L
                    var lastEmitTime = System.currentTimeMillis()
                    var lastEmitBytes = 0L

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesDownloaded += read

                        // Emit progress at most every 200ms (avoid flooding the UI).
                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime >= EMIT_INTERVAL_MS || (totalBytes != null && bytesDownloaded >= totalBytes)) {
                            val speed = if (now > lastEmitTime) {
                                ((bytesDownloaded - lastEmitBytes) * 1000) / (now - lastEmitTime)
                            } else null
                            emit(DownloadProgress.downloading(bytesDownloaded, totalBytes, speed))
                            lastEmitTime = now
                            lastEmitBytes = bytesDownloaded
                        }
                    }
                    output.flush()
                    success = true
                }
            }

            if (success) {
                Log.i(TAG, "download: complete — ${apkFile.length()} bytes at ${apkFile.absolutePath}")
                emit(DownloadProgress.complete(apkFile.length()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "download: failed", e)
            // Clean up partial file.
            if (apkFile.exists()) apkFile.delete()
            emit(DownloadProgress.error(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Gets the file where the APK for [versionName] would be / is stored.
     * The file may not exist if the download hasn't started or failed.
     */
    fun getApkFile(versionName: String): File {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val safeVersion = versionName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        return File(updatesDir, "anikuta-$safeVersion.apk")
    }

    /**
     * Deletes all downloaded APK files (for the "clear cache" action).
     */
    fun clearAllDownloads() {
        val updatesDir = File(context.cacheDir, "updates")
        if (updatesDir.exists()) {
            updatesDir.listFiles()?.forEach { it.delete() }
        }
    }

    private companion object {
        private const val TAG = "AnikutaUpdateDownloader"
        private const val BUFFER_SIZE = 8192
        private const val EMIT_INTERVAL_MS = 200L
    }
}
