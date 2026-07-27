package app.confused.anikuta.feature.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * architecture, `WatchProgressStore` is the source of truth for AniList-keyed
 * progress until source URLs are fully resolved. The store's `changes` Flow
 * emits on every save/clear, so the History screen updates in real time as the
 * user watches episodes.
 *
 * The store keys progress by `"anilistId:episodeUrl"`. We parse that key here
 * to surface the AniList ID for row-tap navigation.
 *
 * ── Backup / Restore (documentation) ──────────────────────────────────────────
 *
 * Watch-progress history MUST be included in the future backup system. The
 * data is fully serializable (it already lives as a JSON map inside
 * `WatchProgressStore` via `PreferenceStore.getObject(...)`). The backup
 * system (when built — see ADR-028, gzipped protobuf) should:
 *
 *  **Export:**
 *   - Read `WatchProgressStore.getAll()` → `Map<String, Progress>`.
 *   - Serialize the map to JSON (use the same `Json { ignoreUnknownKeys = true }`
 *     config as the store) and write it into the backup payload under a
 *     stable key like `"watch_progress"`.
 *   - Each entry's key (`"anilistId:episodeUrl"`) MUST be preserved verbatim —
 *     it's the identity used for resume + dedup.
 *
 *  **Import:**
 *   - Read the `"watch_progress"` JSON from the backup payload.
 *   - Deserialize to `Map<String, Progress>`.
 *   - Write it back via the store's underlying `PreferenceStore.getObject`
 *     setter (the store doesn't expose a bulk `setAll` today; either add one
 *     or clear-then-iterate-`save`. A bulk `setAll` is the cleaner option —
 *     see the `// TODO backup` note in `WatchProgressStore`).
 *   - Merge semantics: prefer the backup entry with the newer `updatedAt`
 *     timestamp when a key exists in both the device and the backup.
 *
 *  **Data class contract:** `WatchProgressStore.Progress` is `@Serializable`
 *  with nullable defaults for the newer fields (`coverUrl`, `animeTitle`,
 *  `episodeNumber`, `thumbnailUrl`), so older backups deserialize cleanly.
 *  Do NOT remove nullable defaults from `Progress` without a backup-version
 *  bump.
 *
 * This ViewModel does NOT perform backup itself — it only documents the
 * contract. The future backup module will import `WatchProgressStore` and
 * follow the export/import steps above.
 */
class HistoryViewModel(
    private val watchProgressStore: WatchProgressStore,
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
                    val entries = progressMap.map { (key, progress) ->
                        val (anilistId, episodeUrl) = parseKey(key)
                        HistoryEntry(anilistId, episodeUrl, progress)
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

    /**
     * Parses a `WatchProgressStore` key into `(anilistId, episodeUrl)`.
     *
     * Keys are `"anilistId:episodeUrl"`. The episode URL may itself contain
     * colons (e.g. `https://...`), so we split on the FIRST colon only.
     * Malformed keys (no colon / non-integer anilistId) yield anilistId 0 —
     * the row still renders but its tap won't navigate.
     */
    private fun parseKey(key: String): Pair<Int, String> {
        val idx = key.indexOf(':')
        if (idx < 0) return 0 to key
        val idPart = key.substring(0, idx)
        val urlPart = key.substring(idx + 1)
        val anilistId = idPart.toIntOrNull() ?: 0
        return anilistId to urlPart
    }

    companion object {
        private const val TAG = "HistoryViewModel"
    }
}
