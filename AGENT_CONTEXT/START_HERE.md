# AGENT_CONTEXT/ — Start Here for New AI Agents

> **If you are a new AI agent picking up this project, you are in the right place.**
> This folder is your onboarding checkpoint. Read `START_HERE.md` (this file)
> first — it gives you the full context and tells you exactly what to read next.

## Why this folder exists

This folder is a **context backup for future AI agents**. A new agent can read
this single file and get: the project vision, current progress, where to find
everything, and what to do next — without scrolling through chat history or
guessing.

## The 60-second brief

**ANIKUTA** is an anime-first Android app (manga comes later) that combines:
1. An **extension-based** content system (like Aniyomi).
2. **AniList as a co-primary data source** (not just a tracker) — for discovery,
   metadata, and personalization.
3. A **custom design language** (M3-inspired but unique — see `DESIGN_LANGUAGE/`).
4. **Unique features**: watch page (YouTube-style), per-episode metadata,
   dual-mode episode notifications, auto-download, customizable screens/nav.

It is **NOT** a fork of Aniyomi. The Aniyomi source is a read-only reference at
`ANIYOMI_REFRENCE/`. All new code goes in `ANIKUTA_PROJECT/ANIKUTA/`.

## Read order (mandatory)

1. **This file** (`AGENT_CONTEXT/START_HERE.md`).
2. [`../ARCHITECTURE.md`](../ARCHITECTURE.md) — the single source of truth.
3. [`../RULES/ai-agent-rules.md`](../RULES/ai-agent-rules.md) — the 14-section ruleset.
4. [`../RULES/project-conventions.md`](../RULES/project-conventions.md) — ANIKUTA-specific rules.
5. [`../DOCS/04-design-decisions.md`](../DOCS/04-design-decisions.md) — all decisions (ADRs 001–022).
6. [`../DOCS/05-roadmap.md`](../DOCS/05-roadmap.md) — what phase we're in.
7. [`../DESIGN_LANGUAGE/`](../DESIGN_LANGUAGE/) — the UI/UX spec.
8. The newest session note in [`../RULES/sessions/`](../RULES/sessions/) — what the last agent did.

## Current phase

**Phase 7+ (Implementation) is IN PROGRESS.** The app builds, ships debug APKs via
CI, and has working: browse, search, anime details, watch (MPV), library,
extensions, and the episode-settings subsystem. See
[`../PLANNING/PHASED_PLAN.md`](../PLANNING/PHASED_PLAN.md) for the full plan.

See [`../DOCS/05-roadmap.md`](../DOCS/05-roadmap.md) for the current phase's exit
criteria.

## What's been done so far

- ✅ Repo structured: `ANIYOMI_REFRENCE/` (reference + 68-doc analysis),
  `OLD_ANIKUTA/` (prior attempt + screen analysis), `ANIKUTA_PROJECT/` (live code).
- ✅ Rules established (`RULES/ai-agent-rules.md` — 14 sections).
- ✅ Aniyomi reference fully documented (`ANIYOMI_REFRENCE/DOCUMENTATION/`).
- ✅ Vision clarified → 30 ADRs in `DOCS/04`.
- ✅ Design language docs complete (`DESIGN_LANGUAGE/` — 12 principles, 9 components,
  themes, 10 per-screen specs).
