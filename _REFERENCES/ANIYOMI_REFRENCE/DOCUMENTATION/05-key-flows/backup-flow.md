# 05-key-flows / Create & restore a backup

> Trace a backup from its trigger (manual SAF picker OR the periodic
> WorkManager job) through `BackupCreator` → eleven per-section creators
> → `Backup` protobuf → gzip → `.tachibk` file. Then trace a restore:
> SAF pick → `BackupRestorer` → `BackupDecoder` → per-section restorers
> upsert into the dual SQLDelight DBs → `PreferenceRestorer` re-arms the
> library-update and backup jobs.

A backup is the user's escape hatch: phone lost, ROM flash, migrating
from Tachiyomi/Mihon, or moving between Aniyomi forks. Aniyomi's backup
engine captures almost everything that is not a downloaded file or an
extension APK (those are too big and machine-specific), and packs the
rest into one self-describing file. See
[`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md)
for the full subsystem deep dive; this doc focuses on the end-to-end
flows.

## The `.tachibk` format

A `.tachibk` file is a **gzipped Protocol Buffers** payload. The schema
is declared in pure Kotlin with `@ProtoNumber(n)` annotations (no `.proto`
file in the repo). The legacy JSON format is detected only to throw a
friendly error. The top-level `Backup` message has fields 1–106 for
manga + app prefs, and fields 500–506 for Aniyomi-specific values
(anime, anime categories, anime sources, extensions, anime extension
repos, custom buttons). The anime block is deliberately bumped to 500+
so a Tachiyomi/Mihon reader that doesn't know about anime can still
parse the manga half.

There is **no encryption**. The only privacy control is the
`privateSettings = false` default, which excludes any preference flagged
private (notably tracker tokens and source credentials).

## Create flow — overview

```
TRIGGER 1: periodic WorkManager job (auto)
   BackupCreateJob.setupTask(context, interval)
      └─ PeriodicWorkRequestBuilder<BackupCreateJob>(interval HOURS, 10 MIN)
            .setConstraints(requiresBatteryNotLow = true)
            .setBackoffCriteria(EXPONENTIAL, 10 MIN)
            .setInputData(IS_AUTO_BACKUP_KEY = true)
            .enqueueUniquePeriodicWork("BackupCreator", UPDATE)
   Called from:
   - Settings → Data → Auto-backup interval onChange
   - PreferenceRestorer.restoreApp()  (re-arm after a restore)
   - SetupBackupCreateMigration        (re-arm on every app upgrade)

TRIGGER 2: manual (user)
   CreateBackupScreen  ── SAF picker ──►  BackupCreateJob.startNow(context, uri, options)
      └─ OneTimeWorkRequestBuilder<BackupCreateJob>
            .setInputData(IS_AUTO_BACKUP_KEY = false, LOCATION_URI_KEY = uri, OPTIONS_KEY = options)
            .enqueueUniqueWork("BackupCreator:manual", KEEP)

   ▼
BackupCreateJob.doWork()
   ├─ if (isAutoBackup && BackupRestoreJob.isRunning) Result.retry()
   ├─ uri = inputData.locationUri ?: storageManager.getAutomaticBackupsDirectory().uri
   ├─ setForegroundSafely()  ── progress notification on CHANNEL_BACKUP_RESTORE_PROGRESS
   └─ BackupCreator(context, isAutoBackup).backup(uri, options)
        │
        ▼
BackupCreator.backup(uri, options)
   ├─ if (isAutoBackup):
   │     ├─ dir = UniFile.fromUri(uri)
   │     ├─ dir.listFiles matching FILENAME_REGEX, sorted desc, drop(MAX_AUTO_BACKUPS - 1) = 3, delete
   │     └─ file = dir.createFile(getFilename())  ← xyz.jmir.tachiyomi.mi_YYYY-MM-DD_HH-mm.tachibk
   │   else:
   │     file = UniFile.fromUri(uri)
   │
   ├─ nonFavoriteAnime = if (options.readEntries) animeRepository.getWatchedAnimeNotInLibrary() else []
   ├─ backupAnime = animeBackupCreator(getAnimeFavorites.await() + nonFavoriteAnime, options)
   ├─ nonFavoriteManga = if (options.readEntries) mangaRepository.getReadMangaNotInLibrary() else []
   ├─ backupManga = mangaBackupCreator(getMangaFavorites.await() + nonFavoriteManga, options)
   │
   ├─ backup = Backup(
   │     backupManga = backupManga,
   │     backupCategories = mangaCategoriesBackupCreator(),       ← if options.categories
   │     backupSources = mangaSourcesBackupCreator(backupManga),
   │     backupPreferences = preferenceBackupCreator.createApp(private = options.privateSettings),  ← if options.appSettings
   │     backupSourcePreferences = preferenceBackupCreator.createSource(private),                    ← if options.sourceSettings
   │     backupMangaExtensionRepo = mangaExtensionRepoBackupCreator(),                               ← if options.extensionRepoSettings
   │     isLegacy = false,
   │     backupAnime = backupAnime,
   │     backupAnimeCategories = animeCategoriesBackupCreator(),
   │     backupAnimeSources = animeSourcesBackupCreator(backupAnime),
   │     backupExtensions = extensionsBackupCreator(),                                              ← if options.extensions
   │     backupAnimeExtensionRepo = animeExtensionRepoBackupCreator(),
   │     backupCustomButton = customButtonBackupCreator(),                                          ← if options.customButton
   │ )
   │
   ├─ byteArray = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
   ├─ file.openOutputStream().sink().gzip().buffer().use { it.write(byteArray) }
   ├─ BackupFileValidator(context).validate(fileUri)   ← re-decode & check missing sources / trackers
   └─ if (isAutoBackup) backupPreferences.lastAutoBackupTimestamp().set(now)
   │
   ▼
BackupCreateJob.doWork returns Result.success()
   └─ (manual only) notifier.showBackupComplete(file)
```

## Create flow — step by step

### 1. Trigger

There are exactly two triggers, both WorkManager-based (there is no
"backup on app close" hook):

- **Auto** — `BackupCreateJob.setupTask(context, prefInterval?)` (in
  `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreateJob.kt`)
  registers a `PeriodicWorkRequestBuilder<BackupCreateJob>` with interval
  6 / 12 / 24 / 48 / 168 hours (0 = disabled), a 10-minute flex window,
  `requiresBatteryNotLow = true`, and exponential backoff. Work name is
  `"BackupCreator"`. Called from:
  - `Settings → Data → Auto-backup interval` `onValueChanged`.
  - `PreferenceRestorer.restoreApp()` after a restore (to re-arm with
    the restored interval).
  - `SetupBackupCreateMigration` (a `Migration.ALWAYS` migration that
    runs on every app upgrade — see step 4 of [`app-startup.md`](app-startup.md)).
- **Manual** — `BackupCreateJob.startNow(context, uri, options)` from
  `CreateBackupScreen`
  (`../ANIYOMI/presentation/.../more/settings/screen/data/CreateBackupScreen.kt`)
  after the user picks an output file via the SAF picker. Enqueued as
  `"BackupCreator:manual"` with `ExistingWorkPolicy.KEEP` (so a second
  tap doesn't queue a duplicate).

### 2. `BackupCreateJob.doWork`

```kotlin
override suspend fun doWork(): Result {
    val isAutoBackup = inputData.getBoolean(IS_AUTO_BACKUP_KEY, true)
    if (isAutoBackup && BackupRestoreJob.isRunning(context)) return Result.retry()
    val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
        ?: getAutomaticBackupLocation()    // storageManager.getAutomaticBackupsDirectory()?.uri
        ?: return Result.failure()
    setForegroundSafely()
    val options = inputData.getBooleanArray(OPTIONS_KEY)?.let { BackupOptions.fromBooleanArray(it) }
        ?: BackupOptions()
    return try {
        val location = BackupCreator(context, isAutoBackup).backup(uri, options)
        if (!isAutoBackup) notifier.showBackupComplete(UniFile.fromUri(context, location.toUri())!!)
        Result.success()
    } catch (e: Exception) {
        if (!isAutoBackup) notifier.showBackupError(e.message)
        Result.failure()
    } finally {
        context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)
    }
}
```

`setForegroundSafely()` promotes the worker to a foreground service with
a progress notification on `CHANNEL_BACKUP_RESTORE_PROGRESS` (the
`getForegroundInfo()` override returns that notification + the
`FOREGROUND_SERVICE_TYPE_DATA_SYNC` flag on Android Q+).

### 3. `BackupCreator.backup` — the orchestrator

`BackupCreator` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt`)
is constructed with `isAutoBackup: Boolean` (so it knows whether to
manage the rolling file history) and a `BackupOptions` data class. It
delegates the actual data extraction to **eleven per-section creators**
in `data/backup/create/creators/`:

