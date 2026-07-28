# EVID-06 — Data Layer Evidence

**Task ID:** EVID-06-DATA-LAYER
**Agent:** Explore (research-only)
**Scope:** Deep analysis of the ANIKUTA data layer — every `.sq` file, every table, every column, every repository, every preference key, the backup data model, the episode-metadata cache, watch progress, downloads.
**Project root:** `/home/z/my-project/anikuta/ANIKUTA_PROJECT/ANIKUTA/`
**No code was modified.** Every claim below is cited `file:line`.

---

## 0. Headline summary

- **6 `.sq` files** found (no `sqldelightanime/` source set — the dual manga/anime schema from ADR-009 is **documented but NOT implemented**). Files: `animes.sq`, `episodes.sq`, `animehistory.sq`, `animetrack.sq`, `anime_category.sq`, `categories.sq` — all under `core/database/src/main/sqldelight/app/confused/anikuta/core/database/`.
- **1 migration file** `1.sqm` — migrates v1→v2 (adds the AniList linkage columns + `hidden` category column + Default-category seed).
- **Schema version:** 2 (implicit — SQLDelight derives it from the highest `.sqm` number plus 1; `1.sqm` brings it to v2). No explicit version constant in `build.gradle.kts`.
- **ADR-024 status columns** (`release_date`, `last_refresh`, `last_metadata_fetch`, `next_episode_check`): **present on `animes` table** ✅ but **MISSING on `episodes` table** ❌ — ARCHITECTURE.md §5 (lines 151-163) says "must include … on anime/episode tables". The `episodes` schema omits ALL four.
- **5 repository interfaces** in `:core:common` (`AnimeRepository`, `EpisodeRepository`, `HistoryRepository`, `CategoryRepository`, `TrackRepository`) + 4 impls (`AnimeRepositoryImpl`, `EpisodeRepositoryImpl`, `CategoryRepositoryImpl` in `:data:anime`; `HistoryRepositoryImpl` in `:data:history`). The `TrackRepository` interface in `:core:common/repository/` is NOT the one used — `:core:tracker` has its own `TrackRepository` interface (`core/tracker/TrackRepository.kt`) with different methods.
- **8 domain models** in `:core:common/model/` (`Anime`, `Episode`, `History`, `Track`, `Category`, `AnimeCategoryLink`, `Source`, plus enums + `UnifiedAnime` in `details/`).
- **AniList-first keying pattern:** The DB uses surrogate `_id` (AUTOINCREMENT) as PK everywhere; `anilist_id` is a partial-unique nullable column on `animes` only. Watch progress, playback state, downloads, episode-metadata cache, source links, and details-view prefs are all stored in SharedPreferences as JSON maps keyed by **`"$anilistId:$episodeUrl"`** (or `anilistId` alone), NOT in SQLDelight.
- **Backup format (ADR-036):** `.anikuta` = ZIP containing `meta.json.gz` (gzipped JSON of `BackupContainer`) + optional `covers/<anilistId>.jpg`. Schema version 1. 10 backup providers.
- **Aniyomi restore:** Reads `.tachibk` (gzip+protobuf), restore-only, via `AniyomiBackupTranslator` which resolves AniList IDs (tracker → MAL lookup → title search) and remaps keys.

---

## 1. SQLDelight `.sq` files — complete schema dump

### 1.1 `animes.sq`

**Path:** `core/database/src/main/sqldelight/app/confused/anikuta/core/database/animes.sq`

**CREATE TABLE statement** (lines 3-35):

```sql
CREATE TABLE animes (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    url TEXT NOT NULL,
    title TEXT NOT NULL,
    artist TEXT,
    author TEXT,
    description TEXT,
    genre TEXT,           -- comma-separated
    cover_url TEXT,
    status INTEGER NOT NULL,    -- 0=unknown, 1=ongoing, 2=completed, ...
    thumbnail_url TEXT,
    favorite INTEGER NOT NULL DEFAULT 0,
    source_id INTEGER NOT NULL,
    date_added INTEGER NOT NULL,
    viewer_flags INTEGER NOT NULL DEFAULT 0,
    next_update INTEGER NOT NULL DEFAULT 0,
    update_strategy INTEGER NOT NULL DEFAULT 0,
    cover_last_modified INTEGER NOT NULL DEFAULT 0,

    -- Status-tracking columns (ADR-024)
    release_date INTEGER,            -- when the anime was first released
    last_refresh INTEGER NOT NULL DEFAULT 0,    -- last time refreshed from source
    last_metadata_fetch INTEGER,     -- last time metadata was fetched (AniList/ext)
    next_episode_check INTEGER,      -- when to next check for new episodes (ADR-014)

    -- Library columns (Phase A — library page)
    anilist_id INTEGER,              -- AniList media ID (nullable for non-AniList entries)
    cover_color TEXT,                -- hex color extracted from cover (e.g. "#B1F256")
    score REAL,                      -- AniList average score (0-100)
    total_episodes INTEGER,          -- AniList total episodes count
    last_watched INTEGER NOT NULL DEFAULT 0,  -- epoch ms of last watch activity
    next_airing_episode INTEGER               -- AniList next airing episode number (for released-episode count)
);
```

**Index** (lines 38-39):
```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_animes_anilist_id
ON animes(anilist_id) WHERE anilist_id IS NOT NULL;
```
→ `anilist_id` is **nullable, partial-unique indexed** (unique only when non-null).

**Named queries** (16):

| Name | Line | SQL |
|---|---|---|
| `selectAll` | 41 | `SELECT * FROM animes;` |
| `selectById` | 44 | `SELECT * FROM animes WHERE _id = :id;` |
| `selectByAnilistId` | 47 | `SELECT * FROM animes WHERE anilist_id = :anilistId;` |
| `selectIdByAnilistId` | 50 | `SELECT _id FROM animes WHERE anilist_id = :anilistId;` |
| `selectFavorites` | 53 | `SELECT * FROM animes WHERE favorite = 1 ORDER BY title COLLATE NOCASE;` |
| `selectBySource` | 56 | `SELECT * FROM animes WHERE source_id = :sourceId AND favorite = 1;` |
| `selectBySourceAndUrl` | 59 | `SELECT * FROM animes WHERE source_id = :sourceId AND url = :url LIMIT 1;` |
| `searchByName` | 62 | `SELECT * FROM animes WHERE title LIKE '%' \|\| :query \|\| '%' COLLATE NOCASE;` |
| `insert` | 65 | full INSERT — 26 columns (lines 66-78) |
| `update` | 80 | full UPDATE by `_id = :id` (lines 81-93) |
| `updateFavorite` | 95 | `UPDATE animes SET favorite = :favorite, date_added = :dateAdded WHERE _id = :id;` |
| `updateFavoriteByAnilistId` | 98 | same, keyed by `anilist_id = :anilistId` |
| `updateLastRefresh` | 101 | `UPDATE animes SET last_refresh = :lastRefresh WHERE _id = :id;` |
| `updateLastMetadataFetch` | 104 | keyed by `_id` |
| `updateNextEpisodeCheck` | 107 | keyed by `_id` |
| `updateLastWatched` | 110 | keyed by `_id` |
| `updateLastWatchedByAnilistId` | 113 | keyed by `anilist_id` |
| `updateAnilistMetadataByAnilistId` | 116 | updates `title, cover_url, cover_color, score, total_episodes, next_airing_episode` keyed by `anilist_id` (lines 117-124) |
| `updatePreferredCoverByAnilistId` | 129 | updates only `cover_url, cover_color` keyed by `anilist_id` (lines 130-133) |
| `updatePreferredCoverBySourceAndUrl` | 136 | updates only `cover_url, cover_color` keyed by `source_id + url` (lines 137-140) |
| `lastInsertedRowId` | 142 | `SELECT last_insert_rowid();` |
| `delete` | 145 | `DELETE FROM animes WHERE _id = :id;` |

**Primary key:** `_id` (INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT) — line 4.
**`anilist_id` column:** present (line 29), nullable, NOT primary key, **partial-unique indexed** (lines 38-39).
**`source_id` column:** present, NOT NULL (line 15).
**`source_url`:** NOT present (the source-relative URL lives in `url`, line 5).
**`episode_number` / `episode_url`:** not applicable (these are on the `episodes` table).

---

### 1.2 `episodes.sq`

**Path:** `core/database/src/main/sqldelight/app/confused/anikuta/core/database/episodes.sq`

**CREATE TABLE** (lines 3-24):

```sql
CREATE TABLE episodes (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    anime_id INTEGER NOT NULL,
    url TEXT,                              -- nullable!
    name TEXT NOT NULL,
    episode_number REAL NOT NULL,
    scanlator TEXT,
    seen INTEGER NOT NULL DEFAULT 0,
    bookmark INTEGER NOT NULL DEFAULT 0,
    last_second_seen INTEGER NOT NULL DEFAULT 0,
    total_seconds INTEGER NOT NULL DEFAULT 0,
    source_order INTEGER NOT NULL,
    date_fetch INTEGER NOT NULL,
    date_upload INTEGER,

    -- Anime-specific fields
    fillermark TEXT,
    summary TEXT,
    preview_url TEXT,

    FOREIGN KEY (anime_id) REFERENCES animes(_id) ON DELETE CASCADE
);
```

**Named queries** (8):

| Name | Line | SQL |
|---|---|---|
| `selectByAnimeId` | 26 | `SELECT * FROM episodes WHERE anime_id = :animeId ORDER BY source_order;` |
| `selectById` | 29 | `SELECT * FROM episodes WHERE _id = :id;` |
| `selectByAnimeIdAndNumber` | 32 | `SELECT * FROM episodes WHERE anime_id = :animeId AND episode_number = :number;` |
| `insert` | 35 | full INSERT — 15 columns (lines 36-44) |
| `update` | 46 | UPDATE by `_id` (lines 47-53) — does NOT update `source_order` |
| `updateSeen` | 55 | `UPDATE episodes SET seen = :seen, last_second_seen = :lastSecondSeen, total_seconds = :totalSeconds WHERE _id = :id;` |
| `updateBookmark` | 59 | keyed by `_id` |
| `delete` | 62 | keyed by `_id` |
| `deleteByAnimeId` | 65 | keyed by `anime_id` (cascade also handles this) |

**Primary key:** `_id` (line 4).
**`anilist_id` column:** **NOT present**. Episodes are linked to anime via `anime_id` → `animes._id` (FK, line 23) only. To get an episode's anilist ID you must join through `animes`.
**`source_id` column:** NOT present.
**`source_url` column:** NOT present.
**`episode_number`:** present, `REAL NOT NULL` (line 8) — float (e.g. `12.5` for specials).
**`episode_url`:** present as `url`, **nullable** (line 6) — this is the source-relative URL of the episode.
**ADR-024 status columns on episodes:** **ALL FOUR MISSING** — no `release_date`, `last_refresh`, `last_metadata_fetch`, `next_episode_check`. ❌ (See §6 below.)

---

### 1.3 `animehistory.sq`

**Path:** `core/database/src/main/sqldelight/app/confused/anikuta/core/database/animehistory.sq`

**CREATE TABLE** (lines 3-12):

```sql
CREATE TABLE animehistory (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    anime_id INTEGER NOT NULL,
    episode_id INTEGER NOT NULL,
    seen_at INTEGER NOT NULL,
    last_second_seen INTEGER NOT NULL DEFAULT 0,
    UNIQUE(anime_id, episode_id),
    FOREIGN KEY (anime_id) REFERENCES animes(_id) ON DELETE CASCADE,
    FOREIGN KEY (episode_id) REFERENCES episodes(_id) ON DELETE CASCADE
);
```

**Named queries** (5):

