# app.confused.anikuta.core.backup

Backup/restore engine for ANIKUTA (ADR-028, ADR-035).

## Architecture

This module is the **aggregation point** for all backup data. It depends on the
data-owning modules to read/write their stores, and provides a format-agnostic
engine that can create and restore backups in multiple formats.

### Key components

| Component | File | Role |
|---|---|---|
| `BackupProvider` | `BackupProvider.kt` | Interface — each data source implements this |
| `BackupEntry` | `BackupEntry.kt` | Sealed class — one subclass per data type |
| `BackupFormat` | `BackupFormat.kt` | Interface — pluggable file formats |
| `BackupManager` | `BackupManager.kt` | Orchestrator — creates/restores backups |
| `BackupStorage` | `BackupStorage.kt` | SAF folder + file management |
| `BackupPreferences` | `BackupPreferences.kt` | Auto-backup config + folder URI |
| `AutoBackupWorker` | `AutoBackupWorker.kt` | WorkManager periodic worker |
| `AutoBackupScheduler` | `AutoBackupScheduler.kt` | Enqueues/cancels WorkManager |
| `CoverDownloader` | `provider/CoverImageProvider.kt` | HTTP cover image downloader |

### Backup format (ANIKUTA)

The `.anikuta` file is a **ZIP archive** containing:
- `meta.json.gz` — gzipped JSON of all backup data (polymorphic sealed class)
- `covers/<anilistId>.jpg` — optional cover image files

### Aniyomi compatibility (restore-only)

`AniyomiBackupFormat` decodes Aniyomi `.tachibk` protobuf backups using
`kotlinx-serialization-protobuf` + minimal model classes in `format/aniyomi/`.
Anime are matched to AniList IDs via their tracker entries.

### Backup providers (10)

| Provider | Category | Data source |
|---|---|---|
| `LibraryBackupProvider` | Library anime | `animes` table (favorite=1) |
| `AnimeDetailsBackupProvider` | Anime details | `animes` table (all) |
| `EpisodeBackupProvider` | Episodes list | `episodes` table |
| `EpisodeMetadataBackupProvider` | Episode metadata | `EpisodeMetadataCache` |
| `WatchProgressBackupProvider` | Watch progress | `WatchProgressStore` |
| `SourceLinkBackupProvider` | Source links | `SourceLinkStore` + `ExtensionLinkStore` |
| `TrackerBackupProviderAdapter` | Tracker | `TrackerBackupProvider` (in `:core:tracker`) |
| `CategoryBackupProvider` | Categories | `categories` + `anime_category` tables |
| `PreferencesBackupProvider` | Preferences | `PreferenceStore.getAll()` |
| `CoverImageProvider` | Cover images | `animes.cover_url` + OkHttp download |

### Adding a new backup category

1. Add a `BackupCategory` enum entry.
2. Add a `BackupEntry` subclass (with `@Transient providerId`).
3. Create a `BackupProvider` implementation in `provider/`.
4. Register it in `di/BackupModule.kt` as `single<BackupProvider> { ... }`.
5. The `BackupManager` automatically picks it up via `getAll<BackupProvider>()`.

## Dependencies

- `:core:database`, `:core:preferences`, `:core:player`, `:core:episode-metadata`, `:core:tracker`
- `:data:extension` (for SourceLinkStore + ExtensionLinkStore)
- `kotlinx-serialization-json` + `kotlinx-serialization-protobuf`
- `OkHttp` (cover downloads)
- `WorkManager` (auto-backup)
- `DocumentFile` (SAF folder management)

## Status

Phase: **Implementation complete** (Agent 1 — Backup & Restore).
