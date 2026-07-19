# 03-subsystems / `updates.md` — Update checking

> Aniyomi periodically asks each library entry's source "what chapters /
> episodes do you have?" and diff-syncs the result against the local
> database. Newly discovered items are written to the `chapters` /
> `episodes` tables, surfaced in the **Updates** feed, and (optionally)
> announced via a system notification. This doc covers the WorkManager
> job that does the fetching, the Updates UI, the notifications, and the
> per-side manga/anime parallelism.
>
> For how the resulting library entries are then displayed in the Library,
> see [`library-management.md`](library-management.md). For app-self-update
> (checking GitHub for a new Aniyomi APK), see [`updater.md`](updater.md).

## Purpose

The updates subsystem does three things:

1. **Detect new chapters / episodes** for library entries by re-fetching
   each source's chapter/episode list and diffing against the local DB.
2. **Surface them in the Updates tab** — a reverse-chronological feed of
   recently-fetched chapters/episodes, grouped by date.
3. **Notify the user** — a system notification per source batch with
   "Mark as read", "View chapters", and "Download" actions.

It does **not** download the new items automatically (unless the user has
enabled the per-entry "auto-download new chapters" flag, in which case the
job queues them with the download manager but defers starting downloads
until the update job is done).

## The library update Worker (WorkManager)

Two near-identical `CoroutineWorker` subclasses do the work, one per side:

| File                                                                                             | Class                    |
|--------------------------------------------------------------------------------------------------|--------------------------|
| `app/.../data/library/manga/MangaLibraryUpdateJob.kt`                                            | `MangaLibraryUpdateJob`  |
| `app/.../data/library/anime/AnimeLibraryUpdateJob.kt`                                            | `AnimeLibraryUpdateJob`  |

Each registers two WorkManager work names:

| Work name                        | Trigger                                            |
|----------------------------------|----------------------------------------------------|
| `LibraryUpdate-auto` / `AnimeLibraryUpdate-auto`   | Periodic (every N hours, scheduled by `setupTask`) |
| `LibraryUpdate-manual` / `AnimeLibraryUpdate-manual` | One-shot (user taps "Update library" or refresh)   |

Both are tagged with a side-specific `TAG` (`"LibraryUpdate"` /
`"AnimeLibraryUpdate"`) so they can be cancelled together. The auto job
defers to the manual one: if both would run, the auto job `Result.retry()`
s.

### Scheduling — `setupTask(context, prefInterval?)`

```
LibraryPreferences.autoUpdateInterval()  (hours; 0 = disabled)
        │
        ▼
if (interval == 0) cancelUniqueWork(WORK_NAME_AUTO)
else
  PeriodicWorkRequestBuilder<*LibraryUpdateJob>(interval, HOURS, 10, MINUTES)
      .setConstraints(
          NetworkRequest (Android 9+)  ── DEVICE_ONLY_ON_WIFI, DEVICE_NETWORK_NOT_METERED
          NetworkType (older)          ── UNMETERED if not-metered, else CONNECTED
          requiresCharging             ── DEVICE_CHARGING
          requiresBatteryNotLow        ── true (always)
      )
      .setBackoffCriteria(LINEAR, 10 min)
      .enqueueUniquePeriodicWork(WORK_NAME_AUTO, ExistingPeriodicWorkPolicy.UPDATE)
```

`setupTask` is called from the settings screen when the user changes the
update interval or device restrictions, and from `App.kt` on app start
to re-arm with the latest preferences. The 10-minute flex window lets
WorkManager batch the wake-up within the hour to save battery.

### Manual run — `startNow(context, category?)`

Returns `Boolean` — `false` if a job for that side is already running
(checked via `WorkManager.isRunning(TAG)`), `true` if a new one-shot was
enqueued. Accepts an optional `Category` to update only one category; if
`null`, updates the whole library (subject to the include/exclude category
preferences — see below). Uses `ExistingWorkPolicy.KEEP` so a second tap
doesn't restart the in-flight one.

Wired to:

- The Library toolbar's per-category refresh button → `startNow(ctx, category)`.
- The Library toolbar's "global update" button → `startNow(ctx, null)`.
- The Updates screen's "Update library" action → `startNow(ctx, null)`.

### `stop(context)`

Cancels the currently running work (queries `WorkManager` for `RUNNING`
work tagged with `TAG`), then re-enqueues the periodic auto job if the
cancelled work was the auto one (so a user "stop" doesn't permanently
disable scheduled updates).