| Name | Line | SQL |
|---|---|---|
| `selectAll` | 14 | `SELECT * FROM animehistory ORDER BY seen_at DESC;` |
| `selectByAnimeId` | 17 | keyed by `anime_id`, ordered by `seen_at DESC` |
| `upsert` | 22 | `INSERT OR REPLACE INTO animehistory (anime_id, episode_id, seen_at, last_second_seen) VALUES (:animeId, :episodeId, :seenAt, :lastSecondSeen);` (the UNIQUE constraint triggers replace) |
| `delete` | 27 | keyed by `_id` |
| `deleteByAnimeId` | 30 | keyed by `anime_id` |

**Primary key:** `_id` (line 4).
**Uniqueness:** `UNIQUE(anime_id, episode_id)` (line 9) — composite natural key.
**`anilist_id`:** NOT present (linked transitively via `anime_id → animes.anilist_id`).

---

### 1.4 `animetrack.sq`

**Path:** `core/database/src/main/sqldelight/app/confused/anikuta/core/database/animetrack.sq`

**CREATE TABLE** (lines 3-16):

```sql
CREATE TABLE animetrack (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    anime_id INTEGER NOT NULL,
    tracker_id INTEGER NOT NULL,           -- 1 = MAL, 2 = AniList (per Aniyomi syncId convention)
    remote_id INTEGER NOT NULL,            -- the tracker-side media ID
    remote_url TEXT,
    last_seen INTEGER NOT NULL DEFAULT 0,
    score REAL NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 0,
    total_episodes INTEGER NOT NULL DEFAULT 0,
    display_score TEXT,
    UNIQUE(anime_id, tracker_id),
    FOREIGN KEY (anime_id) REFERENCES animes(_id) ON DELETE CASCADE
);
```

**Named queries** (7):

| Name | Line | SQL |
|---|---|---|
| `selectByAnimeId` | 18 | keyed by `anime_id` |
| `selectByAnimeIdAndTrackerId` | 21 | keyed by `anime_id + tracker_id` |
| `insert` | 24 | full INSERT, 9 columns (lines 25-31) |
| `update` | 33 | UPDATE by `_id` (lines 34-38) |
| `upsert` | 42 | `INSERT OR REPLACE` (lines 43-49) — relies on UNIQUE(anime_id, tracker_id) |
| `delete` | 51 | keyed by `_id` |
| `selectAllTracks` | 56 | `SELECT * FROM animetrack;` (added by Agent 2 — Profile/Trackers) |
| `deleteByAnimeIdAndTrackerId` | 59 | keyed by `anime_id + tracker_id` |
| `updateLastSeen` | 62 | keyed by `_id` |

**Primary key:** `_id` (line 4).
**Uniqueness:** `UNIQUE(anime_id, tracker_id)` (line 14).
**`anilist_id`:** NOT present directly. The `remote_id` column holds the AniList media ID when `tracker_id = 2`.

---

### 1.5 `categories.sq`

**Path:** `core/database/src/main/sqldelight/app/confused/anikuta/core/database/categories.sq`

**CREATE TABLE** (lines 3-9):

```sql
CREATE TABLE categories (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    category_order INTEGER NOT NULL DEFAULT 0,
    flags INTEGER NOT NULL DEFAULT 0,
    hidden INTEGER NOT NULL DEFAULT 0
);
```

**Seed** (lines 13-14): `INSERT OR IGNORE INTO categories (_id, name, category_order, flags, hidden) VALUES (1, 'Default', 0, 0, 0);` — the Default category is seeded with fixed id=1 on fresh installs. (Note: line 18 says a DB trigger was removed because SQLDelight's parser doesn't recognize OLD/NEW — Default-deletion protection is enforced at the app layer in `CategoryRepositoryImpl.delete`, which throws for id=1.)

**Named queries** (10):

| Name | Line | SQL |
|---|---|---|
| `selectAll` | 20 | ordered by `category_order` |
| `selectVisible` | 23 | `WHERE hidden = 0` |
| `selectById` | 26 | keyed by `_id` |
| `selectByName` | 29 | `WHERE name = :name COLLATE NOCASE LIMIT 1` |
| `selectDefault` | 32 | `WHERE _id = 1` |
| `insert` | 35 | 4 columns |
| `insertDefault` | 39 | `INSERT OR IGNORE` with `_id=1` |
| `update` | 43 | full update keyed by `_id` |
| `updateName` | 47 | keyed by `_id` |
| `updateOrder` | 50 | keyed by `_id` |
| `updateHidden` | 53 | keyed by `_id` |
| `lastInsertedRowId` | 56 | `SELECT last_insert_rowid();` |
| `delete` | 59 | keyed by `_id` |

**Primary key:** `_id` (line 4). The Default category has the FIXED id=1 (not autoincrement — explicitly seeded).
**`anilist_id`:** NOT present.

---

### 1.6 `anime_category.sq`

**Path:** `core/database/src/main/sqldelight/app/confused/anikuta/core/database/anime_category.sq`

**CREATE TABLE** (lines 3-10):

```sql
CREATE TABLE anime_category (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    anime_id INTEGER NOT NULL,
    category_id INTEGER NOT NULL,
    category_order INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (anime_id) REFERENCES animes(_id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(_id) ON DELETE CASCADE
);
```

**Named queries** (8):

| Name | Line | SQL |
|---|---|---|
| `selectByAnimeId` | 12 | keyed by `anime_id` |
| `selectAll` | 15 | all links |
| `selectByCategoryId` | 18 | keyed by `category_id`, ordered by `category_order` |
| `selectCategoriesByAnimeId` | 21 | JOIN with `categories` — returns the full Category rows for an anime |
| `selectAnimeIdsByCategoryId` | 27 | `SELECT anime_id FROM anime_category WHERE category_id = :categoryId ORDER BY category_order;` |
| `countAnimeInCategory` | 30 | `SELECT COUNT(*) …` |
| `insert` | 33 | `INSERT OR IGNORE INTO anime_category (anime_id, category_id, category_order) VALUES (:animeId, :categoryId, :order);` |
| `deleteByAnimeId` | 37 | keyed by `anime_id` |
| `deleteByAnimeIdAndCategoryId` | 40 | keyed by composite |
| `deleteByCategoryId` | 43 | keyed by `category_id` |

**Primary key:** `_id` (line 4).
**Uniqueness:** None declared (insert uses `INSERT OR IGNORE` which is a no-op safety, not a constraint — duplicate `(anime_id, category_id)` pairs are technically allowed by the schema but the app always full-replaces via `setAnimeCategories` which deletes-then-inserts).

---

## 2. The dual-schema pattern (ADR-009) — NOT implemented

ARCHITECTURE.md §5 (lines 164-165) says:

> "The dual manga/anime schema pattern (per ADR-009) follows the reference: separate `sqldelight/` and `sqldelightanime/` source sets, independent version counters."

**Reality on disk:** Only ONE source set exists — `core/database/src/main/sqldelight/`. There is **no `sqldelightanime/` directory** anywhere in the project (verified by Glob for `**/*.sq`).

`core/database/src/main/java/app/confused/anikuta/core/database/DatabaseDriverFactory.kt:10-11,21` confirms:
```kotlin
* The database file is `anikuta.db` (anime-side). A separate manga database
* will be added when manga is implemented (ADR-009).
…
name = "anikuta.db",
```

`core/database/build.gradle.kts:10-17` declares only one database:
```kotlin
sqldelight {
    databases {
        create("AnikutaDatabase") {
            packageName.set("app.confused.anikuta.core.database")
        }
    }
}
```

The `:data:manga` module is an empty stub (0 `.kt` files, per prior AUDIT-FEATURES work). The dual-schema pattern is documented intent, not implemented.

**Current schema version:** 2 (the `1.sqm` migration file brings v1 → v2; SQLDelight numbers schemas by `1 + max(.sqm filename)`).

---

## 3. Migration file `1.sqm`

**Path:** `core/database/src/main/sqldelight/app/confused/anikuta/core/database/1.sqm`

**Full content** (31 lines):

```sql
-- 1.sqm — migrate from v1 to v2: library page support
ALTER TABLE animes ADD COLUMN anilist_id INTEGER;
ALTER TABLE animes ADD COLUMN cover_color TEXT;
ALTER TABLE animes ADD COLUMN score REAL;
ALTER TABLE animes ADD COLUMN total_episodes INTEGER;
ALTER TABLE animes ADD COLUMN last_watched INTEGER NOT NULL DEFAULT 0;
ALTER TABLE animes ADD COLUMN next_airing_episode INTEGER;

CREATE UNIQUE INDEX IF NOT EXISTS idx_animes_anilist_id
ON animes(anilist_id) WHERE anilist_id IS NOT NULL;

ALTER TABLE categories ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0;

INSERT OR IGNORE INTO categories (_id, name, category_order, flags, hidden)
VALUES (1, 'Default', 0, 0, 0);
```

