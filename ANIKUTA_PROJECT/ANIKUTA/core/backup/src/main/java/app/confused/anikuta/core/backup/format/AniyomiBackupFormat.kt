package app.confused.anikuta.core.backup.format

import android.util.Log
import app.confused.anikuta.core.backup.BackupException
import app.confused.anikuta.core.backup.BackupFormat
import app.confused.anikuta.core.backup.BackupFormatType
import app.confused.anikuta.core.backup.format.aniyomi.AniyomiBackup
import app.confused.anikuta.core.backup.format.aniyomi.AniyomiBackupAnime
import app.confused.anikuta.core.backup.format.aniyomi.AniyomiBackupCategory
import app.confused.anikuta.core.backup.format.aniyomi.AniyomiBackupEpisode
import app.confused.anikuta.core.backup.format.aniyomi.AniyomiBackupAnimeTracking
import app.confused.anikuta.core.backup.format.aniyomi.AniyomiBackupAnimeHistory
import app.confused.anikuta.core.backup.format.aniyomi.AniyomiLegacyBackup
import app.confused.anikuta.core.backup.model.AnimeBackup
import app.confused.anikuta.core.backup.model.BackupContainer
import app.confused.anikuta.core.backup.model.CategoryBackup
import app.confused.anikuta.core.backup.model.EpisodeBackup
import app.confused.anikuta.core.backup.model.SourceLinkBackup
import app.confused.anikuta.core.backup.model.SourceLinkItem
import app.confused.anikuta.core.backup.model.TrackerBackupModel
import app.confused.anikuta.core.backup.model.TrackerTrackItem
import app.confused.anikuta.core.backup.model.WatchProgressBackup
import app.confused.anikuta.core.backup.model.WatchProgressItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream

private const val TAG = "AnikutaBackup"

/**
 * Reads Aniyomi protobuf backup files (`.tachibk`) — restore-only.
 *
 * Aniyomi backups are protocol-buffer-encoded (optionally gzipped). This
 * format tries two decode strategies:
 * 1. **Modern format** (`AniyomiBackup`): anime at proto field 501.
 * 2. **Legacy format** (`AniyomiLegacyBackup`): anime at proto field 3.
 *
 * This matches Aniyomi's own `BackupDecoder` which checks `isLegacyBackup`
 * first and falls back. We try modern first (more common in recent backups),
 * then legacy.
 *
 * **Matching strategy:** Aniyomi anime are matched to AniList IDs via their
 * tracker entries (AniList tracker has `syncId = 2`, `mediaId` = AniList ID).
 * Anime without AniList tracking are imported by title (the app can re-search
 * AniList on the details page).
 *
 * **Not supported (restore-only):** [write] throws — we never export in
 * Aniyomi format.
 *
 * All I/O runs on [Dispatchers.IO].
 */
class AniyomiBackupFormat : BackupFormat {

    override val type: BackupFormatType = BackupFormatType.ANIYOMI

    private val protoBuf = ProtoBuf

    override suspend fun write(
        container: BackupContainer,
        covers: Map<Int, ByteArray>,
        output: OutputStream,
    ) {
        throw UnsupportedOperationException("Aniyomi format is restore-only — ANIKUTA does not export in Aniyomi format")
    }

