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
1. An **extension-based** content system (Aniyomi-compatible).
2. **AniList as a co-primary data source** (not just a tracker) — for discovery,
   metadata, and personalization.
3. A **custom design language** (M3-inspired but unique — primary color #B1F256
   lime green, RobotoFamily font).
4. **Full feature set**: browse, search, details, watch (MPV), library, history,
   updates (schedule + calendar), profile (stats + charts), trackers (AniList +
   MAL OAuth), backup/restore, downloads/offline playback, episode settings,
   extension-only details page.

It is **NOT** a fork of Aniyomi. The Aniyomi source is a read-only reference at
`ANIYOMI_REFRENCE/`. All new code goes in `ANIKUTA_PROJECT/ANIKUTA/`.

## Read order (mandatory)

1. **This file** (`AGENT_CONTEXT/START_HERE.md`).
2. [`../AGENT_CONTEXT/PROJECT_STARTUP.md`](../PROJECT_STARTUP.md) — startup steps.
3. [`../ARCHITECTURE.md`](../ARCHITECTURE.md) — the single source of truth.
4. [`../RULES/ai-agent-rules.md`](../RULES/ai-agent-rules.md) — the 14-section ruleset.
5. [`../RULES/project-conventions.md`](../RULES/project-conventions.md) — ANIKUTA-specific rules.
6. [`../DOCS/04-design-decisions.md`](../DOCS/04-design-decisions.md) — all ADRs.
7. [`../DESIGN_LANGUAGE/`](../DESIGN_LANGUAGE/) — the UI/UX spec (read principles + components first).
8. [`../DOCS/episode-settings-architecture.md`](../DOCS/episode-settings-architecture.md) — episode row + settings.

## Current phase

**Phase 8+ (Full feature set) is COMPLETE.** The app builds, ships debug APKs via
CI, and has ALL major features working. The codebase has **41 Gradle modules**
across `:core:*`, `:feature:*`, `:data:*`, and `:app`.

## What's done (all features working)

### Core app screens
- ✅ **Browse/Home** — trending/popular grid, daily cache, pull-to-refresh
- ✅ **Search** — dual-source (AniList + extensions), filters, recent searches, extension→AniList linking
- ✅ **Anime Details** — 3-stage load (AniList → source match → episodes + metadata), episode list with two-section design, source link persistence, episode DB persistence (offline), metadata DB persistence, manual search sheet with currently-linked display, unreleased anime detection
- ✅ **Watch/Player** — MPV player, YouTube-style, gestures, PiP, episode switching with thumbnails, quality/subtitle sheets, metadata-threaded description, episode list with metadata
- ✅ **Library** — grid/list, categories, badges (configurable position), settings sheet, search
- ✅ **Episode Settings** — 4 full-page screens (Hub → Display/Layout/Metadata) with sticky live previews, separate prefs for details vs watch

### New feature screens (by AI agents)
- ✅ **History** — day-grouped watch history list, clear-all, progress bars
- ✅ **Updates** — updates tab (pull-to-refresh, SUB/DUB badges) + schedule tab (list + calendar view)
- ✅ **My Profile** — 10-section stats page (genre/format/status/score/country charts, behind-status, recently watched, reset, customization), works with or without AniList linked
- ✅ **Trackers** — AniList (implicit OAuth) + MAL (PKCE OAuth), auto-sync, token refresh
- ✅ **Backup & Restore** — ANIKUTA format + Aniyomi format compatibility, auto-backup (WorkManager), SAF folder, granular data selection, 10 backup providers
- ✅ **Downloads** — DownloadManager interface, queue, offline playback, SAF folder, HLS support, auto-download, notification, PNG/corrupt rejection

### Infrastructure
- ✅ **Extension system** — Aniyomi-compatible, Injekt + Koin DI
- ✅ **Extension-only details page** — for anime not on AniList, same UI as normal details, "A" re-link button
- ✅ **Smart video resolver** — dual strategy (structured 3-tier hierarchy + raw flat list), hoster name propagation, audio/server detection, auto A/B/C naming
- ✅ **Local persistent caching** — AniList data (24h TTL), episode metadata, episode list (DB), home page (daily), source links
- ✅ **UpdateChecker** — reusable module, manual checking (future: WorkManager background)
- ✅ **StatsCalculator** — local + AniList stats, Compose Canvas charts
- ✅ **AniList rate limiter** — prevents API rate limit issues

## What's NOT done yet (future work)

- ❌ Manga reader (anime-first; manga comes later)
- ❌ Notifications (new episode notifications)
- ❌ 1DM-style multi-threaded download method (interface ready, impl pending)
- ❌ Release (Play Store / signed APK) build flavor
- ❌ Watch page control sheets (audio/speed/server/more — 4 TODO buttons)
- ❌ Dynamic cover-color theming (code exists but coverColor is null — Palette extraction needed)
- ❌ Dark/light theme toggle (hardcoded to dark)
- ❌ General settings page (theme, about, data management)

## Where things live (cheat sheet)

