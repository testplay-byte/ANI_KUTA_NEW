# 05 — Roadmap

> Phased plan. Each phase has a clear definition of done.

## Phase 0 — Repository foundation ✅ DONE

**Goal:** a clean, navigable, documented monorepo with the Aniyomi reference in
place and the rules of engagement defined.

**Done when:**
- ✅ Repo structured: `_REFERENCES/`, `ANIKUTA_PROJECT/`, `DOCS/`, `RULES/`, `.github/`.
- ✅ Aniyomi reference source committed under `_REFERENCES/ANIYOMI_REFRENCE/ANIYOMI/`.
- ✅ Root `README.md` + `AGENTS.md` written.
- ✅ `DOCS/01..06` written.
- ✅ `RULES/` written.
- ✅ CI workflow exists and passes a repo-sanity job.
- ✅ Build policy (CI-only) documented and enforced by convention.

## Phase 0b — Design & decisions ✅ DONE

**Goal:** lock in the architecture and feature scope *before* writing app code.

**Done when:** `ARCHITECTURE.md` is finalized and every open decision in
`04-design-decisions.md` is checked off. All ADRs 009–030 recorded.

## Phase 1 — Scaffold the ANIKUTA Gradle project ✅ DONE

**Goal:** a compiling multi-module Gradle project under `ANIKUTA_PROJECT/ANIKUTA/`,
built by CI.

**Done when:** CI produces a green debug build of the app shell. 41 modules
scaffolded (`settings.gradle.kts`), convention plugins in `buildSrc/`, version
catalogs in place.

## Phase 2 — Core domain & data ✅ DONE

**Goal:** domain models, persistence layer, repository interfaces + first impls.

**Done when:** SQLDelight schema (animes, episodes, animehistory, animetrack,
anime_category, categories) + mappers + repos (`AnimeRepositoryImpl`,
`EpisodeRepositoryImpl`, `CategoryRepositoryImpl`, `HistoryRepositoryImpl`).

## Phase 3 — First source & browse ✅ DONE

**Goal:** a working source model + browse/home page.

**Done when:** Aniyomi-compatible `:core:source-api` + `:data:extension` (loader,
installer, matcher, stores) + `:feature:browse` (trending grid, daily cache,
pull-to-refresh).

## Phase 4 — Watch page & player ✅ DONE

**Goal:** YouTube-style watch page + MPV player.

**Done when:** `:core:player` (MPV wrapper, single instance, subtitle management,
controls, PiP) + `:feature:watch` (mini-player + episode list + maximize) +
`:feature:video-resolver` (smart dual-strategy resolver).

## Phase 5 — Library, search, episode settings ✅ DONE

**Goal:** library, dual-source search, and the episode-settings system.

**Done when:** `:feature:library` (grid/list, categories, badges, settings) +
`:feature:search` (AniList + extensions, filters, linking) +
`:feature:episode-settings` (4 full-page screens with live previews) +
`:feature:anime-details` (3-stage load, extension-only details, two-section row).

## Phase 6 — History, updates, profile ✅ DONE

**Goal:** the "personal data" screens.

**Done when:** `:feature:history` (day-grouped list) + `:feature:updates`
(updates + schedule + calendar) + `:feature:my` (10-section profile with charts) +
`:core:update-checker` + `:core:tracker` (AniList + MAL OAuth, StatsCalculator,
TrackSyncManager) + `:feature:trackers`.

## Phase 7 — Backup & downloads ✅ DONE

**Goal:** backup/restore + downloads/offline playback.

**Done when:** `:core:backup` (ANIKUTA format + Aniyomi restore compat, auto-backup,
10 providers — ADR-028/036) + `:feature:backup` + `:core:download`
(DownloadManager interface, DefaultDownloadManager + AdvancedHttpDownloader
multi-threaded with resume, HLS, queue, SAF storage, notifications) +
`:feature:download` + `:app`'s `DownloadOrchestrator` (ADR-035).

## Phase 8 — Polish & hardening ✅ DONE (mostly)

**Goal:** UX polish, crash handling, extension-compat fixes, download hardening.

**Done when:** CrashHandler + ErrorActivity, Aniyomi extension-compat fixes
(Injekt bootstrap, NetworkHelper, source-api fixes), smart video title parsing,
PNG/corrupt-download rejection, HLS support, drag-reorder downloads, Aniyomi
restore flow, schedule calendar, profile customization.

**Still outstanding from Phase 8 (carried to Phase 9):**
- ⚠️ Watch page control sheets (audio/speed/server/more — TODO buttons)
- ⚠️ Dynamic cover-color theming (Palette extraction)
- ⚠️ Dark/light theme toggle

---

## Phase 9 — Next up (planned, not started)

These are the remaining work items, prioritized roughly by impact. The owner
decides the order.

### Navigation migration (high priority)
- **Migrate from the hand-rolled state machine in `MainActivity.kt` to Voyager.**
  Voyager is already in the dependency catalog (unused). A clean Voyager-based
  navigation will replace the growing `when` block + state flags. Plan to be
  confirmed by the owner before implementation.

### Missing features
- [ ] **Episode-release notifications** (ADR-014) — implement `:core:notification`
  with WorkManager scheduling (AniList scheduled mode + extension-verified mode).
- [ ] **General settings page** — implement `:feature:settings` (theme, about,
  data management, simple mode per ADR-018).
- [ ] **Dark/light theme toggle** — currently hardcoded `AnikutaTheme(darkTheme = true)`.
- [ ] **Watch page control sheets** — audio/speed/server/more (4 TODO buttons).
- [ ] **Dynamic cover-color theming** — Palette extraction for `coverColor`.
- [ ] **WorkManager background update-checking** — UpdateChecker is manual-only.

### Infrastructure
- [ ] **CI expansion** — consider running `./gradlew build` or `:app:lint` to
  catch latent issues (e.g., the missing `:i18n` module folder).
- [ ] **Release build flavor** — signed APK pipeline via CI secrets (ADR-003).
- [ ] **Manga reader** — anime-first; manga deferred per ADR-009. Architecture
  is ready (`:data:manga` stub exists).

---

> This roadmap is a living document. Update it as phases complete and as the
> owner reprioritizes. Each phase gets its own breakdown in
> [`PLANNING/`](PLANNING/) when approached.
