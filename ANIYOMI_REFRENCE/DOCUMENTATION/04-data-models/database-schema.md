# 04-data-models / `database-schema.md` — SQLDelight schema

> Column-by-column reference for every table and view in both of Aniyomi's
> parallel SQLDelight databases. Source roots:
> - **Manga DB** — `../ANIYOMI/data/src/main/sqldelight/`
>   (package `tachiyomi.data`, generated class `Database`).
> - **Anime DB** — `../ANIYOMI/data/src/main/sqldelightanime/`
>   (package `tachiyomi.mi.data`, generated class `AnimeDatabase`).
>
> See [`../02-modules/data.md`](../02-modules/data.md) for the narrative on the
> dual-schema layout and the `*DatabaseHandler` abstraction; this doc is the
> field-level lookup.

## Schema layout at a glance

```
data/src/main/
├── sqldelight/                       ← MANGA DB (Database, tachiyomi.data)
│   ├── data/                         ← 9 .sq table files
│   │   ├── mangas.sq
│   │   ├── chapters.sq
│   │   ├── categories.sq
│   │   ├── mangas_categories.sq
│   │   ├── history.sq
│   │   ├── manga_sync.sq
│   │   ├── sources.sq
│   │   ├── excluded_scanlators.sq    (manga-only)
│   │   └── extension_repos.sq
│   ├── view/                         ← 3 .sq view files
│   │   ├── libraryView.sq
│   │   ├── historyView.sq
│   │   └── updatesView.sq
│   └── migrations/                   ← 32 .sqm files (1.sqm … 32.sqm)
│
└── sqldelightanime/                  ← ANIME DB (AnimeDatabase, tachiyomi.mi.data)
    ├── dataanime/                    ← 9 .sq table files
    │   ├── animes.sq
    │   ├── episodes.sq
    │   ├── categories.sq             (same shape as manga side)
    │   ├── animes_categories.sq
    │   ├── animehistory.sq
    │   ├── anime_sync.sq
    │   ├── animesources.sq
    │   ├── custom_buttons.sq         (anime-only)
    │   └── extension_repos.sq
    ├── view/                         ← 8 .sq view files
    │   ├── animelibView.sq
    │   ├── animehistoryView.sq
    │   ├── animeupdatesView.sq
    │   ├── episodestatsView.sq
    │   ├── animehistorystatsView.sq
    │   ├── animeseasonstatsView.sq
    │   ├── animeseasonsView.sq
    │   └── animedeletableView.sq
    └── migrations/                   ← 23 .sqm files (113.sqm … 135.sqm)
```

**Versions:**
- Manga schema version: **32** (migrations `1..32`); latest schema baseline.
- Anime schema version: **135** (migrations `113..135`); anime was bootstrapped
  at version **113** (when manga was at v112), so the anime baseline matches
  manga's v112 shape and then diverges.

Each `.sq` file declares the `CREATE TABLE` (or `CREATE VIEW`) followed by
named queries (`name: SELECT …` / `name: INSERT …`). SQLDelight compiles
each `.sq` file into a `*Queries` class accessible from the `Database` /
`AnimeDatabase` object.

---

## Manga schema (`sqldelight/data/`)

### `mangas.sq` — `mangas` table

The core manga row. Source: `../ANIYOMI/data/src/main/sqldelight/data/mangas.sq`

| Column | Type | Notes |
|---|---|---|
| `_id` | `INTEGER NOT NULL PRIMARY KEY` | PK. |
| `source` | `INTEGER NOT NULL` | Source id. |
| `url` | `TEXT NOT NULL` | Source-relative URL. |
| `artist` | `TEXT` | |
| `author` | `TEXT` | |
| `description` | `TEXT` | |
| `genre` | `TEXT AS List<String>` | Comma-separated; `StringListColumnAdapter`. |
| `title` | `TEXT NOT NULL` | |
| `status` | `INTEGER NOT NULL` | `SManga.UNKNOWN..ON_HIATUS`. |
| `thumbnail_url` | `TEXT` | |
| `favorite` | `INTEGER AS Boolean NOT NULL` | Library flag. |
| `last_update` | `INTEGER` | Epoch millis of last library-update fetch. |
| `next_update` | `INTEGER` | Scheduled next update. |
| `initialized` | `INTEGER AS Boolean NOT NULL` | Details fetched? |
| `viewer` | `INTEGER NOT NULL` | Packed viewer/reading-mode flags. |
| `chapter_flags` | `INTEGER NOT NULL` | Packed chapter display/sort/filter bitmask. |
| `cover_last_modified` | `INTEGER NOT NULL` | Cover cache-bust. |
| `date_added` | `INTEGER NOT NULL` | When added to library. |
| `update_strategy` | `INTEGER AS UpdateStrategy NOT NULL DEFAULT 0` | `MangaUpdateStrategyColumnAdapter` (ordinal). |
| `calculate_interval` | `INTEGER DEFAULT 0 NOT NULL` | Adaptive fetch interval (days). |
| `last_modified_at` | `INTEGER NOT NULL DEFAULT 0` | Trigger-maintained. |
| `favorite_modified_at` | `INTEGER` | Trigger-maintained. |
| `version` | `INTEGER NOT NULL DEFAULT 0` | Trigger-maintained sync version. |
| `is_syncing` | `INTEGER NOT NULL DEFAULT 0` | When 1, triggers skip version bump. |

