package app.confused.anikuta

import android.app.Application
import android.content.Context
import android.util.Log
import app.confused.anikuta.di.databaseModule
import app.confused.anikuta.di.downloadAppModule
import app.confused.anikuta.di.extensionModule
import app.confused.anikuta.di.repositoryModule
import app.confused.anikuta.feature.history.di.historyModule
import app.confused.anikuta.feature.library.di.libraryModule
import app.confused.anikuta.feature.updates.di.updatesModule
import app.confused.anikuta.core.common.repository.CategoryRepository
import app.confused.anikuta.core.updatechecker.di.updateCheckerModule
import app.confused.anikuta.di.searchModule
import app.confused.anikuta.core.preferences.di.preferenceModule
import app.confused.anikuta.core.player.di.playerModule
import app.confused.anikuta.core.episodemetadata.di.episodeMetadataModule
import app.confused.anikuta.navigation.navModule
import eu.kanade.tachiyomi.animesource.ExtensionAppHolder
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // ── Crash handler (install FIRST, before anything that might throw) ──
        // This ensures that if DI setup or extension loading crashes, the user
        // gets the ErrorActivity screen instead of a silent crash.
        Thread.setDefaultUncaughtExceptionHandler(
            app.confused.anikuta.error.AnikutaCrashHandler(this),
        )

        // Extension app holder — MUST be set before Koin so the extension loader
        // can hand the Application context to ConfigurableAnimeSource extensions.
        ExtensionAppHolder.init(this)

        // ── Injekt singletons (for extension compat — ADR-029) ──
        // We use Koin for our own DI (ADR-023), but extensions call
        // Injekt.get<T>() for several host-provided singletons. These MUST be
        // registered in Injekt before any extension source is loaded.
        try {
            // Application — Keiyoushi extensions call Injekt.get<Application>().
            Injekt.addSingleton(fullType<Application>(), this)
            Injekt.addSingleton(fullType<Context>(), this)

            // NetworkHelper — AnimeHttpSource resolves it via `by injectLazy()`.
            // CRITICAL: NetworkHelper MUST be a class (not interface) — otherwise
            // extension bytecode throws IncompatibleClassChangeError on .client access.
            val networkHelper = NetworkHelper(this)
            Injekt.addSingleton(fullType<NetworkHelper>(), networkHelper)
            Log.i(TAG, "Injekt: Application + Context + NetworkHelper registered")

            // Json — Keiyoushi extensions call Injekt.get<Json>() in static
            // initializers (e.g. for preference serializers). Without this,
            // any extension that uses JSON parsing crashes with
            // ExceptionInInitializerError → InjektionException.
            Injekt.addSingletonFactory(fullType<Json>()) {
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            }
            Log.i(TAG, "Injekt: Json registered")
        } catch (e: Exception) {
            Log.w(TAG, "Injekt: failed to register one or more singletons", e)
        }

        // Koin DI (ADR-023)
        startKoin {
            androidContext(this@App)
            modules(
                databaseModule,
                repositoryModule,
                extensionModule,
                searchModule,
                preferenceModule,
                playerModule,
                libraryModule,
                episodeMetadataModule,
                // ── Agent 1: History + Updates ──
                updateCheckerModule,
                historyModule,
                updatesModule,
                // ── Agent 2: Profile + Trackers ──
                app.confused.anikuta.core.tracker.di.trackerModule,
                app.confused.anikuta.feature.my.di.myModule,
                app.confused.anikuta.feature.trackers.di.trackersModule,
                // ── Agent 1: Backup & Restore ──
                app.confused.anikuta.core.backup.di.backupModule,
                app.confused.anikuta.feature.backup.di.backupFeatureModule,
                app.confused.anikuta.feature.backup.di.aniyomiRestoreModule,
                // ── Agent 2: Downloads & Offline Playback ──
                downloadAppModule,
                // ── Voyager navigation (AppController + shared AniListApi) ──
                navModule,
                // ── Unified anime details translation layer (doc 05) ──
                // AniListDetailsProvider + ExtensionDetailsProvider → AnimeDetailsProviderRegistry.
                app.confused.anikuta.di.detailsModule,
                // ── Phase 2: Metadata provider abstraction (ADR-041) ──
                // AniListMetadataProvider → MetadataProviderRegistry. Adding MAL/TMDB
                // = one module + one entry in the List<MetadataProvider> binding.
                app.confused.anikuta.di.providerApiModule,
                // ── Phase 5: ContentIdMigrator (re-keys stores on link/unlink/switch) ──
                app.confused.anikuta.di.contentIdMigratorModule,
                // ── Advertising system (modular, customizable, on-device tracking) ──
                // Also consumed by the Setup Wizard's "Choose Your Poison" screen.
                app.confused.anikuta.core.ads.di.adsModule,
                // ── App self-update system (GitHub releases + APK download + install) ──
                app.confused.anikuta.core.appupdate.di.appUpdateModule,
            )
        }

        // ── Agent 2: Start the TrackSyncManager (auto-syncs progress to trackers) ──
        try {
            val trackSyncManager = GlobalContext.get().get<app.confused.anikuta.core.tracker.TrackSyncManager>()
            trackSyncManager.start()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start TrackSyncManager", e)
        }

        // Ensure the Default category exists (Phase A — library page).
        // The .sq file seeds it on fresh installs and the 1.sqm migration seeds
        // it on existing installs; this is a safety net called on every startup.
        try {
            val categoryRepo = GlobalContext.get().get<CategoryRepository>()
            CoroutineScope(Dispatchers.IO).launch {
                categoryRepo.ensureDefaultExists()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to ensure Default category exists", e)
        }

        // ── Phase 1 (ADR-050): Backfill local_id + content_id columns ──
        // The 2.sqm migration adds the columns (nullable); this backfill populates
        // them for existing rows on first launch post-migration. Gated by a preference
        // so it only runs once. The identity columns are dormant in Phase 1 — cross-cutting
        // stores migrate to key off content_id in Phases 3–6.
        try {
            val animeRepo = GlobalContext.get().get<app.confused.anikuta.core.common.repository.AnimeRepository>()
            val contentIdPrefs = GlobalContext.get().get<app.confused.anikuta.core.preferences.ContentIdPreferences>()
            val prefStore = GlobalContext.get().get<app.confused.anikuta.core.preferences.PreferenceStore>()
            val backfillDonePref = prefStore.getBoolean(KEY_IDENTITY_BACKFILL_DONE, false)
            CoroutineScope(Dispatchers.IO).launch {
                if (!backfillDonePref.get()) {
                    Log.i(TAG, "Identity backfill: starting (first launch post-migration)")
                    val count = animeRepo.backfillIdentityColumns(contentIdPrefs.getPriority())
                    Log.i(TAG, "Identity backfill: complete — $count rows backfilled")
                    backfillDonePref.set(true)
                }

                // ── Phase 3: Migrate watch progress + playback state to content_id keys ──
                // Runs AFTER the identity backfill (so animes have content_id populated).
                // Re-keys WatchProgressStore + PlaybackStateStore from "$anilistId:$episodeUrl"
                // to "$contentId|$episodeNumber". Gated by a pref; idempotent.
                val progressMigrationDone = prefStore.getBoolean(KEY_PROGRESS_MIGRATION_DONE, false)
                if (!progressMigrationDone.get()) {
                    Log.i(TAG, "Watch progress migration: starting")
                    try {
                        val migrator = GlobalContext.get().get<app.confused.anikuta.core.player.migration.WatchProgressMigrator>()
                        val result = migrator.migrate()
                        Log.i(TAG, "Watch progress migration: complete — " +
                            "progress migrated=${result.watchProgressMigrated} dropped=${result.watchProgressDropped}, " +
                            "playback migrated=${result.playbackStateMigrated} dropped=${result.playbackStateDropped}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Watch progress migration: failed (will retry next launch)", e)
                    }
                    progressMigrationDone.set(true)
                }

                // ── Phase 4: Migrate episode metadata + source links to content_id keys ──
                // Runs AFTER Phase 3 (progress migration) + the identity backfill.
                // Re-keys EpisodeMetadataCache (anilistId → content_id) +
                // SourceLinkStore (anilistId → content_id) +
                // ExtensionLinkStore (value: anilistId → content_id String).
                val metadataMigrationDone = prefStore.getBoolean(KEY_METADATA_MIGRATION_DONE, false)
                if (!metadataMigrationDone.get()) {
                    Log.i(TAG, "Metadata + source links migration: starting")
                    try {
                        val epMetaMigrator = GlobalContext.get()
                            .get<app.confused.anikuta.core.episodemetadata.migration.EpisodeMetadataMigrator>()
                        val epMetaResult = epMetaMigrator.migrate()
                        Log.i(TAG, "Episode metadata migration: complete — " +
                            "migrated=${epMetaResult.migrated} dropped=${epMetaResult.dropped} " +
                            "alreadyMigrated=${epMetaResult.alreadyMigrated}")

                        val sourceLinkMigrator = GlobalContext.get()
                            .get<app.confused.anikuta.data.extension.migration.SourceLinkMigrator>()
                        val sourceResult = sourceLinkMigrator.migrate()
                        Log.i(TAG, "Source links migration: complete — " +
                            "sourceLinks migrated=${sourceResult.sourceLinksMigrated} " +
                            "dropped=${sourceResult.sourceLinksDropped} " +
                            "extensionLinks=${sourceResult.extensionLinksCount}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Metadata + source links migration: failed (will retry next launch)", e)
                    }
                    metadataMigrationDone.set(true)
                }

                // ── Phase 6: Migrate downloads from anilistId to content_id ──
                // Re-keys DownloadStore tasks (legacyAnilistId → contentId) +
                // moves on-disk folders from [anilistId] to [al-anilistId].
                // Runs AFTER all other migrations. Gated by a pref; idempotent.
                val downloadMigrationDone = prefStore.getBoolean(KEY_DOWNLOAD_MIGRATION_DONE, false)
                if (!downloadMigrationDone.get()) {
                    Log.i(TAG, "Download migration: starting")
                    try {
                        val downloadMigrator = GlobalContext.get()
                            .get<app.confused.anikuta.migration.DownloadMigration>()
                        val result = downloadMigrator.migrate()
                        Log.i(TAG, "Download migration: complete — " +
                            "tasks migrated=${result.tasksMigrated} skipped=${result.tasksSkipped}, " +
                            "folders moved=${result.foldersMoved} failed=${result.foldersFailed}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Download migration: failed (will retry next launch)", e)
                    }
                    downloadMigrationDone.set(true)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to run identity backfill", e)
        }

        Log.i(TAG, "ANIKUTA started — DI wired (Koin + Injekt for extensions)")
    }

    companion object {
        private const val TAG = "AnikutaApp"
        private const val KEY_IDENTITY_BACKFILL_DONE = "pref_identity_backfill_v1_done"
        private const val KEY_PROGRESS_MIGRATION_DONE = "pref_progress_migration_v1_done"
        private const val KEY_METADATA_MIGRATION_DONE = "pref_metadata_migration_v1_done"
        private const val KEY_DOWNLOAD_MIGRATION_DONE = "pref_download_migration_v1_done"
    }
}
