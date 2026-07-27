# Backup & Restore

Aniyomi's user-data export/import pipeline: serializes library + history + tracks + categories + preferences + extension repos + (optionally) APKs into a gzipped Protocol-Buffers `.tachibk` file, and restores them back into the two SQLDelight databases.

## Where it lives

- Module root: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/backup/`
  - Top-level helpers: `BackupDecoder.kt`, `BackupDetector.kt`, `BackupFileValidator.kt`, `BackupNotifier.kt`
  - Proto schema (Kotlin data classes annotated with `@ProtoNumber`): `models/`
    - Root container: `models/Backup.kt` (modern), `models/Backup.kt` also defines `LegacyBackup` (older combined layout)
    - Legacy/older container: `full/models/Backup.kt`, `full/models/BackupPreference.kt` (unused by current creator, kept for decode path)
    - Entry models: `BackupAnime.kt`, `BackupManga.kt`, `BackupEpisode.kt`, `BackupChapter.kt`, `BackupCategory.kt`, `BackupHistory.kt`, `BackupAnimeHistory.kt`, `BackupTracking.kt`, `BackupAnimeTracking.kt`, `BackupSource.kt`, `BackupAnimeSource.kt`, `BackupPreference.kt`, `BackupExtension.kt`, `BackupExtensionRepos.kt`, `BackupExtensionPreferences.kt`, `BackupCustomButtons.kt`
  - Create pipeline: `create/`
    - `BackupCreator.kt` (orchestrator), `BackupCreateJob.kt` (WorkManager entrypoint), `BackupOptions.kt` (per-section toggles)
    - Per-section creators in `create/creators/`: `AnimeBackupCreator`, `MangaBackupCreator`, `AnimeCategoriesBackupCreator`, `MangaCategoriesBackupCreator`, `AnimeSourcesBackupCreator`, `MangaSourcesBackupCreator`, `AnimeExtensionRepoBackupCreator`, `MangaExtensionRepoBackupCreator`, `ExtensionsBackupCreator` (both anime+manga), `PreferenceBackupCreator` (both), `CustomButtonBackupCreator`
  - Restore pipeline: `restore/`
    - `BackupRestorer.kt` (orchestrator), `BackupRestoreJob.kt` (WorkManager entrypoint), `RestoreOptions.kt` (per-section toggles)
    - Per-section restorers in `restore/restorers/`: `AnimeRestorer`, `MangaRestorer`, `AnimeCategoriesRestorer`, `MangaCategoriesRestorer`, `AnimeExtensionRepoRestorer`, `MangaExtensionRepoRestorer`, `ExtensionsRestorer`, `PreferenceRestorer`, `CustomButtonRestorer`
- Domain-side preferences: `REFERENCE/domain/src/main/java/tachiyomi/domain/backup/service/`
  - `BackupPreferences.kt` — backup interval, last auto-backup timestamp, legacy backup-flags set
  - `PreferenceValues.kt` — flag-string constants `FLAG_CATEGORIES`, `FLAG_CHAPTERS`, `FLAG_HISTORY`, `FLAG_TRACK`, `FLAG_SETTINGS`, `FLAG_EXT_SETTINGS`, `FLAG_EXTENSIONS` (legacy bitmask still exposed; modern `BackupOptions`/`RestoreOptions` are boolean arrays)
- DI wiring of `ProtoBuf` singleton: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` (registers the default `kotlinx.serialization.protobuf.ProtoBuf` instance)

There is no `.proto` file in the source tree. The on-wire schema is defined entirely by `@ProtoNumber(...)` annotations on Kotlin `@Serializable` data classes, serialized by `kotlinx.serialization.protobuf`.

## What it does

The backup subsystem is the user-data portability layer for aniyomi. It can:

1. Export (create) a `.tachibk` file containing any subset of:
   - Library entries (anime favorites, manga favorites, plus optionally non-library "watched"/"read" entries).
   - Per-entry children: episodes/chapters (with seen/read, bookmark, fillermark, last-second-seen / last-page-read, dates, version), watch/read history, tracker rows.
   - Categories (anime and manga categories are stored as two separate `List<BackupCategory>`).
   - App preferences (typed `Int`/`Long`/`Float`/`String`/`Boolean`/`Set<String>` values, with optional exclusion of "private" keys).
   - Source preferences (per `ConfigurableSource` / `ConfigurableAnimeSource`).
   - Extension repo registry (anime repos and manga repos as two separate lists; each repo is `baseUrl`, `name`, `shortName`, `website`, `signingKeyFingerprint`).
   - Custom buttons (aniyomi-specific home-screen quick actions).
   - Installed extension APKs (optional, off by default; APK bytes are inlined into the backup as `BackupExtension(pkgName, apk: ByteArray)`).
   - Stub source metadata (`BackupSource`/`BackupAnimeSource`: name + sourceId) so the restorer can produce useful error messages for entries whose source is uninstalled.
2. Import (restore) a `.tachibk` file, selectively per section, into the live anime + manga SQLDelight databases.
3. Schedule automatic periodic backups via WorkManager (`BackupCreateJob.setupTask`), keeping at most 4 auto-backup files (`MAX_AUTO_BACKUPS = 4`) per the filename regex `${APPLICATION_ID}_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}.tachibk`.
4. Validate a backup file (post-create and pre-restore) for missing sources and unauthenticated trackers (`BackupFileValidator`).
5. Surface progress + completion through Android notifications (`BackupNotifier`) and write a timestamped error log to the cache dir (`aniyomi_restore_error.txt`) when restore errors occur.

## Key files & classes

### Top-level

