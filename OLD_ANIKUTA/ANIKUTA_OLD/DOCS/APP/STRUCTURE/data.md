# :data module — what we copied from aniyomi

> Step 1.6 — Copy `:data` from aniyomi (anime side only).
> The SQLDelight anime database + repository implementations.

---

## Source
- aniyomi module: `:data`
- aniyomi commit: `2f5cf77` (2025-11-05)
- Copied on: Session 16 (Step 1.6)

## What we copied (67 files)

### Anime SQLDelight schema (17 .sq files)
- `sqldelight/dataanime/` — 9 table schemas: animes, episodes, animehistory, anime_sync, animesources, categories, animes_categories, custom_buttons, extension_repos
- `sqldelight/view/` — 8 views: animedeletableView, animehistoryView, animehistorystatsView, animelibView, animeseasonsView, animeseasonstatsView, animeupdatesView, episodestatsView

### Anime migrations (23 .sqm files)
- `sqldelight/migrations/` — migrations 113–135 (anime DB version tracking)

### Anime Kotlin files (21 files)
- `entries/anime/` — AnimeRepositoryImpl, AnimeMapper
- `handlers/` — AndroidAnimeDatabaseHandler, AnimeDatabaseHandler, AnimeTransactionContext, QueryPagingAnimeSource
- `history/anime/` — AnimeHistoryRepositoryImpl, AnimeHistoryMapper
- `source/anime/` — AnimeSourceRepositoryImpl, AnimeStubSourceRepositoryImpl, AnimeSourcePagingSource
- `category/anime/` — AnimeCategoryRepositoryImpl
- `track/anime/` — AnimeTrackRepositoryImpl, AnimeTrackMapper
- `updates/anime/` — AnimeUpdatesRepositoryImpl
- `mihon/` — AnimeExtensionRepoRepositoryImpl

### Shared Kotlin files (6 files)
- `DatabaseAdapter.kt` — column adapters (Date, StringList, AnimeUpdateStrategy, FetchType). MangaUpdateStrategyColumnAdapter removed.
- `custombutton/CustomButtonRepositoryImpl.kt`
- `items/episode/EpisodeRepositoryImpl.kt`, `EpisodeSanitizer.kt`
- `release/GithubRelease.kt`, `ReleaseServiceImpl.kt`

## What we did NOT copy
- Manga DB (`sqldelight/` — the manga schema, migrations, views)
- Manga repos (`*/manga/` — MangaRepositoryImpl, MangaHistoryRepositoryImpl, etc.)
- Chapter repos (`items/chapter/` — manga-specific)
- Manga handlers (`handlers/manga/`)

## Changes
1. Package rename: `tachiyomi.data.*` → `app.anikuta.data.*`, `tachiyomi.mi.data` → `app.anikuta.data`
2. SQLDelight config: single `AnimeDatabase` (package `app.anikuta.data`), no manga `Database`
3. DatabaseAdapter: removed MangaUpdateStrategyColumnAdapter + its import
4. .sq file imports: `eu.kanade.tachiyomi.animesource.model.*` → `app.anikuta.source.api.model.*`

## Dependencies
- SQLDelight 2.0.2 (android-driver, coroutines, paging, dialect)
- Injekt, paging-runtime
