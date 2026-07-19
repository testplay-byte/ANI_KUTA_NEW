# AI Agent Onboarding — Read This First

> **If you are a new AI agent (or human contributor) starting on ANI-KUTA,
> read this file FIRST.** It tells you exactly what to read, in what order,
> and what the rules are — so you have full context in ~15 minutes without
> re-analyzing the whole project.
>
> This is the entry point. Everything else flows from here.
>
> Last updated: 2026-07-16.

---

## What is ANI-KUTA? (30-second version)

ANI-KUTA is an **Android anime streaming app**. It is built **on the foundations
of [aniyomi](https://github.com/aniyomiorg/aniyomi)** (the anime fork of the
Mihon/Tachiyomi ecosystem) — it reuses aniyomi's extension system, SQLDelight DB
pattern, Injekt DI, and MPV player — but it is **NOT a copy of aniyomi**. It uses
**its own techniques** in most places (extension trust, source preferences, the
AniList + Supabase data layer, the entire Compose UI).

**Three-source architecture:**
1. **AniList** (GraphQL) — discovery, metadata, tracking, artwork.
2. **aniyomi extensions** (user-installed APKs) — streaming sources that resolve episodes to video URLs.
3. **MPV** (native lib) — the video player engine.

The app bridges AniList ↔ extensions via fuzzy title matching, plays through MPV,
and saves watch progress for resume.

- **App ID:** `app.anikuta` · **Version:** `0.1.0`
- **GitHub:** https://github.com/testplay-byte/anikuta (test account; may migrate later)
- **Distribution target:** GitHub releases (NOT Play Store). Temporary signing for now.

---

## The 5-minute reading order (do this now)

Read these in this exact order. Each is short and high-signal.

| # | Read | Why | Time |
|---|------|-----|------|
| 1 | `DOCS/CURRENT-STATE.md` | The single source of truth for where the project is right now. If anything conflicts with another doc, this wins. | 2 min |
| 2 | `DOCS/ENGINEERING/WORKING-RULES.md` | The binding rules for every change (understand-before-change, minimize changes, CI safety, communication). | 3 min |
| 3 | `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` | The comprehensive verified map: stack, 5 modules, every folder, build, CI, data flows. **Bookmark this.** | 8 min |
| 4 | `MEMORY/CORE-RULES.md` | The project's original 4 core rules + communication + architecture philosophy. (Note: §0 "Project Snapshot" is stale — use CURRENT-STATE.md instead.) | 3 min |

**That's the minimum.** After these 4, you have enough context to do most tasks.

### Read these when relevant

