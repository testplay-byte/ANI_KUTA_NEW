package app.confused.anikuta.core.tracker.di

import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.database.AnikutaDatabase
import app.confused.anikuta.core.player.WatchProgressStore
import app.confused.anikuta.core.preferences.PreferenceStore
import app.confused.anikuta.core.tracker.StatsCalculator
import app.confused.anikuta.core.tracker.TrackRepository
import app.confused.anikuta.core.tracker.TrackSyncManager
import app.confused.anikuta.core.tracker.TrackerManager
import app.confused.anikuta.core.tracker.anilist.AniListTrackApi
import app.confused.anikuta.core.tracker.anilist.AniListTracker
import app.confused.anikuta.core.tracker.mal.MalTrackApi
import app.confused.anikuta.core.tracker.mal.MalTracker
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the tracker infrastructure.
 *
 * Registers:
 * - [AniListTrackApi] / [MalTrackApi] — HTTP API clients.
 * - [AniListTracker] / [MalTracker] — tracker implementations.
 * - [TrackerManager] — registry holding all trackers.
 * - [TrackRepository] — SQLDelight CRUD for the animetrack table.
 * - [TrackSyncManager] — auto-sync watch progress to trackers.
 * - [StatsCalculator] — computes ProfileStats for the My Profile page.
 *
 * Must be added to the `modules(...)` list in `App.kt`'s `startKoin`.
 */
val trackerModule: Module = module {
    // API clients
    single { AniListTrackApi() }
    single { MalTrackApi() }

    // Trackers
    single { AniListTracker(get<PreferenceStore>(), get<AniListTrackApi>()) }
    single { MalTracker(get<PreferenceStore>(), get<MalTrackApi>()) }

    // Manager
    single { TrackerManager(get<AniListTracker>(), get<MalTracker>()) }

    // Repository
    single { TrackRepository(get<AnikutaDatabase>()) }

    // Sync manager
    single {
        TrackSyncManager(
            watchProgressStore = get<WatchProgressStore>(),
            trackRepository = get<TrackRepository>(),
            trackerManager = get<TrackerManager>(),
            animeRepository = get<AnimeRepository>(),
        )
    }

    // Stats calculator
    single {
        StatsCalculator(
            watchProgressStore = get<WatchProgressStore>(),
            animeRepository = get<AnimeRepository>(),
            trackerManager = get<TrackerManager>(),
        )
    }
}
