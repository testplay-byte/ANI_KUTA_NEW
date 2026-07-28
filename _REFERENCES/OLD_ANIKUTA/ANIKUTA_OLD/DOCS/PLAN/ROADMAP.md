# Roadmap — Phased Build Plan

> The sequence in which we build ANI-KUTA. Each phase has a clear goal and
> deliverable. No phase starts until the previous is verified.
>
> Principle: **get a thin vertical slice working end-to-end first, then thicken it.**

---

## Phase 0 — Foundation ✅ DONE

**Goal:** Planning + decisions + project setup. No app code yet.

- [x] aniyomi copied into `REFERENCE/`.
- [x] Reference docs complete (modules, app structure, architecture, 8 subsystems, dual-model analysis).
- [x] 4 build decisions **decided** (D1 selective copy-paste, D2 anime-only, D3 keep Injekt, D4 keep SQLDelight).
- [x] `DOCS/APP/` folder set up.
- [x] `DOCS/PLAN/` folder set up (this doc + 8 planning docs).
- [x] Plan subpage on the web (visual planning dashboard).
- [x] Supabase project set up (creds stored gitignored).
- [x] Extension repo + AniKoto 180 extension recorded.
- [x] 4 designs documented (Material 3, Dark Neon, Neobrutalism, Coffee Notebook).
- [x] Module structure + package layout + build environment planned.
- [x] minSdk 26, 5-module structure, GitHub Actions workflow — all confirmed.

**Deliverable:** A documented, decided, planned foundation. ✅

---

## Phase 1 — Skeleton app + onboarding ✅ DONE

**Goal:** An Android app that builds (via GitHub Actions), launches, runs the
7-step onboarding wizard, and lands on an empty home screen. Backend wired.
Material 3 design only (fallback for the other 3 designs later).

**Build target:** ARM64-v8a debug APK, built on GitHub Actions. minSdk 26.

- [x] Create the Gradle project (5 modules: `:app`, `:core`, `:data`, `:domain`, `:source-api`).
- [x] Set up GitHub Actions workflow (`.github/workflows/build-apk.yml`).
- [x] Copy + adapt Injekt DI wiring (anime bindings only) → `:app` `app.anikuta.di`.
- [x] Copy + adapt anime SQLDelight DB → `:data` `app.anikuta.data.db`.
- [x] Copy + adapt `AnimeSourceManager` + `AnimeExtensionManager` → `:app`.
- [x] Material 3 theme scaffold → `:app` `app.anikuta.ui.theme.material3`.
- [x] 7-step onboarding wizard (Welcome, Permissions, Storage, Extension, Backup restore, Design, All set).
- [x] Navigation shell (bottom nav: Home, Library, History, Search, More).
- [x] App launches → onboarding (first boot) → empty home screen.
- [x] GitHub Actions builds a working ARM64-v8a debug APK.

**Deliverable:** An APK the user can install, run onboarding, and land on an
empty home. Backend (DI, DB, extension system) is live. No streaming yet. ✅

---

## Phase 2 — AniList layer + first UI ✅ DONE

**Goal:** The home page pulls data from AniList and displays it (one design).

- [x] AniList GraphQL client (queries: trending, popular, schedules, genres).
- [x] AniList data models + mappers.
- [x] Home page UI (Design 1 "Classic") — all 6 sections.
- [x] Navigation shell (bottom nav — even if other tabs are empty).
- [x] Tap a card → opens a placeholder detail page.

**Deliverable:** A home page that shows real AniList data. One design. No
streaming yet. ✅

---

## Phase 3 — Detail page + episode list ✅ DONE

**Goal:** The detail page shows AniList metadata + aniyomi extension episodes.

- [x] AniyomiSourceBridge (AniList anime → extension lookup).
- [x] Detail page UI (Design 1): hero, description, episode list.
- [x] Episode list populated from the matched extension.
- [x] Save/share buttons work (save → library, share → system sheet).

**Deliverable:** A detail page that merges AniList metadata + extension
episodes. Still no streaming. ✅

---

## Phase 4 — Player (streaming works end-to-end) ✅ DONE

**Goal:** The user can watch an episode.

- [x] Wire aniyomi's PlayerActivity + PlayerViewModel (MPV).
- [x] Episode tap → player opens → extension resolves stream → video plays.
- [x] Player controls (seek, play/pause, skip, tracks) work.
- [x] Progress saved to DB (WatchProgressStore).
- [x] Player reskinned for Design 1 (or wrapped as-is — TBD).
- [x] **User-verified on-device (Session 20):** play, seek, resume all work.

**Deliverable:** End-to-end streaming. Home → detail → play. The thin vertical
slice is complete. ✅

---

## Phase 5 — Library + History + Search ✅ DONE

**Goal:** The other core pages work.

- [x] Library page (saved anime, categories).
- [x] History page (watch progress, continue watching).
- [x] Search page (AniList search by name).
- [x] Settings page (basic — language, AniList login, player defaults).
- [x] Source wiring (extension → AniList match → episode list → video resolution).
- [x] Watch progress save/resume.

**Deliverable:** A usable anime app. Home → detail → play → save → resume. ✅

---

## Phase 6 — App Functionality + Polish ✅ DONE

**Goal:** Make the app fully functional and polished. Extension management,
AniList tracking, video downloads, settings reorganization, player UX.

- [x] Settings reorganization (6 subpages: General, Player, Extensions,
      Downloads, Tracking, About).