**Notable:** the v1 → v2 migration adds the entire AniList-linkage column set (`anilist_id, cover_color, score, total_episodes, last_watched, next_airing_episode`) plus the `hidden` category column. The `release_date, last_refresh, last_metadata_fetch, next_episode_check` columns were already in v1 (they appear in `animes.sq` CREATE TABLE but NOT in `1.sqm` — meaning they're part of the original v1 schema).

There are no `.sqm` files numbered 2 or higher — current schema is v2.

---

## 4. Domain models (`:core:common/model/`)

### 4.1 `Anime.kt`

**Path:** `core/common/src/main/java/app/confused/anikuta/core/common/model/Anime.kt`

**Full data class** (lines 20-50):

| Field | Type | Nullable | Default | Source |
|---|---|---|---|---|
| `id` | `Long` | no | — | DB PK (`animes._id`) |
| `url` | `String` | no | — | source-specific (AniList URL or `SAnime.url`) |
| `title` | `String` | no | — | either |
| `artist` | `String?` | yes | — | extension-only |
| `author` | `String?` | yes | — | extension-only |
| `description` | `String?` | yes | — | either |
| `genre` | `List<String>` | no (empty list ok) | — | either (stored as comma-separated TEXT in DB) |
| `coverUrl` | `String?` | yes | — | either |
| `status` | `Int` | no | — | either (0=unknown… see `AnimeStatus` object) |
| `thumbnailUrl` | `String?` | yes | — | extension-only |
| `favorite` | `Boolean` | no | — | generic (library flag) |
| `sourceId` | `Long` | no | — | source-specific (extension source ID; 0 for pure AniList) |
| `dateAdded` | `Long` | no | — | generic |
| `viewerFlags` | `Int` | no | — | generic |
| `nextUpdate` | `Long` | no | — | generic |
| `updateStrategy` | `Int` | no | — | generic |
| `coverLastModified` | `Long` | no | — | generic |
| `releaseDate` | `Long?` | yes | — | **ADR-024** |
| `lastRefresh` | `Long` | no | — | **ADR-024** |
| `lastMetadataFetch` | `Long?` | yes | — | **ADR-024** |
| `nextEpisodeCheck` | `Long?` | yes | — | **ADR-024** |
| `anilistId` | `Int?` | yes | — | **AniList-specific** |
| `coverColor` | `String?` | yes | — | either (AniList hex or Palette-extracted) |
| `score` | `Double?` | yes | — | AniList-specific |
| `totalEpisodes` | `Int?` | yes | — | AniList-specific |
| `lastWatched` | `Long` | no | — | generic (Phase A library) |
| `nextAiringEpisode` | `Int?` | yes | — | AniList-specific |

**Computed property** (lines 58-63): `releasedEpisodes: Int?` — returns `nextAiringEpisode - 1` if airing, else `totalEpisodes`.

**`AnimeStatus` constants** (lines 67-75): `UNKNOWN=0, ONGOING=1, COMPLETED=2, LICENSED=3, PUBLISHING_FINISHED=4, CANCELLED=5, ON_HIATUS=6`.

### 4.2 `Episode.kt`

**Path:** `core/common/src/main/java/app/confused/anikuta/core/common/model/Episode.kt`

**Full data class** (lines 9-27):

| Field | Type | Nullable |
|---|---|---|
| `id` | `Long` | no |
| `animeId` | `Long` | no |
| `url` | `String?` | **yes** (source episode URL — used as the offline key) |
| `name` | `String` | no |
| `episodeNumber` | `Float` | no |
| `scanlator` | `String?` | yes |
| `seen` | `Boolean` | no |
| `bookmark` | `Boolean` | no |
| `lastSecondSeen` | `Long` | no |
| `totalSeconds` | `Long` | no |
| `sourceOrder` | `Long` | no |
| `dateFetch` | `Long` | no |
| `dateUpload` | `Long?` | yes |
| `fillermark` | `String?` | yes (anime-specific) |
| `summary` | `String?` | yes (anime-specific) |
| `previewUrl` | `String?` | yes (anime-specific) |

**No ADR-024 status-tracking fields** on the `Episode` domain model (mirrors the missing DB columns).

### 4.3 `History.kt`

**Path:** `core/common/src/main/java/app/confused/anikuta/core/common/model/History.kt`

```kotlin
data class History(
    val id: Long,
    val animeId: Long,
    val episodeId: Long,
    val seenAt: Long,
    val lastSecondSeen: Long,
)
```

### 4.4 `Track.kt`

**Path:** `core/common/src/main/java/app/confused/anikuta/core/common/model/Track.kt`

```kotlin
data class Track(
    val id: Long,
    val animeId: Long,
    val trackerId: Long,
    val remoteId: Long,
    val remoteUrl: String?,
    val lastSeen: Long,
    val score: Float,
    val status: Int,
    val totalEpisodes: Int,
    val displayScore: String?,
)
```

### 4.5 `Category.kt`

**Path:** `core/common/src/main/java/app/confused/anikuta/core/common/model/Category.kt`

```kotlin
data class Category(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Int,
    val hidden: Boolean,
) {
    val isDefault: Boolean get() = id == DEFAULT_ID
    companion object { const val DEFAULT_ID = 1L }
}
```

### 4.6 `AnimeCategoryLink.kt`

```kotlin
data class AnimeCategoryLink(
    val animeId: Long,
    val categoryId: Long,
)
```

### 4.7 `Source.kt`

```kotlin
data class Source(
    val id: Long,
    val name: String,
    val lang: String,
)
```

### 4.8 `UnifiedAnime.kt` (the unified details-page value)

**Path:** `core/common/src/main/java/app/confused/anikuta/core/common/model/details/UnifiedAnime.kt`

```kotlin
data class UnifiedAnime(
    val dataSource: DataSource,        // ANILIST or EXTENSION
    val anilistId: Int?,               // AniList-specific (null for unlinked extension anime)
    val malId: Int?,                   // AniList-specific (enables Jikan)
    val sourceId: Long?,               // source-specific (null in pure-AniList mode)
    val sourceName: String,
    val url: String,                   // either `https://anilist.co/anime/{id}` or SAnime.url
    val title: String,
    val coverUrl: String?,
    val coverColorHex: String?,
    val bannerUrl: String?,
    val description: String?,
    val genres: List<String>,
    val status: UnifiedStatus,
    val format: String?,               // AniList-only
    val episodeCount: Int?,
    val averageScore: Int?,            // AniList-only
    val season: String?,               // AniList-only
    val seasonYear: Int?,              // AniList-only
    val startDate: String?,            // AniList-only
    val studios: List<String>,         // AniList-only
    val nextAiringEpisode: NextAiringEpisode?,  // AniList-only
    val author: String?,               // extension-only
    val artist: String?,               // extension-only
    val source: String?,               // AniList-only (ORIGINAL/MANGA/…)
)
```

Plus `DetailsResult(anime: UnifiedAnime, episodes: List<Episode>)` (lines 101-104).

### 4.9 Enums / supporting models

- `DataSource { ANILIST, EXTENSION }` — `core/common/.../details/DataSource.kt:16-22`
- `UnifiedStatus { FINISHED, RELEASING, NOT_YET_RELEASED, CANCELLED, HIATUS, UNKNOWN }` — `DataSource.kt:33-40`
- `NextAiringEpisode(episode: Int, airingAt: Long, timeUntilAiring: Long)` — `DataSource.kt:49-53`
- `DetailsRequest` sealed interface — `core/common/.../details/DetailsRequest.kt:14-41` — two cases: `ByAniListId(anilistId: Int)` and `ByExtension(sourceId: Long, animeUrl: String, animeTitle: String, anilistId: Int? = null)`.
- `AnimeDetailsProvider` interface — `core/common/.../details/AnimeDetailsProvider.kt:37-68` — `suspend fun load(request: DetailsRequest, forceRefresh: Boolean = false): DetailsResult?` + `suspend fun loadEpisodes(request: DetailsRequest): List<Episode>?`.
- `AnimeDetailsProviderRegistry` — `core/common/.../details/AnimeDetailsProviderRegistry.kt:18-31` — Koin multi-binding `List<AnimeDetailsProvider>`, resolved via `forSource(dataSource)`.
- `LibrarySortType { TITLE, DATE_ADDED, LAST_WATCHED, PROGRESS, TOTAL_EPISODES }` + `LibrarySort(type, ascending)` — `LibrarySort.kt`
- `LibraryDisplayMode { COMPACT_GRID, COMFORTABLE_GRID, COVER_ONLY, LIST }` — `LibraryDisplayMode.kt`
- `EpisodeBadgeMode { TOTAL, RELEASED, OFF }` — `EpisodeBadgeMode.kt`
- `BadgePosition { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }` + `oppositeOnSameEdge()` extension — `BadgePosition.kt`

---

## 5. Repository interface + impl map

### 5.1 `AnimeRepository` (interface)

**Path:** `core/common/src/main/java/app/confused/anikuta/core/common/repository/AnimeRepository.kt`

| Method | Line | Takes `anilistId`? | Returns anilistId? |
|---|---|---|---|
| `observeAll(): Flow<List<Anime>>` | 19 | no | yes (in `Anime.anilistId`) |
| `observeFavorites(): Flow<List<Anime>>` | 21 | no | yes |
| `observeById(id: Long): Flow<Anime?>` | 23 | no (takes `_id`) | yes |
| `observeBySource(sourceId: Long): Flow<List<Anime>>` | 25 | no | yes |
| `observeByAnilistId(anilistId: Int): Flow<Anime?>` | 27 | **yes** | yes |
| `getById(id: Long): Anime?` | 29 | no | yes |
| `getByAnilistId(anilistId: Int): Anime?` | 31 | **yes** | yes |
| `getBySourceAndUrl(sourceId: Long, url: String): Anime?` | 33 | no | yes |
| `searchByName(query: String): List<Anime>` | 35 | no | yes |
| `upsert(anime: Anime): Long` | 37 | no (but `Anime` carries `anilistId`) | returns `_id` |
| `updateFavorite(id: Long, favorite: Boolean, dateAdded: Long)` | 39 | no | — |
| `updateFavoriteByAnilistId(anilistId: Int, favorite: Boolean, dateAdded: Long)` | 41 | **yes** | — |
| `updateLastRefresh(id: Long, lastRefresh: Long)` | 43 | no | — |
| `updateLastMetadataFetch(id: Long, lastMetadataFetch: Long)` | 45 | no | — |
| `updateNextEpisodeCheck(id: Long, nextEpisodeCheck: Long?)` | 47 | no | — |
| `updateLastWatched(id: Long, lastWatched: Long)` | 49 | no | — |
| `updateLastWatchedByAnilistId(anilistId: Int, lastWatched: Long)` | 51 | **yes** | — |
| `updateAnilistMetadata(anilistId: Int, title, coverUrl, coverColor, score, totalEpisodes, nextAiringEpisode)` | 53 | **yes** | — |
| `updatePreferredCoverByAnilistId(anilistId: Int, coverUrl, coverColor)` | 68 | **yes** | — |
| `updatePreferredCoverBySourceAndUrl(sourceId, url, coverUrl, coverColor)` | 74 | no | — |
| `delete(id: Long)` | 76 | no | — |

**Impl:** `data/anime/src/main/java/app/confused/anikuta/data/anime/AnimeRepositoryImpl.kt` (lines 24-226). All methods take `AnikutaDatabase` + `DispatcherProvider`; mapper is `AnimeMapper`. Note line 50/59: `anilistId: Int` is converted via `.toLong()` because SQLDelight generated the column as `Long?`.

### 5.2 `EpisodeRepository` (interface)

**Path:** `core/common/src/main/java/app/confused/anikuta/core/common/repository/EpisodeRepository.kt`

| Method | Line | Takes anilistId? |
|---|---|---|
| `observeByAnimeId(animeId: Long): Flow<List<Episode>>` | 9 | no (takes `animes._id`) |
| `observeById(id: Long): Flow<Episode?>` | 11 | no |
| `getByAnimeId(animeId: Long): List<Episode>` | 13 | no |
| `getById(id: Long): Episode?` | 15 | no |
| `upsert(episode: Episode): Long` | 17 | no (but `Episode.animeId` is the parent FK) |
| `updateSeen(id: Long, seen: Boolean, lastSecondSeen: Long, totalSeconds: Long)` | 19 | no |
| `updateBookmark(id: Long, bookmark: Boolean)` | 21 | no |
| `delete(id: Long)` | 23 | no |
| `deleteByAnimeId(animeId: Long)` | 25 | no |

**Impl:** `data/anime/.../EpisodeRepositoryImpl.kt` (lines 12-96). Mapper: `EpisodeMapper`. **None of these methods take `anilistId`** — episodes are keyed by the local DB `_id`/`anime_id` only.

### 5.3 `HistoryRepository` (interface)

**Path:** `core/common/src/main/java/app/confused/anikuta/core/common/repository/HistoryRepository.kt`

| Method | Line |
|---|---|
| `observeAll(): Flow<List<History>>` | 9 |
| `observeByAnimeId(animeId: Long): Flow<List<History>>` | 11 |
| `upsert(animeId: Long, episodeId: Long, seenAt: Long, lastSecondSeen: Long)` | 13 |
| `delete(id: Long)` | 15 |
| `deleteByAnimeId(animeId: Long)` | 17 |

**Impl:** `data/history/src/main/java/app/confused/anikuta/data/history/HistoryRepositoryImpl.kt` (lines 11-37). Mapper: `HistoryMapper`. **No anilistId.** This repo is largely unused — the actual watch-progress store is `WatchProgressStore` (SharedPreferences), NOT this SQLDelight table. The `animehistory` table exists in the schema and is wired up, but the live history feature reads from `WatchProgressStore.changes` (per `core/player/WatchProgressStore.kt:37-38` comment).

### 5.4 `CategoryRepository` (interface)

**Path:** `core/common/src/main/java/app/confused/anikuta/core/common/repository/CategoryRepository.kt`

| Method | Line |
|---|---|
| `observeAll(): Flow<List<Category>>` | 25 |
| `observeVisible(): Flow<List<Category>>` | 28 |
| `getAll(): List<Category>` | 30 |
| `getById(id: Long): Category?` | 32 |
| `getByName(name: String): Category?` | 34 |
| `create(name: String): Long` | 43 |
| `rename(id: Long, name: String)` | 46 |
| `delete(id: Long)` | 55 (throws for id=1) |
| `reorder(id: Long, newIndex: Int)` | 62 |
| `setHidden(id: Long, hidden: Boolean)` | 65 |
| `ensureDefaultExists()` | 68 |
| `observeAllLinks(): Flow<List<AnimeCategoryLink>>` | 73 |
| `observeCategoriesForAnime(animeId: Long): Flow<List<Category>>` | 76 |
| `getAnimeCategories(animeId: Long): List<Category>` | 79 |
| `setAnimeCategories(animeId: Long, categoryIds: List<Long>)` | 86 |
| `countAnimeInCategory(categoryId: Long): Int` | 89 |

**Impl:** `data/anime/.../CategoryRepositoryImpl.kt` (lines 29-197). Uses a `Mutex` for reorder serialization (line 34). **No anilistId.** Categories are keyed by `_id`; anime-category links by `anime_id` (= `animes._id`).

### 5.5 `TrackRepository` (interface — TWO definitions exist!)

There are **two** `TrackRepository` interfaces in the codebase:

1. **`:core:common` version** — `core/common/src/main/java/app/confused/anikuta/core/common/repository/TrackRepository.kt` (lines 7-16):
   ```kotlin
   interface TrackRepository {
       fun observeByAnimeId(animeId: Long): Flow<List<Track>>
       suspend fun getByAnimeId(animeId: Long): List<Track>
       suspend fun upsert(track: Track): Long
       suspend fun delete(id: Long)
   }
   ```
   This is the **unused / orphan interface** — no impl in `:data:tracker` (which is an empty stub).

2. **`:core:tracker` version** — `core/tracker/src/main/java/app/confused/anikuta/core/tracker/TrackRepository.kt` — the **actually-used** one. (Read by `TrackerBackupProviderImpl` and the `:core:tracker` impls.) Its methods include `getAllTracks()`, `bind(...)`, etc. — see `TrackerBackupProviderImpl.kt:41,86-95` for the call sites.

This is a **latent naming collision** — the `:core:common` `TrackRepository` is dead code.

### 5.6 `:data:extension` cache stores (NOT repositories, but they're the source-link persistence layer)

The `:data:extension` module has no `*Repository` classes; instead it has three SharedPreferences-backed JSON stores that play the role of "AniList ↔ extension link persistence":

- **`SourceLinkStore`** — `data/extension/.../cache/SourceLinkStore.kt` — key `pref_source_links` (line 67). JSON map `Map<String, SourceLink>` keyed by **`anilistId.toString()`** → `SourceLink(sourceId, animeUrl, animeTitle)`. Methods: `getLink(anilistId)`, `saveLink(anilistId, sourceId, animeUrl, animeTitle)`, `removeLink(anilistId)`, `getAll()`. (lines 22-69)
- **`ExtensionLinkStore`** — `data/extension/.../cache/ExtensionLinkStore.kt` — key `pref_extension_anilist_links` (line 113). JSON map `Map<String, Int>` keyed by **`"$sourceId:$animeUrl"`** → anilistId. Methods: `getAniListId(sourceId, animeUrl)`, `getPreferredSourceForAnilist(anilistId)` (reverse lookup), `link(sourceId, animeUrl, anilistId)`, `unlink`, `getAll()`. (lines 34-115)
- **`DetailsViewPreferenceStore`** — `data/extension/.../cache/DetailsViewPreferenceStore.kt` — key `pref_details_view_preference` (line 93). JSON map keyed by EITHER `anilistId.toString()` (linked anime) OR `"ext:$sourceId:$url"` (unlinked). Values are `DataSource.ANILIST` or `DataSource.EXTENSION` (enum name string). (lines 33-95)

These three stores together implement the "which extension source does this AniList anime map to?" cache. They are anilistId-keyed for linked anime.

---

## 6. ADR-024 status-tracking columns — verification

**Spec:** ARCHITECTURE.md §5 (lines 151-163):
> "The SQLDelight schema **must include status-tracking columns** on anime/episode tables: `release_date`, `last_refresh`, `last_metadata_fetch`, `next_episode_check`."

**Verification:**

| Column | `animes.sq` line | `episodes.sq` line |
|---|---|---|
| `release_date` | ✅ line 23 (`release_date INTEGER`) | ❌ MISSING |
| `last_refresh` | ✅ line 24 (`last_refresh INTEGER NOT NULL DEFAULT 0`) | ❌ MISSING |
| `last_metadata_fetch` | ✅ line 25 (`last_metadata_fetch INTEGER`) | ❌ MISSING |
| `next_episode_check` | ✅ line 26 (`next_episode_check INTEGER`) | ❌ MISSING |

**Compliance verdict:** `animes` table ✅ FULLY COMPLIANT. `episodes` table ❌ NON-COMPLIANT — zero of the four ADR-024 columns are present.

The `Anime` domain model (`core/common/.../model/Anime.kt:38-42`) mirrors the anime side — all four fields present. The `Episode` domain model (`core/common/.../model/Episode.kt:9-27`) mirrors the episode side — none present. So either ADR-024 was rescoped to anime-only (without updating ARCHITECTURE.md §5), or the episodes side is a regression.

---

## 7. Schema versioning

- **Mechanism:** SQLDelight native — `.sq` files define the current schema; `.sqm` (migration) files are auto-applied for schema-version bumps. The version number is implicit: `current_version = 1 + <highest .sqm number>`.
- **Files found:** One — `1.sqm` (v1 → v2). No `2.sqm` or higher. **Current schema version: 2.**
- **Driver setup:** `core/database/src/main/java/app/confused/anikuta/core/database/DatabaseDriverFactory.kt:18-22`:
  ```kotlin
  return AndroidSqliteDriver(
      schema = AnikutaDatabase.Schema,
      context = context,
      name = "anikuta.db",
  )
  ```
  `AnikutaDatabase.Schema` is the SQLDelight-generated schema object — it tracks the version + runs `.sqm` migrations automatically. No manual `Migration` classes (unlike Room).
- **Migration content (`1.sqm`):** See §3 above. Adds AniList-linkage columns + `hidden` + Default-category seed.
- **Database name:** `anikuta.db` (single file). No separate manga DB.

---

## 8. `:core:preferences` PreferenceStore + key catalog

### 8.1 The `PreferenceStore` interface

**Path:** `core/preferences/src/main/java/app/confused/anikuta/core/preferences/PreferenceStore.kt`

```kotlin
interface PreferenceStore {
    fun getString(key: String, defaultValue: String = ""): Preference<String>
    fun getLong(key: String, defaultValue: Long = 0): Preference<Long>
    fun getInt(key: String, defaultValue: Int = 0): Preference<Int>
    fun getFloat(key: String, defaultValue: Float = 0f): Preference<Float>
    fun getBoolean(key: String, defaultValue: Boolean = false): Preference<Boolean>
    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Preference<Set<String>>
    fun <T> getObject(key: String, defaultValue: T, serializer: (T) -> String, deserializer: (String) -> T): Preference<T>
    fun getAll(): Map<String, *>
}
```

**Keys are STRINGS** — there is no typed-key wrapper. Every caller passes a literal `"pref_…"` string. The `getEnum` inline helper (lines 27-43) wraps `getObject` for enum persistence (serializes `enum.name`).

**`Preference<T>` interface** (`Preference.kt:7-35`): `key()`, `get()`, `set(value)`, `isSet()`, `delete()`, `defaultValue()`, `changes(): Flow<T>`, `stateIn(scope): StateFlow<T>`. Plus companion-object helpers for `privateKey("__PRIVATE_$key")` and `appStateKey("__APP_STATE_$key")` prefixes — not widely used.

**Impl:** `AndroidPreferenceStore` (`AndroidPreferenceStore.kt:10-50`) — wraps `PreferenceManager.getDefaultSharedPreferences(context)`. Reactive via `SharedPreferences.OnSharedPreferenceChangeListener` → `callbackFlow` (lines 52-59).

### 8.2 Complete preference-key catalog

Found via grep for `"pref_[a-z_]+"` in all `.kt` files. **155 matches across 19 files.** Grouped by feature below. **🎯** = anilistId-keyed (per-anime).

#### Theme / appearance (`:core:preferences`)
File: `core/preferences/src/main/java/app/confused/anikuta/core/preferences/ThemePreferences.kt`

| Key | Type | Default | Line |
|---|---|---|---|
| `pref_theme_mode` | enum `ThemeMode` | `SYSTEM` | 88 |
| `pref_theme_amoled` | Boolean | `false` | 89 |
| `pref_theme_accent_preset` | enum `AccentPreset` | `LIME` | 90 |
| `pref_theme_custom_accent` | Int | `0xFFB1F256` | 91 |
| `pref_palette_mode` | enum `PaletteMode` | `SIMPLIFIED` | 93 |
| `pref_theme_custom_bg` | Int | `0xFF14111F` | 94 |
| `pref_theme_custom_card` | Int | `0xFF221E33` | 95 |
| `pref_theme_custom_text` | Int | `0xFFECE6F5` | 96 |
| `pref_custom_palette_mode` | enum `PaletteMode` | `SIMPLIFIED` | 106 |
| `pref_adaptive_colors_details` | Boolean | `true` | 114 |
| `pref_adaptive_colors_player` | Boolean | `true` | 122 |

#### Player (`:core:player`)
File: `core/player/src/main/java/app/confused/anikuta/core/player/PlayerPreferences.kt` (~70 keys; only the ones cited in the grep output are listed below — see the file for the full list of subtitle/seek/gesture prefs).

| Key | Type | Default | Line |
|---|---|---|---|
| `pref_player_speed` | Float | `1.0f` | 25 |
| `pref_try_hwdec` | Boolean | `true` | 28 |
| `pref_gpu_next` | Boolean | `false` | 31 |
| `pref_volume_boost_cap` | Int | `0` | 34 |
| `pref_preferred_audio_lang` | String | `"jpn,eng"` | 37 |
| `pref_seek_step_seconds` | Int | `10` | 40 |
| `pref_player_brightness` | Float | `-1.0f` | 44 |
| `pref_auto_hide_controls` | Boolean | `true` | 48 |
| `pref_show_episode_titles` | Boolean | `true` | 54 |
| `pref_show_episode_summaries` | Boolean | `true` | 58 |
| `pref_show_episode_thumbnails` | Boolean | `true` | 62 |
| `pref_show_episode_dates` | Boolean | `true` | 66 |
| `pref_show_episode_number` | Boolean | `true` | 70 |
| `pref_show_audio_pills` | Boolean | `true` | 74 |
| `pref_synopsis_position` | String | `"below"` | 81 |
| `pref_download_button_placement` | String | `"episode_row"` | 88 |
| `pref_date_position` | String | `"right_below_synopsis"` | 96 |
| `pref_thumbnail_size` | String | `"medium"` | 102 |
| `pref_title_position` | String | `"right"` | 109 |
| `pref_ep_num_position` | String | `"overlay"` | 116 |
| `pref_thumbnail_position` | String | `"left"` | 123 |
| `pref_anime_info_position` | String | `"below"` | 130 |
| `pref_in_app_metadata_fetch` | Boolean | `true` | 139 |
| `pref_fetch_metadata_thumbnails` | Boolean | `true` | 143 |
| `pref_fetch_metadata_titles` | Boolean | `true` | 147 |
| `pref_fetch_metadata_summaries` | Boolean | `true` | 151 |
| `pref_dynamic_detail_theming` | Boolean | `true` | 160 |
| `pref_watched_episode_appearance` | String | `"grayscale"` | 176 |
| `pref_watched_episode_blur_radius` | Float | `2f` | 184 |
| `pref_watched_episode_alpha` | Float | `0.55f` | 192 |
| `pref_default_player_view` | String | `"minimized"` | 204 |
| `pref_skip_button_duration` | Int | `85` | 211 |
| `pref_player_gestures_enabled` | Boolean | `true` | 218 |
| `pref_player_prompt_shown` | Boolean | `false` | 226 |
| `pref_show_player_top_bar` | Boolean | `true` | 237 |
| `pref_pip_on_exit` | Boolean | `false` | 246 |
| `pref_quality_sheet_display_mode` | String | `"current"` | 254 |
| `pref_subtitle_font` | String | `"Sans Serif"` | 258 |
| `pref_subtitle_font_size` | Int | `55` | 262 |
| `pref_sub_scale` | Float | `1f` | 266 |
| `pref_sub_border_size` | Int | `3` | 270 |
| `pref_bold_subtitles` | Boolean | `false` | 274 |
| `pref_italic_subtitles` | Boolean | `false` | 278 |
| `pref_text_color_subtitles` | Int | `0xFFFFFFFF` | 282 |
| `pref_border_color_subtitles` | Int | `0xFF000000` | 286 |
| `pref_background_color_subtitles` | Int | `0x00000000` | 290 |
| `pref_sub_pos` | Int | `100` | 294 |
| `pref_sub_shadow_offset` | Int | `0` | 298 |
| `pref_override_subtitles_ass` | Boolean | `false` | 302 |
| `pref_default_subtitle_mode` | String | `"on"` | 317 |
| `pref_preferred_subtitle_lang` | String | `"en,eng"` | 326 |
| `pref_subtitles_delay` | Int | `0` | 330 |
| `pref_player_autoplay` | Boolean | `true` | 337 |
| `pref_pause_on_app_exit` | Boolean | `true` | 354 |
| `pref_resume_on_app_return` | Boolean | `true` | 367 |
| `pref_speed_sub` | Float | `1.0f` | 375 |
| `pref_speed_dub` | Float | `1.0f` | 383 |

#### Player episode display (separate from the detail page)
File: `core/player/src/main/java/app/confused/anikuta/core/player/PlayerEpisodePreferences.kt`

Keys are prefixed `player_ep_` (NOT `pref_player_ep_` — see comment at line 10). All global — NONE are anilistId-keyed.

| Key | Type | Default | Line |
|---|---|---|---|
| `player_ep_show_number` | Boolean | `true` | 21 |
| `player_ep_show_titles` | Boolean | `true` | 24 |
| `player_ep_show_summaries` | Boolean | `true` | 27 |
| `player_ep_show_thumbnails` | Boolean | `true` | 30 |
| `player_ep_show_dates` | Boolean | `true` | 33 |
| `player_ep_show_audio_pills` | Boolean | `true` | 36 |
| `player_ep_show_download_button` | Boolean | `true` | 45 |
| `player_ep_synopsis_pos` | String | `"below"` | 48 |
| `player_ep_date_pos` | String | `"right_below_synopsis"` | 51 |
| `player_ep_thumb_size` | String | `"medium"` | 54 |
| `player_ep_title_pos` | String | `"right"` | 57 |
| `player_ep_num_pos` | String | `"overlay"` | 60 |
| `player_ep_thumb_pos` | String | `"left"` | 63 |
| `player_ep_dl_btn_pos` | String | `"episode_row"` | 66 |
| `player_ep_show_title_bg` | Boolean | `true` | 75 |
| `player_ep_show_date_bg` | Boolean | `true` | 79 |
| `player_ep_show_audio_bg` | Boolean | `true` | 83 |
| `player_ep_show_synopsis_bg` | Boolean | `true` | 87 |
| `player_ep_title_lines` | Int | `1` | 93 |
| `player_ep_synopsis_lines` | Int | `2` | 97 |

#### Watch progress + playback state (`:core:player`) — 🎯 anilistId-keyed

File: `core/player/src/main/java/app/confused/anikuta/core/player/WatchProgressStore.kt`

| Key | Type | Key shape inside the JSON map | Line |
|---|---|---|---|
| `pref_watch_progress_map` | `Map<String, Progress>` | 🎯 `"$anilistId:$episodeUrl"` | 47 |

File: `core/player/src/main/java/app/confused/anikuta/core/player/PlaybackStateStore.kt`

| Key | Type | Key shape | Line |
|---|---|---|---|
| `pref_playback_state_map` | `Map<String, PlaybackState>` | 🎯 `"$anilistId:$episodeUrl"` | 47 |

#### Library (`:feature:library`)
File: `feature/library/src/main/java/app/confused/anikuta/feature/library/LibraryPreferences.kt`

| Key | Type | Default | Line |
|---|---|---|---|
| `pref_library_display_mode` | enum `LibraryDisplayMode` | `COMPACT_GRID` | 25 |
| `pref_library_columns_portrait` | Int | `0` (auto) | 28 |
| `pref_library_columns_landscape` | Int | `0` | 31 |
| `pref_library_sort_type` | enum `LibrarySortType` | `TITLE` | 34 |
| `pref_library_sort_ascending` | Boolean | `true` | 37 |
| `pref_library_show_continue_watching` | Boolean | `true` | 40 |
| `pref_library_episode_badge_mode` | enum `EpisodeBadgeMode` | `RELEASED` | 44 |
| `pref_library_show_score_badge` | Boolean | `false` | 47 |
| `pref_library_show_total_entries` | Boolean | `false` | 51 |
| `pref_library_title_lines` | Int | `2` | 55 |
| `pref_library_episode_badge_pos` | enum `BadgePosition` | `TOP_END` | 59 |
| `pref_library_score_badge_pos` | enum `BadgePosition` | `BOTTOM_END` | 63 |

#### Profile page (`:feature:my`)
File: `feature/my/src/main/java/app/confused/anikuta/feature/my/ProfilePreferences.kt`

| Key | Type | Default | Line |
|---|---|---|---|
| `pref_profile_show_quick_stats` | Boolean | `true` | 18 |
| `pref_profile_show_genre` | Boolean | `true` | 19 |
| `pref_profile_show_status` | Boolean | `true` | 20 |
| `pref_profile_show_behind` | Boolean | `true` | 21 |
| `pref_profile_show_recent` | Boolean | `true` | 22 |
| `pref_profile_display_name` | String | `""` | 25 |
| `pref_profile_display_avatar_url` | String | `""` | 26 |
| `pref_profile_use_tracker_stats` | Boolean | `true` | 29 |

#### Search UI (`:feature:search`)
File: `feature/search/src/main/java/app/confused/anikuta/feature/search/data/SearchUiPreferences.kt`

| Key | Type | Default | Line |
|---|---|---|---|
| `pref_search_recents_collapsed` | Boolean | `false` | 33 |

#### Episode display (detail page, `:feature:anime-details`)
File: `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/EpisodeDisplayPreferences.kt`

| Key | Type | Default | Line |
|---|---|---|---|
| `pref_ep_show_number` | Boolean | `true` | 26 |
| `pref_ep_show_titles` | Boolean | `true` | 29 |
| `pref_ep_show_summaries` | Boolean | `true` | 32 |
| `pref_ep_show_thumbnails` | Boolean | `true` | 35 |
| `pref_ep_show_dates` | Boolean | `true` | 38 |
| `pref_ep_show_audio_pills` | Boolean | `true` | 41 |
| `pref_ep_thumb_pos` | String | `"left"` | 47 |
| `pref_ep_title_pos` | String | `"right"` | 51 |
| `pref_ep_synopsis_pos` | String | `"below"` | 55 |
| `pref_ep_date_pos` | String | `"right_below_synopsis"` | 59 |
| `pref_ep_num_pos` | String | `"overlay"` | 63 |
| `pref_ep_thumb_size` | String | `"medium"` | 69 |
| `pref_ep_title_lines` | Int | `1` | 73 |
| `pref_ep_synopsis_lines` | Int | `2` | 77 |
| `pref_ep_show_title_bg` | Boolean | `true` | 86 |
| `pref_ep_show_date_bg` | Boolean | `true` | 90 |
| `pref_ep_show_audio_bg` | Boolean | `true` | 94 |
| `pref_ep_show_synopsis_bg` | Boolean | `true` | 98 |
| `pref_ep_show_download_button` | Boolean | `true` | 106 |

#### Episode metadata (`:core:episode-metadata`)
File: `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/EpisodeMetadataPreferences.kt`

| Key | Type | Default | Line |
|---|---|---|---|
| `pref_ep_metadata_enabled` | Boolean | `true` | 19 |
| `pref_ep_metadata_thumbnails` | Boolean | `true` | 23 |
| `pref_ep_metadata_titles` | Boolean | `true` | 27 |
| `pref_ep_metadata_summaries` | Boolean | `true` | 31 |
| `pref_ep_metadata_airdates` | Boolean | `true` | 35 |

#### Episode metadata cache — 🎯 anilistId-keyed
File: `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/repository/EpisodeMetadataCache.kt`

| Key | Type | Key shape inside the JSON map | Line |
|---|---|---|---|
| `episode_metadata_cache` | `Map<String, String>` (outer) | 🎯 `anilistId.toString()` → JSON-stringified `Map<Int, EpisodeMetadata>` (inner key = episode number) | 32 |

(Note: NOT prefixed `pref_` — only key in the catalog without that prefix.)

#### Downloads (`:core:download`)
File: `core/download/src/main/java/app/confused/anikuta/core/download/DownloadPreferences.kt`

| Key | Type | Default | Line |
|---|---|---|---|
| `pref_dl_folder_uri` | String | `""` | 159 |
| `pref_dl_method` | enum `DownloadMethod` | `NORMAL` | 160 |
| `pref_dl_wifi_only` | Boolean | `true` | 161 |
| `pref_dl_concurrent` | Int | `3` | 162 |
| `pref_dl_show_button` | Boolean | `true` | 163 |
| `pref_dl_auto_pick` | Boolean | `true` | 164 |
| `pref_dl_quality_prefs` | `List<String>` | `["1080p","720p","480p","360p"]` | 165 |
| `pref_dl_audio_prefs` | `List<String>` | `["SUB","DUB"]` | 166 |
| `pref_dl_server_prefs` | `Map<String, List<String>>` (keyed by sourceId) | `emptyMap()` | 167 |
| `pref_dl_quality_fallback` | enum `FallbackStrategy` | `TRY_NEXT` | 168 |
| `pref_dl_audio_fallback` | enum `FallbackStrategy` | `TRY_NEXT` | 169 |
| `pref_dl_server_fallback` | enum `FallbackStrategy` | `TRY_NEXT` | 170 |
| `pref_dl_adv_threads` | Int | `4` | 171 |
| `pref_dl_adv_retries` | Int | `3` | 172 |
| `pref_dl_adv_min_size_mb` | Int | `5` | 173 |

#### Download queue (persisted) — 🎯 anilistId-keyed (composite)
File: `core/download/src/main/java/app/confused/anikuta/core/download/DownloadStore.kt`

| Key | Type | Per-task composite key | Line |
|---|---|---|---|
| `pref_download_tasks_v1` | `List<DownloadTask>` | 🎯 `DownloadTask.key = "${request.anime.anilistId}:${request.episode.episodeUrl}"` (`DownloadTask.kt:41`) | 73 |

#### Update checker (`:core:update-checker`)
File: `core/update-checker/src/main/java/app/confused/anikuta/core/updatechecker/UpdateCheckerPreferences.kt`

| Key | Type | Default | Line |
|---|---|---|---|
| `pref_update_check_last_ts` | Long | `0L` | 47 |
| `pref_update_check_interval_hours` | Int | `3` | 54 |
| `pref_update_check_ep_count_$animeId` | Int | `0` | 66 (per-anime; `animeId` here is the local DB `_id`, NOT anilistId — see line 65) |
| `pref_update_check_results` | `List<StoredResult>` (each carries `anilistId` as a field) | `emptyList()` | 84 |

#### Tracker tokens (`:core:tracker`)

File: `core/tracker/src/main/java/app/confused/anikuta/core/tracker/anilist/AniListTracker.kt:138-141` and `core/tracker/src/main/java/app/confused/anikuta/core/tracker/mal/MalTracker.kt:161-162`. Confirmed mirror in `TrackerBackupProviderImpl.kt:111-116`.

| Key | Type |
|---|---|
| `pref_tracker_anilist_token` | String |
| `pref_tracker_anilist_username` | String |
| `pref_tracker_anilist_avatar` | String |
| `pref_tracker_anilist_user_id` | Int |
| `pref_tracker_mal_oauth` | String (JSON) |
| `pref_tracker_mal_username` | String |

All global — NOT per-anime.

#### Backup (`:core:backup`)
File: `core/backup/src/main/java/app/confused/anikuta/core/backup/BackupPreferences.kt:51-57`

| Key | Type | Default |
|---|---|---|
| `pref_backup_auto_enabled` | Boolean | `false` |
| `pref_backup_auto_frequency` | String (enum name) | `EVERY_24H` |
| `pref_backup_auto_categories` | `Set<String>` | `BackupCategory.defaultSelection` |
| `pref_backup_auto_max_keep` | Int | `3` |
| `pref_backup_folder_uri` | String | `""` |
| `pref_backup_last_auto` | Long | `0L` |
| `pref_backup_last_manual` | Long | `0L` |

#### Source-link extension caches (`:data:extension`) — 🎯 anilistId-keyed (or sourceId:url-keyed)

Already covered in §5.6:
- 🎯 `pref_source_links` — keyed by `anilistId.toString()`
- 🎯 `pref_extension_anilist_links` — keyed by `"$sourceId:$animeUrl"` → anilistId (reverse lookup `getPreferredSourceForAnilist` parses the sourceId out of the matching key)
- 🎯 `pref_details_view_preference` — keyed by `anilistId.toString()` OR `"ext:$sourceId:$url"`

### 8.3 AnilistId-keyed preferences summary

**Seven preference keys are keyed (entirely or partially) by `anilistId`:**

1. 🎯 `pref_watch_progress_map` — `"$anilistId:$episodeUrl"`
2. 🎯 `pref_playback_state_map` — `"$anilistId:$episodeUrl"`
3. 🎯 `pref_download_tasks_v1` — per-task `"$anilistId:$episodeUrl"` (via `DownloadTask.key`)
4. 🎯 `episode_metadata_cache` — `anilistId.toString()` (outer key) → `Map<episodeNumber, EpisodeMetadata>`
5. 🎯 `pref_source_links` — `anilistId.toString()` → `SourceLink(sourceId, animeUrl, animeTitle)`
6. 🎯 `pref_extension_anilist_links` — reverse-direction (`"$sourceId:$animeUrl"` → anilistId)
7. 🎯 `pref_details_view_preference` — `anilistId.toString()` OR `"ext:$sourceId:$url"` → `DataSource` enum name

**There is NO per-anime-episode preference store keyed by anilistId + episodeNumber.** The episode-metadata cache uses `anilistId → Map<episodeNumber, EpisodeMetadata>` (so the outer key IS anilistId-keyed but the inner key is episodeNumber, not a composite `anilistId:episodeNumber`).

The `:feature:episode-settings` module has no preference keys of its own — it reads from `PlayerPreferences` + `EpisodeDisplayPreferences` (both global) and provides a UI for them. No per-anime episode-display prefs exist.

---

## 9. Backup data model (`:core:backup`)

### 9.1 Format (ADR-036)

**File:** `core/backup/src/main/java/app/confused/anikuta/core/backup/format/AnikutaBackupFormat.kt`

**Container:** `.anikuta` = ZIP file containing:
```
backup.anikuta (ZIP)
├── meta.json.gz   — gzipped JSON of BackupContainer
└── covers/        — optional cover images
    ├── 12345.jpg   (anilistId.jpg)
    └── 67890.jpg
