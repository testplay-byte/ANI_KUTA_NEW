# 02-modules / `domain.md` — The `:domain` Module

> The pure-Kotlin domain layer of Aniyomi: domain models, use cases (called
> *Interactors*), and repository **interfaces**. This is the innermost ring of the
> layered architecture — it depends on no Android UI code and is implemented by the
> `:data` module (see [`data.md`](data.md)).

## Purpose

The `:domain` module holds the **business vocabulary** of the application:

- **Domain models** — immutable data classes (`Manga`, `Anime`, `Chapter`,
  `Episode`, `Track`, `Category`, `History`, `Source`, …) that the rest of the
  app operates on.
- **Repository interfaces** — pure Kotlin contracts (`MangaRepository`,
  `AnimeRepository`, `ChapterRepository`, `EpisodeRepository`, …). The
  implementations live in `:data` and are bound by Injekt at app startup.
- **Interactors (use cases)** — single-purpose classes such as `GetManga`,
  `GetChaptersByMangaId`, `SetMangaCategories`, `GetNextChapters`,
  `FilterChaptersForDownload`, each named for *one* business operation.
- **Domain services & preferences** — small helpers and `*Preferences` classes
  (`LibraryPreferences`, `DownloadPreferences`, `StoragePreferences`,
  `BackupPreferences`) that wrap the `:core:common` `PreferenceStore`.
- **Pure logic** — `ChapterRecognition`, `EpisodeRecognition`,
  `ChapterSorter`/`EpisodeSorter`/`SeasonSorter`, `MissingChapters`/
  `MissingEpisodes` (numbering-gap detection), `MangaFetchInterval`/
  `AnimeFetchInterval`.

