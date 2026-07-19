# :domain module — what we copied from aniyomi

> Step 1.5 — Copy `:domain` from aniyomi (anime side only).
> Domain models + use cases + repository interfaces. Pure Kotlin, no Android UI,
> no database — just the business-logic contracts.

---

## Source
- aniyomi module: `:domain`
- aniyomi path: `REFERENCE/domain/src/main/java/`
- aniyomi commit: `2f5cf77` (2025-11-05)
- Copied on: Session 15 (Step 1.5)

## What we copied (146 files — anime + shared, manga excluded)

### Anime-specific (68 files in `*/anime/` subdirs)
- `entries/anime/` — Anime model, AnimeCover, AnimeUpdate, AnimeRepository, interactors (GetAnime, GetLibraryAnime, etc.)
- `source/anime/` — AnimeSourceManager, AnimeSource models, AnimeSourceRepository, AnimeStubSourceRepository
- `history/anime/` — AnimeHistory model, AnimeHistoryRepository, interactors
- `category/anime/` — AnimeCategoryRepository, category interactors
- `updates/anime/` — anime updates model + repo
- `track/anime/` — anime track models + repo
- `download/anime/` — anime download provider
- `library/anime/` — library anime models + interactors

### Shared (56 files — used by both anime + manga in aniyomi, anime-only in our app)
- `backup/` — backup models + service interfaces
- `category/model/` — Category, CategoryUpdate
- `custombuttons/` — custom button model + repo + interactors
- `download/service/` — DownloadManager interface
- `entries/` — EntryCover, TriState (shared entry helpers)
- `items/` — episode/season/chapter sorters + recognition (anime-relevant)
- `library/` — library flags + display mode
- `release/` — release model + repo
- `storage/` — StorageManager (fixed: DiskUtil ref commented out)
- `track/` — tracker model + auto-step model

### Mihon ecosystem (22 files)
- `mihon/extensionrepo/` — extension repository management
- `mihon/items/` — shared item types
- `mihon/upcoming/` — upcoming feature

### Aniyomi additions (2 files)
- `anime/SeasonAnime.kt` — season anime model
- `anime/SeasonDisplayMode.kt` — season display mode

## What we did NOT copy (manga side — D2: anime-only)
- All `*/manga/` subdirectories (manga interactors, models, repos)
- Manga-only domain code

## Changes made to copied files
1. **Package rename:** `tachiyomi.domain.*` → `app.anikuta.domain.*`, `mihon.domain.*` → `app.anikuta.domain.mihon.*`, `aniyomi.domain.anime` → `app.anikuta.domain.anime`
2. **Import rename:** all corresponding imports updated + `eu.kanade.tachiyomi.*` → `app.anikuta.*`
3. **DiskUtil reference:** commented out in `StorageManager.kt` (DiskUtil not copied to :core yet — TODO later)

## Dependencies (to `:domain` build.gradle.kts)
- `:source-api`, `:core` (internal)
- kotlinx-coroutines, kotlinx-serialization
- Injekt (for DI in some interactors)