```
- Detection: ZIP magic `0x50 0x4B 0x03 0x04` (line 25, 143-158).
- JSON has `classDiscriminator = "type"` (line 57) for sealed `BackupEntry` polymorphism.
- Schema version: `BackupContainer.CURRENT_SCHEMA_VERSION = 1` (line 30 of `BackupContainer.kt`), `SUPPORTED_VERSIONS = 1..1` (line 33).

### 9.2 The 10 backup models

**Path:** `core/backup/src/main/java/app/confused/anikuta/core/backup/model/`

| Model file | Mirrors | Keyed by |
|---|---|---|
| `BackupContainer.kt` | root | — (top-level) |
| `AnimeBackup.kt` | `animes` row | (no key — list) |
| `EpisodeBackup.kt` | `episodes` row | `animeId` (the parent) inside `BackupEntry.Episodes.byAnime: Map<String, List<EpisodeBackup>>` |
| `CategoryBackup.kt` + `AnimeCategoryBackup` | `categories` row + `anime_category` row | `_id` for categories; `(animeId, categoryId)` for links |
| `WatchProgressBackup.kt` + `WatchProgressItem` | `WatchProgressStore.getAll()` | 🎯 `"$anilistId:$episodeUrl"` |
| `SourceLinkBackup.kt` + `SourceLinkItem` | `SourceLinkStore` + `ExtensionLinkStore` | 🎯 anilistId (sourceLinks) / `"$sourceId:$animeUrl"` (extensionLinks) |
| `EpisodeMetadataBackup.kt` + `EpisodeMetadataItem` | `EpisodeMetadataCache` | 🎯 anilistId → `Map<episodeNumber, EpisodeMetadataItem>` |
| `TrackerBackupModel.kt` + `TrackerTrackItem` | tracker prefs + `animetrack` rows | (no outer key — single tracker account per backup) |
| `PreferenceBackup.kt` + `PrefValue` sealed | `PreferenceStore.getAll()` | the preference key string |

### 9.3 The 10 backup providers (Koin-registered)

**Path:** `core/backup/src/main/java/app/confused/anikuta/core/backup/di/BackupModule.kt:53-66`

```kotlin
single<List<BackupProvider>> {
    listOf(
        LibraryBackupProvider(get<AnikutaDatabase>()),
        AnimeDetailsBackupProvider(get<AnikutaDatabase>()),
        EpisodeBackupProvider(get<AnikutaDatabase>()),
        CategoryBackupProvider(get<AnikutaDatabase>()),
        EpisodeMetadataBackupProvider(get<EpisodeMetadataCache>()),
        WatchProgressBackupProvider(get<WatchProgressStore>()),
        SourceLinkBackupProvider(get<SourceLinkStore>(), get<ExtensionLinkStore>()),
        TrackerBackupProviderAdapter(get<TrackerBackupProvider>()),
        PreferencesBackupProvider(get<PreferenceStore>()),
        CoverImageProvider(get<AnikutaDatabase>()),
    )
}
```

The comment at line 50-52 explicitly warns: "Do NOT use multiple `single<BackupProvider> { ... }` — they share the same Koin key (BackupProvider, null) and overwrite each other." This was the root cause of a previous "only 1 category saved" bug.

### 9.4 The `BackupEntry` sealed class — 10 subclasses

**Path:** `core/backup/src/main/java/app/confused/anikuta/core/backup/BackupEntry.kt:31-112`

| Subclass | Field | BackupCategory id |
|---|---|---|
| `Library` | `animes: List<AnimeBackup>` (favorites only) | `library` |
| `AnimeDetails` | `animes: List<AnimeBackup>` (full) | `anime_details` |
| `Episodes` | `byAnime: Map<String, List<EpisodeBackup>>` | `episodes` |
| `EpisodeMetadata` | `byAnime: Map<String, Map<String, EpisodeMetadataItem>>` | `episode_metadata` |
| `WatchProgress` | `progress: WatchProgressBackup` | `watch_progress` |
| `SourceLinks` | `links: SourceLinkBackup` | `source_links` |
| `Tracker` | `data: TrackerBackupModel` | `tracker` |
| `Categories` | `categories: List<CategoryBackup>`, `links: List<AnimeCategoryBackup>` | `categories` |
| `Preferences` | `prefs: PreferenceBackup` | `preferences` |
| `CoverImages` | `covers: Map<String, String>` (anilistId → original URL; bytes are in `covers/<anilistId>.jpg` zip entries) | `cover_images` |

### 9.5 How anime are identified in the backup

**In `AnimeBackup`** (`AnimeBackup.kt:19-49`): the `_id` field defaults to 0 (the local DB id is NOT preserved across devices — it's remapped on restore). The `anilistId` field (line 43) is the **stable cross-device identifier** when present. The `sourceId + url` pair (`sourceId: Long`, `url: String`) is the secondary identifier for unlinked extension anime.

**In `EpisodeBackup`** (`EpisodeBackup.kt:14-32`): keyed inside `BackupEntry.Episodes.byAnime` by a String. The Aniyomi translator (`AniyomiBackupTranslator.kt:315`) keys episodes by `res.anilistId.toString()` after resolving AniList IDs.

**In `WatchProgressBackup`** (`WatchProgressBackup.kt:14-18`): entries keyed by 🎯 `"$anilistId:$episodeUrl"` — same as the live `WatchProgressStore`.

**In `EpisodeMetadataBackup`** (`EpisodeMetadataBackup.kt:16-19`): outer key = 🎯 `anilistId.toString()`, inner key = `episodeNumber.toString()`.

**In `SourceLinkBackup`** (`SourceLinkBackup.kt:17-22`): `sourceLinks` map keyed by 🎯 `anilistId.toString()`, `extensionLinks` map keyed by `"$sourceId:$animeUrl"` → anilistId.

**In `TrackerBackupModel`** (`TrackerBackupModel.kt:18-26`): `bindings: List<TrackerTrackItem>` where each item has `animeId` — in the Aniyomi translator (`AniyomiBackupTranslator.kt:373`), `animeId = res.anilistId.toLong()` (so the binding is keyed by anilistId in the translated backup).

**In `CoverImages`** (`BackupEntry.kt:108-111` + `AnikutaBackupFormat.kt:79-83`): cover image files are stored as `covers/<anilistId>.jpg` in the zip; the JSON entry maps `anilistId.toString()` → original URL.

### 9.6 Aniyomi backup format analysis + translation plan

**Doc path:** `/home/z/my-project/anikuta/DOCS/aniyomi-backup-format/` (NOT under `ANIKUTA_PROJECT/` — it's at the repo root)

**`01-format-overview.md`:** Aniyomi backups are `.tachibk` = gzip-magic `0x1f 0x8b` wrapping a protobuf blob. Two root schemas: modern `Backup` (anime at proto field 501) + legacy `LegacyBackup` (anime at proto field 3). Aniyomi-specific fields are 500+. ANIKUTA ignores manga, preferences, source-preferences, extensions, custom buttons.

**`05-translation-plan.md`** (the blueprint):

> Current state of `AniyomiBackupFormat`:
> - ✅ Decodes protobuf (modern + legacy)
> - ✅ Maps anime/episodes/categories/tracking/history to `BackupContainer`
> - ❌ Does NOT resolve AniList IDs (originally — but the translator now does)
> - ❌ Does NOT remap episode URLs to AniList-keyed progress (now done)
> - ❌ Does NOT match source IDs (still incomplete — direct ID match or name match; falls back to original)

**Target architecture (from the doc):**
```
AniyomiBackupFormat.read()
    ↓