### `doWork()` — the actual update

```
doWork():
  if tag WORK_NAME_AUTO and (pre-Android-P + WiFi restriction + not on WiFi):
      Result.retry()
  if tag WORK_NAME_AUTO and manual job is running:
      Result.retry()
  setForeground(getForegroundInfo())   ← progress notification, data-sync foreground type
  libraryPreferences.lastUpdatedTimestamp().set(now)
  add*ToQueue(categoryId)              ← builds mangaToUpdate / animeToUpdate
  try:
      update*List()                    ← the actual fetch+sync loop
      Result.success()
  catch CancellationException:
      Result.success()                 ← treat cancel as success
  catch Exception:
      Result.failure()
  finally:
      notifier.cancelProgressNotification()
```

### `add*ToQueue(categoryId)` — building the work list

1. `getLibrary*().await()` — fetch the whole library.
2. If `categoryId != -1L`, filter to that category. Otherwise:
   - Read `*UpdateCategories()` (included categories) and
     `*UpdateCategoriesExclude()` (excluded categories) from preferences.
   - Apply inclusion first (empty = all), then exclusion.
   - `distinctBy { it.*.id }` (an entry can appear in multiple categories).
3. **Anime-only season expansion**: if
   `updateSeasonOnLibraryUpdate()` is on and an entry has
   `fetchType == Seasons`, expand it into its child season anime (via
   `GetAnimeSeasonsByParentId`) and update those individually. Pure
   `Episodes`-type anime pass through directly.
4. Apply **per-item skip rules** from `autoUpdateItemRestrictions()`
   (each skip is logged with a reason and the entry excluded):

   | Restriction                         | Skip condition                                          |
   |-------------------------------------|---------------------------------------------------------|
   | `ENTRY_NON_COMPLETED`               | `entry.status == COMPLETED`                             |
   | `ENTRY_HAS_UNVIEWED`                | `unreadCount != 0` (manga) / `unseenCount != 0` (anime) |
   | `ENTRY_NON_VIEWED`                  | `totalChapters > 0 && !hasStarted`                      |
   | `ENTRY_OUTSIDE_RELEASE_PERIOD`      | `entry.nextUpdate > fetchWindowUpperBound`              |
   | (always) `updateStrategy != ALWAYS_UPDATE` | entry's update strategy isn't `ALWAYS_UPDATE`   |

   The `fetchWindowUpperBound` comes from `*FetchInterval.getWindow(now)`
   (the same logic that computes each entry's next expected update).
5. `sortedBy { it.*.title }` (deterministic order, useful for logs).
6. `notifier.showQueueSizeWarningNotificationIfNeeded(list)` — if any
   single source has >60 entries to update (and isn't an
   `UnmeteredSource`), post a one-shot warning notification linking to
   the Aniyomi FAQ on bulk updates.

### `update*List()` — the fetch+sync loop

The actual fetching is parallelised **per source** with a `Semaphore(5)`
to avoid hammering any single source (and to limit concurrent network
connections):

```
coroutineScope {
  mangaToUpdate.groupBy { it.manga.source }.values
    .map { mangaInSource ->
        async {
            semaphore.withPermit {
                mangaInSource.forEach { libraryManga ->
                    ensureActive()
                    // Re-check favorite in case it was removed mid-update:
                    if (getManga.await(id)?.favorite != true) return@forEach
                    withUpdateNotification(currentlyUpdating, progress, manga) {
                        try {
                            newChapters = updateManga(manga, fetchWindow)
                                       .sortedByDescending { it.sourceOrder }
                            if (newChapters.isNotEmpty()) {
                                chaptersToDownload = filterChaptersForDownload.await(manga, newChapters)
                                if (chaptersToDownload.isNotEmpty()) {
                                    downloadChapters(manga, chaptersToDownload)  // queues, doesn't start
                                    hasDownloads = true
                                }
                                libraryPreferences.newMangaUpdatesCount()
                                    .getAndSet { it + newChapters.size }
                                newUpdates.add(manga to newChapters.toTypedArray())
                            }
                        } catch (e) {
                            failedUpdates.add(manga to errorMessage(e))
                        }
                    }
                }
            }
        }
    }.awaitAll()
}
notifier.cancelProgressNotification()
if (newUpdates.isNotEmpty()) {
    notifier.showUpdateNotifications(newUpdates)
    if (hasDownloads) downloadManager.startDownloads()
}
if (failedUpdates.isNotEmpty()) {
    errorFile = writeErrorFile(failedUpdates)
    notifier.showUpdateErrorNotification(failedUpdates.size, errorFile.uri)
}
```

`updateManga(manga, fetchWindow)` (private) does:

1. If `autoUpdateMetadata()` pref is on: call `source.getMangaDetails`,
   `updateManga.awaitUpdateFromSource(manga, networkManga, manualFetch =
   false, coverCache)` — refreshes cover/title/description.
2. `source.getChapterList(manga.toSManga())` — the actual network fetch.
3. Re-fetch `dbManga` to make sure it's still a favorite (the user may
   have removed it during the fetch).
