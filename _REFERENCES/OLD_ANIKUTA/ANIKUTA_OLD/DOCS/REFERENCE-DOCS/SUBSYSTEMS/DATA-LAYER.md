# Data Layer

SQLDelight-backed persistence for aniyomi, hosting two physically separate SQLite databases (manga + anime) and the repository implementations behind the `:domain` interfaces.

## Where it lives

- Module root: `REFERENCE/data/`
- Gradle config: `REFERENCE/data/build.gradle.kts` (namespace `tachiyomi.data`; SQLDelight plugin, two databases declared)
- Manga schema: `REFERENCE/data/src/main/sqldelight/data/*.sq` (tables) + `view/*.sq` (views) + `migrations/*.sqm` (1..32)
- Anime schema: `REFERENCE/data/src/main/sqldelightanime/dataanime/*.sq` + `view/*.sq` + `migrations/*.sqm` (113..135)
- Repository implementations (Kotlin): `REFERENCE/data/src/main/java/tachiyomi/data/`
  - `category/{anime,manga}/`, `entries/{anime,manga}/`, `handlers/{anime,manga}/`, `history/{anime,manga}/`, `items/{episode,chapter}/`, `release/`, `source/{anime,manga}/`, `track/{anime,manga}/`, `updates/{anime,manga}/`, `custombutton/`