| Creator | Produces |
|---|---|
| `MangaBackupCreator` | `List<BackupManga>` (with chapters, categories, tracking, history) |
| `AnimeBackupCreator` | `List<BackupAnime>` (with episodes, categories, tracking, history, seasons) |
| `MangaCategoriesBackupCreator` | `List<BackupCategory>` (manga side) |
| `AnimeCategoriesBackupCreator` | `List<BackupCategory>` (anime side) |
| `MangaSourcesBackupCreator` | `List<BackupSource>` (deduped source-id↔name map) |
| `AnimeSourcesBackupCreator` | `List<BackupAnimeSource>` |
| `PreferenceBackupCreator` | `List<BackupPreference>` + `List<BackupSourcePreferences>` |
| `MangaExtensionRepoBackupCreator` | Manga extension-repo URLs |
| `AnimeExtensionRepoBackupCreator` | Anime extension-repo URLs |
| `CustomButtonBackupCreator` | Custom MPV Lua buttons |
| `ExtensionsBackupCreator` | `List<BackupExtension>` (the actual APK bytes; opt-in) |

`BackupCreator.backup(uri, options)`:

1. **File creation**:
   - Auto: prune dir to `MAX_AUTO_BACKUPS = 4` rolling files (deletes
     older files matching `FILENAME_REGEX`), then `dir.createFile(getFilename())`
     where `getFilename()` returns
     `xyz.jmir.tachiyomi.mi_YYYY-MM-DD_HH-mm.tachibk`.
   - Manual: `UniFile.fromUri(uri)` (the user's SAF pick).
2. **Fetch favorites** — `getMangaFavorites.await()` +
   `getAnimeFavorites.await()`. If `options.readEntries` is on, also
   include read-but-not-favorited manga / watched-but-not-favorited
   anime (the "non-library entries" toggle).
3. **Run all enabled creators** — each creator is gated on its
   `BackupOptions` flag, so e.g. `backupAnimeCategories` returns
   `emptyList()` if `options.categories == false`. The result is a
   single `Backup(...)` instance.
4. **Serialize + gzip**:
   ```kotlin
   val byteArray = parser.encodeToByteArray(Backup.serializer(), backup)
   file.openOutputStream().sink().gzip().buffer().use { it.write(byteArray) }
   ```
   `parser` is `kotlinx.serialization.protobuf.ProtoBuf`.
5. **Validate** — `BackupFileValidator(context).validate(fileUri)`
   re-decodes the just-written file, walks `backupSources` /
   `backupAnimeSources`, and reports which sources are missing (i.e.
   the user hasn't installed the matching extension) and which trackers
   are not logged in. This is surfaced to the user *before* they trust
   the backup.
6. **(Auto only)** `backupPreferences.lastAutoBackupTimestamp().set(now)`.

### 4. `BackupOptions`

`create/BackupOptions.kt` is a 12-flag data class. The settings UI
renders it as three groups: `libraryOptions`, `settingsOptions`,
`extensionOptions`. It serialises to/from a `BooleanArray` of length 12
for WorkManager input data.

| Group | Flags |
|---|---|
| library | `libraryEntries`, `categories`, `readEntries` (read-but-not-favorited), `customButton` |
| settings | `appSettings`, `sourceSettings`, `privateSettings` (off by default — excludes tokens) |
| extension | `extensionRepoSettings`, `extensions` (embed APK bytes; opt-in) |

## Restore flow — overview

```
TRIGGER: manual (user)
   RestoreBackupScreen  ── SAF GetContent("*/*") picker ──►  BackupFileValidator.validate(uri)
        │                                                       (re-decode + report missing sources / trackers)
        │
        ▼
   BackupRestoreJob.start(context, uri, options)
      └─ OneTimeWorkRequestBuilder<BackupRestoreJob>
            .setInputData(URI_KEY, OPTIONS_KEY)
            .enqueueUniqueWork("BackupRestore", REPLACE)

   ▼
BackupRestoreJob.doWork()
   ├─ setForegroundSafely()  ── progress notification with Cancel action
   └─ BackupRestorer(context, notifier, isSync = false).restore(uri, options)
        │
        ▼
BackupRestorer.restore(uri, options)
   ├─ backup = BackupDecoder(context).decode(uri)
   │     ├─ detect gzip magic 0x1f8b → gunzip
   │     ├─ detect legacy JSON → throw "use older app version"
   │     ├─ detect legacy Aniyomi field numbering → LegacyBackup adapter
   │     └─ ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
   │
   ├─ restoreAmount = sum of items to process (for progress %)
   ├─ coroutineScope {
   │     launch restoreCategories(anime + manga)               ← if options.categories
   │     launch restoreAppPreferences                          ← if options.appSettings
   │     launch restoreSourcePreferences                       ← if options.sourceSettings
   │     launch restoreAnime(backupAnime)                      ← if options.libraryEntries (sorted: new entries first)
   │     launch restoreManga(backupManga)                      ← if options.libraryEntries (sorted: new entries first)
   │     launch restoreExtensionRepos(anime + manga)           ← if options.extensionRepoSettings
   │     launch restoreCustomButtons                           ← if options.customButtons
   │     launch restoreExtensions                              ← if options.extensions
   │  }
   ├─ writeErrorLog()  ── aniyomi_restore_error.txt in cache
   └─ notifier.showRestoreComplete(time, errorCount, logPath, logName, isSync)
```

## Restore flow — step by step

### 1. Trigger

Restore is always manual. From `RestoreBackupScreen`
(`../ANIYOMI/presentation/.../more/settings/screen/data/RestoreBackupScreen.kt`),
the user picks a `.tachibk` file via
`ActivityResultContracts.GetContent` with MIME `*/*`. Before scheduling
the job, the screen runs `BackupFileValidator.validate(uri)` to show
the user which sources are missing and which trackers are logged out —
the user can then go install extensions / log in before the restore
rather than discovering the gaps afterwards.

On confirm, `BackupRestoreJob.start(context, uri, options)`
(`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestoreJob.kt`)
enqueues a `OneTimeWorkRequestBuilder<BackupRestoreJob>` with
`ExistingWorkPolicy.REPLACE` (so a re-pick replaces the in-flight job).

### 2. `BackupDecoder.decode(uri)`

`BackupDecoder` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupDecoder.kt`):

1. Opens the file, peeks the first two bytes. If they are `0x1f 0x8b`
   (gzip magic), wraps the stream in a `GzipSource`.
2. Reads the first chunk and runs `BackupDetector` — a minimal protobuf
   probe that distinguishes:
   - Modern Aniyomi backups (have the `isLegacy = false` flag at proto
     field 500).
   - Legacy Aniyomi backups (pre-field-renumbering) — routed through
     `LegacyBackup`, a `Backup`-shaped adapter that re-maps the old
     field numbers to the new ones.
   - Mihon/Tachiyomi backups (no anime fields) — decoded directly.
3. Rejects JSON backups with a friendly error.
4. `ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)` → `Backup`.

### 3. `BackupRestorer.restore` — parallel per-section restorers

`BackupRestorer` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt`)
is the symmetric orchestrator. It is constructed with `isSync: Boolean`
(the same code path is reused by the optional library-sync feature,
which is just a "restore from a remote backup" with different progress
strings).

