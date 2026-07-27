# 02-modules / `data.md` — The `:data` Module

> The `:data` module is the persistence and repository-implementation layer. It
> implements every repository interface declared in `:domain` (see
> [`domain.md`](domain.md)) against **two parallel SQLDelight databases** — one
> for manga (inherited from Mihon/Tachiyomi) and one for anime (Aniyomi's
> addition). This dual-schema layout is the single most important thing to
> understand about this module.

## Purpose

The `:data` module:

1. **Generates two SQLDelight databases** from `.sq` schema files —
   `Database` (manga, package `tachiyomi.data`) and `AnimeDatabase` (anime,
   package `tachiyomi.mi.data`).
2. **Implements the `:domain` repository interfaces** as `*Impl` classes
   (`MangaRepositoryImpl`, `AnimeRepositoryImpl`, `ChapterRepositoryImpl`,
   `EpisodeRepositoryImpl`, …) using a small `*DatabaseHandler` abstraction.
3. **Maps SQLDelight row tuples → `:domain` models** via per-entity
   `*Mapper` objects (`MangaMapper`, `AnimeMapper`, `MangaTrackMapper`,
   `AnimeTrackMapper`, `MangaHistoryMapper`, `AnimeHistoryMapper`).
4. **Bridges `:source-api` to `:domain`** via the `source/manga` and
   `source/anime` packages (`*SourceRepositoryImpl`, `*SourcePagingSource`,
   `*StubSourceRepositoryImpl`).
5. **Owns schema migrations** (`.sqm` files) for both databases.
6. **Hosts a couple of `:data`-only services** that need network but no
   UI — `ReleaseServiceImpl` (app self-update via GitHub) and the
   `mihon.data.repository.{manga,anime}.*ExtensionRepoRepositoryImpl`
   classes (backed by the `extension_repos` tables).

The module is **Android-only** (it uses `AndroidDriver` for SQLDelight,
`android.database.sqlite.SQLiteException`, `android.os.Build`, etc.). It
deliberately has **no UI** — no Activities, no Compose, no ViewModels.

## Build configuration

The module lives at `../ANIYOMI/data/`. Its
`../ANIYOMI/data/build.gradle.kts` reads:

```kotlin
plugins {
    id("mihon.library")
    kotlin("android")
    kotlin("plugin.serialization")
    alias(libs.plugins.sqldelight)             // app.cash.sqldelight Gradle plugin
}

android {
    namespace = "tachiyomi.data"
    defaultConfig { consumerProguardFiles("consumer-rules.pro") }

    sqldelight {
        databases {
            create("Database") {                // MANGA database
                packageName.set("tachiyomi.data")
                dialect(libs.sqldelight.dialects.sql)
                schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
                srcDirs.from(project.file("./src/main/sqldelight"))
            }
            create("AnimeDatabase") {           // ANIME database (separate file)
                packageName.set("tachiyomi.mi.data")
                dialect(libs.sqldelight.dialects.sql)
                schemaOutputDirectory.set(project.file("./src/main/sqldelightanime"))
                srcDirs.from(project.file("./src/main/sqldelightanime"))
            }
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)             // to implement its repo interfaces
    implementation(projects.core.common)

    api(libs.bundles.sqldelight)                // driver, coroutines-extensions, paging
}
```

### Key build facts

- **Two SQLDelight databases** are declared in one `sqldelight { databases { … } }`
  block. Each has its own package name, source directory, and schema-output
  directory.
