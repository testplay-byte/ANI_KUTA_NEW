package app.anikuta.data.cache

import app.anikuta.core.preference.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Tracks which episodes the user has watched (seen).
 *
 * Keyed by "$anilistId:$episodeUrl" — the same key format as WatchProgressStore.
 * An episode is "seen" when the user has watched past the threshold (default 85%).
 *
 * This is SEPARATE from WatchProgressStore (which tracks resume position).
 * WatchProgressStore is cleared when an episode finishes; EpisodeSeenStore
 * persists the "watched" flag permanently until the user manually unmarks it.
 *
 * The player marks episodes as seen in [PlayerActivity.saveProgress] when
 * position >= duration * watchThreshold. The detail page reads from here
 * to grey out watched episodes.
 *
 * Reactive via [changes] Flow — the detail page updates in real time.
 */
class EpisodeSeenStore(
    private val preferenceStore: PreferenceStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val store = preferenceStore.getObject(
        key = "pref_episode_seen_set",
        defaultValue = emptySet<String>(),
        serializer = { set ->
            json.encodeToString(SetSerializer(String.serializer()), set)
        },
        deserializer = { str ->
            try {
                json.decodeFromString(SetSerializer(String.serializer()), str)
            } catch (e: Exception) {
                emptySet()
            }
        },
    )

    /** Reactive stream of all seen episode keys. Emits on every update. */
    val changes: Flow<Set<String>> = store.changes().map { it }

    /** Key format: "$anilistId:$episodeUrl" */
    private fun key(anilistId: Int, episodeUrl: String) = "$anilistId:$episodeUrl"

    /** Check if an episode is seen (watched). */
    fun isSeen(anilistId: Int, episodeUrl: String): Boolean {
        return store.get().contains(key(anilistId, episodeUrl))
    }

    /** Mark an episode as seen (watched). */
    fun markSeen(anilistId: Int, episodeUrl: String) {
        val set = store.get().toMutableSet()
        set.add(key(anilistId, episodeUrl))
        store.set(set)
    }

    /** Mark an episode as unseen (unwatched). */
    fun markUnseen(anilistId: Int, episodeUrl: String) {
        val set = store.get().toMutableSet()
        set.remove(key(anilistId, episodeUrl))
        store.set(set)
    }

    /** Toggle seen status. Returns the new status. */
    fun toggleSeen(anilistId: Int, episodeUrl: String): Boolean {
        val k = key(anilistId, episodeUrl)
        val set = store.get().toMutableSet()
        if (set.contains(k)) {
            set.remove(k)
            store.set(set)
            return false
        } else {
            set.add(k)
            store.set(set)
            return true
        }
    }

    /** Get all seen episode keys (for backup). */
    fun getAll(): Set<String> = store.get()

    /** Restore seen episodes from backup. */
    fun restoreAll(keys: Set<String>) {
        store.set(keys)
    }

    /** Clear all seen episodes (for an anime). */
    fun clearForAnime(anilistId: Int) {
        val prefix = "$anilistId:"
        val set = store.get().filterNot { it.startsWith(prefix) }.toSet()
        store.set(set)
    }
}
