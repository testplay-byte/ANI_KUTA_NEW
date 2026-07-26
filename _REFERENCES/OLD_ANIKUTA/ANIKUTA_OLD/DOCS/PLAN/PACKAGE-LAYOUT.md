# Package Layout — What we copy from aniyomi + our structure

> The detailed package plan for ANI-KUTA. Which aniyomi subsystems we copy,
> which we build ourselves, and how it all maps to `app.anikuta.*`.
>
> This is the concrete implementation of D1 (selective copy-paste with
> documentation). Every copied part is recorded here + in `DOCS/APP/STRUCTURE/`
> as we build.

---

## ⚠️ Incremental copy-paste (D44)

We do **NOT** copy all of aniyomi's backend at once. We copy **what each phase
needs**, along the way:
- Understand the aniyomi code → analyze if we need it → copy + adapt → document.
- First priority: **the extension system** (managing, communication, data
  fetching, display) — needed for onboarding step 4 + the home page.
- When aniyomi updates, we compare our copied files against the new version,
  analyze the diff, and decide whether to adopt.

See `PHASE-1-PLAN.md` for the step-by-step copy sequence.

---

## The principle (D1)

- We use our own `app.anikuta.*` packages throughout.
- We **copy the specific aniyomi parts we need**, adapting them into our package
  structure (renaming packages from `eu.kanade.*` / `tachiyomi.*` → `app.anikuta.*`).
- **Every copied part is documented** in `DOCS/APP/STRUCTURE/` — what we copied,
  from which aniyomi commit, what we changed, and why.

---

## What we copy from aniyomi (the backend)

| aniyomi subsystem | aniyomi location | Our location | What we change |
|-------------------|------------------|--------------|----------------|
| Source/extension contract (anime) | `:source-api` → `eu.kanade.tachiyomi.animesource.*` | `:source-api` → `app.anikuta.source.api.*` | Rename package; drop manga side. |
| Domain (anime) | `:domain` → `aniyomi.domain.anime.*` + shared `tachiyomi.domain.*` | `:domain` → `app.anikuta.domain.*` | Rename; keep anime-only; drop manga-only. |
| Data layer (anime DB + repos) | `:data` → `tachiyomi.mi.data.*` + `sqldelightanime/` | `:data` → `app.anikuta.data.*` | Rename; drop manga DB (`sqldelight/`); keep anime DB. |
| Core utilities | `:core:common` → `tachiyomi.core.common.*` | `:core` → `app.anikuta.core.*` | Rename; keep what we use. |
| DI wiring | `:app` → `di/AppModule.kt` etc. (anime bindings) | `:app` → `app.anikuta.di.*` | Rename; keep anime bindings only; drop manga. |
| Source/extension managers | `:app` → `source/anime/` + `extension/anime/` | `:app` → `app.anikuta.source.*` + `app.anikuta.extension.*` | Rename; anime-only. |
| Player (MPV) | `:app` → `ui/player/` + `presentation/player/` | `:app` → `app.anikuta.player.*` | Rename; reskin UI per our 4 designs (Phase 6). |
| Download manager (anime) | `:app` → `data/download/anime/` | `:app` → `app.anikuta.data.download.*` | Rename; anime-only. |
| Backup/restore (anime fields) | `:app` → `data/backup/` | `:app` → `app.anikuta.data.backup.*` | Rename; keep anime fields (500-507); emit empty manga. |
| Trackers (anime side) | `:app` → `data/track/<service>/` (anime side) | `:app` → `app.anikuta.data.track.*` | Rename; keep AniList + MAL + Shikimori + Kitsu + Bangumi + Simkl + Jellyfin (anime-side); drop manga-only (Komga, Kavita, Suwayomi, MangaUpdates). |

## What we do NOT copy

- **Manga-only code** — manga source/extension, manga DB, manga domain, manga
  download manager, manga reader (`ui/reader/`), manga trackers. (D2: anime-only.)
- **aniyomi UI** — `ui/` (except player), `presentation/`. We build our own 4 designs.
- **aniyomi i18n** — `:i18n` + `:i18n-aniyomi`. We do our own strings.
- **`:presentation-core` / `:presentation-widget`** — we build our own UI components.
- **`:macrobenchmark`** — not needed yet.
- **`:core:archive`** — manga archive formats (CBZ/CBR/EPUB). Add later if we
  support local anime files.
- **`:core-metadata`** — manga metadata. Add later with manga.
- **`:source-local`** — local manga source. Add later if needed.

## What we build ourselves (the UI + integration)

| Our subsystem | Location | What it does |
|---------------|----------|--------------|
| AniList client | `app.anikuta.data.anilist.*` | GraphQL client for discovery/metadata. OURS — aniyomi doesn't have this. |
| Supabase client | `app.anikuta.data.supabase.*` | Supabase REST/Realtime client for the cache layer. OURS. |
| Caching layer | `app.anikuta.data.cache.*` | The 3-step cache (Local → Supabase → AniList). OURS — learned from aniwatch. |
| UI — 4 designs | `app.anikuta.ui.theme.*` | Material 3 + Neon + Neobrutalism + Coffee. Each = a Compose theme. |
| UI — screens | `app.anikuta.ui.<screen>.*` | Home, Detail, Library, History, Search, Settings, Extensions. OURS. |
| Onboarding | `app.anikuta.onboarding.*` | The 7-step setup wizard. OURS. |
| Navigation | `app.anikuta.navigation.*` | Compose Navigation graph. OURS. |

