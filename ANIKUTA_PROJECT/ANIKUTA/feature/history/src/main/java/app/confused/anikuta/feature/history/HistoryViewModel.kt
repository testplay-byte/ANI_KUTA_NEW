package app.confused.anikuta.feature.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.confused.anikuta.core.common.model.ContentId
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.common.util.relativeDayBucket
import app.confused.anikuta.core.player.WatchProgressStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the History screen.
 *
 * Data source: [WatchProgressStore] (the active, reactive watch-progress store,
 * already Koin-registered in `playerModule`). We do NOT use `HistoryRepository`
 * (the SQLDelight-backed `animehistory` table) — per the project's current
 * architecture, `WatchProgressStore` is the source of truth for watch progress.
 * The store's `changes` Flow emits on every save/clear, so the History screen
 * updates in real time as the user watches episodes.
 *
 * # Phase 3 (ADR-050) — content_id keys
 *
 * The store now keys progress by `"$contentId|$episodeNumber"` (was
 * `"$anilistId:$episodeUrl"`). We parse keys via [WatchProgressStore.parseKey]
 * and resolve the content_id back to an [app.confused.anikuta.core.common.model.Anime]
 * via [AnimeRepository.getByContentId] (taking the first source binding — any
 * one works for opening the detail page).
 *
 * # Bug fix: unlinked anime history rows are now openable
 *
 * The old code parsed `anilistId` from the key + called `onOpenAnime(anilistId)`.
 * For unlinked extension anime (no AniList link), the key was `"0:<url>"` →
 * `onOpenAnime(0)` → AniList fetchById(0) → error state (Doc 01 §5.1).
 *
 * With content_id keys, unlinked anime have a valid content_id (= their
 * local_id), so the [Anime] resolves successfully (with `anilistId == null`).
 * The UI's onClick calls `AppController.openLibraryAnime(anime)`, which handles
 * BOTH linked + unlinked anime — the bug is fixed.
 *
 * # Backup / Restore
 *
 * See the contract documented on [WatchProgressStore] + [WatchProgressBackupProvider].
 * The Phase 3 backup provider serializes the content_id + episodeNumber (not
 * the old anilistId + episodeUrl), so backup/restore round-trips correctly
 * under the new key format.
 */
class HistoryViewModel(
    private val watchProgressStore: WatchProgressStore,
    private val animeRepository: AnimeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            watchProgressStore.changes
                .catch { e ->
                    Log.e(TAG, "Failed to collect watch progress", e)
                    _state.update { it.copy(isLoading = false, isEmpty = true) }
                }
                .collect { progressMap ->
                    val entries = buildList {
                        for ((key, progress) in progressMap) {
                            val (contentId, episodeNumber) = watchProgressStore.parseKey(key)
                                ?: continue
                            // Resolve the content_id back to an Anime (any source binding
                            // works for opening the detail page — `openLibraryAnime`
                            // re-resolves the source for unlinked anime).
                            val anime = animeRepository
                                .getByContentId(ContentId.unsafe(contentId))
                                .firstOrNull()
                            add(HistoryEntry(anime, contentId, episodeNumber, progress))
                        }
                    }.sortedByDescending { it.progress.updatedAt }

                    val grouped = entries.groupBy { entry ->
                        HistorySection.entries[relativeDayBucket(entry.progress.updatedAt)]
                    }

                    _state.update {
                        it.copy(
                            isLoading = false,
                            groupedHistory = grouped,
                            isEmpty = entries.isEmpty(),
                        )
                    }
                }
        }
    }

    /** Show the "clear all history" confirmation dialog. */
    fun showClearConfirm() {
        _state.update { it.copy(showClearConfirm = true) }
    }

    /** Dismiss the "clear all history" confirmation dialog. */
    fun dismissClearConfirm() {
        _state.update { it.copy(showClearConfirm = false) }
    }

    /**
     * Clears ALL watch progress. Called after the user confirms the
     * "Delete all watch history?" dialog. Delegates to
     * [WatchProgressStore.deleteAll] (O(1) single pref write).
     */
    fun clearAllHistory() {
        _state.update { it.copy(showClearConfirm = false) }
        viewModelScope.launch {
            try {
                watchProgressStore.deleteAll()
                Log.i(TAG, "Cleared all watch history")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear watch history", e)
            }
        }
    }

    companion object {
        private const val TAG = "HistoryViewModel"
    }
}
