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

**Phase 0b (Design & Planning) is COMPLETE.** All decisions recorded (ADRs 001–030).
`ARCHITECTURE.md` is finalized. Ready for **Phase 1** (scaffold the Gradle project)
pending the owner's go-ahead.

See [`../PLANNING/PHASED_PLAN.md`](../PLANNING/PHASED_PLAN.md) for the full
10-phase implementation plan.

## What's been done so far

- ✅ Repo structured: `ANIYOMI_REFRENCE/` (reference + 68-doc analysis),
  `OLD_ANIKUTA/` (prior attempt + screen analysis), `ANIKUTA_PROJECT/` (skeleton).
- ✅ Rules established (`RULES/ai-agent-rules.md` — 14 sections).
- ✅ Aniyomi reference fully documented (`ANIYOMI_REFRENCE/DOCUMENTATION/`).
- ✅ Vision clarified → 14 ADRs (009–022) in `DOCS/04`.
- ✅ Design language docs complete (`DESIGN_LANGUAGE/` — 12 principles, 9 components,
  themes, 10 per-screen specs).
- ✅ Old ANIKUTA key screens analyzed (`OLD_ANIKUTA/ANALYSIS/` — 4 files).
- ✅ Episode metadata module spec (`PLANNING/01-feature-specs/episode-metadata-module.md`).
- ✅ Module architecture draft (`PLANNING/04-module-architecture/`).
- ✅ **All open decisions resolved** → ADRs 023–030 (Koin, SQLDelight, Compose-first,
  SDK 26/36, Moko English-only, gzipped protobuf backup, Aniyomi extension compat,
  raw HTTP AniList client).
- ✅ `ARCHITECTURE.md` **finalized** — the single source of truth.
- ✅ Phased implementation plan (`PLANNING/PHASED_PLAN.md` — 10 phases).
- ✅ Agent onboarding (`AGENT_CONTEXT/START_HERE.md` + `PROJECT_STARTUP.md`).

## What's NOT done yet

- ❌ Any actual ANIKUTA app code (Phase 1 starts the scaffolding).
- ❌ The Gradle project under `ANIKUTA_PROJECT/ANIKUTA/` (still a placeholder README).
- ❌ All Phase 1–10 implementation.

## Where things live (cheat sheet)

| You want to... | Go here |
|---|---|
| Understand the vision | `DOCS/04-design-decisions.md` (ADRs 009–022) |
| Understand the design language | `DESIGN_LANGUAGE/` |
| Read the Aniyomi reference analysis | `ANIYOMI_REFRENCE/DOCUMENTATION/` |
| Read the old ANIKUTA screen analysis | `OLD_ANIKUTA/ANALYSIS/` |
| Find planning specs | `PLANNING/` |
| Find the rules | `RULES/` |
| Write new code | `ANIKUTA_PROJECT/ANIKUTA/` (Phase 1) |
| Leave a note for the next agent | `RULES/sessions/` |

## Hard rules (don't violate)

1. **Read `ARCHITECTURE.md` first.** It's the single source of truth.
2. **Don't modify the references** (`ANIYOMI_REFRENCE/`, `OLD_ANIKUTA/`).
3. **Don't build APKs locally.** CI-only (ADR-003).
4. **Send a ntfy.sh notification** on every task completion (ADR-008).
5. **No blind guesses.** If unsure, ask — show reasoning first (Rule §1).
6. **Follow the design language.** See `DESIGN_LANGUAGE/` — don't improvise UI.

## What to do next (if you're resuming)

1. Read the newest session note in `RULES/sessions/`.
2. Check `DOCS/05-roadmap.md` for the current phase's exit criteria.
3. If architecture isn't finalized → work on `ARCHITECTURE.md` + `PLANNING/04`.
4. If architecture is finalized → start Phase 1 (scaffold the Gradle project).

---

*This file is the agent onboarding checkpoint. Keep it updated as the project
evolves. A new agent should never need to read chat history — this file + the
read-order above is enough.*
