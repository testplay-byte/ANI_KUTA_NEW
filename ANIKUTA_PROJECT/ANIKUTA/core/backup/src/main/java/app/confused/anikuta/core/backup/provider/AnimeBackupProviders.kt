package app.confused.anikuta.core.backup.provider

import android.util.Log
import app.confused.anikuta.core.backup.BackupCategory
import app.confused.anikuta.core.backup.BackupEntry
import app.confused.anikuta.core.backup.BackupProvider
import app.confused.anikuta.core.backup.model.AnimeBackup
import app.confused.anikuta.core.database.AnikutaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AnikutaBackup"

/**
 * Backs up library anime (favorites from the `animes` table where `favorite=1`).
 *
 * Export reads only the favorite rows. Import upserts each anime by AniList ID
 * (falls back to source+url if no AniList ID), restoring the favorite flag +
 * core columns. Missing data is skipped gracefully.
 *
 * Uses [AnikutaDatabase] directly (not the repository) to access all columns —
 * the repository interface doesn't expose every status-tracking column needed
 * for a complete backup.
 */
class LibraryBackupProvider(
    private val database: AnikutaDatabase,
) : BackupProvider {

    override val id: String = BackupCategory.LIBRARY.id

    override suspend fun export(): BackupEntry = withContext(Dispatchers.IO) {
        try {
            val favorites = database.animesQueries
                .selectFavorites(BackupMappers::mapAnime)
                .executeAsList()
            Log.i(TAG, "Library export: ${favorites.size} favorite anime")
            BackupEntry.Library(animes = favorites)
        } catch (e: Exception) {
            Log.e(TAG, "Library export failed", e)
            BackupEntry.Library()
        }
    }

    override suspend fun import(entry: BackupEntry): Boolean = withContext(Dispatchers.IO) {
        require(entry is BackupEntry.Library) { "Expected Library entry, got ${entry.providerId}" }
        if (entry.animes.isEmpty()) {
            Log.i(TAG, "Library import: empty, nothing to do")
            return@withContext false
        }
        var imported = 0
        var skipped = 0
        database.animesQueries.transaction {
            entry.animes.forEach { anime ->
                try {
                    upsertAnime(database, anime)
                    imported++
                } catch (e: Exception) {
                    Log.w(TAG, "Library import: skipped '${anime.title}' — ${e.message}")
                    skipped++
                }
            }
        }
        Log.i(TAG, "Library import: $imported imported, $skipped skipped")
        imported > 0
    }
}

/**
 * Backs up full anime details (description, genres, scores, cover colors — all
 * columns from the `animes` table, not just favorites).
 *
 * This is a separate category from [LibraryBackupProvider] so the user can
 * choose to back up just their library or all anime data (including anime they
 * browsed but didn't favorite). On import, it upserts by AniList ID.
 */
class AnimeDetailsBackupProvider(
    private val database: AnikutaDatabase,
) : BackupProvider {

    override val id: String = BackupCategory.ANIME_DETAILS.id

    override suspend fun export(): BackupEntry = withContext(Dispatchers.IO) {
        try {
            val all = database.animesQueries
                .selectAll(BackupMappers::mapAnime)
                .executeAsList()
            Log.i(TAG, "AnimeDetails export: ${all.size} anime (all)")
            BackupEntry.AnimeDetails(animes = all)
        } catch (e: Exception) {
            Log.e(TAG, "AnimeDetails export failed", e)
            BackupEntry.AnimeDetails()
        }
    }

    override suspend fun import(entry: BackupEntry): Boolean = withContext(Dispatchers.IO) {
        require(entry is BackupEntry.AnimeDetails) { "Expected AnimeDetails entry, got ${entry.providerId}" }
        if (entry.animes.isEmpty()) return@withContext false
        var imported = 0
        var skipped = 0
        database.animesQueries.transaction {
            entry.animes.forEach { anime ->
                try {
                    upsertAnime(database, anime)
                    imported++
                } catch (e: Exception) {
                    Log.w(TAG, "AnimeDetails import: skipped '${anime.title}' — ${e.message}")
                    skipped++
                }
            }
        }
        Log.i(TAG, "AnimeDetails import: $imported imported, $skipped skipped")
        imported > 0
    }
}

/**
 * Upserts an [AnimeBackup] into the `animes` table.
 *
 * Strategy:
 * 1. If `anilistId` is set, look up by AniList ID. If found, update. If not, insert.
 * 2. If no AniList ID, look up by `sourceId + url`. If found, update. If not, insert.
 *
 * This shared helper is used by both [LibraryBackupProvider] and
 * [AnimeDetailsBackupProvider].
 */
internal fun upsertAnime(database: AnikutaDatabase, anime: AnimeBackup) {
    val queries = database.animesQueries
    val existingId: Long? = if (anime.anilistId != null) {
        queries.selectIdByAnilistId(anime.anilistId).executeAsOneOrNull()
    } else {
        queries.selectBySourceAndUrl(anime.sourceId, anime.url) { _id, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> _id }.executeAsOneOrNull()
    }

    if (existingId != null) {
        // Update existing row
        queries.update(
            url = anime.url,
            title = anime.title,
            artist = anime.artist,
            author = anime.author,
            description = anime.description,
            genre = anime.genre,
            coverUrl = anime.coverUrl,
            status = anime.status,
            thumbnailUrl = anime.thumbnailUrl,
            favorite = if (anime.favorite) 1L else 0L,
            viewerFlags = anime.viewerFlags,
            nextUpdate = anime.nextUpdate,
            updateStrategy = anime.updateStrategy,
            coverLastModified = anime.coverLastModified,
            releaseDate = anime.releaseDate,
            lastRefresh = anime.lastRefresh,
            lastMetadataFetch = anime.lastMetadataFetch,
            nextEpisodeCheck = anime.nextEpisodeCheck,
            anilistId = anime.anilistId,
            coverColor = anime.coverColor,
            score = anime.score,
            totalEpisodes = anime.totalEpisodes,
            lastWatched = anime.lastWatched,
            nextAiringEpisode = anime.nextAiringEpisode,
            id = existingId,
        )
    } else {
        // Insert new row
        queries.insert(
            url = anime.url,
            title = anime.title,
            artist = anime.artist,
            author = anime.author,
            description = anime.description,
            genre = anime.genre,
            coverUrl = anime.coverUrl,
            status = anime.status,
            thumbnailUrl = anime.thumbnailUrl,
            favorite = if (anime.favorite) 1L else 0L,
            sourceId = anime.sourceId,
            dateAdded = anime.dateAdded,
            viewerFlags = anime.viewerFlags,
            nextUpdate = anime.nextUpdate,
            updateStrategy = anime.updateStrategy,
            coverLastModified = anime.coverLastModified,
            releaseDate = anime.releaseDate,
            lastRefresh = anime.lastRefresh,
            lastMetadataFetch = anime.lastMetadataFetch,
            nextEpisodeCheck = anime.nextEpisodeCheck,
            anilistId = anime.anilistId,
            coverColor = anime.coverColor,
            score = anime.score,
            totalEpisodes = anime.totalEpisodes,
            lastWatched = anime.lastWatched,
            nextAiringEpisode = anime.nextAiringEpisode,
        )
    }
}
