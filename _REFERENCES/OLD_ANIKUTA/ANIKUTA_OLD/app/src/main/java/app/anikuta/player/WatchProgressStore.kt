package app.anikuta.player

import app.anikuta.core.preference.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Lightweight watch-progress store.
 *
 * Saves the last playback position per episode so the user can resume where
 * they left off. Keyed by AniList ID + episode URL (stable across sessions).
 *
 * Phase 1 revamp changes:
 *  - Added [changes] Flow so the History page reacts to progress saves without
 *    a manual refresh.
 *  - Added nullable [Progress.coverUrl] / [Progress.animeTitle] /
 *    [Progress.episodeNumber] / [Progress.thumbnailUrl] fields so the History
 *    page can show real covers + episode thumbnails (Phase 2). Old data
 *    deserializes fine (ignoreUnknownKeys + nullable defaults).
 *  - Added [deleteAll] for O(1) clear-all (was O(anime) pref writes).
 *
 * SQLDelight migration note:
 *  The full aniyomi approach stores progress in the `episodes` + `animehistory`
 *  SQLDelight tables. Our app is AniList-first, and `animes.source` + `url`
 *  (NOT NULL) aren't available until AniyomiSourceBridge resolves. Until that
 *  gap is closed, WatchProgressStore remains the source of truth for
 *  AniList-keyed progress. The SQLDelight repos are wired (Phase 0) and ready
 *  for when we resolve source URLs.
 *
 *  Related files (edit one → check the others):
 *    - PlayerActivity.kt saveProgress() — writes here
 *    - HistoryViewModel.kt — reads via [changes]
 *    - LibraryViewModel.kt sort() — reads via [getAll] for LAST_WATCHED sort
 */
class WatchProgressStore(
    private val preferenceStore: PreferenceStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val progressPref = preferenceStore.getObject(
        key = "pref_watch_progress_map",
        defaultValue = emptyMap(),
        serializer = { map ->
            json.encodeToString(
                kotlinx.serialization.builtins.MapSerializer(
                    String.serializer(),
                    Progress.serializer(),
                ),
                map,
            )
        },
        deserializer = { str ->
            try {
                json.decodeFromString(
                    kotlinx.serialization.builtins.MapSerializer(
                        String.serializer(),
                        Progress.serializer(),
                    ),
                    str,
                )
            } catch (e: Exception) {
                emptyMap()
            }
        },
    )

    /**
     * Reactive stream of all progress entries. Emits on every save/clear.
     * Used by HistoryViewModel so the History page updates in real time when
     * the user watches an episode.
     */
    val changes: Flow<Map<String, Progress>> = progressPref.changes().map { it }

    /** Key = "$anilistId:$episodeUrl" — stable across sessions. */
    private fun key(anilistId: Int, episodeUrl: String) = "$anilistId:$episodeUrl"

    /**
     * Save the current playback position for an episode.
     *
     * The optional fields ([coverUrl], [animeTitle], [episodeNumber],
     * [thumbnailUrl]) are used by the History page to show real covers +
     * episode thumbnails. They are nullable so callers that don't have them
     * (e.g. legacy code paths) can omit them.
     */
    fun save(
        anilistId: Int,
        episodeUrl: String,
        positionSeconds: Int,
        durationSeconds: Int,
        title: String,
        coverUrl: String? = null,
        animeTitle: String? = null,
        episodeNumber: Float = -1f,
        thumbnailUrl: String? = null,
    ) {
        val map = progressPref.get().toMutableMap()
        map[key(anilistId, episodeUrl)] = Progress(
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
            title = title,
            updatedAt = System.currentTimeMillis(),
            coverUrl = coverUrl,
            animeTitle = animeTitle,
            episodeNumber = episodeNumber,
            thumbnailUrl = thumbnailUrl,
        )
        progressPref.set(map)
    }

    /** Get the saved position for an episode, or null if none. */
    fun get(anilistId: Int, episodeUrl: String): Progress? {
        return progressPref.get()[key(anilistId, episodeUrl)]
    }

    /** Clear progress for a single episode. */
    fun clear(anilistId: Int, episodeUrl: String) {
        val map = progressPref.get().toMutableMap()
        map.remove(key(anilistId, episodeUrl))
        progressPref.set(map)
    }

    /** Clear all progress for an anime (all its episodes). */
    fun clearAnime(anilistId: Int) {
        val map = progressPref.get().toMutableMap()
        val prefix = "$anilistId:"
        map.keys.filter { it.startsWith(prefix) }.forEach { map.remove(it) }
        progressPref.set(map)
    }

    /**
     * Delete ALL progress entries in a single pref write.
     * O(1) — replaces the old O(anime) loop that called clearAnime() per anime.
     */
    fun deleteAll() {
        progressPref.set(emptyMap())
    }

    /** Get all saved progress entries (for the History page + Library sort). */
    fun getAll(): Map<String, Progress> = progressPref.get()

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
    )
}
