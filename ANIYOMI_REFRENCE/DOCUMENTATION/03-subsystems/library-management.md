# 03-subsystems / `library-management.md` — Library, Categories, Favorites

> The Library is the user's saved collection of manga and anime — entries whose
> `favorite` flag is `true`. Aniyomi ships a **dual** library (one tab for manga,
> one tab for anime) backed by two parallel databases, two parallel category
> tables, and a shared set of preferences. This doc covers how the Library screen
> is built, how categories work, how items get added/removed, how the periodic
> update check runs, and how covers are rendered.
>
> The actual *update checking* (detecting new chapters/episodes) is documented
> separately in [`updates.md`](updates.md); this doc focuses on the Library
> surface itself.

## What the Library is

The Library is the user's saved manga + anime. An entry is "in the library"
when its `favorite` column is `1` in the `mangas` (or `animes`) table — see
[`02-modules/data.md`](../02-modules/data.md) for the schema. Library membership
is the only thing the `favorite` flag means: it does not affect read/seen
status, downloads, or tracking.

Two distinct flows consume the library:

```
                    ┌─────────────────────────────────────────┐
                    │  mangas.favorite = 1 / animes.favorite = 1│
                    └─────────────┬───────────────────────────┘
                                  │
              ┌───────────────────┼────────────────────┐
              ▼                                         ▼
   LibraryScreen (UI)                       MangaLibraryUpdateJob /
   - filter / sort / search                 AnimeLibraryUpdateJob (WorkManager)
   - categories as tabs                     - iterates library
   - "continue reading" buttons             - asks each source for new chapters/episodes
   - bulk mark read / download / delete     - writes new items into chapters/episodes
                                            - posts update notifications
```

## The dual manga/anime library

Aniyomi inherits Tachiyomi's manga library and adds a parallel anime library.
The two are **completely separate** at the data layer (two SQLDelight
databases, see [`data.md`](../02-modules/data.md)) but are surfaced through
near-identical UI code. The bottom-navigation `HomeScreen` exposes two library
tabs:

| Side  | Tab class         | Title string                       | Default tab index |
|-------|-------------------|------------------------------------|-------------------|
| Anime | `AnimeLibraryTab` | `AYMR.strings.label_anime_library` | 0 (default home)  |
| Manga | `MangaLibraryTab` | `AYMR.strings.label_manga_library` | 1 (or 5 if hidden behind "More" via `NavStyle.MOVE_MANGA_TO_MORE`) |

Both tabs are `data object … : Tab` (Voyager tab) and follow the same template:

1. A `*LibraryTab.Content()` Composable that wires up a `*LibraryScreenModel`
   + `*LibrarySettingsScreenModel`.
2. A `Scaffold` with a `LibraryToolbar` (search, filter, refresh, global update,
   random-entry, selection actions) and a `LibraryBottomActionMenu` (visible in
   selection mode: change category, mark read/unread, download, delete).
3. A `*LibraryContent` Composable (in `:app`'s `eu.kanade.presentation.library`
   package) that renders a paged category grid/list.
4. Three dialogs: `SettingsSheet` (filter & sort), `ChangeCategory`, `Delete*`.

The same `LibraryToolbar`, `LibraryBottomActionMenu`, `ChangeCategoryDialog`,
and `DeleteLibraryEntryDialog` components are shared between the two sides
(parameterised by an `isManga: Boolean` flag where needed).

There is **no single "LibraryScreen"** — the two tabs ARE the library. Each is
registered as a Voyager tab in `HomeScreen` and shown via Voyager's
`LocalTabNavigator`.

## Categories (manga + anime, parallel)

Categories are user-defined folders for organizing library entries. They are
fully parallel: manga categories live in the manga database (`categories` +
`mangas_categories`), anime categories live in the anime database
(`categories` + `animes_categories`). The two are unrelated — a "Reading"
manga category and a "Watching" anime category can both exist with different
ids.

### Category model

The shared `tachiyomi.domain.category.model.Category` data class is used by
both sides (it carries an `id`, `name`, `order`, `flags`, `hidden`).
`Category.UNCATEGORIZED_ID = 0L` is the synthetic "Default" / "Uncategorized"
category — it always exists (the `categories.sq` schema inserts it with
`INSERT OR IGNORE` and a trigger forbids its deletion). `isSystemCategory`
returns `true` for id `0`.

