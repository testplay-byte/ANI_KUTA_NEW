package app.confused.anikuta.feature.updates

import androidx.compose.runtime.Immutable
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.updatechecker.UpdateResult

/**
 * Which top-level tab is active on the Updates screen.
 *
 * Per the implementation prompt: Updates = "new episodes since last check",
 * Schedule = "upcoming episode air dates for library anime".
 */
enum class UpdatesTab(val label: String) {
    UPDATES("Updates"),
    SCHEDULE("Schedule"),
}

/**
 * Which view mode the Schedule tab uses.
 *
 * Per the implementation prompt: LIST = chronological list grouped by day;
 * CALENDAR = monthly calendar grid with dots on days that have episodes.
 */
enum class ScheduleViewMode { LIST, CALENDAR }

/**
 * One upcoming episode airing, flattened from AniList's `airingSchedule`.
 *
 * @property anilistId The AniList media ID (for opening the detail page).
 * @property animeTitle Display title.
 * @property coverUrl Cover image URL (or null).
 * @property coverColor Hex color from AniList (or null).
 * @property episodeNumber The airing episode number.
 * @property airingAtMillis When the episode airs, in epoch MILLISECONDS.
 *   (AniList returns `airingAt` in seconds; we ×1000 at the VM boundary so
 *   the whole app stays in epoch-millis, matching `System.currentTimeMillis()`
 *   and `core.common.util.formatTimeUntil`.)
 */
@Immutable
data class ScheduleEntry(
    val anilistId: Int,
    val animeTitle: String,
    val coverUrl: String?,
    val coverColor: String?,
    val episodeNumber: Int,
    val airingAtMillis: Long,
)

/**
 * The live progress of an in-flight update check, surfaced to the UI as a
 * "Currently checking" card. A UI-facing mirror of
 * `UpdateCheckProgress` (kept here so the UI module doesn't need to import
 * the core sealed type + Anime model transitively).
 *
 * - [Idle] — no check in flight.
 * - [Checking] — [currentAnime] is being fetched right now; [currentIndex]/
 *   [totalCount] is its position; [foundSoFar] is the running found-count.
 * - [Completed] — the check finished; [foundCount] anime had new episodes.
 */
sealed interface CheckProgressUi {
    data object Idle : CheckProgressUi
    data class Checking(
        val currentAnime: Anime,
        val currentIndex: Int,
        val totalCount: Int,
        val foundSoFar: Int,
    ) : CheckProgressUi
    data class Completed(
        val foundCount: Int,
        val totalChecked: Int,
    ) : CheckProgressUi
}

/**
 * Immutable UI state for the Updates screen.
 *
 * @property isLoading True while the initial load (results snapshot + first
 *   schedule fetch) is in flight.
 * @property isChecking True while a manual `UpdateChecker.checkForUpdates()`
 *   is in flight (drives the pull-to-refresh indicator + the live card).
 * @property updates The merged results list (old + new). Anime with `isNew`
 *   were found in the most recent check; the rest are carried over from
 *   previous checks. Navigating away and back preserves this list.
 * @property checkProgress Live progress of the in-flight check (renders the
 *   "Currently checking" card). [CheckProgressUi.Idle] when not checking.
 * @property schedule The flattened upcoming-episode list, sorted by airing time.
 * @property lastCheckedAt Epoch-ms of the last update check (0 = never).
 * @property activeTab The active top-level tab.
 * @property scheduleViewMode The Schedule tab's view mode.
 * @property scheduleError Non-null if the last schedule fetch failed (shown as
 *   a retryable error banner).
 * @property selectedCalendarDay "yyyy-MM-dd" when a calendar day is tapped
 *   (drives the day-detail bottom sheet), or null.
 */
@Immutable
data class UpdatesState(
    val isLoading: Boolean = true,
    val isChecking: Boolean = false,
    val updates: List<UpdateResult> = emptyList(),
    val checkProgress: CheckProgressUi = CheckProgressUi.Idle,
    val schedule: List<ScheduleEntry> = emptyList(),
    val lastCheckedAt: Long = 0,
    val activeTab: UpdatesTab = UpdatesTab.UPDATES,
    val scheduleViewMode: ScheduleViewMode = ScheduleViewMode.LIST,
    val scheduleError: String? = null,
    val selectedCalendarDay: String? = null,
    /**
     * An incrementing counter the UI bumps when the user taps "Jump to today".
     * The [ScheduleCalendar] observes it and resets its displayed month to the
     * current month. Using a signal counter (rather than a Calendar state hoisted
     * to the VM) keeps the calendar's internal month-navigation state local to
     * the composable while still letting the button trigger a reset.
     */
    val calendarJumpSignal: Int = 0,
) {
    /** True when both tabs have nothing to show. */
    val isEmpty: Boolean get() = updates.isEmpty() && schedule.isEmpty()
}