After decoding the backup, it computes `restoreAmount` (the total item
count for progress reporting) and launches every enabled restorer in
parallel inside a single `coroutineScope`:

| Restorer | What it does |
|---|---|
| `MangaRestorer` | Upserts manga + chapters + history + tracks + excluded-scanlators per entry; sorts by "already in DB" then `lastModifiedAt` so brand-new entries restore first. Wraps each entry in a single SQLDelight transaction. |
| `AnimeRestorer` | Same, but also handles the **seasons** parent/child relationship (`parentId`, `seasonNumber`, `fetchType`). |
| `MangaCategoriesRestorer` / `AnimeCategoriesRestorer` | Re-creates categories by name, preserving order & flags. |
| `PreferenceRestorer` | Writes app & source preferences back; also **re-arms `MangaLibraryUpdateJob.setupTask`, `AnimeLibraryUpdateJob.setupTask`, and `BackupCreateJob.setupTask`** so the periodic schedules survive the restore. |
| `MangaExtensionRepoRestorer` / `AnimeExtensionRepoRestorer` | Re-registers extension repo URLs (does **not** fetch them; that happens later via the extension-update subsystem). |
| `CustomButtonRestorer` | Re-inserts custom MPV Lua buttons into the `custom_buttons` table. |
| `ExtensionsRestorer` | Re-installs extension APKs whose bytes are embedded in the backup (only present if the user enabled the `extensions` option at backup time). Uses the standard extension-install flow. |

