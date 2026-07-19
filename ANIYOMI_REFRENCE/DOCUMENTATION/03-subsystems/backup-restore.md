# 03-subsystems / Backup & Restore

> How Aniyomi serialises the user's library (manga **and** anime), categories,
> history, tracks, extension list, extension-repo list, custom MPV buttons, and
> app/source preferences into a single portable file — and how it loads them
> back.

## Purpose

A backup is the user's escape hatch: phone lost, ROM flash, migrating from
Tachiyomi/Mihon, or moving between Aniyomi forks. Aniyomi's backup engine
captures almost everything that is not a downloaded file or an extension APK
(those are too big and machine-specific), and packs the rest into one
self-describing file the user can share, store, or restore on another device.

What is in scope:

| Backed up | Not backed up (lives on disk separately) |
|---|---|
| Manga + chapters + per-chapter read/bookmark state | Downloaded chapter/episode files |
| Anime + episodes + per-episode watched state | Cover / background image cache |
| Manga & anime **categories** (name, order, flags) | Chapter-disk-cache (page images) |
| Reading / watching **history** (last-read URL + timestamp + duration) | Extension APKs (extension *list* is backed up, see below) |
| **Track** bindings (MAL, AniList, Shikimori, Bangumi, MangaUpdates…) | Tracker OAuth tokens |
| App preferences (optionally incl. private ones) | Shizuku / system state |
| Per-source preferences | |
| Manga & anime **extension-repo** URLs | |
| **Custom buttons** (MPV Lua scripts) | |
| List of installed extensions (optional; APK bytes embedded) | |

## The backup format: `.tachibk`

A `.tachibk` file is a **gzipped Protocol Buffers** payload. Confirmed by
`BackupCreator.backup()`:

```kotlin
val byteArray = parser.encodeToByteArray(Backup.serializer(), backup)
file.openOutputStream().sink().gzip().buffer().use { it.write(byteArray) }
```

where `parser` is `kotlinx.serialization.protobuf.ProtoBuf` (injected via
Injekt). The schema is declared in pure Kotlin with `@ProtoNumber(n)`
annotations on every field — there is no `.proto` file in the repo. The legacy
JSON format (used by very old Tachiyomi versions) is **not** written any more;
`BackupDecoder` only detects it to throw a friendly "use an older app version"
error.

### Filenames

Auto backups follow a strict regex so the engine can find and prune them:

```
${APPLICATION_ID}_yyyy-MM-dd_HH-mm.tachibk
e.g. xyz.jmir.tachiyomi.mi_2024-05-12_14-30.tachibk
```

Manual backups use whatever filename the user picked in the SAF picker.

### Container layout

The top-level protobuf message is `Backup`
(`data/backup/models/Backup.kt`). Field numbers are deliberately sparse so
Aniyomi could evolve without breaking old files. The "Aniyomi-specific values"
block is **bumped to field 500+** so a Tachiyomi/Mihon reader that doesn't
know about anime can still parse the manga half.

```
Backup (protobuf)
├── 1   backupManga:            List<BackupManga>
├── 2   backupCategories:       List<BackupCategory>   (manga categories)
├── 101 backupSources:          List<BackupSource>      (manga source registry)
├── 104 backupPreferences:      List<BackupPreference>  (app prefs)
├── 105 backupSourcePreferences:List<BackupSourcePreferences>
├── 106 backupMangaExtensionRepo: List<BackupExtensionRepos>
│
│  ── Aniyomi-specific (500+) ─────────────────────────
├── 500 isLegacy:               Boolean (legacy-detection flag)
├── 501 backupAnime:            List<BackupAnime>
├── 502 backupAnimeCategories:  List<BackupCategory>
├── 503 backupAnimeSources:     List<BackupAnimeSource>
├── 504 backupExtensions:       List<BackupExtension>
├── 505 backupAnimeExtensionRepo: List<BackupExtensionRepos>
└── 506 backupCustomButton:     List<BackupCustomButtons>
```

Each `BackupManga` / `BackupAnime` nests its own children:

```
BackupManga                       BackupAnime
├── source, url, title, author…   ├── source, url, title, author…
├── 16  chapters: [BackupChapter] ├── 16  episodes: [BackupEpisode]
├── 17  categories: [Long]        ├── 17  categories: [Long]
├── 18  tracking: [BackupTracking]├── 18  tracking: [BackupAnimeTracking]
├── 104 history: [BackupHistory]  ├── 104 history: [BackupAnimeHistory]
├── 108 excludedScanlators        ├── 500 backgroundUrl
└── 109 version                   ├── 502-507 parentId/id/seasonFlags/
│                                     seasonNumber/seasonSourceOrder/fetchType
                                  └── 109 version
```

> The `version`, `lastModifiedAt`, and `favoriteModifiedAt` fields exist to
> support the optional library-sync feature; the restorer uses them to merge
> rather than blindly overwrite newer local data.

