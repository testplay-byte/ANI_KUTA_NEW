# 02-modules / README — Module Index

> Per-module deep dives. Aniyomi is a 13-module Gradle project (declared in
> `../ANIYOMI/settings.gradle.kts`). Each module has its own file in this folder;
> this README is the index, the dependency graph, and a guide to the dual
> manga/anime pattern as it appears module-by-module.

## Module list

| File | Module | One-line description |
|---|---|---|
| [`app.md`](app.md) | `:app` | The Android application: UI, DI wiring, activities, manga reader, anime player, extension loader. |
| [`core-common.md`](core-common.md) | `:core:common` | Shared utilities (date, file, system, logging) under package root `tachiyomi.core`. |
| [`core-archive.md`](core-archive.md) | `:core:archive` | CBZ/CBR/ZIP/RAR archive reading via libarchive. |
| [`core-metadata.md`](core-metadata.md) | `:core-metadata` | Manga/anime metadata parsing (ComicInfo.xml, NFO, etc.). |
| [`data.md`](data.md) | `:data` | SQLDelight database + repository implementations (two parallel schemas: manga + anime). |
| [`domain.md`](domain.md) | `:domain` | Pure-Kotlin domain models, use cases, and repository *interfaces*. |
| [`i18n.md`](i18n.md) | `:i18n` | Shared string resources via Moko Resources (inherited from Mihon). |
| [`i18n-aniyomi.md`](i18n-aniyomi.md) | `:i18n-aniyomi` | Aniyomi-specific strings layered on top of `:i18n` (player, anime UI). |
| [`macrobenchmark.md`](macrobenchmark.md) | `:macrobenchmark` | Macrobenchmarks + baseline-profile generation for app startup. |
| [`presentation-core.md`](presentation-core.md) | `:presentation-core` | Shared Compose theme & primitives (`mihon.core.designsystem`). |
| [`presentation-widget.md`](presentation-widget.md) | `:presentation-widget` | Home-screen Glance widgets. |
| [`source-api.md`](source-api.md) | `:source-api` | KMP source/extension contract (`commonMain` + `androidMain`); manga and anime hierarchies. |
| [`source-local.md`](source-local.md) | `:source-local` | Built-in source that reads local files as a library. |

## Module dependency graph

Derived from the `implementation(projects.*)` / `api(projects.*)` declarations in
each module's `build.gradle.kts`. Arrows point from a module to the modules it
depends on.

```
                                  ┌──────────────────────────────┐
                                  │           :app               │
                                  │ (UI, DI, reader, player,     │
                                  │  extension loader)           │
                                  └──┬────────┬────────┬─────────┘
                  ┌──────────────────┤        │        │
                  ▼                  ▼        ▼        ▼
            ┌──────────┐      ┌──────────┐ ┌──────────┐ ┌────────────────────┐
            │  :data   │      │ :domain  │ │ :source- │ │ :presentation-core │
            └──┬───┬───┘      └──┬───┬───┘ │  local   │ └─────────┬──────────┘
               │   │             │   │     └──┬──┬────┘           │
               │   │             │   │        │  │                ▼
               │   └─────────────┘   │        │  │         ┌────────────────────┐
               │         │           │        │  │         │ :presentation-widget│
               ▼         ▼           │        │  │         └─────────┬──────────┘
        ┌─────────────────────┐      │        │  │                   │
        │   :core:common      │◀─────┼────────┴──┴───────────────────┤
        └──────────┬──────────┘      │                             │
                   │ api             │                             │ api
                   ▼                 │                             ▼
              ┌─────────┐            │                       ┌─────────────────────┐
              │  :i18n  │            └─── :core-metadata ───▶│  :i18n-aniyomi      │
              └─────────┘                       │             └─────────────────────┘
                                                │
                                                ▼
                                          ┌─────────────┐
                                          │ :source-api │  (KMP: commonMain + androidMain)
                                          └──────┬──────┘
                                                 │
                                                 ▼
                                          ┌─────────────┐
                                          │ :core:common│
                                          └─────────────┘

          ┌──────────────┐
          │ :core:archive│  (standalone — wraps libarchive; no project deps)
          └──────────────┘

          ┌───────────────────┐
          │ :macrobenchmark   │  ─── targetProjectPath ──▶  :app
          │ (com.android.test)│
          └───────────────────┘
```

Key dependency edges (verified from each `build.gradle.kts`):

