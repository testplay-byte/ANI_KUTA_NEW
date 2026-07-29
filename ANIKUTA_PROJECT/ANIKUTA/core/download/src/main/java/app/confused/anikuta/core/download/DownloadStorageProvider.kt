package app.confused.anikuta.core.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream

/**
 * Owns the on-disk download folder structure (user-selected via SAF) and all
 * file I/O for the download engine.
 *
 * Folder layout (per FOLDER-STRUCTURE-PLAN.md — AniList-first):
 * ```
 * <USER_FOLDER>/ANIKUTA/
 * └── downloads/
 *     └── anime/
 *         └── <Anime Title [anilistId]>/
 *             └── Episode NNN/
 *                 ├── video.<ext>          ← original format from the extension
 *                 └── data/
 *                     ├── subtitles/       ← ALL subtitle files
 *                     └── metadata.json    ← cached episode metadata
 * ```
 *
 * The user picks the root via SAF (`ACTION_OPEN_DOCUMENT_TREE`). We take a
 * persistable permission so the URI survives reboots. All file creation uses
 * `DocumentFile` (content:// URIs) — NEVER raw `java.io.File`, because the
 * user's folder may be on an SD card / external storage we can't reach with
 * File paths.
 *
 * **Offline playback**: MPV plays the video content URI via
 * `resolveUrlForMpv` (fd:// / real-path), so we hand out content URIs.
 *
 * All methods run on `Dispatchers.IO` (callers must be in a coroutine —
 * DocumentFile + ContentResolver I/O must not block the main thread).
 */
