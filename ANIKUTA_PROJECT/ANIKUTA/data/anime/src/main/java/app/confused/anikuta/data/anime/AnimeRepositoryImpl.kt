package app.confused.anikuta.data.anime

import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.ContentId
import app.confused.anikuta.core.common.model.ContentIdGenerator
import app.confused.anikuta.core.common.model.ContentIdPriority
import app.confused.anikuta.core.common.model.ExtensionSystem
import app.confused.anikuta.core.common.model.LocalId
import app.confused.anikuta.core.common.model.LocalIdGenerator
import app.confused.anikuta.core.common.model.MetadataProviderId
import app.confused.anikuta.core.common.model.SourceProvenance
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.common.di.DispatcherProvider
import app.confused.anikuta.core.database.AnikutaDatabase
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import android.util.Log

/**
 * SQLDelight-backed implementation of [AnimeRepository].
 *
 * Per `RULES/ai-agent-rules.md` §3: this implements the interface defined in
 * `:core:common`. The ViewModel never sees this class — only the interface.
 *
 * Logging (ADR-033): uses tag [TAG] for filterable logcat output.
 *
 * Phase A: added AniList-ID-based lookups + lastWatched + metadata updates.
 */
class AnimeRepositoryImpl(
    private val database: AnikutaDatabase,
    private val dispatchers: DispatcherProvider,
) : AnimeRepository {

    override fun observeAll(): Flow<List<Anime>> =
        database.animesQueries.selectAll(AnimeMapper::map)
            .asFlow()
            .mapToList(dispatchers.io)

    override fun observeFavorites(): Flow<List<Anime>> =
        database.animesQueries.selectFavorites(AnimeMapper::map)
            .asFlow()
            .mapToList(dispatchers.io)

    override fun observeById(id: Long): Flow<Anime?> =
        database.animesQueries.selectById(id, AnimeMapper::map)
            .asFlow()
            .mapToOneOrNull(dispatchers.io)

    override fun observeBySource(sourceId: Long): Flow<List<Anime>> =
        database.animesQueries.selectBySource(sourceId, AnimeMapper::map)
            .asFlow()
            .mapToList(dispatchers.io)

    override fun observeByAnilistId(anilistId: Int): Flow<Anime?> =
        database.animesQueries.selectByAnilistId(anilistId.toLong(), AnimeMapper::map)
            .asFlow()
            .mapToOneOrNull(dispatchers.io)

    override fun observeBySourceAndUrl(sourceId: Long, url: String): Flow<Anime?> =
        database.animesQueries.selectBySourceAndUrl(sourceId, url, AnimeMapper::map)
            .asFlow()
            .mapToOneOrNull(dispatchers.io)

    override suspend fun getById(id: Long): Anime? =
        database.animesQueries.selectById(id, AnimeMapper::map)
            .executeAsOneOrNull()

    override suspend fun getByAnilistId(anilistId: Int): Anime? =
        database.animesQueries.selectByAnilistId(anilistId.toLong(), AnimeMapper::map)
            .executeAsOneOrNull()

    override suspend fun getBySourceAndUrl(sourceId: Long, url: String): Anime? =
        database.animesQueries.selectBySourceAndUrl(sourceId, url, AnimeMapper::map)
            .executeAsOneOrNull()

    override suspend fun searchByName(query: String): List<Anime> =
        database.animesQueries.searchByName(query, AnimeMapper::map)
            .executeAsList()

    override suspend fun upsert(anime: Anime): Long {
        Log.d(TAG, "upsert: anime=${anime.title}, id=${anime.id}, anilistId=${anime.anilistId}, sourceId=${anime.sourceId}")
        // Fix: before INSERT, check if a row with the same (source_id, url) already exists.
        // This happens when ExtensionDetailsProvider.persistEpisodes already saved the row,
        // and then saveAnimeToLibrary tries to INSERT a new one with id=0.
        // Without this check, the INSERT fails with UNIQUE constraint on (source_id, url).
        if (anime.id == 0L && anime.sourceId > 0L) {
            val existing = database.animesQueries.selectBySourceAndUrl(
                anime.sourceId, anime.url, AnimeMapper::map,
            ).executeAsOneOrNull()
            if (existing != null) {
                Log.i(TAG, "upsert: found existing row id=${existing.id} for " +
                    "(sourceId=${anime.sourceId}, url=${anime.url}) — UPDATE instead of INSERT")
                val updated = anime.copy(id = existing.id)
                return updateExisting(updated)
            }
        }
        // Also check by anilistId if source_id is 0 (AniList-only anime).
        val aid = anime.anilistId
        if (anime.id == 0L && aid != null) {
            val existingByAnilist = database.animesQueries.selectByAnilistId(
                aid.toLong(), AnimeMapper::map,
            ).executeAsOneOrNull()
            if (existingByAnilist != null) {
                Log.i(TAG, "upsert: found existing row id=${existingByAnilist.id} for " +
                    "anilistId=$aid — UPDATE instead of INSERT")
                val updated = anime.copy(id = existingByAnilist.id)
                return updateExisting(updated)
            }
        }
        return if (anime.id > 0) {
            updateExisting(anime)
        } else {
            insertNew(anime)
        }
    }

    private fun updateExisting(anime: Anime): Long {
        database.animesQueries.update(
            id = anime.id,
            url = anime.url,
            title = anime.title,
            artist = anime.artist,
            author = anime.author,
            description = anime.description,
            genre = anime.genre.takeIf { it.isNotEmpty() }?.joinToString(","),
            coverUrl = anime.coverUrl,
            status = anime.status.toLong(),
            thumbnailUrl = anime.thumbnailUrl,
            favorite = if (anime.favorite) 1L else 0L,
            viewerFlags = anime.viewerFlags.toLong(),
            nextUpdate = anime.nextUpdate,
            updateStrategy = anime.updateStrategy.toLong(),
            coverLastModified = anime.coverLastModified,
            releaseDate = anime.releaseDate,
            lastRefresh = anime.lastRefresh,
            lastMetadataFetch = anime.lastMetadataFetch,
            nextEpisodeCheck = anime.nextEpisodeCheck,
            anilistId = anime.anilistId?.toLong(),
            coverColor = anime.coverColor,
            score = anime.score,
            totalEpisodes = anime.totalEpisodes?.toLong(),
            lastWatched = anime.lastWatched,
            nextAiringEpisode = anime.nextAiringEpisode?.toLong(),
        )
        return anime.id
    }

    private fun insertNew(anime: Anime): Long {
        return database.transactionWithResult {
            database.animesQueries.insert(
                url = anime.url,
                title = anime.title,
                artist = anime.artist,
                author = anime.author,
                description = anime.description,
                genre = anime.genre.takeIf { it.isNotEmpty() }?.joinToString(","),
                coverUrl = anime.coverUrl,
                status = anime.status.toLong(),
                thumbnailUrl = anime.thumbnailUrl,
                favorite = if (anime.favorite) 1L else 0L,
                sourceId = anime.sourceId,
                dateAdded = anime.dateAdded,
                viewerFlags = anime.viewerFlags.toLong(),
                nextUpdate = anime.nextUpdate,
                updateStrategy = anime.updateStrategy.toLong(),
                coverLastModified = anime.coverLastModified,
                releaseDate = anime.releaseDate,
                lastRefresh = anime.lastRefresh,
                lastMetadataFetch = anime.lastMetadataFetch,
                nextEpisodeCheck = anime.nextEpisodeCheck,
                anilistId = anime.anilistId?.toLong(),
                coverColor = anime.coverColor,
                score = anime.score,
                totalEpisodes = anime.totalEpisodes?.toLong(),
                lastWatched = anime.lastWatched,
                nextAiringEpisode = anime.nextAiringEpisode?.toLong(),
            )
            database.animesQueries.lastInsertedRowId().executeAsOne()
        }
    }

    override suspend fun updateFavorite(id: Long, favorite: Boolean, dateAdded: Long) {
        Log.d(TAG, "updateFavorite: id=$id, favorite=$favorite")
        database.animesQueries.updateFavorite(
            id = id,
            favorite = if (favorite) 1L else 0L,
            dateAdded = dateAdded,
        )
    }

    override suspend fun updateFavoriteByAnilistId(anilistId: Int, favorite: Boolean, dateAdded: Long) {
        Log.d(TAG, "updateFavoriteByAnilistId: anilistId=$anilistId, favorite=$favorite")
        database.animesQueries.updateFavoriteByAnilistId(
            anilistId = anilistId.toLong(),
            favorite = if (favorite) 1L else 0L,
            dateAdded = dateAdded,
        )
    }

    override suspend fun updateLastRefresh(id: Long, lastRefresh: Long) {
        database.animesQueries.updateLastRefresh(lastRefresh = lastRefresh, id = id)
    }

    override suspend fun updateLastMetadataFetch(id: Long, lastMetadataFetch: Long) {
        database.animesQueries.updateLastMetadataFetch(lastMetadataFetch = lastMetadataFetch, id = id)
    }

    override suspend fun updateNextEpisodeCheck(id: Long, nextEpisodeCheck: Long?) {
        database.animesQueries.updateNextEpisodeCheck(nextEpisodeCheck = nextEpisodeCheck, id = id)
    }

    override suspend fun updateLastWatched(id: Long, lastWatched: Long) {
        database.animesQueries.updateLastWatched(lastWatched = lastWatched, id = id)
    }

    override suspend fun updateLastWatchedByAnilistId(anilistId: Int, lastWatched: Long) {
        database.animesQueries.updateLastWatchedByAnilistId(
            lastWatched = lastWatched,
            anilistId = anilistId.toLong(),
        )
    }

    override suspend fun clearAnilistId(id: Long) {
        Log.d(TAG, "clearAnilistId: id=$id")
        withContext(dispatchers.io) {
            database.animesQueries.clearAnilistId(id)
        }
    }

    override suspend fun updateSourceAndUrl(id: Long, sourceId: Long, url: String) {
        Log.d(TAG, "updateSourceAndUrl: id=$id, sourceId=$sourceId, url=$url")
        withContext(dispatchers.io) {
            database.animesQueries.updateSourceAndUrl(
                id = id,
                sourceId = sourceId,
                url = url,
            )
        }
    }

    override suspend fun updateAnilistMetadata(
        anilistId: Int,
        title: String,
        coverUrl: String?,
        coverColor: String?,
        score: Double?,
        totalEpisodes: Int?,
        nextAiringEpisode: Int?,
    ) {
        database.animesQueries.updateAnilistMetadataByAnilistId(
            anilistId = anilistId.toLong(),
            title = title,
            coverUrl = coverUrl,
            coverColor = coverColor,
            score = score,
            totalEpisodes = totalEpisodes?.toLong(),
            nextAiringEpisode = nextAiringEpisode?.toLong(),
        )
    }

    override suspend fun updatePreferredCoverByAnilistId(anilistId: Int, coverUrl: String?, coverColor: String?) {
        withContext(dispatchers.io) {
            database.animesQueries.updatePreferredCoverByAnilistId(
                coverUrl = coverUrl,
                coverColor = coverColor,
                anilistId = anilistId.toLong(),
            )
        }
    }

    override suspend fun updatePreferredCoverBySourceAndUrl(sourceId: Long, url: String, coverUrl: String?, coverColor: String?) {
        withContext(dispatchers.io) {
            database.animesQueries.updatePreferredCoverBySourceAndUrl(
                coverUrl = coverUrl,
                coverColor = coverColor,
                sourceId = sourceId,
                url = url,
            )
        }
    }

    // ═══ Fix 3 (SOURCE-SWITCH-FIXES) ═══
    override suspend fun updateMetadataFromExtension(
        id: Long,
        title: String,
        description: String?,
        genre: String?,
        coverUrl: String?,
        coverColor: String?,
        status: Int,
        artist: String?,
        author: String?,
    ) {
        Log.d(TAG, "updateMetadataFromExtension: id=$id, title='$title', coverUrl=$coverUrl, " +
            "coverColor=$coverColor, status=$status, hasDesc=${description != null}, " +
            "hasGenre=${!genre.isNullOrBlank()}")
        withContext(dispatchers.io) {
            database.animesQueries.updateMetadataFromExtension(
                id = id,
                title = title,
                description = description,
                genre = genre,
                coverUrl = coverUrl,
                coverColor = coverColor,
                status = status.toLong(),
                artist = artist,
                author = author,
            )
        }
    }

    override suspend fun delete(id: Long) {
        Log.d(TAG, "delete: id=$id")
        database.animesQueries.delete(id)
    }

    // ═══ Two-tier identity (ADR-050 — Phase 1) ═══

    override suspend fun getByLocalId(localId: LocalId): Anime? =
        withContext(dispatchers.io) {
            database.animesQueries.selectByLocalId(localId.value, AnimeMapper::map)
                .executeAsOneOrNull()
        }

    override suspend fun getByContentId(contentId: ContentId): List<Anime> =
        withContext(dispatchers.io) {
            database.animesQueries.selectByContentId(contentId.value, AnimeMapper::map)
                .executeAsList()
        }

    override suspend fun updateIdentity(id: Long, localId: LocalId, contentId: ContentId) {
        withContext(dispatchers.io) {
            database.animesQueries.updateIdentity(
                id = id,
                localId = localId.value,
                contentId = contentId.value,
                lastResolvedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun updateProvenance(id: Long, provenance: SourceProvenance) {
        withContext(dispatchers.io) {
            database.animesQueries.updateProvenance(
                id = id,
                system = provenance.system.key,
                repoUrl = provenance.repoUrl,
                repoName = provenance.repoName,
                extensionPkgName = provenance.extensionPkgName,
                extensionName = provenance.extensionName,
                extensionVersionName = provenance.extensionVersionName,
                extensionVersionCode = provenance.extensionVersionCode,
                extensionLang = provenance.extensionLang,
                isNsfw = if (provenance.isNsfw) 1L else 0L,
                sourceName = provenance.sourceName,
                discoveredAt = provenance.discoveredAt,
                linkConfidence = provenance.linkConfidence.toLong(),
            )
        }
    }

    /**
     * Backfill implementation: iterates rows where local_id OR content_id is NULL,
     * computes the identity from existing columns, and updates them.
     *
     * For existing rows (all Aniyomi-system today):
     * - local_id = LocalIdGenerator.forExtension(ANIYOMI, sourceId, url) when sourceId > 0,
     *   else LocalIdGenerator.forProvider(ANILIST, anilistId) when anilistId != null.
     *   Rows with neither (sourceId == 0 AND anilistId == null) are skipped + logged.
     * - content_id = ContentIdGenerator.generate(anilistId, localId, priority).
     */
    override suspend fun backfillIdentityColumns(priority: ContentIdPriority): Int =
        withContext(dispatchers.io) {
            val rowsMissing = database.animesQueries.selectRowsMissingIdentity(AnimeMapper::map)
                .executeAsList()
            Log.i(TAG, "backfillIdentityColumns: ${rowsMissing.size} rows need identity")

            var backfilled = 0
            var skipped = 0
            for (anime in rowsMissing) {
                try {
                    val localId = computeLocalIdForExistingRow(anime)
                    if (localId == null) {
                        Log.w(TAG, "backfillIdentityColumns: skipping row ${anime.id} " +
                            "'${anime.title}' — cannot derive local_id " +
                            "(sourceId=${anime.sourceId}, anilistId=${anime.anilistId})")
                        skipped++
                        continue
                    }
                    val contentId = ContentIdGenerator.generate(
                        anilistId = anime.anilistId,
                        localId = localId,
                        priority = priority,
                    )
                    database.animesQueries.updateIdentity(
                        id = anime.id,
                        localId = localId.value,
                        contentId = contentId.value,
                        lastResolvedAt = System.currentTimeMillis(),
                    )
                    backfilled++
                } catch (e: Exception) {
                    // Per-row crash resistance — one bad row must not abort the whole backfill.
                    Log.e(TAG, "backfillIdentityColumns: failed to backfill row ${anime.id} " +
                        "'${anime.title}'", e)
                    skipped++
                }
            }
            Log.i(TAG, "backfillIdentityColumns: backfilled=$backfilled, skipped=$skipped, " +
                "total=${rowsMissing.size}")
            backfilled
        }

    /**
     * Derive a [LocalId] for an existing (pre-ADR-050) row.
     * - If sourceId > 0 → extension-sourced: `LocalIdGenerator.forExtension(ANIYOMI, sourceId, url)`.
     *   (All existing extension rows are Aniyomi-system.)
     * - Else if anilistId != null → provider-sourced: `LocalIdGenerator.forProvider(ANILIST, anilistId)`.
     * - Else → null (can't derive; the row is in an unknown state).
     */
    private fun computeLocalIdForExistingRow(anime: Anime): LocalId? {
        return when {
            anime.sourceId > 0 -> LocalIdGenerator.forExtension(
                system = ExtensionSystem.ANIYOMI,
                extensionId = anime.sourceId,
                sourceContentId = anime.url,
            )
            anime.anilistId != null -> LocalIdGenerator.forProvider(
                provider = MetadataProviderId.ANILIST,
                remoteId = anime.anilistId.toString(),
            )
            else -> null
        }
    }

    companion object {
        private const val TAG = "AnikutaAnimeRepo"
    }
}
