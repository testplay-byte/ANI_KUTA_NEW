# ANIKUTA — Phased Implementation Plan

> **The complete roadmap from "empty skeleton" to "production-ready anime app".**
> Each phase has clear scope, exit criteria, and deliverables. We do NOT start a
> phase until the previous one's exit criteria are met.
>
> **Current status:** Phase 0b (Design & Planning) is COMPLETE. Ready for Phase 1
> pending the owner's go-ahead.

---

## How to read this plan

- **Phases are sequential.** Each builds on the previous. No skipping.
- **Exit criteria are hard gates.** A phase is done only when ALL exit criteria pass.
- **"By myself" vs "subagents"** — per the owner's preference, I handle planning
  and review myself. Subagents are used sparingly for parallel implementation
  work, with detailed prompts + full context.
- **Every phase ends with:** commit → push → CI green → ntfy notification →
  session handoff note.

---

## Phase 1 — Scaffold the Gradle project

**Goal:** An empty-but-compiling multi-module Gradle project under
`ANIKUTA_PROJECT/ANIKUTA/`, built by CI.

**Scope:**
1. Create the root Gradle project: `settings.gradle.kts`, `build.gradle.kts`,
   `gradle.properties`, `gradle/wrapper/`, `gradlew`, `gradlew.bat`.