class DownloadStorageProvider(
    private val context: Context,
    private val preferences: DownloadPreferences,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    // ── Folder-URI permission management ──

    /**
     * Persist read/write permission for the user-selected tree URI. Called when
     * the user picks a folder via the preferences sheet's SAF launcher.
     */
    fun takeFolderPermission(treeUri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            preferences.downloadFolderUri().set(treeUri.toString())
            DownloadLogger.i("Download folder permission persisted: $treeUri")
        } catch (e: SecurityException) {
            DownloadLogger.e("Cannot persist folder permission (not granted by picker)", e)
            throw e
        }
    }

    /** The root ANIKUTA tree DocumentFile, or null if no folder is set / invalid. */
    fun rootTree(): DocumentFile? {
        val uriStr = preferences.downloadFolderUri().get()
        if (uriStr.isBlank()) return null
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(uriStr)) ?: return null
        if (!tree.canWrite()) {
            DownloadLogger.w("Download folder no longer writable (revoked?): $uriStr")
            return null
        }
        return tree
    }

    /** True iff a writable download folder is configured. */
    fun isFolderReady(): Boolean = rootTree() != null

    // ── Path/folder-name helpers ──

    /**
     * `"Jujutsu Kaisen [al-101522]"` — title sanitised for filesystem +
     * content_id bracket (sanitised: `:` → `-` so the folder name is
     * filesystem-safe + the `endsWith` lookups in [deleteAnime] +
     * [findEpisodeDirByNumber] work).
     *
     * Phase 6 (ADR-050): the bracket now contains the content_id (e.g.,
     * `al-154587` for AniList-linked, `aniyomi-123-https-...` for unlinked)
     * instead of the old anilistId.
     */
    fun animeFolderName(anime: DownloadAnimeInfo): String {
        val safeTitle = sanitizeFileName(anime.title.ifBlank { "Unknown" })
        return "$safeTitle [${sanitizeContentIdForFolder(anime.contentId)}]"
    }

    /**
     * Sanitize a content_id for use in a folder name.
     *
     * Content_ids contain `:` (e.g., `"al:154587"`) which [sanitizeFileName]
     * replaces with space — that would break `endsWith` lookups. This helper
     * replaces `:` with `-` instead, producing a stable, filesystem-safe suffix
     * (e.g., `"al-154587"`) that [deleteAnime] + [findEpisodeDirByNumber] can
     * match via `endsWith("[al-154587]")`.
     */
    private fun sanitizeContentIdForFolder(contentId: String): String {
        return contentId.replace(":", "-").replace("/", "-")
    }

    /** `"Episode 001"` — zero-padded 3-digit, floored episode number. */
    fun episodeFolderName(episode: DownloadEpisodeInfo): String {
        val n = episode.episodeNumber.toInt().coerceAtLeast(0)
        return "Episode %03d".format(n)
    }

    /** `"video.mp4"` — extension inferred from the URL, defaulting to mp4. */
    fun videoFileName(videoUrl: String): String {
        val ext = extractExtension(videoUrl)
        return "video.$ext"
    }

    // ── Directory creation ──

    /** Ensures `<root>/ANIKUTA/downloads/anime/<Anime Title [id]>/Episode NNN/data/subtitles/` exists. Returns the Episode dir. */
    fun ensureEpisodeDir(anime: DownloadAnimeInfo, episode: DownloadEpisodeInfo): DocumentFile? {
        val root = rootTree() ?: run {
            DownloadLogger.e("ensureEpisodeDir: no download folder configured")
            return null
        }
        val anikutaDir = ensureDir(root, "ANIKUTA") ?: return null
        val downloadsDir = ensureDir(anikutaDir, "downloads") ?: return null
        val animeDir = ensureDir(downloadsDir, "anime") ?: return null
        val showDir = ensureDir(animeDir, animeFolderName(anime)) ?: return null
        val epDir = ensureDir(showDir, episodeFolderName(episode)) ?: return null
        ensureDir(epDir, "data") ?: return null
        ensureDir(epDir, "data/subtitles") ?: return null
        return epDir
    }

    /** Finds (without creating) the Episode dir, or null if it doesn't exist. */
    fun findEpisodeDir(anime: DownloadAnimeInfo, episode: DownloadEpisodeInfo): DocumentFile? {
        val root = rootTree() ?: return null
        return root.findFile("ANIKUTA")
            ?.findFile("downloads")
            ?.findFile("anime")
            ?.findFile(animeFolderName(anime))
            ?.findFile(episodeFolderName(episode))
    }

    /**
     * Publishes a completed temp download from [TempDownloadCache] into the
     * user's SAF folder. This is the "atomic move" step:
     *
     * 1. Ensures the episode folder structure exists.
     * 2. Copies the temp video file → `Episode NNN/video.<ext>` in SAF.
     * 3. Copies each temp subtitle → `Episode NNN/data/subtitles/<lang>_<i>.<ext>`.
     * 4. Copies the temp metadata.json → `Episode NNN/data/metadata.json`.
     *
     * Returns [PublishResult] with the SAF content URIs (for offline playback +
     * the task record). On any failure, the episode folder is left in a partial
     * state — the caller should mark the task ERROR (the temp dir is cleaned up
     * by [TempDownloadCache.cleanupTask] regardless).
     *
     * All I/O runs on `Dispatchers.IO` (callers must be in a coroutine).
     */
    fun publishToUserFolder(
        anime: DownloadAnimeInfo,
        episode: DownloadEpisodeInfo,
        tempVideoFile: java.io.File,
        tempSubtitlesDir: java.io.File,
        tempMetadataFile: java.io.File,
        videoExtension: String,
    ): PublishResult {
        val epDir = ensureEpisodeDir(anime, episode)
            ?: return PublishResult.Error("Download folder not configured or not writable")

        try {
            // ── 1. Copy the video ──
            val videoName = "video.$videoExtension"
            epDir.findFile(videoName)?.delete() // overwrite if re-downloading
            val videoTarget = epDir.createFile("video/*", videoName)
                ?: return PublishResult.Error("Failed to create video file in SAF folder")
            context.contentResolver.openOutputStream(videoTarget.uri, "w")?.use { out ->
                tempVideoFile.inputStream().use { it.copyTo(out) }
            } ?: return PublishResult.Error("Failed to open video output stream in SAF folder")

            // ── 2. Copy subtitles ──
            val subtitleUris = mutableListOf<String>()
            val subDir = epDir.findFile("data")?.findFile("subtitles")
            if (subDir != null && tempSubtitlesDir.exists()) {
                tempSubtitlesDir.listFiles()?.forEach { tempSub ->
                    if (!tempSub.isFile) return@forEach
                    subDir.findFile(tempSub.name)?.delete()
                    val target = subDir.createFile("application/octet-stream", tempSub.name)
                    if (target != null) {
                        context.contentResolver.openOutputStream(target.uri, "w")?.use { out ->
                            tempSub.inputStream().use { it.copyTo(out) }
                        }
                        subtitleUris.add(target.uri.toString())
                    }
                }
            }

            // ── 3. Copy metadata.json ──
            if (tempMetadataFile.exists()) {
                val dataDir = epDir.findFile("data") ?: ensureDir(epDir, "data")
                if (dataDir != null) {
                    dataDir.findFile("metadata.json")?.delete()
                    val metaTarget = dataDir.createFile("application/json", "metadata.json")
                    if (metaTarget != null) {
                        context.contentResolver.openOutputStream(metaTarget.uri, "w")?.use { out ->
                            tempMetadataFile.inputStream().use { it.copyTo(out) }
                        }
                    }
                }
            }

            val videoSize = tempVideoFile.length()
            DownloadLogger.i("Published to SAF: ${anime.title} EP ${episode.episodeNumber} " +
                "($videoSize bytes, ${subtitleUris.size} subs)")
            return PublishResult.Success(
                videoUri = videoTarget.uri.toString(),
                subtitleUris = subtitleUris,
                sizeBytes = videoSize,
            )
        } catch (e: Exception) {
            DownloadLogger.e("Failed to publish download to SAF folder", e)
            return PublishResult.Error("Failed to move download to folder: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** The result of [publishToUserFolder]. */
    sealed interface PublishResult {
        data class Success(
            val videoUri: String,
            val subtitleUris: List<String>,
            val sizeBytes: Long,
        ) : PublishResult
        data class Error(val message: String) : PublishResult
    }

    // ── Output streams for the downloader ──

    /** Opens an [OutputStream] for the video file inside the episode dir. */
    fun openVideoOutputStream(
        epDir: DocumentFile,
        videoUrl: String,
    ): OutputStream? {
        val name = videoFileName(videoUrl)
        // Replace any existing partial file so we don't append to stale data.
        epDir.findFile(name)?.delete()
        val file = epDir.createFile("video/*", name) ?: run {
            DownloadLogger.e("Failed to create video file: $name")
            return null
        }
        return context.contentResolver.openOutputStream(file.uri, "w")
    }

    /** Opens an [OutputStream] for a subtitle file (named by lang + index). */
    fun openSubtitleOutputStream(
        epDir: DocumentFile,
        track: DownloadTrack,
        index: Int,
    ): OutputStream? {
        val subDir = epDir.findFile("data")?.findFile("subtitles") ?: return null
        val ext = subtitleExtension(track.url)
        val safeLang = sanitizeFileName(track.lang.ifBlank { "track" })
        val name = "${safeLang}_$index.$ext"
        val file = subDir.createFile("application/octet-stream", name) ?: return null
        return context.contentResolver.openOutputStream(file.uri, "w")
    }

    /** Writes the episode metadata JSON to `data/metadata.json`. */
    fun writeMetadata(
        epDir: DocumentFile,
        metadata: EpisodeMetadataCache,
    ) {
        try {
            val dataDir = epDir.findFile("data") ?: ensureDir(epDir, "data") ?: return
            dataDir.findFile("metadata.json")?.delete()
            val file = dataDir.createFile("application/json", "metadata.json") ?: return
            context.contentResolver.openOutputStream(file.uri, "w")?.use { os ->
                os.write(json.encodeToString(metadata).toByteArray())
            }
        } catch (e: Exception) {
            DownloadLogger.w("Failed to write metadata.json", e)
        }
    }

    // ── Content-URI queries for offline playback ──

    /** The content:// URI of the downloaded video, or null if not on disk. */
    fun getVideoUri(anime: DownloadAnimeInfo, episode: DownloadEpisodeInfo): String? {
        val epDir = findEpisodeDir(anime, episode) ?: return null
        // Find the video file — we named it video.<ext>; match by prefix "video."
        val videoFile = epDir.listFiles().firstOrNull { it.name?.startsWith("video.") == true }
        return videoFile?.uri?.toString()
    }

    /** The content:// URIs of all downloaded subtitle files for the episode. */
    fun getSubtitleUris(anime: DownloadAnimeInfo, episode: DownloadEpisodeInfo): List<String> {
        val epDir = findEpisodeDir(anime, episode) ?: return emptyList()
        val subDir = epDir.findFile("data")?.findFile("subtitles") ?: return emptyList()
        return subDir.listFiles().map { it.uri.toString() }
    }

    /** True if the video file exists on disk for this episode. */
    fun isEpisodeDownloaded(anime: DownloadAnimeInfo, episode: DownloadEpisodeInfo): Boolean {
        return getVideoUri(anime, episode) != null
    }

    /** Total bytes of the episode folder (video + subtitles + metadata). */
    fun episodeFolderSize(anime: DownloadAnimeInfo, episode: DownloadEpisodeInfo): Long {
        val epDir = findEpisodeDir(anime, episode) ?: return 0L
        return folderSize(epDir)
    }

    // ── Deletion ──

    /**
     * Deletes the Episode NNN folder (video + subtitles + metadata).
     * **Auto-cleanup:** after deleting the episode, checks if the anime folder
     * is now empty (no remaining episode folders). If so, deletes the anime
     * folder too — keeps the user's folder clean (no empty anime directories).
     */
    fun deleteEpisode(anime: DownloadAnimeInfo, episode: DownloadEpisodeInfo): Boolean {
        val epDir = findEpisodeDir(anime, episode) ?: return false
        val ok = epDir.delete()
        DownloadLogger.i("Deleted episode ${episode.episodeNumber} for anime ${anime.contentId}: $ok")

        // Auto-delete the anime folder if it's now empty.
        if (ok) {
            cleanupEmptyAnimeFolder(anime)
        }
        return ok
    }

    /**
     * Checks if the anime folder has no remaining episode folders. If empty,
     * deletes it. Called after [deleteEpisode] + after [deleteAnimeDownloads].
     */
    fun cleanupEmptyAnimeFolder(anime: DownloadAnimeInfo) {
        try {
            val root = rootTree() ?: return
            val animeDir = root.findFile("ANIKUTA")
                ?.findFile("downloads")
                ?.findFile("anime")
                ?.listFiles()
                ?.firstOrNull { it.name == animeFolderName(anime) }
                ?: return

            // Check if there are any remaining episode folders (or any files).
            val remaining = animeDir.listFiles().filterNotNull()
            if (remaining.isEmpty()) {
                animeDir.delete()
                DownloadLogger.i("Auto-deleted empty anime folder: ${anime.title} [${anime.contentId}]")
            }
        } catch (e: Exception) {
            DownloadLogger.w("Failed to cleanup empty anime folder (non-fatal)", e)
        }
    }

    /** Deletes the entire anime folder (all episodes). */
    /**
     * Delete the entire anime folder (all episodes) by content_id.
     *
     * Phase 6 (ADR-050): takes [contentId] (String) instead of anilistId (Int).
     * The folder suffix is the sanitized content_id (e.g., `[al-154587]`).
     */
    fun deleteAnime(contentId: String, animeTitle: String): Boolean {
        val root = rootTree() ?: return false
        val animeDir = findAnimeDir(contentId)
            ?: return false
        val ok = animeDir.delete()
        DownloadLogger.i("Deleted anime folder for contentId=$contentId: $ok")
        return ok
    }

    /**
     * Find the anime directory by content_id (scans the `anime/` folder for a
     * directory whose name ends with `[sanitized-contentId]`).
     *
     * Used by [deleteAnime] + the source-switching fix in
     * [DefaultDownloadManager.isEpisodeDownloaded] (falls back to a filesystem
     * scan when no in-memory task matches).
     */
    fun findAnimeDir(contentId: String): DocumentFile? {
        val root = rootTree() ?: return null
        val suffix = "[${sanitizeContentIdForFolder(contentId)}]"
        return root.findFile("ANIKUTA")
            ?.findFile("downloads")
            ?.findFile("anime")
            ?.listFiles()
            ?.firstOrNull { it.name?.endsWith(suffix) == true && it.isDirectory }
    }

    /**
     * Find a specific episode directory by content_id + episode number.
     *
     * This is the **source-switching fix**: when the user switches extension
     * source, the episodeUrl changes but the episodeNumber stays the same.
     * This method finds the on-disk `Episode NNN` folder by episode number,
     * independent of the episodeUrl.
     *
     * Used by [DefaultDownloadManager.isEpisodeDownloaded] as a filesystem
     * fallback when no in-memory task matches the new (contentId, episodeNumber).
     */
    fun findEpisodeDirByNumber(contentId: String, episodeNumber: Float): DocumentFile? {
        val animeDir = findAnimeDir(contentId) ?: return null
        val epFolderName = "Episode %03d".format(episodeNumber.toInt().coerceAtLeast(0))
        return animeDir.findFile(epFolderName)?.takeIf { it.isDirectory }
    }

    /**
     * Find a legacy anime directory (pre-Phase-6 format: `<Title [anilistId]>`).
     *
     * Used by [app.confused.anikuta.migration.DownloadMigration] to find old
     * folders that need renaming to the new `<Title [al-anilistId]>` format.
     *
     * @param oldSuffix the suffix to match (e.g., `"[154587]"`).
     */
    fun findLegacyAnimeDir(oldSuffix: String): DocumentFile? {
        val root = rootTree() ?: return null
        return root.findFile("ANIKUTA")
            ?.findFile("downloads")
            ?.findFile("anime")
            ?.listFiles()
            ?.firstOrNull { it.name?.endsWith(oldSuffix) == true && it.isDirectory }
    }

    /**
     * Rename a legacy anime folder to the new content_id format.
     *
     * Called by [app.confused.anikuta.migration.DownloadMigration] for each
     * anime that needs its folder renamed from `<Title [anilistId]>` to
     * `<Title [al-anilistId]>`.
     *
     * @param oldSuffix the legacy suffix (e.g., `"[154587]"`).
     * @param contentId the new content_id (e.g., `"al:154587"`).
     * @param title the anime title (for the new folder name).
     * @return `true` if the folder was found + renamed; `false` if not found or rename failed.
     */
    fun renameLegacyAnimeFolder(oldSuffix: String, contentId: String, title: String): Boolean {
        val animeDir = findLegacyAnimeDir(oldSuffix) ?: return false
        val safeTitle = sanitizeFileName(title.ifBlank { "Unknown" })
        val newContentIdSuffix = sanitizeContentIdForFolder(contentId)
        val newFolderName = "$safeTitle [$newContentIdSuffix]"
        val renamed = animeDir.renameTo(newFolderName)
        if (renamed) {
            DownloadLogger.i("Folder renamed: '${animeDir.name}' → '$newFolderName'")
        } else {
            DownloadLogger.w("Folder rename failed (provider may not support rename): '${animeDir.name}' → '$newFolderName'")
        }
        return renamed
    }

    // ── Internal helpers ──

    private fun ensureDir(parent: DocumentFile, name: String): DocumentFile? {
        parent.findFile(name)?.let { return it }
        return parent.createDirectory(name).also {
            if (it == null) DownloadLogger.e("Failed to create directory: $name")
        }
    }

    private fun folderSize(dir: DocumentFile): Long {
        var size = 0L
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                size += folderSize(child)
            } else {
                size += child.length()
            }
        }
        return size
    }

    private fun sanitizeFileName(name: String): String {
        // Replace filesystem-hostile chars. DocumentFile/SAF is fairly permissive
        // but `/` is always a path separator; trim trailing dots/spaces (Windows-y
        // but some SAF providers reject them).
        return name
            .replace("/", " ")
            .replace(":", " ")
            .replace("\\", " ")
            .replace("*", " ")
            .replace("?", " ")
            .replace("\"", " ")
            .replace("<", " ")
            .replace(">", " ")
            .replace("|", " ")
            .trim()
            .ifBlank { "Unknown" }
    }

    private fun extractExtension(url: String): String {
        val noQuery = url.substringBefore('?')
        val path = noQuery.substringAfterLast('/', "")
        val dot = path.lastIndexOf('.')
        if (dot < 0 || dot == path.length - 1) return "mp4"
        val ext = path.substring(dot + 1).lowercase()
        // Whitelist common video extensions; default to mp4 for unknown.
        return when (ext) {
            "mp4", "mkv", "webm", "avi", "mov", "m4v", "ts" -> ext
            else -> "mp4"
        }
    }

    private fun subtitleExtension(url: String): String {
        val noQuery = url.substringBefore('?')
        val path = noQuery.substringAfterLast('/', "")
        val dot = path.lastIndexOf('.')
        if (dot < 0) return "srt"
        return when (path.substring(dot + 1).lowercase()) {
            "ass", "srt", "vtt", "ssa", "sub" -> path.substring(dot + 1).lowercase()
            else -> "srt"
        }
    }

    companion object {
        /**
         * A best-effort readable name for a SAF tree URI string (no I/O — pure
         * URI parsing, safe on the main thread). Used by the settings sheet to
         * show the currently-selected folder name.
         *
         * SAF tree URIs look like:
         * `content://com.android.externalstorage.documents/tree/primary%3AANIKUTA%20Downloads`
         * `DocumentsContract.getTreeDocumentId` returns the decoded document ID
         * (e.g. `primary:ANIKUTA Downloads`); we take the last segment after
         * `:` or `/` as the display name. Returns null if the URI is blank or
         * unparseable.
         */
        fun folderDisplayName(uriString: String): String? {
            if (uriString.isBlank()) return null
            return try {
                val uri = Uri.parse(uriString)
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val decoded = Uri.decode(docId)
                decoded.substringAfterLast(':').substringAfterLast('/').ifBlank { decoded }
            } catch (e: Exception) {
                DownloadLogger.w("Failed to parse folder display name from URI", e)
                null
            }
        }
    }
}

/**
 * The cached episode metadata written to `data/metadata.json` alongside the
 * video. Human-readable so a user browsing the folder can identify the episode.
 *
 * **Phase 6 (ADR-050):** [contentId] replaced the old `anilistId: Int` field.
 * Existing on-disk `metadata.json` files with the old `anilistId` key parse
 * cleanly (Json `ignoreUnknownKeys = true`) — the missing `contentId` field
 * defaults to `""`. The cache is informational-only + is overwritten with the
 * new format on the next re-download, so no on-disk migration is required.
 */
@kotlinx.serialization.Serializable
data class EpisodeMetadataCache(
    val contentId: String,
    val animeTitle: String,
    val episodeNumber: Float,
    val episodeName: String,
    val videoUrl: String,
    val downloadedAt: Long,
    val sourceId: Long,
)
