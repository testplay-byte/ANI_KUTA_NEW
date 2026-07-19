# RULES/

Operating rules for AI agents (and humans) working on ANIKUTA. These are
**normative** — they say how we work, not what the code does (that's in
`ARCHITECTURE.md` and `DOCS/`).

## Read order

1. [`../ARCHITECTURE.md`](../ARCHITECTURE.md) — **the single source of truth** (read FIRST, per `ai-agent-rules.md` §2).
2. [`ai-agent-rules.md`](ai-agent-rules.md) — the general ruleset (14 sections + quick-paste version).
3. [`project-conventions.md`](project-conventions.md) — ANIKUTA-specific rules (reference boundaries, build policy, notifications, handoff).
4. [`notifications.md`](notifications.md) — the ntfy.sh notification format.
5. The newest file in [`sessions/`](sessions/) — the latest handoff note.

## Files

| File | Purpose |
|---|---|
| [ai-agent-rules.md](ai-agent-rules.md) | The comprehensive general ruleset (14 sections covering architecture, data flow, modularity, deps, design, tasks, logging, code quality, communication, errors, git). |
| [project-conventions.md](project-conventions.md) | ANIKUTA-specific rules: reference boundaries, CI-only build policy, ntfy notifications, session handoff, decision logging. |
| [notifications.md](notifications.md) | ntfy.sh task-completion notification rule (topic `TASKISDONE`). **Mandatory on every task.** |
| [session-handoff-template.md](session-handoff-template.md) | Template for leaving a note for the next agent. |
| [sessions/](sessions/) | Session handoff notes (newest = most recent). |

## Session handoff notes

When you finish a work session, create a file under `sessions/` named
`YYYY-MM-DD-HHMM-short-slug.md` (UTC), using the template. This is how continuity
is preserved across agent boundaries.

The newest file in `sessions/` is the most recent handoff — **read it before
starting work**.
