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
 * Keyed by AniList ID + episode URL (same as WatchProgressStore).
 * Reactive via [changes] Flow.
 *
 * Related files:
 *   - PlayerActivity.kt — writes here on pause/stop, reads on resume
 *   - HistoryViewModel.kt — passes the state to the player via Intent extras
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

    /** Key = "$anilistId:$episodeUrl" — same as WatchProgressStore. */
    private fun key(anilistId: Int, episodeUrl: String) = "$anilistId:$episodeUrl"

    /** Save the playback state for an episode. */
    fun save(
        anilistId: Int,
        episodeUrl: String,
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
        map[key(anilistId, episodeUrl)] = PlaybackState(
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
    fun get(anilistId: Int, episodeUrl: String): PlaybackState? {
        return statePref.get()[key(anilistId, episodeUrl)]
    }

    /** Get all playback states (for backup). */
    fun getAll(): Map<String, PlaybackState> = statePref.get()

    /** Clear the playback state for an episode. */
    fun clear(anilistId: Int, episodeUrl: String) {
        val map = statePref.get().toMutableMap()
        map.remove(key(anilistId, episodeUrl))
        statePref.set(map)
    }
}