| Module | Depends on |
|---|---|
| `:app` | `:i18n`, `:i18n-aniyomi`, `:core:archive`, `:core:common`, `:core-metadata`, `:source-api`, `:source-local`, `:data`, `:domain`, `:presentation-core`, `:presentation-widget` |
| `:data` | `:source-api`, `:domain`, `:core:common` |
| `:domain` | `:source-api`, `:core:common` |
| `:core:common` | `:i18n` |
| `:core:archive` | _no project deps_ |
| `:core-metadata` | `:source-api` |
| `:source-api` | `:core:common` |
| `:source-local` | `:source-api`, `:i18n`, `:i18n-aniyomi`, `:core:archive`, `:core:common`, `:core-metadata`, `:domain` |
| `:presentation-core` | `:core:common` (api), `:i18n` (api) |
| `:presentation-widget` | `:core:common`, `:domain`, `:presentation-core`, `:i18n` (api), `:i18n-aniyomi` (api) |
| `:i18n`, `:i18n-aniyomi` | _no project deps_ (only Moko Resources) |
| `:macrobenchmark` | `:app` (via `targetProjectPath`) |

Dependency-rule summary (enforced structurally — see
[`../01-architecture/01-architecture-overview.md`](../01-architecture/01-architecture-overview.md)):

- **`:domain`** is the innermost ring: pure Kotlin, depends only on `:core:common`
  and the `:source-api` contract.
- **`:data`** implements `:domain`'s repository interfaces against SQLDelight.
- **`:app`** is the composition root and depends on everything.
- **`:i18n`** and **`:i18n-aniyomi`** are leaf resource modules — they have no
  project dependencies and are pulled in transitively by several UI-adjacent
  modules through `api(...)`.
- **`:macrobenchmark`** is a `com.android.test` module that does **not** depend
  on `:app` at compile time — it targets `:app` via `targetProjectPath` so it can
  instrument the benchmark build type.

## The dual manga/anime pattern across modules

Almost every module is **duplicated** for manga vs anime. The manga side is
inherited from Mihon/Tachiyomi; the anime side is Aniyomi's addition. Where to
look in each module:

| Module | Manga side | Anime side |
|---|---|---|
| `:domain` | `tachiyomi.domain.items.chapter`, `...source.manga`, `...entries.manga`, `...track.manga`, `...download.manga` | `tachiyomi.domain.items.episode`, `...source.anime`, `...entries.anime`, `...track.anime`, `...download.anime` |
| `:data` | `data/src/main/sqldelight/` (`.sq` files) | `data/src/main/sqldelightanime/` (parallel `.sq` files) |
| `:app` UI | `ui/reader/` (`ReaderActivity`, `ReaderViewModel`, paged/continuous viewers) | `ui/player/` (`PlayerActivity`, `PlayerViewModel`, MPV-based `AniyomiMPVView`) |
| `:app` data | `data/download/manga/`, `data/track/manga/` | `data/download/anime/`, `data/track/anime/` |
| `:source-api` | `eu.kanade.tachiyomi.source` (manga contract: `MangaSource`, `Chapter`, etc.) | `eu.kanade.tachiyomi.animesource` (anime contract: `AnimeSource`, `Episode`, etc.) |
| `:i18n` + `:i18n-aniyomi` | Generic manga/library strings in `:i18n` | Player/anime-only strings in `:i18n-aniyomi` (see [`i18n-aniyomi.md`](i18n-aniyomi.md)) |

When studying any subsystem, expect a manga file and a parallel anime file. This
dual pattern is the single most important architectural fact in the codebase. See
[`../00-overview/05-project-conventions.md`](../00-overview/05-project-conventions.md)
for the full naming conventions.

## Where to go next

- **Per-module detail** — each file in this folder (table above).
- **Cross-module features** (reader, player, downloads, trackers, sources, etc.)
  live in [`../03-subsystems/`](../03-subsystems/). When a feature spans multiple
  modules — e.g. "watch anime" touches `:app/ui/player`, `:domain`, `:data`,
  `:source-api`, and `:i18n-aniyomi` — the subsystem doc is the place that ties
  them together; the module docs here stay scoped to one module each.
- **Data schemas and domain models** — see [`../04-data-models/`](../04-data-models/).
- **Module × subsystem matrix** — see
  [`../07-reference/cross-reference-matrix.md`](../07-reference/cross-reference-matrix.md).

## See also

- [`../00-overview/03-module-map.md`](../00-overview/03-module-map.md) — the
  higher-level module map (this doc is the more detailed companion).
- [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) — how
  these modules are built (build types, flavors, ABI splits, `buildSrc` plugins).
- [`../01-architecture/01-architecture-overview.md`](../01-architecture/01-architecture-overview.md)
  — the layered-architecture rule that governs the dependency direction.
- [`../00-overview/05-project-conventions.md`](../00-overview/05-project-conventions.md)
  — naming conventions including the manga/anime duality.
