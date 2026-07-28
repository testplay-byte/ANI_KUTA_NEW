# 04 — Module Dependency Graph

> **Phase 1 / Current State.** The complete module dependency graph for all 41 declared Gradle modules, with violations, blurred boundaries, fat modules, and the convention-plugin summary. Raw evidence: `_evidence/EVID-04-module-deps.md`.

---

## 1. Executive summary

- **41 modules declared** in `settings.gradle.kts` (`:app` + 15 `:core:*` + 5 `:data:*` + 19 `:feature:*` + 1 phantom `:i18n`). **40 `build.gradle.kts` files exist on disk** — `:i18n` has no directory.
- **Zero `api(project(...))` declarations** — every project-to-project dependency is `implementation(...)`. (External libs occasionally use `api(...)` — e.g. `:core:source-api` exposes OkHttp + Injekt, `:core:player` exposes the MPV lib.)
- **The graph is a DAG** — no circular dependencies.
- **4 hard violations** of `ARCHITECTURE.md §3`:
  1. `:core:backup → :data:extension` (core→data inversion)
  2. `:feature:episode-settings → :feature:anime-details` (feature→feature)
  3. `:feature:watch → :feature:video-resolver` (feature→feature)
  4. `:feature:download → :feature:video-resolver` (feature→feature)
- **2 blurred boundaries**: `:data:anime → :data:extension` (data→data); `:core:tracker → :core:player` (drags MPV/FFmpeg transitively).
- **8 of 9 empty stubs are immediately removable**; the 9th (`:feature:player`) needs one line removed from `:app` first.
- **`:i18n` is referenced ONLY in `settings.gradle.kts:85`** — zero dependents. Fragile but currently harmless.
- **Fat modules**: `:app` (31 deps), `:feature:watch` (9), `:core:backup` (8), `:data:extension` / `:feature:anime-details` (7 each).
- **4 of 10 version-catalog bundles are used**; 6 are dead. ~30 inline duplicate coordinate strings across modules.

---

## 2. The dependency graph (all 41 modules)

### 2.1 `:app` — composition root (31 project deps)

```
:app
  → :core:common, :core:designsystem, :core:database, :core:anilist,
    :core:preferences, :core:player, :core:update-checker, :core:source-api,
    :core:tracker, :core:download, :core:episode-metadata, :core:backup
  → :data:anime, :data:extension, :data:history
  → :feature:browse, :feature:search, :feature:anime-details, :feature:library,
    :feature:extensions-settings, :feature:video-resolver, :feature:watch,
    :feature:player (empty stub), :feature:episode-settings, :feature:settings,
    :feature:history, :feature:updates, :feature:my, :feature:trackers,
    :feature:download, :feature:backup
```

**12 core + 3 data + 16 feature = 31 project deps.** Plus 7 inline non-catalog coordinates + catalog bundles (`libs.bundles.koin`, `libs.bundles.voyager`, `libs.bundles.test`, etc.).

### 2.2 `:core:*` layer (15 modules)

```
:core:common                    (0 deps — leaf primitive)
:core:designsystem              → :core:common, :core:preferences
:core:network                   (0 deps — empty stub)
:core:database                  (0 deps — leaf primitive; sqldelight plugin)
:core:preferences               (0 deps — leaf primitive)
:core:anilist                   → :core:common, :core:preferences
:core:tracker                   → :core:common, :core:database, :core:preferences,
                                    :core:anilist, :core:player              ◄── ⚠ blurred (drags MPV)
:core:episode-metadata          → :core:common, :core:preferences
:core:source-api                → (external: OkHttp api, Injekt api, kotlinx-serialization)
:core:source-local              (0 deps — empty stub)
:core:player                    → :core:common, :core:preferences, :core:database,
                                    :core:source-api                          (exposes MPV lib via api)
:core:update-checker            → :core:common, :core:preferences, :core:anilist,
                                    :core:database
:core:download                  → :core:common, :core:preferences, :core:source-api
:core:notification              (0 deps — empty stub)
:core:backup                    → :core:common, :core:preferences, :core:database,
                                    :core:anilist, :core:source-api,
                                    :data:extension                           ◄── 🔴 VIOLATION (core→data)
```