- [x] Player UX: preload before opening player, loading state, resume prompt.
- [x] Extension management: install/uninstall/update from within the app.
- [x] AniList tracking: OAuth login + progress sync + anime status.
- [x] Video downloads: WorkManager queue + HTTP Range partial resumption.
- [x] General polish: error states, empty states, skeletons, performance.
- [x] **Player pipeline fixes (Session 19):** extension URL crash, TLS, 403/headers, error overlay — all extensions now play, not just AniKoto.

**Deliverable:** A fully functional anime app — extensions manageable,
progress tracked, episodes downloadable, settings organized. ✅

---

## Phase 7 — Backend improvements ✅ DONE

**Goal:** Extension system overhaul, downloads redesign, episode/video caching, video picker redesign.

- [x] Extension trust system (max 2 trusted sources, popup with auto-trust)
- [x] Extension repo management (add/remove/refresh, SQLDelight-backed)
- [x] Extension details + settings pages (ConfigurableAnimeSource + PreferenceFragmentCompat)
- [x] Sources priority drag-and-drop (affects AniyomiSourceBridge tiebreaking)
- [x] Episode caching (in-memory + persistent) + background soft-refresh
- [x] 3-stage pull-to-refresh (episodes / details / everything)
- [x] Video picker: Server→Audio→Quality hierarchy, collapsible accordion, 10-min cache
- [x] Downloads: drag-and-drop priority lists (quality/audio/server) + fallback modes
- [x] Search bar + filter bottom sheet (language, sort)
- [x] Auto-refresh on package install/uninstall (BroadcastReceiver)
- [x] Direct install (ACTION_INSTALL_PACKAGE, no chooser)
- [x] Floating pill bottom nav (transparent background)
- [x] Onboarding: storage + install-unknown-apps permissions, extension selection with refresh
- [x] Synopsis HTML tag stripping
- [x] Sources priority drag-and-drop in AniyomiSourceBridge

**Deliverable:** Fully functional extension system, downloads, caching, and UI polish. ✅

---

## Phase 7.5 — Episode list enhancements ✅ DONE

**Goal:** Episode thumbnails, titles, summaries, auto-fetch from AniList.

- [x] Episode title parsing (strip "Episode X - " prefix from SEpisode.name) — `EpisodeTitleParser.kt`
- [x] Episode summaries (from AniList/Jikan if extension doesn't provide) — `EpisodeMetadataFetcher.kt`
- [x] Episode thumbnails (preview images) — rendered in `DetailScreen.kt` + `EpisodeListView.kt`
- [x] Auto-fetch from AniList (multi-source: AniList streaming + Jikan/MAL) — `EpisodeMetadataFetcher.kt`
- [x] Soft loading (show episodes first, enhance in background)
- [x] User settings (toggle titles, summaries, thumbnails, dates, audio pills, positions) — `MetadataSettingsScreen`, `PlayerEpisodeDisplayScreen`, `DisplaySettingsScreen`, `LayoutSettingsScreen`, `DetailsSettingsScreen` + live preview (`EpisodeRowPreview`)

**Deliverable:** Rich episode list with thumbnails, titles, and summaries. ✅
(Plan doc `DOCS/PLAN/EPISODE-LIST-ENHANCEMENTS-PLAN.md` is now historical — implementation is complete.)

---

## Phase 8 — Statistics & watch tracking (future)

**Goal:** Track user's watching habits and display beautiful statistics.

- [ ] Watch time tracking (total, per-day/week/month, heatmaps)
- [ ] Episode views (completed, started, dropped)
- [ ] Score distribution + mean score
- [ ] Genre tracking (most-watched, highest-rated)
- [ ] Collection stats

**Deliverable:** Statistics screen with heatmaps, charts, and genre tracking.
See `DOCS/PLAN/STATISTICS-PLAN.md` for full plan.

---

## Phase 9 — The 4 designs + theming (moved from Phase 6)

**Goal:** The customization system lands.

- [ ] Design abstraction (DesignSpec interface + DesignProvider).
- [ ] Design 2 "Dark Neon", Design 3 "Neobrutalism", Design 4 "Coffee Notebook".
- [ ] Theme system (light/dark/AMOLED + accent presets).
- [ ] Custom theming panel (limited overrides).
- [ ] Settings: design picker, theme picker, accent picker.
- [ ] Section show/hide on home page.
- [ ] Player gestures (swipe volume/brightness/seek) + player settings panels.
- [ ] PiP, media session, AniSkip, screenshots, chapters.

**Deliverable:** The user can pick from 4 designs + themes + accents. Player
is fully featured.

---

## Phase 10 — Polish + backup + trackers

**Goal:** Production-ready.

- [ ] Backup/restore (anime data).
- [ ] Performance pass (startup, scroll, player).
- [ ] Bug bash + fixes.

**Deliverable:** A polished, releasable anime app.

---

## After launch

- Monthly upstream tracking (see `UPSTREAM-TRACKING.md`).
- Manga addition (if desired) — parallel stack, see `REFERENCE-DOCS/ANALYSIS-DUAL-MODEL.md`.
- New features as prioritized.

---

## Open questions (roadmap-level)

- [ ] Is the phase order right? (e.g. should downloads come before the 4 designs?)
- [ ] Phase 9 (4 designs): build all 4 at once, or 1 per phase?
- [ ] Should we do a "design preview" subpage on the web to mock the 4 designs before building?

---

_Last updated: 2026-07-16. Phase 7 done (on `main`). Phase 7.5 (episode list enhancements) is next. (Previously "Session 27" — fixed the duplicate Phase 9 numbering: the second "Phase 9" is now Phase 10, matching `README.md`.)_
