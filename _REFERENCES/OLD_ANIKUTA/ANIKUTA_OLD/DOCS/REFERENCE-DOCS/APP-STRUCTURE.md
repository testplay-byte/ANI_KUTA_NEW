# aniyomi `app/` Module — Internal Structure

> The `app/` module holds 952 of aniyomi's ~1,400 Kotlin files. This file
> maps its internal package tree so you can find any feature quickly.
>
> Based on `REFERENCE/app/` at commit `2f5cf77` (2025-11-05).

---

## Top-level packages

All paths below are relative to `REFERENCE/app/src/main/java/`.

| Package | kt files | Purpose |
|---------|----------|---------|
| `eu/kanade/tachiyomi/` | 525 | The main app code (legacy Tachiyomi namespace). UI, data, source/extension mgmt, DI. |
| `eu/kanade/presentation/` | 274 | Compose UI layer (screen components + theme). |
| `eu/kanade/domain/` | 76 | App-level domain helpers (mappers, etc.). |
| `mihon/` | 68 | Mihon-ecosystem features (migration, design system, upcoming). |
| `eu/kanade/core/` | 6 | App-level core glue. |
| `aniyomi/` | 1 | aniyomi-specific utilities. |
| `eu/kanade/test/` | 1 | Test helpers. |

---

## `eu/kanade/tachiyomi/` — the main app

### `ui/` — screens (20 packages)

The primary navigation/feature map. Each sub-package is one screen area.

| Sub-package | What it is |
|-------------|------------|
| `ui/main/` | The main activity + bottom-nav host. App entry point. |
| `ui/home/` | The home screen shell. |
| `ui/library/` | The library (your saved anime/manga), categories, sorting, filters. |
| `ui/entries/` | The entry detail screen (anime or manga detail page). |
| `ui/player/` | **Anime video player.** Sub-packages: `controls/`, `loader/`, `settings/`, `utils/`. aniyomi's flagship feature. |
| `ui/reader/` | **Manga reader.** Sub-packages: `loader/`, `model/`, `setting/`, `viewer/`. |
| `ui/browse/` | Browse sources / extensions catalog. |
| `ui/category/` | Category management. |
| `ui/download/` | Download queue + manager UI. |
| `ui/history/` | Recently-read / recently-watched history. |
| `ui/updates/` | New chapter/episode updates feed. |
| `ui/stats/` | Reading/watching statistics. |
| `ui/storage/` | Storage usage breakdown. |
| `ui/setting/` | Settings screens (all sections). |
| `ui/more/` | The "More" tab (about, help, etc.). |
| `ui/webview/` | Embedded WebView (for source login / browsing). |
| `ui/security/` | App lock / secure screen. |
| `ui/deeplink/` | Deep-link handling (e.g. share-into-app, intents). |
| `ui/base/` | Base activity/fragment classes. |
| `ui/widget/` | Custom UI widgets. |

### `data/` — data + background services (13 packages)

| Sub-package | What it is |
|-------------|------------|
| `data/download/` | **Download manager** — queue, provider, job service. |
| `data/backup/` | Backup/restore of library + settings. |
| `data/cache/` | Caches (cover cache, chapter cache). |
| `data/coil/` | Coil image-loader integration. |
| `data/database/` | Database setup (SQLDelight wiring on the app side). |
| `data/library/` | Library update job + helpers. |
| `data/notification/` | Notification channels + builders. |
| `data/preference/` | Preference keys + defaults. |
| `data/saver/` | Image/page saver. |
| `data/track/` | **Trackers** — one sub-package per service (see below). |
| `data/updater/` | App self-updater. |
| `data/export/` | Data export. |

### `data/track/` — tracker integrations (11 services)

Each is a self-contained package implementing the tracker contract.

| Tracker | Path |
|---------|------|
| MyAnimeList | `data/track/myanimelist/` |
| AniList | `data/track/anilist/` |
| Shikimori | `data/track/shikimori/` |
| Kitsu | `data/track/kitsu/` |
| Simkl | `data/track/simkl/` |
| Bangumi | `data/track/bangumi/` |
| MangaUpdates | `data/track/mangaupdates/` |
| Komga | `data/track/komga/` |
| Kavita | `data/track/kavita/` |
| Jellyfin | `data/track/jellyfin/` |
| Suwayomi | `data/track/suwayomi/` |
| (models) | `data/track/model/` |