Each per-entry try/catch appends to `errors: MutableList<Pair<Date,
String>>`; the loop continues, and at the end an
`aniyomi_restore_error.txt` file is written to cache and surfaced via
the "Show errors" action on the completion notification.

### 4. `PreferenceRestorer` — re-arming the periodic jobs

```kotlin
suspend fun restoreApp(preferences: List<BackupPreference>, categories: List<BackupCategory>?) {
    val preferenceStore = Injekt.get<PreferenceStore>()
    restorePreferences(preferences, preferenceStore)
    AnimeLibraryUpdateJob.setupTask(context)
    MangaLibraryUpdateJob.setupTask(context)
    BackupCreateJob.setupTask(context)
}
```

This is critical: the periodic WorkManager jobs (library update, auto
backup) survive process death but **not** the user clearing the app's
WorkManager database or the user uninstalling+reinstalling the app. So
after a restore, `PreferenceRestorer` reads the restored
`autoUpdateInterval` and `backupInterval` preferences and re-arms the
jobs with the restored values. Without this, the user's library-update
and auto-backup schedules would be silently disabled after a restore.

## Sequence diagram

```
CREATE (auto):
   WorkManager fires BackupCreateJob (periodic, every N hours)
      └─ BackupCreator(context, isAutoBackup = true).backup(autobackupDir, options)
           ├─ prune dir to MAX_AUTO_BACKUPS = 4
           ├─ create file: <appid>_YYYY-MM-DD_HH-mm.tachibk
           ├─ getMangaFavorites.await() + getAnimeFavorites.await()
           │   (+ read-but-not-favorited if options.readEntries)
           ├─ run all enabled creators  ──► Backup(...)
           ├─ ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
           ├─ file.sink().gzip().buffer().write(byteArray)
           ├─ BackupFileValidator.validate(fileUri)
           └─ backupPreferences.lastAutoBackupTimestamp().set(now)

CREATE (manual):
   CreateBackupScreen  ── SAF picker ──► BackupCreateJob.startNow(ctx, uri, options)
      └─ (same BackupCreator.backup, but isAutoBackup = false; showBackupComplete on success)

RESTORE:
   RestoreBackupScreen  ── SAF GetContent ──► BackupFileValidator.validate(uri)
      └─ BackupRestoreJob.start(ctx, uri, options)
           └─ BackupRestorer(ctx, notifier, isSync = false).restore(uri, options)
                ├─ BackupDecoder.decode(uri)  ── gunzip + ProtoBuf.decode → Backup
                ├─ coroutineScope {
                │     launch restoreCategories(anime + manga)
                │     launch restoreAppPreferences  ── also re-arms *LibraryUpdateJob + BackupCreateJob
                │     launch restoreSourcePreferences
                │     launch restoreAnime(backupAnime)  ── per-entry upsert (sorted new-first)
                │     launch restoreManga(backupManga)  ── per-entry upsert (sorted new-first)
                │     launch restoreExtensionRepos(anime + manga)
                │     launch restoreCustomButtons
                │     launch restoreExtensions   ── re-install embedded APKs
                │  }
                ├─ writeErrorLog()  ── aniyomi_restore_error.txt in cache
                └─ notifier.showRestoreComplete(time, errorCount, logPath, logName)
```

## See also

- [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) — the full backup/restore subsystem deep dive (the protobuf schema, the per-section creators/restorers, the validator, the notifications).
- [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md) — where `.tachibk` files live, the SAF directory layout, `StorageManager.getAutomaticBackupsDirectory()`.
- [`../03-subsystems/notifications.md`](../03-subsystems/notifications.md) — the `CHANNEL_BACKUP_RESTORE_*` channels and the `BackupNotifier`.
- [`app-startup.md`](app-startup.md) — the `SetupBackupCreateMigration` that re-arms the periodic backup on every app upgrade.
- [`../03-subsystems/updates.md`](../03-subsystems/updates.md) — the `*LibraryUpdateJob` that `PreferenceRestorer` re-arms.
- [`track-progress.md`](track-progress.md) — what the `BackupTracking` / `BackupAnimeTracking` rows map to (track bindings are backed up; OAuth tokens are not).
