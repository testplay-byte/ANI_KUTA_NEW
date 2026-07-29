package app.confused.anikuta.data.extension.details

import android.graphics.BitmapFactory
import android.util.Log
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.details.toUnifiedAnime
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.Episode
import app.confused.anikuta.core.common.model.ExtensionSystem
import app.confused.anikuta.core.common.model.SourceProvenance
import app.confused.anikuta.core.common.model.details.AnimeDetailsProvider
import app.confused.anikuta.core.common.model.details.DataSource
import app.confused.anikuta.core.common.model.details.DetailsRequest
import app.confused.anikuta.core.common.model.details.DetailsResult
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.common.repository.EpisodeRepository
import app.confused.anikuta.data.extension.cache.ExtensionLinkStore
import app.confused.anikuta.data.extension.cache.SourceLinkStore
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * [AnimeDetailsProvider] for [DataSource.EXTENSION].
 *
 * Translates an extension `SAnime` into a [UnifiedAnime] and fetches the
 * episode list from the extension. Key behaviors (per doc 05 §1.1):
 *
 * 1. **`getAnimeDetails` enrichment** — if `SAnime.initialized == false`,
 *    calls `source.getAnimeDetails(sAnime)` to fetch the full metadata
 *    (longer description, genres, status, thumbnail). **This closes the gap
 *    ANIKUTA had** (doc 02 — `getAnimeDetails` was never called before).
 *    30s timeout + fallback to the un-enriched SAnime (Risk R1 mitigation).
 *
 * 2. **AniList merge** — if the extension anime is linked to AniList
 *    ([ExtensionLinkStore] has a mapping, or [DetailsRequest.ByExtension.anilistId]
 *    is non-null), fetches the AniList data and merges AniList-only fields
 *    (score/format/season/studios/next-airing) into the unified value. Gives
 *    linked extension anime the best of both sources.
 *
 * 3. **Palette cover-color extraction** — for extension covers (which lack
 *    AniList's `coverImage.color` field), downloads the cover bitmap via
 *    OkHttp and extracts the dominant color via [PaletteExtraction.extractFromBitmap]
 *    (Phase 9). The hex color flows into [UnifiedAnime.coverColorHex] so the
 *    adaptive-theme block in `AnimeDetailScreen` works for extension anime too.
 *
 * 4. **Episode persistence** — fetched episodes are persisted to SQLDelight
 *    (keyed by `sourceId + url` for unlinked anime, or `anilistId` for linked)
 *    so re-open is instant + offline. **Unlinked extension anime are visible
 *    in the library** via `AnimeRepository.getBySourceAndUrl` (doc 04 §6).
 *
 * @param anilistApi for the AniList merge.
 * @param sourceMatcher resolves `sourceId` → `AnimeCatalogueSource`.
 * @param animeRepository DB persistence.
 * @param episodeRepository DB persistence.
 * @param sourceLinkStore saved AniList→extension links.
 * @param extensionLinkStore saved extension→AniList links (reverse lookup).
 */
class ExtensionDetailsProvider(
    private val anilistApi: AniListApi,
    private val sourceMatcher: SourceMatcher,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val sourceLinkStore: SourceLinkStore,
    private val extensionLinkStore: ExtensionLinkStore,
) : AnimeDetailsProvider {

    override val dataSource: DataSource = DataSource.EXTENSION

    // Dedicated OkHttp client for Palette bitmap fetch (short timeouts — non-critical).
    private val bitmapClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun load(request: DetailsRequest, forceRefresh: Boolean): DetailsResult? = when (request) {
        is DetailsRequest.ByExtension -> loadByExtension(
            sourceId = request.sourceId,
            animeUrl = request.animeUrl,
            animeTitle = request.animeTitle,
            anilistId = request.anilistId,
            forceRefresh = forceRefresh,
        )
        is DetailsRequest.ByAniListId -> {
            // Extension provider serving an AniList-keyed request: reverse-lookup the
            // preferred extension source + reconstruct the SAnime, then load by extension.
            // Phase 4: SourceLinkStore keys by content_id ("al:$anilistId").
            val savedLink = sourceLinkStore.getLink("al:${request.anilistId}") ?: return null
            loadByExtension(
                sourceId = savedLink.sourceId,
                animeUrl = savedLink.animeUrl,
                animeTitle = savedLink.animeTitle,
                anilistId = request.anilistId,
                forceRefresh = forceRefresh,
            )
        }
    }

    private suspend fun loadByExtension(
        sourceId: Long,
        animeUrl: String,
        animeTitle: String,
        anilistId: Int?,
        forceRefresh: Boolean = false,
    ): DetailsResult? {
        // ── DB-first short-circuit (skip network on re-open) ──
        // Per owner feedback: the page was reloading (getAnimeDetails + Palette + matchAll)
        // every time the user opened an anime set to "View from Extension". Now we check
        // the DB first — if we have an Anime row + episodes, return them instantly.
        // Pull-to-refresh passes forceRefresh=true to bypass this.
        if (!forceRefresh) {
            val dbAnime = when {
                anilistId != null -> animeRepository.getByAnilistId(anilistId)
                else -> animeRepository.getBySourceAndUrl(sourceId, animeUrl)
            }
            if (dbAnime != null) {
                val dbEpisodes = episodeRepository.getByAnimeId(dbAnime.id)
                if (dbEpisodes.isNotEmpty()) {
                    Log.i(TAG, "DB-first short-circuit: loaded ${dbEpisodes.size} episodes from DB for '${dbAnime.title}' (anilistId=$anilistId)")
                    val sourceName = sourceMatcher.getSourceById(sourceId)?.name
                        ?: dbAnime.provenance?.sourceName
                        ?: dbAnime.provenance?.extensionName
                        ?: dbAnime.title
                    val unified = dbAnime.toUnifiedAnimeFromDb(
                        sourceId = sourceId,
                        sourceName = sourceName,
                        anilistId = anilistId,
                    )
                    return DetailsResult(anime = unified, episodes = dbEpisodes)
                }
            }
        }

        val source = withContext(Dispatchers.IO) { sourceMatcher.getSourceById(sourceId) }
            ?: run {
                Log.w(TAG, "Extension source $sourceId not installed — cannot load")
                return null
            }

        // ── Build the SAnime (from the URL + title) ──
        val sAnime = SAnimeImpl().apply {
            url = animeUrl
            title = animeTitle
            initialized = false // force enrichment below
        }

        // ── Stage A: enrich via getAnimeDetails (the gap ANIKUTA had — doc 04 §4) ──
        val enriched = enrichAnimeDetails(source, sAnime)

        // ── Cover robustness: log when an extension doesn't provide a cover ──
        // The AniList merge (Stage D) will fill coverUrl for linked anime. For unlinked,
        // the UI shows a placeholder (surfaceVariant box). No blind AniList-title-search
        // fallback — showing no cover is safer than showing the wrong one.
        if (enriched.thumbnail_url.isNullOrBlank()) {
            Log.w(TAG, "Extension '${source.name}' provided no cover for '${enriched.title}' — UI will show placeholder")
        }

        // ── Stage B: Palette-extract cover color (Phase 9 integration) ──
        val coverColorHex = extractCoverColorHex(enriched.thumbnail_url)

        // ── Stage C: build the base unified anime from the extension ──
        var unified = enriched.toUnifiedAnime(
            sourceId = source.id,
            sourceName = source.name,
            anilistId = anilistId,
            coverColorHex = coverColorHex,
        )

        // ── Stage D: optional AniList merge (linked anime only) ──
        val effectiveAnilistId = anilistId ?: extensionLinkStore.getAniListId(source.id, animeUrl)
        if (effectiveAnilistId != null) {
            val anilistMerge = withContext(Dispatchers.IO) { anilistApi.fetchById(effectiveAnilistId) }
            if (anilistMerge != null) {
                unified = unified.mergeAniListMetadata(anilistMerge.toUnifiedAnime())
                Log.i(TAG, "Merged AniList metadata for linked extension anime (anilistId=$effectiveAnilistId)")
            }
        }

        // ── Stage E: fetch + persist episodes ──
        val episodes = fetchAndPersistEpisodes(source, enriched, effectiveAnilistId)
        unified = unified.copy(episodeCount = episodes.size.takeIf { it > 0 } ?: unified.episodeCount)

        return DetailsResult(anime = unified, episodes = episodes)
    }

    /**
     * Load ONLY episodes — without re-fetching anime metadata (no getAnimeDetails,
     * no Palette, no AniList merge). Used when the user switches extension from the
     * episodes header while in AniList mode: the anime metadata stays, only episodes
     * refresh from the new extension.
     */
    override suspend fun loadEpisodes(request: DetailsRequest): List<Episode>? = when (request) {
        is DetailsRequest.ByExtension -> {
            val source = withContext(Dispatchers.IO) { sourceMatcher.getSourceById(request.sourceId) }
                ?: return null
            val sAnime = SAnimeImpl().apply {
                url = request.animeUrl
                title = request.animeTitle
            }
            fetchAndPersistEpisodes(source, sAnime, request.anilistId)
        }
        is DetailsRequest.ByAniListId -> {
            // Phase 4: SourceLinkStore keys by content_id ("al:$anilistId").
            val savedLink = sourceLinkStore.getLink("al:${request.anilistId}") ?: return null
            val source = withContext(Dispatchers.IO) { sourceMatcher.getSourceById(savedLink.sourceId) }
                ?: return null
            val sAnime = SAnimeImpl().apply {
                url = savedLink.animeUrl
                title = savedLink.animeTitle
            }
            fetchAndPersistEpisodes(source, sAnime, request.anilistId)
        }
    }

    /**
     * Calls `source.getAnimeDetails(sAnime)` to enrich a partial SAnime (from search
     * results) with full metadata. 30s timeout + fallback (Risk R1).
     *
     * **This is the gap ANIKUTA had** (doc 02): `getAnimeDetails` was never called,
     * so extension data was rendered from partial search results. Calling it here
     * gives the user the full description/genres/status/thumbnail.
     */
    private suspend fun enrichAnimeDetails(
        source: AnimeCatalogueSource,
        sAnime: SAnime,
    ): SAnime {
        if (sAnime.initialized) return sAnime
        return try {
            val enriched = withContext(Dispatchers.IO) { source.getAnimeDetails(sAnime) }
            Log.i(TAG, "Enriched SAnime via getAnimeDetails on '${source.name}'")
            enriched
        } catch (e: Throwable) {
            // Catch Throwable — extension binary-incompat throws Error subclasses.
            Log.w(TAG, "getAnimeDetails failed on '${source.name}' — using partial SAnime", e)
            sAnime
        }
    }

    /**
     * Palette-extracts the dominant color from [coverUrl] → hex string for
     * adaptive theming. Returns null if extraction fails (the UI falls back
     * to the default theme).
     *
     * Uses OkHttp (already a dep of :data:extension) + BitmapFactory to load
     * the bitmap, then delegates to [PaletteExtraction.extractFromBitmap]
     * (Phase 9 — implemented in :core:designsystem). No Coil dependency needed.
     */
    private suspend fun extractCoverColorHex(coverUrl: String?): String? {
        if (coverUrl.isNullOrBlank()) return null
        return try {
            val bitmap = withContext(Dispatchers.IO) {
                val response = bitmapClient.newCall(Request.Builder().url(coverUrl).build()).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bytes = resp.body?.bytes() ?: return@use null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
            val argb = app.confused.anikuta.core.designsystem.theme.PaletteExtraction.extractFromBitmap(bitmap)
            if (argb != null && argb != 0) {
                val hex = String.format("#%06X", 0xFFFFFF and argb)
                Log.i(TAG, "Palette-extracted cover color: $hex from $coverUrl")
                hex
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Palette extraction failed for $coverUrl (non-fatal)", e)
            null
        }
    }

    /**
     * Fetches episodes from the extension + persists them to SQLDelight.
     *
     * For **linked** anime (anilistId != null): keyed by anilistId (same as AniList mode).
     * For **unlinked** anime (anilistId == null): keyed by `sourceId + url` via
     * `AnimeRepository.getBySourceAndUrl` — this makes unlinked extension anime
     * visible in the library (doc 04 §6, owner requirement Q2).
     */
    private suspend fun fetchAndPersistEpisodes(
        source: AnimeCatalogueSource,
        sAnime: SAnime,
        anilistId: Int?,
    ): List<Episode> {
        return try {
            val sEpisodes = withContext(Dispatchers.IO) { source.getEpisodeList(sAnime) }
            if (sEpisodes.isEmpty()) return emptyList()
            persistEpisodes(sEpisodes, source.id, sAnime.url, sAnime.title, anilistId, sAnime, source)
            sEpisodes.mapIndexed { index, ep -> ep.toDomainEpisode(index) }
        } catch (e: Throwable) {
            Log.e(TAG, "getEpisodeList failed on '${source.name}'", e)
            emptyList()
        }
    }

    /** Persists episodes to the DB, creating the anime row if needed. */
    private suspend fun persistEpisodes(
        sEpisodes: List<eu.kanade.tachiyomi.animesource.model.SEpisode>,
        sourceId: Long,
        animeUrl: String,
        animeTitle: String,
        anilistId: Int?,
        sAnime: SAnime,
        source: AnimeCatalogueSource,
    ) {
        try {
            // Find or create the anime row. Linked → by anilistId; unlinked → by sourceId+url.
            var dbAnime = if (anilistId != null) {
                animeRepository.getByAnilistId(anilistId)
            } else {
                animeRepository.getBySourceAndUrl(sourceId, animeUrl)
            }
            if (dbAnime == null) {
                val now = System.currentTimeMillis()
                val newAnime = Anime(
                    id = 0,
                    url = animeUrl,
                    title = animeTitle,
                    artist = sAnime.artist,
                    author = sAnime.author,
                    description = sAnime.description,
                    genre = sAnime.getGenres() ?: emptyList(),
                    coverUrl = sAnime.thumbnail_url,
                    status = sAnime.status,
                    thumbnailUrl = null,
                    favorite = false,
                    sourceId = sourceId,
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
                    coverColor = null, // Palette color lives on UnifiedAnime, not the DB row
                    score = null,
                    totalEpisodes = null,
                    lastWatched = 0,
                    nextAiringEpisode = null,
                )
                val newId = animeRepository.upsert(newAnime)
                dbAnime = animeRepository.getById(newId)
            }
            if (dbAnime != null) {
                // ── Populate source provenance (Issue 4 fix) ──
                // Always refresh provenance from the LIVE extension (the extension may
                // have been updated since the row was last touched). When the extension
                // is later uninstalled, the details page falls back to this provenance
                // to display the source name + extension name + version — instead of
                // the wrong fallback (`dbAnime.title`).
                persistProvenance(dbAnime.id, source)

                episodeRepository.deleteByAnimeId(dbAnime.id)
                sEpisodes.forEachIndexed { index, ep ->
                    episodeRepository.upsert(ep.toDomainEpisode(index).copy(animeId = dbAnime.id))
                }
                Log.i(TAG, "Persisted ${sEpisodes.size} episodes for anime (anilistId=$anilistId, sourceId=$sourceId)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist episodes (non-fatal)", e)
        }
    }

    /**
     * Builds + writes a [SourceProvenance] for the anime row, using data from the
     * live extension (resolved via [sourceMatcher.getExtensionForSource]) + the
     * live [AnimeCatalogueSource] (always available when persisting).
     *
     * Fields populated:
     * - [SourceProvenance.system] = `ANIYOMI` (all current extensions are Aniyomi-system).
     * - [SourceProvenance.sourceName] = `source.name` (the source's display name).
     * - [SourceProvenance.extensionName] / `extensionPkgName` / `versionName` / `versionCode` /
     *   `lang` / `isNsfw` / `repoUrl` — from the installed extension (if still installed).
     *
     * When the extension is later uninstalled, the [ExtensionDetailsProvider.loadByExtension]
     * fallback chain uses `dbAnime.provenance?.sourceName ?: provenance?.extensionName` to
     * render the correct "Source unavailable" chip text.
     */
    private suspend fun persistProvenance(
        animeId: Long,
        source: AnimeCatalogueSource,
    ) {
        try {
            val ext = sourceMatcher.getExtensionForSource(source.id)
            val now = System.currentTimeMillis()
            val provenance = SourceProvenance(
                system = ExtensionSystem.ANIYOMI,
                repoUrl = ext?.repoUrl,
                repoName = null, // resolved from the repo list if needed in a follow-up
                extensionPkgName = ext?.pkgName,
                extensionName = ext?.name,
                extensionVersionName = ext?.versionName,
                extensionVersionCode = ext?.versionCode,
                extensionLang = ext?.lang,
                isNsfw = ext?.isNsfw ?: false,
                sourceName = source.name,
                discoveredAt = now,
                lastResolvedAt = now,
                linkConfidence = 0,
            )
            animeRepository.updateProvenance(animeId, provenance)
            Log.d(TAG, "persistProvenance: id=$animeId sourceName='${source.name}' " +
                "extName='${ext?.name}' pkg='${ext?.pkgName}'")
        } catch (e: Exception) {
            Log.w(TAG, "persistProvenance: failed (non-fatal) — id=$animeId, " +
                "sourceName='${source.name}'", e)
        }
    }

    companion object {
        private const val TAG = "AnikutaExtProvider"
    }
}

/** Maps an Aniyomi `SEpisode` to a domain [Episode] (un-persisted — `id=0`, `animeId=0`). */
private fun eu.kanade.tachiyomi.animesource.model.SEpisode.toDomainEpisode(
    index: Int,
): Episode = Episode(
    id = 0,
    animeId = 0,
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
