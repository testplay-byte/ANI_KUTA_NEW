package app.anikuta.di

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import app.anikuta.core.preference.AndroidPreferenceStore
import app.anikuta.core.preference.PreferenceStore
import app.anikuta.core.storage.AndroidStorageFolderProvider
import app.anikuta.storage.StorageManager
import app.anikuta.storage.StoragePreferences
import app.anikuta.data.AnimeDatabase
import app.anikuta.data.AnimeDatabaseFactory
import app.anikuta.data.cache.CacheManager
import app.anikuta.data.cache.EpisodeCacheStore
import app.anikuta.data.cache.LocalCache
import app.anikuta.data.cache.SubDubStore
import app.anikuta.data.cache.ExtensionLinkStore
import app.anikuta.data.cache.EpisodeSeenStore
import app.anikuta.data.cache.ReleaseTrackingStore
import app.anikuta.notification.NotificationPreferences
import app.anikuta.notification.ReleaseCheckPlanner
import app.anikuta.notification.SubDubResolver
import app.anikuta.notification.ReleaseTracker
import app.anikuta.notification.NotificationDispatcher
import app.anikuta.backup.BackupManager
import app.anikuta.data.handlers.anime.AndroidAnimeDatabaseHandler
import app.anikuta.data.handlers.anime.AnimeDatabaseHandler
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.supabase.SupabaseClient
import app.anikuta.player.PlayerPreferences
import app.anikuta.player.PlayerEpisodePreferences
import app.anikuta.player.WatchProgressStore
import app.anikuta.player.PlaybackStateStore
import app.anikuta.ui.library.LibraryStore
import app.anikuta.ui.library.CategoryStore
import app.anikuta.ui.library.LibraryDisplayPrefs
import app.anikuta.data.tracker.AniListTracker
import app.anikuta.source.bridge.AniyomiSourceBridge
import app.anikuta.download.DownloadPreferences
import app.anikuta.download.DownloadProvider
import app.anikuta.download.DownloadStore
import app.anikuta.download.DownloadManager
import app.anikuta.download.DownloadVideoResolver
import app.anikuta.download.DownloadNotifier
import app.anikuta.download.engine.DownloadEngine
import app.anikuta.download.engine.DownloadManifest
import app.anikuta.download.engine.HlsDownloadEngine
import app.anikuta.download.engine.SegmentDownloadEngine
import app.anikuta.download.engine.SinglePassDownloadEngine
import app.anikuta.download.engine.hls.HlsPlaylistFetcher
import app.anikuta.download.engine.hls.HlsPlaylistParser
import app.anikuta.download.engine.hls.HlsSegmentDownloader
import app.anikuta.download.progress.ProgressTracker
import app.anikuta.domain.extension.anime.interactor.TrustAnimeExtension
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.DeleteAnimeExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.GetAnimeExtensionRepoCount
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.ReplaceAnimeExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import app.anikuta.domain.mihon.extensionrepo.service.ExtensionRepoService
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import app.anikuta.domain.source.service.SourcePreferences
import app.anikuta.data.mihon.AnimeExtensionRepoRepositoryImpl
import app.anikuta.extension.anime.AnimeExtensionManager
import app.anikuta.extension.anime.util.AnimeExtensionLoader
import app.anikuta.source.AndroidAnimeSourceManager
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class AppModule(val app: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)
        addSingleton<Context>(app)

        // JSON serializer — extensions (via keiyoushi.utils) call Injekt.get<Json>()
        // in static initializers. Without this, any extension that uses JSON parsing
        // crashes with ExceptionInInitializerError → InjektionException.
        addSingletonFactory {
            kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }

        // Preferences
        addSingletonFactory { AndroidPreferenceStore(get<Context>()) as PreferenceStore }
        addSingletonFactory { NetworkPreferences(get<PreferenceStore>()) }
        addSingletonFactory { NetworkHelper(get<Context>(), get<NetworkPreferences>()) }

        // Storage (SAF folder selection — onboarding picks a folder, persists URI)
        addSingletonFactory { AndroidStorageFolderProvider(get<Context>()) }
        addSingletonFactory { StoragePreferences(get<AndroidStorageFolderProvider>(), get<PreferenceStore>()) }
        addSingletonFactory { StorageManager(get<Context>(), get<StoragePreferences>()) }

        // Player preferences + MPV player surface
        addSingletonFactory { PlayerPreferences(get<PreferenceStore>()) }
        addSingletonFactory { PlayerEpisodePreferences(get<PreferenceStore>()) }
        addSingletonFactory { WatchProgressStore(get<PreferenceStore>()) }
        // Phase C — Playback state memory (last video URL + tracks per episode)
        addSingletonFactory { PlaybackStateStore(get<PreferenceStore>()) }

        // Phase 5 task 5.6 — Library persistence (saved AniList IDs + cached JSON)
        addSingletonFactory { LibraryStore(get<PreferenceStore>()) }
        // Phase 4 — Library categories (Default + user-created)
        addSingletonFactory { CategoryStore(get<PreferenceStore>()) }
        // Phase E — Library display customization (persisted)
        addSingletonFactory { LibraryDisplayPrefs(get<PreferenceStore>()) }

        // Anime database — register BOTH AnimeDatabase and SqlDriver.
        // AndroidAnimeDatabaseHandler needs both (db for queries, driver for
        // transaction checks). Without registering SqlDriver, Injekt.get()
        // fails when constructing the handler → repo management breaks.
        // We use AnimeDatabaseFactory to create both the driver and the db,
        // then register each as a singleton.
        val (dbDriver, dbInstance) = AnimeDatabaseFactory.createWithDriver(app)
        addSingleton(dbDriver)
        addSingleton(dbInstance)
        addSingletonFactory<AnimeDatabaseHandler> { AndroidAnimeDatabaseHandler(get(), get()) }

        // Extension + source management
        addSingletonFactory { SourcePreferences(get<PreferenceStore>()) }

        // Extension repo management (Phase 7 — real implementations, replacing
        // the old stubs that shadowed the domain/ versions).
        addSingletonFactory<AnimeExtensionRepoRepository> {
            AnimeExtensionRepoRepositoryImpl(get<AnimeDatabaseHandler>())
        }
        addSingletonFactory { ExtensionRepoService(get<NetworkHelper>(), get<Json>()) }
        addSingletonFactory { CreateAnimeExtensionRepo(get<AnimeExtensionRepoRepository>(), get<ExtensionRepoService>()) }
        addSingletonFactory { DeleteAnimeExtensionRepo(get<AnimeExtensionRepoRepository>()) }
        addSingletonFactory { ReplaceAnimeExtensionRepo(get<AnimeExtensionRepoRepository>()) }
        addSingletonFactory { GetAnimeExtensionRepo(get<AnimeExtensionRepoRepository>()) }
        addSingletonFactory { GetAnimeExtensionRepoCount(get<AnimeExtensionRepoRepository>()) }
        addSingletonFactory { UpdateAnimeExtensionRepo(get<AnimeExtensionRepoRepository>(), get<ExtensionRepoService>()) }

        // Trust system (Phase 7 — real implementation with SourcePreferences)
        addSingletonFactory { TrustAnimeExtension(get<SourcePreferences>()) }

        addSingletonFactory { AnimeExtensionLoader(get<Context>()) }
        addSingletonFactory { AnimeExtensionManager(get<Context>(), get(), get()) }
        addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(get<Context>(), get()) }

        // AniList ↔ extension source bridge (fuzzy title matching)
        addSingletonFactory { AniyomiSourceBridge(get<AnimeSourceManager>()) }

        // AniList tracker (OAuth + progress sync)
        addSingletonFactory { AniListTracker(get<PreferenceStore>()) }

        // Download manager (modular architecture — Phase 1+2)
        addSingletonFactory { DownloadPreferences(get<PreferenceStore>()) }
        addSingletonFactory { DownloadStore(get<PreferenceStore>()) }
        addSingletonFactory { DownloadProvider(get<Context>(), get<app.anikuta.storage.StorageManager>()) }
        addSingletonFactory { DownloadVideoResolver(get<AnimeSourceManager>(), get<DownloadPreferences>()) }
        // Engine infrastructure
        addSingletonFactory { DownloadManifest(get<Context>(), get<DownloadProvider>()) }
        addSingletonFactory { ProgressTracker() }
        addSingletonFactory { DownloadNotifier(get<Context>()) }
        // All three download engines registered — user picks in settings
        // 1. Single-pass (aniyomi approach — correct size/duration, no resume)
        addSingletonFactory {
            SinglePassDownloadEngine(
                get<Context>(),
                get<DownloadProvider>(),
                get<DownloadVideoResolver>(),
                get<ProgressTracker>(),
                get(),
            )
        }
        // 2. HLS direct (HTTP segments — resume, precise progress, proxy issues)
        addSingletonFactory { HlsPlaylistParser() }
        addSingletonFactory {
            HlsPlaylistFetcher(
                client = get<NetworkHelper>().client,
                parser = get(),
            )
        }
        addSingletonFactory {
            HlsSegmentDownloader(
                client = get<NetworkHelper>().client,
            )
        }
        addSingletonFactory {
            HlsDownloadEngine(
                context = get(),
                provider = get(),
                resolver = get(),
                manifestManager = get(),
                progressTracker = get(),
                fetcher = get(),
                segmentDownloader = get(),
                networkHelper = get(),
                fallbackEngine = get<SegmentDownloadEngine>(),
                downloadPrefs = get(),
            )
        }
        // 3. Segment (FFmpeg -ss — resume, precise progress, wrong size for short videos)
        addSingletonFactory {
            SegmentDownloadEngine(
                get<Context>(),
                get<DownloadProvider>(),
                get<DownloadVideoResolver>(),
                get<DownloadManifest>(),
                get<ProgressTracker>(),
            )
        }
        // DownloadEngine interface returns whichever engine the user selected.
        // The actual selection happens in DownloadWorker.processDownload() which
        // reads downloadPrefs.downloadMethod() and picks the right engine.
        addSingletonFactory<DownloadEngine> { get<SinglePassDownloadEngine>() }
        addSingletonFactory { DownloadManager(get<Context>(), get(), get()) }

        // AniList client
        addSingletonFactory { AniListRepository(get()) }

        // 3-step cache
        addSingletonFactory { LocalCache(get<AnimeDatabase>()) }
        addSingletonFactory { SupabaseClient(get()) }
        addSingletonFactory { CacheManager(get(), get()) }

        // Episode cache (persistent — survives app restart)
        addSingletonFactory { EpisodeCacheStore(get<Context>()) }
        // Phase B — Sub/Dub cache (populated by DetailViewModel, read by Library)
        addSingletonFactory { SubDubStore(get<PreferenceStore>()) }
        // Phase I — Extension-to-AniList link cache
        addSingletonFactory { ExtensionLinkStore(get<PreferenceStore>()) }
        // Episode seen (watched) tracking
        addSingletonFactory { EpisodeSeenStore(get<PreferenceStore>()) }

        // ---- Phase N (Notifications) — release tracking system ----
        // Release-tracking state store (per-anime tracking + offset learning)
        addSingletonFactory { ReleaseTrackingStore(get<PreferenceStore>()) }
        // Global notification + auto-download preferences
        addSingletonFactory { NotificationPreferences(get<PreferenceStore>()) }
        // The scheduler (computes next check time, schedules WorkManager)
        addSingletonFactory { ReleaseCheckPlanner(get<Context>(), get()) }
        // Sub/dub resolver (resolves videos for one episode to detect audio versions)
        addSingletonFactory { SubDubResolver() }
        // Notification dispatcher (builds + fires Android notifications)
        addSingletonFactory { NotificationDispatcher(get<Context>(), get()) }
        // The release-tracking brain (delegates to store, detector, resolver, dispatcher, planner)
        addSingletonFactory {
            ReleaseTracker(
                store = get(),
                sourceManager = get(),
                bridge = get(),
                subDubResolver = get(),
                prefs = get(),
                planner = get(),
                libraryStore = get(),
                subDubStore = get(),
                anilistRepository = get(),
                notificationDispatcher = get(),
                downloadManager = get(),
            )
        }

        // ---- Backup system ----
        addSingletonFactory {
            BackupManager(
                context = get(),
                libraryStore = get(),
                watchProgressStore = get(),
                categoryStore = get(),
                releaseTrackingStore = get(),
                subDubStore = get(),
                extensionLinkStore = get(),
                playbackStateStore = get(),
            )
        }
    }
}
