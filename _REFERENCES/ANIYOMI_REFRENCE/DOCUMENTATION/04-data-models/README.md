# 04-data-models / README — The Data Layer

> Field-level reference for the three things that together define Aniyomi's data
> layer: the **domain models** (pure Kotlin `data class`es), the **SQLDelight
> database schema** (two parallel `.db` files), and the **preference keys**
> (everything stored in `SharedPreferences`). This folder is the lookup catalog
> — for the module-level narrative, see
> [`../02-modules/domain.md`](../02-modules/domain.md) and
> [`../02-modules/data.md`](../02-modules/data.md).

## Files in this folder

| File | What it covers |
|---|---|
| [`domain-models.md`](domain-models.md) | Every domain model class with its fields, types, and purpose. Covers `Manga`/`Anime`, `Chapter`/`Episode`, `Page`/`Video`, `MangaTrack`/`AnimeTrack`, `Category`, `History`, `Updates`, `Source`/`AnimeSource`, `SeasonAnime`, `CustomButton`, `ExtensionRepo`, the source-API transport models (`SManga`/`SAnime`/`SChapter`/`SEpisode`/`Page`/`Video`/`Hoster`), and the small support types (`Pin`/`Pins`, `TriState`, `LibraryDisplayMode`, the sort modes, etc.). |
| [`database-schema.md`](database-schema.md) | Every SQLDelight table and view in both the manga schema (`sqldelight/`) and the anime schema (`sqldelightanime/`), with columns, types, key constraints, indexes, triggers, and the migration folders (`1..32` for manga, `113..135` for anime). |
| [`preferences-catalog.md`](preferences-catalog.md) | The major `*Preferences` classes (`LibraryPreferences`, `ReaderPreferences`, `PlayerPreferences` + the five other player pref classes, `DownloadPreferences`, `SourcePreferences`, `TrackPreferences`, `BackupPreferences`, `UiPreferences`, `BasePreferences`, `StoragePreferences`, `NetworkPreferences`, `SecurityPreferences`, `TorrentPreferences`) with the key preference methods, defaults, and meaning. |

## The dual manga/anime pattern

Aniyomi's defining design choice is that **almost every concept exists twice** —
once for manga (inherited from Tachiyomi/Mihon) and once for anime (Aniyomi's
addition). This pattern runs through all three reference files in this folder:

```
domain-models               database-schema             preferences-catalog
─────────────               ───────────────             ───────────────────
Manga          ↔  Anime     mangas.sq      ↔  animes.sq     (Library/Reader
Chapter        ↔  Episode   chapters.sq    ↔  episodes.sq    prefs cover both
MangaTrack     ↔  AnimeTrack manga_sync.sq ↔  anime_sync.sq  sides in single
MangaHistory   ↔  AnimeHistory history.sq  ↔  animehistory.sq classes; some
MangaUpdatesWithRelations                     updatesView.sq ↔ animeupdatesView.sq have separate
              ↔  AnimeUpdatesWithRelations                  keys per side)
Category       (shared)     categories.sq  ↔  categories.sq
                            (same shape; 2 physical tables)

source-API transport:      Sources & extension repos:
SManga ↔ SAnime            sources.sq      ↔  animesources.sq
SChapter ↔ SEpisode        extension_repos.sq (manga) ↔ extension_repos.sq (anime)
Page ↔ Video               excluded_scanlators.sq (manga only)
                           custom_buttons.sq (anime only)
```

The **anime side adds** the *seasons* concept (an anime may have child
"season" rows under a parent anime via `parent_id` — no manga analogue),
the *fillermark* flag (marking filler episodes), the *background image*
URL+last-modified columns, the *fetch type* (`Episodes` vs `Seasons`
mode), and the *custom Lua buttons* feature for the MPV player.

The **manga side adds** the *excluded scanlators* feature (per-manga
scanlator filtering — anime episodes don't carry scanlator filtering at
the DB level).

## How to use these docs

- **Finding a field's meaning?** → `domain-models.md`.
- **Finding which SQL column backs a domain field?** → `database-schema.md`
  (and `02-modules/data.md` for the mapper layer in between).
- **Finding where a preference is stored?** → `preferences-catalog.md`.
- **Finding the repository interface for a model?** →
  [`../02-modules/domain.md`](../02-modules/domain.md).
- **Finding the repository implementation?** →
  [`../02-modules/data.md`](../02-modules/data.md).

## See also

- [`../02-modules/domain.md`](../02-modules/domain.md) — narrative description
  of the `:domain` module (models, repository interfaces, interactors).
- [`../02-modules/data.md`](../02-modules/data.md) — narrative description of
  the `:data` module (two parallel SQLDelight databases, handlers, mappers).
- [`../02-modules/source-api.md`](../02-modules/source-api.md) — the source
  contract that the `SManga`/`SAnime`/`SChapter`/`SEpisode`/`Page`/`Video`
  transport models belong to.
- [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md)
  — how the `PreferenceStore` abstraction works (this folder lists the *keys*,
  that doc explains the *mechanism*).
- [`../01-architecture/01-architecture-overview.md`](../01-architecture/01-architecture-overview.md)
  — how the domain/data layers fit in the layered architecture.
- [`../00-overview/05-project-conventions.md`](../00-overview/05-project-conventions.md)
  — the dual manga/anime pattern at the project level.
