# ARCHITECTURE.md

> **This is the single source of truth for the ANIKUTA project.**
> Per [`RULES/ai-agent-rules.md`](RULES/ai-agent-rules.md) §2, every AI session
> MUST read this file before writing any code. If something isn't covered here,
> ask the owner — do not invent.

---

## 1. Project overview

**ANIKUTA** is an anime-first Android app (manga deferred, architecture-ready)
that combines:
1. An **extension-based** content system (Aniyomi-compatible — ADR-029).
2. **AniList as a co-primary data source** (not just a tracker — ADR-010).
3. A **custom design language** (M3-inspired — ADR-015, see [`DESIGN_LANGUAGE/`](DESIGN_LANGUAGE/)).
4. **Unique features**: watch page (YouTube-style), per-episode metadata,
   dual-mode notifications, auto-download, customizable screens/nav.

It is **NOT** a fork of Aniyomi. The Aniyomi source is a read-only reference at
[`ANIYOMI_REFRENCE/`](ANIYOMI_REFRENCE/). All new code goes in
[`ANIKUTA_PROJECT/ANIKUTA/`](ANIKUTA_PROJECT/ANIKUTA/).

See [`DOCS/04-design-decisions.md`](DOCS/04-design-decisions.md) for the full
ADR log (ADRs 001–030).

---

## 2. Tech stack (decided)

| Layer | Technology | ADR |
|---|---|---|
| Language | Kotlin | — |
| UI | Jetpack Compose (Compose-first; `AndroidView` for MPV only) | ADR-025 |
| Navigation | Voyager | ADR-012 (watch page) |
| DI | **Koin** | ADR-023 |
| Persistence | **SQLDelight** (with status-tracking columns) | ADR-024 |
| Networking | OkHttp + kotlinx-serialization | ADR-030 |
| AniList client | Raw HTTP + kotlinx-serialization (in `:core:anilist`, swappable) | ADR-030 |
| Localization | **Moko Resources** (English-only initially) | ADR-027 |
| Image loading | Coil 3 | — |
| Player | MPV (aniyomi-mpv-lib), single instance, Compose overlay swap | ADR-025 |
| Backup | Gzipped protobuf (own schema) | ADR-028 |
| Extensions | Aniyomi-compatible source-api (kept exactly) | ADR-029 |
| Min SDK | 26 (Android 8.0) | ADR-026 |
| Target SDK | 36 (Android 16) | ADR-026 |
| Build | GitHub Actions ONLY (no local APK builds) | ADR-003 |

---

## 3. Module structure

Follows `RULES/ai-agent-rules.md` §4 (modularity): `:feature:*`, `:core`, `:data`,
`:app`. Feature modules never import from each other — cross-feature goes through
`:core`.

```
:app                                    ← application shell (DI, nav, activities)
├── :core
│   ├── :core:common                    ← utilities, constants, base types
│   ├── :core:designsystem              ← theme, colors, typography, components (per DESIGN_LANGUAGE/)
│   ├── :core:network                   ← HTTP client, interceptors, rate limiting
│   ├── :core:database                  ← SQLDelight schema, queries
│   ├── :core:preferences               ← PreferenceStore, typed preferences
│   ├── :core:anilist                   ← AniList GraphQL client + repository (ADR-010, ADR-030)
│   ├── :core:episode-metadata          ← per-episode metadata (pluggable sources, ADR-022)
│   ├── :core:source-api                ← the source/extension contract (KMP, Aniyomi-compatible)
│   ├── :core:source-local              ← local-files-as-source
│   ├── :core:player                    ← MPV wrapper, controls, PiP (single instance)
│   ├── :core:download                  ← download manager, queue, storage
│   ├── :core:notification              ← notification channels, schedulers (ADR-014)
│   └── :core:backup                    ← backup/restore engine (gzipped protobuf)
├── :data
│   ├── :data:anime                     ← anime repository impls, anime DB schema
│   ├── :data:manga                     ← manga repository impls, manga DB schema (hidden, ADR-009)
│   ├── :data:extension                 ← extension loader, installer, repo management
│   ├── :data:tracker                   ← tracker impls (MAL, AniList, Shikimori, Bangumi, Simcl)
│   └── :data:history                   ← history repository impls
├── :feature
│   ├── :feature:home                   ← Home screen (AniList trending/seasonal)
│   ├── :feature:library                ← Library screen (anime + manga tabs)
│   ├── :feature:updates                ← Updates feed
│   ├── :feature:history                ← History screen
│   ├── :feature:browse                 ← Browse sources / extensions
│   ├── :feature:search                 ← Search page (dual-source: AniList + extension, ADR-010/029)
│   ├── :feature:my                     ← MY screen (personalized dashboard, ADR-021)
│   ├── :feature:more                   ← More tab (settings, downloads, stats, about)
│   ├── :feature:anime-details          ← Anime details screen (blurred cover, gradient)
│   ├── :feature:episode-list           ← Episode list component (watched = grayscale+blur)
│   ├── :feature:video-resolver         ← Video resolver (server/audio/quality, extensible)
│   ├── :feature:watch                  ← Watch page (YouTube-style, ADR-012)
│   ├── :feature:player                 ← Fullscreen player
│   ├── :feature:extensions-settings    ← Extensions management (3-category, ADR-016)
│   ├── :feature:settings               ← Settings (with simple mode, ADR-018)
│   ├── :feature:trackers               ← Tracker login/binding
│   └── :feature:backup                 ← Backup/restore UI
└── :i18n                               ← Moko Resources strings (English-only initially, ADR-027)
```