The `flags: Long` field is a packed bitset that stores the per-category
display mode and sort mode (using the `Flag` / `FlagWithMask` helpers in
`tachiyomi.domain.library.model`). `MangaLibrarySort.valueOf(flags)` decodes
the sort; `LibraryDisplayMode` is global (not per-category) unless
`categorizedDisplaySettings()` is enabled.

### Category interactors (each side has all of these)

| Manga interactor                  | Anime interactor                  | Purpose                                   |
|-----------------------------------|-----------------------------------|-------------------------------------------|
| `CreateMangaCategoryWithName`     | `CreateAnimeCategoryWithName`     | Insert a new row, return id (or error)    |
| `RenameMangaCategory`             | `RenameAnimeCategory`             | `UPDATE categories SET name = …`          |
| `DeleteMangaCategory`             | `DeleteAnimeCategory`             | Delete row (cascade clears `*_categories`)|
| `ReorderMangaCategory`            | `ReorderAnimeCategory`            | Swap `sort` values to reorder             |
| `HideMangaCategory`               | `HideAnimeCategory`               | Toggle `hidden` flag                      |
| `GetMangaCategories`              | `GetAnimeCategories`              | Subscribe to all categories (incl. hidden)|
| `GetVisibleMangaCategories`       | `GetVisibleAnimeCategories`       | Subscribe to non-hidden categories        |
| `SetMangaCategories`              | `SetAnimeCategories`              | Replace the `*_categories` rows for one entry |
| `SetSortModeForMangaCategory`     | `SetSortModeForAnimeCategory`     | Update `flags` (sort) on one category     |
| `SetMangaDisplayMode`             | `SetAnimeDisplayMode`             | Update global display mode preference     |
| `ResetMangaCategoryFlags`         | `ResetAnimeCategoryFlags`         | Reset all categories' flags to default    |
| `UpdateMangaCategory`             | `UpdateAnimeCategory`             | Generic update (used by reorder)          |

### Category UI