AniyomiBackup (decoded protobuf)
    ↓
AniyomiBackupTranslator.translate()
    ├── 1. Resolve AniList IDs (tracker bindings → MAL lookup → title search)
    ├── 2. Remap source IDs (Aniyomi source → ANIKUTA extension source)
    ├── 3. Build SourceLinkStore entries (anilistId → sourceId+url)
    ├── 4. Remap watch progress keys (sourceId:url:epUrl → anilistId:epUrl)
    └── 5. Build BackupContainer (ready for provider import)
    ↓
BackupManager.restoreBackup()
```

**Implementation status:** The translator IS implemented at `core/backup/src/main/java/app/confused/anikuta/core/backup/translation/AniyomiBackupTranslator.kt` (434 lines). It does:
1. ✅ Resolve AniList IDs via 3 strategies (lines 232-269): tracker `syncId == 2` → use `mediaId`; tracker `syncId == 1` → `anilistApi.searchByMalId(malId)`; else `anilistApi.searchByTitle(title)`. Returns `AnilistResolution.Resolved/Failed/RateLimited`.
2. ⚠️ Source ID remapping: NOT implemented in the translator (it passes through `ani.source` as-is at line 412).
3. ✅ Build `SourceLinkBackup` (lines 387-398) — keyed by `res.anilistId.toString()` → `SourceLinkItem(sourceId = ani.source, animeUrl = ani.url, animeTitle = ani.title)`.
4. ✅ Remap watch progress (lines 348-365) — key = `"${res.anilistId}:${hist.url}"`.
5. ✅ Build `BackupContainer` (lines 400-405) with `appVersion = "aniyomi-translation"`.

Live progress stream: `TranslationProgress(currentIndex, total, currentTitle, resolved, failed, resolution)` via `StateFlow` (line 105).

**`AniyomiBackupFormat`** itself (`core/backup/.../format/AniyomiBackupFormat.kt`):
- Restore-only — `write()` throws `UnsupportedOperationException` (line 66).
- Reads via two strategies: try modern `AniyomiBackup` first (anime at field 501), fall back to `AniyomiLegacyBackup` (anime at field 3). See `tryLegacy()` at line 163.
- Detects gzip magic `0x1f 0x8b` (line 189).
- The legacy `mapToContainer()` method (lines 203-350) is a simpler version that doesn't resolve AniList IDs — used for the basic restore path. The full path goes through `AniyomiBackupTranslator` instead.

---

## 10. Episode metadata cache (`:core:episode-metadata`)

### 10.1 `EpisodeMetadataCache`

**Path:** `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/repository/EpisodeMetadataCache.kt`

**Persistence:** SharedPreferences JSON map.

**Key shape:**
- Outer preference key: `"episode_metadata_cache"` (line 32; NOT prefixed `pref_`)
- Outer JSON map: `Map<String, String>` keyed by 🎯 **`anilistId.toString()`** (line 56)
- Inner JSON value: `Map<Int, EpisodeMetadata>` keyed by **episode number (1-based Int)** (line 26-29, 55-60)

So the metadata is keyed by **`anilistId + episodeNumber`** (anilistId is the outer key, episodeNumber is the inner). It is NOT keyed by episode URL.

### 10.2 `EpisodeMetadata` model

**Path:** `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/model/EpisodeMetadata.kt`

```kotlin
@Serializable
data class EpisodeMetadata(
    val animeId: Int,            // anilistId
    val episodeNumber: Int,      // 1-based
    val title: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val airDate: Long? = null,   // Unix timestamp (seconds)
    val filler: Boolean = false,
    val lastFetched: Long = 0L,  // System.currentTimeMillis()
)
```

### 10.3 The source interface

**Path:** `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/source/EpisodeMetadataSource.kt`

```kotlin
interface EpisodeMetadataSource {
    val id: String
    val name: String
    fun supports(request: EpisodeMetadataRequest): Boolean
    suspend fun fetchAll(request: EpisodeMetadataRequest): Map<Int, EpisodeMetadata>
    val providedFields: Set<EpisodeMetadataField>
}

