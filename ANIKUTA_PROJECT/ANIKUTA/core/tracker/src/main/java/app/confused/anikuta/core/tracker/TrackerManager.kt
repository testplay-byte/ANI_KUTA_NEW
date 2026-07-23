package app.confused.anikuta.core.tracker

import app.confused.anikuta.core.tracker.anilist.AniListTracker
import app.confused.anikuta.core.tracker.mal.MalTracker
import kotlinx.coroutines.flow.combine

/**
 * Registry holding all tracker singletons (ADR-019: multiple trackers).
 *
 * Provides lookup by ID and a combined logged-in flow for UI state.
 */
class TrackerManager(
    private val anilistTracker: AniListTracker,
    private val malTracker: MalTracker,
) {
    val trackers: List<Tracker> = listOf(anilistTracker, malTracker)

    fun getTracker(id: Int): Tracker? = trackers.find { it.id == id }

    val anilist: AniListTracker get() = anilistTracker
    val mal: MalTracker get() = malTracker

    /** All currently logged-in trackers. */
    fun loggedInTrackers(): List<Tracker> = trackers.filter { it.isLoggedIn }

    /** Flow that emits whenever any tracker's logged-in state changes. */
    val loggedInTrackersFlow = combine(
        anilistTracker.username,
        malTracker.username,
    ) { anilistUser, malUser ->
        buildList {
            if (anilistUser != null) add(anilistTracker)
            if (malUser != null) add(malTracker)
        }
    }

    companion object {
        const val ANILIST_ID = Tracker.ANILIST_ID
        const val MAL_ID = Tracker.MAL_ID
    }
}
