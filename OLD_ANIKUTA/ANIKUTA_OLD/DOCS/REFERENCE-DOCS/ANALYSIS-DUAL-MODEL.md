# Analysis: The Dual Anime/Manga Model

> Are aniyomi's anime and manga sides linked together? Can they be separated?
> This doc answers both, based on a verified read of the `REFERENCE/` source
> (commit `2f5cf77`) and the per-subsystem docs in `SUBSYSTEMS/`.
>
> **Bottom line:** anime and manga are **two parallel, nearly-independent
> stacks**. They share only the theme, DI framework, navigation shell, and a
> few core utilities. Code-level coupling is **minimal and one-directional**.
> Anime-only is mechanically achievable; re-adding manga later is also
> mechanical.

---

## 1. Why there are two models

aniyomi is forked from **Mihon** (manga-only). aniyomi's big addition was a
**parallel anime stack** — anime sources, anime DB, anime domain, anime
player, anime downloads, anime trackers — built alongside the existing manga
stack rather than replacing it. The result is that almost every layer has an
anime copy and a manga copy.

This is duplication, not coupling. The two copies rarely talk to each other.

---

## 2. The duplication, layer by layer

| Layer | Manga side | Anime side | Shared? |
|-------|-----------|------------|---------|
| Source contract | `eu.kanade.tachiyomi.source` (`MangaSource`, `HttpSource`, `SManga`, `SChapter`, `Page`) | `eu.kanade.tachiyomi.animesource` (`AnimeSource`, `AnimeHttpSource`, `SAnime`, `SEpisode`, `Video`) | **No shared base.** Parallel package trees. Even `Filter`/`AnimeFilter`, `UpdateStrategy`/`AnimeUpdateStrategy` are line-for-line copies. |
| Source manager | `AndroidMangaSourceManager` | `AndroidAnimeSourceManager` | Separate singletons. |
| Extension loader | `MangaExtensionManager`/`MangaExtensionLoader` | `AnimeExtensionManager`/`AnimeExtensionLoader` | Separate. Different APK feature flags (`tachiyomi.extension` vs `tachiyomi.animeextension`) + lib version ranges (manga 1.4–1.5, anime 12–16) make cross-loading impossible. |
| Domain | `tachiyomi.domain.*` | `aniyomi.domain.anime.*` | Sibling packages. |
| Database | `sqldelight/` → `tachiyomi.db` (`Database`) | `sqldelightanime/` → `tachiyomi.animedb` (`AnimeDatabase`) | **Fully separate SQLite files**, separate drivers, separate handlers (`MangaDatabaseHandler`/`AnimeDatabaseHandler`, no shared base). Even byte-identical files (`categories.sq`) are independent copies. |
| Repositories | `tachiyomi.data.*` (manga repos) | `tachiyomi.mi.data.*` + anime repos | Sibling packages, **no cross-imports**. |
| Download manager | `MangaDownloadManager` (+ Downloader/Provider/Cache/Job/Store) | `AnimeDownloadManager` (+ parallel set) | **No shared base/interface.** Parallel hierarchies, 6 separate DI singletons. |
| Player / Reader | `ui/reader/` (manga reader) | `ui/player/` (anime player) | Separate. Player uses MPV; reader uses Coil/paged viewers. |
| Trackers | `MangaTracker` interface; 4 manga-only services (Komga, MangaUpdates, Kavita, Suwayomi) | `AnimeTracker` interface; 2 anime-only (Simkl,, Jellyfin); 5 dual (MAL, AniList, Kitsu, Shikimori, Bangumi) | Common `Tracker`/`BaseTracker` base. Dual-support trackers implement BOTH interfaces. DB tables split (`anime_sync`/`manga_sync`). |
| Backup | `BackupManga` (proto fields 1–106) | `BackupAnime` (proto fields 500–507) | **Unified container, split fields.** One `Backup` protobuf holds both; 500-offset is commented "Aniyomi specific" so Mihon ignores anime fields. Single `BackupCreator`/`BackupRestorer` handle both. |
| UI screens | `presentation/<feature>/manga/` | `presentation/<feature>/anime/` | Screens split per-package. **Theme + design system shared.** |
| i18n | `:i18n` (Mihon strings) | `:i18n-aniyomi` (anime strings) | Separate modules. |

---

## 3. What is actually shared (the coupling surface)

This is the small set of things that genuinely serve both anime and manga:

1. **Theme & design system** — `presentation/theme/`, `mihon/core/designsystem/`, `:presentation-core`. Material 3, light/dark/AMOLED, Monet. Fully shared. ✅ reusable as-is.
2. **DI framework** — Injekt (4 modules: `AppModule`, `PreferenceModule`, `DomainModule`, `SYDomainModule`). Bindings are parallel per side, but the framework + wiring pattern is shared. ✅ reusable.
3. **Navigation shell** — Voyager `Navigator` in `MainActivity`; the `Screen`/`ScreenModel`/composable trio pattern. Shared. ✅ reusable.
4. **Core utilities** — `:core:common` (preferences, storage, i18n bridge, system/image utils), `:core:archive`, `:core-metadata`. Shared by both sides. ✅ reusable.
5. **Cross-cutting UI feeds** — these MIX anime + manga data:
   - **Library** screen shows both anime + manga entries (categories can hold both).
   - **History** feed shows both anime + manga watch/read history.
   - **Updates** feed shows both.
   - **Stats** / **Storage** aggregate both.
   - These are the few places where anime + manga genuinely meet in code.
