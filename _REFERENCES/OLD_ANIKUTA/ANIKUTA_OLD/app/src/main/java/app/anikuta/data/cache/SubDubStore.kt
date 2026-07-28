package app.anikuta.data.cache

import app.anikuta.core.preference.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Caches sub/dub availability per anime (Phase B).
 *
 * When the Detail page resolves episode videos, the extension provides
 * `Video.audioTracks` and the video title parser detects AudioVersion
 * (SUB/DUB/HSUB). We cache per-anime:
 *  - hasSub: true if any episode has a SUB audio version
 *  - hasDub: true if any episode has a DUB audio version
 *  - subCount: number of episodes with SUB (approximate)
 *  - dubCount: number of episodes with DUB (approximate)
 *
 * This data is shown on library cards as "SUB" / "DUB" badges.
 *
 * Keyed by AniList ID (string). Reactive via [changes] Flow.
 *
 * Related files:
 *   - DetailViewModel.kt resolveVideos() — writes here after resolving
 *   - LibraryViewModel.kt — reads via [changes] for library card badges
 *   - LibraryScreen.kt — renders the badges on cards
 */
class SubDubStore(
    private val preferenceStore: PreferenceStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class SubDubInfo(
        val hasSub: Boolean = false,
        val hasDub: Boolean = false,
        val subCount: Int = 0,
        val dubCount: Int = 0,
        val totalEpisodes: Int = 0,
        val lastUpdated: Long = 0L,
    )

    private val store = preferenceStore.getObject(
        key = "pref_sub_dub_cache",
        defaultValue = emptyMap<String, SubDubInfo>(),
        serializer = { map ->
            json.encodeToString(MapSerializer(String.serializer(), SubDubInfo.serializer()), map)
        },
        deserializer = { str ->
            try {
                json.decodeFromString(MapSerializer(String.serializer(), SubDubInfo.serializer()), str)
            } catch (e: Exception) {
                emptyMap()
            }
        },
    )

    /** Reactive stream of all sub/dub data. Emits on every update. */
    val changes: Flow<Map<String, SubDubInfo>> = store.changes().map { it }

    /** Get sub/dub info for an anime. */
    fun get(anilistId: Int): SubDubInfo? = store.get()[anilistId.toString()]

    /** Get all sub/dub info (for backup). */
    fun getAll(): Map<String, SubDubInfo> = store.get()

    /**
     * Update the sub/dub info for an anime.
     * Called by DetailViewModel after resolving episode videos.
     */
    fun update(anilistId: Int, hasSub: Boolean, hasDub: Boolean, subCount: Int, dubCount: Int, totalEpisodes: Int) {
        val map = store.get().toMutableMap()
        map[anilistId.toString()] = SubDubInfo(
            hasSub = hasSub,
            hasDub = hasDub,
            subCount = subCount,
            dubCount = dubCount,
            totalEpisodes = totalEpisodes,
            lastUpdated = System.currentTimeMillis(),
        )
        store.set(map)
    }
}
