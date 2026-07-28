# EVID-04 — Module dependency graph (all 41 declared Gradle modules)

**Task ID:** EVID-04-MODULE-DEPS
**Agent:** Explore (research-only — no code modified)
**Scope:** `/home/z/my-project/anikuta/ANIKUTA_PROJECT/ANIKUTA/`
**Method:** Read every `build.gradle.kts` (40 files — `:i18n` has no directory) + `settings.gradle.kts` + 4 convention plugins in `buildSrc/` + 5 version catalogs in `gradle/`. Then grep'd every feature module's `import` statements for cross-feature references.

---

## 0. Module inventory (verified from `settings.gradle.kts`)

`settings.gradle.kts` declares **41 modules** (including the phantom `:i18n`):

| Layer | Count | Modules |
|---|---|---|
| `:app` | 1 | `:app` |
| `:core:*` | 15 | common, designsystem, network, database, preferences, anilist, tracker, episode-metadata, source-api, source-local, player, update-checker, download, notification, backup |
| `:data:*` | 5 | anime, manga, extension, tracker, history |
| `:feature:*` | 19 | home, library, updates, history, browse, search, my, more, anime-details, episode-list, episode-settings, video-resolver, watch, player, extensions-settings, settings, trackers, backup, download |
| `:i18n` | 1 | declared at `settings.gradle.kts:85`; **directory does NOT exist on disk** (confirmed via `ls`); zero build.gradle.kts; nothing depends on it |

**Total = 1 + 15 + 5 + 19 + 1 = 41 ✓**

**Build-script count:** 40 `build.gradle.kts` files exist on disk (the 41st, `:i18n`, has no directory and no build script). Plus `buildSrc/build.gradle.kts` + the root `build.gradle.kts`.

> Note: ARCHITECTURE.md §3's printed module tree is **stale** — it omits `:core:tracker`, `:core:update-checker`, and `:feature:episode-settings`, all of which exist on disk and are non-trivial modules. (Already flagged in prior worklog AUDIT-FEATURES §7.6.)

---

## 1. Complete module dependency graph

Format: each module lists its `implementation(project(...))` + `api(project(...))` dependencies. **There are no `api(project(...))` dependencies anywhere in the codebase** — every project-to-project dep is `implementation(...)`. (Only external libs occasionally use `api(...)` — e.g. `:core:source-api` exposes OkHttp + Injekt via `api(...)`, and `:core:player` exposes the MPV lib via `api(...)`.)

### 1.1 `:app` — composition root (31 project deps)

```
:app
  → :core:common
  → :core:designsystem
  → :core:database
  → :core:anilist
  → :core:preferences
  → :core:player
  → :core:update-checker
  → :core:source-api
  → :core:tracker
  → :core:download
  → :core:episode-metadata
  → :core:backup
  → :data:anime
  → :data:extension
  → :data:history
  → :feature:browse
  → :feature:search
  → :feature:anime-details
  → :feature:library
  → :feature:extensions-settings
  → :feature:video-resolver
  → :feature:watch
  → :feature:player              (empty stub — see §7)
  → :feature:episode-settings
  → :feature:settings
  → :feature:history
  → :feature:updates
  → :feature:my
  → :feature:trackers
  → :feature:download
  → :feature:backup
```

Breakdown: **12 core + 3 data + 16 feature = 31 project deps.** Plus 7 inline non-catalog coordinates (`com.squareup.okhttp3:okhttp:5.0.0-alpha.14`, `org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0`, `com.github.mihonapp:injekt:91edab2317`, `androidx.lifecycle:lifecycle-runtime-compose:2.8.7`), plus catalog entries (`libs.bundles.koin`, `libs.bundles.voyager`, `libs.bundles.test`, `kotlinx.coroutines.android`, `androidx.lifecycle.runtimektx`, `androidx.lifecycle.viewmodel.compose`, `libs.logcat`).

### 1.2 `:core:*` layer

```
:core:common                    (0 deps — leaf primitive)
:core:designsystem              → :core:common
                                → :core:preferences
:core:network                   (0 deps — empty stub)
:core:database                  (0 deps — leaf primitive; sqldelight plugin)
:core:preferences               (0 deps — leaf primitive)
:core:anilist                   → :core:common
                                → :core:preferences
:core:tracker                   → :core:common
                                → :core:database
                                → :core:preferences
                                → :core:anilist
                                → :core:player
:core:episode-metadata          → :core:common
                                → :core:preferences
:core:source-api                (0 deps — leaf primitive; Aniyomi contract)
:core:source-local              (0 deps — empty stub)
:core:player                    → :core:common
                                → :core:designsystem
                                → :core:preferences
                                → :core:source-api
                                + api(anikutaLibs.aniyomi.mpv)   ← exposes MPV transitively
:core:update-checker            → :core:common
                                → :core:source-api
                                → :core:anilist
                                → :core:preferences
:core:download                  → :core:common
                                → :core:preferences
                                → :core:source-api
:core:notification              (0 deps — empty stub)
:core:backup                    → :core:common
                                → :core:database
                                → :core:preferences
                                → :core:player
                                → :core:episode-metadata
                                → :core:tracker
                                → :core:anilist
                                → :data:extension              ← VIOLATION (core→data) — see §3.1
```

### 1.3 `:data:*` layer

```
:data:anime                     → :core:common
                                → :core:database
                                → :core:anilist
                                → :core:episode-metadata
                                → :core:source-api
                                → :data:extension              ← blurred boundary (data→data) — see §4
:data:manga                     (0 deps — empty stub)
:data:extension                 → :core:common
                                → :core:source-api
                                → :core:preferences
                                → :core:update-checker
                                → :core:anilist
                                → :core:episode-metadata
                                → :core:designsystem
:data:tracker                   (0 deps — empty stub)
:data:history                   → :core:common
                                → :core:database
```

### 1.4 `:feature:*` layer

```
:feature:home                   (0 deps — empty stub)
:feature:library                → :core:common
                                → :core:designsystem
                                → :core:anilist
                                → :core:preferences
                                → :core:player
                                → :data:anime
:feature:updates                → :core:common
                                → :core:designsystem
                                → :core:anilist
                                → :core:preferences
                                → :core:update-checker
                                → :data:anime
:feature:history                → :core:common
                                → :core:designsystem
                                → :core:preferences
                                → :core:player
                                → :data:anime
:feature:browse                 → :core:common
                                → :core:designsystem
                                → :core:anilist
:feature:search                 → :core:common
                                → :core:designsystem
                                → :core:anilist
                                → :core:preferences
                                → :core:source-api
                                → :data:extension
:feature:my                     → :core:common
                                → :core:designsystem
                                → :core:preferences
                                → :core:player
                                → :core:tracker
                                → :data:anime
:feature:more                   (0 deps — empty stub)
:feature:anime-details          → :core:common
                                → :core:designsystem
                                → :core:anilist
                                → :core:preferences
                                → :core:episode-metadata
                                → :core:source-api
                                → :data:extension
:feature:episode-list           (0 deps — empty stub)
:feature:episode-settings       → :core:common
                                → :core:designsystem
                                → :core:preferences
                                → :core:episode-metadata
                                → :feature:anime-details       ← VIOLATION (feature→feature) — see §3.2
:feature:video-resolver         → :core:common
                                → :core:designsystem
                                → :core:source-api
:feature:watch                  → :core:common
                                → :core:designsystem
                                → :core:preferences
                                → :core:source-api
                                → :core:player
                                → :core:episode-metadata
                                → :data:anime
                                → :data:history
                                → :feature:video-resolver      ← VIOLATION (feature→feature) — see §3.3
:feature:player                 → :core:common
                                → :core:designsystem
                                → :core:player
                                → :core:source-api
                                (module itself is an empty stub — see §7)
:feature:extensions-settings    → :core:common
                                → :core:designsystem
                                → :data:extension
                                → :core:source-api
:feature:settings               → :core:common
                                → :core:designsystem
                                → :core:preferences
                                → :core:player
:feature:trackers               → :core:common
                                → :core:designsystem
                                → :core:preferences
                                → :core:tracker
:feature:backup                 → :core:common
                                → :core:designsystem
                                → :core:preferences
                                → :core:anilist
                                → :core:backup
:feature:download               → :core:common
                                → :core:designsystem
                                → :core:preferences
                                → :core:download
                                → :core:source-api
                                → :feature:video-resolver      ← VIOLATION (feature→feature) — see §3.4
```