6. **Backup container** — one protobuf holds both (see §2).

### The 2 trivial one-directional couplings (player ↔ reader)

- `PlayerViewModel` imports `SaveImageNotifier` from `ui.reader` (used for screenshot notifications).
- `PlayerViewModel` imports the manga `isRecognizedNumber` extension.

Both are **utility-level**, relocatable to a shared package, and create **no architectural tie**. The reader imports nothing from the player.

---

## 4. Can they be separated? — Yes, cleanly.

### Anime-only extraction (drop manga)

Mechanical, per subsystem. Delete the manga half + prune DI bindings:

| Subsystem | What to delete |
|-----------|----------------|
| `:source-api` | `eu.kanade.tachiyomi.source` package (keep `animesource`). |
| `:app` source mgmt | `source/manga/`, `extension/manga/`. |
| `:data` | `src/main/sqldelight/` (manga DB), the `create("Database")` block in `data/build.gradle.kts`, `handlers/manga`, all `*.manga` repo packages. Keep `DatabaseAdapter.kt` (shared). |
| `:domain` | the manga half (`tachiyomi.domain.*` manga-only parts). Keep `aniyomi.domain.anime.*`. |
| `:app` download | `data/download/manga/`, 3 manga DI registrations. |
| `:app` trackers | 4 manga-only trackers; the manga side of the 5 dual trackers. |
| `:app` reader | `ui/reader/` + `presentation/reader/` wholesale (relocate `SaveImageNotifier` + `isRecognizedNumber` to a shared util first). |
| `:app` backup | Keep the unified `Backup` proto but emit empty manga fields (forward-compat). Or drop `BackupManga`. |
| `:app` UI feeds | Library/History/Updates/Stats/Storage — simplify to anime-only (remove the manga branches). |
| `:i18n` | Can drop `:i18n` (Mihon strings) if no manga UI references remain; keep `:i18n-aniyomi`. Verify. |
| DI (`AppModule`/`DomainModule`/`SYDomainModule`) | Remove the ~8 manga concept rows (DB, source mgr, extension mgr, download trio, cover cache, local-source fs, delayed-tracking store, manga repos, manga interactors). `SYDomainModule` is manga-only → drop entirely. |

**Result:** an anime-only app that compiles. The anime side is **unchanged** — it doesn't reference manga code, so nothing breaks.

**Effort estimate:** moderate. Mostly deletion + DI pruning + simplifying the 5 cross-cutting feeds. No rewrites of anime logic.

### Add manga later (re-add the manga half)

Also mechanical, in reverse:

1. Re-copy the manga packages from a fresh aniyomi snapshot into the working app.
2. Re-add the manga DI bindings.
3. Re-add the manga branches in the 5 cross-cutting feeds.
4. Re-add `:i18n` if dropped.
5. Re-add the manga-only trackers + the manga side of dual trackers.

**Caveat:** the longer we diverge from aniyomi's structure, the more merge work re-adding manga becomes. If we keep our package layout close to aniyomi's, re-adding is near-trivial. If we restructure heavily, it's a manual port. (See `DECISIONS-ANALYSIS.md` Decision 1.)

---

## 5. What this means for an anime-first build

- **Anime-first is low-risk.** The anime stack is self-contained; dropping manga won't destabilize it.
- **The shared layer (theme, DI, nav, core utils) is the valuable reuse.** We keep it regardless.
- **The 5 cross-cutting feeds are the only real surgery** for anime-only — they currently mix both, so we simplify them to anime-only.
- **Forward-compat is cheap** if we keep the backup proto's manga field numbers reserved (so a future manga re-add reads old backups).
- **Re-adding manga later is feasible** — the cleaner our divergence from aniyomi, the easier. (This is the key input to Decision 1.)

---

## 6. Verification

All claims in §2–§4 were spot-checked against the `REFERENCE/` source:
- `animesource` package exists in `source-api/src/commonMain/` + `androidMain/`. ✅
- `AnimeSource.kt` + `MangaSource.kt` are separate top-level interfaces. ✅
- `data/src/main/` has both `sqldelight/` + `sqldelightanime/`; `data/build.gradle.kts` has `create("Database")` + `create("AnimeDatabase")`. ✅
- `AnimeTracker.kt` + `MangaTracker.kt` are separate interfaces in `data/track/`. ✅
- `data/download/anime/` + `data/download/manga/` are separate sub-packages. ✅
- DI is Injekt (`uy.kohesive.injekt` imports in `App.kt`). ✅

Per-subsystem depth is in `SUBSYSTEMS/*.md`.
