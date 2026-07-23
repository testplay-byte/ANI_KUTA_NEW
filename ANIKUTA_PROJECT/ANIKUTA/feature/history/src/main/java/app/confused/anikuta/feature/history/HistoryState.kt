package app.confused.anikuta.feature.history

import androidx.compose.runtime.Immutable
import app.confused.anikuta.core.player.WatchProgressStore

/**
 * The four day-buckets the History screen groups entries into.
 *
 * Calendar-day based (not 24-hour deltas) per the History design spec —
 * "Today" always means the current calendar day regardless of when the user
 * opens the screen. See `core.common.util.relativeDayBucket`.
 */
enum class HistorySection(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    EARLIER("Earlier"),
}

/**
 * One watch-progress entry, enriched with the parsed AniList ID + episode URL
 * (which are encoded in the `WatchProgressStore` map key, not in [Progress]).
 *
 * The AniList ID lets the row navigate to the anime detail page on tap.
 */
@Immutable
data class HistoryEntry(
    val anilistId: Int,
    val episodeUrl: String,
    val progress: WatchProgressStore.Progress,
) {
    /** 0..1 watch-progress fraction. Guarded against zero-duration. */
    val progressFraction: Float
        get() = if (progress.durationSeconds > 0) {
            (progress.positionSeconds.toFloat() / progress.durationSeconds).coerceIn(0f, 1f)
        } else 0f

    /** Display title: prefer the stored anime title, fall back to the episode title. */
    val displayTitle: String get() = progress.animeTitle ?: progress.title.ifBlank { "Unknown" }

    /** Episode label: "Episode N" when the number is known, else the stored title. */
    val episodeLabel: String
        get() = if (progress.episodeNumber >= 0f) {
            val n = progress.episodeNumber
            if (n == n.toInt().toFloat()) "Episode ${n.toInt()}" else "Episode $n"
        } else {
            progress.title.ifBlank { "Episode" }
        }
}

/**
 * Immutable UI state for the History screen.
 *
 * @property isLoading True until the first emission from `WatchProgressStore.changes`.
 * @property groupedHistory Entries grouped by day-bucket, in [HistorySection] order.
 *   Empty buckets are omitted from the map.
 * @property isEmpty True if there is no watch history at all (drives the empty state).
 */
@Immutable
data class HistoryState(
    val isLoading: Boolean = true,
    val groupedHistory: Map<HistorySection, List<HistoryEntry>> = emptyMap(),
    val isEmpty: Boolean = false,
    val showClearConfirm: Boolean = false,
) {
    /** Ordered list of non-empty sections (for the LazyColumn to iterate). */
    val visibleSections: List<HistorySection>
        get() = HistorySection.entries.filter { groupedHistory[it]?.isNotEmpty() == true }
}
