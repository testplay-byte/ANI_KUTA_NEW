package app.confused.anikuta.feature.updates

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.model.AiringScheduleInfo
import app.confused.anikuta.core.common.repository.AnimeRepository
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
 *    collects their AniList IDs, and calls `AniListApi.fetchAiringSchedule(ids)`.
 *    The result is flattened into a sorted [ScheduleEntry] list (one entry per
 *    upcoming episode, across all library anime).
 *  - The schedule is cached in `AniListApi` for 5 min, so switching tabs back
 *    and forth doesn't re-fetch. A manual refresh re-runs `fetchSchedule()`.
 *  - We chunk the ID list into batches of 50 (AniList's `id_in` practical max)
 *    and concatenate the results.
 *
 * The ViewModel is UI-agnostic — all state is in [UpdatesState], mutations go
 * through small `fun`s. Koin-registered via `updatesModule`.
 */
class UpdatesViewModel(
    private val updateChecker: UpdateChecker,
    private val anilistApi: AniListApi,
    private val animeRepository: AnimeRepository,
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
     * IDs, chunks them into batches of 50, and concatenates the per-batch
     * `fetchAiringSchedule` results. The flattened [ScheduleEntry] list is
     * sorted by airing time ascending.
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

                val entries = mutableListOf<ScheduleEntry>()
                // Chunk to respect AniList's practical per-request limit.
                for (chunk in ids.chunked(50)) {
                    val info: List<AiringScheduleInfo> = try {
                        anilistApi.fetchAiringSchedule(chunk)
                    } catch (e: Exception) {
                        Log.w(TAG, "fetchAiringSchedule failed for chunk $chunk (non-fatal)", e)
                        emptyList()
                    }
                    for (anime in info) {
                        // nextAiringEpisode (single) — include if present.
                        anime.nextAiringEpisode?.let { na ->
                            val airingAt = na.airingAt
                            val ep = na.episode
                            if (airingAt != null && ep != null) {
                                entries.add(
                                    ScheduleEntry(
                                        anilistId = anime.anilistId,
                                        animeTitle = anime.title,
                                        coverUrl = anime.coverUrl,
                                        coverColor = anime.coverColor,
                                        episodeNumber = ep,
                                        airingAtMillis = airingAt.toLong() * 1000L,
                                    ),
                                )
                            }
                        }
                        // Full upcoming list — include each (dedup against
                        // nextAiringEpisode by episode number to avoid listing
                        // the immediate next episode twice).
                        val nextEpNum = anime.nextAiringEpisode?.episode
                        for (sch in anime.upcomingEpisodes) {
                            if (sch.episode == nextEpNum) continue
                            val airingAt = sch.airingAt
                            val ep = sch.episode
                            if (airingAt != null && ep != null) {
                                entries.add(
                                    ScheduleEntry(
                                        anilistId = anime.anilistId,
                                        animeTitle = anime.title,
                                        coverUrl = anime.coverUrl,
                                        coverColor = anime.coverColor,
                                        episodeNumber = ep,
                                        airingAtMillis = airingAt.toLong() * 1000L,
                                    ),
                                )
                            }
                        }
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

    companion object {
        private const val TAG = "UpdatesViewModel"
    }
}
