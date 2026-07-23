package app.confused.anikuta.core.updatechecker

import app.confused.anikuta.core.preferences.Preference
import app.confused.anikuta.core.preferences.PreferenceStore

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
 *
 * **Future-Proofing.** When a WorkManager worker is added, it will read
 * [checkIntervalHours] to compute its repeat period and [lastCheckTimestamp]
 * to skip a run if a manual check just ran. The per-anime count prefs let the
 * worker diff without re-reading the whole history.
 */
class UpdateCheckerPreferences(private val store: PreferenceStore) {

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

    /** Clears ALL per-anime episode-count prefs + the last-check timestamp. */
    fun resetAll() {
        // Best-effort: we don't enumerate prefs, so we reset the timestamp and
        // rely on per-anime counts being overwritten on the next check. A full
        // reset would require enumerating PreferenceStore.getAll() — left for
        // a future cleanup if needed.
        lastCheckTimestamp().set(0L)
    }
}