### 1.5 `:i18n`

```
:i18n                           (declared in settings.gradle.kts:85; directory DOES NOT EXIST)
                                No build.gradle.kts. Nothing depends on it.
```

---

## 2. Layered ASCII diagram

```
                            ┌─────────────────────────────────────────┐
                            │                  :app                   │  ← composition root
                            │  (MainActivity, AnikutaRoot, AppCtrl,   │     (31 project deps)
                            │   DownloadOrchestrator, MoreScreens,    │
                            │   App.kt, DI modules, Destinations)     │
                            └──┬───────────────────────────────────┬──┘
                               │                                   │
                ┌──────────────┴───────────────┐                   │
                ▼                              ▼                   │
       ┌─────────────────┐            ┌─────────────────┐          │
       │   :feature:*    │            │     :data:*     │◀─────────┘
       │ (16 deps from   │──impls──▶  │  (impls wired   │   (only :app pulls data
       │  :app, 19 total │            │   via Koin)     │    modules directly for
       │  declared)      │            │                 │    Koin composition)
       └──┬───────────┬──┘            └──┬──────────────┘
          │           │                  │
          │           │  interfaces      │ interfaces
          │           ▼                  ▼
          │      ┌────────────────────────────┐
          │      │          :core:*           │  (15 modules)
          │      │  leaf primitives (0 deps): │
          │      │   :core:common             │
          │      │   :core:database           │
          │      │   :core:preferences        │
          │      │   :core:source-api         │
          │      │  (+ 3 empty stubs:         │
          │      │   network, notification,   │
          │      │   source-local)            │
          │      │                            │
          │      │  derived:                  │
          │      │   designsystem (2)         │
          │      │   anilist (2)              │
          │      │   episode-metadata (2)     │
          │      │   download (3)             │
          │      │   update-checker (4)       │
          │      │   player (4)               │
          │      │   tracker (5)              │
          │      │   backup (8) ◀── ⚠ pulls :data:extension (VIOLATION)
          │      └────────────────────────────┘
          │
          │  ⚠ FEATURE→FEATURE VIOLATIONS (3 — see §3.2-3.4):
          │      :feature:episode-settings → :feature:anime-details
          │      :feature:watch            → :feature:video-resolver
          │      :feature:download         → :feature:video-resolver
          │
          └──▶ (cross-feature should route through :core per ARCHITECTURE.md §3)
```

**Conceptual flow:** `:app → :feature:* → :core:*` (interfaces) and `:feature:* → :data:*` (impls, wired via Koin). `:data:* → :core:*`. **Never** `:feature:* → :feature:*`.

**Phantom layer:** `:i18n` is declared but doesn't exist on disk; the architecture doc says it should hold Moko Resources strings (ADR-027), but no `i18n/` directory exists and no module references it.

---

## 3. Violations table

ARCHITECTURE.md §3 invariants (verbatim):
```
:app  →  :feature:*  →  :core:*  (interfaces only)
                     →  :data:*   (impls, wired via Koin)
:feature:*  ↛  :feature:*  (NEVER — cross-feature goes through :core)
:data:*  →  :core:* (to implement interfaces)
```

| # | Module | Forbidden dep | Rule violated | Evidence (file:line) |
|---|---|---|---|---|
| 1 | `:core:backup` | `→ :data:extension` | `:core:* ↛ :data:*` (inverts "`:data:* → :core:*`") | `core/backup/build.gradle.kts:40` |

  Comment block at `core/backup/build.gradle.kts:35-39` explicitly acknowledges this:
  > "This is a documented exception: `:core:backup → :data:extension` because the link stores are not behind interfaces in `:core` (pre-existing architectural choice from the search page implementation)."

  → It is **NOT actually an "accepted" exception** — ARCHITECTURE.md §3 has no exception clause. This is a real boundary inversion that should be fixed by promoting `SourceLinkStore` + `ExtensionLinkStore` to interfaces in `:core:*` (probably `:core:source-api` or a new `:core:source-link`).

| # | Module | Forbidden dep | Rule violated | Evidence (file:line) |
|---|---|---|---|---|
| 2 | `:feature:episode-settings` | `→ :feature:anime-details` | `:feature:* ↛ :feature:*` (NEVER) | `feature/episode-settings/build.gradle.kts:19` |

  Pulls `EpisodeDisplayPreferences` + `EpisodeDisplayPrefs` (UI-state holder for episode display). Comment at L18:
  > "EpisodeDisplayPreferences + EpisodeDisplayPrefs live in feature:anime-details"

  → Fix: move `EpisodeDisplayPreferences`/`EpisodeDisplayPrefs` to `:core:preferences` (or a new `:core:episode-display` module), since they're pref holders, not anime-details UI.