| You want to... | Go here |
|---|---|
| Understand the vision | `DOCS/04-design-decisions.md` (ADRs 009–030+) |
| Understand the design language | `DESIGN_LANGUAGE/` |
| **Write/edit app code** | `ANIKUTA_PROJECT/ANIKUTA/` (41-module Gradle project) |
| Build system / convention plugins | `ANIKUTA_PROJECT/ANIKUTA/buildSrc/src/main/kotlin/` |
| Browse/home page | `ANIKUTA_PROJECT/ANIKUTA/feature/browse/` |
| Search page | `ANIKUTA_PROJECT/ANIKUTA/feature/search/` |
| Anime details + episode list | `ANIKUTA_PROJECT/ANIKUTA/feature/anime-details/` |
| Extension-only details page | `ANIKUTA_PROJECT/ANIKUTA/feature/anime-details/.../ExtensionDetailScreen.kt` |
| Watch/player page | `ANIKUTA_PROJECT/ANIKUTA/feature/watch/` |
| Library page | `ANIKUTA_PROJECT/ANIKUTA/feature/library/` |
| History page | `ANIKUTA_PROJECT/ANIKUTA/feature/history/` |
| Updates + Schedule page | `ANIKUTA_PROJECT/ANIKUTA/feature/updates/` |
| Profile page | `ANIKUTA_PROJECT/ANIKUTA/feature/my/` |
| Trackers settings | `ANIKUTA_PROJECT/ANIKUTA/feature/trackers/` |
| Backup & Restore | `ANIKUTA_PROJECT/ANIKUTA/feature/backup/` + `core/backup/` |
| Downloads | `ANIKUTA_PROJECT/ANIKUTA/feature/download/` + `core/download/` |
| Episode settings | `ANIKUTA_PROJECT/ANIKUTA/feature/episode-settings/` |
| Video resolver (smart) | `ANIKUTA_PROJECT/ANIKUTA/feature/video-resolver/` |
| AniList API + cache | `ANIKUTA_PROJECT/ANIKUTA/core/anilist/` |
| Tracker (OAuth, sync) | `ANIKUTA_PROJECT/ANIKUTA/core/tracker/` |
| Update checker | `ANIKUTA_PROJECT/ANIKUTA/core/update-checker/` |
| Episode metadata | `ANIKUTA_PROJECT/ANIKUTA/core/episode-metadata/` |
| MPV player | `ANIKUTA_PROJECT/ANIKUTA/core/player/` |
| DB (SQLDelight) | `ANIKUTA_PROJECT/ANIKUTA/core/database/` |
| Preferences | `ANIKUTA_PROJECT/ANIKUTA/core/preferences/` |
| Design system (theme, components) | `ANIKUTA_PROJECT/ANIKUTA/core/designsystem/` |
| Extension manager + source matcher | `ANIKUTA_PROJECT/ANIKUTA/data/extension/` |
| Anime/episode repos | `ANIKUTA_PROJECT/ANIKUTA/data/anime/` |
| Navigation (state machine) | `ANIKUTA_PROJECT/ANIKUTA/app/.../MainActivity.kt` |
| Koin DI modules | `ANIKUTA_PROJECT/ANIKUTA/app/src/main/java/.../di/` |
| Backup/restore planning docs | `BACKUP-AND-RESTORE-AND-DOWNLOADING-PLANING/` |
| AI agent prompts (for backup/downloads) | `AI-AGENT-PROMPTS/` |

## Hard rules (don't violate)

1. **Read `ARCHITECTURE.md` first.** It's the single source of truth.
2. **Don't modify the references** (`ANIYOMI_REFRENCE/`, `OLD_ANIKUTA/`).
3. **Don't build APKs locally.** CI-only (ADR-003). Push to a feature branch, trigger `workflow_dispatch`.
4. **Send a ntfy.sh notification** on every task completion to `https://ntfy.sh/TASKISDONE` (ADR-008).
5. **No blind guesses.** If unsure, read the code first (Rule §1).
6. **Follow the design language.** See `DESIGN_LANGUAGE/` — don't improvise UI.
7. **All network calls on `Dispatchers.IO`.**
8. **No nested LazyColumn** inside another LazyColumn.
9. **All ModalBottomSheet: `dragHandle = null`.**
10. **Push to feature branches only.** Never push to `main` directly.

## What to do next (if you're resuming)

1. Read the worklog at `/home/z/my-project/worklog.md` (last 5 sections) for recent context.
2. Check `DOCS/05-roadmap.md` for what's planned next.
3. The Gradle project is at `ANIKUTA_PROJECT/ANIKUTA/`. To build: push to a feature branch, trigger CI `workflow_dispatch`:
   ```bash
   curl -X POST -H "Authorization: token <PAT>" -H "Accept: application/vnd.github+json" \
   "https://api.github.com/repos/testplay-byte/ANI_KUTA_NEW/actions/workflows/ci.yml/dispatches" \
   -d '{"ref":"<your-branch>"}'
   ```
4. Navigation: **hand-rolled state-machine** in `MainActivity.kt` (NOT Voyager/Compose Nav). State flags drive a `when` block. New screens: add state var + `when` branch + `BackHandler` case.
5. DI: **Koin** — modules in `app/.../di/`. Use `koinInject<>()` in composables, `get<>()` in constructors.
6. Design: **#B1F256 lime green** primary, **RobotoFamily** font, `surfaceVariant@0.4f` card backgrounds.

---

*This file is the agent onboarding checkpoint. Keep it updated as the project
evolves. A new agent should never need to read chat history — this file + the
read-order above is enough.*