enum class EpisodeMetadataField { TITLE, DESCRIPTION, THUMBNAIL, AIR_DATE, FILLER }
```

The `EpisodeMetadataRequest` (`EpisodeMetadata.kt:37-44`) carries `animeId: Int` (the AniList ID), `animeTitle`, `episodeNumber`, `malId: Int?` (for Jikan), `bannerImage`, `episodeCount`.

### 10.4 Three source implementations

| Source | Path | Supports when | Provides |
|---|---|---|---|
| `JikanMalSource` | `core/episode-metadata/.../source/jikan/JikanMalSource.kt` | `request.malId != null && > 0` (line 37) | TITLE, AIR_DATE |
| `AniListStreamingSource` | `core/episode-metadata/.../source/anilist/AniListStreamingSource.kt` | (AniList ID present) | THUMBNAIL + (others per source file) |
| `AnikageCcSource` | `core/episode-metadata/.../source/anikage/AnikageCcSource.kt` | (always — best-effort) | THUMBNAIL, DESCRIPTION, AIR_DATE |

**Registry:** `EpisodeMetadataSourceRegistry` (`source/EpisodeMetadataSourceRegistry.kt:19-34`) — simple `mutableListOf`, `register/unregister/getAll/getSupported`. **Priority is registration order** (the `EpisodeMetadataRepository` merge logic at `repository/EpisodeMetadataRepository.kt:125-133` uses "first non-null wins" in source-registration order).

**Repository merge priority** (from `EpisodeMetadataRepository.kt:24-28` KDoc):
- Title: Jikan → Anikage → Kitsu → AniList
- Description: Anikage → Kitsu
- Thumbnail: Anikage → AniList → Kitsu → banner fallback
- Air date: Jikan → Anikage → Kitsu

(Kitsu is mentioned in comments but not implemented as a source — only Jikan, AniList, Anikage are registered.)

---

## 11. Watch progress (`:core:player` `WatchProgressStore`)

**Path:** `core/player/src/main/java/app/confused/anikuta/core/player/WatchProgressStore.kt`

**Persistence mechanism:** **SharedPreferences** (NOT SQLDelight). One JSON-serialized `Map<String, Progress>` stored under key `"pref_watch_progress_map"` (line 47).

**Key shape:** 🎯 **`"$anilistId:$episodeUrl"`** (line 64: `private fun key(anilistId: Int, episodeUrl: String) = "$anilistId:$episodeUrl"`).

**`Progress` model** (lines 130-144):
```kotlin
@Serializable
data class Progress(
    val positionSeconds: Int,
    val durationSeconds: Int,
    val title: String,
    val updatedAt: Long,
    val coverUrl: String? = null,
    val animeTitle: String? = null,
    val episodeNumber: Float = -1f,  // -1 = unknown
    val thumbnailUrl: String? = null,
)
```

**API:**
- `save(anilistId, episodeUrl, positionSeconds, durationSeconds, title, coverUrl?, animeTitle?, episodeNumber, thumbnailUrl?)` — lines 74-97
- `get(anilistId, episodeUrl): Progress?` — line 100
- `clear(anilistId, episodeUrl)` — line 105
- `clearAnime(anilistId)` — removes all keys with prefix `"$anilistId:"` — line 112
- `deleteAll()` — O(1) clear-all (line 123)
- `getAll(): Map<String, Progress>` — line 128
- `changes: Flow<Map<String, Progress>>` — reactive (line 61)

**Why not SQLDelight?** The KDoc (lines 27-33) explains:
> "The full aniyomi approach stores progress in the `episodes` + `animehistory` SQLDelight tables. Our app is AniList-first, and `animes.source` + `url` (NOT NULL) aren't available until AniyomiSourceBridge resolves. Until that gap is closed, WatchProgressStore remains the source of truth for AniList-keyed progress. The SQLDelight repos are wired (Phase 0) and ready for when we resolve source URLs."

The `animehistory` SQLDelight table exists and is wired via `HistoryRepositoryImpl`, but the live History page reads from `WatchProgressStore.changes` (per `feature/history/HistoryViewModel.kt` — see comment in `WatchProgressStore.kt:38`). The SQLDelight history table is effectively **dormant infrastructure**.

**Companion store:** `PlaybackStateStore` (`core/player/.../PlaybackStateStore.kt`) — same pattern, key 🎯 `"$anilistId:$episodeUrl"`, stores `PlaybackState(videoUrl, videoServer, videoAudio, videoQuality, videoHeaders, audioTrackId, subtitleTrackId, sourceId, updatedAt)`. Used for "resume from History" to retry the same video URL + tracks.

---

## 12. Download persistence (`:core:download` `DownloadStore`)

**Path:** `core/download/src/main/java/app/confused/anikuta/core/download/DownloadStore.kt`

**Persistence mechanism:** **SharedPreferences** (NOT SQLDelight, NOT raw JSON file). One JSON-serialized `List<DownloadTask>` stored under key `"pref_download_tasks_v1"` (line 73).

**Why not SQLDelight?** KDoc (lines 17-23) explains:
> "Why not SQLDelight? The download state is small (tens of tasks, not thousands) and highly mutable (progress ticks). A pref-backed JSON list is simpler, has no migration cost, and matches how `WatchProgressStore` already works. The plan's status-tracking columns (ADR-024) apply to anime/episode DB rows, not to the transient download queue."

**Per-task composite key:** 🎯 `DownloadTask.key = "${request.anime.anilistId}:${request.episode.episodeUrl}"` (`DownloadTask.kt:41`). Same shape as `WatchProgressStore` and `PlaybackStateStore` keys.

**`DownloadTask` model** (`DownloadTask.kt:27-49`):
```kotlin
@Serializable
data class DownloadTask(
    val id: Long,                          // monotonic, assigned at enqueue time
    val request: DownloadRequest,
    val status: DownloadStatus,
    val progress: Int = 0,                 // 0..100
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,            // -1 = unknown/chunked
    val errorMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val videoUri: String? = null,          // set on COMPLETED
    val subtitleUris: List<String> = emptyList(),
) {
    val key: String get() = "${request.anime.anilistId}:${request.episode.episodeUrl}"
    val isInQueue: Boolean get() = status in QUEUED/DOWNLOADING/PAUSED/ERROR
}
```

**`DownloadRequest`** (`DownloadRequest.kt:32-46`) carries `anime: DownloadAnimeInfo(anilistId: Int, title, coverUrl?, coverColor?)` + `episode: DownloadEpisodeInfo(episodeUrl: String, episodeNumber: Float, name, scanlator?)` + the resolved `videoUrl`, `videoHeaders`, `subtitleTracks`, `audioTracks`, `sourceId`, `videoServer`, `videoQuality`, `videoAudio`.

**API:**
- `getAll(): List<DownloadTask>` — line 54
- `setAll(tasks: List<DownloadTask>)` — line 57 (called by the queue on every state change)
- `purgeCancelled(): List<DownloadTask>` — line 62 (called on startup; removes CANCELLED tasks)
- `changes: Flow<List<DownloadTask>>` — line 51

Writes are coalesced by the `DownloadQueue` (per KDoc line 24-26): "progress ticks don't write on every byte — the queue writes on state CHANGES (queued/started/paused/completed/error) + a throttled progress snapshot."

---

## 13. Primary-key summary — "what's the PK for each entity today?"

| Entity | DB PK (SQLDelight) | "Natural" key used outside the DB | Notes |
|---|---|---|---|
| **Anime** | `_id` (autoincrement Long) | **`anilistId: Int?`** (partial-unique index) | `anilistId` is nullable for unlinked extension anime; for those, `sourceId + url` is the natural key (see `selectBySourceAndUrl`). |
| **Episode** | `_id` (autoincrement Long) | 🎯 **`anilistId + episodeUrl`** (composite, used by WatchProgressStore, PlaybackStateStore, DownloadStore, backup translator) | The DB itself uses `anime_id` (FK to `animes._id`) + `episode_number` for `selectByAnimeIdAndNumber`. There's no DB unique constraint on (anime_id, episode_number) or (anime_id, url). |
| **History** | `_id` (autoincrement Long) + `UNIQUE(anime_id, episode_id)` | `anilistId + episodeUrl` (used by `WatchProgressStore` which BYPASSES the `animehistory` table) | The `animehistory` table is wired but unused; live history = `WatchProgressStore`. |
| **Category** | `_id` (autoincrement Long; Default fixed at 1) | `_id` | No anilist concept. |
| **Anime↔Category link** | `_id` (autoincrement Long) | `(anime_id, category_id)` | No unique constraint declared (uses `INSERT OR IGNORE` as a soft guard). |
| **Track binding** | `_id` (autoincrement Long) + `UNIQUE(anime_id, tracker_id)` | `(anilistId, trackerId)` via the `anime_id` FK | `remote_id` holds the tracker-side media ID (= AniList ID when trackerId=2). |
| **Download** | (no SQLDelight) — `DownloadTask.id` (monotonic Long, in-memory + pref) | 🎯 **`anilistId + episodeUrl`** (`DownloadTask.key`) | Stored in `pref_download_tasks_v1` JSON list. |
| **Watch progress** | (no SQLDelight) — pref-map entry | 🎯 **`"$anilistId:$episodeUrl"`** | Stored in `pref_watch_progress_map`. |
| **Playback state** | (no SQLDelight) — pref-map entry | 🎯 **`"$anilistId:$episodeUrl"`** | Stored in `pref_playback_state_map`. |
| **Episode metadata** | (no SQLDelight) — pref-map entry | 🎯 **`anilistId + episodeNumber`** (outer = anilistId, inner = episodeNumber) | Stored in `episode_metadata_cache`. |
| **Source link (AniList→ext)** | (no SQLDelight) — pref-map entry | 🎯 **`anilistId`** | Stored in `pref_source_links`. |
| **Extension link (ext→AniList)** | (no SQLDelight) — pref-map entry | `"$sourceId:$animeUrl"` → `anilistId` | Stored in `pref_extension_anilist_links`. |
| **Details view preference** | (no SQLDelight) — pref-map entry | `anilistId` OR `"ext:$sourceId:$url"` | Stored in `pref_details_view_preference`. |
| **Tracker token** | (no SQLDelight) — single pref string | (global, one per tracker) | `pref_tracker_anilist_token`, `pref_tracker_mal_oauth`, etc. |
| **Update-check last-known episode count** | (no SQLDelight) — single pref int | `animeId` (= local DB `_id`, NOT anilistId) | `pref_update_check_ep_count_$animeId`. |

### 13.1 Key observation

**The "AniList-first" architecture (ADR-010) is realized in the SharedPreferences layer (where `anilistId` is the dominant key) but NOT in the SQLDelight layer (where the surrogate `_id` is the PK and `anilist_id` is a nullable secondary column on `animes` only).** The `episodes`, `animehistory`, and `animetrack` tables have NO `anilist_id` column — they reference anime via the FK `anime_id → animes._id`, requiring a JOIN to recover the AniList ID.

This is consistent with the `WatchProgressStore` KDoc comment (lines 27-33): the SQLDelight repos are "wired (Phase 0) and ready for when we resolve source URLs" — i.e. the SQLDelight layer is infrastructure for a future source-first mode, while the AniList-first mode lives in SharedPreferences.

---

## 14. Notable findings / latent issues

1. **ADR-024 episode-side compliance gap.** ARCHITECTURE.md §5 says the four status-tracking columns must be on anime/episode tables. They are present on `animes` only. Either update the doc or add the columns to `episodes.sq` (with a `2.sqm` migration).

2. **Dual-schema pattern (ADR-009) not implemented.** ARCHITECTURE.md §5 says "separate `sqldelight/` and `sqldelightanime/` source sets, independent version counters." Only `sqldelight/` exists. The DatabaseDriverFactory explicitly defers: "A separate manga database will be added when manga is implemented."

3. **Two `TrackRepository` interfaces.** `:core:common/repository/TrackRepository.kt` (4 methods, no impl) vs `:core:tracker/TrackRepository.kt` (used everywhere). The `:core:common` one is dead code — likely should be removed or the `:core:tracker` one renamed.

4. **`animehistory` SQLDelight table is wired but unused.** `HistoryRepositoryImpl` exists and works, but the live History page reads from `WatchProgressStore` (SharedPreferences). The `animehistory` table persists nothing in practice. The KDoc of `WatchProgressStore` (lines 27-33) acknowledges this is a "Phase 0" placeholder.

5. **No unique constraint on `episodes(anime_id, episode_number)` or `episodes(anime_id, url)`.** The schema allows duplicate episodes per anime. The `selectByAnimeIdAndNumber` query exists but isn't used for upsert — the `EpisodeRepositoryImpl.upsert` decides insert-vs-update by `episode.id > 0`, which means callers must track ids explicitly.

6. **`Episode.url` is nullable** (in both the DB schema line 6 of `episodes.sq` and the domain model `Episode.kt:12`). This complicates the composite key `"$anilistId:$episodeUrl"` for WatchProgressStore / DownloadStore — if `episodeUrl` is null, the key would be `"$anilistId:null"`. In practice the watch flow always has a non-null URL (it's the source-episode URL), but the schema permits null.

7. **Schema version is implicit.** No `schemaVersion = N` in `core/database/build.gradle.kts` — relies on SQLDelight's `.sqm` filename counting. This is fine but easy to miss in audits.

8. **`UpdateCheckerPreferences.lastKnownEpisodeCount(animeId)` keys by the LOCAL DB `_id`, NOT `anilistId`** (line 65-66). This is inconsistent with the rest of the app's anilistId-first keying. It works because `UpdateChecker` operates on `Anime` objects (which carry `_id`), but it's a maintainability landmine.

9. **Preference key `episode_metadata_cache` lacks the `pref_` prefix** that every other key uses. Minor inconsistency — backup/restore code handles it fine but it stands out.

10. **`DownloadStore` writes the entire `List<DownloadTask>` on every state change** (`setAll` at line 57). For 50+ items this is a full JSON re-encode + SharedPreferences commit. The KDoc says writes are coalesced by the queue, but the persistence shape is still "rewrite everything."

11. **Aniyomi source-ID remapping is NOT implemented** in `AniyomiBackupTranslator` (it passes through `ani.source` verbatim at line 412). The translation plan doc (§Step 2) describes the intended matching (direct ID → name match → fallback) but it's not in the code. Restored anime from Aniyomi may have non-functional source links until the user manually re-links.

---

## 15. Files inspected (complete list)

### SQLDelight schema + migration
- `core/database/src/main/sqldelight/app/confused/anikuta/core/database/animes.sq`
- `core/database/src/main/sqldelight/app/confused/anikuta/core/database/episodes.sq`
- `core/database/src/main/sqldelight/app/confused/anikuta/core/database/animehistory.sq`
- `core/database/src/main/sqldelight/app/confused/anikuta/core/database/animetrack.sq`
- `core/database/src/main/sqldelight/app/confused/anikuta/core/database/anime_category.sq`
- `core/database/src/main/sqldelight/app/confused/anikuta/core/database/categories.sq`
- `core/database/src/main/sqldelight/app/confused/anikuta/core/database/1.sqm`
- `core/database/build.gradle.kts`
- `core/database/src/main/java/app/confused/anikuta/core/database/DatabaseDriverFactory.kt`
- `core/database/README.md`
- `app/src/main/java/app/confused/anikuta/di/DatabaseModule.kt`

### Domain models (`:core:common`)
- `core/common/src/main/java/app/confused/anikuta/core/common/model/Anime.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/Episode.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/History.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/Track.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/Category.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/AnimeCategoryLink.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/Source.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/LibrarySort.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/LibraryDisplayMode.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/EpisodeBadgeMode.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/BadgePosition.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/details/UnifiedAnime.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/details/AnimeDetailsProvider.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/details/DataSource.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/details/DetailsRequest.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/details/AnimeDetailsProviderRegistry.kt`

### Repository interfaces (`:core:common`)
- `core/common/src/main/java/app/confused/anikuta/core/common/repository/AnimeRepository.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/repository/EpisodeRepository.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/repository/HistoryRepository.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/repository/CategoryRepository.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/repository/TrackRepository.kt`

### Repository impls
- `data/anime/src/main/java/app/confused/anikuta/data/anime/AnimeRepositoryImpl.kt`
- `data/anime/src/main/java/app/confused/anikuta/data/anime/AnimeMapper.kt`
- `data/anime/src/main/java/app/confused/anikuta/data/anime/EpisodeRepositoryImpl.kt`
- `data/anime/src/main/java/app/confused/anikuta/data/anime/EpisodeMapper.kt`
- `data/anime/src/main/java/app/confused/anikuta/data/anime/CategoryRepositoryImpl.kt`
- `data/anime/src/main/java/app/confused/anikuta/data/anime/CategoryMapper.kt`
- `data/history/src/main/java/app/confused/anikuta/data/history/HistoryRepositoryImpl.kt`
- `data/history/src/main/java/app/confused/anikuta/data/history/HistoryMapper.kt`
- `data/extension/src/main/java/app/confused/anikuta/data/extension/cache/SourceLinkStore.kt`
- `data/extension/src/main/java/app/confused/anikuta/data/extension/cache/ExtensionLinkStore.kt`
- `data/extension/src/main/java/app/confused/anikuta/data/extension/cache/DetailsViewPreferenceStore.kt`

### Preferences
- `core/preferences/src/main/java/app/confused/anikuta/core/preferences/PreferenceStore.kt`
- `core/preferences/src/main/java/app/confused/anikuta/core/preferences/Preference.kt`
- `core/preferences/src/main/java/app/confused/anikuta/core/preferences/AndroidPreferenceStore.kt`
- `core/preferences/src/main/java/app/confused/anikuta/core/preferences/ThemePreferences.kt`
- `core/player/src/main/java/app/confused/anikuta/core/player/PlayerPreferences.kt`
- `core/player/src/main/java/app/confused/anikuta/core/player/PlayerEpisodePreferences.kt`
- `core/download/src/main/java/app/confused/anikuta/core/download/DownloadPreferences.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/BackupPreferences.kt`
- `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/EpisodeMetadataPreferences.kt`
- `core/update-checker/src/main/java/app/confused/anikuta/core/updatechecker/UpdateCheckerPreferences.kt`
- `feature/library/src/main/java/app/confused/anikuta/feature/library/LibraryPreferences.kt`
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/EpisodeDisplayPreferences.kt`
- `feature/my/src/main/java/app/confused/anikuta/feature/my/ProfilePreferences.kt`
- `feature/search/src/main/java/app/confused/anikuta/feature/search/data/SearchUiPreferences.kt`

