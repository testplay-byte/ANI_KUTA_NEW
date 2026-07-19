# rules/

Operating rules for AI agents (and humans) working on ANIKUTA. These are
**normative** — they say how we work, not what the code does (that's in `../docs/`).

| File | Purpose |
|---|---|
| [agent-conventions.md](agent-conventions.md) | Hard conventions every agent must follow. |
| [session-handoff-template.md](session-handoff-template.md) | Template for leaving a note for the next agent. |

## Session handoff notes

When you finish a work session, create a file under `sessions/` (create the folder
if it does not exist) named `YYYY-MM-DD-HHMM-short-slug.md` (UTC), using the
template. This is how continuity is preserved across agent boundaries.

The newest file in `sessions/` is the most recent handoff — read it before starting
work.
