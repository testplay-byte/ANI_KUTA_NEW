# Project Conventions — ANIKUTA-specific Rules

> These rules are **project-specific** and complement the general
> [`ai-agent-rules.md`](ai-agent-rules.md). They cover things unique to this
> repository (the reference snapshots, the CI-only build policy, the notification
> rule, and the session handoff process) that the general ruleset doesn't address.

---

## 1. Read order when starting a session

Before changing anything, read in this order:
1. [`../README.md`](../README.md) — project overview.
2. [`../AGENTS.md`](../AGENTS.md) — orientation guide.
3. [`../ARCHITECTURE.md`](../ARCHITECTURE.md) — **the single source of truth** (per `ai-agent-rules.md` §2).
4. [`ai-agent-rules.md`](ai-agent-rules.md) — the general rules.
5. This file.
6. [`../DOCS/04-design-decisions.md`](../DOCS/04-design-decisions.md) — every decision made so far + open questions.
7. [`../DOCS/05-roadmap.md`](../DOCS/05-roadmap.md) — what phase we are in and what's next.
8. The newest file in [`sessions/`](sessions/) — what the last agent was doing.
9. The specific area you are about to touch.

---

## 2. Respect the reference boundaries (CRITICAL)

This repo contains **two read-only reference snapshots**. Neither may be modified,
reorganized, or built inside.

| Reference | Location | What it is | ADR |
|---|---|---|---|
| Aniyomi source | `ANIYOMI_REFRENCE/ANIYOMI/` | The original Aniyomi app (source-only, no `.git`) | ADR-002, ADR-005 |
| Old ANIKUTA project | `OLD_ANIKUTA/ANIKUTA_OLD/` | Our previous attempt (source-only, no `.git`) | ADR-007 |
| Aniyomi documentation | `ANIYOMI_REFRENCE/DOCUMENTATION/` | 68-doc analysis of the Aniyomi source | — |

**Rules:**
- 🔒 **READ-ONLY.** Never edit, rename, or reorganize files in these folders.
- 🚫 **Do not build here.** No `./gradlew` runs inside any reference folder.
- 📋 **Copying ideas is allowed and encouraged.** To port a concept: read it in
  the reference, then implement fresh under `ANIKUTA_PROJECT/ANIKUTA/`. Do not
  edit the reference in place.
- 📖 The `ANIYOMI_REFRENCE/DOCUMENTATION/` folder is a comprehensive analysis of
  the Aniyomi source. Read it instead of reverse-engineering the source.

---

## 3. Build policy: GitHub Actions ONLY (CRITICAL)

> Per ADR-003 and `ai-agent-rules.md` §7.

- **Never produce an APK/AAB locally.** No `./gradlew assemble*` / `bundle*`.
- **Never run any build inside a reference folder** (`ANIYOMI_REFRENCE/` or `OLD_ANIKUTA/`).
- **All builds happen via GitHub Actions** (`.github/workflows/`).
- Static checks, edits, and documentation are fine locally.
- See [`../DOCS/06-build-and-ci.md`](../DOCS/06-build-and-ci.md) for the full policy.

---

## 4. Task-completion notifications via ntfy.sh (MANDATORY)

> Per ADR-008. This is **in addition** to the structured summary required by
> `ai-agent-rules.md` §11.

- **Every task completion — small or big — MUST trigger a notification** via
  [ntfy.sh](https://ntfy.sh) to topic **`TASKISDONE`**.
- Also send a 🟧 "processing" notification when starting a long task, and a 🟦
  "stopped/needs-input" notification when blocked.
- The exact format, colors, and curl command are in [`notifications.md`](notifications.md).
- **Color semantics:** 🟩 success / 🟥 error / 🟦 stopped-needs-input / 🟧 processing.
- **Format:** 8 emojis (same color) on line 1, blank line, then the message.
- This is non-negotiable. An agent that forgets to notify has not finished the task.

---

## 5. Session handoff process

> Per ADR-006. Continuity across agent boundaries depends on this.

- At the end of every work session, write a handoff note in [`sessions/`](sessions/)
  using the [`session-handoff-template.md`](session-handoff-template.md).
- Name it `sessions/YYYY-MM-DD-HHMM-short-slug.md` (UTC).
- The note must let a cold-start agent continue your work without asking questions.
- Include: what you did, what's done, what's not done, what the next agent should do,
  any blockers, and pointers to the key files.

---

## 6. Decision logging

- Every non-trivial decision is recorded as an ADR in
  [`../DOCS/04-design-decisions.md`](../DOCS/04-design-decisions.md).
- If a decision is superseded, mark it ~~struck through~~ with a "Superseded by
  ADR-XXX on YYYY-MM-DD" note. **Never delete entries.**
- Open (undecided) questions are listed at the bottom of that file. If you need
  an answer that isn't there, **ask the human** — don't silently pick one.

---

## 7. The ANIYOMI reference vs. our ANIKUTA project

> A common source of confusion: the reference uses a different architecture than
> what we're building.

| | Aniyomi reference (`ANIYOMI_REFRENCE/`) | Our project (`ANIKUTA_PROJECT/ANIKUTA/`) |
|---|---|---|
| Architecture | Tachiyomi/Mihon lineage: `:app`, `:core:common`, `:data`, `:domain`, etc. Injekt DI. | **To be defined** in `ARCHITECTURE.md` — will follow `ai-agent-rules.md` (`:feature:*`, `:core`, `:data`). |
| Status | Frozen snapshot (read-only) | Not yet scaffolded (Phase 1, after design) |
| Use | Study + port ideas | The actual new app |

When studying the reference, note its patterns but **do not assume** we'll copy
them. Our architecture will be defined in `ARCHITECTURE.md` and may differ.

---

## See also

- [`ai-agent-rules.md`](ai-agent-rules.md) — the general ruleset (read first).
- [`notifications.md`](notifications.md) — the ntfy.sh notification format.
- [`session-handoff-template.md`](session-handoff-template.md) — handoff note template.
- [`../ARCHITECTURE.md`](../ARCHITECTURE.md) — the single source of truth for the project.
- [`../DOCS/04-design-decisions.md`](../DOCS/04-design-decisions.md) — the ADR log.
- [`../DOCS/06-build-and-ci.md`](../DOCS/06-build-and-ci.md) — the CI-only build policy.
