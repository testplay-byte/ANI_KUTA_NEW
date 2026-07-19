# AGENTS.md — Orientation Guide for AI Agents

> If you are an AI agent (or a new human contributor) resuming work on ANIKUTA, **read this file first.**
> It is the single entry point for understanding where things are and how to operate safely.

---

## 1. The 30-second orientation

- **ANI_KUTA_NEW** is a monorepo for building **ANIKUTA**, a reimagined version of the Aniyomi manga/anime reader.
- **We did NOT fork Aniyomi.** Its source is stored read-only under `ANIYOMI_REFRENCE/` for reference only.
- **All new code goes under `ANIKUTA_PROJECT/ANIKUTA/`.**
- **Never build APKs locally.** Builds happen only through GitHub Actions (`.github/workflows/`).
- **Read `ARCHITECTURE.md` FIRST** — it's the single source of truth (per `RULES/ai-agent-rules.md` §2).

---

## 2. Read order when resuming work

1. [`ARCHITECTURE.md`](ARCHITECTURE.md) — **the single source of truth** (MUST READ FIRST per rules).
2. [`RULES/ai-agent-rules.md`](RULES/ai-agent-rules.md) — the general ruleset (14 sections).
3. [`RULES/project-conventions.md`](RULES/project-conventions.md) — ANIKUTA-specific rules.
4. [`RULES/notifications.md`](RULES/notifications.md) — the ntfy.sh notification format.
5. [`RULES/session-handoff-template.md`](RULES/session-handoff-template.md) — the format for leaving notes.
6. [`DOCS/04-design-decisions.md`](DOCS/04-design-decisions.md) — every decision made so far and why (avoids re-litigating).
7. [`DOCS/05-roadmap.md`](DOCS/05-roadmap.md) — what phase we are in and what comes next.
8. [`DOCS/03-reference-module-map.md`](DOCS/03-reference-module-map.md) — what each Aniyomi module does (use when porting ideas).
9. The most recent session handoff note in [`RULES/sessions/`](RULES/sessions/) — what the last agent was doing.

> The newest file in `RULES/sessions/` is the latest handoff. Read it before starting.

---

## 3. Where things live (cheat sheet)

| You want to... | Go here |
|---|---|
| Read the single source of truth | [`ARCHITECTURE.md`](ARCHITECTURE.md) |
| Read the general AI agent rules | [`RULES/ai-agent-rules.md`](RULES/ai-agent-rules.md) |
| Read ANIKUTA-specific rules (reference boundary, CI-only, etc.) | [`RULES/project-conventions.md`](RULES/project-conventions.md) |
| Read the original Aniyomi code for reference | `ANIYOMI_REFRENCE/ANIYOMI/` (**read-only**) |
| Read the Aniyomi analysis docs (68 docs) | `ANIYOMI_REFRENCE/DOCUMENTATION/` (**read-only**) |
| Read the OLD ANIKUTA project (prior attempt + its Aniyomi analysis) | `OLD_ANIKUTA/ANIKUTA_OLD/` (**read-only**) |
| Write new ANIKUTA app code | `ANIKUTA_PROJECT/ANIKUTA/` |
| Record an architecture/design decision | [`DOCS/04-design-decisions.md`](DOCS/04-design-decisions.md) |
| Leave a note for the next agent | [`RULES/sessions/`](RULES/sessions/) |
| Add/modify CI | `.github/workflows/` |
| Understand the target architecture (draft) | [`DOCS/02-target-architecture.md`](DOCS/02-target-architecture.md) |
| See the plan / current phase | [`DOCS/05-roadmap.md`](DOCS/05-roadmap.md) |
| Send a task-completion notification | [`RULES/notifications.md`](RULES/notifications.md) (ntfy.sh, topic `TASKISDONE`) |

---

## 4. Hard rules (do not violate)

