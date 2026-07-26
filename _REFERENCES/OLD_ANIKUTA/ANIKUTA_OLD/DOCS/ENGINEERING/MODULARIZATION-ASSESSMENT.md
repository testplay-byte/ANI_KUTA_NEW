# ANI-KUTA — Modularization & Structure Assessment

> Prioritized, verified list of structural problems in the ANI-KUTA codebase.
> Every item is based on actual files on `main` @ `ca644ad` (2026-07-16).
>
> The user's explicit goal: make the structure **modular, properly documented,
> and future-proof / easy to manage**. This file is the diagnostic that informs
> the eventual restructure. **No changes have been made yet** — this is analysis
> only, awaiting your direction.

---

## Executive summary

The codebase is **functionally complete** (Phases 0–7 done, player verified on-device)
but **structurally strained** in two places:

1. **The `:app` module is a monolith** (147 files / 38.5K LoC) holding UI, player,
   download, extensions, DI, AND app-local data/domain packages. The player and
   detail subsystems contain god-objects that block velocity.
2. **Documentation is fragmented and partly stale.** Six docs claim `app/` "does
   not exist yet"; a password is committed in plaintext; recent work lives only
   in `worklog.md`.

The lower 4 modules (`:core`, `:data`, `:domain`, `:source-api`) are in
**reasonable shape** — clean layering, compiler-enforced boundaries — with a
handful of leaks (Compose annotations in domain, `api`-exposed Injekt/RxJava,
dead manga code, zero DB migrations).

**Recommended sequencing (not started — awaiting your go-ahead):**
P0 security fix → P0 doc "current state" fix → P1 god-object refactors →
P1 feature-module extraction → P2 consistency pass → P3 cleanup.

---

## P0 — Critical (act first)

### P0.1 SECURITY: Supabase DB password committed in plaintext
- **Where:** `DOCS/APP/STRUCTURE/supabase-schema.md` line ~130 (in a how-to-connect bash script).
- **Impact:** Anyone with repo read access has the DB password. Contradicts the project's own credentials policy (`.gitignore` covers `*.env`, `secrets.properties`, etc., but not a password embedded in a markdown doc).
- **Fix:** Redact the password to `[PASSWORD — see MEMORY/CREDENTIALS/]`, then **rotate the Supabase DB password** (the leaked one must be considered compromised). Also `git filter-repo` or BFG to purge history if you want it gone from history (optional — the repo is on a test account).

