package app.confused.anikuta.data.anime.details

import android.content.Context
import android.util.Log
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.details.toUnifiedAnime
import app.confused.anikuta.core.anilist.model.coverUrl
import app.confused.anikuta.core.anilist.model.displayTitle
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.Episode
import app.confused.anikuta.core.common.model.details.AnimeDetailsProvider
import app.confused.anikuta.core.common.model.details.DataSource
import app.confused.anikuta.core.common.model.details.DetailsRequest
import app.confused.anikuta.core.common.model.details.DetailsResult
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.common.repository.EpisodeRepository
import app.confused.anikuta.data.extension.cache.ExtensionLinkStore
import app.confused.anikuta.data.extension.cache.SourceLinkStore
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import eu.kanade.tachiyomi.animesource.model.SAnimeImpl
import eu.kanade.tachiyomi.animesource.model.SEpisodeImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [AnimeDetailsProvider] for [DataSource.ANILIST].
 *
 * Wraps the existing three-stage load (doc 01 §2):
 *  1. **AniList fetch** — `AniListApi.fetchById(anilistId)` → `AniListAnime`.
 *  2. **Source match** — `SourceMatcher.matchAll(title)` or a saved `SourceLinkStore`
 *     link → the `AnimeCatalogueSource` + `SAnime` to fetch episodes from.
 *  3. **Episode fetch** — `source.getEpisodeList(sAnime)` → `List<SEpisode>`,
 *     persisted to the DB for offline re-open.
 *
 * Returns a [DetailsResult] with the unified anime + episodes (as domain
 * [Episode]s). The ViewModel separately fetches episode metadata + resolves
 * the `AnimeSource` for the watch/download flows (those need `:core:source-api`
 * + `:core:episode-metadata` which the VM has).
 *
 * @param anilistApi the AniList GraphQL client.
 * @param sourceMatcher searches installed extensions by title.
 * @param animeRepository DB persistence (for the library row + offline episodes).
 * @param episodeRepository DB persistence (episodes).
 * @param sourceLinkStore saved AniList→extension source links (skips re-matching).
 * @param extensionLinkStore reverse-lookup: which source did the user originally
 *   link this AniList anime to? (Preferred-source hint.)
 * @param appContext for SharedPreferences (per-anime source preference).
 */