4. `syncChaptersWithSource.await(chapters, dbManga, source, false,
   fetchWindow)` — the diff-and-persist interactor (in
   `app/.../domain/items/chapter/interactor/`). Returns the list of newly
   inserted chapters.

The anime twin is identical in shape, except it calls
`getAnimeDetails`, `getEpisodeList`, and `syncEpisodesWithSource`, and
includes the season expansion noted above.

### The progress notification

`withUpdateNotification` is a small wrapper that, before/after each
entry's update, calls `notifier.showProgressNotification(currentlyUpdating,
completed, total)`. The notification:

- Channel: `CHANNEL_LIBRARY_PROGRESS`.
- Title: `"Updating library — 42%"` (formatted with `NumberFormat`).
- Body (if `hideNotificationContent()` is off): the list of currently
  updating manga titles (chopped to 40 chars each), shown in
  `BigTextStyle`.
- Ongoing, `onlyAlertOnce`, with a "Cancel" action that fires
  `NotificationReceiver.cancelLibraryUpdatePendingBroadcast` →
  `*LibraryUpdateJob.stop(context)`.
- Foreground service type: `FOREGROUND_SERVICE_TYPE_DATA_SYNC` (Android
  10+).

### Error logging

`writeErrorFile(failedUpdates)` writes a plain-text file
`aniyomi_update_errors.txt` to the cache dir, grouped by error message
then by source:

```
! HTTP 429 Too Many Requests
  # MangaDex
    - One Piece
    - Jujutsu Kaisen
! No chapters found
  # SomeSource
    - Some Manga
```

The error notification's tap intent opens this file via
`NotificationReceiver.openErrorLogPendingActivity`.

## The Updates screen

`UpdatesTab` (`app/.../ui/updates/UpdatesTab.kt`) is a Voyager tab hosting
a `TabbedScreen` with two pages (anime first, manga second):

```
UpdatesTab (Tab)
  ├── TabbedScreen(titleRes = label_recent_updates)
  │     ├── animeUpdatesTab(context, fromMore)   — page 0
  │     └── mangaUpdatesTab(context, fromMore)   — page 1
  │
  └── onReselect(navigator) → navigator.push(DownloadsTab)
                              (one-tap jump to the Downloads queue)
