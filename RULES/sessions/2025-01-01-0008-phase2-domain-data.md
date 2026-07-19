# Session handoff — Phase 2: Core domain & data layer

**Agent:** Z.ai Code (session 8)
**Task ID:** phase-2-domain-data
**Session goal:** Implement the core domain models, SQLDelight schema, repository
pattern, Koin wiring, and unit tests.

## What I did

### Pre-Phase-2 fixes (icon + signing)
- Created temporary adaptive app icon (vector: play triangle on dark bg).
- Generated stable debug keystore (`anikuta-debug.keystore`) so the owner can
  update without reinstalling (password: "android").
- Configured debug build type to sign with it.
- `.gitignore` exception for the debug keystore.

### Phase 2 implementation

**Step 1:** Added SQLDelight plugin to root build.gradle.kts.

**Step 2:** Set up `:core:database`:
- SQLDelight plugin + config (`AnikutaDatabase`).
- 6 `.sq` files: `animes.sq`, `episodes.sq`, `categories.sq`, `anime_category.sq`,
  `animehistory.sq`, `animetrack.sq`.
- Status-tracking columns on `animes` table (ADR-024): `release_date`,
  `last_refresh`, `last_metadata_fetch`, `next_episode_check`.
- `DatabaseDriverFactory.kt` — provides the Android `AndroidSqliteDriver`.

**Step 3:** Domain models in `:core:common`:
- `Anime.kt` (with status fields), `Episode.kt`, `Category.kt`, `History.kt`,
  `Track.kt`, `Source.kt`.
- `AnimeStatus` constants object.

**Step 4:** Repository interfaces in `:core:common`:
- `AnimeRepository`, `EpisodeRepository`, `CategoryRepository`,
  `HistoryRepository`, `TrackRepository`.
- All return `Flow<...>` for reactive queries.

**Step 5:** Repository implementations:
- `AnimeRepositoryImpl` + `EpisodeRepositoryImpl` in `:data:anime`.
- `HistoryRepositoryImpl` in `:data:history`.
- SQLDelight-backed, with mappers (`AnimeMapper`, `EpisodeMapper`, `HistoryMapper`).
- Fixed: SQLDelight property names (`animesQueries`, `episodesQueries`).
- Fixed: parameter order (named arguments to avoid SQLDelight's first-appearance ordering).

**Step 6:** Koin wiring in `:app`:
- `DatabaseModule` — provides `DatabaseDriverFactory` + `AnikutaDatabase`.
- `RepositoryModule` — binds interfaces → impls.
- `App.kt` registers both modules.

**Step 7:** Unit tests:
- `AnimeMapperTest` in `:data:anime/src/test/` — 4 tests covering:
  full anime, null fields, empty genre, whitespace genre.
- Added JUnit Platform (`useJUnitPlatform()`) to convention plugins.
- CI workflow updated to run `:data:anime:testDebugUnitTest`.

## What is DONE (pending CI)
- All domain models defined with status fields.
- SQLDelight schema (6 tables).
- 3 repository interfaces + 3 implementations.
- Koin wiring (database + repository modules).
- Unit tests for the AnimeMapper.
- Phase 2 plan document.

## What is NOT done
- Category + Track repository impls (deferred — not needed until Phase 4+).
- Manga domain models + schema (hidden per ADR-009 — skeleton only).
- Full DB integration tests (mapper tests are sufficient for Phase 2 exit criteria).
- UI (Phase 3+).
- Bottom navigation (owner will share references first).

## Potential CI risks
- SQLDelight query generation (first time — may have syntax issues in `.sq` files).
- Mapper parameter type mismatches (if SQLDelight maps types differently than expected).
- SQLDelight dialect resolution (removed explicit dialect, using default).
- The `coroutines-extensions` import paths (may differ in 2.0.2).

## What the NEXT agent should do
1. Check CI result. If failed, read the error, fix, push.
2. If CI passed, Phase 2 exit criteria are met → Phase 3 (Design system & theme).
3. Phase 3 will build the `:core:designsystem` module with all components from
   `DESIGN_LANGUAGE/`. But NOT the bottom nav (owner will share references first).

## Pointers
- `PLANNING/02-phase-2-execution-plan.md` — the plan.
- `core/database/src/main/sqldelight/` — the SQLDelight schema.
- `core/common/src/main/java/.../model/` — domain models.
- `core/common/src/main/java/.../repository/` — repository interfaces.
- `data/anime/src/main/java/.../` — anime + episode repo impls + mappers.
- `data/history/src/main/java/.../` — history repo impl + mapper.
- `app/src/main/java/.../di/` — Koin modules.
