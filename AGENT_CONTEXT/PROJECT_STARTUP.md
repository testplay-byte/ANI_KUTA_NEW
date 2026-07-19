# PROJECT_STARTUP.md — Starting Instructions for AI Agents

> **Read this file FIRST when you start working on ANIKUTA.**
> It gives you the exact steps to get oriented, understand the project, and begin
> working safely. Follow it in order.

---

## Step 1: Read the mandatory files (in this order)

Before writing any code or making any change, read these files:

1. **[`../ARCHITECTURE.md`](../ARCHITECTURE.md)** — the single source of truth for
   the project. Per `RULES/ai-agent-rules.md` §2, you MUST read this before writing
   any code. If it's still a stub, the architecture isn't finalized — ask the owner.

2. **[`../RULES/ai-agent-rules.md`](../RULES/ai-agent-rules.md)** — the 14-section
   ruleset. Non-negotiable. Covers: no blind guesses, data flow, modularity, deps,
   design language, task management, logging, code quality, communication, errors,
   git, module boundaries.

3. **[`../RULES/project-conventions.md`](../RULES/project-conventions.md)** —
   ANIKUTA-specific rules: reference boundaries (read-only), CI-only builds, ntfy
   notifications, session handoff.

4. **[`../RULES/notifications.md`](../RULES/notifications.md)** — the ntfy.sh
   notification format. You MUST send a notification on every task completion.

5. **[`../DOCS/04-design-decisions.md`](../DOCS/04-design-decisions.md)** — all
   decisions (ADRs 001–022+). Read this to understand the "why" behind everything.

6. **[`../DOCS/05-roadmap.md`](../DOCS/05-roadmap.md)** — what phase we're in and
   what's next.

7. **The newest session note in [`../RULES/sessions/`](../RULES/sessions/)** —
   what the last agent was doing. Find the newest file by name (YYYY-MM-DD-HHMM).

8. **[`../DESIGN_LANGUAGE/`](../DESIGN_LANGUAGE/)** — the UI/UX spec. Read the
   `01-principles/core-principles.md` and `02-components/components.md` first, then
   the specific screen you're working on.

---

## Step 2: Understand the project (the 2-minute brief)

**ANIKUTA** is an anime-first Android app (manga comes later) that combines:

1. An **extension-based** content system (like Aniyomi) — extensions are external
   APKs that implement the `:source-api` contract.
2. **AniList as a co-primary data source** (not just a tracker) — for discovery,
   metadata, and personalization. The user picks their preferred metadata source
   (AniList vs extension) with automatic fallback.
3. A **custom design language** (M3-inspired but unique — see `DESIGN_LANGUAGE/`).
4. **Unique features**: YouTube-style watch page, per-episode metadata, dual-mode
   episode notifications, auto-download, customizable screens/nav, floating bottom nav.

**It is NOT a fork of Aniyomi.** The Aniyomi source is a read-only reference at
`ANIYOMI_REFRENCE/`. All new code goes in `ANIKUTA_PROJECT/ANIKUTA/`.

---

## Step 3: Know the hard rules (don't violate these)

1. **Read `ARCHITECTURE.md` first.** It's the single source of truth.
2. **Don't modify the references** (`ANIYOMI_REFRENCE/`, `OLD_ANIKUTA/`) — read-only.
3. **Don't build APKs locally.** CI-only (ADR-003). No `./gradlew assemble*`.
4. **Send a ntfy.sh notification** on every task completion (ADR-008).
5. **No blind guesses.** If unsure, ask — show reasoning first (Rule §1).
6. **Follow the design language.** See `DESIGN_LANGUAGE/` — don't improvise UI.
7. **Respect module boundaries.** Feature modules never import from other feature
   modules. Cross-feature goes through `:core` (Rule §3, §4).
8. **Data flows through layers:** UI → ViewModel → Repository → Data Source (Rule §3).
9. **One concern per commit.** Descriptive commit messages.
10. **Document as you go.** New decision → ADR. New module → README. Phase task → tick.

---

## Step 4: Know where things live

