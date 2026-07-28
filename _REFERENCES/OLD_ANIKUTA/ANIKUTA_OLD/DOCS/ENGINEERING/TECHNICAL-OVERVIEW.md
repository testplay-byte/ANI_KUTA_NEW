# ANI-KUTA — Technical Overview (Verified)

> **Single source of truth** for the ANI-KUTA codebase, produced by full-codebase
> discovery. Every fact below was verified against actual files on `main` at
> commit `ca644ad` (2026-07-16).
>
> This supersedes the stale claims in `DOCS/NAVIGATION-GUIDE.md`,
> `DOCS/ARCHITECTURE/README.md`, `SETUP/README.md`, and `BUILD-APK/README.md`
> (which still say `app/` "does not exist yet" — it does, and is fully built).
>
> If anything here conflicts with another doc, **this file is correct.**

---

## 1. High-Level Overview

**ANI-KUTA** is an Android **anime streaming app**. It is a **copy** (not a git fork)
of [aniyomi](https://github.com/aniyomiorg/aniyomi) — the anime fork of the
Mihon/Tachiyomi ecosystem. ANI-KUTA selectively copies aniyomi's anime-side
backend (DI, database, source/extension system, MPV player) and rebuilds the
entire UI from scratch in Jetpack Compose.

**Three-source architecture:**
1. **AniList** (GraphQL) — discovery, metadata, tracking, artwork.
2. **aniyomi extensions** (user-installed APKs) — the actual streaming sources that resolve episodes to playable video URLs.
3. **MPV** (native lib) — the video player engine (with FFmpeg for demuxing/decoding).

The app bridges AniList ↔ extensions via fuzzy title matching (`AniyomiSourceBridge`),
so a user browses on AniList, taps an episode, the app finds the matching extension
entry, resolves the stream, and plays it through MPV — with watch progress saved
for resume.

| Field | Value |
|-------|-------|
| Display name | ANI-KUTA |
| Application ID | `app.anikuta` |
| Version | `0.1.0` (versionCode 1) |
| Origin | Copy of aniyomi (commit `2f5cf77`, 2025-11-05) kept read-only in `REFERENCE/` |
| GitHub | https://github.com/testplay-byte/anikuta (test account `testplay-byte`) |
| License | Not yet declared at repo root (aniyomi upstream is Apache 2.0) |
| Status | Phases 0–7 done on `main`. Phase 7.5 (episode list enhancements) is next. Latest verified build `27053e1`. The full download-system rebuild (modular engines, segment resume, foreground service, downloads page) is **already merged into `main`** (via the now-deleted `player-experiment` branch, merge commit `a05d07c`). The only other branch is `live-preview-dashboard` (temporary). |

---

## 2. Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.2.0 |
| Build | Gradle (KTS) + AGP | Gradle 8.13, AGP 8.9.0 |
| JDK | 17 (temurin in CI) | 17 |
| UI | Jetpack Compose (Material 3 Expressive) | BOM 2025.04.01 |
| Min/Compile/Target SDK | 26 / 35 / 35 | Android 8.0+ |
| ABI | `arm64-v8a` only | physical devices only |
| DI | Injekt (Mihon fork, JitPack commit `91edab2317`) | reflection-based, no annotation processor |
| DB | SQLDelight | 2.0.2 (sqlite-3-38 dialect) |
| Networking | OkHttp (core/logging/brotli/dnsoverhttps) | **5.0.0-alpha.14** (pre-release) |
| Serialization | kotlinx.serialization-json | 1.9.0 |
| HTML parsing | Jsoup | 1.19.1 |
| Image loading | Coil 3 (compose + okhttp) | 3.1.0 |
| Background work | WorkManager (work-runtime-ktx) | 2.10.0 |
| SAF storage | UniFile (tachiyomiorg fork, JitPack commit `e0def6b3dc`) | — |
| Drag-and-drop | reorderable (sh.calvin.reorderable) | 2.4.3 |
| Video player (native) | aniyomi-mpv-lib (`com.github.aniyomiorg`, `1.18.n`) + ffmpeg-kit (`com.github.jmir1`, `1.18`) + arthenica smart-exception | — |
| Legacy | RxJava **1.3.8** (EOL since 2018; `api`-leaked from `:core`, still used by extension contract surface) | — |
| Navigation | navigation-compose | 2.8.5 |
| Testing | junit 4.13.2 only — **no test sources exist**, no AndroidX test deps, no instrumented tests | — |

**Dependency risk flags:** OkHttp 5 alpha; RxJava 1 EOL; 4 JitPack commit-hash pins (injekt, unifile, mpv-lib, ffmpeg-kit) with no SemVer/CVE tracking; `androidx.media` catalog entry is dead code; jmir1/ffmpeg-kit is a community fork of an archived project.

---

## 3. Repository Structure (Top-Level)

```
anikuta/
├── .github/workflows/build-apk.yml   ← ONLY CI workflow (debug APK build)
├── .gitignore                         ← secrets + build outputs + REFERENCE-EXTENSIONS
├── README.md                          ← project overview + phase status
├── KNOWN-ISSUES.md                    ← 31 triaged issues (3 CRIT / 4 HIGH / 8 MED / 16 LOW)
├── PLAYER_REDO_PLAN.md                ← MPV init rewrite plan (implemented)
├── STORAGE.md                         ← SAF folder selection architecture
├── SUBTITLES_FIX.md                   ← subtitle rendering root-cause + fix
├── plan.md                            ← Download System Plan v2 (supersedes DOCS/PLAN/DOWNLOAD-PLAN.md)
├── worklog.md                         ← project's session-by-session log (128 KB, 2,389 lines)
├── live-preview.html + live-preview/  ← static HTML progress dashboards
├── backup/                            ← (misc backups)
│
├── app/ core/ data/ domain/ source-api/   ← THE 5 GRADLE MODULES (working code)
├── build.gradle.kts                   ← root build (plugins apply false)
├── settings.gradle.kts                ← 5 module includes + JitPack repo
├── gradle.properties                  ← AndroidX, parallel, 4g heap
├── gradle/                            ← wrapper + libs.versions.toml (catalog)
├── gradlew / gradlew.bat
│
├── MEMORY/                            ← session memory + rules + credentials (gitignored secrets)
├── DOCS/                              ← all documentation (ENGINEERING/ subfolder = this file)
├── SETUP/                             ← env setup guide (STALE)
├── BUILD-APK/                         ← APK output folder (binaries gitignored) (STALE readme)
├── REFERENCE/                         ← PRISTINE aniyomi copy (1,988 files, READ-ONLY, never edit)
└── REFERENCE-STAGING/                 ← empty (.gitkeep only) — for upstream review
```

---

## 4. The 5 Gradle Modules

```
app → core, data, domain, source-api
data → core, domain, source-api
domain → core, source-api
source-api → core
core → (no internal deps)
```

### 4.1 `:app` — the Android application (147 .kt files, ~38,588 LoC)
Namespace `app.anikuta`. The UI + player + download + extensions + DI glue.
Plugins: `android.application`, `kotlin.android`, `kotlin.compose`, `kotlin.serialization`.
Build types: `debug` (signed by committed `debug.keystore`), `release` (unsigned, minify off), `release-debuggable` (unsigned, debuggable).
See **§5** for the full package map.

### 4.2 `:core` — shared foundation (29 .kt files, ~1,788 LoC)
Namespace `app.anikuta.core`. **No internal module dependencies.** Two root packages:
- `app/anikuta/core/` — preference system (`AndroidPreference` sealed class + Flow-backed reactive `PreferenceStore`), storage helpers (`FolderProvider`, `AndroidStorageFolderProvider`), `Constants`, util (coroutines, RxJava bridge, logcat).
- `eu/kanade/tachiyomi/` — network layer kept under the original aniyomi package: `NetworkHelper` (4 interceptors + 13 DoH providers), `OkHttpExtensions`, `JsoupExtensions`, `CloudflareInterceptor` (commented out). **The `core.md` doc falsely claims this was renamed — it was not.**
- `api`-exposes: okhttp, okio, rxjava, logcat, coroutines, serialization, preference-ktx (so every consumer transitively receives these — including RxJava 1).

### 4.3 `:data` — SQLDelight local DB + repository implementations (25 .kt files, ~2,101 LoC)
Namespace `app.anikuta.data`. Applies the `sqldelight` plugin.
- `.sq` schema: **18 files (10 tables + 8 views), 947 LoC** under `src/main/sqldelight/{dataanime,view}/`. DB name `tachiyomi.animedb` (leftover aniyomi string). Includes `anilistcache.sq` (undocumented).
- **ZERO `.sqm` migration files** despite `data.md` claiming "23 migrations (113–135)". **CRITICAL: any future schema change will wipe user data unless migrations are added.**
- 11 repository implementations, all implementing `:domain` interfaces via the `AnimeDatabaseHandler` abstraction (interface + single `AndroidAnimeDatabaseHandler` impl).
- `api`-exposes sqldelight drivers + injekt.
- `consumer-rules.pro` exists but is **empty (0 bytes)**.
- **Division of labor with `:app`'s `data/` package:** `:data` = LOCAL persistence (SQLDelight); `:app` `data/` = REMOTE sources (AniList/Supabase/tracker) + SharedPrefs caches + `CacheManager` that orchestrates a 3-step cache (Local `:data` → Supabase → AniList).

### 4.4 `:domain` — models + repository interfaces + interactors (121 .kt files, ~4,752 LoC)
Namespace `app.anikuta.domain`. Depends on `:core`, `:source-api`.
- **64 interactors** in 14 sub-packages (NOT ~33 as some docs claim): anime, category, entries, history, items/episode, items/season, library, mihon/extensionrepo, release, source, track, updates, etc.
- **11 repository interfaces.**
- 4 preference-service classes (`LibraryPreferences`, `DownloadPreferences`, `BackupPreferences`, `PreferenceValues`), all depending on `:core.PreferenceStore`.
- Key model: `Anime.kt` (345 LoC, 27 fields, ~250 LoC of bit-packed `episodeFlags`/`seasonFlags`/`viewerFlags` constants — dense, fragile, untested).
- **Manga code leaks despite Decision D2 (anime-only):** `domain/mihon/extensionrepo/manga/` is a full 7-file dead subpackage; `LibraryPreferences`/`DownloadPreferences` carry ~20 manga/chapter getters + a `ChapterSwipeAction` enum nothing reads.
- Uses `compileOnly` Compose (BOM + ui) purely for `@Immutable`/`@Stable` annotations — **couples the domain layer to the UI framework** (anti-pattern).
- Name collision: `AnimeSource` exists both as the `:source-api` contract interface AND as a `:domain` DB data class — confusing for IDE auto-import.

### 4.5 `:source-api` — the extension contract (28 .kt files, ~1,908 LoC)
Namespace `app.anikuta.source.api`. Depends on `:core`. **This is the binary interface aniyomi extensions compile against — must not break.**
- Two root packages co-exist deliberately:
  - `eu/kanade/tachiyomi/animesource/` (25 real contract files) — kept under the aniyomi name because **extensions are compiled against these binary names**. Renaming breaks extension classloading (this was learned the hard way in Phase 5 and reverted).
  - `app/anikuta/source/api/` (3 typealias files, 65 LoC) — typealiases so app code can use the `app.anikuta.*` names while the real classes stay `eu.kanade.*`.
- **Source type hierarchy:**
  - `AnimeSource` (interface) — suspend getters for `getDetails`, `getEpisodes`, `getSeasonList`, `getHosterList`, `getVideoList(hoster)`.
  - `AnimeCatalogueSource` : `AnimeSource` — adds `popular`, `search`, `latest`, `filters`.
  - `AnimeHttpSource` (abstract, 672 LoC) : `AnimeCatalogueSource` — OkHttp-based, MD5 id.
  - `ParsedAnimeHttpSource` : `AnimeHttpSource` — Jsoup selector helpers.
  - Mixins: `ConfigurableAnimeSource`, `ResolvableAnimeSource`, `UnmeteredSource`, `AnimeSourceFactory`.
  - ext-lib 16 adds season-based series: `getSeasonList`/`getHosterList`/`getVideoList(hoster)` + `FetchType` enum.
- Models: `SAnime`, `SEpisode`, `Video`, `Hoster`, `AnimeFilter`, `AnimeFilterList`, `AnimeUpdateStrategy`, `FetchType`, `AnimesPage`.
- `api`-exposes injekt + jsoup; `implementation` Compose (BOM + ui) for `@Stable` on `AnimeFilterList` — same UI-coupling smell as `:domain`.

---

## 5. `:app` Module — Package Map (Verified)

Path: `app/src/main/java/app/anikuta/`. 147 .kt files, ~38,588 LoC.

| Package | Files | Responsibility | Key classes |
|---------|------:|----------------|-------------|
| (root) | 2 | App lifecycle, launcher, OAuth | `App` (100 LoC), `MainActivity` (85 LoC) |
| `di/` | 3 (396 LoC) | Injekt wiring | `AppModule` (226 LoC, **56 singletons**), `DomainModule` (154 LoC, 4 repos + ~32 interactors), `PreferenceModule` (16 LoC) |
| `data/` | 10 | **App-local REMOTE data sources** (NOT a duplicate of `:data` module): AniList GraphQL, Supabase, tracker, episode metadata + SharedPrefs caches | `AniListRepository`, `CacheManager` (3-step), `EpisodeMetadataFetcher` (458 LoC), `SupabaseClient`, `AniListTracker`, `EpisodeCacheStore`, `SubDubStore`, `ExtensionLinkStore` |
| `domain/` | 2 | **Anikuta-specific domain overrides** diverging from aniyomi | `SourcePreferences` (trust-based, max 2 trusted), `TrustAnimeExtension` |
| `download/` | 16 | Episode download manager + 3 pluggable engines | `DownloadManager` (521 LoC), `DownloadWorker`, `DownloadStore`, `DownloadManifest` (360 LoC), `ProgressTracker`, engines: `SinglePass`/`Hls`/`Segment` + `hls/` subpackage (5 files) |
| `error/` | 2 | Global crash UX | `AnikutaCrashHandler`, `ErrorActivity` |
| `extension/` | 6 | APK extension loader/manager | `AnimeExtensionManager`, `AnimeExtensionApi`, `AnimeExtensionLoader` (in `util/`), `InstallStep`, `ExtensionUpdateNotifier` |
| `navigation/` | 1 | Compose NavHost | `AnikutaNavGraph` (451 LoC, **31 routes** — not 10 as docs claim) |
| `onboarding/` | 2 | First-launch 7-step wizard | `OnboardingScreen` (980 LoC), `OnboardingState` |
| `player/` | 20 | **MPV player subsystem (biggest, most fragile)** | see §6 |
| `source/` | 4 | Source manager impl + aniyomi bridge | `AndroidAnimeSourceManager`, `AniyomiSourceBridge` (fuzzy title match + source priority), `TitleMatcher` |
| `storage/` | 2 | SAF folder selection | `StorageManager`, `StoragePreferences` |
| `ui/` | 75 | All Compose screens | see §7 |
| `util/` | 6 | Misc helpers | `Hash`, `FileExtensions`, `DiskUtil`, `FFmpegUtils`, `ChildFirstPathClassLoader` |

---

## 6. The Player Subsystem (Critical Risk Area)

The player is the single biggest modularization problem. It has an **inverted responsibility pattern** vs. the rest of the app: everywhere else the `ViewModel` holds logic and the Screen is dumb; in the player, `PlayerActivity` does ALL the work and `PlayerViewModel` is just a state bag.

| Layer | File | LoC | Role |
|-------|------|----:|------|
| Android host | `PlayerActivity.kt` | **2,430** | Activity lifecycle + MPV native surface ownership + **ALL business logic**: episode switching, video resolution, track auto-select, AniList sync, PiP, audio focus, screenshot, sleep timer. **God object.** |
| Compose UI | `PlayerScreen.kt` | 1,048 | Top-level Composable (minimized + fullscreen); almost the whole file is one Composable. |
| State holder | `PlayerViewModel.kt` | 437 | Pure state bag — 33 setter/getter functions, **NO business logic**. |
| MPV wrapper | `AnikutaMPVView.kt` | 374 | `SurfaceView` subclass wrapping `MPVLib`; track API + property getters. |
| Config | `MpvConfigManager.kt` | 156 | `object`; copies assets to mpvDir. |
| Observer | `PlayerObserver.kt` | 80 | Bridges MPV lib events → Activity callbacks. |
| Stores | `WatchProgressStore.kt` (160), `PlaybackStateStore.kt` (105) | — | Resume position + last playback state (both SharedPrefs, reactive Flows). |
| Preferences | `PlayerPreferences.kt` (294), `PlayerEpisodePreferences.kt` (67) | — | Player + episode-display prefs. |
| Controls | 11 files in `controls/` + 2 in `controls/sheets/` | — | `FullscreenControls` (357), `MinimizedControls` (523), `PlayerControls` (264 dispatcher), `EpisodeListView` (**850**), `SubtitleSettingsPanel` (522), `NumericKeypad` (278), `PlayerGestureHandler`, `EpisodeSwitchingOverlay`, `ColorPickerDialog`, `ServerVersionDropdowns` (224), `FirstTimePlayerPrompt` (228), `sheets/PlayerSheet` (94), `sheets/PlayerSheets` (560). |

**Known fragile areas (from worklog history):** MPV native lifecycle (init-once-per-process, re-init SIGABRTs), subtitle rendering (broke multiple times — fake HTML font, sid getter, arg order), stale headers / race conditions in video resolution (Session 44 found 3 CRITICAL races), `PlayerActivity` grew from ~200 LoC (Session 19) to 2,430 LoC (Session 44).

---

## 7. `:app` `ui/` — Compose Screens (75 files)

| Folder | Pattern | Notable files |
|--------|---------|---------------|
| `ui/components/` | Shared composables | `ExpressiveCard`, `FloatingTopBar`, `SectionHeader`, `SkeletonBox` (4 files) |
| `ui/theme/` | M3 Expressive theme | `Theme.kt`, `Expressive.kt` (springs + typography), `Color.kt` |
| `ui/home/` | Screen + ViewModel | `HomeScreen`, `HomeViewModel` (192 LoC) |
| `ui/library/` | Screen + ViewModel + Store + Prefs (canonical pattern) | `LibraryScreen` (1,199 LoC), `LibraryViewModel`, `LibraryStore`, `CategoryStore`, `LibraryDisplayPrefs` |
| `ui/history/` | Screen + ViewModel (no Store — uses `WatchProgressStore` from `player/`) | `HistoryScreen` (566 LoC), `HistoryViewModel` |
| `ui/search/` | Screen + ViewModel | `SearchScreen` (1,363 LoC), `SearchViewModel` (571 LoC) |
| `ui/detail/` | Screen + ViewModel + helpers | `DetailScreen` (**1,695 LoC**), `DetailViewModel` (**1,313 LoC**), `SourceLinkingScreen`, `SourceDetailScreen`, `VideoPickerSheet`, `VideoTitleParser`, `EpisodeTitleParser`, `DynamicTheming`, `ThreeStagePullRefresh` |
| `ui/download/` | Screen (+ ViewModel) | `DownloadQueueScreen` (808 LoC) — **undocumented in DOCS/ARCHITECTURE.md** |
| `ui/settings/` | 32 files (not 28) | `SettingsHomeScreen`, `MoreScreen`, `LibrarySettingsScreen`, `HistorySettingsScreen`, `SearchSettingsScreen`, `PlayerSettingsScreen` (+ 4 subpages), `ExtensionsSettingsScreen` (834 LoC), `SelectableOptionCard`, `StyledSegmentedRow`, 4 ViewModels, … |
| `ui/debug/` | Screen + ViewModel | `DebugScreen`, `DebugViewModel` (210 LoC) |

**Inconsistent feature-folder pattern:** `library/` has Screen+ViewModel+Store+Prefs (5 files); `history/`/`search/` have only Screen+ViewModel (no Store); `home/` has Screen+ViewModel but no Store; `detail/` has Screen+ViewModel+helpers but no Store (uses `LibraryStore` cross-package). A canonical pattern should be chosen.

---

## 8. Key Data Flows

### Library (bookmark)
```
DetailScreen bookmark toggle
  → DetailViewModel.toggleSaved()
  → LibraryStore.save(anime)           [SharedPreferences JSON map, key pref_library_saved_anime]
  → LibraryStore.changes Flow
  → LibraryViewModel collects → LibraryState.Success
  → LibraryScreen recomposes
```
CategoryStore + LibraryDisplayPrefs (SharedPrefs) + WatchProgressStore.changes (unwatched badges) + SubDubStore.changes (SUB/DUB badges) all feed the Library.

### History (resume)
```
PlayerActivity.saveProgress()
  → WatchProgressStore.save()          [position + coverUrl + thumbnailUrl]
  → PlaybackStateStore.save()          [videoUrl + server + audio + quality]
  → WatchProgressStore.changes Flow
  → HistoryViewModel collects → HistoryState.Success

History tap → PlaybackStateStore cache hit?
  → YES: PlayerActivity.newIntent(saved videoUrl + coverUrl + animeTitle)
  → NO:  navigate to detail/$anilistId?autoPlayUrl=$episodeUrl
```

### Search
```
User types + Enter
  → SearchViewModel.onSubmit() → doSearch() → AniListRepository.searchAnime()
  → SearchState.Success → ResultsGrid (infinite scroll pagination)

Extension mode + no query
  → loadExtensionBrowse() → fetchPopularFromAllSources() + fetchLatestFromAllSources()
  → ExtensionBrowseSection (Popular + Latest horizontal rows)
```

### Extension → AniList linking
```
Extension result tap
  → Check ExtensionLinkStore cache
  → HIT:  navigate to detail/$anilistId (instant)
  → MISS: SourceLinkingScreen
    → searchAnime(title) [without adult filter]
    → searchAnimeWithAdult(title) [if no results]
    → Found: link + navigate to detail/$anilistId
    → Not found: show results list + manual search field
```

### 3-tier cache (homepage/details)
```
CacheManager.get(key)
  → LocalCache (:data SQLDelight)  [TTL check]
  → MISS: SupabaseClient           [shared homepage cache]
  → MISS: AniListRepository        [source of truth]
  → write-back to Local + Supabase
```

---

## 9. Navigation Routes (31 total)

| Route | Screen |
|-------|--------|
| `/` (bottom nav) | HomeScreen |
| `library` | LibraryScreen |
| `history` | HistoryScreen |
| `search` | SearchScreen |
| `more` | MoreScreen (lives in `ui/settings/`) |
| `detail/{anilistId}` | DetailScreen |
| `detail/{anilistId}?autoPlayUrl={url}` | DetailScreen (auto-play) |
| `source-link/{sourceId}/{animeUrl}/{title}/{thumb}` | SourceLinkingScreen |
| `debug` | DebugScreen |
| `extension_repos` | Extension repos management |
| `extension_details/{pkgName}` | Extension details |
| `source_preferences/{sourceId}` | Source prefs (PreferenceFragmentCompat) |
| `settings/*` (23 sub-routes) | Library/History/Search/Player/Extensions/Downloads/General/About settings + subpages |

All defined in a single `AnikutaNavGraph.kt` (451 LoC).

---

## 10. SharedPreferences-Backed Stores

| Store | Key | Purpose |
|-------|-----|---------|
| `LibraryStore` | `pref_library_saved_anime` | Saved AniList anime (JSON map) |
| `CategoryStore` | `pref_library_categories` + `pref_library_category_assignments` | User categories + anime→category |
| `LibraryDisplayPrefs` | `lib_display_mode`, `lib_grid_columns`, … | Display customization (persisted) |
| `WatchProgressStore` | `pref_watch_progress_map` | Resume position + cover/thumbnail |
| `PlaybackStateStore` | `pref_playback_state_map` | Last video URL + server + tracks |
| `SubDubStore` | `pref_sub_dub_cache` | Sub/dub availability per anime |
| `ExtensionLinkStore` | `pref_extension_anilist_links` | Extension→AniList ID cache |

---

## 11. Build Process

### How the app is built
1. **Toolchain:** Gradle 8.13 wrapper, AGP 8.9.0, Kotlin 2.2.0, JDK 17.
2. **Command:** `./gradlew assembleDebug` (the only command CI runs).
3. **Output:** `app/build/outputs/apk/debug/app-debug.apk` — a single `arm64-v8a` APK (no ABI splits).
4. **Native libs packaged:** MPV + FFmpeg (libavcodec/libavformat/libavutil/libmpv/libffmpegkit/libswresample/libswscale/libpostproc/libxml2/libc++_shared/…) — kept via `packaging.jniLibs.keepDebugSymbols`.
5. **SQLDelight** generates DB code at build time via the `sqldelight` Gradle plugin on `:data` (DB name `AnimeDatabase`, package `app.anikuta.data`, sqlite-3-38 dialect, schema output to `src/main/sqldelight`).
6. **Compose** uses the new Kotlin 2.2 Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose`), not legacy `composeOptions`.
7. **Injekt** is reflection-based — no `kapt`/`ksp` anywhere. Good.
8. **Minify is OFF** in all 3 build types (`debug`, `release`, `release-debuggable`). ProGuard files are effectively empty.

### How APKs are generated (locally)
- Debug: `./gradlew assembleDebug` → signed with committed `app/debug.keystore` (alias `debug`, passwords `android`). Installable directly.
- Release: `./gradlew assembleRelease` → **UNSIGNED** (no `signingConfig` on the release build type). Not installable as-is.
- release-debuggable: `./gradlew assembleReleaseDebuggable` → also unsigned, but debuggable. (The build-file comment claiming "R8 optimization" is misleading — `isMinifyEnabled=false` means R8 doesn't run.)

### Signing state (IMPORTANT)
- `app/debug.keystore` is **committed** (force-added in commit `8ed9a2c`, bypassing `.gitignore`). Safe because it's a debug key.
- **No release keystore exists** anywhere in the tree. `MEMORY/CREDENTIALS/keystore/` is gitignored and empty.
- `SETUP/README.md` and `BUILD-APK/README.md` signing sections are **STALE** (describe a TODO env-var plan that was never implemented; omit the third `release-debuggable` variant).

---

## 12. GitHub Actions / CI-CD

**Single workflow:** `.github/workflows/build-apk.yml`. The `.github/` folder has nothing else (no CODEOWNERS, no ISSUE_TEMPLATE, no dependabot, no PR template).

### Workflow detail
- **Name:** "Build APK". Single job `build` on `ubuntu-latest`.
- **Triggers:**
  - `push` to `main` with path filter (`app/**`, `core/**`, `data/**`, `domain/**`, `source-api/**`, `gradle/**`, root gradle files, the workflow file).
  - `pull_request` to `main` — **no path filter** (runs on every PR touch).
  - `workflow_dispatch` (manual).
- **Steps:**
  1. `actions/checkout@v4`
  2. `actions/setup-java@v4` → temurin 17
  3. `android-actions/setup-android@v3` → accept licenses
  4. `actions/cache@v4` → paths `~/.gradle/caches` + `~/.gradle/wrapper`; key `gradle-${{ runner.os }}-${{ hashFiles('gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties', '**/*.gradle.kts') }}`
  5. `chmod +x gradlew`
  6. `./gradlew assembleDebug --stacktrace`
  7. `actions/upload-artifact@v4` → name `anikuta-debug-arm64-v8a`, path `app/build/outputs/apk/debug/*.apk`, `retention-days: 90`, `if-no-files-found: error`
  8. `ntfy.sh` notify on `always()` → topic `TASKISDONE` (public)

### CI gaps and risks (high-priority list)
1. **No release workflow** — CI only ever builds debug.
2. **No release signing in CI** — release builds are unsigned even if built.
3. **No test job** — `./gradlew test` never runs (and no test sources exist anyway).
4. **No lint job** — no `lint`/`detekt`/`ktlint`/`spotless`. No static-analysis gate.
5. **No required status checks** — PR merges aren't blocked on CI.
6. **Cache key fragility** — `**/*.gradle.kts` glob also matches `REFERENCE/**/*.gradle.kts` (committed aniyomi snapshot) → unrelated edits invalidate the cache.
7. **Single-arch build** — only `arm64-v8a`; no x86_64 for emulator CI.
8. **No Dependabot/Renovate** — dependency drift invisible.
9. **No coverage / size tracking / version-bump automation.**
10. **ntfy topic is public** — anyone can observe build status/SHAs.
11. **PR trigger lacks path filter** — a docs-only PR re-runs the full APK build.

---

## 13. Aniyomi Reference (READ-ONLY)

`REFERENCE/` holds a pristine, **read-only** copy of aniyomi at commit `2f5cf77` (2025-11-05), 1,988 files, ~24 MB, shallow clone (no `.git` history). Never edit, never build from it.

**Aniyomi's 13 modules** (from `REFERENCE/settings.gradle.kts`):
`:app`, `:core:archive`, `:core:common`, `:core-metadata`, `:data`, `:domain`, `:i18n`, `:i18n-aniyomi`, `:macrobenchmark`, `:presentation-core`, `:presentation-widget`, `:source-api`, `:source-local`.

**ANI-KUTA keeps 5** (`:app`, `:core`, `:data`, `:domain`, `:source-api`) and **drops 8**:
- `:core:archive`, `:core-metadata` — manga archive/metadata (add later if local anime file support needed).
- `:i18n`, `:i18n-aniyomi` — ANI-KUTA does its own strings (currently only `app_name`, no localization).
- `:macrobenchmark` — benchmarks not needed yet.
- `:presentation-core`, `:presentation-widget` — ANI-KUTA builds its own 4-design UI; no widgets.
- `:source-local` — local source (add later if local anime file support needed).

`REFERENCE-STAGING/` is empty (`.gitkeep` only) — no pending upstream review. The documented "monthly upstream tracking" cadence (DOCS/PLAN/UPSTREAM-TRACKING.md) has never been executed.

---

## 14. Project History Arc (condensed from worklog.md)

- **Sessions 1–14 (Phases 0–5):** Foundation → first end-to-end streaming. 5-module Gradle project, GitHub Actions, Injekt DI, M3 theme, 7-step onboarding, AniList GraphQL client, 3-step cache, home/detail/library/history/search, MPV player (minimal 7-file from aniyomi's 64), source wiring + fuzzy matching + progress resume.
- **Sessions 15–17 (Phase 6):** Settings reorg, player UX, extension mgmt, AniList tracking (OAuth client ID 5338, borrowed from aniyomi), downloads (WorkManager + HTTP Range), polish.
- **Session 18:** Sandbox wipe; restored from GitHub + user creds.
- **Session 19:** Player pipeline fixes (5 root causes for non-AniKoto playback: URL host crash, TLS, 403/headers, error surfacing, stale header).
- **Session 20:** User-verified on-device — play, seek, resume all work.
- **Sessions 21–27 (Phase 7):** Extension trust system, repo mgmt, settings UI, episode caching, 3-stage pull-to-refresh, video picker redesign, downloads drag-and-drop priority, floating nav, onboarding permissions.
- **Session 28–31:** Episode metadata, dynamic theming, settings redesign, Library/History/Search revamp (merged via `library-history-search-revamp` branch).
- **Sessions 28–44+ (player iteration):** Long sequence of player UI refinement (seekbar, quality/server/audio switching, subtitle settings, PiP, subtitle root-cause fix, deep code review with 3 CRITICAL + 5 HIGH + 2 MEDIUM fixes).
- **Sessions 44+ (download system redesign):** Full modular download architecture — segment engine, foreground service, manifest-based resume, notifications, HLS direct engine, downloads page (4 redesigns). **This work was on a `player-experiment` branch which has since been MERGED into `main` (merge commit `a05d07c`) and the branch deleted.** Everything is on `main` now.

**Recurring fragile areas:** oversized `PlayerActivity.kt` (flagged repeatedly, never refactored), MPV native lifecycle, subtitle rendering, download StateFlow reactivity, sandbox resets, stale headers/race conditions.

---

## 15. Documentation State (Honest Assessment)

The project has ~80 markdown docs — genuinely impressive depth. But:

- **No single "current state" doc.** README phase table, ROADMAP, PROJECT-CONTEXT, CORE-RULES §0, and worklog all disagree on what's done.
- **6 docs make the stale claim** that `app/` "does not exist yet" (NAVIGATION-GUIDE, ARCHITECTURE/README, SETUP, BUILD-APK, CORE-RULES §0).
- **Security issue:** Supabase DB password committed in plaintext at `DOCS/APP/STRUCTURE/supabase-schema.md` line 130.
- **Recent activity is only in `worklog.md`** — `MEMORY/SESSION-LOGS/` stops at Session 31; the entire `player-experiment` download rebuild (12+ task iterations) is undocumented in structured logs.
- **Session-number collisions** in both `worklog.md` (Sessions 21, 22, 23, 28 each appear twice) and `MEMORY/SESSION-LOGS/` (two `session-31` files).
- **ROADMAP.md has duplicate Phase 9 entries** and no Phase 10.
- **Missing standard docs:** no CONTRIBUTING, no RELEASE, no TESTING-PLAN, no module-level READMEs, no ADR status tracking, no dependency-update policy, no CI explainer, no LICENSE at repo root.
- **DOCS/APP/HOW-TO/ and DOCS/APP/RATIONALE/** are referenced in `DOCS/APP/README.md` but don't exist on disk.

See `MODULARIZATION-ASSESSMENT.md` §"Documentation debt" for the full prioritized list.

---

## 16. Where to Look for What (Quick Index)

| If you need to… | Go to |
|-----------------|-------|
| Understand the working rules | `DOCS/ENGINEERING/WORKING-RULES.md` + `MEMORY/CORE-RULES.md` |
| Find a screen | `app/src/main/java/app/anikuta/ui/<feature>/` |
| Find a ViewModel | same folder as its screen, `<Feature>ViewModel.kt` |
| Find the player | `app/.../player/` (start at `PlayerActivity.kt`) |
| Find DI wiring | `app/.../di/AppModule.kt` (56 singletons) + `DomainModule.kt` |
| Find the DB schema | `data/src/main/sqldelight/{dataanime,view}/*.sq` |
| Find a repository impl | `data/src/main/java/app/anikuta/data/<area>/` |
| Find a domain interactor | `domain/src/main/java/app/anikuta/domain/<area>/interactor/` |
| Find the extension contract | `source-api/src/main/java/eu/kanade/tachiyomi/animesource/` |
| Find navigation routes | `app/.../navigation/AnikutaNavGraph.kt` |
| Find a cache/store | `app/.../data/cache/` (app-local) or `:data` `LocalCache` (SQLDelight) |
| Find the CI workflow | `.github/workflows/build-apk.yml` |
| Find dependencies | `gradle/libs.versions.toml` |
| Find aniyomi reference code | `REFERENCE/` (read-only) |
| Find what changed recently | `worklog.md` (project's own log) — read the tail |
| Find past decisions | `MEMORY/DECISIONS/README.md` (46 ADR-style decisions) |
| Find known issues | `KNOWN-ISSUES.md` (31 triaged issues) |

---

_Last verified: 2026-07-16 against `main` @ `ca644ad`. Update this file whenever structure, build, or CI changes._
