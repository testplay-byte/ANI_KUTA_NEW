package app.confused.anikuta.data.anime

import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.ContentId
import app.confused.anikuta.core.common.model.ExtensionSystem
import app.confused.anikuta.core.common.model.LocalId
import app.confused.anikuta.core.common.model.SourceProvenance

/**
 * Maps SQLDelight query results to the [Anime] domain model.
 *
 * Parameter order and types match the `animes` table columns (CREATE TABLE order).
 * SQLDelight calls this mapper with the column values; we convert types here.
 *
 * Phase A: added anilistId, coverColor, score, totalEpisodes, lastWatched.
 * Phase 1 (ADR-050): added localId, contentId, provenance (all nullable during transition).
 */
object AnimeMapper {

    @Suppress("UNUSED_PARAMETER")
    fun map(
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
        // ── Two-tier identity (ADR-050) — nullable during transition ──
        localId: String?,
        contentId: String?,
        // ── Source provenance (ADR-050 §5.1) ──
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
    ): Anime = Anime(
        id = id,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
        coverUrl = coverUrl,
        status = status.toInt(),
        thumbnailUrl = thumbnailUrl,
        favorite = favorite != 0L,
        sourceId = sourceId,
        dateAdded = dateAdded,
        viewerFlags = viewerFlags.toInt(),
        nextUpdate = nextUpdate,
        updateStrategy = updateStrategy.toInt(),
        coverLastModified = coverLastModified,
        releaseDate = releaseDate,
        lastRefresh = lastRefresh,
        lastMetadataFetch = lastMetadataFetch,
        nextEpisodeCheck = nextEpisodeCheck,
        anilistId = anilistId?.toInt(),
        coverColor = coverColor,
        score = score,
        totalEpisodes = totalEpisodes?.toInt(),
        lastWatched = lastWatched,
        nextAiringEpisode = nextAiringEpisode?.toInt(),
        // Two-tier identity (nullable during transition — backfilled by AnimeRepositoryImpl)
        localId = localId?.takeIf { it.isNotBlank() }?.let { LocalId.unsafe(it) },
        contentId = contentId?.takeIf { it.isNotBlank() }?.let { ContentId.unsafe(it) },
        provenance = buildProvenance(
            system = system,
            repoUrl = repoUrl,
            repoName = repoName,
            extensionPkgName = extensionPkgName,
            extensionName = extensionName,
            extensionVersionName = extensionVersionName,
            extensionVersionCode = extensionVersionCode,
            extensionLang = extensionLang,
            isNsfw = isNsfw,
            sourceName = sourceName,
            discoveredAt = discoveredAt,
            lastResolvedAt = lastResolvedAt,
            linkConfidence = linkConfidence,
        ),
    )

    /**
     * Builds a [SourceProvenance] from the flat DB columns.
     * Returns null if [system] is null/blank (the row hasn't been backfilled yet).
     */
    private fun buildProvenance(
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
    ): SourceProvenance? {
        val resolvedSystem = system?.let { ExtensionSystem.fromKey(it) } ?: return null
        return SourceProvenance(
            system = resolvedSystem,
            repoUrl = repoUrl,
            repoName = repoName,
            extensionPkgName = extensionPkgName,
            extensionName = extensionName,
            extensionVersionName = extensionVersionName,
            extensionVersionCode = extensionVersionCode,
            extensionLang = extensionLang,
            isNsfw = isNsfw != 0L,
            sourceName = sourceName,
            discoveredAt = discoveredAt,
            lastResolvedAt = lastResolvedAt,
            linkConfidence = linkConfidence.toInt(),
        )
    }
}