### Dependency rules (invariant)

```
:app  →  :feature:*  →  :core:*  (interfaces only)
                     →  :data:*   (impls, wired via Koin)
:feature:*  ↛  :feature:*  (NEVER — cross-feature goes through :core)
:data:*  →  :core:* (to implement interfaces)
```

- **`:core:source-api`** is KMP (`commonMain` + `androidMain`) — Aniyomi-compatible.
- **`:core:designsystem`** holds the theme + components from `DESIGN_LANGUAGE/`.
- **`:core:episode-metadata`** is the pluggable module — TMDB addable later (ADR-022).
- **`:core:anilist`** is swappable — raw HTTP now, Apollo later if needed (ADR-030).
- **`:core:player`** owns the single MPV instance — watch page + fullscreen share it.

### Each feature module contains

```
:feature:foo/
├── ui/              # Screens, composables
├── viewmodel/       # ViewModels (or Voyager ScreenModels)
├── data/            # Local data models (if any)
├── navigation/      # Navigation route definitions
└── di/              # Koin module for this feature
```

---

## 4. Data flow (invariant — per `RULES/ai-agent-rules.md` §3)

```
UI (Compose) → ViewModel/ScreenModel → Repository (interface in :core) → Data Source
                                       ↑ implemented by :data
```

- **UI** contains ONLY display logic + event forwarding. No business logic.
- **ViewModel** calls Repositories only. Never APIs/DB directly.
- **Repository interfaces** live in `:core`. Implementations in `:data`.
- **Feature modules never import from other feature modules.** Cross-feature = `:core`.

### The MetadataResolver (ADR-011)

A special `:core` component that resolves anime/episode metadata:
1. Tries the user's preferred source (AniList OR extension).
2. Falls back to the other if the preferred lacks data.
3. Skips if both lack it.
4. Tracks per-field provenance for debugging + re-resolution.

---

## 5. Database schema requirements (ADR-024)

The SQLDelight schema **must include status-tracking columns** on anime/episode
tables:

| Column | Purpose |
|---|---|
| `release_date` | When the anime/episode was released. |
| `last_refresh` | Last time the library entry was refreshed from the source. |
| `last_metadata_fetch` | Last time metadata was fetched (AniList/extension). |
| `next_episode_check` | When to next check for a new episode (for ADR-014 notifications). |

These enable the notification/auto-download features and let us debug stale data.
The dual manga/anime schema pattern (per ADR-009) follows the reference: separate
`sqldelight/` and `sqldelightanime/` source sets, independent version counters.

---

## 6. Player architecture (ADR-012, ADR-025)

- **Single MPV instance** — the watch page's mini-player and the fullscreen
  player share one MPV surface. Maximize = swap the Compose overlay, NOT recreate
  the surface.
- The MPV `AndroidView` lives in `:core:player`. The watch page
  (`:feature:watch`) and fullscreen player (`:feature:player`) both observe it.