- The **manga DB** generates into package `tachiyomi.data.Database`; the
  **anime DB** generates into package `tachiyomi.mi.data.AnimeDatabase`.
  (`tachiyomi.mi.data` is the historical package name — `mi` comes from the
  app id `xyz.jmir.tachiyomi.mi`. Don't read too much into it; it's just the
  anime side's namespace.)
- Both use the **same dialect** (`libs.sqldelight.dialects.sql` — generic
  SQLite dialect, since the Android driver is a SQLite driver under the hood).
- The module `api`-exposes `libs.bundles.sqldelight` (driver + coroutine
  extensions + paging) so that `:app` can construct the
  `AndroidSqliteDriver` and the `*DatabaseHandler` instances.
- It depends on `:domain` (to implement the interfaces) and `:core:common`
  (for `PreferenceStore`, logging, `withIOContext`).
- It depends on `:source-api` because the source repositories return
  `SManga` / `SAnime` paging sources.

## The two parallel schemas

```
data/src/main/
├── sqldelight/          ← MANGA DB  (package tachiyomi.data, class Database)
│   ├── data/            ← .sq table files (9)
│   ├── view/            ← .sq view files  (3)
│   └── migrations/      ← .sqm files      (32: 1..32)
│
└── sqldelightanime/     ← ANIME DB  (package tachiyomi.mi.data, class AnimeDatabase)
    ├── dataanime/       ← .sq table files (9)
    ├── view/            ← .sq view files  (8)
    └── migrations/      ← .sqm files      (23: 113..135)
```

### Why two databases?

Aniyomi inherited a complete manga-only Tachiyomi/Mihon schema (the
`sqldelight/` tree). Rather than refactor the existing tables to share a
single schema with anime — which would have required a massive migration and
risked breaking the manga side — the Aniyomi authors created a **second,
parallel schema** (`sqldelightanime/`) for the anime-side tables. The two
schemas are near-identical in shape but live in separate `.db` files at
runtime, have separate generated `Database` / `AnimeDatabase` classes, and
have separate migration histories.

The cost is **duplication**: every manga-side table has an anime-side twin,
every repository has two implementations, and every handler is duplicated.
The benefit is **isolation**: changes to the anime schema cannot break the
manga side (and vice versa), and the manga side continues to track
upstream Mihon cleanly.

### Schema file listing — manga side (`sqldelight/data/`)

| `.sq` file | Table | Notes |
|---|---|---|
| `mangas.sq` | `mangas` | Core manga row. Includes 3 triggers (`update_last_favorite_at_mangas`, `update_last_modified_at_mangas`, `update_manga_version`) that maintain `favorite_modified_at`, `last_modified_at`, and the sync `version` column. |
| `chapters.sq` | `chapters` | FK → `mangas(_id)` `ON DELETE CASCADE`. Trigger `update_chapter_and_manga_version` bumps both chapter and parent manga `version` when read/bookmark/last_page_read changes (unless `is_syncing = 1`). |
| `mangas_categories.sq` | `mangas_categories` | Join table. Trigger bumps manga `version` on insert. |
| `categories.sq` | `categories` | Shared shape with anime side. Has a "system category" (`_id = 0`) protected by a `BEFORE DELETE` trigger. |
| `history.sq` | `history` | FK → `chapters(_id)`. `upsert` uses `ON CONFLICT(chapter_id) DO UPDATE` accumulating `time_read`. |
| `manga_sync.sq` | `manga_sync` | Tracker state (`sync_id`, `remote_id`, `last_chapter_read`, …). `UNIQUE (manga_id, sync_id) ON CONFLICT REPLACE`. |
| `sources.sq` | `sources` | Stub-source registry (id/lang/name) for sources whose extension is no longer installed. |
| `excluded_scanlators.sq` | `excluded_scanlators` | Manga-only: per-manga scanlator exclusion list (used by `getChaptersByMangaId`'s LEFT JOIN). |
| `extension_repos.sq` | `extension_repos` | Extension-repo registry. PK `base_url`, UNIQUE `signing_key_fingerprint`. |

### Schema file listing — anime side (`sqldelightanime/dataanime/`)

| `.sq` file | Table | Notes |
|---|---|---|
| `animes.sq` | `animes` | Core anime row. Mirrors `mangas` **plus** anime-only columns: `fetch_type AS FetchType`, `parent_id` (season grouping), `season_flags`, `season_number`, `season_source_order`, `background_url`, `background_last_modified`. Extra indexes: `animes_parent_id`, `animes_fetch_type`. Same 3 trigger pattern as `mangas`. |
| `episodes.sq` | `episodes` | FK → `animes(_id)`. Mirrors `chapters` plus `seen`, `last_second_seen`, `total_seconds`, `summary`, `preview_url`, `fillermark`. Trigger `update_episode_and_anime_version` bumps both episode and parent anime `version` when `seen`/`bookmark`/`last_second_seen` change. |
| `animes_categories.sq` | `animes_categories` | Join table, parallel to `mangas_categories`. |
| `categories.sq` | `categories` | Same shape as manga side (the manga and anime category tables are separate physical tables with identical schema). |
| `animehistory.sq` | `animehistory` | FK → `episodes(_id)`. Parallel to `history` but stores `last_seen AS Date` only (no `time_read`). |
| `anime_sync.sq` | `anime_sync` | Tracker state. Mirrors `manga_sync` (`last_episode_seen`, `total_episodes`, …). |
| `animesources.sq` | `animesources` | Stub-source registry for anime. Parallel to `sources`. |
| `custom_buttons.sq` | `custom_buttons` | **Anime-only.** Stores user-defined MPV player buttons: `name`, `isFavorite`, `sortIndex`, `content`, `longPressContent`, `onStartup` (all Lua scripts). Seeds a default `+85 s` skip-intro button via `INSERT OR IGNORE`. |
| `extension_repos.sq` | `extension_repos` | Extension-repo registry (anime side). Same schema as manga side; kept as a separate table. |

### Side-by-side: manga ↔ anime table mapping

```
MANGA (sqldelight/data/)              ANIME (sqldelightanime/dataanime/)
─────────────────────────────         ─────────────────────────────────
mangas.sq                  ◀────────▶ animes.sq
chapters.sq                ◀────────▶ episodes.sq
mangas_categories.sq       ◀────────▶ animes_categories.sq
categories.sq              ◀────────▶ categories.sq            (same shape)
history.sq                 ◀────────▶ animehistory.sq
manga_sync.sq              ◀────────▶ anime_sync.sq
sources.sq                 ◀────────▶ animesources.sq
extension_repos.sq         ◀────────▶ extension_repos.sq       (same shape)
excluded_scanlators.sq     ◀─── (no anime equivalent)
                                    custom_buttons.sq  ◀─── (no manga equivalent)
```

## Views (SQLDelight `view/` folders)

SQLDelight treats `.sq` files in a `view/` subfolder as view definitions.
Each `.sq` defines a `CREATE VIEW … AS SELECT …` followed by named queries
against that view.

### Manga views (`sqldelight/view/`) — 3 files

| `.sq` file | View | Purpose |
|---|---|---|
| `libraryView.sq` | `libraryView` | Joins `mangas` × aggregated `chapters` (total/read/bookmark/latest upload/last read/fetched) × `mangas_categories`. The `library:` query is what the library screen subscribes to. |
| `historyView.sq` | `historyView` | Joins `mangas` × `chapters` × `history` plus a `max_last_read` subquery to find each manga's most-recent chapter. Drives the "Recently read" screen. |
| `updatesView.sq` | `updatesView` | Joins `mangas` × `chapters` for library favorites with `date_fetch > date_added`. Drives the "Updates" tab. |

### Anime views (`sqldelightanime/view/`) — 8 files

The anime side has more views because of the **seasons** feature (an anime
can have child "season" rows under a parent anime via `parent_id`) and the
**deletable-parent** feature (parents with no favorites can be pruned).

| `.sq` file | View | Purpose |
|---|---|---|
| `animelibView.sq` | `animelibView` | Library view for anime. Uses `CASE M.fetch_type WHEN 1 THEN … WHEN 0 THEN …` to compute counts either from episode stats (episodes mode) or from season stats (seasons mode). |
| `animehistoryView.sq` | `animehistoryView` | Parallel to `historyView`. |
| `animeupdatesView.sq` | `animeupdatesView` | Parallel to `updatesView`. |
| `episodestatsView.sq` | `episodestatsView` | Per-anime aggregate of `episodes` (total/seen/bookmark/fillermark/latest upload/fetched). |
| `animehistorystatsView.sq` | `animehistorystatsView` | Per-anime `max(last_seen)` from `animehistory`. |
| `animeseasonstatsView.sq` | `animeseasonstatsView` | Per-parent aggregates of season children (child count, fully-seen seasons, max uploads/fetched/last-seen, total bookmarks/fillermarks). |
| `animeseasonsView.sq` | `animeseasonsView` | Per-season view joining parent `animes` × the three stats views above. Used by `AnimeRepository.getAnimeSeasonsById(parentId)`. |
| `animedeletableView.sq` | `animedeletableView` | Lists parent animes (`parent_id IS NULL`) with `favorite = 0` — candidates for cleanup. |

The anime library view is the most complex piece of SQL in the codebase:

```sql
CREATE VIEW animelibView AS
SELECT M.*,
  CASE M.fetch_type
    WHEN 1 THEN coalesce(ES.total, 0)              -- episodes mode
    WHEN 0 THEN coalesce(ASS.child_count, 0)       -- seasons  mode
  END AS totalCount,
  -- … five more CASE columns for seenCount, latestUpload,
  --     episodeFetchedAt, lastSeen, bookmarkCount, fillermarkCount
  coalesce(MC.category_id, 0) AS category
FROM animes M
LEFT JOIN episodestatsView      AS ES  ON M._id = ES.anime_id
LEFT JOIN animehistorystatsView AS AHS ON M._id = AHS.anime_id
LEFT JOIN animes_categories     AS MC  ON MC.anime_id = M._id
LEFT JOIN animeseasonstatsView  AS ASS ON M._id = ASS.parent_id
WHERE M.favorite = 1;
```

## Migrations

SQLDelight migrations are `.sqm` files in a `migrations/` subfolder, named
`<N>.sqm` where `N` is the target schema version. SQLDelight verifies that
the cumulative result of applying `1.sqm, 2.sqm, …, N.sqm` produces the same
schema as the `.sq` files describe (it runs them in tests).

### Manga migrations (`sqldelight/migrations/`)

- **32 files**: `1.sqm` … `32.sqm`.
- Schema history starts at version 1 (the original Tachiyomi schema) and has
  been extended through version 32. The `Database` class's current schema
  version is the highest applied migration + 1.
- Example `1.sqm`:

  ```sql
  ALTER TABLE chapters ADD COLUMN source_order INTEGER DEFAULT 0;
  UPDATE mangas SET thumbnail_url = replace(thumbnail_url, '93.174.95.110', 'kissmanga.com')
  WHERE source = 4;
  ```

  (Yes — that's a real migration from Tachiyomi history, migrating a
  defunct manga source's cover URLs.)

- Example `32.sqm` (the latest):

  ```sql
  ALTER TABLE manga_sync ADD COLUMN private INTEGER AS Boolean DEFAULT 0 NOT NULL;
  ```

### Anime migrations (`sqldelightanime/migrations/`)

- **23 files**: `113.sqm` … `135.sqm`.
- The anime schema is **bootstrapped at version 113** — i.e. the anime DB
  was created when the manga DB was already at version 112, so the anime
  schema's baseline matches manga's version-112 shape. From there, anime
  migrations are versioned independently (113, 114, …, 135).
- Example `113.sqm`:

  ```sql
  UPDATE episodes SET date_upload = date_fetch WHERE date_upload = 0;
  ```

### How schema evolution works

```
                ┌─────────────────────────────────────────┐
                │  .sq files describe the LATEST schema    │
                │  (mangas.sq, animes.sq, etc.)            │
                └────────────────────┬────────────────────┘
                                     │
              SQLDelight Gradle plugin compiles these into
              generated Kotlin (Database, AnimeDatabase, *Queries)
                                     │
                                     ▼
                ┌─────────────────────────────────────────┐
                │  .sqm files describe how to UPGRADE      │
                │  from version N-1 to version N           │
                └────────────────────┬────────────────────┘
                                     │
              At app install/upgrade time, AndroidSqliteDriver
              runs the .sqm files between the user's current
              DB version and the latest schema version
                                     │
                                     ▼
                ┌─────────────────────────────────────────┐
                │  User's on-disk .db file is migrated     │
                │  (tachiyomi.db / tachiyomi.anime.db)     │
                └─────────────────────────────────────────┘
```

The two databases have **independent version counters** — bumping the
manga schema does not require a corresponding anime migration (and
vice versa). Each `.sqm` file is small and focused (typically one
`ALTER TABLE` or one `UPDATE`), and they are applied in order.

## Repository implementations

Every `:domain` repository interface is implemented in `:data` by an `*Impl`
class. Each implementation takes a `*DatabaseHandler` as a constructor
parameter (injected by Injekt) and translates calls into SQLDelight queries
through the handler.

### Implementation map

| `:domain` interface | `:data` implementation | Handler used |
|---|---|---|
| `MangaRepository` | `tachiyomi.data.entries.manga.MangaRepositoryImpl` | `MangaDatabaseHandler` |
| `AnimeRepository` | `tachiyomi.data.entries.anime.AnimeRepositoryImpl` | `AnimeDatabaseHandler` |
| `ChapterRepository` | `tachiyomi.data.items.chapter.ChapterRepositoryImpl` | `MangaDatabaseHandler` |
| `EpisodeRepository` | `tachiyomi.data.items.episode.EpisodeRepositoryImpl` | `AnimeDatabaseHandler` |
| `MangaCategoryRepository` | `tachiyomi.data.category.manga.MangaCategoryRepositoryImpl` | `MangaDatabaseHandler` |
| `AnimeCategoryRepository` | `tachiyomi.data.category.anime.AnimeCategoryRepositoryImpl` | `AnimeDatabaseHandler` |
| `MangaHistoryRepository` | `tachiyomi.data.history.manga.MangaHistoryRepositoryImpl` | `MangaDatabaseHandler` |
| `AnimeHistoryRepository` | `tachiyomi.data.history.anime.AnimeHistoryRepositoryImpl` | `AnimeDatabaseHandler` |
| `MangaTrackRepository` | `tachiyomi.data.track.manga.MangaTrackRepositoryImpl` | `MangaDatabaseHandler` |
| `AnimeTrackRepository` | `tachiyomi.data.track.anime.AnimeTrackRepositoryImpl` | `AnimeDatabaseHandler` |
| `MangaSourceRepository` | `tachiyomi.data.source.manga.MangaSourceRepositoryImpl` | `MangaDatabaseHandler` + `MangaSourceManager` |
| `AnimeSourceRepository` | `tachiyomi.data.source.anime.AnimeSourceRepositoryImpl` | `AnimeDatabaseHandler` + `AnimeSourceManager` |
| `MangaStubSourceRepository` | `tachiyomi.data.source.manga.MangaStubSourceRepositoryImpl` | `MangaDatabaseHandler` |
| `AnimeStubSourceRepository` | `tachiyomi.data.source.anime.AnimeStubSourceRepositoryImpl` | `AnimeDatabaseHandler` |
| `MangaUpdatesRepository` | `tachiyomi.data.updates.manga.MangaUpdatesRepositoryImpl` | `MangaDatabaseHandler` |
| `AnimeUpdatesRepository` | `tachiyomi.data.updates.anime.AnimeUpdatesRepositoryImpl` | `AnimeDatabaseHandler` |
| `MangaExtensionRepoRepository` | `mihon.data.repository.manga.MangaExtensionRepoRepositoryImpl` | `MangaDatabaseHandler` |
| `AnimeExtensionRepoRepository` | `mihon.data.repository.anime.AnimeExtensionRepoRepositoryImpl` | `AnimeDatabaseHandler` |
| `CustomButtonRepository` | `tachiyomi.data.custombutton.CustomButtonRepositoryImpl` | `AnimeDatabaseHandler` |
| `ReleaseService` (interface in `:domain`) | `tachiyomi.data.release.ReleaseServiceImpl` | (no DB — hits GitHub API) |

### Anatomy of an implementation: `MangaRepositoryImpl`

`MangaRepositoryImpl` takes `MangaDatabaseHandler` as its sole constructor
dependency, delegates each method to `handler.await* { … }` (one-shot) or
`handler.subscribeTo* { … }` (reactive `Flow`) with a lambda that runs
against the generated `Database`, and maps each row via `MangaMapper`:

```kotlin
override suspend fun getMangaById(id: Long): Manga =
    handler.awaitOne { mangasQueries.getMangaById(id, MangaMapper::mapManga) }

override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> =
    handler.subscribeToList { libraryViewQueries.library(MangaMapper::mapLibraryManga) }

override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
    handler.await(inTransaction = true) {
        mangas_categoriesQueries.deleteMangaCategoryByMangaId(mangaId)
        categoryIds.map { mangas_categoriesQueries.insert(mangaId, it) }
    }
}
```

The anime twin `AnimeRepositoryImpl` is structurally identical but also
implements anime-only operations: `getAnimeSeasonsById` /
`getAnimeSeasonsByIdAsFlow` (via `animeseasonsViewQueries`),
`removeParentIdByIds`, `getDeletableParentAnime` (via
`animedeletableViewQueries`), and `getChildrenByParentId`.

### Mappers

Mappers are stateless `object`s (one per entity per side) that translate
SQLDelight row tuples into `:domain` data classes. SQLDelight generates a
mapper function signature whose parameters match the columns in `SELECT *`,
so the mapper is just a long `fun mapX(id: Long, source: Long, …): Manga`.

| Mapper | File |
|---|---|
| `MangaMapper` | `entries/manga/MangaMapper.kt` (also has `mapLibraryManga`) |
| `AnimeMapper` | `entries/anime/AnimeMapper.kt` (also `mapLibraryAnime`, `mapSeasonAnime`, `mapDeletableAnime`) |
| `MangaTrackMapper` | `track/manga/MangaTrackMapper.kt` |
| `AnimeTrackMapper` | `track/anime/AnimeTrackMapper.kt` |
| `MangaHistoryMapper` | `history/manga/MangaHistoryMapper.kt` |
| `AnimeHistoryMapper` | `history/anime/AnimeHistoryMapper.kt` |

### Sanitizers

`ChapterSanitizer` (`items/chapter/ChapterSanitizer.kt`) and
`EpisodeSanitizer` (`items/episode/EpisodeSanitizer.kt`) are small objects
that strip the manga/anime title prefix and trailing whitespace/separators
from a chapter/episode name (used when displaying "next chapter" titles).

## Column adapters

`DatabaseAdapter.kt` (`tachiyomi.data.DatabaseAdapter`) declares
`ColumnAdapter` implementations used by the generated `*Queries` to
encode/decode non-trivial column types:

| Adapter | Encodes |
|---|---|
| `DateColumnAdapter` | `java.util.Date ↔ Long` (epoch millis) — used by `history.last_read` / `animehistory.last_seen`. |
| `StringListColumnAdapter` | `List<String> ↔ String` (comma+space separated) — used by `mangas.genre` / `animes.genre`. |
| `MangaUpdateStrategyColumnAdapter` | `UpdateStrategy ↔ Long` (ordinal) — used by `mangas.update_strategy`. |
| `AnimeUpdateStrategyColumnAdapter` | `AnimeUpdateStrategy ↔ Long` (ordinal) — used by `animes.update_strategy`. |
| `FetchTypeColumnAdapter` | `FetchType ↔ Long` (ordinal) — used by `animes.fetch_type` (Episodes vs Seasons mode). |

## The `handlers/` package — database access abstraction

The `tachiyomi.data.handlers/` package is the **abstraction layer between
the repository implementations and the generated SQLDelight `Database` /
`AnimeDatabase` classes**. It exists because SQLDelight's raw `Query` API is
verbose and coroutine-unfriendly; the handler wraps it into a small set of
suspended helpers.

There are **two parallel handler hierarchies** — one per database:

```
tachiyomi.data.handlers
├── manga/
│   ├── MangaDatabaseHandler.kt         ← interface
│   ├── AndroidMangaDatabaseHandler.kt  ← concrete impl (wraps tachiyomi.data.Database)
│   ├── MangaTransactionContext.kt      ← suspending transaction support
│   └── QueryPagingMangaSource.kt       ← PagingSource over a SQLDelight Query
└── anime/
    ├── AnimeDatabaseHandler.kt         ← interface (wraps tachiyomi.mi.data.AnimeDatabase)
    ├── AndroidAnimeDatabaseHandler.kt
    ├── AnimeTransactionContext.kt
    └── QueryPagingAnimeSource.kt
```

### What the handler exposes

`MangaDatabaseHandler` (the manga side; `AnimeDatabaseHandler` is
identical except it references `AnimeDatabase`):

```kotlin
interface MangaDatabaseHandler {
    suspend fun <T>        await(inTransaction: Boolean = false,
                                block: suspend Database.() -> T): T
    suspend fun <T : Any>  awaitList(inTransaction: Boolean = false,
                                block: suspend Database.() -> Query<T>): List<T>
    suspend fun <T : Any>  awaitOne(inTransaction: Boolean = false,
                                block: suspend Database.() -> Query<T>): T
    suspend fun <T : Any>  awaitOneExecutable(...)
    suspend fun <T : Any>  awaitOneOrNull(...)
    suspend fun <T : Any>  awaitOneOrNullExecutable(...)

    fun <T : Any> subscribeToList(block: Database.() -> Query<T>): Flow<List<T>>
    fun <T : Any> subscribeToOne(block: Database.() -> Query<T>): Flow<T>
    fun <T : Any> subscribeToOneOrNull(block: Database.() -> Query<T>): Flow<T?>

    fun <T : Any> subscribeToPagingSource(
        countQuery: Database.() -> Query<Long>,
        queryProvider: Database.(Long, Long) -> Query<T>,
    ): PagingSource<Long, T>
}
```

So a repository method like:

```kotlin
override suspend fun getMangaById(id: Long): Manga =
    handler.awaitOne { mangasQueries.getMangaById(id, MangaMapper::mapManga) }
```

…calls `handler.awaitOne { … }` with a lambda that runs against the
generated `Database`. Inside, `mangasQueries` is a property of `Database`
auto-generated by SQLDelight from `mangas.sq`.

### Transactions

The `*TransactionContext.kt` files implement a **suspending transaction**
mechanism that mirrors Room's `withTransaction { … }`. The key function is
`withMangaTransaction` / `withAnimeTransaction`:

```kotlin
internal suspend fun <T> AndroidMangaDatabaseHandler.withMangaTransaction(
    block: suspend () -> T,
): T {
    val transactionContext =
        coroutineContext[TransactionElement]?.transactionDispatcher
            ?: createTransactionContext()
    return withContext(transactionContext) {
        val transactionElement = coroutineContext[TransactionElement]!!
        transactionElement.acquire()
        try {
            db.transactionWithResult { runBlocking(transactionContext) { block() } }
        } finally {
            transactionElement.release()
        }
    }
}
```

It acquires a single thread from SQLDelight's query executor
(`acquireTransactionThread`), wraps it in a `CoroutineContext`
(`TransactionElement` + a `ThreadLocal` marker) so nested suspending DAO
calls dispatch to the **same** thread (and therefore the **same** SQLite
transaction), uses `transactionWithResult { … }` to mark the transaction
successful only if no exception escapes, and releases the thread when the
reference-counted `TransactionElement` drops to zero (supporting nested
transactions). This is why `setMangaCategories` can do "delete-all then
insert-many" atomically via `handler.await(inTransaction = true) { … }`.

### Paging

`QueryPagingMangaSource` / `QueryPagingAnimeSource`
(`handlers/{manga,anime}/QueryPaging*Source.kt`) adapt a SQLDelight
`Query<T>` into an AndroidX `PagingSource<Long, T>` so that paged
catalog results can be consumed by Compose `LazyColumn`s via the
`androidx.paging` runtime. They are used by the source repositories
when serving local library/search results (remote paging uses a
different `*SourcePagingSource` that talks directly to the source API).

## The `source/` packages

The `tachiyomi.data.source.manga` and `tachiyomi.data.source.anime`
packages implement the `:domain` source repositories. They are unusual
among `:data` packages because they straddle **two** data sources: the
SQLDelight database (for the stub-source registry and per-source library
counts) and the in-memory `*SourceManager` (for the live `CatalogueSource`
objects loaded from extensions).

```
tachiyomi.data.source.manga
├── MangaSourceRepositoryImpl.kt     ← implements MangaSourceRepository
├── MangaStubSourceRepositoryImpl.kt ← implements MangaStubSourceRepository
├── MangaSourcePagingSource.kt       ← SourceSearchPagingSource,
│                                       SourcePopularPagingSource,
│                                       SourceLatestPagingSource
└── (SourceSearchPagingSource etc. are NOT SQLDelight-backed — they
    call source.getSearchManga(page, query, filters) directly)

tachiyomi.data.source.anime
├── AnimeSourceRepositoryImpl.kt
├── AnimeStubSourceRepositoryImpl.kt
└── AnimeSourcePagingSource.kt
```

`MangaSourceRepositoryImpl`, for example:

- `getMangaSources()` / `getOnlineMangaSources()` — read from the
  `MangaSourceManager` (live extension sources), map each to a domain
  `Source`.
- `getMangaSourcesWithFavoriteCount()` — `combine`s a SQLDelight query
  (`mangasQueries.getSourceIdWithFavoriteCount()`) with the live source
  list to attach counts.
- `searchManga(sourceId, query, filters)` — looks up the source in
  `MangaSourceManager` and returns a `SourceSearchPagingSource` that pages
  the source's remote API. **No SQLDelight involved** here.

So a single repository can serve both DB-backed and remote-backed data —
the `:domain` interface doesn't distinguish them.

## The `mihon.data.repository/` packages

These implement the `:domain` extension-repo repository interfaces. They
are pure SQLDelight — no network access (that's `ExtensionRepoService`
in `:domain`'s job, called by the `Create*ExtensionRepo` interactors).

```
mihon.data.repository
├── manga/MangaExtensionRepoRepositoryImpl.kt
└── anime/AnimeExtensionRepoRepositoryImpl.kt
```

Each implementation uses the `extension_reposQueries` from its respective
database to `findAll`, `findOne`, `findOneBySigningKeyFingerprint`,
`count`, `insert`, `upsert`, `replace`, `delete`. They catch
`android.database.sqlite.SQLiteException` and rethrow as
`mihon.domain.extensionrepo.exception.SaveExtensionRepoException` so the
interactor can disambiguate "already exists" vs "duplicate signing key".

## The `release/` package

`tachiyomi.data.release.ReleaseServiceImpl` implements
`tachiyomi.domain.release.service.ReleaseService` (the only `:domain`
service interface whose impl is in `:data`). It hits
`https://api.github.com/repos/<repo>/releases/latest`, parses the JSON
into `GithubRelease` (`release/GithubRelease.kt`), picks the right APK
asset for the device's ABI, and returns a `Release`. It is **not**
DB-backed — it lives in `:data` because it depends on `:app`'s
`NetworkHelper` (provided at runtime via Injekt).

## Schema-evolution triggers (a notable pattern)

Both `mangas.sq` and `animes.sq` define SQLite triggers that maintain
synchronization metadata used by the (optional) Aniyomi sync feature:

| Trigger | What it does |
|---|---|
| `update_last_modified_at_{mangas,animes,chapters,episodes}` | After any UPDATE, sets `last_modified_at = strftime('%s', 'now')` on the row. |
| `update_last_favorite_at_{mangas,animes}` | After UPDATE OF `favorite`, sets `favorite_modified_at`. |
| `update_{manga,anime}_version` | After UPDATE (when `is_syncing = 0` and key fields changed), increments `version`. Used by sync to detect local modifications. |
| `update_chapter_and_manga_version` / `update_episode_and_anime_version` | When a chapter/episode's `read`/`bookmark`/`last_page_read` (or `seen`/`bookmark`/`last_second_seen`) changes, bump both the item's `version` and the parent's `version`. |
| `insert_{manga,anime}_category_update_version` | Bump parent `version` when a category binding is added. |
| `system_category_delete_trigger` | Prevent deletion of the system category (`_id <= 0`). |

The `is_syncing` flag is set to `1` by the sync code when it writes, so
the triggers don't double-increment `version` for sync-driven writes.

## Key files table

| File | Role |
|---|---|
| `../ANIYOMI/data/build.gradle.kts` | Two-database SQLDelight config. |
| `../ANIYOMI/data/src/main/sqldelight/data/{mangas,chapters,history,manga_sync,sources,extension_repos,categories,mangas_categories,excluded_scanlators}.sq` | Manga-side tables (9 files). |
| `../ANIYOMI/data/src/main/sqldelight/view/{libraryView,historyView,updatesView}.sq` | Manga-side views (3 files). |
| `../ANIYOMI/data/src/main/sqldelight/migrations/1.sqm` … `32.sqm` | Manga migrations (32 files). |
| `../ANIYOMI/data/src/main/sqldelightanime/dataanime/{animes,episodes,animehistory,anime_sync,animesources,extension_repos,categories,animes_categories,custom_buttons}.sq` | Anime-side tables (9 files). |
| `../ANIYOMI/data/src/main/sqldelightanime/view/{animelibView,animehistoryView,animeupdatesView,animeseasonsView,animedeletableView,episodestatsView,animehistorystatsView,animeseasonstatsView}.sq` | Anime-side views (8 files). |
| `../ANIYOMI/data/src/main/sqldelightanime/migrations/113.sqm` … `135.sqm` | Anime migrations (23 files). |
| `../ANIYOMI/data/src/main/java/tachiyomi/data/DatabaseAdapter.kt` | Column adapters (Date, List<String>, UpdateStrategy, FetchType). |
| `../ANIYOMI/data/src/main/java/tachiyomi/data/handlers/{manga,anime}/` | Per-DB handler interface + `Android*DatabaseHandler` + `*TransactionContext` + `QueryPaging*Source` (4 files each side). |
| `../ANIYOMI/data/src/main/java/tachiyomi/data/entries/{manga,anime}/{Manga,Anime}RepositoryImpl.kt` | Canonical repo impls (`MangaRepositoryImpl`, `AnimeRepositoryImpl`). |
| `../ANIYOMI/data/src/main/java/tachiyomi/data/entries/{manga,anime}/{Manga,Anime}Mapper.kt` | Row → domain-model mappers (anime side also maps `SeasonAnime` / `DeletableAnime`). |
| `../ANIYOMI/data/src/main/java/tachiyomi/data/items/{chapter,episode}/{Chapter,Episode}RepositoryImpl.kt` | Chapter/Episode repo impls + sanitizers. |
| `../ANIYOMI/data/src/main/java/tachiyomi/data/source/{manga,anime}/` | `*SourceRepositoryImpl` + `*StubSourceRepositoryImpl` + `*SourcePagingSource` (remote paging). |
| `../ANIYOMI/data/src/main/java/mihon/data/repository/{manga,anime}/{Manga,Anime}ExtensionRepoRepositoryImpl.kt` | Extension-repo repos (SQLDelight-backed). |
| `../ANIYOMI/data/src/main/java/tachiyomi/data/release/ReleaseServiceImpl.kt` | GitHub release fetcher (impl of `:domain`'s `ReleaseService`). |
| `../ANIYOMI/data/src/main/java/tachiyomi/data/custombutton/CustomButtonRepositoryImpl.kt` | Custom MPV-button repo (anime-only). |

## See also

- [`domain.md`](domain.md) — the `:domain` module whose interfaces `:data`
  implements.
- [`../04-data-models/database-schema.md`](../04-data-models/database-schema.md)
  — full table-by-table, column-by-column schema reference.
- [`../04-data-models/domain-models.md`](../04-data-models/domain-models.md)
  — the domain models that `:data` mappers produce.
- [`../01-architecture/01-architecture-overview.md`](../01-architecture/01-architecture-overview.md)
  — how `:data` fits in the layered architecture.
- [`../01-architecture/02-dependency-injection.md`](../01-architecture/02-dependency-injection.md)
  — how Injekt binds `:domain` interfaces to `:data` implementations and
  provides the `*DatabaseHandler` singletons.
- [`../01-architecture/03-state-and-async.md`](../01-architecture/03-state-and-async.md)
  — how `Flow`-returning repository methods become reactive UI state.
- [`../00-overview/03-module-map.md`](../00-overview/03-module-map.md) —
  the module-level dependency graph.
- [`../00-overview/05-project-conventions.md`](../00-overview/05-project-conventions.md)
  — the dual manga/anime pattern at the project level.
- [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) —
  how the `source/` packages interact with the runtime `*SourceManager`.
- [`../03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md)
  — how the `extension_repos` table is used by the extension-update
  subsystem.
- [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md)
  — how the `version` / `last_modified_at` / `is_syncing` columns (and
  their triggers) support backup/restore and sync.
