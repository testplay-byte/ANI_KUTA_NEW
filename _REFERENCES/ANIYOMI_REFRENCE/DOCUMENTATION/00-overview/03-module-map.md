# 00-overview / 03 — Module Map

> Aniyomi is a multi-module Gradle project. Modules are declared in
> `../ANIYOMI/settings.gradle.kts`. This doc lists every module, its role, its
> key source roots, and the dependency graph between modules.

## Modules at a glance

| Module | Path | Role | Package roots |
|---|---|---|---|
| `:app` | `../ANIYOMI/app/` | The Android application: UI, DI wiring, activities, reader, player, extension loader. | `eu.kanade.tachiyomi`, `mihon.*`, `aniyomi.*` |
| `:core:common` | `../ANIYOMI/core/common/` | Shared utilities (date, file, system, logging helpers). | `tachiyomi.core` |
| `:core:archive` | `../ANIYOMI/core/archive/` | Archive reading (CBZ/CBR/ZIP/RAR) via libarchive. | `tachiyomi.core.archive` |
| `:core-metadata` | `../ANIYOMI/core-metadata/` | Manga/anime metadata parsing. | `tachiyomi.core.metadata` |
| `:data` | `../ANIYOMI/data/` | Database (SQLDelight) + repository implementations. | `tachiyomi.data`, `mihon.data` |
| `:domain` | `../ANIYOMI/domain/` | Pure-Kotlin domain models, use cases, repository interfaces. | `tachiyomi.domain`, `mihon.domain`, `aniyomi.domain` |
| `:i18n` | `../ANIYOMI/i18n/` | Shared string resources (Moko Resources, from Mihon). | `mihon.i18n` |
| `:i18n-aniyomi` | `../ANIYOMI/i18n-aniyomi/` | Aniyomi-specific string resources. | `aniyomi.i18n` |
| `:macrobenchmark` | `../ANIYOMI/macrobenchmark/` | Macrobenchmarks + baseline profile generation. | `mihon.benchmark` |
| `:presentation-core` | `../ANIYOMI/presentation-core/` | Shared Compose theme & primitives. | `mihon.core.designsystem` (+ theme) |
| `:presentation-widget` | `../ANIYOMI/presentation-widget/` | Home-screen widgets. | `eu.kanade.tachiyomi.widget` (+) |
| `:source-api` | `../ANIYOMI/source-api/` | The source/extension contract (KMP: commonMain + androidMain). | `eu.kanade.tachiyomi.source`, `...animesource` |
| `:source-local` | `../ANIYOMI/source-local/` | Built-in source that reads local files as a library. | `tachiyomi.source.local` |

## Dependency graph (module-level)

```
                       ┌──────────────┐
                       │   :app       │  ← UI, DI, activities, reader, player
                       └──────┬───────┘
          ┌───────────────────┼───────────────────────┐
          ▼                   ▼                       ▼
   ┌────────────┐     ┌────────────┐          ┌─────────────────┐
   │  :data     │     │  :domain   │          │ :presentation-* │
   └─────┬──────┘     └─────▲──────┘          └─────────────────┘
         │                  │
         │  implements      │  (pure Kotlin, no Android UI)
         ▼                  │
   ┌────────────┐     ┌─────────────────┐
   │ :core:*    │◀────│  :source-api    │  (KMP contract)
   │ :core-md   │     └─────────────────┘
   └─────┬──────┘             ▲
         │                    │  implements
         ▼                    │
   ┌────────────┐       ┌─────────────────┐
   │ (libs)     │       │ :source-local   │
   └────────────┘       └─────────────────┘

   :i18n  :i18n-aniyomi   ← consumed by :app (and indirectly by modules via accessor)
   :macrobenchmark        ← depends on :app (benchmark target)
```

Key dependency rules (enforced by the layered architecture):

- **`:domain` depends on nothing Android-UI.** It is pure Kotlin (models, use
  cases, repository *interfaces*). This is the innermost ring.
- **`:data` implements `:domain`'s repository interfaces** using SQLDelight.
  `:data` depends on `:domain` and `:core:common`.
- **`:app` depends on everything** and wires it together.
- **`:source-api` is KMP** (`commonMain` + `androidMain`); `:source-local`
  implements it for local files. Extensions (external APKs) implement it too.
- **`:presentation-core`** holds shared Compose theme/components used by `:app`.
- **`:presentation-widget`** is the home-screen widget, depends on `:app`/`:domain`.

## Module sizes (approximate, from the snapshot)

| Module | Rough file count | Notes |
|---|---|---|
| `:app` | ~1400+ source files | The vast majority of the codebase. |
| `:data` | ~150 files + ~40 `.sq` schema files | Two parallel schemas (manga + anime). |
| `:domain` | ~200 files | Heavy because of the manga/anime duplication. |
| `:source-api` | ~40 files | Small but central — the extension contract. |
| `:core:common` | ~80 files | |
| `:core:archive` | ~10 files | |
| `:core-metadata` | ~10 files | |
| `:source-local` | ~20 files | |
| `:i18n` / `:i18n-aniyomi` | string XML per locale (~50+ locales) | |
| `:presentation-core` | ~30 files | |
| `:presentation-widget` | ~10 files | |
| `:macrobenchmark` | ~10 files | |

## The dual manga/anime pattern (cross-cutting)

Almost every module and subsystem is **duplicated** for manga vs anime. This is
visible in:

- Domain: `tachiyomi.domain.items.chapter` ↔ `tachiyomi.domain.items.episode`,
  `...source.manga` ↔ `...source.anime`, `...entries.manga` ↔ `...entries.anime`,
  `...track.manga` ↔ `...track.anime`, `...download.manga` ↔ `...download.anime`.
- Data: `data/src/main/sqldelight/` (manga) ↔ `.../sqldelightanime/` (anime),
  `tachiyomi.data.source.manga` ↔ `...anime`, etc.
- App UI: `ui/reader` (manga) ↔ `ui/player` (anime), `ui/library` shows both.
- Source API: `eu.kanade.tachiyomi.source` (manga) ↔ `...animesource` (anime).

When studying any subsystem, expect a manga file and a parallel anime file. The
anime side is Aniyomi's addition; the manga side is inherited from Mihon/Tachiyomi.

## See also

- [`04-build-system.md`](04-build-system.md) — how these modules are built.
- `../../02-modules/` — per-module deep dives.
- `../../07-reference/cross-reference-matrix.md` — module × subsystem matrix.
