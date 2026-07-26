package app.anikuta.download

import android.content.Context
import android.util.Log
import app.anikuta.storage.StorageManager
import app.anikuta.util.storage.DiskUtil
import com.hippo.unifile.UniFile

/**
 * Provides the directory paths for downloaded episodes.
 *
 * Path scheme: `<downloads>/<sourceName>/<animeTitle>/<episodeName>/`
 *
 * Each episode directory contains a `.episode_url` file with the stable episode URL.
 * This file is the source of truth for matching episodes to downloads — it survives
 * metadata enrichment (which can change episode names) and app reinstalls.
 *
 * Uses [DiskUtil.buildValidFilename] to sanitize directory names for
 * filesystem compatibility. All directories are created on demand via
 * [UniFile.createDirectory] (idempotent — returns existing if present).
 *
 * @param context the application context
 * @param storageManager provides the base downloads directory (SAF)
 */
class DownloadProvider(
    private val context: Context,
    private val storageManager: StorageManager,
) {
    companion object {
        private const val TAG = "DownloadProvider"
        private const val EPISODE_URL_FILE = ".episode_url"
    }

    private val downloadsDir: UniFile?
        get() = storageManager.getDownloadsDirectory()

    /**
     * Get (or create) the source directory.
     */
    fun getSourceDir(sourceName: String): UniFile? {
        return downloadsDir?.createDirectory(DiskUtil.buildValidFilename(sourceName))
    }

    /**
     * Get (or create) the anime directory inside a source directory.
     */
    fun getAnimeDir(animeTitle: String, sourceName: String): UniFile? {
        return getSourceDir(sourceName)
            ?.createDirectory(DiskUtil.buildValidFilename(animeTitle))
    }

    /**
     * Get (or create) the episode directory inside an anime directory.
     */
    fun getEpisodeDir(episodeName: String, animeTitle: String, sourceName: String): UniFile? {
        return getAnimeDir(animeTitle, sourceName)
            ?.createDirectory(DiskUtil.buildValidFilename(episodeName.ifBlank { "Episode" }))
    }

    /**
     * Find an existing episode directory (without creating it).
     */
    fun findEpisodeDir(episodeName: String, animeTitle: String, sourceName: String): UniFile? {
        val animeDir = getAnimeDir(animeTitle, sourceName) ?: return null
        val dirName = DiskUtil.buildValidFilename(episodeName.ifBlank { "Episode" })
        return animeDir.findFile(dirName)
    }

    /**
     * Write the `.episode_url` file inside an episode directory.
     *
     * This file contains the stable episode URL, which is used to match
     * episodes to downloads even when the episode name changes (e.g., after
     * metadata enrichment). Called by the download engine when creating
     * a new download.
     */
    fun writeEpisodeUrlFile(episodeDir: UniFile, episodeUrl: String) {
        try {
            val urlFile = episodeDir.createFile(EPISODE_URL_FILE) ?: return
            urlFile.openOutputStream(false).bufferedWriter().use { it.write(episodeUrl) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not write .episode_url file: ${e.message}")
        }
    }

    /**
     * Read the `.episode_url` file from an episode directory.
     * Returns null if the file doesn't exist (old downloads before this feature).
     */
    private fun readEpisodeUrlFile(episodeDir: UniFile): String? {
        return try {
            val urlFile = episodeDir.findFile(EPISODE_URL_FILE) ?: return null
            urlFile.openInputStream().bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if an episode is downloaded (by name).
     * Kept for backward compatibility. Prefer [isEpisodeDownloadedByUrl].
     */
    fun isEpisodeDownloaded(episodeName: String, animeTitle: String, sourceName: String): Boolean {
        val episodeDir = findEpisodeDir(episodeName, animeTitle, sourceName) ?: return false
        return episodeDir.listFiles()?.any { file ->
            val name = file.name?.lowercase() ?: ""
            name.endsWith(".mkv") || name.endsWith(".mp4")
        } ?: false
    }

    /**
     * List all downloaded episodes for a given anime, keyed by episode URL.
     *
     * Scans the anime directory. For each episode directory that contains a video file:
     * - Reads the `.episode_url` file if present (new downloads) → uses episodeUrl as key
     * - Falls back to the directory name (old downloads) → uses dirName as key
     *
     * Returns a map of episodeUrl → episodeDirName. The episodeUrl is the stable
     * identifier used to match episodes across metadata changes.
     *
     * @param animeTitle the anime title
     * @param sourceName the source display name
     * @return map of episodeUrl (or dirName for old downloads) → episode directory name
     */
    fun listDownloadedEpisodesWithUrls(animeTitle: String, sourceName: String): Map<String, String> {
        val animeDir = getAnimeDir(animeTitle, sourceName) ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        animeDir.listFiles()?.forEach { episodeDir ->
            if (episodeDir.isDirectory) {
                val hasVideo = episodeDir.listFiles()?.any { file ->
                    val name = file.name?.lowercase() ?: ""
                    name.endsWith(".mkv") || name.endsWith(".mp4")
                } ?: false
                if (hasVideo) {
                    val dirName = episodeDir.name ?: return@forEach
                    val episodeUrl = readEpisodeUrlFile(episodeDir) ?: dirName
                    result[episodeUrl] = dirName
                }
            }
        }
        return result
    }

    /**
     * List all downloaded episode names for a given anime.
     * Kept for backward compatibility. Prefer [listDownloadedEpisodesWithUrls].
     */
    fun listDownloadedEpisodes(animeTitle: String, sourceName: String): Set<String> {
        return listDownloadedEpisodesWithUrls(animeTitle, sourceName).values.toSet()
    }

    /**
     * Get the downloaded video file for an episode (by name).
     * Kept for backward compatibility.
     */
    fun getDownloadedVideoFile(episodeName: String, animeTitle: String, sourceName: String): UniFile? {
        val episodeDir = findEpisodeDir(episodeName, animeTitle, sourceName) ?: return null
        return episodeDir.listFiles()?.firstOrNull { file ->
            val name = file.name?.lowercase() ?: ""
            name.endsWith(".mkv") || name.endsWith(".mp4")
        }
    }

    /**
     * Delete a downloaded episode.
     *
     * After deleting the episode directory, cleans up empty parent directories:
     * - If the anime directory has no more episode directories (with video files),
     *   the anime directory is deleted.
     * - If the source directory has no more anime directories, the source directory
     *   is deleted too.
     * This prevents orphaned empty folders from accumulating in the downloads root.
     */
    fun deleteEpisode(episodeName: String, animeTitle: String, sourceName: String): Boolean {
        val episodeDir = findEpisodeDir(episodeName, animeTitle, sourceName) ?: return true
        return try {
            episodeDir.delete()
            // Clean up empty parent directories
            cleanupEmptyDirectories(animeTitle, sourceName)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete episode dir", e)
            false
        }
    }

    /**
     * Delete an episode directory by its URL (stable identifier).
     * Uses the .episode_url file to find the correct directory even after
     * metadata enrichment changes the episode name.
     *
     * After deleting, cleans up empty parent directories (anime + source).
     */
    fun deleteEpisodeByUrl(episodeUrl: String, animeTitle: String, sourceName: String): Boolean {
        val animeDir = getAnimeDir(animeTitle, sourceName) ?: return true
        // Find the episode directory that has this URL in its .episode_url file
        val episodeDir = animeDir.listFiles()?.find { dir ->
            dir.isDirectory && readEpisodeUrlFile(dir) == episodeUrl
        } ?: run {
            // Fallback: try to find by name (old downloads without .episode_url)
            val dirName = DiskUtil.buildValidFilename(episodeUrl.substringAfterLast("/").ifBlank { episodeUrl })
            animeDir.findFile(dirName)
        } ?: return true

        return try {
            episodeDir.delete()
            cleanupEmptyDirectories(animeTitle, sourceName)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete episode dir by URL", e)
            false
        }
    }

    /**
     * Remove the anime directory and source directory if they are empty
     * (no remaining episode directories with video files).
     */
    private fun cleanupEmptyDirectories(animeTitle: String, sourceName: String) {
        try {
            val animeDir = getAnimeDir(animeTitle, sourceName) ?: return
            // Check if any episode directories with video files remain
            val hasEpisodes = animeDir.listFiles()?.any { dir ->
                dir.isDirectory && dir.listFiles()?.any { file ->
                    val name = file.name?.lowercase() ?: ""
                    name.endsWith(".mkv") || name.endsWith(".mp4")
                } ?: false
            } ?: false

            if (!hasEpisodes) {
                Log.d(TAG, "cleanupEmptyDirectories: anime dir is empty — deleting: ${animeDir.name}")
                animeDir.delete()

                // Also check if the source directory is now empty
                val sourceDir = getSourceDir(sourceName) ?: return
                val hasAnimes = sourceDir.listFiles()?.any { it.isDirectory } ?: false
                if (!hasAnimes) {
                    Log.d(TAG, "cleanupEmptyDirectories: source dir is empty — deleting: ${sourceDir.name}")
                    sourceDir.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "cleanupEmptyDirectories: could not clean up: ${e.message}")
        }
    }
}
