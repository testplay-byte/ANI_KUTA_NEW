package app.confused.anikuta.core.download

import app.confused.anikuta.core.preferences.Preference
import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Caches discovered server names per source ID, so the download settings can
 * show the user which servers each extension offers (and let them reorder).
 *
 * **Why this exists.** Server names (e.g. "Vidstreaming", "Streamtape",
 * "Beta Server") are only known AFTER resolving a video for a specific
 * episode — they're not listed in the extension metadata. So we record them
 * passively: every time [DownloadOrchestrator] resolves a video, it calls
 * [recordServers] with the server names it saw. Over time, this builds a
 * per-source map of known servers.
 *
 * The user can then rearrange these in Download Settings → Server preferences.
 * When auto-download is ON, [DownloadOrchestrator] uses the user's priority
 * order (from [DownloadPreferences.serverPreferences]) to pick the preferred
 * server. Servers not in the user's list are tried last.
 *
 * **Reactivity.** [serverMap] is a Flow so the settings UI updates live when
 * new servers are discovered.
 *
 * **Deduplication.** [recordServers] merges new names with existing ones
 * (preserving order: existing first, then new ones appended). This means the
 * user's manual reorder is preserved across new discoveries.
 */
class ServerDiscoveryStore(
    store: PreferenceStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val serverMapPref: Preference<Map<String, List<String>>> = store.getObject(
        KEY_SERVER_MAP,
        emptyMap(),
        { map -> json.encodeToString(
            MapSerializer(String.serializer(), ListSerializer(String.serializer())), map) },
        { str ->
            try { json.decodeFromString(
                MapSerializer(String.serializer(), ListSerializer(String.serializer())), str) }
            catch (e: Exception) {
                DownloadLogger.w("Failed to decode server map, starting fresh", e)
                emptyMap()
            }
        },
    )

    /** Reactive stream of the full server map (sourceId → server names). */
    val serverMap: Flow<Map<String, List<String>>> = serverMapPref.changes().map { it }

    /** Snapshot of all discovered servers for a source. */
    fun getServers(sourceId: Long): List<String> =
        serverMapPref.get()[sourceId.toString()] ?: emptyList()

    /**
     * Records [serverNames] for [sourceId]. Merges with existing: preserves
     * existing order, appends any new names. Deduplicates.
     */
    fun recordServers(sourceId: Long, serverNames: List<String>) {
        if (serverNames.isEmpty()) return
        val key = sourceId.toString()
        val current = serverMapPref.get().toMutableMap()
        val existing = current[key] ?: emptyList()
        // Merge: existing first (preserves user's reorder), then new ones.
        val merged = (existing + serverNames.filter { it !in existing }).distinct()
        if (merged != existing) {
            current[key] = merged
            serverMapPref.set(current)
            DownloadLogger.d("Recorded servers for source $sourceId: $merged")
        }
    }

    companion object {
        private const val KEY_SERVER_MAP = "pref_dl_server_discovery_v1"
    }
}
