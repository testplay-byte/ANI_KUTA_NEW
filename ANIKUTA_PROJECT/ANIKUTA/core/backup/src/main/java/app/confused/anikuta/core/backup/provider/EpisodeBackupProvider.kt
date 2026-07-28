package app.confused.anikuta.core.backup.provider

import android.util.Log
import app.confused.anikuta.core.backup.BackupCategory
import app.confused.anikuta.core.backup.BackupEntry
import app.confused.anikuta.core.backup.BackupProvider
import app.confused.anikuta.core.backup.model.EpisodeBackup
import app.confused.anikuta.core.database.AnikutaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AnikutaBackup"

/**
 * Backs up the episodes list for each anime in the library.
 *
 * Export reads episodes for all favorite anime (keyed by the anime's local DB id).
 * Import re-maps the backup's anime id to the local anime's id (matched by
 * AniList ID or source+url), then upserts each episode by `anime_id + url`.
 *
 * Missing anime (not in the local DB) are skipped gracefully — the user may
 * need to re-add the anime to their library first.
 */
class EpisodeBackupProvider(
    private val database: AnikutaDatabase,
) : BackupProvider {

    override val id: String = BackupCategory.EPISODES.id

    override suspend fun export(): BackupEntry = withContext(Dispatchers.IO) {
        try {
            val favorites = database.animesQueries
                .selectFavorites(BackupMappers::mapAnime)
                .executeAsList()
            val byAnime = mutableMapOf<String, List<EpisodeBackup>>()
            favorites.forEach { anime ->
                val episodes = database.episodesQueries
                    .selectByAnimeId(anime._id, BackupMappers::mapEpisode)
                    .executeAsList()
                if (episodes.isNotEmpty()) {
                    // Key by anilistId if available, else by sourceId:url (for stable cross-device mapping)
                    val key = anime.anilistId?.toString() ?: "${anime.sourceId}:${anime.url}"
                    byAnime[key] = episodes
                }
            }
            Log.i(TAG, "Episodes export: ${byAnime.size} anime with episodes")
            BackupEntry.Episodes(byAnime = byAnime)
        } catch (e: Exception) {
            Log.e(TAG, "Episodes export failed", e)
            BackupEntry.Episodes()
        }
    }

    override suspend fun import(entry: BackupEntry): Boolean = withContext(Dispatchers.IO) {
        require(entry is BackupEntry.Episodes) { "Expected Episodes entry, got ${entry.providerId}" }
        if (entry.byAnime.isEmpty()) return@withContext false
        var imported = 0
        var skipped = 0

        entry.byAnime.forEach { (animeKey, episodes) ->
            try {
                val localAnimeId = resolveLocalAnimeId(database, animeKey)
                if (localAnimeId == null) {
                    Log.w(TAG, "Episodes import: anime $animeKey not found locally — skipping ${episodes.size} episodes")
                    skipped += episodes.size
                    return@forEach
                }
                database.episodesQueries.transaction {
                    episodes.forEach { ep ->
                        upsertEpisode(database, localAnimeId, ep)
                        imported++
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Episodes import: failed for $animeKey — ${e.message}")
                skipped += episodes.size
            }
        }
        Log.i(TAG, "Episodes import: $imported imported, $skipped skipped")
        imported > 0
    }
}

/** Resolves a backup anime key to the local DB anime _id. */
internal fun resolveLocalAnimeId(database: AnikutaDatabase, key: String): Long? {
    val queries = database.animesQueries
    // Try as AniList ID first
    val anilistId = key.toLongOrNull()
    if (anilistId != null) {
        queries.selectIdByAnilistId(anilistId).executeAsOneOrNull()?.let { return it }
    }
    // Try as "sourceId:url"
    val colonIdx = key.indexOf(':')
    if (colonIdx > 0) {
        val sourceId = key.substring(0, colonIdx).toLongOrNull()
        val url = key.substring(colonIdx + 1)
        if (sourceId != null) {
            queries.selectBySourceAndUrl(sourceId, url) { _id, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> _id }
                .executeAsOneOrNull()?.let { return it }
        }
    }
    return null
}

/** Upserts an episode by `anime_id + url` (insert or update). */
@Suppress("LongParameterList")
internal fun upsertEpisode(database: AnikutaDatabase, animeId: Long, ep: EpisodeBackup) {
    val queries = database.episodesQueries
    // Check if an episode with the same URL exists for this anime
    val existing = if (ep.url != null) {
        // selectByAnimeIdAndNumber is the closest query; we use it as a proxy
        queries.selectByAnimeIdAndNumber(animeId, ep.episodeNumber) { _id, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> _id }
            .executeAsOneOrNull()
    } else {
        queries.selectByAnimeIdAndNumber(animeId, ep.episodeNumber) { _id, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> _id }
            .executeAsOneOrNull()
    }

    if (existing != null) {
        queries.update(
            url = ep.url,
            name = ep.name,
            episodeNumber = ep.episodeNumber,
            scanlator = ep.scanlator,
            seen = if (ep.seen) 1L else 0L,
            bookmark = if (ep.bookmark) 1L else 0L,
            lastSecondSeen = ep.lastSecondSeen,
            totalSeconds = ep.totalSeconds,
            dateFetch = ep.dateFetch,
            dateUpload = ep.dateUpload,
            fillermark = ep.fillermark,
            summary = ep.summary,
            previewUrl = ep.previewUrl,
            id = existing,
        )
    } else {
        queries.insert(
            animeId = animeId,
            url = ep.url,
            name = ep.name,
            episodeNumber = ep.episodeNumber,
            scanlator = ep.scanlator,
            seen = if (ep.seen) 1L else 0L,
            bookmark = if (ep.bookmark) 1L else 0L,
            lastSecondSeen = ep.lastSecondSeen,
            totalSeconds = ep.totalSeconds,
            sourceOrder = ep.sourceOrder,
            dateFetch = ep.dateFetch,
            dateUpload = ep.dateUpload,
            fillermark = ep.fillermark,
            summary = ep.summary,
            previewUrl = ep.previewUrl,
        )
    }
}