`CategoriesTab` (`ui/category/CategoriesTab.kt`) is a single Voyager tab that
hosts a `TabbedScreen` with two pages: `animeCategoryTab()` (page 0) and
`mangaCategoryTab()` (page 1). The `CategoriesTab.showMangaCategory()`
channel lets other screens (e.g. the library's "Edit categories" button) ask
the tab to scroll to the manga page on open.

Each page owns a `*CategoryScreenModel` (`StateScreenModel`) that subscribes
to `Get*Categories`/`GetVisible*Categories` (chosen by the
`hideHiddenCategoriesSettings()` preference) and exposes
`createCategory / renameCategory / deleteCategory / hideCategory /
changeOrder` actions. The screen surfaces `Create / Rename / Delete` dialogs
(`CategoryCreateDialog`, `CategoryRenameDialog`, `CategoryDeleteDialog`) and
emits `*CategoryEvent.LocalizedMessage` events for errors.

## The LibraryScreenModel, state, filtering, sorting

`MangaLibraryScreenModel` and `AnimeLibraryScreenModel` are
`StateScreenModel<State>` subclasses — the same shape mirrored for both sides.
They each hold:

- The full library as `Map<Category, List<*LibraryItem>>` (the `*LibraryMap`
  typealias).
- The current `searchQuery`, `selection` (`PersistentList<LibraryManga>` /
  `PersistentList<LibraryAnime>`), `hasActiveFilters`, `showCategoryTabs`,
  `show*Count`, `show*ContinueButton`, and an optional `Dialog`.
- `activeCategoryIndex` — a `PreferenceMutableState<Int>` backed by
  `libraryPreferences.lastUsed*Category()` so the last-viewed category
  survives process death.

### The build pipeline

The screen model's `init` block wires a multi-source `combine` flow:

```
 searchQuery (debounced)   getLibraryFlow()         getTracksPerManga.subscribe()
        │                  │   └─ getLibraryManga.subscribe()    │
        │                  │      + library item prefs flow      │
        │                  │      + downloadCache.changes         │
        │                  └── groupBy Category → Map<Category, List<Item>>
        ▼                                                        ▼
        └──────────────►  applyFilters(trackMap, trackingFilter) ◄┘
                                    │
                                    ▼
                          applySort(trackMap, loggedInTrackerIds)
                                    │
                                    ▼
                filter by searchQuery (if non-null) via Item.matches()
                                    │
                                    ▼
                          State(library = …, isLoading = false)
```

A second `combine` watches the three display preferences
(`categoryTabs`, `categoryNumberOfItems`, `showContinueViewingButton`) and
pushes them into `State`. A third `combine` derives `hasActiveFilters` from
the six tri-state filter prefs plus the per-tracker tri-state filters.

### Filters

Each side supports the same six `TriState` filters (configured in
`LibraryPreferences`, applied in `*LibraryScreenModel.applyFilters`):

| Filter                  | Manga pref                        | Anime pref                        |
|-------------------------|-----------------------------------|-----------------------------------|
| Downloaded              | `filterDownloadedManga()`         | `filterDownloadedAnime()`         |
| Unread / Unseen         | `filterUnread()`                  | `filterUnseen()`                  |
| Started                 | `filterStartedManga()`            | `filterStartedAnime()`            |
| Bookmarked              | `filterBookmarkedManga()`         | `filterBookmarkedAnime()`         |
| Completed               | `filterCompletedManga()`          | `filterCompletedAnime()`          |
| Outside release period  | `filterIntervalCustom()`          | `filterIntervalCustom()` (shared) |

Plus per-tracker filters: `filterTracked*(trackerId.toInt())` returns a
`TriState` for each logged-in tracker; entries are included/excluded based on
whether they have a track row for that tracker. The global `downloadedOnly`
preference forces the downloaded filter to `ENABLED_IS`.

`TriState` cycles `DISABLED → ENABLED_IS → ENABLED_NOT` (the `next()` helper
in `applyFilter` from `tachiyomi.domain.entries.applyFilter`).

### Sort

`MangaLibrarySort` / `AnimeLibrarySort` (in
`tachiyomi.domain.library.{manga,anime}.model`) are `FlagWithMask` sealed
classes with the same `Type` enum and an `Ascending` / `Descending`
`Direction`:

`Alphabetical`, `LastRead`, `LastUpdate`, `UnreadCount` / `UnseenCount`,
`TotalChapters` / `TotalEpisodes`, `LatestChapter` / `LatestEpisode`,
`ChapterFetchDate` / `EpisodeFetchDate`, `DateAdded`, `TrackerMean`,
`Random`.

The active sort is **per-category** (read from `Category.flags` via the
`Category?.sort` extension). `Random` is special: it uses a per-side seed
preference (`randomMangaSortSeed` / `randomAnimeSortSeed`) so the shuffle is
stable until the user re-picks it. `TrackerMean` averages the 10-point scores
returned by each logged-in tracker's `get10PointScore(track)` for the entry.
The alphabetical comparator is always appended as a tiebreaker
(`thenComparator(sortAlphabetically)`).

### Search

`*LibraryItem.matches(constraint: String)` is the per-item search predicate.
It supports:

- `id:<long>` — exact id match.
- Plain substring match on `title`, `author`, `artist`, `description`.
- Comma-separated `genre,source` tokens (each negatable with a `-` prefix)
  matched against the source's display name and the entry's genre list.

The query is debounced (`SEARCH_DEBOUNCE_MILLIS`) in the screen model's
`init` flow before being applied. The toolbar also offers a "global search"
button that pushes a `Global*MangaSearchScreen` / `Global*AnimeSearchScreen`
with the current query.

### Display modes & columns

`LibraryDisplayMode` (sealed interface in `tachiyomi.domain.library.model`):
`CompactGrid`, `ComfortableGrid`, `List`, `CoverOnlyGrid` (default
`CompactGrid`). It is a single global preference (`displayMode()`) unless
`categorizedDisplaySettings()` is on, in which case each category carries its
own display mode in its `flags`.

Grid column counts are orientation-specific and per-side:
`mangaPortraitColumns` / `mangaLandscapeColumns` /
`animePortraitColumns` / `animeLandscapeColumns`. `0` means "auto" (system
font scale + screen width).

### Selection mode

Long-press on a card enters selection mode. `toggleSelection`,
`toggleRangeSelection` (with haptic feedback), `selectAll(index)`,
`invertSelection(index)`, and `clearSelection` operate on a
`PersistentList<LibraryManga>` / `PersistentList<LibraryAnime>`. While in
selection mode the bottom `LibraryBottomActionMenu` appears with:

- **Change category** → opens `ChangeCategoryDialog` (preselects the
  intersection of categories across the selection via
  `getCommonCategories`, marks non-common but present ones as
  `TriState.Exclude`).
- **Mark as viewed / unviewed** → `setReadStatus.await(...)` /
  `setSeenStatus.await(...)` (see [`download-manager.md`](download-manager.md)
  for the download side).
- **Download** → `runDownloadActionSelection(action)` with
  `DownloadAction.{NEXT_1, NEXT_5, NEXT_10, NEXT_25, UNVIEWED}_ITEMS`. Hidden
  for local-source entries (`isLocal()`).
- **Delete** → opens `DeleteLibraryEntryDialog`, which can both remove from
  library (set `favorite = false`) and delete downloaded files.

## Adding / removing library items

The **`favorite` flag** is the only thing that makes a manga/anime a library
member. The flow is:

```
MangaScreen / AnimeScreen  ── "Add to library" button ──►  UpdateManga.awaitUpdateFavorite(id, true)
                                                              │
                                                              ▼
                                              MangaUpdate(id, favorite = true,
                                                          dateAdded = now())
                                                              │
                                                              ▼
                                              MangaRepository.updateManga  →  Database.mangas
```

`UpdateManga.awaitUpdateFavorite` (`app/.../domain/entries/manga/interactor/
UpdateManga.kt`) sets `favorite = true` and `dateAdded = now()` (or
`favorite = false` + `dateAdded = 0` on removal). The anime twin is
identical.

### Category assignment on add

When the user adds an entry, the screen checks the default-category
preference (`libraryPreferences.defaultMangaCategory()` /
`defaultAnimeCategory()`) and either:

1. **A specific default category is set** — favorite the entry and call
   `SetMangaCategories.await(mangaId, [defaultCategoryId])`.
2. **Default is "Default" (id 0) or no categories exist** — favorite and
   call `SetMangaCategories.await(mangaId, [])` (entry lands in the
   synthetic "Default" page).
3. **No default set and categories exist** — show `ChangeCategoryDialog`
   first, then favorite on confirm.

The library's bulk "Change category" action calls
`setMangaCategories(mangaList, addIds, removeIds)` which computes the new
category-id set per entry as `(current − remove) ∪ add` and writes it.

### Removal

Removing from the library (`removeMangas` / `removeAnimes` screen-model
methods) does three things in one `launchNonCancellable`:

1. Calls `it.removeCovers(coverCache)` to drop the cached cover (so a future
   re-add re-downloads it).
2. Issues `MangaUpdate(favorite = false, id = …)` for each entry via
   `updateManga.awaitAll(...)`.
3. Optionally also deletes downloaded chapters/episodes on disk via
   `downloadManager.deleteManga(manga, source)`.

### Adding from the History screen

The History ScreenModel also exposes `addFavorite(mangaId)` /
`addFavorite(animeId)` which performs duplicate detection
(`GetDuplicateLibraryManga` / `GetDuplicateLibraryAnime`) and offers a
migrate-or-keep-both dialog if a duplicate exists. After adding it also calls
`addTracks.bindEnhancedTrackers(manga, source)` to auto-bind any enhanced
trackers (Komga/Kavita/Suwayomi for manga, Jellyfin for anime — see
[`trackers.md`](trackers.md)).

## Library update service

The periodic "check the library for new chapters/episodes" job lives in
`app/src/main/java/eu/kanade/tachiyomi/data/library/{manga,anime}/`. It is
documented in detail in [`updates.md`](updates.md); the summary from the
library's perspective:

- **`MangaLibraryUpdateJob` / `AnimeLibraryUpdateJob`** — `CoroutineWorker`
  subclasses scheduled with WorkManager. Two work names each: an *auto*
  periodic job (`LibraryUpdate-auto` / `AnimeLibraryUpdate-auto`) and a
  *manual* one-shot (`LibraryUpdate-manual` / `AnimeLibraryUpdate-manual`).
- **`setupTask(context, interval?)`** — re-arms the periodic job from
  `LibraryPreferences.autoUpdateInterval()` (in hours; `0` = disabled).
  Constraints come from `autoUpdateDeviceRestrictions()`
  (`DEVICE_ONLY_ON_WIFI`, `DEVICE_NETWORK_NOT_METERED`, `DEVICE_CHARGING`).
- **`startNow(context, category?)`** — manual one-shot for a specific
  category or the whole library; returns `false` if another update is
  already running. Wired to the toolbar's refresh button and "Update
  library" global action.
- **`stop(context)`** — cancels the running job and re-enqueues the periodic
  one if it was the auto job.
- The job iterates the library, asks each source for its chapter/episode
  list, calls `SyncChaptersWithSource` / `SyncEpisodesWithSource` to diff &
  persist, optionally auto-downloads new items, and finally calls
  `*LibraryUpdateNotifier.showUpdateNotifications(newUpdates)`.
- **`*LibraryUpdateNotifier`** — posts a progress notification (with a
  Cancel action), a per-manga/anime "new chapters/episodes" notification
  (with Mark-as-read / View / Download actions, cover icon via Coil), and an
  error notification with a link to a written-to-cache error log file.
- **`*MetadataUpdateJob`** — a separate WorkManager job that refreshes
  cover/title/description from the source for every library entry (no
  chapter/episode fetching); triggered manually from Settings.

## Covers / thumbnails display

Covers are rendered by **Coil 3** through dedicated `Fetcher` and `Keyer`
implementations in `app/src/main/java/eu/kanade/tachiyomi/data/coil/`:

| File                     | Role                                                              |
|--------------------------|-------------------------------------------------------------------|
| `MangaCoverFetcher.kt`   | `Fetcher<Manga>`: resolves cover URL → HTTP or cached file        |
| `MangaCoverKeyer.kt`     | `Keyer`: stable Coil cache key from manga id + last-modified      |
| `AnimeImageFetcher.kt`   | `Fetcher<Anime>`: twin of `MangaCoverFetcher`                     |
| `AnimeCoverKeyer.kt`     | `Keyer` for `Anime`                                               |
| `TachiyomiImageDecoder.kt`| Custom Coil decoder (handles animated WebP/AVIF/GIF etc.)        |
| `BufferedSourceFetcher.kt`| Generic `BufferedSource` → `ImageSource` fetcher                 |
| `Utils.kt`               | `Manga.asMangaCover()` / `Anime.asAnimeCover()` adapters          |

### Cover cache files

`MangaCoverCache` (`data/cache/MangaCoverCache.kt`) and its anime twin
`AnimeCoverCache` are on-disk caches rooted at `<cache>/covers/` and
`<cache>/covers/custom/`. Library entries' covers are cached by
`md5(thumbnailUrl)` (`DiskUtil.hashKeyForDisk`); custom covers (set by the
user from the reader/player) are cached by `md5(mangaId)`. The
`MangaCoverFetcher` checks the custom-cover file first (if
`USE_CUSTOM_COVER_KEY` extra is set, default `true`), then the cached file,
then falls back to HTTP via the source's `imageRequest` (with proper
referer/headers for `HttpSource`). Non-library entries skip the disk cache
and go straight through Coil's `DiskCache`.

