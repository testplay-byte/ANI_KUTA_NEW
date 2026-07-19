# 01-architecture / 01 — Architecture Overview

> The cross-cutting architecture of Aniyomi: how the layers are organized, how
> dependencies flow, and the recurring patterns.

## Layered architecture

```
┌──────────────────────────────────────────────────────────────────┐
│  PRESENTATION  (in :app)                                         │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ Voyager Screens + ScreenModels + Compose UI + legacy Views │  │
│  │ Reader / Player / Library / Browse / Downloads / Settings  │  │
│  └───────────────────────┬────────────────────────────────────┘  │
└──────────────────────────┼───────────────────────────────────────┘
                           │ calls
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│  DOMAIN  (:domain — pure Kotlin, no Android UI)                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ Models: Manga/Anime, Chapter/Episode, Track, Category, ... │  │
│  │ Use cases (Interactors): Get*, Fetch*, Set*, Update*       │  │
│  │ Repository INTERFACES                                      │  │
│  └───────────────────────┬────────────────────────────────────┘  │
└──────────────────────────┼───────────────────────────────────────┘
                           │ implemented by
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│  DATA  (:data)                                                   │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ Repository IMPLEMENTATIONS (SQLDelight-backed)             │  │
│  │ SQLDelight .sq schemas (manga + anime, in parallel)        │  │
│  │ Preference stores                                          │  │
│  └───────────────────────┬────────────────────────────────────┘  │
└──────────────────────────┼───────────────────────────────────────┘
                           │ depends on
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│  CORE  (:core:common, :core:archive, :core-metadata)             │
│  Utilities, archive reading, metadata parsing.                   │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│  SOURCE API  (:source-api — KMP contract)                         │
│  Implemented by :source-local (built-in) AND external extensions  │
│  (loaded at runtime). The boundary between app and content.       │
└──────────────────────────────────────────────────────────────────┘
```

## Dependency rule (invariant)

> **Dependencies point inward.** `:domain` depends on nothing UI-related.
> `:data` depends on `:domain` (to implement interfaces) and `:core`. `:app`
> depends on everything. The source-api is a contract boundary; the app talks to
> sources through interfaces, never concrete external classes.

This is enforced structurally: `:domain`'s `build.gradle.kts` is a plain Kotlin
library (no `com.android.application`/Compose), so it physically cannot import
Android UI classes.

## The "Interactor" pattern

Aniyomi (inheriting Tachiyomi/Mihon) names its use cases `*Interactor`. Each is a
single-purpose class, usually with one `operator fun invoke(...)` or a `suspend
fun ...`/`fun subscribe(...)`.

Examples (from `:domain` and `app/.../domain/`):
- `GetMangaBySource` — read manga for a source from DB.
- `FetchChapters` — hit the source for chapter list, persist.
- `SetMangaCategories` — write category bindings.
- `GetAnimeEpisodesByAnimeId.subscribe(...)` — long-lived `Flow`.
- `GetDownloadsQueue.subscribe(...)` — observe the download queue.

UI code calls interactors; interactors call repositories; repositories call
either SQLDelight (local) or the source-api (remote).

## The "ScreenModel" pattern (Voyager)

For Compose screens, Aniyomi uses Voyager's `ScreenModel` — essentially a
scoped ViewModel tied to a `Screen`'s lifecycle. A `ScreenModel`:

- Holds state in `MutableStateFlow` exposed as `StateFlow`.
- Calls interactors (injected via Injekt).
- Survives configuration changes.

Each `*Screen` (Voyager screen) has a companion `*ScreenModel`. Legacy
View-based screens (reader, player) still use `androidx.lifecycle.ViewModel`.

## Two UI paradigms coexist

| Paradigm | Where | Why |
|---|---|---|
| Jetpack Compose + Voyager | Most screens (library, browse, history, updates, more/settings) | The modern target. |
| Legacy Views + Activity + ViewModel | `ReaderActivity`, `PlayerActivity` | Not yet migrated — the reader uses `subsampling-scale-image-view` (a View), and the player wraps an MPV `View`. |

See `../../06-ui/compose-migration.md` for the migration status.

## Repository interface location

- **Interface** → `:domain` (e.g. `tachiyomi.domain.manga.repository.MangaRepository`).
- **Implementation** → `:data` (e.g. `tachiyomi.data.entries.manga.MangaRepositoryImpl`).
- **Wiring** → Injekt binds interface to impl (see
  `../../01-architecture/02-dependency-injection.md`).

This lets `:domain` stay Android-free while `:data` does the SQLDelight work.

## Data flow example: opening the library

```
LibraryScreen (Compose)
   └─ collects LibraryScreenModel.state : StateFlow<LibraryState>
        └─ LibraryScreenModel invokes GetLibraryManga.subscribe()
             └─ GetLibraryManga (interactor) calls MangaRepository.getFavoritesBySource()
                  └─ MangaRepositoryImpl runs a SQLDelight query
                       └─ .sq file emits on DB change → Flow updates → UI recomposes
```

The same pattern repeats for every list screen.

## Where the "app/.../domain" folder fits

Besides the `:domain` module, the `:app` module has its own
`eu.kanade.domain.*` packages (`app/src/main/java/eu/kanade/domain/`). These hold
**app-specific interactors** that depend on Android (e.g. interactors that need
`Context`, `ExtensionManager`, or `DownloadManager`). Pure domain interactors
live in `:domain`; Android-aware ones live here.

## See also

- [`02-dependency-injection.md`](02-dependency-injection.md) — Injekt.
- [`03-state-and-async.md`](03-state-and-async.md) — Coroutines/Flow/StateFlow.
- [`04-navigation.md`](04-navigation.md) — Voyager.
- `../../02-modules/domain.md` & `../../02-modules/data.md`.
- `../../05-key-flows/` — concrete flows through these layers.