| File / class | Role |
|---|---|
| `data/backup/models/Backup.kt` → `Backup` | Modern root protobuf container. Holds manga + anime fields, app/source prefs, ext repos, custom buttons. Proto field numbers below. |
| `data/backup/models/Backup.kt` → `LegacyBackup` | Older combined-layout container (anime fields at proto numbers 3, 4, 103, ...). `toBackup()` normalizes it into the modern `Backup` shape. Used by `BackupDecoder` when `BackupDetector.isLegacyBackup(...)` returns true. |
| `data/backup/full/models/Backup.kt` | Legacy "full" backup model (proto numbers 1-4 + 101-104). Kept for decode compatibility; not emitted by the current creator. |
| `BackupDecoder` | Reads the file, detects gzip magic `0x1f8b` and gunzips if present, rejects JSON-signatured files (`{}`, `{"`, `{\n`), then runs `BackupDetector.isLegacyBackup` to choose `LegacyBackup.serializer()` vs `Backup.serializer()` for `ProtoBuf.decodeFromByteArray`. |
| `BackupDetector` | Decodes only two fields (`@ProtoNumber(103) backupAnimeSources`, `@ProtoNumber(500) isLegacy`) to heuristically classify a byte array as "old aniyomi backup" (legacy = true AND anime-sources list non-empty) vs "mihon backup or new aniyomi backup". |
| `BackupFileValidator` | Post-decode validator; returns `Results(missingSources, missingTrackers)` by cross-referencing backup source IDs against `AnimeSourceManager` / `MangaSourceManager` and tracker `syncId`s against `TrackerManager` (checking `isLoggedIn`). Used by the create flow (`BackupCreator.backup` calls `validate(fileUri)` right after writing) and by restore UI to warn the user before import. |
| `BackupNotifier` | Notification builders for backup progress / complete / error and restore progress / complete / error. Owns the "show errors" action that opens `aniyomi_restore_error.txt`. |

### Create pipeline

| File / class | Role |
|---|---|
| `create/BackupCreateJob` | `CoroutineWorker`. Periodic (auto, `setupTask`, default 12 h interval from `BackupPreferences.backupInterval()`, requires battery not low) or one-shot (manual, `startNow`). Foreground notification + `FOREGROUND_SERVICE_TYPE_DATA_SYNC`. Bails to `Result.retry()` if `BackupRestoreJob.isRunning`. |
| `create/BackupCreator` | The orchestrator. Builds a `Backup` from 11 per-section creators, `ProtoBuf.encodeToByteArray(Backup.serializer(), backup)`, writes via `file.sink().gzip().buffer().write(byteArray)` (gzip-wrapped protobuf), then `BackupFileValidator.validate`. For auto-backups it lists the target directory, matches `FILENAME_REGEX`, sorts by name desc, and deletes everything past `MAX_AUTO_BACKUPS - 1` before creating the new file. Updates `BackupPreferences.lastAutoBackupTimestamp()` on auto-backup success. |
| `create/BackupOptions` | Boolean flags: `libraryEntries`, `categories`, `chapters`, `tracking`, `history`, `readEntries` (non-library watched/read), `appSettings`, `extensionRepoSettings`, `customButton`, `sourceSettings`, `privateSettings`, `extensions`. Serialized to/from a `BooleanArray` for WorkManager `workDataOf`. `canCreate()` requires at least one of the non-children flags. `chapters`/`tracking`/`history`/`readEntries` are subordinate to `libraryEntries` in the UI (`enabled = { it.libraryEntries }`). |
| `create/creators/AnimeBackupCreator` | Iterates favorites (+ watched non-library when `options.readEntries`); for each anime emits a `BackupAnime`, then conditionally attaches episodes (`backupEpisodeMapper`), category orders, tracks (`backupAnimeTrackMapper`), and history (mapped via `GetAnimeHistory` + episode URL lookup). |
| `create/creators/MangaBackupCreator` | Mirror of the above for manga; also writes `excludedScanlators` per manga. Uses `backupChapterMapper`, `backupMangaTrackMapper`, `BackupHistory`. |
| `create/creators/AnimeCategoriesBackupCreator` / `MangaCategoriesBackupCreator` | Lists non-system categories and maps them through `backupCategoryMapper`. |
| `create/creators/AnimeSourcesBackupCreator` / `MangaSourcesBackupCreator` | Distinct `source` IDs from the backed-up entries, resolves each through `SourceManager.getOrStub`, emits `BackupAnimeSource` / `BackupSource`. |
| `create/creators/AnimeExtensionRepoBackupCreator` / `MangaExtensionRepoBackupCreator` | Maps `mihon.domain.extensionrepo.{anime,manga}.model.ExtensionRepo` through `backupExtensionReposMapper`. |
| `create/creators/ExtensionsBackupCreator` | Reads APK bytes off disk via `PackageManager.getApplicationInfo(...).publicSourceDir` for every installed anime AND manga extension; emits `BackupExtension(pkgName, apk)`. Off by default (`options.extensions`). |
| `create/creators/PreferenceBackupCreator` | `createApp(includePrivatePreferences)` walks `PreferenceStore.getAll()`, skips `Preference.isAppState(key)` keys, filters private keys unless requested. `createSource(...)` iterates `ConfigurableAnimeSource` + `ConfigurableSource` and emits a `BackupSourcePreferences` per source with non-empty prefs. |
| `create/creators/CustomButtonBackupCreator` | Lists `CustomButton`s through `backupCustomButtonsMapper`. |

### Restore pipeline

