package app.confused.anikuta.core.appupdate

import kotlinx.serialization.Serializable

/**
 * Information about an available app update.
 *
 * Produced by an [UpdateSource] (e.g., [GitHubUpdateSource]) and consumed by
 * [AppUpdateManager] + the UI.
 *
 * @param versionName the semantic version string (e.g., "0.2.0"). Parsed from
 *   the release tag (GitHub: `tag_name` with optional `v` prefix stripped).
 * @param versionCode the numeric version code (for comparison). Parsed from
 *   the release if available, otherwise derived from the version name.
 * @param downloadUrl the direct APK download URL. For GitHub, this is the
 *   `browser_download_url` of the first `.apk` asset in the release.
 * @param changelog the release notes / description (GitHub: `body`).
 * @param releaseDate epoch milliseconds of the release publish date.
 * @param source the source identifier (e.g., "github", "custom").
 * @param apkSizeBytes the APK file size in bytes (for display), or null if unknown.
 * @param releaseName the human-readable release name (GitHub: `name` field).
 */
@Serializable
data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val changelog: String,
    val releaseDate: Long,
    val source: String,
    val apkSizeBytes: Long? = null,
    val releaseName: String? = null,
)

/**
 * Download progress for an APK update.
 *
 * Emitted as a Flow by [UpdateDownloader].
 *
 * @param bytesDownloaded how many bytes have been downloaded so far.
 * @param totalBytes the total APK size, or null if unknown (streaming).
 * @param percent 0–100 (computed from bytesDownloaded/totalBytes), or null
 *   if totalBytes is unknown.
 * @param speedBytesPerSec the current download speed, or null if not measured.
 * @param isComplete true when the download has finished.
 * @param error an error message if the download failed, or null.
 */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val percent: Int?,
    val speedBytesPerSec: Long?,
    val isComplete: Boolean,
    val error: String?,
) {
    companion object {
        fun downloading(bytesDownloaded: Long, totalBytes: Long?, speed: Long? = null): DownloadProgress {
            val percent = if (totalBytes != null && totalBytes > 0) {
                ((bytesDownloaded.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100)
            } else null
            return DownloadProgress(bytesDownloaded, totalBytes, percent, speed, isComplete = false, error = null)
        }
        fun complete(totalBytes: Long?): DownloadProgress =
            DownloadProgress(totalBytes ?: 0, totalBytes, 100, null, isComplete = true, error = null)
        fun error(message: String): DownloadProgress =
            DownloadProgress(0, null, null, null, isComplete = false, error = message)
    }
}

/**
 * A downloaded APK file record (for the "downloaded versions" list in settings).
 *
 * @param versionName the version this APK corresponds to.
 * @param filePath the absolute path to the downloaded APK file.
 * @param downloadedAt epoch milliseconds when the download completed.
 * @param sizeBytes the file size.
 * @param source the update source (e.g., "github").
 */
@Serializable
data class DownloadedApk(
    val versionName: String,
    val filePath: String,
    val downloadedAt: Long,
    val sizeBytes: Long,
    val source: String,
)
