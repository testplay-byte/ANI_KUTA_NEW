package app.confused.anikuta.core.player

import app.confused.anikuta.core.preferences.Preference
import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * Lightweight watch-progress store.
 *
 * Saves the last playback position per episode so the user can resume where
 * they left off.
 *
 * # Key format (Phase 3, ADR-050)
 *
 * Keyed by **content_id + episode number**: `"$contentId|$episodeNumber"`.
 *
 * - `contentId` is the Tier 2 per-content identity (e.g., `"al:154587"` for an
 *   AniList-linked anime, or `"aniyomi:123:url"` for an unlinked extension anime).
 *   It survives source switches — the same anime from a different extension has
 *   the same content_id.
 * - `episodeNumber` is the source-independent episode number (e.g., `1.0`, `2.0`).
 *   It survives source switches — episode 1 is episode 1 regardless of source.
 * - The `|` separator is used because content_id contains `:` (e.g., `"al:154587"`),
 *   so `:` would be ambiguous. `|` is unambiguous.
 *
 * **Old format** (pre-Phase-3): `"$anilistId:$episodeUrl"`. The [WatchProgressMigrator]
 * re-keys existing entries on first launch. Entries with `anilistId == 0` (the
 * polluted unlinked-anime entries — Doc 01 §6.3) are dropped (they were already
 * unopenable).
 *
 * # Why content_id + episodeNumber (not anilistId + episodeUrl)?
 *
 * - **Source switching:** when the user switches extension source, episodeUrl
 *   changes but content_id + episodeNumber stay the same → progress survives.
 * - **Unlinked anime:** content_id works for unlinked extension anime (falls back
 *   to local_id), while anilistId=0 was a degenerate key that made history rows
 *   unopenable (Doc 01 §5.1).
 *
 * Related files (edit one → check the others):
 *   - WatchScreen.kt saveProgress() — writes here
 *   - HistoryViewModel.kt — reads via [changes], parses keys via [parseKey]
 *   - TrackSyncManager.kt — reads via [changes], parses keys via [parseKey]
 *   - StatsCalculator.kt — reads via [changes], parses keys via [parseKey]
 *   - WatchProgressBackupProvider.kt — reads [getAll], writes [save]
 *   - WatchProgressMigrator.kt — re-keys on first launch
 */
