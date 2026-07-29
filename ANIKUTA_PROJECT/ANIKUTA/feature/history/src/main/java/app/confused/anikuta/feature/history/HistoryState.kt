package app.confused.anikuta.feature.history

import androidx.compose.runtime.Immutable
import app.confused.anikuta.core.common.model.Anime
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
 * One watch-progress entry, enriched with the parsed content_id + episode
 * number + the resolved [Anime] (if still in the library).
 *
 * # Phase 3 (ADR-050) — content_id keys
 *
 * The `WatchProgressStore` key is now `"$contentId|$episodeNumber"` (was
 * `"$anilistId:$episodeUrl"`). We parse it via [WatchProgressStore.parseKey]
 * + resolve the content_id back to an [Anime] via `AnimeRepository.getByContentId`.
 *
 * # Bug fix: unlinked anime history rows are now openable
 *
 * The old code parsed `anilistId` from the key + called `onOpenAnime(anilistId)`.
 * For unlinked extension anime (no AniList link), the key was `"0:<url>"` →
 * `onOpenAnime(0)` → AniList fetchById(0) → error state. The row rendered but
 * was unopenable (Doc 01 §5.1).
 *
 * With content_id keys, unlinked anime have a valid content_id (= their
 * local_id), so [anime] resolves to a real [Anime] (with `anilistId == null`).
 * The History row's onClick calls `AppController.openLibraryAnime(anime)`,
 * which handles BOTH linked (pushDetail) and unlinked (pushExtensionDetail)
 * anime. The bug is fixed.
 *
 * # Orphaned entries
 *
 * If the anime was deleted from the library (but the watch-progress entry
 * remains), [anime] is null. The row still renders (so the user can see +
 * clear it) but the onClick is a no-op.
 */
@Immutable
data class HistoryEntry(
    /** The resolved [Anime] for this content_id, or null if not in the library. */
    val anime: Anime?,
    /** The content_id parsed from the `WatchProgressStore` key (e.g., `"al:154587"`). */
    val contentId: String,
    /** The episode number parsed from the `WatchProgressStore` key. */
    val episodeNumber: Float,
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