- ✅ Old ANIKUTA key screens analyzed (`OLD_ANIKUTA/ANALYSIS/` — 4 files).
- ✅ `ARCHITECTURE.md` finalized — the single source of truth.
- ✅ Gradle project scaffolded under `ANIKUTA_PROJECT/ANIKUTA/` — multi-module
  (core/*/feature/*/data/*/app), Compose-first, Koin DI, convention plugins in
  `buildSrc/` (`anikuta.library` + `anikuta.library.compose`).
- ✅ Browse + AniList API + extension system (Aniyomi-compat via Injekt).
- ✅ Search (dual-source AniList + extensions, manual link flow).
- ✅ Anime details (3-stage load: AniList → source match → episodes + metadata).
- ✅ Watch screen + MPV player (YouTube-style, gestures, PiP, episode switching).
- ✅ Library (grid/list, categories, selection mode).
- ✅ Episode metadata enrichment (Jikan/MAL + Anikage.cc + AniList Streaming;
  `:core:episode-metadata` module; per-field fetch toggles).
- ✅ **Episode settings subsystem** (`:feature:episode-settings` module — see
  [`../DOCS/episode-settings-architecture.md`](../DOCS/episode-settings-architecture.md)):
  - 4 full-page screens (Hub → Display / Layout / Metadata) with sticky live previews.
  - Episode row rebuilt to match OLD ANIKUTA design (black 70% pill badge,
    outlineVariant date/audio pills, plain-text title).
  - `EpisodeDisplayPreferences` is now correctly wired to `EpisodeRow` via
    `koinInject` + reactive `Preference.changes` (previously disconnected — a
    critical bug where settings only affected the preview, not the list).

## What's NOT done yet

- ❌ Trackers (AniList/MAL tracking sync beyond display).
- ❌ Manga reader (anime-first; manga comes later).
- ❌ Downloads / offline playback.
- ❌ Notifications (dual-mode episode notifications).
- ❌ Backups.
- ❌ Release (Play Store / signed APK) build flavor.

## Where things live (cheat sheet)

| You want to... | Go here |
|---|---|
| Understand the vision | `DOCS/04-design-decisions.md` (ADRs 009–030) |
| Understand the design language | `DESIGN_LANGUAGE/` |
| Read the Aniyomi reference analysis | `ANIYOMI_REFRENCE/DOCUMENTATION/` |
| Read the old ANIKUTA screen analysis | `OLD_ANIKUTA/ANALYSIS/` |
| Find planning specs | `PLANNING/` |
| Find the rules | `RULES/` |
| **Write/edit app code** | `ANIKUTA_PROJECT/ANIKUTA/` (live multi-module project) |
| Episode settings screens | `ANIKUTA_PROJECT/ANIKUTA/feature/episode-settings/` |
| Episode row + display prefs | `ANIKUTA_PROJECT/ANIKUTA/feature/anime-details/.../EpisodesSection.kt` + `EpisodeDisplayPreferences.kt` |
| Episode metadata (sources/repo/prefs) | `ANIKUTA_PROJECT/ANIKUTA/core/episode-metadata/` |
| Convention plugins (build) | `ANIKUTA_PROJECT/ANIKUTA/buildSrc/src/main/kotlin/` |
| Leave a note for the next agent | `RULES/sessions/` |

## Hard rules (don't violate)

1. **Read `ARCHITECTURE.md` first.** It's the single source of truth.
2. **Don't modify the references** (`ANIYOMI_REFRENCE/`, `OLD_ANIKUTA/`).
3. **Don't build APKs locally.** CI-only (ADR-003).
4. **Send a ntfy.sh notification** on every task completion (ADR-008).
5. **No blind guesses.** If unsure, ask — show reasoning first (Rule §1).
6. **Follow the design language.** See `DESIGN_LANGUAGE/` — don't improvise UI.

## What to do next (if you're resuming)

1. Read the newest session note in `RULES/sessions/` (or the worklog at
   `/home/z/my-project/worklog.md` if you're in the build sandbox).
2. Check `DOCS/05-roadmap.md` for the current phase's exit criteria.
3. The Gradle project is live at `ANIKUTA_PROJECT/ANIKUTA/`. To build, push to
   a feature branch and trigger the `CI` workflow via `workflow_dispatch`
   (feature branches do NOT auto-build on push — only `main` does). Do NOT
   build APKs locally (ADR-003, CI-only).
4. The episode settings subsystem lives in `:feature:episode-settings`. Its
   architecture is documented in `DOCS/episode-settings-architecture.md`.
5. The app uses a **hand-rolled state-machine for navigation** in `MainActivity.kt`
   (NOT Voyager, NOT Compose Nav) — state flags like `detailAnimeId`, `showSettings`,
   `episodeSettingsPage` drive a `when` block. Follow this pattern for new screens.

---

*This file is the agent onboarding checkpoint. Keep it updated as the project
evolves. A new agent should never need to read chat history — this file + the
read-order above is enough.*