- Mihon-side repository impls: `REFERENCE/data/src/main/java/mihon/data/repository/{anime,manga}/` (extension-repo repos only)
- Domain interfaces implemented by the above: `REFERENCE/domain/src/main/java/` under `tachiyomi.domain.*`, `aniyomi.domain.*`, `mihon.domain.*`
- DI wiring that constructs both DBs and binds every repo impl to its interface: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` (DBs + handlers) and `REFERENCE/app/src/main/java/eu/kanade/domain/DomainModule.kt` (repositories + interactors)

## What it does

The `:data` module is the persistence layer in aniyomi's clean-architecture stack:

1. Owns the SQLDelight schema (`.sq`/`.sqm` files) for **both** the manga DB and the anime DB.
2. Generates two SQLDelight `Database` classes at compile time — `tachiyomi.data.Database` (manga, package `tachiyomi.data`) and `tachiyomi.mi.data.AnimeDatabase` (anime, package `tachiyomi.mi.data`) — each exposing typed `*Queries` accessors.
3. Provides thin `*DatabaseHandler` wrappers (one per DB) that expose `await*` / `subscribeTo*` suspending + Flow APIs over those queries, plus a coroutine transaction context and a `PagingSource` adapter.
4. Provides `*RepositoryImpl` classes that implement `:domain` repository interfaces (`AnimeRepository`, `MangaRepository`, `EpisodeRepository`, `ChapterRepository`, `AnimeHistoryRepository`, `MangaHistoryRepository`, `AnimeCategoryRepository`, `MangaCategoryRepository`, `AnimeTrackRepository`, `MangaTrackRepository`, `AnimeSourceRepository`, `MangaSourceRepository`, `AnimeStubSourceRepository`, `MangaStubSourceRepository`, `AnimeUpdatesRepository`, `MangaUpdatesRepository`, `AnimeExtensionRepoRepository`, `MangaExtensionRepoRepository`, `CustomButtonRepository`, `ReleaseService`).
5. Provides `*Mapper` objects that translate SQLDelight generated row types into immutable `:domain` data classes (`Anime`, `Manga`, `Episode`, `Chapter`, `AnimeHistory`, `MangaHistory`, `AnimeTrack`, `MangaTrack`, `LibraryAnime`, `LibraryManga`, `SeasonAnime`, `AnimeUpdatesWithRelations`, `MangaUpdatesWithRelations`, `Category`, `StubAnimeSource`, `StubMangaSource`, `ExtensionRepo`, `CustomButton`, `Release`).
6. Provides `*Sanitizer` helpers for episode/chapter display name cleanup, plus `DateColumnAdapter` / `StringListColumnAdapter` / `MangaUpdateStrategyColumnAdapter` / `AnimeUpdateStrategyColumnAdapter` / `FetchTypeColumnAdapter` in `DatabaseAdapter.kt` (shared column adapters wired into both DBs).

`:data` never references UI, network, or app code. It depends only on `:domain` (interfaces + models), `:source-api` (for `UpdateStrategy`, `AnimeUpdateStrategy`, `FetchType` enums used by column adapters), and `:core:common` (for `logcat`, `withIOContext`, paging utilities). It exposes its implementations to `:app` via Injekt DI (see `DomainModule.kt`).

## Key files & schemas

### Manga DB (`sqldelight/`, package `tachiyomi.data`, generated class `Database`)

Tables in `sqldelight/data/`:

| File | Table(s) | Purpose |
|---|---|---|
| `mangas.sq` | `mangas` | Library/catalog manga entries (favorite flag, status, genre, cover, update strategy, version, is_syncing). |
| `chapters.sq` | `chapters` | Per-manga chapter rows (read, bookmark, last_page_read, chapter_number, scanlator, dates, version). FK→mangas with `ON DELETE CASCADE`. |
| `categories.sq` | `categories` | User-defined library categories (name, sort, flags, hidden). Seeds system category `_id=0`. Trigger blocks deletion of `_id <= 0`. |
| `mangas_categories.sq` | `mangas_categories` | Many-to-many between mangas and categories. Trigger increments manga `version` on insert (when not syncing). |
| `history.sq` | `history` | Reading history per chapter (last_read AS Date, time_read). FK→chapters CASCADE. |
| `manga_sync.sq` | `manga_sync` | Tracker rows per manga (sync_id, remote_id, last_chapter_read, status, score, dates, private). Unique on (manga_id, sync_id). |
| `sources.sq` | `sources` | Stub-source metadata (_id, lang, name) for sources whose extension is uninstalled. |
| `extension_repos.sq` | `extension_repos` | Extension repository registry (base_url PK, name, short_name, website, signing_key_fingerprint UNIQUE). |
| `excluded_scanlators.sq` | `excluded_scanlators` | Per-manga hidden scanlators. FK→mangas CASCADE. |

Views in `sqldelight/view/`:

| File | View | Purpose |
|---|---|---|
| `libraryView.sq` | `libraryView` (`library:` query) | Mangas with favorite=1 joined with chapter stats (total, readCount, latestUpload, lastRead, bookmarkCount) and category_id; honors `excluded_scanlators`. |
| `historyView.sq` | `historyView` (`history:`, `getLatestHistory:`) | Join mangas+chapters+history with per-manga max-read subquery for the history list. |
| `updatesView.sq` | `updatesView` (`getRecentUpdates:`, `getUpdatesByReadStatus:`) | New chapter rows for favorite mangas where `date_fetch > date_added`. |

Migrations: `migrations/1.sqm` … `migrations/32.sqm` (32 migration files). Latest schema version is 33.

Triggers (defined inline in the `.sq` files):
- `update_last_favorite_at_mangas` — sets `favorite_modified_at` on favorite change.
- `update_last_modified_at_mangas` — bumps `last_modified_at` on any update.
- `update_manga_version` — bumps `version` when url/description/favorite change while not syncing.
- `update_last_modified_at_chapters` — same for chapters.
- `update_chapter_and_manga_version` — bumps chapter + manga version when read/bookmark/last_page_read changes while not syncing.
- `insert_manga_category_update_version` — bumps manga version on category insert.
- `system_category_delete_trigger` — blocks deletion of system category `_id <= 0`.

### Anime DB (`sqldelightanime/`, package `tachiyomi.mi.data`, generated class `AnimeDatabase`)

Tables in `sqldelightanime/dataanime/`:

| File | Table(s) | Purpose |
|---|---|---|
| `animes.sq` | `animes` | Library/catalog anime entries. Same shape as `mangas` plus anime-only columns: `fetch_type` (FetchType enum), `parent_id` (season grouping), `season_flags`, `season_number`, `season_source_order`, `background_url`, `background_last_modified`. Triggers mirror manga (`update_last_favorite_at_animes`, `update_last_modified_at_animes`, `update_anime_version`). Indexes on `favorite`, `url`, `parent_id`, `fetch_type`. |
| `episodes.sq` | `episodes` | Per-anime episode rows (seen, bookmark, last_second_seen, total_seconds, episode_number, scanlator, summary, preview_url, fillermark, dates, version). FK→animes CASCADE. Triggers: `update_last_modified_at_episodes`, `update_episode_and_anime_version` (bumps both episode and parent anime version on seen/bookmark/last_second_seen change while not syncing). |
| `categories.sq` | `categories` | Anime library categories (structurally identical to manga `categories.sq`; queries renamed `getCategoriesByAnimeId` etc.). |
| `animes_categories.sq` | `animes_categories` | Many-to-many anime↔category with version-bump trigger. |
| `animehistory.sq` | `animehistory` | Watching history per episode (last_seen AS Date). FK→episodes CASCADE. (No `time_read` column — anime has no total-watch-duration tracking; manga does.) |
| `anime_sync.sq` | `anime_sync` | Tracker rows per anime (last_episode_seen, total_episodes, status, score, dates, private). Unique on (anime_id, sync_id). |
| `animesources.sq` | `animesources` | Stub anime source metadata. |
| `extension_repos.sq` | `extension_repos` | Anime extension-repo registry (byte-identical schema to manga's). |
| `custom_buttons.sq` | `custom_buttons` | Anime-only: user-defined mpv/Lua custom-player buttons (name, isFavorite, sortIndex, content, longPressContent, onStartup). Seeds a default "+85 s" skip-intro button. |

Views in `sqldelightanime/view/`:

| File | View | Purpose |
|---|---|---|
| `animelibView.sq` | `animelibView` (`animelib:`) | Like `libraryView` but dispatches via `CASE M.fetch_type WHEN 1 THEN episodestatsView WHEN 0 THEN animeseasonstatsView` to support both episode-counted anime (fetch_type=1) and season-counted anime (fetch_type=0). |
| `animehistoryView.sq` | `animehistoryView` (`animehistory:`, `getLatestAnimeHistory:`) | Anime equivalent of `historyView`. |
| `animeupdatesView.sq` | `animeupdatesView` (`getRecentAnimeUpdates:`, `getUpdatesBySeenStatus:`) | New episode rows for favorite anime. |
| `episodestatsView.sq` | `episodestatsView` | Per-anime episode aggregates (total, seenCount, latestUpload, fetchedAt, bookmarkCount, fillermarkCount). |
| `animeseasonstatsView.sq` | `animeseasonstatsView` | Per-parent-anime season aggregates (child_count, fully_seen_seasons, max_*_upload/fetched_at/last_seen, total_bookmarks, total_fillermarks). |
| `animeseasonsView.sq` | `animeseasonsView` (`getAnimeSeasonsById:`) | Animes where `parent_id IS NOT NULL` (seasons), with the same fetch_type-dispatched stats as `animelibView`. |
| `animehistorystatsView.sq` | `animehistorystatsView` | Per-anime max(last_seen). |
| `animedeletableView.sq` | `animedeletableView` (`getDeletableParentAnime:`) | Parent anime (parent_id IS NULL) with favorite=0 — candidates for cleanup. |

Migrations: `migrations/113.sqm` … `migrations/135.sqm` (23 migration files). Latest schema version is 136. The 113+ starting point is inherited from aniyomi's pre-SQLDelight anime DB version counter (the legacy anime database had reached v112 before SQLDelight adoption; aniyomi continued the counter from 113 rather than restarting at 1). TODO: confirm via git history of the aniyomi fork.

Notable migrations:
- `113.sqm` — backfill `episodes.date_upload` from `date_fetch` when 0.
- `114.sqm` — full schema rewrite (renames every table, recreates with SQLDelight-typed columns, rebuilds `animehistoryView`).
- `115.sqm` — drops the `animehistory_*` column prefixes, makes `last_seen` NOT NULL, removes `time_seen`.
- `128.sqm` — creates `extension_repos` table.
- `132.sqm` — rewrites `fetch_type` enum values (2→1, 1→0, 0→1).
- `134.sqm` — adds `episodes.summary`, `episodes.preview_url`, `episodes.fillermark`; adds `animes.background_url`, `animes.background_last_modified`; rebuilds `animeupdatesView`, `episodestatsView`, `animeseasonstatsView`, `animelibView`, `animeseasonsView`.
- `135.sqm` — sets all `animes.fetch_type = 1`.

### Repository classes

All in `REFERENCE/data/src/main/java/`:

| Domain interface | Implementation | Handler used | Mapper |
|---|---|---|---|
| `tachiyomi.domain.entries.anime.repository.AnimeRepository` | `tachiyomi.data.entries.anime.AnimeRepositoryImpl` | `AnimeDatabaseHandler` | `AnimeMapper` |
| `tachiyomi.domain.entries.manga.repository.MangaRepository` | `tachiyomi.data.entries.manga.MangaRepositoryImpl` | `MangaDatabaseHandler` | `MangaMapper` |
| `tachiyomi.domain.items.episode.repository.EpisodeRepository` | `tachiyomi.data.items.episode.EpisodeRepositoryImpl` | `AnimeDatabaseHandler` | (inline `mapEpisode`) |
| `tachiyomi.domain.items.chapter.repository.ChapterRepository` | `tachiyomi.data.items.chapter.ChapterRepositoryImpl` | `MangaDatabaseHandler` | (inline `mapChapter`) |
| `tachiyomi.domain.history.anime.repository.AnimeHistoryRepository` | `tachiyomi.data.history.anime.AnimeHistoryRepositoryImpl` | `AnimeDatabaseHandler` | `AnimeHistoryMapper` |
| `tachiyomi.domain.history.manga.repository.MangaHistoryRepository` | `tachiyomi.data.history.manga.MangaHistoryRepositoryImpl` | `MangaDatabaseHandler` | `MangaHistoryMapper` |
| `tachiyomi.domain.category.anime.repository.AnimeCategoryRepository` | `tachiyomi.data.category.anime.AnimeCategoryRepositoryImpl` | `AnimeDatabaseHandler` | (inline `mapCategory`) |
| `tachiyomi.domain.category.manga.repository.MangaCategoryRepository` | `tachiyomi.data.category.manga.MangaCategoryRepositoryImpl` | `MangaDatabaseHandler` | (inline `mapCategory`) |
| `tachiyomi.domain.track.anime.repository.AnimeTrackRepository` | `tachiyomi.data.track.anime.AnimeTrackRepositoryImpl` | `AnimeDatabaseHandler` | `AnimeTrackMapper` |
| `tachiyomi.domain.track.manga.repository.MangaTrackRepository` | `tachiyomi.data.track.manga.MangaTrackRepositoryImpl` | `MangaDatabaseHandler` | `MangaTrackMapper` |
| `tachiyomi.domain.source.anime.repository.AnimeSourceRepository` | `tachiyomi.data.source.anime.AnimeSourceRepositoryImpl` | `AnimeDatabaseHandler` (+ `AnimeSourceManager`) | `mapSourceToDomainSource` (inline) |
| `tachiyomi.domain.source.manga.repository.MangaSourceRepository` | `tachiyomi.data.source.manga.MangaSourceRepositoryImpl` | `MangaDatabaseHandler` (+ `MangaSourceManager`) | `mapSourceToDomainSource` (inline) |
| `tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository` | `tachiyomi.data.source.anime.AnimeStubSourceRepositoryImpl` | `AnimeDatabaseHandler` | (inline `mapStubSource`) |
| `tachiyomi.domain.source.manga.repository.MangaStubSourceRepository` | `tachiyomi.data.source.manga.MangaStubSourceRepositoryImpl` | `MangaDatabaseHandler` | (inline `mapStubSource`) |
| `tachiyomi.domain.updates.anime.repository.AnimeUpdatesRepository` | `tachiyomi.data.updates.anime.AnimeUpdatesRepositoryImpl` | `AnimeDatabaseHandler` | (inline `mapUpdatesWithRelations`) |
| `tachiyomi.domain.updates.manga.repository.MangaUpdatesRepository` | `tachiyomi.data.updates.manga.MangaUpdatesRepositoryImpl` | `MangaDatabaseHandler` | (inline `mapUpdatesWithRelations`) |
| `mihon.domain.extensionrepo.anime.repository.AnimeExtensionRepoRepository` | `mihon.data.repository.anime.AnimeExtensionRepoRepositoryImpl` | `AnimeDatabaseHandler` | (inline `mapExtensionRepo`) |
| `mihon.domain.extensionrepo.manga.repository.MangaExtensionRepoRepository` | `mihon.data.repository.manga.MangaExtensionRepoRepositoryImpl` | `MangaDatabaseHandler` | (inline `mapExtensionRepo`) |
| `tachiyomi.domain.custombuttons.repository.CustomButtonRepository` | `tachiyomi.data.custombutton.CustomButtonRepositoryImpl` | `AnimeDatabaseHandler` | (inline `mapCustomButton`) |
| `tachiyomi.domain.release.service.ReleaseService` | `tachiyomi.data.release.ReleaseServiceImpl` | (none — uses `NetworkHelper` + `Json`) | n/a (HTTP only) |

Helpers:
- `tachiyomi.data.DatabaseAdapter` — `DateColumnAdapter`, `StringListColumnAdapter`, `MangaUpdateStrategyColumnAdapter`, `AnimeUpdateStrategyColumnAdapter`, `FetchTypeColumnAdapter` (top-level objects, used by both DBs as needed).
- `tachiyomi.data.items.episode.EpisodeSanitizer`, `tachiyomi.data.items.chapter.ChapterSanitizer` — small string-cleanup helpers (parallel implementations).
- `tachiyomi.data.release.GithubRelease` — `@Serializable` DTO for the GitHub releases API.

## How it works

### SQLDelight wiring

`REFERENCE/data/build.gradle.kts` applies the SQLDelight Gradle plugin (`alias(libs.plugins.sqldelight)`) and declares **two** databases inside the `sqldelight { databases { ... } }` block:

```kotlin
sqldelight {
    databases {
        create("Database") {
            packageName.set("tachiyomi.data")
            dialect(libs.sqldelight.dialects.sql)
            schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
            srcDirs.from(project.file("./src/main/sqldelight"))
        }
        create("AnimeDatabase") {
            packageName.set("tachiyomi.mi.data")
            dialect(libs.sqldelight.dialects.sql)
            schemaOutputDirectory.set(project.file("./src/main/sqldelightanime"))
            srcDirs.from(project.file("./src/main/sqldelightanime"))
        }
    }
}
```

Each `create(...)` block produces one SQLDelight `Database` Kotlin class:
- `Database` is generated into package `tachiyomi.data` from the `.sq`/`.sqm` files under `src/main/sqldelight/`. Its generated row types live in package `data` (e.g. `data.Mangas`, `data.History`) — note the bare `data` package, not `tachiyomi.data`.
- `AnimeDatabase` is generated into package `tachiyomi.mi.data` from the `.sq`/`.sqm` files under `src/main/sqldelightanime/`. Its generated row types live in package `dataanime` (e.g. `dataanime.Animes`, `dataanime.Animehistory`).

Both DBs use the same SQL dialect (`libs.sqldelight.dialects.sql` — plain SQLite) and the same SQLDelight runtime (exposed via `api(libs.bundles.sqldelight)` in `data/build.gradle.kts`).

### Dual-database setup (driver + handler)

Construction happens in `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt`. Two independent `AndroidSqliteDriver` instances are created:

```kotlin
val sqlDriverManga = AndroidSqliteDriver(
    schema = Database.Schema,
    context = app,
    name = "tachiyomi.db",
    factory = ...,
    callback = object : AndroidSqliteDriver.Callback(Database.Schema) {
        override fun onOpen(db: SupportSQLiteDatabase) {
            setPragma(db, "foreign_keys = ON")
            setPragma(db, "journal_mode = WAL")
            setPragma(db, "synchronous = NORMAL")
        }
        ...
    },
)

