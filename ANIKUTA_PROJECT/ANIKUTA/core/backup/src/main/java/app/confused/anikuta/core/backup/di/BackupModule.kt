package app.confused.anikuta.core.backup.di

import app.confused.anikuta.core.backup.AutoBackupScheduler
import app.confused.anikuta.core.backup.BackupManager
import app.confused.anikuta.core.backup.BackupPreferences
import app.confused.anikuta.core.backup.BackupProvider
import app.confused.anikuta.core.backup.BackupStorage
import app.confused.anikuta.core.backup.provider.AnimeDetailsBackupProvider
import app.confused.anikuta.core.backup.provider.CategoryBackupProvider
import app.confused.anikuta.core.backup.provider.CoverDownloader
import app.confused.anikuta.core.backup.provider.CoverImageProvider
import app.confused.anikuta.core.backup.provider.EpisodeBackupProvider
import app.confused.anikuta.core.backup.provider.EpisodeMetadataBackupProvider
import app.confused.anikuta.core.backup.provider.LibraryBackupProvider
import app.confused.anikuta.core.backup.provider.PreferencesBackupProvider
import app.confused.anikuta.core.backup.provider.SourceLinkBackupAccess
import app.confused.anikuta.core.backup.provider.SourceLinkBackupProvider
import app.confused.anikuta.core.backup.provider.TrackerBackupProviderAdapter
import app.confused.anikuta.core.backup.provider.WatchProgressBackupProvider
import app.confused.anikuta.core.database.AnikutaDatabase
import app.confused.anikuta.core.episodemetadata.repository.EpisodeMetadataCache
import app.confused.anikuta.core.player.WatchProgressStore
import app.confused.anikuta.core.preferences.PreferenceStore
import app.confused.anikuta.core.tracker.TrackerBackupProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the backup engine (`:core:backup`).
 *
 * Registers:
 * - All 10 [BackupProvider] implementations as a single `List<BackupProvider>`
 *   (NOT 10 separate `single<BackupProvider>` — in Koin, multiple `single<T>`
 *   with the same type + no qualifier OVERWRITE each other, leaving only the
 *   last one registered. This was the root cause of "only 1 category saved").
 * - [CoverDownloader] — HTTP cover image downloader.
 * - [BackupManager] — the orchestrator (receives the provider list).
 * - [BackupPreferences] — auto-backup config + SAF folder URI.
 * - [BackupStorage] — SAF folder/file management.
 * - [AutoBackupScheduler] — WorkManager periodic work scheduler.
 *
 * **Adding a new provider:**
 * 1. Create the provider class.
 * 2. Add it to the `listOf(...)` in the `single<List<BackupProvider>>` binding below.
 */
val backupModule: Module = module {
    // ── All backup providers as a single list ──
    // CRITICAL: Do NOT use multiple `single<BackupProvider> { ... }` — they share
    // the same Koin key (BackupProvider, null) and overwrite each other.
    // Using a single List binding preserves all 10 providers.
    single<List<BackupProvider>> {
        listOf(
            LibraryBackupProvider(get<AnikutaDatabase>()),
            AnimeDetailsBackupProvider(get<AnikutaDatabase>()),
            EpisodeBackupProvider(get<AnikutaDatabase>()),
            CategoryBackupProvider(get<AnikutaDatabase>()),
            EpisodeMetadataBackupProvider(get<EpisodeMetadataCache>()),
            WatchProgressBackupProvider(get<WatchProgressStore>()),
            SourceLinkBackupProvider(get<SourceLinkBackupAccess>()),
            TrackerBackupProviderAdapter(get<TrackerBackupProvider>()),
            PreferencesBackupProvider(get<PreferenceStore>()),
            CoverImageProvider(get<AnikutaDatabase>()),
        )
    }

    // ── Cover downloader ──
    single { CoverDownloader() }

    // ── Orchestrator (receives the full provider list) ──
    single {
        BackupManager(
            providers = get<List<BackupProvider>>(),
            coverDownloader = get<CoverDownloader>(),
        )
    }

    // ── Preferences + storage ──
    single { BackupPreferences(get<PreferenceStore>()) }
    single { BackupStorage(androidContext(), get<BackupPreferences>()) }
    single { AutoBackupScheduler(androidContext()) }
}
