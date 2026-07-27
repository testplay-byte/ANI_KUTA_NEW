# ANI-KUTA — Project Architecture & Structure

> Complete documentation of the project's modular architecture.
> Last updated: Session 31 (after Library/History/Search revamp merge).

---

## Project Overview

**ANI-KUTA** is an Android anime streaming app, built as a copy (not fork) of [aniyomi](https://github.com/aniyomiorg/aniyomi). It uses AniList for discovery + metadata, aniyomi extensions for streaming sources, and MPV for video playback.

- **App ID:** `app.anikuta`
- **Min SDK:** 26 (Android 8.0) · **Compile SDK:** 35 · **Target SDK:** 35
- **Language:** Kotlin · **Build:** Gradle KTS · **UI:** Jetpack Compose (Material 3 Expressive)
- **Architecture:** 5-module Gradle project + Injekt DI

---

## Module Structure

```
anikuta/
├── app/           ← Android app (UI, player, download, extensions, DI)
├── core/          ← Shared utilities (preferences, network, storage)
├── data/          ← SQLDelight database, repositories, cache
├── domain/        ← Domain models, interactors, repository interfaces
├── source-api/    ← Extension source API (contract for 3rd-party extensions)
```

### Module Dependencies
```
app → core, data, domain, source-api
data → core, domain, source-api
domain → core, source-api
source-api → core
core → (no internal deps)
```

---

## app/ Module — Package Structure

```
app/src/main/java/app/anikuta/
├── App.kt                    ← Application class (DI setup, crash handler)
├── MainActivity.kt           ← Launcher activity (onboarding/nav)
│
├── di/                       ← Dependency Injection (Injekt)
│   ├── AppModule.kt          ← Main DI module (~50 singletons)
│   ├── DomainModule.kt       ← Domain layer DI (repos + interactors)
│   └── PreferenceModule.kt   ← PreferenceStore registration
│
├── data/                     ← App-local data layer
│   ├── anilist/              ← AniList GraphQL client
│   │   ├── api/AniListQueries.kt
│   │   ├── model/AniListModels.kt
│   │   └── repository/AniListRepository.kt
│   ├── cache/                ← Persistent caches
│   │   ├── CacheManager.kt
│   │   ├── EpisodeCacheStore.kt
│   │   ├── SubDubStore.kt    ← Sub/dub counts cache
│   │   └── ExtensionLinkStore.kt ← Extension→AniList link cache
│   ├── supabase/SupabaseClient.kt
│   ├── metadata/EpisodeMetadataFetcher.kt
│   ├── notification/Notifications.kt
│   └── tracker/AniListTracker.kt
│
├── domain/                   ← App-local domain overrides
│   ├── extension/anime/interactor/TrustAnimeExtension.kt
│   └── source/service/SourcePreferences.kt
│
├── download/                 ← Download manager (WorkManager)
│   ├── DownloadManager.kt
│   ├── DownloadWorker.kt
│   ├── DownloadStore.kt
│   ├── DownloadPreferences.kt
│   ├── DownloadProvider.kt
│   ├── DownloadVideoResolver.kt
│   ├── DownloadNotifier.kt
│   └── engine/               ← Download engines (SinglePass, HLS, Segment)
│
├── error/                    ← Global crash handling
│   ├── AnikutaCrashHandler.kt
│   └── ErrorActivity.kt
│
├── extension/                ← Extension APK loader/manager
│   └── anime/
│       ├── AnimeExtensionManager.kt
│       ├── AnimeExtensionApi.kt
│       ├── AnimeExtensionLoader.kt
│       └── model/AnimeExtension.kt, AnimeLoadResult.kt
│
├── navigation/
│   └── AnikutaNavGraph.kt    ← Compose Navigation (all routes)
│
├── onboarding/
│   ├── OnboardingScreen.kt
│   └── OnboardingState.kt
│
├── player/                   ← MPV video player
│   ├── PlayerActivity.kt     ← Player host (2360+ lines)
│   ├── PlayerScreen.kt       ← Compose UI (minimized + fullscreen)
│   ├── PlayerViewModel.kt    ← State management
│   ├── PlayerPreferences.kt  ← Player prefs (mode, skip, gestures)
│   ├── PlayerEpisodePreferences.kt
│   ├── PlayerObserver.kt     ← MPV event → Activity callbacks
│   ├── AnikutaMPVView.kt     ← MPV wrapper + track API
│   ├── MpvConfigManager.kt
│   ├── WatchProgressStore.kt ← Resume position persistence (reactive)
│   ├── PlaybackStateStore.kt ← Server/quality/track memory for resume
│   └── controls/
│       ├── FullscreenControls.kt
│       ├── MinimizedControls.kt
│       ├── EpisodeListView.kt
│       ├── EpisodeSwitchingOverlay.kt
│       ├── PlayerGestureHandler.kt
│       ├── SubtitleSettingsPanel.kt
│       ├── NumericKeypad.kt
│       ├── ColorPickerDialog.kt
│       ├── ServerVersionDropdowns.kt
│       └── sheets/PlayerSheet.kt, PlayerSheets.kt
│
├── source/                   ← Source management + Aniyomi bridge
│   ├── AndroidAnimeSourceManager.kt
│   ├── AnimeSourceExtensions.kt
│   └── bridge/
│       ├── AniyomiSourceBridge.kt ← Fuzzy title matching + search
│       └── TitleMatcher.kt
│
├── storage/                  ← SAF folder selection
│   ├── StorageManager.kt
│   └── StoragePreferences.kt
│
├── ui/                       ← Compose UI screens
│   ├── components/           ← Shared composables
│   │   ├── ExpressiveCard.kt     ← Spring press feedback card
│   │   ├── FloatingTopBar.kt     ← Pill-shaped top bar
│   │   └── SectionHeader.kt      ← Uppercase label + accent bar
│   ├── theme/
│   │   ├── Theme.kt
│   │   ├── Expressive.kt     ← AnikutaSprings + AnikutaTypography
│   │   └── Color.kt
│   ├── home/                 ← Home screen
│   ├── library/              ← Library screen (revamped)
│   │   ├── LibraryScreen.kt
│   │   ├── LibraryViewModel.kt
│   │   ├── LibraryStore.kt       ← AniList-keyed bookmark persistence
│   │   ├── CategoryStore.kt      ← User categories (SharedPreferences)
│   │   └── LibraryDisplayPrefs.kt ← Display customization (persisted)
│   ├── history/              ← History screen (revamped)
│   │   ├── HistoryScreen.kt
│   │   └── HistoryViewModel.kt
│   ├── search/               ← Search screen (revamped)
│   │   ├── SearchScreen.kt
│   │   └── SearchViewModel.kt
│   ├── detail/               ← Detail screen + source linking
│   │   ├── DetailScreen.kt
│   │   ├── DetailViewModel.kt
│   │   ├── SourceLinkingScreen.kt ← Extension→AniList linking
│   │   ├── SourceDetailScreen.kt  ← Fallback (extension-only detail)
│   │   ├── VideoPickerSheet.kt
│   │   ├── VideoTitleParser.kt
│   │   ├── EpisodeTitleParser.kt
│   │   └── DynamicTheming.kt
│   ├── settings/             ← Settings (28 files)
│   │   ├── SettingsHomeScreen.kt
│   │   ├── LibrarySettingsScreen.kt
│   │   ├── HistorySettingsScreen.kt
│   │   ├── SearchSettingsScreen.kt
│   │   ├── PlayerSettingsScreen.kt (+ 4 subpages)
│   │   ├── SelectableOptionCard.kt
│   │   └── ...
│   └── debug/DebugScreen.kt
│
└── util/
    ├── lang/Hash.kt
    ├── system/ChildFirstPathClassLoader.kt
    └── storage/FileExtensions.kt
```

---

## Key Data Flows

### Library
```
DetailScreen bookmark toggle
  → DetailViewModel.toggleSaved()
  → LibraryStore.save(anime)          [SharedPreferences JSON map]
  → LibraryStore.changes Flow
  → LibraryViewModel collects → LibraryState.Success
  → LibraryScreen recomposes

CategoryStore (SharedPreferences)      ← user-created categories
LibraryDisplayPrefs (SharedPreferences) ← display customization (persisted)
WatchProgressStore.changes Flow        ← unwatched count badges
SubDubStore.changes Flow               ← SUB/DUB badges
```

### History
```
PlayerActivity.saveProgress()
  → WatchProgressStore.save()          [position + coverUrl + thumbnailUrl]
  → PlaybackStateStore.save()          [videoUrl + server + audio + quality]
  → WatchProgressStore.changes Flow
  → HistoryViewModel collects → HistoryState.Success

History tap → PlaybackStateStore cache hit?
  → YES: PlayerActivity.newIntent(saved videoUrl + coverUrl + animeTitle)
  → NO: navigate to detail/$anilistId?autoPlayUrl=$episodeUrl
```

### Search
```
User types + presses Enter
  → SearchViewModel.onSubmit()
  → doSearch(query) → AniListRepository.searchAnime()
  → SearchState.Success(anime)
  → ResultsGrid (infinite scroll pagination)

Extension mode + no query
  → loadExtensionBrowse() → fetchPopularFromAllSources() + fetchLatestFromAllSources()
  → ExtensionBrowseSection (Popular + Latest horizontal rows)

Filter Apply
  → loadAniListBrowse() → getTrending() → applyFilters() (client-side)
```

### Extension → AniList Linking
```
Extension result tap
  → Check ExtensionLinkStore cache
  → HIT: navigate to detail/$anilistId (instant)
  → MISS: SourceLinkingScreen
    → searchAnime(title) [without adult filter]
    → searchAnimeWithAdult(title) [if no results]
    → Found: link + navigate to detail/$anilistId
    → Not found: show results list + manual search field
```

---

## Navigation Routes

| Route | Screen | Purpose |
|-------|--------|---------|
| `/` (bottom nav) | HomeScreen | Trending/popular anime |
| `library` | LibraryScreen | Saved anime (grid/list) |
| `history` | HistoryScreen | Watch history + continue watching |
| `search` | SearchScreen | AniList + extension search |
| `more` | MoreScreen | Settings + debug |
| `detail/{anilistId}` | DetailScreen | Anime detail + episodes |
| `detail/{anilistId}?autoPlayUrl={url}` | DetailScreen | Detail + auto-play episode |
| `source-link/{sourceId}/{animeUrl}/{title}/{thumb}` | SourceLinkingScreen | Extension→AniList linking |
| `settings/*` | Various settings screens | Library/History/Search/Player/etc. settings |
| `debug` | DebugScreen | Hidden debug screen |

---

## Key Stores (SharedPreferences-backed)

| Store | Key | Purpose |
|-------|-----|---------|
| `LibraryStore` | `pref_library_saved_anime` | Saved AniList anime (JSON map) |
| `CategoryStore` | `pref_library_categories` + `pref_library_category_assignments` | User categories + anime→category |
| `LibraryDisplayPrefs` | `lib_display_mode`, `lib_grid_columns`, etc. | Display customization (persisted) |
| `WatchProgressStore` | `pref_watch_progress_map` | Resume position + cover/thumbnail |
| `PlaybackStateStore` | `pref_playback_state_map` | Last video URL + server + tracks |
| `SubDubStore` | `pref_sub_dub_cache` | Sub/dub availability per anime |
| `ExtensionLinkStore` | `pref_extension_anilist_links` | Extension→AniList ID cache |

---

## Documentation Files

| File | Purpose |
|------|---------|
| `DOCS/DESIGN-SYSTEM.md` | Design tokens, component patterns, DO/AVOID rules |
| `DOCS/EXTENSION-LINKING.md` | Extension→AniList linking flow + architecture |
| `DOCS/REFERENCE-RESEARCH/aniyomi-solutions.md` | How aniyomi solves each problem |
| `DOCS/REFERENCE-RESEARCH/design-language.md` | ANI-KUTA's M3 Expressive design analysis |
| `MEMORY/CORE-RULES.md` | Working rules (understand before acting, etc.) |
| `MEMORY/ERROR-HANDLING-RULES.md` | 7-step error resolution workflow |
| `MEMORY/PLAYER-RULES.md` | Player development rules (MPV lifecycle, tracks, etc.) |
| `MEMORY/SESSION-LOGS/2026-07-15-session-31.md` | This session's log |

---

## Build System

- **GitHub Actions:** `.github/workflows/build-apk.yml`
- **Trigger:** Push to `main` (paths: app/core/data/domain/source-api) + manual dispatch
- **Output:** Debug APK (arm64-v8a), 90-day artifact retention
- **Notification:** `ntfy.sh/TASKISDONE` on success/failure

---

## What Was Done in This Branch (55 commits)

### Phase 0-6 (Foundation → Settings)
- Wired SQLDelight domain layer into DI (4 repos + 33 interactors)
- Made watch progress reactive (Flow)
- Fixed History (real covers, episode thumbnails, resume, dual click)
- Extracted shared M3 Expressive components
- Library: display toggle, categories, unwatched badges
- Search: removed auto-search, wired Enter, pagination, filters, source toggle
- Settings: Library/History/Search settings screens + watch threshold

### Phase A-D (UI Redesign + Features)
- Library UI redesigned (3 display modes, alternating bg, surfaceContainer titles, AudioPills)
- Sub/Dub counts (cached from Detail page)
- Server memory for resume (PlaybackStateStore)
- Source search results clickable → SourceDetailScreen

### Phase E-I (Refinements)
- Library: three-dot settings button → tabbed bottom sheet (Filter/Sort/Display)
- Library: pill categories on page, sort asc/desc, gradient blur, scroll-to-hide
- Library: persisted display settings (survives screen switches)
- History: 16:9 episode thumbnails, direct resume with saved server
- Search: centered toggle (Recent/AniList/Extensions), tabbed filter sheet, multi-select
- Search: AniList trending browse, extension Popular/Latest browse
- Extension linking: auto-search AniList + cache + manual search fallback + adult filter
- Design system document created
- All `primaryContainer` colors removed from player