### 2.3 `:data:*` layer (5 modules)

```
:data:anime                     → :core:common, :core:database, :core:preferences,
                                    :data:extension                            ◄── ⚠ blurred (data→data)
:data:manga                     (0 deps — empty stub)
:data:extension                 → :core:common, :core:source-api, :core:preferences,
                                    :core:database
:data:tracker                   (0 deps — empty stub)
:data:history                   → :core:common, :core:database
```

### 2.4 `:feature:*` layer (19 modules)

```
:feature:browse                 → :core:common, :core:designsystem, :core:anilist,
                                    :core:preferences
:feature:library                → :core:common, :core:designsystem, :core:preferences,
                                    :core:database, :data:anime
:feature:updates                → :core:common, :core:designsystem, :core:preferences,
                                    :core:anilist, :core:database, :data:anime
:feature:history                → :core:common, :core:designsystem, :core:preferences,
                                    :core:player, :core:database, :data:history
:feature:search                 → :core:common, :core:designsystem, :core:anilist,
                                    :core:source-api, :core:preferences, :data:extension
:feature:my                     → :core:common, :core:designsystem, :core:preferences,
                                    :core:anilist, :core:tracker, :core:database,
                                    :data:anime
:feature:more                   (0 deps — empty stub)
:feature:anime-details          → :core:common, :core:designsystem, :core:anilist,
                                    :core:source-api, :core:preferences, :core:database,
                                    :data:anime, :data:extension
:feature:episode-list           (0 deps — empty stub)
:feature:episode-settings       → :core:common, :core:designsystem, :core:preferences,
                                    :feature:anime-details                     ◄── 🔴 VIOLATION (feat→feat)
:feature:video-resolver         → :core:common, :core:designsystem, :core:source-api,
                                    :core:preferences
:feature:watch                  → :core:common, :core:designsystem, :core:preferences,
                                    :core:player, :core:source-api, :core:database,
                                    :core:episode-metadata, :data:anime,
                                    :feature:video-resolver                    ◄── 🔴 VIOLATION (feat→feat)
:feature:player                 (0 deps — empty stub)
:feature:extensions-settings    → :core:common, :core:designsystem, :core:preferences,
                                    :data:extension
:feature:settings               → :core:common, :core:designsystem, :core:preferences
:feature:trackers               → :core:common, :core:designsystem, :core:tracker,
                                    :core:preferences
:feature:backup                 → :core:common, :core:designsystem, :core:backup,
                                    :core:preferences
:feature:download               → :core:common, :core:designsystem, :core:download,
                                    :core:preferences, :core:source-api,
                                    :feature:video-resolver                    ◄── 🔴 VIOLATION (feat→feat)
```

### 2.5 `:i18n` — phantom

Declared at `settings.gradle.kts:85`. **Directory does NOT exist on disk.** Zero build.gradle.kts. Nothing depends on it.

---

## 3. Layered diagram (conceptual)

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
          │  ⚠ FEATURE→FEATURE VIOLATIONS (3):
          │      :feature:episode-settings → :feature:anime-details
          │      :feature:watch            → :feature:video-resolver
          │      :feature:download         → :feature:video-resolver
          │
          └──▶ (cross-feature should route through :core per ARCHITECTURE.md §3)
```

**Conceptual flow:** `:app → :feature:* → :core:*` (interfaces) and `:feature:* → :data:*` (impls, wired via Koin). `:data:* → :core:*`. **Never** `:feature:* → :feature:*`.

---

## 4. Violations table

`ARCHITECTURE.md §3` invariants (verbatim):
```
:app  →  :feature:*  →  :core:*  (interfaces only)
                     →  :data:*   (impls, wired via Koin)