## The dual manga/anime backup

There is **one combined `.tachibk`**, not separate manga and anime files. The
manga and anime halves live as parallel lists inside the same `Backup`
message (fields `1`/`501`). Both are always written on every backup. The
restore path, however, lets the user opt in/out per category
(`RestoreOptions.libraryEntries` covers both sides; there is no per-side
toggle in the UI today).

This combined-file approach has one important consequence: **a Tachiyomi or
Mihon install can read the manga half of an Aniyomi backup** (because the
manga fields use the same proto numbers), but not vice-versa.

## The `BackupCreator` engine

`data/backup/create/BackupCreator.kt` is the orchestrator. It is constructed
with `isAutoBackup: Boolean` so it knows whether to manage the rolling file
history. It delegates the actual data extraction to **eleven per-section
creators** in `data/backup/create/creators/`:

| Creator | Produces |
|---|---|
| `MangaBackupCreator` | `List<BackupManga>` (with chapters, categories, tracking, history) |
| `AnimeBackupCreator` | `List<BackupAnime>` (with episodes, categories, tracking, history) |
| `MangaCategoriesBackupCreator` | `List<BackupCategory>` (manga side) |
| `AnimeCategoriesBackupCreator` | `List<BackupCategory>` (anime side) |
| `MangaSourcesBackupCreator` | `List<BackupSource>` (deduped source-id↔name map for manga) |
| `AnimeSourcesBackupCreator` | `List<BackupAnimeSource>` (same for anime) |
| `PreferenceBackupCreator` | `List<BackupPreference>` + `List<BackupSourcePreferences>` |
| `MangaExtensionRepoBackupCreator` | Manga extension-repo URLs |
| `AnimeExtensionRepoBackupCreator` | Anime extension-repo URLs |
| `CustomButtonBackupCreator` | Custom MPV Lua buttons |
| `ExtensionsBackupCreator` | `List<BackupExtension>` (the actual APK bytes; opt-in) |

### What `BackupCreator.backup()` does, end-to-end

```
                  ┌──── isAutoBackup? ────┐
                  │ yes                   │ no
                  ▼                       ▼
  Prune dir to MAX_AUTO_BACKUPS=4    UniFile.fromUri(userPickedUri)
  Create file named by getFilename()
                  │
                  ▼
  Fetch favorites (+ read-but-not-favorited if readEntries option set)
  Run all enabled creators  ──► Backup(...)
  ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
  Gzip + write to file
                  │
                  ▼
  BackupFileValidator.validate(fileUri)   ◄── re-decode & check sources/trackers
                  │
                  ▼
  (auto only) backupPreferences.lastAutoBackupTimestamp().set(now)
  return fileUri.toString()
```

Notable behaviours:

- **`MAX_AUTO_BACKUPS = 4`** — auto-backup keeps at most four rolling files;
  the oldest is deleted before a new one is written.