### Player + download stores
- `core/player/src/main/java/app/confused/anikuta/core/player/WatchProgressStore.kt`
- `core/player/src/main/java/app/confused/anikuta/core/player/PlaybackStateStore.kt`
- `core/download/src/main/java/app/confused/anikuta/core/download/DownloadStore.kt`
- `core/download/src/main/java/app/confused/anikuta/core/download/DownloadTask.kt`
- `core/download/src/main/java/app/confused/anikuta/core/download/DownloadRequest.kt`
- `core/download/src/main/java/app/confused/anikuta/core/download/DownloadModels.kt`

### Episode metadata
- `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/repository/EpisodeMetadataCache.kt`
- `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/repository/EpisodeMetadataRepository.kt`
- `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/model/EpisodeMetadata.kt`
- `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/source/EpisodeMetadataSource.kt`
- `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/source/EpisodeMetadataSourceRegistry.kt`
- `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/source/jikan/JikanMalSource.kt`

### Backup
- `core/backup/src/main/java/app/confused/anikuta/core/backup/model/BackupContainer.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/model/AnimeBackup.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/model/EpisodeBackup.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/model/WatchProgressBackup.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/model/SourceLinkBackup.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/model/EpisodeMetadataBackup.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/model/CategoryBackup.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/model/TrackerBackupModel.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/model/PreferenceBackup.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/BackupEntry.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/BackupCategory.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/di/BackupModule.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/format/AnikutaBackupFormat.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/format/AniyomiBackupFormat.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/format/aniyomi/AniyomiBackupModels.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/translation/AniyomiBackupTranslator.kt`
- `core/tracker/src/main/java/app/confused/anikuta/core/tracker/TrackerBackupProviderImpl.kt`

### AniList + DataSource
- `core/anilist/src/main/java/app/confused/anikuta/core/anilist/model/AniListAnime.kt`

### Architecture + docs
- `ARCHITECTURE.md` §5 (lines 151-165) — ADR-024 spec
- `/home/z/my-project/anikuta/DOCS/aniyomi-backup-format/01-format-overview.md`
- `/home/z/my-project/anikuta/DOCS/aniyomi-backup-format/05-translation-plan.md`

---

**End of evidence file.**