val sqlDriverAnime = AndroidSqliteDriver(
    schema = AnimeDatabase.Schema,
    context = app,
    name = "tachiyomi.animedb",
    ...
    callback = object : AndroidSqliteDriver.Callback(AnimeDatabase.Schema) { ... same pragmas ... },
)
```

Key facts:
- Two **separate SQLite files**: `tachiyomi.db` (manga) and `tachiyomi.animedb` (anime). Same folder, no shared tables.
- Two **separate `SqlDriver` instances**. Nothing is shared at the driver level.
- Both open with the same pragmas: `foreign_keys = ON`, `journal_mode = WAL`, `synchronous = NORMAL`.
- Two **separate SQLDelight `Database` objects** constructed with their own column adapters:

```kotlin
addSingletonFactory {
    Database(
        driver = sqlDriverManga,
        historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
        mangasAdapter = Mangas.Adapter(
            genreAdapter = StringListColumnAdapter,
            update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
        ),
    )
}
addSingletonFactory {
    AnimeDatabase(
        driver = sqlDriverAnime,
        animehistoryAdapter = Animehistory.Adapter(last_seenAdapter = DateColumnAdapter),
        animesAdapter = Animes.Adapter(
            genreAdapter = StringListColumnAdapter,
            update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
            fetch_typeAdapter = FetchTypeColumnAdapter,
        ),
    )
}
```

- Two **separate `*DatabaseHandler` singletons** wrap them:

```kotlin
addSingletonFactory<MangaDatabaseHandler> { AndroidMangaDatabaseHandler(get(), sqlDriverManga) }
addSingletonFactory<AnimeDatabaseHandler> { AndroidAnimeDatabaseHandler(get(), sqlDriverAnime) }
```

The handler interface (`AnimeDatabaseHandler` / `MangaDatabaseHandler`) is identical in shape — `await`, `awaitList`, `awaitOne`, `awaitOneOrNull`, `awaitOneExecutable`, `awaitOneOrNullExecutable`, `subscribeToList`, `subscribeToOne`, `subscribeToOneOrNull`, `subscribeToPagingSource` — but each is bound to a different generated `Database`/`AnimeDatabase` receiver type, so they are not interchangeable.

The implementations (`AndroidAnimeDatabaseHandler`, `AndroidMangaDatabaseHandler`) are near-verbatim copies of each other that differ only in the receiver type and in the names of the internal transaction helpers (`withAnimeTransaction` / `withMangaTransaction`, `getCurrentAnimeDatabaseContext` / `getCurrentMangaDatabaseContext`, `AnimeTransactionElement` / `MangaTransactionElement` — see `AnimeTransactionContext.kt` and `MangaTransactionContext.kt`). There is no shared base class.

### Repository → handler → queries flow

A repository method (e.g. `AnimeRepositoryImpl.getLibraryAnimeAsFlow`) follows this flow:

1. Call `handler.subscribeToList { animelibViewQueries.animelib(AnimeMapper::mapLibraryAnime) }`.
2. `AndroidAnimeDatabaseHandler.subscribeToList` calls `block(db)` to get a `Query<T>`, then `asFlow().mapToList(queryDispatcher)`.
3. SQLDelight emits a new `List<T>` whenever any table referenced by the query changes.
4. Each row is mapped from the generated `AnimelibView` data class into the `LibraryAnime` domain model by `AnimeMapper.mapLibraryAnime` (which in turn delegates to `mapAnime` for the embedded `Anime`).

Writes follow the same pattern through `await(inTransaction = true) { ... }`, which routes through `withAnimeTransaction` (a single-threaded coroutine transaction context that takes over a thread from SQLDelight's query executor — see `AnimeTransactionContext.kt`). Multi-step writes (e.g. `setAnimeCategories`) wrap multiple statements in one transaction.

Updates use SQLDelight's `coalesce(:param, column)` idiom — every nullable `AnimeUpdate` field either overwrites or preserves the existing column value, so callers can do partial updates without reading first. The DB-side `update_anime_version` trigger auto-bumps `version` for relevant field changes (when `is_syncing = 0`), implementing aniyomi's sync/conflict-resolution versioning.

### Paging

`subscribeToPagingSource` returns an AndroidX Paging `PagingSource<Long, T>` implemented by `QueryPagingAnimeSource` / `QueryPagingMangaSource`. These register as a `Query.Listener` so the paging source is invalidated when underlying tables change. `AnimeSourceRepositoryImpl.searchAnime` / `getPopularAnime` / `getLatestAnime` use a separate non-DB paging source (`AnimeSourcePagingSource`) that pages over the network source directly — no DB involvement.

### Migrations

`.sqm` files are auto-applied by SQLDelight's `Schema.migrate` based on the DB version. A fresh install creates the DB at the latest version directly from the `.sq` schema. An existing DB at version N gets migrations N+1, N+2, … applied in order. Because the anime migrations start at 113, any anime DB below v113 cannot be migrated — aniyomi handles this by having introduced the anime DB only at v113 (TODO: confirm the legacy migration story from the aniyomi git history). The manga migrations form a complete chain 1→33.

## Dependencies

`:data` declares (in `REFERENCE/data/build.gradle.kts`):
- `implementation(projects.sourceApi)` — needs `UpdateStrategy`, `AnimeUpdateStrategy`, `FetchType` (column adapters).
- `implementation(projects.domain)` — implements `:domain` repository interfaces, returns `:domain` model classes.
- `implementation(projects.core.common)` — `logcat`, `withIOContext`, `toLong` extensions, paging helpers.
- `api(libs.bundles.sqldelight)` — exposes SQLDelight runtime to consumers.

Only `:app` depends on `:data`. `:app` provides the DI wiring (`AppModule.kt` constructs the DBs + handlers; `DomainModule.kt` binds every `*RepositoryImpl` to its `:domain` interface and constructs the `:domain` interactors that consume those interfaces).

`:data` does **not** depend on `:app`, `:source-local`, `:i18n`, `:presentation-*`, or any UI/network module. (The one exception is `ReleaseServiceImpl`, which takes a `NetworkHelper` from `eu.kanade.tachiyomi.network` — TODO: trace how this compiles, since `:data` does not declare a dependency on the network module. The `NetworkHelper` import resolves because `:app` provides it at runtime via Injekt; the compile-time dependency likely comes through a transitive path. See `ReleaseServiceImpl.kt` line 4: `import eu.kanade.tachiyomi.network.NetworkHelper`.)

## Anime vs manga

This is the critical section for the anime-first decision.

**Are `sqldelight/` (manga) and `sqldelightanime/` (anime) fully separate schemas?** Yes.

- Two physically separate SQLite files (`tachiyomi.db` and `tachiyomi.animedb`).
- Two separate `AndroidSqliteDriver` instances with no shared state.
- Two separate SQLDelight `Database.Schema` objects.
- Two separate generated `Database` / `AnimeDatabase` Kotlin classes in different packages (`tachiyomi.data` vs `tachiyomi.mi.data`); their row types live in `data.*` vs `dataanime.*`.
- Two separate `*DatabaseHandler` interfaces with identical shapes but incompatible receiver types — there is no common base interface and no shared abstraction.

**Do they share any tables or views?** No. Each `.sq`/`.sqm` file belongs to exactly one of the two source directories. There is no `.sq` file imported by both. The two `categories.sq` files (one per DB) are byte-similar but independent tables in independent DBs. The two `extension_repos.sq` files are byte-identical but again independent tables. The same applies to the `sources.sq` / `animesources.sq` pair, the `history.sq` / `animehistory.sq` pair, etc.

**Are there shared repository base classes?** No. Every `*RepositoryImpl` is standalone. The anime/manga pairs (`AnimeRepositoryImpl` / `MangaRepositoryImpl`, `AnimeCategoryRepositoryImpl` / `MangaCategoryRepositoryImpl`, etc.) are near-duplicate source files but do not extend a common abstract class. The only genuinely shared code at the `:data` module level is:

- `tachiyomi.data.DatabaseAdapter` — column adapters used by both DBs (manga DB uses `DateColumnAdapter`, `StringListColumnAdapter`, `MangaUpdateStrategyColumnAdapter`; anime DB uses `DateColumnAdapter`, `StringListColumnAdapter`, `AnimeUpdateStrategyColumnAdapter`, `FetchTypeColumnAdapter`).
- `tachiyomi.data.release.ReleaseServiceImpl` + `GithubRelease` — app-update check, anime/manga-agnostic.
- `tachiyomi.data.items.episode.EpisodeSanitizer` / `tachiyomi.data.items.chapter.ChapterSanitizer` — parallel implementations, not shared.

**Do anime repos import manga code or vice versa?** No. A grep for `AnimeDatabaseHandler` vs `MangaDatabaseHandler` confirms strict separation: every anime repo imports only `AnimeDatabaseHandler` (and `tachiyomi.mi.data.AnimeDatabase` where the receiver is named), every manga repo imports only `MangaDatabaseHandler` (and `tachiyomi.data.Database`). No anime-side file references a manga-side query type, and no manga-side file references an anime-side query type. The two halves of the `:data` module are compile-time decoupled.

**Could we ship anime-only by dropping `sqldelight/` cleanly?** Yes, with caveats:

1. **Schema side**: removing `src/main/sqldelight/` (the directory) plus the `create("Database") { ... }` block in `data/build.gradle.kts` removes the manga DB cleanly. No anime `.sq`/`.sqm` file references any manga table.

2. **Repository side**: removing `tachiyomi.data.{entries.manga, items.chapter, history.manga, category.manga, track.manga, source.manga, updates.manga}` packages plus `mihon.data.repository.manga.MangaExtensionRepoRepositoryImpl` removes the manga repositories. No anime repo imports from any of those packages.

3. **Handler side**: removing `tachiyomi.data.handlers.manga` (the whole package) removes the manga handler. No anime code references it.

4. **Shared `:data` code that stays**: `DatabaseAdapter.kt` (keep `DateColumnAdapter`, `StringListColumnAdapter`, `AnimeUpdateStrategyColumnAdapter`, `FetchTypeColumnAdapter`; drop `MangaUpdateStrategyColumnAdapter`), `release/` (stays — anime/manga-agnostic), `custombutton/` (stays — anime-only already).

5. **Domain interfaces that need removal**: the corresponding `tachiyomi.domain.*.manga.*` interfaces and models in `:domain` would also need to be removed or kept-but-unimplemented. The `:domain` module itself mirrors the same anime/manga split, so the surgery extends one module up.

6. **App-level wiring**: `AppModule.kt` would lose the `sqlDriverManga`, `Database(...)`, and `MangaDatabaseHandler` factories (about 40 lines). `DomainModule.kt` would lose every `Manga*RepositoryImpl` binding and every `Manga*` interactor binding (about 80 lines).

7. **Backup**: `Backup.kt` and `BackupCreator.kt` put anime and manga sections into one protobuf file. An anime-only build would naturally produce backups with empty manga sections (or the manga fields could be removed from the proto schema — TODO: decide whether to keep proto-field numbers stable for future manga reintroduction).

8. **No code in `:data` couples anime to manga.** The reverse — manga code depending on anime — is also false. The two are siblings, not parent/child.

Bottom line: aniyomi's `:data` module is already structurally anime-extractable. The cost is mechanical deletion across `:data`, `:domain`, and `:app`'s DI modules, not architectural refactoring.

## Relationships

- **SOURCE-SYSTEM** (see `SOURCE-SYSTEM.md`): The `AnimeSourceRepository` / `MangaSourceRepository` implementations bridge `:data` and `:source-api`. `AnimeSourceRepositoryImpl` takes both an `AnimeDatabaseHandler` (for `getAnimeSourceIdWithFavoriteCount`) and an `AnimeSourceManager` (for live source enumeration). The `animesources` / `sources` tables persist "stub" source metadata for entries whose extension has been uninstalled, so library rows still render. `AnimeStubSourceRepositoryImpl` / `MangaStubSourceRepositoryImpl` are pure-DB (no `SourceManager`).

- **DOWNLOAD-MANAGER** (see `DOWNLOAD-MANAGER.md`): The download managers do not touch `:data` directly — they keep their own state in SharedPreferences and the filesystem. They do, however, consume `:domain` interactors (`GetEpisode`, `GetChapter`, etc.) that ultimately read from `:data`. The download pipeline writes back seen/read progress through `EpisodeRepository` / `ChapterRepository`.

- **TRACKERS** (see `TRACKERS.md`): The `anime_sync` / `manga_sync` tables are the local mirror of tracker state. `AnimeTrackRepositoryImpl` / `MangaTrackRepositoryImpl` provide the CRUD. The `:app`-level `DelayedAnimeTrackingStore` / `DelayedMangaTrackingStore` are an offline queue layered on top, flushed by WorkManager jobs that call back into the repository. The `is_syncing` column on `animes`/`episodes`/`mangas`/`chapters` and the version-bump triggers exist primarily to support two-way tracker sync without spurious local-version increments.

- **BACKUP-RESTORE**: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/backup/` (not yet documented as its own SUBSYSTEMS doc) consumes `:domain` repositories to serialize/deserialize a `.proto.gz` backup file. The `Backup` proto (in `Backup.kt`) carries anime and manga sections side-by-side (`backupAnime` @ProtoNumber 3, `backupManga` @ProtoNumber 1; `backupAnimeCategories` @4, `backupAnimeSources` @103, `backupAnimeExtensionRepo` @107; manga equivalents at 2/102/106/108). On restore, the backup creator calls `AnimeRepository.insertAnime`, `EpisodeRepository.addAllEpisodes`, `AnimeCategoryRepository.insertAnimeCategory`, `AnimeTrackRepository.insertAnime`, `AnimeExtensionRepoRepository.insertRepo`, etc. — so every anime-side `:data` table has a corresponding backup field. Custom buttons are **not** currently in the backup proto (TODO: confirm).