| # | Module | Forbidden dep | Rule violated | Evidence (file:line) |
|---|---|---|---|---|
| 3 | `:feature:watch` | `→ :feature:video-resolver` | `:feature:* ↛ :feature:*` (NEVER) | `feature/watch/build.gradle.kts:18` |

  Pulls `ResolverResult`, `ResolverService`, `ResolverServer`, `ResolverVideo`, `SubtitleTrack`. No comment in build.gradle.kts. The watch screen needs the resolver to launch playback.

  → Fix: promote `ResolverService` + the resolver result/server/video models to a `:core:video-resolver` interface module (or `:core:source-api`, since it's already the source contract).

| # | Module | Forbidden dep | Rule violated | Evidence (file:line) |
|---|---|---|---|---|
| 4 | `:feature:download` | `→ :feature:video-resolver` | `:feature:* ↛ :feature:*` (NEVER) | `feature/download/build.gradle.kts:28` |

  Pulls `ResolverServer`, `ResolverVideo` for the `DownloadVideoPickerSheet`. Comment block at L24-27 explicitly acknowledges this:
  > "This is a feature→feature dependency, but it's read-only (no import cycles: video-resolver doesn't depend on feature:download). Accepted per Rule §14 because the picker sheet IS the resolver UI repurposed for download selection."

  → Same fix as #3 — once `ResolverServer`/`ResolverVideo` are in `:core:*`, this dep can drop. The "Rule §14" reference is to the agent-rules modularity clause, but ARCHITECTURE.md §3 has no exception, so the comment is rationalizing a real violation.

### Other rule checks (no violations found)

| Check | Result |
|---|---|
| Any `:feature:* → :app` (wrong direction) | **None** (grep'd `projects.app` / `project(":app")` → 0 hits) |
| Any `:data:* → :feature:*` (wrong direction) | **None** (grep'd `projects.feature.*` in `data/**/*.kts` → 0 hits) |
| Any `:core:* → :feature:*` (wrong direction) | **None** (grep'd `projects.feature.*` in `core/**/*.kts` → 0 hits) |
| Any `:core:* → :core:*` smell (primitive depending on non-primitive) | **One smell** — `:core:tracker → :core:player` (`core/tracker/build.gradle.kts:20`). `:core:tracker` needs `WatchProgressStore` (for stats + sync) which lives in `:core:player`. Because `:core:player` exposes MPV via `api(anikutaLibs.aniyomi.mpv)` (L19), `:core:tracker` transitively drags in the entire MPV + FFmpeg native stack. The conceptual smell is that a tracker-stats module shouldn't need the MPV media player. **Fix:** move `WatchProgressStore` (and `PlaybackStateStore`) out of `:core:player` into `:core:preferences` or a new `:core:playback-state` leaf module — then `:core:tracker` and `:core:backup` both drop their `:core:player` dep. |
| Any `:core:common → :core:*` (the most primitive should have no project deps) | **None** — `:core:common` has 0 project deps ✓ |
| Any circular dependency | **None detected** — graph is a DAG. Verified by topological sort by hand: leaf primitives (common, source-api, database, preferences) → derive designsystem, anilist, episode-metadata, download → derive player, update-checker → derive tracker → derive backup. No back-edges. `:feature:video-resolver` is the leaf for the 3 feature→feature violations (it has no feature deps), so the violations are acyclic (single-direction fan-in into video-resolver + anime-details). |

---

## 4. Blurred boundaries (Kotlin imports crossing feature lines)

Grep'd every `.kt` file under `feature/*/src/` for `^import app\.confused\.anikuta\.feature\.` and filtered to imports whose package ≠ the file's own feature package.

| # | Source file (in feature module X) | Cross-feature import (from feature module Y) | Gradle dep declared? |
|---|---|---|---|
| 1 | `feature/watch/.../WatchScreen.kt` | `app.confused.anikuta.feature.videoresolver.ResolverResult` | ✅ (matches `:feature:watch → :feature:video-resolver` violation #3) |
| 2 | `feature/watch/.../WatchScreen.kt` | `app.confused.anikuta.feature.videoresolver.ResolverService` | ✅ |
| 3 | `feature/watch/.../WatchRequest.kt` | `app.confused.anikuta.feature.videoresolver.ResolverServer` | ✅ |
| 4 | `feature/watch/.../WatchRequest.kt` | `app.confused.anikuta.feature.videoresolver.SubtitleTrack` | ✅ |
| 5 | `feature/watch/.../sheets/PlayerSheets.kt` | `app.confused.anikuta.feature.videoresolver.ResolverServer` | ✅ |
| 6 | `feature/watch/.../sheets/PlayerSheets.kt` | `app.confused.anikuta.feature.videoresolver.ResolverVideo` | ✅ |
| 7 | `feature/download/.../DownloadVideoPickerSheet.kt` | `app.confused.anikuta.feature.videoresolver.ResolverServer` | ✅ (matches `:feature:download → :feature:video-resolver` violation #4) |
| 8 | `feature/download/.../DownloadVideoPickerSheet.kt` | `app.confused.anikuta.feature.videoresolver.ResolverVideo` | ✅ |
| 9 | `feature/episode-settings/.../RememberEpisodeDisplayPrefs.kt` | `app.confused.anikuta.feature.animedetails.EpisodeDisplayPrefs` | ✅ (matches `:feature:episode-settings → :feature:anime-details` violation #2) |
| 10 | `feature/episode-settings/.../RememberEpisodeDisplayPrefs.kt` | `app.confused.anikuta.feature.animedetails.EpisodeDisplayPreferences` | ✅ |
| 11 | `feature/episode-settings/.../EpisodeLayoutSettingsScreen.kt` | `app.confused.anikuta.feature.animedetails.EpisodeDisplayPreferences` | ✅ |
| 12 | `feature/episode-settings/.../EpisodeDisplaySettingsScreen.kt` | `app.confused.anikuta.feature.animedetails.EpisodeDisplayPreferences` | ✅ |
| 13 | `feature/episode-settings/.../EpisodeRowPreview.kt` | `app.confused.anikuta.feature.animedetails.EpisodeDisplayPrefs` | ✅ |

**All 13 cross-feature imports correspond 1-to-1 with the 3 Gradle feature→feature violations.** There are **no hidden cross-feature imports** that compile without a Gradle dep — i.e., the Gradle dependency graph faithfully reflects the source-level coupling. The blurred boundary and the declared boundary are the same boundary.

### Bonus: `:data:anime → :data:extension` (data→data — not in the rule list)

ARCHITECTURE.md §3 says "`:data:* → :core:*` (to implement interfaces)". It does **not** say `:data:* → :data:*` is allowed. The single data→data dep is:

- `:data:anime → :data:extension` (`data/anime/build.gradle.kts:21`)
  - Pulls `AnimeExtensionManager`, `SourceMatcher`, `SourceLinkStore`, `ExtensionLinkStore` for `AniListDetailsProvider`'s stage-2 source match.
  - Not a strict violation (rule only says "data→core"), but a **blurred boundary** — `:data:anime` should depend on interfaces in `:core:source-api`, not on a sibling data module's concrete classes.
  - Same root cause as violation #1: the link stores (`SourceLinkStore`, `ExtensionLinkStore`) live as concrete classes in `:data:extension` instead of behind interfaces in `:core:*`.

---

## 5. `:app` composition-root detail (Task §5)

The `:app` module hosts the following hand-picked files (per task spec):

| File (path under `app/src/main/java/app/confused/anikuta/`) | Project deps it actually pulls in (via `import app.confused.anikuta.*` statements) |
|---|---|
| `MainActivity.kt` | `:core:designsystem` (AnikutaTheme), `:core:preferences` (ThemePreferences), `:app` itself (AnikutaRoot) |
| `navigation/AnikutaRoot.kt` | `:core:designsystem` (AnikutaBottomNavBar, NavIcons, NavItem), `:feature:download` (DownloadVideoPickerSheet), `:feature:search` (ExtensionLinkingSheet), `:feature:video-resolver` (VideoResolverSheet, VideoResolverState) |
| `navigation/AppController.kt` | `:core:anilist` (AniListApi), `:core:download` (DownloadManager, DownloadStatus, DownloadTask, ServerDiscoveryStore), `:core:tracker` (Tracker, TrackerManager), `:data:extension` (AnimeExtensionManager, ExtensionLinkStore, SourceMatcher, ExtensionRepoApi, ExtensionRepoRepository), `:app` (DownloadOrchestrator, EnqueueResult, PickerContext), `:feature:anime-details` (EpisodeDownloadState, WatchEpisodeContext), `:feature:search` (RecentSearchesStore, SearchUiPreferences), `:feature:video-resolver` (ResolverResult, ResolverService, ResolverVideo, SubtitleTrack, VideoResolverState), `:feature:watch` (WatchRequest) |
| `navigation/MoreScreens.kt` (`MoreScreen` + `SettingsScreen` composables, lines 49 + 109) | `:core:designsystem` (CollapsingHeader, RobotoFamily) — plus 3 fully-qualified calls into feature modules (no `import` statement): `app.confused.anikuta.feature.my.ProfileTrackersMoreEntries` (L66), `app.confused.anikuta.feature.history.HistoryUpdatesMoreEntries` (L82), `app.confused.anikuta.feature.download.DownloadsMoreEntries` (L89). All 3 are already covered by `:app`'s direct deps on `:feature:my`, `:feature:history`, `:feature:download`. |
| `download/DownloadOrchestrator.kt` | `:core:download` (10 types: DownloadAnimeInfo, DownloadEpisodeInfo, DownloadManager, DownloadPreferences, DownloadRequest, DownloadTrack, FallbackStrategy, ServerDiscoveryStore, TrackKind, + EnqueueResult from `:app`), `:feature:video-resolver` (ResolverResult, ResolverServer, ResolverVideo, ResolverService, SubtitleTrack) |
| `App.kt` (Koin Application subclass) | `:app` DI modules (databaseModule, downloadAppModule, extensionModule, repositoryModule, searchModule), `:feature:history` (historyModule), `:feature:library` (libraryModule), `:feature:updates` (updatesModule), `:core:common` (CategoryRepository), `:core:update-checker` (updateCheckerModule), `:core:preferences` (preferenceModule), `:core:player` (playerModule), `:core:episode-metadata` (episodeMetadataModule), `:app` (navModule) |
| `di/DownloadAppModule.kt` | `:core:download` (downloadModule), `:app` (DownloadOrchestrator), `:feature:download` (downloadFeatureModule), `:feature:video-resolver` (ResolverService) |
| `di/SearchModule.kt` | `:feature:search` (RecentSearchesStore, SearchUiPreferences) |
| `di/DetailsModule.kt` | (not grep'd, but lives in `:app`'s DI package — wires `AnimeDetailsProviderRegistry`) |
| `di/ExtensionModule.kt` | (wires `AnimeExtensionManager` + `ExtensionInstallService` from `:data:extension`) |
| `di/RepositoryModule.kt` | (wires `AnimeRepositoryImpl`, `EpisodeRepositoryImpl`, `CategoryRepositoryImpl` from `:data:anime` + `HistoryRepositoryImpl` from `:data:history`) |
| `di/DatabaseModule.kt` | (wires `DatabaseDriverFactory` from `:core:database`) |
| `navigation/Destinations.kt` | All 16 feature-module screens (browse, search, anime-details, library, extensions-settings, video-resolver, watch, player, episode-settings, settings, history, updates, my, trackers, download, backup) — this is the Voyager `Screen` registry |
| `navigation/NavModule.kt` | Koin module for `AppController` |

**Key observation about `:app`'s 31 deps:** :app is the only module that pulls all 16 active feature modules + 12 core modules + 3 data modules together. It is the composition root in the truest sense — its `App.kt` is the Koin application class, its `Destinations.kt` is the Voyager screen registry, and its `di/*` package wires every concrete impl from `:data:*` to the interfaces in `:core:*`.

The 3 feature→feature violations **bypass `:app`** — i.e., they happen at the feature layer, not at the composition root. (In a stricter architecture, `:app` would be the only place that ties features together; the 3 violations let feature modules reach each other directly without going through `:app` or `:core`.)

`DownloadOrchestrator` is a textbook example of the **correct pattern** — `:app` bridges `:core:download` (engine) + `:feature:video-resolver` (UI) because `:core:download` is forbidden from importing `:feature:*`. The `feature/download/build.gradle.kts` comment at L24-27 acknowledges this orchestrator pattern but then rationalizes the feature→feature dep — the rationalization doesn't hold up against ARCHITECTURE.md §3.

---

## 6. Fat modules (top by project-dep count)

| Rank | Module | Project deps | Role |
|---|---|---|---|
| 1 | `:app` | **31** (12 core + 3 data + 16 feature) | Composition root — Koin Application + Voyager screen registry + DownloadOrchestrator |
| 2 | `:feature:watch` | **9** | Watch-page orchestrator — needs player, source-api, episode-metadata, anime, history, video-resolver. The most feature-coupled feature module. |
| 3 | `:core:backup` | **8** (incl. 1 data) | Aggregation point for backup providers — pulls every data-owning core module + `:data:extension` (the violation). |
| 4 | `:data:extension` | 7 | Extension system — pulls source-api, update-checker (for EpisodeFetchGateway impl), anilist + episode-metadata (for ExtensionDetailsProvider), designsystem (for Palette extraction). |
| 4 | `:feature:anime-details` | 7 | Details-page orchestrator — needs source-api, episode-metadata, extension manager. |
| 6 | `:data:anime` | 6 (incl. 1 data) | Anime repository impls — pulls extension for source match (blurred boundary §4). |
| 6 | `:feature:library` | 6 | Library screen — pulls anime repo + player for continue-watching. |
| 6 | `:feature:search` | 6 | Search page — dual-source (AniList + extensions). |
| 6 | `:feature:my` | 6 | Profile/stats — pulls tracker + player + anime repo. |
| 6 | `:feature:updates` | 6 | Updates feed — pulls update-checker + anime repo. |
| 6 | `:feature:download` | 6 (incl. 1 feature) | Downloads UI — pulls download engine + video-resolver (violation #4). |
| 11 | `:core:tracker` | 5 | Pulls anilist + player (smell — see §3) + database. |
| 11 | `:feature:episode-settings` | 5 (incl. 1 feature) | Episode display settings — pulls anime-details (violation #2). |
| 11 | `:feature:history` | 5 | History page — pulls player + anime repo. |
| 11 | `:feature:backup` | 5 | Backup UI — pulls backup engine + anilist. |
| 15 | `:core:player` | 4 | Pulls designsystem + source-api. |
| 15 | `:core:update-checker` | 4 | Pulls source-api + anilist. |
| 15 | `:feature:player` | 4 | (Empty stub — module has no source; dep block is the only contents.) |
| 15 | `:feature:settings` | 4 | Pulls player for PlayerPreferences. |
| 15 | `:feature:trackers` | 4 | Pulls tracker for TrackerManager. |
| 15 | `:feature:extensions-settings` | 4 | Pulls extension manager + source-api. |
| 21 | `:feature:video-resolver` | 3 | Lightweight — pulls source-api only. |
| 21 | `:feature:browse` | 3 | Pulls anilist for trending feed. |
| 21 | `:core:download` | 3 | Pulls source-api. |
| 24 | `:core:designsystem` | 2 | Pulls common + preferences (for theming). |
| 24 | `:core:anilist` | 2 | Pulls common + preferences. |
| 24 | `:core:episode-metadata` | 2 | Pulls common + preferences. |
| 24 | `:data:history` | 2 | Pulls common + database. |
| 28 | `:core:common` | 0 | Leaf primitive. |
| 28 | `:core:database` | 0 | Leaf primitive (sqldelight). |
| 28 | `:core:preferences` | 0 | Leaf primitive. |
| 28 | `:core:source-api` | 0 | Leaf primitive (Aniyomi contract). |
| 28 | `:core:network` | 0 | **Empty stub.** |
| 28 | `:core:notification` | 0 | **Empty stub.** |
| 28 | `:core:source-local` | 0 | **Empty stub.** |
| 28 | `:data:manga` | 0 | **Empty stub.** |
| 28 | `:data:tracker` | 0 | **Empty stub.** |
| 28 | `:feature:home` | 0 | **Empty stub.** |
| 28 | `:feature:more` | 0 | **Empty stub.** |
| 28 | `:feature:episode-list` | 0 | **Empty stub.** |
| — | `:i18n` | n/a | **Phantom** — no build.gradle.kts; directory missing. |

**"Fat module" definition (used above):** ≥ 5 project deps. Twelve modules qualify: `:app`, `:feature:watch`, `:core:backup`, `:data:extension`, `:feature:anime-details`, `:data:anime`, `:feature:library`, `:feature:search`, `:feature:my`, `:feature:updates`, `:feature:download`, and the cluster of 5-dep modules (`:core:tracker`, `:feature:episode-settings`, `:feature:history`, `:feature:backup`).

---

## 7. Empty stub modules — inbound dependency audit (Task §8)

The 9 empty stubs (modules with zero `.kt` source files) per prior worklog:

| Stub module | Inbound deps (who declares `projects.X` for it)? | Safe to remove? |
|---|---|---|
| `:core:network` | **None** (grep'd all 40 build.gradle.kts → 0 hits) | ✅ Yes |
| `:core:notification` | **None** | ✅ Yes |
| `:core:source-local` | **None** | ✅ Yes |
| `:data:manga` | **None** | ✅ Yes |
| `:data:tracker` | **None** | ✅ Yes |
| `:feature:home` | **None** | ✅ Yes |
| `:feature:more` | **None** | ✅ Yes |
| `:feature:player` | **`:app` only** (`app/build.gradle.kts:68`) | ⚠ Almost — :app declares the dep but never references it in any `.kt` file (grep'd `feature.player.` in `app/**/*.kt` → 0 hits). The README at `feature/player/README.md:18-22` confirms: "The dependency is currently a no-op (empty module). It can be removed from the :app deps + settings.gradle.kts in a future cleanup pass." So: remove the dep line in :app + remove the module from settings.gradle.kts → safe. |
| `:feature:episode-list` | **None** | ✅ Yes |

**Source-level confirmation:** grep'd all `.kt` files for `app\.confused\.anikuta\.(core\.network|core\.notification|core\.sourcelocal|data\.manga|data\.tracker|feature\.home|feature\.more|feature\.player|feature\.episodelist)\.` → **0 matches.** None of the 9 empty stubs are referenced anywhere in the source code.

**Conclusion:** All 9 empty stubs are dead weight. 8 are immediately removable (zero inbound deps). The 9th (`:feature:player`) requires also removing one line from `:app/build.gradle.kts`. None of the 9 modules would break the build if deleted.

---

## 8. `:i18n` reference audit (Task §9)

| Check | Result |
|---|---|
| Declared in `settings.gradle.kts` | ✅ Line 85: `include(":i18n")` |
| `i18n/` directory exists | ❌ `ls /home/z/my-project/anikuta/ANIKUTA_PROJECT/ANIKUTA/i18n` → "No such file or directory" |
| `i18n/build.gradle.kts` exists | ❌ |
| Any `projects.i18n` in any `build.gradle.kts` | ❌ 0 hits (grep'd all 40 build scripts + buildSrc) |
| Any `project(":i18n")` in any `build.gradle.kts` | ❌ 0 hits |
| Any Kotlin source reference (`app.confused.anikuta.i18n.*`) | ❌ 0 hits |
| ARCHITECTURE.md §3 description | "Moko Resources strings (English-only initially, ADR-027)" |

**Conclusion:** `:i18n` is referenced **only** in `settings.gradle.kts:85`. Nothing depends on it. The directory doesn't exist.

**Build behavior note:** Whether Gradle actually fails on this depends on the version — with `TYPESAFE_PROJECT_ACCESSORS` enabled (settings.gradle.kts:32), Gradle generates typed accessors for declared projects at configuration time. If no module references `projects.i18n`, the missing directory is typically a *sync-time warning* (Gradle logs "Project ':i18n' could not be found") rather than a hard error. The prior worklog already flagged this as "a known CI latent issue" — the build apparently passes because nothing tries to import `:i18n`. **This is fragile** — any future module that adds `implementation(projects.i18n)` would break the build instantly.

**Recommendation:** Either create the `:i18n/` directory with a minimal `build.gradle.kts` (per ADR-027 — Moko Resources), or remove `include(":i18n")` from `settings.gradle.kts:85` until the i18n work actually starts.

---

## 9. Convention plugins (Task §10)

`buildSrc/src/main/kotlin/` contains 4 convention plugins (+ 2 helper files). The 4 plugin IDs are referenced from each module's `plugins { ... }` block.

### 9.1 `anikuta.library` (`buildSrc/.../anikuta.library.gradle.kts`)

**Plugin ID:** `anikuta.library`

**What it applies:**
- `com.android.library` (Android library AGP plugin)
- `org.jetbrains.kotlin.android` (Kotlin Android plugin)

**What it configures:**
- `android.compileSdk = 36`
- `android.defaultConfig.minSdk = 26`
- `android.defaultConfig.targetSdk = 36`
- `android.defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`
- Java 17 source/target compatibility
- `isCoreLibraryDesugaringEnabled = true`
- Excludes `/META-INF/{AL2.0,LGPL2.1}` from packaging
- Kotlin `jvmTarget = JVM_17`
- All `Test` tasks → `useJUnitPlatform()` (JUnit 5/Jupiter)

**What it adds to dependencies:**
- `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")`
- `implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.1"))` — installs the coroutines BOM so downstream `kotlinx-coroutines-*` deps don't need versions

**Used by (count):** 28 of 40 build scripts (`core/{common, network, database, preferences, anilist, tracker, episode-metadata, source-api, source-local, update-checker, download, notification, backup}`, `data/{anime, manga, extension, tracker, history}`, `feature/{episode-settings, video-resolver, watch, player, extensions-settings, settings, trackers, backup, download}` — note: NOT used by `feature/{home, library, updates, history, browse, search, my, more, anime-details, episode-list}` which use `anikuta.library.compose`).

### 9.2 `anikuta.library.compose` (`buildSrc/.../anikuta.library.compose.gradle.kts`)

**Plugin ID:** `anikuta.library.compose`

**What it applies:**
- `anikuta.library` (full inheritance — the entire §9.1 above)
- `org.jetbrains.kotlin.plugin.compose` (Kotlin Compose compiler plugin)

**What it configures beyond `anikuta.library`:**
- `android.buildFeatures.compose = true`

**What it adds to dependencies beyond `anikuta.library`:**
- `implementation(platform("androidx.compose:compose-bom:2025.03.00"))` — installs the Compose BOM
- `implementation("androidx.compose.foundation:foundation")`
- `implementation("androidx.compose.animation:animation")`
- `implementation("androidx.compose.material3:material3")`
- `implementation("androidx.compose.material:material-icons-extended")`
- `implementation("androidx.compose.runtime:runtime")`
- `implementation("androidx.compose.ui:ui-tooling-preview")`
- `debugImplementation("androidx.compose.ui:ui-tooling")`
- `implementation("androidx.core:core-ktx:1.15.0")`

**`anikuta.library.compose` vs `anikuta.library` summary:** adds the Compose compiler plugin, enables `buildFeatures.compose`, installs the Compose BOM, and pulls in the core Compose UI artifacts (foundation, animation, material3, material-icons-extended, runtime, ui-tooling-preview, ui-tooling/debug) + `androidx.core:core-ktx`. **Note:** it does NOT add `activity-compose` or `lifecycle-runtime-compose`/`lifecycle-viewmodel-compose` — those are pulled in per-module as needed. It also doesn't add `animation-graphics` (only `animation`).

**Used by (count):** 19 of 40 build scripts (every `feature/*` module that has Compose UI, including the empty stubs `home`, `more`, `episode-list`, `player` — they apply the plugin but have no source. Note that `feature/episode-settings`, `feature/video-resolver`, `feature/watch`, `feature/player`, `feature/extensions-settings`, `feature/settings`, `feature/trackers`, `feature/backup`, `feature/download` use it. Also note: `core/designsystem` and `core/player` use it — these are the only `:core:*` modules with Compose UI).

### 9.3 `anikuta.android.application` (`buildSrc/.../anikuta.android.application.gradle.kts`)

**Plugin ID:** `anikuta.android.application`

**What it applies:**
- `com.android.application` (Android app AGP plugin)
- `org.jetbrains.kotlin.android`

**What it configures:**
- Same SDK levels + Java 17 + coroutines BOM + JUnit 5 + desugar as `anikuta.library` (boilerplate duplicated — see §9.5)
- `android.namespace = "app.confused.anikuta"` (the APPLICATION_ID)
- `android.defaultConfig.applicationId = AndroidConfig.APPLICATION_ID`
- `android.defaultConfig.versionCode = 1`
- `android.defaultConfig.versionName = "0.1.0"`

**What it adds to dependencies:**
- Same as `anikuta.library`: `coreLibraryDesugaring(desugar)` + `coroutines-bom` platform.

**Used by:** 0 modules directly — only used as the base for `anikuta.android.application.compose` below.

### 9.4 `anikuta.android.application.compose` (`buildSrc/.../anikuta.android.application.compose.gradle.kts`)

**Plugin ID:** `anikuta.android.application.compose`

**What it applies:**
- `anikuta.android.application` (full inheritance — §9.3)
- `org.jetbrains.kotlin.plugin.compose`

**What it configures beyond `anikuta.android.application`:**
- `android.buildFeatures.compose = true`

**What it adds to dependencies beyond `anikuta.android.application`:**
- `implementation(platform("androidx.compose:compose-bom:2025.03.00"))`
- `implementation("androidx.compose.foundation:foundation")`
- `implementation("androidx.compose.material3:material3")`
- `implementation("androidx.compose.material:material-icons-extended")`
- `implementation("androidx.compose.runtime:runtime")`
- `implementation("androidx.compose.ui:ui-tooling-preview")`
- `implementation("androidx.compose.ui:ui-util")`  ← **only in the application variant**, not the library variant
- `debugImplementation("androidx.compose.ui:ui-tooling")`
- `implementation("androidx.activity:activity-compose:1.10.1")`  ← **only in the application variant**

**Differences from `anikuta.library.compose`:** the application variant adds `ui-util` + `activity-compose`; the library variant adds `animation`. Both pull in foundation, material3, material-icons-extended, runtime, ui-tooling-preview, ui-tooling/debug. The application variant does NOT pull `animation` (an oversight? or intentional?).

**Used by:** 1 module — `:app` (the only Android application module).

### 9.5 Convention-plugin observations

1. **Duplicated boilerplate** between `anikuta.library` and `anikuta.android.application`. The SDK config, Java 17, desugar, coroutines BOM, JUnit 5, packaging excludes — all are copy-pasted. A cleaner pattern would be a shared `anikuta.base` plugin applied by both, but this is a minor maintainability smell, not a bug.

2. **Compose coords hardcoded** in the convention plugins (e.g. `"androidx.compose:compose-bom:2025.03.00"`, `"androidx.core:core-ktx:1.15.0"`). These duplicate values in the `gradle/compose.versions.toml` catalog (which has `compose-bom = "2025.03.00"`). The catalog is **not used by the convention plugins** — the plugins hardcode the strings. This means version updates must happen in two places.

3. **Helper file `AndroidConfig.kt`** defines the shared config constants: `COMPILE_SDK = 36`, `TARGET_SDK = 36`, `MIN_SDK = 26`, `NDK = "27.1.12297006"`, `BUILD_TOOLS = "35.0.1"`, `JavaVersion = VERSION_17`, `JvmTarget = JVM_17`, `APPLICATION_ID = "app.confused.anikuta"`, `VERSION_CODE = 1`, `VERSION_NAME = "0.1.0"`. The `ProjectExtensions.kt` file has `getBuildTime()` / `getCommitCount()` / `getGitSha()` helpers (not currently referenced by any module's build script — likely future-use for versionName embedding).

4. **`buildSrc/settings.gradle.kts`** declares its own 4 version catalogs (`libs`, `kotlinx`, `androidx`, `compose`) — note: it does NOT declare the `anikutaLibs` catalog, so the buildSrc build script can't reference `anikutaLibs.*`. The main `settings.gradle.kts` declares all 5 (`kotlinx`, `androidx`, `compose`, `anikutaLibs`, plus the `libs` catalog implicit via `gradle/libs.versions.toml` — actually no, the main settings does NOT explicitly create `libs`, but Gradle auto-creates it from `gradle/libs.versions.toml`).

---

## 10. Version catalogs (Task §11)

5 catalog files in `gradle/`:

| File | Catalog accessor | Purpose |
|---|---|---|
| `gradle/libs.versions.toml` | `libs.*` | Main catalog — Koin, Coil, Voyager, SQLDelight, OkHttp, etc. |
| `gradle/kotlinx.versions.toml` | `kotlinx.*` | KotlinX libs (coroutines, serialization, xmlutil) + Kotlin/Compose-compiler/serialization plugin versions |
| `gradle/androidx.versions.toml` | `androidx.*` | AndroidX libs (lifecycle, paging, workmanager, etc.) + AGP version |
| `gradle/compose.versions.toml` | `compose.*` | Compose BOM + Compose UI artifacts (no plugins, no bundles) |
| `gradle/anikuta.versions.toml` | `anikutaLibs.*` | ANIKUTA-specific libs (aniyomi-mpv, ffmpeg-kit, seeker, nanohttpd, mediasession, truetypeparser, torrserver, compose-constraintlayout) |

### 10.1 Bundles defined per catalog

| Catalog | Bundle | Members |
|---|---|---|
| `libs` | `libs.bundles.okhttp` | `okhttp-core`, `okhttp-logging`, `okhttp-brotli`, `okhttp-dnsoverhttps` |
| `libs` | `libs.bundles.sqlite` | `sqlite-framework`, `sqlite-ktx`, `sqlite-android` |
| `libs` | `libs.bundles.koin` | `koin-core`, `koin-android`, `koin-androidx-compose` |
| `libs` | `libs.bundles.coil` | `coil-core`, `coil-gif`, `coil-compose`, `coil-network-okhttp` |
| `libs` | `libs.bundles.sqldelight` | `sqldelight-android-driver`, `sqldelight-coroutines`, `sqldelight-android-paging` |
| `libs` | `libs.bundles.voyager` | `voyager-core`, `voyager-navigator`, `voyager-screenmodel`, `voyager-tab-navigator`, `voyager-transitions` |
| `libs` | `libs.bundles.test` | `junit` (Jupiter 5.11.4), `kotest-assertions`, `mockk` |
| `kotlinx` | `kotlinx.bundles.coroutines` | `coroutines-core`, `coroutines-android`, `coroutines-guava` |
| `kotlinx` | `kotlinx.bundles.serialization` | `serialization-json`, `serialization-json-okio`, `serialization-protobuf`, `serialization-xml-core`, `serialization-xml` |
| `androidx` | `androidx.bundles.lifecycle` | `lifecycle-common`, `lifecycle-process`, `lifecycle-runtimektx` |
| `compose` | (no bundles) | — |
| `anikutaLibs` | (no bundles) | — |

**Total bundles defined: 10** (7 in `libs`, 2 in `kotlinx`, 1 in `androidx`).

### 10.2 Bundle usage (grep'd every `*.kts` for `\.bundles\.`)

| Bundle | Used by |
|---|---|
| `libs.bundles.koin` | `:app` (`app/build.gradle.kts:101`) |
| `libs.bundles.voyager` | `:app` (`app/build.gradle.kts:104`) |
| `libs.bundles.sqldelight` | `:core:database` (`core/database/build.gradle.kts:20`) |
| `libs.bundles.test` | `:app`, `:core:common`, `:core:database`, `:data:anime`, `:data:history` (5 modules; only `:data:anime` actually has a test — `AnimeMapperTest.kt`) |
| `libs.bundles.okhttp` | **UNUSED** |
| `libs.bundles.sqlite` | **UNUSED** |
| `libs.bundles.coil` | **UNUSED** — modules pull `io.coil-kt.coil3:coil-compose:3.1.0` + `io.coil-kt.coil3:coil-network-okhttp:3.1.0` directly as inline strings |
| `kotlinx.bundles.coroutines` | **UNUSED** — modules pull `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1` inline |
| `kotlinx.bundles.serialization` | **UNUSED** — modules pull `org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0` inline |
| `androidx.bundles.lifecycle` | **UNUSED** — modules pull `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7` inline |

**Bundle usage summary:**
- **4 of 10 bundles are used** (koin, voyager, sqldelight, test).
- **6 of 10 bundles are dead** (defined in catalogs but never referenced).
- The codebase has a pervasive pattern of **duplicating dependency coordinates inline** rather than using the catalog — at least 30 inline `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1` declarations, ~15 inline `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7`, ~15 inline `io.coil-kt.coil3:coil-compose:3.1.0`, ~10 inline `com.squareup.okhttp3:okhttp:5.0.0-alpha.14`, etc. This is a maintainability smell — version bumps require N edits instead of 1.

### 10.3 Per-module bundle usage table

| Module | Bundles used | Notable inline coordinates (duplicated) |
|---|---|---|
| `:app` | `libs.bundles.koin`, `libs.bundles.voyager`, `libs.bundles.test` | `okhttp:5.0.0-alpha.14`, `kotlinx-serialization-json:1.9.0`, `injekt:91edab2317`, `lifecycle-runtime-compose:2.8.7` |
| `:core:common` | `libs.bundles.test` | `kotlinx-coroutines-core` (via BOM, no version) |
| `:core:database` | `libs.bundles.sqldelight`, `libs.bundles.test` | `kotlinx-coroutines-core`, `libs.sqldelight.dialects.sql` |
| `:core:preferences` | (none) | `preference-ktx:1.2.1`, `kotlinx-coroutines-core`, `core-ktx:1.15.0`, `libs.koin.{bom, core, android}` |
| `:core:anilist` | (none) | `okhttp`, `kotlinx-serialization-json`, `kotlinx-coroutines-core:1.10.1`, `junit-jupiter:5.11.4` |
| `:core:tracker` | (none) | `okhttp`, `kotlinx-serialization-json`, `kotlinx-coroutines-core`, `libs.koin.{bom, core, android}`, `libs.sqldelight.coroutines` |
| `:core:episode-metadata` | (none) | `okhttp`, `kotlinx-serialization-json`, `kotlinx-coroutines-core`, `koin-android:4.0.0`, `junit-jupiter`, `kotlinx-coroutines-test` |
| `:core:source-api` | (none) | `api(okhttp)`, `jsoup`, `kotlinx-serialization-json`, `kotlinx-serialization-json-okio`, `kotlinx-coroutines-core`, `rxjava:1.3.8`, `rxandroid:1.2.1`, `nanohttpd:2.3.1`, `api(injekt:91edab2317)`, `compose-stable-marker:1.0.5` (compileOnly), `preference-ktx:1.2.1` |
| `:core:player` | (none) | `api(anikutaLibs.aniyomi.mpv)`, `anikutaLibs.ffmpeg.kit`, `anikutaLibs.arthenica.smartexceptions`, `anikutaLibs.seeker`, `anikutaLibs.nanohttpd`, `anikutaLibs.mediasession`, `anikutaLibs.truetypeparser`, `coil-compose:3.1.0`, `libs.koin.{bom, core, android}`, `kotlinx-coroutines-core`, `kotlinx-serialization-json` |
| `:core:update-checker` | (none) | `kotlinx-serialization-json`, `kotlinx-coroutines-core`, `libs.koin.{bom, core}` |
| `:core:download` | (none) | `okhttp`, `documentfile:1.0.1`, `kotlinx-serialization-json`, `kotlinx-coroutines-core`, `libs.koin.{bom, core, android}` |
| `:core:backup` | (none) | `kotlinx-serialization-json`, `kotlinx-serialization-protobuf`, `kotlinx-coroutines-core`, `okhttp`, `libs.koin.{bom, core, android}`, `work-runtime-ktx:2.10.0`, `documentfile:1.0.1`, `junit-jupiter`, `kotlinx-coroutines-test` |
| `:data:anime` | `libs.bundles.test` | `libs.sqldelight.coroutines`, `kotlinx-coroutines-core` |
| `:data:extension` | (none) | `core-ktx:1.15.0`, `okhttp`, `kotlinx-serialization-json`, `kotlinx-coroutines-core`, `rxjava:1.3.8` |
| `:data:history` | `libs.bundles.test` | `libs.sqldelight.coroutines`, `kotlinx-coroutines-core` |
| `:feature:browse` | (none) | `coil-compose:3.1.0`, `coil-network-okhttp:3.1.0`, `kotlinx-coroutines-core`, `lifecycle-runtime-ktx:2.8.7`, `lifecycle-viewmodel-compose:2.8.7` |
| `:feature:search` | (none) | `coil-compose`, `coil-network-okhttp`, `kotlinx-coroutines-core`, `kotlinx-serialization-json`, `lifecycle-runtime-ktx`, `lifecycle-viewmodel-compose` |
| `:feature:library` | (none) | `coil-compose`, `coil-network-okhttp`, `kotlinx-coroutines-core`, `lifecycle-runtime-ktx`, `lifecycle-viewmodel-compose`, `libs.koin.{bom, core, android, androidx.compose}` |
| `:feature:updates` | (none) | (same as library + `activity-compose:1.10.1`) |
| `:feature:history` | (none) | (same as updates) |
| `:feature:my` | (none) | (same as library) |
| `:feature:anime-details` | (none) | `activity-compose:1.10.1`, `coil-compose`, `coil-network-okhttp`, `kotlinx-coroutines-core`, `lifecycle-runtime-ktx`, `lifecycle-runtime-compose:2.8.7`, `lifecycle-viewmodel-compose`, `libs.koin.{bom, core, android, androidx.compose}` |
| `:feature:episode-settings` | (none) | `activity-compose`, `lifecycle-runtime-ktx`, `kotlinx-coroutines-core`, `libs.koin.{bom, core, android, androidx.compose}` |
| `:feature:video-resolver` | (none) | `activity-compose`, `kotlinx-coroutines-core` |
| `:feature:watch` | (none) | `activity-compose`, `lifecycle-runtime-ktx`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`, `coil-compose`, `kotlinx-coroutines-core`, `libs.koin.{bom, androidx.compose}` |
| `:feature:player` | (none) | `activity-compose`, `coil-compose`, `libs.koin.{bom, androidx.compose}` |
| `:feature:extensions-settings` | (none) | `core-ktx:1.15.0`, `coil-compose`, `coil-network-okhttp`, `kotlinx-coroutines-core`, `lifecycle-runtime-ktx`, `lifecycle-viewmodel-compose`, `activity-compose` |
| `:feature:settings` | (none) | `libs.koin.androidx.compose`, `androidx.lifecycle.runtimektx`, `androidx.lifecycle.viewmodel.compose`, `lifecycle-runtime-compose:2.8.7` |
| `:feature:trackers` | (none) | `coil-compose`, `kotlinx-coroutines-core`, `lifecycle-runtime-ktx`, `lifecycle-viewmodel-compose`, `libs.koin.{bom, core, android, androidx.compose}` |
| `:feature:backup` | (none) | `kotlinx-coroutines-core`, `lifecycle-runtime-ktx`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`, `activity-compose`, `coil-compose`, `libs.koin.{bom, core, android, androidx.compose}` |
| `:feature:download` | (none) | `coil-compose`, `coil-network-okhttp`, `kotlinx-coroutines-core`, `lifecycle-runtime-ktx`, `lifecycle-viewmodel-compose`, `activity-compose`, `libs.koin.{bom, core, android, androidx.compose}` |
| `:feature:home`, `:feature:more`, `:feature:episode-list` | (none) | (empty stubs — only the convention plugin, no deps) |
| `:core:network`, `:core:notification`, `:core:source-local`, `:data:manga`, `:data:tracker` | (none) | (empty stubs — only the convention plugin, no deps) |

---

## 11. Headline findings (TL;DR)

### Violations (4 total — see §3 for details)

1. **`:core:backup → :data:extension`** — core→data inversion (rule: data→core). `core/backup/build.gradle.kts:40`. Comment block at L35-39 acknowledges.
2. **`:feature:episode-settings → :feature:anime-details`** — feature→feature (NEVER). `feature/episode-settings/build.gradle.kts:19`.
3. **`:feature:watch → :feature:video-resolver`** — feature→feature (NEVER). `feature/watch/build.gradle.kts:18`.
4. **`:feature:download → :feature:video-resolver`** — feature→feature (NEVER). `feature/download/build.gradle.kts:28`. Comment block at L24-27 acknowledges ("Accepted per Rule §14" — but ARCHITECTURE.md §3 has no exception).

### Blurred boundaries

5. **`:data:anime → :data:extension`** — data→data (not in the rule list; rule only permits data→core). Root cause is the same as violation #1: `SourceLinkStore` + `ExtensionLinkStore` are concrete classes in `:data:extension` instead of interfaces in `:core:*`. (`data/anime/build.gradle.kts:21`)
6. **`:core:tracker → :core:player`** — pulls MPV/FFmpeg transitively for `WatchProgressStore`. Conceptual smell — a tracker-stats module shouldn't depend on the media player. Fix: extract `WatchProgressStore`/`PlaybackStateStore` to a leaf `:core` module. (`core/tracker/build.gradle.kts:20`)
7. **13 cross-feature Kotlin imports** — all correspond 1-to-1 with the 3 Gradle feature→feature violations. No hidden coupling. (§4)
8. **6 of 10 version-catalog bundles are unused**; ~30 inline duplicate coordinate strings across modules. (§10.2)
9. **Convention-plugin Compose coordinates hardcoded** rather than read from the `compose.versions.toml` catalog — version bumps require 2 edits. (§9.5)
10. **ARCHITECTURE.md §3 module tree is stale** — missing `:core:tracker`, `:core:update-checker`, `:feature:episode-settings`. (§0)

### Empty stub modules (9 total)

11. **8 of 9 empty stubs are immediately removable** (zero inbound deps): `:core:network`, `:core:notification`, `:core:source-local`, `:data:manga`, `:data:tracker`, `:feature:home`, `:feature:more`, `:feature:episode-list`. (§7)
12. **`:feature:player` is depended on by `:app` but :app never uses it** in source — also safe to remove (delete one line in `:app/build.gradle.kts:68` + the `include(":feature:player")` line in `settings.gradle.kts:77`). (§7)

### Phantom module

13. **`:i18n` is declared in `settings.gradle.kts:85` but the directory does not exist**. Nothing depends on it. The build apparently passes because `TYPESAFE_PROJECT_ACCESSORS` only generates accessors for declared-but-also-existing projects — but this is fragile; any future `projects.i18n` reference would break the build. (§8)

### Fat modules

14. **`:app` is the heaviest composition root** with 31 project deps (12 core + 3 data + 16 feature). The next-heaviest is `:feature:watch` with 9 (it's the most cross-cutting feature). `:core:backup` is the heaviest `:core:*` with 8 (it's the backup aggregation point). (§6)

### No circular dependencies

15. **The full graph is a DAG.** Topological sort: leaf primitives (common, source-api, database, preferences) → derived core (designsystem, anilist, episode-metadata, download) → fatter core (player, update-checker, tracker) → backup. The 3 feature→feature violations are acyclic (all point at `:feature:video-resolver` and `:feature:anime-details`, which have no feature deps themselves). (§3 last row)

### `api(project(...))` usage

16. **There are ZERO `api(project(...))` declarations anywhere** — every project-to-project dep is `implementation(...)`. This means transitive project deps are NOT exposed across module boundaries, which is correct per ARCHITECTURE.md §3 "(interfaces only)" intent. The only `api(...)` declarations are for external libs: `:core:source-api` exposes `okhttp` + `injekt` (extensions need them on the host classpath), and `:core:player` exposes `aniyomi-mpv` (because `AnikutaMPVView` extends `BaseMPVView`).

---

## 12. Next-action recommendations (for the architecture-plan owner)

1. **Promote link stores to interfaces.** Move `SourceLinkStore` + `ExtensionLinkStore` from `:data:extension` to interface definitions in `:core:source-api` (or a new `:core:source-link` module). Impl stays in `:data:extension`. This eliminates violation #1 (`:core:backup → :data:extension`) and blurred boundary #5 (`:data:anime → :data:extension`).

2. **Promote `EpisodeDisplayPreferences` to `:core:preferences`.** Move it (and `EpisodeDisplayPrefs`) out of `:feature:anime-details`. This eliminates violation #2 (`:feature:episode-settings → :feature:anime-details`).

3. **Promote `ResolverService` + resolver result models to `:core:source-api`** (or a new `:core:video-resolver` interface module). The UI (`VideoResolverSheet`) stays in `:feature:video-resolver`; the contract moves to `:core`. This eliminates violations #3 + #4 (`:feature:watch → :feature:video-resolver`, `:feature:download → :feature:video-resolver`).

4. **Extract `WatchProgressStore` + `PlaybackStateStore` from `:core:player` to a leaf `:core:playback-state` module** (or fold into `:core:preferences`). This removes the `:core:tracker → :core:player` smell and the `:core:backup → :core:player` dep — both pull these stores only for stats/backup, not for playback. Reduces transitive MPV/FFmpeg bloat in tracker + backup.

5. **Delete the 9 empty stubs.** 8 are zero-inbound; the 9th (`:feature:player`) needs one line removed from `:app/build.gradle.kts:68` + one from `settings.gradle.kts:77`. Total cleanup: 9 modules gone, 41 → 32 declared modules.

6. **Resolve `:i18n`.** Either create the directory + minimal `build.gradle.kts` (per ADR-027 — Moko Resources), or remove `include(":i18n")` from `settings.gradle.kts:85` until the i18n work actually starts.

7. **Migrate inline dependency coordinates to the catalogs.** ~30 inline `kotlinx-coroutines-core:1.10.1` declarations → `kotlinx.coroutines.core` (already in catalog). ~15 inline `lifecycle-runtime-ktx:2.8.7` → `androidx.lifecycle.runtimektx` (already in catalog). Etc. Also delete the 6 unused bundles (or repurpose them).

8. **Refresh ARCHITECTURE.md §3 module tree** to include `:core:tracker`, `:core:update-checker`, `:feature:episode-settings` (currently missing), and remove the 9 empty stubs if/when they're deleted.

9. **Refactor convention plugins** to read Compose coordinates from the `compose.versions.toml` catalog (not hardcode them). Optional: extract shared SDK/Java/desugar config into a `anikuta.base` plugin applied by both `anikuta.library` and `anikuta.android.application` to remove the boilerplate duplication.

---

**End of EVID-04.**
