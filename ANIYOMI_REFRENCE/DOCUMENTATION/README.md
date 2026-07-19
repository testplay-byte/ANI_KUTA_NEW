# Aniyomi Reference — Documentation

> **Purpose:** A complete, self-contained documentation of the Aniyomi reference
> codebase located at `../ANIYOMI/`. Read these docs to understand **every** part
> of the reference project — what each module does, how the subsystems work, where
> files live, and how the pieces connect — without having to reverse-engineer the
> source yourself.
>
> **Audience:** AI agents and humans working on ANIKUTA who need to study Aniyomi
> as a reference for porting concepts, understanding patterns, or comparing
> approaches.

---

## How to use this documentation

1. **Start with [`00-overview/`](00-overview/)** to understand what Aniyomi is,
   the tech stack, and the module layout.
2. **Then [`01-architecture/`](01-architecture/)** for the cross-cutting patterns
   (DI, navigation, state).
3. **Dive into [`02-modules/`](02-modules/)** for per-module detail (each Gradle
   module has its own file).
4. **Read [`03-subsystems/`](03-subsystems/)** for feature-level deep dives that
   span multiple modules (reader, player, downloads, trackers, sources, etc.).
5. **Refer to [`04-data-models/`](04-data-models/)** for the domain models and DB
   schema.
6. **Follow [`05-key-flows/`](05-key-flows/)** for end-to-end user journeys.
7. **Use [`06-ui/`](06-ui/)** for screens, theme, and components.
8. **Use [`07-reference/`](07-reference/)** as a lookup index (file locator,
   glossary, cross-reference matrix).

Every doc cross-references the actual source files with relative paths like
`../ANIYOMI/app/src/main/java/...` so you can jump between docs and code.

---

## Documentation map

### `00-overview/` — The 10,000-foot view
| File | What it covers |
|---|---|
| [`01-project-overview.md`](00-overview/01-project-overview.md) | What Aniyomi is, its purpose, history, dual manga+anime nature. |
| [`02-tech-stack.md`](00-overview/02-tech-stack.md) | Languages, libraries, versions (Kotlin, Compose, SQLDelight, MPV, etc.). |
| [`03-module-map.md`](00-overview/03-module-map.md) | All Gradle modules, their roles, and dependency graph. |
| [`04-build-system.md`](00-overview/04-build-system.md) | Gradle config, version catalogs, build types, flavors, ABI splits, buildSrc. |
| [`05-project-conventions.md`](00-overview/05-project-conventions.md) | Naming, package layout, code style, the dual manga/anime pattern. |

### `01-architecture/` — Cross-cutting patterns
| File | What it covers |
|---|---|
| [`01-architecture-overview.md`](01-architecture/01-architecture-overview.md) | Layered architecture (domain ← data ← app), dependency rules. |
| [`02-dependency-injection.md`](01-architecture/02-dependency-injection.md) | Injekt (the DI framework), how it's wired. |
| [`03-state-and-async.md`](01-architecture/03-state-and-async.md) | Coroutines, Flow, StateFlow, ViewModels. |
| [`04-navigation.md`](01-architecture/04-navigation.md) | Voyager navigator, screens, tab navigation. |
| [`05-preferences-system.md`](01-architecture/05-preferences-system.md) | The preference mechanism (PreferenceStore/SharedPreferences). |
| [`06-error-handling.md`](01-architecture/06-error-handling.md) | Crash reporting, ACRA, error patterns. |

### `02-modules/` — Per-module deep dives
| File | Module | What it covers |
|---|---|---|
| [`README.md`](02-modules/README.md) | — | Index + dependency graph. |
| [`app.md`](02-modules/app.md) | `:app` | The main application (UI, DI, activities, reader/player). |
| [`core-common.md`](02-modules/core-common.md) | `:core:common` | Shared utilities. |
| [`core-archive.md`](02-modules/core-archive.md) | `:core:archive` | CBZ/CBR/ZIP/RAR archive reading. |
| [`core-metadata.md`](02-modules/core-metadata.md) | `:core-metadata` | Manga/anime metadata parsing. |
| [`data.md`](02-modules/data.md) | `:data` | Database (SQLDelight), repositories. |
| [`domain.md`](02-modules/domain.md) | `:domain` | Pure-Kotlin domain models, use cases, repository interfaces. |
| [`i18n.md`](02-modules/i18n.md) | `:i18n` | Shared localization (Moko Resources). |
| [`i18n-aniyomi.md`](02-modules/i18n-aniyomi.md) | `:i18n-aniyomi` | Aniyomi-specific strings. |
| [`macrobenchmark.md`](02-modules/macrobenchmark.md) | `:macrobenchmark` | Baseline profiles & benchmarks. |
| [`presentation-core.md`](02-modules/presentation-core.md) | `:presentation-core` | Shared Compose theme & components. |
| [`presentation-widget.md`](02-modules/presentation-widget.md) | `:presentation-widget` | Home-screen widgets. |
| [`source-api.md`](02-modules/source-api.md) | `:source-api` | The source/extension contract. |
| [`source-local.md`](02-modules/source-local.md) | `:source-local` | Local-files-as-source. |