```

Its bottom-nav index shifts based on `NavStyle` (`MOVE_UPDATES_TO_MORE`
hides it behind More; `MOVE_HISTORY_TO_MORE` / `MOVE_BROWSE_TO_MORE` /
`MOVE_MANGA_TO_MORE` shift its slot).

### `*UpdatesScreenModel.kt`

A `StateScreenModel<State>` whose `State` carries `isLoading`, `items:
PersistentList<*UpdatesItem>`, and `dialog: Dialog?`. The model:

- Subscribes to `GetMangaUpdates.subscribe(limit)` /
  `GetAnimeUpdates.subscribe(limit)` where `limit = now.minusMonths(3)`
  (the Updates feed only shows the last 3 months).
- Combines the updates flow with `downloadCache.changes` and
  `downloadManager.queueState` so download state stays fresh without
  re-querying.
- Maps each `*UpdatesWithRelations` row into a `*UpdatesItem` carrying
  `downloadStateProvider: () -> *Download.State` and
  `downloadProgressProvider: () -> Int` (these are lambdas so the
  download state can be updated in-place without rebuilding the whole
  list).
- Subscribes to `downloadManager.statusFlow()` +
  `downloadManager.progressFlow()` (merged) to patch the per-item
  download state in-place via `updateDownloadState(download)`.
- Exposes `lastUpdated` (a `PreferenceMutableState<Long>` from
  `libraryPreferences.lastUpdatedTimestamp()`) so the UI can show "last
  updated X minutes ago".
- Exposes `updateLibrary(): Boolean` (calls
  `*LibraryUpdateJob.startNow(app)` and emits a `LibraryUpdateTriggered`
  event).
- Exposes `resetNewUpdatesCount()` (clears the
  `newMangaUpdatesCount` / `newAnimeUpdatesCount` preference — this is
  the unread-count badge on the Updates tab).

The selection logic (`toggleSelection`, `toggleAllSelection`,
`invertSelection`) mirrors the library's, including long-press range
selection.

Per-item actions: `downloadChapters(items, action)` /
`downloadEpisodes(items, action)` with `ChapterDownloadAction` /
`EpisodeDownloadAction` (`START`, `START_NOW`, `CANCEL`, `DELETE`, and
anime-only `SHOW_QUALITIES`); `markUpdatesRead` / `markUpdatesSeen`;
`bookmarkUpdates`; anime-only `fillermarkUpdates`; `deleteChapters` /
`deleteEpisodes` (with a `Dialog.DeleteConfirmation`).

### The `*UpdatesItem` model

```kotlin
@Immutable
data class MangaUpdatesItem(
    val update: MangaUpdatesWithRelations,
    val downloadStateProvider: () -> MangaDownload.State,
    val downloadProgressProvider: () -> Int,
    val selected: Boolean = false,
)
```

Anime twin: `AnimeUpdatesItem`. The provider lambdas are the key trick —
they let the ScreenModel patch download state on a single item without
rebuilding the list.

## The updates table & view

The Updates feed is **not** a separate table — it's a view over
`chapters` / `episodes` joined with `mangas` / `animes`, filtered to
library entries and to rows whose `date_fetch > date_added` (i.e. items
that appeared *after* the entry was added to the library, not backfill).

### Manga: `updatesView` (`sqldelight/view/updatesView.sq`)

```sql
CREATE VIEW updatesView AS
SELECT
    mangas._id        AS mangaId,
    mangas.title      AS mangaTitle,
    chapters._id      AS chapterId,
    chapters.name     AS chapterName,
    chapters.scanlator,
    chapters.read,
    chapters.bookmark,
    chapters.last_page_read,
    mangas.source,
    mangas.favorite,
    mangas.thumbnail_url AS thumbnailUrl,
    mangas.cover_last_modified AS coverLastModified,
    chapters.date_upload AS dateUpload,
    chapters.date_fetch  AS datefetch
FROM mangas JOIN chapters
ON mangas._id = chapters.manga_id
WHERE favorite = 1
  AND date_fetch > date_added
