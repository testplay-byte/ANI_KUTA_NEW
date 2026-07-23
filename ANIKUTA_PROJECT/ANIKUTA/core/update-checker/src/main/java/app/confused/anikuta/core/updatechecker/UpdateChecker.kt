package app.confused.anikuta.core.updatechecker

import android.util.Log
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.repository.AnimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Checks library anime for new episode releases.
 *
 * This is the reusable, UI-agnostic core of the "Updates" feature. It is
 * consumed by the Updates page (`:feature:updates`) for manual pull-to-refresh
 * and is designed to be callable from a future `WorkManager` periodic worker
 * for background checking — without rewriting any of the logic here.
 *
 * ## Data flow
 *
 * ```
 * AnimeRepository.observeFavorites()  ──┐
 *                                       ├─→ for each anime:
 * EpisodeFetchGateway.fetchEpisodes() ──┤    1. fetch current episode list
 *                                       │    2. diff vs UpdateCheckerPreferences.lastKnownEpisodeCount
 * AniListApi.fetchById() ───────────────┤    3. cross-ref AniList nextAiringEpisode
 *                                       │    4. persist new count + emit UpdateResult
 * UpdateCheckerPreferences ─────────────┘
 *         │
 *         └─→ _results: MutableStateFlow<List<UpdateResult>>  (reactive, read by UI)
 * ```
 *
 * ## Threading
 *
 * Every network/source call runs on `Dispatchers.IO`. [EpisodeFetchGateway]
 * implementors are responsible for shifting off the calling thread; the
 * AniList calls here are wrapped in `withContext(Dispatchers.IO)` explicitly
 * (the `AniListApi` already uses `Dispatchers.IO` internally, but the
 * library-iteration loop also does repo work, so we shift the whole loop).
 *
 * ## Error isolation
 *
 * We catch `Throwable` (not `Exception`) per-extension, because extension
 * bytecode can throw `IncompatibleClassChangeError` / `NoClassDefFoundError`
 * on binary incompat. One broken extension must never abort the whole check —
 * it just yields no result for that anime and we log + move on.
 *
 * ## Sub/dub detection
 *
 * [parseAudioAvailability] parses "SUB"/"DUB"/"HSUB" tokens from the new
 * episodes' names + scanlator fields. This is the same heuristic the
 * anime-details episode list uses, so the Updates page's pills match.
 *
 * ## Future: WorkManager background worker
 *
 * A future `UpdateCheckWorker : CoroutineWorker` would:
 *  1. Read `UpdateCheckerPreferences.checkIntervalHours()` and
 *     `lastCheckTimestamp()` to decide whether to run now.
 *  2. Call `checkForUpdates()` (this method is already a `suspend fun` and
 *     self-contained — no UI coupling).
 *  3. Post a notification for each `UpdateResult` with `newEpisodeCount > 0`.
 *  4. The worker's repeat period would be derived from `checkIntervalHours()`
 *     (e.g. `PeriodicWorkRequest.Builder(..., intervalHours, HOURS)`).
 *
 * No changes to this class would be required — that's the point of keeping
 * the check logic UI-agnostic and behind a clean suspend API.
 *
 * ## Future: extensibility
 *
 * New check strategies (RSS feeds, AniList notifications, manual per-anime
 * triggers) can be added without changing the public API:
 *  - RSS: add an `RssUpdateStrategy` that implements the same diff loop but
 *    reads an RSS feed instead of `EpisodeFetchGateway`.
 *  - AniList notifications: a future authenticated AniList client could push
 *    airing notifications; the consumer would still read `getLastResults()`.
 *  - Per-anime: [checkAnime] already supports a targeted check.
 *
 * ## Construction
 *
 * Registered in Koin via `updateCheckerModule` (see `di/UpdateCheckerModule`).
 * The [EpisodeFetchGateway] is injected — its impl lives in `:data:extension`.
 */