- **`BackupFileValidator`** re-decodes the just-written file, walks
  `backupSources` / `backupAnimeSources`, and reports which sources are
  missing (i.e. the user hasn't installed the matching extension) and which
  trackers are not logged in. This is surfaced to the user *before* they
  trust the backup.
- **`readEntries` option** — when set, anime that are "watched but not in
  library" and manga that are "read but not in library" are also backed up.
  This is the "non-library entries" toggle in the UI.
- **`privateSettings`** — when set, source/app preferences flagged as private
  (e.g. containing tokens) are included. Off by default.

### `BackupOptions`

`create/BackupOptions.kt` is a 12-flag data class. The settings UI renders it
as three groups: `libraryOptions`, `settingsOptions`, `extensionOptions`. It
serialises to/from a `BooleanArray` of length 12 for WorkManager input data.

## The `BackupRestorer` engine

`data/backup/restore/BackupRestorer.kt` is the symmetric orchestrator. It is
constructed with `isSync: Boolean` (the same code path is reused by the
library-sync feature, which is just a "restore from a remote backup" with
different progress strings).

### Restore flow

```
BackupRestoreJob.start(uri, options)
   │
   ▼
BackupRestorer.restore(uri, options)
   │
   ▼
BackupDecoder.decode(uri) ──► Backup
   │  (handles gzip magic 0x1f8b, legacy detection, JSON rejection)
   ▼
Build restoreAmount = sum of items to process (for progress %)
coroutineScope {
   restoreCategories(anime + manga)         ── launch
   restoreAppPreferences                    ── launch
   restoreSourcePreferences                 ── launch
   restoreAnime(backupAnime)                ── launch  (sorted: new entries first)
   restoreManga(backupManga)                ── launch  (sorted: new entries first)
   restoreExtensionRepos(anime + manga)     ── launch
   restoreCustomButtons                     ── launch
   restoreExtensions                        ── launch
}
writeErrorLog()  ──► aniyomi_restore_error.txt in cache
notifier.showRestoreComplete(time, errorCount, logPath, logName, isSync)
```

Each restorer in `data/backup/restore/restorers/` is a small class with an
`operator fun invoke` (or `restore(...)`) so it can be called like a
function:

| Restorer | What it does |
|---|---|
| `MangaRestorer` | Upserts manga + chapters + history + tracks + excluded-scanlators per entry; sorts by "already in DB" then `lastModifiedAt` so brand-new entries restore first. Wraps each entry in a single SQLDelight transaction. |
| `AnimeRestorer` | Same, but also handles the **seasons** parent/child relationship (`parentId`, `seasonNumber`, `fetchType`). |
| `MangaCategoriesRestorer` / `AnimeCategoriesRestorer` | Re-creates categories by name, preserving order & flags. |
| `PreferenceRestorer` | Writes app & source preferences back; also re-arms `MangaLibraryUpdateJob.setupTask`, `AnimeLibraryUpdateJob.setupTask`, and `BackupCreateJob.setupTask` so the periodic schedules survive the restore. |
| `MangaExtensionRepoRestorer` / `AnimeExtensionRepoRestorer` | Re-registers extension repo URLs (does **not** fetch them; that happens later via the extension-update subsystem). |
| `CustomButtonRestorer` | Re-inserts custom MPV Lua buttons into the `custom_buttons` table. |
| `ExtensionsRestorer` | Re-installs extension APKs whose bytes are embedded in the backup (only present if the user enabled the `extensions` option at backup time). Uses the standard extension-install flow. |

Errors during restore are **not** fatal: each per-entry try/catch appends to
`errors: MutableList<Pair<Date, String>>`, the loop continues, and at the end
an `aniyomi_restore_error.txt` file is written to cache and surfaced via the
"Show errors" action on the completion notification.

### `RestoreOptions`

`restore/RestoreOptions.kt` is a 7-flag data class (no per-chapter/history
granularity on restore — those come back with the library entries). Like
`BackupOptions` it serialises to a `BooleanArray` for WorkManager.

## Auto-backup

Auto-backup is a **periodic WorkManager job**, not an on-app-close hook.
`BackupCreateJob.setupTask()` registers a `PeriodicWorkRequestBuilder` with
tag `"BackupCreator"`:

```kotlin
val request = PeriodicWorkRequestBuilder<BackupCreateJob>(
    interval.toLong(), TimeUnit.HOURS,   // 6, 12, 24, 48, or 168 (weekly)
    10,          TimeUnit.MINUTES,       // flex period
)
    .setBackoffCriteria(EXPONENTIAL, 10, TimeUnit.MINUTES)
    .setConstraints(Constraints(requiresBatteryNotLow = true))
    .setInputData(workDataOf(IS_AUTO_BACKUP_KEY to true))
    .build()
workManager.enqueueUniquePeriodicWork("BackupCreator", UPDATE, request)
```

Interval is read from `BackupPreferences.backupInterval()` (default 12 h; 0
means off). When the user changes the interval in Settings → Data, the
setting's `onValueChanged` calls `BackupCreateJob.setupTask(context, it)`,
which enqueues with `UPDATE` so the existing periodic work is rescheduled
without duplicates.

`setupTask()` is also re-called from:

- `PreferenceRestorer.restoreApp()` — after restoring app prefs the periodic
  schedule is re-armed with the restored interval.
- `mihon.core.migration.migrations.SetupBackupCreateMigration` — runs on every
  app upgrade (`Migration.ALWAYS`) to make sure the periodic work survives.

There is **no "backup on app close"** hook. The two triggers are:

1. The periodic WorkManager job (auto).
2. `BackupCreateJob.startNow()` from `CreateBackupScreen` (manual).

## Manual backup / restore UI

The UI lives under
`app/src/main/java/eu/kanade/presentation/more/settings/screen/data/`:

- `CreateBackupScreen.kt` — picker for the options, SAF picker for the output
  file, calls `BackupCreateJob.startNow(context, uri, options)`.
- `RestoreBackupScreen.kt` — picker for the input file (via
  `ActivityResultContracts.GetContent` with `*/*`), shows the
  `BackupFileValidator` results, calls `BackupRestoreJob.start(context, uri,
  options)`.

Both screens are reachable from Settings → Data. See the end-to-end flow in
[`../05-key-flows/backup-flow.md`](../05-key-flows/backup-flow.md).

## Where backups live on disk

Auto-backups go to the user-picked SAF directory, exposed via
`StorageManager.getAutomaticBackupsDirectory()` which is the `autobackup/`
subdirectory of the configured base storage location. Manual backups go to
wherever the SAF picker was pointed. See
[`storage-and-cache.md`](storage-and-cache.md) for the full on-disk layout.

The MIME type used when sharing a `.tachibk` is `application/x-protobuf+gzip`
(see `NotificationReceiver.shareFile`).

## Encryption

**There is no encryption.** A `.tachibk` is plain gzip + protobuf. The only
protection is the `privateSettings = false` default, which excludes any
preference flagged private (notably tracker tokens and source credentials)
from the backup. Users who want a private-settings-inclusive backup must
opt in explicitly in the backup-options sheet.

## Notifications

Backup & restore post on two channels (see
[`notifications.md`](notifications.md)):

- `CHANNEL_BACKUP_RESTORE_PROGRESS` (`IMPORTANCE_LOW`, ongoing) — `ID_BACKUP_PROGRESS` / `ID_RESTORE_PROGRESS`.
- `CHANNEL_BACKUP_RESTORE_COMPLETE` (`IMPORTANCE_HIGH`, silent) — `ID_BACKUP_COMPLETE` / `ID_RESTORE_COMPLETE`.

The restore progress notification has a **Cancel** action that broadcasts to
`NotificationReceiver.cancelRestorePendingBroadcast`, which calls
`BackupRestoreJob.stop(context)`. The complete notification for restore has a
**Show errors** action that opens the error-log file.

## Key files

| File | Role |
|---|---|
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt` | Orchestrates backup; assembles `Backup`, gzips, validates. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreateJob.kt` | WorkManager `CoroutineWorker` for both auto & manual backup; `setupTask()` registers the periodic job. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupOptions.kt` | 12-flag options data class + UI group definitions. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/create/creators/*.kt` | Eleven per-section creators (manga, anime, categories, sources, preferences, repos, custom buttons, extensions). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt` | Orchestrates restore; coroutineScope launches per-section restorers; writes error log. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestoreJob.kt` | WorkManager `CoroutineWorker`; `start()` / `stop()` / `isRunning()`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/RestoreOptions.kt` | 7-flag restore options. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/*.kt` | Per-section restorers (MangaRestorer, AnimeRestorer, categories, preferences, repos, custom buttons, extensions). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupDecoder.kt` | Detects gzip magic 0x1f8b, rejects JSON, decodes protobuf; routes legacy backups through `LegacyBackup`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupDetector.kt` | Minimal protobuf probe to distinguish legacy Aniyomi backups from Mihon backups. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupFileValidator.kt` | Re-decodes a backup and reports missing sources & logged-out trackers. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupNotifier.kt` | Progress / error / complete notifications for both backup and restore. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt` | Top-level protobuf schema + `LegacyBackup` adapter. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupManga.kt` | Manga schema (chapters, categories, tracking, history, excluded scanlators, version). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupAnime.kt` | Anime schema (episodes, categories, tracking, history, season fields, fetchType, background). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupChapter.kt` / `BackupEpisode.kt` | Per-chapter / per-episode schema. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupCategory.kt` | Category schema (shared manga/anime). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupTracking.kt` / `BackupAnimeTracking.kt` | Tracker bindings. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupHistory.kt` / `BackupAnimeHistory.kt` | Read/watch history (url, readAt, readDuration). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupSource.kt` / `BackupAnimeSource.kt` | Source-id↔name registry. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupPreference.kt` | App preference entries. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupExtensionRepos.kt` | Extension-repo URL entries. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupExtension.kt` | Optional embedded extension APK (`pkgName` + `apk: ByteArray`). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupCustomButtons.kt` | Custom MPV Lua button entries. |
| `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/screen/data/CreateBackupScreen.kt` | Manual backup UI. |
| `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/screen/data/RestoreBackupScreen.kt` | Manual restore UI. |
| `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt` | Auto-backup interval setting + entry points to create/restore. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/backup/service/BackupPreferences.kt` | `backupInterval`, `lastAutoBackupTimestamp`, `backupFlags`. |
| `../ANIYOMI/app/src/main/java/mihon/core/migration/migrations/SetupBackupCreateMigration.kt` | Re-arms the periodic job on every app upgrade. |

## See also

- [`../05-key-flows/backup-flow.md`](../05-key-flows/backup-flow.md) — end-to-end create/restore user journey.
- [`storage-and-cache.md`](storage-and-cache.md) — where `.tachibk` files live, the SAF directory layout, `StorageManager`.
- [`notifications.md`](notifications.md) — the `CHANNEL_BACKUP_RESTORE_*` channels and the `BackupNotifier`.
- [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md) — how `BackupPreferences` is wired.
- [`../04-data-models/database-schema.md`](../04-data-models/database-schema.md) — the tables the restorers write into.
- [`trackers.md`](trackers.md) — what `BackupTracking` / `BackupAnimeTracking` map to.
- [`../02-modules/data.md`](../02-modules/data.md) — the dual manga/anime schemas that the restorers target.
