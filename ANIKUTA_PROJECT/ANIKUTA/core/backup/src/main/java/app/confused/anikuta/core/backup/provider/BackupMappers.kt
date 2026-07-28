package app.confused.anikuta.core.backup.provider

import app.confused.anikuta.core.backup.model.AnimeBackup
import app.confused.anikuta.core.backup.model.AnimeCategoryBackup
import app.confused.anikuta.core.backup.model.CategoryBackup
import app.confused.anikuta.core.backup.model.EpisodeBackup

/**
 * Mapper functions that convert SQLDelight query result rows to backup models.
 *
 * Parameter order + types match the `.sq` CREATE TABLE column order (same as
 * the generated SQLDelight mapper signatures). These are kept here (not in the
 * data models) so the models stay pure `@Serializable` data classes without
 * SQLDelight coupling.
 */
object BackupMappers {

    /**
     * Maps an `animes` row (42 columns) to [AnimeBackup].
     *
     * The 15 two-tier-identity + provenance columns added by ADR-050 (Phase 1)
     * are accepted to match the regenerated SQLDelight mapper type, but ignored
     * here — identity/provenance are NOT part of the backup format yet. They
     * will be re-derived on restore by the backfill in `AnimeRepositoryImpl`.
     */
    @Suppress("UNUSED_PARAMETER", "LongParameterList")
    fun mapAnime(
        id: Long,
        url: String,
        title: String,
        artist: String?,
        author: String?,
        description: String?,
        genre: String?,
        coverUrl: String?,
        status: Long,
        thumbnailUrl: String?,
        favorite: Long,
        sourceId: Long,
        dateAdded: Long,
        viewerFlags: Long,
        nextUpdate: Long,
        updateStrategy: Long,
        coverLastModified: Long,
        releaseDate: Long?,
        lastRefresh: Long,
        lastMetadataFetch: Long?,
        nextEpisodeCheck: Long?,
        anilistId: Long?,
        coverColor: String?,
        score: Double?,
        totalEpisodes: Long?,
        lastWatched: Long,
        nextAiringEpisode: Long?,
        // ── Two-tier identity + provenance (ADR-050, Phase 1) — ignored by backup ──
        localId: String?,
        contentId: String?,
        system: String?,
        repoUrl: String?,
        repoName: String?,
        extensionPkgName: String?,
        extensionName: String?,
        extensionVersionName: String?,
        extensionVersionCode: Long?,
        extensionLang: String?,
        isNsfw: Long,
        sourceName: String?,
        discoveredAt: Long,
        lastResolvedAt: Long,
        linkConfidence: Long,
    ): AnimeBackup = AnimeBackup(
        _id = id,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genre,
        coverUrl = coverUrl,
        status = status,
        thumbnailUrl = thumbnailUrl,
        favorite = favorite != 0L,
        sourceId = sourceId,
        dateAdded = dateAdded,
        viewerFlags = viewerFlags,
        nextUpdate = nextUpdate,
        updateStrategy = updateStrategy,
        coverLastModified = coverLastModified,
        releaseDate = releaseDate,
        lastRefresh = lastRefresh,
        lastMetadataFetch = lastMetadataFetch,
        nextEpisodeCheck = nextEpisodeCheck,
        anilistId = anilistId,
        coverColor = coverColor,
        score = score,
        totalEpisodes = totalEpisodes,
        lastWatched = lastWatched,
        nextAiringEpisode = nextAiringEpisode,
    )

    /**
     * Maps an `episodes` row (20 columns) to [EpisodeBackup].
     *
     * The 4 ADR-024 status-tracking columns (`release_date`, `last_refresh`,
     * `last_metadata_fetch`, `next_episode_check`) are accepted to match the
     * regenerated SQLDelight mapper type, but ignored here — they're not part
     * of the backup format.
     */
    @Suppress("UNUSED_PARAMETER", "LongParameterList")
    fun mapEpisode(
        id: Long,
        animeId: Long,
        url: String?,
        name: String,
        episodeNumber: Double,
        scanlator: String?,
        seen: Long,
        bookmark: Long,
        lastSecondSeen: Long,
        totalSeconds: Long,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long?,
        fillermark: String?,
        summary: String?,
        previewUrl: String?,
        // ── Status-tracking columns (ADR-024) — ignored by backup ──
        releaseDate: Long?,
        lastRefresh: Long,
        lastMetadataFetch: Long?,
        nextEpisodeCheck: Long?,
    ): EpisodeBackup = EpisodeBackup(
        _id = id,
        animeId = animeId,
        url = url,
        name = name,
        episodeNumber = episodeNumber,
        scanlator = scanlator,
        seen = seen != 0L,
        bookmark = bookmark != 0L,
        lastSecondSeen = lastSecondSeen,
        totalSeconds = totalSeconds,
        sourceOrder = sourceOrder,
        dateFetch = dateFetch,
        dateUpload = dateUpload,
        fillermark = fillermark,
        summary = summary,
        previewUrl = previewUrl,
    )

    /** Maps a `categories` row (5 columns) to [CategoryBackup]. */
    fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        hidden: Long,
    ): CategoryBackup = CategoryBackup(
        _id = id,
        name = name,
        order = order,
        flags = flags,
        hidden = hidden != 0L,
    )

    /** Maps an `anime_category` row (4 columns) to [AnimeCategoryBackup]. */
    fun mapAnimeCategory(
        id: Long,
        animeId: Long,
        categoryId: Long,
        order: Long,
    ): AnimeCategoryBackup = AnimeCategoryBackup(
        animeId = animeId,
        categoryId = categoryId,
        order = order,
    )
}