### `03-subsystems/` — Feature-level deep dives
| File | Subsystem |
|---|---|
| [`README.md`](03-subsystems/README.md) | Index. |
| [`library-management.md`](03-subsystems/library-management.md) | Library, categories, favorites. |
| [`manga-reader.md`](03-subsystems/manga-reader.md) | The manga reading engine (paged + continuous). |
| [`anime-player.md`](03-subsystems/anime-player.md) | The MPV-based anime player. |
| [`source-system.md`](03-subsystems/source-system.md) | How sources/extensions are loaded & used. |
| [`download-manager.md`](03-subsystems/download-manager.md) | Download queue, providers, storage. |
| [`trackers.md`](03-subsystems/trackers.md) | MAL, AniList, Shikimori, Bangumi tracking. |
| [`backup-restore.md`](03-subsystems/backup-restore.md) | Backup format & restore logic. |
| [`history.md`](03-subsystems/history.md) | Recently-read, resume points. |
| [`updates.md`](03-subsystems/updates.md) | Update checking for library & extensions. |
| [`search-discovery.md`](03-subsystems/search-discovery.md) | Browse, search, global search. |
| [`extensions-update.md`](03-subsystems/extensions-update.md) | Extension repo management & updates. |
| [`torrent-streaming.md`](03-subsystems/torrent-streaming.md) | Torrserver integration for anime. |
| [`notifications.md`](03-subsystems/notifications.md) | Notification channels & triggers. |
| [`storage-and-cache.md`](03-subsystems/storage-and-cache.md) | SAF, cache, disk layout. |
| [`updater.md`](03-subsystems/updater.md) | App self-update mechanism. |

### `04-data-models/` — The data layer
| File | What it covers |
|---|---|
| [`README.md`](04-data-models/README.md) | Index. |
| [`domain-models.md`](04-data-models/domain-models.md) | Manga, Anime, Chapter, Episode, Track, Category, History, etc. |
| [`database-schema.md`](04-data-models/database-schema.md) | SQLDelight `.sq` files, tables, views, migrations. |
| [`preferences-catalog.md`](04-data-models/preferences-catalog.md) | All preference keys & their meaning. |

### `05-key-flows/` — End-to-end user journeys
| File | Flow |
|---|---|
| [`README.md`](05-key-flows/README.md) | Index. |
| [`app-startup.md`](05-key-flows/app-startup.md) | From app launch to home screen. |
| [`browse-catalog.md`](05-key-flows/browse-catalog.md) | Browsing a source's catalog. |
| [`read-manga.md`](05-key-flows/read-manga.md) | Opening & reading a manga chapter. |
| [`watch-anime.md`](05-key-flows/watch-anime.md) | Opening & watching an anime episode. |
| [`add-to-library.md`](05-key-flows/add-to-library.md) | Adding a manga/anime to the library. |
| [`download-chapter.md`](05-key-flows/download-chapter.md) | Downloading a chapter/episode. |
| [`track-progress.md`](05-key-flows/track-progress.md) | Syncing progress to a tracker. |
| [`backup-flow.md`](05-key-flows/backup-flow.md) | Creating & restoring backups. |

### `06-ui/` — Presentation layer
| File | What it covers |
|---|---|
| [`README.md`](06-ui/README.md) | Index. |
| [`theme-design.md`](06-ui/theme-design.md) | Material 3, color, typography, dark mode. |
| [`screens.md`](06-ui/screens.md) | Catalog of all screens & their ViewModels. |
| [`components.md`](06-ui/components.md) | Reusable Compose components. |
| [`compose-migration.md`](06-ui/compose-migration.md) | The ongoing Views → Compose migration. |

### `07-reference/` — Lookup indexes
| File | What it covers |
|---|---|
| [`README.md`](07-reference/README.md) | Index. |
| [`file-index.md`](07-reference/file-index.md) | "Where do I find X?" — key file locator. |
| [`glossary.md`](07-reference/glossary.md) | Terms (source, extension, entry, chapter, episode, tracker, etc.). |
| [`cross-reference-matrix.md`](07-reference/cross-reference-matrix.md) | Subsystem × Module × Key-file matrix. |

---

## Conventions used in these docs

- **Source paths** are always relative to `ANIYOMI_REFRENCE/ANIYOMI/` and written
  like `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/App.kt` (relative to this
  DOCUMENTATION folder).
- **Cross-links** between docs use relative markdown links.
- **Diagrams** are ASCII art for portability.
- **"See also"** sections point to related docs and source files.

## Status

This documentation was produced by analyzing the Aniyomi source snapshot at
`../ANIYOMI/` (branch `main`, see `../README.md` for the snapshot date). It
reflects that snapshot; upstream Aniyomi may have evolved since.
