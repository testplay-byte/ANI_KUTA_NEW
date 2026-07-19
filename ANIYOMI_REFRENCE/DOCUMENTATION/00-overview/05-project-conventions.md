# 00-overview / 05 — Project Conventions

> Coding & structural conventions used throughout the Aniyomi reference. Knowing
> these up front makes the source far easier to navigate.

## 1. Package roots (historical lineage)

| Package root | Origin | Used for |
|---|---|---|
| `eu.kanade.tachiyomi.*` | Tachiyomi (original) | The app's main namespace: UI, source loader, DI. Kept for extension-compat. |
| `tachiyomi.*` | Tachiyomi / Mihon | Domain models, data layer, core utilities. |
| `mihon.*` | Mihon (Tachiyomi successor) | Build logic (`mihon.buildlogic`), design system (`mihon.core.designsystem`), migration code, i18n, benchmarks. |
| `aniyomi.*` | Aniyomi | Aniyomi-specific additions: utilities, some domain models, i18n-aniyomi. |

You'll see all four roots coexisting. The split is historical, not semantic.

## 2. The manga ↔ anime duality (MOST IMPORTANT convention)

Almost every concept exists twice. When you find one, look for the parallel:

| Manga concept | Anime concept | Notes |
|---|---|---|
| `Manga` (domain model) | `Anime` | A library entry. |
| `Chapter` | `Episode` | A readable/watchable unit. |
| `MangaSource` / `Source` | `AnimeSource` | The site/extension. |
| `MangaChapterOperations` | `AnimeEpisodeOperations` | |
| `MangaReaderActivity` | `PlayerActivity` | The reader/player. |
| `Page` | `Video` | A sub-unit (manga page / anime stream URL). |
| `MangaDownloadsQueue` | `AnimeDownloadsQueue` | |
| `MangaCategoryRepository` | `AnimeCategoryRepository` | |
| `MangaHistoryRepository` | `AnimeHistoryRepository` | |
| `MangaTrackRepository` | `AnimeTrackRepository` | Tracker binding per entry. |
| `sqldelight/` (manga schema) | `sqldelightanime/` (anime schema) | Two parallel DB schemas. |

**Implication for porting:** when studying a manga subsystem, also read the anime
twin — Aniyomi's additions (and design choices) are often clearest there.

## 3. Layered architecture & dependency direction

```
   UI (app)  ──►  domain use cases  ──►  domain models
        │              │
        ▼              ▼
   data (impls)  ──►  core:common
        │
        ▼
   source-api (contract)  ◄── source-local + external extensions
```

- **Domain is pure Kotlin.** No `android.*` imports in `:domain`.
- **Repository interfaces live in `:domain`.** Implementations live in `:data`.
- **Use cases ("Interactors") live in `:domain`** (and in `app/.../domain/`).
- **UI never touches `:data` directly** — it goes through use cases / repositories.

## 4. Naming patterns

| Pattern | Meaning | Example |
|---|---|---|
| `*Interactor` | A use case (domain layer). | `GetMangaBySource`, `FetchEpisodes` |
| `*Repository` | Data-access interface (domain) or impl (data). | `MangaRepository`, `MangaRepositoryImpl` |
| `*Screen` | A Voyager screen (Compose). | `LibraryScreen`, `ReaderActivity` (legacy) |
| `*ScreenModel` | Voyager's MVVM-ish ViewModel. | `LibraryScreenModel` |
| `*Activity` | A legacy Android Activity (often View-based). | `ReaderActivity`, `PlayerActivity` |
| `*ViewModel` | An `androidx.lifecycle.ViewModel`. | `ReaderViewModel`, `PlayerViewModel` |
| `*Notifier` | Builds & posts a notification. | `DownloadNotifier`, `ExtensionUpdateNotifier` |
| `*Store` | A preference store or state holder. | `PreferenceStore`, `MangaCoverStore` |
| `*Manager` | Coordinates multiple things. | `DownloadManager`, `ExtensionManager` |
| `*Loader` | Loads data for reader/player. | `ChapterLoader`, `EpisodeLoader` |
| `*Viewer` | A manga reader mode. | `R2LPagerViewer`, `VerticalReaderType` |
| `*.sq` | A SQLDelight query file. | `manga.sq`, `chapter.sq` |

## 5. "Get" vs "Fetch" vs "Set" vs "Update"

| Prefix | Convention |
|---|---|
| `Get*` | Reads local data (DB or memory), no network. |
| `Fetch*` | Hits the network (a source) and may persist locally. |
| `Set*` / `Update*` | Writes local data. |
| `Subscribe*` | Returns a long-lived `Flow` (reactive). |

## 6. Reactive data

- **Coroutines + Flow** everywhere. `StateFlow` for screen state.
- SQLDelight queries are exposed as `Flow<...>` via `asObservable().map { ... }`
  or the SQLDelight coroutines extension.
- ScreenModels expose `StateFlow<State>`; Compose collects it.

## 7. DI with Injekt

Aniyomi uses **Injekt** (a Tachiyomi/Mihon lineage DI). See
`../../01-architecture/02-dependency-injection.md` for the full story. Short
version:

- A single `Injekt` object holds a global graph.
- Modules register bindings via `addFactory`, `addSingleton`, `addAlias`.
- Consumers obtain deps via `Injekt.get<SomeDep>()` or constructor injection in
  newer code.
- The app's `App.kt` is the `Injekt`-aware `Application`.

## 8. Preference access

- `PreferenceStore` (interface in `:core:common`) wraps `SharedPreferences`.
- Each preference is a typed `Preference<T>` with a key + default.
- Preferences are grouped into `*Preferences` classes
  (e.g. `sourcePreferences`, `readerPreferences`, `playerPreferences`).
- See `../../01-architecture/05-preferences-system.md`.

## 9. Source / extension contract

- Defined in `:source-api` (KMP).
- Two parallel hierarchies: `eu.kanade.tachiyomi.source.*` (manga) and
  `eu.kanade.tachiyomi.animesource.*` (anime).
- Extensions are **separate APKs** loaded at runtime by `ExtensionManager`.
- See `../../03-subsystems/source-system.md`.

## 10. Error handling

- Crash screen: `app/.../crash/` (a custom `UncaughtExceptionHandler` →
  `CrashActivity`).
- ACRA scaffold exists but is commented out.
- Source errors surface as `Results.failure` in use-case return types.
- See `../../01-architecture/06-error-handling.md`.

## 11. Code formatting

- **Spotless** + **ktlint** enforce style (see `mihon.code.lint` plugin).
- `.editorconfig` at the repo root sets indentation, line length, etc.
- Imports are sorted by ktlint.

## See also

- [`01-project-overview.md`](01-project-overview.md)
- `../../01-architecture/` — the patterns above, expanded.
- `../../07-reference/glossary.md` — term definitions.