### `source/` and `extension/` — the source/extension system

| Sub-package | What it is |
|-------------|------------|
| `source/anime/` | Anime source manager (loads + manages anime extensions). |
| `source/manga/` | Manga source manager (loads + manages manga extensions). |
| `extension/anime/` | Anime extension loading/installing. |
| `extension/manga/` | Manga extension loading/installing. |

> The **contract** these extensions implement lives in the `:source-api`
> module (`eu.kanade.tachiyomi.source.*`). See `MODULES.md` §7.

### `di/` — dependency injection

| File | What it is |
|------|------------|
| `di/AppModule.kt` | Main DI module (binds repositories, sources, services). |
| `di/PreferenceModule.kt` | Preference store binding. |

> aniyomi uses **Injekt** (not Hilt/Dagger). You'll see `@Inject`-style
> constructor injection throughout.

### Other `eu/kanade/tachiyomi/` packages

| Package | What it is |
|---------|------------|
| `crash/` | Crash reporter + crash screen. |
| `util/` | App-level utilities. |
| `widget/` | (root-level widgets, separate from `ui/widget/`). |

---

## `eu/kanade/presentation/` — Compose UI layer (16 packages)

Mirror of the `ui/` screens but in **Jetpack Compose**. Each screen has its
Compose components here.

| Sub-package | Maps to screen |
|-------------|----------------|
| `library/` | Library screen Compose UI. |
| `entries/` | Entry detail Compose UI. |
| `player/` | Player Compose UI (overlays, controls). |
| `reader/` | Reader Compose UI. |
| `browse/` | Browse Compose UI. |
| `category/` | Category Compose UI. |
| `history/` | History Compose UI. |
| `updates/` | Updates Compose UI. |
| `more/` | More-tab Compose UI. |
| `track/` | Tracker setup Compose UI. |
| `webview/` | WebView Compose UI. |
| `theme/` | **App theme** — colors, typography, Material setup. |
| `components/` | Shared Compose components. |
| `util/` | Compose utilities. |
| `crash/` | Crash screen Compose UI. |

> **Pattern:** `ui/<screen>/` holds the Activity/Fragment + ViewModel +
  state; `presentation/<screen>/` holds the Compose composables. They pair up.

---

## `mihon/` — Mihon-ecosystem features (68 files)

| Sub-package | What it is |
|-------------|------------|
| `mihon/core/designsystem/` | Design-system tokens (colors, shapes, type). |
| `mihon/core/migration/` | App version migration logic (DB + prefs). |
| `mihon/feature/upcoming/` | "Upcoming" feature (calendar/schedule view). |

---

## `eu/kanade/domain/` — app-level domain helpers (76 files)

Mappers, formatters, and helpers that bridge `:domain` models to what the UI
needs. Sits alongside (not inside) the `:domain` module.

---

## Entry points (where execution starts)

| Entry point | Path | When |
|-------------|------|------|
| `Application` class | `eu/kanade/tachiyomi/App.kt` | App process start. |
| Main `Activity` | `eu/kanade/tachiyomi/ui/main/MainActivity.kt` | Launcher opens app. |
| DI root | `eu/kanade/tachiyomi/di/AppModule.kt` | Wired at Application start. |
| AndroidManifest | `app/src/main/AndroidManifest.xml` | Declares activities, services, permissions. |

---

## How to find things — quick lookup

| I want to find… | Look in… |
|------------------|----------|
| A screen's code (logic) | `eu/kanade/tachiyomi/ui/<screen>/` |
| A screen's Compose UI | `eu/kanade/presentation/<screen>/` |
| The video player | `ui/player/` (+ `presentation/player/`) |
| The manga reader | `ui/reader/` (+ `presentation/reader/`) |
| The download manager | `data/download/` |
| A tracker integration | `data/track/<service>/` |
| The source/extension loader | `source/` + `extension/` |
| The database schema | the `:data` module (`data/src/main/sqldelight*/`) |
| A domain model / use case | the `:domain` module (`tachiyomi.domain.*`) |
| Settings screens | `ui/setting/` |
| App theme / colors | `presentation/theme/` + `mihon/core/designsystem/` |
| DI bindings | `di/AppModule.kt`, `di/PreferenceModule.kt` |
