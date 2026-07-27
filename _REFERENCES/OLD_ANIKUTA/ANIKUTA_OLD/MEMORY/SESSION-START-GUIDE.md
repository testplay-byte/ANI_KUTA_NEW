# Session Start Guide

> Read this **first** at the start of every new session.
> It tells you exactly what to load into memory before doing anything.

---

## Why this exists

The sandbox sometimes forgets or deletes things. We survive by keeping
everything in `MEMORY/` + `DOCS/` on GitHub. This guide is the reliable
entry point that never changes location.

---

## Step-by-step (do these in order)

1. **Read the AI Agent Onboarding guide.**
   - `DOCS/ENGINEERING/AI-AGENT-ONBOARDING.md` — the proper entry point with
     the 5-minute reading order. **Start here.**

2. **Read the current state.**
   - `DOCS/CURRENT-STATE.md` — where the project is right now (single source
     of truth for status).

3. **Read the rules.**
   - `DOCS/ENGINEERING/WORKING-RULES.md` — the binding change rules.
   - `MEMORY/CORE-RULES.md` — the project's original 4 core rules + philosophy.
     (Note: §0 "Project Snapshot" is a quick orientation only — use
     `DOCS/CURRENT-STATE.md` for live status.)

4. **Read the technical overview (if you need depth).**
   - `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` — the comprehensive verified map.

5. **Check past decisions.**
   - `MEMORY/DECISIONS/` — so we don't relitigate settled choices.

6. **Check the latest activity.**
   - The tail of `worklog.md` (repo root) — the project's session-by-session log.
     Note: it's a historical narrative; verify live state against git, not against
     this file.

7. **Check the GitHub token.**
   - `MEMORY/CREDENTIALS/github-token.txt` — if it's missing, ask the user
     to re-paste it. (It is gitignored, so it won't be in the repo backup.)

8. **Confirm the current goal with the user** before making changes.

---

## Working rules (reminder)

- Understand before acting. Never assume.
- Split big tasks into small, documented files.
- One issue at a time. Verify before fixing. Don't hand fixes to sub-agents.
- Summarize when done. Clarify before big/breaking changes.
- Ask questions in batches of 5. Keep wording simple.
- Be honest and direct. Don't sugarcoat.

---

## When you finish a session

1. Update `DOCS/CURRENT-STATE.md` if the project status changed.
2. Update `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` if structure/build changed.
3. Update `DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md` if a structural issue
   was resolved or found.
4. (Optional) Update or create a session log in `MEMORY/SESSION-LOGS/`.
5. Record any new decisions in `MEMORY/DECISIONS/`.
6. Commit and push `MEMORY/` + `DOCS/` to GitHub.
7. Send the done-notification: `ntfy.sh/TASKISDONE`.
8. Update the live preview web page if the structure/status changed.

---

_Last updated: 2026-07-16 (documentation cleanup)._