The `MangaCover` / `AnimeCover` domain models
(`tachiyomi.domain.entries.{manga,anime}.model.*Cover`) are the lightweight
payloads Coil receives — they carry `mangaId`/`sourceId`/`isFavorite`/`url`/
`lastModified`. The `asMangaCover()` / `asAnimeCover()` extension functions
adapt the full `Manga` / `Anime` model into these.

When a cover URL changes (during a metadata refresh), `UpdateManga.
awaitUpdateFromSource` bumps `coverLastModified` and calls
`coverCache.deleteFromCache(manga, false)`, which forces Coil to re-fetch.
The `coverLastModified` field is part of the Coil cache key (via the
`Keyer`), so changing it invalidates the old entry.

## Key files

### Library UI (`app/src/main/java/eu/kanade/tachiyomi/ui/library/`)

| File                                                       | Role                                                       |
|------------------------------------------------------------|------------------------------------------------------------|
| `manga/MangaLibraryTab.kt`                                 | Voyager tab; toolbar, bottom action menu, dialogs          |
| `manga/MangaLibraryScreenModel.kt`                         | State, filters, sort, selection, category actions          |
| `manga/MangaLibrarySettingsScreenModel.kt`                 | Filter/sort/display-mode settings sheet backing model      |
| `manga/MangaLibraryItem.kt`                                | Per-entry wrapper + `matches(searchQuery)` predicate       |
| `anime/AnimeLibraryTab.kt`                                 | Anime twin of `MangaLibraryTab`                            |
| `anime/AnimeLibraryScreenModel.kt`                         | Anime twin of `MangaLibraryScreenModel`                    |
| `anime/AnimeLibrarySettingsScreenModel.kt`                 | Anime twin of the settings sheet model                     |
| `anime/AnimeLibraryItem.kt`                                | Anime twin of `MangaLibraryItem`                           |

