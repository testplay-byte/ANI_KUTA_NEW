package app.confused.anikuta.core.backup.translation

import android.util.Log
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.model.AniListAnime
import app.confused.anikuta.core.backup.BackupEntry
import app.confused.anikuta.core.backup.format.aniyomi.AniyomiBackup
import app.confused.anikuta.core.backup.format.aniyomi.AniyomiBackupAnime
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val TAG = "AniyomiTranslator"

/**
 * Result of resolving a single Aniyomi anime to an AniList ID.
 */
sealed class AnilistResolution {
    /** Successfully resolved — the anime has an AniList ID. */
    data class Resolved(
        val anilistId: Int,
        val anilistAnime: AniListAnime?,
        val method: String, // "tracker", "mal-lookup", "title-search"
    ) : AnilistResolution()

    /** Failed to resolve — no AniList match found. */
    data class Failed(val title: String, val reason: String) : AnilistResolution()
}

/**
 * Live progress update during the translation process.
 * Emitted as each anime is processed.
 */
data class TranslationProgress(
    val currentIndex: Int,
    val total: Int,
    val currentTitle: String,
    val resolved: Int,
    val failed: Int,
    val resolution: AnilistResolution?,
)

/**
 * The result of translating an Aniyomi backup to ANIKUTA format.
 */
data class TranslationResult(
    val container: BackupContainer,
    val resolutions: List<AnilistResolution>,
    val stats: TranslationStats,
)

/**
 * Summary statistics of the translation.
 */
data class TranslationStats(
    val totalAnime: Int,
    val resolvedAnime: Int,
    val failedAnime: Int,
    val totalEpisodes: Int,
    val totalCategories: Int,
    val totalManga: Int,
    val totalMangaCategories: Int,
)

/**
 * Translates an Aniyomi backup into ANIKUTA's [BackupContainer] format.
 *
 * This is the **core translation module** — it resolves AniList IDs for each
 * Aniyomi anime (via tracker bindings → MAL lookup → title search), remaps
 * source IDs, and builds a [BackupContainer] that the existing
 * [app.confused.anikuta.core.backup.BackupManager] can restore.
 *
 * **Modular design:** This translator is self-contained and depends only on
 * [AniListApi]. It can be tested independently and swapped out for other
 * format translators (e.g., a future Tachiyomi translator would follow the
 * same pattern).
 *
 * **Rate limiting:** Uses [AniListApi]'s built-in [AniListRateLimiter] to
 * enforce max 80 requests/minute with dynamic speed adjustment.
 *
 * @param anilistApi the AniList API client (with rate limiter).
 */