| You want to... | Go here |
|---|---|
| Understand the vision | `DOCS/04-design-decisions.md` (ADRs 009–022) |
| Understand the design language | `DESIGN_LANGUAGE/` |
| Read the Aniyomi reference analysis | `ANIYOMI_REFRENCE/DOCUMENTATION/` |
| Read the old ANIKUTA screen analysis | `OLD_ANIKUTA/ANALYSIS/` |
| Find planning specs | `PLANNING/` |
| Find the rules | `RULES/` |
| Write new code | `ANIKUTA_PROJECT/ANIKUTA/` (Phase 1+) |
| Leave a note for the next agent | `RULES/sessions/` |
| Send a notification | ntfy.sh, topic `TASKISDONE` (see `RULES/notifications.md`) |

---

## Step 5: How to make a change (the workflow)

1. **Understand** — read the relevant docs + the area you're touching.
2. **Plan** — which modules/files are affected? What data flows in/out? What are the
   side effects? (Rule §7).
3. **Ask** — if anything is unclear or undecided, ask the owner. Show your reasoning,
   list 2-3 options with trade-offs, recommend one (Rule §1).
4. **Implement** — follow the architecture, module boundaries, design language.
5. **Verify** — does it compile (via CI)? Do tests pass? Any regressions?
6. **Document** — update `ARCHITECTURE.md` if structural. Add an ADR if a decision.
   Update the relevant `DESIGN_LANGUAGE/` or `PLANNING/` doc if applicable.
7. **Hand off** — write a session note in `RULES/sessions/` using the template.
8. **Notify** — send a ntfy.sh notification (green 🟩 for success, red 🟥 for error).

---

## Step 6: How to send a notification

```bash
# Success
curl -s -H "Title: <short title>" -d "🟩🟩🟩🟩🟩🟩🟩🟩

<message>" https://ntfy.sh/TASKISDONE

# Error
curl -s -H "Title: <short title>" -d "🟥🟥🟥🟥🟥🟥🟥🟥

<message>" https://ntfy.sh/TASKISDONE

# Processing (starting a long task)
curl -s -H "Title: <short title>" -d "🟧🟧🟧🟧🟧🟧🟧🟧

<message>" https://ntfy.sh/TASKISDONE

# Stopped / needs input
curl -s -H "Title: <short title>" -d "🟦🟦🟦🟦🟦🟦🟦🟦

<message>" https://ntfy.sh/TASKISDONE
```

8 emojis (same color) on line 1, blank line, then the message. See
`RULES/notifications.md` for details.

---

## Step 7: The end goal (what we're building toward)

A production-ready Android anime app with:
- **Feature parity** with Aniyomi's anime features (sources, downloads, tracking, backup, history, updates).
- **AniList integration** as a co-primary data source (discovery, metadata, personalization).
- A **custom design language** (M3-inspired, unique look — see `DESIGN_LANGUAGE/`).
- **Unique features**: watch page, per-episode metadata, dual-mode notifications, auto-download, customizable screens/nav.
- **Manga support** deferred (architecture-ready, hidden behind UI).
- **Extensible architecture** — add features to any screen without rework.

Built via **GitHub Actions only** (no local APK builds). Highly documented so any
new agent can continue the work.

---

## Step 8: If the architecture isn't finalized yet

If `ARCHITECTURE.md` is still a stub (Phase 0b), do NOT start writing app code.
Instead:
1. Check `DOCS/04-design-decisions.md` for open decisions.
2. Check `PLANNING/04-module-architecture/` for the draft module plan.
3. Ask the owner to resolve the open decisions (DI framework, persistence, SDK, etc.).
4. Once resolved, finalize `ARCHITECTURE.md`, then proceed to Phase 1 (scaffolding).

---

## Step 9: Keep this folder updated

When you finish a work session:
1. Update `START_HERE.md` if the "What's been done" or "Current phase" sections changed.
2. Update this file if the workflow or rules changed.
3. Write a session handoff note in `RULES/sessions/`.

The goal: the next agent reads `PROJECT_STARTUP.md` + `START_HERE.md` + the read
order they point to, and has enough context to continue working without asking
questions that are already answered in the docs.

---

*This file is the agent's first stop. Keep it accurate and current.*
