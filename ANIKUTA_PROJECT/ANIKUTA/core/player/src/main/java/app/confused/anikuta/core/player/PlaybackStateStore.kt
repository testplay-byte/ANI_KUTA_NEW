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
 * Saves the last playback state per episode (Phase C).
 *
 * When the user resumes from History, we try the exact same video URL +
 * audio track + subtitle track + resolution that was used last time.
 * If that URL is dead, the player falls back to re-resolving via the source.
 *
 * # Key format (Phase 3, ADR-050)
 *
 * Keyed by **content_id + episode number**: `"$contentId|$episodeNumber"`.
 * Same format as [WatchProgressStore] — see its docs for details.
 *
 * Reactive via [changes] Flow.
 *
 * Related files:
 *   - WatchScreen.kt — writes here on pause/stop, reads on resume
 *   - HistoryViewModel.kt — passes the state to the player
 *   - WatchProgressStore.kt — the companion store (position/duration)
 */
class PlaybackStateStore(
    private val store: PreferenceStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class PlaybackState(
        val videoUrl: String,
        val videoServer: String = "",
        val videoAudio: String = "",
        val videoQuality: Int = -1,
        val videoHeaders: String = "",
        val audioTrackId: Int = -1,
        val subtitleTrackId: Int = -1,
        val sourceId: Long = -1L,
        val updatedAt: Long = 0L,
    )

    private val statePref: Preference<Map<String, PlaybackState>> = store.getObject(
        "pref_playback_state_map",
        emptyMap<String, PlaybackState>(),
        { map -> json.encodeToString(map) },
        { str ->
            try { json.decodeFromString<Map<String, PlaybackState>>(str) }
            catch (e: Exception) { emptyMap() }
        },
    )

    /** Reactive stream of all playback states. */
    val changes: Flow<Map<String, PlaybackState>> = statePref.changes().map { it }

    /**
     * Build the key for a content_id + episode number pair.
     * Format: `"$contentId|$episodeNumberKey"` (same as WatchProgressStore).
     */
    fun key(contentId: String, episodeNumber: Float): String =
        "$contentId|${episodeNumberKey(episodeNumber)}"

    /** Format an episode number as a stable key component (3 decimal places). */
    private fun episodeNumberKey(n: Float): String = "%.3f".format(n)

    /**
     * Parse a key back into (content_id, episode_number).
     * Returns null if the key is malformed.
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

    /** Save the playback state for an episode. */
    fun save(
        contentId: String,
        episodeNumber: Float,
        videoUrl: String,
        videoServer: String = "",
        videoAudio: String = "",
        videoQuality: Int = -1,
        videoHeaders: String = "",
        audioTrackId: Int = -1,
        subtitleTrackId: Int = -1,
        sourceId: Long = -1L,
    ) {
        val map = statePref.get().toMutableMap()
        map[key(contentId, episodeNumber)] = PlaybackState(
            videoUrl = videoUrl,
            videoServer = videoServer,
            videoAudio = videoAudio,
            videoQuality = videoQuality,
            videoHeaders = videoHeaders,
            audioTrackId = audioTrackId,
            subtitleTrackId = subtitleTrackId,
            sourceId = sourceId,
            updatedAt = System.currentTimeMillis(),
        )
        statePref.set(map)
    }

    /** Get the saved playback state for an episode, or null if none. */
    fun get(contentId: String, episodeNumber: Float): PlaybackState? {
        return statePref.get()[key(contentId, episodeNumber)]
    }

    /** Get all playback states (for backup). */
    fun getAll(): Map<String, PlaybackState> = statePref.get()

    /** Clear the playback state for an episode. */
    fun clear(contentId: String, episodeNumber: Float) {
        val map = statePref.get().toMutableMap()
        map.remove(key(contentId, episodeNumber))
        statePref.set(map)
    }

    /**
     * Re-key an entry from [oldKey] to [newKey] (used by the migrator +
     * the future ContentIdMigrator).
     */
    fun rekey(oldKey: String, newKey: String) {
        val map = statePref.get().toMutableMap()
        val state = map.remove(oldKey) ?: return
        map[newKey] = state
        statePref.set(map)
    }

    /**
     * Replace the entire state map (used by the migrator for bulk re-keying +
     * by backup restore).
     */
    fun replaceAll(newMap: Map<String, PlaybackState>) {
        statePref.set(newMap)
    }
}
