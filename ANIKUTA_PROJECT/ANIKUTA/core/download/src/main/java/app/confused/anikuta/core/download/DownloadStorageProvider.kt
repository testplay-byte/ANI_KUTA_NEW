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

    /** `"Jujutsu Kaisen [101522]"` — title sanitised for filesystem + ID bracket. */
    fun animeFolderName(anime: DownloadAnimeInfo): String {
        val safeTitle = sanitizeFileName(anime.title.ifBlank { "Unknown" })
        return "$safeTitle [${anime.anilistId}]"
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

    /** Deletes the Episode NNN folder (video + subtitles + metadata). */
    fun deleteEpisode(anime: DownloadAnimeInfo, episode: DownloadEpisodeInfo): Boolean {
        val epDir = findEpisodeDir(anime, episode) ?: return false
        val ok = epDir.delete()
        DownloadLogger.i("Deleted episode ${episode.episodeNumber} for anime ${anime.anilistId}: $ok")
        return ok
    }

    /** Deletes the entire anime folder (all episodes). */
    fun deleteAnime(anilistId: Int, animeTitle: String): Boolean {
        val root = rootTree() ?: return false
        val animeDir = root.findFile("ANIKUTA")
            ?.findFile("downloads")
            ?.findFile("anime")
            ?.listFiles()
            ?.firstOrNull { it.name?.endsWith("[$anilistId]") == true }
            ?: return false
        val ok = animeDir.delete()
        DownloadLogger.i("Deleted anime folder for $anilistId: $ok")
        return ok
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
 */
@kotlinx.serialization.Serializable
data class EpisodeMetadataCache(
    val anilistId: Int,
    val animeTitle: String,
    val episodeNumber: Float,
    val episodeName: String,
    val videoUrl: String,
    val downloadedAt: Long,
    val sourceId: Long,
)