| File / class | Role |
|---|---|
| `restore/BackupRestoreJob` | `CoroutineWorker`. Always one-shot (`start`); `ExistingWorkPolicy.KEEP` prevents concurrent restores. `SYNC_KEY` boolean lets the same job power a "sync" mode (same code path, different notification strings). Foreground service + cancel action. |
| `restore/BackupRestorer` | Orchestrator. `restoreFromFile` decodes via `BackupDecoder`, builds `restoreAmount` from the per-section counts, then `coroutineScope { ... }` launches parallel restore coroutines for categories, app prefs, source prefs, library entries (anime + manga concurrently), extension repos, custom buttons, extensions. Each section updates `restoreProgress` and pushes a notification. Per-entry failures are caught and appended to `errors: MutableList<Pair<Date, String>>` (formatted `"title [sourceName]: message"`); the job does not abort. At the end, `writeErrorLog()` writes `aniyomi_restore_error.txt` and `notifier.showRestoreComplete(time, errors.size, path, name, isSync)` notifies. |
| `restore/RestoreOptions` | Boolean flags: `libraryEntries`, `categories`, `appSettings`, `extensionRepoSettings`, `customButtons`, `sourceSettings`, `extensions`. Note: no `chapters`/`tracking`/`history` flags — those are restored unconditionally when `libraryEntries` is on. |
| `restore/restorers/AnimeRestorer` | Restores anime + seasons. `sortByNew` orders backup entries so already-existing-in-DB entries are restored first (by `(url in db) asc, lastModifiedAt desc`). Per anime: looks up existing by `(url, source)`, calls `restoreNewAnime` or `restoreExistingAnime` (uses `version` field on both backup and DB row to decide which side's metadata wins; merges via `copyFrom` preserving `favorite` OR, `initialized` OR). Then `restoreAnimeDetails` restores categories (matches by name across DB and backup), episodes (compares via `forComparison()` skipping id/animeId/dates/version; merges `seen`/`bookmark`/`fillermark`/`lastSecondSeen` with "DB wins if already seen"), tracking (`forComparison()` skip; `lastEpisodeSeen = max(db, backup)`), and history (`seenAt = max(backup, db)`). Whole anime+seasons restore runs in a single transaction (`handler.await(inTransaction = true) { ... }`). Season handling: `parentId` associates child `BackupAnime` rows with their parent; the parent is restored first, then each season is restored with `parentId = restoredAnime.id`. |
| `restore/restorers/MangaRestorer` | Mirror of `AnimeRestorer` for manga + chapters + `excludedScanlators`. No equivalent of seasons/parentId. |
| `restore/restorers/AnimeCategoriesRestorer` / `MangaCategoriesRestorer` | Inserts missing categories by name (skips if a same-named DB category exists), assigns `nextOrder` incrementally, sets `LibraryPreferences.categorizedDisplaySettings()` based on whether distinct `flags` values exist. |
| `restore/restorers/AnimeExtensionRepoRestorer` / `MangaExtensionRepoRestorer` | Inserts each repo; errors if a different signing key fingerprint is already registered for the same `baseUrl`, or if any other repo already has the same signing fingerprint. |
| `restore/restorers/ExtensionsRestorer` | For each `BackupExtension`, if the package isn't already installed, writes the APK to `cacheDir/<pkgName>.apk` and fires an `ACTION_VIEW` intent with MIME `application/vnd.android.package-archive` to trigger the system installer. |
| `restore/restorers/PreferenceRestorer` | Walks each `BackupPreference`; only restores if the live `PreferenceStore.getAll()[key]` is `null`-assignable to the matching type (e.g. `IntPreferenceValue` only writes if the current value is `Int?`). Special-cases `LibraryPreferences.DEFAULT_MANGA_CATEGORY_PREF_KEY` and `DEFAULT_ANIME_CATEGORY_PREF_KEY`: re-maps the backup's category-id value to the current DB's category-id by name lookup. Also special-cases `StringSetPreferenceValue` for `LibraryPreferences.categoryPreferenceKeys + DownloadPreferences.categoryPreferenceKeys`: re-maps the set of backup category IDs to current DB IDs by name. On app-prefs restore, also reschedules `AnimeLibraryUpdateJob`, `MangaLibraryUpdateJob`, and `BackupCreateJob`. |
| `restore/restorers/CustomButtonRestorer` | Inserts missing custom buttons by name (skips existing). Honors a single-favorite invariant (`dbHasFavorite`). |

### Proto schema (root `Backup`)

Modern `Backup` (`data/backup/models/Backup.kt`), field numbers:

| `@ProtoNumber` | Field | Type | Notes |
|---|---|---|---|
| 1 | `backupManga` | `List<BackupManga>` | Manga library entries. |
| 2 | `backupCategories` | `List<BackupCategory>` | Manga categories. |
| 101 | `backupSources` | `List<BackupSource>` | Manga stub-source metadata. (100 reserved for "broken" legacy source.) |
| 104 | `backupPreferences` | `List<BackupPreference>` | App prefs. |
| 105 | `backupSourcePreferences` | `List<BackupSourcePreferences>` | Source prefs. |
| 106 | `backupMangaExtensionRepo` | `List<BackupExtensionRepos>` | Manga extension repos. |
| 500 | `isLegacy` | `Boolean` | Detection flag. New backups write `false`. |
| 501 | `backupAnime` | `List<BackupAnime>` | Anime library entries. |
| 502 | `backupAnimeCategories` | `List<BackupCategory>` | Anime categories. |
| 503 | `backupAnimeSources` | `List<BackupAnimeSource>` | Anime stub-source metadata. |
| 504 | `backupExtensions` | `List<BackupExtension>` | APK bytes (anime + manga combined). |
| 505 | `backupAnimeExtensionRepo` | `List<BackupExtensionRepos>` | Anime extension repos. |
| 506 | `backupCustomButton` | `List<BackupCustomButtons>` | Custom home-screen buttons. |

Legacy `LegacyBackup` (same file) uses proto numbers 1-22 with anime fields interleaved at low numbers (3, 4, 103, ...). `toBackup()` translates it into the modern shape. See `## Anime vs manga` for the implications.

Per-entry `BackupAnime` (relevant subset): `source(1)`, `url(2)`, `title(3)`, `episodes(16)`, `categories(17)`, `tracking(18)`, `favorite(100)`, `episode_flags(101)`, `viewer_flags(103)`, `history(104)`, `updateStrategy(105)`, `lastModifiedAt(106)`, `favoriteModifiedAt(107)`, `version(109)`, then aniyomi-specific `backgroundUrl(500)`, `parentId(502)`, `id(503)`, `seasonFlags(504)`, `seasonNumber(505)`, `seasonSourceOrder(506)`, `fetchType(507)`.

Per-entry `BackupManga` mirrors the low-field layout: `source(1)`, `url(2)`, ..., `chapters(16)`, `categories(17)`, `tracking(18)`, `favorite(100)`, `chapterFlags(101)`, `viewer_flags(103)`, `history(104)`, `updateStrategy(105)`, `lastModifiedAt(106)`, `favoriteModifiedAt(107)`, `excludedScanlators(108)`, `version(109)`. No aniyomi-specific 500+ fields.

`BackupEpisode` adds aniyomi-specific `fillermark(501)`, `summary(502)`, `previewUrl(503)` on top of the mihon `BackupChapter` layout (`url(1)`, `name(2)`, `scanlator(3)`, `seen/read(4)`, `bookmark(5)`, `lastSecondSeen/lastPageRead(6)`, `dateFetch(7)`, `dateUpload(8)`, `episodeNumber/chapterNumber(9)`, `sourceOrder(10)`, `lastModifiedAt(11)`, `version(12)`, plus anime-only `totalSeconds(16)`).

`BackupTracking` (manga) and `BackupAnimeTracking` share identical field numbers (`syncId(1)`, `libraryId(2)`, `mediaIdInt(3, deprecated)`, `trackingUrl(4)`, `title(5)`, `lastChapterRead`/`lastEpisodeSeen(6)`, `totalChapters`/`totalEpisodes(7)`, `score(8)`, `status(9)`, started/finished dates (10/11), `private(12)`, `mediaId(100)`).

`BackupPreference` is `{ key(1), value(2) }` where `value` is a sealed `PreferenceValue` (Int/Long/Float/String/Boolean/StringSet). `BackupSourcePreferences` is `{ sourceKey(1), prefs(2) }`.

## How it works

### File format

- Container: gzip-compressed Protocol Buffers.
- Extension: `.tachibk`.
- Wire format produced by `kotlinx.serialization.protobuf.ProtoBuf` (default singleton from `AppModule.kt`) encoding `Backup.serializer()`.
- No `.proto` files exist in the repo. The schema is implicit in `@ProtoNumber`-annotated Kotlin data classes. Adding/removing/renaming a field without renumbering is safe because protobuf wire compatibility is by tag number; new optional fields default to their Kotlin defaults when absent.
- JSON backups are explicitly rejected at decode time (`MAGIC_JSON_SIGNATURE1/2/3` — `{}`, `{"`, `{\n`).

### What's included

Per `BackupOptions` (create) / `RestoreOptions` (restore), the user can toggle:

- Library entries (anime + manga favorites, optionally plus non-favorite watched/read entries via `readEntries`).
- Per-entry: chapters/episodes, tracking, history (these three are bundled under `libraryEntries` on restore — no separate restore toggles).
- Categories (anime + manga separately).
- App settings (preferences), with optional inclusion of "private" keys.
- Source settings (per-source preferences).
- Extension-repo settings (anime + manga separately).
- Custom buttons.
- Extensions (APK bytes; off by default on both create and restore).

### Versioning and migration

There is no integer "backup format version" field. Forward/backward compatibility is achieved through three mechanisms:

1. Protobuf tag-number wire compatibility: unknown tags are ignored on decode; missing fields fall back to Kotlin defaults. Renamed properties (e.g. `url` was `key` in 1.x, `thumbnailUrl` was `cover`) keep the same proto number, so old backups decode into new model classes.
2. The `isLegacy` boolean at field 500 of the modern `Backup` schema. `BackupDetector` peeks at this field (and at `backupAnimeSources` at field 103) to decide whether to decode the bytes as `LegacyBackup` (older combined aniyomi layout where anime fields share the low number space with manga) or as the modern `Backup` (where anime fields live in the 500-range, separate from manga's 1-106 range).
3. Per-entity `version: Long` field (proto 109 on both `BackupAnime` and `BackupManga`, proto 12 on `BackupEpisode`/`BackupChapter`). On restore, when both a DB row and a backup row exist for the same `(url, source)`, the higher `version` wins the metadata merge (`AnimeRestorer.restoreExistingAnime` / `MangaRestorer.restoreExistingManga`). This is the same `version` column populated by SQLDelight triggers on the live tables (see DATA-LAYER.md).

Legacy JSON backups (pre-protobuf era) are not supported — `BackupDecoder` throws `IOException(MR.strings.invalid_backup_file_json)` for any file beginning with `{`.

### Backup flow

1. UI / scheduler calls `BackupCreateJob.startNow(context, uri, options)` (manual) or `BackupCreateJob.setupTask(context)` (auto, periodic 12 h).
2. `BackupCreateJob.doWork` reads `isAutoBackup`, `LOCATION_URI_KEY`, `OPTIONS_KEY` from `inputData`; for auto-backups it reads `StorageManager.getAutomaticBackupsDirectory()` instead.
3. It calls `BackupCreator(context, isAutoBackup).backup(uri, options)`.
4. `BackupCreator.backup`:
   - For auto-backups: lists the target directory, filters by `FILENAME_REGEX`, sorts by name descending, deletes everything past `MAX_AUTO_BACKUPS - 1 = 3` (i.e. keeps newest 3 + creates 1 new = 4 total), then creates a new file named `${APPLICATION_ID}_yyyy-MM-dd_HH-mm.tachibk`.
   - Fetches `GetAnimeFavorites.await()` (+ `getWatchedAnimeNotInLibrary()` if `options.readEntries`) and `GetMangaFavorites.await()` (+ `getReadMangaNotInLibrary()`).
   - Invokes the 11 per-section creators, gated by the matching `BackupOptions` flag.
   - Builds the `Backup(...)` data class with `isLegacy = false`.
   - `parser.encodeToByteArray(Backup.serializer(), backup)` (the `ProtoBuf` singleton).
   - Truncates the target file, pipes through `okio`'s `.sink().gzip().buffer().use { write(byteArray) }`.
   - `BackupFileValidator(context).validate(fileUri)` — throws if decode fails; returns `Results(missingSources, missingTrackers)` (not currently surfaced for auto-backups, but used to abort the create on a malformed file).
   - For auto-backups: `backupPreferences.lastAutoBackupTimestamp().set(Instant.now().toEpochMilli())`.
   - Returns the file's URI string.
5. On success: `notifier.showBackupComplete(UniFile)`. On failure: `notifier.showBackupError(e.message)` and the partial file is deleted.

### Restore flow

1. UI calls `BackupRestoreJob.start(context, uri, options, sync=false)`.
2. `BackupRestoreJob.doWork` sets foreground service, then `BackupRestorer(context, notifier, isSync).restore(uri, options)`.
3. `BackupRestorer.restore`:
   - `restoreFromFile(uri, options)`:
     - `BackupDecoder(context).decode(uri)` → `Backup` (handles gzip + legacy detection).
     - Builds `animeSourceMapping` / `mangaSourceMapping` for error messages. (Note: the file has a copy-paste bug — `mangaSourceMapping` is assigned twice, once from `backupAnimeSources` and once from `backupSources`; the second assignment wins, so `animeSourceMapping` is effectively `emptyMap()` in current source. See TODOs.)
     - Computes `restoreAmount` from per-section sizes.
     - `coroutineScope { ... }` launches parallel coroutines for: categories (anime + manga), app prefs, source prefs, library entries (anime + manga concurrently), extension repos (anime then manga), custom buttons, extensions.
     - Each entry's restore is wrapped in `try/catch`; failures append to `errors`. `ensureActive()` honors cancellation.
   - `writeErrorLog()` writes `aniyomi_restore_error.txt` to the cache dir if `errors.isNotEmpty()`.
   - `notifier.showRestoreComplete(time, errors.size, logFile.parent, logFile.name, isSync)`.
4. Per-entry restore (`AnimeRestorer.restore` / `MangaRestorer.restore`) runs entirely in a single SQLDelight transaction (`handler.await(inTransaction = true) { ... }`). It uses `copyFrom` merges to preserve existing DB state where appropriate (`favorite = this.favorite || newer.favorite`, `initialized = this.initialized || newer.initialized`, etc.) and `max(...)` for `lastSeen`/`lastEpisodeSeen`/`seenAt`. Episodes/chapters are de-duplicated by URL; matching entries are updated only if `forComparison()` (which strips id/parentId/dates/version) differs.

### Schema mismatch / migration handling

- Unknown proto fields: ignored (protobuf wire-compat).
- Missing proto fields: default to the Kotlin default value (`0`, `null`, `false`, `emptyList()`).
- Older aniyomi backups (legacy layout): detected by `BackupDetector` and re-routed through `LegacyBackup.serializer()` then `.toBackup()`.
- Mihon-style backups (no anime fields, no `isLegacy` field present): `BackupDetector.isLegacyBackup` returns false (the `isLegacy` field defaults to `true` on decode but `backupAnimeSources` is empty so the `&&` short-circuits), so they decode as modern `Backup` with empty anime lists and restore cleanly into aniyomi.
- Per-row `version` field resolves concurrent-modification conflicts on a per-entry basis during restore.
- Source mismatches: `BackupFileValidator` warns pre-restore about missing sources and unauthenticated trackers; during restore, missing sources produce per-entry errors (caught and logged, not fatal).

### Partial vs full

There is no separate "full backup" code path in the current creator. "Full" vs "partial" is just `BackupOptions` with all flags on vs a subset. The `full/` package is a legacy model-only artifact. The creator always produces the modern `Backup` schema with `isLegacy = false`.

Restore is similarly partial via `RestoreOptions`. Note the asymmetry: `BackupOptions` exposes `chapters`/`tracking`/`history`/`readEntries` as separate create toggles, but `RestoreOptions` does not — those child data are restored whenever `libraryEntries` is on.

## Dependencies

### Upstream (what backup/restore reads from)

- `:domain` interactors / repositories (via Injekt): `GetAnimeFavorites`, `GetMangaFavorites`, `AnimeRepository.getWatchedAnimeNotInLibrary()`, `MangaRepository.getReadMangaNotInLibrary()`, `GetAnimeCategories`, `GetMangaCategories`, `GetAnimeHistory`, `GetMangaHistory`, `GetAnimeByUrlAndSourceId`, `GetMangaByUrlAndSourceId`, `GetEpisodesByAnimeId`, `GetChaptersByMangaId`, `GetAnimeTracks`, `GetMangaTracks`, `InsertAnimeTrack`, `InsertMangaTrack`, `UpdateAnime`, `UpdateManga`, `AnimeFetchInterval`, `MangaFetchInterval`, `GetAnimeExtensionRepo`, `GetMangaExtensionRepo`, `GetCustomButtons`.
- `tachiyomi.data.handlers.{anime,manga}.{Anime,Manga}DatabaseHandler` — direct SQLDelight query access (`episodesQueries`, `chaptersQueries`, `animesQueries`, `mangasQueries`, `animes_categoriesQueries`, `mangas_categoriesQueries`, `animehistoryQueries`, `history` (manga), `anime_syncQueries`, `manga_syncQueries`, `extension_reposQueries`, `custom_buttonsQueries`, `excluded_scanlatorsQueries`). Restorers bypass the repository abstraction and write to the DB handlers directly so they can wrap multi-step restores in a single transaction.
- `tachiyomi.domain.source.{anime,manga}.service.{Anime,Manga}SourceManager` — for source preference backup and validation.
- `eu.kanade.tachiyomi.data.track.TrackerManager` — for validation.
- `eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager` / `eu.kanade.tachiyomi.extension.manga.MangaExtensionManager` — for APK backup.
- `tachiyomi.domain.backup.service.BackupPreferences` — backup interval, last auto-backup timestamp, legacy backup flags.
- `tachiyomi.domain.library.service.LibraryPreferences`, `tachiyomi.domain.download.service.DownloadPreferences` — for category-ID remapping during preference restore.
- `tachiyomi.core.common.preference.PreferenceStore` (+ `AndroidPreferenceStore` for per-source prefs), `tachiyomi.core.common.preference.Preference` (for `isAppState`, `isPrivate` classification).
- `tachiyomi.domain.storage.service.StorageManager` — for the auto-backup directory.
- `kotlinx.serialization.protobuf.ProtoBuf` (singleton from `AppModule`).
- `okio` (`source`, `gzip`, `buffer`, `sink`).
- `com.hippo.unifile.UniFile` — SAF-aware file access.
- `androidx.work.*` — WorkManager scheduling.
- `eu.kanade.tachiyomi.data.library.{anime,manga}.{Anime,Manga}LibraryUpdateJob` — re-scheduled by `PreferenceRestorer.restoreApp`.
- `eu.kanade.tachiyomi.data.notification.*` — notification channels + `NotificationReceiver` (share/cancel/open-error-log pending intents).

### Downstream (what depends on backup/restore)

- UI: settings screens that construct `BackupOptions` / `RestoreOptions` and call `BackupCreateJob.startNow` / `BackupRestoreJob.start`.
- `PreferenceRestorer` calls back into `BackupCreateJob.setupTask` and the library-update jobs to reschedule them after a preference restore.
- `AnimeLibraryUpdateJob` / `MangaLibraryUpdateJob` — `BackupCreateJob.setupTask` is invoked from `PreferenceRestorer` to re-arm auto-backups after restoring app prefs (so a restored `backupInterval` takes effect).

## Anime vs manga

This is the most important section for an anime-first fork. The backup system is **unified at the container level but split at the field level**:

- **One container, two domains.** A single `Backup` protobuf message holds both anime and manga data. There is no `AnimeBackupManager` or `MangaBackupManager` class. `BackupCreator` builds one `Backup` with both `backupAnime` (field 501) and `backupManga` (field 1) populated; `BackupRestorer` decodes one `Backup` and restores both lists.
- **Proto field numbers are NOT shared between anime and manga for the same logical concept at the root level.** Manga sits in the 1-106 range (`backupManga=1`, `backupCategories=2`, `backupSources=101`, `backupPreferences=104`, `backupSourcePreferences=105`, `backupMangaExtensionRepo=106`). Anime sits in the 500-506 range (`isLegacy=500`, `backupAnime=501`, `backupAnimeCategories=502`, `backupAnimeSources=503`, `backupExtensions=504`, `backupAnimeExtensionRepo=505`, `backupCustomButton=506`). The 500-offset is explicitly commented `// Aniyomi specific values` — it was chosen so aniyomi backups remain decodable by upstream mihon (which ignores unknown fields) without mihon mistaking aniyomi's anime list for anything else.
- **Per-entry field numbers ARE shared between `BackupAnime` and `BackupManga` for parallel concepts** (`source=1`, `url=2`, `title=3`, ..., `episodes`/`chapters=16`, `categories=17`, `tracking=18`, `favorite=100`, `history=104`, `updateStrategy=105`, `lastModifiedAt=106`, `favoriteModifiedAt=107`, `version=109`). `BackupAnime` extends the layout with `500..507` for aniyomi-only fields (`backgroundUrl`, `parentId`, `id`, `seasonFlags`, `seasonNumber`, `seasonSourceOrder`, `fetchType`). `BackupManga` adds `108` for `excludedScanlators` (a manga-only concept). `BackupEpisode` (anime) and `BackupChapter` (manga) share `1..12` but `BackupEpisode` adds `16` (`totalSeconds`) and `501..503` (`fillermark`, `summary`, `previewUrl`). `BackupTracking` (manga) and `BackupAnimeTracking` share `1..12, 100` exactly; only the field names differ (`lastChapterRead` vs `lastEpisodeSeen`, `startedReadingDate` vs `startedWatchingDate`, etc.).
- **Separate creators and restorers per domain.** `AnimeBackupCreator` / `MangaBackupCreator`, `AnimeCategoriesBackupCreator` / `MangaCategoriesBackupCreator`, `AnimeSourcesBackupCreator` / `MangaSourcesBackupCreator`, `AnimeExtensionRepoBackupCreator` / `MangaExtensionRepoBackupCreator`, `AnimeRestorer` / `MangaRestorer`, `AnimeCategoriesRestorer` / `MangaCategoriesRestorer`, `AnimeExtensionRepoRestorer` / `MangaExtensionRepoRestorer`. They are paired but independent classes, not generic parameterizations of a single base.
- **Shared creators/restorers** for cross-cutting data: `PreferenceBackupCreator` (writes both anime-source and manga-source prefs in one pass), `ExtensionsBackupCreator` (writes both anime and manga APKs into one `backupExtensions` list), `PreferenceRestorer`, `ExtensionsRestorer`, `CustomButtonBackupCreator` / `CustomButtonRestorer`.
- **Can an anime-only app read/write aniyomi backups cleanly?** Mostly yes.
  - Writing: trivial. An anime-only fork can produce a `Backup` with `backupManga = emptyList()`, `backupCategories = emptyList()`, `backupSources = emptyList()`, `backupMangaExtensionRepo = emptyList()`, and the anime fields populated. The result decodes cleanly in full aniyomi (which will simply see an empty manga library). Mihon would also decode it (ignoring the 500+ fields) and see only an empty manga library — useless but not corrupt.
  - Reading an aniyomi backup: an anime-only fork can decode any aniyomi `.tachibk` file (the schema is the same `Backup` class) and just ignore the `backupManga*` fields. The legacy-detection path (`LegacyBackup`) also works because `LegacyBackup.toBackup()` carries the manga fields through transparently.
  - Reading a mihon backup: works (anime lists will simply be empty). The `isLegacy` field defaults to `true` on missing, but `BackupDetector.isLegacyBackup` requires both `isLegacy` AND non-empty `backupAnimeSources` (field 103), so a pure mihon backup decodes as modern `Backup`.
  - Caveat: the `BackupFileValidator` cross-references both `AnimeSourceManager` and `MangaSourceManager`. An anime-only fork would need to drop the manga-side checks (or stub `MangaSourceManager` to return empty).
  - Caveat: `PreferenceRestorer` re-maps both `DEFAULT_MANGA_CATEGORY_PREF_KEY` and `DEFAULT_ANIME_CATEGORY_PREF_KEY`; an anime-only fork can drop the manga branch.
  - Caveat: `BackupRestorer.restoreFromFile` calls both `restoreAnime` and `restoreManga`. An anime-only fork would drop the manga call (and the manga categories/repos restorers).
  - Caveat: `ExtensionsBackupCreator` writes both anime and manga APKs. An anime-only fork would only iterate the anime manager.
  - Caveat: `extensionsBackupCreator` / `ExtensionsRestorer` does not distinguish anime vs manga APKs — `BackupExtension` only stores `pkgName` + `apk`, with no domain tag. An anime-only fork restoring an aniyomi backup that included manga APKs would attempt to install them; the package name would tell the manga extension installer apart from the anime one (mihon uses `eu.kanade.tachiyomi.extension.manga.*`, aniyomi uses `eu.kanade.tachiyomi.animesource.extension.anime.*` — TODO verify exact pkg prefixes), so the user would just see an install prompt for an unrelated package. Not ideal but not corrupting.
- **Are backup field numbers shared or separate?** Both, depending on the level: root-level `Backup` uses separate number ranges (1-106 for manga, 500-506 for anime); per-entry `BackupAnime` / `BackupManga` use shared numbers for parallel fields. This means: at the root, anime and manga are cleanly separable proto sub-trees; inside an entry, the wire format of a `BackupAnime` and a `BackupManga` is structurally similar (so shared (de)serialization logic is feasible) but the two are distinct types and are not interchangeable.

## Relationships

- **DATA-LAYER**: backup/restore reads and writes both SQLDelight databases (anime + manga) via the `*DatabaseHandler` abstractions. The restorers write directly to `*Queries` objects (e.g. `animesQueries.insert`, `episodesQueries.insert`, `animehistoryQueries.upsert`, `anime_syncQueries.update`, `extension_reposQueries.insert`, `custom_buttonsQueries.insert`, `excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId`) rather than going through repository interfaces, in order to wrap a multi-table restore in a single transaction. The `version` column on each table (bumped by triggers — see DATA-LAYER.md) is the same `version` field stored at proto 109 on `BackupAnime` / `BackupManga` and used for restore conflict resolution. The `is_syncing = 1` flag is passed to `animesQueries.update` / `mangasQueries.update` during restore to suppress trigger-driven version bumps (see `AnimeRestorer.updateAnime`).
- **TRACKERS**: tracker rows are embedded inside each `BackupAnime` / `BackupManga` entry as `tracking: List<BackupAnimeTracking>` / `List<BackupTracking>`. Each row stores `syncId` (the `Tracker` ID — see TRACKERS.md), the remote `mediaId`, `libraryId`, last-seen/last-read progress, score, status, dates, and a `private` flag. `BackupFileValidator.validate` walks `backup.backupAnime.flatMap { it.tracking }` + `backup.backupManga.flatMap { it.tracking }`, collects distinct `syncId`s, and flags any tracker that exists in `TrackerManager` but `!isLoggedIn`. `AnimeRestorer.restoreTracking` / `MangaRestorer.restoreTracking` insert new tracks via `InsertAnimeTrack.awaitAll` / `InsertMangaTrack.awaitAll` or update existing ones via `anime_syncQueries.update` / `manga_syncQueries.update`, taking `max(db, backup)` for `lastEpisodeSeen` / `lastChapterRead`.
- **DOWNLOAD-MANAGER**: backup/restore does not back up downloaded episode/chapter files. Only the metadata (episode/chapter rows, history, seen/read state) is backed up. After a restore, the user must re-download. `PreferenceRestorer` does restore `DownloadPreferences.categoryPreferenceKeys` (category-ID set remapping) so download-category filters survive the round-trip. The `ExtensionsRestorer` writes APKs to `cacheDir` and fires the system package installer — it does not interact with the download manager.
- **SOURCE-SYSTEM**: stub source metadata is backed up (`BackupSource` / `BackupAnimeSource` with just `name` + `sourceId`) so that on restore, the restorer can resolve entries to a (possibly stubbed) source for fetching missing metadata. `BackupFileValidator` warns about missing sources. Source preferences (`ConfigurableSource` / `ConfigurableAnimeSource`) are backed up via `PreferenceBackupCreator.createSource` and restored via `PreferenceRestorer.restoreSource` using `sourcePreferences(sourceKey)` as the preference namespace.
- **EXTENSIONS / EXTENSION REPOS**: extension repos (the `extension_repos` table) are backed up as `BackupExtensionRepos` and restored via `AnimeExtensionRepoRestorer` / `MangaExtensionRepoRestorer` (which validate against SHA-fingerprint and base-URL conflicts). Installed extension APKs can optionally be backed up via `ExtensionsBackupCreator` (reads `publicSourceDir` bytes) and restored via `ExtensionsRestorer` (writes APK to cache and fires `ACTION_VIEW` installer intent).
- **DI**: the `ProtoBuf` singleton is registered in `AppModule.kt`. All backup classes receive their dependencies through Injekt constructor injection (`Injekt.get<T>()`), making them unit-testable.
- **WORKMANAGER / NOTIFICATIONS**: `BackupCreateJob` and `BackupRestoreJob` are the two WorkManager entrypoints. They use foreground-service notifications (`FOREGROUND_SERVICE_TYPE_DATA_SYNC`) with progress + complete + error channels. `BackupCreateJob.setupTask` is invoked both from settings UI and from `PreferenceRestorer.restoreApp` (to re-arm auto-backups after a preference restore).

## Notes for our build (anime-first)

For an anime-only fork, the backup subsystem can be slimmed substantially while preserving wire-format compatibility with aniyomi:

1. **Keep the `Backup` proto schema as-is.** Do not renumber fields. An anime-only fork that emits `backupManga = emptyList()` and the other manga fields as their defaults produces a file that any aniyomi install can still read. This is the cheapest forward-compatibility insurance.
2. **Drop the manga-side creators and restorers** (`MangaBackupCreator`, `MangaCategoriesBackupCreator`, `MangaSourcesBackupCreator`, `MangaExtensionRepoBackupCreator`, `MangaRestorer`, `MangaCategoriesRestorer`, `MangaExtensionRepoRestorer`). Also drop the manga branches in `PreferenceBackupCreator`, `PreferenceRestorer`, `BackupFileValidator`, and `BackupRestorer.restoreFromFile` (the `restoreManga` call and the manga half of `restoreExtensionRepos`).
3. **Drop `MangaSourceManager`, `MangaExtensionManager`, `MangaRepository`, `GetMangaFavorites`, `MangaDatabaseHandler`** from the dependency graph. The remaining anime-side classes only inject anime-side equivalents.
4. **Forward-compatibility with a future manga addition**: because the proto schema already reserves the 1-106 range for manga, adding manga back later is purely additive — no schema migration, no backup-format version bump. Existing anime-only backups will decode cleanly into a future anime+manga build (the manga fields will be empty).
5. **`ExtensionsBackupCreator` caveat**: today it bundles anime AND manga APKs into one `backupExtensions` list with no domain tag. An anime-only fork should restrict it to `animeExtensionManager.installedExtensionsFlow` only. If we ever add manga back, we'd need a way to tell anime vs manga APKs apart in the backup; the cleanest option is to introduce a new `@ProtoNumber(3) val type: ExtensionType = ANIME` field on `BackupExtension` (defaulting to ANIME so old anime-only backups still decode).
6. **`BackupFileValidator` caveat**: it iterates `backup.backupSources` (manga) and `backup.backupAnimeSources` (anime). An anime-only fork drops the manga half. The tracker-validation half is shared (`animeTrackers + mangaTrackers`) and can be reduced to just `animeTrackers`.
7. **`isLegacy` detection caveat**: `BackupDetector.isLegacyBackup` checks `isLegacy && backupAnimeSources.isNotEmpty()`. An anime-only fork should preserve this logic unchanged so it can still decode old aniyomi backups (which used the `LegacyBackup` combined layout). The `LegacyBackup.toBackup()` path is needed for round-tripping pre-fork backups.
8. **Custom buttons** (`BackupCustomButtons`, `CustomButtonBackupCreator`, `CustomButtonRestorer`) are aniyomi-specific (not mihon) and are tied to the anime DB (`custom_buttonsQueries` lives in the anime handler). They should be kept in an anime-only fork.
9. **`aniyomi_restore_error.txt`**: keep the per-entry error-collection pattern — it's user-visible and valuable.
10. **Auto-backup rotation**: the `MAX_AUTO_BACKUPS = 4` + `FILENAME_REGEX` rotation logic is domain-agnostic and should be kept verbatim.

## TODOs / open questions

- `BackupRestorer.restoreFromFile` (lines 85-88 of `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt`) appears to have a copy-paste bug:
  ```kotlin
  val backupAnimeMaps = backup.backupAnimeSources
  mangaSourceMapping = backupAnimeMaps.associate { it.sourceId to it.name }  // should be animeSourceMapping
  val backupMangaMaps = backup.backupSources
  mangaSourceMapping = backupMangaMaps.associate { it.sourceId to it.name }
  ```
  `animeSourceMapping` is declared but never assigned a non-empty value, so anime restore error messages fall back to `it.source.toString()` rather than the source name. Confirm whether this is intentional or a regression.
- The `full/models/Backup.kt` and `full/models/BackupPreference.kt` files appear to be dead code (no references found in the create/restore paths). Confirm whether they're loaded by any legacy decode path or whether they can be deleted.
- `BackupPreferences.backupFlags()` (string set of `FLAG_*` constants in `PreferenceValues.kt`) appears to be the legacy representation of `BackupOptions`. Confirm whether anything still reads it; if not, it can be dropped in favor of `BackupOptions` / `RestoreOptions`.
- `BackupExtension` has no domain discriminator (anime vs manga). If we ever need to restore an aniyomi backup that contains manga APKs into an anime-only fork, the user will see install prompts for unrelated packages. TODO: decide whether to add a `type` field for our fork.
- The exact package-name prefixes that distinguish anime vs manga extensions (used implicitly by `ExtensionsRestorer`'s "skip if already installed" check) need to be verified against the extension-installer code — not covered by this doc.
- `RestoreOptions` does not expose `chapters` / `tracking` / `history` toggles (they are restore-on if `libraryEntries` is on). Confirm whether this is intentional UX or a missing feature vs `BackupOptions`.
- `BackupCreateJob.setupTask` reads `backupInterval()` (default 12 h) and only enqueues if `interval > 0`; `0` cancels the periodic work. The UI that flips this between 0 and nonzero was not inspected for this doc. TODO: cross-reference with the settings screen.