**Indexes:**
- `library_favorite_index` ON `mangas(favorite)` `WHERE favorite = 1` — partial index for the library query.
- `mangas_url_index` ON `mangas(url)`.

**Triggers:**
- `update_last_favorite_at_mangas` — `AFTER UPDATE OF favorite`, sets `favorite_modified_at = strftime('%s','now')`.
- `update_last_modified_at_mangas` — `AFTER UPDATE` (any column), sets `last_modified_at = strftime('%s','now')`.
- `update_manga_version` — `AFTER UPDATE`, when `is_syncing = 0` and (`url`/`description`/`favorite` changed), increments `version`.

**Key queries:** `getMangaById`, `getMangaByUrlAndSource`, `getFavorites`,
`getReadMangaNotInLibrary`, `getAllManga`, `getSourceIdWithFavoriteCount`,
`getFavoriteBySourceId`, `getDuplicateLibraryManga`, `getUpcomingManga`,
`resetViewerFlags`, `resetIsSyncing`, `getSourceIdsWithNonLibraryManga`,
`deleteMangasNotInLibraryBySourceIds`, `insert`, `update` (uses `coalesce`
for partial patches), `selectLastInsertedRowId`.

### `chapters.sq` — `chapters` table

| Column | Type | Notes |
|---|---|---|
| `_id` | `INTEGER NOT NULL PRIMARY KEY` | |
| `manga_id` | `INTEGER NOT NULL` | FK → `mangas(_id)` `ON DELETE CASCADE`. |
| `url` | `TEXT NOT NULL` | |
| `name` | `TEXT NOT NULL` | |
| `scanlator` | `TEXT` | |
| `read` | `INTEGER AS Boolean NOT NULL` | |
| `bookmark` | `INTEGER AS Boolean NOT NULL` | |
| `last_page_read` | `INTEGER NOT NULL` | |
| `chapter_number` | `REAL NOT NULL` | |
| `source_order` | `INTEGER NOT NULL` | |
| `date_fetch` | `INTEGER NOT NULL` | |
| `date_upload` | `INTEGER NOT NULL` | |
| `last_modified_at` | `INTEGER NOT NULL DEFAULT 0` | Trigger-maintained. |
| `version` | `INTEGER NOT NULL DEFAULT 0` | Trigger-maintained. |
| `is_syncing` | `INTEGER NOT NULL DEFAULT 0` | |

**Indexes:** `chapters_manga_id_index` ON `chapters(manga_id)`;
`chapters_unread_by_manga_index` ON `chapters(manga_id, read)` `WHERE read = 0`.

**Triggers:**
- `update_last_modified_at_chapters` — sets `last_modified_at` on any update.
- `update_chapter_and_manga_version` — `AFTER UPDATE` when `is_syncing = 0`
  and `read`/`bookmark`/`last_page_read` changed: bumps **both** the chapter's
  `version` and the parent manga's `version` (so sync can detect read-progress
  changes from either side).

**Key queries:** `getChapterById`, `getChaptersByMangaId` (LEFT JOINs
`excluded_scanlators` to apply the per-manga scanlator filter when
`:applyScanlatorFilter = 1`), `getScanlatorsByMangaId`,
`getBookmarkedChaptersByMangaId`, `getChapterByUrl`,
`getChapterByUrlAndMangaId`, `removeChaptersWithIds`, `resetIsSyncing`,
`insert`, `update` (partial patch), `selectLastInsertedRowId`.

### `categories.sq` — `categories` table (manga side)

Same physical shape as the anime-side `categories` — they are **two separate
SQLite tables** with identical schema.

| Column | Type | Notes |
|---|---|---|
| `_id` | `INTEGER NOT NULL PRIMARY KEY` | |
| `name` | `TEXT NOT NULL` | |
| `sort` | `INTEGER NOT NULL` | Order (mapped to domain `Category.order`). |
| `flags` | `INTEGER NOT NULL` | Packed sort/display bitmask (`MangaLibrarySort`). |
| `hidden` | `INTEGER NOT NULL DEFAULT 0` | |

**Seed:** `INSERT OR IGNORE INTO categories(_id, name, sort, flags) VALUES (0, "", -1, 0)`
— the system "Default" category at `_id = 0`.

**Trigger:** `system_category_delete_trigger` (BEFORE DELETE) — `RAISE(ABORT,
"System category can't be deleted")` when `old._id <= 0`.

**Key queries:** `getCategory`, `getCategories`, `getVisibleCategories`,
`getCategoriesByMangaId` (JOIN `mangas_categories`),
`getVisibleCategoriesByMangaId`, `insert`, `delete`, `update`,
`updateAllFlags`, `selectLastInsertedRowId`.

### `mangas_categories.sq` — `mangas_categories` join table