ORDER BY date_fetch DESC;
```

Named queries: `getRecentUpdates(after, limit)` (filters by
`dateUpload > :after`), `getUpdatesByReadStatus(read, after, limit)`.

### Anime: `animeupdatesView` (`sqldelightanime/view/animeupdatesView.sq`)

Same shape with the extra anime-only columns
(`episodes.fillermark`, `episodes.last_second_seen`, `episodes.total_seconds`)
and named queries `getRecentAnimeUpdates` / `getUpdatesBySeenStatus`.

### The `*UpdatesWithRelations` domain models

| Manga (`tachiyomi.domain.updates.manga.model`) | Anime (`tachiyomi.domain.updates.anime.model`) |
|------------------------------------------------|------------------------------------------------|
| `MangaUpdatesWithRelations(mangaId, mangaTitle, chapterId, chapterName, scanlator, read, bookmark, lastPageRead, sourceId, dateFetch, coverData)` | `AnimeUpdatesWithRelations(animeId, animeTitle, episodeId, episodeName, scanlator, seen, bookmark, fillermark, lastSecondSeen, totalSeconds, sourceId, dateFetch, coverData)` |

Both carry a `*Cover` payload (same Coil pipeline as the library, see
[`library-management.md`](library-management.md)).

### Repository & interactor

| Manga                                                          | Anime                                                          |
|----------------------------------------------------------------|----------------------------------------------------------------|
| `MangaUpdatesRepository` (interface)                           | `AnimeUpdatesRepository` (interface)                           |
| `MangaUpdatesRepositoryImpl` (`data/.../updates/manga/`)       | `AnimeUpdatesRepositoryImpl` (`data/.../updates/anime/`)       |
| `GetMangaUpdates` (interactor)                                 | `GetAnimeUpdates` (interactor)                                 |

`Get*Updates` exposes three methods:

- `await(read, after)` — one-shot list (used rarely).
- `subscribe(instant)` — `Flow<List<…WithRelations>>` of all updates
  since `instant`, limit 500.
- `subscribe(read, after)` — `Flow<List<…WithRelations>>` filtered by
  read/seen status.

## The new-updates notification

`*LibraryUpdateNotifier.showUpdateNotifications(updates: List<Pair<*,
Array<*>>>)` posts two notifications:

1. **Group summary** (notification id `ID_NEW_CHAPTERS`, channel
   `CHANNEL_NEW_CHAPTERS_EPISODES`, group `GROUP_NEW_CHAPTERS`):
   "N new chapters" / "N new episodes", with the list of titles in
   `BigTextStyle` (unless `hideNotificationContent()` is on). Tapping
   opens `MainActivity` with action `SHORTCUT_UPDATES`.
2. **Per-entry** (one notification per manga/anime that got new items;
   id = `manga.id.hashCode()`): the entry title, the list of new
   chapter/episode numbers (formatted via `formatChapterNumber` /
   `getNewEpisodesDescription`), the entry's cover icon (loaded via Coil
   with `CircleCropTransformation`), and three actions:
   - **Mark as read / seen** →
     `NotificationReceiver.markAsViewedPendingBroadcast` (broadcast).
   - **View chapters / episodes** →
     `NotificationReceiver.openChapterPendingActivity` (opens the entry's
     chapter/episode list).
   - **Download** (only if ≤ `CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD`
     new items) → `NotificationReceiver.downloadChaptersPendingBroadcast`.

If `hideNotificationContent()` is on, only the group summary is posted
(no per-entry notifications, no title list).

The per-entry description (`getNewChaptersDescription` /
`getNewEpisodesDescription`) builds a compact string:

- 0 recognized numbers → `"5 new chapters"`.
- 1 recognized number → `"Chapter 2.5"` or `"Chapter 2.5 and 10 more"`.
- Multiple → `"Chapters 1, 2.5, 3, 4, 5 and 10 more"` (truncated to 5
  numbers).

## Manual "update library" action

Two surfaces trigger an immediate update:

1. The Library toolbar's per-category refresh button (calls
   `*LibraryUpdateJob.startNow(ctx, category)` and shows a snackbar with
   `updating_category` or `update_already_running`).
2. The Library toolbar's "global update" button and the Updates screen's
   "Update library" action (call `*LibraryUpdateJob.startNow(ctx, null)`).
   The Updates screen emits a `LibraryUpdateTriggered(started)` event
   which the Compose layer surfaces as a snackbar.

The "auto-update metadata" toggle (`LibraryPreferences.autoUpdateMetadata`)
controls whether the job *also* refreshes covers/titles/descriptions
during each update. Independent of that, the `*MetadataUpdateJob` (a
separate one-shot WorkManager job, `*MetadataUpdateJob.startNow(ctx)`)
forces a metadata refresh for every library entry without fetching
chapters — triggered from Settings.

## Categorizing updates by date

Both `*UpdatesScreenModel.State.getUiModel()` and
`*HistoryScreenModel.toHistoryUiModels()` use the same
`insertSeparators` helper (from `eu.kanade.core.util`) to inject
`Header(localDate)` rows between items whose `dateFetch.toLocalDate()`
(or `readAt.toLocalDate()` for history) differ:

```
[Header("Today"),   Item(...), Item(...),
 Header("Yesterday"), Item(...),
 Header("2024-03-12"), Item(...), Item(...)]
