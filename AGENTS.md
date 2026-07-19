# AGENTS.md — Orientation Guide for AI Agents

> If you are an AI agent (or a new human contributor) resuming work on ANIKUTA, **read this file first.**
> It is the single entry point for understanding where things are and how to operate safely.

---

## 1. The 30-second orientation

- **ANI_KUTA_NEW** is a monorepo for building **ANIKUTA**, a reimagined version of the Aniyomi manga/anime reader.
- **We did NOT fork Aniyomi.** Its source is stored read-only under `ANIYOMI_REFRENCE/ANIYOMI/` for reference only.
- **All new code goes under `ANIKUTA_PROJECT/ANIKUTA/`.**
- **Never build APKs locally.** Builds happen only through GitHub Actions (`.github/workflows/`).

---

## 2. Read order when resuming work

1. `README.md` (root) — project overview.
2. `AGENTS.md` (this file) — how to operate.
3. `rules/agent-conventions.md` — hard rules you MUST follow.
4. `rules/session-handoff-template.md` — the format for leaving notes for the next agent.
5. `docs/04-design-decisions.md` — every decision made so far and why (avoids re-litigating).
6. `docs/05-roadmap.md` — what phase we are in and what comes next.
7. `docs/03-reference-module-map.md` — what each Aniyomi module does (use when porting ideas).
8. The most recent session handoff note in `rules/sessions/` (if any) — what the last agent was doing.

> If a `rules/sessions/` directory exists, the newest file there is the latest handoff. Read it before starting.

---

## 3. Where things live (cheat sheet)

| You want to... | Go here |
|---|---|
| Read the original Aniyomi code for reference | `ANIYOMI_REFRENCE/ANIYOMI/` (**read-only**) |
| Write new ANIKUTA app code | `ANIKUTA_PROJECT/ANIKUTA/` |
| Record an architecture/design decision | `docs/design-decisions/` (or `docs/04-design-decisions.md`) |
| Leave a note for the next agent | `rules/sessions/` (create the folder if missing) |
| Add/modify CI | `.github/workflows/` |
| Understand the target architecture | `docs/02-target-architecture.md` |
| See the plan / current phase | `docs/05-roadmap.md` |

---

## 4. Hard rules (do not violate)

1. **Do not modify anything under `ANIYOMI_REFRENCE/`.** It is a frozen snapshot. If you need to copy an idea, copy it into `ANIKUTA_PROJECT/ANIKUTA/` and adapt it there.
2. **Do not build APKs locally.** No `./gradlew assembleDebug`, no Android Studio builds. CI does this. If you need to verify compilation, do it via a CI-triggered workflow or a syntax/type check that doesn't produce an APK.
3. **Do not commit secrets, tokens, or keystore files.** The `.gitignore` blocks common ones, but stay vigilant.
4. **Every non-trivial change must be documented.** If you add a module, a design choice, or change an approach, update `docs/04-design-decisions.md` (and the relevant `docs/` file) in the same commit.
5. **One concern per commit.** Keep commits focused so future agents can `git log` to understand history.
6. **Never delete or rewrite documented decisions silently.** If a decision is superseded, mark it `~~struck through~~` with a "Superseded by ADR-XXX on YYYY-MM-DD" note, do not erase it.
7. **Follow the naming & structure conventions** in `rules/agent-conventions.md`.

---

## 5. Where the reference Aniyomi came from

- Source: `https://github.com/aniyomiorg/aniyomi` (branch `main`).
- Downloaded as a source **tarball** (no `.git` history) on the date shown in `ANIYOMI_REFRENCE/README.md`.
- It is **not kept in sync** with upstream. It is a point-in-time snapshot for study. To update it, see the procedure in `ANIYOMI_REFRENCE/README.md`.

---

## 6. Build policy reminder

- **GitHub Actions only.** See `docs/06-build-and-ci.md`.
- The placeholder workflow at `.github/workflows/ci-placeholder.yml` exists to be expanded later. Do not wire it to produce releases until the `ANIKUTA` app actually has something to build.

---

## 7. Handing off to the next agent

When you finish a work session:

1. Create a file `rules/sessions/YYYY-MM-DD-HHMM-short-slug.md` (use UTC).
2. Use the template in `rules/session-handoff-template.md`.
3. Summarize: what you did, what you left unfinished, what the next agent should do, any blockers, any decisions that need the human.
4. Commit it with message `docs(rules): session handoff YYYY-MM-DD`.

This is how continuity is preserved across agent boundaries.
