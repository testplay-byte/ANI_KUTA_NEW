package app.anikuta.data.cache

import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Phase N-1 (Notifications) — Persistent per-anime release-tracking state.
 *
 * Stores the tracker's "memory" for each tracked anime:
 *  - Last-known episode count (sub + dub) from the extension
 *  - Last check time + last seen airing time
 *  - Next scheduled check time
 *  - Release-time offset learning (sub + dub separately)
 *  - Per-anime notification + auto-download settings (null = inherit global)
 *
 * Architecture: SharedPreferences-backed JSON map, keyed by AniList ID (string).
 * Reactive via [changes] Flow (same pattern as SubDubStore / LibraryStore).
 *
 * Supabase integration (deferred): the [TrackedAnime] data class is designed
 * to be serializable for later Supabase crowd-sourced release tracking. The
 * store interface won't change — only the tracker will gain a Supabase hook.
 *
 * Related files:
 *   - ReleaseTracker.kt — reads + writes here during background checks
 *   - ReleaseCheckPlanner.kt — reads nextScheduledCheck to schedule WorkManager
 *   - DetailViewModel.kt — writes here when user manually refreshes (feeds offset learning)
 *   - LibraryStore — adding/removing from library triggers tracking add/remove here
 */
class ReleaseTrackingStore(
    private val preferenceStore: PreferenceStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class TrackedAnime(
        val anilistId: Int,
        val title: String,
        val coverUrl: String? = null,
        // ---- Source mapping (cached so the tracker doesn't re-search every time) ----
        /** The extension source ID that matches this anime. Null if not yet matched. */
        val sourceId: Long? = null,
        /** The extension anime URL (the SAnime.url). Null if not yet matched. */
        val animeUrl: String? = null,
        // ---- Tracking state ----
        val lastKnownEpisodeCount: Int = 0,
        val lastKnownSubCount: Int = 0,
        val lastKnownDubCount: Int = 0,
        val lastCheckTime: Long = 0L,
        val lastSeenAiringAt: Long = 0L,
        val nextScheduledCheck: Long = 0L,
        val isCompleted: Boolean = false,
        val hasPendingDub: Boolean = false,
        // ---- Release-time offset learning (§2.5 of the plan) ----
        /** Rolling average offset for SUB: avg(T_ext - T_al) over recent episodes, in millis. */
        val subReleaseOffsetMs: Long = 0L,
        /** Rolling average offset for DUB: avg(T_ext - T_al) over recent dub episodes, in millis. */
        val dubReleaseOffsetMs: Long = 0L,
        /** Number of sub episodes observed (for rolling average). */
        val subOffsetSampleCount: Int = 0,
        /** Number of dub episodes observed (for rolling average). */
        val dubOffsetSampleCount: Int = 0,
        // ---- Per-anime settings (null = inherit global default) ----
        val notifyOnNew: Boolean? = null,
        val notifySub: Boolean? = null,
        val notifyDub: Boolean? = null,
        val autoDownloadNew: Boolean? = null,
        val autoDownloadSub: Boolean? = null,
        val autoDownloadDub: Boolean? = null,
        val autoDownloadQuality: String? = null,
        val autoDownloadAudio: String? = null,
    )

    private val store = preferenceStore.getObject(
        key = "pref_release_tracking_map",
        defaultValue = emptyMap<String, TrackedAnime>(),
        serializer = { map ->
            json.encodeToString(MapSerializer(String.serializer(), TrackedAnime.serializer()), map)
        },
        deserializer = { str ->
            try {
                json.decodeFromString(MapSerializer(String.serializer(), TrackedAnime.serializer()), str)
            } catch (e: Exception) {
                emptyMap()
            }
        },
    )

    /** Reactive stream of all tracked anime. Emits on every update. */
    val changes: Flow<Map<String, TrackedAnime>> = store.changes().map { it }

    /** Get all tracked anime. */
    fun getAll(): Map<String, TrackedAnime> = store.get()

    /** Get tracking state for one anime. */
    fun get(anilistId: Int): TrackedAnime? = store.get()[anilistId.toString()]

    /** Add or replace tracking state for an anime. */
    fun put(anime: TrackedAnime) {
        val map = store.get().toMutableMap()
        map[anime.anilistId.toString()] = anime
        store.set(map)
    }

    /** Remove tracking for an anime (called when removed from library). */
    fun remove(anilistId: Int) {
        val map = store.get().toMutableMap()
        map.remove(anilistId.toString())
        store.set(map)
    }

    /** Check if an anime is tracked. */
    fun isTracked(anilistId: Int): Boolean = store.get().containsKey(anilistId.toString())

    /**
     * Update the release-time offset learning after detecting a new episode.
     *
     * @param anilistId The anime ID.
     * @param anilistAiringAt The AniList airing time for the detected episode (epoch millis).
     * @param extensionDetectedAt When the extension actually had the episode (epoch millis, ~now).
     * @param isDub True if this was a dub episode (updates dub offset); false for sub.
     */
    fun recordReleaseTime(anilistId: Int, anilistAiringAt: Long, extensionDetectedAt: Long, isDub: Boolean) {
        val anime = get(anilistId) ?: return
        val observedOffset = extensionDetectedAt - anilistAiringAt

        if (isDub) {
            // Rolling average for dub
            val newCount = anime.dubOffsetSampleCount + 1
            val newOffset = if (anime.dubOffsetSampleCount == 0) {
                observedOffset
            } else {
                (anime.dubReleaseOffsetMs * anime.dubOffsetSampleCount + observedOffset) / newCount
            }
            put(anime.copy(
                dubReleaseOffsetMs = newOffset,
                dubOffsetSampleCount = newCount,
            ))
        } else {
            // Rolling average for sub
            val newCount = anime.subOffsetSampleCount + 1
            val newOffset = if (anime.subOffsetSampleCount == 0) {
                observedOffset
            } else {
                (anime.subReleaseOffsetMs * anime.subOffsetSampleCount + observedOffset) / newCount
            }
            put(anime.copy(
                subReleaseOffsetMs = newOffset,
                subOffsetSampleCount = newCount,
            ))
        }
    }

    /**
     * Update episode counts after a check.
     */
    fun updateEpisodeCounts(anilistId: Int, episodeCount: Int, subCount: Int, dubCount: Int, checkTime: Long) {
        val anime = get(anilistId) ?: return
        put(anime.copy(
            lastKnownEpisodeCount = episodeCount,
            lastKnownSubCount = subCount,
            lastKnownDubCount = dubCount,
            lastCheckTime = checkTime,
        ))
    }

    /**
     * Set the next scheduled check time for an anime.
     */
    fun setNextScheduledCheck(anilistId: Int, time: Long) {
        val anime = get(anilistId) ?: return
        put(anime.copy(nextScheduledCheck = time))
    }

    /**
     * Update the cached source mapping (sourceId + animeUrl) for a tracked anime.
     * Called when the tracker first matches the anime to an extension source,
     * or when the detail page resolves the source.
     */
    fun updateSourceMapping(anilistId: Int, sourceId: Long, animeUrl: String) {
        val anime = get(anilistId) ?: return
        put(anime.copy(sourceId = sourceId, animeUrl = animeUrl))
    }

    /**
     * Get all anime whose nextScheduledCheck is due now or in the past.
     */
    fun getDueChecks(now: Long): List<TrackedAnime> {
        return store.get().values.filter { it.nextScheduledCheck in 1..now }
    }

    /**
     * Get the earliest nextScheduledCheck across all tracked anime.
     * Returns 0 if no anime are tracked.
     */
    fun getEarliestNextCheck(): Long {
        return store.get().values
            .mapNotNull { it.nextScheduledCheck.takeIf { t -> t > 0 } }
            .minOrNull() ?: 0L
    }
}