### Category UI (`app/src/main/java/eu/kanade/tachiyomi/ui/category/`)

| File                                                       | Role                                                       |
|------------------------------------------------------------|------------------------------------------------------------|
| `CategoriesTab.kt`                                         | Tabbed screen hosting both anime + manga category pages    |
| `manga/MangaCategoryTab.kt`                                | Composable page; wires dialogs to screen model             |
| `manga/MangaCategoryScreenModel.kt`                        | CRUD operations + state (Loading/Success) + events         |
| `anime/AnimeCategoryTab.kt`                                | Anime twin                                                 |
| `anime/AnimeCategoryScreenModel.kt`                        | Anime twin                                                 |

### Library update service (`app/src/main/java/eu/kanade/tachiyomi/data/library/`)

| File                                                       | Role                                                       |
|------------------------------------------------------------|------------------------------------------------------------|
| `manga/MangaLibraryUpdateJob.kt`                           | WorkManager `CoroutineWorker`; scheduling + chapter fetch  |
| `manga/MangaLibraryUpdateNotifier.kt`                      | Progress / new-chapters / error notifications              |
| `manga/MangaMetadataUpdateJob.kt`                          | Cover/metadata refresh job                                 |
| `anime/AnimeLibraryUpdateJob.kt`                           | Anime twin of `MangaLibraryUpdateJob`                      |
| `anime/AnimeLibraryUpdateNotifier.kt`                      | Anime twin of the notifier                                 |
| `anime/AnimeMetadataUpdateJob.kt`                          | Anime twin of the metadata job                             |