| When | Read |
|------|------|
| You're about to refactor, or want to know what's fragile | `DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md` (prioritized P0–P3 issues) |
| You need to know what's broken / known-bad | `KNOWN-ISSUES.md` (31 triaged issues) |
| You need recent history (what was the last thing worked on?) | The tail of `worklog.md` (the project's session-by-session log) |
| You're touching the player | `MEMORY/PLAYER-RULES.md` + `SUBTITLES_FIX.md` + the player section of `TECHNICAL-OVERVIEW.md` |
| You're touching the download system | `plan.md` (Download System Plan v2) + the download section of `TECHNICAL-OVERVIEW.md` |
| You're touching storage/SAF | `STORAGE.md` |
| You're touching the extension contract | `DOCS/APP/STRUCTURE/source-api.md` + the source-api section of `TECHNICAL-OVERVIEW.md` |
| You need to know WHY a decision was made | `MEMORY/DECISIONS/README.md` (46 ADR-style decisions) |
| You need to see aniyomi's original approach | `REFERENCE/` (READ-ONLY) + `DOCS/REFERENCE-DOCS/` |

---

## The 5 Gradle modules (mental model)

```
app → core, data, domain, source-api
data → core, domain, source-api
domain → core, source-api
source-api → core
core → (no internal deps)
```

| Module | What it is |
|--------|-----------|
| `:app` (147 kt / 38.5K LoC) | The Android app: UI, player, download, extensions, DI glue, + app-local remote data sources (AniList/Supabase/tracker) |
| `:core` (29 kt / 1.8K LoC) | Foundation: preference system, network (OkHttp + DoH), storage helpers, util. `api`-exposes okhttp/okio/rxjava/injekt |
| `:data` (25 kt / 2.1K LoC + 18 `.sq`) | SQLDelight local DB + 11 repository implementations |
| `:domain` (121 kt / 4.8K LoC) | Models + 64 interactors + 11 repo interfaces + 4 preference-service classes |
| `:source-api` (28 kt / 1.9K LoC) | **The extension binary contract** — aniyomi extensions compile against this. NEVER rename `eu.kanade.tachiyomi.animesource.*` |

**Important nuance:** `:app` has its own `data/` and `domain/` *packages*. These are **NOT duplicates** of the `:data`/`:domain` *modules*. The app packages hold **remote** sources (AniList/Supabase) + Anikuta-specific overrides. The naming collision is confusing (flagged as a P1 issue).

Full detail: `TECHNICAL-OVERVIEW.md` §4–§7.

---

## The rules (condensed — read WORKING-RULES.md for the full version)

1. **Understand before acting.** Never assume, never guess. Trace the execution flow before editing.
2. **Minimize changes.** Only touch files that need to change. No "while I'm here" refactors.
3. **Maintain consistency** with existing style, naming, architecture, error handling.
4. **Never modify code you do not understand.** Investigate further or ask the user.
5. **Think before coding:** analyze → list affected files → explain plan → side effects → alternatives → wait for confirmation if significant → implement → verify → document.
6. **CI safety:** changes to `app/**`, `core/**`, `data/**`, `domain/**`, `source-api/**`, `gradle/**`, root gradle files, or `.github/workflows/**` trigger the `build-apk.yml` workflow. Ensure `./gradlew assembleDebug` passes before pushing to `main`.
7. **`REFERENCE/` is READ-ONLY.** Never edit, never build from it.
8. **Never rename `eu.kanade.tachiyomi.animesource.*`** package names — breaks extension classloading.
9. **One issue at a time.** Don't hand issue-fixing to sub-agents.
10. **If anything is unclear, STOP.** Ask focused questions. Don't blindly guess.
11. **Communication:** ask questions in batches of 5. Keep wording simple. Be honest and direct. Don't sugarcoat.
12. **Document every change** in the appropriate `DOCS/` file. Keep `TECHNICAL-OVERVIEW.md` and `CURRENT-STATE.md` current.
13. **Do not begin implementing new features until explicitly asked.**

---

## The high-caution zones

These areas are fragile. Touch them only with full understanding + on-device verification:

1. **`PlayerActivity.kt`** (2,430 LoC god object) — all player logic in one Activity. 3 known CRITICAL race conditions (Session 44). MPV native lifecycle is fragile (init-once-per-process).
2. **MPV native lifecycle** — `MPVLib.initialize()` once per process; re-init crashes.
3. **Subtitle rendering** — broke silently multiple times. See `SUBTITLES_FIX.md`.
4. **SQLDelight schema** — **zero migration files exist**. Any schema change will wipe user data unless `.sqm` migrations are added first.
5. **The extension binary contract** (`:source-api` `eu.kanade.*` names) — renaming breaks every installed extension.
6. **The `live-preview-dashboard` branch** — temporary; do not base feature work on it.

---

## How we work (workflow)

- **Main agent** (you, if you're the primary engineer) owns `MEMORY/` and the overall plan.
- **Sub-agents** may be dispatched for research/exploration but must NOT touch `MEMORY/`, `MEMORY/CREDENTIALS/`, or the project's own `worklog.md` (at repo root).
- **Coordination worklog** for multi-agent discovery/analysis lives at the *sandbox* path `/home/z/my-project/worklog.md` (NOT in the repo). The project's own `worklog.md` is at the *repo* root and is a historical narrative — do not modify it.
- After finishing a task: update `DOCS/CURRENT-STATE.md` if status changed, update `TECHNICAL-OVERVIEW.md` if structure changed, update `MODULARIZATION-ASSESSMENT.md` if a structural issue was resolved or found.

---

## Quick "where is X?" index

| If you need to find… | Go to |
|----------------------|-------|
| A screen | `app/src/main/java/app/anikuta/ui/<feature>/` |
| A ViewModel | same folder as its screen, `<Feature>ViewModel.kt` |
| The player | `app/.../player/` (start at `PlayerActivity.kt`, but read `MEMORY/PLAYER-RULES.md` first) |
| DI wiring | `app/.../di/AppModule.kt` (56 singletons) + `DomainModule.kt` |
| The DB schema | `data/src/main/sqldelight/{dataanime,view}/*.sq` |
| A repository impl | `data/src/main/java/app/anikuta/data/<area>/` |
| A domain interactor | `domain/src/main/java/app/anikuta/domain/<area>/interactor/` |
| The extension contract | `source-api/src/main/java/eu/kanade/tachiyomi/animesource/` |
| Navigation routes | `app/.../navigation/AnikutaNavGraph.kt` (31 routes) |
| A cache/store | `app/.../data/cache/` (app-local) or `:data` `LocalCache` (SQLDelight) |
| The CI workflow | `.github/workflows/build-apk.yml` |
| Dependencies | `gradle/libs.versions.toml` |
| aniyomi reference code | `REFERENCE/` (read-only) |
| What changed recently | tail of `worklog.md` (repo root) |
| Past decisions | `MEMORY/DECISIONS/README.md` (46 ADR-style decisions) |
| Known issues | `KNOWN-ISSUES.md` |

---

## Common pitfalls (don't repeat these)

1. **Don't trust `worklog.md` for live branch state.** It's a historical narrative. Older entries reference a `player-experiment` branch that has since been merged into `main` and deleted. Verify branch state with `git branch -a`.
2. **Don't trust stale "current state" claims** in `MEMORY/CORE-RULES.md §0` or `MEMORY/PROJECT-CONTEXT.md`. Use `DOCS/CURRENT-STATE.md`.
3. **Don't edit `REFERENCE/`.** It's the pristine aniyomi snapshot.
4. **Don't rename `eu.kanade.*` packages** in `:source-api` or `:core` network — breaks extensions.
5. **Don't enable minify** without writing ProGuard keep rules first (none exist). `isMinifyEnabled=false` everywhere today.
6. **Don't change the SQLDelight schema** without adding a `.sqm` migration file — users will lose their data.
7. **Don't add `kapt`/`ksp`** — the project uses Injekt (reflection-based) and SQLDelight (Gradle plugin). No annotation processors.
8. **Don't hand issue-fixing to sub-agents** — the main agent does fixes (per `MEMORY/CORE-RULES.md` Rule 4).

---

_This file is the front door. Keep it accurate. If you notice something here is wrong, fix it and mention it in your summary._
