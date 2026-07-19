# aniyomi Modules

> The 13 Gradle modules that make up aniyomi, what each one does, and how
> they depend on each other. Based on the snapshot in `REFERENCE/`
> (commit `2f5cf77`, 2025-11-05).
>
> Read this together with `ARCHITECTURE.md` (the dependency graph + data flow)
> and `APP-STRUCTURE.md` (the `app/` module's internal packages).

---

## Quick summary

aniyomi is a **multi-module Gradle project** using clean-architecture-style
layering. The layers, from bottom (foundational) to top (app):

```
i18n / i18n-aniyomi   ← translations (no code deps)
        ↑
core/common            ← shared utilities, preferences, storage
        ↑
source-api             ← the source/extension contract (Kotlin Multiplatform)
        ↑
domain                 ← use cases + domain models (no Android UI)
        ↑
data                   ← SQLDelight database, repositories (implements domain)
        ↑
presentation-core / presentation-widget / core-metadata / core/archive / source-local
        ↑
app                    ← the actual Android application (952 kt files)
```

`macrobenchmark` is a standalone performance-testing module.

---

## Module dependency table

> `→ deps` = modules this module depends on.
> `← used by` = modules that depend on this one.

| # | Module | Path | kt files | → Depends on | ← Used by |
|---|--------|------|----------|--------------|-----------|
| 1 | `:app` | `app/` | 952 | i18n, i18n-aniyomi, core:archive, core:common, core-metadata, source-api, source-local, data, domain, presentation-core, presentation-widget | — (top) |
| 2 | `:data` | `data/` | 40 | source-api, domain, core:common | app |
| 3 | `:domain` | `domain/` | 200 | source-api, core:common | data, app, presentation-widget |
| 4 | `:core:common` | `core/common/` | 46 | i18n | source-api, domain, data, core:archive?, source-local, presentation-widget, app |
| 5 | `:core:archive` | `core/archive/` | 6 | (none internal) | app |
| 6 | `:core-metadata` | `core-metadata/` | 5 | source-api | app |
| 7 | `:source-api` | `source-api/` | 46 | core:common | domain, data, core-metadata, source-local, app |
| 8 | `:source-local` | `source-local/` | 23 | source-api, i18n, core:archive, core:common, core-metadata, domain | app |
| 9 | `:i18n` | `i18n/` | 0 (resources only) | (none) | core:common, source-local, presentation-widget, app |
| 10 | `:i18n-aniyomi` | `i18n-aniyomi/` | 0 (resources only) | (none) | app |
| 11 | `:presentation-core` | `presentation-core/` | 47 | (none internal) | app, presentation-widget |
| 12 | `:presentation-widget` | `presentation-widget/` | 19 | core:common, domain, presentation-core, i18n | app |
| 13 | `:macrobenchmark` | `macrobenchmark/` | 2 | (none internal — benchmarks the app) | — (standalone) |

---

## Detailed module profiles

### 1. `:app` — the Android application
- **Path:** `REFERENCE/app/`
- **Files:** 952 Kotlin files (the bulk of the project).
- **Purpose:** The actual Android app — entry point, all screens, DI glue,
  extension loading, player, reader, download manager, trackers, settings.
- **Depends on:** almost every other module (11 of 12 others).
- **Used by:** nothing (it's the top of the dependency tree).
- **Key internal packages** (see `APP-STRUCTURE.md` for the full tree):
  - `eu.kanade.tachiyomi.ui.*` — 20 screen packages (library, entries, player,
    reader, downloads, history, settings, etc.).
  - `eu.kanade.presentation.*` — Compose UI for each screen.
  - `eu.kanade.tachiyomi.data.*` — download, backup, cache, track, notifications.
  - `eu.kanade.tachiyomi.source.*` / `extension.*` — anime + manga source mgmt.
  - `mihon.*` — Mihon-ecosystem features (migration, design system, upcoming).
- **Notes:** This is where ~73% of the code lives. Our working `app/` will
  be built on top of the patterns established here.

### 2. `:data` — data layer (database + repositories)
- **Path:** `REFERENCE/data/`
- **Files:** 40 Kotlin + SQLDelight `.sq` files.
- **Purpose:** Implements the repository interfaces declared in `:domain`.
  Holds the SQLDelight database schema, migrations, views, and the concrete
  repository classes.
- **Depends on:** `:source-api`, `:domain`, `:core:common`.
- **Used by:** `:app`.
- **Key contents:**
  - `src/main/sqldelight/` — manga database schema + migrations.
  - `src/main/sqldelightanime/` — **anime** database schema + migrations
    (aniyomi's addition over Mihon).
  - `tachiyomi.data.*` / `mihon.data.repository.*` — repository implementations
    (category, entries, history, source, track, updates, release, items).
- **Notes:** Two parallel DB schemas (manga + anime) — aniyomi's signature
  extension over the manga-only Mihon. Any data-layer work must respect both.

### 3. `:domain` — domain layer (models + use cases)
- **Path:** `REFERENCE/domain/`
- **Files:** 200 Kotlin files.
- **Purpose:** Pure-Kotlin domain models and use cases. No Android UI, no
  database — just the business-logic contracts. Repositories are declared
  here as interfaces (implemented by `:data`).
- **Depends on:** `:source-api`, `:core:common`.
- **Used by:** `:data`, `:app`, `:presentation-widget`.
- **Key sub-packages:**
  - `tachiyomi.domain.*` — backup, category, download, entries, history,
    library, release, source, storage, track, updates, items, custombuttons.
  - `aniyomi.domain.anime` — anime-specific domain models.
  - `mihon.domain.*` — extension-repo, items, upcoming.
- **Notes:** This is the contract layer. `:app` and `:data` both depend on
  it, which is what lets the UI stay decoupled from the database.

### 4. `:core:common` — shared core utilities
- **Path:** `REFERENCE/core/common/`
- **Files:** 46 Kotlin files.
- **Purpose:** Foundation utilities shared across all modules — preferences,
  storage helpers, i18n bridge, coroutines/Rx bridges, system/image utils.
- **Depends on:** `:i18n` (for the localize bridge).
- **Used by:** `:source-api`, `:domain`, `:data`, `:source-local`,
  `:presentation-widget`, `:app` (transitively, the most-depended-on module).
- **Key contents:**
  - `tachiyomi.core.common.preference.*` — `PreferenceStore`, `AndroidPreference`,
    `CheckboxState`, `TriState` (the app's preference abstraction).
  - `tachiyomi.core.common.storage.*` — `UniFileExtensions` (storage helpers).
  - `tachiyomi.core.common.util.system.*` — `ImageUtil`, `LogcatExtensions`.
  - `tachiyomi.core.common.util.lang.*` — coroutine/Rx bridges, sort utils.
  - `tachiyomi.core.common.i18n.*` — `Localize` (bridge to moko-resources).
- **Notes:** This is the leaf-most code module. Changes here ripple
  everywhere — review carefully.

### 5. `:core:archive` — archive handling
- **Path:** `REFERENCE/core/archive/`
- **Files:** 6 Kotlin files.
- **Purpose:** Handles archive formats (CBZ, CBR, EPUB, ZIP, RAR) used for
  local manga/anime storage. Used by the local source + download manager.
- **Depends on:** (no internal module deps).
- **Used by:** `:app`, `:source-local`.
- **Key contents:** `mihon.core.*` — archive extraction / creation helpers.

### 6. `:core-metadata` — metadata handling
- **Path:** `REFERENCE/core-metadata/`
- **Files:** 5 Kotlin files.
- **Purpose:** Parses and manages metadata for entries (e.g. from EPUB
  archives or source-provided metadata).
- **Depends on:** `:source-api`.
- **Used by:** `:app`, `:source-local`.

### 7. `:source-api` — the source/extension contract
- **Path:** `REFERENCE/source-api/`
- **Files:** 46 Kotlin files. **Kotlin Multiplatform** (commonMain + androidMain).
- **Purpose:** Defines the contract that every anime/manga source (extension)
  must implement. This is the foundation of aniyomi's plugin-style source
  system — third-party extensions compile against this API.
- **Depends on:** `:core:common`.
- **Used by:** `:domain`, `:data`, `:core-metadata`, `:source-local`, `:app`.
- **Key contents:**
  - `eu.kanade.tachiyomi.source` — `Source`, `CatalogueSource`,
    `MangaSource`, `ConfigurableSource`, `UnmeteredSource` (the core interfaces).
  - `eu.kanade.tachiyomi.source.online` — `HttpSource`, `ParsedHttpSource`,
    `ResolvableSource` (HTTP-based source base classes).
  - `eu.kanade.tachiyomi.source.model` — `SManga`, `SChapter`, `Page`,
    `FilterList`, `UpdateStrategy` (the data models sources return).
- **Notes:** This is the single most important module to understand — it's
  the boundary between the app and all third-party content sources.

### 8. `:source-local` — local source implementation
- **Path:** `REFERENCE/source-local/`
- **Files:** 23 Kotlin files. Kotlin Multiplatform.
- **Purpose:** Implements a `Source` that reads from the device's local
  filesystem (your downloaded/local manga + anime archives).
- **Depends on:** `:source-api`, `:i18n`, `:core:archive`, `:core:common`,
  `:core-metadata`, `:domain`.
- **Used by:** `:app`.
- **Key contents:** `tachiyomi.source.*` — local source + archive reading.

### 9. `:i18n` — shared translations
- **Path:** `REFERENCE/i18n/`
- **Files:** 0 Kotlin — **moko-resources** localization files only.
- **Purpose:** Shared, Mihon-ecosystem string resources (inherited from
  Mihon/Tachiyomi). ~50+ languages.
- **Depends on:** nothing.
- **Used by:** `:core:common`, `:source-local`, `:presentation-widget`, `:app`.

### 10. `:i18n-aniyomi` — aniyomi-specific translations
- **Path:** `REFERENCE/i18n-aniyomi/`
- **Files:** 0 Kotlin — moko-resources only.
- **Purpose:** aniyomi-only string resources (the anime-specific UI strings
  that don't exist in manga-only Mihon). ~50+ languages.
- **Depends on:** nothing.
- **Used by:** `:app`.

### 11. `:presentation-core` — shared UI components
- **Path:** `REFERENCE/presentation-core/`
- **Files:** 47 Kotlin files.
- **Purpose:** Shared Compose UI components and the design-system foundation
  used across screens. No screen-specific logic — just reusable pieces.
- **Depends on:** (no internal module deps — stays lightweight).
- **Used by:** `:app`, `:presentation-widget`.
- **Key contents:**
  - `tachiyomi.presentation.*` — Compose components, util.
  - `mihon.presentation.*` — design-system pieces.

### 12. `:presentation-widget` — home-screen widgets
- **Path:** `REFERENCE/presentation-widget/`
- **Files:** 19 Kotlin files.
- **Purpose:** Android home-screen widgets (e.g. "recently updated" widget).
- **Depends on:** `:core:common`, `:domain`, `:presentation-core`, `:i18n`.
- **Used by:** `:app`.

### 13. `:macrobenchmark` — performance benchmarks
- **Path:** `REFERENCE/macrobenchmark/`
- **Files:** 2 Kotlin files.
- **Purpose:** Macrobenchmark tests that measure app startup + runtime
  performance. Not shipped in the APK; dev/CI only.
- **Depends on:** (no internal module deps — benchmarks the built app).
- **Used by:** nothing (standalone).

---

## How to use this map

- **Finding a feature:** look in `APP-STRUCTURE.md` first (the `app/` module's
  package tree). Most user-facing features live in `eu.kanade.tachiyomi.ui.*`.
- **Finding a data model / use case:** look in `:domain` (`tachiyomi.domain.*`).
- **Finding the database / repository impl:** look in `:data`
  (`tachiyomi.data.*`, `mihon.data.repository.*`).
- **Understanding the source/extension system:** start at `:source-api`
  (`eu.kanade.tachiyomi.source.*`), then `:app`'s `extension/` + `source/`
  packages for the loading/management side.
- **Understanding module wiring:** see `ARCHITECTURE.md` for the dependency
  graph and a sample request lifecycle.
