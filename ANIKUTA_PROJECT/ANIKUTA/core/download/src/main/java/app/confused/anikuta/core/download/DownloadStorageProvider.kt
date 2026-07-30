package app.confused.anikuta.core.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import app.confused.anikuta.core.downloadidentity.DownloadIdentity
import app.confused.anikuta.core.downloadidentity.DownloadIdentityManager
import app.confused.anikuta.core.downloadidentity.DownloadIdentityStore
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
    /**
     * High-level manager for per-folder `identity.json` files.
     *
     * When non-null, [findAnimeDir] delegates to it (identity.json scan + legacy
     * suffix fallback), and [ensureEpisodeDir] writes an `identity.json` into
     * the anime folder on creation. When null, the provider falls back to the
     * pre-refactor suffix-match behavior (legacy folders only) — kept for
     * backward compat with any caller that doesn't wire the manager in.
     *
     * DOWNLOAD-IDENTITY-STORAGE-UPDATE: this is the seam that decouples folder
     * names from anime identity. Folder names are now just the sanitized title
     * (no `[contentId]` bracket suffix); all identity lives in identity.json.
     */
    private val downloadIdentityManager: DownloadIdentityManager? = null,
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

    /**
     * The `anime/` directory inside the download root (`<root>/ANIKUTA/downloads/anime/`).
     *
     * Creates the directory chain if missing. Returns null if no download
     * folder is configured or any segment couldn't be created.
     *
     * Used by [DownloadIdentityManager] (via the `animeBaseDir` lambda wired in
     * `DownloadAppModule`) to scan all anime folders for identity.json files.
     */
    fun getAnimeBaseDir(): DocumentFile? {
        val root = rootTree() ?: return null
        val anikutaDir = ensureDir(root, "ANIKUTA") ?: return null
        val downloadsDir = ensureDir(anikutaDir, "downloads") ?: return null
        return ensureDir(downloadsDir, "anime")
    }

    // ── Path/folder-name helpers ──

    /**
     * `"Jujutsu Kaisen"` — title sanitised for filesystem use. NO content_id
     * bracket (the identity lives in `identity.json` next to the episode
     * folders, NOT in the folder name).
     *
     * DOWNLOAD-IDENTITY-STORAGE-UPDATE: previously returned
     * `"$safeTitle [${sanitizeContentIdForFolder(contentId)}]"` (e.g.
     * `"Jujutsu Kaisen [al-101522]"`). The bracket suffix is now redundant —
     * folder lookup goes through [findAnimeDir], which scans `anime/` + reads
     * each folder's `identity.json` (with a legacy suffix-match fallback for
     * folders created before this refactor).
     *
     * Title collisions (two anime with the same sanitized title) are tolerated
     * — the folders coexist on disk + are disambiguated by identity.json. This
     * is the whole point of the refactor: the folder name is human-readable,
     * identity is machine-readable, and they no longer fight each other.
     */
    fun animeFolderName(anime: DownloadAnimeInfo): String {
        val safeTitle = sanitizeFileName(anime.title.ifBlank { "Unknown" })
        return safeTitle
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

    /**
     * Ensures `<root>/ANIKUTA/downloads/anime/<Anime Title>/Episode NNN/data/subtitles/`
     * exists. Returns the Episode dir.
     *
     * DOWNLOAD-IDENTITY-STORAGE-UPDATE: after creating the anime folder
     * (`showDir`), writes an `identity.json` into it on first creation. The
     * identity carries the anime's contentId + sourceId + sourceUrl + title +
     * cover info, decoupling the folder NAME (just the title) from the folder
     * IDENTITY (everything else). Subsequent link/unlink/switch operations
     * update identity.json in place — no folder rename, no orphaned downloads.
     *
     * The `sourceId` + `sourceUrl` params carry the request's source identity.
     * They default to `0L` + `""` for backward compat with callers that don't
     * have a DownloadRequest handy (the identity.json will be backfilled on
     * the next link/unlink/switch operation).
     */
    fun ensureEpisodeDir(
        anime: DownloadAnimeInfo,
        episode: DownloadEpisodeInfo,
        sourceId: Long = 0L,
        sourceUrl: String = "",
    ): DocumentFile? {
        val root = rootTree() ?: run {
            DownloadLogger.e("ensureEpisodeDir: no download folder configured")
            return null
        }
        val anikutaDir = ensureDir(root, "ANIKUTA") ?: return null
        val downloadsDir = ensureDir(anikutaDir, "downloads") ?: return null
        val animeDir = ensureDir(downloadsDir, "anime") ?: return null
        val showDir = ensureDir(animeDir, animeFolderName(anime)) ?: return null

        // ── Write identity.json on first folder creation ──
        // The manager's ensureIdentity is idempotent: if identity.json already
        // exists (e.g. re-downloading an episode into an existing folder), it
        // updates fields that changed (cover URL/color, title) and preserves
        // the createdAt + migration history. If the manager is null (legacy
        // path), identity.json is NOT written — the folder will be found via
        // the suffix-match fallback in [findAnimeDir] (which the manager also
        // uses when present).
        if (downloadIdentityManager != null && !DownloadIdentityStore.exists(showDir)) {
            val identity = DownloadIdentity(
                contentId = anime.contentId,
                sourceId = sourceId,
                sourceUrl = sourceUrl,
                title = anime.title,
                coverUrl = anime.coverUrl,
                coverColor = anime.coverColor?.let { String.format("#%06X", it) },
            )
            downloadIdentityManager.ensureIdentity(showDir, identity)
            DownloadLogger.i("ensureEpisodeDir: wrote identity.json " +
                "(contentId='${anime.contentId}', title='${anime.title}', " +
                "sourceId=$sourceId)")
        }

        val epDir = ensureDir(showDir, episodeFolderName(episode)) ?: return null
        ensureDir(epDir, "data") ?: return null
        ensureDir(epDir, "data/subtitles") ?: return null
        return epDir
    }

    /**
     * Finds (without creating) the Episode dir, or null if it doesn't exist.
     *
     * DOWNLOAD-IDENTITY-STORAGE-UPDATE: now delegates the anime-folder lookup
     * to [findAnimeDir] (identity.json scan + legacy suffix fallback) instead
     * of an exact-name match on `animeFolderName(anime)`. This is necessary
     * because `animeFolderName` no longer includes the `[contentId]` bracket,
     * so an exact-name match would miss legacy folders (which still have the
     * bracket). The identity-aware lookup handles both new + legacy folders
     * uniformly.
     */
    fun findEpisodeDir(anime: DownloadAnimeInfo, episode: DownloadEpisodeInfo): DocumentFile? {
        val animeDir = findAnimeDir(anime.contentId) ?: return null
        return animeDir.findFile(episodeFolderName(episode))?.takeIf { it.isDirectory }
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
        sourceId: Long = 0L,
        sourceUrl: String = "",
    ): PublishResult {
        val epDir = ensureEpisodeDir(anime, episode, sourceId = sourceId, sourceUrl = sourceUrl)
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
     * Delete a single episode by contentId + episode number.
     *
     * Used when the in-memory task queue doesn't have the task (e.g., after
     * app restart) but the file exists on disk. Finds the anime folder via
     * [findAnimeDir] (identity.json scan with fallbacks), then finds the
     * `Episode NNN/` folder by number.
     */
    fun deleteEpisodeByNumber(contentId: String, episodeNumber: Float): Boolean {
        val epDir = findEpisodeDirByNumber(contentId, episodeNumber) ?: run {
            DownloadLogger.w("deleteEpisodeByNumber: episode folder not found " +
                "(contentId=$contentId, epNum=$episodeNumber)")
            return false
        }
        val ok = epDir.delete()
        DownloadLogger.i("Deleted episode $episodeNumber for contentId=$contentId: $ok")

        // Auto-delete the anime folder if it's now empty.
        if (ok) {
            val animeDir = findAnimeDir(contentId)
            if (animeDir != null) {
                val hasEpisodes = animeDir.listFiles().any {
                    it.isDirectory && it.name?.startsWith("Episode ") == true
                }
                if (!hasEpisodes) {
                    animeDir.delete()
                    DownloadLogger.i("Auto-deleted empty anime folder for contentId=$contentId")
                }
            }
        }
        return ok
    }

    /**
     * Checks if the anime folder has no remaining episode folders. If empty,
     * deletes it. Called after [deleteEpisode] + after [deleteAnimeDownloads].
     *
     * DOWNLOAD-IDENTITY-STORAGE-UPDATE: now uses [findAnimeDir] (identity.json
     * scan + legacy suffix fallback) instead of an exact-name match on
     * `animeFolderName(anime)`. This keeps cleanup working for both new
     * (title-only names) + legacy (`Title [contentId]`) folders.
     */
    fun cleanupEmptyAnimeFolder(anime: DownloadAnimeInfo) {
        try {
            val animeDir = findAnimeDir(anime.contentId) ?: return

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

    /**
     * Delete the entire anime folder (all episodes) by content_id.
     *
     * Phase 6 (ADR-050): takes [contentId] (String) instead of anilistId (Int).
     * The folder is located via [findAnimeDir] (identity.json scan + legacy
     * suffix fallback).
     *
     * DOWNLOAD-IDENTITY-STORAGE-UPDATE: the `animeTitle` parameter is unused
     * (kept for source-compat with [DefaultDownloadManager.deleteAnimeDownloads]
     * — the legacy caller passes it for log context but [findAnimeDir] keys
     * purely off `contentId`).
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
     * Find the anime directory by content_id.
     *
     * DOWNLOAD-IDENTITY-STORAGE-UPDATE: now delegates to
     * [DownloadIdentityManager.findAnimeDir] when the manager is wired in
     * (scans `anime/` + reads each folder's `identity.json`, with a legacy
     * suffix-match fallback for folders created before this refactor). When
     * the manager is null (legacy callers / unit tests), falls back directly
     * to [legacyFindAnimeDir] (suffix match only).
     *
     * Used by [deleteAnime] + [findEpisodeDirByNumber] + [findEpisodeDir] +
     * [cleanupEmptyAnimeFolder]. All four call sites now go through the
     * identity-aware lookup, so new (title-only) + legacy (`Title [id]`)
     * folders are handled uniformly.
     */
    fun findAnimeDir(contentId: String): DocumentFile? {
        return downloadIdentityManager?.findAnimeDir(contentId) ?: legacyFindAnimeDir(contentId)
    }

    /**
     * Legacy suffix-match fallback for finding an anime directory by content_id.
     *
     * Scans the `anime/` folder for a directory whose name ends with
     * `[sanitized-contentId]`. Used by [findAnimeDir] when
     * [downloadIdentityManager] is null, AND as the inner fallback inside
     * [DownloadIdentityManager.findAnimeDir] itself (for folders created
     * before the identity.json refactor that haven't been backfilled yet).
     *
     * Kept public so the manager can call into it (the manager doesn't have
     * direct access to the SAF root tree).
     */
    fun legacyFindAnimeDir(contentId: String): DocumentFile? {
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
     * Scan all downloaded episodes for an anime by scanning the filesystem.
     *
     * Walks the on-disk `<root>/ANIKUTA/downloads/anime/<Title>/Episode NNN/`
     * tree for the given [contentId] + returns a [ScannedEpisode] for every
     * `Episode NNN/` folder that contains a `video.<ext>` file.
     *
     * Used by [DefaultDownloadManager.getDownloadedEpisodes] to cover episodes
     * that exist on disk but are NOT in the in-memory [DownloadStore] queue
     * (e.g. after an app restart where the queue was purged, or after a
     * content_id migration that re-keyed the cross-cutting stores but NOT
     * the DownloadStore).
     *
     * DOWNLOAD-STATUS-FILESYSTEM-FIX: this is the filesystem scan that makes
     * `getDownloadedEpisodes` robust against in-memory queue drift — previously
     * `getDownloadedEpisodes` returned only the in-memory COMPLETED tasks, so
     * the details page would show episodes as "not downloaded" after a restart
     * even though the files were still on disk.
     *
     * @return a list of [ScannedEpisode] (episodeNumber + videoUri + subtitleUris).
     *   Empty if no folder exists for [contentId] or no episode has a video file.
     */
    fun scanDownloadedEpisodes(contentId: String): List<ScannedEpisode> {
        val animeDir = findAnimeDir(contentId) ?: run {
            DownloadLogger.d("scanDownloadedEpisodes: no folder for contentId=$contentId")
            return emptyList()
        }
        val result = mutableListOf<ScannedEpisode>()
        for (epDir in animeDir.listFiles()) {
            if (!epDir.isDirectory) continue
            val name = epDir.name ?: continue
            // Parse "Episode NNN" → episode number.
            if (!name.startsWith("Episode ")) continue
            val epNumStr = name.removePrefix("Episode ").trim()
            // Allow fractional episode numbers (e.g. "Episode 005.5" for specials)
            // by parsing as Float. The folder-name formatter zero-pads the floor
            // (see [episodeFolderName]), so this only matches whole-number folders
            // in practice — the Float parse is forward-compatible if a future
            // change adds fractional folder names.
            val epNum = epNumStr.toFloatOrNull() ?: continue
            // Find the video file — match by prefix "video." (same convention as
            // [getVideoUri] / [isEpisodeDownloaded]).
            val videoFile = epDir.listFiles().firstOrNull {
                it.isFile && it.name?.startsWith("video.") == true
            }
            if (videoFile != null) {
                val subtitles = epDir.findFile("data")?.findFile("subtitles")?.listFiles()
                    ?.filter { it.isFile }
                    ?.map { it.uri.toString() }
                    ?: emptyList()
                result.add(ScannedEpisode(epNum, videoFile.uri.toString(), subtitles))
            }
        }
        DownloadLogger.i("scanDownloadedEpisodes: contentId=$contentId → " +
            "${result.size} episode(s) on disk")
        return result
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
    /** The server name the episode was downloaded from (e.g. "GogoAnime - Server 1"). */
    val videoServer: String = "",
    /** The audio version label (e.g. "SUB", "DUB"). */
    val videoAudio: String = "",
    /** The quality/resolution label (e.g. "1080p", "720p"). */
    val videoQuality: String = "",
)

/**
 * A downloaded episode found by a filesystem scan (no in-memory task required).
 *
 * Returned by [DownloadStorageProvider.scanDownloadedEpisodes] + merged into
 * the [DownloadedEpisode] list by [DefaultDownloadManager.getDownloadedEpisodes]
 * so episodes that exist on disk but are no longer in the in-memory
 * [DownloadStore] queue (e.g. after an app restart) are still surfaced to the
 * UI.
 *
 * DOWNLOAD-STATUS-FILESYSTEM-FIX.
 *
 * @property episodeNumber Parsed from the `Episode NNN` folder name.
 * @property videoUri The SAF content:// URI of the `video.<ext>` file.
 * @property subtitleUris SAF content:// URIs of any subtitle files in
 *   `Episode NNN/data/subtitles/`. Empty if there are no subtitles.
 */
data class ScannedEpisode(
    val episodeNumber: Float,
    val videoUri: String,
    val subtitleUris: List<String>,
)