The module is intentionally **side-by-side duplicated** for manga vs anime. This
is Aniyomi's central design pattern: every concept that exists for manga also
exists in a parallel anime package (and a parallel `season` package only on the
anime side). See [The dual manga/anime pattern](#the-dual-mangaanime-pattern)
below.

## Build configuration

The module lives at `../ANIYOMI/domain/`. Its
`../ANIYOMI/domain/build.gradle.kts` reads:

```kotlin
plugins {
    id("mihon.library")               // shared Android-library convention plugin
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "tachiyomi.domain"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(projects.sourceApi)            // KMP source/extension contract
    implementation(projects.core.common)          // PreferenceStore, utilities, logging

    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)
    implementation(kotlinx.bundles.serialization)

    implementation(libs.unifile)                  // SAF-aware file handle for StorageManager

    api(libs.sqldelight.android.paging)           // PagingSource type used in repo interfaces

    compileOnly(libs.compose.stablemarker)        // @Immutable annotation only

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
}
```

### Notes on "pure-Kotlin"

- The plugin is `mihon.library` + `kotlin("android")`, so the module is
  technically an **Android library** module (it has an `AndroidManifest.xml`
  at `../ANIYOMI/domain/src/main/AndroidManifest.xml`). It is **not** a
  plain `org.jetbrains.kotlin.jvm` library.
- However, its dependency list contains **no Android UI libraries** — no
  `androidx.activity`, `androidx.fragment`, `androidx.compose.ui`,
  `androidx.lifecycle`, `material`, etc. So at runtime it is effectively
  UI-free; only the `@Immutable` annotation from `compose-stablemarker`
  (`compileOnly`) leaks in, and that is a no-op marker annotation.
- `api(libs.sqldelight.android.paging)` exposes `androidx.paging.PagingSource`
  because some repository interfaces (e.g.
  `MangaSourceRepository.searchManga(...)`) return a `PagingSource` to the
  presentation layer. The actual database is **not** depended on here — only
  the paging *type*.
- `projects.sourceApi` is pulled in because the domain models embed
  `UpdateStrategy` / `AnimeUpdateStrategy` and `FetchType` enums from the
  source-api (e.g. `Manga.updateStrategy: UpdateStrategy`).

## Source layout

Root: `../ANIYOMI/domain/src/main/java/`

There are three package roots:

| Package root | Origin | What lives here |
|---|---|---|
| `tachiyomi.domain.*` | Tachiyomi/Mihon (inherited) | Most of the manga-side domain, and shared/common pieces (entries, items, library, category, history, source, track, updates, release, storage, download, backup, custombuttons). |
| `mihon.domain.*` | Mihon (inherited) | Newer Mihon features: `extensionrepo` (extension-repo management) and `upcoming` (upcoming releases). Also two filter interactors `FilterChaptersForDownload` / `FilterEpisodesForDownload`. |
| `aniyomi.domain.*` | Aniyomi (added) | Anime-only extras that don't fit the dual-package pattern: `aniyomi.domain.anime.SeasonAnime`, `aniyomi.domain.anime.SeasonDisplayMode`. |

There is also a small `src/test/java/` tree with unit tests
(`MissingItemsTest`, `ChapterRecognitionTest`, `LibraryFlagsTest`,
`MangaFetchIntervalTest`, `AnimeFetchIntervalTest`,
`GetApplicationReleaseTest`).

## The dual manga/anime pattern

Almost every concept is mirrored across a `manga` and an `anime` package. The
anime side is Aniyomi's addition; the manga side is inherited from
Mihon/Tachiyomi. The anime side additionally has a `season` package (an anime
may have seasons under a parent anime — a concept that has no manga analogue).

### Parallel package pairs

| Manga package | Anime package | Notes |
|---|---|---|
| `tachiyomi.domain.entries.manga` | `tachiyomi.domain.entries.anime` | Models `Manga`/`MangaUpdate`/`MangaCover` ↔ `Anime`/`AnimeUpdate`/`AnimeCover`; interactors `GetManga`, `GetAnime`, `GetLibraryManga`, `GetLibraryAnime`, `NetworkToLocalManga`, `NetworkToLocalAnime`, `GetMangaWithChapters`, `GetAnimeWithEpisodesAndSeasons`, `MangaFetchInterval`, `AnimeFetchInterval`, `SetMangaChapterFlags`, `SetAnimeEpisodeFlags`/`SetAnimeSeasonFlags`, `ResetMangaViewerFlags`, `ResetAnimeViewerFlags`, `GetDuplicateLibraryManga`, `GetDuplicateLibraryAnime`. Repos `MangaRepository` ↔ `AnimeRepository`. |
| `tachiyomi.domain.items.chapter` | `tachiyomi.domain.items.episode` | Models `Chapter`/`ChapterUpdate`/`NoChaptersException` ↔ `Episode`/`EpisodeUpdate`/`NoEpisodesException`; services `ChapterRecognition`/`ChapterSorter`/`MissingChapters` ↔ `EpisodeRecognition`/`EpisodeSorter`/`MissingEpisodes`; interactors `GetChapter`/`GetChaptersByMangaId`/`UpdateChapter`/`ShouldUpdateDbChapter`/`SetMangaDefaultChapterFlags` ↔ `GetEpisode`/`GetEpisodesByAnimeId`/`UpdateEpisode`/`ShouldUpdateDbEpisode`/`SetAnimeDefaultEpisodeFlags`; repos `ChapterRepository` ↔ `EpisodeRepository`. |
| — (no analogue) | `tachiyomi.domain.items.season` | Anime-only: `SeasonRecognition`, `SeasonSorter`, `GetAnimeSeasonsByParentId`, `SetAnimeDefaultSeasonFlags`, `ShouldUpdateDbSeason`. Seasons group episodes under a parent anime (the `parent_id` column on `animes`). |
| `tachiyomi.domain.source.manga` | `tachiyomi.domain.source.anime` | Models `Source`/`StubMangaSource`/`MangaSourceWithCount`/`Pin` ↔ `AnimeSource`/`StubAnimeSource`/`AnimeSourceWithIds`/`DeletableAnime`/`Pin`; services `MangaSourceManager` ↔ `AnimeSourceManager`; repos `MangaSourceRepository`/`MangaStubSourceRepository` ↔ `AnimeSourceRepository`/`AnimeStubSourceRepository`; interactors `GetRemoteManga`, `GetMangaSourcesWithNonLibraryManga` ↔ `GetRemoteAnime`, `GetAnimeSourcesWithNonLibraryAnime`. |
| `tachiyomi.domain.track.manga` | `tachiyomi.domain.track.anime` | Models `MangaTrack` ↔ `AnimeTrack`; interactors `GetMangaTracks`/`InsertMangaTrack`/`DeleteMangaTrack`/`GetTracksPerManga` ↔ `GetAnimeTracks`/`InsertAnimeTrack`/`DeleteAnimeTrack`/`GetTracksPerAnime`; repos `MangaTrackRepository` ↔ `AnimeTrackRepository`. |
| `tachiyomi.domain.download.manga` *(under `download.service`)* | `tachiyomi.domain.download.anime` *(under `download.service`)* | Shared `DownloadPreferences` covers both sides (it has both manga and anime preference getters), but download-*queue* state lives in `:app`'s `DownloadManager` (which is also duplicated: manga `DownloadManager` + anime `AnimeDownloadManager`). |
| `tachiyomi.domain.entries.manga` (manga library) | `tachiyomi.domain.entries.anime` (anime library) | `LibraryManga` (in `tachiyomi.domain.library.manga`) ↔ `LibraryAnime` (in `tachiyomi.domain.library.anime`); sort modes `MangaLibrarySort`/`MangaLibrarySortMode` ↔ `AnimeLibrarySort`/`AnimeLibrarySortMode`. |
| `tachiyomi.domain.category.manga` | `tachiyomi.domain.category.anime` | Interactors `CreateMangaCategoryWithName`/`RenameMangaCategory`/`DeleteMangaCategory`/`ReorderMangaCategory`/`HideMangaCategory`/`SetMangaCategories`/`SetSortModeForMangaCategory`/`SetMangaDisplayMode`/`GetMangaCategories`/`GetVisibleMangaCategories`/`UpdateMangaCategory`/`ResetMangaCategoryFlags` ↔ the same set for `Anime`. Shared `Category`/`CategoryUpdate` models live in `tachiyomi.domain.category.model`. |
| `tachiyomi.domain.history.manga` | `tachiyomi.domain.history.anime` | Models `MangaHistory`/`MangaHistoryUpdate`/`MangaHistoryWithRelations` ↔ `AnimeHistory`/`AnimeHistoryUpdate`/`AnimeHistoryWithRelations`; interactors `GetMangaHistory`/`UpsertMangaHistory`/`RemoveMangaHistory`/`GetNextChapters`/`GetTotalReadDuration` ↔ `GetAnimeHistory`/`UpsertAnimeHistory`/`RemoveAnimeHistory`/`GetNextEpisodes`; repos `MangaHistoryRepository` ↔ `AnimeHistoryRepository`. |
| `tachiyomi.domain.updates.manga` | `tachiyomi.domain.updates.anime` | Model `MangaUpdatesWithRelations` ↔ `AnimeUpdatesWithRelations`; interactor `GetMangaUpdates` ↔ `GetAnimeUpdates`; repos `MangaUpdatesRepository` ↔ `AnimeUpdatesRepository`. |
| `mihon.domain.extensionrepo.manga` | `mihon.domain.extensionrepo.anime` | See [Extension repo management](#extension-repo-management-mihondomainextensionrepo) below. |
| `mihon.domain.upcoming.manga` | `mihon.domain.upcoming.anime` | See [Upcoming releases](#upcoming-releases-mihondomainupcoming) below. |

### ASCII map of the parallel packages

```
tachiyomi.domain
├── entries
│   ├── manga/       (Manga, MangaUpdate, MangaCover, MangaRepository, GetManga, GetLibraryManga, …)
│   ├── anime/       (Anime, AnimeUpdate, AnimeCover, AnimeRepository, GetAnime, GetLibraryAnime, …)
│   ├── EntryCover   (shared)
│   └── TriState     (shared)
├── items
│   ├── chapter/     (Chapter, ChapterUpdate, ChapterRepository, ChapterRecognition, ChapterSorter, …)
│   ├── episode/     (Episode, EpisodeUpdate, EpisodeRepository, EpisodeRecognition, EpisodeSorter, …)
│   └── season/      (anime-only: SeasonRecognition, SeasonSorter, GetAnimeSeasonsByParentId, …)
├── source
│   ├── manga/       (Source, StubMangaSource, MangaSourceManager, MangaSourceRepository, …)
│   └── anime/       (AnimeSource, StubAnimeSource, AnimeSourceManager, AnimeSourceRepository, …)
├── track
│   ├── manga/       (MangaTrack, MangaTrackRepository, GetMangaTracks, InsertMangaTrack, …)
│   └── anime/       (AnimeTrack, AnimeTrackRepository, GetAnimeTracks, InsertAnimeTrack, …)
├── category
│   ├── manga/       (CreateMangaCategoryWithName, SetMangaCategories, MangaCategoryRepository, …)
│   ├── anime/       (CreateAnimeCategoryWithName, SetAnimeCategories, AnimeCategoryRepository, …)
│   └── model/       (shared Category, CategoryUpdate)
├── history
│   ├── manga/       (MangaHistory, GetMangaHistory, GetNextChapters, MangaHistoryRepository, …)
│   └── anime/       (AnimeHistory, GetAnimeHistory, GetNextEpisodes, AnimeHistoryRepository, …)
├── updates
│   ├── manga/       (MangaUpdatesWithRelations, GetMangaUpdates, MangaUpdatesRepository)
│   └── anime/       (AnimeUpdatesWithRelations, GetAnimeUpdates, AnimeUpdatesRepository)
├── library/         (shared LibraryPreferences, LibraryDisplayMode, Flag, LibraryManga, LibraryAnime)
├── release/         (Release, ReleaseService, GetApplicationRelease — single, app self-update)
├── storage/         (StoragePreferences, StorageManager — single)
├── download/service/(DownloadPreferences — single, covers both sides)
├── backup/service/  (BackupPreferences, PreferenceValues — single)
└── custombuttons/   (CustomButton, CustomButtonRepository, CreateCustomButton, …)

mihon.domain
├── extensionrepo/   (shared model ExtensionRepo, service ExtensionRepoService, exception)
│   ├── manga/       (MangaExtensionRepoRepository, CreateMangaExtensionRepo, …)
│   └── anime/       (AnimeExtensionRepoRepository, CreateAnimeExtensionRepo, …)
├── upcoming/
│   ├── manga/       (GetUpcomingManga)
│   └── anime/       (GetUpcomingAnime)
└── items/
    ├── chapter/     (FilterChaptersForDownload)
    └── episode/     (FilterEpisodesForDownload)

aniyomi.domain
└── anime/           (SeasonAnime, SeasonDisplayMode — anime-only extras)
```

## Domain models

These are simple immutable `data class`es. They are persisted by `:data`
through SQLDelight column adapters and mappers (see [`data.md`](data.md) and
[`../04-data-models/domain-models.md`](../04-data-models/domain-models.md)).

| Model | File | Notes |
|---|---|---|
| `Manga` | `entries/manga/model/Manga.kt` | `@Immutable`, `Serializable`. Carries `chapterFlags` (a packed bitmask of sort/display/filter flags) and `viewerFlags`. Has `expectedNextUpdate`, `sorting`, `displayMode`, `unreadFilter`, etc. as computed properties. Companion holds flag constants (`CHAPTER_SORTING_MASK`, `CHAPTER_SHOW_UNREAD`, …). |
| `MangaUpdate` | `entries/manga/model/MangaUpdate.kt` | Patch model for partial updates — every field is nullable; `:data`'s `update` SQL uses `coalesce(:field, field)`. |
| `MangaCover` | `entries/manga/model/MangaCover.kt` | Cover-image identity for the Coil image pipeline. |
| `Anime` | `entries/anime/model/Anime.kt` | `@Immutable`, `Serializable`. Mirrors `Manga` plus anime-only fields: `fetchType: FetchType`, `parentId: Long?`, `seasonFlags: Long`, `seasonNumber: Double`, `seasonSourceOrder: Long`, `backgroundUrl`/`backgroundLastModified`. Carries both `episodeFlags` and `seasonFlags` (two packed bitmasks) and an anime `viewerFlags` that encodes skip-intro length, next-airing episode, airing time. |
| `AnimeUpdate` | `entries/anime/model/AnimeUpdate.kt` | Patch model, parallel to `MangaUpdate`. |
| `AnimeCover` | `entries/anime/model/AnimeCover.kt` | Cover identity (anime side). |
| `Chapter` | `items/chapter/model/Chapter.kt` | `id, mangaId, read, bookmark, lastPageRead, dateFetch, sourceOrder, url, name, dateUpload, chapterNumber, scanlator, lastModifiedAt, version`. Has `copyFrom(other)` to merge fields from a freshly-fetched source chapter. |
| `ChapterUpdate` | `items/chapter/model/ChapterUpdate.kt` | Patch model. |
| `Episode` | `items/episode/model/Episode.kt` | Parallel to `Chapter` plus anime-only fields `seen, fillermark, lastSecondSeen, totalSeconds, summary, previewUrl`. |
| `EpisodeUpdate` | `items/episode/model/EpisodeUpdate.kt` | Patch model. |
| `MangaTrack` / `AnimeTrack` | `track/{manga,anime}/model/*Track.kt` | Tracker sync state (one row per `(manga/anime, tracker)` pair): `trackerId, remoteId, lastChapterRead/lastEpisodeSeen, totalChapters/totalEpisodes, status, score, remoteUrl, startDate, finishDate, private`. |
| `Category` | `category/model/Category.kt` | Shared by manga and anime. `id, name, order, flags, hidden`. `UNCATEGORIZED_ID = 0L` is the system "default" category. |
| `CategoryUpdate` | `category/model/CategoryUpdate.kt` | Patch model. |
| `MangaHistory` / `AnimeHistory` | `history/{manga,anime}/model/*History.kt` | Last-read/last-seen resume point (`MangaHistory`: `chapterId, readAt, readDuration`; `AnimeHistory`: `episodeId, seenAt`). |
| `MangaHistoryWithRelations` / `AnimeHistoryWithRelations` | same package | Denormalised row joining history → chapter/episode → manga/anime, for the "Recently read" screen. |
| `Source` (manga) / `AnimeSource` | `source/{manga,anime}/model/Source.kt`, `AnimeSource.kt` | Domain-side view of an installed source: `id, lang, name, supportsLatest, isStub, pin, isUsedLast`. The manga `Source` also carries `isExcludedFromDataSaver` (a Tachiyomi-SY extension). |
| `StubMangaSource` / `StubAnimeSource` | `source/{manga,anime}/model/Stub*Source.kt` | Placeholder for a source whose extension is no longer installed (so library manga still display without crashing). |
| `MangaSourceWithCount` / `AnimeSourceWithIds` | `source/{manga,anime}/model/*.kt` | Used by the "Sources" browse tab to show per-source library counts / non-library item lists. |
| `Pin` / `Pins` | `source/{manga,anime}/model/Pin.kt` | Source pinning (pinned sources appear first in the source list). |
| `MangaUpdatesWithRelations` / `AnimeUpdatesWithRelations` | `updates/{manga,anime}/model/*UpdatesWithRelations.kt` | Row for the "Updates" tab — joins chapter/episode to its parent manga/anime. |
| `Release` | `release/model/Release.kt` | App self-update info: `version, info, releaseLink, downloadLink`. |
| `ExtensionRepo` | `mihon/domain/extensionrepo/model/ExtensionRepo.kt` | An extension-repo source: `baseUrl, name, shortName?, website, signingKeyFingerprint`. |
| `CustomButton` / `CustomButtonUpdate` | `custombuttons/model/*.kt` | Aniyomi-specific: user-defined MPV player buttons (Lua scripts in `content`/`longPressContent`/`onStartup` columns). |
| `SeasonAnime` | `aniyomi/domain/anime/SeasonAnime.kt` | Anime-only: an `Anime` row plus aggregate season counts (`totalCount, seenCount, bookmarkCount, fillermarkCount, latestUpload, fetchedAt, lastSeen`). |

> Full field-by-field reference: [`../04-data-models/domain-models.md`](../04-data-models/domain-models.md).

## Repository interfaces

Each repository is a **pure-Kotlin interface** declared in `:domain`. The
implementations (`*Impl`) live in `:data`; Injekt binds interface → impl at app
startup (see [`../01-architecture/02-dependency-injection.md`](../01-architecture/02-dependency-injection.md)).

The interfaces come in manga/anime pairs (and one shared `Category` model):

| Interface | File | Implementation |
|---|---|---|
| `MangaRepository` | `entries/manga/repository/MangaRepository.kt` | `tachiyomi.data.entries.manga.MangaRepositoryImpl` |
| `AnimeRepository` | `entries/anime/repository/AnimeRepository.kt` | `tachiyomi.data.entries.anime.AnimeRepositoryImpl` |
| `ChapterRepository` | `items/chapter/repository/ChapterRepository.kt` | `tachiyomi.data.items.chapter.ChapterRepositoryImpl` |
| `EpisodeRepository` | `items/episode/repository/EpisodeRepository.kt` | `tachiyomi.data.items.episode.EpisodeRepositoryImpl` |
| `MangaCategoryRepository` / `AnimeCategoryRepository` | `category/{manga,anime}/repository/*CategoryRepository.kt` | `tachiyomi.data.category.{manga,anime}.*CategoryRepositoryImpl` |
| `MangaHistoryRepository` / `AnimeHistoryRepository` | `history/{manga,anime}/repository/*HistoryRepository.kt` | `tachiyomi.data.history.{manga,anime}.*HistoryRepositoryImpl` |
| `MangaTrackRepository` / `AnimeTrackRepository` | `track/{manga,anime}/repository/*TrackRepository.kt` | `tachiyomi.data.track.{manga,anime}.*TrackRepositoryImpl` |
| `MangaSourceRepository` / `AnimeSourceRepository` | `source/{manga,anime}/repository/*SourceRepository.kt` | `tachiyomi.data.source.{manga,anime}.*SourceRepositoryImpl` |
| `MangaStubSourceRepository` / `AnimeStubSourceRepository` | `source/{manga,anime}/repository/*StubSourceRepository.kt` | `tachiyomi.data.source.{manga,anime}.*StubSourceRepositoryImpl` |
| `MangaUpdatesRepository` / `AnimeUpdatesRepository` | `updates/{manga,anime}/repository/*UpdatesRepository.kt` | `tachiyomi.data.updates.{manga,anime}.*UpdatesRepositoryImpl` |
| `MangaExtensionRepoRepository` / `AnimeExtensionRepoRepository` | `mihon/domain/extensionrepo/{manga,anime}/repository/*ExtensionRepoRepository.kt` | `mihon.data.repository.{manga,anime}.*ExtensionRepoRepositoryImpl` |
| `CustomButtonRepository` | `custombuttons/repository/CustomButtonRepository.kt` | `tachiyomi.data.custombutton.CustomButtonRepositoryImpl` |

A typical interface declares both **suspend one-shot** methods (`getX(...)` →
`T`) and **reactive** methods (`getXAsFlow(...)` → `Flow<T>`). For example,
`MangaRepository` (full interface):

```kotlin
interface MangaRepository {
    suspend fun getMangaById(id: Long): Manga
    suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga>
    suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga?
    fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?>
    suspend fun getMangaFavorites(): List<Manga>
    suspend fun getReadMangaNotInLibrary(): List<Manga>
    suspend fun getLibraryManga(): List<LibraryManga>
    fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>>
    fun getMangaFavoritesBySourceId(sourceId: Long): Flow<List<Manga>>
    suspend fun getDuplicateLibraryManga(id: Long, title: String): List<Manga>
    suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>>
    suspend fun resetMangaViewerFlags(): Boolean
    suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>)
    suspend fun insertManga(manga: Manga): Long?
    suspend fun updateManga(update: MangaUpdate): Boolean
    suspend fun updateAllManga(mangaUpdates: List<MangaUpdate>): Boolean
}
```

The source repositories additionally expose a `PagingSource` returning
type alias so the UI can page remote catalog results:

```kotlin
typealias SourcePagingSourceType = PagingSource<Long, SManga>          // manga
typealias AnimeSourcePagingSourceType = PagingSource<Long, SAnime>     // anime
```

## The Interactor (use case) pattern

Aniyomi (inheriting Tachiyomi/Mihon) names its use cases `*Interactor`-style
classes — though the class name is usually the *verb phrase* (e.g. `GetManga`,
not `GetMangaInteractor`). Each is a single-purpose class that:

1. Takes one or more repository interfaces (or other interactors) as
   constructor parameters — injected by Injekt.
2. Exposes either an `operator fun invoke(...)`, a `suspend fun await(...)`
   (one-shot), or a `suspend fun subscribe(...)` / `fun subscribe(...)`
   (returns a long-lived `Flow`).
3. Encapsulates the business rule (filtering, sorting, cross-repo
   orchestration) so the UI just calls one method.

### Concrete examples

| Interactor | File | What it does |
|---|---|---|
| `GetMangaWithChapters` | `entries/manga/interactor/GetMangaWithChapters.kt` | Combines `MangaRepository.getMangaByIdAsFlow(id)` and `ChapterRepository.getChapterByMangaIdAsFlow(id)` into a single `Flow<Pair<Manga, List<Chapter>>>` for the reader screen. Also has `awaitManga`/`awaitChapters` one-shot variants. |
| `GetChaptersByMangaId` | `items/chapter/interactor/GetChaptersByMangaId.kt` | Thin wrapper that calls `ChapterRepository.getChapterByMangaId(...)` and converts exceptions to `emptyList()`. |
| `NetworkToLocalManga` | `entries/manga/interactor/NetworkToLocalManga.kt` | Given a freshly-fetched `Manga` (from a source), looks it up by `(url, source)`; if missing inserts it; if present-but-not-favorite, updates the display title from the source. Used when a remote manga is opened so we always have a local DB id. (Anime twin: `NetworkToLocalAnime`.) |
| `GetNextChapters` | `history/manga/interactor/GetNextChapters.kt` | Three overloads: `await(onlyUnread)` uses the most recent history row; `await(mangaId, onlyUnread)` returns the sorted chapter list; `await(mangaId, fromChapterId, onlyUnread)` returns chapters from `fromChapterId` onward, with subtle "current chapter is unfinished" handling. Composes `GetManga` + `GetChaptersByMangaId` + `MangaHistoryRepository` + `getChapterSort(...)`. (Anime twin: `GetNextEpisodes`.) |
| `FilterChaptersForDownload` | `mihon/domain/items/chapter/interactor/FilterChaptersForDownload.kt` | Given a `Manga` and its new chapters, applies the user's "download new chapters" preferences (category include/exclude, "only unread", "only new") to decide which to enqueue. Composes `GetChaptersByMangaId` + `DownloadPreferences` + `GetMangaCategories`. (Anime twin: `FilterEpisodesForDownload`.) |
| `MangaFetchInterval` | `entries/manga/interactor/MangaFetchInterval.kt` | Computes the adaptive fetch-interval (days between library updates) for a manga based on its upload-date history. Has unit tests in `src/test/.../MangaFetchIntervalTest.kt`. (Anime twin: `AnimeFetchInterval`.) |
| `GetUpcomingManga` | `mihon/domain/upcoming/manga/interactor/GetUpcomingManga.kt` | Subscribes to `MangaRepository.getUpcomingManga({ONGOING, PUBLISHING_FINISHED})` for the "Upcoming" tab. (Anime twin: `GetUpcomingAnime`.) |
| `CreateMangaExtensionRepo` | `mihon/domain/extensionrepo/manga/interactor/CreateMangaExtensionRepo.kt` | Validates an `index.min.json` URL, fetches `repo.json` via `ExtensionRepoService`, inserts it, and on `SaveExtensionRepoException` disambiguates "already exists" vs "duplicate signing key". Returns a sealed `Result` (`Success`, `InvalidUrl`, `RepoAlreadyExists`, `DuplicateFingerprint`, `Error`). |
| `GetApplicationRelease` | `release/interactor/GetApplicationRelease.kt` | Throttles GitHub release checks to once per 3 days (via a `PreferenceStore`-backed `lastChecked` pref), compares semver, returns `Result.NewUpdate(release)` / `Result.NoNewUpdate` / `Result.OsTooOld`. |
| `SetMangaCategories` | `category/manga/interactor/SetMangaCategories.kt` | Replaces the manga↔category bindings for one manga (delete-all + insert). (Anime twin: `SetAnimeCategories`.) |
| `SetMangaChapterFlags` / `SetAnimeEpisodeFlags` / `SetAnimeSeasonFlags` | `entries/{manga,anime}/interactor/*.kt` | Persist the per-manga/anime display+filter+sort flags packed in `chapterFlags`/`episodeFlags`/`seasonFlags`. |

### Naming conventions

- `Get*` — read-side interactor. Often paired with `*AsFlow` repository
  methods. One-shot variant: `Get*.await(...)`. Reactive variant:
  `Get*.subscribe(...)`.
- `Set*`, `Update*`, `Insert*`, `Delete*`, `Reset*`, `Reorder*`, `Rename*`,
  `Hide*`, `Upsert*`, `Replace*` — write-side interactors.
- `NetworkToLocal*` — the "upsert-or-fetch" pattern when bridging a remote
  source object into the local DB.
- `Filter*ForDownload` — decision interactors that bridge library/updates
  state to the download manager.
- `*FetchInterval` — adaptive scheduling helpers.

### Composition example: `GetMangaWithChapters`

```
ReaderScreenModel
   └─ GetMangaWithChapters.subscribe(id)               (interactor)
        ├─ MangaRepository.getMangaByIdAsFlow(id)      (interface in :domain)
        │    └─ impl: MangaRepositoryImpl              (in :data)
        │         └─ handler.subscribeToOne { mangasQueries.getMangaById(...) }
        └─ ChapterRepository.getChapterByMangaIdAsFlow(id)   (interface in :domain)
             └─ impl: ChapterRepositoryImpl            (in :data)
                  └─ handler.subscribeToList { chaptersQueries.getChaptersByMangaId(...) }
   combine(...) → Flow<Pair<Manga, List<Chapter>>>
```

The UI only ever sees the interactor. The repository interface and the
SQLDelight query live behind two layers of indirection — which is what keeps
`:domain` Android-free and unit-testable.

## Extension-repo management (`mihon.domain.extensionrepo`)

Extension repositories are HTTP endpoints that serve an `index.min.json` of
available extension APKs and a `repo.json` describing the repo itself
(`name`, `website`, `signingKeyFingerprint`, …). The `:domain` module owns
the contract and the orchestration; the `:data` module owns the
`extension_repos` SQLDelight table (duplicated in both schemas — see
[`data.md`](data.md)).

```
mihon.domain.extensionrepo
├── model/ExtensionRepo.kt                  — immutable data class
├── exception/SaveExtensionRepoException.kt — thrown by repos on SQLite conflicts
├── service/
│   ├── ExtensionRepoService.kt             — HTTP fetcher (GET $repo/repo.json, parseAs<ExtensionRepoMetaDto>)
│   └── ExtensionRepoDto.kt                 — kotlinx.serialization DTO + toExtensionRepo()
├── manga/
│   ├── repository/MangaExtensionRepoRepository.kt   — interface
│   └── interactor/
│       ├── CreateMangaExtensionRepo.kt
│       ├── GetMangaExtensionRepo.kt
│       ├── GetMangaExtensionRepoCount.kt
│       ├── ReplaceMangaExtensionRepo.kt
│       ├── DeleteMangaExtensionRepo.kt
│       └── UpdateMangaExtensionRepo.kt
└── anime/
    ├── repository/AnimeExtensionRepoRepository.kt   — interface
    └── interactor/  (same six operations, anime variants)
```

The interactors return sealed `Result` types so the UI can render
fine-grained error states (e.g. `DuplicateFingerprint(oldRepo, newRepo)`
lets the user choose to replace the existing repo).

`ExtensionRepoService` is the one place `:domain` reaches into the network
layer — it depends on `eu.kanade.tachiyomi.network.NetworkHelper` (provided
by `:app` at runtime via Injekt). It is one of the few `:domain` classes
that isn't strictly DB-only.

## Upcoming releases (`mihon.domain.upcoming`)

The "Upcoming" tab shows library manga/anime whose `next_update` is in the
future and whose status is `ONGOING` or `PUBLISHING_FINISHED`. Each side has
a single interactor that wraps the matching `MangaRepository.getUpcomingManga`
/ `AnimeRepository.getUpcomingAnime` query:

```
mihon.domain.upcoming
├── manga/interactor/GetUpcomingManga.kt
└── anime/interactor/GetUpcomingAnime.kt
```

Both are tiny — `GetUpcomingManga` is ~20 lines:

```kotlin
class GetUpcomingManga(private val mangaRepository: MangaRepository) {
    private val includedStatuses = setOf(
        SManga.ONGOING.toLong(),
        SManga.PUBLISHING_FINISHED.toLong(),
    )
    suspend fun subscribe(): Flow<List<Manga>> =
        mangaRepository.getUpcomingManga(includedStatuses)
}
```

## Other notable pieces

- **`release/`** — app self-update. `Release` model, `ReleaseService`
  interface (impl in `:data` as `ReleaseServiceImpl` hitting GitHub's
  releases API), and `GetApplicationRelease` interactor with 3-day throttling
  and semver comparison.
- **`storage/`** — `StoragePreferences` (SAF-backed storage locations for
  downloads/backup/restore) and `StorageManager` (a thin wrapper around
  `UniFile` from `libs.unifile`).
- **`library/`** — `LibraryPreferences` (the single largest preferences
  class, covering display mode, sort mode, badges, filters, columns,
  category defaults, swipe actions, season settings, etc. for **both** manga
  and anime). Also shared `LibraryDisplayMode`, `Flag`, `LibraryManga`,
  `LibraryAnime`, `MangaLibrarySort`/`MangaLibrarySortMode`,
  `AnimeLibrarySort`/`AnimeLibrarySortMode`.
- **`download/service/DownloadPreferences.kt`** — single class covering both
  sides (manga chapter downloads + anime episode downloads).
- **`backup/service/`** — `BackupPreferences` and `PreferenceValues` (enum
  preference types used by the backup/restore subsystem).
- **`custombuttons/`** — Aniyomi-specific: user-defined Lua-scripted buttons
  shown in the MPV player. Interface `CustomButtonRepository`, interactors
  `CreateCustomButton`/`GetCustomButtons`/`DeleteCustomButton`/
  `ReorderCustomButton`/`UpdateCustomButton`/`ToggleFavoriteCustomButton`.
- **`entries/TriState.kt`** and **`entries/EntryCover.kt`** — shared types
  used by both manga and anime (`TriState` is the
  filter state `DISABLED`/`ENABLED_IS`/`ENABLED_NOT`).
- **Pure services** — `ChapterRecognition`/`EpisodeRecognition`/
  `SeasonRecognition` parse free-text chapter/episode/season numbers;
  `ChapterSorter`/`EpisodeSorter`/`SeasonSorter` produce comparators;
  `MissingChapters`/`MissingEpisodes` compute numbering gaps. These are
  pure functions over the domain models and are unit-tested in
  `src/test/java/`.

## Key files table

| File | Role |
|---|---|
| `../ANIYOMI/domain/build.gradle.kts` | Build config — Android library, no UI deps. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/entries/manga/model/Manga.kt` | Manga domain model + flag constants. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/entries/anime/model/Anime.kt` | Anime domain model + episode/season/viewer flag constants. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/items/chapter/model/Chapter.kt` | Chapter domain model. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/items/episode/model/Episode.kt` | Episode domain model. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/items/season/service/SeasonRecognition.kt` | Anime-only season-number parser. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/entries/manga/repository/MangaRepository.kt` | MangaRepository interface. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/entries/anime/repository/AnimeRepository.kt` | AnimeRepository interface. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/items/chapter/repository/ChapterRepository.kt` | ChapterRepository interface. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/items/episode/repository/EpisodeRepository.kt` | EpisodeRepository interface. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/source/manga/service/MangaSourceManager.kt` | MangaSourceManager interface. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/source/anime/service/AnimeSourceManager.kt` | AnimeSourceManager interface. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/source/manga/repository/MangaSourceRepository.kt` | Source repo + `SourcePagingSourceType` typealias. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/entries/manga/interactor/GetMangaWithChapters.kt` | Composing interactor example. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/entries/manga/interactor/NetworkToLocalManga.kt` | Remote→local upsert pattern. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/history/manga/interactor/GetNextChapters.kt` | Multi-overload interactor with cross-repo composition. |
| `../ANIYOMI/domain/src/main/java/mihon/domain/items/chapter/interactor/FilterChaptersForDownload.kt` | Decision interactor bridging library↔download. |
| `../ANIYOMI/domain/src/main/java/mihon/domain/extensionrepo/service/ExtensionRepoService.kt` | HTTP fetcher for extension-repo metadata. |
| `../ANIYOMI/domain/src/main/java/mihon/domain/extensionrepo/manga/interactor/CreateMangaExtensionRepo.kt` | Sealed-`Result` interactor example. |
| `../ANIYOMI/domain/src/main/java/mihon/domain/upcoming/manga/interactor/GetUpcomingManga.kt` | Upcoming-tab interactor. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/release/interactor/GetApplicationRelease.kt` | App self-update interactor. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/library/service/LibraryPreferences.kt` | The largest preferences class — covers both sides. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/category/model/Category.kt` | Shared Category model. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/track/manga/model/MangaTrack.kt` | Tracker sync state (manga side). |
| `../ANIYOMI/domain/src/main/java/aniyomi/domain/anime/SeasonAnime.kt` | Anime-only season aggregate. |

## See also

- [`data.md`](data.md) — the `:data` module that implements every repository
  interface listed here.
- [`../04-data-models/domain-models.md`](../04-data-models/domain-models.md) —
  field-by-field reference for every domain model.
- [`../04-data-models/database-schema.md`](../04-data-models/database-schema.md)
  — the SQLDelight `.sq` files backing these models.
- [`../01-architecture/01-architecture-overview.md`](../01-architecture/01-architecture-overview.md)
  — the layered architecture and the Interactor / ScreenModel patterns.
- [`../01-architecture/02-dependency-injection.md`](../01-architecture/02-dependency-injection.md)
  — how Injekt binds `:domain` interfaces to `:data` implementations.
- [`../00-overview/03-module-map.md`](../00-overview/03-module-map.md) —
  module-level dependency graph.
- [`../00-overview/05-project-conventions.md`](../00-overview/05-project-conventions.md)
  — the dual manga/anime pattern at the project level.
- [`../03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md)
  — how the `mihon.domain.extensionrepo` package is used at runtime.
- [`../03-subsystems/updates.md`](../03-subsystems/updates.md) — how
  `mihon.domain.upcoming` and the `*FetchInterval` interactors feed the
  library-update scheduler.
