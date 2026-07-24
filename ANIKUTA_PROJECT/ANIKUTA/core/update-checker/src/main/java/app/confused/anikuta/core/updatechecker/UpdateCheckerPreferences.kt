package app.confused.anikuta.core.updatechecker

import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.preferences.Preference
import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * PreferenceStore-backed preferences for [UpdateChecker].
 *
 * Follows the same pattern as `LibraryPreferences` / `EpisodeDisplayPreferences`:
 * a plain class (Koin `single`) that constructor-injects a [PreferenceStore]
 * and exposes one `fun` per pref returning a `Preference<T>`.
 *
 * Keys:
 *  - `pref_update_check_last_ts` — when the last full check ran (epoch ms).
 *  - `pref_update_check_interval_hours` — desired background interval (future
 *    WorkManager use). Default 3h. The Updates page does NOT gate on this —
 *    it's here for the future worker.
 *  - `pref_update_check_ep_count_<animeId>` — last-known episode count per
 *    anime (used to diff against a freshly-fetched list). 0 = never checked.
 *  - `pref_update_check_results` — the serialized merged results list (old +
 *    new), so the Updates page survives process death. The user reported that
 *    closing + reopening the app cleared the list — this pref fixes that.
 *
 * **Persistence approach.** [UpdateResult] embeds [Anime], which lives in
 * `:core:common` and isn't `@Serializable` (adding the kotlinx-serialization
 * plugin there would ripple across the codebase). So we persist via a
 * serializable [StoredResult] DTO + convert at the boundary. Only the fields
 * the UI needs to re-render are stored (id, title, cover, counts, flags,
 * timestamps) — the full [Anime] is re-fetched live from the repo when the
 * user opens the detail page anyway.
 *
 * **Future-Proofing.** When a WorkManager worker is added, it will read
 * [checkIntervalHours] to compute its repeat period and [lastCheckTimestamp]
 * to skip a run if a manual check just ran. The per-anime count prefs let the
 * worker diff without re-reading the whole history.
 */
class UpdateCheckerPreferences(private val store: PreferenceStore) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Epoch-ms of the last successful [UpdateChecker.checkForUpdates] run. */
    fun lastCheckTimestamp(): Preference<Long> =
        store.getLong("pref_update_check_last_ts", 0L)

    /**
     * Desired background-check interval in hours. Consumed by a future
     * WorkManager worker (not yet wired). Default 3h.
     */
    fun checkIntervalHours(): Preference<Int> =
        store.getInt("pref_update_check_interval_hours", 3)

    /**
     * Last-known episode count for [animeId]. Used by [UpdateChecker] to diff
     * a freshly-fetched episode list against the previously-seen count.
     *
     * Returns 0 for never-checked anime (the default), which means the first
     * check for a newly-added library anime reports ALL fetched episodes as
     * "new" — that's intentional (the user sees the full available episode
     * list once, then only deltas thereafter).
     */
    fun lastKnownEpisodeCount(animeId: Long): Preference<Int> =
        store.getInt("pref_update_check_ep_count_$animeId", 0)

    /** Sets the last-known episode count for [animeId]. */
    fun setLastKnownEpisodeCount(animeId: Long, count: Int) {
        lastKnownEpisodeCount(animeId).set(count)
    }

    /**
     * The persisted merged-results list (old + new [UpdateResult]s), stored as
     * serializable [StoredResult] DTOs.
     *
     * On app start, [UpdateChecker] loads this into its in-memory `_results`
     * StateFlow so the Updates page shows the previous check's results
     * immediately — even after the process was killed. The user reported that
     * closing + reopening the app cleared the list; this pref is the fix.
     */
    fun storedResults(): Preference<List<StoredResult>> =
        store.getObject(
            key = "pref_update_check_results",
            defaultValue = emptyList(),
            serializer = { list -> json.encodeToString(ListSerializer(StoredResult.serializer()), list) },
            deserializer = { str ->
                try {
                    json.decodeFromString(ListSerializer(StoredResult.serializer()), str)
                } catch (e: Exception) {
                    emptyList()
                }
            },
        )

    /** Clears ALL per-anime episode-count prefs + the last-check timestamp + stored results. */
    fun resetAll() {
        lastCheckTimestamp().set(0L)
        storedResults().set(emptyList())
    }
}

/**
 * Serializable DTO for persisting an [UpdateResult] across process death.
 *
 * We store only the fields the Updates UI needs to re-render (id, title,
 * cover URL/color, counts, flags, timestamps). The full [Anime] object is
 * NOT stored (it's not `@Serializable` and re-fetching it from the repo is
 * cheap + always fresh). When [UpdateChecker] loads stored results on init,
 * it rebuilds a minimal [Anime] from these fields — enough for the row to
 * render; the detail page re-fetches the full record.
 */
@Serializable
data class StoredResult(
    val animeId: Long,
    val anilistId: Int? = null,
    val animeTitle: String,
    val coverUrl: String? = null,
    val coverColor: String? = null,
    val newEpisodeCount: Int,
    val checkedAt: Long,
    val hasSub: Boolean,
    val hasDub: Boolean,
    val sourceName: String? = null,
    val isNew: Boolean = false,
)

/** Converts a [UpdateResult] to its serializable [StoredResult] form. */
fun UpdateResult.toStored(): StoredResult = StoredResult(
    animeId = anime.id,
    anilistId = anime.anilistId,
    animeTitle = anime.title,
    coverUrl = anime.coverUrl,
    coverColor = anime.coverColor,
    newEpisodeCount = newEpisodeCount,
    checkedAt = checkedAt,
    hasSub = hasSub,
    hasDub = hasDub,
    sourceName = sourceName,
    isNew = isNew,
)

/**
 * Rebuilds a minimal [UpdateResult] from a [StoredResult].
 *
 * The [Anime] is reconstructed with only the fields the UI needs (id, title,
 * cover, anilistId, coverColor). Other fields are defaulted. This is enough
 * for the Updates row to render; the detail page re-fetches the full record
 * via [AnimeRepository.getById] / AniList.
 */
fun StoredResult.toUpdateResult(): UpdateResult = UpdateResult(
    anime = Anime(
        id = animeId,
        url = "",
        title = animeTitle,
        artist = null,
        author = null,
        description = null,
        genre = emptyList(),
        coverUrl = coverUrl,
        status = 0,
        thumbnailUrl = null,
        favorite = true,
        sourceId = 0,
        dateAdded = 0,
        viewerFlags = 0,
        nextUpdate = 0,
        updateStrategy = 0,
        coverLastModified = 0,
        releaseDate = null,
        lastRefresh = 0,
        lastMetadataFetch = null,
        nextEpisodeCheck = null,
        anilistId = anilistId,
        coverColor = coverColor,
        score = null,
        totalEpisodes = null,
        lastWatched = 0,
        nextAiringEpisode = null,
    ),
    newEpisodeCount = newEpisodeCount,
    newEpisodes = emptyList(), // Not persisted — re-fetched on detail open.
    checkedAt = checkedAt,
    hasSub = hasSub,
    hasDub = hasDub,
    sourceName = sourceName,
    isNew = isNew,
)