- **DOMAIN module**: `:data` is the implementation-side twin of `:domain`. Every public repository interface in `:domain` has exactly one implementation in `:data` (registered in `DomainModule.kt`). `:data` cannot exist without `:domain` (compile-time dependency). `:domain` cannot be exercised at runtime without `:data`. The mapper layer is the only place where `:data` constructs `:domain` model instances.

## Notes for our build (anime-first)

If anikuta ships anime-only, the data layer simplifies dramatically:

- **Keep** all of `src/main/sqldelightanime/` and `tachiyomi.data.{handlers.anime, entries.anime, items.episode, history.anime, category.anime, track.anime, source.anime, updates.anime, custombutton}` plus `mihon.data.repository.anime` and the anime-relevant pieces of `release/` and `DatabaseAdapter.kt`.
- **Drop** `src/main/sqldelight/`, `tachiyomi.data.{handlers.manga, entries.manga, items.chapter, history.manga, category.manga, track.manga, source.manga, updates.manga}` and `mihon.data.repository.manga`. Also drop `ChapterSanitizer.kt` and `MangaUpdateStrategyColumnAdapter`.
- **Edit** `data/build.gradle.kts`: remove the `create("Database") { ... }` block, keep only `create("AnimeDatabase") { ... }`. Optionally rename the directory `sqldelightanime/` to `sqldelight/` for tidiness (and update `srcDirs.from` + `schemaOutputDirectory.set`).
- **Edit** `:app`'s `AppModule.kt`: remove `sqlDriverManga`, the `Database(...)` factory, and the `MangaDatabaseHandler` factory. Edit `DomainModule.kt`: remove every `Manga*RepositoryImpl` binding and every `Manga*` interactor factory.
- **Edit** `:domain`: remove every `tachiyomi.domain.*.manga.*` interface, model, and interactor. Keep the shared `tachiyomi.domain.category.model.Category`, `CategoryUpdate`, `tachiyomi.domain.entries.EntryCover`, `tachiyomi.domain.entries.TriState`, `tachiyomi.domain.library.model.*`, `tachiyomi.domain.release.*`, `tachiyomi.domain.backup.service.*`, `tachiyomi.domain.download.service.DownloadPreferences`, `tachiyomi.domain.storage.*`, `tachiyomi.domain.custombuttons.*`, and `aniyomi.domain.anime.*`.
- **Backup proto**: keep the field numbers stable (do not renumber `backupAnime` etc.). Either keep `backupManga`/`backupMangaCategories`/`backupMangaSources`/`backupMangaExtensionRepo` fields as no-ops or strip them. Keeping them is cheaper for forward-compat.
- **Renaming opportunity**: the `tachiyomi.mi.data` package name (chosen to avoid clashing with the manga `tachiyomi.data` package) becomes a vestige once manga is gone. Renaming `AnimeDatabase`'s package to `tachiyomi.data` (and the row types from `dataanime.*` to `data.*`) is a mechanical SQLDelight config change but would touch every anime repo + handler. Optional, defer.

