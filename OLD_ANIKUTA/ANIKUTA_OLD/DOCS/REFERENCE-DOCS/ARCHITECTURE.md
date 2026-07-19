# aniyomi Architecture

> How aniyomi's modules fit together, how data flows through the app, and
> what a typical user action looks like end-to-end.
>
> Read after `MODULES.md` (the per-module reference) and `APP-STRUCTURE.md`
> (the app package tree). Based on `REFERENCE/` at commit `2f5cf77` (2025-11-05).

---

## 1. The layering

aniyomi uses a **clean-architecture-style** layering. Dependencies only point
*downward* (toward the foundation), never up. This is what keeps the UI
swappable and the database decoupled.

```
┌─────────────────────────────────────────────────────────────┐
│                        :app  (952 files)                     │
│  UI activities/ViewModels · DI · player · reader · trackers  │
│  extension loader · download mgr · source mgr · settings     │
└──────────┬──────────────────────────────────────┬───────────┘
           │ depends on                            │ depends on
           ▼                                       ▼
┌──────────────────┐   ┌──────────────────┐   ┌─────────────────┐
│ :presentation-   │   │ :data            │   │ :source-local   │
│  core / -widget  │   │ (repos + DB)     │   │ (local files)   │
└────────┬─────────┘   └────────┬─────────┘   └────────┬────────┘
         │ depends on           │ depends on            │ depends on
         ▼                      ▼                       ▼
┌─────────────────────────────────────────────────────────────┐
│                     :domain (200 files)                      │
│         use cases · domain models · repo interfaces          │
└──────────────────────────────┬──────────────────────────────┘
                               │ depends on
                               ▼
┌──────────────────┐   ┌──────────────────┐   ┌─────────────────┐
│ :source-api      │   │ :core:common     │   │ :core:archive / │
│ (source contract)│   │ (utilities)      │   │ :core-metadata  │
└────────┬─────────┘   └────────┬─────────┘   └─────────────────┘
         │ depends on           │ depends on
         ▼                      ▼
         └──────────┬───────────┘
                    ▼
         ┌────────────────────┐
         │ :i18n / :i18n-     │   (translations only — no code deps)
         │   aniyomi          │
         └────────────────────┘
```

### Key invariants

1. **`:domain` declares repository *interfaces*; `:data` implements them.**
   The UI (`:app`) depends only on the interfaces, so the database can be
   swapped without touching UI.
2. **`:source-api` is the plugin boundary.** Third-party extensions compile
   against it. The app loads them at runtime.
3. **`:i18n` modules are leaves** — nothing depends upward from them, so
   translations never cause code changes.
4. **`:app` is the composition root** — it wires everything together via DI.

---

## 2. The source/extension system (aniyomi's core design)

aniyomi doesn't bundle any anime/manga sources. Instead, it loads
**extensions** (separate APKs) at runtime. Each extension implements the
contract in `:source-api`.

```
┌──────────────────────────────────────────────────────────┐
│                        :app                               │
│                                                           │
│  extension/anime/  ──loads──▶  AnimeSource (interface)    │
│  extension/manga/  ──loads──▶  MangaSource (interface)    │
│         │                              ▲                  │
│         │ installs APK                 │ implements        │
│         ▼                              │                  │
│   ┌─────────────┐              ┌──────────────────┐       │
│   │ Extension   │              │ :source-api      │       │
│   │ APK (3rd    │──────────────│ Source,          │       │
│   │ party)      │   compiles   │ HttpSource,      │       │
│   └─────────────┘   against    │ CatalogueSource  │       │
│                                 │ SManga, SChapter,│       │
│                                 │ Page, ...        │       │
│                                 └──────────────────┘       │
└──────────────────────────────────────────────────────────┘
```

**Flow:** User installs an extension → `:app`'s extension loader discovers it
→ the extension exposes a `Source` → the app calls `Source.fetchSearchManga()`
/ `fetchAnimeList()` etc. → results flow into `:domain` models → stored via
`:data` → displayed by Compose UI.

This is why `:source-api` is Kotlin Multiplatform — extensions are built
independently and only need to depend on the API, not the whole app.

---

## 3. The dual data model (anime + manga)

aniyomi inherits Mihon's manga data model and **adds a parallel anime model**.
This duplication runs through every layer:

| Layer | Manga side | Anime side |
|-------|-----------|------------|
| `:source-api` | `SManga`, `SChapter`, `Page` | (anime uses the same source interfaces but anime-specific impls) |
| `:domain` | `tachiyomi.domain.entries` (manga) | `aniyomi.domain.anime` |
| `:data` (DB) | `src/main/sqldelight/` | `src/main/sqldelightanime/` |
| `:app` source mgr | `source/manga/`, `extension/manga/` | `source/anime/`, `extension/anime/` |
| `:app` UI | `ui/reader/` | `ui/player/` |