### P0.2 No single-source-of-truth "current state" doc
- **Where:** README phase table, `DOCS/PLAN/ROADMAP.md`, `MEMORY/PROJECT-CONTEXT.md`, `MEMORY/CORE-RULES.md` §0, and `worklog.md` all disagree on what's done.
- **Impact:** A new primary engineer spends 2–4 hours reconciling docs against code to learn the real state.
- **Fix:** Create a 1-page `DOCS/CURRENT-STATE.md` (latest main commit + date, latest verified build SHA, what's on `main` vs `player-experiment`, open Known Issues count, next planned phase, 1-paragraph "what changed since last session log"). Update after every merge to `main`. (The `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` I just wrote partially fills this role, but a short status snapshot is still needed.)

### P0.3 Six docs falsely claim `app/` "does not exist yet"
- **Where:** `DOCS/NAVIGATION-GUIDE.md`, `DOCS/ARCHITECTURE/README.md`, `SETUP/README.md`, `BUILD-APK/README.md`, `MEMORY/CORE-RULES.md` §0, and (implicitly) `DOCS/PLAN/README.md`.
- **Impact:** Actively misleads any newcomer.
- **Fix:** Update each to reflect that `app/` exists and is on Phase 7.5. (Low effort, high clarity payoff.)

### P0.4 `PlayerActivity.kt` is a 2,430-LoC god object (inverted MVVM)
- **Where:** `app/src/main/java/app/anikuta/player/PlayerActivity.kt`.
- **Impact:** The single biggest velocity blocker. `PlayerActivity` owns Android lifecycle + MPV native surface + **ALL** player business logic (episode switching, video resolution, track auto-select, AniList sync, PiP, audio focus, screenshot, sleep timer). Meanwhile `PlayerViewModel` (437 LoC) is just a state bag with 33 setters and **no logic** — the opposite of the pattern used everywhere else in the app. This is why the player has been the most fragile subsystem (3 CRITICAL race conditions found in Session 44's code review).
- **Fix:** Extract a `PlayerController` (no Android UI deps) holding episode/video resolution + track selection + AniList sync; move logic into `PlayerViewModel`; reduce `PlayerActivity` to lifecycle + MPV surface ownership + event dispatching. Target: `PlayerActivity` ≤ ~500 LoC, `PlayerViewModel` holds the logic. **This is the single highest-value refactor in the codebase** — but it is also the riskiest (MPV native lifecycle is fragile). Must be done carefully with on-device verification.

### P0.5 No SQLDelight migrations — schema changes will wipe user data
- **Where:** `data/src/main/sqldelight/` has 18 `.sq` files but **zero `.sqm` migration files** (the `data.md` doc falsely claims "23 migrations").
- **Impact:** The very first schema change after launch will silently wipe every user's local DB (library, history, categories, downloads metadata).
- **Fix:** Before any schema change, either (a) add `.sqm` migration files following SQLDelight's `1.sqm, 2.sqm, …` convention, or (b) explicitly accept "wipe on schema change" as a pre-launch tradeoff and document it. Add a `data/src/main/sqldelight/migrations/` workflow to the working rules.

---

## P1 — High (structural, blocks future-proofing)

### P1.1 `DetailViewModel.kt` (1,313 LoC) mixes 5 concerns
- **Where:** `app/.../ui/detail/DetailViewModel.kt`.
- **Impact:** AniList fetch + extension source matching + video resolution + download orchestration + bookmarking + refresh-staging all in one ViewModel. Hard to test, hard to change one concern without breaking another.
- **Fix:** Split into `DetailMetadataLoader`, `DetailEpisodeResolver`, `DetailDownloadController` interactors; ViewModel becomes a thin state aggregator (mirrors the canonical pattern `library/` already follows).

### P1.2 Four oversized single-Composable screen files
- **Where:** `ui/detail/DetailScreen.kt` (1,695 LoC), `ui/search/SearchScreen.kt` (1,363 LoC), `ui/library/LibraryScreen.kt` (1,199 LoC), `player/PlayerScreen.kt` (1,048 LoC — only 2 `@Composable` decls).
- **Impact:** Hard to navigate; small UI tweaks require touching a huge file.
- **Fix:** Extract per-section composables into separate files (e.g. `detail/EpisodeRow*.kt`, `library/LibraryFilterSheet.kt`, `search/ExtensionBrowseSection.kt`, `player/PlayerMinimizedRoot.kt` + `PlayerFullscreenRoot.kt` + `PlayerLoadingOverlay.kt`).

### P1.3 No feature-module split — `:app` is a 147-file monolith
- **Where:** The entire `:app` module.
- **Impact:** Everything recompiles on any change; no compiler-enforced boundaries between features; DI surface is unbounded.
- **Fix (medium-term):** Extract `:feature:player`, `:feature:library`, `:feature:search`, `:feature:detail`, `:feature:settings`, `:feature:download` Gradle submodules. This also forces the DI boundary to clean up. **Large change — propose a plan before starting.**

### P1.4 Navigation graph is a single 451-LoC file with 31 routes
- **Where:** `app/.../navigation/AnikutaNavGraph.kt`.
- **Impact:** Will keep growing; hard to find a route.
- **Fix:** Split into `NavGraph.kt` (top-level + bottom-nav) + `SettingsNavGraph.kt` + `ExtensionNavGraph.kt` as extension functions on `NavGraphBuilder`.

### P1.5 Naming collision: `data/` and `domain/` exist as both `:app` packages AND Gradle modules
- **Where:** `app/anikuta/data/` + `:data` module; `app/anikuta/domain/` + `:domain` module.
- **Impact:** Confuses readers; the `:app` packages are NOT duplicates (they hold remote sources + Anikuta-specific overrides) but the names suggest they are.
- **Fix:** Rename `app/anikuta/data/` → `app/anikuta/remote/` (for anilist/supabase/tracker/metadata/notification) and keep `app/anikuta/data/cache/` or promote it to `:data`. Rename `app/anikuta/domain/` → merge `SourcePreferences`+`TrustAnimeExtension` into `:domain` if feasible, or rename to `app/anikuta/domainoverrides/`.

### P1.6 Compose leaks into `:domain` and `:source-api`
- **Where:** `:domain` uses `compileOnly(platform(compose.bom))` + `compileOnly(compose.ui)` for `@Immutable`/`@Stable` on ≥10 models; `:source-api` uses `implementation(compose.ui)` for `@Stable` on `AnimeFilterList`. Inconsistent (compileOnly vs implementation).
- **Impact:** Couples a supposedly pure-Kotlin domain/contract layer to the UI framework.
- **Fix:** Replace with a tiny in-repo `@Immutable`/`@Stable` annotation (or a `:core:annotations` module); drop the Compose dependency from both modules.

### P1.7 Injekt `api`-exposed from 4 modules
- **Where:** `:core`, `:source-api`, `:domain`, `:data` all `api(libs.injekt)`.
- **Impact:** Every transitive consumer can `inject<T>()` from anywhere — DI surface is unbounded; nothing stops a Composable from reaching into the DB.
- **Fix:** Switch to `implementation` + a single DI entry point in `:app`; or accept it (aniyomi does the same) and document the tradeoff.

### P1.8 `:domain` carries dead manga code despite Decision D2 (anime-only)
- **Where:** `domain/mihon/extensionrepo/manga/` (7 files, ~167 LoC dead); `LibraryPreferences` + `DownloadPreferences` carry ~20 manga/chapter getters + a `ChapterSwipeAction` enum nothing reads.
- **Impact:** Confuses the "anime-only" intent; dead code.
- **Fix:** Delete the manga subpackage; prune manga getters from the two preference classes. Verify no references first.

### P1.9 `ExtensionReposViewModel` reaches into the DB handler directly
- **Where:** `app/.../ui/settings/ExtensionReposViewModel.kt` manually constructs `AnimeExtensionRepoRepositoryImpl(get<AnimeDatabaseHandler>())` as a DI fallback.
- **Impact:** Violates "ViewModel depends only on domain interactors"; couples a ViewModel to the DB layer.
- **Fix:** Make `AppModule` registration robust so the fallback is unnecessary; remove the DB import.

### P1.10 `RELEASE`/`release-debuggable` build types are unsigned; signing docs stale
- **Where:** `app/build.gradle.kts` (release build type has no `signingConfig`); `SETUP/README.md` + `BUILD-APK/README.md` describe a TODO signing plan never implemented.
- **Impact:** No installable release APK is possible today; the `release-debuggable` build-file comment claiming "R8 optimization" is false (`isMinifyEnabled=false`).
- **Fix:** Define a release signing strategy (GitHub Actions secret + `signingConfig` from env vars, keystore in `MEMORY/CREDENTIALS/keystore/` gitignored). Update SETUP/BUILD-APK docs. Fix the misleading comment.

---

## P2 — Medium (consistency, docs, hygiene)

### P2.1 Inconsistent feature-folder pattern
- `library/` = Screen+ViewModel+Store+Prefs (canonical); `history/`/`search/` = Screen+ViewModel (no Store); `home/` = Screen+ViewModel (no Store); `detail/` = Screen+ViewModel+helpers (uses `LibraryStore` cross-package); `debug/` = Screen+ViewModel.
- **Fix:** Pick one canonical pattern (recommend: Screen + ViewModel + optional Store + optional Prefs) and apply it; document in `DOCS/ENGINEERING/`.

### P2.2 Oversized player control files
- `player/controls/EpisodeListView.kt` (850 LoC) bundles list + 3 row variants; `player/controls/sheets/PlayerSheets.kt` (560 LoC) bundles multiple sheets; `player/controls/MinimizedControls.kt` (523 LoC); `player/controls/SubtitleSettingsPanel.kt` (522 LoC).
- **Fix:** Split each sheet/row into its own file (mirrors the `detail/` package's `EpisodeRow*` split).

### P2.3 RxJava 1.3.8 (EOL) leaks via `:core` `api`
- **Where:** `:core` `api(libs.rxjava)`; used by `OkHttpExtensions.asObservable()` + `RxCoroutineBridge.awaitSingle()` — part of the extension-contract surface.
- **Impact:** Every module transitively pulls RxJava 1 (EOL since March 2018).
- **Fix:** Cannot simply drop (extensions depend on the contract surface). Plan a migration of the contract surface to coroutines/Flow (coordinated with an extension-lib bump) — or accept and document.

### P2.4 Doc-vs-reality drift in `DOCS/ARCHITECTURE.md`
- Says settings = 28 files (actual 32); AppModule = ~50 singletons (actual 56); nav = 10 routes (actual 31). Misses `ui/download/` folder, `ui/home/HomeViewModel.kt`, `ui/debug/DebugViewModel.kt`, `ui/components/SkeletonBox.kt`, `ui/detail/ThreeStagePullRefresh.kt`, 4 player files, 5 `download/engine/hls/` files, `util/storage/DiskUtil.kt` + `FFmpegUtils.kt`. Path drift: `AnimeExtensionLoader.kt` is in `extension/anime/util/` not `extension/anime/`.
- **Fix:** `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` is now the corrected version; either update `DOCS/ARCHITECTURE.md` or mark it superseded.

### P2.5 Stale `DOCS/APP/STRUCTURE/*.md` copy records
- `source-api.md`: claims 25 files (actual 28), claims `eu.kanade → app.anikuta` rename (reverted in Phase 5), says "compose-runtime" (actual compose.ui), omits Phase 5 typealias fix.
- `domain.md`: claims 146 files (actual 121), claims `storage/StorageManager` (doesn't exist), claims `download/anime/` + `DownloadManager` interface (only `DownloadPreferences`), omits the `mihon/extensionrepo/manga/` leak.
- `data.md`: claims 21 .kt (actual 25) + 17 .sq (actual 18); **falsely claims 23 .sqm migrations (zero exist)**; misses `AnimeDatabaseFactory.kt`, `cache/LocalCache.kt`, `anilistcache.sq`.
- `core.md`: claims 26 .kt (actual 29); **falsely claims `eu.kanade.tachiyomi.network.*` was renamed to `app.anikuta.core.network.*`** (it was not — all 13 network files still use `eu.kanade.*`).
- **Fix:** Update each copy record, or mark them all superseded by `TECHNICAL-OVERVIEW.md`.

### P2.6 ROADMAP.md has duplicate Phase 9 entries + no Phase 10
- **Fix:** Renumber; align with README's phase table.

### P2.7 Session-number collisions
- `worklog.md`: Sessions 21, 22, 23, 28 each appear twice. `MEMORY/SESSION-LOGS/`: two `session-31` files; `SESSION-LOGS/README.md` index stops at Session 20.
- **Fix:** Renumber or date-stamp; rebuild the index.

### P2.8 Recent activity only in `worklog.md`
- `MEMORY/SESSION-LOGS/` stops at Session 31 (Library revamp); the entire `player-experiment` download rebuild (12+ task iterations) is undocumented in structured logs.
- **Fix:** Backfill structured session logs from `worklog.md`, OR formalize `worklog.md` as the canonical activity log and stop maintaining `SESSION-LOGS/`.

### P2.9 `REFERENCE/` inflates CI cache invalidation
- The CI cache key's `**/*.gradle.kts` glob matches `REFERENCE/**/*.gradle.kts` (committed aniyomi snapshot).
- **Fix:** Tighten the glob to exclude `REFERENCE/**` (e.g. use `hashFiles('app/**/*.gradle.kts', 'core/**/*.gradle.kts', ...)` or a generated lock file).

### P2.10 No convention plugins
- compileSdk/minSdk/jvmTarget/Java-version duplicated across 5 `build.gradle.kts` files.
- **Fix:** Add a `build-logic/` convention plugin (or `buildSrc`) to centralize Android config and prevent drift.

### P2.11 Test scaffolding is theatrical (PARTIALLY RESOLVED 2026-07-16)
- Every module declares `testImplementation(libs.junit)`; `app` declares `testInstrumentationRunner`; previously **zero test sources existed**.
- **Status:** A thin unit-test layer has been added for 4 pure-logic pieces in `:domain` (`EpisodeRecognition`, `SeasonRecognition`, `AnimeFetchInterval`, `GetApplicationRelease`) — see `DOCS/ENGINEERING/TESTING.md`. The `app` module's test scaffolding remains cargo-culted (no instrumented tests yet). CI does not yet run `./gradlew :domain:test` (planned follow-up).

### P2.13 `CreateAnimeExtensionRepo` not unit-testable (concrete service dependency)
- `CreateAnimeExtensionRepo` depends on `ExtensionRepoService`, which is a **concrete class** (not an interface) whose constructor needs `NetworkHelper` → Android `Context`. The interactor cannot be constructed in a pure JVM unit test.
- The URL-normalization + regex validation logic (the most bug-prone part) is inline-private, so it can't be tested directly either.
- **Fix (small refactor, not yet done):** Either (a) extract an `IExtensionRepoService` interface and have the concrete class implement it, so `CreateAnimeExtensionRepo` depends on the interface (fakeable); or (b) extract the URL-normalization into a pure top-level function and test that directly. Option (b) is the smallest change and tests the most bug-prone piece. See `DOCS/ENGINEERING/TESTING.md` → "Testability gap".

### P2.12 Missing standard onboarding docs
- No CONTRIBUTING, no RELEASE, no TESTING-PLAN, no module-level READMEs, no ADR status tracking, no dependency-update policy, no CI explainer, no LICENSE at repo root.
- `DOCS/APP/HOW-TO/` and `DOCS/APP/RATIONALE/` referenced in `DOCS/APP/README.md` but don't exist on disk.
- **Fix:** Create the missing docs as the project matures toward release.

---

## P3 — Low (cleanup)

### P3.1 Dead/unused items
- `androidx.media` catalog entry (PlayerMediaSession was removed — D.2).
- `Video.kt` has 3 `TODO(1.6): Remove after ext lib bump` legacy constructors.
- `AnimeFilterList.equals` always returns `false` (intentional but undocumented).
- `util/VideoInfo.kt` (4 LoC dead).
- `-Xcontext-parameters` compiler flag in `:domain` may be unused (only `ExtensionRepoService` was the documented user; it uses `with(json){}` not `context(...)`).
- `InMemoryPreferenceStore.getStringSet()` throws `TODO`.
- DB name `tachiyomi.animedb` (leftover aniyomi string).
- `StringListColumnAdapter` uses `", "` separator (fragile if values contain `, `).
- `runBlocking` inside transaction context.
- `Constants.kt` has manga-side leftovers.
- `DohProviders.kt` could be data-driven.
- `GlobalScope` launchers.
- Empty `data/src/main/consumer-rules.pro`.

### P3.2 `live-preview/` undocumented
- 5 HTML files (index, build-progress, project-plan, player-design-plan, player-ux-analysis) with no README.

### P3.3 ntfy topic inconsistency
- `CORE-RULES.md` says `ntfy.sh/THEANIMEAPPTASKISDONE`; `PLAYER-RULES.md` + CI say `ntfy.sh/TASKISDONE`. Two different topics.

### P3.4 `plan.md` (39 KB) status field is stale
- Says "Planning — NOT ready for execution" but the worklog shows the download-system implementation WAS executed. Either update status or mark as historical.

### P3.5 (RESOLVED) `player-experiment` branch — no longer exists
- The `player-experiment` branch was **merged into `main`** (merge commit `a05d07c`) and **deleted**. All download-system work is on `main`. The project's own `worklog.md` still references "the `player-experiment` branch" in ~12+ task entries — those references are **historical/stale** (written while the branch existed). No action needed on the branch itself; the stale worklog references are a documentation-hygiene item (see P2.8).

---

## What's actually in GOOD shape (don't break these)

- **The 5-module Gradle split** — compiler-enforced UI/logic separation works; matches aniyomi for upstream tracking.
- **The `:core` preference system** — sealed `AndroidPreference` + Flow-backed reactive `PreferenceStore` is clean.
- **The `:data` ↔ `:domain` repository pattern** — 11 repos cleanly implement `:domain` interfaces via `AnimeDatabaseHandler`.
- **The source-api contract preservation** — keeping `eu.kanade.tachiyomi.animesource.*` binary names + `app.anikuta.source.api.*` typealiases is the correct solution (learned the hard way in Phase 5).
- **The `library/` feature folder** — Screen+ViewModel+Store+Prefs is the canonical pattern other folders should follow.
- **The 3-tier `CacheManager`** — Local → Supabase → AniList with TTL + stale fallback is a sound design.
- **The AniyomiSourceBridge fuzzy matching** — bridges AniList ↔ extensions with source-priority drag-and-drop tiebreaking.
- **The extension trust system** — max-2-trusted popup is a reasonable simplification of aniyomi's repo-fingerprint model.

---

_Next step: discuss this assessment with the user, agree on sequencing, then execute P0 items first. No structural changes have been made._
