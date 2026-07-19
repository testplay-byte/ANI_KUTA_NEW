# Navigation Guide — ANI-KUTA repo

This is the top-level map of the **anikuta** repository. It tells you what every
top-level folder is for, who edits it, and where to go next.

> **NEW? Read `DOCS/ENGINEERING/AI-AGENT-ONBOARDING.md` FIRST** — it's the
> proper entry point with the 5-minute reading order. This file is the
> folder-level reference map.

- **Repository:** https://github.com/testplay-byte/anikuta
- **App display name:** ANI-KUTA
- **Application ID:** `app.anikuta`
- **Origin:** Built on the foundations of **aniyomi** (https://github.com/aniyomiorg/aniyomi).
  ANI-KUTA reuses aniyomi's extension system, SQLDelight DB pattern, Injekt DI,
  and MPV player — but uses its own techniques in most places.
- **Current status:** See `DOCS/CURRENT-STATE.md`.

## Top-level folder map

| Folder | Purpose | Who edits it | Read this first |
|--------|---------|--------------|-----------------|
| `app/` `core/` `data/` `domain/` `source-api/` | **The 5 working Gradle modules** — the actual Android app source. Phases 0–7 complete. | All agents working on app code. | `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` §4–§7. |
| `MEMORY/` | Persistent project memory: core rules, context, session logs, decisions, credentials. The "brain" that survives sandbox resets. | Main agent only. Sub-agents must NOT touch `MEMORY/`. | `MEMORY/CORE-RULES.md` (rules), `DOCS/CURRENT-STATE.md` (status — §0 of CORE-RULES is stale). |
| `DOCS/` | All project documentation. | Any agent (per scope). | `DOCS/ENGINEERING/AI-AGENT-ONBOARDING.md` (entry point) → `DOCS/CURRENT-STATE.md` → `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md`. |
| `DOCS/ENGINEERING/` ⭐ | **Primary engineering docs** — verified technical map, modularization assessment, working rules, AI-agent onboarding. The single source of truth. | Main agent. | `AI-AGENT-ONBOARDING.md` first, then the others. |
| `SETUP/` | Environment setup guide (toolchain, signing). | Documentation agents. | `SETUP/README.md`. |
| `BUILD-APK/` | Where built APK outputs land (renamed). APKs themselves are gitignored; only the README is tracked. | Build/scripts. | `BUILD-APK/README.md`. |
| `REFERENCE/` | Pristine, READ-ONLY copy of the aniyomi upstream source (1,988 files, commit `2f5cf77`, 2025-11-05). **Never edited, never built from.** Only refreshed by replacing the whole copy. | Nobody edits it. The main agent replaces the whole copy when refreshing upstream. | `DOCS/REFERENCE-DOCS/NAVIGATION-GUIDE.md` (orientation) → then `MODULES.md` / `APP-STRUCTURE.md` / `ARCHITECTURE.md`. |
| `REFERENCE-STAGING/` | Landing zone for a fresh copy of aniyomi when reviewing upstream updates. Currently empty (`.gitkeep`). | Main agent during upstream review. | See "How to diff upstream updates" in `DOCS/REFERENCE-DOCS/NAVIGATION-GUIDE.md`. |
| `.github/workflows/` | CI — single workflow `build-apk.yml` (debug APK build). | Main agent (high caution). | `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` §12. |
| `gradle/` | Gradle wrapper + version catalog (`libs.versions.toml`). | Main agent. | `gradle/libs.versions.toml`. |
| `live-preview/` + `live-preview.html` | Static HTML progress dashboards (5 files). Not part of the app. | Documentation agents. | (no README — undocumented; low priority.) |
| `backup/` | Misc backups. | — | — |

## Top-level files

| File | Purpose |
|------|---------|
| `README.md` | Project overview + phase status + recovery instructions. |
| `KNOWN-ISSUES.md` | 31 triaged issues (3 CRITICAL / 4 HIGH / 8 MEDIUM / 16 LOW). |
| `DOCS/CURRENT-STATE.md` | ⭐ Single-source-of-truth status snapshot. Read this for "where are we". |
| `plan.md` | Download System Implementation Plan v2 (supersedes `DOCS/PLAN/DOWNLOAD-PLAN.md`). Status field is stale — implementation is done. |
| `PLAYER_REDO_PLAN.md` | MPV init rewrite plan (implemented). |
| `STORAGE.md` | SAF folder selection architecture. |
| `SUBTITLES_FIX.md` | Subtitle rendering root-cause + fix (SOLVED). |
| `worklog.md` | The project's historical session-by-session log (2,389 lines). **Historical narrative** — verify live state against git, not against this file. |
| `build.gradle.kts` / `settings.gradle.kts` / `gradle.properties` | Root Gradle config. |
| `gradlew` / `gradlew.bat` | Gradle wrapper. |

## How to navigate as a newcomer

1. **First time on the project?** Read `DOCS/ENGINEERING/AI-AGENT-ONBOARDING.md`
   — it gives the 5-minute reading order.
2. **Need to know current status?** Read `DOCS/CURRENT-STATE.md`.
3. **Need to know where files live?** You are in the right place — see the tables above.
   For per-module detail, see `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md`.
4. **Setting up a build environment?** Go to `SETUP/README.md`.
5. **Building an APK?** Go to `BUILD-APK/README.md`.
6. **Understanding architecture?** Go to `DOCS/ARCHITECTURE/README.md` (principles)
   and `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` (verified reality).
7. **Looking at aniyomi internals / planning reuse?** Go to `DOCS/REFERENCE-DOCS/`
   — start at `NAVIGATION-GUIDE.md`, then drill into `MODULES.md` /
   `APP-STRUCTURE.md` / `ARCHITECTURE.md`.
8. **Looking for past decisions?** Go to `MEMORY/DECISIONS/README.md` (46 ADR-style decisions).
9. **Looking for recent history?** Read the tail of `worklog.md` (repo root).
   Note: older entries may reference a `player-experiment` branch that has since
   been merged into `main` and deleted — verify branch state with `git branch -a`.

## Notes

- The Android app source (`app/`, `core/`, `data/`, `domain/`, `source-api/`)
  **exists and is fully built** (Phases 0–7 complete). Older docs claiming it
  "does not exist yet" are stale — ignore them.
- `REFERENCE/` holds the pristine aniyomi copy (commit `2f5cf77`, 1,988 files).
  Its documentation lives in `DOCS/REFERENCE-DOCS/`.
- `MEMORY/CREDENTIALS/` holds secrets (e.g. GitHub PAT). It is gitignored and
  must never be committed or touched by sub-agents.
