package app.anikuta.ui.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.player.WatchProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar

/**
 * ViewModel for the History screen.
 *
 * Phase 1 revamp changes:
 *  - Reactive: collects [WatchProgressStore.changes] Flow instead of a one-shot
 *    load(). History now updates in real time when the user watches an episode.
 *  - Fixed clearAll O(n) → single [WatchProgressStore.deleteAll] call.
 *  - Fixed "This Week" off-by-one: dayDiff 2..7 → This Week (was 2..6).
 *  - Added coverUrl / animeTitle / episodeNumber / thumbnailUrl to HistoryEntry
 *    (for Phase 2's History UI with real covers + episode thumbnails).
 *
 *  Related files (edit one → check the others):
 *    - WatchProgressStore.kt — the data source (changes Flow)
 *    - HistoryScreen.kt — the UI (reads HistoryState)
 *    - PlayerActivity.kt saveProgress() — writes to WatchProgressStore
 */
class HistoryViewModel : ViewModel() {

    companion object {
        private const val TAG = "HistoryViewModel"

        /** Entries below this fraction are shown in "Continue Watching". */
        private const val CONTINUE_WATCHING_THRESHOLD = 0.9f
    }

    private val store: WatchProgressStore? = try {
        Injekt.get<WatchProgressStore>().also { Log.d(TAG, "✅ WatchProgressStore obtained") }
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to get WatchProgressStore from DI", e)
        null
    }

    private val _state = MutableStateFlow<HistoryState>(HistoryState.Loading)
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    init {
        observeChanges()
    }

    /**
     * Collect the reactive [WatchProgressStore.changes] Flow.
     * History updates in real time — no manual refresh needed.
     */
    private fun observeChanges() {
        val s = store
        if (s == null) {
            _state.value = HistoryState.Error("App not properly initialized")
            return
        }
        viewModelScope.launch {
            s.changes.collectLatest { all ->
                val result = withContext(Dispatchers.Default) {
                    try {
                        if (all.isEmpty()) {
                            return@withContext HistoryState.Empty
                        }
                        val now = System.currentTimeMillis()

                        // Parse keys: "$anilistId:$episodeUrl". Episode URLs contain
                        // colons (https://...), so only split on the FIRST colon.
                        val entries = all.mapNotNull { (key, progress) ->
                            val anilistId = key.substringBefore(':').toIntOrNull()
                                ?: return@mapNotNull null
                            val episodeUrl = key.substringAfter(':')
                            val fraction = if (progress.durationSeconds > 0) {
                                (progress.positionSeconds.toFloat() / progress.durationSeconds.toFloat())
                                    .coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            HistoryEntry(
                                anilistId = anilistId,
                                episodeUrl = episodeUrl,
                                title = progress.title,
                                positionSeconds = progress.positionSeconds,
                                durationSeconds = progress.durationSeconds,
                                updatedAt = progress.updatedAt,
                                progressFraction = fraction,
                                coverUrl = progress.coverUrl,
                                animeTitle = progress.animeTitle,
                                episodeNumber = progress.episodeNumber,
                                thumbnailUrl = progress.thumbnailUrl,
                            )
                        }.sortedByDescending { it.updatedAt }

                        val continueWatching = entries
                            .filter { it.progressFraction < CONTINUE_WATCHING_THRESHOLD }

                        val groups = groupByTime(entries, now)

                        HistoryState.Success(
                            continueWatching = continueWatching,
                            groups = groups,
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load history", e)
                        HistoryState.Error(e.message ?: "Unknown error")
                    }
                }
                _state.value = result
            }
        }
    }

    /** Re-read progress from the store. Safe to call repeatedly. */
    fun refresh() {
        // The Flow auto-updates, but this is kept for explicit refresh requests
        // (e.g. pull-to-refresh). It's a no-op since the Flow is always live.
    }

    /**
     * Remove every saved progress entry, then the Flow auto-reloads.
     * Single pref write via [WatchProgressStore.deleteAll] (was O(anime)).
     */
    fun clearAll() {
        val s = store ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    s.deleteAll()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear all history", e)
                }
            }
        }
    }

    /** Remove a single entry. The Flow auto-reloads. */
    fun clearEntry(anilistId: Int, episodeUrl: String) {
        val s = store ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    s.clear(anilistId, episodeUrl)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear entry", e)
                }
            }
        }
    }

    /**
     * Bucket entries into time periods based on calendar day (not 24-hour
     * deltas) so "Today" always means the current calendar day.
     *
     * Fix: "This Week" now covers dayDiff 2..7 (was 2..6). dayDiff==7 is
     * still within a rolling 7-day window.
     */
    private fun groupByTime(entries: List<HistoryEntry>, now: Long): List<HistoryGroup> {
        val startOfToday = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayMs = 24L * 60 * 60 * 1000

        val buckets = linkedMapOf(
            "Today" to mutableListOf<HistoryEntry>(),
            "Yesterday" to mutableListOf<HistoryEntry>(),
            "This Week" to mutableListOf<HistoryEntry>(),
            "Earlier" to mutableListOf<HistoryEntry>(),
        )
        for (entry in entries) {
            val startOfThatDay = Calendar.getInstance().apply {
                timeInMillis = entry.updatedAt
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val dayDiff = (startOfToday - startOfThatDay) / dayMs
            val bucket = when {
                dayDiff <= 0L -> "Today"
                dayDiff == 1L -> "Yesterday"
                dayDiff <= 7L -> "This Week"  // fix: was < 7L
                else -> "Earlier"
            }
            buckets[bucket]!!.add(entry)
        }
        return buckets
            .filter { it.value.isNotEmpty() }
            .map { HistoryGroup(it.key, it.value) }
    }
}

/** UI state for the History screen. */
sealed class HistoryState {
    data object Loading : HistoryState()
    data class Success(
        val continueWatching: List<HistoryEntry>,
        val groups: List<HistoryGroup>,
    ) : HistoryState()
    data object Empty : HistoryState()
    data class Error(val message: String) : HistoryState()
}

/** A single watch-progress entry, ready for display. */
data class HistoryEntry(
    val anilistId: Int,
    val episodeUrl: String,
    val title: String,
    val positionSeconds: Int,
    val durationSeconds: Int,
    val updatedAt: Long,
    val progressFraction: Float,
    /** Anime cover URL — for the History page cover image. Null = use placeholder. */
    val coverUrl: String? = null,
    /** Anime title — for the History page. Null = unknown. */
    val animeTitle: String? = null,
    /** Episode number — for the History page. -1 = unknown. */
    val episodeNumber: Float = -1f,
    /** Episode thumbnail URL — for the History page episode thumbnail. Null = use cover fallback. */
    val thumbnailUrl: String? = null,
)

/** A labeled, in-order group of entries for the chronological list. */
data class HistoryGroup(
    val label: String,
    val entries: List<HistoryEntry>,
)