**Migration path if we add manga later**: re-add the manga `.sq`/`.sqm` files, the `create("Database")` block, the manga handler + repos, the manga `:domain` interfaces, and the `:app` DI bindings. Because the anime and manga halves are decoupled in aniyomi already, this is purely additive — no anime code would need to change. The version counters in the two DBs are independent, so manga's "start at v1, migrate to v33" chain can be reintroduced without touching anime's v113→v136 chain. Backup proto fields 1/2/101/102/106/108 are already reserved for manga.

## TODOs / open questions

- Confirm the historical reason the anime DB version counter starts at 113. Hypothesis: aniyomi inherited the anime DB from a pre-SQLDelight Tachiyomi-era anime fork that had a version counter reaching 112. Verify by reading the aniyomi git history around the SQLDelight migration commit.
- `ReleaseServiceImpl` imports `eu.kanade.tachiyomi.network.NetworkHelper`, but `:data`'s `build.gradle.kts` does not declare a dependency on the network module. Trace how this compiles. Likely: the network module is a transitive dependency of `:core:common` or `:source-api`, or the import resolves through the `:app`-side classpath at runtime (unlikely for compile). TODO: open `libs.versions.toml` and the network module's `build.gradle.kts` to confirm.
- Confirm custom buttons are excluded from the backup proto (no `backupCustomButtons` field in `Backup.kt`). If so, document as a known gap.
- Aniyomi's anime `animehistory` table has no `time_read` analog (anime doesn't track total watch duration), unlike manga's `history.time_read`. This means anime has no "total watch time" stat. TODO: confirm this is intentional and not a regression.
- The `anime_sync` and `manga_sync` tables share the column name `private` (a SQL-reserved-ish word in some dialects). SQLite accepts it unquoted; verify this doesn't bite if we ever swap dialects.
- The `extension_repos.sq` files in the two DBs are byte-identical. Consider whether a single shared table could replace both — but this would require merging the two SQLite files, which breaks the dual-DB architecture. Not recommended; just noting the duplication.
- TODO: document the `custom_buttons.onStartup` Lua/mpv-script mechanism in a separate PLAYER-DOC (the seed row in `custom_buttons.sq` contains a multi-line Lua script with mpv-observe_property — relevant to the player subsystem, not data layer).