class AniListDetailsProvider(
    private val anilistApi: AniListApi,
    private val sourceMatcher: SourceMatcher,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val sourceLinkStore: SourceLinkStore,
    private val extensionLinkStore: ExtensionLinkStore,
    private val appContext: Context,
) : AnimeDetailsProvider {

    override val dataSource: DataSource = DataSource.ANILIST

    override suspend fun load(request: DetailsRequest): DetailsResult? = when (request) {
        is DetailsRequest.ByAniListId -> loadByAniListId(request.anilistId)
        is DetailsRequest.ByExtension -> {
            // AniList provider can only serve AniList-keyed lookups. If the extension
            // anime is linked, load by its anilistId; otherwise return null (the
            // ExtensionDetailsProvider handles the unlinked case).
            val anilistId = request.anilistId
                ?: extensionLinkStore.getAniListId(request.sourceId, request.animeUrl)
            if (anilistId != null) loadByAniListId(anilistId) else null
        }
    }

    private suspend fun loadByAniListId(anilistId: Int): DetailsResult? {
        // ── Stage 1: AniList fetch ──
        val anilistAnime = withContext(Dispatchers.IO) { anilistApi.fetchById(anilistId) }
            ?: return null

        // ── Stage 2 + 3: source match + episode fetch ──
        // (Non-fatal: AniList-only mode still works if no extension matches.)
        val (episodes, matchedSourceId, matchedSourceName) = loadEpisodes(anilistId, anilistAnime.displayTitle)

        val unified = anilistAnime.toUnifiedAnime(
            matchedSourceId = matchedSourceId,
            matchedSourceName = matchedSourceName,
        )
        return DetailsResult(anime = unified, episodes = episodes)
    }

    /**
     * Load ONLY episodes — used when the user switches extension from the episodes
     * header while in AniList mode. Skips the DB-first short-circuit (forces a fresh
     * fetch from the new extension source).
     */
    override suspend fun loadEpisodes(request: DetailsRequest): List<Episode>? = when (request) {
        is DetailsRequest.ByAniListId -> {
            // Use the saved source link (or match) to fetch fresh episodes.
            val savedLink = sourceLinkStore.getLink(request.anilistId)
            if (savedLink != null) {
                val source = withContext(Dispatchers.IO) { sourceMatcher.getSourceById(savedLink.sourceId) }
                if (source != null) {
                    val sAnime = SAnimeImpl().apply {
                        url = savedLink.animeUrl
                        title = savedLink.animeTitle
                    }
                    fetchAndPersistEpisodes(source, sAnime, request.anilistId)
                } else null
            } else null
        }
        is DetailsRequest.ByExtension -> {
            // Resolve the source + fetch episodes directly.
            val source = withContext(Dispatchers.IO) { sourceMatcher.getSourceById(request.sourceId) }
                ?: return null
            val sAnime = SAnimeImpl().apply {
                url = request.animeUrl
                title = request.animeTitle
            }
            fetchAndPersistEpisodes(source, sAnime, request.anilistId)
        }
    }

    /**
     * Stage 2 + 3. Mirrors the `findAndLoadEpisodes` + `loadEpisodes` + `saveEpisodesToDb`
     * logic from the old `AnimeDetailViewModel` (doc 01 §2), but returns the episodes
     * instead of emitting to StateFlows.
     *
     * Order: DB-first short-circuit → saved SourceLinkStore link → fresh matchAll.
     * Episodes are persisted to the DB for offline re-open (per user requirement).
     *
     * @return (episodes, matchedSourceId, matchedSourceName). Episodes may be empty
     *   if no source matched; sourceId/Name may be null in that case.
     */
    private suspend fun loadEpisodes(
        anilistId: Int,
        title: String,
    ): Triple<List<Episode>, Long?, String?> {
        // ── DB-first: if we already have episodes saved, return them instantly ──
        val dbAnime = animeRepository.getByAnilistId(anilistId)
        if (dbAnime != null) {
            val dbEpisodes = episodeRepository.getByAnimeId(dbAnime.id)
            if (dbEpisodes.isNotEmpty()) {
                Log.i(TAG, "AniList provider: loaded ${dbEpisodes.size} episodes from DB for anilistId=$anilistId")
                val savedLink = sourceLinkStore.getLink(anilistId)
                val sourceName = savedLink?.let { sourceMatcher.getSourceById(it.sourceId)?.name }
                return Triple(dbEpisodes, savedLink?.sourceId, sourceName)
            }
        }

        // ── Saved source link: reconstruct the SAnime + fetch fresh episodes ──
        val savedLink = sourceLinkStore.getLink(anilistId)
        if (savedLink != null) {
            val source = withContext(Dispatchers.IO) { sourceMatcher.getSourceById(savedLink.sourceId) }
            if (source != null) {
                val sAnime = SAnimeImpl().apply {
                    url = savedLink.animeUrl
                    this.title = savedLink.animeTitle
                }
                val episodes = fetchAndPersistEpisodes(source, sAnime, anilistId)
                if (episodes.isNotEmpty()) {
                    return Triple(episodes, source.id, source.name)
                }
            } else {
                Log.w(TAG, "Saved source ${savedLink.sourceId} not installed — falling back to search")
                sourceLinkStore.removeLink(anilistId)
            }
        }

        // ── Fresh search: matchAll across installed sources ──
        return try {
            val all = withContext(Dispatchers.IO) { sourceMatcher.matchAll(title) }
            if (all.isEmpty()) {
                Log.i(TAG, "AniList provider: no sources matched '$title'")
                return Triple(emptyList(), null, null)
            }
            val explicitPrefId = sourcePrefs.getLong(sourcePrefKey(anilistId), -1L)
            val linkedPrefId = extensionLinkStore.getPreferredSourceForAnilist(anilistId)
            val preferredSourceId = when {
                explicitPrefId != -1L -> explicitPrefId
                linkedPrefId != null -> linkedPrefId
                else -> -1L
            }
            val selected = all.firstOrNull { it.source.id == preferredSourceId } ?: all.first()
            Log.i(TAG, "AniList provider: matched '${selected.source.name}' for '$title'")

            // Save the link so we don't re-search next time
            sourceLinkStore.saveLink(
                anilistId = anilistId,
                sourceId = selected.source.id,
                animeUrl = selected.sAnime.url,
                animeTitle = selected.sAnime.title,
            )
            val episodes = fetchAndPersistEpisodes(selected.source, selected.sAnime, anilistId)
            Triple(episodes, selected.source.id, selected.source.name)
        } catch (e: Throwable) {
            // Catch Throwable (not Exception) — extension binary-incompat throws Error subclasses.
            Log.e(TAG, "AniList provider: source matching failed for '$title'", e)
            Triple(emptyList(), null, null)
        }
    }

    /**
     * Calls `source.getEpisodeList(sAnime)`, persists the result to the DB
     * (ensuring the anime row exists first), and returns domain [Episode]s.
     */
    private suspend fun fetchAndPersistEpisodes(
        source: eu.kanade.tachiyomi.animesource.AnimeCatalogueSource,
        sAnime: eu.kanade.tachiyomi.animesource.model.SAnime,
        anilistId: Int?,
    ): List<Episode> {
        return try {
            val sEpisodes = withContext(Dispatchers.IO) { source.getEpisodeList(sAnime) }
            if (sEpisodes.isEmpty()) return emptyList()
            // Only persist to DB for linked anime (anilistId != null). For unlinked,
            // the ExtensionDetailsProvider handles persistence via getBySourceAndUrl.
            if (anilistId != null) {
                saveEpisodesToDb(sEpisodes, anilistId)
            }
            sEpisodes.mapIndexed { index, ep ->
                ep.toDomainEpisode(index)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "AniList provider: getEpisodeList failed on '${source.name}'", e)
            emptyList()
        }
    }

    /** Persists fetched SEpisodes to SQLDelight (ensures the anime row exists first). */
    private suspend fun saveEpisodesToDb(
        sEpisodes: List<eu.kanade.tachiyomi.animesource.model.SEpisode>,
        anilistId: Int,
    ) {
        try {
            var dbAnime = animeRepository.getByAnilistId(anilistId)
            if (dbAnime == null) {
                val anilistAnime = withContext(Dispatchers.IO) { anilistApi.fetchById(anilistId) }
                val now = System.currentTimeMillis()
                val newAnime = Anime(
                    id = 0,
                    url = "anilist:$anilistId",
                    title = anilistAnime?.displayTitle ?: "",
                    artist = null,
                    author = null,
                    description = anilistAnime?.description,
                    genre = anilistAnime?.genres ?: emptyList(),
                    coverUrl = anilistAnime?.coverUrl,
                    status = 0,
                    thumbnailUrl = null,
                    favorite = false,
                    sourceId = 0,
                    dateAdded = now,
                    viewerFlags = 0,
                    nextUpdate = 0,
                    updateStrategy = 0,
                    coverLastModified = 0,
                    releaseDate = null,
                    lastRefresh = now,
                    lastMetadataFetch = now,
                    nextEpisodeCheck = null,
                    anilistId = anilistId,
                    coverColor = anilistAnime?.coverImage?.color,
                    score = anilistAnime?.averageScore?.toDouble(),
                    totalEpisodes = anilistAnime?.episodes,
                    lastWatched = 0,
                    nextAiringEpisode = anilistAnime?.nextAiringEpisode?.episode,
                )
                val newId = animeRepository.upsert(newAnime)
                dbAnime = animeRepository.getById(newId)
            }
            if (dbAnime != null) {
                episodeRepository.deleteByAnimeId(dbAnime.id)
                sEpisodes.forEachIndexed { index, ep ->
                    episodeRepository.upsert(ep.toDomainEpisode(anilistId, index).copy(animeId = dbAnime.id))
                }
                Log.i(TAG, "AniList provider: saved ${sEpisodes.size} episodes to DB for anilistId=$anilistId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "AniList provider: failed to save episodes to DB (non-fatal)", e)
        }
    }

    private val sourcePrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private fun sourcePrefKey(anilistId: Int) = "source_pref_$anilistId"

    companion object {
        private const val TAG = "AnikutaAniListProvider"
        private const val PREFS_NAME = "anikuta_source_prefs"
    }
}

/**
 * Maps an Aniyomi `SEpisode` to the domain [Episode] (un-persisted — `id=0`, `animeId=0`).
 * The caller ([AniListDetailsProvider.saveEpisodesToDb]) fills `animeId` before upsert.
 */
private fun eu.kanade.tachiyomi.animesource.model.SEpisode.toDomainEpisode(
    index: Int,
): Episode = Episode(
    id = 0,
    animeId = 0, // filled by the caller before upsert
    url = url,
    name = name,
    episodeNumber = episode_number,
    scanlator = scanlator,
    seen = false,
    bookmark = false,
    lastSecondSeen = 0,
    totalSeconds = 0,
    sourceOrder = index.toLong(),
    dateFetch = System.currentTimeMillis(),
    dateUpload = date_upload.takeIf { it > 0 },
    fillermark = if (fillermark) "filler" else null,
    summary = summary,
    previewUrl = preview_url,
)