1. **Read `ARCHITECTURE.md` before writing ANY code.** It is the single source of truth. If something isn't covered there, ask — don't invent.
2. **Do not modify anything under `ANIYOMI_REFRENCE/` or `OLD_ANIKUTA/`.** Both are frozen snapshots. If you need to copy an idea, copy it into `ANIKUTA_PROJECT/ANIKUTA/` and adapt it there.
3. **Do not build APKs locally.** No `./gradlew assembleDebug`, no Android Studio builds. CI does this. If you need to verify compilation, do it via a CI-triggered workflow or a syntax/type check that doesn't produce an APK.
4. **Do not commit secrets, tokens, or keystore files.** The `.gitignore` blocks common ones, but stay vigilant.
5. **Every non-trivial change must be documented.** If you add a module, a design choice, or change an approach, update `ARCHITECTURE.md` and [`DOCS/04-design-decisions.md`](DOCS/04-design-decisions.md) in the same commit.
6. **One concern per commit.** Keep commits focused so future agents can `git log` to understand history.
7. **Never delete or rewrite documented decisions silently.** If a decision is superseded, mark it `~~struck through~~` with a "Superseded by ADR-XXX on YYYY-MM-DD" note, do not erase it.
8. **Follow the rules in [`RULES/ai-agent-rules.md`](RULES/ai-agent-rules.md)** — 14 sections covering architecture, data flow, modularity, deps, design, tasks, logging, code quality, communication, errors, git.
9. **Follow the project-specific rules in [`RULES/project-conventions.md`](RULES/project-conventions.md)** — reference boundaries, CI-only builds, notifications, handoff.
10. **Always send a task-completion notification** via ntfy.sh (topic `TASKISDONE`). See [`RULES/notifications.md`](RULES/notifications.md). Applies to every task, small or big.

---

## 5. Where the references came from

There are **two** read-only references in this repo:

### `ANIYOMI_REFRENCE/ANIYOMI/` — the original Aniyomi source
- Source: `https://github.com/aniyomiorg/aniyomi` (branch `main`).
- Downloaded as a source **tarball** (no `.git` history) — see `ANIYOMI_REFRENCE/README.md`.
- Not kept in sync with upstream; point-in-time snapshot for study.
- **Fully documented** in `ANIYOMI_REFRENCE/DOCUMENTATION/` (68 docs, ~21,900 lines). Read the docs instead of reverse-engineering the source.

### `OLD_ANIKUTA/ANIKUTA_OLD/` — our previous ANIKUTA attempt
- Source: `https://github.com/testplay-byte/anikuta` (branch `main`).
- Downloaded as a source **tarball** (no `.git` history) — see `OLD_ANIKUTA/README.md`.
- The old project reached a working state but had structural/modularity issues and unpolished UI. We rebuild fresh in `ANIKUTA_PROJECT/`.
- **Valuable:** the old project contains prior Aniyomi subsystem analysis under
  `OLD_ANIKUTA/ANIKUTA_OLD/DOCS/REFERENCE-DOCS/SUBSYSTEMS/` and 30+ session logs
  under `OLD_ANIKUTA/ANIKUTA_OLD/MEMORY/SESSION-LOGS/`. Mine these during the
  design phase instead of redoing the research.
- Note: the old project shipped its **own** Aniyomi snapshot at
  `OLD_ANIKUTA/ANIKUTA_OLD/REFERENCE/` (commit `2f5cf77`). This duplicates our
  top-level `ANIYOMI_REFRENCE/` but at an older commit. Prefer our top-level
  snapshot when studying Aniyomi; cross-reference the old one only when its docs
  cite specific lines.

---

## 6. Build policy reminder

- **GitHub Actions only.** See [`DOCS/06-build-and-ci.md`](DOCS/06-build-and-ci.md).
- The placeholder workflow at `.github/workflows/ci-placeholder.yml` exists to be expanded later. Do not wire it to produce releases until the `ANIKUTA` app actually has something to build.

---

## 7. Handing off to the next agent

When you finish a work session:

1. Create a file `RULES/sessions/YYYY-MM-DD-HHMM-short-slug.md` (use UTC).
2. Use the template in [`RULES/session-handoff-template.md`](RULES/session-handoff-template.md).
3. Summarize: what you did, what you left unfinished, what the next agent should do, any blockers, any decisions that need the human.
4. Commit it with message `docs(rules): session handoff YYYY-MM-DD`.
5. **Send a ntfy.sh notification** (topic `TASKISDONE`, green 🟩 for success or red 🟥 for errors).

This is how continuity is preserved across agent boundaries.