class UpdateChecker(
    private val animeRepository: AnimeRepository,
    private val anilistApi: AniListApi,
    private val episodeFetchGateway: EpisodeFetchGateway,
    private val preferences: UpdateCheckerPreferences,
) {

    private val _results = MutableStateFlow<List<UpdateResult>>(emptyList())
    private val _lastCheckTimestamp = MutableStateFlow(0L)
    private val _checkProgress = MutableStateFlow<UpdateCheckProgress>(UpdateCheckProgress.Idle)

    /** Reactive stream of the merged results list (old + new). Read by the Updates page. */
    fun getLastResults(): StateFlow<List<UpdateResult>> = _results.asStateFlow()

    /** Reactive stream of when the last check ran (epoch ms). 0 = never. */
    fun getLastCheckTimestamp(): StateFlow<Long> = _lastCheckTimestamp.asStateFlow()

    /**
     * Reactive stream of the live check progress. The Updates page renders a
     * "Currently checking" card from [UpdateCheckProgress.Checking] — showing
     * the anime currently being searched (poster + title + index/total) with
     * smooth transitions between anime. Emits [UpdateCheckProgress.Idle] when
     * no check is in flight, and [UpdateCheckProgress.Completed] briefly when
     * a check finishes.
     */
    fun getCheckProgress(): StateFlow<UpdateCheckProgress> = _checkProgress.asStateFlow()

    init {
        // Seed the timestamp from prefs so the UI shows the real "last checked"
        // even across process restarts.
        _lastCheckTimestamp.value = preferences.lastCheckTimestamp().get()
    }

    /**
     * Manually checks ALL library anime for new episodes.
     *
     * Emits live progress via [getCheckProgress] (per-anime: which anime is
     * being searched, its index/total, and the running found-count) so the
     * Updates page can render a "Currently checking" card. Results are MERGED
     * into the existing [getLastResults] list (not replaced) — anime already in
     * the list from a previous check are kept and marked `isNew = false`;
     * anime with new episodes in THIS check are added/updated with `isNew = true`.
     * This means navigating away and back preserves the full history of found
     * updates, and the UI can highlight the freshly-found ones.
     *
     * Safe to call from any coroutine scope (e.g. `viewModelScope` on
     * pull-to-refresh, or a future `WorkManager` worker). All network/source
     * work happens on `Dispatchers.IO`.
     *
     * @return the full merged results list after this check.
     */
    suspend fun checkForUpdates(): List<UpdateResult> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val library = try {
            // Take a single snapshot from the reactive flow.
            animeRepository.observeFavorites().first()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to read library favorites", e)
            _checkProgress.value = UpdateCheckProgress.Idle
            return@withContext _results.value
        }

        if (library.isEmpty()) {
            Log.i(TAG, "checkForUpdates: library empty, nothing to check")
            preferences.lastCheckTimestamp().set(now)
            _lastCheckTimestamp.value = now
            _checkProgress.value = UpdateCheckProgress.Completed(0, 0)
            _results.value = emptyList()
            return@withContext emptyList()
        }

        Log.i(TAG, "checkForUpdates: checking ${library.size} library anime")

        // Start from the existing results — mark ALL as old (isNew = false).
        // New/updated results from this check will be marked isNew = true.
        val merged = _results.value.associateBy { it.anime.id }
            .mapValues { it.value.copy(isNew = false) }
            .toMutableMap()
        var foundThisCheck = 0

        library.forEachIndexed { index, anime ->
            // Emit live progress BEFORE the fetch so the UI shows which anime
            // is being searched right now.
            _checkProgress.value = UpdateCheckProgress.Checking(
                currentAnime = anime,
                currentIndex = index + 1,
                totalCount = library.size,
                foundSoFar = foundThisCheck,
            )
            try {
                val result = checkAnimeInternal(anime, now)
                if (result != null && result.newEpisodeCount > 0) {
                    // Merge: add or replace this anime's entry, marked new.
                    merged[anime.id] = result.copy(isNew = true)
                    foundThisCheck++
                    // Emit incremental results so the UI list grows as we go.
                    _results.value = merged.values
                        .sortedWith(compareByDescending<UpdateResult> { it.isNew }.thenByDescending { it.newEpisodeCount })
                }
            } catch (t: Throwable) {
                // Isolate failures — one broken anime/extension must not abort
                // the whole check.
                Log.e(TAG, "checkForUpdates: failed for anime ${anime.id} (${anime.title})", t)
            }
        }

        val finalResults = merged.values
            .sortedWith(compareByDescending<UpdateResult> { it.isNew }.thenByDescending { it.newEpisodeCount })
        preferences.lastCheckTimestamp().set(now)
        _lastCheckTimestamp.value = now
        _results.value = finalResults
        _checkProgress.value = UpdateCheckProgress.Completed(foundThisCheck, library.size)

        Log.i(TAG, "checkForUpdates: done — $foundThisCheck new this check, ${finalResults.size} total in merged list")
        finalResults
    }

    /**
     * Checks a single library anime for new episodes. Future use: targeted
     * checking (e.g. user long-presses an anime → "check for updates").
     *
     * Returns the [UpdateResult] (which may have `newEpisodeCount == 0`), or
     * null if the anime isn't in the library / has no AniList ID / no source
     * matched.
     */
    suspend fun checkAnime(animeId: Long): UpdateResult? = withContext(Dispatchers.IO) {
        val anime = animeRepository.getById(animeId) ?: return@withContext null
        checkAnimeInternal(anime, System.currentTimeMillis())
    }

    /**
     * Core per-anime check. Does NOT mutate shared state (caller does that).
     *
     * Steps:
     *  1. Fetch the current episode list via [EpisodeFetchGateway].
     *  2. Diff against [UpdateCheckerPreferences.lastKnownEpisodeCount].
     *  3. The "new" episodes are those whose episode number is greater than
     *     the previously-known count. (Episode-number-based diffing is robust
     *     to source re-ordering; URL-based diffing would miss re-uploads.)
     *  4. Parse sub/dub availability from the new episodes' names + scanlator.
     *  5. Persist the new known count so the next check diffs from here.
     *  6. Cross-reference AniList's `nextAiringEpisode` to enrich the result
     *     (the Updates page can show "next ep in 2d" even when there are no
     *     new extension episodes yet).
     */
    private suspend fun checkAnimeInternal(anime: Anime, now: Long): UpdateResult? {
        val title = anime.title.ifBlank { return null }
        val knownCount = preferences.lastKnownEpisodeCount(anime.id).get()

        val fetchResult = try {
            episodeFetchGateway.fetchEpisodes(title)
        } catch (t: Throwable) {
            Log.e(TAG, "fetchEpisodes failed for \"${anime.title}\"", t)
            EpisodeFetchResult.NoSource
        }

        val sourceName: String?
        val currentEpisodes: List<EpisodeInfo>
        when (fetchResult) {
            is EpisodeFetchResult.Success -> {
                sourceName = fetchResult.sourceName
                currentEpisodes = fetchResult.episodes
            }
            EpisodeFetchResult.NoSource -> {
                sourceName = null
                currentEpisodes = emptyList()
            }
        }

        // Diff: episodes with a number strictly greater than the known count.
        // Known count 0 (never checked) → ALL fetched episodes are "new" once.
        val newEpisodes = if (currentEpisodes.isEmpty()) {
            emptyList()
        } else {
            currentEpisodes
                .filter { it.episodeNumber > knownCount.toFloat() }
                .sortedBy { it.episodeNumber }
        }

        // Persist the new known count (the highest episode number we've seen).
        if (currentEpisodes.isNotEmpty()) {
            val maxNumber = currentEpisodes.maxOf { it.episodeNumber }.toInt().coerceAtLeast(0)
            if (maxNumber > knownCount) {
                preferences.setLastKnownEpisodeCount(anime.id, maxNumber)
            }
        }

        // Parse sub/dub from the NEW episodes (not the whole list).
        var hasSub = false
        var hasDub = false
        for (ep in newEpisodes) {
            val audio = parseAudioAvailability(ep.scanlator, ep.title)
            if (audio.hasSub) hasSub = true
            if (audio.hasDub || audio.hasHsub) hasDub = true
            if (hasSub && hasDub) break
        }

        // AniList cross-reference (best-effort, non-blocking on failure).
        // We don't currently surface this on the Updates list row, but it's
        // fetched so the Updates page can show a "next airing" badge later.
        // Kept intentionally cheap — fetchById is cached 5 min in AniListApi.
        anime.anilistId?.let { aid ->
            try {
                anilistApi.fetchById(aid)
            } catch (t: Throwable) {
                Log.w(TAG, "AniList cross-ref failed for $aid (non-fatal)", t)
            }
        }

        return UpdateResult(
            anime = anime,
            newEpisodeCount = newEpisodes.size,
            newEpisodes = newEpisodes,
            checkedAt = now,
            hasSub = hasSub,
            hasDub = hasDub,
            sourceName = sourceName,
            isNew = true,
        )
    }

    companion object {
        private const val TAG = "UpdateChecker"
    }
}