2. Set up `buildSrc/` convention plugins (our own, `anikuta.*` namespace — NOT
   copied from Aniyomi's `mihon.*`):
   - `anikuta.android.application` + `anikuta.android.application.compose`
   - `anikota.library` + `anikuta.library.compose`
   - `anikuta.code.lint`
3. Create version catalogs under `gradle/`:
   - `libs.versions.toml` (main: OkHttp, Koin, SQLDelight, Coil, Voyager, etc.)
   - `kotlinx.versions.toml`, `androidx.versions.toml`, `compose.versions.toml`,
     `anikuta.versions.toml` (MPV, ffmpeg, etc.)
4. Create every module from the architecture (ARCHITECTURE.md §3) as a skeleton:
   - Each module gets `build.gradle.kts` + a `README.md` describing its purpose.
   - Modules are empty of code (just the build config + README).
5. Wire Koin in `:app` (a basic `Application` class + a startup Koin module).
6. Wire SQLDelight in `:core:database` (empty schema, one test table).
7. Set up the `:app` module with a trivial "hello world" `MainActivity` (Compose)
   so CI has something to compile.
8. Update the CI workflow (`.github/workflows/`) to compile a debug build of
   `ANIKUTA_PROJECT/ANIKUTA/` (replace the placeholder `ci-placeholder.yml`).
9. Configure the `debug` build type (release/preview/benchmark come later).

**Exit criteria:**
- [ ] CI produces a green debug build of the empty shell.
- [ ] All modules exist with `build.gradle.kts` + `README.md`.
- [ ] Koin is wired (a trivial `get<SomeDep>()` works).
- [ ] SQLDelight generates queries for one test table.
- [ ] `./gradlew :app:assembleDebug` succeeds on CI (NOT locally — ADR-003).

**Deliverables:** A compiling project skeleton. No features yet.

---

## Phase 2 — Core domain & data layer

**Goal:** The domain models + persistence layer + repository pattern are in place.

**Scope:**
1. **Domain models** (`:core:common` or a new `:core:domain`):
   - `Anime`, `Episode`, `Category`, `History`, `Track`, `Source` (anime-side).
   - `Manga`, `Chapter` (manga-side, skeleton — hidden per ADR-009).
   - Status fields per ADR-024: `release_date`, `last_refresh`,
     `last_metadata_fetch`, `next_episode_check`.
2. **SQLDelight schema** (`:core:database`):
   - `animes.sq`, `episodes.sq`, `categories.sq`, `anime_category.sq`,
     `animehistory.sq`, `animetrack.sq` (anime-side).
   - `mangas.sq`, `chapters.sq`, ... (manga-side, skeleton).
   - Views: `animeupdatesView`, etc.
   - Status columns on every relevant table.
3. **Repository interfaces** (`:core:common` or per-domain):
   - `AnimeRepository`, `EpisodeRepository`, `CategoryRepository`,
     `HistoryRepository`, `TrackRepository`.
4. **Repository implementations** (`:data:anime`, `:data:history`, etc.):
   - SQLDelight-backed. Expose `Flow<...>` for reactive queries.
5. **Koin wiring** — bind interfaces to impls in `:data:*` Koin modules.
6. **Unit tests** for repositories (success path + error + edge cases — Rule §10).

**Exit criteria:**
- [ ] Domain models defined with status fields.
- [ ] SQLDelight schema compiles; queries generate.
- [ ] Repository interfaces in `:core`, impls in `:data`.
- [ ] Koin binds them; a test can `get<AnimeRepository>()` and query.
- [ ] Unit tests pass on CI.

**Deliverables:** A working data layer (no UI yet).

---

## Phase 3 — Design system & theme

**Goal:** The `:core:designsystem` module is complete with the custom M3-inspired
theme + all reusable components from `DESIGN_LANGUAGE/`.

**Scope:**
1. **Theme** (`:core:designsystem`):
   - Color system (light/dark/AMOLED) — structure from
     `DESIGN_LANGUAGE/03-themes/themes-and-colors.md`.
   - Typography, shapes, motion springs.
   - The `AnikutaPalette` data class + theme selector.
   - Dynamic cover-color theming (from the old project's `DynamicTheming.kt`).
2. **Components** (per `DESIGN_LANGUAGE/02-components/components.md`):
   - §1: Bottom-up menu (no drag handle, partial height).
   - §2: 3-way toggle (`StyledSegmentedRow`).
   - §3: 2-way toggle.
   - §4: Custom numeric keyboard.
   - §5: Floating bottom nav (3-7 tabs, rearrange, fixed "More").
   - §6: Episode row (watched = grayscale + blur).
   - §7: Blurred cover header.
   - §8: Live preview panel.
   - §9: Section header (accent left-aligned).
3. **Edge-to-edge** setup in `:app` (`enableEdgeToEdge()`).
4. **Theme selection** — a basic settings screen to pick theme/mode.

**Exit criteria:**
- [ ] All 9 components exist and match the design language spec.
- [ ] Theme applies consistently to a test screen.
- [ ] Edge-to-edge works (no status bar padding on top bars).
- [ ] Floating bottom nav renders with dummy tabs.

**Deliverables:** The design system is ready to build screens against.

---

## Phase 4 — First source & browse

**Goal:** The app can load an Aniyomi-compatible extension and browse its catalog.

**Scope:**
1. **`:core:source-api`** — port the Aniyomi source-api contract (KMP,
   `commonMain` + `androidMain`). Anime-side: `AnimeSource`, `AnimeHttpSource`,
   `SEpisode`, `Video`, etc. (ADR-029).
2. **`:core:source-local`** — the local-files-as-source (for testing without
   installing extensions).
3. **`:data:extension`** — the extension loader (`ChildFirstPathClassLoader`),
   `AnimeExtensionManager`, the 4 installer backends (Legacy/Private/
   PackageInstaller/Shizuku).
4. **`:feature:browse`** — the Browse screen (source list, source catalog,
   search, global search).
5. **`:feature:extensions-settings`** — the extensions management screen
   (3-category: trusted → installed → available, anime/manga toggle per ADR-016).
6. **Paging** — `AnimeSourcePagingSource` for paged catalog browsing.

**Exit criteria:**
- [ ] An installed Aniyomi anime extension loads and its catalog browses.
- [ ] The extensions settings screen shows the 3-category layout.
- [ ] Search within a source works.
- [ ] Local source works (reading local video files).

**Deliverables:** A usable browse + extensions experience.

---

## Phase 5 — Anime details + episode list + video resolver

**Goal:** The anime details screen, episode list, and video resolver are working.

**Scope:**
1. **`:feature:anime-details`** — the details screen:
   - Blurred cover header + gradient (component §7).
   - Dynamic cover-color theming.
   - Metadata via the MetadataResolver (AniList + extension, ADR-011).
   - Action buttons (favorite, track, share).
2. **`:feature:episode-list`** — the episode list component:
   - Watched = grayscale + blur (component §6).
   - Gestures: tap, long-press, swipe-right (toggle watched), swipe-left (download).
   - Used on both details screen AND watch page.
3. **`:feature:video-resolver`** — the video resolver (per owner: a complete
   extensible module):
   - 3-tier hierarchy: Server → Audio → Quality.
   - Loading/resolving state.
   - Results UI (per `DESIGN_LANGUAGE/04-screens/video-resolver.md`).
   - Extensible for new extension techniques (ADR-022).
4. **`:core:episode-metadata`** — the pluggable episode metadata module
   (per `PLANNING/01-feature-specs/episode-metadata-module.md`):
   - 4 sources (Anikage/AniList/Jikan/Kitsu) initially.
   - Pluggable source registry (TMDB addable later).
   - Caching (in-memory + disk).

**Exit criteria:**
- [ ] Tapping an anime opens the details screen with blurred cover + gradient.
- [ ] Episode list shows watched episodes as grayscale + blur.
- [ ] Tapping an episode opens the video resolver (not the player directly).
- [ ] Resolver shows server/audio/quality hierarchy.
- [ ] Episode metadata fetches and displays.

**Deliverables:** The core anime browsing → details → resolve flow.

---

## Phase 6 — Watch page + player

**Goal:** The YouTube-style watch page and fullscreen player work.

**Scope:**
1. **`:core:player`** — the MPV wrapper:
   - Single MPV instance (ADR-025).
   - `AniyomiMPVView` ported from the reference (with fixes for the old project's
     subtitle issues).
   - Subtitle track management (careful lifecycle).
   - PiP support.
   - Media session.
2. **`:feature:watch`** — the watch page (ADR-012):
   - Mini-player at top (embedded MPV via `AndroidView`).
   - Episode description below.
   - Episode list below that.
   - Maximize → fullscreen player (swap Compose overlay, keep MPV surface).
3. **`:feature:player`** — the fullscreen player:
   - Controls: timestamp TOP-LEFT, seek bar BOTTOM, fullscreen button RIGHT.
   - Top-right: ONLY subtitles + quality.
   - Subtitle bottom-up menu (no drag handle).
   - Subtitle settings bottom-up menu (partial height, custom keyboard).
   - Quality bottom-up menu (no drag handle).
4. **Torrent streaming** — Torrserver integration (if applicable).

**Exit criteria:**
- [ ] Selecting a video in the resolver opens the watch page with mini-player.
- [ ] Mini-player plays; episodes are listed below.
- [ ] Maximizing swaps to fullscreen without dropping playback.
- [ ] Subtitle/quality menus work (no drag handle, partial height).
- [ ] Subtitles display correctly (the old project's issue is fixed).

**Deliverables:** A working watch + player experience.

---

## Phase 7 — AniList integration

**Goal:** AniList feeds Home, MY, and metadata.

**Scope:**
1. **`:core:anilist`** — the AniList module (ADR-030):
   - Raw HTTP + kotlinx-serialization GraphQL client.
   - `AniListRepository` interface + impl.
   - Public API (trending, seasonal, basic metadata) — no auth required.
   - Authenticated API (user's list, personalized feed) — OAuth (ADR-013).
   - Rate limiting (90 req/min) + caching.
2. **`:feature:home`** — the Home screen (AniList trending/seasonal).
3. **`:feature:my`** — the MY screen (personalized dashboard, ADR-021):
   - User's watch status.
   - Release schedule for tracked anime.
   - Recommendations.
   - Customizable name (default "MY").
4. **MetadataResolver** — wire AniList as a co-primary source (ADR-011):
   - User preference: AniList-first or extension-first.
   - Automatic fallback.
5. **AniList as a tracker** (ADR-019) — sync progress to AniList if linked.

**Exit criteria:**
- [ ] Home screen shows trending/seasonal anime from AniList (no auth).
- [ ] MY screen shows personalized data (with auth).
- [ ] Metadata resolves from AniList with fallback to extension.
- [ ] AniList tracker sync works.

**Deliverables:** Full AniList integration.

---

## Phase 8 — Downloads + notifications + auto-download

**Goal:** Download manager + dual-mode notifications + auto-download work.

**Scope:**
1. **`:core:download`** — the download manager:
   - Queue, states, priorities.
   - Video download (direct or ffmpeg remux).
   - Storage layout (SAF).
   - Wi-Fi-only default.
2. **`:core:notification`** — the notification scheduler (ADR-014):
   - Dual mode: AniList scheduled / extension verified.
   - Sub/dub preference.
   - Global + per-series override.
   - WorkManager scheduling.
   - 10/20-min backoff for extension mode.
3. **Auto-download** (ADR-020):
   - Triggered by the release-detection pipeline.
   - Global + per-series opt-in.
   - Preference matching (audio version, quality).

**Exit criteria:**
- [ ] Downloading an episode works.
- [ ] Notifications fire on new episodes (both modes).
- [ ] Auto-download triggers on matching new episodes.

**Deliverables:** Offline-ready + notification-aware app.

---

## Phase 9 — Trackers + backup + history + updates

**Goal:** Remaining Aniyomi-parity features.

**Scope:**
1. **`:data:tracker`** — MAL, Shikimori, Bangumi, Simcl (AniList already in Phase 7).
2. **`:core:backup` + `:feature:backup`** — gzipped protobuf backup/restore (ADR-028).
3. **`:feature:history`** — history screen (accent headers, continue watching).
4. **`:feature:updates`** — updates feed (new episodes for library).
5. **`:feature:library`** — the library screen (anime + manga tabs, categories).

**Exit criteria:**
- [ ] All 5+ trackers work.
- [ ] Backup creates + restores a `.anikuta` file.
- [ ] History shows continue-watching with accent headers.
- [ ] Updates feed shows new episodes.
- [ ] Library shows favorites with categories.

**Deliverables:** Feature parity with Aniyomi's anime features.

---

## Phase 10 — Settings + polish + release

**Goal:** Settings complete (with simple mode), UI polished, first release.

**Scope:**
1. **`:feature:settings`** — all settings screens (ADR-018):
   - Simple mode toggle (hides advanced).
   - Custom defaults.
   - Details settings (live preview).
   - Episode layout settings (3-way/2-way toggles).
   - Theme/color selection.
   - Bottom nav customization (3-7 tabs, rearrange).
2. **`:feature:more`** — the More tab (settings, downloads, stats, about).
3. **UI polish pass** — review every screen against `DESIGN_LANGUAGE/`.
4. **Release signing** — CI secrets for release APK.
5. **First tagged release** via GitHub Actions.

**Exit criteria:**
- [ ] All settings work; simple mode hides advanced.
- [ ] Every screen matches the design language.
- [ ] Release APK builds + signs via CI.
- [ ] First GitHub Release published.

**Deliverables:** A production-ready v1 release.

---

## Summary table

| Phase | Goal | Key deliverable |
|---|---|---|
| 1 | Scaffold Gradle project | Compiling empty shell |
| 2 | Core domain & data layer | Working DB + repositories |
| 3 | Design system & theme | All components ready |
| 4 | First source & browse | Browse + extensions |
| 5 | Details + episodes + resolver | Core anime flow |
| 6 | Watch page + player | Full watch experience |
| 7 | AniList integration | Home + MY + metadata |
| 8 | Downloads + notifications | Offline + notification-aware |
| 9 | Trackers + backup + history | Feature parity |
| 10 | Settings + polish + release | v1 release |

---

## How I'll work (per the owner's preferences)

- **Planning + review by me** — I design each phase, write the specs, review results.
- **Subagents used sparingly** — only for parallel implementation work (e.g.,
  "implement these 5 components"), with detailed prompts + full context.
- **Every task ends with:** commit → push → CI green → ntfy notification →
  session handoff note.
- **No blind guesses** — if anything is unclear, I ask first (Rule §1).

---

## The "another agent" proposition

The owner mentioned possibly continuing with a different agent (where I guide it
via prompts and review its results). This works because:

1. **`AGENT_CONTEXT/`** is the onboarding checkpoint — a new agent reads
   `START_HERE.md` + `PROJECT_STARTUP.md` and has the full context.
2. **Every phase is documented** in this file with clear scope + exit criteria.
3. **The rules + design language + architecture** are all in the repo, not in
   chat history.

So either approach works:
- **(a) I continue** — I implement each phase, using subagents sparingly.
- **(b) A new agent continues** — I write detailed phase prompts; the new agent
  implements; I review against the specs.

The owner decides. Both are viable because the docs are comprehensive.
