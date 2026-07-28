# ENGINEERING/ — Primary Engineer's Working Docs

> This folder holds the **primary engineer's** consolidated, verified documentation
> for the ANI-KUTA project. It is the single source of truth produced by the
> full-codebase discovery (2026-07-16) so we never have to re-analyze the whole
> project from scratch.
>
> These docs are **distinct** from the project's pre-existing `DOCS/` (planning,
> reference analysis, design specs) and `MEMORY/` (session memory, rules).
> Everything here was verified against the actual source on `main` at commit
> `ca644ad` (2026-07-16).

---

## Folder contents

| File | Purpose | Read this when… |
|------|---------|-----------------|
| `AI-AGENT-ONBOARDING.md` ⭐ | **The entry point.** The 5-minute reading order + mental model + rules summary + "where is X" index + common pitfalls. | **You are new. Read this FIRST.** |
| `TECHNICAL-OVERVIEW.md` | The comprehensive, verified map of the whole project: identity, stack, 5-module architecture, every folder explained, build process, CI/CD, data flows. | You need to understand how anything works or where anything lives. |
| `MODULARIZATION-ASSESSMENT.md` | Prioritized list (P0–P3) of structural problems: god-objects, mixed concerns, naming collisions, missing feature modules, doc staleness, security issues. | You are about to refactor, or you want to know what's fragile and why. |
| `WORKING-RULES.md` | The deployment/working rules the primary engineer must follow on every change (understand-before-change, minimize changes, CI safety, communication, etc.). | Before making ANY change to the repo. |
| `TESTING.md` | The testing strategy: manual (on-device) + thin automated unit tests for pure-logic pieces. How to run tests, how to add new ones, the manual testing checklist. | You're adding/verifying a test, or planning a change that needs verification. |

Also see `DOCS/CURRENT-STATE.md` (one level up) — the single-source-of-truth
status snapshot, updated after every merge to `main`.

---

## How this fits with the existing docs

- **`MEMORY/CORE-RULES.md`** — the project's original working rules (4 core rules + communication + architecture + backup strategy). Still authoritative for project philosophy. `WORKING-RULES.md` here *supplements* it with the additional deployment rules and does not contradict it. (Note: CORE-RULES §0 "Project Snapshot" now points to `DOCS/CURRENT-STATE.md` for live status.)
- **`MEMORY/PROJECT-CONTEXT.md`** — high-level context. Updated 2026-07-16; now points to `DOCS/CURRENT-STATE.md` for live status.
- **`MEMORY/SESSION-START-GUIDE.md`** — new-session checklist. Updated to point to `AI-AGENT-ONBOARDING.md` first.
- **`DOCS/ARCHITECTURE.md`** — the project's existing architecture doc (Session 31). Accurate at the package level but stale in some counts (settings files, nav routes, singletons) and misses several folders/files. `TECHNICAL-OVERVIEW.md` here is the **corrected + verified** version.
- **`DOCS/NAVIGATION-GUIDE.md`**, **`SETUP/README.md`**, **`BUILD-APK/README.md`**, **`DOCS/ARCHITECTURE/README.md`** — all refreshed 2026-07-16 (no longer say `app/` "does not exist yet").
- **`DOCS/PLAN/ROADMAP.md`** — fixed the duplicate Phase 9 numbering (the second "Phase 9" is now Phase 10, matching `README.md`).
- **`MEMORY/SESSION-LOGS/README.md`** — index extended through Session 31 + a note that Sessions 32+ live in `worklog.md`.

---

## Maintenance policy

- Update `TECHNICAL-OVERVIEW.md` whenever the module/folder structure or build/CI changes.
- Update `MODULARIZATION-ASSESSMENT.md` whenever a P0/P1 item is resolved or a new one is found.
- Update `DOCS/CURRENT-STATE.md` after every merge to `main` or any significant status change.
- `WORKING-RULES.md` changes only by explicit agreement with the user.
- `AI-AGENT-ONBOARDING.md` changes when the reading order, mental model, or common pitfalls shift.

---

_Last updated: 2026-07-16 (documentation cleanup complete). Verified against `main` @ `ca644ad`._