    /**
     * Decodes the raw Aniyomi protobuf backup (without mapping to BackupContainer).
     *
     * Used by [AniyomiBackupTranslator] which needs the raw [AniyomiBackup] to
     * resolve AniList IDs before building a BackupContainer.
     *
     * @param input the backup file stream (caller closes).
     * @return the decoded [AniyomiBackup].
     */
    suspend fun decodeRaw(input: InputStream): AniyomiBackup = withContext(Dispatchers.IO) {
        try {
            val rawBytes = input.readBytes()
            Log.i(TAG, "Aniyomi backup: read ${rawBytes.size} raw bytes")

            val protoBytes = if (isGzipped(rawBytes)) {
                Log.d(TAG, "Aniyomi backup: gzip-compressed, decompressing...")
                GZIPInputStream(rawBytes.inputStream()).use { it.readBytes() }
            } else {
                rawBytes
            }
            Log.d(TAG, "Aniyomi backup: ${protoBytes.size} bytes to decode")

            // Try modern format first (anime at proto field 501)
            try {
                Log.d(TAG, "Aniyomi backup: trying modern format (Backup)...")
                val modern = protoBuf.decodeFromByteArray(
                    AniyomiBackup.serializer(),
                    protoBytes,
                )
                if (modern.backupAnime.isNotEmpty()) {
                    Log.i(TAG, "Aniyomi backup: modern format decoded — ${modern.backupAnime.size} anime")
                    modern
                } else {
                    Log.d(TAG, "Aniyomi backup: modern format had 0 anime, trying legacy...")
                    tryLegacy(protoBytes)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Aniyomi backup: modern format failed (${e.message}), trying legacy...")
                tryLegacy(protoBytes)
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode Aniyomi backup", e)
            throw BackupException.CorruptFile("Aniyomi decode failed: ${e.message}", e)
        }
    }

    override suspend fun read(input: InputStream): BackupContainer = withContext(Dispatchers.IO) {
        try {
            // Read the full byte array (need to check for gzip magic first)
            val rawBytes = input.readBytes()
            Log.i(TAG, "Aniyomi backup: read ${rawBytes.size} raw bytes")

            val protoBytes = if (isGzipped(rawBytes)) {
                Log.d(TAG, "Aniyomi backup: gzip-compressed, decompressing...")
                GZIPInputStream(rawBytes.inputStream()).use { it.readBytes() }
            } else {
                rawBytes
            }
            Log.d(TAG, "Aniyomi backup: ${protoBytes.size} bytes to decode")

            // Try modern format first (anime at proto field 501)
            val aniyomiBackup = try {
                Log.d(TAG, "Aniyomi backup: trying modern format (Backup)...")
                val modern = protoBuf.decodeFromByteArray(
                    AniyomiBackup.serializer(),
                    protoBytes,
                )
                if (modern.backupAnime.isNotEmpty()) {
                    Log.i(TAG, "Aniyomi backup: modern format decoded — ${modern.backupAnime.size} anime")
                    modern
                } else {
                    Log.d(TAG, "Aniyomi backup: modern format had 0 anime, trying legacy...")
                    tryLegacy(protoBytes)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Aniyomi backup: modern format failed (${e.message}), trying legacy...")
                tryLegacy(protoBytes)
            }

            mapToContainer(aniyomiBackup)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read Aniyomi backup", e)
            throw BackupException.CorruptFile("Aniyomi read failed: ${e.message}", e)
        }
    }

    /**
     * Tries to decode the bytes as a legacy Aniyomi backup (anime at proto field 3).
     * Throws if legacy decode also fails.
     */
    private fun tryLegacy(protoBytes: ByteArray): AniyomiBackup {
        val legacy = protoBuf.decodeFromByteArray(
            AniyomiLegacyBackup.serializer(),
            protoBytes,
        )
        Log.i(TAG, "Aniyomi backup: legacy format decoded — ${legacy.backupAnime.size} anime")
        // Convert legacy to modern structure
        return AniyomiBackup(
            backupManga = legacy.backupManga,
            backupCategories = legacy.backupCategories,
            backupAnime = legacy.backupAnime,
            backupAnimeCategories = legacy.backupAnimeCategories,
            backupAnimeSources = legacy.backupAnimeSources,
        )
    }

    override fun detect(input: InputStream): Boolean {
        return try {
            input.mark(2)
            val header = ByteArray(2)
            val read = input.read(header)
            input.reset()
            if (read < 2) return false
            // Aniyomi backups are gzip (0x1f 0x8b) or raw protobuf.
            // We detect the gzip variant. Non-gzipped protobuf is tried as a
            // fallback by the BackupManager if ANIKUTA detection fails.
            header[0].toInt() and 0xFF == 0x1f && header[1].toInt() and 0xFF == 0x8b
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Maps a decoded [AniyomiBackup] to ANIKUTA's [BackupContainer].
     *
     * Only anime-related data is mapped (manga is ignored). Each Aniyomi anime
     * becomes an [AnimeBackup]; episodes become [EpisodeBackup]s; tracking
     * entries become [TrackerTrackItem]s; history becomes [WatchProgressItem]s;
     * categories become [CategoryBackup]s.
     */
    private fun mapToContainer(aniyomi: AniyomiBackup): BackupContainer {
        val entries = mutableListOf<app.confused.anikuta.core.backup.BackupEntry>()

        // ── Library + Anime details ──
        val animeBackups = aniyomi.backupAnime.map { ani ->
            AnimeBackup(
                sourceId = ani.source,
                url = ani.url,
                title = ani.title,
                artist = ani.artist,
                author = ani.author,
                description = ani.description,
                genre = ani.genre.joinToString(","),
                coverUrl = ani.thumbnailUrl,
                status = ani.status.toLong(),
                favorite = ani.favorite,
                dateAdded = ani.dateAdded,
                updateStrategy = ani.updateStrategy.toLong(),
                coverLastModified = ani.lastModifiedAt,
            )
        }
        if (animeBackups.isNotEmpty()) {
            entries.add(app.confused.anikuta.core.backup.BackupEntry.Library(animes = animeBackups.filter { it.favorite }))
            entries.add(app.confused.anikuta.core.backup.BackupEntry.AnimeDetails(animes = animeBackups))
        }

        // ── Episodes ──
        val episodesByAnime = mutableMapOf<String, List<EpisodeBackup>>()
        aniyomi.backupAnime.forEachIndexed { index, ani ->
            if (ani.episodes.isNotEmpty()) {
                val eps = ani.episodes.map { ep ->
                    EpisodeBackup(
                        animeId = index.toLong(), // temporary — remapped on restore
                        url = ep.url,
                        name = ep.name,
                        episodeNumber = ep.episodeNumber.toDouble(),
                        scanlator = ep.scanlator,
                        seen = ep.seen,
                        bookmark = ep.bookmark,
                        lastSecondSeen = ep.lastSecondSeen,
                        totalSeconds = ep.totalSeconds,
                        sourceOrder = ep.sourceOrder,
                        dateFetch = ep.dateFetch,
                        dateUpload = ep.dateUpload,
                        fillermark = if (ep.fillermark) "filler" else null,
                        summary = ep.summary,
                        previewUrl = ep.previewUrl,
                    )
                }
                episodesByAnime[index.toString()] = eps
            }
        }
        if (episodesByAnime.isNotEmpty()) {
            entries.add(app.confused.anikuta.core.backup.BackupEntry.Episodes(byAnime = episodesByAnime))
        }

        // ── Categories ──
        val allCategories = aniyomi.backupAnimeCategories.ifEmpty { aniyomi.backupCategories }
        if (allCategories.isNotEmpty()) {
            val cats = allCategories.map { cat ->
                CategoryBackup(
                    _id = cat.id,
                    name = cat.name,
                    order = cat.order,
                    flags = cat.flags,
                )
            }
            // Build anime–category links from each anime's category list
            val links = mutableListOf<app.confused.anikuta.core.backup.model.AnimeCategoryBackup>()
            aniyomi.backupAnime.forEachIndexed { index, ani ->
                ani.categories.forEach { catId ->
                    links.add(app.confused.anikuta.core.backup.model.AnimeCategoryBackup(
                        animeId = index.toLong(),
                        categoryId = catId,
                    ))
                }
            }
            entries.add(app.confused.anikuta.core.backup.BackupEntry.Categories(categories = cats, links = links))
        }

        // ── Watch progress (from history) ──
        val progressEntries = mutableMapOf<String, WatchProgressItem>()
        aniyomi.backupAnime.forEach { ani ->
            ani.history.forEach { hist ->
                // Aniyomi history keys by episode URL; we key by "sourceId:url:episodeUrl"
                // On restore, the WatchProgressBackupProvider will try to match by URL.
                val key = "${ani.source}:${ani.url}:${hist.url}"
                progressEntries[key] = WatchProgressItem(
                    positionSeconds = hist.readDuration.toInt(),
                    durationSeconds = 0,
                    title = ani.title,
                    updatedAt = hist.lastRead,
                    animeTitle = ani.title,
                    coverUrl = ani.thumbnailUrl,
                )
            }
        }
        if (progressEntries.isNotEmpty()) {
            entries.add(app.confused.anikuta.core.backup.BackupEntry.WatchProgress(progress = WatchProgressBackup(entries = progressEntries)))
        }

        // ── Tracker bindings ──
        val trackItems = mutableListOf<TrackerTrackItem>()
        aniyomi.backupAnime.forEachIndexed { index, ani ->
            ani.tracking.forEach { tr ->
                trackItems.add(TrackerTrackItem(
                    animeId = index.toLong(),
                    trackerId = tr.syncId.toLong(),
                    remoteId = if (tr.mediaId != 0L) tr.mediaId else tr.mediaIdInt.toLong(),
                    remoteUrl = tr.trackingUrl,
                    lastSeen = tr.lastEpisodeSeen.toLong(),
                    score = tr.score.toDouble(),
                    status = tr.status.toLong(),
                    totalEpisodes = tr.totalEpisodes.toLong(),
                ))
            }
        }
        // Always include tracker entry (even if empty) so restore knows to process it
        entries.add(app.confused.anikuta.core.backup.BackupEntry.Tracker(data = TrackerBackupModel(
            bindings = trackItems,
        )))

        // ── Source links (from anime source + url) ──
        val sourceLinks = mutableMapOf<String, SourceLinkItem>()
        aniyomi.backupAnime.forEach { ani ->
            // Try to use AniList tracking as the anilistId
            val anilistTrack = ani.tracking.firstOrNull { it.syncId == 2 }
            if (anilistTrack != null && anilistTrack.mediaId != 0L) {
                sourceLinks[anilistTrack.mediaId.toString()] = SourceLinkItem(
                    sourceId = ani.source,
                    animeUrl = ani.url,
                    animeTitle = ani.title,
                )
            }
        }
        if (sourceLinks.isNotEmpty()) {
            entries.add(app.confused.anikuta.core.backup.BackupEntry.SourceLinks(links = SourceLinkBackup(sourceLinks = sourceLinks)))
        }

        Log.i(TAG, "Aniyomi backup mapped: ${entries.size} entries")

        return BackupContainer(
            schemaVersion = BackupContainer.CURRENT_SCHEMA_VERSION,
            createdAt = System.currentTimeMillis(),
            appVersion = "aniyomi-import",
            entries = entries,
        )
    }

    /** Checks if the byte array starts with gzip magic (0x1f 0x8b). */
    private fun isGzipped(bytes: ByteArray): Boolean {
        return bytes.size >= 2 &&
            (bytes[0].toInt() and 0xFF) == 0x1f &&
            (bytes[1].toInt() and 0xFF) == 0x8b
    }
}
