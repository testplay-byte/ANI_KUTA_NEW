# Phase 2 — Execution Plan

> Detailed plan for Phase 2: Core domain & data layer.
> Per `PHASED_PLAN.md` Phase 2.

## Goal

Domain models + persistence layer (SQLDelight) + repository pattern, all wired
via Koin, with unit tests passing on CI.

## Modules involved

| Module | Role |
|---|---|
| `:core:common` | Domain models + repository interfaces (pure Kotlin + coroutines) |
| `:core:database` | SQLDelight schema, database driver, queries |
| `:data:anime` | Anime + Episode repository impls |
| `:data:history` | History repository impl |
| `:app` | Koin wiring (bind interfaces → impls, provide DB) |

## Step-by-step

### Step 1: Add SQLDelight plugin to root build
- Add `id("app.cash.sqldelight") version "2.0.2" apply false` to root `build.gradle.kts`.

### Step 2: Set up `:core:database`
- Apply `anikuta.library` + `app.cash.sqldelight` plugins.
- Configure SQLDelight: database name `AnikutaDatabase`, package `app.confused.anikuta.core.database`.
- Add `sqldelight-android-driver` + `sqldelight-coroutines` deps.
- Create `.sq` files under `src/main/sqldelight/app/confused/anikuta/core/database/`:
  - `animes.sq` — anime table with status columns (ADR-024)
  - `episodes.sq` — episode table
  - `categories.sq` — category table
  - `anime_category.sq` — junction table
  - `animehistory.sq` — history table
  - `animetrack.sq` — tracker binding table
- Create `DatabaseDriverFactory.kt` — provides the Android `AndroidSqliteDriver`.

### Step 3: Domain models in `:core:common`
- `Anime.kt` — with status fields: `releaseDate`, `lastRefresh`, `lastMetadataFetch`, `nextEpisodeCheck`.
- `Episode.kt` — with anime-specific fields: `fillermark`, `summary`, `previewUrl`, `lastSecondSeen`, `totalSeconds`.
- `Category.kt`, `History.kt`, `Track.kt`, `Source.kt` (stub).
- Manga equivalents (skeleton, hidden per ADR-009).

### Step 4: Repository interfaces in `:core:common`
- `AnimeRepository` — get, getFavorites, search, upsert, updateFavorite.
- `EpisodeRepository` — getByAnime, get, upsert, updateSeen.
- `CategoryRepository` — getAll, create, update, delete.
- `HistoryRepository` — getAll, upsert, delete.
- `TrackRepository` — getByAnime, upsert, delete.
- All return `Flow<...>` for reactive queries.

### Step 5: Repository implementations in `:data:anime` + `:data:history`
- `AnimeRepositoryImpl` — SQLDelight-backed, maps queries → domain models.
- `EpisodeRepositoryImpl` — same.
- `HistoryRepositoryImpl` — same.
- Map between SQLDelight generated types and domain models.

### Step 6: Koin wiring in `:app`
- A `DataModule` Koin module: bind interfaces → impls, provide `AnikutaDatabase`.
- A `DatabaseModule`: provide `DatabaseDriverFactory`.
- Register in `App.kt`'s `startKoin { }` block.

### Step 7: Unit tests
- `AnimeRepositoryImplTest` — mock the queries, test mapping logic.
- `EpisodeRepositoryImplTest` — same.
- Success path + edge cases (empty, null).

### Step 8: Push + verify CI
- Commit, push, wait for CI green.
- Send success notification.

## NOT in Phase 2 scope
- UI (Phase 3+).
- Bottom navigation (owner will share references first).
- AniList integration (Phase 7).
- Episode metadata module (Phase 5).
- Actual source/extension loading (Phase 4).

## ADRs referenced
- ADR-009: Anime-first, manga-ready (manga skeleton, hidden).
- ADR-023: Koin DI.
- ADR-024: SQLDelight with status-tracking columns.
- ADR-029: Aniyomi extension compat (source-api shape).