| Column | Type | Notes |
|---|---|---|
| `_id` | `INTEGER NOT NULL PRIMARY KEY` | |
| `manga_id` | `INTEGER NOT NULL` | FK → `mangas(_id)` cascade. |
| `category_id` | `INTEGER NOT NULL` | FK → `categories(_id)` cascade. |

**Trigger:** `insert_manga_category_update_version` — `AFTER INSERT`, bumps
parent manga's `version` (when manga isn't `is_syncing`). Lets sync detect
category-binding changes.

**Queries:** `insert`, `deleteMangaCategoryByMangaId`.

### `history.sq` — `history` table (manga)

| Column | Type | Notes |
|---|---|---|
| `_id` | `INTEGER NOT NULL PRIMARY KEY` | |
| `chapter_id` | `INTEGER NOT NULL UNIQUE` | FK → `chapters(_id)` cascade. One row per chapter. |
| `last_read` | `INTEGER AS Date` | `DateColumnAdapter` (epoch millis). |
| `time_read` | `INTEGER NOT NULL` | Accumulated read time (millis). |

**Index:** `history_history_chapter_id_index` ON `history(chapter_id)`.

**`upsert` query** uses `ON CONFLICT(chapter_id) DO UPDATE SET last_read = :readAt, time_read = time_read + :time_read` — accumulates session-read time.

**Queries:** `getHistoryByMangaId`, `getHistoryByChapterUrl`,
`resetHistoryById`, `resetHistoryByMangaId`, `removeAllHistory`,
`removeResettedHistory` (delete rows where `last_read = 0`), `upsert`,
`getReadDuration` (`sum(time_read)`).

### `manga_sync.sq` — `manga_sync` table (tracker state)

| Column | Type | Notes |
|---|---|---|
| `_id` | `INTEGER NOT NULL PRIMARY KEY` | |
| `manga_id` | `INTEGER NOT NULL` | FK → `mangas(_id)` cascade. |
| `sync_id` | `INTEGER NOT NULL` | Tracker id (1=MAL, 2=AniList, 3=Shikimori, 4=Bangumi, …). |
| `remote_id` | `INTEGER NOT NULL` | Tracker-side media id. |
| `library_id` | `INTEGER` | Legacy, unused. |
| `title` | `TEXT NOT NULL` | |
| `last_chapter_read` | `REAL NOT NULL` | |
| `total_chapters` | `INTEGER NOT NULL` | |
| `status` | `INTEGER NOT NULL` | Tracker status code. |
| `score` | `REAL NOT NULL` | |
| `remote_url` | `TEXT NOT NULL` | |
| `start_date` | `INTEGER NOT NULL` | |
| `finish_date` | `INTEGER NOT NULL` | |
| `private` | `INTEGER AS Boolean DEFAULT 0 NOT NULL` | Added in migration 32. |

**Constraint:** `UNIQUE (manga_id, sync_id) ON CONFLICT REPLACE` — one row
per (manga, tracker) pair; re-inserting replaces.

**Queries:** `delete`, `getTracks`, `getTrackById`, `getTracksByMangaId`,
`insert`, `update` (partial patch via `coalesce`).

### `sources.sq` — `sources` table (stub-source registry, manga)

| Column | Type | Notes |
|---|---|---|
| `_id` | `INTEGER NOT NULL PRIMARY KEY` | Source id. |
| `lang` | `TEXT NOT NULL` | |
| `name` | `TEXT NOT NULL` | |

**Queries:** `findAll`, `findOne`, `upsert` (`ON CONFLICT(_id) DO UPDATE`).

Used so that library manga whose extension was uninstalled can still display
the source's name/lang without crashing.

### `excluded_scanlators.sq` — `excluded_scanlators` table (manga-only)

Per-manga scanlator exclusion list (no anime equivalent).

| Column | Type | Notes |
|---|---|---|
| `manga_id` | `INTEGER NOT NULL` | FK → `mangas(_id)` cascade. |
| `scanlator` | `TEXT NOT NULL` | |

No primary key — `(manga_id, scanlator)` is the logical key. **Index:**
`excluded_scanlators_manga_id_index`.

**Queries:** `insert`, `remove` (`scanlator IN :scanlators`),
`getExcludedScanlatorsByMangaId`.

Read by `chapters.sq`'s `getChaptersByMangaId` LEFT JOIN — chapters whose
scanlator is in this table are filtered out when `:applyScanlatorFilter = 1`.

### `extension_repos.sq` — `extension_repos` table (manga side)

| Column | Type | Notes |
|---|---|---|
| `base_url` | `TEXT NOT NULL PRIMARY KEY` | Repo base URL. |
| `name` | `TEXT NOT NULL` | Display name. |
| `short_name` | `TEXT` | Optional short name. |
| `website` | `TEXT NOT NULL` | Repo website. |
| `signing_key_fingerprint` | `TEXT UNIQUE NOT NULL` | Repo signing key fingerprint. |

Same schema as the anime side (a separate physical table).

