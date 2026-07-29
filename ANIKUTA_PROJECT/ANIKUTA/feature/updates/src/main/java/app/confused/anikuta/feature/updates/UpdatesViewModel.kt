package app.confused.anikuta.feature.updates

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.providerapi.AiringScheduleProvider
import app.confused.anikuta.core.providerapi.MetadataCapability
import app.confused.anikuta.core.providerapi.MetadataProviderRegistry
import app.confused.anikuta.core.updatechecker.UpdateCheckProgress
import app.confused.anikuta.core.updatechecker.UpdateChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Updates screen (both the Updates tab and the Schedule tab).
 *
 * ── Updates tab ──
 *  - Collects [UpdateChecker.getLastResults] reactively (so the list updates
 *    the moment a manual check completes).
 *  - `checkForUpdates()` triggers a fresh check (pull-to-refresh). The
 *    `isChecking` flag drives the pull-to-refresh indicator.
 *  - `lastCheckedAt` comes from [UpdateChecker.getLastCheckTimestamp].
 *
 * ── Schedule tab ──
 *  - `fetchSchedule()` reads the library favorites (`AnimeRepository.observeFavorites`),
 *    collects their AniList IDs, and asks the active [AiringScheduleProvider]
 *    (resolved via [MetadataProviderRegistry]) for each chunk's upcoming
 *    episodes. The result is flattened into a sorted [ScheduleEntry] list
 *    (one entry per upcoming episode, across all library anime).
 *  - The provider returns a flat `List<AiringScheduleInfo>` keyed by anime ID
 *    + episode number (the rich per-anime AniList payload with title/cover is
 *    collapsed in the AniList adapter — see `AniListMetadataProvider`).
 *    Because the provider contract is provider-agnostic, title / cover URL /
 *    cover color come from the local library entry (we already have them —
 *    the user favorited the anime, so its `Anime` row in the DB has them).
 *  - A manual refresh re-runs `fetchSchedule()`. We chunk the ID list into
 *    batches of 50 (AniList's `id_in` practical max) and concatenate the
 *    results.
 *
 * The ViewModel is UI-agnostic — all state is in [UpdatesState], mutations go
 * through small `fun`s. Koin-registered via `updatesModule`.
 *
 * Phase 7 (ADR-041): the schedule fetch now routes through the registry
 * instead of calling `anilistApi.fetchAiringSchedule` directly. `anilistApi`
 * is retained as a constructor dep for now (still used as a fallback if no
 * provider is available — see `fetchSchedule` — and as a future-proofing hook
 * for schedule-by-id lookups that aren't in the provider contract yet).
 */
class UpdatesViewModel(
    private val updateChecker: UpdateChecker,
    private val anilistApi: AniListApi,
    private val animeRepository: AnimeRepository,
    private val registry: MetadataProviderRegistry,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdatesState())
    val state = _state.asStateFlow()

    init {
        // Collect cached update results reactively (merged list — old + new).
        viewModelScope.launch {
            updateChecker.getLastResults().collect { results ->
                _state.update { it.copy(updates = results, isLoading = false) }
            }
        }
        // Collect the last-check timestamp reactively.
        viewModelScope.launch {
            updateChecker.getLastCheckTimestamp().collect { ts ->
                _state.update { it.copy(lastCheckedAt = ts) }
            }
        }
        // Collect the live check-progress stream + map to the UI type. When a
        // check completes, briefly show Completed then fall back to Idle so the
        // "Currently checking" card animates out.
        viewModelScope.launch {
            updateChecker.getCheckProgress().collect { progress ->
                val ui = when (progress) {
                    is UpdateCheckProgress.Idle -> CheckProgressUi.Idle
                    is UpdateCheckProgress.Checking -> CheckProgressUi.Checking(
                        currentAnime = progress.currentAnime,
                        currentIndex = progress.currentIndex,
                        totalCount = progress.totalCount,
                        foundSoFar = progress.foundSoFar,
                    )
                    is UpdateCheckProgress.Completed -> CheckProgressUi.Completed(
                        foundCount = progress.foundCount,
                        totalChecked = progress.totalChecked,
                    )
                }
                _state.update { it.copy(checkProgress = ui) }
                // If completed, hold the Completed state briefly so the user
                // sees "Found N new", then reset to Idle.
                if (progress is UpdateCheckProgress.Completed) {
                    delay(1500)
                    _state.update { it.copy(checkProgress = CheckProgressUi.Idle) }
                }
            }
        }
        // Fetch the schedule once on first open.
        fetchSchedule()
    }

    /** Switches the active top-level tab. */
    fun setTab(tab: UpdatesTab) {
        _state.update { it.copy(activeTab = tab) }
    }

    /** Switches the Schedule tab's view mode (List / Calendar). */
    fun setScheduleViewMode(mode: ScheduleViewMode) {
        _state.update { it.copy(scheduleViewMode = mode) }
    }

    /**
     * Triggers a manual update check (pull-to-refresh on the Updates tab).
     * Sets `isChecking` while in flight so the pull-to-refresh indicator shows.
     */
    fun checkForUpdates() {
        if (_state.value.isChecking) return
        _state.update { it.copy(isChecking = true) }
        viewModelScope.launch {
            try {
                updateChecker.checkForUpdates()
            } catch (e: Exception) {
                Log.e(TAG, "Manual update check failed", e)
            } finally {
                _state.update { it.copy(isChecking = false) }
            }
        }
    }

    /**
     * Fetches upcoming airing schedules for all library anime.
     *
     * Reads the library once (`observeFavorites().first()`), collects AniList
     * IDs, chunks them into batches of 50, and asks the active
     * [AiringScheduleProvider] for each chunk's schedule. The provider returns
     * a flat `List<AiringScheduleInfo>` (one entry per upcoming episode,
     * provider-agnostic — see `AniListMetadataProvider.fetchSchedule` for the
     * AniList-side flattening). We then look up the per-anime display fields
     * (title / coverUrl / coverColor) from the local library entry — those are
     * NOT in the provider contract because future providers (MAL, TMDB) may
     * not have them at all, and we already have them locally (the user
     * favorited the anime).
     *
     * If the registry has no available [AiringScheduleProvider], we fall back
     * to calling `anilistApi.fetchAiringSchedule` directly — this preserves
     * the pre-Phase-7 behavior so the schedule tab still works if the
     * registry is somehow misconfigured. (Today AniList is always registered,
     * so this fallback path is essentially dead code — kept as a safety net
     * for the Phase 7 rollout.)
     *
     * The flattened [ScheduleEntry] list is sorted by airing time ascending.
     */
    fun fetchSchedule() {
        viewModelScope.launch {
            _state.update { it.copy(scheduleError = null) }
            try {
                val library = animeRepository.observeFavorites().first()
                val ids = library.mapNotNull { it.anilistId }
                Log.i(
                    TAG,
                    "fetchSchedule: library=${library.size} anime, ${ids.size} with anilistId" +
                        if (ids.size < library.size) " (${library.size - ids.size} skipped — null anilistId)" else "",
                )
                if (ids.isEmpty()) {
                    _state.update { it.copy(schedule = emptyList(), isLoading = false) }
                    return@launch
                }

                // Build a lookup map: anilistId -> Anime. Used to attach the
                // local title/cover/color to each provider-schedule entry
                // (the provider contract returns only {animeId, episode, airingAt}).
                val libraryByAnilistId: Map<Int, Anime> = library
                    .mapNotNull { anime -> anime.anilistId?.let { it to anime } }
                    .toMap()

                // Resolve the active provider ONCE (the registry's fallback
                // chain may ping isAvailable() on each candidate — don't repeat
                // per chunk). If null, fall back to the legacy AniList path.
                val provider: AiringScheduleProvider? =
                    registry.forCapability<AiringScheduleProvider>(MetadataCapability.AIRING_SCHEDULE)
                if (provider != null) {
                    Log.d(TAG, "fetchSchedule: using AiringScheduleProvider=${provider.displayName}")
                } else {
                    Log.w(TAG, "fetchSchedule: no AiringScheduleProvider available — " +
                        "falling back to anilistApi.fetchAiringSchedule (legacy path)")
                }

                val entries = mutableListOf<ScheduleEntry>()
                // Dedup by (anilistId, episode) — the AniList adapter emits
                // nextAiringEpisode + all upcomingEpisodes (which overlap on
                // the immediate next). The first occurrence wins (next's airingAt).
                val seen = HashSet<Pair<Int, Int>>()

                // Chunk to respect AniList's practical per-request limit.
                for (chunk in ids.chunked(50)) {
                    val info: List<app.confused.anikuta.core.providerapi.AiringScheduleInfo> = try {
                        if (provider != null) {
                            provider.fetchSchedule(chunk)
                        } else {
                            // Legacy fallback: call anilistApi directly + map
                            // the rich AniList-side payload to the provider's
                            // flat contract so the rendering loop below is
                            // shared. (This path is dead-code in practice —
                            // kept as a safety net for the Phase 7 rollout.)
                            anilistApi.fetchAiringSchedule(chunk).flatMap { animeInfo ->
                                buildList {
                                    animeInfo.nextAiringEpisode?.let { na ->
                                        if (na.episode != null && na.airingAt != null) {
                                            add(app.confused.anikuta.core.providerapi.AiringScheduleInfo(
                                                animeId = animeInfo.anilistId,
                                                episode = na.episode!!,
                                                airingAt = na.airingAt!!.toLong(),
                                                timeUntilAiring = na.timeUntilAiring?.toLong() ?: 0L,
                                            ))
                                        }
                                    }
                                    for (sch in animeInfo.upcomingEpisodes) {
                                        if (sch.episode != null && sch.airingAt != null) {
                                            add(app.confused.anikuta.core.providerapi.AiringScheduleInfo(
                                                animeId = animeInfo.anilistId,
                                                episode = sch.episode!!,
                                                airingAt = sch.airingAt!!.toLong(),
                                                timeUntilAiring = sch.timeUntilAiring?.toLong() ?: 0L,
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "fetchSchedule failed for chunk $chunk (non-fatal)", e)
                        emptyList()
                    }

                    for (entry in info) {
                        // Look up the local library entry for the display fields.
                        // If the anime isn't in our library (shouldn't happen —
                        // we filtered ids from the library above), skip.
                        val anime = libraryByAnilistId[entry.animeId] ?: continue

                        // Skip degenerate entries (the AniList adapter emits
                        // episode=0 / airingAt=0 when AniList's nullable fields
                        // are null — preserves the original VM's null-checks).
                        val ep = entry.episode
                        val airingAt = entry.airingAt
                        if (ep <= 0 || airingAt <= 0L) continue

                        // Dedup next + upcoming overlap.
                        val key = entry.animeId to ep
                        if (!seen.add(key)) continue

                        entries.add(
                            ScheduleEntry(
                                anilistId = entry.animeId,
                                animeTitle = anime.title,
                                coverUrl = anime.coverUrl,
                                coverColor = anime.coverColor,
                                episodeNumber = ep,
                                airingAtMillis = airingAt * 1000L,
                            ),
                        )
                    }
                }

                val sorted = entries.sortedBy { it.airingAtMillis }
                Log.i(TAG, "fetchSchedule: built ${sorted.size} schedule entries from ${ids.size} library ids")
                _state.update {
                    it.copy(schedule = sorted, isLoading = false, scheduleError = null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchSchedule failed", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        scheduleError = "Couldn't load schedule: ${e.message ?: "network error"}",
                    )
                }
            }
        }
    }

    /** Opens/closes the calendar day-detail sheet for [dayKey] ("yyyy-MM-dd"). */
    fun selectCalendarDay(dayKey: String?) {
        _state.update { it.copy(selectedCalendarDay = dayKey) }
    }

    /**
     * Bumps [UpdatesState.calendarJumpSignal] so the [ScheduleCalendar] resets
     * its displayed month to the current month. Called by the "Jump to today"
     * button in the Schedule tab's calendar view.
     */
    fun jumpToToday() {
        _state.update { it.copy(calendarJumpSignal = it.calendarJumpSignal + 1) }
    }

    /**
     * Marks the update result for [animeId] as acknowledged (no longer "new").
     *
     * Called when the user taps an update row to open the anime detail page —
     * so the "new" highlight + vertical bar clear for that entry once the user
     * has looked at it. The state change flows back to the UI via
     * [UpdateChecker.getLastResults] (which the VM collects).
     */
    fun acknowledgeUpdate(animeId: Long) {
        updateChecker.acknowledgeResult(animeId)
    }

    companion object {
        private const val TAG = "UpdatesViewModel"
    }
}