### Domain (`domain/src/main/java/tachiyomi/domain/`)

| File                                                       | Role                                                       |
|------------------------------------------------------------|------------------------------------------------------------|
| `category/model/Category.kt`                               | Shared `Category` data class                               |
| `category/model/CategoryUpdate.kt`                         | Partial-update payload                                     |
| `category/{manga,anime}/interactor/*`                      | 12 interactors per side (see table above)                  |
| `category/{manga,anime}/repository/*CategoryRepository.kt` | Repository interfaces                                      |
| `library/service/LibraryPreferences.kt`                    | All library/category/sort/filter preferences               |
| `library/model/LibraryDisplayMode.kt`                     | Display-mode sealed interface                              |
| `library/model/Flag.kt`                                    | `FlagWithMask` bit-packing helpers                         |
| `library/manga/LibraryManga.kt`                            | `Manga + counts` derived model                             |
| `library/anime/LibraryAnime.kt`                            | `Anime + counts` derived model                             |
| `library/manga/model/MangaLibrarySortMode.kt`              | Sort type + direction                                      |
| `library/anime/model/AnimeLibrarySortMode.kt`              | Anime twin                                                 |
| `entries/{manga,anime}/model/*Cover.kt`                    | Coil cover payloads                                        |

### Data (`data/src/main/sqldelight/` and `sqldelightanime/`)

| File                                                       | Role                                                       |
|------------------------------------------------------------|------------------------------------------------------------|
| `data/categories.sq`                                       | Manga `categories` table + system-category trigger         |
| `data/mangas_categories.sq`                                | Manga entry↔category join table                            |
| `sqldelightanime/dataanime/categories.sq`                  | Anime twin                                                 |
| `sqldelightanime/dataanime/animes_categories.sq`           | Anime twin                                                 |

### Covers (`app/src/main/java/eu/kanade/tachiyomi/data/`)

| File                                                       | Role                                                       |
|------------------------------------------------------------|------------------------------------------------------------|
| `coil/MangaCoverFetcher.kt` + `MangaCoverKeyer.kt`         | Coil fetcher/keyer for `Manga`                             |
| `coil/AnimeImageFetcher.kt` + `AnimeCoverKeyer.kt`         | Coil fetcher/keyer for `Anime`                             |
| `cache/MangaCoverCache.kt`                                 | On-disk cover cache (library + custom covers)              |
| `cache/AnimeCoverCache.kt`                                 | Anime twin                                                 |
| `cache/AnimeBackgroundCache.kt`                            | On-disk background-image cache (anime only)                |

## See also

- [`updates.md`](updates.md) — the library-update Worker in depth (scheduling, fetch window, notifications).
- [`history.md`](history.md) — the "continue reading" buttons query history to find the next unread chapter/episode.
- [`trackers.md`](trackers.md) — the per-tracker library filters and the auto-bind-on-add flow.
- [`../02-modules/data.md`](../02-modules/data.md) — the `categories` / `mangas_categories` / `animes_categories` schemas.
- [`../02-modules/domain.md`](../02-modules/domain.md) — the Interactor pattern used by every category/library use case.
- [`../04-data-models/preferences-catalog.md`](../04-data-models/preferences-catalog.md) — every `LibraryPreferences` key.
- [`../05-key-flows/add-to-library.md`](../05-key-flows/add-to-library.md) — end-to-end "add to library" user flow.