class WatchProgressStore(
    private val store: PreferenceStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val progressPref: Preference<Map<String, Progress>> = store.getObject(
        "pref_watch_progress_map",
        emptyMap(),
        { map -> json.encodeToString(map) },
        { str ->
            try { json.decodeFromString<Map<String, Progress>>(str) }
            catch (e: Exception) { emptyMap() }
        },
    )

    /**
     * Reactive stream of all progress entries. Emits on every save/clear.
     * Used by HistoryViewModel + TrackSyncManager + StatsCalculator.
     */
    val changes: Flow<Map<String, Progress>> = progressPref.changes().map { it }

    /**
     * Build the key for a content_id + episode number pair.
     *
     * Format: `"$contentId|$episodeNumberKey"` where `episodeNumberKey` is the
     * episode number formatted to 3 decimal places (zero-padded, stable).
     * The `|` separator is unambiguous because content_id contains `:`.
     */
    fun key(contentId: String, episodeNumber: Float): String =
        "$contentId|${episodeNumberKey(episodeNumber)}"

    /** Format an episode number as a stable key component (3 decimal places). */
    private fun episodeNumberKey(n: Float): String = "%.3f".format(n)

    /**
     * Parse a key back into (content_id, episode_number).
     *
     * Returns null if the key is malformed or doesn't match the `"$contentId|$epNum"` format.
     * The content_id is everything before the LAST `|`; the episode number is after it.
     */
    fun parseKey(key: String): Pair<String, Float>? {
        val idx = key.lastIndexOf('|')
        if (idx < 0) return null
        val contentId = key.substring(0, idx)
        val epNumStr = key.substring(idx + 1)
        val epNum = epNumStr.toFloatOrNull() ?: return null
        if (contentId.isBlank()) return null
        return contentId to epNum
    }

    /**
     * Save the current playback position for an episode.
     *
     * @param contentId the Tier 2 per-content identity (e.g., `"al:154587"`).
     * @param episodeNumber the episode number (source-independent).
     * @param positionSeconds current playback position.
     * @param durationSeconds total episode duration.
     * @param title episode display title.
     * @param coverUrl anime cover URL (for History page).
     * @param animeTitle anime title (for History page).
     * @param thumbnailUrl episode thumbnail URL (for History page).
     */
    fun save(
        contentId: String,
        episodeNumber: Float,
        positionSeconds: Int,
        durationSeconds: Int,
        title: String,
        coverUrl: String? = null,
        animeTitle: String? = null,
        thumbnailUrl: String? = null,
    ) {
        val map = progressPref.get().toMutableMap()
        map[key(contentId, episodeNumber)] = Progress(
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
            title = title,
            updatedAt = System.currentTimeMillis(),
            coverUrl = coverUrl,
            animeTitle = animeTitle,
            episodeNumber = episodeNumber,
            thumbnailUrl = thumbnailUrl,
            contentId = contentId,
        )
        progressPref.set(map)
    }

    /** Get the saved position for an episode, or null if none. */
    fun get(contentId: String, episodeNumber: Float): Progress? {
        return progressPref.get()[key(contentId, episodeNumber)]
    }

    /** Clear progress for a single episode. */
    fun clear(contentId: String, episodeNumber: Float) {
        val map = progressPref.get().toMutableMap()
        map.remove(key(contentId, episodeNumber))
        progressPref.set(map)
    }

    /**
     * Clear all progress for a content (all its episodes).
     * Matches by key prefix `"$contentId|"` so it catches all episodes.
     */
    fun clearContent(contentId: String) {
        val map = progressPref.get().toMutableMap()
        val prefix = "$contentId|"
        map.keys.filter { it.startsWith(prefix) }.forEach { map.remove(it) }
        progressPref.set(map)
    }

    /**
     * Delete ALL progress entries in a single pref write.
     * O(1) — replaces the old O(anime) loop.
     */
    fun deleteAll() {
        progressPref.set(emptyMap())
    }

    /** Get all saved progress entries (for the History page, Library sort, backup). */
    fun getAll(): Map<String, Progress> = progressPref.get()

    /**
     * Re-key an entry from [oldKey] to [newKey] (used by [WatchProgressMigrator] +
     * the future ContentIdMigrator for link/unlink events).
     *
     * If [oldKey] doesn't exist, this is a no-op. If [newKey] already exists,
     * the old entry is still removed (the new entry wins).
     */
    fun rekey(oldKey: String, newKey: String) {
        val map = progressPref.get().toMutableMap()
        val progress = map.remove(oldKey) ?: return
        map[newKey] = progress
        progressPref.set(map)
    }

    /**
     * Replace the entire progress map (used by the migrator for bulk re-keying +
     * by backup restore). More efficient than calling [rekey] per entry.
     */
    fun replaceAll(newMap: Map<String, Progress>) {
        progressPref.set(newMap)
    }

    @Serializable
    data class Progress(
        val positionSeconds: Int,
        val durationSeconds: Int,
        val title: String,
        val updatedAt: Long,
        /** Anime cover URL — for the History page cover image. Nullable for backward compat. */
        val coverUrl: String? = null,
        /** Anime title — for the History page. Nullable for backward compat. */
        val animeTitle: String? = null,
        /** Episode number — for the History page. -1 = unknown. */
        val episodeNumber: Float = -1f,
        /** Episode thumbnail URL — for the History page episode thumbnail. Nullable. */
        val thumbnailUrl: String? = null,
        /** The content_id this progress belongs to (Phase 3, ADR-050). Nullable for pre-Phase-3 entries. */
        val contentId: String? = null,
    )
}