> **Implication:** any feature that touches "entries" often has two parallel
> implementations. When we build our app, we must respect both (or decide to
> drop one — a future decision).

---

## 4. Request lifecycle: "user opens an anime detail page"

A concrete end-to-end example of how the layers cooperate.

```
1. TAP on anime in library
   └─▶ :app  ui/library/  (Compose) sends intent / navigates
        └─▶ :app  ui/entries/EntryActivity  +  EntryViewModel

2. EntryViewModel asks for data
   └─▶ :domain  GetAnimeWithEpisodes (use case)
        └─▶ :domain  AnimeRepository (interface)

3. Repository impl answers
   └─▶ :data  AnimeRepositoryImpl
        └─▶ :data  SQLDelight queries (sqldelightanime/)
             └─▶ SQLite database on device

4. Domain models return up the stack
   └─▶ :domain  Anime + Episodes  →  :app  EntryViewModel.state

5. Compose renders
   └─▶ :app  presentation/entries/  (composables)
        └─▶ uses :presentation-core components + :app theme

6. (Optional) user taps an episode → player
   └─▶ :app  ui/player/  (the video player)
        └─▶ may call Source.fetchEpisodeList() via :source-api
             (the loaded 3rd-party extension) if not cached
```

Notice: the UI never touches SQLDelight directly. It only sees `:domain`
interfaces. This is the decoupling that lets us change the DB (or the UI)
independently.

---

## 5. Request lifecycle: "user searches a source for anime"

```
1. TAP search in browse screen
   └─▶ :app  ui/browse/  →  BrowseViewModel

2. ViewModel calls the source manager
   └─▶ :app  source/anime/AnimeSourceManager
        └─▶ returns the AnimeSource for the selected extension

3. Source call (3rd-party extension, loaded at runtime)
   └─▶ AnimeSource.fetchSearchAnime(query)   ← contract from :source-api
        └─▶ HTTP request to the source's website
        └─▶ parses response → returns List<AnimeInfo>

4. Results mapped to domain models
   └─▶ :domain  AnimeSearchResult  →  ViewModel.state

5. Compose renders the list
   └─▶ :app  presentation/browse/
```

Here the key boundary is `:source-api` — the app doesn't know *how* a source
fetches data, only that it returns the agreed-upon models.

---

## 6. Dependency injection

aniyomi uses **Injekt** (a lightweight Kotlin DI framework, not Hilt/Dagger).

- **Wiring:** `:app`'s `di/AppModule.kt` + `di/PreferenceModule.kt`.
- **Pattern:** constructor injection — classes declare their deps as
  constructor params; Injekt provides them.
- **Repositories:** the `:domain` interface is bound to the `:data`
  implementation in `AppModule`.

> When we build our app, we can keep Injekt or switch to Hilt/Koin — that's a
> future decision (see `MEMORY/DECISIONS/`).

---

## 7. Persistence

- **ORM:** SQLDelight (not Room). Schema is `.sq` files, not annotations.
- **Two databases:** `sqldelight/` (manga) + `sqldelightanime/` (anime).
- **Migrations:** in `data/src/main/sqldelight{,anime}/migrations/`.
- **Preferences:** custom `PreferenceStore` abstraction in `:core:common`
  (not Android `SharedPreferences` directly) — see `AndroidPreferenceStore`.

---

## 8. UI architecture

- **View system:** **mixed** — some screens are legacy Fragment/View-based,
  many are **Jetpack Compose**. The `presentation/` package holds the Compose
  side; `ui/` holds activities + viewmodels + (some) legacy views.
- **State:** ViewModel + `StateFlow`/`Compose State`.
- **Theme:** `presentation/theme/` (Material) + `mihon/core/designsystem/`
  (design tokens). Light/dark + AMOLED variants.
- **Image loading:** Coil (`data/coil/`).

> **Implication for our UI/logic separation rule:** aniyomi already separates
> Compose UI (`presentation/`) from logic (`ui/` viewmodels + `:domain`).
> This is a good base to build on.

---

## 9. What this means for our build

| aniyomi pattern | How it lines up with our rules |
|-----------------|--------------------------------|
| Clean-architecture layering (domain/data/app) | ✅ Matches our UI/logic separation rule. |
| `:source-api` as a plugin boundary | ✅ Reusable logic, decoupled from UI. |
| Dual anime/manga models | ⚠️ We must decide: keep both, or go anime-only. |
| Mixed View + Compose UI | ⚠️ Our app can go Compose-first to simplify. |
| Injekt DI | ⚠️ Future decision: keep Injekt or move to Hilt/Koin. |
| SQLDelight (not Room) | ⚠️ Future decision: keep SQLDelight or move to Room. |

These are **future decisions** — recorded here so we don't forget. They'll be
formalized in `MEMORY/DECISIONS/` when we start building.