class AniyomiBackupTranslator(
    private val anilistApi: AniListApi,
) {

    private val _progress = MutableStateFlow<TranslationProgress?>(null)
    /** Live progress updates — observe this in the UI for real-time feedback. */
    val progress: StateFlow<TranslationProgress?> = _progress.asStateFlow()

    /**
     * Translates an Aniyomi backup into a [BackupContainer].
     *
     * Steps:
     * 1. For each anime, resolve the AniList ID (tracker → MAL → title search).
     * 2. Build `AnimeBackup` entries with resolved `anilistId`.
     * 3. Remap episode `animeId` to the resolved AniList ID.
     * 4. Build `SourceLinkBackup` entries (anilistId → sourceId+url).
     * 5. Remap watch progress keys (anilistId:episodeUrl).
     * 6. Build category + tracker entries.
     *
     * @param aniyomiBackup the decoded Aniyomi backup.
     * @return the translation result (BackupContainer + per-anime resolutions).
     */
    suspend fun translate(aniyomiBackup: AniyomiBackup): TranslationResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "═══ Translating Aniyomi backup ═══")
        Log.i(TAG, "  Anime: ${aniyomiBackup.backupAnime.size}")
        Log.i(TAG, "  Categories: ${aniyomiBackup.backupAnimeCategories.size}")
        Log.i(TAG, "  Manga: ${aniyomiBackup.backupManga.size}")

        val anime = aniyomiBackup.backupAnime
        val resolutions = mutableListOf<AnilistResolution>()

        // ── Phase 1: Resolve AniList IDs ──
        anime.forEachIndexed { index, ani ->
            val resolution = resolveAnilistId(ani)
            resolutions.add(resolution)

            _progress.value = TranslationProgress(
                currentIndex = index + 1,
                total = anime.size,
                currentTitle = ani.title,
                resolved = resolutions.count { it is AnilistResolution.Resolved },
                failed = resolutions.count { it is AnilistResolution.Failed },
                resolution = resolution,
            )

            val statusText = when (resolution) {
                is AnilistResolution.Resolved -> "✓ AniList ${resolution.anilistId} (${resolution.method})"
                is AnilistResolution.Failed -> "✗ ${resolution.reason}"
            }
            Log.i(TAG, "  [${index + 1}/${anime.size}] '${ani.title}' → $statusText")
        }

        // ── Phase 2: Build BackupContainer ──
        val container = buildContainer(aniyomiBackup, resolutions)

        val stats = TranslationStats(
            totalAnime = anime.size,
            resolvedAnime = resolutions.count { it is AnilistResolution.Resolved },
            failedAnime = resolutions.count { it is AnilistResolution.Failed },
            totalEpisodes = anime.sumOf { it.episodes.size },
            totalCategories = aniyomiBackup.backupAnimeCategories.size,
            totalManga = aniyomiBackup.backupManga.size,
            totalMangaCategories = aniyomiBackup.backupCategories.size,
        )

        Log.i(TAG, "═══ Translation complete: ${stats.resolvedAnime}/${stats.totalAnime} resolved ═══")

        _progress.value = null
        TranslationResult(container, resolutions, stats)
    }

    /**
     * Resolves the AniList ID for a single Aniyomi anime.
     *
     * Strategy (in priority order):
     * 1. AniList tracker binding (syncId == 2, mediaId = AniList ID)
     * 2. MAL tracker binding → AniList lookup (syncId == 1, mediaId = MAL ID)
     * 3. Title-based AniList search
     */
    private suspend fun resolveAnilistId(ani: AniyomiBackupAnime): AnilistResolution {
        // Strategy 1: AniList tracker binding
        val anilistTrack = ani.tracking.firstOrNull { it.syncId == 2 && it.mediaId != 0L }
        if (anilistTrack != null) {
            val anilistId = anilistTrack.mediaId.toInt()
            val anilistAnime = try { anilistApi.fetchById(anilistId) } catch (e: Exception) { null }
            return AnilistResolution.Resolved(anilistId, anilistAnime, "tracker")
        }

        // Strategy 2: MAL tracker binding → AniList lookup
        val malTrack = ani.tracking.firstOrNull { it.syncId == 1 && it.mediaId != 0L }
        if (malTrack != null) {
            val malId = malTrack.mediaId.toInt()
            val anilistAnime = anilistApi.searchByMalId(malId)
            if (anilistAnime != null) {
                return AnilistResolution.Resolved(anilistAnime.id, anilistAnime, "mal-lookup")
            }
        }

        // Strategy 3: Title search
        val title = ani.title.ifBlank { return AnilistResolution.Failed(ani.title, "Empty title") }
        val anilistAnime = anilistApi.searchByTitle(title)
        if (anilistAnime != null) {
            return AnilistResolution.Resolved(anilistAnime.id, anilistAnime, "title-search")
        }

        return AnilistResolution.Failed(ani.title, "No AniList match")
    }

    /**
     * Builds the ANIKUTA [BackupContainer] from the Aniyomi backup + resolutions.
     */
    private fun buildContainer(
        aniyomi: AniyomiBackup,
        resolutions: List<AnilistResolution>,
    ): BackupContainer {
        val entries = mutableListOf<BackupEntry>()
        val anime = aniyomi.backupAnime

        // ── Library + Anime details (only resolved anime) ──
        val resolvedAnimeBackups = anime.mapIndexedNotNull { index, ani ->
            val res = resolutions[index] as? AnilistResolution.Resolved ?: return@mapIndexedNotNull null
            buildAnimeBackup(ani, res)
        }
        if (resolvedAnimeBackups.isNotEmpty()) {
            entries.add(BackupEntry.Library(animes = resolvedAnimeBackups.filter { it.favorite }))
            entries.add(BackupEntry.AnimeDetails(animes = resolvedAnimeBackups))
        }

        // ── Episodes (keyed by anilistId) ──
        val episodesByAnime = mutableMapOf<String, List<EpisodeBackup>>()
        anime.forEachIndexed { index, ani ->
            val res = resolutions[index] as? AnilistResolution.Resolved ?: return@forEachIndexed
            if (ani.episodes.isNotEmpty()) {
                val eps = ani.episodes.map { ep ->
                    EpisodeBackup(
                        animeId = res.anilistId.toLong(),
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
                episodesByAnime[res.anilistId.toString()] = eps
            }
        }
        if (episodesByAnime.isNotEmpty()) {
            entries.add(BackupEntry.Episodes(byAnime = episodesByAnime))
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
            // Build anime→category links using resolved anilistIds
            val links = mutableListOf<app.confused.anikuta.core.backup.model.AnimeCategoryBackup>()
            anime.forEachIndexed { index, ani ->
                val res = resolutions[index] as? AnilistResolution.Resolved ?: return@forEachIndexed
                ani.categories.forEach { catId ->
                    links.add(app.confused.anikuta.core.backup.model.AnimeCategoryBackup(
                        animeId = res.anilistId.toLong(),
                        categoryId = catId,
                    ))
                }
            }
            entries.add(BackupEntry.Categories(categories = cats, links = links))
        }

        // ── Watch progress ──
        val progressEntries = mutableMapOf<String, WatchProgressItem>()
        anime.forEachIndexed { index, ani ->
            val res = resolutions[index] as? AnilistResolution.Resolved ?: return@forEachIndexed
            ani.history.forEach { hist ->
                val key = "${res.anilistId}:${hist.url}"
                progressEntries[key] = WatchProgressItem(
                    positionSeconds = hist.readDuration.toInt(),
                    durationSeconds = 0,
                    title = ani.title,
                    updatedAt = hist.lastRead,
                    animeTitle = ani.title,
                    coverUrl = ani.thumbnailUrl ?: res.anilistAnime?.coverImage?.large,
                )
            }
        }
        if (progressEntries.isNotEmpty()) {
            entries.add(BackupEntry.WatchProgress(progress = WatchProgressBackup(entries = progressEntries)))
        }

        // ─<arg_value> Tracker bindings ──
        val trackItems = mutableListOf<TrackerTrackItem>()
        anime.forEachIndexed { index, ani ->
            val res = resolutions[index] as? AnilistResolution.Resolved ?: return@forEachIndexed
            ani.tracking.forEach { tr ->
                trackItems.add(TrackerTrackItem(
                    animeId = res.anilistId.toLong(),
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
        entries.add(BackupEntry.Tracker(data = TrackerBackupModel(bindings = trackItems)))

        // ── Source links ──
        val sourceLinks = mutableMapOf<String, SourceLinkItem>()
        anime.forEachIndexed { index, ani ->
            val res = resolutions[index] as? AnilistResolution.Resolved ?: return@forEachIndexed
            sourceLinks[res.anilistId.toString()] = SourceLinkItem(
                sourceId = ani.source,
                animeUrl = ani.url,
                animeTitle = ani.title,
            )
        }
        if (sourceLinks.isNotEmpty()) {
            entries.add(BackupEntry.SourceLinks(links = SourceLinkBackup(sourceLinks = sourceLinks)))
        }

        return BackupContainer(
            schemaVersion = BackupContainer.CURRENT_SCHEMA_VERSION,
            createdAt = System.currentTimeMillis(),
            appVersion = "aniyomi-translation",
            entries = entries,
        )
    }

    /** Builds an [AnimeBackup] from an Aniyomi anime + resolved AniList data. */
    private fun buildAnimeBackup(ani: AniyomiBackupAnime, res: AnilistResolution.Resolved): AnimeBackup {
        val anilistData = res.anilistAnime
        return AnimeBackup(
            sourceId = ani.source,
            url = ani.url,
            title = anilistData?.title?.romaji ?: anilistData?.title?.english ?: ani.title,
            artist = ani.artist,
            author = ani.author,
            description = anilistData?.description ?: ani.description,
            genre = anilistData?.genres?.joinToString(",") ?: ani.genre.joinToString(","),
            coverUrl = anilistData?.coverImage?.large ?: ani.thumbnailUrl,
            status = ani.status.toLong(),
            thumbnailUrl = ani.thumbnailUrl,
            favorite = ani.favorite,
            dateAdded = ani.dateAdded,
            updateStrategy = ani.updateStrategy.toLong(),
            coverLastModified = ani.lastModifiedAt,
            anilistId = res.anilistId.toLong(),
            coverColor = anilistData?.coverImage?.color,
            score = anilistData?.averageScore?.toDouble(),
            totalEpisodes = anilistData?.episodes?.toLong(),
            nextAiringEpisode = anilistData?.nextAiringEpisode?.episode?.toLong(),
        )
    }
}