**Queries:** `findOne`, `findOneBySigningKeyFingerprint`, `findAll`, `count`,
`insert`, `upsert` (`ON CONFLICT(base_url) DO UPDATE`),
`replace` (`ON CONFLICT(signing_key_fingerprint) DO UPDATE` — lets the user
swap a repo's URL when the signing key matches), `delete`.

### Manga views (`sqldelight/view/`) — 3 files

#### `libraryView.sq` — `libraryView` view

Joins `mangas` × aggregated `chapters` (with `excluded_scanlators` LEFT JOIN
to filter excluded scanlators out of the counts) × `mangas_categories`,
filtered to `favorite = 1`. Produces the per-manga `totalCount`, `readCount`,
`latestUpload`, `chapterFetchedAt`, `lastRead`, `bookmarkCount`, `category`
columns that back the `LibraryManga` domain model. Query: `library: SELECT * FROM libraryView`.

#### `historyView.sq` — `historyView` view

Joins `mangas` × `chapters` × `history` plus a `max_last_read` subquery to
find each manga's most-recent chapter. The `history: ...` query filters to
`readAt > 0 AND maxReadAtChapterId = chapterId AND title LIKE :query`, ordered
by `readAt DESC` — drives the "Recently read" screen. `getLatestHistory:`
returns the single most-recent row.

#### `updatesView.sq` — `updatesView` view

Joins `mangas` × `chapters` for library favorites where `date_fetch > date_added`
(newly fetched chapters). Queries: `getRecentUpdates` (filter by `dateUpload > :after`, limit), `getUpdatesByReadStatus` (filter by `read = :read`).

---

## Anime schema (`sqldelightanime/dataanime/`)

### `animes.sq` — `animes` table

Mirrors `mangas` **plus anime-only columns**: `fetch_type`, `parent_id`,
`season_flags`, `season_number`, `season_source_order`, `background_url`,
`background_last_modified`.

| Column | Type | Notes |
|---|---|---|
| `_id` | `INTEGER NOT NULL PRIMARY KEY` | |
| `source` | `INTEGER NOT NULL` | |
| `url` | `TEXT NOT NULL` | |
| `artist` | `TEXT` | |
| `author` | `TEXT` | |
| `description` | `TEXT` | |
| `genre` | `TEXT AS List<String>` | `StringListColumnAdapter`. |
| `title` | `TEXT NOT NULL` | |
| `status` | `INTEGER NOT NULL` | `SAnime.UNKNOWN..ON_HIATUS`. |
| `thumbnail_url` | `TEXT` | |
| `favorite` | `INTEGER AS Boolean NOT NULL` | |
| `last_update` | `INTEGER` | |
| `next_update` | `INTEGER` | |
| `initialized` | `INTEGER AS Boolean NOT NULL` | |
| `viewer` | `INTEGER NOT NULL` | Packed: skip-intro length, next-airing episode+time. |
| `episode_flags` | `INTEGER NOT NULL` | Packed episode display/sort/filter bitmask. |
| `cover_last_modified` | `INTEGER NOT NULL` | |
| `date_added` | `INTEGER NOT NULL` | |
| `update_strategy` | `INTEGER AS AnimeUpdateStrategy NOT NULL DEFAULT 0` | `AnimeUpdateStrategyColumnAdapter`. |
| `calculate_interval` | `INTEGER DEFAULT 0 NOT NULL` | |
| `last_modified_at` | `INTEGER NOT NULL DEFAULT 0` | Trigger-maintained. |
| `favorite_modified_at` | `INTEGER` | Trigger-maintained. |
| `version` | `INTEGER NOT NULL DEFAULT 0` | Trigger-maintained. |
| `is_syncing` | `INTEGER NOT NULL DEFAULT 0` | |
| `fetch_type` | `INTEGER AS FetchType NOT NULL DEFAULT 1` | **Anime-only.** `FetchTypeColumnAdapter` (`1=Episodes`, `0=Seasons`). |
| `parent_id` | `INTEGER` | **Anime-only.** Set on season children. |
| `season_flags` | `INTEGER NOT NULL` | **Anime-only.** Packed season display/sort/filter bitmask. |
| `season_number` | `REAL NOT NULL` | **Anime-only.** `-1.0` for root. |
| `season_source_order` | `INTEGER NOT NULL` | **Anime-only.** Order within parent. |
| `background_url` | `TEXT` | **Anime-only.** Background image URL. |
| `background_last_modified` | `INTEGER NOT NULL` | **Anime-only.** Background cache-bust. |

**Indexes:**
- `animelib_favorite_index` ON `animes(favorite) WHERE favorite = 1` (partial).
- `animes_url_index` ON `animes(url)`.
- `animes_parent_id` ON `animes(parent_id)` — **anime-only**.
- `animes_fetch_type` ON `animes(fetch_type)` — **anime-only**.

**Triggers:** same triple pattern as manga:
- `update_last_favorite_at_animes` (on `favorite`).
- `update_last_modified_at_animes` (on any update).
- `update_anime_version` (bump `version` when `is_syncing = 0` and
  `url`/`description`/`favorite` changed).

**Key queries:** mirror manga side (`getAnimeById`, `getAnimeByUrlAndSource`,
`getFavorites`, `getWatchedAnimeNotInLibrary`, `getAllAnime`,
`getAnimeSourceIdWithFavoriteCount`, `getFavoriteBySourceId`,
`getDuplicateLibraryAnime`, `getUpcomingAnime`, `resetViewerFlags`,
`resetIsSyncing`) **plus** anime-only: `removeParentIdByIds` (clears
`parent_id` on a set of anime), `getChildrenByParentId` (lists season
children), `deleteAnimesNotInLibraryByAnimeIds`. Same `insert`/`update`/
`selectLastInsertedRowId` pattern.

### `episodes.sq` — `episodes` table

Mirrors `chapters` plus anime-only fields: `seen`, `last_second_seen`,
`total_seconds`, `summary`, `preview_url`, `fillermark`.

| Column | Type | Notes |
|---|---|---|
| `_id` | `INTEGER NOT NULL PRIMARY KEY` | |
| `anime_id` | `INTEGER NOT NULL` | FK → `animes(_id)` cascade. |
| `url` | `TEXT NOT NULL` | |
| `name` | `TEXT NOT NULL` | |
| `scanlator` | `TEXT` | |
| `seen` | `INTEGER AS Boolean NOT NULL` | **Anime-only** (≈ `read`). |
| `bookmark` | `INTEGER AS Boolean NOT NULL` | |
| `last_second_seen` | `INTEGER NOT NULL` | **Anime-only.** Resume position (s). |
| `total_seconds` | `INTEGER NOT NULL` | **Anime-only.** Episode length (s). |
| `episode_number` | `REAL NOT NULL` | |
| `source_order` | `INTEGER NOT NULL` | |
| `date_fetch` | `INTEGER NOT NULL` | |
| `date_upload` | `INTEGER NOT NULL` | |
| `last_modified_at` | `INTEGER NOT NULL DEFAULT 0` | Trigger-maintained. |
| `version` | `INTEGER NOT NULL DEFAULT 0` | Trigger-maintained. |
| `is_syncing` | `INTEGER NOT NULL DEFAULT 0` | |
| `summary` | `TEXT` | **Anime-only.** Episode text summary. |
| `preview_url` | `TEXT` | **Anime-only.** Preview thumbnail URL. |
| `fillermark` | `INTEGER AS Boolean NOT NULL` | **Anime-only.** Filler flag. |

**Indexes:** `episodes_anime_id_index`; `episodes_unseen_by_anime_index` on
`(anime_id, seen) WHERE seen = 0` (partial).

**Triggers:** `update_last_modified_at_episodes`; `update_episode_and_anime_version`
(bumps both episode's and parent anime's `version` when `seen`/`bookmark`/
`last_second_seen` change and `is_syncing = 0`).

**Key queries:** mirror manga side (`getEpisodeById`, `getEpisodesByAnimeId`,
`getBookmarkedEpisodesByAnimeId`, `getEpisodeByUrl`,
`getEpisodeByUrlAndAnimeId`, `removeEpisodesWithIds`, `resetIsSyncing`,
`insert`, `update`, `selectLastInsertedRowId`). Note: **no** scanlator-filter
LEFT JOIN here (anime has no `excluded_scanlators` table).

### `categories.sq` — `categories` table (anime side)

Identical schema to the manga-side `categories.sq`. Same system-category seed
and `system_category_delete_trigger`. Queries mirror the manga side but with
`getCategoriesByAnimeId` / `getVisibleCategoriesByAnimeId` JOINing
`animes_categories` instead of `mangas_categories`.

### `animes_categories.sq` — `animes_categories` join table

Identical shape to `mangas_categories`: `_id`, `anime_id`, `category_id`,
both FKs cascade. **Trigger:** `insert_anime_category_update_version` —
bumps the parent anime's `version` on insert. **Queries:** `insert`,
`deleteAnimeCategoryByAnimeId`.

### `animehistory.sq` — `animehistory` table

| Column | Type | Notes |
|---|---|---|
| `_id` | `INTEGER NOT NULL PRIMARY KEY` | |
| `episode_id` | `INTEGER NOT NULL UNIQUE` | FK → `episodes(_id)` cascade. |
| `last_seen` | `INTEGER AS Date` | `DateColumnAdapter`. |

> **Note:** anime history has **no** `time_read` / cumulative-duration column
> — only `last_seen`. The manga side tracks total read time, the anime side
> only tracks the last-watched timestamp.

**Index:** `animehistory_history_episode_id_index`.

**`upsert` query:** `ON CONFLICT(episode_id) DO UPDATE SET last_seen = :seenAt`
(replaces, doesn't accumulate).

**Queries:** `getHistoryByAnimeId`, `getHistoryByEpisodeUrl`,
`resetAnimeHistoryById`, `resetHistoryByAnimeId`, `removeAllHistory`,
`removeResettedHistory`, `upsert`.

### `anime_sync.sq` — `anime_sync` table (tracker state)

Mirrors `manga_sync` (anime-side column names use `last_episode_seen` /
`total_episodes`). Same `UNIQUE (anime_id, sync_id) ON CONFLICT REPLACE`.
`private` column added in anime migration 134 (parallel to manga 32).

| Column | Type | Notes |
|---|---|---|
| `_id` | `INTEGER NOT NULL PRIMARY KEY` | |
| `anime_id` | `INTEGER NOT NULL` | FK → `animes(_id)` cascade. |
| `sync_id` | `INTEGER NOT NULL` | Tracker id. |
| `remote_id` | `INTEGER NOT NULL` | |
| `library_id` | `INTEGER` | Legacy. |
| `title` | `TEXT NOT NULL` | |
| `last_episode_seen` | `REAL NOT NULL` | |
| `total_episodes` | `INTEGER NOT NULL` | |
| `status` | `INTEGER NOT NULL` | |
| `score` | `REAL NOT NULL` | |
| `remote_url` | `TEXT NOT NULL` | |
| `start_date` | `INTEGER NOT NULL` | |
| `finish_date` | `INTEGER NOT NULL` | |
| `private` | `INTEGER AS Boolean DEFAULT 0 NOT NULL` | |

**Queries:** `delete`, `getAnimeTracks`, `getTrackByAnimeId`,
`getTracksByAnimeId`, `insert`, `update`.

### `animesources.sq` — `animesources` table (stub-source registry, anime)

Identical shape to manga `sources.sq`: `_id`, `lang`, `name`. Same
`findAll` / `findOne` / `upsert` (`ON CONFLICT(_id) DO UPDATE`).

### `custom_buttons.sq` — `custom_buttons` table (anime-only)

User-defined MPV Lua buttons. No manga equivalent.

| Column | Type | Notes |
|---|---|---|
| `_id` | `INTEGER NOT NULL PRIMARY KEY` | |
| `name` | `TEXT NOT NULL` | Button label. |
| `isFavorite` | `INTEGER AS Boolean NOT NULL` | Pinned to controls bar. |
| `sortIndex` | `INTEGER NOT NULL` | Display order. |
| `content` | `TEXT NOT NULL` | Lua source for tap. |
| `longPressContent` | `TEXT NOT NULL` | Lua source for long-press. |
| `onStartup` | `TEXT NOT NULL` | Lua source run on player startup. |

**Seed:** `INSERT OR IGNORE INTO custom_buttons (_id, name, isFavorite, sortIndex, content, longPressContent, onStartup) VALUES (1, '+85 s', 1, 0, …)` —
the default `+85 s` skip-intro button. The default `content` calls
`aniyomi.right_seek_by(intro_length)`; `longPressContent` opens an int-picker
dialog to change the intro length; `onStartup` registers an
`mp.observe_property("user-data/current-anime/intro-length", …)` that
updates the button title and hides it when length = 0.

**Queries:** `findAll` (`ORDER BY sortIndex`), `insert`, `delete` (by `_id`),
`update` (partial patch via `coalesce`), `selectLastInsertedRowId`.

### `extension_repos.sq` — `extension_repos` table (anime side)

Identical schema to the manga-side `extension_repos.sq` (separate physical
table). Same `findOne` / `findOneBySigningKeyFingerprint` / `findAll` /
`count` / `insert` / `upsert` / `replace` / `delete` queries.

---

## Anime views (`sqldelightanime/view/`) — 8 files

The anime side has 8 views (vs the manga side's 3) because of the **seasons**
feature (an anime may have child "season" rows under a parent anime via
`parent_id`) and the **deletable-parent** feature.

### `animelibView.sq` — `animelibView` view

The anime library view. Joins `animes` × `episodestatsView` ×
`animehistorystatsView` × `animeseasonstatsView` × `animes_categories`,
filtered to `favorite = 1`. Uses `CASE M.fetch_type WHEN 1 THEN … WHEN 0 THEN …`
to compute counts from `episodestatsView` (Episodes mode) or
`animeseasonstatsView` (Seasons mode). Returns the same per-anime
`totalCount`/`seenCount`/`latestUpload`/`episodeFetchedAt`/`lastSeen`/
`bookmarkCount`/`fillermarkCount`/`category` columns that back the
`LibraryAnime` domain model. Query: `animelib: SELECT * FROM animelibView`.

### `animehistoryView.sq` — `animehistoryView` view

Parallel to manga `historyView`. Joins `animes` × `episodes` × `animehistory`
plus a `max_last_seen` subquery. Queries: `animehistory: … ORDER BY seenAt DESC`
(drives the "Recently watched" screen); `getLatestAnimeHistory: … LIMIT 1`.

### `animeupdatesView.sq` — `animeupdatesView` view

Parallel to manga `updatesView`. Joins `animes` × `episodes` for library
favorites where `date_fetch > date_added`. Includes the anime-only
`fillermark`, `last_second_seen`, `total_seconds` columns. Queries:
`getRecentAnimeUpdates`, `getUpdatesBySeenStatus`.

### `episodestatsView.sq` — `episodestatsView` view

Per-anime aggregate of `episodes`: `total`, `seenCount`, `latestUpload`
(`max(date_upload)`), `fetchedAt` (`max(date_fetch)`), `bookmarkCount`,
`fillermarkCount`. Grouped by `anime_id`. **No manga equivalent** (manga
computes these inline in `libraryView`).

### `animehistorystatsView.sq` — `animehistorystatsView` view

Per-anime `max(last_seen)` from `animehistory`. Grouped by `anime_id`. Used
by `animelibView` and `animeseasonsView` to surface the "last watched"
timestamp.

### `animeseasonstatsView.sq` — `animeseasonstatsView` view

**Anime-only.** Per-parent aggregates of season children (`parent_id IS NOT NULL`):
`child_count`, `fully_seen_seasons` (count of seasons where `total == seenCount`),
`max_latest_upload`, `max_fetched_at`, `max_last_seen`, `total_bookmarks`,
`total_fillermarks`. Grouped by `parent_id`.

### `animeseasonsView.sq` — `animeseasonsView` view

**Anime-only.** Per-season view joining parent `animes` × the three stats views
above (`episodestatsView`, `animehistorystatsView`, `animeseasonstatsView`).
Filtered to `parent_id IS NOT NULL` (only season children). Same
`CASE fetch_type` shape as `animelibView` for the count columns. Query:
`getAnimeSeasonsById: SELECT * FROM animeseasonsView WHERE parent_id = :parentId`
— backs `AnimeRepository.getAnimeSeasonsById(parentId)`.

### `animedeletableView.sq` — `animedeletableView` view

**Anime-only.** Lists parent animes (`parent_id IS NULL`) with `favorite = 0` —
candidates for cleanup. Query: `getDeletableParentAnime: SELECT * FROM animedeletableView`.
Used by `AnimeRepository.getDeletableParentAnime()`.

---

## Migrations

SQLDelight migrations are `.sqm` files named `<N>.sqm` (target version N).
At app upgrade, `AndroidSqliteDriver` runs the `.sqm` files between the user's
current DB version and the latest schema version, in order. SQLDelight
verifies (in tests) that applying `1.sqm, 2.sqm, …, N.sqm` produces the same
schema as the `.sq` files describe.

### Manga migrations (`sqldelight/migrations/`)

- **32 files:** `1.sqm` … `32.sqm`.
- Schema history starts at version 1 (the original Tachiyomi schema) and has
  been extended through version 32.
- Example `1.sqm` (the very first migration, dating from the early Tachiyomi
  era):
  ```sql
  ALTER TABLE chapters ADD COLUMN source_order INTEGER DEFAULT 0;
  UPDATE mangas SET thumbnail_url = replace(thumbnail_url, '93.174.95.110', 'kissmanga.com')
  WHERE source = 4;
  ```
  (A real piece of Tachiyomi history — migrating a defunct source's cover URLs.)
- Example `32.sqm` (the latest):
  ```sql
  ALTER TABLE manga_sync ADD COLUMN private INTEGER AS Boolean DEFAULT 0 NOT NULL;
  ```
- Most migrations are a single `ALTER TABLE ADD COLUMN` or `UPDATE`.

### Anime migrations (`sqldelightanime/migrations/`)

- **23 files:** `113.sqm` … `135.sqm`.
- The anime schema is **bootstrapped at version 113** — i.e. the anime DB
  was created when the manga DB was already at version 112, so the anime
  schema's baseline matches manga's v112 shape. From there, anime migrations
  are versioned independently (113, 114, …, 135).
- Example `113.sqm` (the bootstrap migration):
  ```sql
  UPDATE episodes SET date_upload = date_fetch WHERE date_upload = 0;
  ```
- Example `114.sqm` — a massive table-rebuild migration (drops and recreates
  `animes`, `categories`, `episodes`, `animehistory`, `animes_categories`,
  `anime_sync`, and the `animehistoryView` view to standardize SQLDelight
  column-type adapters — e.g. adds `AS Long`, `AS Boolean`, `AS Float`).
- Example `133.sqm` — adds the anime-only episode columns `summary`,
  `preview_url`, `fillermark` and anime-only `background_url`,
  `background_last_modified`, plus rebuilds the dependent views
  (`animeupdatesView`, `episodestatsView`, `animeseasonstatsView`,
  `animelibView`, `animeseasonsView`).
- Example `135.sqm` (the latest):
  ```sql
  UPDATE animes SET fetch_type = 1;
  ```
- Example `134.sqm` — adds `private INTEGER AS Boolean DEFAULT 0 NOT NULL` to
  `anime_sync` (parallel to manga migration 32).

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

The two databases have **independent version counters** — bumping the manga
schema does not require a corresponding anime migration (and vice versa).
Each `.sqm` file is small and focused (typically one `ALTER TABLE` or one
`UPDATE`), and they are applied in order.

---

## Schema-evolution triggers (summary)

Both schemas use the same trigger pattern to maintain sync metadata.

| Trigger | Tables | What it does |
|---|---|---|
| `update_last_modified_at_{table}` | `mangas`, `chapters`, `animes`, `episodes` | After any UPDATE, sets `last_modified_at = strftime('%s','now')`. |
| `update_last_favorite_at_{mangas,animes}` | `mangas`, `animes` | After UPDATE OF `favorite`, sets `favorite_modified_at`. |
| `update_{manga,anime}_version` | `mangas`, `animes` | After UPDATE (when `is_syncing = 0` and `url`/`description`/`favorite` changed), increments `version`. |
| `update_chapter_and_manga_version` / `update_episode_and_anime_version` | `chapters` (bumps `mangas`), `episodes` (bumps `animes`) | When `read`/`bookmark`/`last_page_read` (or `seen`/`bookmark`/`last_second_seen`) changes, bump both the item's and the parent's `version`. |
| `insert_{manga,anime}_category_update_version` | `mangas_categories`, `animes_categories` | After INSERT, bump parent's `version`. |
| `system_category_delete_trigger` | `categories` (both schemas) | BEFORE DELETE, abort if `_id <= 0`. |

The `is_syncing` flag is set to `1` by the sync code when it writes, so
triggers don't double-increment `version` for sync-driven writes.

---

## Column adapters

The `DatabaseAdapter.kt` file (`../ANIYOMI/data/src/main/java/tachiyomi/data/DatabaseAdapter.kt`)
declares `ColumnAdapter` implementations used by the generated `*Queries`:

| Adapter | Encodes | Used by |
|---|---|---|
| `DateColumnAdapter` | `Date ↔ Long` (epoch millis) | `history.last_read`, `animehistory.last_seen`. |
| `StringListColumnAdapter` | `List<String> ↔ String` (comma+space) | `mangas.genre`, `animes.genre`. |
| `MangaUpdateStrategyColumnAdapter` | `UpdateStrategy ↔ Long` (ordinal) | `mangas.update_strategy`. |
| `AnimeUpdateStrategyColumnAdapter` | `AnimeUpdateStrategy ↔ Long` (ordinal) | `animes.update_strategy`. |
| `FetchTypeColumnAdapter` | `FetchType ↔ Long` (ordinal) | `animes.fetch_type`. |

---

## Side-by-side manga ↔ anime table mapping

```
MANGA (sqldelight/data/)              ANIME (sqldelightanime/dataanime/)
─────────────────────────────         ─────────────────────────────────
mangas.sq                  ◀────────▶ animes.sq                  (+ 8 anime-only cols)
chapters.sq                ◀────────▶ episodes.sq                (+ 6 anime-only cols)
mangas_categories.sq       ◀────────▶ animes_categories.sq
categories.sq              ◀────────▶ categories.sq              (same shape; 2 physical tables)
history.sq                 ◀────────▶ animehistory.sq            (anime has no time_read)
manga_sync.sq              ◀────────▶ anime_sync.sq
sources.sq                 ◀────────▶ animesources.sq
extension_repos.sq         ◀────────▶ extension_repos.sq         (same shape; 2 physical tables)
excluded_scanlators.sq     ◀─── (no anime equivalent)
                                     custom_buttons.sq  ◀─── (no manga equivalent)

VIEWS
libraryView.sq             ◀────────▶ animelibView.sq            (anime branches on fetch_type)
historyView.sq             ◀────────▶ animehistoryView.sq
updatesView.sq             ◀────────▶ animeupdatesView.sq
                                     episodestatsView.sq        ◀─── anime-only
                                     animehistorystatsView.sq   ◀─── anime-only
                                     animeseasonstatsView.sq    ◀─── anime-only
                                     animeseasonsView.sq        ◀─── anime-only
                                     animedeletableView.sq      ◀─── anime-only
```

## See also

- [`domain-models.md`](domain-models.md) — the domain models that these tables
  back (with the mappers in `:data` translating between the two layers).
- [`../02-modules/data.md`](../02-modules/data.md) — narrative description of
  `:data`, including the `*DatabaseHandler` abstraction, the mappers, and the
  repository implementations that consume these `*Queries`.
- [`../02-modules/domain.md`](../02-modules/domain.md) — the domain models and
  repository interfaces.
- [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md)
  — how `libraryView` / `animelibView` feed the library screen.
- [`../03-subsystems/history.md`](../03-subsystems/history.md) — how the
  `history` / `animehistory` tables and `*historyView` views drive the
  "Recently read/watched" screens.
- [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) — how
  `manga_sync` / `anime_sync` are pushed to and pulled from trackers.
- [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md)
  — how the `version` / `last_modified_at` / `is_syncing` columns (and their
  triggers) support backup/restore and sync.
- [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) — how
  `custom_buttons` Lua scripts are loaded into MPV.
- [`../03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md)
  — how the `extension_repos` tables (both of them) are used.
- [`../01-architecture/01-architecture-overview.md`](../01-architecture/01-architecture-overview.md)
  — where `:data` sits in the layered architecture.
- [`../00-overview/05-project-conventions.md`](../00-overview/05-project-conventions.md)
  — the dual manga/anime pattern at the project level.
