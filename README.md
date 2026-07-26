# ANI_KUTA_NEW

> A ground-up reimagining of [Aniyomi](https://github.com/aniyomiorg/aniyomi) — an anime-first
> (manga deferred) Android app built with Jetpack Compose, Koin DI, SQLDelight, and a custom
> design language (primary color #B1F256 lime green, RobotoFamily font).
> We reuse Aniyomi's core ideas and extension compatibility, but with a complete redesign,
> restructure, and rework tailored to our own preferences.

---

## What is this repository?

This is the **monorepo** that holds **everything** related to the ANIKUTA project:

1. **Our actual app**, `ANIKUTA` (`ANIKUTA_PROJECT/ANIKUTA/`), being built from scratch using
   Aniyomi as a conceptual foundation — **not** as a fork.
2. A **read-only backup of the original Aniyomi source + a 68-doc analysis of it**
   (`_REFERENCES/ANIYOMI_REFRENCE/`) — for study, comparison, and porting.
3. A **read-only backup of our previous ANIKUTA attempt** (`_REFERENCES/OLD_ANIKUTA/`) — the
   earlier project that reached a working state but had structural issues. We mine it for prior
   research and lessons learned, but rebuild fresh.
4. **Documentation, design decisions (ADRs), AI-agent rules, and session handoff notes** so the
   work can be continued by any new contributor (human or AI agent) without losing context.

> ⚠️ **This is NOT a fork of Aniyomi.** The Aniyomi source lives under `_REFERENCES/` purely as a
> backup reference. All new work happens under `ANIKUTA_PROJECT/`.

---

## Current status

**The app is feature-complete (Phase 8+).** It builds, ships debug APKs via CI, and has all major
features working. The codebase has **41 Gradle modules** across `:core:*`, `:feature:*`, `:data:*`,
and `:app`.

### What's done (all features working)

**Core app screens:**
- ✅ **Browse/Home** — trending/popular grid, daily cache, pull-to-refresh
- ✅ **Search** — dual-source (AniList + extensions), filters, recent searches, extension→AniList linking
- ✅ **Anime Details** — 3-stage load (AniList → source match → episodes + metadata), two-section
  episode row, source link persistence, episode DB persistence (offline), extension-only details page
- ✅ **Watch/Player** — MPV player, YouTube-style, gestures, PiP, episode switching with thumbnails,
  quality/subtitle sheets
- ✅ **Library** — grid/list, categories, badges, settings sheet, search, continue-watching
- ✅ **Episode Settings** — 4 full-page screens (Hub → Display/Layout/Metadata) with sticky live previews

**Feature screens (built by AI agents):**
- ✅ **History** — day-grouped watch history list, clear-all, progress bars
- ✅ **Updates** — updates tab (pull-to-refresh, SUB/DUB badges) + schedule tab (list + calendar)
- ✅ **My Profile** — 10-section stats page (charts, behind-status, recently watched, reset, customization)
- ✅ **Trackers** — AniList (implicit OAuth) + MAL (PKCE OAuth), auto-sync, token refresh
- ✅ **Backup & Restore** — ANIKUTA format + Aniyomi restore compat, auto-backup (WorkManager), SAF folder
- ✅ **Downloads** — DownloadManager interface, advanced multi-threaded downloader with resume, queue,
  offline playback, HLS support, auto-download, notification, PNG/corrupt rejection

**Infrastructure:**
- ✅ Aniyomi-compatible extension system (Injekt + Koin DI)
- ✅ Smart video resolver (dual strategy, hoster name propagation, audio/server detection)
- ✅ Local persistent caching (AniList 24h TTL, episode metadata, episode list DB, source links)
- ✅ UpdateChecker, StatsCalculator, AniListRateLimiter, CrashHandler

### What's NOT done yet (future work)

- ❌ Manga reader (anime-first; manga deferred per ADR-009)
- ❌ Episode-release notifications (ADR-014 — `:core:notification` is a stub)
- ❌ General settings page (theme, about, data management — `:feature:settings` is a stub)
- ❌ Dark/light theme toggle (currently hardcoded to dark)
- ❌ Watch page control sheets (audio/speed/server/more — 4 TODO buttons)
- ❌ Dynamic cover-color theming (Palette extraction)
- ❌ WorkManager background update-checking
- ❌ Release (Play Store / signed APK) build flavor

See [`DOCS/05-roadmap.md`](DOCS/05-roadmap.md) for the full plan.

---

## Repository layout

```
ANI_KUTA_NEW/                       ← repository root (kept clean & navigable)
├── README.md                       ← you are here (start here)
├── ARCHITECTURE.md                 ← ⭐ SINGLE SOURCE OF TRUTH (read FIRST per RULES/ai-agent-rules.md §2)
├── AGENTS.md                       ← orientation guide for AI agents (READ THIS if you are an agent)
├── LICENSE                         ← Apache 2.0 (inherited from Aniyomi reference; see DOCS/04)
├── .gitignore
├── .github/
│   ├── CODEOWNERS
│   └── workflows/                  ← CI / build pipelines (GitHub Actions ONLY — no local APK builds)
├── ANIKUTA_PROJECT/                ← OUR project (all active work lives here)
│   └── ANIKUTA/                    ← the actual app codebase (41 Gradle modules)
├── _REFERENCES/                    ← ⛔ OFF-LIMITS BACKUP SNAPSHOTS (see _REFERENCES/README.md)
│   ├── README.md                   ← read this before entering
│   ├── ANIYOMI_REFRENCE/           ← frozen Aniyomi source + 68-doc analysis
│   └── OLD_ANIKUTA/                ← frozen previous ANIKUTA attempt + screen analysis
├── PROTOTYPE_REFERENCE/            ← prototype app used as UI reference (search/library)
├── DOCS/                           ← architecture, design decisions (ADRs 001–036), roadmap, build/CI
├── PLANNING/                       ← detailed specs (feature specs, screen specs, phased plans)
├── DESIGN_LANGUAGE/                ← the ANIKUTA design language spec (principles, components, themes, per-screen)
├── AGENT_CONTEXT/                  ← ⭐ onboarding checkpoint for new AI agents (read START_HERE.md first)
├── AI-AGENT-PROMPTS/               ← prompts used to drive past AI agent tasks (backup, downloads)
├── PROMPTS/                        ← library + search page implementation prompts
├── 1DM-DOWNLOAD-ANALYSIS/          ← download method analysis (1DM-style multi-threaded vs current OkHttp)
├── BACKUP-AND-RESTORE-AND-DOWNLOADING-PLANING/  ← backup/restore + download folder-structure planning
└── RULES/                          ← AI-agent operating rules & session handoff notes
    ├── ai-agent-rules.md           ← the comprehensive general ruleset (14 sections)
    ├── project-conventions.md      ← ANIKUTA-specific rules (reference boundary, CI-only, etc.)
    ├── notifications.md            ← ntfy.sh task-completion notification rule
    ├── session-handoff-template.md
    └── sessions/                   ← session handoff notes (newest = most recent)
```

---

## Tech stack (decided — see `DOCS/04-design-decisions.md` for ADRs)

| Layer | Technology | ADR |
|---|---|---|
| Language | Kotlin | — |
| UI | Jetpack Compose (AndroidView for MPV only) | ADR-025 |
| Navigation | Hand-rolled state machine in `MainActivity.kt` (Voyager migration planned) | — |
| DI | **Koin** (host) + **Injekt** (for extension compat) | ADR-023 |
| Persistence | **SQLDelight** (with status-tracking columns) | ADR-024 |
| Networking | OkHttp + kotlinx-serialization | ADR-030 |
| AniList client | Raw HTTP + kotlinx-serialization (swappable) | ADR-030 |
| Image loading | Coil 3 | — |
| Player | MPV (aniyomi-mpv-lib), single instance, Compose overlay swap | ADR-025 |
| Backup | Gzipped JSON in a ZIP (own schema) + Aniyomi restore compat | ADR-028/036 |
| Extensions | Aniyomi-compatible source-api (kept exactly) | ADR-029 |
| Min SDK / Target SDK | 26 / 36 | ADR-026 |
| Application ID | `app.confused.anikuta` | ADR-031 |
| Build | GitHub Actions ONLY (no local APK builds) | ADR-003 |
| ABI | arm64-v8a only (testing phase) | ADR-032 |

---

## Build & release policy

- **APKs are built exclusively via GitHub Actions.** No contributor (human or AI agent) is expected
  to produce an APK in their local environment (ADR-003).
- The CI workflow at `.github/workflows/ci.yml` compiles the debug APK + runs unit tests on every
  push to `main` and every PR. Feature branches can be built via `workflow_dispatch`.
- See [`DOCS/06-build-and-ci.md`](DOCS/06-build-and-ci.md) for details.

---

## For AI agents picking up this work

1. **Read [`AGENT_CONTEXT/START_HERE.md`](AGENT_CONTEXT/START_HERE.md) FIRST** — the 60-second brief.
2. **Read [`ARCHITECTURE.md`](ARCHITECTURE.md)** — the single source of truth (per [`RULES/ai-agent-rules.md`](RULES/ai-agent-rules.md) §2).
3. **Read [`AGENTS.md`](AGENTS.md)** — orientation guide.
4. **Read [`RULES/`](RULES/)** — the rules (general + project-specific).
5. **Read [`DOCS/`](DOCS/)** — the project planning docs + ADRs.
6. **Read the newest session note in [`RULES/sessions/`](RULES/sessions/)** — what the last agent was doing.
7. **Stay out of [`_REFERENCES/`](_REFERENCES/)** unless you have a specific cross-reference need (see its README).

---

## License

Apache License 2.0 — inherited from the Aniyomi reference (which is Apache-2.0 licensed). Derivative
works must preserve attribution. See [`LICENSE`](LICENSE) and the licensing note in
[`DOCS/04-design-decisions.md`](DOCS/04-design-decisions.md). This choice is a sensible default for an
Apache-2.0 derivative and can be revisited.