- Subtitle track management is careful — the old project had subtitle issues
  from improper MPV state handling. We handle lifecycle + track selection
  explicitly. See `DESIGN_LANGUAGE/04-screens/player.md`.

---

## 7. Design language (ADR-015)

The UI follows the custom design language in [`DESIGN_LANGUAGE/`](DESIGN_LANGUAGE/).
Key principles:
- Edge-to-edge top bar (no status bar padding).
- No drag handle on bottom-up menus.
- Bottom-up menus are partial-height (not full-screen).
- Blurred cover + gradient darkening on detail screens.
- Watched episodes = grayscale + blur.
- Accent-colored left-aligned section headers.
- Live preview in appearance-affecting settings.
- Floating bottom nav (3-7 tabs, rearrange, fixed "More").
- Custom numeric keyboard for number entry.
- Custom M3-inspired (not stock Material 3 Expressive).

See [`DESIGN_LANGUAGE/01-principles/core-principles.md`](DESIGN_LANGUAGE/01-principles/core-principles.md)
for all 12 principles and [`DESIGN_LANGUAGE/04-screens/`](DESIGN_LANGUAGE/04-screens/)
for per-screen specs.

---

## 8. Extension system (ADR-029)

- **Aniyomi-compatible** — existing Aniyomi anime extensions work out of the box.
- The `:core:source-api` module is KMP and matches the Aniyomi contract.
- Extensions are external APKs loaded at runtime via `ChildFirstPathClassLoader`.
- Two categories: **Video** and **Image/Manga** (ADR-016).
- We can add our own capabilities via the source-api without breaking compat
  (ADR-022) — extensions declare what they support; the app renders UI for
  declared capabilities.

See [`ANIYOMI_REFRENCE/DOCUMENTATION/02-modules/source-api.md`](ANIYOMI_REFRENCE/DOCUMENTATION/02-modules/source-api.md)
for the contract and [`ANIYOMI_REFRENCE/DOCUMENTATION/03-subsystems/source-system.md`](ANIYOMI_REFRENCE/DOCUMENTATION/03-subsystems/source-system.md)
for how the loader works.

---

## 9. Build & CI (ADR-003)

- **APKs built via GitHub Actions ONLY.** No local APK builds.
- The CI workflow at `.github/workflows/` compiles, tests, and (later) releases.
- See [`DOCS/06-build-and-ci.md`](DOCS/06-build-and-ci.md) for the full policy.
- Signing keys (for release) stored as GitHub Actions secrets — never in the repo.

---

## 10. How to make changes

1. **Read this file** + the relevant `DESIGN_LANGUAGE/` + `PLANNING/` docs.
2. **Plan** — which modules/files affected? What data flows? Side effects?
3. **Ask** — if anything is unclear or undecided, ask the owner (Rule §1).
4. **Implement** — follow the architecture, module boundaries, design language.
5. **Verify** — CI compiles it. Tests pass.
6. **Document** — update this file if structural. Add an ADR if a decision.
7. **Hand off** — session note in `RULES/sessions/`. ntfy.sh notification.

---

## 11. Status

**Phase 0b (Design & Planning) is COMPLETE.** All decisions recorded (ADRs 001–030).
This file is finalized. The next step is **Phase 1: scaffold the Gradle project**
under `ANIKUTA_PROJECT/ANIKUTA/` — pending the owner's go-ahead.

See [`DOCS/05-roadmap.md`](DOCS/05-roadmap.md) for the full roadmap and
[`PLANNING/PHASED_PLAN.md`](PLANNING/PHASED_PLAN.md) for the detailed phase plan.

---

## 12. References

- [`DOCS/04-design-decisions.md`](DOCS/04-design-decisions.md) — ADRs 001–030.
- [`DESIGN_LANGUAGE/`](DESIGN_LANGUAGE/) — the UI/UX spec.
- [`PLANNING/`](PLANNING/) — detailed specs + phased plan.
- [`RULES/ai-agent-rules.md`](RULES/ai-agent-rules.md) — the 14-section ruleset.
- [`AGENT_CONTEXT/START_HERE.md`](AGENT_CONTEXT/START_HERE.md) — agent onboarding.
- [`ANIYOMI_REFRENCE/DOCUMENTATION/`](ANIYOMI_REFRENCE/DOCUMENTATION/) — Aniyomi reference analysis.