---

## The full package tree (proposed)

```
app.anikuta/
│
├── App.kt                              ← Application class (Injekt setup)
│
├── di/                                 ← Dependency injection (Injekt)
│   ├── AppModule.kt                    ← infra singletons (DB, source mgr, etc.)
│   ├── PreferenceModule.kt             ← preference store
│   └── DomainModule.kt                 ← repo bindings + use cases
│
├── core/                               ← :core module — shared utilities
│   ├── preference/                     ← PreferenceStore (from aniyomi :core:common)
│   ├── storage/                        ← storage helpers (from aniyomi)
│   ├── util/                           ← system/image/lang utils (from aniyomi)
│   └── i18n/                           ← localize bridge (ours)
│
├── domain/                             ← :domain module — models + use cases
│   ├── anime/                          ← anime domain models (from aniyomi)
│   ├── source/                         ← source interfaces (from aniyomi)
│   ├── track/                          ← tracker interfaces (from aniyomi)
│   ├── library/                        ← library use cases
│   ├── history/                        ← history use cases
│   └── ...                             ← (per-feature use cases)
│
├── data/                               ← :data module — DB + repos
│   ├── db/                             ← SQLDelight anime DB (from aniyomi)
│   ├── repository/                     ← anime repo impls (from aniyomi)
│   ├── anilist/                        ← AniList client (OURS)
│   ├── supabase/                       ← Supabase client (OURS)
│   ├── cache/                          ← 3-step cache layer (OURS)
│   ├── download/                       ← AnimeDownloadManager (from aniyomi)
│   ├── backup/                         ← BackupCreator/Restorer (from aniyomi)
│   ├── track/                          ← tracker impls (from aniyomi, anime side)
│   └── preference/                     ← preference impls (from aniyomi)
│
├── source/                             ← :app — source system
│   ├── api/                            ← AnimeSource contract (from :source-api)
│   └── manager/                        ← AnimeSourceManager (from aniyomi)
│
├── extension/                          ← :app — extension system
│   ├── api/                            ← extension loader contract
│   └── manager/                        ← AnimeExtensionManager (from aniyomi)
│
├── player/                             ← :app — video player (from aniyomi)
│   ├── activity/                       ← PlayerActivity
│   ├── viewmodel/                      ← PlayerViewModel
│   ├── controls/                       ← playback controls
│   ├── loader/                         ← episode/stream loader
│   ├── settings/                       ← player settings
│   └── utils/                          ← player utils
│
├── ui/                                 ← :app — UI layer (OURS)
│   ├── theme/                          ← 4 designs
│   │   ├── material3/                  ← Design 1
│   │   ├── neon/                       ← Design 2
│   │   ├── neobrutalism/               ← Design 3
│   │   ├── coffee/                     ← Design 4
│   │   └── Theme.kt                    ← design picker
│   ├── components/                     ← shared Compose components
│   ├── home/                           ← home screen
│   ├── detail/                         ← anime detail screen
│   ├── library/                        ← library screen
│   ├── history/                        ← history screen
│   ├── search/                         ← search screen
│   ├── settings/                       ← settings screens
│   ├── extensions/                     ← extensions management screen
│   └── player/                         ← player UI overlay (per design)
│
├── onboarding/                         ← :app — setup wizard (OURS)
│   ├── steps/                          ← 7 steps
│   └── OnboardingViewModel.kt
│
├── navigation/                         ← :app — nav graph (OURS)
│   └── AnikutaNavGraph.kt
│
└── util/                               ← :app — app-level utils
```

---

## How the copy-paste works (concrete example)

When we copy, say, the `AnimeSource` interface from aniyomi:

1. **Find it:** `REFERENCE/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeSource.kt`
2. **Copy to:** `anikuta-app/source-api/src/main/java/app/anikuta/source/api/AnimeSource.kt`
3. **Adapt:**
   - Change `package eu.kanade.tachiyomi.animesource` → `package app.anikuta.source.api`
   - Update imports (other aniyomi references → our packages)
   - Drop anything manga-coupled (none here — anime side is clean)
4. **Document:** in `DOCS/APP/STRUCTURE/source-api.md` — "AnimeSource.kt, copied
   from aniyomi commit 2f5cf77, renamed package, no logic changes."

This is repeated for every file. Each gets a doc entry.

---

## Module → aniyomi mapping (for upstream tracking)

When aniyomi updates a file, we know where our version lives:

| aniyomi module | Our module | Notes |
|----------------|------------|-------|
| `:source-api` (anime) | `:source-api` | 1:1, anime-only |
| `:domain` (anime) | `:domain` | 1:1, anime-only |
| `:data` (anime DB + repos) | `:data` | 1:1, anime-only |
| `:core:common` | `:core` | subset (what we use) |
| `:app` (anime subsystems) | `:app` | source mgr, extension mgr, player, download, backup, trackers |
| `:app` (UI) | — | NOT copied; we build our own |
| `:i18n` / `:i18n-aniyomi` | — | NOT copied; our own strings |
| manga modules | — | NOT copied (anime-only) |

---

## Open questions

- [ ] Does this package structure work for you, or want changes?
- [ ] The `:source-api` module — keep as a separate module (like aniyomi, for
  future extension-development), or fold into `:domain`? (aniyomi keeps it
  separate so 3rd-party extensions compile against just `:source-api`.)
- [ ] Do we want `:source-local` (local anime files) now or later?