:feature:*  ↛  :feature:*  (NEVER — cross-feature goes through :core)
:data:*  →  :core:* (to implement interfaces)
```

| # | Module | Forbidden dep | Rule violated | Evidence (file:line) |
|---|---|---|---|---|
| 1 | `:core:backup` | `→ :data:extension` | `:core:* ↛ :data:*` (inverts "`:data:* → :core:*`") | `core/backup/build.gradle.kts:40` |
| 2 | `:feature:episode-settings` | `→ :feature:anime-details` | `:feature:* ↛ :feature:*` | `feature/episode-settings/build.gradle.kts:19` |
| 3 | `:feature:watch` | `→ :feature:video-resolver` | `:feature:* ↛ :feature:*` | `feature/watch/build.gradle.kts:18` |
| 4 | `:feature:download` | `→ :feature:video-resolver` | `:feature:* ↛ :feature:*` | `feature/download/build.gradle.kts:28` |

### 4.1 Why each violation exists (root cause)

**Violation 1 — `:core:backup → :data:extension`:**
`SourceLinkBackupProvider` and `ExtensionLinkBackupProvider` need `SourceLinkStore` + `ExtensionLinkStore`, which live in `:data:extension`. The clean fix is to move the link-store interfaces (or the stores themselves) to `:core:common` or `:core:backup`, with impls in `:data:extension`. Alternatively, define a `BackupSourceLinkProvider` interface in `:core:backup` and let `:data:extension` implement it.

**Violations 2-4 — three `:feature:* → :feature:video-resolver` / `:feature:anime-details`:**
- `:feature:episode-settings` uses `EpisodeRowPreview` from `:feature:anime-details` for its live preview.
- `:feature:watch` uses `VideoResolverSheet` + `ResolverService` from `:feature:video-resolver` for quality/subtitle selection.
- `:feature:download` uses `DownloadVideoPickerSheet` + `ResolverService` from `:feature:video-resolver` for the download video picker.

The clean fix: extract the shared components (`EpisodeRowPreview`, `VideoResolverSheet`, `DownloadVideoPickerSheet`, `ResolverService`) into `:core:designsystem` (for pure UI) or a new `:core:video-resolver` / `:core:episode-display` module (for logic-bearing components). The video-resolver *logic* (`ResolverService`, `VideoTitleParser`, `ResolverStrategyPicker`) should arguably be a `:core` module, not a `:feature` module — it has no UI of its own.

---

## 5. Blurred boundaries (Kotlin-import level)

These aren't Gradle violations, but they're smells:

| # | From → To | Type | Why it's a smell |
|---|---|---|---|
| 1 | `:data:anime → :data:extension` | data→data | `AnimeRepositoryImpl` needs `SourceLinkStore` + `ExtensionLinkStore` from `:data:extension` to resolve anime by source+url. Data modules importing other data modules isn't forbidden by ARCHITECTURE.md, but it creates a tighter coupling than the conceptual model implies. |
| 2 | `:core:tracker → :core:player` | core→core | `TrackSyncManager` observes `WatchProgressStore` (in `:core:player`). This drags the entire MPV + FFmpeg transitive dependency tree into `:core:tracker`. `WatchProgressStore` should arguably live in `:core:common` or `:core:history` (a new module), not `:core:player`. |

**13 cross-feature Kotlin imports** were found via grep — all match the 3 Gradle violations 1-to-1. No hidden coupling. The Gradle deps and the imports are consistent.

---

## 6. Empty stub modules — removability audit

9 declared modules have **0 `.kt` files**. Are they depended on?

| Module | Inbound deps | Removable? |
|---|---|---|
| `:core:network` | 0 | ✅ Yes — remove from `settings.gradle.kts` |
| `:core:notification` | 0 | ✅ Yes (but keep if notifications work is planned — ADR-014) |
| `:core:source-local` | 0 | ✅ Yes (but keep if local-files-as-source is planned) |
| `:data:manga` | 0 | ✅ Yes (but keep — ADR-009 says manga is deferred but architecture-ready) |
| `:data:tracker` | 0 | ✅ Yes (tracker impls live in `:core:tracker` instead) |
| `:feature:home` | 0 | ✅ Yes (Home tab = `BrowseScreen` in `:feature:browse`) |
| `:feature:more` | 0 | ✅ Yes (More tab rendered inline in `MainActivity.kt`/`MoreScreens.kt`) |
| `:feature:player` | 1 (`:app`) | ⚠ Remove the `:app` dep first, then the module |
| `:feature:episode-list` | 0 | ✅ Yes (episode list lives in `:feature:anime-details`/`EpisodesSection.kt`) |

**Recommendation:** Either remove the removable stubs (declutter `settings.gradle.kts`) OR add a one-line README to each explaining why it's intentionally empty, so future agents don't "fix" them.

---

## 7. `:i18n` — the phantom module

- Declared at `settings.gradle.kts:85`.
- **No directory on disk.** No `build.gradle.kts`. No `.kt` files.
- **Zero dependents** — no module's `build.gradle.kts` references `projects.i18n`.
- ARCHITECTURE.md §3 says it should hold Moko Resources strings (ADR-027), but it was never created.
- **Risk:** Currently harmless (nothing references it). But if anyone adds `implementation(projects.i18n)` to a module, the build breaks.
- **Recommendation:** Either create the directory + a minimal `build.gradle.kts` (with the Moko Resources plugin), OR remove the declaration from `settings.gradle.kts`.

---

## 8. Fat modules (by dependency count)

| Module | Project deps | Notes |
|---|---|---|
| `:app` | 31 | Composition root — expected to be fat. But `AppController` (~600 lines) is flagged for future split (ADR-037). |
| `:feature:watch` | 9 | Pulls `:feature:video-resolver` (violation) + 8 core/data. |
| `:core:backup` | 8 | Pulls `:data:extension` (violation) + 7 core. |
| `:data:extension` | 7 | The extension manager — inherently central. |
| `:feature:anime-details` | 7 | Details page + episodes + source switcher. |
| `:core:tracker` | 5 | Pulls `:core:player` (smell) + 4 core. |
| `:core:update-checker` | 4 | |
| `:core:player` | 4 | |

---

## 9. Convention plugins (4, in `buildSrc/`)

| Plugin ID | File | Purpose |
|---|---|---|
| `anikuta.library` | `anikuta.library.gradle.kts` | Base Android library — AGP `com.android.library` + Kotlin, no Compose. Sets compileSdk/min/target, Java 17, core library desugaring, JUnit 5. |
| `anikuta.library.compose` | `anikuta.library.compose.gradle.kts` | Adds Compose (compiler plugin + BOM 2025.03.00 + M3 + material-icons-extended). |
| `anikuta.android.application` | `anikuta.android.application.gradle.kts` | Base application — namespace, applicationId, versionCode/Name, signing config. |
| `anikuta.android.application.compose` | `anikuta.android.application.compose.gradle.kts` | Adds Compose + activity-compose to the app module. |

> ⚠️ `PROJECT_STARTUP.md` Step 4 lists only 3 convention plugins (omits `anikuta.android.application.compose`). Documentation drift.

**Helper files:**
- `anikuta/buildlogic/AndroidConfig.kt` — SDK/NDK/version constants (`COMPILE_SDK = 36`, `TARGET_SDK = 36`, `MIN_SDK = 26`, `NDK = "27.1.12297006"`, `BUILD_TOOLS = "35.0.1"`, Java 17, `APPLICATION_ID = "app.confused.anikuta"`, `VERSION_CODE = 1`, `VERSION_NAME = "0.1.0"`).
- `ProjectExtensions.kt` — git SHA / commit-count helpers.

---

## 10. Version catalogs (5, in `gradle/`)

| Catalog | File | Key versions |
|---|---|---|
| `libs` (main) | `libs.versions.toml` | Koin 4.0.0, Voyager 1.0.1, SQLDelight 2.0.2, Coil 3.1.0, OkHttp 5.0.0-alpha.14, Moko 0.24.5, AboutLibraries 11.6.3, Shizuku 13.1.0, LeakCanary 2.14, logcat 0.1, JUnit Jupiter 5.11.4, MockK 1.13.17, Kotest 5.9.1, Spotless 7.0.2, ktlint 1.5.0 |
| `kotlinx` | `kotlinx.versions.toml` | Kotlin 2.2.0, kotlinx-serialization-json 1.9.0, kotlinx-coroutines BOM 1.10.1, xmlutil 0.90.3, immutable-collections 0.3.8 |
| `androidx` | `androidx.versions.toml` | AGP 8.9.1, lifecycle 2.8.7, paging 3.3.6, work-runtime 2.10.0, core-ktx 1.15.0, splashscreen 1.0.1 |
| `compose` | `compose.versions.toml` | Compose BOM 2025.03.00, activity-compose 1.10.1 |
| `anikutaLibs` | `anikuta.versions.toml` | aniyomi-mpv-lib 1.18.n, ffmpeg-kit 1.18, nanohttpd 2.3.1, seeker 1.2.2, torrserver 0.1.0, truetypeparser 2.1.4, media 1.7.0, constraint-layout-compose 1.1.0, smartexceptions 0.2.1 |

**Bundle usage:** 4 of 10 declared bundles are actually used (`libs.bundles.koin`, `libs.bundles.voyager`, `libs.bundles.compose`, `libs.bundles.test`). 6 are dead. ~30 inline duplicate coordinate strings exist across modules (e.g. `com.squareup.okhttp3:okhttp:5.0.0-alpha.14` appears in `:app` + `:core:anilist` + `:core:tracker` instead of being centralized in a catalog).

---

## 11. Where the proposed restructuring requires module changes

The Phase 2 proposals (internal ID system, provider abstraction, download redesign, extension evolution) require these module-level changes:

### 11.1 New modules to create

| Proposed module | Purpose | Driven by |
|---|---|---|
| `:core:history` (new) | `WatchProgressStore` + `PlaybackStateStore` moved here from `:core:player` | Removes the `:core:tracker → :core:player` smell; gives history its own home |
| `:core:video-resolver` (new, extracted from `:feature:video-resolver`) | `ResolverService`, `VideoTitleParser`, `ResolverStrategyPicker` (the logic, no UI) | Removes 2 feature→feature violations (`:feature:watch`, `:feature:download`) |
| `:core:provider-api` (new) | `HomeFeedProvider`, `SearchProvider`, `AiringScheduleProvider`, `MetadataProvider` interfaces | Provider abstraction (proposal 02) |
| `:data:provider-anilist` (new, extracted from `:core:anilist`) | AniList impls of the provider interfaces | Provider abstraction |
| `:core:media-source-api` (new, parallel to `:core:source-api`) | CloudStream-style extension contract (future) | Extension evolution (proposal 04) |
| `:data:media-extension` (new, parallel to `:data:extension`) | CloudStream-style extension loader (future) | Extension evolution |

### 11.2 Modules to fix (violation removal)

| Module | Fix |
|---|---|
| `:core:backup` | Remove `:data:extension` dep. Define `BackupSourceLinkProvider` interface in `:core:backup`; let `:data:extension` implement it. |
| `:feature:episode-settings` | Remove `:feature:anime-details` dep. Extract `EpisodeRowPreview` to `:core:designsystem` or `:feature:episode-settings`. |
| `:feature:watch` | Remove `:feature:video-resolver` dep. Depend on new `:core:video-resolver` for logic; keep `VideoResolverSheet` UI in `:feature:watch` or move to `:core:designsystem`. |
| `:feature:download` | Remove `:feature:video-resolver` dep. Same as above. |
| `:core:tracker` | Remove `:core:player` dep. Depend on new `:core:history` for `WatchProgressStore`. |

### 11.3 Modules to remove or document

| Module | Action |
|---|---|
| `:i18n` | Create the directory + minimal build script, OR remove from `settings.gradle.kts`. |
| 8 removable empty stubs | Remove from `settings.gradle.kts`, OR add READMEs explaining they're intentional. |
| `:feature:player` | Remove the `:app` dep, then remove the module. |

---

## 12. Conclusion

The module architecture is **mostly clean** — a proper DAG with clear layering, only 4 hard violations, and no circular dependencies. The violations have clear root causes (shared UI components + link stores in the wrong layer) and clear fixes (extract to `:core` or `:core:designsystem`).

The bigger structural opportunity is **splitting the fat modules**:
- `:app`'s `AppController` (~600 lines) is flagged for split (ADR-037).
- `:core:backup` pulling `:data:extension` is the most urgent violation to fix.
- `:core:tracker` pulling `:core:player` is a smell that a new `:core:history` module would resolve.

The proposed restructuring (Phase 2) adds 4-6 new modules — but they're all either (a) extractions of existing code into the right layer, or (b) parallel structures for the provider/extension abstractions. The net effect is a cleaner DAG with fewer violations, not a more complex one.

---

*Evidence source: `_evidence/EVID-04-module-deps.md` (776 lines).*