```

The header is a `LocalDate` rendered via `DateTimeFormatter.ofPattern`
in the Compose layer. The list itself is `ORDER BY date_fetch DESC` (per
the view), so the separators naturally fall in reverse-chronological
order.

## Key files

### Library update jobs (`app/src/main/java/eu/kanade/tachiyomi/data/library/`)

| File                                              | Role                                                       |
|---------------------------------------------------|------------------------------------------------------------|
| `manga/MangaLibraryUpdateJob.kt`                  | WorkManager worker + `setupTask`/`startNow`/`stop`         |
| `manga/MangaLibraryUpdateNotifier.kt`             | Progress + new-chapters + error notifications              |
| `manga/MangaMetadataUpdateJob.kt`                 | Cover/metadata refresh job                                 |
| `anime/AnimeLibraryUpdateJob.kt`                  | Anime twin                                                 |
| `anime/AnimeLibraryUpdateNotifier.kt`             | Anime twin                                                 |
| `anime/AnimeMetadataUpdateJob.kt`                 | Anime twin                                                 |

### Updates UI (`app/src/main/java/eu/kanade/tachiyomi/ui/updates/`)

| File                                              | Role                                                       |
|---------------------------------------------------|------------------------------------------------------------|
| `UpdatesTab.kt`                                   | Voyager tab; hosts the two pages; onReselect → Downloads   |
| `manga/MangaUpdatesTab.kt`                        | Per-page Composable; wires dialogs + events                |
| `manga/MangaUpdatesScreenModel.kt`                | State, flow subscription, download/selection actions       |
| `anime/AnimeUpdatesTab.kt`                        | Anime twin                                                 |
| `anime/AnimeUpdatesScreenModel.kt`                | Anime twin (adds `SHOW_QUALITIES` action, `fillermark`)    |

### Domain (`domain/src/main/java/tachiyomi/domain/updates/`)

| File                                                          | Role                              |
|---------------------------------------------------------------|-----------------------------------|
| `manga/model/MangaUpdatesWithRelations.kt`                    | Joined row model                  |
| `manga/repository/MangaUpdatesRepository.kt`                  | Repo interface                    |
| `manga/interactor/GetMangaUpdates.kt`                         | `subscribe(instant)` / `subscribe(read, after)` / `await` |
| `anime/model/AnimeUpdatesWithRelations.kt`                    | Anime twin (adds `fillermark`, `lastSecondSeen`, `totalSeconds`) |
| `anime/repository/AnimeUpdatesRepository.kt`                  | Anime twin                        |
| `anime/interactor/GetAnimeUpdates.kt`                         | Anime twin                        |

### Data (`data/src/main/`)

| File                                                                    | Role                              |
|-------------------------------------------------------------------------|-----------------------------------|
| `sqldelight/view/updatesView.sq`                                        | Manga updates view + queries      |
| `sqldelightanime/view/animeupdatesView.sq`                              | Anime updates view + queries      |
| `java/tachiyomi/data/updates/manga/MangaUpdatesRepositoryImpl.kt`       | Repo impl                         |
| `java/tachiyomi/data/updates/anime/AnimeUpdatesRepositoryImpl.kt`       | Anime twin                        |

### Preferences (`domain/src/main/java/tachiyomi/domain/library/service/LibraryPreferences.kt`)

| Preference                            | Type / default                    | Role                                            |
|---------------------------------------|-----------------------------------|-------------------------------------------------|
| `autoUpdateInterval()`                | Int hours, default 0 (off)        | Periodic job interval                           |
| `autoUpdateDeviceRestrictions()`      | `Set<String>` (`{wifi}`)          | WiFi / not-metered / charging constraints       |
| `autoUpdateItemRestrictions()`        | `Set<String>` (all 4 enabled)     | Per-item skip rules                             |
| `autoUpdateMetadata()`                | Boolean, default false            | Refresh metadata during update                  |
| `lastUpdatedTimestamp()`              | Long (app-state key)              | "Last updated" display                          |
| `newMangaUpdatesCount()` / `newAnimeUpdatesCount()` | Int                | Unread badge on Updates tab                     |
| `mangaUpdateCategories()` / `animeUpdateCategories()` | `Set<String>`   | Categories to include in update                 |
| `mangaUpdateCategoriesExclude()` / `animeUpdateCategoriesExclude()` | `Set<String>` | Categories to exclude                           |
| `updateSeasonOnLibraryUpdate()`       | Boolean, default false            | Anime: also fetch season sub-entries            |

## See also

- [`library-management.md`](library-management.md) — the Library UI that triggers manual updates and shows the result.
- [`notifications.md`](notifications.md) — the notification channels (`CHANNEL_LIBRARY_PROGRESS`, `CHANNEL_NEW_CHAPTERS_EPISODES`, `CHANNEL_LIBRARY_ERROR`) and the `NotificationReceiver` actions.
- [`download-manager.md`](download-manager.md) — the `filterChaptersForDownload` / `downloadChapters` calls the update job makes.
- [`../02-modules/data.md`](../02-modules/data.md) — the `updatesView` / `animeupdatesView` views in context.
- [`updater.md`](updater.md) — the unrelated app-self-update mechanism (checks GitHub for new Aniyomi APKs).
- [`extensions-update.md`](extensions-update.md) — checking for new versions of installed extensions.
